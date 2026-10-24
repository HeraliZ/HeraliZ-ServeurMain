package fr.heralisz.clans.client.gui.hud;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClansClient;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.ClientHandlers;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.network.Packets;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * HUD du mod, a l'echelle du joueur (lisible a l'echelle 4 : 480 x 270) :
 * <ul>
 * <li>toasts en haut a droite (verre, liseré d'accent, titre en capitales espacees, texte, sous-texte) : pile de 3,
 * 6 s pour une invitation (avec sa banniere), 4 s sinon, fondu d'entree 250 ms et de sortie 500 ms ;</li>
 * <li>petit panneau de zone en haut a gauche : ville, proprietaire, distance du drapeau, etat de capture et barre
 * (CAPTURE accent, CONTESTE rouge, DEFENSE ambre, PROTEGEE, LIMITE).</li>
 * </ul>
 * Aucun tick : tout est calcule au rendu a partir de l'horloge ; rien n'est alloue par image hors la liste des toasts.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class ClanHud {
    private ClanHud() {
    }

    private static final int TOAST_W = 156, PILE_MAX = 3;
    private static final long FONDU_IN = 250, FONDU_OUT = 500;

    /**
     * Un toast : textes mis en forme une fois a la reception (texte coupe en deux lignes au plus a la largeur utile,
     * la seconde terminee par « … » si besoin), rien n'est recalcule a l'image.
     */
    private record Toast(Packets.NoticeS2C notice, long depuis, long duree, int accent, String titre, Component ligne1,
                         @Nullable Component ligne2, @Nullable Component sous) {
        float alpha(long now) {
            long age = now - depuis;
            if (age < FONDU_IN) return age / (float) FONDU_IN;
            long reste = depuis + duree - now;
            if (reste < FONDU_OUT) return Math.max(0, reste / (float) FONDU_OUT);
            return 1;
        }

        boolean fini(long now) {
            return now >= depuis + duree;
        }

        int hauteur() {
            int h = ligne2 != null ? 40 : 30;
            return sous != null ? h + 6 : h;
        }
    }

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static final List<Toast> EN_ATTENTE = new ArrayList<>();

    // ------------------------------------------------------------------ toasts
    /** Empile un toast (3 visibles au plus ; les suivants attendent une place). */
    public static void toast(Packets.NoticeS2C n) {
        long now = Util.getMillis();
        String titre = Component.translatable("gui.toast." + n.genre().name().toLowerCase(Locale.ROOT)).getString();
        // ERREUR / INFO : cle + arguments envoyes par le serveur, traduits ici (ClientHandlers.texteNotice)
        boolean libre = n.genre() == Packets.NoticeS2C.Genre.ERREUR || n.genre() == Packets.NoticeS2C.Genre.INFO;
        String texte = libre ? Component.translatable(n.genre().libelle(), ClientHandlers.texteNotice(n.args())).getString()
                : Component.translatable(n.genre().libelle(), n.args().toArray()).getString();
        Component sous = null;
        int accent;
        switch (n.genre()) {
            case INVITATION -> {
                sous = Ui.lit(Component.translatable("notice.invitation.sous").getString(), Ui.SMALL);
                accent = n.banniere() != null ? n.banniere().accent() : Ui.ACCENT_NEUTRE;
            }
            case CAPTURE, RAVITAILLEMENT -> accent = n.banniere() != null ? n.banniere().accent() : ClientClans.accent();
            case QUETE_FINIE -> accent = Ui.OK;
            case NIVEAU -> accent = Ui.OR;
            case ZONE_PERDUE, ERREUR -> accent = Ui.DANGER;
            default -> accent = ClientClans.accent();
        }
        long duree = n.genre() == Packets.NoticeS2C.Genre.INVITATION ? 6000 : n.genre() == Packets.NoticeS2C.Genre.ERREUR ? 3500 : 4000;
        // largeur utile la plus etroite (toast avec banniere : texte a x + 30)
        String[] l = couper(Minecraft.getInstance().font, texte, TOAST_W - 8 - 30);
        Toast t = new Toast(n, now, duree, accent, titre, Ui.lit(l[0], Ui.FONT), l[1] == null ? null : Ui.lit(l[1], Ui.FONT), sous);
        if (TOASTS.size() < PILE_MAX) TOASTS.addLast(t);
        else EN_ATTENTE.add(t);
    }

    /** Coupe le texte au dernier espace qui tient dans la largeur : deux lignes au plus, la seconde abregee si besoin. */
    private static String[] couper(Font font, String texte, int largeur) {
        if (font.width(Ui.lit(texte, Ui.FONT)) <= largeur) return new String[]{texte, null};
        int coupe = -1;
        for (int i = texte.indexOf(' '); i > 0; i = texte.indexOf(' ', i + 1)) {
            if (font.width(Ui.lit(texte.substring(0, i), Ui.FONT)) > largeur) break;
            coupe = i;
        }
        if (coupe < 0) return new String[]{Ui.ellipsis(font, texte, Ui.FONT, largeur), null};
        return new String[]{texte.substring(0, coupe), Ui.ellipsis(font, texte.substring(coupe + 1).trim(), Ui.FONT, largeur)};
    }

    /** Vide la pile (deconnexion). */
    public static void vider() {
        TOASTS.clear();
        EN_ATTENTE.clear();
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || ClansClient.ecranANous(mc.screen)) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth();
        Font font = mc.font;
        // panneau de zone : rien que des aplats et du texte, envoyés en un seul lot (sinon un dessin par aplat, par texte
        // et par lettre des capitales espacées, à chaque image tant qu'on est dans une ville)
        if (ClientClans.hudZone() != null) g.drawManaged(() -> barreZone(g, font, w));
        toasts(g, font, w);
    }

    private static void toasts(GuiGraphics g, Font font, int w) {
        long now = Util.getMillis();
        TOASTS.removeIf(t -> t.fini(now));
        while (TOASTS.size() < PILE_MAX && !EN_ATTENTE.isEmpty()) {
            Toast t = EN_ATTENTE.remove(0);
            TOASTS.addLast(new Toast(t.notice, now, t.duree, t.accent, t.titre, t.ligne1, t.ligne2, t.sous));
        }
        int y = 8;
        int x = w - 8 - TOAST_W;
        for (Toast t : TOASTS) {
            float a = t.alpha(now);
            int h = t.hauteur();
            // glissement depuis la droite a l'entree
            int dx = Math.round((1 - Math.min(1, (now - t.depuis) / (float) FONDU_IN)) * 12);
            dessinerToast(g, font, t, x + dx, y, h, a);
            y += h + 6;
        }
    }

    private static void dessinerToast(GuiGraphics g, Font font, Toast t, int x, int y, int h, float a) {
        int al = Math.round(255 * a);
        if (al < 6) return;
        Ui.soft(g, x, y, TOAST_W, h, Ui.argb(Ui.VERRE, Math.round(215 * a)));
        Ui.softOutline(g, x + 1, y + 1, TOAST_W - 2, h - 2, Ui.argb(0xFFFFFF, Math.round(26 * a)));
        g.fill(x + 1, y + 2, x + 3, y + h - 2, Ui.argb(t.accent, Math.round(230 * a)));
        int tx = x + 10;
        Banniere b = t.notice.banniere();
        if (b != null) {
            BannerArt.draw(g, b, x + 9, y + (h - 20) / 2, 15, 20, a);
            tx = x + 30;
        } else {
            int icone = switch (t.notice.genre()) {
                case QUETE_FINIE -> Icons.COCHE;
                case NIVEAU -> Icons.ETOILE;
                case CAPTURE, ZONE_PERDUE -> Icons.DRAPEAU;
                case RAVITAILLEMENT -> Icons.FLECHE;
                case ERREUR -> Icons.CROIX;
                default -> Icons.INFO;
            };
            Icons.draw(g, icone, x + 9, y + (h - 12) / 2, 12, t.accent, al);
            tx = x + 27;
        }
        Ui.spaced(g, font, t.titre, Ui.SMALL, tx, y + 6, Ui.argb(t.accent, al), 0.8f);
        g.drawString(font, t.ligne1, tx, y + 14, Ui.argb(Ui.TEXT, al), false);
        if (t.ligne2 != null) g.drawString(font, t.ligne2, tx, y + 24, Ui.argb(Ui.TEXT, al), false);
        if (t.sous != null) g.drawString(font, t.sous, tx, y + (t.ligne2 != null ? 35 : 25), Ui.argb(Ui.MUTED, al), false);
    }

    // ------------------------------------------------------------------ panneau de zone (haut a gauche)
    /**
     * Petit panneau de verre en haut a gauche, tant que le joueur est dans une zone : nom de la ville, a qui elle est
     * (libre, votre zone, tenue par [TAG]), ou est le drapeau (« Drapeau a 45 m », « Au drapeau », « Pas de drapeau »)
     * et, pendant une capture, une defense, une contestation ou la protection, l'etat et une barre fine.
     */
    private static void barreZone(GuiGraphics g, Font font, int w) {
        Packets.TerritoireHudS2C z = ClientClans.hudZone();
        if (z == null) return;
        EtatZone e = z.etat();
        boolean mienne = ClientClans.snapshot() != null && !z.proprioTag().isEmpty() && ClientClans.snapshot().tag().equalsIgnoreCase(z.proprioTag());
        // a qui est la zone
        int couleurProprio;
        String proprio;
        if (z.proprioTag().isEmpty()) {
            couleurProprio = Ui.MUTED;
            proprio = Component.translatable("gui.hud.zone_libre").getString();
        } else if (mienne) {
            couleurProprio = Ui.OK;
            proprio = Component.translatable("gui.hud.votre_zone").getString();
        } else {
            couleurProprio = Ui.mix(z.couleur(), 0xFFFFFF, 0.25f);
            proprio = Component.translatable("gui.hud.tenue_par", z.proprioTag()).getString();
        }
        // etat du moment (rien quand la zone est calme)
        String etat = null;
        int couleur = Ui.MUTED;
        float ratio = 0;
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
                etat = Component.translatable("gui.hud.protegee", Math.max(1, (z.protegeeMs() + 59_999) / 60_000)).getString();
                ratio = 1;
            }
            case LIMITE -> {
                couleur = Ui.DANGER;
                etat = Component.translatable("gui.hud.limite").getString();
            }
            default -> {
            }
        }
        String drapeau;
        int couleurDrapeau;
        if (z.drapeau() == -2) {
            drapeau = Component.translatable("gui.hud.sans_drapeau").getString();
            couleurDrapeau = Ui.FAINT;
        } else if (z.drapeau() == -1) {
            drapeau = Component.translatable("gui.hud.au_drapeau").getString();
            couleurDrapeau = Ui.OK;
        } else {
            drapeau = Component.translatable("gui.hud.drapeau_distance", z.drapeau()).getString();
            couleurDrapeau = Ui.MUTED;
        }
        int x = 6, y = 6, pw = 118;
        int ph = etat == null ? 34 : 50;
        Ui.glass(g, x, y, pw, ph);
        g.fill(x + 2, y + 3, x + 4, y + ph - 3, Ui.argb(z.proprioTag().isEmpty() ? Ui.ACCENT_NEUTRE : z.couleur(), 220));
        int tx = x + 8;
        Ui.spaced(g, font, z.zone(), Ui.BOLD, tx, y + 5, Ui.argb(Ui.TEXT, 255), 0.8f);
        g.drawString(font, Ui.lit(proprio, Ui.SMALL), tx, y + 15, Ui.argb(couleurProprio, 255), false);
        g.drawString(font, Ui.lit(drapeau, Ui.SMALL), tx, y + 24, Ui.argb(couleurDrapeau, 255), false);
        if (etat != null) {
            int c2 = Ui.mix(couleur, 0xFFFFFF, 0.2f);
            g.drawString(font, Ui.lit(etat, Ui.SMALL), tx, y + 34, Ui.argb(c2, 255), false);
            Ui.bar(g, tx, y + 43, pw - 16, 2, ratio, couleur);
            if (e == EtatZone.CONTESTE) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(Util.getMillis() / 180.0));
                g.fill(tx, y + 43, tx + pw - 16, y + 45, Ui.argb(Ui.DANGER, Math.round(60 * pulse)));
            }
        }
    }
}
