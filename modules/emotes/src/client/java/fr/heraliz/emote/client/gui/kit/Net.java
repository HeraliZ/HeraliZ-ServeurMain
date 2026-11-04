package fr.heraliz.emote.client.gui.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Dessin net des écrans des emotes (roue, édition de la roue) : polices du mod à leur vraie taille, texte posé sur un point
 * de l'écran, rectangles, cadres, disques et anneaux aux bords posés sur les points de l'écran (même méthode que le kit Net
 * des quêtes). Sorti de RoueEmotes le 04/11 pour servir aussi à l'écran « Modifier la roue ».
 * <p>Le texte (drawString) vide le tampon des formes à chaque appel : une forme dessinée avant un texte est toujours sous
 * lui. Avant un {@code blit} (dessiné tout de suite), appeler {@code g.flush()} pour que les formes d'avant passent dessous.
 */
public final class Net {
    private Net() {
    }

    /** Taille des polices du mod (fichiers font/*.json). */
    public static float taille(ResourceLocation f) {
        return switch (f.getPath()) {
            case "ui_titre" -> 14f;
            case "ui_small" -> 9f;
            case "ui_gros" -> 16f;
            default -> 11f;
        };
    }

    /** Milieu des capitales de DM Sans sous le point de dessin (ascendante 992, capitales 700 sur 1302, décalage 1). */
    public static float milieu(ResourceLocation f) {
        return taille(f) * 0.49309f - 1f;
    }

    /** Un point de l'écran, en unités d'interface. */
    public static float point() {
        return (float) (1.0 / Minecraft.getInstance().getWindow().getGuiScale());
    }

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

    /** Texte dont le milieu des capitales est en {@code cyt} ; rend sa largeur. */
    public static float texte(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float cyt, int rgb) {
        if (s.isEmpty()) return 0;
        g.pose().pushPose();
        placer(g, x, cyt - milieu(f));
        g.drawString(font, Ui.lit(s, f), 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
        return largeur(font, s, f);
    }

    public static void centre(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float cyt, int rgb) {
        texte(g, font, s, f, x - largeur(font, s, f) / 2f, cyt, rgb);
    }

    public static float capsLargeur(Font font, String s, ResourceLocation f, float esp) {
        String up = s.toUpperCase(Locale.ROOT);
        float w = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            w += largeur(font, new String(Character.toChars(up.codePointAt(i))), f) + esp;
        }
        return Math.max(0, w - esp);
    }

    public static void capsCentre(GuiGraphics g, Font font, String s, ResourceLocation f, float esp, float x, float cyt, int rgb) {
        String up = s.toUpperCase(Locale.ROOT);
        float px = x - capsLargeur(font, s, f, esp) / 2f;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            texte(g, font, ch, f, px, cyt, rgb);
            px += largeur(font, ch, f) + esp;
        }
    }

    public static String couper(Font font, String s, ResourceLocation f, float max) {
        if (largeur(font, s, f) <= max) return s;
        int n = s.length();
        while (n > 0 && largeur(font, s.substring(0, n).stripTrailing() + "…", f) > max) n--;
        return n <= 0 ? "" : s.substring(0, n).stripTrailing() + "…";
    }

    /** Rectangle plein, bords posés sur les points de l'écran (au moins un point). */
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

    /** Cadre d'un point de l'écran. */
    public static void cadre(GuiGraphics g, float x, float y, float w, float h, int argb) {
        float e = point();
        rect(g, x, y, x + w, y + e, argb);
        rect(g, x, y + h - e, x + w, y + h, argb);
        rect(g, x, y + e, x + e, y + h - e, argb);
        rect(g, x + w - e, y + e, x + w, y + h - e, argb);
    }

    public static void disque(GuiGraphics g, float x, float y, float r, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s;
        for (float l = (float) Math.floor((y - r) * s) / s; l < y + r; l += pas) {
            float d = l + pas / 2 - y, demi = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (demi > pas / 3) rect(g, x - demi, l, x + demi, l + pas, argb);
        }
    }

    public static void anneau(GuiGraphics g, float x, float y, float r, float e, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s, ri = r - e;
        for (float l = (float) Math.floor((y - r) * s) / s; l < y + r; l += pas) {
            float d = l + pas / 2 - y, ext = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (ext <= pas / 3) continue;
            if (Math.abs(d) >= ri) {
                rect(g, x - ext, l, x + ext, l + pas, argb);
            } else {
                float in = (float) Math.sqrt(Math.max(0, ri * ri - d * d));
                rect(g, x - ext, l, x - in, l + pas, argb);
                rect(g, x + in, l, x + ext, l + pas, argb);
            }
        }
    }
}
