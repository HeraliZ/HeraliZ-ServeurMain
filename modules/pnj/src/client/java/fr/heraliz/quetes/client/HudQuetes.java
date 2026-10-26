package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * À l'écran, sans rien ouvrir : la quête suivie, en petit à droite (choisie avec « Suivre » dans /quetes), et les
 * notifications courtes en haut à droite (« Étape terminée », « Quête terminée : +300 hérals »).
 * Panneaux dessinés dans un repère réduit (0,7 et 0,8) ; leurs textes, eux, avec des polices à leur vraie taille (le texte
 * réduit d'avant, arrondi au demi-point), jamais agrandis ni réduits, chacun posé sur un point de l'écran.
 */
public final class HudQuetes {
    private HudQuetes() {
    }

    private static final int LARGEUR = 150;
    /** Taille du suivi à l'écran (1 = taille de l'interface du jeu : jugée beaucoup trop grosse). */
    private static final float ECHELLE = 0.7F;
    /** Taille des notifications en haut à droite. */
    private static final float ECHELLE_NOTIFS = 0.8F;
    /**
     * Polices à leur vraie taille. Suivi (avant : 11 et 9 réduits à 0,7) : titre en gras 7,5, étapes en 7,5, nom du PNJ
     * et distances en 6,5. Notifications (avant : 11 réduit à 0,8) : 9.
     */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_micro"),
            MOYEN = new ResourceLocation("heraliz_quetes", "ui_moyen"), MINI = new ResourceLocation("heraliz_quetes", "ui_mini"),
            NOTIF = Ui.SMALL;
    /**
     * Milieu des capitales des anciennes polices (11 et 9) sous le haut de leur ligne, en unités du repère réduit : chaque
     * texte garde le milieu de ses capitales à sa place d'avant.
     */
    private static final float M11 = 11 * 0.49309F - 1F, M9 = 9 * 0.49309F - 1F;
    private static Reseau.Suivi suivi = new Reseau.Suivi(fr.heraliz.quetes.Texte.VIDE, fr.heraliz.quetes.Texte.VIDE, List.of());

    /** Une notification ; ses lignes coupées à la largeur sont calculées à sa première image, pas à chaque image. */
    private static final class Notif {
        final String texte;
        final int couleur;
        final long debut;
        List<FormattedCharSequence> lignes;
        /** Largeur de la plus longue ligne, dans le repère réduit des notifications. */
        int largeur;

        Notif(String texte, int couleur, long debut) {
            this.texte = texte;
            this.couleur = couleur;
            this.debut = debut;
        }
    }

    /**
     * Mise en page de la quête suivie (lignes coupées, textes de droite, hauteur) : refaite seulement quand le serveur
     * envoie un nouveau suivi ou que la langue change. Avant, tout était recoupé (Font.split) à chaque image tant qu'une
     * quête était suivie.
     */
    private record Page(Reseau.Suivi suivi, String langue, String pnj, List<FormattedCharSequence> titre,
                        List<List<FormattedCharSequence>> lignes, List<String> droites, int hauteur) {
    }

    private static Page page;

    private static final List<Notif> NOTIFS = new ArrayList<>();
    private static final long NOTIF_MS = 4500;

    static void suivi(Reseau.Suivi s) {
        suivi = s;
    }

    static void notif(Reseau.Notif n) {
        String s = TexteClient.str(n.texte());
        if (s.isEmpty()) return;
        NOTIFS.add(new Notif(s, n.couleur(), System.currentTimeMillis()));
        while (NOTIFS.size() > 4) NOTIFS.remove(0);
        SonsUi.clic(0.35F);
    }

    static void vider() {
        suivi = new Reseau.Suivi(fr.heraliz.quetes.Texte.VIDE, fr.heraliz.quetes.Texte.VIDE, List.of());
        page = null;
        NOTIFS.clear();
    }

    static void rendu(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        if (NOTIFS.isEmpty() && suivi.titre().vide()) return;
        Font f = mc.font;
        // rien que des aplats et du texte : envoyés en un seul lot (sinon un dessin par aplat et par texte, à chaque image)
        g.drawManaged(() -> {
            g.pose().pushPose();
            g.pose().scale(ECHELLE_NOTIFS, ECHELLE_NOTIFS, 1);
            notifs(g, f, (int) (w / ECHELLE_NOTIFS));
            g.pose().popPose();
            if (suivi.titre().vide()) return;
            g.pose().pushPose();
            g.pose().scale(ECHELLE, ECHELLE, 1);
            dessinerSuivi(g, f, (int) (w / ECHELLE), (int) (h / ECHELLE));
            g.pose().popPose();
        });
    }

    private static Page page(Font f) {
        Reseau.Suivi s = suivi;
        String langue = fr.heraliz.quetes.Langue.code();
        Page p = page;
        if (p != null && p.suivi() == s && p.langue().equals(langue)) return p;
        // lignes coupées à la largeur d'avant, mesurée en points d'interface (les textes y sont à leur vraie taille)
        List<FormattedCharSequence> titre = f.split(Ui.lit(TexteClient.str(s.titre()), TITRE), (int) ((LARGEUR - 16) * ECHELLE));
        List<List<FormattedCharSequence>> lignes = new ArrayList<>();
        List<String> droites = new ArrayList<>();
        for (Reseau.SLigne l : s.lignes()) {
            String droite = "";
            if (l.total() > 1) droite = l.fait() + "/" + l.total();
            if (l.distance() >= 0) droite = (droite.isEmpty() ? "" : droite + " · ") + TexteClient.distance(l.distance(), l.approx());
            droites.add(droite);
            lignes.add(f.split(Ui.lit(TexteClient.str(l.libelle()), MOYEN), (int) ((LARGEUR - 22) * ECHELLE)));
        }
        int hauteur = 14 + titre.size() * 10 + 4;
        for (int i = 0; i < lignes.size(); i++) hauteur += lignes.get(i).size() * 10 + (droites.get(i).isEmpty() ? 0 : 9) + 3;
        p = new Page(s, langue, TexteClient.str(s.pnj()), titre, lignes, droites, hauteur);
        page = p;
        return p;
    }

    /** La quête suivie, dessinée dans un repère réduit (w, h : taille de l'écran dans ce repère). */
    private static void dessinerSuivi(GuiGraphics g, Font f, int w, int h) {

        // quête suivie : à droite, au tiers de la hauteur (mise en page gardée tant que le suivi ne change pas)
        Page page = page(f);
        Reseau.Suivi suivi = page.suivi();
        List<FormattedCharSequence> titre = page.titre();
        List<List<FormattedCharSequence>> lignes = page.lignes();
        List<String> droites = page.droites();
        int hauteur = page.hauteur();
        int x = w - LARGEUR - 6, y = Math.max(40, h / 3 - hauteur / 2);
        Ui.glass(g, x, y, LARGEUR, hauteur, 120);
        g.fill(x + 1, y + 3, x + 3, y + hauteur - 3, Ui.argb(Ui.OR, 200));
        caps(g, f, page.pnj(), MINI, ECHELLE, x + 9, y + 6 + M9, 0.8F * ECHELLE, Ui.argb(Ui.OR, 230));
        int cy = y + 16;
        for (FormattedCharSequence l : titre) {
            ligne(g, f, l, TITRE, ECHELLE, x + 9, cy + M11, Ui.argb(Ui.TEXT, 255));
            cy += 10;
        }
        cy += 4;
        for (int i = 0; i < lignes.size(); i++) {
            Reseau.SLigne l = suivi.lignes().get(i);
            // etat : 0 à surveiller (ambre), 1 en cours, 2 fait (grisé : étape d'avant du tutoriel des bases)
            int couleur = l.etat() == 0 ? Ui.AMBRE : l.etat() == 2 ? Ui.MUTED : Ui.TEXT;
            Ui.point(g, x + 9, cy + 2, l.etat() == 0 ? Ui.AMBRE : Ui.OK, 230);
            for (FormattedCharSequence s : lignes.get(i)) {
                ligne(g, f, s, MOYEN, ECHELLE, x + 18, cy + M11, Ui.argb(couleur, 235));
                cy += 10;
            }
            if (!droites.get(i).isEmpty()) {
                caps(g, f, droites.get(i), MINI, ECHELLE, x + 18, cy + M9, 0.5F * ECHELLE, Ui.argb(Ui.MUTED, 255));
                cy += 9;
            }
            if (l.total() > 1) Ui.bar(g, x + 18, cy, LARGEUR - 28, 1, l.fait() / (float) l.total(), Ui.OK);
            cy += 3;
        }
    }

    private static void notifs(GuiGraphics g, Font f, int w) {
        long maintenant = System.currentTimeMillis();
        NOTIFS.removeIf(n -> maintenant - n.debut > NOTIF_MS);
        int y = 8;
        for (int i = NOTIFS.size() - 1; i >= 0; i--) {
            Notif n = NOTIFS.get(i);
            long age = maintenant - n.debut;
            float a = age < 200 ? age / 200f : age > NOTIF_MS - 500 ? (NOTIF_MS - age) / 500f : 1f;
            if (n.lignes == null) {
                // coupées à la largeur d'avant, en points d'interface ; largeur ramenée dans le repère réduit
                n.lignes = f.split(Ui.lit(n.texte, NOTIF), (int) (190 * ECHELLE_NOTIFS));
                float plus = 0;
                for (FormattedCharSequence s : n.lignes) plus = Math.max(plus, f.getSplitter().stringWidth(s));
                n.largeur = (int) Math.ceil(plus / ECHELLE_NOTIFS);
            }
            List<FormattedCharSequence> l = n.lignes;
            int lw = n.largeur;
            int bw = lw + 20, bh = l.size() * 10 + 10, x = w - bw - 8 + (int) ((1 - a) * 20);
            Ui.glass(g, x, y, bw, bh, (int) (190 * a));
            g.fill(x + 1, y + 2, x + 3, y + bh - 2, Ui.argb(n.couleur, (int) (230 * a)));
            int cy = y + 5;
            for (FormattedCharSequence s : l) {
                ligne(g, f, s, NOTIF, ECHELLE_NOTIFS, x + 11, cy + M11, Ui.argb(Ui.TEXT, Math.max(8, (int) (255 * a))));
                cy += 10;
            }
            y += bh + 4;
        }
    }

    static String majuscules(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ texte à sa vraie taille, posé net
    /** Milieu des capitales sous le point de dessin (DM Sans du mod, décalage 1 : taille x 0,49309 - 1). */
    private static float milieu(ResourceLocation police) {
        float taille = switch (police.getPath()) {
            case "ui_mini" -> 6.5F;
            case "ui_micro", "ui_moyen" -> 7.5F;
            case "ui_small" -> 9F;
            default -> 11F;
        };
        return taille * 0.49309F - 1F;
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; comme le journal des quêtes). */
    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    /**
     * Ligne de texte à sa vraie taille, appelée dans le repère réduit de k où se dessinent les panneaux : la réduction est
     * annulée le temps du texte (la police a déjà la bonne taille). x (bord gauche) et cy (milieu des capitales) en unités
     * du repère réduit.
     */
    private static void ligne(GuiGraphics g, Font f, FormattedCharSequence l, ResourceLocation police, float k, float x, float cy, int argb) {
        g.pose().pushPose();
        g.pose().scale(1 / k, 1 / k, 1);
        placer(g, x * k, cy * k - milieu(police));
        g.drawString(f, l, 0, 0, argb, false);
        g.pose().popPose();
    }

    /**
     * Capitales espacées à leur vraie taille (même repère réduit que {@link #ligne}) ; espace entre les lettres en points
     * d'interface ; chaque lettre posée sur un point de l'écran.
     */
    private static void caps(GuiGraphics g, Font f, String s, ResourceLocation police, float k, float x, float cy, float espace, int argb) {
        String up = s.toUpperCase(Locale.ROOT);
        g.pose().pushPose();
        g.pose().scale(1 / k, 1 / k, 1);
        float cx = x * k, haut = cy * k - milieu(police);
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            Component c = Ui.lit(new String(Character.toChars(up.codePointAt(i))), police);
            g.pose().pushPose();
            placer(g, cx, haut);
            g.drawString(f, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += f.getSplitter().stringWidth(c) + espace;
        }
        g.pose().popPose();
    }
}
