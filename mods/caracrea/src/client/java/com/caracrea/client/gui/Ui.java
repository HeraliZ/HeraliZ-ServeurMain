package com.caracrea.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The creator's look: a dark, flat theme with one warm accent, and the few primitives it is drawn with. */
final class Ui {
    static final int BACKDROP_TOP = 0xF00B0D11;
    static final int BACKDROP_BOTTOM = 0xF8050608;
    static final int PANEL = 0xE6141820;
    static final int SURFACE = 0xFF1A1F28;
    static final int SURFACE_HOVER = 0xFF232A35;
    static final int SUNKEN = 0xFF0F1217;
    static final int LINE = 0x14FFFFFF;
    static final int BORDER = 0xFF262D38;
    static final int TEXT = 0xFFEDEDED;
    static final int TEXT_DIM = 0xFFB4BBC6;
    static final int MUTED = 0xFF7D8694;
    static final int ACCENT = 0xFFE4A23F;
    static final int ACCENT_HOVER = 0xFFF0B45A;
    static final int ACCENT_SOFT = 0x40E4A23F;
    static final int ON_ACCENT = 0xFF1A1611;
    static final int SCAR = 0xFFE07A7A;

    private Ui() {
    }

    /** Rectangle with rounded corners of radius {@code r} (1 to 4 pixels). */
    static void round(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** A 1-pixel rounded outline. */
    static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Soft shadow under a card. */
    static void shadow(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x + 1, y + h, x + w - 1, y + h + 5, 0x55000000, 0);
    }

    /** Filled disc of radius {@code r} centred on (cx, cy). */
    static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            double fy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - fy * fy)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    /** Ring of radius {@code r}, 1 pixel thick. */
    static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            double fy = dy + 0.5;
            int outer = (int) Math.round(Math.sqrt(Math.max(0, r * r - fy * fy)));
            int inner = (int) Math.round(Math.sqrt(Math.max(0, (r - 1) * (r - 1) - fy * fy)));
            if (Math.abs(fy) >= r - 1) {
                g.fill(cx - outer, cy + dy, cx + outer, cy + dy + 1, color);
            } else {
                g.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                g.fill(cx + inner, cy + dy, cx + outer, cy + dy + 1, color);
            }
        }
    }

    /** A straight line of the given thickness, drawn as small squares. */
    static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x0 + dx * i / steps - thickness / 2F);
            int y = Math.round(y0 + dy * i / steps - thickness / 2F);
            g.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Multiplies a colour's alpha by {@code a} (0..1); text needs at least a little alpha to show. */
    static int alpha(int color, float a) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (Math.max(alpha, 5) << 24) | (color & 0xFFFFFF);
    }

    /** Linear blend of two colours. */
    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Text vertically centred in a box of height {@code h}. */
    static void text(GuiGraphics g, Font font, Component text, int x, int y, int h, int color) {
        g.drawString(font, text, x, y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    static void textCentered(GuiGraphics g, Font font, Component text, int x, int y, int w, int h, int color) {
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    /** Text scaled by {@code scale} (titles). */
    static void bigText(GuiGraphics g, Font font, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Small caps label (drawn at 0.75 scale). */
    static void label(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        label(g, font, text, x, y, Integer.MAX_VALUE, color);
    }

    /** A small uppercase label, clipped with an ellipsis to {@code maxWidth} screen pixels. */
    static void label(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        Component upper = Component.literal(text.getString().toUpperCase(java.util.Locale.ROOT));
        if (maxWidth != Integer.MAX_VALUE) {
            upper = clip(font, upper, (int) (maxWidth / 0.75F));
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, upper, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Clips a label to {@code width} pixels with an ellipsis. */
    static Component clip(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String s = text.getString();
        while (s.length() > 1 && font.width(s + "…") > width) {
            s = s.substring(0, s.length() - 1);
        }
        return Component.literal(s + "…");
    }

    /** Small triangle pointing left or right. */
    static void arrow(GuiGraphics g, int x, int y, int h, boolean left, int color) {
        int half = h / 2;
        for (int i = 0; i < half; i++) {
            int w = i + 1;
            int px = left ? x + i : x + half - 1 - i;
            g.fill(px, y + half - w, px + 1, y + half + w, color);
        }
    }
}
