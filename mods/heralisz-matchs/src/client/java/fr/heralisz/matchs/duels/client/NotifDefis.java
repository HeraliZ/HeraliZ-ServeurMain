package fr.heralisz.matchs.duels.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.client.HudMoteur;
import fr.heralisz.matchs.moteur.client.MoteurClient;
import fr.heralisz.matchs.moteur.client.TexteEspace;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Defis de duel recus : petite notification discrete en haut a droite (remplace l'ancien panneau au milieu de l'ecran,
 * qui genait en plein PvP). Aucun ecran : ni la souris ni le clavier ne sont pris, le jeu continue.
 * <p>Panneau de verre 172 px (echelle du joueur, DM Sans, charte des clans), lisere or a gauche :
 * <pre>
 *  ▌ DEFI DE DUEL                    0:24
 *  ▌ Vortex vous defie
 *  ▌ 1V1 · Match a mort · Nuketown
 *  ▌ ✓ /duel accepter   ✕ /duel refuser      (defi le plus recent seulement)
 *  ▌ ━━━━━━━━━━━━━━━━━━━━━━━━──────────      (barre fine qui se vide, rouge sous 8 s)
 * </pre>
 * Entree en glissant depuis la droite avec un fondu (220 ms), sortie de meme (200 ms) ; quand la pile change, les
 * autres glissent en douceur a leur place. Place SOUS ce qui occupe deja ce coin ({@link CoinHautDroit} : icones
 * d'effets, fil des eliminations, invitations de groupe, toasts du ClashWar, des clans et des bases).
 * <p>Plusieurs defis : le plus recent en haut, 3 affiches au plus (les suivants attendent une place). Reponses, pour le
 * plus recent : {@code /duel accepter|refuser} (comme avant), les boutons [Accepter] [Refuser] du message du chat (propres
 * a chaque defi), l'ecran {@code /duel}, et deux touches SANS touche par defaut (« Accepter / Refuser un defi de duel »,
 * a regler dans les controles, categorie HeraliZ Matchs) : une fois reglees, le rappel montre la touche au lieu de la
 * commande. Masquee avec F1, sous nos ecrans de matchs, en match et pendant une killcam. Fil client.
 */
final class NotifDefis {
    private NotifDefis() {
    }

    /** Defis affiches au plus (les suivants attendent une place). */
    static final int VISIBLES_MAX = 3;
    /** Defis gardes au plus (le serveur en garde un par joueur qui defie). */
    private static final int GARDES_MAX = 8;
    private static final int W = 172, MARGE_DROITE = 8, HAUT_MIN = 8, ECART = 5, H_TETE = 54, H_AUTRE = 42;
    private static final long ENTREE_MS = 220, SORTIE_MS = 200;
    /** En dessous : barre et temps en rouge. */
    private static final long URGENT_MS = 8_000;

