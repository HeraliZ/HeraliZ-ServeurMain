package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT3bAtlas.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/** Pièces communes du lot T3b (repère : px, sol en y = 0, avant vers +Z, dos au mur en z = 0). */
final class LotT3bKit {
    private LotT3bKit() {
    }

    static float[] rayons(int n, float r) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, r);
        return o;
    }

    /** Intérieur d'un caisson ouvert vers +Z : fond, parois, sol et plafond tournés vers la cavité. */
    static void interieur(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, Mat murs, Mat sol) {
        b.face(murs, v(0, 0, 1), p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0));
        b.face(murs, v(1, 0, 0), p(x0, y0, z1), p(x0, y0, z0), p(x0, y1, z0), p(x0, y1, z1));
        b.face(murs, v(-1, 0, 0), p(x1, y0, z0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, z0));
        if (sol != null) b.face(sol, v(0, 1, 0), p(x0, y0, z0), p(x0, y0, z1), p(x1, y0, z1), p(x1, y0, z0));
        b.face(murs, v(0, -1, 0), p(x0, y1, z1), p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1));
    }

    /** Intérieur d'un bac ouvert vers le haut : fond et quatre parois tournées vers la cavité. */
    static void interieurHaut(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, Mat murs, Mat fond) {
        b.face(fond, v(0, 1, 0), p(x0, y0, z0), p(x0, y0, z1), p(x1, y0, z1), p(x1, y0, z0));
        b.face(murs, v(0, 0, 1), p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0));
        b.face(murs, v(0, 0, -1), p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1));
        b.face(murs, v(1, 0, 0), p(x0, y0, z1), p(x0, y0, z0), p(x0, y1, z0), p(x0, y1, z1));
        b.face(murs, v(-1, 0, 0), p(x1, y0, z0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, z0));
    }

    /**
     * Étiquette (matière FIT) enroulée sur un cylindre d'axe Y local : arc de a0 à a1 degrés (90 = face +Z), le texte se
     * lit de gauche à droite vu de l'extérieur.
     */
    static void bandeCyl(MeshBuilder b, Mat m, float r, float y0, float y1, float a0, float a1, int segs) {
        bandeCone(b, m, r, r, y0, y1, a0, a1, segs);
    }

    /** Étiquette (FIT) sur un tronc de cône d'axe Y : rayon r0 en y0, r1 en y1 (seau, entonnoir). */
    static void bandeCone(MeshBuilder b, Mat m, float r0, float r1, float y0, float y1, float a0, float a1, int segs) {
        float pente = (r0 - r1) / (y1 - y0);
        for (int i = 0; i < segs; i++) {
            double t0 = Math.toRadians(a0 + (a1 - a0) * i / segs), t1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / segs);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float[][] pts = {{r0 * c0, y0, r0 * s0}, {r0 * c1, y0, r0 * s1}, {r1 * c1, y1, r1 * s1}, {r1 * c0, y1, r1 * s0}};
            float u0 = 1f - (float) i / segs, u1 = 1f - (float) (i + 1) / segs;
            float[][] uv = {{m.u(u0), m.v(1)}, {m.u(u1), m.v(1)}, {m.u(u1), m.v(0)}, {m.u(u0), m.v(0)}};
            float[][] nr = {{c0, pente, s0}, {c1, pente, s1}, {c1, pente, s1}, {c0, pente, s0}};
            b.poly(pts, uv, nr, new float[]{(c0 + c1) / 2, pente, (s0 + s1) / 2});
        }
    }

    /**
     * Cadenas de laiton terni (5 cm) accroché en (x, y : haut du corps, z : dos), face +Z. force : anse forcée (sortie
     * du corps et tournée).
     */
    static void cadenas(MeshBuilder b, float x, float y, float z, boolean force) {
        b.bevelBox(x - 0.62f, y - 1.25f, z, x + 0.62f, y, z + 0.48f, 0.14f, AtlasCivil.BRASS);
        b.push().translate(x, y + (force ? 0.45f : 0), z + 0.24f);
        if (force) b.rotateY(55);
        b.rotateX(90);
        b.torus(0.4f, 0.1f, 12, 5, 0, 180, METAL_NU);
        b.pop();
        b.push().translate(x, y - 0.75f, z + 0.48f).rotateX(90);
        b.cylinder(0.12f, 0, 0.04f, 6, DARK_KEY, null, DARK_KEY);
        b.pop();
    }

    static final Mat DARK_KEY = AtlasCivil.DARK;

    /** Charnière (petit cylindre vertical) à la position (x, y, z), hauteur h. */
    static void gond(MeshBuilder b, float x, float y, float z, float h, Mat m) {
        b.push().translate(x, y, z);
        b.cylinder(0.16f, 0, h, 8, m, m, m);
        b.pop();
    }

    /**
     * Brodequin (ranger) de cuir noir : semelle sur y = 0, talon en z = 0, bout vers +Z (longueur 4,5 px = 28 cm).
     */
    static void ranger(MeshBuilder b) {
        b.bevelBox(-0.82f, 0, -0.1f, 0.82f, 0.36f, 4.5f, 0.12f, CAOUTCHOUC_USE);
        b.bevelBox(-0.78f, 0.32f, 0, 0.78f, 1.55f, 3.2f, 0.42f, CUIR_NOIR);
        b.push().translate(0, 0.34f, 3.05f).scale(0.78f, 0.75f, 1.35f);
        b.sphereSection(1.0f, 12, 5, 0, 90, CUIR_NOIR);
        b.pop();
        b.bevelBox(-0.74f, 0.9f, 0.02f, 0.74f, 4.1f, 1.95f, 0.45f, CUIR_NOIR);
        b.color(0.75f);
        b.bevelBox(-0.8f, 3.85f, -0.04f, 0.8f, 4.35f, 2.0f, 0.2f, CUIR_NOIR);
        b.color(1);
        // languette et lacets
        b.bevelBox(-0.42f, 1.3f, 1.9f, 0.42f, 4.2f, 2.12f, 0.12f, CUIR_NOIR);
        for (float y = 1.6f; y < 3.9f; y += 0.45f) b.bevelBox(-0.55f, y, 2.08f, 0.55f, y + 0.1f, 2.2f, 0.03f, NOIR_MAT);
    }

    /** Cartouche de chasse (calibre 12, 7 cm) couchée le long de X local : culot laiton en x = 0. */
    static void cartouche(MeshBuilder b, Mat tube, boolean vide) {
        b.push().rotateZ(-90);
        b.cylinder(0.31f, 0, 0.45f, 8, AtlasCivil.BRASS, AtlasCivil.BRASS, null);
        b.cylinder(0.29f, 0.45f, 1.15f, 8, tube, null, vide ? INTERIEUR_SALE : tube);
        b.pop();
    }

    /** Cagette / caisse en planches ouverte (fond, quatre côtés en planches avec un jour, intérieur visible). */
    static void caissePlanches(MeshBuilder b, float x0, float y0, float z0, float L, float W, float H, Mat m, long graine) {
        float x1 = x0 + L, z1 = z0 + W, e = 0.4f;
        Random r = new Random(graine);
        b.bevelBox(x0 + 0.15f, y0, z0 + 0.15f, x1 - 0.15f, y0 + 0.45f, z1 - 0.15f, 0.08f, m);
        int n = Math.max(2, Math.round(H / 2.2f));
        float hp = (H - 0.45f) / n;
        for (int k = 0; k < n; k++) {
            float ya = y0 + 0.45f + k * hp, yb = ya + hp - 0.12f;
            float d = (r.nextFloat() - 0.5f) * 0.06f;
            b.bevelBox(x0 + e, ya, z1 - e + d, x1 - e, yb, z1 + d, 0.08f, m);
            b.bevelBox(x0 + e, ya, z0, x1 - e, yb, z0 + e, 0.08f, m);
            b.bevelBox(x0, ya, z0 + e, x0 + e, yb, z1 - e, 0.08f, m);
            b.bevelBox(x1 - e, ya, z0 + e, x1, yb, z1 - e, 0.08f, m);
        }
        // montants d'angle intérieurs
        for (float[] c : new float[][]{{x0 + e, z0 + e}, {x1 - e - 0.6f, z0 + e}, {x0 + e, z1 - e - 0.6f}, {x1 - e - 0.6f, z1 - e - 0.6f}}) {
            b.bevelBox(c[0], y0 + 0.44f, c[1], c[0] + 0.6f, y0 + H - 0.1f, c[1] + 0.6f, 0.08f, m);
        }
    }

    /** Panneau de grillage soudé double face (rendu en découpe) : quadrilatère pts, normale n (côté avant). */
    static void grillage(MeshBuilder b, float[] n, float[]... pts) {
        float e = 0.025f;
        float[][] av = new float[pts.length][], ar = new float[pts.length][];
        for (int k = 0; k < pts.length; k++) {
            av[k] = p(pts[k][0] + n[0] * e, pts[k][1] + n[1] * e, pts[k][2] + n[2] * e);
            ar[k] = p(pts[k][0] - n[0] * e, pts[k][1] - n[1] * e, pts[k][2] - n[2] * e);
        }
        b.face(GRILLE_FINE, n, av);
        b.face(GRILLE_FINE, v(-n[0], -n[1], -n[2]), ar);
    }

    /**
     * Grillage sur un rectangle vertical, plan x = c (planX) ou z = c, de a0 à a1 (z ou x) et de y0 à y1 ; découpé sur
     * la grille de la tuile (4 px) pour que la maille ne soit jamais étirée. sens : signe de la normale.
     */
    static void grillageRect(MeshBuilder b, boolean planX, float c, float a0, float a1, float y0, float y1, float sens) {
        java.util.List<Float> as = coupes(a0, a1), ys = coupes(y0, y1);
        for (int i = 0; i + 1 < as.size(); i++) {
            for (int j = 0; j + 1 < ys.size(); j++) {
                float aa = as.get(i), ab = as.get(i + 1), ya = ys.get(j), yb = ys.get(j + 1);
                if (planX) grillage(b, v(sens, 0, 0), p(c, ya, aa), p(c, ya, ab), p(c, yb, ab), p(c, yb, aa));
                else grillage(b, v(0, 0, sens), p(aa, ya, c), p(ab, ya, c), p(ab, yb, c), p(aa, yb, c));
            }
        }
    }

    /** Bornes a, b et les lignes de la grille des tuiles de 4 px (coordonnée + 8 multiple de 4) entre les deux. */
    static java.util.List<Float> coupes(float a, float b) {
        java.util.List<Float> o = new java.util.ArrayList<>();
        o.add(a);
        for (float g = (float) Math.ceil((a + 8) / 4f) * 4f - 8; g < b - 0.05f; g += 4f) if (g > a + 0.05f) o.add(g);
        o.add(b);
        return o;
    }

    /** Sachet de pièce à conviction (plastique, bande de fermeture rouge, étiquette de scellé), posé sur y = 0. */
    static void sachet(MeshBuilder b, float w, float h, float d, boolean etiquette) {
        b.bevelBox(-w / 2, 0, -d / 2, w / 2, h, d / 2, Math.min(d * 0.45f, 0.35f), SACHET);
        b.color(0.75f, 0.22f, 0.18f);
        b.bevelBox(-w / 2 + 0.12f, h - 0.5f, -d / 2 - 0.04f, w / 2 - 0.12f, h - 0.28f, d / 2 + 0.04f, 0.03f, PLASTIQUE_USE);
        b.color(1);
        if (etiquette) KitUsure.decal(b, ETIQ_SCELLE, 'z', 0, Math.min(h * 0.42f, h - 0.6f - w * 0.18f), d / 2, w * 0.72f, w * 0.36f);
    }

    /** Chemise cartonnée (dossier A4 fermé) à plat, teinte (r, g, b), feuilles qui dépassent. */
    static void dossier(MeshBuilder b, float r, float g, float bl) {
        b.color(r, g, bl);
        b.bevelBox(-2.55f, 0, -1.9f, 2.55f, 0.17f, 1.9f, 0.05f, CHEMISE);
        b.color(1);
        b.box(-2.4f, 0.045f, -1.75f, 2.42f, 0.125f, 2.08f, PAPIER);
    }

    /** Ruban posé à plat (sangle, bande) suivant un chemin (x, y, z), largeur w, face vers le haut. */
    static void bandeSol(MeshBuilder b, float[][] pts, float w, Mat m) {
        int n = pts.length;
        float[][] g = new float[n][], d = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = pts[Math.max(0, i - 1)], c = pts[Math.min(n - 1, i + 1)];
            float tx = c[0] - a[0], tz = c[2] - a[2], l = (float) Math.hypot(tx, tz);
            float nx = -tz / l * w / 2, nz = tx / l * w / 2;
            g[i] = p(pts[i][0] + nx, pts[i][1], pts[i][2] + nz);
            d[i] = p(pts[i][0] - nx, pts[i][1], pts[i][2] - nz);
        }
        for (int i = 0; i < n - 1; i++) b.face(m, v(0, 1, 0), g[i], g[i + 1], d[i + 1], d[i]);
    }

    /** Clous (têtes rouillées) le long d'une planche, face +Z. */
    static void clous(MeshBuilder b, float x0, float x1, float y, float z, int n) {
        for (int k = 0; k < n; k++) {
            float x = n == 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * k / (n - 1);
            KitUsure.boulon(b, x, y, z, 0.11f);
        }
    }
}
