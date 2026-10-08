package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** T1b — cuisine : micro-ondes, cuisinière, lave-linge, meuble bas, poubelle, mini-frigo, tiroir, chariot. */
final class LotT1bCuisine {
    private LotT1bCuisine() {
    }

    static Mat[] faces(Mat nx, Mat px, Mat ny, Mat py, Mat nz, Mat pz) {
        return new Mat[]{nx, px, ny, py, nz, pz};
    }

    /** Teinte amande de l'électroménager des années 80. */
    static void amande(MeshBuilder b) {
        b.color(0.96f, 0.93f, 0.85f);
    }

    // =================================================================== micro-ondes
    /**
     * Micro-ondes (48 x 36 x 28 cm) posé sur un carton « CUISINE » affaissé : porte arrachée d'une charnière, ouverte de
     * travers ; enceinte crasseuse, bol moisi sur le plateau ; un bouton manquant ; fil qui pend.
     */
    static void microOndes(MeshBuilder b) {
        // carton (50 x 40 x 38 cm), ruban brun, mot au feutre
        float cx0 = 3.6f, cx1 = 11.8f, cz0 = 0.5f, cz1 = 6.9f, ch = 6.0f;
        b.bevelBox(cx0, 0, cz0, cx1, ch, cz1, 0.18f, CARTON_MOUILLE);
        scotchY(b, (cx0 + cx1) / 2, cz0 - 0.01f, (cx0 + cx1) / 2, cz1 + 0.01f, ch, 0.9f, SCOTCH_BRUN);
        b.box((cx0 + cx1) / 2 - 0.45f, ch - 1.0f, cz1 + 0.02f, (cx0 + cx1) / 2 + 0.45f, ch + 0.02f, cz1 + 0.08f, SCOTCH_BRUN);
        decal(b, T_CUISINE, 'z', 7.4f, 2.8f, cz1, 6.4f, 1.6f, 4);
        decal(b, AUREOLE, 'X', cx1, 1.6f, 3.6f, 4.0f, 3.0f);
        boue(b, 'z', 7.7f, 0, cz1, 8.0f, 1.4f, 1);
        // corps (tôle amande) : dessus, fond, paroi gauche, colonne de commande, dos ; faces intérieures crasseuses
        float x0 = 3.9f, x1 = 11.5f, z0 = 0.8f, z1 = 6.5f, y0 = 6.25f, y1 = 10.6f, xc = 9.4f, e = 0.4f;
        for (float[] q : new float[][]{{4.6f, 1.6f}, {10.8f, 1.6f}, {4.6f, 5.8f}, {10.8f, 5.8f}}) {
            b.push().translate(q[0], ch - 0.02f, q[1]);
            b.cylinder(0.3f, 0, 0.29f, 8, CAOUTCHOUC_USE, null, null);
            b.pop();
        }
        amande(b);
        b.bevelBox(x0, y1 - e, z0, x1, y1, z1, 0.15f, ELECTRO, faces(null, null, INTERIEUR_SALE, null, null, null), 0);
        b.bevelBox(x0, y0, z0, x1, y0 + e, z1, 0.15f, ELECTRO, faces(null, null, null, INTERIEUR_SALE, null, null), 0);
        b.bevelBox(x0, y0 + e - 0.02f, z0, x0 + e, y1 - e + 0.02f, z1, 0.08f, ELECTRO, faces(null, INTERIEUR_SALE, null, null, null, null), 0);
        b.bevelBox(xc, y0 + e - 0.02f, z0, x1, y1 - e + 0.02f, z1, 0.08f, ELECTRO, faces(INTERIEUR_SALE, null, null, null, null, null), 0);
        b.box(x0 + e, y0 + e, z0, xc, y1 - e, z0 + 0.5f, INTERIEUR_SALE);
        b.color(1);
        decal(b, MOISI, 'z', 6.0f, 7.4f, z0 + 0.5f, 2.6f, 1.6f);
        // panneau de commande, un bouton (l'autre arraché : axe nu)
        decal(b, PANNEAU_MO, 'z', (xc + x1) / 2, 8.42f, z1, 1.95f, 3.9f);
        b.push().translate((xc + x1) / 2, 9.25f, z1 + 0.05f).rotateX(90);
        b.cylinder(0.5f, 0, 0.42f, 12, AtlasCivil.DARK, null, AtlasCivil.DARK);
        b.pop();
        b.bevelBox((xc + x1) / 2 - 0.08f, 9.05f, z1 + 0.42f, (xc + x1) / 2 + 0.08f, 9.65f, z1 + 0.5f, 0.02f, AtlasCivil.WHITE);
        b.push().translate((xc + x1) / 2, 7.95f, z1).rotateX(90);
        b.cylinder(0.12f, 0, 0.32f, 6, METAL_NU, null, METAL_NU);
        b.pop();
        decal(b, PERSIENNES, 'X', x1, 9.4f, 3.6f, 3.4f, 1.6f);
        decal(b, ETIQUETTE_DECHIREE, 'y', 6.0f, y1, 2.4f, 3.0f, 1.4f, 10);
        decal(b, AUREOLE, 'y', 9.6f, y1, 4.6f, 2.2f, 2.0f);
        coulure(b, 'X', x1, y1 - 0.2f, 1.8f, 1.0f, 1);
        // plateau tournant (verre, passe translucide) et bol moisi
        b.color(0.84f, 0.8f, 0.7f);
        b.push().translate(6.7f, y0 + e + 0.12f, 3.7f);
        b.lathe(new float[][]{{0.01f, 0}, {0.7f, 0.02f}, {1.05f, 0.9f}, {1.1f, 1.0f}}, 12, 0, 360, CERAMIQUE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.02f, 0.98f}, {0.65f, 0.15f}}, 12, 0, 360, CERAMIQUE, 1, 1, 0, 0, 0, true);
        b.color(1);
        b.disc(0.82f, 0.5f, 10, MOISI, true);
        b.pop();
        // porte : arrachée de sa charnière du haut, ouverte de travers et affaissée
        b.push().translate(x0, y0 + 0.4f, z1).rotateY(-104).rotateZ(-7).translate(-x0, -(y0 + 0.4f), -z1);
        porteMo(b, x0, xc, y0 + 0.12f, y1 - 0.12f, z1);
        b.pop();
        // fil secteur qui pend derrière, prise au sol
        b.tube(new float[][]{p(11.2f, 7.0f, 1.0f), p(12.1f, 6.4f, 1.2f), p(12.4f, 3.0f, 1.6f), p(12.6f, 0.2f, 2.6f), p(13.6f, 0.2f, 4.2f)}, fill(5, 0.11f), 5, AtlasCivil.DARK, false, false, 0, false);
        b.push().translate(14.0f, 0.42f, 4.8f).rotateY(-50);
        b.bevelBox(-0.4f, -0.42f, -0.5f, 0.4f, 0.42f, 0.5f, 0.12f, AtlasCivil.DARK);
        b.pop();
    }

    /** Porte du micro-ondes fermée (repère du four : charnière en x0, face avant en z1). */
    static void porteMo(MeshBuilder b, float x0, float x1, float y0, float y1, float z1) {
        float t = 0.42f;
        b.color(0.3f, 0.3f, 0.31f);
        b.bevelBox(x0 + 0.04f, y0, z1 + 0.02f, x1 - 0.04f, y1, z1 + t, 0.12f, ELECTRO);
        b.color(1);
        b.push().translate(0, 0, 0);
        b.decalZ(GRILLE_MO, (x0 + x1) / 2 - 0.3f, (y0 + y1) / 2, z1 + t + 0.05f, (x1 - x0) - 1.6f, (y1 - y0) - 1.0f);
        b.pop();
        b.push().translate((x0 + x1) / 2 - 0.3f, (y0 + y1) / 2, z1 - 0.03f).rotateY(180);
        b.decalZ(GRILLE_MO, 0, 0, 0, (x1 - x0) - 1.6f, (y1 - y0) - 1.0f);
        b.pop();
        b.bevelBox(x1 - 0.9f, y0 + 0.6f, z1 + t - 0.02f, x1 - 0.4f, y1 - 0.6f, z1 + t + 0.5f, 0.12f, AtlasCivil.PLASTIC_GREY);
    }

    // =================================================================== cuisinière
    /**
     * Cuisinière mixte émaillée (60 x 60 x 85 cm) : porte du four pendante (charnière de droite cassée), grille et
     * lèchefrite brûlées, deux boutons manquants, casserole croûtée sur un brûleur, rouille et graisse.
     */
    static void cuisiniere(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.4f, z1 = 10.0f, h = 13.6f, s = 0.42f;
        float yf0 = 3.2f, yf1 = 10.4f;
        // socle en retrait, tiroir à casseroles fermé
        b.box(x0 + 0.5f, 0, z0 + 0.4f, x1 - 0.5f, 0.8f, z1 - 0.6f, AtlasCivil.DARK);
        amande(b);
        b.bevelBox(x0, 0.8f, z0, x0 + s, h - 0.6f, z1, 0.1f, ELECTRO);
        b.bevelBox(x1 - s, 0.8f, z0, x1, h - 0.6f, z1, 0.1f, ELECTRO);
        b.box(x0 + s, 0.8f, z0, x1 - s, h - 0.6f, z0 + 0.3f, ELECTRO);
        b.bevelBox(x0 + s + 0.05f, 0.85f, z1 - 0.5f, x1 - s - 0.05f, yf0 - 0.15f, z1 + 0.05f, 0.12f, ELECTRO);
        // four : fond, plafond, sole, parois (émail noir brûlé)
        b.color(1);
        b.box(x0 + s, yf0 - 0.15f, z0 + 0.3f, x1 - s, yf0, z1 - 0.5f, FONTE);
        b.box(x0 + s, yf0, z0 + 0.3f, x1 - s, yf1, z0 + 0.6f, FONTE);
        b.box(x0 + s, yf1, z0 + 0.3f, x1 - s, yf1 + 0.15f, z1, FONTE);
        b.color(0.75f);
        b.face(FONTE, MeshBuilder.v(1, 0, 0), p(x0 + s + 0.06f, yf0, z0 + 0.6f), p(x0 + s + 0.06f, yf0, z1), p(x0 + s + 0.06f, yf1, z1), p(x0 + s + 0.06f, yf1, z0 + 0.6f));
        b.face(FONTE, MeshBuilder.v(-1, 0, 0), p(x1 - s - 0.06f, yf0, z1), p(x1 - s - 0.06f, yf0, z0 + 0.6f), p(x1 - s - 0.06f, yf1, z0 + 0.6f), p(x1 - s - 0.06f, yf1, z1));
        b.color(1);
        b.box(x0 + s, yf0 - 0.15f, z1 - 0.5f, x1 - s, yf0, z1, FONTE);
        // gradins, grille et lèchefrite croûtée
        for (float y : new float[]{5.4f, 7.6f}) {
            for (float x : new float[]{x0 + s + 0.06f, x1 - s - 0.36f}) b.box(x, y, z0 + 0.7f, x + 0.3f, y + 0.18f, z1 - 0.3f, METAL_NU);
        }
        b.box(x0 + s + 0.36f, 5.58f, z0 + 0.8f, x1 - s - 0.36f, 5.72f, z0 + 1.0f, METAL_NU);
        b.box(x0 + s + 0.36f, 5.58f, z1 - 0.9f, x1 - s - 0.36f, 5.72f, z1 - 0.7f, METAL_NU);
        for (float x = x0 + 1.4f; x < x1 - 1.0f; x += 0.9f) b.box(x, 5.6f, z0 + 1.0f, x + 0.12f, 5.7f, z1 - 0.9f, METAL_NU);
        b.color(0.5f, 0.42f, 0.36f);
        b.bevelBox(x0 + s + 0.4f, 7.78f, z0 + 1.0f, x1 - s - 0.4f, 8.4f, z1 - 1.4f, 0.12f, FONTE);
        b.color(1);
        b.face(BRULURE.asFit(), MeshBuilder.v(0, 1, 0), p(x0 + 1.4f, 8.46f, z1 - 1.6f), p(x1 - 1.4f, 8.46f, z1 - 1.6f), p(x1 - 1.4f, 8.46f, z0 + 1.2f), p(x0 + 1.4f, 8.46f, z0 + 1.2f));
        // bandeau de commande, boutons (deux manquants)
        amande(b);
        b.bevelBox(x0 + s, yf1 + 0.15f, z1 - 0.6f, x1 - s, h - 0.6f, z1, 0.1f, ELECTRO);
        b.color(1);
        decal(b, BANDEAU_FOUR, 'z', 8.0f, (yf1 + h - 0.45f) / 2, z1, x1 - x0 - 1.2f, h - 0.75f - yf1);
        float[] xs = {0.1f, 0.28f, 0.46f, 0.64f, 0.86f};
        float bw = x1 - x0 - 1.2f, yb = yf1 + 0.15f + (h - 0.75f - yf1) * 0.44f;
        for (int k = 0; k < xs.length; k++) {
            float x = 8.0f - bw / 2 + bw * xs[k];
            b.push().translate(x, yb, z1 + 0.05f).rotateX(90);
            if (k == 1 || k == 3) {
                b.cylinder(0.1f, 0, 0.36f, 6, METAL_NU, null, METAL_NU);
            } else {
                b.cylinder(0.5f, 0, 0.3f, 12, AtlasCivil.DARK, null, null);
                b.cylinder(0.42f, 0.3f, 0.62f, 12, AtlasCivil.DARK, null, AtlasCivil.DARK);
                b.box(-0.08f, 0.6f, -0.38f, 0.08f, 0.66f, 0.1f, AtlasCivil.WHITE);
            }
            b.pop();
        }
        // plan de cuisson : tôle, quatre brûleurs, grilles en fonte, dosseret
        amande(b);
        b.bevelBox(x0 - 0.05f, h - 0.62f, z0, x1 + 0.05f, h, z1 + 0.1f, 0.12f, ELECTRO);
        b.bevelBox(x0, h - 0.02f, z0, x1, h + 1.6f, z0 + 0.55f, 0.15f, ELECTRO);
        b.color(1);
        decal(b, BRULURE, 'y', 10.2f, h, 7.4f, 3.6f, 3.0f, 30);
        decal(b, AUREOLE, 'y', 6.0f, h, 4.8f, 2.4f, 2.2f);
        float[][] br = {{5.6f, 3.4f}, {10.4f, 3.4f}, {5.6f, 7.6f}, {10.4f, 7.6f}};
        for (int k = 0; k < 4; k++) {
            float bx = br[k][0], bz = br[k][1];
            b.push().translate(bx, h, bz);
            b.cylinder(0.95f, -0.02f, 0.2f, 14, METAL_NU, null, METAL_NU);
            b.cylinder(0.62f, 0.2f, 0.42f, 12, FONTE, null, FONTE);
            for (int a = 0; a < 4; a++) {
                b.push().rotateY(45 + a * 90);
                b.box(0.75f, 0.02f, -0.12f, 2.0f, 0.62f, 0.12f, FONTE);
                b.pop();
            }
            b.translate(0, 0.32f, 0);
            b.torus(1.9f, 0.12f, 16, 4, 0, 360, FONTE);
            b.pop();
        }
        // casserole émaillée sur le brûleur avant gauche, croûte brûlée
        b.push().translate(5.6f, h + 0.62f, 7.6f);
        b.color(0.72f, 0.3f, 0.24f);
        b.lathe(new float[][]{{0.01f, 0}, {1.55f, 0.02f}, {1.7f, 0.25f}, {1.72f, 2.0f}, {1.82f, 2.1f}}, 16, 0, 360, PEINT, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.74f, 2.05f}, {1.6f, 0.3f}}, 16, 0, 360, PEINT, 1, 1, 0, 0, 0, true);
        b.color(1);
        b.disc(1.6f, 0.9f, 16, MOISI, true);
        b.pop();
        b.tube(new float[][]{p(3.9f, h + 2.2f, 7.9f), p(1.6f, h + 2.4f, 8.6f), p(0.5f, h + 2.45f, 8.9f)}, new float[]{0.24f, 0.22f, 0.2f}, 8, AtlasCivil.DARK, true, true, 0, false);
        // porte du four : ouverte vers le bas à 50°, tordue (charnière droite cassée)
        float pz = z1 + 0.35f;
        b.push().translate(4.0f, yf0, pz).rotateZ(-4).rotateX(50).translate(-4.0f, -yf0, -pz);
        amande(b);
        b.bevelBox(x0 + s, yf0, z1 + 0.02f, x1 - s, yf0 + 7.0f, z1 + 0.68f, 0.2f, ELECTRO);
        b.color(1);
        b.decalZ(VITRE_FOUR, 8.0f, yf0 + 3.6f, z1 + 0.73f, 6.2f, 4.0f);
        b.push().translate(8.0f, yf0 + 3.6f, z1 - 0.03f).rotateY(180);
        b.decalZ(VITRE_FOUR, 0, 0, 0, 6.2f, 4.0f);
        b.pop();
        poigneeBarre(b, x0 + 1.4f, x1 - 1.4f, yf0 + 6.3f, z1 + 0.68f, AtlasCivil.CHROME);
        b.pop();
        // usure : rouille sous le plan, boue, éclats sur la tranche
        coulure(b, 'X', x1, h - 0.7f, 3.0f, 1.6f, 0);
        coulure(b, 'X', x1, h - 0.7f, 7.8f, 1.2f, 2);
        coulure(b, 'x', x0, h - 0.7f, 5.4f, 1.4f, 1);
        coulure(b, 'z', x1 - 1.2f, yf0 - 0.5f, z1 + 0.05f, 0.9f, 1);
        boue(b, 'X', x1, 0.8f, 5.2f, 9.0f, 1.8f, 0);
        boue(b, 'x', x0, 0.8f, 5.2f, 9.0f, 1.6f, 1);
        decal(b, GRIFFURES, 'x', x0, 8.0f, 6.0f, 3.0f, 1.4f, 15);
    }

    // =================================================================== lave-linge
    /**
     * Lave-linge à hublot (60 x 55 x 85 cm) : hublot arraché d'une charnière, grand ouvert, linge qui pend jusqu'au sol,
     * bac à lessive sorti, tuyau de vidange arraché, étiquette énergie jaunie, joint moisi.
     */
    static void laveLinge(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.4f, z1 = 9.2f, y0 = 0.6f, h = 13.6f, bv = 0.4f;
        float cx = 8.0f, cy = 6.6f, rt = 2.5f;
        for (float[] q : new float[][]{{4.0f, 1.2f}, {12.0f, 1.2f}, {4.0f, 8.4f}, {12.0f, 8.4f}}) {
            b.push().translate(q[0], 0, q[1]);
            b.cylinder(0.32f, 0, y0 + 0.02f, 8, CAOUTCHOUC_USE, null, null);
            b.pop();
        }
        // caisse (face avant laissée ouverte : plaque percée), plan de travail
        b.bevelBox(x0, y0, z0, x1, h - 0.55f, z1, bv, ELECTRO, null, MeshBuilder.SKIP_PZ);
        plaqueTrouee(b, ELECTRO, cx, cy, z1, 4.4f, 4.4f, rt + 0.6f, 24);
        b.face(ELECTRO, MeshBuilder.v(0, 0, 1), p(x0 + bv, y0 + bv, z1), p(x1 - bv, y0 + bv, z1), p(x1 - bv, cy - 4.4f, z1), p(x0 + bv, cy - 4.4f, z1));
        b.face(BANDEAU_LAVE, MeshBuilder.v(0, 0, 1), p(x0 + bv, cy + 4.4f, z1), p(x1 - bv, cy + 4.4f, z1), p(x1 - bv, h - 0.55f - bv, z1), p(x0 + bv, h - 0.55f - bv, z1));
        b.color(0.9f, 0.88f, 0.82f);
        b.bevelBox(x0 - 0.1f, h - 0.57f, z0, x1 + 0.1f, h, z1 + 0.15f, 0.2f, PLAST);
        b.color(1);
        // joint (caoutchouc moisi), tambour inox, aubes, linge
        b.color(0.7f);
        b.push().translate(cx, cy, z1 - 0.02f).rotateX(90);
        b.torus(rt + 0.3f, 0.32f, 24, 6, 0, 360, CAOUTCHOUC_USE);
        b.pop();
        fut(b, CAOUTCHOUC_USE, cx, cy, z1 - 1.6f, z1, rt + 0.6f, 24);
        b.color(1);
        fut(b, INOX, cx, cy, z1 - 7.0f, z1 - 1.6f, rt + 0.6f, 24);
        disqueZ(b, INOX, cx, cy, z1 - 7.0f, rt + 0.65f, 24);
        for (int k = 0; k < 3; k++) {
            b.push().translate(cx, cy, 0).rotateZ(30 + k * 120);
            b.box(-0.3f, rt + 0.0f, z1 - 6.9f, 0.3f, rt + 0.5f, z1 - 1.7f, INOX);
            b.pop();
        }
        decal(b, MOISI, 'z', cx, cy - 3.45f, z1, 3.0f, 0.7f);
        tas(b, cx + 0.2f, cy - 1.9f, z1 - 4.0f, 2.2f, 1.1f, 2.6f, 3, LINGE);
        b.color(0.42f, 0.52f, 0.68f);
        // linge qui pend par-dessus le joint jusqu'au sol (jambe de pantalon, serviette)
        pendant(b, new float[][]{p(cx - 0.8f, cy - 1.6f, z1 - 3.0f), p(cx - 0.9f, cy - 1.8f, z1 - 0.2f), p(cx - 1.0f, cy - 2.3f, z1 + 0.9f),
                p(cx - 1.2f, cy - 3.6f, z1 + 1.4f), p(cx - 1.3f, 2.0f, z1 + 1.6f), p(cx - 1.4f, 0.56f, z1 + 2.2f), p(cx - 2.6f, 0.56f, z1 + 3.4f)}, 0.55f, TOILE);
        b.color(1);
        b.color(0.55f, 0.62f, 0.72f);
        drape(b, new float[][]{p(cx + 0.6f, cy - 1.8f, z1 - 2.0f), p(cx + 0.7f, cy - 1.9f, z1 - 0.1f), p(cx + 0.9f, cy - 2.6f, z1 + 0.7f), p(cx + 1.1f, cy - 4.2f, z1 + 1.2f),
                p(cx + 1.3f, 0.1f, z1 + 1.6f), p(cx + 1.7f, 0.1f, z1 + 3.0f)}, 1.4f, MeshBuilder.v(1, 0, 0), EPONGE);
        b.color(1);
        // bac à lessive sorti, bouton programmateur
        b.color(0.9f, 0.88f, 0.82f);
        b.bevelBox(x0 + bv + 0.15f, cy + 4.65f, z1 - 2.0f, x0 + bv + 2.4f, cy + 5.75f, z1 + 1.4f, 0.1f, PLAST, faces(null, null, null, AtlasUsure.TERRE, null, null), 0);
        b.color(1);
        b.push().translate(x0 + bv + (x1 - x0 - 2 * bv) * 0.695f, cy + 5.2f, z1).rotateX(90);
        b.cylinder(0.85f, 0, 0.4f, 16, AtlasCivil.PLASTIC_GREY, null, AtlasCivil.PLASTIC_GREY);
        b.pop();
        decal(b, ENERGIE, 'z', 11.6f, 9.6f, z1, 1.2f, 2.5f, 3);
        // hublot : anneau, verre (passe translucide) ; charnière gauche, ouvert à 120°, pend de travers
        b.push().translate(cx - 3.6f, cy, z1 + 0.3f).rotateY(-120).rotateZ(-5).translate(-(cx - 3.6f), -cy, -(z1 + 0.3f));
        hublot(b, cx, cy, z1 + 0.3f);
        b.pop();
        // tuyau de vidange arraché (pend à droite, bout déchiqueté au sol)
        b.color(0.62f, 0.62f, 0.6f);
        b.tube(new float[][]{p(x1 - 0.6f, h - 1.6f, z0 + 0.8f), p(x1 + 0.6f, h - 1.2f, z0 + 1.4f), p(x1 + 1.2f, h - 3.0f, z0 + 2.2f), p(x1 + 1.3f, 5.0f, 3.4f),
                p(x1 + 1.5f, 0.4f, 5.4f), p(x1 + 1.0f, 0.38f, 7.6f), p(x1 + 1.6f, 0.4f, 9.4f)}, fill(7, 0.38f), 8, CAOUTCHOUC_USE, false, true, 0, false);
        b.color(1);
        coulure(b, 'X', x1, 4.0f, 2.6f, 1.4f, 2);
        coulure(b, 'x', x0, h - 1.0f, 6.0f, 1.2f, 0);
        boue(b, 'z', 8.0f, y0 + bv, z1, 8.4f, 1.0f, 0);
        boue(b, 'x', x0, y0, 4.8f, 8.0f, 1.6f, 1);
        decal(b, AUREOLE, 'y', 9.4f, h, 4.0f, 3.2f, 2.8f);
    }

    /** Hublot fermé (repère de la machine) : anneau en plastique, cerclage chromé, poignée. Le verre est à part. */
    static void hublot(MeshBuilder b, float cx, float cy, float z) {
        b.color(0.86f, 0.85f, 0.8f);
        b.push().translate(cx, cy, z).rotateX(90);
        b.torus(3.0f, 0.55f, 24, 6, 0, 360, PLAST);
        b.pop();
        b.color(1);
        b.push().translate(cx, cy, z + 0.3f).rotateX(90);
        b.torus(2.45f, 0.14f, 24, 4, 0, 360, CHROME_PIQUE);
        b.pop();
        b.push().translate(cx + 3.4f, cy, z).rotateZ(7).rotateY(5).translate(-(cx + 3.4f), -cy, -z);
        b.bevelBox(cx + 2.9f, cy - 1.0f, z - 0.27f, cx + 3.85f, cy + 1.0f, z + 0.47f, 0.2f, AtlasCivil.PLASTIC_GREY);
        b.pop();
        for (float y : new float[]{cy - 1.6f, cy + 1.6f}) b.bevelBox(cx - 3.75f, y - 0.4f, z - 0.4f, cx - 3.2f, y + 0.4f, z + 0.1f, 0.1f, METAL_NU);
    }

    static void laveLingeVerre(MeshBuilder b) {
        float cx = 8.0f, cy = 6.6f, z = 9.5f;
        b.push().translate(cx - 3.6f, cy, z).rotateY(-120).rotateZ(-5).translate(-(cx - 3.6f), -cy, -z);
        b.push().translate(cx, cy, z - 0.2f).rotateX(-90);
        b.lathe(new float[][]{{2.6f, 0}, {2.4f, 0.8f}, {1.6f, 1.6f}, {0.01f, 1.9f}}, 20, 0, 360, VERRE_SALE, 1, 1, 0, 0, 0, false);
        b.pop();
        b.pop();
    }

    static void microOndesVerre(MeshBuilder b) {
        b.push().translate(6.6f, 6.7f, 3.7f).rotateZ(-4);
        b.cylinder(2.2f, 0, 0.1f, 20, VERRE_SALE, VERRE_SALE, VERRE_SALE);
        b.pop();
    }
}
