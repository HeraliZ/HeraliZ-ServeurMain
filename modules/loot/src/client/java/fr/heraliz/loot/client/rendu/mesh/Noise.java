package fr.heraliz.loot.client.rendu.mesh;

import java.util.Random;

/**
 * Bruit de gradient 3D (Perlin ameliore) deterministe, pour deformer les formes organiques.
 */
public final class Noise {
    private Noise() {
    }

    private static final int[] P = new int[512];

    static {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(0x4E5A11L);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) P[i] = p[i & 255];
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static float grad(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** Bruit dans [-1, 1] environ. */
    public static float noise(float x, float y, float z) {
        int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255, Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        float u = fade(x), v = fade(y), w = fade(z);
        int A = P[X] + Y, AA = P[A] + Z, AB = P[A + 1] + Z, B = P[X + 1] + Y, BA = P[B] + Z, BB = P[B + 1] + Z;
        return lerp(w, lerp(v, lerp(u, grad(P[AA], x, y, z), grad(P[BA], x - 1, y, z)),
                        lerp(u, grad(P[AB], x, y - 1, z), grad(P[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(P[AA + 1], x, y, z - 1), grad(P[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(P[AB + 1], x, y - 1, z - 1), grad(P[BB + 1], x - 1, y - 1, z - 1))));
    }

    /** Somme d'octaves, resultat ~[-1, 1]. */
    public static float fbm(float x, float y, float z, int octaves) {
        float sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * f, y * f, z * f);
            norm += amp;
            amp *= 0.5f;
            f *= 2.03f;
        }
        return sum / norm;
    }

    /** Bruit "cellulaire" grossier (bosses) dans [0,1]. */
    public static float ridged(float x, float y, float z, int octaves) {
        return 1f - Math.abs(fbm(x, y, z, octaves));
    }
}
