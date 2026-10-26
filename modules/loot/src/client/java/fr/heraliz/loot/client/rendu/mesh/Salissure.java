package fr.heraliz.loot.client.rendu.mesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Usure cuite dans la couleur des sommets, après l'occlusion ambiante (monde post-apocalyptique : rien n'est propre).
 * Terre et humidité au pied des objets : les faces qui descendent dans la bande basse sont recoupées à l'horizontale
 * pour que le dégradé reste en bas au lieu de s'étaler sur toute la face. Crasse en nuages (bruit 3D) et teinte un peu
 * terne partout.
 */
public final class Salissure {
    private Salissure() {
    }

    /** Hauteur (px) de la bande de terre au pied des objets, et les coupes horizontales qui la dessinent. */
    static final float BANDE = 4.5f;
    private static final float[] COUPES = {0.8f, 1.8f, 3.2f};
    private static final int S = Mesh.STRIDE;

    /**
     * @param force 1 = usure normale, 0.5 = objet entretenu (haute sécurité), 0 = rien
     * @param graine décale les nuages de crasse d'un conteneur à l'autre
     */
    public static void appliquer(MeshBuilder b, float force, int graine) {
        if (force <= 0) return;
        float[] d = decouper(b.rawData(), b.rawSize());
        int n = d.length;
        float ox = (graine & 255) * 0.37f, oz = ((graine >> 8) & 255) * 0.53f;
        for (int i = 0; i < n; i += S) {
            float x = d[i], y = d[i + 1], z = d[i + 2];
            // terre au pied, en taches (pas une bande régulière)
            float t = 1f - smooth(0, BANDE, y);
            float tache = 0.55f + 0.45f * clamp01((Noise.fbm(x * 0.21f + ox, y * 0.3f, z * 0.21f + oz, 2) + 0.35f) * 1.4f);
            float f = t * t * 0.62f * force * tache;
            // crasse en nuages
            float g = clamp01((Noise.fbm(x * 0.09f + oz, y * 0.11f + 7.1f, z * 0.09f + ox, 3) + 0.15f) * 1.6f);
            float k = 1f - force * 0.16f * g;
            d[i + 8] *= k * (1f - f * 0.42f);
            d[i + 9] *= k * 0.985f * (1f - f * 0.5f);
            d[i + 10] *= k * 0.955f * (1f - f * 0.6f);
        }
        b.replaceRaw(d, n);
    }

    /** Recoupe à l'horizontale les faces qui traversent la bande basse (les faces tournées vers le bas sont laissées). */
    static float[] decouper(float[] src, int size) {
        float[] out = new float[size + size / 2 + 64];
        int n = 0;
        float[][] q = new float[4][];
        for (int o = 0; o + 4 * S <= size; o += 4 * S) {
            float ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE, ny = 0;
            float xmin = Float.MAX_VALUE, xmax = -Float.MAX_VALUE, zmin = Float.MAX_VALUE, zmax = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                ymin = Math.min(ymin, src[o + k * S + 1]);
                ymax = Math.max(ymax, src[o + k * S + 1]);
                xmin = Math.min(xmin, src[o + k * S]);
                xmax = Math.max(xmax, src[o + k * S]);
                zmin = Math.min(zmin, src[o + k * S + 2]);
                zmax = Math.max(zmax, src[o + k * S + 2]);
                ny += src[o + k * S + 6];
            }
            // seules les grandes faces sont recoupées (une face étroite, goulot ou montant, garde son dégradé)
            boolean large = Math.max(xmax - xmin, zmax - zmin) > 1.5f;
            boolean coupe = large && ny / 4 > -0.6f && ymin < COUPES[COUPES.length - 1] && ymax - ymin > 0.5f;
            if (!coupe) {
                out = place(out, n, 4 * S);
                System.arraycopy(src, o, out, n, 4 * S);
                n += 4 * S;
                continue;
            }
            for (int k = 0; k < 4; k++) q[k] = Arrays.copyOfRange(src, o + k * S, o + (k + 1) * S);
            List<float[][]> polys = new ArrayList<>();
            polys.add(q.clone());
            for (float c : COUPES) {
                if (c <= ymin + 0.2f || c >= ymax - 0.2f) continue;
                List<float[][]> suite = new ArrayList<>();
                for (float[][] p : polys) {
                    float[][][] deux = trancher(p, c);
                    for (float[][] r : deux) if (r != null && r.length >= 3) suite.add(r);
                }
                polys = suite;
            }
            for (float[][] p : polys) {
                // éventail de quads (le dernier peut être un triangle : sommet répété)
                for (int a = 1; a + 1 < p.length; a += 2) {
                    float[][] f = {p[0], p[a], p[a + 1], a + 2 < p.length ? p[a + 2] : p[a + 1]};
                    out = place(out, n, 4 * S);
                    for (float[] v : f) {
                        System.arraycopy(v, 0, out, n, S);
                        n += S;
                    }
                }
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static float[] place(float[] out, int n, int besoin) {
        return n + besoin <= out.length ? out : Arrays.copyOf(out, Math.max(out.length * 2, n + besoin));
    }

    /** Coupe un polygone convexe par le plan y = c : {dessous, dessus}. */
    private static float[][][] trancher(float[][] p, float c) {
        List<float[]> bas = new ArrayList<>(), haut = new ArrayList<>();
        for (int i = 0; i < p.length; i++) {
            float[] a = p[i], e = p[(i + 1) % p.length];
            boolean ab = a[1] < c, eb = e[1] < c;
            (ab ? bas : haut).add(a);
            if (ab != eb) {
                float t = (c - a[1]) / (e[1] - a[1]);
                float[] m = new float[S];
                for (int k = 0; k < S; k++) m[k] = a[k] + (e[k] - a[k]) * t;
                m[1] = c;
                float l = (float) Math.sqrt(m[5] * m[5] + m[6] * m[6] + m[7] * m[7]);
                if (l > 1e-6f) {
                    m[5] /= l;
                    m[6] /= l;
                    m[7] /= l;
                }
                bas.add(m);
                haut.add(m);
            }
        }
        return new float[][][]{bas.toArray(new float[0][]), haut.toArray(new float[0][])};
    }

    private static float smooth(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
