package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.mesh.Noise;

import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Pièces communes du lot T4b : décalcomanies courbes (sur un fût, un sac couché), sac de sable (coussin aplati au sol),
 * clou tordu, chiffon, sangle posée sur une surface. Repère : pixels, sol en y = 0, avant vers +Z.
 */
final class LotT4bKit {
    private LotT4bKit() {
    }

    /**
     * Décalcomanie enroulée sur un cylindre d'axe Y local (fût debout) : le texte fait le tour, lu de gauche à droite
     * par quelqu'un qui regarde depuis l'angle ac (degrés, 90 = face +Z). aw : largeur angulaire, y0..y1 : hauteur.
     */
    static void decalTour(MeshBuilder b, Mat m, float r, float ac, float aw, float y0, float y1, int n) {
        float a0 = ac + aw / 2, a1 = ac - aw / 2;
        for (int i = 0; i < n; i++) {
            float sa = (float) i / n, sb = (float) (i + 1) / n;
            double pa = Math.toRadians(a0 + (a1 - a0) * sa), pb = Math.toRadians(a0 + (a1 - a0) * sb);
            float ca = (float) Math.cos(pa), za = (float) Math.sin(pa), cb = (float) Math.cos(pb), zb = (float) Math.sin(pb);
            float[][] pts = {p(r * ca, y0, r * za), p(r * cb, y0, r * zb), p(r * cb, y1, r * zb), p(r * ca, y1, r * za)};
            float[][] uv = {{m.u(sa), m.v(1)}, {m.u(sb), m.v(1)}, {m.u(sb), m.v(0)}, {m.u(sa), m.v(0)}};
            float[][] nr = {p(ca, 0, za), p(cb, 0, zb), p(cb, 0, zb), p(ca, 0, za)};
            b.poly(pts, uv, nr, p((ca + cb) / 2, 0, (za + zb) / 2));
        }
    }

    /**
     * Décalcomanie sur un tube couché d'axe X local (sac, fût couché), section elliptique (ry, rz) : le texte suit l'axe
     * (de xa à xb), entre les angles phi0 (bas du texte) et phi1 (haut), mesurés depuis l'avant (+Z) vers le haut (+Y).
     */
    static void decalLong(MeshBuilder b, Mat m, float xa, float xb, float ry, float rz, float phi0, float phi1, int n) {
        for (int i = 0; i < n; i++) {
            float ta = (float) i / n, tb = (float) (i + 1) / n;
            double pa = Math.toRadians(phi1 + (phi0 - phi1) * ta), pb = Math.toRadians(phi1 + (phi0 - phi1) * tb);
            float ya = ry * (float) Math.sin(pa), za = rz * (float) Math.cos(pa), yb = ry * (float) Math.sin(pb), zb = rz * (float) Math.cos(pb);
            float[] na = norm(p(0, ya / (ry * ry), za / (rz * rz))), nb = norm(p(0, yb / (ry * ry), zb / (rz * rz)));
            float[][] pts = {p(xa, ya, za), p(xb, ya, za), p(xb, yb, zb), p(xa, yb, zb)};
            float[][] uv = {{m.u(0), m.v(ta)}, {m.u(1), m.v(ta)}, {m.u(1), m.v(tb)}, {m.u(0), m.v(tb)}};
            b.poly(pts, uv, new float[][]{na, na, nb, nb}, norm(p(0, na[1] + nb[1], na[2] + nb[2])));
        }
    }

    static float[] norm(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-6f ? p(0, 1, 0) : p(a[0] / l, a[1] / l, a[2] / l);
    }

