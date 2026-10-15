package fr.heraliz.loot.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Petit badge du palier d'un conteneur (« T1 » … « T7 »), à côté de son nom dans l'écran de fouille : pastille sombre,
 * liseré fin et lettres de la couleur du palier, texte réduit aux trois quarts. Couleurs alignées sur les raretés des
 * objets (T1 gris Commun, T2 vert, T3 bleu, T4 violet, T5 or ; T6 orange, entre l'or et le rouge ; T7 rouge Unique).
 */
public final class BadgePalier {
    private BadgePalier() {
    }

    private static final int[] COULEURS = {0x9A9A9A, 0x9A9A9A, 0x5CBF4A, 0x3F8CFF, 0xA64DFF, 0xF2B83A, 0xFF7A2E, 0xFF3333};
    private static final int FOND = 0xC0141314, H = 8;
    private static final float ECHELLE = 0.75f;

    /** Couleur (RVB) du palier 1 à 7. */
    public static int couleur(int palier) {
        return COULEURS[palier >= 1 && palier <= 7 ? palier : 0];
    }

    /** Largeur du badge en pixels d'écran. */
    public static int largeur(Font font, int palier) {
        return (int) Math.ceil(font.width("T" + palier) * ECHELLE) + 5;
    }

    /** Dessine le badge, coin haut gauche en (x, y) ; rien hors de 1 à 7. Hauteur : 8 px (celle d'une ligne de titre). */
    public static void dessiner(GuiGraphics g, Font font, int palier, int x, int y) {
        if (palier < 1 || palier > 7) return;
        int c = couleur(palier), w = largeur(font, palier);
        g.fill(x, y, x + w, y + H, FOND);
        int bord = 0x90 << 24 | c;
        g.fill(x, y, x + w, y + 1, bord);
        g.fill(x, y + H - 1, x + w, y + H, bord);
        g.fill(x, y + 1, x + 1, y + H - 1, bord);
        g.fill(x + w - 1, y + 1, x + w, y + H - 1, bord);
        g.pose().pushPose();
        g.pose().translate(x + 2.5f, y + 1.75f, 0);
        g.pose().scale(ECHELLE, ECHELLE, 1);
        g.drawString(font, "T" + palier, 0, 0, 0xFF000000 | c, false);
        g.pose().popPose();
    }
}
