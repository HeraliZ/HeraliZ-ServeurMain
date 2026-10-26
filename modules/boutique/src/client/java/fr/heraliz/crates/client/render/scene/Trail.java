package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;

/**
 * Trainees lumineuses : ruban (deux plans croises, visible sous tous les angles) le long d'une suite de points, du plus
 * recent (tete, pleine largeur et pleine lumiere) au plus ancien (queue, fine et eteinte). A dessiner en additif
 * (tuile blanche de l'atlas) avec la couleur voulue ; maillage dynamique, a reconstruire a chaque image.
 */
public final class Trail {
    private Trail() {
    }

    /** pts[0] = tete. w0 = demi-largeur a la tete, w1 = a la queue (px). */
    public static Mesh ribbon(float[][] pts, float w0, float w1) {
        int n = pts.length;
        if (n < 2) return Mesh.EMPTY;
        MeshBuilder b = new MeshBuilder((n - 1) * 8);
        float[] uvc = {Mat.WHITE.u(0.5f), Mat.WHITE.v(0.5f)};
        float[][] uv = {uvc, uvc, uvc, uvc};
        for (int plane = 0; plane < 2; plane++) {
            for (int i = 0; i + 1 < n; i++) {
                float[] a = pts[i], c = pts[i + 1];
                float[] d = Geo.sub(c, a);
                if (Geo.len(d) < 1e-4f) continue;
                d = Geo.norm(d);
                float[] ref = Math.abs(d[1]) < 0.9f ? Geo.v(0, 1, 0) : Geo.v(1, 0, 0);
                float[] s1 = Geo.norm(Geo.cross(d, ref));
                float[] side = plane == 0 ? s1 : Geo.norm(Geo.cross(d, s1));
                float ka = 1 - i / (float) (n - 1), kc = 1 - (i + 1) / (float) (n - 1);
                float wa = w1 + (w0 - w1) * ka, wc = w1 + (w0 - w1) * kc;
                float fa = ka * ka, fc = kc * kc;
                float[][] q = {Geo.add(a, Geo.mul(side, -wa)), Geo.add(a, Geo.mul(side, wa)), Geo.add(c, Geo.mul(side, wc)), Geo.add(c, Geo.mul(side, -wc))};
                float[][] col = {{fa, fa, fa}, {fa, fa, fa}, {fc, fc, fc}, {fc, fc, fc}};
                b.polyColored(q, uv, null, Geo.cross(side, d), col);
            }
        }
        return b.buildDynamic();
    }
}
