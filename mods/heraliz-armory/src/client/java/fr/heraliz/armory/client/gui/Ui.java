package fr.heraliz.armory.client.gui;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Charte des interfaces HeraliZ (copie reduite du kit des ecrans de clan, heralisz_clans client/gui/kit/Ui) : police
 * DM Sans (assets/heraliz_armory/font, licence OFL), panneaux « verre », capitales espacees, filets, jamais d'aplat.
 */
public final class Ui {
    private Ui() {}

    public static final ResourceLocation FONT = HeralizArmory.id("ui"), BOLD = HeralizArmory.id("ui_bold"), SMALL = HeralizArmory.id("ui_small");
    public static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, ACCENT = 0x6FA8FF, OR = 0xE8C36A;
    public static final int VERRE = 0x0D0F14, VERRE_ALPHA = 200;

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    /** Le composant dans une police (ses morceaux sans police propre en heritent). */
    public static MutableComponent in(Component c, ResourceLocation font) {
        return Component.empty().withStyle(st -> st.withFont(font)).append(c);
    }

    public static float spacedWidth(Font font, String s, ResourceLocation f, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            w += font.width(lit(new String(Character.toChars(s.codePointAt(i))), f)) + spacing;
        }
        return Math.max(0, w - spacing);
    }

    /** Texte en capitales espacees ; x = bord gauche. */
    public static void spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, int argb, float spacing) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            MutableComponent c = lit(new String(Character.toChars(up.codePointAt(i))), f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + spacing;
        }
    }

    public static void spacedRight(GuiGraphics g, Font font, String s, ResourceLocation f, float xRight, float y, int argb, float spacing) {
        spaced(g, font, s, f, xRight - spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing), y, argb, spacing);
    }

    /** Rectangle aux coins adoucis (un pixel retire a chaque coin). */
    public static void soft(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.fill(x + 1, y, x + w - 1, y + 1, argb);
        g.fill(x, y + 1, x + w, y + h - 1, argb);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, argb);
    }

    /** Contour d'un pixel aux coins adoucis, juste autour du rectangle (x, y, w, h). */
    public static void softOutline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y - 1, x + w - 1, y, argb);
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, argb);
        g.fill(x - 1, y + 1, x, y + h - 1, argb);
        g.fill(x + w, y + 1, x + w + 1, y + h - 1, argb);
    }

    /** Panneau « verre » : fond 0x0D0F14 translucide, contour blanc a 10 %, lisere haut, coins adoucis. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        soft(g, x, y, w, h, argb(VERRE, alpha));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, 26));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, 41));
    }

    /** Fond plein ecran : degrade diagonal (le monde reste lisible, jamais un aplat). */
    public static void fond(GuiGraphics g, int w, int h) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            int x0 = w * i / bands, x1 = w * (i + 1) / bands;
            float t = (i + 0.5f) / bands;
            int top = argb(0, (int) (0x8C + (0xB4 - 0x8C) * t * 0.5f)), bottom = argb(0, (int) (0x8C + (0xB4 - 0x8C) * (0.5f + t * 0.5f)));
            g.fillGradient(x0, 0, x1, h, top, bottom);
        }
    }

    /** Filet horizontal d'un pixel qui s'efface vers les deux bouts (ou vers un seul). */
    public static void hairline(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha, boolean fadeLeft, boolean fadeRight) {
        int w = x1 - x0;
        if (w <= 0) return;
        int steps = Math.max(1, w / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            float a = 1;
            if (fadeLeft && fadeRight) a = 1 - Math.abs(t * 2 - 1);
            else if (fadeLeft) a = t;
            else if (fadeRight) a = 1 - t;
            a = a * a * (3 - 2 * a);
            g.fill(x0 + i * w / steps, y, x0 + (i + 1) * w / steps, y + 1, argb(rgb, (int) (alpha * a)));
        }
    }

    /** Bande horizontale qui s'efface vers ses deux bouts sur fade pixels. */
    public static void band(GuiGraphics g, int x0, int y0, int x1, int y1, int rgb, int alpha, int fade) {
        g.fill(x0 + fade, y0, x1 - fade, y1, argb(rgb, alpha));
        int steps = Math.max(1, fade / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            int a = (int) (alpha * t * t * (3 - 2 * t));
            int w0 = i * fade / steps, w1 = (i + 1) * fade / steps;
            g.fill(x0 + w0, y0, x0 + w1, y1, argb(rgb, a));
            g.fill(x1 - w1, y0, x1 - w0, y1, argb(rgb, a));
        }
    }
}
