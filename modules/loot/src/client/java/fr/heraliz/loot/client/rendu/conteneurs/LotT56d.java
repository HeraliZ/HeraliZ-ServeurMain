package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T56, T6 : coffres de banque, grand coffre-fort, armoire forte, caisse de laboratoire, frigo de labo, lingots. */
final class LotT56d {
    private LotT56d() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t6_coffres_banque", "t6", "metal", "Coffres de banque", "Bank safe deposit boxes", LotT56d::coffresBanque);
        Conteneurs.usure("t6_coffres_banque", 0.6f);
        Conteneurs.ajouter("t6_coffre_fort_grand", "t6", "metal", "Grand coffre-fort", "Large safe", LotT56d::coffreFortGrand);
        Conteneurs.usure("t6_coffre_fort_grand", 0.6f);
        Conteneurs.ajouter("t6_armoire_forte", "t6", "metal", "Armoire forte à code", "Keypad security cabinet", LotT56d::armoireForte);
        Conteneurs.usure("t6_armoire_forte", 0.6f);
        Conteneurs.ajouter("t6_caisse_labo", "t6", "metal", "Caisse de laboratoire", "Lab sample case", LotT56d::caisseLabo);
        Conteneurs.verre("t6_caisse_labo", LotT56d::caisseLaboVerre);
        Conteneurs.usure("t6_caisse_labo", 0.6f);
        Conteneurs.ajouter("t6_frigo_labo", "t6", "metal", "Réfrigérateur de laboratoire", "Laboratory fridge", LotT56d::frigoLabo);
        Conteneurs.verre("t6_frigo_labo", LotT56d::frigoLaboVerre);
        Conteneurs.usure("t6_frigo_labo", 0.6f);
        Conteneurs.ajouter("t6_caisse_lingots", "t6", "metal", "Caisse de lingots", "Gold bar case", LotT56d::caisseLingots);
        Conteneurs.usure("t6_caisse_lingots", 0.6f);
        LotT56e.enregistrer();
    }

    // =================================================================== coffres de banque
    /**
     * Bloc de coffres individuels (90 x 50 x 180 cm), inox : grille de 4 x 9 casiers, portes numérotées ; certaines
     * forcées et ouvertes, d'autres arrachées ; un tiroir à moitié sorti, deux tiroirs vidés au sol, papiers épars.
     */
    static void coffresBanque(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.4f, z1 = 8.4f, yb = 1.6f, yh = 27.6f, h = 28.8f, e = 0.5f, zf = z1 - 0.3f;
        b.color(0.6f, 0.6f, 0.62f);
        b.bevelBox(x0 + 0.3f, 0, z0 + 0.3f, x1 - 0.3f, yb + 0.02f, z1 - 0.4f, 0.1f, INOX);
        b.color(1);
        b.bevelBox(x0, yb, z0, x0 + e, yh + 0.02f, z1, 0.15f, INOX);
        b.bevelBox(x1 - e, yb, z0, x1, yh + 0.02f, z1, 0.15f, INOX);
        b.bevelBox(x0 - 0.15f, yh, z0 + 0.06f, x1 + 0.15f, h, z1 + 0.15f, 0.2f, INOX);
        b.bevelBox(x0 + e - 0.02f, yb, z0 + 0.06f, x1 - e + 0.02f, yb + 0.4f, z1, 0.1f, INOX);
        b.bevelBox(x0 + e - 0.02f, yb + 0.38f, z0, x1 - e + 0.02f, yh + 0.02f, z0 + 0.3f, 0.06f, INOX);
        // grille intérieure : tablettes et cloisons (sombres), fond crasseux
        int nc = 4, nl = 9;
        float cw = (x1 - x0 - 2 * e) / nc, lh = (yh - yb - 0.4f) / nl, y0 = yb + 0.4f;
        b.color(0.45f, 0.45f, 0.47f);
        for (int l = 1; l < nl; l++) b.box(x0 + e, y0 + l * lh - 0.08f, z0 + 0.3f, x1 - e, y0 + l * lh + 0.08f, zf, INOX);
        for (int c = 1; c < nc; c++) b.box(x0 + e + c * cw - 0.08f, y0, z0 + 0.3f, x0 + e + c * cw + 0.08f, yh, zf, INOX);
        b.color(1);
        // portes : fermées, ouvertes (pendantes), arrachées ; tiroir sorti
        java.util.Random r = new java.util.Random(13);
        for (int l = 0; l < nl; l++) {
            for (int c = 0; c < nc; c++) {
                float cx0 = x0 + e + c * cw + 0.1f, cx1 = x0 + e + (c + 1) * cw - 0.1f, cy0 = y0 + l * lh + 0.1f, cy1 = y0 + (l + 1) * lh - 0.1f;
                int etat = etatCasier(l, c);
                Mat porte = porteCasier(l * 4 + c + (l > 7 ? 0 : 0));
                if (etat != 0) {
                    b.color(0.7f, 0.68f, 0.64f);
                    niche(b, INTERIEUR_SALE, cx0 + 0.04f, cy0 + 0.04f, z0 + 0.36f, cx1 - 0.04f, cy1 - 0.04f, zf);
                    b.color(1);
                }
                if (etat == 0) {
                    b.box6(cx0, cy0, zf - 0.02f, cx1, cy1, zf + 0.25f, INOX, INOX, INOX, INOX, null, porte);
                } else if (etat == 1) {
                    float ang = 95 + r.nextFloat() * 25;
                    b.push().translate(cx0, 0, zf + 0.25f).rotateY(-ang).translate(-cx0, 0, -zf - 0.25f);
                    b.box6(cx0, cy0, zf, cx1, cy1, zf + 0.25f, INOX, INOX, INOX, INOX, INOX, porte);
                    b.pop();
                } else if (etat == 3) {
                    // tiroir à moitié sorti, papiers dedans
                    b.bevelBox(cx0 + 0.2f, cy0 + 0.02f, zf - 3.4f, cx1 - 0.2f, cy0 + 0.9f, zf + 2.4f, 0.06f, INOX);
                    b.box(cx0 + 0.35f, cy0 + 0.5f, zf - 3.2f, cx1 - 0.35f, cy0 + 0.78f, zf + 2.2f, PAPIER);
                    b.box6(cx0, cy0 + 0.02f, zf + 2.4f, cx1, cy1 - 0.4f, zf + 2.6f, INOX, INOX, INOX, INOX, null, porte);
                }
                if (etat == 0 && (l + c) % 3 == 0) boulon(b, cx1 - 0.5f, cy1 - 0.3f, zf + 0.25f, 0.08f);
            }
        }
        // tiroirs vidés au sol, porte arrachée, documents
        b.push().translate(4.0f, 0, 11.6f).rotateY(18);
        b.bevelBox(-1.4f, 0, -3.0f, -1.25f, 0.9f, 3.0f, 0.04f, INOX);
        b.bevelBox(1.25f, 0, -3.0f, 1.4f, 0.9f, 3.0f, 0.04f, INOX);
        b.bevelBox(-1.34f, 0.02f, -2.94f, 1.34f, 0.15f, 2.94f, 0.04f, INOX);
        b.bevelBox(-1.25f, 0, 2.85f, 1.25f, 0.9f, 3.0f, 0.04f, INOX);
        b.bevelBox(-1.25f, 0, -3.0f, 1.25f, 0.9f, -2.85f, 0.04f, INOX);
        b.box6(-1.5f, 0.1f, 3.0f, 1.5f, 2.6f, 3.2f, INOX, INOX, INOX, INOX, null, porteCasier(22));
        b.pop();
        b.push().translate(10.6f, 0.95f, 12.4f).rotateY(-62).rotateZ(180);
        b.bevelBox(-1.4f, 0, -3.0f, 1.4f, 0.95f, 3.0f, 0.04f, INOX);
        b.pop();
        b.push().translate(13.6f, 0.13f, 10.2f).rotateY(30).rotateX(-90);
        b.box6(-1.5f, -1.15f, -0.12f, 1.5f, 1.15f, 0.12f, INOX, INOX, INOX, INOX, INOX, porteCasier(9));
        b.pop();
        jonchee(b, 1.0f, 8.8f, 15.4f, 15.6f, 7, DOCUMENTS, 2.0f, 701);
        jonchee(b, 2.0f, 9.4f, 14.0f, 15.0f, 4, PAPIER, 1.6f, 702);
        // usure : coups de pied-de-biche, poussière, rouille légère au socle
        decal(b, GRIFFURES, 'z', 6.8f, 14.0f, zf + 0.25f, 2.6f, 1.0f, 30);
        decal(b, GRIFFURES, 'X', x1, 9.0f, 4.2f, 2.8f, 1.2f, -20);
        decal(b, IMPACTS, 'x', x0, 20.0f, 4.4f, 3.0f, 1.5f, 0);
        coulure(b, 'z', x0 + 0.25f, yb - 0.1f, z1, 0.6f, 1);
        boue(b, 'z', 8.0f, 0, z1 - 0.4f, 13.0f, 1.2f, 0);
    }

    /** État d'un casier : 0 fermé, 1 porte forcée ouverte, 2 porte arrachée (manquante), 3 tiroir sorti. */
    static int etatCasier(int l, int c) {
        if (l == 4 && c == 1) return 3;
        int[][] ouv = {{1, 2}, {2, 0}, {3, 3}, {5, 2}, {6, 0}, {7, 3}, {0, 1}};
        int[][] arr = {{2, 2}, {4, 3}, {6, 1}, {8, 0}, {3, 0}};
        for (int[] o : ouv) if (o[0] == l && o[1] == c) return 1;
        for (int[] o : arr) if (o[0] == l && o[1] == c) return 2;
        return 0;
    }

    // =================================================================== grand coffre-fort
    /**
     * Grand coffre-fort (75 x 55 x 150 cm), acier noir : porte de 10 cm entrouverte (pênes sortis), volant chromé,
     * cadran, plaque de laiton ; intérieur garni de tablettes, casier intérieur ouvert, liasses oubliées ; traces de
     * perçage et brûlure de chalumeau, débris au sol.
     */
    static void coffreFortGrand(MeshBuilder b) {
        float x0 = 2.0f, x1 = 14.0f, z0 = 0.6f, z1 = 9.4f, y0 = 0.9f, h = 24.0f, e = 1.2f;
        b.bevelBox(x0 + 0.4f, 0, z0 + 0.4f, x1 - 0.4f, y0 + 0.03f, z1 - 0.4f, 0.1f, AtlasLoot.DARK);
        b.bevelBox(x0, y0, z0, x0 + e, h, z1, 0.3f, ACIER_NOIR);
        b.bevelBox(x1 - e, y0, z0, x1, h, z1, 0.3f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.05f, y0, z0, x1 - e + 0.05f, y0 + e, z1, 0.2f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.05f, h - e, z0, x1 - e + 0.05f, h, z1, 0.2f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.05f, y0 + e - 0.05f, z0, x1 - e + 0.05f, h - e + 0.05f, z0 + e, 0.2f, ACIER_NOIR);
        // garniture intérieure grise, tablettes, casier intérieur, contenu
        float ix0 = x0 + e + 0.06f, ix1 = x1 - e - 0.06f, iy0 = y0 + e + 0.06f, iy1 = h - e - 0.06f, iz0 = z0 + e + 0.06f, iz1 = z1 - 0.1f;
        b.color(0.5f, 0.5f, 0.5f);
        niche(b, INOX, ix0, iy0, iz0, ix1, iy1, iz1);
        b.color(1);
        for (float y : new float[]{8.4f, 14.8f}) b.bevelBox(ix0, y, iz0, ix1, y + 0.35f, iz1 - 0.4f, 0.06f, METAL_NU);
        b.bevelBox(ix0 + 0.1f, 17.2f, iz0, ix1 - 0.1f, 17.5f, iz1 - 0.6f, 0.06f, METAL_NU);
        b.bevelBox(ix0 + 0.1f, 17.5f, iz0, ix0 + 0.4f, iy1, iz1 - 0.6f, 0.06f, METAL_NU);
        b.bevelBox(ix1 - 0.4f, 17.5f, iz0, ix1 - 0.1f, iy1, iz1 - 0.6f, 0.06f, METAL_NU);
        b.push().translate(ix0 + 0.4f, 0, iz1 - 0.6f).rotateY(-115).translate(-ix0 - 0.4f, 0, -iz1 + 0.6f);
        b.bevelBox(ix0 + 0.4f, 17.6f, iz1 - 0.75f, ix1 - 0.4f, iy1 - 0.1f, iz1 - 0.55f, 0.06f, ACIER_NOIR);
        b.pop();
        liasse(b, 6.0f, 8.75f, 4.6f, 8, 0.5f);
        liasse(b, 6.2f, 9.25f, 4.4f, -6, 0.5f);
        liasse(b, 9.4f, 8.75f, 5.0f, 30, 0.5f);
        b.push().translate(8.6f, 15.15f, 5.0f).rotateY(-10);
        b.bevelBox(-2.4f, 0, -1.8f, 2.4f, 2.2f, 1.8f, 0.08f, CARTON_MOUILLE);
        b.pop();
        b.push().translate(7.2f, iy0, 5.2f).rotateY(12);
        b.bevelBox(-1.6f, 0, -1.2f, 1.6f, 1.4f, 1.2f, 0.12f, ACIER_NOIR);
        b.pop();
        // porte épaisse entrouverte (charnière à droite), pênes sortis, volant, cadran, plaque
        float ep = 1.0f, bouchon = 0.8f;
        b.push().translate(x1, 0, z1).rotateY(26).translate(-x1, 0, -z1);
        b.bevelBox(x0 + 0.3f, y0 + 0.3f, z1, x1, h - 0.3f, z1 + ep, 0.35f, ACIER_NOIR);
        b.color(0.75f, 0.75f, 0.75f);
        b.bevelBox(x0 + e + 0.1f, y0 + e + 0.1f, z1 - bouchon, x1 - e - 0.1f, h - e - 0.1f, z1 + 0.02f, 0.2f, ACIER_NOIR);
        b.color(1);
        b.bevelBox(x0 + e + 0.5f, y0 + e + 0.5f, z1 - bouchon - 0.06f, x1 - e - 0.5f, h - e - 0.5f, z1 - bouchon + 0.04f, 0.1f, METAL_NU);
        for (float y : new float[]{6.0f, 11.0f, 16.0f, 20.6f}) {
            b.push().translate(x0 + e + 0.1f, y, z1 - bouchon / 2).rotateZ(90);
            b.cylinder(0.38f, 0, 1.1f, 10, AtlasLoot.CHROME, null, AtlasLoot.CHROME);
            b.pop();
        }
        float fz = z1 + ep, cxv = 7.2f, cyv = 12.2f;
        b.push().translate(cxv, cyv, fz).rotateX(90);
        b.cylinder(0.5f, 0, 0.75f, 12, AtlasLoot.CHROME, null, AtlasLoot.CHROME);
        b.translate(0, 0.6f, 0);
        b.torus(1.7f, 0.16f, 20, 6, 0, 360, AtlasLoot.CHROME);
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(90 + k * 120);
            b.tube(new float[][]{p(0, 0, 0), p((float) Math.cos(a) * 1.7f, 0, (float) Math.sin(a) * 1.7f)}, fill(2, 0.12f), 6, AtlasLoot.CHROME, false, false, 0, false);
            b.push().translate((float) Math.cos(a) * 1.7f, 0, (float) Math.sin(a) * 1.7f);
            b.cylinder(0.2f, 0, 0.9f, 8, AtlasLoot.DARK, null, AtlasLoot.DARK);
            b.pop();
        }
        b.pop();
        b.push().translate(cxv, cyv + 4.0f, fz).rotateX(90);
        b.cylinder(1.1f, 0, 0.25f, 16, ACIER_NOIR, null, ACIER_NOIR);
        b.pop();
        b.push().translate(cxv, cyv + 4.0f, fz + 0.25f);
        b.decalZ(CADRAN, 0, 0, 0.05f, 2.1f, 2.1f);
        b.pop();
        Kit.pochoir(b, PLAQUE_LAITON, 'z', cxv, cyv - 4.4f, fz, 2.6f, 1.3f);
        b.push().translate(cxv, cyv - 6.6f, fz).rotateX(90);
        b.cylinder(0.34f, 0, 0.15f, 10, AtlasLoot.BRASS, null, AtlasLoot.BRASS);
        b.pop();
        decal(b, IMPACTS, 'z', cxv + 1.6f, cyv + 4.0f, fz, 2.4f, 1.2f, 20);
        decal(b, BRULURE, 'z', x0 + 1.6f, 15.0f, fz, 3.0f, 4.4f, 0);
        decal(b, GRIFFURES, 'z', x0 + 1.4f, 8.0f, fz, 2.4f, 1.0f, 80);
        b.pop();
        // charnières (deux gonds) sur l'arête droite
        for (float y : new float[]{4.0f, 20.0f}) {
            b.push().translate(x1 + 0.1f, y, z1 + 0.3f);
            b.cylinder(0.5f, 0, 2.6f, 12, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR);
            b.pop();
        }
        // usure, débris de perçage et mèche cassée au sol
        decal(b, BRULURE, 'X', x1, 14.0f, 6.4f, 3.4f, 3.4f, 0);
        coulure(b, 'X', x1, 21.6f, 7.6f, 1.2f, 0);
        coulure(b, 'x', x0, 10.0f, 3.0f, 1.1f, 2);
        boue(b, 'X', x1, y0, 5.0f, 8.0f, 1.4f, 0);
        debris(b, 6.0f, 13.8f, 2.2f, 10, METAL_NU, 711);
        b.push().translate(10.4f, 0.12f, 14.4f).rotateY(40);
        b.cylinder(0.12f, 0, 0.1f, 6, METAL_NU, null, null);
        b.tube(new float[][]{p(0, 0, 0), p(2.4f, 0, 0)}, fill(2, 0.1f), 6, METAL_NU, true, true, 0, false);
        b.pop();
        jonchee(b, 1.0f, 15.6f, 15.0f, 15.9f, 2, PAPIER, 1.2f, 712);
    }

    // =================================================================== armoire forte
    /**
     * Armoire forte rouge à clavier (80 x 50 x 160 cm) : porte forcée au pied-de-biche, coin tordu, écran du clavier en
     * veille ; classeurs et boîte à valeurs sur les tablettes ; le pied-de-biche abandonné au sol.
     */
    static void armoireForte(MeshBuilder b) {
        float x0 = 1.8f, x1 = 14.6f, z0 = 0.5f, z1 = 8.5f, y0 = 0.7f, h = 25.6f, e = 0.45f;
        b.bevelBox(x0 + 0.3f, 0, z0 + 0.3f, x1 - 0.3f, y0 + 0.03f, z1 - 0.5f, 0.1f, AtlasLoot.DARK);
        b.bevelBox(x0, y0, z0, x0 + e, h, z1, 0.15f, ROUGE_SECU);
        b.bevelBox(x1 - e, y0, z0, x1, h, z1, 0.15f, ROUGE_SECU);
        b.bevelBox(x0 + e - 0.03f, y0, z0, x1 - e + 0.03f, y0 + e, z1, 0.12f, ROUGE_SECU);
        b.bevelBox(x0 + e - 0.03f, h - e, z0, x1 - e + 0.03f, h, z1, 0.12f, ROUGE_SECU);
        b.bevelBox(x0 + e - 0.03f, y0 + e - 0.03f, z0, x1 - e + 0.03f, h - e + 0.03f, z0 + e, 0.1f, ROUGE_SECU);
        // tablettes, classeurs, boîte à valeurs, documents
        float ix0 = x0 + e, ix1 = x1 - e, iz0 = z0 + e;
        for (float y : new float[]{7.6f, 13.8f, 19.8f}) b.bevelBox(ix0, y, iz0, ix1, y + 0.3f, z1 - 0.6f, 0.06f, ROUGE_SECU);
        float[][] coul = {{0.25f, 0.32f, 0.5f}, {0.5f, 0.2f, 0.18f}, {0.22f, 0.22f, 0.22f}, {0.3f, 0.42f, 0.3f}};
        for (int k = 0; k < 6; k++) {
            float[] c = coul[k % 4];
            b.color(c[0], c[1], c[2]);
            float x = ix0 + 0.4f + k * 1.0f;
            b.push().translate(x, 13.8f + 0.3f, iz0 + 0.4f).rotateZ(k == 5 ? -24 : 0);
            b.bevelBox(0, 0, 0, 0.9f, 4.4f, 4.6f, 0.08f, PLASTIQUE_GRAIN);
            b.pop();
        }
        b.color(1);
        b.push().translate(9.6f, 7.9f, 4.0f).rotateY(-8);
        b.bevelBox(-2.0f, 0, -1.4f, 2.0f, 1.8f, 1.4f, 0.12f, ACIER_NOIR);
        b.bevelBox(-0.5f, 1.8f, -0.3f, 0.5f, 2.2f, 0.3f, 0.06f, AtlasLoot.CHROME);
        b.pop();
        b.push().translate(5.6f, 20.1f, 3.4f).rotateY(5);
        b.bevelBox(-2.2f, 0, -1.6f, 2.2f, 1.2f, 1.6f, 0.06f, PAPIER);
        b.push().translate(0, 1.2f, 0).rotateX(-90);
        b.decalZ(DOCUMENTS, 0, 0, 0.05f, 4.0f, 3.0f);
        b.pop();
        b.pop();
        decal(b, DOCUMENTS, 'y', 7.2f, y0 + e, 4.4f, 3.4f, 3.4f, 25);
        // porte forcée (charnière à gauche), coin haut droit tordu
        float zd = z1, ep = 0.55f;
        b.push().translate(x0, 0, zd).rotateY(-16).translate(-x0, 0, -zd);
        b.bevelBox(x0 + 0.05f, y0 + 0.05f, zd, x1 - 0.05f, h - 4.0f, zd + ep, 0.15f, ROUGE_SECU);
        b.bevelBox(x0 + 0.05f, h - 4.0f, zd, x1 - 4.0f, h - 0.05f, zd + ep, 0.15f, ROUGE_SECU);
        b.push().translate(x1 - 4.0f, h - 4.0f, zd).rotateY(-14).rotateX(-6);
        b.bevelBox(0.02f, 0.02f, 0, 3.95f, 3.95f, ep, 0.15f, ROUGE_SECU);
        b.pop();
        float fz = zd + ep;
        // clavier, écran en veille, voyant, poignée, pêne tordu
        b.bevelBox(x1 - 3.6f, 13.4f, fz - 0.02f, x1 - 1.2f, 17.8f, fz + 0.3f, 0.1f, ACIER_NOIR);
        Kit.pochoir(b, KEYPAD_ACIER, 'z', x1 - 2.4f, 14.7f, fz + 0.3f, 1.9f, 1.9f);
        Kit.pochoir(b, ECRAN_VEILLE, 'z', x1 - 2.4f, 16.7f, fz + 0.3f, 1.8f, 0.9f);
        b.bevelBox(x1 - 1.6f, 17.3f, fz + 0.28f, x1 - 1.35f, 17.55f, fz + 0.36f, 0.03f, LED_ROUGE);
        b.bevelBox(x1 - 2.0f, 9.0f, fz - 0.02f, x1 - 1.4f, 12.6f, fz + 0.2f, 0.1f, AtlasLoot.DARK);
        b.push().translate(x1 - 1.7f, 11.6f, fz + 0.2f).rotateZ(-70);
        b.bevelBox(-0.25f, -2.4f, 0, 0.25f, 0.2f, 0.4f, 0.1f, AtlasLoot.CHROME);
        b.pop();
        decal(b, GRIFFURES, 'z', x1 - 1.4f, 7.4f, fz, 2.6f, 1.0f, 75);
        decal(b, GRIFFURES, 'z', x1 - 1.2f, 20.0f, fz, 2.2f, 0.9f, 95);
        Kit.pochoir(b, S_CONFIDENTIEL, 'z', (x0 + x1) / 2 - 1.0f, 21.4f, fz, 5.6f, 1.4f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', x0 + 2.4f, 4.0f, fz, 1.8f, 0.9f);
        b.pop();
        b.push().translate(x1 - 0.5f, 11.0f, zd + 0.2f).rotateY(30).rotateZ(10);
        b.bevelBox(0, -0.4f, -0.15f, 1.4f, 0.4f, 0.15f, 0.06f, METAL_NU);
        b.pop();
        // pied-de-biche abandonné au sol
        float[][] pdb = lisse(new float[][]{p(2.6f, 0.18f, 12.8f), p(9.0f, 0.18f, 14.6f), p(10.6f, 0.18f, 14.9f), p(11.2f, 0.4f, 14.6f), p(11.4f, 0.8f, 14.0f)}, 3);
        b.color(0.75f, 0.18f, 0.14f);
        b.tube(pdb, fill(pdb.length, 0.18f), 6, PLASTIQUE_GRAIN, true, true, 0, false);
        b.color(1);
        b.push().translate(2.6f, 0.18f, 12.8f).rotateY(-16);
        b.bevelBox(-0.9f, -0.12f, -0.22f, 0, 0.1f, 0.22f, 0.06f, METAL_NU);
        b.pop();
        // usure : éclats de peinture au pied-de-biche, coulures, poussière au sol
        decal(b, GRIFFURES, 'X', x1, 12.0f, 7.4f, 2.4f, 1.0f, -80);
        coulure(b, 'X', x1, h - 0.3f, 4.0f, 1.2f, 1);
        coulure(b, 'x', x0, 15.0f, 5.0f, 1.0f, 0);
        boue(b, 'X', x1, y0, 4.4f, 7.2f, 1.4f, 1);
        debris(b, 13.0f, 11.6f, 1.4f, 6, ROUGE_SECU, 721);
    }

    // =================================================================== caisse de laboratoire
    static final float LB_X0 = 4.4f, LB_X1 = 11.4f, LB_Z0 = 2.0f, LB_Z1 = 6.9f, LB_Y0 = 4.85f;

    /**
     * Deux caisses de transport d'échantillons en aluminium empilées (danger biologique) : celle du haut ouverte,
     * mousse percée, tubes à bouchons colorés (quelques places vides) ; un tube brisé au sol et sa tache.
     */
    static void caisseLabo(MeshBuilder b) {
        float x0 = 3.6f, x1 = 12.0f, z0 = 1.2f, z1 = 7.0f, h = 4.8f;
        b.bevelBox(x0, 0, z0, x1, h, z1, 0.35f, ALU);
        for (float y : new float[]{1.1f, 3.4f}) b.bevelBox(x0 - 0.06f, y, z0 - 0.06f, x1 + 0.06f, y + 0.3f, z1 + 0.06f, 0.08f, ALU);
        Kit.coins(b, x0, 0, z0, x1, h, z1, 0.8f, AtlasLoot.CHROME);
        Kit.fermoir(b, 5.4f, h - 1.2f, z1, AtlasLoot.CHROME);
        Kit.fermoir(b, 10.2f, h - 1.2f, z1, AtlasLoot.CHROME);
        Kit.pochoir(b, ETIQUETTE_BIO, 'z', 7.8f, 2.25f, z1, 4.0f, 1.0f);
        Kit.pochoir(b, S_ECHANTILLONS, 'X', x1, 2.3f, (z0 + z1) / 2, 4.0f, 1.0f);
        // caisse du haut ouverte
        float ax0 = LB_X0, ax1 = LB_X1, az0 = LB_Z0, az1 = LB_Z1, y0 = LB_Y0, yc = y0 + 3.2f, e = 0.25f;
        b.bevelBox(ax0, y0, az0, ax0 + e, yc, az1, 0.08f, ALU);
        b.bevelBox(ax1 - e, y0, az0, ax1, yc, az1, 0.08f, ALU);
        b.bevelBox(ax0 + e - 0.02f, y0, az0, ax1 - e + 0.02f, yc, az0 + e, 0.08f, ALU);
        b.bevelBox(ax0 + e - 0.02f, y0, az1 - e, ax1 - e + 0.02f, yc, az1, 0.08f, ALU);
        b.bevelBox(ax0 + e - 0.02f, y0, az0 + e - 0.02f, ax1 - e + 0.02f, y0 + 0.3f, az1 - e + 0.02f, 0.05f, ALU);
        float yf = yc - 1.3f;
        b.color(0.75f, 0.75f, 0.78f);
        b.box(ax0 + e, y0 + 0.3f, az0 + e, ax1 - e, yf, az1 - e, MOUSSE_GRISE);
        b.color(1);
        // tubes : 2 rangées de 6, bouchons colorés, liquide ; trois places vides (trous sombres)
        float[][] cols = {{0.7f, 0.15f, 0.12f}, {0.85f, 0.7f, 0.15f}, {0.4f, 0.2f, 0.5f}};
        for (int i = 0; i < 6; i++) {
            for (int k = 0; k < 2; k++) {
                float tx = ax0 + 0.9f + i * 1.0f, tz = az0 + 1.5f + k * 1.9f;
                boolean vide = (i == 2 && k == 0) || (i == 4 && k == 1) || (i == 5 && k == 0);
                if (vide) {
                    b.push().translate(tx, yf + 0.06f, tz);
                    b.disc(0.3f, 0, 10, AtlasLoot.DARK, true);
                    b.pop();
                    continue;
                }
                float[] c = cols[(i + k) % 3];
                b.color(c[0], c[1], c[2]);
                b.push().translate(tx, yf + 1.5f, tz);
                b.cylinder(0.32f, 0, 0.45f, 10, PLASTIQUE_GRAIN, PLASTIQUE_GRAIN, PLASTIQUE_GRAIN);
                b.pop();
                b.color(c[0] * 0.6f, c[1] * 0.5f, c[2] * 0.5f);
                b.push().translate(tx, yf - 0.4f, tz);
                b.cylinder(0.22f, 0, 0.6f + ((i * 7 + k * 3) % 5) * 0.15f, 8, PLASTIQUE_GRAIN, null, PLASTIQUE_GRAIN);
                b.pop();
                b.color(1);
            }
        }
        // couvercle ouvert (100°) contre le mur
        b.push().translate(0, yc, az0).rotateX(-100).translate(0, -yc, -az0);
        b.bevelBox(ax0 - 0.04f, yc + 0.02f, az0 - 0.04f, ax1 + 0.04f, yc + 0.8f, az1 + 0.04f, 0.2f, ALU);
        b.color(0.75f, 0.75f, 0.78f);
        b.box(ax0 + 0.3f, yc - 0.4f, az0 + 0.3f, ax1 - 0.3f, yc + 0.04f, az1 - 0.3f, AtlasLoot.FOAM);
        b.color(1);
        b.pop();
        for (float x : new float[]{5.4f, 10.4f}) fermoirOuvert(b, x, yc, az1, x < 6 ? 30 : 10, AtlasLoot.CHROME);
        Kit.pochoir(b, BIOHAZARD, 'z', 7.9f, y0 + 1.5f, az1, 1.8f, 1.8f);
        // tube brisé au sol, tache, éclats
        b.color(0.4f, 0.2f, 0.5f);
        b.push().translate(11.2f, 0.32f, 10.6f).rotateY(30).rotateZ(90);
        b.cylinder(0.32f, -0.2f, 0.25f, 10, PLASTIQUE_GRAIN, PLASTIQUE_GRAIN, PLASTIQUE_GRAIN);
        b.pop();
        b.color(1);
        decal(b, AUREOLE, 'y', 10.0f, 0, 11.6f, 3.0f, 2.4f, 20);
        debris(b, 10.2f, 11.4f, 1.4f, 8, CHROME_PIQUE, 731);
        // usure : rayures, poussière, boue, griffures
        decal(b, GRIFFURES, 'x', x0, 2.2f, 4.0f, 3.0f, 1.2f, 5);
        boue(b, 'z', 7.8f, 0, z1, 6.0f, 0.95f, 1);
        decal(b, ETIQUETTE_DECHIREE, 'X', ax1, y0 + 1.6f, 4.6f, 2.2f, 1.1f, -6);
    }

    /** Verre des tubes (passe translucide) et éclats du tube brisé. */
    static void caisseLaboVerre(MeshBuilder b) {
        float ax0 = LB_X0, az0 = LB_Z0, yf = LB_Y0 + 3.2f - 1.3f;
        for (int i = 0; i < 6; i++) {
            for (int k = 0; k < 2; k++) {
                if ((i == 2 && k == 0) || (i == 4 && k == 1) || (i == 5 && k == 0)) continue;
                b.push().translate(ax0 + 0.9f + i * 1.0f, yf - 0.45f, az0 + 1.5f + k * 1.9f);
                b.cylinder(0.27f, 0, 1.95f, 10, VERRE_SALE, null, null);
                b.pop();
            }
        }
        b.push().translate(11.2f, 0.28f, 10.6f).rotateY(30).rotateZ(90);
        b.cylinder(0.27f, 0.25f, 1.1f, 10, VERRE_SALE, null, null);
        b.pop();
    }

    // =================================================================== réfrigérateur de laboratoire
    static final float FL_X0 = 3.0f, FL_X1 = 12.6f, FL_Z1 = 10.0f, FL_Y0 = 1.2f, FL_YH = 25.8f;

    /**
     * Réfrigérateur de laboratoire (60 x 60 x 185 cm) : porte vitrée fêlée entrouverte (verre en passe translucide),
     * clayettes grillagées, flacons et portoirs de tubes (certains renversés), afficheur de température éteint,
     * étiquette danger biologique, roulettes ; flacon brisé au pied.
     */
    static void frigoLabo(MeshBuilder b) {
        float x0 = FL_X0, x1 = FL_X1, z0 = 0.5f, z1 = FL_Z1, y0 = FL_Y0, yh = FL_YH, h = 29.6f, e = 0.6f;
        for (float x : new float[]{x0 + 1.0f, x1 - 1.0f}) {
            for (float z : new float[]{z0 + 1.2f, z1 - 1.2f}) {
                b.push().translate(x, 0.5f, z).rotateZ(90);
                b.cylinder(0.5f, -0.25f, 0.25f, 10, CAOUTCHOUC_USE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
                b.pop();
                b.bevelBox(x - 0.4f, 0.8f, z - 0.4f, x + 0.4f, y0 + 0.02f, z + 0.4f, 0.08f, METAL_NU);
            }
        }
        b.bevelBox(x0, y0, z0, x0 + e, h, z1, 0.2f, BLANC_ONU);
        b.bevelBox(x1 - e, y0, z0, x1, h, z1, 0.2f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.03f, y0, z0, x1 - e + 0.03f, y0 + 1.6f, z1, 0.15f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.03f, yh, z0, x1 - e + 0.03f, h, z1, 0.15f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.03f, y0 + 1.5f, z0, x1 - e + 0.03f, yh + 0.03f, z0 + e, 0.12f, BLANC_ONU);
        // grille d'aération en bas, panneau de commande en haut
        b.box(x0 + 1.0f, y0 + 0.3f, z1 + 0.01f, x1 - 1.0f, y0 + 1.2f, z1 + 0.07f, AtlasLoot.DARK);
        for (float x = x0 + 1.3f; x < x1 - 1.0f; x += 0.7f) b.box(x, y0 + 0.35f, z1 + 0.06f, x + 0.3f, y0 + 1.15f, z1 + 0.12f, METAL_NU);
        Kit.pochoir(b, LCD_OFF, 'z', x0 + 2.6f, yh + 1.9f, z1, 2.4f, 1.2f);
        for (int k = 0; k < 3; k++) {
            b.push().translate(x0 + 4.9f + k * 0.9f, yh + 1.9f, z1).rotateX(90);
            b.cylinder(0.25f, 0, 0.15f, 8, AtlasLoot.DARK, null, AtlasLoot.DARK);
            b.pop();
        }
        Kit.pochoir(b, LED_ETEINTE, 'z', x1 - 1.4f, yh + 1.9f, z1, 0.4f, 0.4f);
        Kit.pochoir(b, ETIQUETTE_BIO, 'z', x1 - 3.4f, yh + 1.9f, z1, 2.8f, 0.7f);
        // intérieur blanc, clayettes grillagées, flacons
        float ix0 = x0 + e + 0.06f, ix1 = x1 - e - 0.06f, iy0 = y0 + 1.66f, iy1 = yh - 0.06f, iz0 = z0 + e + 0.06f, iz1 = z1 - 0.2f;
        b.color(0.85f, 0.85f, 0.82f);
        niche(b, PLASTIQUE_USE, ix0, iy0, iz0, ix1, iy1, iz1);
        b.color(1);
        float[] ys = {iy0 + 5.6f, iy0 + 11.2f, iy0 + 16.8f};
        for (float y : ys) {
            b.box(ix0, y, iz0, ix1, y + 0.12f, iz1 - 0.6f, GRILLAGE);
            b.bevelBox(ix0, y - 0.05f, iz1 - 0.75f, ix1, y + 0.2f, iz1 - 0.55f, 0.04f, METAL_NU);
        }
        float[] base = {iy0, ys[0] + 0.12f, ys[1] + 0.12f, ys[2] + 0.12f};
        java.util.Random r = new java.util.Random(17);
        for (int s = 0; s < 4; s++) {
            for (int k = 0; k < 4; k++) {
                if ((s * 4 + k) % 5 == 3) continue;
                float bx = ix0 + 0.9f + k * 1.9f + r.nextFloat() * 0.3f, bz = iz0 + 1.6f + r.nextFloat() * 4.0f;
                boolean couche = (s * 4 + k) % 6 == 1;
                b.push().translate(bx, base[s] + (couche ? 0.75f : 0), bz);
                if (couche) b.rotateY(r.nextFloat() * 90).rotateZ(90).translate(0, -1.4f, 0);
                Mat m = (k + s) % 2 == 0 ? AtlasCivil.BOTTLE_BROWN : AtlasCivil.MILK;
                b.lathe(new float[][]{{0.7f, 0}, {0.72f, 1.9f}, {0.4f, 2.4f}, {0.3f, 2.8f}, {0.34f, 2.85f}, {0.34f, 3.2f}, {0.01f, 3.21f}}, 8, 0, 360, m, 1, 1, 0, 0, 0, false);
                b.pop();
            }
        }
        b.push().translate(ix0 + 2.0f, base[1], iz0 + 3.0f);
        b.bevelBox(-1.6f, 0, -0.8f, 1.6f, 0.9f, 0.8f, 0.06f, PLASTIQUE_USE);
        for (int k = 0; k < 5; k++) {
            b.push().translate(-1.2f + k * 0.6f, 0.9f, 0);
            b.color(0.7f, 0.2f, 0.16f);
            b.cylinder(0.18f, 0, 0.35f, 6, PLASTIQUE_GRAIN, null, PLASTIQUE_GRAIN);
            b.color(1);
            b.pop();
        }
        b.pop();
        // porte vitrée entrouverte (cadre noir), poignée
        float zd = z1, ep = 0.6f;
        b.push().translate(x0, 0, zd).rotateY(-9).translate(-x0, 0, -zd);
        b.bevelBox(x0 + 0.05f, y0 + 1.55f, zd, x0 + 1.0f, yh - 0.05f, zd + ep, 0.12f, ACIER_NOIR);
        b.bevelBox(x1 - 1.0f, y0 + 1.55f, zd, x1 - 0.05f, yh - 0.05f, zd + ep, 0.12f, ACIER_NOIR);
        b.bevelBox(x0 + 0.98f, y0 + 1.55f, zd, x1 - 0.98f, y0 + 2.5f, zd + ep, 0.12f, ACIER_NOIR);
        b.bevelBox(x0 + 0.98f, yh - 1.0f, zd, x1 - 0.98f, yh - 0.05f, zd + ep, 0.12f, ACIER_NOIR);
        b.bevelBox(x1 - 0.9f, 11.0f, zd + ep - 0.02f, x1 - 0.5f, 19.0f, zd + ep + 0.6f, 0.15f, AtlasLoot.CHROME);
        b.pop();
        // flacon brisé au pied, tache séchée
        b.push().translate(10.6f, 0.68f, 13.2f).rotateY(-40).rotateZ(90).translate(0, -1.4f, 0);
        b.lathe(new float[][]{{0.7f, 0}, {0.72f, 1.6f}, {0.5f, 1.8f}, {0.01f, 1.81f}}, 8, 0, 360, AtlasCivil.BOTTLE_BROWN, 1, 1, 0, 0, 0, false);
        b.pop();
        debris(b, 9.0f, 13.0f, 1.6f, 8, AtlasCivil.BOTTLE_BROWN, 741);
        decal(b, AUREOLE, 'y', 8.6f, 0, 12.6f, 3.6f, 2.8f, 0);
        // usure : jaunissement, coulures, poussière, boue, étiquette arrachée
        coulure(b, 'X', x1, yh - 0.4f, 6.0f, 1.2f, 1);
        boue(b, 'X', x1, y0, 5.4f, 9.0f, 1.4f, 0);
        decal(b, ETIQUETTE_DECHIREE, 'X', x1, 18.0f, 4.0f, 2.6f, 1.3f, 3);
        decal(b, AUREOLE, 'x', x0, 14.0f, 5.0f, 3.0f, 3.0f, 0);
    }

    /** Vitre fêlée de la porte (passe translucide), suit la rotation de la porte. */
    static void frigoLaboVerre(MeshBuilder b) {
        float x0 = FL_X0, x1 = FL_X1, zd = FL_Z1, y0 = FL_Y0, yh = FL_YH, zg = zd + 0.3f;
        b.push().translate(x0, 0, zd).rotateY(-9).translate(-x0, 0, -zd);
        b.face(VERRE_FELE, MeshBuilder.v(0, 0, 1), p(x0 + 1.0f, y0 + 2.5f, zg), p(x1 - 1.0f, y0 + 2.5f, zg), p(x1 - 1.0f, yh - 1.0f, zg), p(x0 + 1.0f, yh - 1.0f, zg));
        b.face(VERRE_FELE, MeshBuilder.v(0, 0, -1), p(x1 - 1.0f, y0 + 2.5f, zg - 0.02f), p(x0 + 1.0f, y0 + 2.5f, zg - 0.02f), p(x0 + 1.0f, yh - 1.0f, zg - 0.02f), p(x1 - 1.0f, yh - 1.0f, zg - 0.02f));
        b.pop();
    }

    // =================================================================== caisse de lingots
    /**
     * Petite caisse renforcée (40 x 30 x 20 cm), acier noir et coins chromés, couvercle ouvert : feutre, lingots ternis
     * dans leurs logements (deux manquent) ; un lingot tombé au sol, un autre appuyé contre la caisse.
     */
    static void caisseLingots(MeshBuilder b) {
        float x0 = 4.4f, x1 = 11.2f, z0 = 2.0f, z1 = 7.0f, yc = 2.6f, e = 0.3f;
        b.bevelBox(x0, 0, z0, x0 + e, yc, z1, 0.08f, ACIER_NOIR);
        b.bevelBox(x1 - e, 0, z0, x1, yc, z1, 0.08f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.02f, 0, z0, x1 - e + 0.02f, yc, z0 + e, 0.08f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.02f, 0, z1 - e, x1 - e + 0.02f, yc, z1, 0.08f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.02f, 0, z0 + e - 0.02f, x1 - e + 0.02f, 0.3f, z1 - e + 0.02f, 0.05f, ACIER_NOIR);
        for (float x : new float[]{x0 + 1.2f, x1 - 1.9f}) b.bevelBox(x, -0.0f, z1 - 0.05f, x + 0.7f, yc + 0.02f, z1 + 0.15f, 0.06f, METAL_NU);
        Kit.coins(b, x0, 0, z0, x1, yc, z1, 0.6f, AtlasLoot.CHROME);
        b.box(x0 + e, 0.3f, z0 + e, x1 - e, 1.3f, z1 - e, AtlasLoot.INNER);
        // logements : 3 x 2, lingots
        float lw = (x1 - x0 - 2 * e) / 2, ld = (z1 - z0 - 2 * e) / 3;
        for (int c = 0; c < 2; c++) {
            for (int l = 0; l < 3; l++) {
                if ((c == 1 && l == 0) || (c == 0 && l == 2)) {
                    b.push().translate(x0 + e + (c + 0.5f) * lw, 1.32f, z0 + e + (l + 0.5f) * ld);
                    b.box(-lw / 2 + 0.3f, 0, -ld / 2 + 0.2f, lw / 2 - 0.3f, 0.02f, ld / 2 - 0.2f, AtlasLoot.DARK);
                    b.pop();
                    continue;
                }
                lingot(b, x0 + e + (c + 0.5f) * lw, 1.28f, z0 + e + (l + 0.5f) * ld, (c * 3 + l) % 2 == 0 ? 2 : -3, 2.6f, 1.1f, 0.65f);
            }
        }
        // couvercle ouvert (100°), feutre, sangles de renfort
        b.push().translate(0, yc, z0).rotateX(-100).translate(0, -yc, -z0);
        b.bevelBox(x0 - 0.04f, yc + 0.02f, z0 - 0.04f, x1 + 0.04f, yc + 0.7f, z1 + 0.04f, 0.15f, ACIER_NOIR);
        b.box(x0 + 0.35f, yc - 0.12f, z0 + 0.35f, x1 - 0.35f, yc + 0.04f, z1 - 0.35f, AtlasLoot.INNER);
        b.pop();
        b.bevelBox(7.4f, yc - 1.0f, z1 - 0.02f, 8.2f, yc - 0.1f, z1 + 0.25f, 0.06f, AtlasLoot.DARK);
        b.push().translate(7.8f, yc - 0.4f, z1 + 0.25f).rotateX(-20);
        b.bevelBox(-0.3f, -1.3f, 0, 0.3f, 0, 0.18f, 0.05f, AtlasLoot.CHROME);
        b.pop();
        Kit.poigneeU(b, x1 + 0.08f, 1.6f, (z0 + z1) / 2, 2.2f, 1, AtlasLoot.CHROME);
        Kit.poigneeU(b, x0 - 0.08f, 1.6f, (z0 + z1) / 2, 2.2f, -1, AtlasLoot.CHROME);
        // lingot tombé au sol, lingot appuyé contre la caisse
        lingot(b, 9.8f, 0, 10.6f, 34, 2.6f, 1.1f, 0.65f);
        b.push().translate(12.3f, 0, 4.8f).rotateZ(68);
        lingot(b, 0.62f, -0.02f, 0, 90, 2.6f, 1.1f, 0.65f);
        b.pop();
        // usure : rayures, griffures du couvercle forcé, poussière
        decal(b, GRIFFURES, 'z', 7.8f, 1.2f, z1, 2.4f, 0.9f, 8);
        Kit.pochoir(b, AtlasLoot.LABEL, 'x', x0, 1.3f, 4.5f, 1.8f, 0.9f);
        boue(b, 'z', 7.4f, 0, z1, 3.2f, 0.9f, 1);
        jonchee(b, 2.0f, 8.0f, 14.0f, 13.0f, 4, FEUILLES, 0.9f, 751);
    }
}
