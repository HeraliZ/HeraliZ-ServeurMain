package fr.heraliz.crac.build.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cuts piece meshes into per-block fragments (Sutherland-Hodgman against block cubes, UVs interpolated) and splits
 * faces on texture tile borders so every fragment maps into a single sprite.
 */
public final class MeshClipper {
    /** Fragments smaller than this (px^2) are dropped. */
    public static final double MIN_AREA = 1e-4;
    /** A face lying on a block border belongs to the block behind it (the side its normal points away from). */
    private static final double BIAS = 1e-3;

    private MeshClipper() {
    }

    /** Keeps the part of the polygon with coordinate {@code axis} (0 x, 1 y, 2 z) >= bound (keepGreater) or <= bound. */
    public static MeshFace clip(MeshFace f, int axis, double bound, boolean keepGreater) {
        int n = f.size();
        boolean allIn = true;
        boolean allOut = true;
        for (int i = 0; i < n; i++) {
            double c = f.pos[i * 3 + axis];
            boolean in = keepGreater ? c >= bound : c <= bound;
            allIn &= in;
            allOut &= !in;
        }
        if (allIn) {
            return f;
        }
        if (allOut) {
            return null;
        }
        double[] pos = new double[(n + 2) * 3];
        double[] uv = new double[(n + 2) * 2];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int p = (i + n - 1) % n;
            double cc = f.pos[i * 3 + axis];
            double pc = f.pos[p * 3 + axis];
            boolean curIn = keepGreater ? cc >= bound : cc <= bound;
            boolean prevIn = keepGreater ? pc >= bound : pc <= bound;
            if (curIn != prevIn) {
                double t = (bound - pc) / (cc - pc);
                for (int k = 0; k < 3; k++) {
                    pos[m * 3 + k] = k == axis ? bound : f.pos[p * 3 + k] + (f.pos[i * 3 + k] - f.pos[p * 3 + k]) * t;
                }
                for (int k = 0; k < 2; k++) {
                    uv[m * 2 + k] = f.uv[p * 2 + k] + (f.uv[i * 2 + k] - f.uv[p * 2 + k]) * t;
                }
                m++;
            }
            if (curIn) {
                System.arraycopy(f.pos, i * 3, pos, m * 3, 3);
                System.arraycopy(f.uv, i * 2, uv, m * 2, 2);
                m++;
            }
        }
        if (m < 3) {
            return null;
        }
        double[] outPos = new double[m * 3];
        double[] outUv = new double[m * 2];
        System.arraycopy(pos, 0, outPos, 0, m * 3);
        System.arraycopy(uv, 0, outUv, 0, m * 2);
        MeshFace out = f.withGeometry(outPos, outUv);
        return out.area() < MIN_AREA ? null : out;
    }

    /** Same as {@link #clip} on the texture coordinates (component 0 u, 1 v). */
    public static MeshFace clipUv(MeshFace f, int comp, double bound, boolean keepGreater) {
        int n = f.size();
        boolean allIn = true;
        boolean allOut = true;
        for (int i = 0; i < n; i++) {
            double c = f.uv[i * 2 + comp];
            boolean in = keepGreater ? c >= bound : c <= bound;
            allIn &= in;
            allOut &= !in;
        }
        if (allIn) {
            return f;
        }
        if (allOut) {
            return null;
        }
        double[] pos = new double[(n + 2) * 3];
        double[] uv = new double[(n + 2) * 2];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int p = (i + n - 1) % n;
            double cc = f.uv[i * 2 + comp];
            double pc = f.uv[p * 2 + comp];
            boolean curIn = keepGreater ? cc >= bound : cc <= bound;
            boolean prevIn = keepGreater ? pc >= bound : pc <= bound;
            if (curIn != prevIn) {
                double t = (bound - pc) / (cc - pc);
                for (int k = 0; k < 3; k++) {
                    pos[m * 3 + k] = f.pos[p * 3 + k] + (f.pos[i * 3 + k] - f.pos[p * 3 + k]) * t;
                }
                for (int k = 0; k < 2; k++) {
                    uv[m * 2 + k] = k == comp ? bound : f.uv[p * 2 + k] + (f.uv[i * 2 + k] - f.uv[p * 2 + k]) * t;
                }
                m++;
            }
            if (curIn) {
                System.arraycopy(f.pos, i * 3, pos, m * 3, 3);
                System.arraycopy(f.uv, i * 2, uv, m * 2, 2);
                m++;
            }
        }
        if (m < 3) {
            return null;
        }
        double[] outPos = new double[m * 3];
        double[] outUv = new double[m * 2];
        System.arraycopy(pos, 0, outPos, 0, m * 3);
        System.arraycopy(uv, 0, outUv, 0, m * 2);
        MeshFace out = f.withGeometry(outPos, outUv);
        return out.area() < MIN_AREA ? null : out;
    }

    /** Clip to the axis-aligned box [x0,x1] x [y0,y1] x [z0,z1]; null when nothing is left. */
    public static MeshFace clipBox(MeshFace f, double x0, double y0, double z0, double x1, double y1, double z1) {
        MeshFace c = clip(f, 0, x0, true);
        if (c != null) c = clip(c, 0, x1, false);
        if (c != null) c = clip(c, 1, y0, true);
        if (c != null) c = clip(c, 1, y1, false);
        if (c != null) c = clip(c, 2, z0, true);
        if (c != null) c = clip(c, 2, z1, false);
        return c;
    }

    /** Splits a face on integer u and v lines and brings every piece back into the [0, 1] tile. */
    public static void splitTiles(MeshFace f, List<MeshFace> out) {
        double u0 = Double.MAX_VALUE;
        double u1 = -Double.MAX_VALUE;
        double v0 = Double.MAX_VALUE;
        double v1 = -Double.MAX_VALUE;
        for (int i = 0; i < f.size(); i++) {
            u0 = Math.min(u0, f.u(i));
            u1 = Math.max(u1, f.u(i));
            v0 = Math.min(v0, f.v(i));
            v1 = Math.max(v1, f.v(i));
        }
        int iu0 = (int) Math.floor(u0 + 1e-6);
        int iu1 = (int) Math.ceil(u1 - 1e-6);
        int iv0 = (int) Math.floor(v0 + 1e-6);
        int iv1 = (int) Math.ceil(v1 - 1e-6);
        for (int iu = iu0; iu < Math.max(iu1, iu0 + 1); iu++) {
            MeshFace cu = f;
            if (iu1 - iu0 > 1) {
                cu = clipUv(cu, 0, iu, true);
                if (cu != null) cu = clipUv(cu, 0, iu + 1, false);
            }
            if (cu == null) continue;
            for (int iv = iv0; iv < Math.max(iv1, iv0 + 1); iv++) {
                MeshFace c = cu;
                if (iv1 - iv0 > 1) {
                    c = clipUv(c, 1, iv, true);
                    if (c != null) c = clipUv(c, 1, iv + 1, false);
                }
                if (c == null) continue;
                if (iu != 0 || iv != 0) {
                    double[] uv = c.uv.clone();
                    for (int i = 0; i < uv.length; i += 2) {
                        uv[i] -= iu;
                        uv[i + 1] -= iv;
                    }
                    c = c.withGeometry(c.pos, uv);
                }
                out.add(c);
            }
        }
    }

    public static List<MeshFace> splitTiles(List<MeshFace> faces) {
        List<MeshFace> out = new ArrayList<>(faces.size() + faces.size() / 2);
        for (MeshFace f : faces) {
            splitTiles(f, out);
        }
        return out;
    }

    public static long blockKey(int bx, int by, int bz) {
        return ((long) (bx + 512) << 20) | ((long) (by + 512) << 10) | (bz + 512);
    }

    public static int keyX(long k) {
        return (int) (k >> 20 & 1023) - 512;
    }

    public static int keyY(long k) {
        return (int) (k >> 10 & 1023) - 512;
    }

    public static int keyZ(long k) {
        return (int) (k & 1023) - 512;
    }

    /**
     * Cuts the faces into the 16 px blocks of the piece. Every fragment goes to the block containing it when that block
     * is one of {@code reserved} (offsets {bx, by, bz}), else to the nearest reserved block. Positions stay in
     * piece-local pixels. Faces on a block border go to the block behind them.
     */
    public static Map<Long, List<MeshFace>> byBlock(List<MeshFace> faces, List<int[]> reserved) {
        Map<Long, List<MeshFace>> out = new HashMap<>();
        Set<Long> res = new HashSet<>();
        for (int[] o : reserved) {
            res.add(blockKey(o[0], o[1], o[2]));
        }
        for (MeshFace f : faces) {
            MeshFace s = f.offset(-BIAS);
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (int i = 0; i < s.size(); i++) {
                minX = Math.min(minX, s.x(i));
                minY = Math.min(minY, s.y(i));
                minZ = Math.min(minZ, s.z(i));
                maxX = Math.max(maxX, s.x(i));
                maxY = Math.max(maxY, s.y(i));
                maxZ = Math.max(maxZ, s.z(i));
            }
            int bx0 = (int) Math.floor(minX / 16);
            int by0 = (int) Math.floor(minY / 16);
            int bz0 = (int) Math.floor(minZ / 16);
            int bx1 = (int) Math.floor(maxX / 16);
            int by1 = (int) Math.floor(maxY / 16);
            int bz1 = (int) Math.floor(maxZ / 16);
            for (int by = by0; by <= by1; by++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int bx = bx0; bx <= bx1; bx++) {
                        MeshFace c = clipBox(s, bx * 16, by * 16, bz * 16, bx * 16 + 16, by * 16 + 16, bz * 16 + 16);
                        if (c == null) continue;
                        c = c.offset(BIAS);
                        long key = blockKey(bx, by, bz);
                        if (!res.contains(key)) {
                            key = nearest(c, reserved);
                        }
                        out.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
                    }
                }
            }
        }
        return out;
    }

    private static long nearest(MeshFace f, List<int[]> reserved) {
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (int i = 0; i < f.size(); i++) {
            cx += f.x(i);
            cy += f.y(i);
            cz += f.z(i);
        }
        cx /= f.size();
        cy /= f.size();
        cz /= f.size();
        double best = Double.MAX_VALUE;
        int[] bo = reserved.get(0);
        for (int[] o : reserved) {
            double dx = Math.max(Math.max(o[0] * 16 - cx, 0), cx - (o[0] * 16 + 16));
            double dy = Math.max(Math.max(o[1] * 16 - cy, 0), cy - (o[1] * 16 + 16));
            double dz = Math.max(Math.max(o[2] * 16 - cz, 0), cz - (o[2] * 16 + 16));
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                bo = o;
            }
        }
        return blockKey(bo[0], bo[1], bo[2]);
    }

    /**
     * Grows a convex polygon by {@code eps} pixels in its own plane (mitred corners). Neighbouring faces then overlap
     * a hair, which seals the pixel cracks a T-junction (a vertex on the middle of the neighbour's edge, e.g. from a
     * texture tile split) would otherwise leave. UVs are kept, so the border texel stretches over the margin.
     */
    public static MeshFace inflate(MeshFace f, double eps) {
        int n = f.size();
        double[] p = new double[n * 3];
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n;
            int b = (i + 1) % n;
            double[] n1 = edgeNormal(f, a, i);
            double[] n2 = edgeNormal(f, i, b);
            double den = Math.max(0.25, 1 + n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2]);
            for (int k = 0; k < 3; k++) {
                p[i * 3 + k] = f.pos[i * 3 + k] + (n1[k] + n2[k]) * eps / den;
            }
        }
        return f.withGeometry(p, f.uv);
    }

    /** In-plane outward normal of edge i -> j (edge x face normal for counter-clockwise faces). */
    private static double[] edgeNormal(MeshFace f, int i, int j) {
        double ex = f.x(j) - f.x(i);
        double ey = f.y(j) - f.y(i);
        double ez = f.z(j) - f.z(i);
        double cx = ey * f.nz - ez * f.ny;
        double cy = ez * f.nx - ex * f.nz;
        double cz = ex * f.ny - ey * f.nx;
        double l = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (l < 1e-12) {
            return new double[3];
        }
        return new double[]{cx / l, cy / l, cz / l};
    }
}
