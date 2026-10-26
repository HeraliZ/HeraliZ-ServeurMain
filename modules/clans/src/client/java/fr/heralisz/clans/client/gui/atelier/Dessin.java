package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Petits elements dessines de l'atelier (aucune allocation) : palette de 16 couleurs, puces a bascule, apercu d'un
 * style de degrade, couleur contrastee pour une nouvelle forme.
 */
public final class Dessin {
    private Dessin() {
    }

    /** Palette de l'editeur : 12 tons profonds / desatures + brique, ocre, jade, bleu acier. */
    public static final int[] PALETTE = {
            0x15161A, 0x1B1F2A, 0x2A1B3D, 0x1E2F26, 0x2E3A4A, 0x3A2A1E, 0x5B2D2D, 0x2D4A4A,
            0x4A3B2E, 0x6E6A5E, 0x8A7F6A, 0xECE6D6, 0xB8402E, 0xC98B2A, 0x3F7F6B, 0x4A6FA5};
    /** Case de la palette (12 px) et pas (14 px) : 16 cases = 222 px. */
    public static final int CASE = 12, PAS = 14;
    /** Prefixe du champ hexa. */
    public static final Component DIESE = Ui.lit("#", Ui.FONT);

    public static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Palette sur une ligne a partir de (x, y) ; la couleur courante est cerclee de blanc. */
    public static void palette(GuiGraphics g, int x, int y, int courant, int mx, int my) {
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = x + i * PAS;
            boolean sel = PALETTE[i] == (courant & 0xFFFFFF), hover = dans(mx, my, sx, y, CASE, CASE);
            Ui.soft(g, sx, y, CASE, CASE, Ui.argb(PALETTE[i], 255));
            if (sel) Ui.softOutline(g, sx, y, CASE, CASE, Ui.argb(0xFFFFFF, 230));
            else if (hover) Ui.softOutline(g, sx, y, CASE, CASE, Ui.argb(0xFFFFFF, 110));
            else Ui.softOutline(g, sx + 1, y + 1, CASE - 2, CASE - 2, Ui.argb(0xFFFFFF, 16));
        }
    }

    /** Index de la case de palette sous la souris, -1 sinon. */
    public static int paletteSous(int x, int y, double mx, double my) {
        if (my < y || my >= y + CASE || mx < x) return -1;
        int i = (int) ((mx - x) / PAS);
        if (i < 0 || i >= PALETTE.length || mx - x - i * PAS >= CASE) return -1;
        return i;
    }

    /**
     * Puce a bascule (cible de couleur, epaisseur de contour, angle) : pleine a l'accent si active, creuse sinon,
     * grisee si inactive. Texte en petites capitales espacees centre.
     */
    public static void puce(GuiGraphics g, Font font, String texte, int x, int y, int w, int h, boolean active, boolean survol, boolean grisee,
                            int accent) {
        int fond, bord, txt;
        if (grisee) {
            fond = Ui.argb(0xFFFFFF, 3);
            bord = Ui.argb(0xFFFFFF, 16);
            txt = Ui.argb(Ui.FAINT, 255);
        } else if (active) {
            fond = Ui.argb(accent, survol ? 70 : 50);
            bord = Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.2f), 220);
            txt = 0xFFFFFFFF;
        } else {
            fond = Ui.argb(0xFFFFFF, survol ? 16 : 6);
            bord = Ui.argb(0xFFFFFF, survol ? 90 : 34);
            txt = Ui.argb(survol ? Ui.TEXT : Ui.MUTED, 255);
        }
        Ui.soft(g, x, y, w, h, fond);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, bord);
        Ui.spacedCentered(g, font, texte, Ui.SMALL, x + w / 2f, y + (h - 6) / 2f, txt, 0.6f);
    }

    /** Petit apercu d'un style de degrade avec les couleurs a et b (UNI = a seule). */
    public static void apercuDegrade(GuiGraphics g, Banniere.Degrade d, int ca, int cb, int x, int y, int w, int h) {
        int a = Ui.argb(ca, 255), b = Ui.argb(cb, 255);
        switch (d) {
            case VERTICAL -> g.fillGradient(x, y, x + w, y + h, a, b);
            case DIAGONAL -> {
                int bandes = 6;
                for (int i = 0; i < bandes; i++) {
                    float t0 = i / (float) bandes, t1 = (i + 1) / (float) bandes;
                    int c0 = Ui.argb(Ui.mix(ca, cb, t0 * 0.5f), 255), c1 = Ui.argb(Ui.mix(ca, cb, 0.5f + t1 * 0.5f), 255);
                    g.fillGradient(x + w * i / bandes, y, x + w * (i + 1) / bandes, y + h, c0, c1);
                }
            }
            case RADIAL -> {
                g.fill(x, y, x + w, y + h, b);
                for (int i = 3; i >= 0; i--) {
                    int c = Ui.argb(Ui.mix(ca, cb, i / 3f), 255);
                    int ix = Math.round(w / 2f * (1 - (i + 1) / 4f)), iy = Math.round(h / 2f * (1 - (i + 1) / 4f));
                    g.fill(x + ix, y + iy, x + w - ix, y + h - iy, c);
                }
            }
            case PARTAGE -> {
                g.fill(x, y, x + w / 2, y + h, a);
                g.fill(x + w / 2, y, x + w, y + h, b);
            }
            case UNI -> g.fill(x, y, x + w, y + h, a);
        }
    }

    /** Luminance percue 0..1. */
    public static float luminance(int rgb) {
        return (0.299f * ((rgb >> 16) & 255) + 0.587f * ((rgb >> 8) & 255) + 0.114f * (rgb & 255)) / 255f;
    }

    /** Couleur claire ou sombre bien lisible sur le fond de la banniere (nouvelle forme). */
    public static int contraste(Banniere b) {
        int fond = b.degrade == Banniere.Degrade.UNI ? b.couleurA : Ui.mix(b.couleurA, b.couleurB, 0.5f);
        return luminance(fond) > 0.55f ? 0x15161A : 0xF2F4F8;
    }
}
