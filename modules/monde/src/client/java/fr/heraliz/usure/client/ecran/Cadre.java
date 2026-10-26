package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Pieces communes des ecrans du mod (style des ecrans de clan) : panneau de verre, titre, filets, croix de fermeture. */
final class Cadre {
    private Cadre() {
    }

    static boolean dedans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Fond, panneau de verre, titre en capitales espacees, sous-titre, filet sous l'en-tete. */
    static void panneau(GuiGraphics g, Font font, int ecranL, int ecranH, int x, int y, int w, int h, String titre, String sousTitre) {
        Ui.fond(g, ecranL, ecranH);
        Ui.glass(g, x, y, w, h);
        Ui.spaced(g, font, titre, Ui.BOLD, x + Ui.MARGE, y + 14, Ui.argb(Ui.TEXT, 255), 1.6f);
        if (sousTitre != null && !sousTitre.isEmpty()) {
            g.drawString(font, Ui.lit(Ui.ellipsis(font, sousTitre, Ui.SMALL, w - 2 * Ui.MARGE - 20), Ui.SMALL), x + Ui.MARGE, y + 28,
                    Ui.argb(Ui.MUTED, 255), false);
        }
        Ui.hairline(g, x + Ui.MARGE, x + w - Ui.MARGE, y + 39, 0xFFFFFF, 36, false, true);
    }

    /** Croix de fermeture en haut a droite ; rend vrai si la souris est dessus. */
    static boolean fermer(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean over = dedans(mouseX, mouseY, x - 3, y - 3, 13, 13);
        int c = Ui.argb(over ? Ui.TEXT : Ui.MUTED, 255);
        for (int i = 0; i < 7; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, c);
        }
        return over;
    }

    /** Fond d'une ligne de reglage (plus clair au survol). */
    static void ligne(GuiGraphics g, int x, int y, int w, int h, boolean survol) {
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, survol ? 12 : 5));
    }
}
