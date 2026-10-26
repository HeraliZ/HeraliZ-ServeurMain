package com.caracrea.client.gui;

import com.caracrea.client.gui.kit.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Drawing pieces shared by the voice chat screens, in the HeraliZ interface style (kit {@code gui.kit}, copied from
 * the clan screens): glass panel with title and hairlines, setting rows (name + short explanation on the left, control
 * on the right), switches, segmented choices, sliders, level meters.
 */
final class VoiceUi {
    static final int ROW = 26;

    private VoiceUi() {
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Screen backdrop, glass panel, spaced title with an optional subtitle, hairline under the header. */
    static void panel(GuiGraphics g, Font font, int screenW, int screenH, int x, int y, int w, int h, String title, String subtitle) {
        Ui.fond(g, screenW, screenH);
        Ui.glass(g, x, y, w, h);
        Ui.spaced(g, font, title, Ui.BOLD, x + Ui.MARGE, y + 14, Ui.argb(Ui.TEXT, 255), 1.6f);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.drawString(font, Ui.lit(Ui.ellipsis(font, subtitle, Ui.SMALL, w - 2 * Ui.MARGE), Ui.SMALL), x + Ui.MARGE, y + 28,
                    Ui.argb(Ui.MUTED, 255), false);
        }
        Ui.hairline(g, x + Ui.MARGE, x + w - Ui.MARGE, y + 39, 0xFFFFFF, 36, false, true);
    }

    /** Close cross at the top right; returns whether the mouse is over it. */
    static boolean close(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean over = inside(mouseX, mouseY, x - 3, y - 3, 13, 13);
        int c = Ui.argb(over ? Ui.TEXT : Ui.MUTED, 255);
        for (int i = 0; i < 7; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, c);
        }
        return over;
    }

    static void rowBackground(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, hover ? 12 : 5));
    }

    /** Name and explanation of a row. */
    static void label(GuiGraphics g, Font font, int x, int y, int maxW, Component name, String hint, boolean hover) {
        g.drawString(font, Ui.in(name, Ui.FONT), x + 8, y + 4, Ui.argb(hover ? 0xFFFFFF : Ui.TEXT, 255), false);
        if (hint != null && !hint.isEmpty()) {
            g.drawString(font, Ui.lit(Ui.ellipsis(font, hint, Ui.SMALL, maxW), Ui.SMALL), x + 8, y + 15, Ui.argb(Ui.MUTED, 255), false);
        }
    }

    /** On/off switch ending at {@code right}; returns its left edge. */
    static int toggle(GuiGraphics g, Font font, int right, int y, int h, boolean on, boolean hover) {
        int accent = Ui.accent();
        int sw = 22;
        int sh = 10;
        int sx = right - sw - 8;
        int sy = y + (h - sh) / 2;
        Ui.soft(g, sx, sy, sw, sh, on ? Ui.argb(accent, hover ? 200 : 160) : Ui.argb(0xFFFFFF, hover ? 30 : 18));
        Ui.softOutline(g, sx + 1, sy + 1, sw - 2, sh - 2, on ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.3f), 200) : Ui.argb(0xFFFFFF, 50));
        int kx = on ? sx + sw - 8 : sx + 2;
        Ui.soft(g, kx, sy + 2, 6, 6, on ? 0xFFFFFFFF : Ui.argb(Ui.MUTED, 255));
        String state = Component.translatable(on ? "options.on" : "options.off").getString();
        Ui.spacedRight(g, font, state, Ui.SMALL, sx - 6, sy + 2, on ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.4f), 255) : Ui.argb(Ui.FAINT, 255), 0.8f);
        return sx;
    }

    /**
     * Segmented choice of {@code labels.length} parts in [x, x + w); {@code selected} is highlighted. Returns the index
     * under the mouse, or -1.
     */
    static int segments(GuiGraphics g, Font font, int x, int y, int w, int h, String[] labels, int selected, int mouseX, int mouseY) {
        int accent = Ui.accent();
        int hovered = -1;
        int n = labels.length;
        for (int i = 0; i < n; i++) {
            int sx = x + w * i / n;
            int ex = x + w * (i + 1) / n;
            boolean over = inside(mouseX, mouseY, sx, y, ex - sx, h);
            if (over) {
                hovered = i;
            }
            boolean on = i == selected;
            Ui.soft(g, sx, y, ex - sx - 2, h, on ? Ui.argb(accent, over ? 90 : 60) : Ui.argb(0xFFFFFF, over ? 20 : 8));
            if (on) {
                Ui.softOutline(g, sx + 1, y + 1, ex - sx - 4, h - 2, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.25f), 190));
            }
            String text = couperEspace(font, labels[i].toUpperCase(Locale.ROOT), 0.6f, ex - sx - 8);
            Ui.spacedCentered(g, font, text, Ui.SMALL, sx + (ex - sx - 2) / 2f, y + (h - 6) / 2f,
                    on ? 0xFFFFFFFF : Ui.argb(over ? Ui.TEXT : Ui.MUTED, 255), 0.6f);
        }
        return hovered;
    }

    /** Coupe avec « … » d'apres la largeur en petites capitales espacees (mesuree comme elle est dessinee). */
    static String couperEspace(Font font, String s, float espace, int max) {
        if (Ui.spacedWidth(font, s, Ui.SMALL, espace) <= max) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            if (Ui.spacedWidth(font, sb + ch + "…", Ui.SMALL, espace) > max) break;
            sb.append(ch);
        }
        return sb.toString().trim() + "…";
    }

    /** Horizontal slider in [x, x + w) for {@code t} in 0..1. */
    static void slider(GuiGraphics g, int x, int y, int w, float t, boolean hover) {
        int accent = Ui.accent();
        g.fill(x, y, x + w, y + 2, Ui.argb(0xFFFFFF, 30));
        int fx = x + Math.round(Math.max(0, Math.min(1, t)) * w);
        g.fill(x, y, fx, y + 2, Ui.argb(accent, 230));
        Ui.soft(g, fx - 3, y - 3, 7, 8, hover ? 0xFFFFFFFF : Ui.argb(Ui.TEXT, 255));
    }

    /** Level meter: track, fill (green when the voice passes), optional threshold marker and background marker. */
    static void meter(GuiGraphics g, int x, int y, int w, int h, float level, boolean active, float threshold, float floor) {
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 14));
        int fill = Math.round(w * Math.max(0, Math.min(1, level)));
        if (fill > 1) {
            Ui.soft(g, x, y, fill, h, active ? Ui.argb(Ui.OK, 230) : Ui.argb(Ui.mix(Ui.OK, 0x101418, 0.55f), 230));
        }
        if (floor >= 0) {
            int fx = x + Math.round(w * Math.min(1, floor));
            g.fill(fx, y + 1, fx + 1, y + h - 1, Ui.argb(Ui.AMBRE, 200));
        }
        if (threshold >= 0) {
            int tx = x + Math.round(w * Math.min(1, threshold));
            g.fill(tx - 1, y - 3, tx + 1, y + h + 3, Ui.argb(Ui.accent(), 255));
        }
    }

    /** dBFS to 0..1 on the meters: -60 dB empty, 0 dB full. */
    static float scale(float db) {
        return Math.max(0, Math.min(1, (db + 60) / 60));
    }
}
