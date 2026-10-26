package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Pièces communes du lot T56 (repère : pixels, sol en y = 0, dos au mur en z = 0, avant vers +Z) : boîtes déformées
 * (cartons affaissés, tôles cabossées), sangles plates qui suivent un chemin, cartons d'aide, charnières, rivets,
 * cartouches, lingots, tubes à essai, câbles.
 */
final class LotT56Kit {
    private LotT56Kit() {
    }

    static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    /**
     * Boîte à huit coins quelconques (faces planes ou presque) : c[0..3] = bas (x0z0, x1z0, x1z1, x0z1), c[4..7] = haut
     * dans le même ordre. Les faces s'orientent vers l'extérieur. Masque : bit i = face omise (-X, +X, -Y, +Y, -Z, +Z).
     */
    static void boite(MeshBuilder b, Mat m, float[][] c, int sans) {
        float[] ce = new float[3];
        for (float[] q : c) {
            ce[0] += q[0] / 8;
            ce[1] += q[1] / 8;
            ce[2] += q[2] / 8;
        }
        if ((sans & 1) == 0) b.flatOut(m, ce, c[0], c[3], c[7], c[4]);
        if ((sans & 2) == 0) b.flatOut(m, ce, c[1], c[2], c[6], c[5]);
        if ((sans & 4) == 0) b.flatOut(m, ce, c[0], c[1], c[2], c[3]);
        if ((sans & 8) == 0) b.flatOut(m, ce, c[4], c[5], c[6], c[7]);
        if ((sans & 16) == 0) b.flatOut(m, ce, c[0], c[1], c[5], c[4]);
        if ((sans & 32) == 0) b.flatOut(m, ce, c[3], c[2], c[6], c[7]);
    }

