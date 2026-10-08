package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BOTTLE_GREEN;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BRASS;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.PAINT_RED;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.WHITE;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Lot T2b, meubles (suite) : buffet de ferme peint, vaisselier, coffre de bout de lit, machine à coudre à pédale,
 * pile de valises anciennes.
 */
final class LotT2bMeubles2 {
    private LotT2bMeubles2() {
    }

    /** Vert d'eau passé des meubles de ferme peints (teinte de BOIS_PEINT). */
    static void vertEau(MeshBuilder b) {
        b.color(0.6f, 0.71f, 0.6f);
    }

    // =================================================================== buffet
    /**
     * Buffet bas de ferme (1,4 m x 52 x 92 cm) peint vert d'eau écaillé, plateau de chêne : deux tiroirs (le droit
     * tiré : couverts, torchon), deux portes dont la droite, arrachée du gond du haut, pend de travers sur l'intérieur
     * (assiettes, bocaux) ; vaisselle sur le plateau (pile d'assiettes, bols, tasses, pichet renversé et sa tache),
     * torchon qui pend, assiette cassée au sol, croix de fouille sur un tiroir.
     */
    static void buffet(MeshBuilder b) {
        Mat pe = BOIS_PEINT, ch = CHENE;
        float x0 = 0.6f, x1 = 23.0f, z0 = 0.4f, zf = 8.4f, yb = 1.2f, yt = 13.8f, xm0 = 11.2f, xm1 = 12.4f;
        vertEau(b);
        bois(b, x0 - 0.05f, 0, zf - 1.3f, x0 + 1.2f, yt, zf, 0.15f, pe);
        bois(b, x1 - 1.2f, 0, zf - 1.3f, x1 + 0.05f, yt, zf, 0.15f, pe);
        bois(b, x0 - 0.05f, 0, z0, x0 + 1.2f, yt, z0 + 1.3f, 0.15f, pe, NZ);
        bois(b, x1 - 1.2f, 0, z0, x1 + 0.05f, yt, z0 + 1.3f, 0.15f, pe, NZ);
        float fx0 = x0 + 1.15f, fx1 = x1 - 1.15f, rz0 = zf - 1.0f, rz1 = zf - 0.08f;
        bois(b, fx0, yb, rz0, fx1, 2.4f, rz1, 0.12f, pe);
        bois(b, fx0, 9.6f, rz0, fx1, 10.2f, rz1, 0.1f, pe);
        bois(b, fx0, 13.2f, rz0, fx1, yt, rz1, 0.1f, pe);
        bois(b, xm0, 2.35f, rz0 + 0.05f, xm1, 13.25f, rz1 + 0.06f, 0.12f, pe);
        float[][] arc = new float[9][];
        for (int k = 0; k < 9; k++) {
            double a = Math.PI * k / 8;
            arc[k] = new float[]{(float) Math.cos(a) * 3.2f, -(float) Math.sin(a) * 0.85f};
        }
        b.push().translate((xm0 + xm1) / 2, yb + 0.02f, zf - 0.45f);
        b.slab(arc, 0.5f, 0.12f, true, pe, null, pe);
        b.pop();
        cotes(b, x0, x1, z0, zf, yb, 2.6f, 12.6f, yt, pe);
        // intérieur : fond, plancher, cloison, tablette, et ce qui reste dedans
        b.color(0.55f, 0.5f, 0.45f);
        bois(b, fx0, 2.0f, z0 + 0.1f, fx1, 13.2f, z0 + 0.5f, 0.05f, ch, NZ);
        bois(b, fx0, 2.0f, 0.9f, fx1, 2.38f, rz0, 0.05f, ch);
        bois(b, xm0 + 0.2f, 2.38f, 0.95f, xm1 - 0.2f, 9.35f, rz0 - 0.05f, 0.05f, ch);
        bois(b, fx0, 9.3f, 0.9f, fx1, 9.62f, rz0, 0.05f, ch);
        bois(b, xm1 - 0.2f, 5.9f, 0.9f, fx1, 6.25f, rz0 - 0.4f, 0.05f, ch);
        b.color(1);
        pile(b, 14.8f, 2.38f, 3.6f, 1.6f, 5, ASSIETTE_2);
        b.push().translate(18.6f, 2.38f + 0.6f, 4.2f).rotateY(70).rotateZ(90).translate(0, -2.4f, 0);
        bouteille(b, BOTTLE_GREEN, PAINT_RED, 8);
        b.pop();
        bocal(b, 15.0f, 6.25f, 4.0f, 0.9f, 2.0f, BOCAL_CONFITURE, false);
        bocal(b, 17.4f, 6.25f, 3.6f, 0.9f, 2.0f, BOCAL_ABRICOT, true);
        toile(b, fx1 - 0.02f, 9.3f, 4.5f, 2.6f, 2.2f, 180, 0);
        // tiroirs : gauche fermé, droit tiré
        vertEau(b);
        tiroir(b, fx0 + 0.05f, 10.25f, xm0 - 0.05f, 13.15f, zf - 0.12f, 0, pe, true);
        vertEau(b);
        tiroir(b, xm1 + 0.05f, 10.25f, fx1 - 0.05f, 13.15f, zf - 0.12f, 3.0f, pe, true);
        // portes : gauche fermée, droite arrachée du gond du haut
        float dz0 = zf - 0.9f, dz1 = zf - 0.16f;
        vertEau(b);
        porte(b, fx0 + 0.05f, 2.45f, xm0 - 0.05f, 9.55f, dz0, dz1, pe, false, true);
        float hx = fx1 - 0.05f, hy = 3.3f;
        b.push().translate(hx, hy, dz1).rotateY(28).rotateZ(7).translate(-hx, -hy, -dz1);
        vertEau(b);
        porte(b, xm1 + 0.05f, 2.45f, hx, 9.55f, dz0, dz1, pe, true, true);
        b.pop();
        b.color(1);
        fiche(b, hx + 0.05f, 2.7f, dz1 + 0.12f, 1.3f);
        b.push().translate(hx + 0.25f, 8.4f, zf + 0.02f).rotateZ(-30);
        b.bevelBox(-0.25f, -0.6f, 0, 0.25f, 0.6f, 0.1f, 0.03f, BRASS);
        b.pop();
        boulon(b, hx + 0.45f, 7.5f, zf, 0.1f);
        // plateau de chêne et vaisselle
        float yp = yt + 0.75f;
        bois(b, x0 - 0.4f, yt, z0 + 0.05f, x1 + 0.4f, yp, zf + 0.6f, 0.2f, ch, NZ);
        pile(b, 3.6f, yp, 3.4f, 1.8f, 6, ASSIETTE_1);
        pile(b, 7.2f, yp, 3.0f, 1.4f, 3, ASSIETTE_2);
        bol(b, 10.4f, yp, 3.4f, 0);
        bol(b, 10.42f, yp + 0.3f, 3.42f, 1);
        tasse(b, 13.4f, yp, 4.2f, 20);
        tasse(b, 15.4f, yp, 3.0f, -40);
        pichetCouche(b, 19.4f, yp, 4.0f);
        decal(b, AUREOLE, 'y', 18.2f, yp, 6.6f, 3.4f, 2.0f, 15);
        drap(b, torchonBuffet(yp, zf + 0.6f), TORCHON);
        // usure : croix de fouille, boue, assiette cassée au sol
        decal(b, CROIX, 'z', 3.6f, 11.7f, zf - 0.02f, 2.3f, 2.3f, 8);
        boue(b, 'z', 6.0f, yb, rz1, 8.4f, 1.1f, 1);
        eclats(b, 7.6f, 11.4f, 1.8f, 7, ASSIETTE_1, 5);
        demiAssiette(b, 4.6f, 11.0f, 30, ASSIETTE_1);
    }

