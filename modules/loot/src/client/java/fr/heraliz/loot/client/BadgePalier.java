package fr.heraliz.loot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Petit badge du palier d'un conteneur (« T1 » … « T7 »), à côté de son nom dans l'écran de fouille : pastille sombre,
 * liseré fin et lettres de la couleur du palier. Couleurs alignées sur les raretés des objets (T1 gris Commun, T2 vert,
 * T3 bleu, T4 violet, T5 or ; T6 orange, entre l'or et le rouge ; T7 rouge Unique).
 * <p>Lettres en DM Sans gras 9,5 à leur vraie taille (assets/heraliz_loot/font/ui_badge.json), posées sur un point de
 * l'écran : même hauteur de capitales et même milieu que les lettres de Minecraft réduites aux trois quarts d'avant
 * (floues).
 */
public final class BadgePalier {
    private BadgePalier() {
    }

    private static final int[] COULEURS = {0x9A9A9A, 0x9A9A9A, 0x5CBF4A, 0x3F8CFF, 0xA64DFF, 0xF2B83A, 0xFF7A2E, 0xFF3333};
    private static final int FOND = 0xC0141314, H = 8;
    private static final ResourceLocation POLICE = new ResourceLocation("heraliz_loot", "ui_badge");
    private static final float TAILLE = 9.5f;
    /** Milieu des capitales sous le haut du badge (celui des anciennes lettres : 1,75 + 0,75 x 3,5). */
    private static final float MILIEU = 4.375f;
    /** Textes « T1 » … « T7 » dans la police du badge (préparés une fois). */
    private static final Component[] TEXTES = new Component[8];

    static {
        for (int i = 1; i <= 7; i++) TEXTES[i] = Component.literal("T" + i).withStyle(s -> s.withFont(POLICE));
    }

    /** Couleur (RVB) du palier 1 à 7. */
    public static int couleur(int palier) {
        return COULEURS[palier >= 1 && palier <= 7 ? palier : 0];
    }

    /** Largeur du badge en pixels d'écran. */
    public static int largeur(Font font, int palier) {
        return (int) Math.ceil(font.getSplitter().stringWidth(TEXTES[palier >= 1 && palier <= 7 ? palier : 7])) + 5;
    }

    /** Nom du conteneur coupé avec « … » s'il dépasse {@code max} pixels (le badge se pose juste après). */
    public static String couper(Font font, String nom, int max) {
        if (font.width(nom) <= max) return nom;
        return font.plainSubstrByWidth(nom, Math.max(0, max - font.width("…"))).trim() + "…";
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
        // texte centré dans le badge, milieu des capitales au même endroit qu'avant
        Component t = TEXTES[palier];
        g.pose().pushPose();
        placer(g, x + (w - font.getSplitter().stringWidth(t)) / 2f, y + MILIEU - (TAILLE * 0.49309f - 1f));
        g.drawString(font, t, 0, 0, 0xFF000000 | c, false);
        g.pose().popPose();
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; repris du kit des Caisses). */
    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }
}
