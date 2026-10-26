package fr.heraliz.crates.client.render.scene.crates;

import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Noise;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Elements partages par plusieurs caisses.
 */
final class Parts {
    private Parts() {
    }

    /**
     * Prisme vertical a n cotes (sommets aux azimuts 0, -360/n... : une face plate regarde +Z si n = 6),
     * de y0 a y1, aretes chanfreinees. top/bottom : faces du dessus/dessous, side : flancs.
     */
    static void prism(MeshBuilder b, float cx, float cz, float r, int n, float y0, float y1, float bevel, Mat top, Mat bottom, Mat side) {
        b.push().translate(cx, (y0 + y1) / 2f, cz).rotateX(-90);
        b.slab(MeshBuilder.ngon(r, n, 0), y1 - y0, bevel, false, top, bottom, side);
        b.pop();
    }

    /** Azimut (degres, convention Geo.polar) du k-ieme sommet d'un prisme construit par prism(). */
    static float prismVertex(int n, int k) {
        return -360f * k / n;
    }

    /** Socle de pierre irregulier (disque bombe aux bords arrondis). */
    static final class RockSlab {
        float cx = 8, cz = 8, radius = 15.2f, hCenter = 4.4f, hEdge = 2.8f, bump = 0.7f, freq = 0.16f, edgeNoise = 1.0f;
        int seed = 1;
        /** Projection plane (vue de dessus) sur le dessus : dallages, parquets. */
        boolean planarTop;
        float planarWorld = 32f;

        float edge(float phi) {
            double a = Math.toRadians(phi);
            return radius + edgeNoise * Noise.fbm((float) Math.cos(a) * 1.3f + seed * 3.7f, 0.3f, (float) Math.sin(a) * 1.3f, 3);
        }

        float top(float x, float z) {
            float dx = x - cx, dz = z - cz;
            float r = (float) Math.sqrt(dx * dx + dz * dz);
            float k = Math.min(1f, r / radius);
            return hCenter + (hEdge - hCenter) * k * k + bump * Noise.fbm(x * freq + seed * 1.9f, 0.5f + seed, z * freq - seed, 4);
        }

        void build(MeshBuilder b, Mat m, int nu, int nv, float tilesAround) {
            float[][][] P = new float[nv + 1][nu + 1][];
            float[][] S = new float[nv + 1][nu + 1];
            float[][] T = new float[nv + 1][nu + 1];
            for (int j = 0; j <= nu; j++) {
                float phi = 360f * (j % nu) / nu;
                float e = edge(phi);
                double a = Math.toRadians(phi);
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                for (int i = 0; i <= nv; i++) {
                    float vv = (float) i / nv;
                    float r, y;
                    if (vv <= 0.72f) {
                        r = e * (vv / 0.72f);
                        y = top(cx + c * r, cz + s * r);
                    } else {
                        float w = (vv - 0.72f) / 0.28f;
                        float t0 = top(cx + c * e, cz + s * e);
                        r = e * (1 + 0.05f * (float) Math.sin(w * Math.PI)) + 0.6f * w;
                        y = t0 * (float) Math.pow(1 - w, 0.8);
                    }
                    P[i][j] = v(cx + c * r, y, cz + s * r);
                    if (planarTop && vv <= 0.72f) {
                        S[i][j] = (P[i][j][0] + 8) / planarWorld;
                        T[i][j] = (P[i][j][2] + 8) / planarWorld;
                    } else {
                        S[i][j] = (float) j / nu * tilesAround;
                        T[i][j] = vv * 0.62f;
                    }
                }
            }
            b.grid(P, null, S, T, true, m, MeshBuilder.Orient.OUT, true);
        }
    }
}
