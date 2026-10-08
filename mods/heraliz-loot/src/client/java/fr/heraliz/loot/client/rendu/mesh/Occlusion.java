package fr.heraliz.loot.client.rendu.mesh;

import java.util.HashMap;
import java.util.Map;

/**
 * Occlusion ambiante precalculee (par sommet) : les creux, l'interieur des cavites et le contact avec
 * le sol s'assombrissent naturellement. La geometrie est "voxelisee" puis on lance des rayons
 * depuis chaque sommet dans l'hemisphere de sa normale.
 */
public final class Occlusion {
    private Occlusion() {
    }

    private static final float[][] DIRS = fibonacci(64);

    /**
     * @param target    maillage en construction dont les sommets seront assombris
     * @param voxel     taille d'un voxel (px)
     * @param dist      portee des rayons (px)
     * @param strength  assombrissement maximal (0..1)
     * @param groundY   hauteur du sol (px) qui occulte aussi, ou NaN
     * @param occluders autres maillages qui projettent de l'ombre (non modifies)
     */
    public static void bake(MeshBuilder target, float voxel, float dist, float strength, float groundY, Mesh... occluders) {
        float[] tdata = target.rawData();
        int tsize = target.rawSize();
        if (tsize == 0) return;
        final int S = Mesh.STRIDE;
        // ---------------------------------------------------------------- bornes
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        bounds(tdata, tsize, lo, hi);
        for (Mesh m : occluders) if (m != null) bounds(m.data(), m.vertexCount() * S, lo, hi);
        for (int k = 0; k < 3; k++) {
            lo[k] -= dist + voxel * 2;
            hi[k] += dist + voxel * 2;
        }
        int nx = (int) Math.ceil((hi[0] - lo[0]) / voxel) + 1;
        int ny = (int) Math.ceil((hi[1] - lo[1]) / voxel) + 1;
        int nz = (int) Math.ceil((hi[2] - lo[2]) / voxel) + 1;
        long cells = (long) nx * ny * nz;
        if (cells > 40_000_000L) return; // securite memoire
        boolean[] grid = new boolean[(int) cells];
        Grid g = new Grid(grid, nx, ny, nz, lo, voxel);
        g.fill(tdata, tsize);
        for (Mesh m : occluders) if (m != null) g.fill(m.data(), m.vertexCount() * S);

        // ---------------------------------------------------------------- rayons
        Map<Long, Float> cache = new HashMap<>();
        float step = voxel * 0.6f;
        float start = voxel * 1.6f;
        for (int i = 0; i < tsize; i += S) {
            float px = tdata[i], py = tdata[i + 1], pz = tdata[i + 2];
            float qx = tdata[i + 5], qy = tdata[i + 6], qz = tdata[i + 7];
            long key = quant(px, 0.25f) * 73856093L ^ quant(py, 0.25f) * 19349663L ^ quant(pz, 0.25f) * 83492791L
                    ^ quant(qx, 0.1f) * 2654435761L ^ quant(qy, 0.1f) * 40503L ^ quant(qz, 0.1f) * 97L;
            Float cached = cache.get(key);
            float ao;
            if (cached != null) {
                ao = cached;
            } else {
                float occ = 0, wsum = 0;
                for (float[] d : DIRS) {
                    float c = d[0] * qx + d[1] * qy + d[2] * qz;
                    if (c < 0.08f) continue;
                    wsum += c;
                    float ox = px + qx * start, oy = py + qy * start, oz = pz + qz * start;
                    for (float s = 0; s < dist; s += step) {
                        float x = ox + d[0] * s, y = oy + d[1] * s, z = oz + d[2] * s;
                        if ((!Float.isNaN(groundY) && y < groundY - voxel * 0.5f) || g.get(x, y, z)) {
                            float f = s / dist;
                            occ += c * (1f - f * f * 0.7f);
                            break;
                        }
                    }
                }
                float o = wsum > 0 ? occ / wsum : 0;
                ao = 1f - strength * Math.min(1f, o * 1.15f);
                cache.put(key, ao);
            }
            tdata[i + 8] *= ao;
            tdata[i + 9] *= ao;
            tdata[i + 10] *= ao;
        }
    }

    private static long quant(float v, float q) {
        return Math.round(v / q);
    }

    private static void bounds(float[] d, int size, float[] lo, float[] hi) {
        for (int i = 0; i < size; i += Mesh.STRIDE) {
            for (int k = 0; k < 3; k++) {
                float v = d[i + k];
                if (v < lo[k]) lo[k] = v;
                if (v > hi[k]) hi[k] = v;
            }
        }
    }

    private static float[][] fibonacci(int n) {
        float[][] out = new float[n][];
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i + 0.5) * 2.0 / n;
            double r = Math.sqrt(1 - y * y);
            double th = golden * i;
            out[i] = new float[]{(float) (Math.cos(th) * r), (float) y, (float) (Math.sin(th) * r)};
        }
        return out;
    }

    private static final class Grid {
        final boolean[] cells;
        final int nx, ny, nz;
        final float[] lo;
        final float voxel;

        Grid(boolean[] cells, int nx, int ny, int nz, float[] lo, float voxel) {
            this.cells = cells;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.lo = lo;
            this.voxel = voxel;
        }

        boolean get(float x, float y, float z) {
            int i = (int) ((x - lo[0]) / voxel), j = (int) ((y - lo[1]) / voxel), k = (int) ((z - lo[2]) / voxel);
            if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return false;
            return cells[(i * ny + j) * nz + k];
        }

        void set(float x, float y, float z) {
            int i = (int) ((x - lo[0]) / voxel), j = (int) ((y - lo[1]) / voxel), k = (int) ((z - lo[2]) / voxel);
            if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return;
            cells[(i * ny + j) * nz + k] = true;
        }

        void fill(float[] d, int size) {
            final int S = Mesh.STRIDE;
            for (int q = 0; q + 4 * S <= size; q += 4 * S) {
                tri(d, q, q + S, q + 2 * S);
                tri(d, q, q + 2 * S, q + 3 * S);
            }
        }

        private void tri(float[] d, int a, int b, int c) {
            float ax = d[a], ay = d[a + 1], az = d[a + 2];
            float bx = d[b] - ax, by = d[b + 1] - ay, bz = d[b + 2] - az;
            float cx = d[c] - ax, cy = d[c + 1] - ay, cz = d[c + 2] - az;
            float lb = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            float lc = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float lbc = (float) Math.sqrt((bx - cx) * (bx - cx) + (by - cy) * (by - cy) + (bz - cz) * (bz - cz));
            int n = Math.max(1, (int) Math.ceil(Math.max(lb, Math.max(lc, lbc)) / (voxel * 0.5f)));
            if (n > 400) n = 400;
            for (int i = 0; i <= n; i++) {
                for (int j = 0; j <= n - i; j++) {
                    float u = (float) i / n, v = (float) j / n;
                    set(ax + bx * u + cx * v, ay + by * u + cy * v, az + bz * u + cz * v);
                }
            }
        }
    }
}
