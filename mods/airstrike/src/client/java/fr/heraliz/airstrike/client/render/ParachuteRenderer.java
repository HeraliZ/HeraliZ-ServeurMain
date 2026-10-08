package fr.heraliz.airstrike.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.client.model.ModModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cluster of three round cargo canopies, built as smooth meshes every frame (so they can inflate, breathe and
 * collapse). Coordinates are blocks in the crate frame: origin = bottom centre of the pallet, y up.
 */
public final class ParachuteRenderer {
    public static final ResourceLocation TEXTURE = ModModels.texture("canopy");
    private static final int CANOPIES = 3;
    private static final int GORES = 20;
    private static final int SUB = 2;
    private static final int RINGS = 9;
    private static final float SPHERE_R = 4.3f;
    private static final float A_VENT = (float) Math.toRadians(8);
    private static final float A_SKIRT = (float) Math.toRadians(100);
    private static final float FLATTEN = 0.8f;
    private static final float SKIRT_Y = 16.0f;
    private static final float SPREAD = 4.6f;
    private static final float TILT = (float) Math.toRadians(17);
    private static final float RISER_Y = 7.6f;
    private static final float CONFLUENCE_Y = 4.8f;
    private static final float CORNER = 1.53f, CORNER_Y = 0.2f;
    private static final float[][] GORE_COLORS = {{0.36f, 0.39f, 0.25f}, {0.72f, 0.66f, 0.52f}};
    private static final float[] LINE_COLOR = {0.88f, 0.86f, 0.78f};
    private static final float[] RISER_COLOR = {0.30f, 0.33f, 0.21f};
    private static final float[] SLING_COLOR = {0.79f, 0.65f, 0.15f};

    /**
     * @param inflate  0 = packed streamers, 1 = fully open
     * @param age      animation time (ticks) for the breathing
     * @param collapse 0 = flying, 1 = lying on the ground downwind (+x)
     * @param alpha    final fade out
     */
    public record State(float inflate, float age, float collapse, float alpha) {
    }

