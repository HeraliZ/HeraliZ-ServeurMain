package fr.heralisz.clans.client.gui.hud;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClansClient;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.ClientHandlers;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.network.Packets;
import fr.heraliz.noyau.client.Bandeaux;
import fr.heraliz.noyau.client.kit.Bandeau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * HUD du mod, a l'echelle du joueur (lisible a l'echelle 4 : 480 x 270), dans le style commun des messages a l'ecran
 * (18/11, maquette validee {@code maquettes/notifications/} : {@link Bandeau} du noyau) :
 * <ul>
 * <li>avis du clan (invitation, capture, quete, niveau, zone perdue, ravitaillement, erreur) : bandeaux communs a droite
 * de l'ecran ({@link Bandeaux}), 6 s pour une invitation, 4 s sinon ;</li>
 * <li>petit panneau de zone en haut a gauche : ville en titre, a qui elle est et ou est le drapeau dessous, etat de
 * capture et barre fine (CAPTURE accent, CONTESTE rouge, DEFENSE ambre, PROTEGEE, LIMITE).</li>
 * </ul>
 * Aucun tick : tout est calcule au rendu ; rien n'est alloue par image (textes recalcules seulement s'ils changent).
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class ClanHud {
    private ClanHud() {
    }

    // ------------------------------------------------------------------ avis du clan
    /** Avis du clan : bandeau commun a droite de l'ecran (titre, texte, puis l'aide d'une invitation). */
    public static void toast(Packets.NoticeS2C n) {
        String titre = Component.translatable("gui.toast." + n.genre().name().toLowerCase(Locale.ROOT)).getString();
        // ERREUR / INFO : cle + arguments envoyes par le serveur, traduits ici (ClientHandlers.texteNotice)
        boolean libre = n.genre() == Packets.NoticeS2C.Genre.ERREUR || n.genre() == Packets.NoticeS2C.Genre.INFO;
        String texte = libre ? Component.translatable(n.genre().libelle(), ClientHandlers.texteNotice(n.args())).getString()
                : Component.translatable(n.genre().libelle(), n.args().toArray()).getString();
        int accent;
        switch (n.genre()) {
            case INVITATION -> {
                texte = texte + "\n" + Component.translatable("notice.invitation.sous").getString();
                accent = n.banniere() != null ? n.banniere().accent() : Ui.ACCENT_NEUTRE;
            }
            case CAPTURE, RAVITAILLEMENT -> accent = n.banniere() != null ? n.banniere().accent() : ClientClans.accent();
            case QUETE_FINIE -> accent = Ui.OK;
            case NIVEAU -> accent = Ui.OR;
            case ZONE_PERDUE, ERREUR -> accent = Ui.DANGER;
            default -> accent = ClientClans.accent();
        }
        Bandeaux.ajouter(titre, texte, accent, n.genre() == Packets.NoticeS2C.Genre.INVITATION ? 6 : 4);
    }

    /** Deconnexion : rien a garder ici (les bandeaux sont vides par le noyau). */
    public static void vider() {
        ZONE_CLE[0] = null;
        dernierEtat = null;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || ClansClient.ecranANous(mc.screen)) return;
        // 25/10 (audit en jeu) : rien sous un écran plein écran (quêtes, warps, créateur, mort...) : seuls le chat et les
        // inventaires / coffres laissent voir le panneau de zone (même règle que EcransPleins de heraliz_interface)
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)
                && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return;
        if (ClientClans.hudZone() != null) barreZone(e.getGuiGraphics());
    }

    // ------------------------------------------------------------------ panneau de zone (haut a gauche)
    /** Le panneau de zone, dans le style commun ; ses textes ne sont refaits que s'ils changent. */
    private static final Bandeau ZONE = new Bandeau();
    /** Textes du panneau tels que poses (ville, ligne, etat) : rien n'est refait tant qu'ils ne changent pas. */
    private static final String[] ZONE_CLE = new String[3];

    private static java.lang.reflect.Method purge;
    private static boolean purgeCherche;

    /**
     * Hauteur de l'icone de la Purge de CRac, posee au coin en haut a gauche (25/10, {@code PurgeHud.hauteur()} par
     * reflexion) : le panneau de zone se pose dessous. 0 sans CRac ou hors Purge.
     */
    private static int hauteurPurge() {
        if (!purgeCherche) {
            purgeCherche = true;
            try {
                purge = Class.forName("fr.heraliz.crac.client.PurgeHud").getMethod("hauteur");
            } catch (ReflectiveOperationException | LinkageError ex) {
                purge = null;
            }
        }
        try {
            return purge == null ? 0 : (int) purge.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0;
        }
    }

    /**
     * Petit panneau en haut a gauche, tant que le joueur est dans une zone : nom de la ville en titre, puis a qui elle est
     * (libre, votre zone, tenue par [TAG]) et ou est le drapeau (« drapeau a 45 m », « au drapeau », « pas de drapeau ») ;
     * pendant une capture, une defense, une contestation ou la protection : l'etat et une barre fine.
     */
    private static void barreZone(GuiGraphics g) {
        Packets.TerritoireHudS2C z = ClientClans.hudZone();
        if (z == null) return;
        boolean mienne = ClientClans.snapshot() != null && !z.proprioTag().isEmpty() && ClientClans.snapshot().tag().equalsIgnoreCase(z.proprioTag());
        // 21/11 (audit) : les textes ne sont refaits qu'à l'arrivée d'un nouvel état de la zone (le serveur ne l'envoie que
        // s'il change), d'un autre clan ou d'une autre langue ; avant, trois ou quatre textes traduits et mis en forme à
        // chaque image, puis comparés, tant que le joueur était dans une zone
        String langue = Minecraft.getInstance().getLanguageManager().getSelected();
        if (z != dernierEtat || mienne != dernierMienne || !langue.equals(derniereLangue)) {
            dernierEtat = z;
            dernierMienne = mienne;
            derniereLangue = langue;
            preparer(z, mienne);
        }
        ZONE.accent(z.proprioTag().isEmpty() ? Ui.ACCENT_NEUTRE : z.couleur()).barre(ratioZone, couleurZone);
        ZONE.dessiner(g, 5f, 5f + hauteurPurge(), ZONE.largeur(), 1f);
    }

    /** État de la zone (paquet du serveur), clan et langue des textes posés dans le panneau. */
    private static Packets.TerritoireHudS2C dernierEtat;
    private static boolean dernierMienne;
    private static String derniereLangue = "";
    /** Barre fine du panneau : avancement (-1 : aucune) et couleur. */
    private static float ratioZone = -1f;
    private static int couleurZone = Ui.MUTED;

    /** Textes et barre du panneau pour cet état de la zone (seulement quand il change). */
    private static void preparer(Packets.TerritoireHudS2C z, boolean mienne) {
        EtatZone e = z.etat();
        // a qui est la zone
        String proprio;
        if (z.proprioTag().isEmpty()) proprio = Component.translatable("gui.hud.zone_libre").getString();
        else if (mienne) proprio = Component.translatable("gui.hud.votre_zone").getString();
        else proprio = Component.translatable("gui.hud.tenue_par", z.proprioTag()).getString();
        // etat du moment (rien quand la zone est calme)
        String etat = null;
        int couleur = Ui.MUTED;
        float ratio = -1f;
        switch (e) {
            case CAPTURE -> {
                couleur = ClientClans.snapshot() != null && ClientClans.snapshot().tag().equalsIgnoreCase(z.capteurTag()) ? ClientClans.accent() : Ui.AMBRE;
                etat = z.capteurTag().isEmpty() ? Component.translatable("gui.hud.capture", Math.round(z.progression() * 100)).getString()
                        : Component.translatable("gui.hud.capture_par", z.capteurTag(), Math.round(z.progression() * 100)).getString();
                ratio = z.progression();
            }
            case CONTESTE -> {
                couleur = Ui.DANGER;
                etat = Component.translatable("gui.hud.conteste").getString();
                ratio = z.progression();
            }
            case DEFENSE -> {
                couleur = Ui.AMBRE;
                etat = Component.translatable("gui.hud.defense", Math.round(z.progression() * 100)).getString();
                ratio = z.progression();
            }
            case PROTEGEE -> {
                couleur = z.proprioTag().isEmpty() ? Ui.ACCENT_NEUTRE : z.couleur();
                // 27/10 : protegee toute la tenue (2 h) : reste en « 1 h 52 » ou « 12 min » (arrondi a la minute par le serveur)
                etat = Component.translatable("gui.hud.protegee", Ui.duree(Math.max(60_000L, z.protegeeMs()))).getString();
                ratio = 1f;
            }
            case LIMITE -> {
                couleur = Ui.DANGER;
                etat = Component.translatable("gui.hud.limite").getString();
            }
            case VERROUILLEE -> {
                // 31/10 : clan avant le niveau 5 ou sans l'amelioration Territoires (le chat dit lequel)
                couleur = Ui.AMBRE;
                etat = Component.translatable("gui.hud.verrouillee").getString();
            }
            default -> {
            }
        }
        String drapeau;
        if (z.drapeau() == -2) drapeau = Component.translatable("gui.hud.sans_drapeau").getString();
        else if (z.drapeau() == -1) drapeau = Component.translatable("gui.hud.au_drapeau").getString();
        else drapeau = Component.translatable("gui.hud.drapeau_distance", z.drapeau()).getString();
        String ligne = proprio + " · " + drapeau;
        String cleEtat = etat == null ? "" : etat;
        if (!z.zone().equals(ZONE_CLE[0]) || !ligne.equals(ZONE_CLE[1]) || !cleEtat.equals(ZONE_CLE[2])) {
            ZONE_CLE[0] = z.zone();
            ZONE_CLE[1] = ligne;
            ZONE_CLE[2] = cleEtat;
            ZONE.titre(z.zone()).ligne(0, ligne, mienne ? Ui.OK : Bandeau.ATTENUE);
            if (etat != null) ZONE.ligne(1, etat, Ui.mix(couleur, 0xFFFFFF, 0.2f));
            else ZONE.lignes(1);
        }
        ratioZone = ratio;
        couleurZone = couleur;
    }
}