    /** Côtés à cadre et panneau d'un meuble (montants d'angle de 1,3 de profondeur). */
    static void cotes(MeshBuilder b, float x0, float x1, float z0, float zf, float yb, float yp0, float yp1, float yt, Mat m) {
        for (int c = 0; c < 2; c++) {
            b.push();
            if (c == 0) b.translate(x0 + 0.1f, 0, z0 + 1.25f).rotateY(-90);
            else b.translate(x1 - 0.1f, 0, zf - 1.25f).rotateY(90);
            float L = zf - z0 - 2.5f;
            bois(b, -0.05f, yp1, -1.0f, L + 0.05f, yt, -0.05f, 0.12f, m);
            bois(b, -0.05f, yb, -1.0f, L + 0.05f, yp0, -0.05f, 0.12f, m);
            panneau(b, 0, yp0, L, yp1, -0.05f, m);
            b.pop();
        }
    }

    /** Moitié d'assiette cassée posée au sol. */
    static void demiAssiette(MeshBuilder b, float x, float z, float ang, Mat m) {
        float[][] o = new float[9][];
        for (int k = 0; k < 9; k++) {
            double a = Math.PI * k / 8 + 0.2;
            o[k] = new float[]{(float) Math.cos(a) * 1.7f, (float) Math.sin(a) * 1.7f - 0.4f};
        }
        b.push().translate(x, 0.11f, z).rotateY(ang).rotateX(-90);
        b.slab(o, 0.2f, 0.04f, true, m, WHITE, WHITE);
        b.pop();
    }

    /**
     * Tiroir à façade moulurée (boutons, entrée de laiton) ; sortie > 0 : tiré, caisson et couverts visibles.
     * La couleur courante (peinture) s'applique à la façade.
     */
    static void tiroir(MeshBuilder b, float x0, float y0, float x1, float y1, float zf, float sortie, Mat m, boolean boutons) {
        float s = sortie, zb = zf - 0.6f, xm = (x0 + x1) / 2, ym = (y0 + y1) / 2;
        bois(b, x0, y0, zb + s, x1, y1, zf + s, 0.14f, m);
        bois(b, x0 + 0.45f, y0 + 0.42f, zf - 0.2f + s, x1 - 0.45f, y1 - 0.42f, zf + 0.1f + s, 0.16f, m);
        b.color(1);
        if (boutons) {
            bouton(b, x0 + (x1 - x0) * 0.22f, ym, zf + 0.1f + s, 0.34f, BRASS);
            bouton(b, x0 + (x1 - x0) * 0.78f, ym, zf + 0.1f + s, 0.34f, BRASS);
        }
        serrure(b, xm, ym, zf + 0.1f + s);
        if (s > 0) {
            float zr = zb + s - 6.2f;
            b.color(0.85f, 0.8f, 0.72f);
            bois(b, x0 + 0.2f, y0 + 0.1f, zr, x0 + 0.5f, y1 - 0.6f, zb + s + 0.03f, 0.05f, CHENE);
            bois(b, x1 - 0.5f, y0 + 0.1f, zr, x1 - 0.2f, y1 - 0.6f, zb + s + 0.03f, 0.05f, CHENE);
            bois(b, x0 + 0.48f, y0 + 0.1f, zr, x1 - 0.48f, y0 + 0.36f, zb + s + 0.03f, 0.04f, CHENE);
            b.color(1);
            for (int k = 0; k < 3; k++) {
                b.push().translate(x0 + 2.2f + k * 1.1f, y0 + 0.4f, zb + s - 1.6f - k * 0.3f).rotateY(70 + k * 12);
                b.bevelBox(-1.3f, 0, -0.1f, 0.6f, 0.08f, 0.1f, 0.03f, CHROME_PIQUE);
                b.push().translate(0.9f, 0.04f, 0).scale(1.6f, 0.4f, 1);
                b.sphereSection(0.38f, 8, 3, 0, 90, CHROME_PIQUE);
                b.pop();
                b.pop();
            }
            b.push().translate(x1 - 2.6f, y0 + 0.36f, zb + s - 2.4f).scale(1.6f, 0.55f, 1.3f);
            b.sphereSection(1.0f, 10, 4, 0, 90, TORCHON);
            b.pop();
        }
    }