    private ParachuteRenderer() {
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float easeOutBack(float x) {
        x = Mth.clamp(x, 0f, 1f) - 1f;
        return 1f + 2.4f * x * x * x + 1.4f * x * x;
    }

    /** Local frame of one canopy: skirt centre, axis and two horizontal-ish vectors. */
    private static final class Frame {
        final Vector3f c = new Vector3f(), u = new Vector3f(), x = new Vector3f(), z = new Vector3f();
        float sR, sY, stretch, wrinkle, phase;
    }

    private static Frame frame(int k, State s) {
        Frame f = new Frame();
        float psi = (float) Math.toRadians(90 + 120 * k);
        float dx = Mth.cos(psi), dz = Mth.sin(psi);
        float inf = s.inflate();
        float spread = SPREAD * (0.25f + 0.75f * smooth(inf));
        // flying: tilted outward around the vertical axis
        Vector3f c0 = new Vector3f(dx * spread, SKIRT_Y, dz * spread);
        float tilt = TILT * smooth(inf);
        Vector3f u0 = new Vector3f(dx * Mth.sin(tilt), Mth.cos(tilt), dz * Mth.sin(tilt));
        float breathe = Mth.sin(s.age() * 0.19f + k * 2.1f) * (1f - s.collapse());
        f.sR = (0.1f + 0.9f * easeOutBack(inf)) * (1f + 0.025f * breathe);
        f.sY = (1.8f - 0.8f * smooth(inf)) * (1f - 0.02f * breathe);
        f.phase = k * 1.7f;
        // landed: the canopies drift downwind, tip over and settle flat and crumpled on the ground
        float c = Mth.clamp((s.collapse() * 1.15f - k * 0.05f), 0f, 1f);
        if (c > 0f) {
            Vector3f end = new Vector3f(8.0f + 4.5f * k, 0.25f, (k - 1) * 4.5f);
            float h = smooth(c);
            float v = smooth(Math.min(1f, c * 1.35f));
            c0.set(Mth.lerp(h, c0.x, end.x), Mth.lerp(v, c0.y, end.y), Mth.lerp(h, c0.z, end.z));
            float lean = (float) Math.toRadians(38) * Mth.sin(Mth.PI * c);
            u0.lerp(new Vector3f(Mth.sin(lean), Mth.cos(lean), 0), h).normalize();
            // the fabric bunches up into a long heap lying downwind
            f.sY *= 1f - 0.86f * v;
            f.sR *= 1f - 0.38f * h;
            f.stretch = 0.6f * h;
            f.wrinkle = h;
        }
        f.c.set(c0);
        f.u.set(u0).normalize();
        // x axis = downwind direction made perpendicular to the canopy axis
        f.x.set(1, 0, 0).sub(new Vector3f(f.u).mul(f.u.x)).normalize();
        f.u.cross(f.x, f.z);
        f.z.negate();
        return f;
    }

    /** Point of the dome: seam coordinate g (gores, fractional) and ring coordinate i (0 = vent, RINGS = skirt). */
    private static Vector3f dome(Frame f, float g, float i) {
        float a = A_VENT + (A_SKIRT - A_VENT) * i / RINGS;
        float phi = Mth.TWO_PI * g / GORES;
        float lobe = Mth.sin(Mth.PI * (g - (float) Math.floor(g)));
        float r = SPHERE_R * Mth.sin(a) * f.sR * (1f + 0.045f * lobe);
        float h = FLATTEN * SPHERE_R * (Mth.cos(a) - Mth.cos(A_SKIRT)) * f.sY;
        float lx = r * Mth.cos(phi) * (1f + f.stretch), lz = r * Mth.sin(phi) * (1f - f.stretch * 0.75f);
        if (f.wrinkle > 0f) {
            // soft folds, larger towards the skirt
            float w = f.wrinkle * (0.4f + 0.6f * i / RINGS);
            h += w * (0.16f * Mth.sin(2f * phi + 3f * a + f.phase) + 0.08f * Mth.sin(5f * phi - 2f * a));
            lx += w * 0.14f * Mth.sin(3f * phi + f.phase);
            h = Math.max(h, -0.15f);
        }
        return new Vector3f(f.c).add(new Vector3f(f.x).mul(lx)).add(new Vector3f(f.u).mul(h)).add(new Vector3f(f.z).mul(lz));
    }

    public static void render(PoseStack ps, MultiBufferSource buffers, int light, State s) {
        if (s.alpha() <= 0.01f) return;
        PoseStack.Pose pose = ps.last();
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();
        VertexConsumer cloth = buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        VertexConsumer cords = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float alpha = s.alpha();
        float collapse = s.collapse();
        Vector3f confluence = new Vector3f(0, CONFLUENCE_Y, 0);
        if (collapse > 0f) {
            float h = smooth(collapse * 1.3f);
            confluence.set(Mth.lerp(h, 0f, 3.2f), Mth.lerp(h, CONFLUENCE_Y, 0.15f), 0f);
        }
        float sag = 1.6f * smooth(collapse * 1.2f);

        for (int k = 0; k < CANOPIES; k++) {
            Frame f = frame(k, s);
            // --- canopy
            for (int g = 0; g < GORES; g++) {
                float[] col = GORE_COLORS[g % 2];
                for (int j = 0; j < SUB; j++) {
                    float g0 = g + j / (float) SUB, g1 = g + (j + 1) / (float) SUB;
                    float u0 = 0.75f * j / SUB, u1 = 0.75f * (j + 1) / SUB;
                    for (int i = 0; i < RINGS; i++) {
                        Vector3f p00 = dome(f, g0, i), p10 = dome(f, g1, i), p11 = dome(f, g1, i + 1), p01 = dome(f, g0, i + 1);
                        float v0 = i / (float) RINGS, v1 = (i + 1) / (float) RINGS;
                        Vector3f n = new Vector3f(p10).sub(p00).cross(new Vector3f(p01).sub(p00)).normalize();
                        Vector3f mid = new Vector3f(p00).add(p11).mul(0.5f);
                        Vector3f inside = new Vector3f(f.c).add(new Vector3f(f.u).mul(0.6f));
                        if (n.dot(new Vector3f(mid).sub(inside)) < 0) n.negate();
                        if (!Float.isFinite(n.x)) continue;
                        float shade = 0.85f + 0.15f * (i / (float) RINGS);
                        quad(cloth, mat, nrm, p00, p10, p11, p01, u0, u1, v0, v1, n, col[0] * shade, col[1] * shade, col[2] * shade, alpha, light);
                        // inner face, slightly darker, visible from below
                        Vector3f off = new Vector3f(n).mul(-0.04f);
                        Vector3f in = new Vector3f(n).negate();
                        quad(cloth, mat, nrm, new Vector3f(p00).add(off), new Vector3f(p10).add(off), new Vector3f(p11).add(off),
                                new Vector3f(p01).add(off), u0, u1, v0, v1, in, col[0] * 0.62f, col[1] * 0.62f, col[2] * 0.6f, alpha, light);
                    }
                }
            }
            // --- suspension lines from the skirt seams to the riser
            float psi = (float) Math.toRadians(90 + 120 * k);
            float inf = smooth(s.inflate());
            Vector3f riser = new Vector3f(Mth.cos(psi) * 0.9f * inf, RISER_Y, Mth.sin(psi) * 0.9f * inf);
            if (collapse > 0f) {
                float h = smooth(collapse * 1.3f);
                riser.lerp(new Vector3f(5.5f + 1.5f * k, 0.12f, (k - 1) * 3.0f), h);
            }
            for (int g = 0; g < GORES; g++) {
                cord(cords, mat, nrm, dome(f, g, RINGS), riser, 0.025f, LINE_COLOR, alpha, light, sag * 0.6f);
            }
            cord(cords, mat, nrm, riser, confluence, 0.09f, RISER_COLOR, alpha, light, sag * 0.4f);
        }
        // --- sling from the pallet corners to the confluence ring
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                cord(cords, mat, nrm, new Vector3f(sx * CORNER, CORNER_Y, sz * CORNER), confluence, 0.11f, SLING_COLOR, alpha, light, sag * 0.3f);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, Vector3f p, float u, float v, Vector3f n,
                               float r, float g, float b, float a, int light) {
        vc.vertex(mat, p.x, p.y, p.z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(nrm, n.x, n.y, n.z).endVertex();
    }

    /** Quad facing n (vertex order fixed so that back face culling keeps the side n points to). */
    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, Vector3f p00, Vector3f p10, Vector3f p11,
                             Vector3f p01, float u0, float u1, float v0, float v1, Vector3f n, float r, float g, float b,
                             float a, int light) {
        Vector3f face = new Vector3f(p10).sub(p00).cross(new Vector3f(p11).sub(p00));
        if (face.dot(n) >= 0) {
            vertex(vc, mat, nrm, p00, u0, v0, n, r, g, b, a, light);
            vertex(vc, mat, nrm, p10, u1, v0, n, r, g, b, a, light);
            vertex(vc, mat, nrm, p11, u1, v1, n, r, g, b, a, light);
            vertex(vc, mat, nrm, p01, u0, v1, n, r, g, b, a, light);
        } else {
            vertex(vc, mat, nrm, p01, u0, v1, n, r, g, b, a, light);
            vertex(vc, mat, nrm, p11, u1, v1, n, r, g, b, a, light);
            vertex(vc, mat, nrm, p10, u1, v0, n, r, g, b, a, light);
            vertex(vc, mat, nrm, p00, u0, v0, n, r, g, b, a, light);
        }
    }

    /** Cord or strap: two crossed ribbons, sagging (quadratic curve resting on the ground) once slack. */
    private static void cord(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, Vector3f a, Vector3f b, float width,
                             float[] col, float alpha, int light, float sag) {
        int segs = sag > 0.01f ? 6 : 1;
        Vector3f prev = new Vector3f(a);
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            Vector3f p = new Vector3f(a).lerp(b, t);
            if (segs > 1) {
                p.y -= sag * 4f * t * (1f - t);
                p.y = Math.max(p.y, 0.06f);
            }
            ribbon(vc, mat, nrm, prev, p, width, col, alpha, light);
            prev = p;
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, Vector3f a, Vector3f b, float width,
                               float[] col, float alpha, int light) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1e-8f) return;
        d.normalize();
        Vector3f side1 = Math.abs(d.y) < 0.95f ? new Vector3f(d).cross(0, 1, 0) : new Vector3f(d).cross(1, 0, 0);
        side1.normalize().mul(width * 0.5f);
        Vector3f side2 = new Vector3f(d).cross(side1).normalize().mul(width * 0.5f);
        for (Vector3f side : new Vector3f[]{side1, side2}) {
            Vector3f n = new Vector3f(d).cross(side).normalize();
            vertex(vc, mat, nrm, new Vector3f(a).sub(side), 0.9f, 0.4f, n, col[0], col[1], col[2], alpha, light);
            vertex(vc, mat, nrm, new Vector3f(a).add(side), 0.95f, 0.4f, n, col[0], col[1], col[2], alpha, light);
            vertex(vc, mat, nrm, new Vector3f(b).add(side), 0.95f, 0.6f, n, col[0], col[1], col[2], alpha, light);
            vertex(vc, mat, nrm, new Vector3f(b).sub(side), 0.9f, 0.6f, n, col[0], col[1], col[2], alpha, light);
        }
    }
}
