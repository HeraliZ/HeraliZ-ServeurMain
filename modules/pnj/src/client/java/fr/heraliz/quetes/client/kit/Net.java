package fr.heraliz.quetes.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Dessin net (25/10, audit des écrans) : texte des polices du mod à sa vraie taille, centré sur le milieu de ses
 * capitales et posé sur un point de l'écran ; formes à la mesure exacte (bords sur les points de l'écran, filets d'un
 * point). Minecraft 1.20.1 pose une lettre TTF à y + ascendante + 2 x décalage - 3 ; DM Sans : ascendante 992 et
 * capitales 700 sur 1302, décalage 1 : milieu des capitales = taille x 0,49309 - 1. Mêmes calculs que le journal des
 * quêtes et que les aperçus hors du jeu (modules/pnj/outils/apercu_*.py).
 */
public final class Net {
    private Net() {
    }

    public static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre"),
            PETIT_GRAS = new ResourceLocation("heraliz_quetes", "ui_small_bold"), MICRO = new ResourceLocation("heraliz_quetes", "ui_micro");

    public static float taille(ResourceLocation f) {
        return switch (f.getPath()) {
            case "ui_titre" -> 14f;
            case "ui_small", "ui_small_bold" -> 9f;
            case "ui_micro", "ui_moyen" -> 7.5f;
            case "ui_mini" -> 6.5f;
            case "ui_code" -> 18f;
            default -> 11f;
        };
    }

    /** Milieu des capitales sous le point de dessin. */
    public static float milieu(ResourceLocation f) {
        return taille(f) * 0.49309f - 1f;
    }

    /** Hauteur des capitales. */
    public static float cap(ResourceLocation f) {
        return taille(f) * 0.53763f;
    }

