package fr.heraliz.airstrike.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight replacement of {@code ModelPart}: same box-UV layout and pose semantics, but UVs are inset by a tiny
 * fraction of a texel so that nearest sampling never bleeds into the neighbouring face of the atlas.
 */
public final class GeoPart {
    private static final float UV_INSET = 0.035f;
    private static final float SMOOTH_UV_INSET = 0.01f;

    public final String name;
    public float x, y, z;
    public float xRot, yRot, zRot;
    public boolean visible = true;
    private final float ix, iy, iz, irx, iry, irz;
    final Map<String, GeoPart> children = new LinkedHashMap<>();
    /** Quads: 4 vertices x (x, y, z, u, v) followed by the normal (nx, ny, nz) = 23 floats each. */
    private float[] quads = new float[0];
    private int quadCount;
    /** Smooth quads: 4 vertices x (x, y, z, u, v, nx, ny, nz) = 32 floats each (rounded lighting). */
    private float[] smooth = new float[0];
    private int smoothCount;

    public GeoPart(String name, float x, float y, float z, float xRot, float yRot, float zRot) {
        this.name = name;
        this.x = this.ix = x;
        this.y = this.iy = y;
        this.z = this.iz = z;
        this.xRot = this.irx = xRot;
        this.yRot = this.iry = yRot;
        this.zRot = this.irz = zRot;
    }

    public GeoPart child(String name) {
        return children.get(name);
    }

    public boolean hasChild(String name) {
        return children.containsKey(name);
    }

    public void resetPose() {
        x = ix;
        y = iy;
        z = iz;
        xRot = irx;
        yRot = iry;
        zRot = irz;
    }

    public float initialXRot() {
        return irx;
    }

    public float initialYRot() {
        return iry;
    }

    public float initialZRot() {
        return irz;
    }

    /**
     * Adds a box (model units). {@code uvs} holds, for the faces down, up, west, north, east, south, the
     * rectangle (u1, v1, u2, v2) in texels, assigned to the corners exactly like vanilla {@code ModelPart.Polygon}.
     */
    public void addBox(float x0, float y0, float z0, float w, float h, float d, float grow, float[] uvs, float texW, float texH) {
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
        float gx0 = x0 - grow, gy0 = y0 - grow, gz0 = z0 - grow, gx1 = x1 + grow, gy1 = y1 + grow, gz1 = z1 + grow;
        float[] v7 = {gx0, gy0, gz0}, vA = {gx1, gy0, gz0}, v1 = {gx1, gy1, gz0}, v2 = {gx0, gy1, gz0};
        float[] v3 = {gx0, gy0, gz1}, v4 = {gx1, gy0, gz1}, v5 = {gx1, gy1, gz1}, v6 = {gx0, gy1, gz1};
        float[][][] faces = {
                {v4, v3, v7, vA}, {v1, v2, v6, v5}, {v7, v3, v6, v2}, {vA, v7, v2, v1}, {v4, vA, v1, v5}, {v3, v4, v5, v6}};
        float[][] normals = {{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}};
        List<float[]> list = new ArrayList<>(6);
        for (int f = 0; f < 6; f++) {
            float u1 = uvs[f * 4], vv1 = uvs[f * 4 + 1], u2 = uvs[f * 4 + 2], vv2 = uvs[f * 4 + 3];
            list.add(quad(faces[f], u1, vv1, u2, vv2, texW, texH, normals[f][0], normals[f][1], normals[f][2]));
        }
        float[] grown = new float[quads.length + list.size() * 23];
        System.arraycopy(quads, 0, grown, 0, quads.length);
        int o = quads.length;
        for (float[] q : list) {
            System.arraycopy(q, 0, grown, o, 23);
            o += 23;
        }
        quads = grown;
        quadCount += list.size();
    }