    /** Accepter le defi le plus recent : aucune touche par defaut (pas de conflit), contexte « en jeu » seulement. */
    static final KeyMapping CLE_ACCEPTER = new KeyMapping("key.heralisz_matchs.defi_accepter", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.heralisz_matchs");
    /** Refuser le defi le plus recent : aucune touche par defaut. */
    static final KeyMapping CLE_REFUSER = new KeyMapping("key.heralisz_matchs.defi_refuser", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.heralisz_matchs");

    /** Une notification : textes prepares a la reception (rien n'est mis en forme a chaque image, sauf la seconde). */
    private static final class Notif {
        Vues.DefiVue defi;
        long recuMs;
        long apparueMs = -1;
        long sortieMs = -1;
        float y = Float.NaN;
        MutableComponent ligne1 = Component.empty(), ligne2 = Component.empty(), temps = Component.empty();
        int secondeMontree = -1, largeurTemps;

        Notif(Vues.DefiVue d, long now) {
            maj(d, now);
        }

        void maj(Vues.DefiVue d, long now) {
            defi = d;
            recuMs = now;
            Font font = Minecraft.getInstance().font;
            boolean groupe = d.campA().size() > 1;
            String qui = groupe ? Component.translatable("duel.defi.vous_defient", String.join(" & ", d.campA())).getString()
                    : Component.translatable("duel.defi.vous_defie", d.defiant()).getString();
            ligne1 = Ui.lit(Ui.ellipsis(font, qui, Ui.BOLD, W - 20), Ui.BOLD);
            String details = Component.translatable("duel.defi.details", d.format().toUpperCase(Locale.ROOT), Component.translatable("duel.mode." + d.mode()),
                    d.carte()).getString();
            ligne2 = Ui.lit(Ui.ellipsis(font, details, Ui.SMALL, W - 20), Ui.SMALL);
        }

        long resteMs(long now) {
            return Math.max(0, defi.resteMs() - (now - recuMs));
        }

        boolean sortante() {
            return sortieMs >= 0;
        }
    }

    /** Defis recus, le plus recent en tete. */
    private static final List<Notif> PILE = new ArrayList<>();
    private static final TexteEspace TITRE = new TexteEspace(Ui.SMALL, 1.0f);
    private static long derniereImage;
    private static int ticksRappel;
    /** Rappel des reponses : touche reglee (pastille) + mot, sinon la commande. */
    @Nullable
    private static MutableComponent toucheA, toucheR;
    private static MutableComponent texteA = Component.empty(), texteR = Component.empty();

    static void enregistrerTouches(RegisterKeyMappingsEvent e) {
        e.register(CLE_ACCEPTER);
        e.register(CLE_REFUSER);
    }

    // ------------------------------------------------------------------ etat
    /**
     * Defi recu (ou mis a jour). {@code nouveau} : vrai pour un defi qui vient d'arriver (paquet de defi), faux pour un
     * rappel (ecran DUEL) : un defi deja affiche garde alors son animation.
     */
    static void ajouter(Vues.DefiVue d, boolean nouveau) {
        long now = Util.getMillis();
        for (Notif n : PILE) {
            if (n.defi.id() != d.id()) continue;
            n.maj(d, now);
            if (nouveau && n.sortante()) n.sortieMs = -1;
            return;
        }
        PILE.add(0, new Notif(d, now));
        while (PILE.size() > GARDES_MAX) PILE.remove(PILE.size() - 1);
        TITRE.texte(Component.translatable("duel.defi.titre").getString());
        preparerRappel();
    }

    /** Fin d'un defi (accepte, refuse, annule, expire) : la notification sort en glissant. */
    static void retirer(int id) {
        long now = Util.getMillis();
        Iterator<Notif> it = PILE.iterator();
        while (it.hasNext()) {
            Notif n = it.next();
            if (n.defi.id() != id) continue;
            if (n.apparueMs < 0) it.remove();
            else if (!n.sortante()) n.sortieMs = now;
        }
    }

    /** Tous les defis tombent : en glissant ({@code anime}, debut d'un duel) ou d'un coup (deconnexion). */
    static void vider(boolean anime) {
        if (!anime) {
            PILE.clear();
            return;
        }
        long now = Util.getMillis();
        Iterator<Notif> it = PILE.iterator();
        while (it.hasNext()) {
            Notif n = it.next();
            if (n.apparueMs < 0) it.remove();
            else if (!n.sortante()) n.sortieMs = now;
        }
    }

    /** Defi le plus recent encore en attente, ou null (ecran DUEL, touches, commande). */
    @Nullable
    static Vues.DefiVue plusRecent() {
        Notif n = tete();
        return n == null ? null : n.defi;
    }

    @Nullable
    private static Notif tete() {
        for (Notif n : PILE) if (!n.sortante()) return n;
        return null;
    }

    /** Reponse au defi le plus recent (touches) : le serveur tranche, la notification sort tout de suite. */
    private static void repondre(boolean accepte) {
        Notif n = tete();
        Minecraft mc = Minecraft.getInstance();
        if (n == null || mc.getConnection() == null) return;
        MatchsNet.versServeur(new PaquetsDuels.ReponseDefiC2S(n.defi.id(), accepte));
        n.sortieMs = Util.getMillis();
        SonsUi.clic(0.5f);
    }

    // ------------------------------------------------------------------ tick
    /** Fin de tick client : touches, expiration, sorties terminees, rappel des touches (deux fois par seconde). */
    static void tick() {
        while (CLE_ACCEPTER.consumeClick()) repondre(true);
        while (CLE_REFUSER.consumeClick()) repondre(false);
        if (PILE.isEmpty()) return;
        long now = Util.getMillis();
        Iterator<Notif> it = PILE.iterator();
        while (it.hasNext()) {
            Notif n = it.next();
            if (n.sortante()) {
                if (now - n.sortieMs > SORTIE_MS) it.remove();
            } else if (n.resteMs(now) <= 0) {
                if (n.apparueMs < 0) it.remove();
                else n.sortieMs = now;
            }
        }
        if (++ticksRappel >= 10) {
            ticksRappel = 0;
            preparerRappel();
        }
    }

    /** Rappel des reponses : touche reglee par le joueur, sinon la commande (identique dans toutes les langues). */
    private static void preparerRappel() {
        if (CLE_ACCEPTER.isUnbound()) {
            toucheA = null;
            texteA = Ui.lit(DuelsConfig.COMMANDE_ACCEPTER, Ui.SMALL);
        } else {
            toucheA = Ui.lit(CLE_ACCEPTER.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT), Ui.SMALL);
            texteA = Ui.tr(Ui.SMALL, "duel.ecran.accepter");
        }
        if (CLE_REFUSER.isUnbound()) {
            toucheR = null;
            texteR = Ui.lit(DuelsConfig.COMMANDE_REFUSER, Ui.SMALL);
        } else {
            toucheR = Ui.lit(CLE_REFUSER.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT), Ui.SMALL);
            texteR = Ui.tr(Ui.SMALL, "duel.ecran.refuser");
        }
        // touches aux noms longs (« MAJ GAUCHE ») : les pastilles seules, sans les mots, pour tenir dans le panneau
        Font font = Minecraft.getInstance().font;
        if (11 + largeur(font, toucheA, texteA) + 21 + largeur(font, toucheR, texteR) > W - 20) {
            if (toucheA != null) texteA = Component.empty();
            if (toucheR != null) texteR = Component.empty();
        }
    }

