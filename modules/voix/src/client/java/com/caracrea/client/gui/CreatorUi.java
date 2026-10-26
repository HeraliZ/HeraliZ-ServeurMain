package com.caracrea.client.gui;

import com.caracrea.client.gui.kit.Ui;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Drawing of the character creator (validated mock-up): rectangles, gradients, discs and strokes at free coordinates (the
 * thin borders are one screen pixel, as on the mock-up), and text placed between pixels. The look follows the clan screens'
 * kit ({@link Ui}) with the red of the logo as accent.
 */
final class CreatorUi {
    private CreatorUi() {
    }

    /** DM Sans: titles (bold 13), category names (bold 9), body text (7.5) and small text (6.5). */
    static final ResourceLocation TITLE = font("ui_titre"), BOLD_9 = font("ui_small_bold"), TEXT = font("ui_moyen"),
            TEXT_BOLD = font("ui_moyen_bold"), MINI = font("ui_mini"), MINI_BOLD = font("ui_mini_bold"),
            AIDE = font("ui_aide"), BOUTON_BOLD = font("ui_bouton_bold");

    static final int ACCENT = 0xE3201B, WHITE = 0xFFFFFF, LIGHT = 0xECEEF2, OPTION = 0xC3C8D0, BUTTON = 0xC9CDD4, GREY = 0x9AA0AA,
            MUTED = 0x8F95A0, FAINT = 0x5A5F69, KEYS = 0x7E848E, VALUE = 0xF2C9C6, PINK = 0xF08A86, WELCOME = 0xE9B8B4,
            DANGER = 0xFF6B6B, NIGHT = 0x050608, GLASS = 0x0D0F14, SCAR = 0xE07A7A;

    private static ResourceLocation font(String name) {
        return new ResourceLocation("caracrea", name);
    }

    /** An RGB colour with an opacity from 0 to 255. */
    static int a(int rgb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        return Ui.mix(a, b, Math.max(0, Math.min(1, t)));
    }

