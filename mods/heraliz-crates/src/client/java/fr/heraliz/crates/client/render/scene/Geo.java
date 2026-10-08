package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;

/**
 * Aides geometriques : splines, cornes/griffes effilees, cones, vecteurs.
 */
public final class Geo {
    private Geo() {
    }

    /** Courbe de Catmull-Rom passant par les points de controle. */
    public static float[][] spline(float[][] c, int perSeg) {
        int n = c.length;
        if (n < 2) return c;
        float[][] out = new float[(n - 1) * perSeg + 1][];
        int k = 0;
        for (int i = 0; i < n - 1; i++) {
            float[] p0 = c[Math.max(0, i - 1)], p1 = c[i], p2 = c[i + 1], p3 = c[Math.min(n - 1, i + 2)];
            for (int s = 0; s < perSeg; s++) {
                float t = (float) s / perSeg;
                out[k++] = cr(p0, p1, p2, p3, t);
            }
        }
        out[k] = c[n - 1].clone();
        return out;
    }

    private static float[] cr(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        float[] o = new float[3];
        for (int a = 0; a < 3; a++) {
            o[a] = 0.5f * ((2 * p1[a]) + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2
                    + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3);
        }
        return o;
    }

    /** Rayons effiles r0 -> r1 (pow &gt; 1 : s'affine vite vers la pointe). */
    public static float[] taper(int n, float r0, float r1, float pow) {
        float[] r = new float[n];
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : (float) i / (n - 1);
            r[i] = r1 + (r0 - r1) * (float) Math.pow(1 - t, pow);
        }
        return r;
    }

    /** Corne / griffe / tentacule : tube effile le long d'une spline. */
    public static void horn(MeshBuilder b, float[][] ctrl, float r0, float r1, float pow, int perSeg, int radial, Mat m) {
        float[][] path = spline(ctrl, perSeg);
        b.tube(path, taper(path.length, r0, r1, pow), radial, m, true, true, 0, false);
    }

    /** Cone (pointe) partant de "base" dans la direction "dir". */
    public static void cone(MeshBuilder b, float[] base, float[] dir, float len, float r, int radial, Mat m) {
        float[] d = norm(dir);
        float[][] path = new float[6][];
        float[] radii = new float[6];
        for (int i = 0; i < 6; i++) {
            float t = i / 5f;
            path[i] = add(base, mul(d, len * t));
            radii[i] = r * (1 - t) + 0.03f;
        }
        b.tube(path, radii, radial, m, true, false, 0, false);
    }

    public static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    public static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static float[] mul(float[] a, float k) {
        return new float[]{a[0] * k, a[1] * k, a[2] * k};
    }

    public static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    public static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static float len(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }

    public static float[] norm(float[] a) {
        float l = len(a);
        return l < 1e-7f ? new float[]{0, 1, 0} : mul(a, 1f / l);
    }

    /** Interpolation spherique approximative de deux directions. */
    public static float[] slerp(float[] a, float[] b, float t) {
        return norm(lerp(norm(a), norm(b), t));
    }

    /** Point sur un cercle horizontal : angle (degres, 90 = +Z), rayon, hauteur, autour de (cx, cz). */
    public static float[] polar(float cx, float cz, float deg, float r, float y) {
        double a = Math.toRadians(deg);
        return new float[]{cx + (float) Math.cos(a) * r, y, cz + (float) Math.sin(a) * r};
    }
}