    /** Porte à cadre et panneau (bouton et entrée côté libre), couleur courante pour le bois. */
    static void porte(MeshBuilder b, float x0, float y0, float x1, float y1, float zb, float zf, Mat m, boolean bordGauche, boolean bouton) {
        float[] c = {1, 1, 1};
        cadre(b, x0, y0, x1, y1, zb, zf, 1.1f, 1.0f, 1.0f, m);
        panneau(b, x0 + 1.1f, y0 + 1.0f, x1 - 1.1f, y1 - 1.0f, zf, m);
        b.color(1);
        float xk = bordGauche ? x0 + 0.55f : x1 - 0.55f;
        if (bouton) bouton(b, xk, (y0 + y1) / 2 + 0.6f, zf, 0.3f, BRASS);
        else boulon(b, xk, (y0 + y1) / 2 + 0.6f, zf, 0.09f);
        serrure(b, xk, (y0 + y1) / 2 - 0.6f, zf);
    }

    /** Bol en faïence (n = 0 bleu pâle, 1 crème) posé en (x, y, z). */
    static void bol(MeshBuilder b, float x, float y, float z, int n) {
        if (n == 0) b.color(0.68f, 0.76f, 0.88f);
        else b.color(0.92f, 0.86f, 0.74f);
        b.push().translate(x, y, z);
        b.lathe(new float[][]{{0.55f, 0}, {0.85f, 0.12f}, {1.2f, 0.62f}, {1.28f, 0.78f}}, 12, 0, 360, EMAIL, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.2f, 0.78f}, {1.1f, 0.6f}, {0.62f, 0.19f}, {0.01f, 0.19f}}, 12, 0, 360, EMAIL, 1, 1, 0, 0, 0, true);
        b.pop();
        b.color(1);
    }

    /** Tasse et soucoupe (anse tournée de ang degrés). */
    static void tasse(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y, z).rotateY(ang);
        assiette(b, 0.95f, ASSIETTE_1);
        b.translate(0, 0.2f, 0);
        tasseSeule(b);
        b.pop();
    }

    static void tasseSeule(MeshBuilder b) {
        b.lathe(new float[][]{{0.3f, 0}, {0.42f, 0.08f}, {0.56f, 0.62f}, {0.6f, 0.78f}}, 10, 0, 360, WHITE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.54f, 0.78f}, {0.5f, 0.6f}, {0.36f, 0.18f}, {0.01f, 0.18f}}, 10, 0, 360, INTERIEUR_SALE, 1, 1, 0, 0, 0, true);
        b.tube(new float[][]{p(0.5f, 0.62f, 0), p(0.85f, 0.6f, 0), p(0.88f, 0.3f, 0), p(0.5f, 0.2f, 0)}, fill(4, 0.07f), 5, WHITE, false, false, 0, false);
    }

    /** Pichet de grès (base en y = 0 local, axe +Y), anse vers +Z local. */
    static void pichet(MeshBuilder b) {
        b.color(0.78f, 0.62f, 0.44f);
        b.lathe(new float[][]{{0.01f, 0}, {0.7f, 0}, {0.85f, 0.25f}, {0.98f, 0.9f}, {0.94f, 1.6f}, {0.72f, 2.2f}, {0.7f, 2.45f}, {0.8f, 2.62f}}, 12, 0, 360, EMAIL, 1, 1, 0, 0, 0, false);
        b.push().translate(0, 2.56f, 0);
        b.disc(0.75f, 0, 12, INTERIEUR_SALE, true);
        b.pop();
        b.tube(new float[][]{p(0, 2.2f, 0.72f), p(0, 2.3f, 1.2f), p(0, 1.6f, 1.35f), p(0, 1.0f, 0.95f)}, fill(4, 0.13f), 6, EMAIL, true, true, 0, false);
        b.color(1);
    }

    /** Pichet renversé sur le flanc (anse sur le côté). */
    static void pichetCouche(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y + 0.98f, z).rotateY(-28).rotateZ(-90).translate(0, -1.3f, 0).rotateY(90);
        pichet(b);
        b.pop();
    }

    /** Torchon posé sur le plateau qui retombe devant le tiroir gauche. */
    static float[][][] torchonBuffet(float yp, float zb) {
        float[][] yz = {{yp + 0.04f, 6.0f}, {yp + 0.04f, 7.6f}, {yp + 0.04f, zb - 0.3f}, {yp - 0.12f, zb + 0.12f}, {yp - 0.6f, zb + 0.2f},
                {yp - 1.4f, zb + 0.24f}, {yp - 2.2f, zb + 0.22f}, {yp - 2.9f, zb + 0.3f}};
        int ni = yz.length, nj = 5;
        float[][][] P = new float[ni][nj][];
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                float u = j / (float) (nj - 1);
                float x = 5.2f + u * 2.6f + (i > 3 ? (i - 3) * 0.12f : 0);
                float dz = i > 3 ? (float) Math.sin(u * Math.PI * 2 + i) * 0.1f : 0;
                float dy = i > 3 ? -(float) Math.sin(u * Math.PI) * 0.25f * (i - 3) / 4f : 0;
                P[i][j] = new float[]{x, yz[i][0] + dy, yz[i][1] + dz};
            }
        }
        return P;
    }

    // =================================================================== vaisselier
    /**
     * Vaisselier de chêne (95 x 50 x 1,9 m) : bas à deux portes et deux tiroirs (un bouton arraché, un tiroir
     * entrouvert), haut à trois étagères à fond bleu passé et barres d'appui ; assiettes debout (une manque, une a
     * glissé de travers), tasses aux crochets, pichet, moulin à café ; assiettes cassées au sol.
     */
    static void vaisselier(MeshBuilder b) {
        Mat bo = CHENE;
        float x0 = 0.5f, x1 = 15.5f, z0 = 0.4f, zf = 8.0f, yb = 0.9f, yt = 13.0f;
        bois(b, x0 - 0.05f, 0, zf - 1.2f, x0 + 1.1f, yt, zf, 0.14f, bo);
        bois(b, x1 - 1.1f, 0, zf - 1.2f, x1 + 0.05f, yt, zf, 0.14f, bo);
        bois(b, x0 - 0.05f, 0, z0, x0 + 1.1f, yt, z0 + 1.2f, 0.14f, bo, NZ);
        bois(b, x1 - 1.1f, 0, z0, x1 + 0.05f, yt, z0 + 1.2f, 0.14f, bo, NZ);
        float fx0 = x0 + 1.05f, fx1 = x1 - 1.05f, rz0 = zf - 0.95f, rz1 = zf - 0.08f;
        bois(b, fx0, yb, rz0, fx1, 2.0f, rz1, 0.12f, bo);
        bois(b, fx0, 9.4f, rz0, fx1, 9.9f, rz1, 0.1f, bo);
        bois(b, fx0, 12.5f, rz0, fx1, yt, rz1, 0.1f, bo);
        bois(b, 7.45f, 1.95f, rz0 + 0.05f, 8.55f, 12.55f, rz1 + 0.06f, 0.12f, bo);
        cotes(b, x0, x1, z0, zf, yb, 2.0f, 11.8f, yt, bo);
        tiroir(b, fx0 + 0.05f, 9.95f, 7.4f, 12.45f, zf - 0.12f, 0, bo, true);
        tiroir(b, 8.6f, 9.95f, fx1 - 0.05f, 12.45f, zf - 0.12f, 1.3f, bo, true);
        float dz0 = zf - 0.9f, dz1 = zf - 0.16f;
        porte(b, fx0 + 0.05f, 2.05f, 7.4f, 9.35f, dz0, dz1, bo, false, false);
        porte(b, 8.6f, 2.05f, fx1 - 0.05f, 9.35f, dz0, dz1, bo, true, true);
        bois(b, x0 - 0.3f, yt, z0 + 0.05f, x1 + 0.3f, yt + 0.6f, zf + 0.4f, 0.18f, bo, NZ);
        // haut : fond de planches peintes bleu passé, joues, trois étagères à barre
        float yr = yt + 0.6f, zr0 = 0.4f, zr1 = 3.0f, zb = zr0 + 0.45f;
        b.color(0.6f, 0.68f, 0.78f);
        for (int k = 0; k < 4; k++) bois(b, 1.25f + k * 3.38f, yr, zr0 + 0.05f, 1.25f + (k + 1) * 3.38f - 0.03f, 30.4f, zb, 0.05f, BOIS_PEINT, NZ);
        b.color(1);
        bois(b, 0.6f, yr, zr0, 1.3f, 30.4f, zr1 + 0.2f, 0.12f, bo, NZ);
        bois(b, 14.7f, yr, zr0, 15.4f, 30.4f, zr1 + 0.2f, 0.12f, bo, NZ);
        float[] ys = {17.6f, 21.9f, 26.2f};
        for (float y : ys) {
            bois(b, 1.25f, y, zb - 0.02f, 14.75f, y + 0.4f, zr1, 0.08f, bo);
            bois(b, 1.25f, y + 1.0f, zr1 - 0.55f, 14.75f, y + 1.35f, zr1 - 0.2f, 0.08f, bo);
        }
        bois(b, 0.3f, 30.4f, zr0 + 0.05f, 15.7f, 31.0f, zr1 + 0.6f, 0.15f, bo, NZ);
        bois(b, 0.55f, 29.7f, zr1 + 0.05f, 15.45f, 30.42f, zr1 + 0.35f, 0.1f, bo);
        // assiettes debout
        float[] xs = {2.9f, 6.3f, 9.7f, 13.1f};
        for (int e = 0; e < 3; e++) {
            float y = ys[e] + 0.4f;
            for (int k = 0; k < 4; k++) {
                if (e == 0 && k == 2) continue;
                if (e == 2 && k == 3) continue;
                Mat m = (e + k) % 2 == 0 ? ASSIETTE_1 : ASSIETTE_2;
                float rz = e == 1 && k == 1 ? -16 : 0;
                assietteDebout(b, xs[k], y, zb, 1.62f, 12, rz, m);
            }
        }
        b.push().translate(13.1f, ys[2] + 0.4f, 1.9f);
        pichet(b);
        b.pop();
        // tasses aux crochets sous la première étagère, moulin à café et bols sur le plateau
        for (int k = 0; k < 4; k++) {
            float x = 2.8f + k * 3.3f;
            b.tube(new float[][]{p(x, ys[0] + 0.02f, 2.2f), p(x, ys[0] - 0.5f, 2.2f), p(x, ys[0] - 0.7f, 2.45f)}, fill(3, 0.06f), 4, METAL_NU, false, false, 0, false);
            if (k == 1) continue;
            b.push().translate(x + 0.45f, ys[0] - 1.6f, 2.45f).rotateZ(90 + (k == 3 ? 15 : 0));
            tasseSeule(b);
            b.pop();
        }
        moulinCafe(b, 4.2f, yr, 4.6f);
        bol(b, 10.6f, yr, 5.0f, 1);
        // usure : assiettes cassées, toile, boue
        eclats(b, 6.4f, 11.0f, 2.0f, 9, ASSIETTE_2, 11);
        eclats(b, 10.2f, 10.0f, 1.2f, 5, ASSIETTE_1, 12);
        demiAssiette(b, 8.4f, 11.6f, -50, ASSIETTE_2);
        b.push().translate(12.4f, 0.02f, 11.2f).rotateY(20);
        assiette(b, 1.62f, ASSIETTE_1);
        b.pop();
        toile(b, 1.32f, 30.38f, 2.6f, 2.4f, 2.0f, 0, 0);
        toile(b, 1.32f, ys[1] - 0.02f, 2.2f, 2.2f, 1.6f, 0, 0);
        decal(b, AUREOLE, 'z', 4.2f, 5.6f, dz1 - 0.02f, 2.6f, 2.0f);
        boue(b, 'z', 8.0f, yb, rz1, 11.0f, 1.0f, 0);
    }

    /** Assiette debout appuyée au fond (inclinée de 12°) ; rz : glissée de travers. */
    static void assietteDebout(MeshBuilder b, float x, float y, float zb, float r, float pente, float rz, Mat m) {
        double a = Math.toRadians(90 - pente);
        float cy = y + (float) Math.sin(a) * r + 0.04f, cz = zb + (float) Math.cos(a) * r + 0.06f;
        b.push().translate(x, cy, cz).rotateZ(rz).rotateX(90 - pente);
        assiette(b, r, m);
        b.pop();
    }

    /** Moulin à café de bois (manivelle, tiroir) posé en (x, y, z). */
    static void moulinCafe(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z).rotateY(-18);
        b.bevelBox(-0.75f, 0, -0.75f, 0.75f, 1.7f, 0.75f, 0.08f, CHENE);
        b.bevelBox(-0.55f, 0.3f, 0.7f, 0.55f, 0.75f, 0.82f, 0.04f, CHENE);
        bouton(b, 0, 0.52f, 0.82f, 0.12f, BRASS);
        b.cylinder(0.55f, 1.7f, 2.1f, 10, AtlasUsure.ROUILLE, null, AtlasUsure.METAL_NU);
        b.cylinder(0.1f, 2.1f, 2.5f, 6, AtlasUsure.METAL_NU, null, null);
        b.tube(new float[][]{p(0, 2.45f, 0), p(0.6f, 2.5f, 0.2f), p(1.1f, 2.5f, 0.35f)}, fill(3, 0.06f), 4, AtlasUsure.METAL_NU, false, false, 0, false);
        b.push().translate(1.1f, 2.5f, 0.35f);
        b.sphere(0.16f, 6, 4, CHENE);
        b.pop();
        b.pop();
    }

    // =================================================================== coffre
    /**
     * Coffre de bout de lit en chêne (85 x 45 x 48 cm) à panneaux : couvercle entrouvert, retenu par une couverture
     * écossaise qui dépasse et pend devant ; poignées de fer, moraillon, pieds à patins ; lettres éparpillées au sol.
     */
    static void coffreBois(MeshBuilder b) {
        Mat bo = CHENE;
        float x0 = 1.2f, x1 = 14.8f, z0 = 0.8f, zf = 7.8f, yb = 0.9f, yt = 7.4f;
        for (float[] q : new float[][]{{x0 + 0.1f, zf - 1.2f}, {x1 - 1.3f, zf - 1.2f}, {x0 + 0.1f, z0 + 0.1f}, {x1 - 1.3f, z0 + 0.1f}}) {
            b.bevelBox(q[0], 0, q[1], q[0] + 1.2f, yb + 0.05f, q[1] + 1.1f, 0.15f, bo);
        }
        bois(b, x0 - 0.05f, yb, zf - 1.0f, x0 + 1.0f, yt, zf, 0.14f, bo);
        bois(b, x1 - 1.0f, yb, zf - 1.0f, x1 + 0.05f, yt, zf, 0.14f, bo);
        bois(b, x0 - 0.05f, yb, z0, x0 + 1.0f, yt, z0 + 1.0f, 0.14f, bo, NZ);
        bois(b, x1 - 1.0f, yb, z0, x1 + 0.05f, yt, z0 + 1.0f, 0.14f, bo, NZ);
        float rz0 = zf - 0.85f, rz1 = zf - 0.08f;
        bois(b, x0 + 0.95f, yb, rz0, x1 - 0.95f, yb + 1.1f, rz1, 0.12f, bo);
        bois(b, x0 + 0.95f, yt - 1.0f, rz0, x1 - 0.95f, yt, rz1, 0.12f, bo);
        bois(b, 7.5f, yb + 1.05f, rz0 + 0.05f, 8.5f, yt - 0.95f, rz1 + 0.05f, 0.12f, bo);
        panneau(b, x0 + 1.0f, yb + 1.1f, 7.5f, yt - 1.0f, rz1, bo);
        panneau(b, 8.5f, yb + 1.1f, x1 - 1.0f, yt - 1.0f, rz1, bo);
        cotes(b, x0, x1, z0 - 0.25f, zf + 0.25f, yb, yb + 1.1f, yt - 1.0f, yt, bo);
        // intérieur sombre (vu par l'entrebâillement)
        b.color(0.5f, 0.46f, 0.42f);
        bois(b, x0 + 0.9f, yb, z0 + 0.9f, x1 - 0.9f, yb + 0.6f, zf - 0.9f, 0.04f, bo);
        b.color(1);
        // poignées de fer forgé et moraillon
        for (int c = -1; c <= 1; c += 2) {
            float x = c < 0 ? x0 + 0.28f : x1 - 0.28f;
            for (float z : new float[]{3.0f, 5.6f}) b.bevelBox(Math.min(x - c * 0.04f, x + c * 0.25f), 4.6f, z - 0.35f, Math.max(x - c * 0.04f, x + c * 0.25f), 5.6f, z + 0.35f, 0.06f, AtlasCivil.DARK);
            b.tube(new float[][]{p(x + c * 0.2f, 5.0f, 3.0f), p(x + c * 0.55f, 4.0f, 3.2f), p(x + c * 0.55f, 4.0f, 5.4f), p(x + c * 0.2f, 5.0f, 5.6f)}, fill(4, 0.14f), 6, ROUILLE, false, false, 0, false);
            coulure(b, c < 0 ? 'x' : 'X', x, 4.6f, 3.0f, 0.9f, c < 0 ? 0 : 2);
        }
        b.bevelBox(7.3f, yt - 1.6f, rz1 + 0.04f, 8.7f, yt - 0.1f, rz1 + 0.2f, 0.06f, ROUILLE);
        b.box(7.9f, yt - 1.3f, rz1 + 0.19f, 8.1f, yt - 0.7f, rz1 + 0.26f, AtlasCivil.DARK);
        // couvercle entrouvert (gonds à l'arrière), retenu par la couverture
        float ang = -8;
        b.push().translate(0, yt, z0 + 0.1f).rotateX(ang).translate(0, -yt, -(z0 + 0.1f));
        bois(b, x0 - 0.3f, yt + 0.02f, z0 - 0.05f, x1 + 0.3f, yt + 0.75f, zf + 0.35f, 0.2f, bo);
        bois(b, x0 + 0.2f, yt + 0.72f, z0 + 0.4f, x1 - 0.2f, yt + 1.0f, zf - 0.1f, 0.12f, bo);
        b.bevelBox(7.55f, yt - 0.9f, zf + 0.3f, 8.45f, yt + 0.6f, zf + 0.45f, 0.05f, ROUILLE);
        b.pop();
        drap(b, couvertureCoffre(yt, zf), PLAID);
        // usure : lettres éparpillées, auréole, boue, toile sous le coffre
        epars(b, 3.0f, 9.2f, 13.0f, 13.6f, 7, PAPIER, 1.6f, 21);
        epars(b, 9.0f, 10.0f, 12.0f, 13.0f, 2, JOURNAL, 2.2f, 22);
        decal(b, AUREOLE, 'z', 11.6f, 4.0f, rz1 - 0.1f, 2.2f, 1.8f);
        boue(b, 'z', 8.0f, yb, rz1, 10.0f, 1.0f, 1);
        toile(b, x0 + 1.35f, yb - 0.02f, zf - 0.5f, 2.0f, 0.85f, 0, 0);
    }

    /** Couverture coincée sous le couvercle : elle sort de la caisse et pend devant les panneaux. */
    static float[][][] couvertureCoffre(float yt, float zf) {
        float[][] yz = {{yt - 1.2f, 4.0f}, {yt - 0.4f, 5.5f}, {yt + 0.2f, 6.8f}, {yt + 0.42f, zf - 0.2f}, {yt + 0.3f, zf + 0.4f}, {yt - 0.4f, zf + 0.7f},
                {yt - 1.6f, zf + 0.78f}, {yt - 2.8f, zf + 0.8f}, {yt - 3.9f, zf + 0.88f}};
        int ni = yz.length, nj = 8;
        float[][][] P = new float[ni][nj][];
        for (int i = 0; i < ni; i++) {
            float pend = i >= 5 ? (i - 4) / 4f : 0;
            for (int j = 0; j < nj; j++) {
                float u = j / (float) (nj - 1);
                float xa = 4.4f - pend * 0.3f, xc = 10.6f + pend * 0.2f;
                float x = xa + (xc - xa) * u;
                float pli = (float) Math.sin(u * Math.PI * 3 + i * 0.5f) * (0.08f + 0.22f * pend);
                float dy = i >= 5 ? -(float) Math.sin(u * Math.PI) * 0.6f * pend : (i == 3 || i == 4 ? (float) Math.sin(u * Math.PI * 4) * 0.08f : 0);
                P[i][j] = new float[]{x, yz[i][0] + dy, yz[i][1] + pli};
            }
        }
        return P;
    }

    // =================================================================== machine à coudre
    /**
     * Machine à coudre ancienne (tête noire à décor doré « AURORE ») sur son meuble à pédale : piétement de fonte
     * ajouré, volant, courroie, pédale ; caisson de tiroirs à gauche (un tiroir ouvert sur des bobines) ; tissu sous
     * le pied, ciseaux, bobine tombée qui déroule son fil, toile d'araignée dans le piétement.
     */
    static void machineCoudre(MeshBuilder b) {
        float x0 = 1.0f, x1 = 15.0f, z0 = 0.8f, z1 = 7.8f, yt = 11.2f;
        // piétement : deux montants ajourés (double face), traverse, pédale, volant
        for (float x : new float[]{5.8f, 13.2f}) {
            b.face(FONTE_AJOUREE, v(1, 0, 0), p(x + 0.12f, 0, 7.4f), p(x + 0.12f, 0, 1.2f), p(x + 0.12f, yt - 0.05f, 1.2f), p(x + 0.12f, yt - 0.05f, 7.4f));
            b.face(FONTE_AJOUREE, v(-1, 0, 0), p(x - 0.12f, 0, 1.2f), p(x - 0.12f, 0, 7.4f), p(x - 0.12f, yt - 0.05f, 7.4f), p(x - 0.12f, yt - 0.05f, 1.2f));
        }
        b.tube(new float[][]{p(5.8f, 1.65f, 4.3f), p(13.2f, 1.65f, 4.3f)}, fill(2, 0.22f), 8, FONTE, false, false, 0, false);
        b.push().translate(9.5f, 1.2f, 5.0f).rotateX(-12);
        b.bevelBox(-3.0f, 0, -1.6f, 3.0f, 0.28f, 1.6f, 0.1f, FONTE);
        for (float x = -2.4f; x < 2.5f; x += 0.8f) b.box(x, 0.27f, -1.2f, x + 0.35f, 0.32f, 1.2f, ROUILLE);
        b.pop();
        b.push().translate(12.6f, 6.0f, 4.3f).rotateZ(90);
        b.torus(2.3f, 0.2f, 18, 5, 0, 360, FONTE);
        b.cylinder(0.35f, -0.3f, 0.3f, 8, FONTE, FONTE, FONTE);
        b.pop();
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(k * 90 + 20);
            b.tube(new float[][]{p(12.6f, 6.0f, 4.3f), p(12.6f, 6.0f + 2.15f * (float) Math.sin(a), 4.3f + 2.15f * (float) Math.cos(a))}, fill(2, 0.12f), 5, FONTE, false, false, 0, false);
        }
        b.tube(new float[][]{p(12.45f, 7.0f, 5.2f), p(11.2f, 1.6f, 5.6f)}, fill(2, 0.1f), 5, FONTE, false, false, 0, false);
        b.tube(new float[][]{p(12.4f, 8.3f, 4.3f), p(12.0f, yt + 0.6f, 3.8f), p(11.9f, yt + 2.9f, 3.9f)}, fill(3, 0.06f), 4, CUIR_FAUVE, false, false, 0, false);
        // plateau et caisson de tiroirs
        bois(b, x0, yt, z0, x1, yt + 0.5f, z1, 0.16f, CHENE, NZ);
        bois(b, 1.4f, 5.6f, 1.1f, 5.2f, yt + 0.02f, 7.3f, 0.1f, CHENE, NZ);
        for (int k = 0; k < 3; k++) {
            float y0 = 5.85f + k * 1.8f;
            float s = k == 1 ? 1.8f : 0;
            b.color(0.9f, 0.85f, 0.8f);
            bois(b, 1.6f, y0, 7.25f + s, 5.0f, y0 + 1.6f, 7.65f + s, 0.1f, CHENE);
            b.color(1);
            if (k != 2) bouton(b, 3.3f, y0 + 0.8f, 7.65f + s, 0.22f, BRASS);
            else boulon(b, 3.3f, y0 + 0.8f, 7.65f, 0.07f);
            if (s > 0) {
                bois(b, 1.7f, y0 + 0.05f, 7.25f + s - 3.5f, 4.9f, y0 + 0.3f, 7.27f + s, 0.04f, CHENE);
                bois(b, 1.62f, y0 + 0.12f, 7.31f + s - 3.5f, 1.87f, y0 + 1.3f, 7.22f + s, 0.03f, CHENE);
                bois(b, 4.73f, y0 + 0.12f, 7.31f + s - 3.5f, 4.98f, y0 + 1.3f, 7.22f + s, 0.03f, CHENE);
                for (int q = 0; q < 3; q++) bobine(b, 2.5f + q * 0.9f, y0 + 0.3f, 7.25f + s - 1.0f - (q % 2) * 0.8f, q);
            }
        }
        // tête de machine
        float yb = yt + 0.5f;
        b.bevelBox(4.8f, yb - 0.02f, 2.4f, 11.8f, yb + 0.5f, 5.8f, 0.15f, FONTE);
        b.bevelBox(9.9f, yb + 0.45f, 3.2f, 11.4f, yb + 3.4f, 5.0f, 0.5f, FONTE);
        b.bevelBox(5.8f, yb + 2.5f, 3.3f, 11.2f, yb + 3.8f, 4.9f, 0.3f, FONTE);
        b.bevelBox(5.2f, yb + 1.6f, 3.2f, 6.6f, yb + 3.9f, 5.0f, 0.4f, FONTE);
        decal(b, DECOR_MACHINE, 'z', 8.6f, yb + 3.15f, 4.9f, 3.2f, 0.66f);
        b.push().translate(5.75f, yb + 0.5f, 4.6f);
        b.cylinder(0.08f, 0, 1.15f, 5, CHROME_PIQUE, null, null);
        b.pop();
        b.bevelBox(5.45f, yb + 0.6f, 4.3f, 6.1f, yb + 0.8f, 5.1f, 0.05f, CHROME_PIQUE);
        b.push().translate(11.45f, yb + 3.0f, 4.1f).rotateZ(-90);
        b.cylinder(1.05f, 0, 0.45f, 14, FONTE, CHROME_PIQUE, CHROME_PIQUE);
        b.pop();
        b.push().translate(8.6f, yb + 3.78f, 4.1f);
        b.cylinder(0.07f, 0, 0.9f, 5, CHROME_PIQUE, null, null);
        b.cylinder(0.3f, 0.15f, 0.75f, 8, PAINT_RED, CHROME_PIQUE, CHROME_PIQUE);
        b.pop();
        // tissu sous le pied, ciseaux, pelote, bobine tombée et son fil
        b.push().translate(6.4f, yb + 0.52f, 5.0f).rotateY(14);
        b.bevelBox(-2.2f, 0, -1.3f, 1.6f, 0.1f, 1.6f, 0.04f, TORCHON);
        b.pop();
        b.push().translate(12.8f, yt + 0.5f, 6.3f).rotateY(-35);
        b.bevelBox(-1.0f, 0, -0.08f, 0.6f, 0.08f, 0.08f, 0.02f, CHROME_PIQUE);
        b.push().rotateY(14);
        b.bevelBox(-1.0f, 0.06f, -0.08f, 0.6f, 0.14f, 0.08f, 0.02f, CHROME_PIQUE);
        b.pop();
        b.push().translate(0.85f, 0.04f, 0.2f);
        b.torus(0.25f, 0.06f, 8, 3, 0, 360, AtlasCivil.DARK);
        b.translate(0, 0.04f, -0.5f);
        b.torus(0.25f, 0.06f, 8, 3, 0, 360, AtlasCivil.DARK);
        b.pop();
        b.pop();
        b.push().translate(2.8f, yt + 0.5f, 3.4f);
        b.cylinder(0.55f, 0, 0.3f, 10, CHENE, null, CHENE);
        b.translate(0, 0.3f, 0).scale(1, 0.75f, 1);
        b.sphereSection(0.6f, 10, 4, 0, 90, PLAID);
        b.pop();
        b.push().translate(9.2f, 0.34f, 10.6f).rotateY(30).rotateZ(90);
        bobine(b, 0, -0.35f, 0, 0);
        b.pop();
        b.tube(new float[][]{p(9.2f, 0.4f, 10.6f), p(10.4f, 0.05f, 9.6f), p(11.0f, 0.05f, 8.2f), p(11.6f, 0.2f, 7.2f), p(12.1f, 2.0f, 6.6f), p(12.6f, yt - 0.2f, 6.9f)},
                fill(6, 0.035f), 3, PAINT_RED, false, false, 0, false);
        // usure
        toile(b, 5.95f, 4.6f, 3.3f, 3.0f, 2.4f, 0, 0);
        toile(b, 13.0f, yt - 0.02f, 2.0f, 2.4f, 2.0f, 180, 0);
        epars(b, 4.0f, 8.4f, 9.0f, 11.6f, 3, TORCHON, 1.0f, 33);
    }

    /** Bobine de fil (bois et fil coloré), axe +Y local, posée en (x, y, z). */
    static void bobine(MeshBuilder b, float x, float y, float z, int c) {
        Mat[] fils = {PAINT_RED, AtlasCivil.POLICE_BLUE, AtlasCivil.YELLOW};
        b.push().translate(x, y, z);
        b.cylinder(0.32f, 0, 0.1f, 8, CHENE, CHENE, CHENE);
        b.cylinder(0.26f, 0.1f, 0.6f, 8, fils[c % 3], null, null);
        b.cylinder(0.32f, 0.6f, 0.7f, 8, CHENE, null, CHENE);
        b.pop();
    }

    // =================================================================== valises
    /**
     * Pile de valises anciennes contre le mur : grande valise de cuir fauve, valise de toile rayée, petite valise de
     * cuir sombre, carton à chapeau ; une quatrième debout, appuyée contre la pile. Sangles et boucles, coins de laiton,
     * étiquettes d'hôtel délavées, poignée cassée, poussière.
     */
    static void valisesAnciennes(MeshBuilder b) {
        valise(b, 6.4f, 0, 4.0f, 11.2f, 6.6f, 3.2f, 3, CUIR_FAUVE, CUIR_USE, 0, true);
        valise(b, 6.0f, 3.22f, 3.9f, 9.6f, 5.8f, 2.9f, -6, TOILE_VALISE, CUIR_USE, 1, false);
        valise(b, 6.6f, 6.14f, 3.6f, 7.4f, 4.8f, 2.4f, 9, CUIR_USE, CUIR_USE, 2, true);
        // carton à chapeau
        b.push().translate(6.2f, 8.56f, 3.4f).rotateY(20);
        b.cylinder(2.1f, 0, 2.3f, 16, TOILE_VALISE, null, null);
        b.cylinder(2.2f, 1.75f, 2.55f, 16, CUIR_FAUVE, null, CUIR_FAUVE);
        b.tube(new float[][]{p(-1.4f, 2.55f, 0), p(-0.6f, 3.3f, 0.2f), p(0.6f, 3.3f, 0.2f), p(1.4f, 2.55f, 0)}, fill(4, 0.08f), 5, AtlasCivil.TAPE, false, false, 0, false);
        b.pop();
        // valise debout sur la tranche, penchée contre la pile (poignée vers l'avant)
        float L = 8.8f, W = 5.6f, H = 2.5f;
        b.push().translate(12.75f, 0.1f, 3.9f).rotateZ(10).translate(H / 2, 0, 0).rotateZ(90).translate(L / 2, 0, 0);
        valiseLocale(b, L, W, H, TOILE_VALISE, CUIR_FAUVE, 3, false, 0);
        b.pop();
        decal(b, AUREOLE, 'y', 9.0f, 3.22f + 2.9f, 6.2f, 2.4f, 1.6f, 30);
        toile(b, 6.2f, 11.1f, 0.06f, 1.5f, 2.4f, -90, 0);
    }

    /** Valise couchée (centre (cx, y, cz) au sol de la valise, L le long de X, W en profondeur, H en hauteur), tournée de ang degrés. */
    static void valise(MeshBuilder b, float cx, float y, float cz, float L, float W, float H, float ang, Mat cuir, Mat bord, int eti, boolean poignee) {
        b.push().translate(cx, y, cz).rotateY(ang).translate(0, H / 2, 0);
        valiseLocale(b, L, W, H, cuir, bord, eti, poignee, MeshBuilder.SKIP_NY);
        b.pop();
    }

    /** Valise centrée en 0 (L selon X, H selon Y, W selon Z), face avant (poignée, serrures) vers +Z. */
    static void valiseLocale(MeshBuilder b, float L, float W, float H, Mat cuir, Mat bord, int eti, boolean poignee, int skip) {
        float l = L / 2, h = H / 2, w = W / 2;
        b.bevelBox(-l, -h, -w, l, h, w, Math.min(0.4f, H * 0.18f), cuir, null, skip);
        b.bevelBox(-l - 0.08f, -0.18f, -w - 0.08f, l + 0.08f, 0.12f, w + 0.08f, 0.1f, bord);
        b.box(-l - 0.14f, -h + 0.05f, w - 0.5f, -l + 0.5f, h - 0.05f, w + 0.14f, BRASS);
        b.box(l - 0.5f, -h + 0.05f, w - 0.5f, l + 0.14f, h - 0.05f, w + 0.14f, BRASS);
        for (float x : new float[]{-l * 0.55f, l * 0.55f}) {
            b.box(x - 0.4f, -h - 0.04f, -w - 0.04f, x + 0.4f, h + 0.04f, w + 0.04f, bord);
            b.bevelBox(x - 0.5f, h - 0.9f, w + 0.02f, x + 0.5f, h - 0.3f, w + 0.14f, 0.05f, BRASS);
        }
        for (float x : new float[]{-l * 0.25f, l * 0.25f}) b.bevelBox(x - 0.35f, -0.35f, w + 0.06f, x + 0.35f, 0.35f, w + 0.2f, 0.06f, BRASS);
        b.bevelBox(-1.3f, 0.2f, w + 0.02f, -0.9f, 0.5f, w + 0.25f, 0.05f, BRASS);
        if (poignee) {
            b.bevelBox(0.9f, 0.2f, w + 0.02f, 1.3f, 0.5f, w + 0.25f, 0.05f, BRASS);
            b.tube(new float[][]{p(-1.1f, 0.4f, w + 0.2f), p(-0.8f, 0.6f, w + 0.75f), p(0.8f, 0.6f, w + 0.75f), p(1.1f, 0.4f, w + 0.2f)}, fill(4, 0.18f), 6, bord, false, false, 0, false);
        } else {
            // poignée cassée : un seul côté, qui pend
            b.tube(new float[][]{p(-1.1f, 0.4f, w + 0.2f), p(-0.85f, 0.15f, w + 0.6f), p(-0.6f, -0.5f, w + 0.55f)}, fill(3, 0.18f), 6, bord, false, true, 0, false);
        }
        Mat sticker = ETIQ_VALISE.sub((eti % 2) * 0.5f, (eti / 2) * 0.5f, (eti % 2) * 0.5f + 0.5f, (eti / 2) * 0.5f + 0.5f);
        float s = Math.min(3.0f, W * 0.55f);
        b.push().translate(-l * 0.12f, h, 0).rotateX(-90).rotateZ(eti * 37 % 30 - 15);
        b.decalZ(sticker, 0, 0, 0.11f, s, s);
        b.pop();
    }
}
