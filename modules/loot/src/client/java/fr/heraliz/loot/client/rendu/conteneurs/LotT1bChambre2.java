package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/** T1b — chambre (suite) : panier à linge, matelas, cartons, coffre à jouets, grand cabas, meuble à chaussures. */
final class LotT1bChambre2 {
    private LotT1bChambre2() {
    }

    // =================================================================== panier à linge
    /** Panier à linge ajouré en plastique bleu (45 x 32 x 55 cm), linge sale qui déborde, couvercle posé contre. */
    static void panierLinge(MeshBuilder b) {
        float x0 = 4.4f, x1 = 11.6f, z0 = 0.6f, z1 = 5.8f, h = 8.8f, t = 0.28f;
        b.color(0.64f, 0.76f, 0.86f);
        b.bevelBox(x0, 0, z0, x1, 0.5f, z1, 0.12f, PLAST);
        for (float[] c : new float[][]{{x0 + 0.35f, z0 + 0.35f}, {x1 - 0.35f, z0 + 0.35f}, {x0 + 0.35f, z1 - 0.35f}, {x1 - 0.35f, z1 - 0.35f}}) {
            b.bevelBox(c[0] - 0.35f, 0.48f, c[1] - 0.35f, c[0] + 0.35f, h - 0.68f, c[1] + 0.35f, 0.1f, PLAST);
        }
        for (float[] yb : new float[][]{{0.5f, 1.1f}, {4.3f, 4.9f}}) {
            b.bevelBox(x0 + 0.7f, yb[0], z1 - t, x1 - 0.7f, yb[1], z1, 0.06f, PLAST);
            b.bevelBox(x0, yb[0], z0 + 0.7f, x0 + t, yb[1], z1 - 0.7f, 0.06f, PLAST);
            b.bevelBox(x1 - t, yb[0], z0 + 0.7f, x1, yb[1], z1 - 0.7f, 0.06f, PLAST);
        }
        b.box(x0 + 0.7f, 0.5f, z0, x1 - 0.7f, h - 0.7f, z0 + t, PLAST);
        // bord supérieur épais (cadre)
        b.bevelBox(x0 - 0.15f, h - 0.7f, z1 - 0.4f, x1 + 0.15f, h, z1 + 0.15f, 0.15f, PLAST);
        b.bevelBox(x0 - 0.15f, h - 0.7f, z0 - 0.15f, x1 + 0.15f, h, z0 + 0.4f, 0.15f, PLAST);
        b.bevelBox(x0 - 0.15f, h - 0.7f, z0 + 0.38f, x0 + 0.4f, h, z1 - 0.38f, 0.15f, PLAST);
        b.bevelBox(x1 - 0.4f, h - 0.7f, z0 + 0.38f, x1 + 0.15f, h, z1 - 0.38f, 0.15f, PLAST);
        // barreaux entre les fentes (devant et côtés)
        for (float[] yb : new float[][]{{1.1f, 4.3f}, {4.9f, h - 0.7f}}) {
            for (int k = 1; k <= 5; k++) {
                float x = x0 + 0.7f + k * 0.97f - 0.2f;
                b.box(x, yb[0], z1 - t, x + 0.4f, yb[1], z1, PLAST);
            }
            for (int k = 1; k <= 3; k++) {
                float z = z0 + 0.7f + k * 0.95f - 0.2f;
                b.box(x0, yb[0], z, x0 + t, yb[1], z + 0.4f, PLAST);
                b.box(x1 - t, yb[0], z, x1, yb[1], z + 0.4f, PLAST);
            }
        }
        b.color(1);
        // linge dedans (vu par les fentes) et qui déborde
        tas(b, 8.0f, 4.4f, 3.2f, 2.9f, 3.6f, 2.1f, 5, LINGE);
        tas(b, 8.2f, 8.9f, 3.3f, 3.6f, 1.5f, 2.5f, 8, LINGE);
        b.color(0.62f, 0.3f, 0.28f);
        pendant(b, new float[][]{p(6.4f, 9.2f, 4.6f), p(6.2f, 9.0f, 6.3f), p(6.3f, 7.0f, 6.6f), p(6.0f, 4.6f, 6.5f)}, 0.5f, TOILE);
        b.color(0.9f, 0.86f, 0.7f);
        drape(b, new float[][]{p(5.0f, 9.6f, 2.6f), p(4.1f, 9.1f, 2.8f), p(3.8f, 7.0f, 3.0f), p(3.9f, 5.2f, 3.1f)}, 2.0f, v(0, 0, 1), EPONGE);
        b.color(0.85f);
        pendant(b, new float[][]{p(2.0f, 0.32f, 8.0f), p(2.8f, 0.32f, 8.8f), p(3.6f, 0.32f, 8.6f), p(4.0f, 0.34f, 7.9f)}, 0.28f, TOILE);
        b.color(1);
        tasSol(b, 9.6f, 8.6f, 2.2f, 0.5f, 1.5f, 0.12f, 12, LINGE);
        // couvercle posé contre le flanc droit
        b.color(0.64f, 0.76f, 0.86f);
        b.push().translate(14.0f, 0.09f, 3.2f).rotateZ(18);
        b.bevelBox(-0.25f, 0, -2.8f, 0.25f, 7.6f, 2.8f, 0.15f, PLAST);
        b.pop();
        b.color(1);
        decal(b, MOISI, 'z', 8.0f, 0.8f, z1, 4.4f, 0.55f);
        boue(b, 'x', x0 - 0.15f, 0, 3.2f, 4.0f, 0.5f, 1);
    }