    // ------------------------------------------------------------------ shapes

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, int argb) {
        vc.vertex(m, x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
    }

    /** One quad, corners top-left, bottom-left, bottom-right, top-right (the winding Minecraft keeps). */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int tl, int bl, int br, int tr) {
        vertex(vc, m, x0, y0, tl);
        vertex(vc, m, x0, y1, bl);
        vertex(vc, m, x1, y1, br);
        vertex(vc, m, x1, y0, tr);
    }

    private static VertexConsumer buffer(GuiGraphics g) {
        return g.bufferSource().getBuffer(RenderType.gui());
    }

    static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || argb >>> 24 == 0) {
            return;
        }
        quad(buffer(g), g.pose().last().pose(), x0, y0, x1, y1, argb, argb, argb, argb);
        g.flush();
    }

    /** Horizontal gradient, left to right. */
    static void gradientH(GuiGraphics g, float x0, float y0, float x1, float y1, int left, int right) {
        if (x1 <= x0 || y1 <= y0 || (left >>> 24 == 0 && right >>> 24 == 0)) {
            return;
        }
        quad(buffer(g), g.pose().last().pose(), x0, y0, x1, y1, left, left, right, right);
        g.flush();
    }

    /** Vertical gradient, top to bottom. */
    static void gradientV(GuiGraphics g, float x0, float y0, float x1, float y1, int top, int bottom) {
        if (x1 <= x0 || y1 <= y0 || (top >>> 24 == 0 && bottom >>> 24 == 0)) {
            return;
        }
        quad(buffer(g), g.pose().last().pose(), x0, y0, x1, y1, top, bottom, bottom, top);
        g.flush();
    }

    /** Inner border of thickness {@code t} (the mock-up's "inset 0 0 0 1px" with t = one screen pixel). */
    static void border(GuiGraphics g, float x, float y, float w, float h, float t, int argb) {
        if (argb >>> 24 == 0 || w <= 0 || h <= 0) {
            return;
        }
        VertexConsumer vc = buffer(g);
        Matrix4f m = g.pose().last().pose();
        quad(vc, m, x, y, x + w, y + t, argb, argb, argb, argb);
        quad(vc, m, x, y + h - t, x + w, y + h, argb, argb, argb, argb);
        quad(vc, m, x, y + t, x + t, y + h - t, argb, argb, argb, argb);
        quad(vc, m, x + w - t, y + t, x + w, y + h - t, argb, argb, argb, argb);
        g.flush();
    }

    /** Arc of a ring from fraction {@code from} to fraction {@code to} of a turn (0 = noon, clockwise). */
    static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut, float from, float to, int argb) {
        float d = Math.max(0, Math.min(1, from));
        float f = Math.max(d, Math.min(1, to));
        if (f - d <= 0.0005F || argb >>> 24 == 0) {
            return;
        }
        int n = Math.max(2, (int) Math.ceil(64 * (f - d) * Math.max(1, rOut / 8)));
        VertexConsumer vc = buffer(g);
        Matrix4f m = g.pose().last().pose();
        double t0 = d * 2 * Math.PI;
        float s0 = (float) Math.sin(t0), c0 = (float) Math.cos(t0);
        for (int i = 1; i <= n; i++) {
            double t1 = (d + (f - d) * i / n) * 2 * Math.PI;
            float s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            vertex(vc, m, cx + rOut * s0, cy - rOut * c0, argb);
            vertex(vc, m, cx + rIn * s0, cy - rIn * c0, argb);
            vertex(vc, m, cx + rIn * s1, cy - rIn * c1, argb);
            vertex(vc, m, cx + rOut * s1, cy - rOut * c1, argb);
            s0 = s1;
            c0 = c1;
        }
        g.flush();
    }

    static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut, int argb) {
        ring(g, cx, cy, rIn, rOut, 0, 1, argb);
    }

    static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        ring(g, cx, cy, 0, r, 0, 1, argb);
    }

    /** Dashed circle ({@code dashes} dashes and as many gaps). */
    static void dashedRing(GuiGraphics g, float cx, float cy, float r, float t, int dashes, int argb) {
        for (int i = 0; i < dashes; i++) {
            ring(g, cx, cy, r - t / 2, r + t / 2, (float) i / dashes, (i + 0.5F) / dashes, argb);
        }
    }

    /** Elliptic glow: the colour in the middle, fading to nothing at the edge. */
    static void glow(GuiGraphics g, float cx, float cy, float rx, float ry, int argb) {
        if (argb >>> 24 == 0 || rx <= 0 || ry <= 0) {
            return;
        }
        int edge = argb & 0xFFFFFF;
        int n = 48;
        VertexConsumer vc = buffer(g);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < n; i++) {
            double t0 = i * 2 * Math.PI / n;
            double t1 = (i + 1) * 2 * Math.PI / n;
            vertex(vc, m, cx + rx * (float) Math.sin(t0), cy - ry * (float) Math.cos(t0), edge);
            vertex(vc, m, cx, cy, argb);
            vertex(vc, m, cx, cy, argb);
            vertex(vc, m, cx + rx * (float) Math.sin(t1), cy - ry * (float) Math.cos(t1), edge);
        }
        g.flush();
    }

    /** Straight stroke of thickness {@code t}. */
    static void stroke(GuiGraphics g, float x0, float y0, float x1, float y1, float t, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4F || argb >>> 24 == 0) {
            return;
        }
        float nx = -dy / l * t / 2, ny = dx / l * t / 2;
        VertexConsumer vc = buffer(g);
        Matrix4f m = g.pose().last().pose();
        vertex(vc, m, x0 - nx, y0 - ny, argb);
        vertex(vc, m, x0 + nx, y0 + ny, argb);
        vertex(vc, m, x1 + nx, y1 + ny, argb);
        vertex(vc, m, x1 - nx, y1 - ny, argb);
        g.flush();
    }

    /** Tick mark in a square of side {@code s} (top-left corner x, y). */
    static void check(GuiGraphics g, float x, float y, float s, float t, int argb) {
        float ax = x, ay = y + s * 0.55F, bx = x + s * 0.38F, by = y + s * 0.9F, cx = x + s, cy = y + s * 0.12F;
        stroke(g, ax, ay, bx, by, t, argb);
        stroke(g, bx, by, cx, cy, t, argb);
        disc(g, bx, by, t / 2, argb);
    }

    /** Die of the Random button: a thin square and five dots (7.3 px). */
    static void die(GuiGraphics g, float x, float y, int argb) {
        float s = 7.33F;
        border(g, x, y, s, s, 0.67F, argb);
        float[][] dots = {{2, 2}, {5.33F, 2}, {3.67F, 3.67F}, {2, 5.33F}, {5.33F, 5.33F}};
        for (float[] d : dots) {
            disc(g, x + d[0], y + d[1], 0.67F, argb);
        }
    }

    // ------------------------------------------------------------------ panels and buttons

    /** Glass panel of the mock-up: dark background at 78 %, a one-pixel border and a lighter edge at the top. */
    static void glass(GuiGraphics g, float x, float y, float w, float h, float px) {
        rect(g, x, y, x + w, y + h, a(GLASS, 199));
        border(g, x, y, w, h, px, a(WHITE, 26));
        rect(g, x + px, y + px, x + w - px, y + 2 * px, a(WHITE, 41));
    }

    /** Background of a button (secondary, or red when {@code primary}); {@code hover} from 0 to 1. */
    static void button(GuiGraphics g, float x, float y, float w, float h, boolean primary, float hover, float px) {
        if (primary) {
            gradientH(g, x, y, x + w, y + h, a(ACCENT, 77 + 34 * hover), a(ACCENT, 31 + 26 * hover));
            border(g, x, y, w, h, px, a(ACCENT, 140 + 60 * hover));
        } else {
            rect(g, x, y, x + w, y + h, a(WHITE, 13 + 9 * hover));
            border(g, x, y, w, h, px, a(WHITE, 31 + 30 * hover));
        }
    }

    /** Keyboard key (light chip) centred on {@code cy}; returns its width. */
    static float key(GuiGraphics g, Font font, String text, float x, float cy, float h, float spacing, float minW, int fill, float px) {
        String up = text.toUpperCase(Locale.ROOT);
        float tw = Ui.spacedWidth(font, up, MINI_BOLD, spacing);
        float w = Math.max(minW, tw + 4.67F);
        float y = cy - h / 2;
        rect(g, x, y, x + w, y + h, fill);
        border(g, x, y, w, h, px, a(WHITE, 61));
        Ui.spaced(g, font, up, MINI_BOLD, x + (w - tw) / 2, cy - 4.21F, a(WHITE, 255), spacing);
        return w;
    }

    /** Key of the shortcut row (23/10, rendu validé) : 7,5 px gras, hauteur et largeur mini au choix. */
    static float keyLarge(GuiGraphics g, Font font, String text, float x, float cy, float h, float spacing, float minW, int fill, float px) {
        String up = text.toUpperCase(Locale.ROOT);
        float tw = Ui.spacedWidth(font, up, TEXT_BOLD, spacing);
        float w = Math.max(minW, tw + 4.67F);
        float y = cy - h / 2;
        rect(g, x, y, x + w, y + h, fill);
        border(g, x, y, w, h, px, a(WHITE, 61));
        Ui.spaced(g, font, up, TEXT_BOLD, x + (w - tw) / 2, cy - 4.9F, a(WHITE, 255), spacing);
        return w;
    }

    /** Arrow key drawn as a small centred triangle (dir : 0 up, 1 down, 2 left, 3 right) instead of a font glyph. */
    static float keyArrow(GuiGraphics g, float x, float cy, float h, float w, int dir, int fill, float px) {
        float y = cy - h / 2;
        rect(g, x, y, x + w, y + h, fill);
        border(g, x, y, w, h, px, a(WHITE, 61));
        triangle(g, x + w / 2, cy, h * 0.26F, dir, a(WHITE, 255));
        return w;
    }

    /** Filled triangle pointing up / down / left / right, centred on (cx, cy), half-size k, drawn as thin strips. */
    static void triangle(GuiGraphics g, float cx, float cy, float k, int dir, int argb) {
        int n = 12;
        float len = 1.8F * k;
        for (int i = 0; i < n; i++) {
            float t0 = (float) i / n, t1 = (float) (i + 1) / n;
            float half = k * 1.1F * (t0 + t1) / 2;
            switch (dir) {
                case 0 -> rect(g, cx - half, cy - k + t0 * len, cx + half, cy - k + t1 * len, argb);
                case 1 -> rect(g, cx - half, cy + k - t1 * len, cx + half, cy + k - t0 * len, argb);
                case 2 -> rect(g, cx - k + t0 * len, cy - half, cx - k + t1 * len, cy + half, argb);
                default -> rect(g, cx + k - t1 * len, cy - half, cx + k - t0 * len, cy + half, argb);
            }
        }
    }

    /** Chevron ‹ or › drawn with two strokes, centred on (cx, cy). */
    static void chevron(GuiGraphics g, float cx, float cy, float k, boolean left, float t, int argb) {
        float s = left ? 1 : -1;
        float tipX = cx - s * k * 0.55F, backX = cx + s * k * 0.45F;
        stroke(g, backX, cy - k, tipX, cy, t, argb);
        stroke(g, tipX, cy, backX, cy + k, t, argb);
        disc(g, tipX, cy, t / 2, argb);
    }

    static float keyWidth(Font font, String text, float spacing, float minW) {
        return Math.max(minW, Ui.spacedWidth(font, text.toUpperCase(Locale.ROOT), MINI_BOLD, spacing) + 4.67F);
    }

    // ------------------------------------------------------------------ text

    /** Text at a free position (nothing below an opacity of 4: Minecraft would draw it opaque). */
    static void text(GuiGraphics g, Font font, Component c, float x, float y, int argb) {
        if (argb >>> 24 < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    static void text(GuiGraphics g, Font font, Component c, float x, float y, float scale, int argb) {
        if (argb >>> 24 < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Spaced capitals with their left edge at x; returns their width. */
    static float caps(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float spacing, int argb) {
        String up = s.toUpperCase(Locale.ROOT);
        if (argb >>> 24 >= 4) {
            Ui.spaced(g, font, up, f, x, y, argb, spacing);
        }
        return Ui.spacedWidth(font, up, f, spacing);
    }

    /** Spaced capitals drawn at {@code scale} (the spacing is in screen layout pixels); returns their width. */
    static float caps(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float scale, float spacing, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        float w = caps(g, font, s, f, 0, 0, spacing / scale, argb);
        g.pose().popPose();
        return w * scale;
    }

    static float capsWidth(Font font, String s, ResourceLocation f, float spacing) {
        return Ui.spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
    }

    static MutableComponent lit(String s, ResourceLocation f) {
        return Ui.lit(s, f);
    }

    static float width(Font font, String s, ResourceLocation f) {
        return font.width(Ui.lit(s, f));
    }
}
