package com.heraliz.extraction.preview;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Mini rastériseur logiciel (z-buffer, texture, éclairage simple) pour prévisualiser les modèles hors jeu. */
final class SoftRaster {
    static final int FULL_BRIGHT = 15728880;

    record Vtx(float x, float y, float z, float u, float v, float r, float g, float b, float a,
               float nx, float ny, float nz, int light) {
    }

    record Tri(Vtx a, Vtx b, Vtx c, BufferedImage tex) {
    }

    final List<Tri> tris = new ArrayList<>();

    /** VertexConsumer qui capture les quads émis par le code du jeu. */
    VertexConsumer consumer(BufferedImage texture) {
        return new VertexConsumer() {
            final List<Vtx> quad = new ArrayList<>();
            double x, y, z;
            float u, v, r = 1, g = 1, b = 1, a = 1, nx, ny = 1, nz;
            int light;

            @Override public VertexConsumer vertex(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
            @Override public VertexConsumer color(int r, int g, int b, int a) { this.r = r / 255f; this.g = g / 255f; this.b = b / 255f; this.a = a / 255f; return this; }
            @Override public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
            @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
            @Override public VertexConsumer uv2(int u, int v) { this.light = u | (v << 16); return this; }
            @Override public VertexConsumer normal(float x, float y, float z) { this.nx = x; this.ny = y; this.nz = z; return this; }
            @Override public void defaultColor(int r, int g, int b, int a) { }
            @Override public void unsetDefaultColor() { }

            @Override
            public void endVertex() {
                quad.add(new Vtx((float) x, (float) y, (float) z, u, v, r, g, b, a, nx, ny, nz, light));
                if (quad.size() == 4) {
                    tris.add(new Tri(quad.get(0), quad.get(1), quad.get(2), texture));
                    tris.add(new Tri(quad.get(0), quad.get(2), quad.get(3), texture));
                    quad.clear();
                }
            }
        };
    }

    /** Ajoute un quad de couleur unie (sol). */
    void solidQuad(float[][] p, float r, float g, float b) {
        Vtx[] v = new Vtx[4];
        for (int i = 0; i < 4; i++) {
            v[i] = new Vtx(p[i][0], p[i][1], p[i][2], 0, 0, r, g, b, 1, 0, 1, 0, 0);
        }
        tris.add(new Tri(v[0], v[1], v[2], null));
        tris.add(new Tri(v[0], v[2], v[3], null));
    }

    BufferedImage render(Matrix4f viewProj, int w, int h, int bgTop, int bgBottom) {
        int ss = 2;
        int W = w * ss, H = h * ss;
        float[] depth = new float[W * H];
        int[] color = new int[W * H];
        for (int y = 0; y < H; y++) {
            int c = lerpColor(bgTop, bgBottom, (float) y / H);
            for (int x = 0; x < W; x++) {
                color[y * W + x] = c;
                depth[y * W + x] = Float.MAX_VALUE;
            }
        }
        Vector3f light = new Vector3f(0.35f, 1.0f, 0.55f).normalize();

        for (Tri t : tris) {
            Vtx[] vs = {t.a(), t.b(), t.c()};
            float[] sx = new float[3], sy = new float[3], sz = new float[3], iw = new float[3];
            boolean skip = false;
            for (int i = 0; i < 3; i++) {
                Vector4f p = new Vector4f(vs[i].x(), vs[i].y(), vs[i].z(), 1).mul(viewProj);
                if (p.w <= 0.01f) {
                    skip = true;
                    break;
                }
                iw[i] = 1f / p.w;
                sx[i] = (p.x * iw[i] * 0.5f + 0.5f) * W;
                sy[i] = (1f - (p.y * iw[i] * 0.5f + 0.5f)) * H;
                sz[i] = p.z * iw[i];
            }
            if (skip) {
                continue;
            }
            float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
            if (Math.abs(area) < 1e-6f) {
                continue;
            }
            // éclairage par face (double face)
            Vtx a = vs[0];
            float dot = a.nx() * light.x + a.ny() * light.y + a.nz() * light.z;
            float shade = a.light() == FULL_BRIGHT ? 1.0f : 0.42f + 0.58f * Math.abs(dot);

            int minX = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
            int maxX = Math.min(W - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
            int minY = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
            int maxY = Math.min(H - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    float cx = px + 0.5f, cy = py + 0.5f;
                    float w0 = ((sx[1] - cx) * (sy[2] - cy) - (sx[2] - cx) * (sy[1] - cy)) / area;
                    float w1 = ((sx[2] - cx) * (sy[0] - cy) - (sx[0] - cx) * (sy[2] - cy)) / area;
                    float w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) {
                        continue;
                    }
                    float z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2];
                    int idx = py * W + px;
                    if (z >= depth[idx]) {
                        continue;
                    }
                    float pw = w0 * iw[0] + w1 * iw[1] + w2 * iw[2];
                    float u = (w0 * vs[0].u() * iw[0] + w1 * vs[1].u() * iw[1] + w2 * vs[2].u() * iw[2]) / pw;
                    float v = (w0 * vs[0].v() * iw[0] + w1 * vs[1].v() * iw[1] + w2 * vs[2].v() * iw[2]) / pw;
                    float r = a.r(), g = a.g(), b = a.b(), al = 1;
                    if (t.tex() != null) {
                        int tx = clamp((int) Math.floor(u * t.tex().getWidth()), t.tex().getWidth());
                        int ty = clamp((int) Math.floor(v * t.tex().getHeight()), t.tex().getHeight());
                        int argb = t.tex().getRGB(tx, ty);
                        al = ((argb >>> 24) & 0xFF) / 255f;
                        if (al < 0.1f) {
                            continue;
                        }
                        r *= ((argb >> 16) & 0xFF) / 255f;
                        g *= ((argb >> 8) & 0xFF) / 255f;
                        b *= (argb & 0xFF) / 255f;
                    }
                    depth[idx] = z;
                    color[idx] = rgb(r * shade, g * shade, b * shade);
                }
            }
        }

        BufferedImage big = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        big.setRGB(0, 0, W, H, color, 0, W);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(big, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    private static int rgb(float r, float g, float b) {
        return (Math.min(255, (int) (r * 255)) << 16) | (Math.min(255, (int) (g * 255)) << 8) | Math.min(255, (int) (b * 255));
    }

    private static int lerpColor(int c0, int c1, float t) {
        int r = (int) (((c0 >> 16) & 0xFF) * (1 - t) + ((c1 >> 16) & 0xFF) * t);
        int g = (int) (((c0 >> 8) & 0xFF) * (1 - t) + ((c1 >> 8) & 0xFF) * t);
        int b = (int) ((c0 & 0xFF) * (1 - t) + (c1 & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }
}