    // =================================================================== matelas
    /**
     * Matelas à ressorts (190 x 90 x 18 cm) appuyé debout contre le mur : coutil taché d'auréoles et de moisi,
     * éventré (mousse, ressorts qui sortent, lambeaux) ; sac de couchage roulé au pied.
     */
    static void matelas(MeshBuilder b) {
        float W = 14.4f, L = 30.4f, E = 2.9f, bv = 0.9f, ang = -8f;
        float dz = -(float) (L * Math.sin(Math.toRadians(ang))) + 0.08f;
        b.push().translate(0.8f, 0, dz).rotateX(ang);
        b.bevelBox(0, 0, 0, W, L, E, bv, MATELAS, null, MeshBuilder.SKIP_PZ);
        float hx = 9.0f, hy = 15.5f, hw = 3.6f, hh = 4.2f;
        trou(b, MATELAS, MOUSSE_JAUNE, hx, hy, E, hw, hh, 2.6f, 3.2f, 16, 1.6f, 7);
        b.face(MATELAS, v(0, 0, 1), p(bv, bv, E), p(W - bv, bv, E), p(W - bv, hy - hh, E), p(bv, hy - hh, E));
        b.face(MATELAS, v(0, 0, 1), p(bv, hy + hh, E), p(W - bv, hy + hh, E), p(W - bv, L - bv, E), p(bv, L - bv, E));
        b.face(MATELAS, v(0, 0, 1), p(bv, hy - hh, E), p(hx - hw, hy - hh, E), p(hx - hw, hy + hh, E), p(bv, hy + hh, E));
        b.face(MATELAS, v(0, 0, 1), p(hx + hw, hy - hh, E), p(W - bv, hy - hh, E), p(W - bv, hy + hh, E), p(hx + hw, hy + hh, E));
        // ressorts (hélices) dont un qui sort franchement
        float[][] rs = {{hx - 0.9f, hy + 0.9f, E + 0.15f}, {hx + 0.8f, hy - 0.5f, E + 1.5f}, {hx - 0.4f, hy - 1.7f, E - 0.1f}};
        for (float[] q : rs) {
            int n = 22;
            float[][] path = new float[n][];
            for (int i = 0; i < n; i++) {
                double a = i / (double) (n - 1) * Math.PI * 2 * 3.2;
                float zz = E - 1.6f + (q[2] - (E - 1.6f)) * i / (n - 1);
                path[i] = p(q[0] + 0.55f * (float) Math.cos(a), q[1] + 0.55f * (float) Math.sin(a), zz);
            }
            b.tube(path, fill(n, 0.06f), 4, METAL_NU, false, false, 0, false);
        }
        // lambeaux de coutil retroussés au bord de la déchirure
        for (float[] f : new float[][]{{30, 3.0f}, {150, 3.3f}, {255, 2.6f}}) {
            double a = Math.toRadians(f[0]);
            float px = hx + 2.6f * 0.95f * (float) Math.cos(a), py = hy + 3.2f * 0.95f * (float) Math.sin(a);
            b.push().translate(px, py, E + 0.02f).rotateZ(f[0] - 90).rotateX(55);
            b.box(-0.8f, 0, 0, 0.8f, f[1] * 0.5f, 0.06f, MATELAS);
            b.pop();
        }
        // passepoil du bord avant
        b.color(0.8f, 0.76f, 0.66f);
        b.tube(new float[][]{p(0.3f, 0.35f, E - 0.3f), p(W - 0.3f, 0.35f, E - 0.3f), p(W - 0.3f, L - 0.3f, E - 0.3f), p(0.3f, L - 0.3f, E - 0.3f), p(0.3f, 0.35f, E - 0.3f)},
                fill(5, 0.2f), 6, TOILE, false, false, 0, false);
        b.color(1);
        decal(b, AUREOLE, 'z', 4.2f, 24.0f, E, 5.2f, 5.8f, 20);
        decal(b, AUREOLE, 'z', 10.6f, 6.0f, E, 4.0f, 3.6f);
        decal(b, MOISI, 'z', 3.6f, 4.4f, E, 4.2f, 3.0f, -10);
        decal(b, MOISI, 'z', 11.4f, 26.8f, E, 3.0f, 2.2f, 30);
        b.pop();
        // sac de couchage roulé au pied, deux sangles
        b.color(0.7f, 0.42f, 0.26f);
        b.push().translate(6.2f, 1.7f, 10.6f).rotateY(8).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -3.6f}, {1.1f, -3.55f}, {1.5f, -3.2f}, {1.62f, -2.6f}, {1.62f, 2.6f}, {1.5f, 3.2f}, {1.1f, 3.55f}, {0.01f, 3.6f}},
                16, 0, 360, DUVET, 1, 1, 0.03f, 1.0f, 4, false);
        b.color(0.22f);
        for (float y : new float[]{-1.8f, 1.8f}) {
            b.push().translate(0, y, 0);
            b.torus(1.56f, 0.13f, 16, 4, 0, 360, TOILE);
            b.pop();
        }
        b.color(1);
        b.pop();
    }

    /**
     * Déchirure : plaque (plan z, vers +Z) percée d'un trou irrégulier, parois et fond en retrait de prof (mousse).
     * Le rectangle (demi-côtés hw, hh) est centré sur le trou.
     */
    static void trou(MeshBuilder b, Mat plaque, Mat paroi, float cx, float cy, float z, float hw, float hh, float rx, float ry, int segs, float prof, long graine) {
        Random r = new Random(graine);
        float[][] ring = new float[segs][], bord = new float[segs][];
        for (int i = 0; i < segs; i++) {
            double a = 2 * Math.PI * i / segs;
            float k = 0.78f + 0.4f * r.nextFloat(), c = (float) Math.cos(a), s = (float) Math.sin(a), m = Math.max(Math.abs(c), Math.abs(s));
            ring[i] = new float[]{cx + rx * k * c, cy + ry * k * s};
            bord[i] = new float[]{cx + hw * c / m, cy + hh * s / m};
        }
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % segs;
            b.face(plaque, v(0, 0, 1), p(ring[i][0], ring[i][1], z), p(ring[j][0], ring[j][1], z), p(bord[j][0], bord[j][1], z), p(bord[i][0], bord[i][1], z));
            float nx = cx - (ring[i][0] + ring[j][0]) / 2, ny = cy - (ring[i][1] + ring[j][1]) / 2, l = (float) Math.hypot(nx, ny);
            b.face(paroi, v(nx / l, ny / l, 0), p(ring[i][0], ring[i][1], z), p(ring[j][0], ring[j][1], z), p(ring[j][0], ring[j][1], z - prof), p(ring[i][0], ring[i][1], z - prof));
            b.face(paroi, v(0, 0, 1), p(cx, cy, z - prof), p(ring[i][0], ring[i][1], z - prof), p(ring[j][0], ring[j][1], z - prof));
        }
    }

    // =================================================================== cartons de déménagement
    /** Carton de déménagement : fermé (ruban brun) ou ouvert (parois, rabats), mot au feutre sur la face avant. */
    static void cartonT1(MeshBuilder b, float x0, float y0, float z0, float w, float h, float d, Mat matiere, Mat mot, float motW, boolean ferme) {
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d, zc = z0 + d / 2;
        if (ferme) {
            b.bevelBox(x0, y0, z0, x1, y1, z1, 0.12f, matiere);
            scotchY(b, x0 - 0.02f, zc, x1 + 0.02f, zc, y1, 0.9f, SCOTCH_BRUN);
            b.box(x0 - 0.08f, y1 - 1.1f, zc - 0.45f, x0 - 0.02f, y1 + 0.02f, zc + 0.45f, SCOTCH_BRUN);
            b.box(x1 + 0.02f, y1 - 1.1f, zc - 0.45f, x1 + 0.08f, y1 + 0.02f, zc + 0.45f, SCOTCH_BRUN);
        } else {
            float e = 0.12f;
            b.box(x0, y0, z0, x1, y1, z0 + e, matiere);
            b.box(x0, y0, z1 - e, x1, y1, z1, matiere);
            b.box(x0, y0, z0 + e, x0 + e, y1, z1 - e, matiere);
            b.box(x1 - e, y0, z0 + e, x1, y1, z1 - e, matiere);
            b.box(x0 + e, y0, z0 + e, x1 - e, y0 + 0.12f, z1 - e, matiere);
            // rabats : arrière dressé, avant penché dehors, côtés ouverts
            b.push().translate(0, y1, z0).rotateX(-80);
            b.box(x0 + 0.1f, 0, 0, x1 - 0.1f, 0.08f, d * 0.48f, matiere);
            b.pop();
            b.push().translate(0, y1, z1).rotateX(115);
            b.box(x0 + 0.1f, 0, -d * 0.48f, x1 - 0.1f, 0.08f, 0, matiere);
            b.pop();
            b.push().translate(x0, y1, 0).rotateZ(110);
            b.box(0, 0, z0 + 0.1f, w * 0.26f, 0.08f, z1 - 0.1f, matiere);
            b.pop();
            b.push().translate(x1, y1, 0).rotateZ(-110);
            b.box(-w * 0.26f, 0, z0 + 0.1f, 0, 0.08f, z1 - 0.1f, matiere);
            b.pop();
        }
        if (mot != null) decal(b, mot, 'z', (x0 + x1) / 2, y0 + h * 0.45f, z1, motW, motW / 4, ferme ? -3 : 4);
    }

    /** Cartons empilés « CHAMBRE », « LIVRES », « CUISINE » (ouvert, vaisselle dans le journal), « PAPIERS » tombé. */
    static void cartons(MeshBuilder b) {
        cartonT1(b, 0.8f, 0, 0.5f, 9.6f, 6.4f, 6.4f, CARTON_MOUILLE, T_CHAMBRE, 7.0f, true);
        cartonT1(b, 10.8f, 0, 0.6f, 4.6f, 4.8f, 4.3f, AtlasCivil.CARTON, T_LIVRES, 4.0f, true);
        b.push().translate(5.6f, 6.52f, 3.6f).rotateY(7).translate(-5.6f, -6.52f, -3.6f);
        cartonT1(b, 1.6f, 6.52f, 0.9f, 8.0f, 5.0f, 5.4f, AtlasCivil.CARTON, T_CUISINE, 6.0f, false);
        // vaisselle emballée dans le journal, une assiette debout
        float[][] boules = {{3.2f, 10.9f, 2.4f}, {5.0f, 11.2f, 3.0f}, {7.0f, 10.8f, 2.2f}, {8.0f, 11.0f, 4.4f}, {3.8f, 10.9f, 4.6f}};
        for (int k = 0; k < boules.length; k++) {
            b.push().translate(boules[k][0], boules[k][1], boules[k][2]);
            b.blob(1.1f, 0.95f, 1.0f, 0.9f, 0.2f, 2.2f, 20 + k, 10, JOURNAL);
            b.pop();
        }
        b.push().translate(5.8f, 11.0f, 4.5f).rotateX(80);
        b.cylinder(1.9f, -0.08f, 0.08f, 16, CERAMIQUE, CERAMIQUE, CERAMIQUE);
        b.pop();
        b.pop();
        // carton « PAPIERS » tombé devant, ruban décollé
        b.push().translate(12.0f, 0, 9.4f).rotateY(-25);
        cartonT1(b, -2.4f, 0, -2.0f, 4.8f, 3.6f, 4.0f, CARTON_MOUILLE, T_PAPIERS, 3.8f, true);
        drape(b, new float[][]{p(-2.48f, 3.0f, 0.4f), p(-2.85f, 2.0f, 0.45f), p(-3.05f, 0.9f, 0.5f), p(-3.2f, 0.5f, 0.55f)}, 0.9f, v(0, 0, 1), SCOTCH_BRUN);
        b.pop();
        papiers(b, 2.0f, 8.0f, 10.0f, 13.0f, 6, 51);
        decal(b, AUREOLE, 'z', 3.0f, 1.6f, 6.9f, 3.0f, 2.4f);
        boue(b, 'z', 5.6f, 0, 6.9f, 9.0f, 1.2f, 1);
        decal(b, ETIQUETTE_DECHIREE, 'X', 15.4f, 2.6f, 2.8f, 2.4f, 1.1f);
    }

    // =================================================================== coffre à jouets
    /** Coffre à jouets en bois peint (70 x 40 x 45 cm), couvercle relevé, nounours borgne et ballon dégonflé devant. */
    static void coffreJouets(MeshBuilder b) {
        float x0 = 2.4f, x1 = 13.6f, z0 = 0.6f, z1 = 7.0f, y0 = 0.5f, h = 7.2f, t = 0.45f;
        for (float[] q : new float[][]{{x0 + 0.2f, z0 + 0.2f}, {x1 - 1.0f, z0 + 0.2f}, {x0 + 0.2f, z1 - 1.0f}, {x1 - 1.0f, z1 - 1.0f}}) {
            b.bevelBox(q[0], 0, q[1], q[0] + 0.8f, y0 + 0.02f, q[1] + 0.8f, 0.08f, BOIS_VERNI);
        }
        b.color(0.6f, 0.73f, 0.84f);
        b.bevelBox(x0, y0, z1 - t, x1, h, z1, 0.1f, PEINT);
        b.bevelBox(x0, y0, z0, x1, h, z0 + t, 0.1f, PEINT);
        b.bevelBox(x0, y0, z0 + t - 0.02f, x0 + t, h, z1 - t + 0.02f, 0.1f, PEINT);
        b.bevelBox(x1 - t, y0, z0 + t - 0.02f, x1, h, z1 - t + 0.02f, 0.1f, PEINT);
        b.box(x0 + t, y0, z0 + t, x1 - t, y0 + 0.4f, z1 - t, PEINT);
        // couvercle relevé presque à la verticale (charnières à l'arrière)
        b.push().translate(0, h, z0).rotateX(-85).translate(0, -h, -z0);
        b.bevelBox(x0 - 0.1f, h + 0.02f, z0, x1 + 0.1f, h + 0.52f, z1 + 0.25f, 0.15f, PEINT);
        b.pop();
        b.color(1);
        decal(b, AUTOCOLLANTS, 'z', 8.0f, 4.0f, z1, 6.4f, 3.2f, 3);
        decal(b, GRIFFURES, 'X', x1, 3.6f, 3.8f, 2.4f, 1.0f, -10);
        decal(b, MOISI, 'x', x0, 1.6f, 3.8f, 4.0f, 1.8f);
        boue(b, 'z', 8.0f, y0, z1, 10.0f, 1.0f, 0);
        // jouets dedans : peluches et vêtements de poupée, cubes, camion
        tas(b, 8.0f, h - 1.6f, 3.8f, 4.6f, 1.4f, 2.6f, 11, LINGE);
        int[] cols = {0xB84A3A, 0x3A6AA8, 0xD8B040, 0x4A8A4A};
        float[][] cubes = {{4.6f, h - 0.6f, 3.0f, 20}, {6.0f, h - 0.4f, 4.6f, -15}, {10.6f, h - 0.5f, 2.8f, 40}};
        for (int k = 0; k < cubes.length; k++) {
            teinte(b, cols[k]);
            b.push().translate(cubes[k][0], cubes[k][1], cubes[k][2]).rotateY(cubes[k][3]).rotateX(12);
            b.bevelBox(-0.6f, -0.6f, -0.6f, 0.6f, 0.6f, 0.6f, 0.12f, PLAST);
            b.pop();
        }
        teinte(b, 0xC8402E);
        b.push().translate(9.0f, h - 0.1f, 4.6f).rotateY(-25).rotateZ(-10);
        b.bevelBox(-1.6f, 0, -0.7f, 0.2f, 1.0f, 0.7f, 0.15f, PLAST);
        teinte(b, 0x3A6AA8);
        b.bevelBox(0.2f, 0, -0.7f, 1.4f, 1.5f, 0.7f, 0.15f, PLAST);
        b.color(0.2f);
        for (float x : new float[]{-1.1f, 0.9f}) {
            for (float z : new float[]{-0.75f, 0.75f}) {
                b.push().translate(x, 0, z).rotateX(90);
                b.cylinder(0.4f, -0.15f, 0.15f, 10, CAOUTCHOUC_USE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
                b.pop();
            }
        }
        b.pop();
        b.color(1);
        // devant : nounours assis, ballon dégonflé, deux cubes
        nounours(b, 3.6f, 10.0f, 25);
        b.color(0.78f, 0.28f, 0.24f);
        tasSol(b, 12.2f, 10.4f, 2.0f, 0.7f, 2.0f, 0.12f, 4, PLAST);
        b.color(1);
        for (int k = 0; k < 2; k++) {
            teinte(b, cols[k + 2]);
            b.push().translate(8.0f + k * 1.8f, 0.6f, 9.2f + k * 1.4f).rotateY(25 + k * 40);
            b.bevelBox(-0.6f, -0.6f, -0.6f, 0.6f, 0.6f, 0.6f, 0.12f, PLAST);
            b.pop();
        }
        b.color(1);
    }

    /** Ours en peluche assis (40 cm), un œil arraché, couture qui laisse sortir la mousse. */
    static void nounours(MeshBuilder b, float x, float z, float ang) {
        b.push().translate(x, 0, z).rotateY(ang);
        b.color(0.62f, 0.46f, 0.32f);
        b.push().translate(0, 2.0f, 0).scale(1.5f, 1.9f, 1.25f);
        b.sphere(1.0f, 12, 8, PELUCHE);
        b.pop();
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{p(s * 0.85f, 0.64f, 0.3f), p(s * 0.95f, 0.62f, 1.4f), p(s * 1.05f, 0.62f, 2.2f)}, fill(3, 0.6f), 8, PELUCHE, true, true, 0, false);
            b.tube(new float[][]{p(s * 1.25f, 2.9f, 0.1f), p(s * 1.7f, 2.3f, 0.6f), p(s * 1.9f, 1.6f, 1.0f)}, new float[]{0.48f, 0.45f, 0.42f}, 8, PELUCHE, true, true, 0, false);
        }
        b.push().translate(0, 4.5f, 0.1f);
        b.sphere(1.3f, 12, 8, PELUCHE);
        b.pop();
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 0.95f, 5.55f, 0).scale(1, 1, 0.5f);
            b.sphere(0.5f, 8, 5, PELUCHE);
            b.pop();
        }
        b.color(0.86f, 0.76f, 0.6f);
        b.push().translate(0, 4.2f, 1.1f).scale(0.6f, 0.45f, 0.45f);
        b.sphere(1.0f, 10, 6, PELUCHE);
        b.pop();
        b.color(0.12f);
        b.push().translate(-0.45f, 4.75f, 1.18f);
        b.sphere(0.16f, 8, 5, PLAST);
        b.pop();
        b.push().translate(0, 4.35f, 1.55f);
        b.sphere(0.18f, 8, 5, PLAST);
        b.pop();
        b.color(1);
        b.push().translate(1.1f, 2.6f, 0.8f);
        b.blob(0.45f, 0.35f, 0.4f, 1, 0.2f, 3f, 2, 8, MOUSSE_JAUNE);
        b.pop();
        b.pop();
    }

    // =================================================================== grand cabas
    /** Grand cabas en plastique tissé bleu (55 x 37 x 35 cm), plein de vêtements, appuyé contre le mur. */
    static void grandCabas(MeshBuilder b) {
        b.push().translate(8.0f, 0, 3.9f).rotateY(-6).rotateX(-7);
        b.push().translate(0, 3.2f, 0);
        b.blob(4.4f, 3.0f, 2.7f, 0.45f, 0.04f, 1.2f, 5, 18, CABAS);
        b.pop();
        tas(b, 0.2f, 6.0f, 0.1f, 3.9f, 1.6f, 2.4f, 7, LINGE);
        b.color(0.35f, 0.4f, 0.55f);
        pendant(b, new float[][]{p(1.5f, 6.4f, 1.6f), p(1.8f, 6.3f, 2.95f), p(2.0f, 4.8f, 3.15f), p(2.1f, 3.2f, 3.05f)}, 0.5f, TOILE);
        b.color(1);
        // anses : sangle avant dressée (aplatie), sangle arrière rabattue sur le linge
        b.push().translate(0, 0, 2.78f).scale(1, 1, 0.35f);
        b.tube(new float[][]{p(-2.4f, 4.8f, 0), p(-2.3f, 7.6f, 0), p(-1.2f, 9.0f, 0), p(1.2f, 9.0f, 0), p(2.3f, 7.6f, 0), p(2.4f, 4.8f, 0)}, fill(6, 0.34f), 6, CABAS, true, true, 0, false);
        b.pop();
        b.tube(new float[][]{p(-2.4f, 5.4f, -2.6f), p(-2.0f, 7.4f, -1.5f), p(0, 7.95f, 0.4f), p(2.0f, 7.4f, -1.5f), p(2.4f, 5.4f, -2.6f)}, fill(5, 0.32f), 6, CABAS, true, true, 0, false);
        decal(b, ETIQUETTE_DECHIREE, 'z', -1.8f, 2.4f, 2.82f, 2.2f, 1.0f, -6);
        b.pop();
    }

    // =================================================================== meuble à chaussures
    /** Banc à chaussures en bois (80 x 30 x 48 cm) à lattes, une latte cassée ; chaussures dépareillées, une botte tombée. */
    static void rangeChaussures(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.6f, z1 = 5.4f, h = 7.6f, e = 0.45f;
        b.bevelBox(x0, 0, z0, x0 + e, h - 0.45f, z1, 0.1f, BOIS_VERNI);
        b.bevelBox(x1 - e, 0, z0, x1, h - 0.45f, z1, 0.1f, BOIS_VERNI);
        b.bevelBox(x0 - 0.15f, h - 0.47f, z0, x1 + 0.15f, h, z1 + 0.15f, 0.12f, BOIS_VERNI);
        for (float y : new float[]{1.0f, 4.2f}) {
            for (int k = 0; k < 4; k++) {
                if (y > 4 && k == 2) continue;
                float zz = z0 + 0.3f + k * 1.15f;
                b.bevelBox(x0 + e, y, zz, x1 - e, y + 0.3f, zz + 0.8f, 0.08f, BOIS_VERNI);
            }
        }
        // latte cassée tombée au sol
        b.push().translate(9.0f, 0.15f, 8.6f).rotateY(-14);
        b.bevelBox(-5.0f, -0.15f, -0.4f, 3.6f, 0.15f, 0.4f, 0.08f, BOIS_VERNI);
        b.pop();
        float zc = (z0 + z1) / 2;
        chaussure(b, 3.6f, 1.3f, zc, -90, 0, 0xD8D4C8);
        chaussure(b, 6.4f, 1.3f, zc + 0.2f, -84, 3, 0x7A5A8A);
        chaussure(b, 11.6f, 1.3f, zc, -96, 1, 0x3A2A20);
        chaussure(b, 3.8f, 4.53f, zc + 0.3f, -94, 1, 0x5A3A26);
        chaussure(b, 11.2f, 4.53f, zc + 0.3f, -85, 0, 0x3A5A8A);
        // botte couchée devant, basket renversée
        b.push().translate(4.6f, 0.82f, 9.8f).rotateY(25).rotateX(90);
        chaussure(b, 0, 0, 0, 0, 2, 0x4A3A2A);
        b.pop();
        chaussure(b, 12.4f, 0, 9.4f, 35, 0, 0xB84A3A);
        boue(b, 'x', x0, 0, 3.0f, 4.4f, 0.8f, 1);
        boue(b, 'X', x1, 0, 3.0f, 4.4f, 0.9f, 0);
        decal(b, GRIFFURES, 'y', 9.0f, h, 3.0f, 3.0f, 1.2f, 15);
        decal(b, AUREOLE, 'y', 4.0f, h, 3.4f, 2.2f, 2.0f);
    }
}
