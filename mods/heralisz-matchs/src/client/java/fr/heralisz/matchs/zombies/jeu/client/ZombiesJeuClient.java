package fr.heralisz.matchs.zombies.jeu.client;

import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.api.EvenementsHud;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.MomentMusique;
import fr.heralisz.matchs.zombies.jeu.api.MotifPoints;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Cote client du jeu zombies (charge seulement sur le client physique) : traitement des paquets S2C (etat du HUD dans
 * {@link HudZombies}, evenements vers {@link HudZombies#ecouteur()}, objectif, lore d'intro, ecrans de lancement, de
 * l'etabli et de fin) et entrees :
 * <ul>
 *     <li>touche d'interaction : pendant une partie, la touche d'echange de main (F par defaut) n'echange plus rien ; son
 *     etat enfonce / relache est envoye au serveur ({@code ToucheC2S}), qui decide de tout ;</li>
 *     <li>coup de melee : chaque clic d'attaque avec une arme blanche est signale au serveur ({@code CoupC2S}) pour les
 *     zombies colles aux fenetres (les planches les cachent au ciblage normal).</li>
 * </ul>
 * Les cartes de Xaero sont masquees par le moteur pendant tout match ({@code moteur/client/CartesMasquees}).
 */
public final class ZombiesJeuClient {
    private ZombiesJeuClient() {
    }

    private static boolean enfonceeEnvoye;
    private static boolean attaqueAvant;
    private static long dernierCoup;
    private static boolean poseForcee;

    /** Appele par ZombiesJeu.construire sur le client. */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ZombiesJeuClient::tick);
        MinecraftForge.EVENT_BUS.addListener(ZombiesJeuClient::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(ZombiesJeuClient::commandes);
    }

    /**
     * Commande client {@code /zbjeu} (banc d'essai) : {@code coup} = meme chemin qu'un clic d'attaque a l'arme blanche
     * ({@code CoupC2S}) ; {@code hud} = etat client du HUD zombies dans le journal (objectif, intro, ambiance, materiaux).
     */
    private static void commandes(net.minecraftforge.client.event.RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("zbjeu")
                .then(net.minecraft.commands.Commands.literal("coup").executes(c -> {
                    dernierCoup = 0;
                    signalerCoup(Minecraft.getInstance());
                    fr.heralisz.matchs.HeraliszMatchs.LOG.info("[zombies] banc client : coup signale");
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("hud").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    HudZombies.Joueur moi = mc.player == null ? null : HudZombies.joueur(mc.player.getUUID());
                    StringBuilder intro = new StringBuilder();
                    for (Component l : HudZombies.intro()) intro.append(l.getString()).append(" | ");
                    fr.heralisz.matchs.HeraliszMatchs.LOG.info("[zombies] banc client hud : actif={} manche={} objectif={} \"{}\" ambiance={} intro=[{}] invite=\"{}\" cout={} prog={} materiaux={} kits={} plaques={} vie={}/{} absorption={}",
                            HudZombies.actif(), HudZombies.manche(), HudZombies.objectifId(), HudZombies.objectif().getString(), HudZombies.ambiance(),
                            intro, HudZombies.invite() == null ? "" : HudZombies.invite().getString(), HudZombies.inviteCout(), HudZombies.progression(),
                            moi == null ? -1 : moi.materiaux(), moi == null ? -1 : moi.autoReanimations(), moi == null ? -1 : moi.plaques(),
                            mc.player == null ? 0 : mc.player.getHealth(), mc.player == null ? 0 : mc.player.getMaxHealth(),
                            mc.player == null ? 0 : mc.player.getAbsorptionAmount());
                    return 1;
                })));
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (!HudZombies.actif() || mc.player == null) {
            if (poseForcee && mc.player != null) mc.player.setForcedPose(null);
            poseForcee = false;
            attaqueAvant = false;
            if (enfonceeEnvoye) {
                enfonceeEnvoye = false;
                if (mc.getConnection() != null) MatchsNet.versServeur(new PaquetsZombies.ToucheC2S(false));
            }
            return;
        }
        // l'echange de main ne doit jamais se declencher en partie : on consomme les appuis avant Minecraft
        while (mc.options.keySwapOffhand.consumeClick()) {
            // rien : l'etat enfonce est lu ci-dessous
        }
        boolean enfoncee = mc.screen == null && mc.options.keySwapOffhand.isDown();
        if (enfoncee != enfonceeEnvoye) {
            enfonceeEnvoye = enfoncee;
            MatchsNet.versServeur(new PaquetsZombies.ToucheC2S(enfoncee));
        }
        // coup de melee (front montant du clic d'attaque, arme blanche en main)
        boolean attaque = mc.screen == null && mc.options.keyAttack.isDown();
        if (attaque && !attaqueAvant) signalerCoup(mc);
        attaqueAvant = attaque;
        // a terre : rampe aussi cote client (la pose forcee du serveur n'est pas transmise au joueur local)
        HudZombies.Joueur moi = HudZombies.joueur(mc.player.getUUID());
        boolean aTerre = moi != null && moi.etat() == HudZombies.A_TERRE;
        if (aTerre && mc.player.getForcedPose() != Pose.SWIMMING) {
            mc.player.setForcedPose(Pose.SWIMMING);
            poseForcee = true;
        } else if (!aTerre && poseForcee) {
            mc.player.setForcedPose(null);
            poseForcee = false;
        }
    }

    /** Signale un coup de melee au serveur (arme blanche seulement ; le serveur revalide tout). */
    public static void signalerCoup(Minecraft mc) {
        if (mc.player == null || !HudZombies.actif()) return;
        ItemStack main = mc.player.getMainHandItem();
        if (main.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(main.getItem());
        if (id != null && "tacz".equals(id.getNamespace())) return;
        long now = System.currentTimeMillis();
        if (now - dernierCoup < 350) return;
        dernierCoup = now;
        MatchsNet.versServeur(new PaquetsZombies.CoupC2S());
    }

    private static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        enfonceeEnvoye = false;
        attaqueAvant = false;
        HudZombies.vider();
    }

    // ------------------------------------------------------------------ paquets
    public static void etat(PaquetsZombies.EtatS2C p) {
        HudZombies.etat(p.actif(), p.manche(), p.speciale(), p.restants(), p.courant(), p.joueurs(), p.bonus());
    }

    public static void invite(PaquetsZombies.InviteS2C p) {
        HudZombies.invite(p.texte(), p.cout(), p.abordable(), p.progression());
    }

    public static void evenement(PaquetsZombies.EvenementS2C p) {
        EvenementsHud h = HudZombies.ecouteur();
        try {
            switch (p.type()) {
                case PaquetsZombies.EvenementS2C.POINTS -> h.points(p.joueur(), p.valeur(), MotifPoints.de(p.extra()));
                case PaquetsZombies.EvenementS2C.MANCHE -> h.manche(p.valeur(), (p.extra() & 1) != 0, (p.extra() & 2) != 0);
                case PaquetsZombies.EvenementS2C.BONUS -> h.bonus(TypeBonus.de(p.extra()), p.texte());
                case PaquetsZombies.EvenementS2C.ATOUT -> h.atout(p.joueur(), TypeAtout.de(p.extra()));
                case PaquetsZombies.EvenementS2C.ETAT -> h.etatJoueur(p.joueur(), p.texte(), p.extra());
                case PaquetsZombies.EvenementS2C.REFUS -> h.refus(p.composant());
                case PaquetsZombies.EvenementS2C.ACHAT -> h.achat(p.composant(), p.valeur());
                case PaquetsZombies.EvenementS2C.COURANT -> h.courant();
                case PaquetsZombies.EvenementS2C.MUSIQUE -> {
                    MomentMusique m = MomentMusique.de(p.extra() & 0xFF);
                    // un accent (bit 0x100) ne change pas l'ambiance durable
                    if ((p.extra() & 0x100) == 0) HudZombies.ambiance(m);
                    h.musique(m, p.valeur());
                }
                case PaquetsZombies.EvenementS2C.PAP -> h.packAPunch(p.joueur(), p.composant(), Math.max(1, p.extra()));
                case PaquetsZombies.EvenementS2C.FABRICATION -> h.fabrication(p.joueur(), p.composant());
                case PaquetsZombies.EvenementS2C.MATERIAUX -> h.materiaux(p.joueur(), p.valeur());
                case PaquetsZombies.EvenementS2C.EASTER_EGG -> h.easterEgg(p.valeur(), p.extra() & 0xFFFF, (p.extra() & 0x10000) != 0);
                case PaquetsZombies.EvenementS2C.BOITE -> h.boite(Rarete.de(p.extra()), p.composant());
                case PaquetsZombies.EvenementS2C.TELEPORT -> h.teleport();
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            fr.heralisz.matchs.HeraliszMatchs.LOG.error("[zombies] ecouteur du HUD en erreur", ex);
        }
    }

    /** Langue du client : vrai si francais. */
    private static boolean francais() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("fr");
        } catch (RuntimeException ex) {
            return true;
        }
    }

    public static void objectif(PaquetsZombies.ObjectifS2C p) {
        Component c;
        if (p.id().isEmpty()) c = Component.empty();
        else if (p.fr().isEmpty() && p.en().isEmpty()) {
            // 0.2.3 : lieu de la cible (%1$s) et lettre du teleporteur (%2$s) dans le texte
            String lieu = francais() || p.lieuEn().isEmpty() ? p.lieuFr() : p.lieuEn();
            c = Component.translatable("zombies.jeu.objectif." + p.id(), lieu, p.arg());
        }
        else c = Component.literal(francais() || p.en().isEmpty() ? (p.fr().isEmpty() ? p.en() : p.fr()) : p.en());
        // v3 : progression apres le texte ((2/3), chrono de liaison), en doux
        if (!p.id().isEmpty() && !p.progres().isEmpty()) {
            c = Component.empty().append(c).append(Component.literal("  " + p.progres()).withStyle(st -> st.withColor(0xFFD27A)));
        }
        HudZombies.objectif(p.id(), c);
        HudZombies.objectifCible(p.cible());
        try {
            HudZombies.ecouteur().objectif(c, p.nouveau());
        } catch (RuntimeException ex) {
            fr.heralisz.matchs.HeraliszMatchs.LOG.error("[zombies] ecouteur du HUD en erreur", ex);
        }
    }

    public static void intro(PaquetsZombies.IntroS2C p) {
        List<String> src = francais() || p.en().isEmpty() ? (p.fr().isEmpty() ? p.en() : p.fr()) : p.en();
        List<Component> l = new ArrayList<>(3);
        if (src.isEmpty()) {
            for (int i = 1; i <= 3; i++) l.add(Component.translatable("zombies.jeu.intro." + i));
        } else {
            for (String s : src) l.add(Component.literal(s));
        }
        HudZombies.intro(l);
        try {
            HudZombies.ecouteur().intro(l);
        } catch (RuntimeException ex) {
            fr.heralisz.matchs.HeraliszMatchs.LOG.error("[zombies] ecouteur du HUD en erreur", ex);
        }
    }

    public static void lieu(PaquetsZombies.LieuS2C p) {
        String nom = francais() || p.en().isEmpty() ? (p.fr().isEmpty() ? p.en() : p.fr()) : p.en();
        if (!nom.equals(HudZombies.lieu())) HudZombies.lieu(nom);
    }

    public static void atelier(PaquetsZombies.AtelierS2C p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranAtelier e && e.atelier() == p.atelier()) e.maj(p);
        else mc.setScreen(new EcranAtelier(p));
    }

    public static void ouvrir(PaquetsZombies.OuvrirS2C p) {
        Minecraft.getInstance().setScreen(new EcranZombies(p));
    }

    public static void fin(PaquetsZombies.FinS2C p) {
        Minecraft.getInstance().setScreen(new EcranFinZombies(p));
    }
}