    /** Un point de l'écran, en unités d'interface. */
    public static float point() {
        return (float) (1.0 / Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes). */
    public static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    public static float largeur(Font font, String s, ResourceLocation f) {
        return font.getSplitter().stringWidth(Ui.lit(s, f));
    }

    /** Texte : bord gauche en x, milieu des capitales sur cy ; rend sa largeur. */
    public static float texte(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float cy, int rgb) {
        if (s.isEmpty()) return 0;
        g.pose().pushPose();
        placer(g, x, cy - milieu(f));
        g.drawString(font, Ui.lit(s, f), 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
        return largeur(font, s, f);
    }

    public static void centre(GuiGraphics g, Font font, String s, ResourceLocation f, float cx, float cy, int rgb) {
        texte(g, font, s, f, cx - largeur(font, s, f) / 2f, cy, rgb);
    }

    public static void droite(GuiGraphics g, Font font, String s, ResourceLocation f, float xd, float cy, int rgb) {
        texte(g, font, s, f, xd - largeur(font, s, f), cy, rgb);
    }

    /** Ligne déjà coupée (aides, résumés). */
    public static void ligne(GuiGraphics g, Font font, FormattedCharSequence l, ResourceLocation f, float x, float cy, int rgb) {
        g.pose().pushPose();
        placer(g, x, cy - milieu(f));
        g.drawString(font, l, 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
    }

    public static float capsLargeur(Font font, String s, ResourceLocation f, float esp) {
        String up = s.toUpperCase(Locale.ROOT);
        float w = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            w += font.getSplitter().stringWidth(Ui.lit(new String(Character.toChars(up.codePointAt(i))), f)) + esp;
        }
        return Math.max(0, w - esp);
    }

    /** Capitales espacées, chaque lettre posée sur un point de l'écran ; bord gauche x, milieu cy ; rend la largeur. */
    public static float caps(GuiGraphics g, Font font, String s, ResourceLocation f, float esp, float x, float cy, int rgb) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        int argb = Ui.argb(rgb, 255);
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            MutableComponent c = Ui.lit(new String(Character.toChars(up.codePointAt(i))), f);
            g.pose().pushPose();
            placer(g, cx, cy - milieu(f));
            font.drawInBatch(c, 0, 0, argb, false, g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
            g.pose().popPose();
            cx += font.getSplitter().stringWidth(c) + esp;
        }
        g.flush();
        return Math.max(0, cx - esp - x);
    }

    public static void capsCentre(GuiGraphics g, Font font, String s, ResourceLocation f, float esp, float cx, float cy, int rgb) {
        caps(g, font, s, f, esp, cx - capsLargeur(font, s, f, esp) / 2f, cy, rgb);
    }

    public static void capsDroite(GuiGraphics g, Font font, String s, ResourceLocation f, float esp, float xd, float cy, int rgb) {
        caps(g, font, s, f, esp, xd - capsLargeur(font, s, f, esp), cy, rgb);
    }

    /** Coupe le texte à max (points d'interface) avec « … ». */
    public static String couper(Font font, String s, ResourceLocation f, float max) {
        if (largeur(font, s, f) <= max) return s;
        int n = s.length();
        while (n > 0 && largeur(font, s.substring(0, n).stripTrailing() + "…", f) > max) n--;
        return n <= 0 ? "" : s.substring(0, n).stripTrailing() + "…";
    }

    // ------------------------------------------------------------------ formes
    /** Rectangle plein, bords posés sur les points de l'écran (au moins un point de large). */
    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float a0 = Math.round(x0 * s) / s, b0 = Math.round(y0 * s) / s, a1 = Math.round(x1 * s) / s, b1 = Math.round(y1 * s) / s;
        if (a1 <= a0) a1 = a0 + 1 / s;
        if (b1 <= b0) b1 = b0 + 1 / s;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int a = argb >>> 24, r = argb >> 16 & 255, v = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, a0, b0, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a0, b1, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a1, b1, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a1, b0, 0).color(r, v, b, a).endVertex();
    }

    /** Contour d'un point d'écran à l'intérieur du rectangle. */
    public static void cadre(GuiGraphics g, float x, float y, float w, float h, int argb) {
        float e = point();
        rect(g, x, y, x + w, y + e, argb);
        rect(g, x, y + h - e, x + w, y + h, argb);
        rect(g, x, y + e, x + e, y + h - e, argb);
        rect(g, x + w - e, y + e, x + w, y + h - e, argb);
    }

    /** Dégradé vertical (de haut en bas). */
    public static void degrade(GuiGraphics g, float x0, float y0, float x1, float y1, int haut, int bas) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float a0 = Math.round(x0 * s) / s, b0 = Math.round(y0 * s) / s, a1 = Math.round(x1 * s) / s, b1 = Math.round(y1 * s) / s;
        if (a1 <= a0 || b1 <= b0) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.vertex(m, a0, b0, 0).color(haut >> 16 & 255, haut >> 8 & 255, haut & 255, haut >>> 24).endVertex();
        vc.vertex(m, a0, b1, 0).color(bas >> 16 & 255, bas >> 8 & 255, bas & 255, bas >>> 24).endVertex();
        vc.vertex(m, a1, b1, 0).color(bas >> 16 & 255, bas >> 8 & 255, bas & 255, bas >>> 24).endVertex();
        vc.vertex(m, a1, b0, 0).color(haut >> 16 & 255, haut >> 8 & 255, haut & 255, haut >>> 24).endVertex();
    }

    /** Disque plein, ligne de points d'écran par ligne ; anneau si epaisseur > 0. */
    public static void disque(GuiGraphics g, float cx, float cy, float r, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s;
        for (float y = (float) Math.floor((cy - r) * s) / s; y < cy + r; y += pas) {
            float d = y + pas / 2 - cy, demi = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (demi > pas / 3) rect(g, cx - demi, y, cx + demi, y + pas, argb);
        }
    }

    public static void anneau(GuiGraphics g, float cx, float cy, float r, float e, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s, ri = r - e;
        for (float y = (float) Math.floor((cy - r) * s) / s; y < cy + r; y += pas) {
            float d = y + pas / 2 - cy, ext = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (ext <= pas / 3) continue;
            if (Math.abs(d) >= ri) {
                rect(g, cx - ext, y, cx + ext, y + pas, argb);
            } else {
                float in = (float) Math.sqrt(Math.max(0, ri * ri - d * d));
                rect(g, cx - ext, y, cx - in, y + pas, argb);
                rect(g, cx + in, y, cx + ext, y + pas, argb);
            }
        }
    }

    /** Coche (deux traits d'épaisseur e) dans le carré (x, y, s). */
    public static void coche(GuiGraphics g, float x, float y, float s, float e, int argb) {
        trait(g, x, y + s * 0.55f, x + s * 0.38f, y + s * 0.9f, e, argb);
        trait(g, x + s * 0.38f, y + s * 0.9f, x + s, y + s * 0.12f, e, argb);
    }

    public static void trait(GuiGraphics g, float x0, float y0, float x1, float y1, float e, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l <= 0) return;
        float ux = dx / l * e / 2, uy = dy / l * e / 2, nx = -uy, ny = ux;
        // prolongé d'une demi-épaisseur aux deux bouts : les jointures restent pleines
        x0 -= ux;
        y0 -= uy;
        x1 += ux;
        y1 += uy;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int a = argb >>> 24, r = argb >> 16 & 255, v = argb >> 8 & 255, b = argb & 255;
        float[][] p = {{x0 + nx, y0 + ny}, {x0 - nx, y0 - ny}, {x1 - nx, y1 - ny}, {x1 + nx, y1 + ny}};
        // les deux sens de rotation : visible quel que soit le sens des sommets
        for (int k = 0; k < 4; k++) vc.vertex(m, p[k][0], p[k][1], 0).color(r, v, b, a).endVertex();
        for (int k = 3; k >= 0; k--) vc.vertex(m, p[k][0], p[k][1], 0).color(r, v, b, a).endVertex();
    }

    /** Triangle plein (pointe de flèche). */
    public static void triangle(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int a = argb >>> 24, r = argb >> 16 & 255, v = argb >> 8 & 255, b = argb & 255;
        // quadrilatère dégénéré (dernier sommet doublé), dans les deux sens
        float[][] p = {{x0, y0}, {x1, y1}, {x2, y2}, {x2, y2}};
        for (int k = 0; k < 4; k++) vc.vertex(m, p[k][0], p[k][1], 0).color(r, v, b, a).endVertex();
        for (int k = 3; k >= 0; k--) vc.vertex(m, p[k][0], p[k][1], 0).color(r, v, b, a).endVertex();
    }
}
