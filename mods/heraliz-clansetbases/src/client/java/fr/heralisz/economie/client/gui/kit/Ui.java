package fr.heralisz.economie.client.gui.kit;

import fr.heralisz.economie.HeraliszEconomie;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Charte des interfaces du mod (kit minimal repris des clans) : police DM Sans (assets/heralisz_economie/font,
 * licence OFL, + glyphe « héral » U+E000), couleurs sobres, capitales espacees, panneaux « verre ». Pas de cadres
 * ni de boutons vanilla.
 */
public final class Ui {
    private Ui() {
    }

    /** DM Sans Medium (texte courant), Bold (titres, chiffres), Medium petit corps (legendes). */
    public static final ResourceLocation FONT = HeraliszEconomie.id("ui"), BOLD = HeraliszEconomie.id("ui_bold"), SMALL = HeraliszEconomie.id("ui_small");

    public static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, DANGER = 0xFF6B6B, OK = 0x5FD08A;
    public static final int ACCENT_NEUTRE = 0x6FA8FF, OR = 0xE8C36A, AMBRE = 0xE8B34A, LAITON = 0xD6B05C;
    /** Fond des panneaux verre et son opacite. */
    public static final int VERRE = 0x0D0F14, VERRE_ALPHA = 200;
    public static final int MARGE = 16, ESPACE = 8, ESPACE_L = 12;

    public static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    public static MutableComponent tr(ResourceLocation font, String key, Object... args) {
        return Component.translatable(key, args).withStyle(st -> st.withFont(font));
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Texte avec alpha : la police ignore un alpha < 4 (le rend opaque), donc en dessous de 8 on ne dessine rien.
     */
    public static void texte(GuiGraphics g, Font font, Component c, float x, float y, int rgb, int alpha) {
        if (alpha < 8) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.drawString(font, c, 0, 0, argb(rgb, alpha), false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ capitales espacees
    public static float spacedWidth(Font font, String s, ResourceLocation f, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            w += font.width(lit(ch, f)) + spacing;
        }
        return Math.max(0, w - spacing);
    }

    /** Texte en capitales espacees ; x = bord gauche. */
    public static void spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, int argb, float spacing) {
        if ((argb >>> 24) < 8) return;
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            MutableComponent c = lit(ch, f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + spacing;
        }
    }

    /** Capitales espacees alignees a droite (x = bord droit). */
    public static void spacedRight(GuiGraphics g, Font font, String s, ResourceLocation f, float xRight, float y, int argb, float spacing) {
        float w = spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
        spaced(g, font, s, f, xRight - w, y, argb, spacing);
    }

    public static void right(GuiGraphics g, Font font, Component c, float xRight, float y, int rgb, int alpha) {
        texte(g, font, c, xRight - font.width(c), y, rgb, alpha);
    }

    // ------------------------------------------------------------------ formes
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

    /** Panneau « verre » : fond 0x0D0F14 a ~78 %, contour 1 px blanc a 10 %, lisere haut blanc a 16 %. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h) {
        glass(g, x, y, w, h, 1f);
    }

    /** Verre attenue par un facteur d'opacite 0..1 (fondu). */
    public static void glass(GuiGraphics g, int x, int y, int w, int h, float opacite) {
        soft(g, x, y, w, h, argb(VERRE, Math.round(VERRE_ALPHA * opacite)));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, Math.round(26 * opacite)));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, Math.round(41 * opacite)));
    }

    /** Fond plein ecran : degrade diagonal 0x8C000000 -> 0xB4000000 (le monde reste lisible). */
    public static void fond(GuiGraphics g, int w, int h) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            int x0 = w * i / bands, x1 = w * (i + 1) / bands;
            float t = (i + 0.5f) / bands;
            int top = argb(0, (int) (0x8C + (0xB4 - 0x8C) * t * 0.5f)), bottom = argb(0, (int) (0x8C + (0xB4 - 0x8C) * (0.5f + t * 0.5f)));
            g.fillGradient(x0, 0, x1, h, top, bottom);
        }
    }

    /** Filet horizontal d'un pixel qui s'efface vers les deux bouts. */
    public static void hairline(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha) {
        int w = x1 - x0;
        if (w <= 0) return;
        int steps = Math.max(1, w / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            float a = 1 - Math.abs(t * 2 - 1);
            a = a * a * (3 - 2 * a);
            int xa = x0 + i * w / steps, xb = x0 + (i + 1) * w / steps;
            g.fill(xa, y, xb, y + 1, argb(rgb, (int) (alpha * a)));
        }
    }
}
