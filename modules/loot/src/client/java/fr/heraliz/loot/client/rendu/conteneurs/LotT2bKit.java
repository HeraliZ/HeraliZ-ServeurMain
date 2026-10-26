package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BRASS;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.DARK;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.WHITE;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.METAL_NU;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.ROUILLE;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bAtlas.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Pièces communes du lot T2b (repère : pixels, sol en y = 0, dos au mur en z = 0, avant vers +Z) : bois dont le fil
 * suit la pièce, portes à cadre et panneau, ferrures, pieds, linge plié, drap qui pend (double face), toiles
 * d'araignée, vaisselle, bouteilles, bocaux et conserves.
 */
final class LotT2bKit {
    private LotT2bKit() {
    }

    static final int NX = MeshBuilder.SKIP_NX, PX = MeshBuilder.SKIP_PX, NY = MeshBuilder.SKIP_NY, PY = MeshBuilder.SKIP_PY,
            NZ = MeshBuilder.SKIP_NZ, PZ = MeshBuilder.SKIP_PZ;

    // ================================================================== bois
    /**
     * Pièce de bois biseautée dont le fil (horizontal dans la tuile) suit la plus grande dimension : la pièce est
     * construite à plat puis tournée (la projection des UV se fait avant la rotation). skip : faces omises (repère monde).
     */
    static void bois(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float bev, Mat m, int skip) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        if (dy > dx && dy >= dz) {
            // local +X = monde +Y, local +Y = monde -X
            int s = remap(skip, new int[]{PY, NY, NX, PX, NZ, PZ});
            b.push().translate(x0, y0, 0).rotateZ(90);
            b.bevelBox(0, -dx, z0, dy, 0, z1, bev, m, null, s);
            b.pop();
        } else if (dz > dx && dz > dy) {
            // local +X = monde +Z, local +Z = monde -X
            int s = remap(skip, new int[]{PZ, NZ, NY, PY, NX, PX});
            b.push().translate(x0, 0, z0).rotateY(-90);
            b.bevelBox(0, y0, -dx, dz, y1, 0, bev, m, null, s);
            b.pop();
        } else {
            b.bevelBox(x0, y0, z0, x1, y1, z1, bev, m, null, skip);
        }
    }

    static void bois(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float bev, Mat m) {
        bois(b, x0, y0, z0, x1, y1, z1, bev, m, 0);
    }

    /** Traduit des drapeaux monde (-X,+X,-Y,+Y,-Z,+Z) en drapeaux locaux : loc[k] = drapeau local de la face monde k. */
    private static int remap(int skip, int[] loc) {
        int s = 0;
        for (int k = 0; k < 6; k++) if ((skip & (1 << k)) != 0) s |= loc[k];
        return s;
    }

    /** Cadre de porte ou de côté (deux montants, deux traverses), face avant en zf, épaisseur zf - zb. */
    static void cadre(MeshBuilder b, float x0, float y0, float x1, float y1, float zb, float zf, float lm, float lh, float lb, Mat m) {
        bois(b, x0, y0, zb, x0 + lm, y1, zf, 0.12f, m);
        bois(b, x1 - lm, y0, zb, x1, y1, zf, 0.12f, m);
        bois(b, x0 + lm, y1 - lh, zb + 0.04f, x1 - lm, y1, zf - 0.04f, 0.12f, m);
        bois(b, x0 + lm, y0, zb + 0.04f, x1 - lm, y0 + lb, zf - 0.04f, 0.12f, m);
    }

    /** Panneau en retrait dans un cadre (x0..x1, y0..y1 : jour du cadre), plate-bande bombée ; zf = face du cadre. */
    static void panneau(MeshBuilder b, float x0, float y0, float x1, float y1, float zf, Mat m) {
        bois(b, x0 - 0.25f, y0 - 0.25f, zf - 0.62f, x1 + 0.25f, y1 + 0.25f, zf - 0.32f, 0.06f, m);
        bois(b, x0 + 0.55f, y0 + 0.55f, zf - 0.36f, x1 - 0.55f, y1 - 0.55f, zf - 0.13f, 0.18f, m);
    }

    // ================================================================== ferrures
    /** Bouton tourné (axe +Z) posé sur une face avant en z. */
    static void bouton(MeshBuilder b, float x, float y, float z, float r, Mat m) {
        b.push().translate(x, y, z).rotateX(90);
        b.lathe(new float[][]{{r * 0.45f, -0.05f}, {r * 0.4f, r * 0.5f}, {r, r * 1.0f}, {r * 0.85f, r * 1.6f}, {0.01f, r * 1.75f}}, 8, 0, 360, m, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Charnière à fiche (laiton) : axe vertical centré en (x, z), de y à y + h. */
    static void fiche(MeshBuilder b, float x, float y, float z, float h) {
        b.push().translate(x, y, z);
        b.cylinder(0.19f, 0.2f, h - 0.2f, 6, BRASS, null, null);
        b.lathe(new float[][]{{0.01f, 0}, {0.16f, 0.02f}, {0.2f, 0.2f}}, 6, 0, 360, BRASS, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.2f, h - 0.2f}, {0.16f, h - 0.02f}, {0.01f, h}}, 6, 0, 360, BRASS, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Entrée de serrure en laiton (écusson + trou de clé) sur une face avant en z. */
    static void serrure(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z);
        b.slab(new float[][]{{0, 0.75f}, {-0.42f, 0.35f}, {-0.42f, -0.55f}, {0, -0.8f}, {0.42f, -0.55f}, {0.42f, 0.35f}}, 0.12f, 0.04f, false, BRASS, null, BRASS);
        b.pop();
        b.box(x - 0.08f, y - 0.42f, z + 0.065f, x + 0.08f, y + 0.12f, z + 0.1f, DARK);
        b.push().translate(x, y + 0.18f, z + 0.065f).rotateX(90);
        b.cylinder(0.14f, 0, 0.035f, 8, DARK, null, DARK);
        b.pop();
    }

    /** Pied miche (boule aplatie) sous un meuble. */
    static void piedMiche(MeshBuilder b, float x, float z, float r, float h, Mat m) {
        b.push().translate(x, 0, z);
        b.lathe(new float[][]{{0.01f, 0}, {r * 0.7f, 0.02f}, {r, h * 0.35f}, {r * 0.92f, h * 0.68f}, {r * 0.6f, h * 0.88f}, {r * 0.55f, h}}, 10, 0, 360, m, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    // ================================================================== textiles
    /** Pile de linge plié (n pièces) posée en y, coin arrière gauche (x0, z0) ; renvoie le haut de la pile. */
    static float linge(MeshBuilder b, float x0, float y, float z0, float w, float d, int n, Mat[] mats, long graine) {
        Random r = new Random(graine);
        for (int k = 0; k < n; k++) {
            float e = 0.45f + r.nextFloat() * 0.3f, dx = (r.nextFloat() - 0.5f) * 0.4f, dz = (r.nextFloat() - 0.5f) * 0.3f;
            float cx = x0 + w / 2 + dx, cz = z0 + d / 2 + dz;
            b.push().translate(cx, y, cz).rotateY((k % 2 == 0 ? 1 : -1) * (3.0f + r.nextFloat() * 1.5f));
            b.bevelBox(-w / 2, 0, -d / 2, w / 2, e, d / 2, Math.min(0.24f, e * 0.45f), mats[k % mats.length], null, NY);
            b.pop();
            y += e - 0.03f;
        }
        return y;
    }

    /**
     * Étoffe souple (drap, couverture) : grille de points P[i][j], émise sur les deux faces décalées de ±0,015 le long
     * de la normale (visible des deux côtés, aucune face confondue).
     */
    static void drap(MeshBuilder b, float[][][] P, Mat m) {
        int ni = P.length, nj = P[0].length;
        float[][][] A = new float[ni][nj][], B = new float[ni][nj][];
        float[][] S = new float[ni][nj], T = new float[ni][nj];
        float w = m.world > 0 ? m.world : 8f;
        float[] li = new float[ni];
        for (int i = 1; i < ni; i++) li[i] = li[i - 1] + dist(P[i][nj / 2], P[i - 1][nj / 2]);
        for (int i = 0; i < ni; i++) {
            float lj = 0;
            for (int j = 0; j < nj; j++) {
                if (j > 0) lj += dist(P[i][j], P[i][j - 1]);
                float[] di = sub(P[Math.min(ni - 1, i + 1)][j], P[Math.max(0, i - 1)][j]);
                float[] dj = sub(P[i][Math.min(nj - 1, j + 1)], P[i][Math.max(0, j - 1)]);
                float[] n = norm(cross(di, dj));
                A[i][j] = new float[]{P[i][j][0] + n[0] * 0.015f, P[i][j][1] + n[1] * 0.015f, P[i][j][2] + n[2] * 0.015f};
                B[i][j] = new float[]{P[i][j][0] - n[0] * 0.015f, P[i][j][1] - n[1] * 0.015f, P[i][j][2] - n[2] * 0.015f};
                S[i][j] = lj / w;
                T[i][j] = li[i] / w;
            }
        }
        b.grid(A, null, S, T, false, m, MeshBuilder.Orient.KEEP, true);
        b.grid(B, null, S, T, false, m, MeshBuilder.Orient.FLIP, true);
    }

    // ================================================================== décor
    /**
     * Toile d'araignée d'angle (double face) : coin d'accroche en (x, y, z), la toile s'étend de w vers +X local et de
     * h vers -Y local ; rotY / rotZ orientent le plan (rotY 90 : plan YZ, la toile s'étend vers -Z).
     */
    static void toile(MeshBuilder b, float x, float y, float z, float w, float h, float rotY, float rotZ) {
        b.push().translate(x, y, z).rotateY(rotY).rotateZ(rotZ);
        b.face(TOILE_ARAIGNEE, v(0, 0, 1), p(0, -h, 0.012f), p(w, -h, 0.012f), p(w, 0, 0.012f), p(0, 0, 0.012f));
        b.face(TOILE_ARAIGNEE, v(0, 0, -1), p(w, -h, -0.012f), p(0, -h, -0.012f), p(0, 0, -0.012f), p(w, 0, -0.012f));
        b.pop();
    }

    /** Assiette à plat (repère local, posée en y = 0) : dessous bombé, dessus peint au niveau du marli. */
    static void assiette(MeshBuilder b, float r, Mat dessus) {
        b.lathe(new float[][]{{r * 0.6f, 0}, {r * 0.8f, 0.07f}, {r, 0.19f}, {r * 0.985f, 0.22f}}, 11, 0, 360, WHITE, 1, 1, 0, 0, 0, false);
        b.disc(r * 0.6f, 0.004f, 14, WHITE, false);
        b.disc(r * 0.985f, 0.22f, 14, dessus, true);
    }

    /** Pile d'assiettes (n) posée en (x, y, z). */
    static void pile(MeshBuilder b, float x, float y, float z, float r, int n, Mat dessus) {
        float h = n * 0.2f;
        b.push().translate(x, y, z);
        b.cylinder(r, 0, h, 14, LotT2bAtlas.pileAssiettes(h), null, dessus);
        b.pop();
    }

    /** Bouteille de vin (30 cm) d'axe +Y local, base en 0 ; capsule de couleur. */
    static void bouteille(MeshBuilder b, Mat verre, Mat capsule, int segs) {
        b.lathe(new float[][]{{0.01f, 0.02f}, {0.5f, 0}, {0.6f, 0.1f}, {0.6f, 3.0f}, {0.5f, 3.35f}, {0.27f, 3.72f}, {0.235f, 3.95f}}, segs, 0, 360, verre, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.235f, 3.95f}, {0.235f, 4.38f}, {0.27f, 4.42f}, {0.27f, 4.8f}, {0.01f, 4.81f}}, segs, 0, 360, capsule, 1, 1, 0, 0, 0, false);
    }

    /** Bocal (contenu peint déroulé) debout en (x, y, z), couvercle à vis rouillé ou joint orange. */
    static void bocal(MeshBuilder b, float x, float y, float z, float r, float h, Mat contenu, boolean joint) {
        b.push().translate(x, y, z);
        b.cylinder(r, 0, h, 12, contenu, null, null);
        b.lathe(new float[][]{{r, h}, {r * 0.86f, h + 0.25f}, {r * 0.86f, h + 0.3f}}, 12, 0, 360, contenu, 1, 1, 0, 0, 0, false);
        if (joint) {
            b.push().translate(0, h + 0.3f, 0);
            b.torus(r * 0.88f, 0.09f, 12, 4, 0, 360, AtlasCivil.ORANGE);
            b.pop();
            Mat verre = contenu.sub(0, 0, 1, 0.1f);
            b.cylinder(r * 0.9f, h + 0.3f, h + 0.52f, 12, verre, null, verre);
        } else {
            b.cylinder(r * 0.9f, h + 0.28f, h + 0.62f, 12, ROUILLE, null, METAL_NU);
        }
        b.pop();
    }

    /** Boîte de conserve debout en (x, y, z) : étiquette déroulée (avant en u = 0,25), couvercle serti. */
    static void conserve(MeshBuilder b, float x, float y, float z, float r, float h, Mat etiquette) {
        b.push().translate(x, y, z);
        b.cylinder(r, 0.1f, h - 0.1f, 12, etiquette, null, null);
        b.cylinder(r + 0.04f, 0, 0.12f, 12, METAL_NU, null, null);
        b.cylinder(r + 0.04f, h - 0.12f, h, 12, METAL_NU, null, METAL_NU);
        b.pop();
    }

    /** Bûche (cylindre d'écorce, bouts de bois) couchée selon X local. */
    static void buche(MeshBuilder b, float l, float r) {
        b.push().rotateZ(-90);
        b.cylinder(r, -l / 2, l / 2, 9, ECORCE, BOUT_BOIS, BOUT_BOIS);
        b.pop();
    }

    /** Boîte englobante {x0, y0, z0, x1, y1, z1} d'une construction (bâtie à blanc) : pour recaler une pièce penchée. */
    static float[] bornes(java.util.function.Consumer<MeshBuilder> f) {
        MeshBuilder t = new MeshBuilder();
        f.accept(t);
        float[] bo = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
        t.shade(0, (x, y, z, nx, ny, nz, rgb) -> {
            bo[0] = Math.min(bo[0], x);
            bo[1] = Math.min(bo[1], y);
            bo[2] = Math.min(bo[2], z);
            bo[3] = Math.max(bo[3], x);
            bo[4] = Math.max(bo[4], y);
            bo[5] = Math.max(bo[5], z);
        });
        return bo;
    }

    /**
     * Construit f recalé : posé au sol (y min = 0) et, si mur, touchant le mur (z min = 0,05). f est construite
     * dans le repère du modèle (sans transformation en cours).
     */
    static void recaler(MeshBuilder b, boolean mur, java.util.function.Consumer<MeshBuilder> f) {
        float[] bo = bornes(f);
        b.push().translate(0, -bo[1], mur ? 0.05f - bo[2] : 0);
        f.accept(b);
        b.pop();
    }

    /** Repère local posé sur une surface : origine o, axe X local selon ax, axe Y local selon ay (Z = normale sortante). */
    static void repere(MeshBuilder b, float[] o, float[] ax, float[] ay) {
        float[] x = norm(ax), z = norm(cross(ax, ay)), y = cross(z, x);
        b.push().mul(new org.joml.Matrix4f(x[0], x[1], x[2], 0, y[0], y[1], y[2], 0, z[0], z[1], z[2], 0, o[0], o[1], o[2], 1));
    }

    /**
     * Étiquette courbe (Mat FIT) collée sur un cylindre d'axe Y local centré en (cx, cz), rayon r : de y0 à y1,
     * centrée sur l'angle a (degrés, 90 = face +Z), largeur angulaire da.
     */
    static void etiquette(MeshBuilder b, Mat m, float cx, float cz, float r, float y0, float y1, float a, float da) {
        int nj = Math.max(3, (int) (da / 12) + 2);
        float[][][] P = new float[2][nj][], N = new float[2][nj][];
        float[][] S = new float[2][nj], T = new float[2][nj];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < nj; j++) {
                double an = Math.toRadians(a + da / 2 - da * j / (nj - 1));
                float c = (float) Math.cos(an), s = (float) Math.sin(an);
                P[i][j] = new float[]{cx + c * r, i == 0 ? y1 : y0, cz + s * r};
                N[i][j] = new float[]{c, 0, s};
                S[i][j] = j / (float) (nj - 1);
                T[i][j] = i;
            }
        }
        b.grid(P, N, S, T, false, m, MeshBuilder.Orient.KEEP, false);
    }

    /** Panneau ajouré (grillage, filet) double face dans le plan z, de (x0, y0) à (x1, y1). */
    static void grille(MeshBuilder b, Mat m, float x0, float y0, float x1, float y1, float z) {
        b.face(m, v(0, 0, 1), p(x0, y0, z + 0.012f), p(x1, y0, z + 0.012f), p(x1, y1, z + 0.012f), p(x0, y1, z + 0.012f));
        b.face(m, v(0, 0, -1), p(x1, y0, z - 0.012f), p(x0, y0, z - 0.012f), p(x0, y1, z - 0.012f), p(x1, y1, z - 0.012f));
    }

    /** Sac souple (coussin) centré en (x, y, z) : demi-dimensions rx, ry, rz ; carre &lt; 1 = plus carré. */
    static void coussin(MeshBuilder b, float x, float y, float z, float rx, float ry, float rz, float carre, float bosse, int graine, Mat m) {
        b.push().translate(x, y, z);
        b.blob(rx, ry, rz, carre, bosse, 1.4f, graine, 16, m);
        b.pop();
    }

    /** Manche (tube droit) de a à c, rayon r. */
    static void manche(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 7, m, true, true, 0, false);
    }

    /**
     * Feuilles, papiers, brins épars au sol (remplace KitUsure.jonchee, dont les cartes inclinées passent sous le sol) :
     * cartes presque à plat ; cinq orientations (à plat, ±3° autour de X, ±3° autour de Z) et des hauteurs propres à
     * chaque appel (graine modulo 3) : aucune face confondue, rien sous le sol. n ≤ 10.
     */
    static void epars(MeshBuilder b, float x0, float z0, float x1, float z1, int n, Mat m, float taille, long graine) {
        Random r = new Random(graine);
        n = Math.min(n, 10);
        int g = (int) Math.floorMod(graine, 3);
        for (int k = 0; k < n; k++) {
            float x = x0 + r.nextFloat() * (x1 - x0), z = z0 + r.nextFloat() * (z1 - z0), s = taille * (0.6f + r.nextFloat() * 0.5f);
            float y = 0.1f + 0.032f * ((k / 5) * 3 + g);
            int c = k % 5;
            b.push().translate(x, y, z);
            if (c == 1 || c == 2) b.rotateX(c == 1 ? 3 : -3);
            else if (c == 3 || c == 4) b.rotateZ(c == 3 ? 3 : -3);
            b.rotateY(r.nextFloat() * 360).rotateX(-90);
            b.decalDouble(m.sub(r.nextFloat() * 0.5f, r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.5f), s, s * (0.6f + r.nextFloat() * 0.5f));
            b.pop();
        }
    }

    /** Éclats à plat au sol (verre, faïence, granulés, terre) : remplace KitUsure.debris (éclats debout à demi enterrés). n ≤ 10. */
    static void eclats(MeshBuilder b, float cx, float cz, float rayon, int n, Mat m, long graine) {
        Random r = new Random(graine);
        n = Math.min(n, 10);
        int g = (int) Math.floorMod(graine, 3);
        for (int k = 0; k < n; k++) {
            double a = r.nextFloat() * Math.PI * 2;
            float d = rayon * (float) Math.sqrt(r.nextFloat());
            float x = cx + (float) Math.cos(a) * d, z = cz + (float) Math.sin(a) * d;
            float s = 0.25f + r.nextFloat() * 0.4f;
            float y = 0.08f + 0.032f * ((k / 4) * 3 + g);
            int c = k % 4;
            b.push().translate(x, y, z);
            if (c < 2) b.rotateX(c == 0 ? 6 : -6);
            else b.rotateZ(c == 2 ? 6 : -6);
            b.rotateY(r.nextFloat() * 360).rotateX(-90);
            b.prism(new float[][]{{-s, 0}, {s * 0.8f, -s * 0.4f}, {s * 0.3f, s * 0.9f}}, 0.07f, m, m);
            b.pop();
        }
    }

    static float[] lerp(float[] a, float[] c, float t) {
        return new float[]{a[0] + (c[0] - a[0]) * t, a[1] + (c[1] - a[1]) * t, a[2] + (c[2] - a[2]) * t};
    }

    static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    static float[] sub(float[] a, float[] c) {
        return new float[]{a[0] - c[0], a[1] - c[1], a[2] - c[2]};
    }

    static float[] cross(float[] a, float[] c) {
        return new float[]{a[1] * c[2] - a[2] * c[1], a[2] * c[0] - a[0] * c[2], a[0] * c[1] - a[1] * c[0]};
    }

    static float[] norm(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-7f ? new float[]{0, 1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static float dist(float[] a, float[] c) {
        float dx = a[0] - c[0], dy = a[1] - c[1], dz = a[2] - c[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
