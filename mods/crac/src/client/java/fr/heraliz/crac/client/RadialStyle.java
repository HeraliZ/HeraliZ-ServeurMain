package fr.heraliz.crac.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class RadialStyle {
    public static final float R_IN = 36.0F;
    public static final float R_OUT = 88.0F;
    public static final float R_OUT_HOV = 95.0F;
    public static final float GAP_PX = 2.5F;
    public static final int DEADZONE = 36;
    public static final int BG = 805306368;
    public static final int SEG = -938337752;
    public static final int SEG_HOVER = -385906146;
    public static final int SEG_ACTIVE = -668050776;
    public static final int SEG_OFF = -937944288;
    public static final int RING = 1728053247;
    public static final int HUB = -804647144;
    private static BufferBuilder buffer;

    private RadialStyle() {
    }

    public static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void end() {
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        buffer = null;
    }

    public static void segment(Matrix4f mat, double cx, double cy, double rIn, double rOut, double a0, double a1, int argb) {
        double gIn = 2.5 / rIn;
        double gOut = 2.5 / rOut;
        double i0 = a0 + gIn;
        double i1 = a1 - gIn;
        double o0 = a0 + gOut;
        double o1 = a1 - gOut;
        if (!(i1 <= i0) && !(o1 <= o0)) {
            int steps = 18;

            for (int s = 0; s < 18; s++) {
                double t0 = s / 18.0;
                double t1 = (s + 1) / 18.0;
                double ia0 = i0 + (i1 - i0) * t0;
                double ia1 = i0 + (i1 - i0) * t1;
                double oa0 = o0 + (o1 - o0) * t0;
                double oa1 = o0 + (o1 - o0) * t1;
                double ix0 = cx + Math.cos(ia0) * rIn;
                double iy0 = cy + Math.sin(ia0) * rIn;
                double ix1 = cx + Math.cos(ia1) * rIn;
                double iy1 = cy + Math.sin(ia1) * rIn;
                double ox0 = cx + Math.cos(oa0) * rOut;
                double oy0 = cy + Math.sin(oa0) * rOut;
                double ox1 = cx + Math.cos(oa1) * rOut;
                double oy1 = cy + Math.sin(oa1) * rOut;
                tri(mat, ix0, iy0, ox0, oy0, ox1, oy1, argb);
                tri(mat, ix0, iy0, ox1, oy1, ix1, iy1, argb);
            }
        }
    }

    public static void ring(Matrix4f mat, double cx, double cy, double r, double thickness, int argb) {
        int steps = 72;
        double rIn = r - thickness / 2.0;
        double rOut = r + thickness / 2.0;

        for (int s = 0; s < 72; s++) {
            double a0 = s / 72.0 * Math.PI * 2.0;
            double a1 = (s + 1) / 72.0 * Math.PI * 2.0;
            double ix0 = cx + Math.cos(a0) * rIn;
            double iy0 = cy + Math.sin(a0) * rIn;
            double ix1 = cx + Math.cos(a1) * rIn;
            double iy1 = cy + Math.sin(a1) * rIn;
            double ox0 = cx + Math.cos(a0) * rOut;
            double oy0 = cy + Math.sin(a0) * rOut;
            double ox1 = cx + Math.cos(a1) * rOut;
            double oy1 = cy + Math.sin(a1) * rOut;
            tri(mat, ix0, iy0, ox0, oy0, ox1, oy1, argb);
            tri(mat, ix0, iy0, ox1, oy1, ix1, iy1, argb);
        }
    }

    public static void disc(Matrix4f mat, double cx, double cy, double r, int argb) {
        int steps = 48;

        for (int s = 0; s < 48; s++) {
            double a0 = s / 48.0 * Math.PI * 2.0;
            double a1 = (s + 1) / 48.0 * Math.PI * 2.0;
            tri(mat, cx, cy, cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, argb);
        }
    }

    private static void tri(Matrix4f mat, double x0, double y0, double x1, double y1, double x2, double y2, int argb) {
        float a = (argb >>> 24) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        buffer.vertex(mat, (float)x0, (float)y0, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(mat, (float)x1, (float)y1, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(mat, (float)x2, (float)y2, 0.0F).color(r, g, b, a).endVertex();
    }

    public static int pick(double dx, double dy, double base, int n) {
        if (Math.sqrt(dx * dx + dy * dy) < 36.0) {
            return -1;
        } else {
            double twoPi = Math.PI * 2;
            double rel = (Math.atan2(dy, dx) - base) % twoPi;
            if (rel < 0.0) {
                rel += twoPi;
            }

            return Math.min(n - 1, (int)(rel / (twoPi / n)));
        }
    }

    public static void dim(GuiGraphics g, int w, int h) {
        g.fill(0, 0, w, h, 805306368);
    }
}
