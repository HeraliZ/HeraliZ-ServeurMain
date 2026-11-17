package fr.heraliz.recolte.client.enmain;

import fr.heraliz.recolte.entites.Arbre;
import fr.heraliz.recolte.entites.Gisement;
import fr.heraliz.recolte.objets.OutilRecolte;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.WeakHashMap;

/**
 * Le coup d'un outil de récolte chez le joueur (A_FAIRE §7, PROPOSITION §6 bis). Le serveur décide de tout (outil,
 * portée, cadence de 1,2 s, matière) ; ici, seulement quand part l'attaque et ce qu'on voit :
 * <ul>
 *    <li>outil en main, le clic gauche lance le geste ({@link Geste}, 0,6 s) au lieu du coup immédiat de Minecraft (les
 *    clics de la touche Attaquer sont pris au début du tick, comme le marteau de CRac) ; d'autres mods peuvent le
 *    refuser comme un clic de Minecraft (joueur à terre, emote, cinématique : même évènement de Forge) ;</li>
 *    <li>l'attaque normale de Minecraft part À L'IMPACT du geste, sur ce que vise le joueur à cet instant (gisement,
 *    arbre, et aussi un joueur ou un zombie : l'outil reste une arme faible). Rien de visé : coup dans le vide, même
 *    geste sans impact ;</li>
 *    <li>à l'impact, petite secousse de la vue (première personne, réglage « effets de distorsion » de Minecraft) ;</li>
 *    <li>clic maintenu : un coup dès que la cadence le permet, tant qu'on vise un gisement ou un arbre debout que
 *    l'outil sait récolter. Un clic un peu trop tôt (0,3 s) est gardé ;</li>
 *    <li>cadence vue ici : un coup toutes les {@link #CADENCE} ticks (1,25 s : les 1,2 s du serveur plus une marge
 *    pour le réseau), et jamais plus vite en temps réel ; pas de coup pendant que l'outil sort ;</li>
 *    <li>les autres joueurs voient le geste : il part avec le geste de la main GAUCHE de Minecraft (jamais fait par un
 *    autre geste de l'outil, la main gauche du serveur étant vide), que les autres jeux reconnaissent
 *    ({@link #ageVu}).</li>
 * </ul>
 * Rien ne tourne sans outil en main (une comparaison par tick). Aucune allocation par image.
 */
public final class CoupClient {
    /** Ticks entre deux coups, et la même chose en temps réel (ms, un peu moins : ticks irréguliers). */
    static final int CADENCE = 25;
    private static final long CADENCE_MS = CADENCE * 50L - 30L;
    /** Clic gardé s'il arrive au plus tant de ticks avant que le coup soit permis. */
    private static final int CLIC_GARDE = 6;
    /** Sortie de l'outil quand on le prend en main (ticks) : pas de coup avant la fin. */
    private static final float SORTIE = 6.0F;
    /** Secousse de la vue à l'impact (ticks) et ses amplitudes (degrés). */
    private static final float SECOUSSE = 5.0F, SECOUSSE_TANGAGE = 0.9F, SECOUSSE_ROULIS = 0.5F;
    private static final long LOIN = Long.MIN_VALUE / 4L;

    /** Horloge du joueur, en ticks (avance à la fin de chaque tick du jeu). */
    private static long horloge;
    /** Coup en cours (tick où son geste a 0 tick), son côté (hache-pioche : pic) et son impact déjà donné. */
    private static long debut = LOIN;
    private static boolean pic, impactFait = true;
    /** Prochain coup permis (tick, ms), dernier clic gardé, sortie de l'outil, dernière secousse. */
    private static long pretA, pretMs, clic = LOIN, sortieDebut = LOIN, secousseDebut = LOIN;
    /** Case et objet tenus au dernier tick (sortie de l'outil quand ils changent). */
    private static int caseVue = -1;
    private static Item objetVu;
    /** Vrai pendant que l'évènement de clic est posté ici (il ne doit pas être annulé par {@link #touche}). */
    private static boolean posteIci;
    /** Coups vus des autres (et le sien, vue de dos) : début du geste (tick), côté pic (1). Oubliés avec l'entité. */
    private static final WeakHashMap<Entity, long[]> COUPS = new WeakHashMap<>();

