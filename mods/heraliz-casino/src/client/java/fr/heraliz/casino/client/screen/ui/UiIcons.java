package fr.heraliz.casino.client.screen.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Petites icones de l'interface dessinees pixel par pixel (nettes a toutes les echelles d'interface) : haut-parleur
 * (et coupe), agrandir l'ecran (coins vers l'exterieur) / revenir (coins vers l'interieur).
 */
public final class UiIcons {
    private UiIcons() {
    }

    public static final int SPEAKER = 0, MUTED = 1, EXPAND = 2, SHRINK = 3;

    private static final String[][] ICONS = {
            {
                    "....#.....",
                    "...##..#..",
                    "#####...#.",
                    "#####.#.#.",
                    "#####.#.#.",
                    "#####...#.",
                    "...##..#..",
                    "....#.....",
            },
            {
                    "....#.....",
                    "...##.....",
                    "#####.#..#",
                    "#####..##.",
                    "#####..##.",
                    "#####.#..#",
                    "...##.....",
                    "....#.....",
            },
            {
                    "###....###",
                    "##......##",
                    "#.#....#.#",
                    "..........",
                    "..........",
                    "#.#....#.#",
                    "##......##",
                    "###....###",
            },
            {
                    "#.#....#.#",
                    ".##....##.",
                    "###....###",
                    "..........",
                    "..........",
                    "###....###",
                    ".##....##.",
                    "#.#....#.#",
            },
    };

    /** Dessine l'icone centree dans le rectangle (x, y, w, h), couleur 0xRRGGBB, avec une ombre d'un pixel. */
    public static void draw(GuiGraphics g, int icon, int x, int y, int w, int h, int rgb) {
        String[] rows = ICONS[icon];
        int iw = rows[0].length(), ih = rows.length;
        int ox = x + (w - iw) / 2, oy = y + (h - ih) / 2;
        for (int pass = 0; pass < 2; pass++) {
            int col = pass == 0 ? 0xA0000000 : 0xFF000000 | rgb;
            int d = pass == 0 ? 1 : 0;
            for (int j = 0; j < ih; j++) {
                String row = rows[j];
                for (int i = 0; i < iw; i++) {
                    if (row.charAt(i) == '#') g.fill(ox + i + d, oy + j + d, ox + i + d + 1, oy + j + d + 1, col);
                }
            }
        }
    }
}