    /**
     * Sac de sable plein (coussin aplati, oreille liée à un bout) posé en y0, centré en (cx, cz) : rx, rz demi-longueur
     * et demi-largeur, ry demi-hauteur. Le dessous est plat (posé), le dessus bombé et creusé au milieu, bosselé.
     */
    static void sacSable(MeshBuilder b, Mat m, float cx, float y0, float cz, float rx, float ry, float rz, float rotY, float roulis, int graine,
                         boolean oreille) {
        float plat = 0.78f;
        // teinte propre à chaque sac (toile neuve plus claire, sacs olive, sacs boueux)
        java.util.Random rt = new java.util.Random(graine * 977L);
        float t = 0.72f + rt.nextFloat() * 0.26f;
        if (graine % 4 == 1) b.color(t * 0.82f, t * 0.88f, t * 0.62f);
        else b.color(t, t * 0.97f, t * 0.9f);
        b.push().translate(cx, y0 + plat * ry, cz).rotateY(rotY).rotateX(roulis);
        float perim = (float) (Math.PI * (rx + rz)), w = m.world > 0 ? m.world : 16f;
        b.surface((u, v) -> {
            double ph = u * 2 * Math.PI, th = v * Math.PI;
            float sx = (float) (Math.sin(th) * Math.cos(ph)), sy = (float) Math.cos(th), sz = (float) (Math.sin(th) * Math.sin(ph));
            float ex = Math.signum(sx) * (float) Math.pow(Math.abs(sx), 0.45), ez = Math.signum(sz) * (float) Math.pow(Math.abs(sz), 0.5);
            float ey = Math.signum(sy) * (float) Math.pow(Math.abs(sy), 0.7);
            float x = ex * rx, z = ez * rz;
            float bosse = Noise.fbm(x * 0.35f + graine * 3.1f, 0.5f * graine, z * 0.35f - graine * 1.7f, 2);
            float k = 1 + 0.06f * bosse;
            x *= k;
            z *= k;
            float y;
            if (ey < 0) {
                y = Math.max(ey * ry, -plat * ry);
            } else {
                float creux = 1f - 0.22f * (1 - Math.min(1, Math.abs(x / rx) * 1.3f)) * (1 - Math.abs(z / rz));
                y = ey * ry * creux * (1 + 0.1f * bosse);
            }
            return p(x, y, z);
        }, 12, 6, m, Math.max(1f, perim / w), Math.max(0.3f, ry * 2 / w), true, MeshBuilder.Orient.OUT);
        if (oreille) {
            // bout lié : toile pincée et ficelle
            b.tube(new float[][]{p(rx * 0.92f, -0.1f, 0), p(rx * 1.12f, 0.05f, 0.1f), p(rx * 1.32f, -0.05f, 0.05f)},
                    new float[]{ry * 0.45f, ry * 0.32f, ry * 0.38f}, 6, m, false, true, 0, false);
            b.push().translate(rx * 1.08f, 0.0f, 0.05f).rotateZ(90);
            b.torus(ry * 0.36f, 0.06f, 8, 3, 0, 360, AtlasLoot.ROPE);
            b.pop();
        }
        b.pop();
        b.color(1);
    }

    /**
     * Sac mou couché le long de X (sac marin, sac de tente, bâche roulée) : profil (x, rayon, fronces) du fond au col,
     * section aplatie (sy), dessous tassé à plat sur le sol, bosses de bruit, fronces (plis rayonnants) là où la toile est
     * resserrée. Le dessous est ramené en y = 0. Les décalcomanies suivent la même surface (décollées de « off »).
     */
    static final class SacMou {
        final float[][] prof;
        final float sy, plat, bruit;
        final int graine;
        float dy;

        SacMou(float[][] prof, float sy, float plat, float bruit, int graine) {
            this.prof = prof;
            this.sy = sy;
            this.plat = plat;
            this.bruit = bruit;
            this.graine = graine;
            float mn = Float.MAX_VALUE;
            for (int i = 0; i < prof.length; i++) for (int j = 0; j < 24; j++) mn = Math.min(mn, point(i, j / 24f * 360f, 0)[1]);
            dy = -mn;
        }

        /** t : indice (fractionnaire) dans le profil ; phi : degrés depuis l'avant (+Z) vers le haut (+Y). */
        float[] point(float t, float phi, float off) {
            int i = Math.max(0, Math.min(prof.length - 2, (int) Math.floor(t)));
            float f = Math.max(0, Math.min(1, t - i));
            float x = prof[i][0] + (prof[i + 1][0] - prof[i][0]) * f, r = prof[i][1] + (prof[i + 1][1] - prof[i][1]) * f;
            float g = prof[i][2] + (prof[i + 1][2] - prof[i][2]) * f;
            double a = Math.toRadians(phi);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float n = Noise.fbm(x * 0.28f + graine * 2.3f, c * 1.3f + graine, s * 1.3f, 2);
            float rr = r * (1 + bruit * n + g * 0.11f * (float) Math.sin(a * 9 + x * 1.7f));
            float y = rr * sy * s, z = rr * c;
            float yb = -plat * r * sy;
            if (y < yb) y = yb + (y - yb) * 0.12f;
            if (r > 1e-3f) {
                y += off * s;
                z += off * c;
            }
            return p(x, y + dy, z);
        }