    /**
     * Adds a smooth quad: 4 corners (x, y, z, u, v, nx, ny, nz), positions in model units, u v in texels. The u v are
     * pulled a hair towards the middle of the quad so that a corner on the edge of its texture region never samples
     * the neighbouring texel.
     */
    public void addQuad(float[] corners, float texW, float texH) {
        float cu = 0f, cv = 0f;
        for (int i = 0; i < 4; i++) {
            cu += corners[i * 8 + 3] * 0.25f;
            cv += corners[i * 8 + 4] * 0.25f;
        }
        float[] grown = new float[smooth.length + 32];
        System.arraycopy(smooth, 0, grown, 0, smooth.length);
        int o = smooth.length;
        for (int i = 0; i < 4; i++) {
            int k = i * 8;
            float u = corners[k + 3], v = corners[k + 4];
            float du = cu - u, dv = cv - v, len = (float) Math.sqrt(du * du + dv * dv);
            float e = len > 1e-6f ? Math.min(SMOOTH_UV_INSET, len * 0.25f) / len : 0f;
            grown[o + k] = corners[k] / 16f;
            grown[o + k + 1] = corners[k + 1] / 16f;
            grown[o + k + 2] = corners[k + 2] / 16f;
            grown[o + k + 3] = (u + du * e) / texW;
            grown[o + k + 4] = (v + dv * e) / texH;
            grown[o + k + 5] = corners[k + 5];
            grown[o + k + 6] = corners[k + 6];
            grown[o + k + 7] = corners[k + 7];
        }
        smooth = grown;
        smoothCount++;
    }

    private static float inset(float a, float b, boolean first) {
        float span = Math.abs(b - a);
        float e = Math.min(UV_INSET, span * 0.25f);
        if (a <= b) return first ? a + e : b - e;
        return first ? a - e : b + e;
    }

    private static float[] quad(float[][] verts, float u1, float v1, float u2, float v2, float texW, float texH,
                                float nx, float ny, float nz) {
        // same corner assignment as ModelPart.Polygon, with a small inset
        float iu1 = inset(u1, u2, true), iu2 = inset(u1, u2, false);
        float iv1 = inset(v1, v2, true), iv2 = inset(v1, v2, false);
        float[][] uv = {{iu2, iv1}, {iu1, iv1}, {iu1, iv2}, {iu2, iv2}};
        float[] out = new float[23];
        for (int i = 0; i < 4; i++) {
            out[i * 5] = verts[i][0] / 16f;
            out[i * 5 + 1] = verts[i][1] / 16f;
            out[i * 5 + 2] = verts[i][2] / 16f;
            out[i * 5 + 3] = uv[i][0] / texW;
            out[i * 5 + 4] = uv[i][1] / texH;
        }
        out[20] = nx;
        out[21] = ny;
        out[22] = nz;
        return out;
    }

    // scratch objects (rendering happens on the render thread only): no garbage per part and per frame
    private static final Quaternionf ROT = new Quaternionf();
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector4f POS = new Vector4f();

    public void translateAndRotate(PoseStack ps) {
        ps.translate(x / 16f, y / 16f, z / 16f);
        if (xRot != 0f || yRot != 0f || zRot != 0f) {
            ps.mulPose(ROT.rotationZYX(zRot, yRot, xRot));
        }
    }

    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        if (!visible) return;
        if (quadCount == 0 && smoothCount == 0 && children.isEmpty()) return;
        ps.pushPose();
        translateAndRotate(ps);
        if (quadCount > 0 || smoothCount > 0) {
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            Vector3f nv = NORMAL;
            Vector4f pv = POS;
            for (int q = 0; q < quadCount; q++) {
                int o = q * 23;
                n.transform(nv.set(quads[o + 20], quads[o + 21], quads[o + 22]));
                for (int i = 0; i < 4; i++) {
                    int k = o + i * 5;
                    m.transform(pv.set(quads[k], quads[k + 1], quads[k + 2], 1f));
                    vc.vertex(pv.x(), pv.y(), pv.z(), r, g, b, a, quads[k + 3], quads[k + 4], overlay, light,
                            nv.x(), nv.y(), nv.z());
                }
            }
            for (int q = 0; q < smoothCount; q++) {
                for (int i = 0; i < 4; i++) {
                    int k = q * 32 + i * 8;
                    m.transform(pv.set(smooth[k], smooth[k + 1], smooth[k + 2], 1f));
                    n.transform(nv.set(smooth[k + 5], smooth[k + 6], smooth[k + 7]));
                    vc.vertex(pv.x(), pv.y(), pv.z(), r, g, b, a, smooth[k + 3], smooth[k + 4], overlay, light,
                            nv.x(), nv.y(), nv.z());
                }
            }
        }
        for (GeoPart c : children.values()) c.render(ps, vc, light, overlay, r, g, b, a);
        ps.popPose();
    }
}