    private CoupClient() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, CoupClient::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, InputEvent.InteractionKeyMappingTriggered.class, CoupClient::touche);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.ComputeCameraAngles.class, CoupClient::secousse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, e -> oublier());
    }

    private static void oublier() {
        debut = LOIN;
        impactFait = true;
        pretA = 0L;
        pretMs = 0L;
        clic = LOIN;
        sortieDebut = LOIN;
        secousseDebut = LOIN;
        caseVue = -1;
        objetVu = null;
        COUPS.clear();
    }

    // ------------------------------------------------------------------------------------------------ état lu au rendu

    /** Ticks depuis le début du geste en cours du joueur (première personne), -1 sans geste. */
    static float ageCoup(float partiel) {
        float t = (float) (horloge - debut) + partiel;
        return t >= 0.0F && t < Geste.FIN ? t : -1.0F;
    }

    /** Hache-pioche : le coup en cours est donné du côté pic (choisi à son début : un gisement visé). */
    static boolean coupPic() {
        return pic;
    }

    /** De 1 (outil tout juste pris en main, baissé) à 0 (sorti). */
    static float sortie(float partiel) {
        float t = (float) (horloge - sortieDebut) + partiel;
        if (t < 0.0F || t >= SORTIE) {
            return 0.0F;
        }
        float u = 1.0F - t / SORTIE;
        return u * u;
    }

    /**
     * Ticks depuis le début du dernier geste de cette entité, -1 sans geste. Le geste d'un autre joueur est reconnu au
     * geste de sa main gauche (envoyé par son jeu au début du coup) : son début est retrouvé avec {@code swingTime},
     * même si on le voit un peu après. Le sien est noté au début du coup ({@link #lancer}).
     */
    static float ageVu(LivingEntity e, float partiel) {
        long[] c = COUPS.get(e);
        if (e.swinging && e.swingingArm == InteractionHand.OFF_HAND) {
            long d = horloge - e.swingTime;
            if (c == null) {
                c = new long[]{LOIN, 0L};
                COUPS.put(e, c);
            }
            if (d > c[0] + 3L) {
                c[0] = d;
                c[1] = 0L;
            }
        }
        if (c == null) {
            return -1.0F;
        }
        float t = (float) (horloge - c[0]) + partiel;
        return t >= 0.0F && t < Geste.FIN ? t : -1.0F;
    }

    /** Le dernier geste de cette entité est donné côté pic (seulement connu pour soi : les autres, côté hache). */
    static boolean picVu(LivingEntity e) {
        long[] c = COUPS.get(e);
        return c != null && c[1] != 0L;
    }

    // ------------------------------------------------------------------------------------------------ ticks et touches

    private static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase == TickEvent.Phase.START) {
            debutTick(mc);
            return;
        }
        horloge++;
        LocalPlayer joueur = mc.player;
        if (joueur == null) {
            caseVue = -1;
            objetVu = null;
            return;
        }
        suivreSortie(joueur);
        if (!impactFait && horloge - debut >= (long) Geste.IMPACT) {
            impactFait = true;
            impact(mc, joueur);
        }
    }

    /** Début du tick, avant les touches de Minecraft : clics de la touche Attaquer, clic maintenu. */
    private static void debutTick(Minecraft mc) {
        LocalPlayer joueur = mc.player;
        if (joueur == null || mc.level == null || mc.gameMode == null
                || !(joueur.getMainHandItem().getItem() instanceof OutilRecolte outil)) {
            return;
        }
        int clics = 0;
        while (mc.options.keyAttack.consumeClick()) {
            clics++;
        }
        if (mc.screen != null || mc.getOverlay() != null || joueur.isSpectator() || !joueur.isAlive() || joueur.isUsingItem()
                || joueur.isHandsBusy()) {
            return; // comme Minecraft : clics perdus
        }
        // le geste lancé à ce tick a 0 tick juste après lui
        long maintenant = horloge + 1L;
        if (clics > 0) {
            clic = maintenant;
        }
        if (maintenant < pretA || System.currentTimeMillis() < pretMs || maintenant - sortieDebut < (long) SORTIE) {
            return;
        }
        boolean demande = maintenant - clic <= CLIC_GARDE;
        if (!demande && mc.options.keyAttack.isDown() && mc.mouseHandler.isMouseGrabbed()) {
            demande = viseRessource(mc, outil);
        }
        if (demande) {
            lancer(mc, joueur, outil, maintenant);
        }
    }

    /** Vise un gisement ou un arbre debout que cet outil récolte (le clic maintenu ne frappe rien d'autre). */
    private static boolean viseRessource(Minecraft mc, OutilRecolte outil) {
        if (!(mc.hitResult instanceof EntityHitResult h)) {
            return false;
        }
        Entity cible = h.getEntity();
        return cible instanceof Gisement && outil.casseLesGisements()
                || cible instanceof Arbre a && a.debout() && outil.coupeLesArbres();
    }

    private static void lancer(Minecraft mc, LocalPlayer joueur, OutilRecolte outil, long maintenant) {
        clic = LOIN;
        // les mods qui refusent un clic (joueur à terre, emote, cinématique...) le refusent ici aussi
        InputEvent.InteractionKeyMappingTriggered ev;
        posteIci = true;
        try {
            ev = ForgeHooksClient.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
        } finally {
            posteIci = false;
        }
        if (ev.isCanceled()) {
            return;
        }
        debut = maintenant;
        impactFait = false;
        pic = outil.legendaire() && mc.hitResult instanceof EntityHitResult h && h.getEntity() instanceof Gisement;
        pretA = maintenant + CADENCE;
        pretMs = System.currentTimeMillis() + CADENCE_MS;
        long[] c = COUPS.get(joueur);
        if (c == null) {
            COUPS.put(joueur, new long[]{maintenant, pic ? 1L : 0L});
        } else {
            c[0] = maintenant;
            c[1] = pic ? 1L : 0L;
        }
        // geste de la main gauche : les autres jeux y voient le début du coup (VueDesAutres)
        joueur.swing(InteractionHand.OFF_HAND);
    }

    /** Impact du geste : l'attaque normale de Minecraft sur ce que vise le joueur, ou rien (coup dans le vide). */
    private static void impact(Minecraft mc, LocalPlayer joueur) {
        if (mc.gameMode == null || joueur.isSpectator() || !joueur.isAlive()
                || !(joueur.getMainHandItem().getItem() instanceof OutilRecolte)) {
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult h)) {
            return;
        }
        Entity cible = h.getEntity();
        if (cible == joueur || !cible.isAlive() || !cible.isAttackable()) {
            return;
        }
        mc.gameMode.attack(joueur, cible);
        secousseDebut = horloge;
    }

    /** Outil tout juste pris en main (autre case ou autre outil) : il sort. */
    private static void suivreSortie(LocalPlayer joueur) {
        ItemStack s = joueur.getMainHandItem();
        boolean outil = s.getItem() instanceof OutilRecolte;
        int c = joueur.getInventory().selected;
        if (outil && (c != caseVue || s.getItem() != objetVu)) {
            sortieDebut = horloge;
        }
        caseVue = outil ? c : -1;
        objetVu = outil ? s.getItem() : null;
    }

    /**
     * Outil en main : Minecraft ne frappe ni ne casse rien de lui-même (bloc tenu au clic maintenu, clic venu d'ailleurs) ;
     * seuls nos coups partent, à l'impact.
     */
    private static void touche(InputEvent.InteractionKeyMappingTriggered e) {
        if (posteIci || !e.isAttack()) {
            return;
        }
        LocalPlayer joueur = Minecraft.getInstance().player;
        if (joueur != null && joueur.getMainHandItem().getItem() instanceof OutilRecolte) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    /** Petite secousse de la vue à l'impact (première personne). */
    private static void secousse(ViewportEvent.ComputeCameraAngles e) {
        if (secousseDebut == LOIN) {
            return;
        }
        float t = (float) ((double) (horloge - secousseDebut) + e.getPartialTick());
        if (t < 0.0F || t >= SECOUSSE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float a = 1.0F - t / SECOUSSE;
        a *= a * mc.options.screenEffectScale().get().floatValue();
        e.setPitch(e.getPitch() + SECOUSSE_TANGAGE * a * Mth.sin(t * 2.6F + 0.4F));
        e.setRoll(e.getRoll() + SECOUSSE_ROULIS * a * Mth.sin(t * 3.3F + 1.1F));
    }
}