        void construire(MeshBuilder b, Mat m, int nu) {
            int ni = prof.length, nj = nu + 1;
            float[][][] P = new float[ni][nj][];
            float[][] S = new float[ni][nj], T = new float[ni][nj];
            float w = m.world > 0 ? m.world : 16f, len = 0;
            for (int i = 0; i < ni; i++) {
                if (i > 0) len += (float) Math.hypot(prof[i][0] - prof[i - 1][0], prof[i][1] - prof[i - 1][1]);
                for (int j = 0; j < nj; j++) {
                    P[i][j] = point(i, (j % nu) * 360f / nu, 0);
                    S[i][j] = (float) j / nu * (float) (2 * Math.PI * 2.6f) / w;
                    T[i][j] = len / w;
                }
            }
            b.grid(P, null, S, T, true, m, MeshBuilder.Orient.OUT, true);
        }

        /** Décalcomanie : de l'indice t0 à t1 le long du sac (gauche → droite), de phiBas à phiHaut. */
        void decal(MeshBuilder b, Mat m, float t0, float t1, float phiBas, float phiHaut, int n, int k, float off) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < k; j++) {
                    float sa = (float) i / n, sb = (float) (i + 1) / n, ta = (float) j / k, tb = (float) (j + 1) / k;
                    float[] a = point(t0 + (t1 - t0) * sa, phiHaut + (phiBas - phiHaut) * ta, off);
                    float[] bb = point(t0 + (t1 - t0) * sb, phiHaut + (phiBas - phiHaut) * ta, off);
                    float[] c = point(t0 + (t1 - t0) * sb, phiHaut + (phiBas - phiHaut) * tb, off);
                    float[] d = point(t0 + (t1 - t0) * sa, phiHaut + (phiBas - phiHaut) * tb, off);
                    float[] nrm = norm(cross(sub(c, a), sub(d, bb)));
                    // la normale doit sortir du sac (vers l'avant / le haut de la section)
                    double ph = Math.toRadians(phiHaut + (phiBas - phiHaut) * (ta + tb) / 2);
                    if (nrm[1] * Math.sin(ph) + nrm[2] * Math.cos(ph) < 0) nrm = p(-nrm[0], -nrm[1], -nrm[2]);
                    b.poly(new float[][]{d, c, bb, a}, new float[][]{{m.u(sa), m.v(tb)}, {m.u(sb), m.v(tb)}, {m.u(sb), m.v(ta)}, {m.u(sa), m.v(ta)}}, null, nrm);
                }
            }
        }
    }

    static float[] sub(float[] a, float[] b) {
        return p(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
    }

    static float[] cross(float[] a, float[] b) {
        return p(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
    }

    /**
     * Pochoir (ou étiquette) posé au-dessus des couches d'usure communes (boue 0,05, auréole 0,08, coulure 0,11, autres
     * 0,14 px) : plan à 0,18 px de la face, la peinture ne scintille jamais avec la crasse qui la recouvre.
     */
    static void pochoir(MeshBuilder b, Mat m, char face, float cx, float cy, float cz, float w, float h) {
        float o = 0.13f;
        switch (face) {
            case 'Z' -> cz -= o;
            case 'x' -> cx -= o;
            case 'X' -> cx += o;
            case 'y' -> cy += o;
            default -> cz += o;
        }
        Kit.pochoir(b, m, face, cx, cy, cz, w, h);
    }

    /** Fût de 22 segments : décalcomanie alignée sur ses facettes (k0 : première facette, n : nombre de facettes). */
    static void decalFut(MeshBuilder b, Mat m, float r, int k0, int n, float y0, float y1) {
        float pas = 360f / 22;
        decalTour(b, m, r, (k0 + n / 2f) * pas, n * pas, y0, y1, n);
    }

    /** Clou tordu qui dépasse d'un chant (base en (x, y, z), direction d'origine +Y, tordu vers dx/dz). */
    static void clou(MeshBuilder b, float x, float y, float z, float l, float dx, float dz) {
        b.tube(new float[][]{p(x, y - 0.1f, z), p(x, y + l * 0.55f, z), p(x + dx, y + l * 0.85f, z + dz)}, new float[]{0.06f, 0.06f, 0.05f}, 4,
                AtlasUsure.ROUILLE, false, true, 0, false);
    }

    /** Ruban (sangle) posé sur un chemin, largeur constante, côté donné (direction de la largeur). */
    static void sangle(MeshBuilder b, Mat m, float[][] chemin, float largeur, float[] cote) {
        float[] w = new float[chemin.length];
        java.util.Arrays.fill(w, largeur);
        b.ribbon(chemin, w, cote, m);
    }

    static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }
}