    /** Boîte droite dont le dessus est enfoncé (affaissement) : dy0..dy3 = descente des coins hauts (x0z0, x1z0, x1z1, x0z1). */
    static void boiteAffaissee(MeshBuilder b, Mat m, float x0, float y0, float z0, float x1, float y1, float z1, float d0, float d1, float d2, float d3) {
        boite(b, m, new float[][]{p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1),
                p(x0, y1 - d0, z0), p(x1, y1 - d1, z0), p(x1, y1 - d2, z1), p(x0, y1 - d3, z1)}, 4);
    }

    /**
     * Sangle plate (largeur, épaisseur) qui suit un chemin ; haut = direction de la face visible (normale) en chaque
     * point. Faces dessus, dessous et chants.
     */
    static void bande(MeshBuilder b, float[][] chemin, float[][] haut, float largeur, float ep, Mat m) {
        int n = chemin.length;
        float[][] s = new float[n][], nn = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = chemin[Math.max(0, i - 1)], c = chemin[Math.min(n - 1, i + 1)];
            float[] t = norm(new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]});
            float[] h = haut[Math.min(i, haut.length - 1)];
            s[i] = norm(cross(t, h));
            nn[i] = norm(cross(s[i], t));
        }
        float w = largeur / 2, e = ep / 2;
        for (int i = 0; i < n - 1; i++) {
            float[][] q = new float[8][];
            for (int k = 0; k < 2; k++) {
                int j = i + k;
                float[] c = chemin[j];
                q[k * 4] = add(c, s[j], -w, nn[j], e);
                q[k * 4 + 1] = add(c, s[j], w, nn[j], e);
                q[k * 4 + 2] = add(c, s[j], w, nn[j], -e);
                q[k * 4 + 3] = add(c, s[j], -w, nn[j], -e);
            }
            float[] up = norm(new float[]{nn[i][0] + nn[i + 1][0], nn[i][1] + nn[i + 1][1], nn[i][2] + nn[i + 1][2]});
            float[] sd = norm(new float[]{s[i][0] + s[i + 1][0], s[i][1] + s[i + 1][1], s[i][2] + s[i + 1][2]});
            b.face(m, up, q[0], q[1], q[5], q[4]);
            b.face(m, new float[]{-up[0], -up[1], -up[2]}, q[3], q[2], q[6], q[7]);
            b.face(m, sd, q[1], q[2], q[6], q[5]);
            b.face(m, new float[]{-sd[0], -sd[1], -sd[2]}, q[0], q[3], q[7], q[4]);
        }
    }

    static float[] add(float[] c, float[] a, float ka, float[] bb, float kb) {
        return new float[]{c[0] + a[0] * ka + bb[0] * kb, c[1] + a[1] * ka + bb[1] * kb, c[2] + a[2] * ka + bb[2] * kb};
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float[] norm(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-7f ? new float[]{0, 1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static final float[] HAUT = {0, 1, 0}, AVANT = {0, 0, 1}, DROITE = {1, 0, 0};

    /**
     * Carton d'aide (coin bas-arrière-gauche x0, y0, z0) : ruban sur le dessus et l'avant, marquage UN et « aide
     * humanitaire » sur l'avant (imprime : 0 rien, 1 avant, 2 avant + flanc droit).
     */
    static void cartonAide(MeshBuilder b, float x0, float y0, float z0, float w, float h, float d, int imprime, Mat ruban) {
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d, xm = x0 + w / 2;
        b.bevelBox(x0, y0, z0, x1, y1, z1, 0.12f, CARTON_BLANC);
        b.box(xm - 0.5f, y1 - 0.02f, z0 + 0.3f, xm + 0.5f, y1 + 0.04f, z1 + 0.02f, ruban);
        b.box(xm - 0.5f, y1 - 1.1f, z1 - 0.02f, xm + 0.5f, y1 - 0.02f, z1 + 0.04f, ruban);
        if (imprime > 0) {
            float uw = Math.min(w * 0.5f, 3.2f);
            Kit.pochoir(b, S_UN_NOIR, 'z', x0 + w * 0.3f, y0 + h * 0.56f, z1, uw, uw / 2);
            Kit.pochoir(b, S_AIDE, 'z', x0 + w / 2, y0 + 0.95f, z1, Math.min(w * 0.86f, 5.6f), Math.min(w * 0.86f, 5.6f) / 4);
        }
        if (imprime > 1) Kit.pochoir(b, S_UN_NOIR, 'X', x1, y0 + h * 0.5f, z0 + d / 2, Math.min(d * 0.6f, 3.6f), Math.min(d * 0.6f, 3.6f) / 2);
    }

    /** Charnière à piano / à lames sur la face +Z en (x, y, z), longueur l le long de X. */
    static void charniereX(MeshBuilder b, float x0, float x1, float y, float z, float r, Mat m) {
        b.push().translate(x0, y, z).rotateZ(-90);
        b.cylinder(r, 0, x1 - x0, 8, m, m, m);
        b.pop();
    }

    /** Rangée de rivets bombés sur une face +Z, de (x0, y) à (x1, y). */
    static void rivetsZ(MeshBuilder b, float x0, float x1, float y, float z, int n, float r, Mat m) {
        for (int k = 0; k < n; k++) {
            float x = n == 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * k / (n - 1);
            b.push().translate(x, y, z).rotateX(90);
            b.cylinder(r, -0.02f, r * 0.45f, 6, m, null, m);
            b.pop();
        }
    }

    /** Cartouche (étui laiton, balle) couchée le long de son axe local +X, culot en (0, 0, 0). */
    static void cartouche(MeshBuilder b, float longueur, float rayon, Mat etui, Mat balle) {
        b.push().rotateZ(-90);
        float le = longueur * 0.72f;
        b.lathe(new float[][]{{0.01f, 0}, {rayon * 1.02f, 0}, {rayon * 1.02f, rayon * 0.35f}, {rayon * 0.85f, rayon * 0.5f},
                {rayon, rayon * 0.7f}, {rayon, le * 0.78f}, {rayon * 0.7f, le * 0.9f}, {rayon * 0.62f, le}}, 6, 0, 360, etui, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{rayon * 0.62f, le - 0.01f}, {rayon * 0.6f, le + (longueur - le) * 0.35f}, {rayon * 0.35f, le + (longueur - le) * 0.8f},
                {0.01f, longueur}}, 6, 0, 360, balle, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Lingot trapézoïdal (base L x l, hauteur h) posé en (x, y, z), tourné de ang autour de Y. */
    static void lingot(MeshBuilder b, float x, float y, float z, float ang, float L, float l, float h) {
        b.push().translate(x, y, z).rotateY(ang);
        float r = 0.22f;
        boite(b, OR_TERNI, new float[][]{p(-L / 2, 0, -l / 2), p(L / 2, 0, -l / 2), p(L / 2, 0, l / 2), p(-L / 2, 0, l / 2),
                p(-L / 2 + r, h, -l / 2 + r), p(L / 2 - r, h, -l / 2 + r), p(L / 2 - r, h, l / 2 - r), p(-L / 2 + r, h, l / 2 - r)}, 4);
        b.pop();
    }

    /** Faces intérieures d'un bac ouvert (fond et quatre parois tournées vers l'intérieur, pas de dessus). */
    static void bacInterieur(MeshBuilder b, Mat m, float x0, float y0, float z0, float x1, float y1, float z1) {
        b.face(m, MeshBuilder.v(0, 1, 0), p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1));
        b.face(m, MeshBuilder.v(1, 0, 0), p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
        b.face(m, MeshBuilder.v(-1, 0, 0), p(x1, y0, z0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, z0));
        b.face(m, MeshBuilder.v(0, 0, 1), p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0));
        b.face(m, MeshBuilder.v(0, 0, -1), p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1));
    }

    /** Cartouche simplifiée (étui cylindrique, ogive) le long de +X, culot en (0, 0, 0). */
    static void cartoucheSimple(MeshBuilder b, float longueur, float rayon, Mat etui, Mat balle) {
        float le = longueur * 0.7f;
        b.push().rotateZ(-90);
        b.cylinder(rayon, 0, le, 6, etui, etui, null);
        b.lathe(new float[][]{{rayon * 0.72f, le - 0.01f}, {rayon * 0.6f, le + (longueur - le) * 0.5f}, {0.01f, longueur}}, 6, 0, 360, balle, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Poignée de corde sur un flanc ±X : deux taquets (matière donnée) et une boucle de corde. */
    static void poigneeCorde(MeshBuilder b, float x, float y, float zc, float largeur, int cote, Mat taquet) {
        float z0 = zc - largeur / 2, z1 = zc + largeur / 2, s = cote;
        for (float z : new float[]{z0, z1}) b.bevelBox(Math.min(x, x + s * 0.5f), y - 0.5f, z - 0.45f, Math.max(x, x + s * 0.5f), y + 0.5f, z + 0.45f, 0.12f, taquet);
        b.tube(new float[][]{p(x + s * 0.3f, y, z0), p(x + s * 0.9f, y - 0.9f, z0 + 0.4f), p(x + s * 1.0f, y - 1.3f, zc), p(x + s * 0.9f, y - 0.9f, z1 - 0.4f), p(x + s * 0.3f, y, z1)},
                fill(5, 0.22f), 8, AtlasLoot.ROPE, false, false, 0, false);
    }

    /** Fermoir ouvert qui pend (plaque sur la face +Z, levier rabattu vers le bas), ouverture en degrés. */
    static void fermoirOuvert(MeshBuilder b, float x, float y, float z, float ouverture, Mat m) {
        b.bevelBox(x - 0.7f, y - 1.2f, z - 0.02f, x + 0.7f, y - 0.2f, z + 0.2f, 0.07f, AtlasLoot.DARK);
        b.push().translate(x, y - 1.0f, z + 0.2f).rotateX(-ouverture);
        b.bevelBox(-0.48f, -1.9f, 0, 0.48f, 0.2f, 0.22f, 0.08f, m);
        b.bevelBox(-0.3f, -2.1f, 0.05f, 0.3f, -1.7f, 0.42f, 0.05f, m);
        b.pop();
    }

    /** Cordon spiralé (téléphone, combiné radio) de a vers b, n tours de rayon r, fil de rayon fil. */
    static void spirale(MeshBuilder b, float[] a, float[] c, int tours, float r, float fil) {
        int n = tours * 6 + 1;
        float[] t = norm(new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]});
        float[] u = norm(cross(t, Math.abs(t[1]) < 0.9f ? HAUT : DROITE)), v = cross(t, u);
        float[][] pts = new float[n][];
        for (int k = 0; k < n; k++) {
            float f = k / (float) (n - 1);
            double ang = f * tours * Math.PI * 2;
            float cu = (float) Math.cos(ang) * r, sv = (float) Math.sin(ang) * r;
            pts[k] = p(a[0] + (c[0] - a[0]) * f + u[0] * cu + v[0] * sv, a[1] + (c[1] - a[1]) * f + u[1] * cu + v[1] * sv, a[2] + (c[2] - a[2]) * f + u[2] * cu + v[2] * sv);
        }
        b.tube(pts, fill(n, fil), 4, AtlasLoot.DARK, false, false, 0, false);
    }

    /** Niche ouverte vers +Z (casier) : fond, plafond, plancher et deux joues, faces tournées vers l'intérieur. */
    static void niche(MeshBuilder b, Mat m, float x0, float y0, float z0, float x1, float y1, float z1) {
        b.face(m, MeshBuilder.v(0, 0, 1), p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0));
        b.face(m, MeshBuilder.v(0, 1, 0), p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1));
        b.face(m, MeshBuilder.v(0, -1, 0), p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1));
        b.face(m, MeshBuilder.v(1, 0, 0), p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
        b.face(m, MeshBuilder.v(-1, 0, 0), p(x1, y0, z0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, z0));
    }

    /**
     * Décalcomanie enroulée sur un cylindre vertical (fût) : centre (cx, cz), rayon r, de y0 à y1, secteur centré sur
     * l'angle a (degrés, 90 = face +Z), d'ouverture span degrés. La texture se lit de gauche à droite vue de face.
     */
    static void decalCylindre(MeshBuilder b, Mat m, float cx, float cz, float r, float y0, float y1, float a, float span, int seg) {
        Mat f = m.fit ? m : m.asFit();
        for (int i = 0; i < seg; i++) {
            float s0 = i / (float) seg, s1 = (i + 1) / (float) seg;
            double a0 = Math.toRadians(a + span / 2 - span * s0), a1 = Math.toRadians(a + span / 2 - span * s1);
            float c0 = (float) Math.cos(a0), n0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), n1 = (float) Math.sin(a1);
            float[][] pts = {p(cx + r * c0, y0, cz + r * n0), p(cx + r * c1, y0, cz + r * n1), p(cx + r * c1, y1, cz + r * n1), p(cx + r * c0, y1, cz + r * n0)};
            float[][] uv = {{f.u(s0), f.v(1)}, {f.u(s1), f.v(1)}, {f.u(s1), f.v(0)}, {f.u(s0), f.v(0)}};
            float[][] nr = {{c0, 0, n0}, {c1, 0, n1}, {c1, 0, n1}, {c0, 0, n0}};
            b.poly(pts, uv, nr, new float[]{(c0 + c1) / 2, 0, (n0 + n1) / 2});
        }
    }

    /** Plaque horizontale (dessus, normale +Y) en y, rectangle x0..x1 / z0..z1 percé d'un trou rond (cx, cz, r). */
    static void plaqueTrouee(MeshBuilder b, Mat m, float x0, float z0, float x1, float z1, float cx, float cz, float r, float y, int seg) {
        java.util.List<Float> angs = new java.util.ArrayList<>();
        for (int k = 0; k < seg; k++) angs.add((float) (k * Math.PI * 2 / seg));
        for (float[] c : new float[][]{{x1, z1}, {x0, z1}, {x0, z0}, {x1, z0}}) {
            float a = (float) Math.atan2(c[1] - cz, c[0] - cx);
            angs.add(a < 0 ? a + (float) (Math.PI * 2) : a);
        }
        java.util.Collections.sort(angs);
        int n = angs.size();
        for (int i = 0; i < n; i++) {
            float a0 = angs.get(i), a1 = angs.get((i + 1) % n);
            float[] q0 = bord(x0, z0, x1, z1, cx, cz, a0), q1 = bord(x0, z0, x1, z1, cx, cz, a1);
            b.face(m, MeshBuilder.v(0, 1, 0), p(cx + r * (float) Math.cos(a0), y, cz + r * (float) Math.sin(a0)), p(q0[0], y, q0[1]), p(q1[0], y, q1[1]),
                    p(cx + r * (float) Math.cos(a1), y, cz + r * (float) Math.sin(a1)));
        }
    }

    /** Point du bord du rectangle sur le rayon issu de (cx, cz) d'angle a. */
    static float[] bord(float x0, float z0, float x1, float z1, float cx, float cz, float a) {
        float dx = (float) Math.cos(a), dz = (float) Math.sin(a), t = Float.MAX_VALUE;
        if (dx > 1e-6f) t = Math.min(t, (x1 - cx) / dx);
        if (dx < -1e-6f) t = Math.min(t, (x0 - cx) / dx);
        if (dz > 1e-6f) t = Math.min(t, (z1 - cz) / dz);
        if (dz < -1e-6f) t = Math.min(t, (z0 - cz) / dz);
        return new float[]{cx + dx * t, cz + dz * t};
    }

    /** Liasse de billets (bague de papier kraft) posée en (x, y, z), tournée de ang. */
    static void liasse(MeshBuilder b, float x, float y, float z, float ang, float h) {
        b.push().translate(x, y, z).rotateY(ang);
        b.box6(-1.25f, 0, -0.6f, 1.25f, h, 0.6f, BILLETS_TRANCHE, BILLETS_TRANCHE, null, BILLETS, BILLETS_TRANCHE, BILLETS_TRANCHE);
        b.box(-0.35f, -0.02f, -0.64f, 0.35f, h + 0.04f, 0.64f, PAPIER_KRAFT);
        b.pop();
    }

    /** Remonte les points d'un chemin lissé au-dessus de ymin (une courbe qui retombe au sol ne doit pas le traverser). */
    static float[][] auSol(float[][] pts, float ymin) {
        for (float[] q : pts) q[1] = Math.max(q[1], ymin);
        return pts;
    }

    /** Câble souple : tube fin le long d'un chemin, teinte donnée. */
    static void cable(MeshBuilder b, float[][] chemin, float r, float cr, float cg, float cb) {
        b.color(cr, cg, cb);
        b.tube(chemin, fill(chemin.length, r), 6, CABLE, true, true, 0, false);
        b.color(1);
    }

    /** Chemin lissé (Catmull-Rom) passant par des points de contrôle, n points par segment. */
    static float[][] lisse(float[][] ctrl, int n) {
        int m = ctrl.length;
        float[][] out = new float[(m - 1) * n + 1][];
        int k = 0;
        for (int i = 0; i < m - 1; i++) {
            float[] p0 = ctrl[Math.max(0, i - 1)], p1 = ctrl[i], p2 = ctrl[i + 1], p3 = ctrl[Math.min(m - 1, i + 2)];
            for (int j = 0; j < n; j++) {
                float t = j / (float) n, t2 = t * t, t3 = t2 * t;
                float[] q = new float[3];
                for (int a = 0; a < 3; a++) {
                    q[a] = 0.5f * (2 * p1[a] + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2 + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3);
                }
                out[k++] = q;
            }
        }
        out[k] = ctrl[m - 1].clone();
        return out;
    }
}
