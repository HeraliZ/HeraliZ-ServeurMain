package fr.heraliz.carte.client;

import fr.heralisz.utils.client.kit.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Textes de la carte à n'importe quelle taille (polices DM Sans du kit, mises à l'échelle) : texte simple, capitales
 * espacées, étiquettes de la carte avec un liseré sombre (lisibles sur le satellite comme sur le relief).
 */
final class Texte {
    private Texte() {
    }

    /** Liseré des étiquettes de la carte. */
    static final int LISERE = 0xD8080A0E;
    private static final float[][] HUIT = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    static MutableComponent lit(String s, ResourceLocation police) {
        return Ui.lit(s, police);
    }

    static float largeur(Font font, String s, ResourceLocation police, float echelle) {
        return font.width(lit(s, police)) * echelle;
    }

    /** Texte simple, (x, y) = coin haut gauche. */
    static void ecrire(GuiGraphics g, Font font, String s, ResourceLocation police, float x, float y, int argb, float echelle) {
        ecrire(g, font, lit(s, police), x, y, argb, echelle);
    }

    static void ecrire(GuiGraphics g, Font font, Component c, float x, float y, int argb, float echelle) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(echelle, echelle, 1);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Texte aligné à droite (x = bord droit). */
    static void droite(GuiGraphics g, Font font, String s, ResourceLocation police, float x, float y, int argb, float echelle) {
        ecrire(g, font, s, police, x - largeur(font, s, police, echelle), y, argb, echelle);
    }

    /** Largeur de capitales espacées. */
    static float largeurEspacee(Font font, String s, ResourceLocation police, float echelle, float espacement) {
        String up = s.toUpperCase(Locale.ROOT);
        float w = 0;
        int n = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            w += font.width(lit(new String(Character.toChars(up.codePointAt(i))), police)) * echelle;
            n++;
        }
        return w + Math.max(0, n - 1) * espacement;
    }

    /** Capitales espacées, (x, y) = coin haut gauche ; avec liseré si {@code lisere}. */
    static void espacee(GuiGraphics g, Font font, String s, ResourceLocation police, float x, float y, int argb, float echelle,
                        float espacement, boolean lisere) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        float o = 0.55f / echelle; // liseré d'environ un demi-pixel d'interface, quelle que soit la taille
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            MutableComponent c = lit(new String(Character.toChars(up.codePointAt(i))), police);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(echelle, echelle, 1);
            if (lisere) {
                int a = Math.min(LISERE >>> 24, argb >>> 24);
                int l = (a << 24) | (LISERE & 0xFFFFFF);
                for (float[] d : HUIT) {
                    g.pose().pushPose();
                    g.pose().translate(d[0] * o, d[1] * o, 0);
                    g.drawString(font, c, 0, 0, l, false);
                    g.pose().popPose();
                }
            }
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) * echelle + espacement;
        }
    }

    /** Capitales espacées centrées sur cx. */
    static void espaceeCentree(GuiGraphics g, Font font, String s, ResourceLocation police, float cx, float y, int argb, float echelle,
                               float espacement, boolean lisere) {
        espacee(g, font, s, police, cx - largeurEspacee(font, s, police, echelle, espacement) / 2f, y, argb, echelle, espacement, lisere);
    }

    /** Capitales espacées alignées à droite. */
    static void espaceeDroite(GuiGraphics g, Font font, String s, ResourceLocation police, float xd, float y, int argb, float echelle,
                              float espacement) {
        espacee(g, font, s, police, xd - largeurEspacee(font, s, police, echelle, espacement), y, argb, echelle, espacement, false);
    }

    /** Hauteur de ligne de la police (taille des fichiers de police du kit : 9 pour la petite, 11 pour les autres). */
    static float em(ResourceLocation police) {
        return police.equals(Ui.SMALL) ? 9f : 11f;
    }

    /** Largeur d'une étiquette (capitales espacées ou texte simple). */
    static float largeurEtiquette(Font font, String s, ResourceLocation police, float echelle, float espacement, boolean capitales) {
        return capitales ? largeurEspacee(font, s, police, echelle, espacement) : largeur(font, s, police, echelle);
    }

    /**
     * Étiquette de la carte centrée en (cx, cy), entourée d'un halo sombre d'environ {@code halo} unités d'interface
     * (lisible sur la neige comme sur la mer). Capitales espacées si {@code capitales}, sinon texte tel quel.
     */
    static void etiquette(GuiGraphics g, Font font, String s, ResourceLocation police, float cx, float cy, int argb, float echelle,
                          float espacement, boolean capitales, float halo) {
        float w = largeurEtiquette(font, s, police, echelle, espacement, capitales);
        float x = cx - w / 2f, y = cy - em(police) * echelle * 0.5f;
        int a = Math.min(LISERE >>> 24, argb >>> 24);
        int l = (a << 24) | (LISERE & 0xFFFFFF);
        float o = halo / echelle;
        if (!capitales) {
            MutableComponent c = lit(s, police);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(echelle, echelle, 1);
            if (halo > 0) {
                for (float[] d : HUIT) {
                    g.pose().pushPose();
                    g.pose().translate(d[0] * o, d[1] * o, 0);
                    g.drawString(font, c, 0, 0, l, false);
                    g.pose().popPose();
                }
            }
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            return;
        }
        String up = s.toUpperCase(Locale.ROOT);
        float px = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            MutableComponent c = lit(new String(Character.toChars(up.codePointAt(i))), police);
            g.pose().pushPose();
            g.pose().translate(px, y, 0);
            g.pose().scale(echelle, echelle, 1);
            if (halo > 0) {
                for (float[] d : HUIT) {
                    g.pose().pushPose();
                    g.pose().translate(d[0] * o, d[1] * o, 0);
                    g.drawString(font, c, 0, 0, l, false);
                    g.pose().popPose();
                }
            }
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            px += font.width(c) * echelle + espacement;
        }
    }

    /** Texte coupé avec « … » s'il dépasse la largeur (à l'échelle donnée). */
    static String couper(Font font, String s, ResourceLocation police, float echelle, float max) {
        if (largeur(font, s, police, echelle) <= max) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            if (largeur(font, sb + ch + "…", police, echelle) > max) break;
            sb.append(ch);
        }
        return sb.toString().trim() + "…";
    }
}
