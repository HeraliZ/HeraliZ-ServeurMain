package fr.heraliz.casino.client.render.mesh;

/**
 * Materiau = zone de l'atlas 1024x1024 d'un coffre + mode de projection des UV.
 * TRI : projection "triplanaire" (densite de texture constante, en pixels monde)
 * FIT : la face entiere couvre la tuile (decals).
 * Doit rester synchronise avec tools/gen_textures.py (SLOTS).
 */
public final class Mat {
    private static final float ATLAS = 1024f;
    private static final float INSET = 1.5f;

    public static final Mat MAIN = tri(0, 0, 512, 512, 32);
    public static final Mat MAIN2 = tri(512, 0, 512, 512, 32);
    public static final Mat TRIM = tri(0, 512, 256, 256, 16);
    public static final Mat ACCENT = tri(256, 512, 256, 256, 16);
    public static final Mat EMBLEM = fit(512, 512, 256, 256);
    public static final Mat LOCK = fit(768, 512, 256, 256);
    public static final Mat DECAL_A = fit(0, 768, 256, 256);
    public static final Mat DECAL_B = fit(256, 768, 256, 256);
    public static final Mat INTERIOR = tri(512, 768, 256, 256, 32);
    public static final Mat GEM = tri(768, 768, 128, 128, 8);
    public static final Mat DARK = tri(896, 768, 128, 128, 16);
    public static final Mat SPECIAL = tri(768, 896, 128, 128, 16);
    public static final Mat WHITE = tri(896, 896, 128, 128, 16);

    /** Toutes les tuiles de l'atlas (pour l'analyse du calque lumineux). */
    public static final Mat[] TILES = {MAIN, MAIN2, TRIM, ACCENT, EMBLEM, LOCK, DECAL_A, DECAL_B, INTERIOR, GEM, DARK, SPECIAL, WHITE};

    public boolean contains(float u, float v) {
        return u >= u0 - 0.002f && u <= u1 + 0.002f && v >= v0 - 0.002f && v <= v1 + 0.002f;
    }

    /** Decalage ajoute aux coordonnees avant projection (le modele va de -8 a 24 px). */
    private static final float ORIGIN = 8f;

    public final float u0, v0, u1, v1;
    public final float world;
    public final boolean fit;

    private Mat(float u0, float v0, float u1, float v1, float world, boolean fit) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.world = world;
        this.fit = fit;
    }

    public static Mat tri(float x, float y, float w, float h, float world) {
        return new Mat((x + INSET) / ATLAS, (y + INSET) / ATLAS, (x + w - INSET) / ATLAS, (y + h - INSET) / ATLAS, world, false);
    }

    public static Mat fit(float x, float y, float w, float h) {
        return new Mat((x + INSET) / ATLAS, (y + INSET) / ATLAS, (x + w - INSET) / ATLAS, (y + h - INSET) / ATLAS, 0, true);
    }

    /** Meme zone, projetee en mode FIT. */
    public Mat asFit() {
        return new Mat(u0, v0, u1, v1, 0, true);
    }

    /** Meme zone, projection TRI avec une autre densite (pixels monde couverts par la tuile). */
    public Mat withWorld(float w) {
        return new Mat(u0, v0, u1, v1, w, false);
    }

    /** Sous-rectangle de la tuile (fractions 0..1). */
    public Mat sub(float s0, float t0, float s1, float t1) {
        return new Mat(u(s0), v(t0), u(s1), v(t1), world, fit);
    }

    public float u(float s) {
        return u0 + (u1 - u0) * clamp01(s);
    }

    public float v(float t) {
        return v0 + (v1 - v0) * clamp01(t);
    }

    private static float clamp01(float f) {
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }

    /**
     * UV (atlas) d'un polygone plan, par projection selon l'axe dominant de sa normale.
     * pts : coordonnees locales (pixels) ; n : normale locale ; bounds (FIT, optionnel) : minA,maxA,minB,maxB.
     */
    public float[][] project(float[][] pts, float[] n, float[] bounds) {
        int count = pts.length;
        float[] a = new float[count];
        float[] b = new float[count];
        for (int i = 0; i < count; i++) {
            float[] ab = planar(pts[i], n);
            a[i] = ab[0];
            b[i] = ab[1];
        }
        float[][] uv = new float[count][2];
        if (fit) {
            float minA, maxA, minB, maxB;
            if (bounds != null) {
                minA = bounds[0];
                maxA = bounds[1];
                minB = bounds[2];
                maxB = bounds[3];
            } else {
                minA = min(a);
                maxA = max(a);
                minB = min(b);
                maxB = max(b);
            }
            float da = Math.max(1e-4f, maxA - minA), db = Math.max(1e-4f, maxB - minB);
            for (int i = 0; i < count; i++) {
                uv[i][0] = u((a[i] - minA) / da);
                uv[i][1] = v((b[i] - minB) / db);
            }
        } else {
            float w = world > 0 ? world : 16f;
            float[] s = new float[count];
            float[] t = new float[count];
            for (int i = 0; i < count; i++) {
                s[i] = a[i] / w;
                t[i] = b[i] / w;
            }
            fitRange(s);
            fitRange(t);
            for (int i = 0; i < count; i++) {
                uv[i][0] = u(s[i]);
                uv[i][1] = v(t[i]);
            }
        }
        return uv;
    }

    /** Coordonnees planaires (a = vers la droite, b = vers le bas) vues depuis l'exterieur de la face. */
    public static float[] planar(float[] p, float[] n) {
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        float x = p[0] + ORIGIN, y = p[1] + ORIGIN, z = p[2] + ORIGIN;
        if (az >= ax && az >= ay) {
            return new float[]{n[2] >= 0 ? x : -x, -y};
        } else if (ax >= ay) {
            return new float[]{n[0] >= 0 ? -z : z, -y};
        } else {
            return new float[]{x, n[1] >= 0 ? z : -z};
        }
    }

    /** Ramene une serie de coordonnees de texture dans [0,1] en conservant l'alignement quand c'est possible. */
    static void fitRange(float[] s) {
        float mn = min(s), mx = max(s);
        float off = (float) Math.floor(mn);
        mn -= off;
        mx -= off;
        for (int i = 0; i < s.length; i++) s[i] -= off;
        if (mx > 1f) {
            if (mx - mn <= 1f) {
                float shift = mx - 1f;
                for (int i = 0; i < s.length; i++) s[i] -= shift;
            } else {
                float ext = mx - mn;
                for (int i = 0; i < s.length; i++) s[i] = (s[i] - mn) / ext;
            }
        }
    }

    static float min(float[] f) {
        float m = Float.MAX_VALUE;
        for (float v : f) m = Math.min(m, v);
        return m;
    }

    static float max(float[] f) {
        float m = -Float.MAX_VALUE;
        for (float v : f) m = Math.max(m, v);
        return m;
    }
}