    private static int largeur(Font font, @Nullable MutableComponent touche, MutableComponent texte) {
        return (touche == null ? 0 : font.width(touche) + 11) + font.width(texte);
    }

    // ------------------------------------------------------------------ rendu
    /** HUD masque : F1, nos ecrans (ils montrent le defi eux-memes), en match, pendant une killcam. */
    private static boolean masquee(Minecraft mc) {
        return mc.options.hideGui || mc.player == null || mc.screen instanceof EcranDuels || HudMoteur.ecranANous() || MoteurClient.enMatch()
                || DuelsClient.killcamActive();
    }

    static void rendre(RenderGuiEvent.Post e) {
        if (PILE.isEmpty()) {
            derniereImage = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        long dt = derniereImage == 0 ? 0 : Math.min(100, Math.max(0, now - derniereImage));
        derniereImage = now;
        if (masquee(mc)) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int bas = CoinHautDroit.bas(mc, h, now);
        int cible = bas > 0 ? Math.max(HAUT_MIN, bas + 6) : HAUT_MIN;
        // glissement vertical : ~70 ms de constante de temps, quelle que soit la cadence d'images
        float k = dt <= 0 ? 1f : 1f - (float) Math.exp(-dt / 70.0);
        int x0 = w - MARGE_DROITE - W;
        int montrees = 0;
        boolean teteVue = false;
        for (Notif n : PILE) {
            if (!n.sortante() && montrees >= VISIBLES_MAX) break;
            if (!n.sortante()) montrees++;
            if (n.apparueMs < 0) {
                n.apparueMs = now;
                n.y = cible;
            }
            boolean estTete = !teteVue && !n.sortante();
            if (estTete) teteVue = true;
            int hn = estTete ? H_TETE : H_AUTRE;
            n.y = Float.isNaN(n.y) ? cible : n.y + (cible - n.y) * k;
            dessiner(g, font, n, x0, Math.round(n.y), hn, estTete, now);
            cible += hn + ECART;
        }
    }

    private static void dessiner(GuiGraphics g, Font font, Notif n, int x0, int y, int hn, boolean tete, long now) {
        // entree : glissement depuis la droite + fondu (sortie cubique) ; sortie : l'inverse
        float te = Math.min(1f, (now - n.apparueMs) / (float) ENTREE_MS);
        float e = 1f - (1f - te) * (1f - te) * (1f - te);
        float s = n.sortante() ? Math.max(0f, 1f - (now - n.sortieMs) / (float) SORTIE_MS) : 1f;
        float a = e * s * (tete ? 1f : 0.88f);
        if (a < 0.03f) return;
        int x = x0 + Math.round((1f - e) * 18f + (1f - s) * 22f);
        int al = Math.max(4, Math.round(255 * a));
        long reste = n.resteMs(now);
        boolean urgent = reste < URGENT_MS;
        int accent = urgent ? Ui.DANGER : Ui.OR;
        // verre de la charte (fond, contour, lisere haut), tout en fondu
        Ui.soft(g, x, y, W, hn, Ui.argb(Ui.VERRE, Math.round(205 * a)));
        Ui.softOutline(g, x + 1, y + 1, W - 2, hn - 2, Ui.argb(0xFFFFFF, Math.round(26 * a)));
        g.fill(x + 2, y + 1, x + W - 2, y + 2, Ui.argb(0xFFFFFF, Math.round(41 * a)));
        g.fill(x + 1, y + 3, x + 3, y + hn - 3, Ui.argb(accent, Math.round(230 * a)));
        int tx = x + 10;
        // titre en capitales espacees, temps restant a droite
        TITRE.dessiner(g, tx, y + 6, Ui.argb(Ui.mix(Ui.OR, 0xFFFFFF, 0.25f), al));
        int sec = (int) ((reste + 999) / 1000);
        if (sec != n.secondeMontree) {
            n.secondeMontree = sec;
            n.temps = Ui.lit(sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60, Ui.SMALL);
            n.largeurTemps = font.width(n.temps);
        }
        g.drawString(font, n.temps, x + W - 9 - n.largeurTemps, y + 6, Ui.argb(urgent ? Ui.DANGER : Ui.MUTED, al), false);
        g.drawString(font, n.ligne1, tx, y + 16, Ui.argb(Ui.TEXT, al), false);
        g.drawString(font, n.ligne2, tx, y + 28, Ui.argb(Ui.MUTED, al), false);
        if (tete) rappel(g, font, tx, y + 41, a, al);
        // barre fine qui se vide (piste et remplissage en fondu avec le panneau)
        int bx = x + 9, by = y + hn - 3, bw = W - 18;
        g.fill(bx, by, bx + bw, by + 1, Ui.argb(0xFFFFFF, Math.round(22 * a)));
        int fw = Math.round(bw * Math.max(0f, Math.min(1f, reste / (float) DuelsConfig.DEFI_MS)));
        if (fw > 0) g.fill(bx, by, bx + fw, by + 1, Ui.argb(accent, Math.round(235 * a)));
    }

    /** « ✓ /duel accepter   ✕ /duel refuser », ou « ✓ [K] Accepter   ✕ [L] Refuser » avec les touches reglees. */
    private static void rappel(GuiGraphics g, Font font, int x, int y, float a, int al) {
        Icons.draw(g, Icons.COCHE, x, y - 1, 8, Ui.OK, al);
        x = segment(g, font, x + 11, y, toucheA, texteA, a, al);
        Icons.draw(g, Icons.CROIX, x + 10, y - 1, 8, Ui.DANGER, al);
        segment(g, font, x + 21, y, toucheR, texteR, a, al);
    }

    /** Pastille de touche (si reglee) puis texte ; rend le bord droit. */
    private static int segment(GuiGraphics g, Font font, int x, int y, @Nullable MutableComponent touche, MutableComponent texte, float a, int al) {
        if (touche != null) {
            int tw = font.width(touche) + 7;
            Ui.soft(g, x, y - 2, tw, 11, Ui.argb(0xFFFFFF, Math.round(28 * a)));
            Ui.softOutline(g, x + 1, y - 1, tw - 2, 9, Ui.argb(0xFFFFFF, Math.round(60 * a)));
            g.drawString(font, touche, x + 4, y, Ui.argb(Ui.TEXT, al), false);
            x += tw + 4;
        }
        g.drawString(font, texte, x, y, Ui.argb(touche != null ? Ui.MUTED : Ui.mix(Ui.TEXT, Ui.MUTED, 0.35f), al), false);
        return x + font.width(texte);
    }

    /** Resume pour le journal du banc d'essai. */
    static String etat() {
        StringBuilder sb = new StringBuilder();
        long now = Util.getMillis();
        for (Notif n : PILE) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append('#').append(n.defi.id()).append(' ').append(n.defi.defiant()).append(' ').append(n.resteMs(now) / 1000).append("s")
                    .append(n.sortante() ? " (sortie)" : "").append(" y ").append(Float.isNaN(n.y) ? -1 : Math.round(n.y));
        }
        return sb.length() == 0 ? "aucun defi" : sb.toString();
    }
}
