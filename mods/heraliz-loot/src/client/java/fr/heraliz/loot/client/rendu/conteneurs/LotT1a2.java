package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1aAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1aKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Lot T1a, commerces et rue : journaux, caddie, palette de marchandises, cagettes et casiers, vitrine réfrigérée,
 * distributeur, corbeille de rue, bac de recyclage, présentoir à journaux.
 */
final class LotT1a2 {
    private LotT1a2() {
    }

    // =================================================================== 6. paquets de journaux

    /** Ballot de journaux ficelé (L x W x H), posé en (x, y, z), tourné de ry ; une : la une sur le dessus. */
    static void ballot(MeshBuilder b, float x, float y, float z, float ry, float rx, float rz, float L, float W, float H, boolean une, int g) {
        b.push().translate(x, y, z).rotateY(ry).rotateX(rx).rotateZ(rz);
        float h1 = H * 0.52f;
        b.bevelBox(-L / 2, 0, -W / 2, L / 2, h1, W / 2, 0.1f, TRANCHE, new Mat[]{null, null, null, JOURNAL, null, null}, 0);
        b.push().translate(0, h1 + 0.02f, 0).rotateY((g % 5 - 2) * 1.2f);
        b.bevelBox(-L / 2, 0, -W / 2, L / 2, H - h1, W / 2, 0.12f, TRANCHE, new Mat[]{null, null, null, une ? UNE : JOURNAL, null, null}, 0);
        // ficelle en croix
        float yt = H - h1 + 0.02f, e = 0.09f;
        b.box(-L / 2 - e, 0.05f, -e, -L / 2 - 0.01f, yt, e, TAPE);
        b.box(L / 2 + 0.01f, 0.05f, -e, L / 2 + e, yt, e, TAPE);
        b.box(-e, 0.05f, -W / 2 - e, e, yt, -W / 2 - 0.01f, TAPE);
        b.box(-e, 0.05f, W / 2 + 0.01f, e, yt, W / 2 + e, TAPE);
        b.box(-L / 2 - e, yt, -e, L / 2 + e, yt + 0.1f, e, TAPE);
        b.box(-e, yt + 0.05f, -W / 2 - e, e, yt + 0.15f, W / 2 + e, TAPE);
        b.pop();
        b.box(-L / 2 - e, 0.05f, -e, -L / 2 - 0.01f, h1, e, TAPE);
        b.box(L / 2 + 0.01f, 0.05f, -e, L / 2 + e, h1, e, TAPE);
        b.box(-e, 0.05f, -W / 2 - e, e, h1, -W / 2 - 0.01f, TAPE);
        b.box(-e, 0.05f, W / 2 + 0.01f, e, h1, W / 2 + e, TAPE);
        b.pop();
    }

    /** Ballots de journaux jaunis, ficelés, empilés de travers contre le mur ; un tombé sur la tranche, un éventré. */
    static void paquetsJournaux(MeshBuilder b) {
        float L = 6.2f, W = 4.4f, H = 2.3f;
        ballot(b, 3.6f, 0, 2.8f, 4, 0, 0, L, W, H, false, 1);
        ballot(b, 10.2f, 0, 2.9f, -6, 0, 0, L, W, H, false, 2);
        ballot(b, 4.3f, H, 2.9f, 12, 0, 1.2f, L, W, H, true, 3);
        ballot(b, 10.6f, H, 3.0f, -15, 0, -1.0f, L, W, H, false, 4);
        ballot(b, 7.4f, 2 * H + 0.04f, 3.2f, 28, 0, 0, L, W, H, true, 5);
        // tombé sur la tranche devant, appuyé contre la pile
        ballot(b, 4.4f, W / 2 + 0.05f, 8.3f, -10, -90, 0, L, W, H, false, 6);
        // éventré au sol : ficelle cassée, feuilles glissées
        b.push().translate(11.6f, 0, 9.6f).rotateY(-24);
        b.bevelBox(-3.1f, 0, -2.2f, 3.1f, 1.0f, 2.2f, 0.1f, TRANCHE, new Mat[]{null, null, null, JOURNAL, null, null}, 0);
        b.pop();
        feuille(b, 11.0f, 1.0f, 9.8f, 6.0f, 4.2f, -12, 0.25f, UNE);
        feuille(b, 13.0f, 0, 13.6f, 5.0f, 3.6f, 20, 0.12f, JOURNAL);
        feuille(b, 7.8f, 0, 12.6f, 4.4f, 3.2f, -40, 0.18f, JOURNAL);
        b.tube(new float[][]{p(8.8f, 0.08f, 11.6f), p(9.6f, 0.08f, 13.0f), p(9.2f, 0.1f, 14.2f), p(10.4f, 0.08f, 14.9f)}, fill(4, 0.07f), 4, TAPE, false, false, 0, false);
        // usure : auréoles d'humidité, moisissure, papiers épars
        decalT(b, MOUSSE_VERTE, 3.6f, 0.7f, 2.8f + W / 2 + 0.02f, 4.6f, 1.4f, 4, 0, 0, 0.16f);
        decalT(b, MOISI.asFit().sub(0, 0, 0.6f, 0.4f), 10.2f, 0.6f, 2.9f + W / 2, 3.2f, 1.0f, -6, 0, 0, 0.17f);
        jonchee(b, 1.0f, 11.0f, 7.0f, 15.6f, 6, PAPIER, 1.2f, 41);
    }

    // =================================================================== 7. caddie renversé

    /** Petite roulette de caddie (roue, chape) d'axe vertical local, le bas de la roue en y = 0. */
    static void roulettePetite(MeshBuilder b, float x, float z, float r, float ang, boolean roue) {
        b.push().translate(x, 0, z).rotateY(ang);
        if (roue) {
            b.push().translate(0, r, 0).rotateZ(90);
            b.cylinder(r, -0.32f, 0.32f, 8, CAOUTCHOUC_USE, PLASTIC_GREY, PLASTIC_GREY);
            b.pop();
        }
        for (int s = -1; s <= 1; s += 2) b.box(s * 0.4f - 0.06f, r * 0.8f, -0.25f, s * 0.4f + 0.06f, r * 2 + 0.35f, 0.25f, CHROME_PIQUE);
        b.box(-0.46f, r * 2 + 0.35f, -0.4f, 0.46f, r * 2 + 0.55f, 0.4f, CHROME_PIQUE);
        b.pop();
    }

    static void tubeDroit(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, false, false, 0, false);
    }

    /** Caddie (95 x 55 x 100 cm) couché sur le flanc, roues contre le mur, panier ouvert vers l'avant ; sac renversé. */
    static void caddie(MeshBuilder b) {
        b.push().translate(0.9f, 4.55f, 0.1f).rotateX(90);
        // repère du caddie debout : x avant -> arrière (poignée), y haut, z largeur
        float yb = 8.0f, yh = 14.5f;
        float[] fb0 = {0.8f, 3.1f}, fb1 = {13.2f, 4.1f}, ft0 = {0, 3.4f}, ft1 = {13.6f, 4.35f};
        Mat gr = GRILLAGE.withWorld(4);
        float[][] B = {p(fb0[0], yb, -fb0[1]), p(fb1[0], yb, -fb1[1]), p(fb1[0], yb, fb1[1]), p(fb0[0], yb, fb0[1])};
        float[][] T = {p(ft0[0], yh, -ft0[1]), p(ft1[0], yh, -ft1[1]), p(ft1[0], yh, ft1[1]), p(ft0[0], yh, ft0[1])};
        doubleFace(b, gr, B[0], B[1], B[2], B[3]);
        for (int k = 0; k < 4; k++) {
            int q = (k + 1) % 4;
            doubleFace(b, gr, B[k], B[q], T[q], T[k]);
        }
        Mat ch = CHROME_PIQUE;
        float rt = 0.13f;
        for (int k = 0; k < 4; k++) {
            int q = (k + 1) % 4;
            tubeDroit(b, T[k], T[q], rt, ch);
            tubeDroit(b, B[k], B[q], rt * 0.8f, ch);
            tubeDroit(b, B[k], T[k], rt * 0.8f, ch);
        }
        // châssis : longerons, pieds arrière, plateau inférieur grillagé, roulettes (une arrachée)
        float yc = 2.3f;
        for (int s = -1; s <= 1; s += 2) {
            tubeDroit(b, p(0.6f, yc, s * 2.6f), p(13.4f, yc, s * 3.8f), 0.16f, ch);
            tubeDroit(b, p(13.2f, yc, s * 3.8f), p(13.2f, yb, s * 4.1f), 0.14f, ch);
            tubeDroit(b, p(1.2f, yc, s * 2.65f), p(1.0f, yb, s * 3.1f), 0.12f, ch);
        }
        doubleFace(b, gr, p(4.0f, yc + 0.25f, -2.9f), p(12.6f, yc + 0.25f, -3.7f), p(12.6f, yc + 0.25f, 3.7f), p(4.0f, yc + 0.25f, 2.9f));
        roulettePetite(b, 1.2f, -2.6f, 0.8f, 20, true);
        roulettePetite(b, 1.2f, 2.6f, 0.8f, -10, false);
        roulettePetite(b, 12.8f, -3.7f, 0.8f, 0, true);
        roulettePetite(b, 12.8f, 3.7f, 0.8f, 45, true);
        // poignée et siège enfant (plaque publicitaire)
        for (int s = -1; s <= 1; s += 2) tubeDroit(b, T[s < 0 ? 1 : 2], p(14.6f, 15.2f, s * 4.2f), 0.15f, ch);
        b.push().translate(14.6f, 15.2f, 0).rotateX(90);
        b.cylinder(0.15f, -4.3f, 4.3f, 6, ch, ch, ch);
        b.pop();
        b.push().translate(14.6f, 15.2f, 0).rotateX(90);
        b.cylinder(0.32f, -3.0f, 3.0f, 8, PAINT_RED, PAINT_RED, PAINT_RED);
        b.pop();
        b.push().translate(14.1f, 13.2f, 0).rotateZ(-8);
        b.bevelBox(-0.12f, -1.0f, -3.4f, 0.12f, 1.1f, 3.4f, 0.06f, PLASTIQUE_USE);
        b.pop();
        decalT(b, L_MARCHE, 14.24f, 13.2f, 0, 4.0f, 2.0f, 90, 0, 8, 0.05f);
        b.pop();
        // feuilles mortes et flaque dans le panier couché, sac de courses renversé devant l'ouverture
        jonchee(b, 2.0f, 9.0f, 13.0f, 14.5f, 7, FEUILLES, 1.3f, 51);
        mou(b, place(11.8f, 1.6f, 13.6f, -30, 0, -95), new float[][]{{0.01f, -2.4f}, {1.6f, -2.2f}, {2.1f, -1.0f}, {2.0f, 0.8f}, {1.4f, 2.0f}, {1.0f, 2.6f}},
                12, 0.75f, 1.0f, 0.12f, 0.6f, 52, SAC_BLANC, 0);
        decalT(b, L_MARCHE, 12.4f, 1.6f, 15.45f, 2.6f, 1.3f, -30, 0, 0, 0.05f);
        conserve(b, 9.4f, 0, 14.6f, BOITE_RAVIOLI, 60, true);
        conserve(b, 14.4f, 0, 11.2f, BOITE_POIS, 20, true);
        b.push().translate(7.6f, 0, 14.4f).rotateY(25);
        b.bevelBox(-1.4f, 0, -0.45f, 1.4f, 0.9f, 0.45f, 0.06f, RED_BOX, new Mat[]{null, null, null, SN_BISCUIT, null, SN_BISCUIT}, 0);
        b.pop();
        decalO(b, D_FLAQUE, 'y', 6.0f, 0, 12.4f, 5.4f, 4.0f, 30, 0.07f);
    }

    // =================================================================== 8. palette de marchandises

    /** Carton imprimé (coin bas-arrière-gauche), scotch brun sur le dessus. */
    static void cartonImp(MeshBuilder b, float x0, float y0, float z0, float w, float h, float d, Mat m, Mat avant, Mat cote) {
        b.bevelBox(x0, y0, z0, x0 + w, y0 + h, z0 + d, 0.1f, m, new Mat[]{cote, cote, null, null, null, avant}, 0);
        b.box(x0 + w / 2 - 0.45f, y0 + h - 0.02f, z0 - 0.04f, x0 + w / 2 + 0.45f, y0 + h + 0.04f, z0 + d + 0.04f, SCOTCH_BRUN);
        b.box(x0 + w / 2 - 0.45f, y0 + h - 0.9f, z0 + d - 0.02f, x0 + w / 2 + 0.45f, y0 + h - 0.03f, z0 + d + 0.04f, SCOTCH_BRUN);
    }

    static final float[][] PAL_CARTONS = {
            // x0, z0, couche, sorte (0 eau, 1 biscottes, 2 lessive, 3 fragile)
            {6.7f, 1.0f, 0, 0}, {12.9f, 1.0f, 0, 1}, {19.1f, 1.0f, 0, 0}, {6.7f, 6.8f, 0, 2}, {12.9f, 6.8f, 0, 0}, {19.1f, 6.8f, 0, 3},
            {6.7f, 1.0f, 1, 1}, {12.9f, 1.0f, 1, 0}, {19.1f, 1.0f, 1, 2}, {12.9f, 6.8f, 1, 3}};

    /**
     * Palette Europe (1,2 x 0,8 m) sur deux blocs, deux couches de cartons filmés ; le film est déchiré à l'avant où des
     * cartons ont été pris, un carton éventré a laissé tomber ses bouteilles ; planche cassée, palette grisée.
     */
    static void paletteCartons(MeshBuilder b) {
        float x0 = 6.4f, x1 = 25.6f, z0 = 0.5f, z1 = 13.3f;
        Random r = new Random(61);
        for (float z : new float[]{z0, (z0 + z1) / 2 - 0.8f, z1 - 1.6f}) b.bevelBox(x0, 0, z, x1, 0.35f, z + 1.6f, 0.05f, BOIS_GRIS);
        for (float x : new float[]{x0, (x0 + x1) / 2 - 0.8f, x1 - 1.6f}) {
            for (float z : new float[]{z0, (z0 + z1) / 2 - 0.8f, z1 - 1.6f}) b.bevelBox(x, 0.35f, z + 0.05f, x + 1.6f, 1.6f, z + 1.55f, 0.06f, PINE);
            b.bevelBox(x, 1.6f, z0, x + 1.6f, 1.95f, z1, 0.05f, BOIS_GRIS);
        }
        float[] zp = {z0, z0 + 2.9f, (z0 + z1) / 2 - 0.8f, z1 - 4.5f, z1 - 2.3f};
        float[] wp = {2.3f, 1.6f, 1.6f, 1.6f, 2.3f};
        for (int k = 0; k < 5; k++) {
            float c = 0.85f + r.nextFloat() * 0.2f;
            b.color(c, c, c * 0.97f);
            if (k == 4) {
                // planche avant cassée : un morceau manque, l'autre est fendu et relevé
                b.bevelBox(x0, 1.95f, zp[k], x0 + 7.6f, 2.3f, zp[k] + wp[k], 0.05f, BOIS_GRIS);
                b.push().translate(x1, 2.3f, zp[k]).rotateZ(-7);
                b.bevelBox(-8.4f, -0.35f, 0, 0, 0, wp[k], 0.05f, BOIS_GRIS);
                b.pop();
            } else {
                b.bevelBox(x0, 1.95f, zp[k], x1, 2.3f, zp[k] + wp[k], 0.05f, k % 2 == 0 ? BOIS_GRIS : PINE);
            }
        }
        b.color(1);
        // cartons
        Mat[] av = {CT_EAU, CT_BISCOTTES, CT_LESSIVE, CT_FRAGILE};
        float w = 6.1f, h = 4.6f, d = 5.6f;
        for (float[] c : PAL_CARTONS) {
            float y = 2.3f + c[2] * (h + 0.08f);
            float ang = c[2] == 1 && c[1] < 3 ? (c[0] - 12.9f) * 0.35f + 1.2f : 0;
            b.push().translate(c[0] + w / 2, y, c[1] + d / 2).rotateY(ang).translate(-(c[0] + w / 2), -y, -(c[1] + d / 2));
            Mat m = c[2] == 0 && r.nextFloat() < 0.5f ? CARTON_MOUILLE : CARTON;
            cartonImp(b, c[0], y, c[1], w, h, d, m, c[1] > 3 ? av[(int) c[3]] : null, c[0] > 18 || c[0] < 7 ? av[(int) c[3]] : null);
            b.pop();
        }
        // carton éventré en haut à gauche de l'avant : rabats ouverts, bouteilles d'eau
        float ex = 6.7f, ey = 2.3f + h + 0.08f, ez = 6.8f;
        LotT1a.cartonOuvert(b, ex + w / 2 - 0.2f, ey + 0.07f, ez + d / 2 - 0.4f, 6, 1);
        bouteillePet(b, 13.0f, 15.0f, -10, PET_EAU, PLASTIC_BLUE, 1);
        bouteillePet(b, 19.6f, 14.6f, 35, PET_EAU, PLASTIC_BLUE, 0.7f);
        // usure : moisissure et auréoles sur les cartons du bas, coulures sur les dés, papiers
        decalT(b, MOUSSE_VERTE, 22.1f, 3.2f, 12.4f, 5.0f, 1.6f, 0, 0, 0, 0.16f);
        decalO(b, AUREOLE, 'y', 14.4f, 2.3f + 2 * h + 0.08f, 3.6f, 2.4f, 2.4f, 20, 0.12f);
        decalT(b, BOUE_2, 16.0f, 0.9f, z1 + 0.02f, 18.0f, 1.8f, 0, 0, 0, 0.05f);
        jonchee(b, 2.0f, 13.6f, 30.0f, 15.6f, 6, FEUILLES, 1.3f, 62);
        b.push().translate(28.4f, 0, 11.0f).rotateY(70);
        b.bevelBox(-2.4f, 0, -0.75f, 2.4f, 0.3f, 0.75f, 0.05f, BOIS_GRIS);
        b.pop();
    }

    /** Film étirable (passe translucide) autour des deux couches, déchiré à l'avant gauche. */
    static void paletteFilm(MeshBuilder b) {
        float x0 = 6.55f, x1 = 25.35f, z0 = 0.85f, z1 = 12.55f, y0 = 2.1f, y1 = 11.55f, ym = 6.9f;
        b.face(FILM, v(0, 0, -1), p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0));
        b.face(FILM, v(1, 0, 0), p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1));
        b.face(FILM, v(-1, 0, 0), p(x0, y0, z0), p(x0, y0, z1), p(x0, ym, z1), p(x0, ym, z0));
        b.face(FILM, v(0, 0, 1), p(12.6f, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(12.6f, y1, z1));
        b.face(FILM, v(0, 0, 1), p(x0, y0, z1), p(12.6f, y0, z1), p(12.6f, ym - 1.2f, z1), p(x0, ym - 2.6f, z1));
        b.face(FILM, v(0, 1, 0), p(12.6f, y1 + 0.05f, z0), p(x1, y1 + 0.05f, z0), p(x1, y1 + 0.05f, z1), p(12.6f, y1 + 0.05f, z1));
        // lambeaux qui pendent
        b.ribbon(new float[][]{p(9.0f, ym - 2.4f, z1 + 0.05f), p(9.4f, ym - 3.6f, z1 + 0.6f), p(9.2f, 1.4f, z1 + 1.4f)}, new float[]{2.4f, 1.6f, 0.6f}, v(1, 0, 0.1f), FILM);
        b.ribbon(new float[][]{p(12.6f, y1, z1 - 2.0f), p(12.0f, y1 + 0.4f, z1 - 0.5f), p(11.0f, y1 - 0.8f, z1 + 0.6f)}, new float[]{1.6f, 1.2f, 0.4f}, v(0, 0.2f, 1), FILM);
    }

    // =================================================================== 9. cagettes de marché

    /** Cagette plastique ajourée (w x d x h), coin bas-arrière-gauche en (0, 0, 0) local, teinte (r, g, bl). */
    static void cagette(MeshBuilder b, float w, float d, float h, float r, float g, float bl) {
        b.color(r, g, bl);
        b.bevelBox(0.15f, 0, 0.15f, w - 0.15f, 0.22f, d - 0.15f, 0.06f, PLASTIQUE_USE);
        float i = 0.12f;
        doubleFace(b, CAGETTE, p(i, 0.2f, d - i), p(w - i, 0.2f, d - i), p(w - i, h - 0.3f, d - i), p(i, h - 0.3f, d - i));
        doubleFace(b, CAGETTE, p(w - i, 0.2f, i), p(i, 0.2f, i), p(i, h - 0.3f, i), p(w - i, h - 0.3f, i));
        doubleFace(b, CAGETTE, p(i, 0.2f, i), p(i, 0.2f, d - i), p(i, h - 0.3f, d - i), p(i, h - 0.3f, i));
        doubleFace(b, CAGETTE, p(w - i, 0.2f, d - i), p(w - i, 0.2f, i), p(w - i, h - 0.3f, i), p(w - i, h - 0.3f, d - i));
        float e = 0.3f;
        b.bevelBox(0, h - 0.32f, 0, w, h, e, 0.08f, PLASTIQUE_USE);
        b.bevelBox(0, h - 0.32f, d - e, w, h, d, 0.08f, PLASTIQUE_USE);
        b.bevelBox(0, h - 0.32f, e, e, h, d - e, 0.08f, PLASTIQUE_USE);
        b.bevelBox(w - e, h - 0.32f, e, w, h, d - e, 0.08f, PLASTIQUE_USE);
        for (float[] c : new float[][]{{0, 0}, {w - 0.4f, 0}, {0, d - 0.4f}, {w - 0.4f, d - 0.4f}}) b.box(c[0], 0.07f, c[1], c[0] + 0.4f, h - 0.32f, c[1] + 0.4f, PLASTIQUE_USE);
        b.color(1);
    }

    static void tomates(MeshBuilder b, float x0, float z0, float w, float d, float y, int n, long g) {
        Random r = new Random(g);
        for (int k = 0; k < n; k++) {
            float x = x0 + 0.6f + r.nextFloat() * (w - 1.2f), z = z0 + 0.6f + r.nextFloat() * (d - 1.2f);
            b.push().translate(x, y + 0.42f + r.nextFloat() * 0.15f, z).rotateY(r.nextFloat() * 360).scale(1, 0.72f, 1);
            b.sphere(0.58f, 8, 5, TOMATE);
            b.pop();
        }
    }

    static void legumes(MeshBuilder b, float x0, float z0, float w, float d, float y, int sorte, long g) {
        Random r = new Random(g);
        if (sorte == 0) {
            tomates(b, x0, z0, w, d, y, 9, g);
        } else if (sorte == 1) {
            for (int k = 0; k < 3; k++) {
                b.push().translate(x0 + 1.6f + k * (w - 3.2f) / 2, y + 0.9f, z0 + d / 2 + (r.nextFloat() - 0.5f) * 1.2f);
                b.rock(1.4f, 1.0f, 1.3f, 0.35f, 1.4f, (int) g + k, 9, SALADE);
                b.pop();
            }
        } else if (sorte == 2) {
            for (int k = 0; k < 9; k++) {
                float x = x0 + 0.8f + r.nextFloat() * (w - 1.6f), z = z0 + 0.8f + r.nextFloat() * (d - 1.6f);
                float a = r.nextFloat() * 360;
                b.push().translate(x, y + 0.25f + (k % 3) * 0.2f, z).rotateY(a);
                b.tube(new float[][]{p(-1.2f, 0, 0), p(0, 0.05f, 0.1f), p(1.0f, 0, 0)}, new float[]{0.24f, 0.2f, 0.04f}, 6, CAROTTE, true, false, 0, false);
                b.pop();
            }
        } else {
            for (int k = 0; k < 10; k++) {
                float x = x0 + 0.6f + r.nextFloat() * (w - 1.2f), z = z0 + 0.6f + r.nextFloat() * (d - 1.2f);
                b.push().translate(x, y + 0.4f + (k % 2) * 0.25f, z);
                b.rock(0.55f, 0.4f, 0.45f, 0.25f, 2.0f, (int) g + k, 6, PATATE);
                b.pop();
            }
        }
    }

    /** Cagettes plastique empilées de travers, une renversée devant ; tomates, salades, carottes et patates pourries. */
    static void cagettesMarche(MeshBuilder b) {
        float w = 9.6f, d = 6.4f, h = 2.8f;
        b.push().translate(0.6f, 0, 0.5f);
        cagette(b, w, d, h, 0.42f, 0.6f, 0.4f);
        legumes(b, 0, 0, w, d, 0.25f, 3, 71);
        b.pop();
        b.push().translate(5.4f, h, 3.8f).rotateY(5).translate(-4.8f, 0, -3.2f);
        cagette(b, w, d, h, 0.26f, 0.27f, 0.28f);
        legumes(b, 0, 0, w, d, 0.25f, 2, 72);
        b.pop();
        b.push().translate(5.2f, 2 * h, 3.6f).rotateY(-7).translate(-4.8f, 0, -3.2f);
        cagette(b, w, d, h, 0.35f, 0.45f, 0.6f);
        legumes(b, 0, 0, w, d, 0.25f, 0, 73);
        b.pop();
        b.push().translate(10.8f, 0, 0.6f).rotateY(-90).translate(0, 0, -4.8f);
        cagette(b, 6.4f, 4.8f, h, 0.42f, 0.6f, 0.4f);
        legumes(b, 0, 0, 6.4f, 4.8f, 0.25f, 1, 74);
        b.pop();
        // renversée devant, sur le flanc
        b.push().translate(3.4f, d - 1.6f, 8.8f).rotateY(-12).rotateX(90);
        cagette(b, w, d - 1.6f, h, 0.26f, 0.27f, 0.28f);
        b.pop();
        tomates(b, 3.8f, 12.2f, 7.0f, 3.0f, 0, 7, 75);
        legumes(b, 10.0f, 12.0f, 5.0f, 3.4f, 0, 3, 76);
        // usure : jus de pourriture, feuilles de salade écrasées, étiquette
        decalO(b, D_SAUCE, 'y', 8.2f, 0, 11.0f, 4.0f, 3.0f, 15, 0.07f);
        decalO(b, D_FLAQUE, 'y', 11.4f, 0, 8.6f, 4.0f, 2.8f, -20, 0.1f);
        jonchee(b, 1.0f, 8.0f, 15.0f, 15.4f, 8, SALADE, 1.3f, 77);
        decalT(b, ETIQUETTE_DECHIREE, 5.4f, 1.4f, 6.9f + 0.02f, 2.6f, 1.3f, 0, 0, -4, 0.06f);
    }

    // =================================================================== 10. casiers à bière

    /** Casier à bière 24 bouteilles (40 x 30 x 27 cm), coin bas-arrière-gauche en (0,0,0) local ; n bouteilles dedans. */
    static void casier(MeshBuilder b, float r, float g, float bl, int n, int cassees, long graine) {
        float w = 6.4f, d = 4.8f, h = 4.3f, i = 0.16f;
        b.color(r, g, bl);
        b.bevelBox(0.1f, 0, 0.1f, w - 0.1f, 0.3f, d - 0.1f, 0.06f, PLASTIQUE_USE);
        float[][] ext = {p(0, 0.1f, d), p(w, 0.1f, d), p(w, h, d), p(0, h, d)};
        b.face(CASIER, v(0, 0, 1), ext);
        b.face(CASIER, v(0, 0, -1), p(w, 0.1f, 0), p(0, 0.1f, 0), p(0, h, 0), p(w, h, 0));
        b.face(CASIER, v(-1, 0, 0), p(0, 0.1f, 0), p(0, 0.1f, d), p(0, h, d), p(0, h, 0));
        b.face(CASIER, v(1, 0, 0), p(w, 0.1f, d), p(w, 0.1f, 0), p(w, h, 0), p(w, h, d));
        b.face(CASIER, v(0, 0, -1), p(i, 0.3f, d - i), p(w - i, 0.3f, d - i), p(w - i, h, d - i), p(i, h, d - i));
        b.face(CASIER, v(0, 0, 1), p(w - i, 0.3f, i), p(i, 0.3f, i), p(i, h, i), p(w - i, h, i));
        b.face(CASIER, v(1, 0, 0), p(i, 0.3f, d - i), p(i, 0.3f, i), p(i, h, i), p(i, h, d - i));
        b.face(CASIER, v(-1, 0, 0), p(w - i, 0.3f, i), p(w - i, 0.3f, d - i), p(w - i, h, d - i), p(w - i, h, i));
        b.face(PLASTIQUE_USE, v(0, 1, 0), p(0, h, d), p(w, h, d), p(w - i, h, d - i), p(i, h, d - i));
        b.face(PLASTIQUE_USE, v(0, 1, 0), p(w, h, 0), p(0, h, 0), p(i, h, i), p(w - i, h, i));
        b.face(PLASTIQUE_USE, v(0, 1, 0), p(0, h, 0), p(0, h, d), p(i, h, d - i), p(i, h, i));
        b.face(PLASTIQUE_USE, v(0, 1, 0), p(w, h, d), p(w, h, 0), p(w - i, h, i), p(w - i, h, d - i));
        for (int k = 1; k < 6; k++) b.box(k * w / 6 - 0.04f, 0.3f, i, k * w / 6 + 0.04f, 2.6f, d - i, PLASTIQUE_USE);
        for (int k = 1; k < 4; k++) b.box(i, 0.3f, k * d / 4 - 0.04f, w - i, 2.58f, k * d / 4 + 0.04f, PLASTIQUE_USE);
        b.color(1);
        Random rr = new Random(graine);
        int pose = 0;
        for (int k = 0; k < 24 && pose < n; k++) {
            if (rr.nextFloat() < 0.35f) continue;
            float x = (k % 6 + 0.5f) * w / 6, z = (k / 6 + 0.5f) * d / 4;
            boolean c = pose < cassees;
            b.push().translate(x, 0.3f, z);
            if (!c) {
                b.lathe(new float[][]{{0.46f, 1.9f}, {0.4f, 2.45f}, {0.2f, 2.95f}, {0.19f, 3.45f}, {0.23f, 3.5f}, {0.23f, 3.66f}, {0.01f, 3.68f}}, 6, 0, 360, rr.nextBoolean() ? BOTTLE_BROWN : BOTTLE_GREEN, 1, 1, 0, 0, 0, false);
            } else {
                b.lathe(new float[][]{{0.46f, 1.9f}, {0.42f, 2.3f}}, 6, 0, 360, BOTTLE_GREEN, 1, 1, 0.12f, 2.4f, (int) graine + k, false);
                b.lathe(new float[][]{{0.42f, 2.3f}, {0.38f, 1.9f}}, 6, 0, 360, BOTTLE_GREEN, 1, 1, 0.12f, 2.4f, (int) graine + k, true);
            }
            b.pop();
            pose++;
        }
    }

    /** Casiers à bière empilés en quinconce contre le mur, bouteilles vides (dont cassées), un casier renversé devant. */
    static void caissesBiere(MeshBuilder b) {
        b.push().translate(1.0f, 0, 0.5f);
        casier(b, 0.78f, 0.64f, 0.26f, 0, 0, 81);
        b.pop();
        b.push().translate(7.8f, 0, 0.6f).rotateY(-3);
        casier(b, 0.7f, 0.22f, 0.18f, 0, 0, 82);
        b.pop();
        b.push().translate(7.6f, 4.32f, 3.0f).rotateY(9).translate(-3.2f, 0, -2.4f);
        casier(b, 0.3f, 0.48f, 0.3f, 12, 2, 83);
        b.pop();
        // renversé devant, ouverture vers l'avant
        b.push().translate(3.0f, 4.8f, 8.4f).rotateY(14).rotateX(90);
        casier(b, 0.78f, 0.64f, 0.26f, 0, 0, 84);
        b.pop();
        bouteilleCouchee(b, 10.8f, 10.4f, -30, BOTTLE_BROWN);
        bouteilleCouchee(b, 12.6f, 13.6f, 55, BOTTLE_GREEN);
        bouteilleCouchee(b, 4.6f, 14.8f, 170, BOTTLE_GREEN);
        bouteille(b, 13.6f, 0, 8.6f, BOTTLE_GREEN, true, 85);
        eclats(b, 11.4f, 9.0f, 1.8f, 12, BOTTLE_GREEN, 86);
        eclats(b, 8.0f, 14.6f, 1.2f, 7, BOTTLE_BROWN, 87);
        decalO(b, D_FLAQUE, 'y', 12.0f, 0, 12.0f, 4.2f, 3.4f, 40, 0.07f);
        decalT(b, ETIQUETTE_DECHIREE, 4.2f, 2.0f, 5.32f + 0.02f, 2.4f, 1.2f, 0, 0, 6, 0.06f);
        jonchee(b, 0.8f, 6.0f, 15.0f, 15.6f, 4, PAPIER, 1.0f, 88);
    }

    // =================================================================== 11. vitrine réfrigérée

    static final float FX0 = 3.2f, FX1 = 12.8f, FZ0 = 0.3f, FZ1 = 9.9f, FY0 = 2.0f, FY1 = 27.2f, PORTE = 13;

    /** Vitrine à boissons (60 x 60 x 200 cm) pillée : porte vitrée entrouverte et brisée, clayettes presque vides. */
    static void frigoBoissons(MeshBuilder b) {
        b.color(0.74f, 0.2f, 0.17f);
        Mat t = PEINTURE_USEE;
        float e = 0.55f;
        b.bevelBox(FX0, FY0, FZ0, FX0 + e, FY1, FZ1 - 0.5f, 0.12f, t);
        b.bevelBox(FX1 - e, FY0, FZ0, FX1, FY1, FZ1 - 0.5f, 0.12f, t);
        b.bevelBox(FX0 + e, FY0, FZ0, FX1 - e, FY1, FZ0 + e, 0.08f, t);
        b.bevelBox(FX0 + e, FY1 - e, FZ0 + e, FX1 - e, FY1, FZ1 - 0.5f, 0.08f, t);
        b.bevelBox(FX0, 0.5f, FZ0, FX1, FY0, FZ1 - 0.3f, 0.1f, t);
        // fronton (enseigne éteinte)
        b.bevelBox(FX0 - 0.2f, FY1, FZ0, FX1 + 0.2f, FY1 + 3.9f, FZ1 + 0.3f, 0.15f, t, new Mat[]{null, null, null, null, null, E_FRIGO}, 0);
        b.bevelBox(FX0 - 0.35f, FY1 + 3.85f, FZ0, FX1 + 0.35f, FY1 + 4.35f, FZ1 + 0.45f, 0.15f, t);
        b.color(1);
        // pieds, grille d'aération
        for (float[] q : new float[][]{{FX0 + 0.8f, FZ0 + 0.8f}, {FX1 - 0.8f, FZ0 + 0.8f}, {FX0 + 0.8f, FZ1 - 1.2f}, {FX1 - 0.8f, FZ1 - 1.2f}}) {
            b.push().translate(q[0], 0, q[1]);
            b.cylinder(0.4f, 0, 0.52f, 8, DARK, DARK, DARK);
            b.pop();
        }
        b.face(GRILLE, v(0, 0, 1), p(FX0 + 0.3f, 0.6f, FZ1 - 0.25f), p(FX1 - 0.3f, 0.6f, FZ1 - 0.25f), p(FX1 - 0.3f, FY0 - 0.15f, FZ1 - 0.25f), p(FX0 + 0.3f, FY0 - 0.15f, FZ1 - 0.25f));
        // intérieur : doublure blanche sale, clayettes, quelques canettes et bouteilles
        b.color(0.82f, 0.82f, 0.79f);
        float o = 0.07f;
        b.face(PLASTIQUE_USE, v(1, 0, 0), p(FX0 + e + o, FY0, FZ0 + e), p(FX0 + e + o, FY0, FZ1 - 0.6f), p(FX0 + e + o, FY1 - e, FZ1 - 0.6f), p(FX0 + e + o, FY1 - e, FZ0 + e));
        b.face(PLASTIQUE_USE, v(-1, 0, 0), p(FX1 - e - o, FY0, FZ1 - 0.6f), p(FX1 - e - o, FY0, FZ0 + e), p(FX1 - e - o, FY1 - e, FZ0 + e), p(FX1 - e - o, FY1 - e, FZ1 - 0.6f));
        b.face(PLASTIQUE_USE, v(0, 0, 1), p(FX0 + e, FY0, FZ0 + e + o), p(FX1 - e, FY0, FZ0 + e + o), p(FX1 - e, FY1 - e, FZ0 + e + o), p(FX0 + e, FY1 - e, FZ0 + e + o));
        b.color(1);
        b.face(INTERIEUR_SALE, v(0, 1, 0), p(FX0 + e, FY0 + o, FZ1 - 0.6f), p(FX1 - e, FY0 + o, FZ1 - 0.6f), p(FX1 - e, FY0 + o, FZ0 + e), p(FX0 + e, FY0 + o, FZ0 + e));
        float[] yc = {7.2f, 12.2f, 17.2f, 22.2f};
        for (float y : yc) {
            doubleFace(b, GRILLAGE.withWorld(5), p(FX0 + e + 0.05f, y, FZ1 - 1.0f), p(FX1 - e - 0.05f, y, FZ1 - 1.0f), p(FX1 - e - 0.05f, y, FZ0 + e + 0.1f), p(FX0 + e + 0.05f, y, FZ0 + e + 0.1f));
            b.box(FX0 + e + 0.05f, y - 0.6f, FZ1 - 1.08f, FX1 - e - 0.05f, y + 0.12f, FZ1 - 0.98f, PLASTIQUE_USE);
        }
        decalT(b, L_PROMO, 6.0f, 11.85f, FZ1 - 0.98f, 1.6f, 0.8f, 0, 0, -6, 0.05f);
        canette(b, 4.8f, 12.22f, 4.0f, CAN_COLA, 80);
        canette(b, 6.0f, 12.22f, 6.6f, CAN_ORANGE, 100);
        canette(b, 10.6f, 17.22f, 3.6f, CAN_MENTHE, 70);
        b.push().translate(9.0f, 22.2f + 0.52f, 5.8f).rotateY(30).rotateZ(90).translate(0, -0.9f, 0);
        etiquetteCyl(b, 0.52f, 0.12f, 1.7f, 10, CAN_BIERE, 0, CHROME_PIQUE, CHROME_PIQUE);
        b.pop();
        b.push().translate(10.4f, 7.22f, 5.0f);
        b.lathe(new float[][]{{0.01f, 0}, {0.6f, 0.02f}, {0.72f, 0.25f}, {0.72f, 1.3f}}, 10, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        etiquetteCyl(b, 0.73f, 1.3f, 2.6f, 10, PET_SODA, 90, null, null);
        b.lathe(new float[][]{{0.72f, 2.6f}, {0.72f, 3.3f}, {0.5f, 4.0f}, {0.24f, 4.5f}}, 10, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        b.cylinder(0.26f, 4.5f, 4.85f, 8, PAINT_RED, null, PAINT_RED);
        b.pop();
        // porte vitrée (charnières à droite), entrouverte : cadre, joint, poignée
        b.push().translate(FX1, 0, FZ1).rotateY(PORTE).translate(-FX1, 0, -FZ1);
        b.color(0.74f, 0.2f, 0.17f);
        b.bevelBox(FX0 + 0.05f, FY0, FZ1 - 0.5f, FX0 + 0.85f, FY1 - 0.1f, FZ1, 0.1f, t);
        b.bevelBox(FX1 - 0.85f, FY0, FZ1 - 0.5f, FX1 - 0.05f, FY1 - 0.1f, FZ1, 0.1f, t);
        b.bevelBox(FX0 + 0.85f, FY0, FZ1 - 0.5f, FX1 - 0.85f, FY0 + 1.0f, FZ1, 0.1f, t);
        b.bevelBox(FX0 + 0.85f, FY1 - 1.0f, FZ1 - 0.5f, FX1 - 0.85f, FY1 - 0.1f, FZ1, 0.1f, t);
        b.color(1);
        b.bevelBox(FX0 + 0.3f, 10.0f, FZ1 + 0.02f, FX0 + 0.7f, 18.0f, FZ1 + 0.6f, 0.15f, CHROME_PIQUE);
        coulure(b, 'z', FX1 - 0.45f, 22.0f, FZ1, 1.0f, 1);
        decalT(b, ETIQUETTE_DECHIREE, 8.0f, FY0 + 0.5f, FZ1, 3.2f, 0.8f, 0, 0, 0, 0.05f);
        b.pop();
        // usure : rouille sous les charnières, boue, affichette, impacts, morceaux de vitre au sol
        decalT(b, COULURE_3, FX1, 24.0f, 5.0f, 1.4f, 2.8f, 90, 0, 0, 0.06f);
        decalT(b, L_PANNE, FX1, 15.0f, 4.6f, 4.6f, 2.3f, 90, 0, 3, 0.06f);
        decalT(b, IMPACTS, FX0, 12.0f, 5.0f, 4.0f, 2.0f, -90, 0, 0, 0.06f);
        decalT(b, GRIFFURES, FX0, 6.0f, 4.0f, 3.4f, 1.7f, -90, 0, -15, 0.06f);
        decalT(b, BOUE_1, (FX0 + FX1) / 2, 1.4f, FZ1 - 0.3f, 8.8f, 1.8f, 0, 0, 0, 0.14f);
        decalT(b, COULURE_1, FX0 + 1.2f, FY1 + 2.6f, FZ1 + 0.3f, 1.0f, 2.0f, 0, 0, 0, 0.06f);
        eclats(b, 9.0f, 12.6f, 2.6f, 16, PLASTIQUE_USE, 91);
        papierFroisse(b, 4.2f, 0, 12.6f, 0.6f, PAPIER, 92);
    }

    /** Vitre de la porte : haut sale et fêlé, bas brisé (trou). */
    static void frigoVerre(MeshBuilder b) {
        b.push().translate(FX1, 0, FZ1).rotateY(PORTE).translate(-FX1, 0, -FZ1);
        float z = FZ1 - 0.25f, x0 = FX0 + 0.85f, x1 = FX1 - 0.85f, ym = 15.0f;
        b.face(VERRE_SALE, v(0, 0, 1), p(x0, ym, z), p(x1, ym, z), p(x1, FY1 - 1.0f, z), p(x0, FY1 - 1.0f, z));
        b.face(VERRE_BRISE, v(0, 0, 1), p(x0, FY0 + 1.0f, z), p(x1, FY0 + 1.0f, z), p(x1, ym, z), p(x0, ym, z));
        b.pop();
    }

    // =================================================================== 12. distributeur automatique

    static final float DX0 = 0.8f, DX1 = 15.2f, DZ0 = 0.4f, DZ1 = 13.2f, DH = 28.8f, DXV = 10.6f, DYV0 = 7.4f, DYV1 = 25.4f;

    static void spirale(MeshBuilder b, float x, float y, float z0, float z1) {
        int n = 15;
        float[][] pts = new float[n][];
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 * 2.5 / (n - 1);
            pts[k] = p(x + 0.55f * (float) Math.cos(a), y + 0.62f + 0.55f * (float) Math.sin(a), z0 + (z1 - z0) * k / (n - 1));
        }
        b.tube(pts, fill(n, 0.06f), 4, INOX, false, false, 0, false);
    }

    /** Distributeur de snacks (1,8 m x 0,9 x 0,8) pillé : vitre brisée, spirales presque vides, monnayeur arraché. */
    static void distributeur(MeshBuilder b) {
        Mat t = PEINTURE_USEE;
        float e = 0.6f;
        b.color(0.25f, 0.26f, 0.29f);
        b.bevelBox(DX0, 0.4f, DZ0, DX0 + e, DH, DZ1, 0.15f, t);
        b.bevelBox(DX1 - e, 0.4f, DZ0, DX1, DH, DZ1, 0.15f, t);
        b.bevelBox(DX0 + e, 0.4f, DZ0, DX1 - e, DH, DZ0 + e, 0.1f, t);
        b.bevelBox(DX0 + e, DH - 0.6f, DZ0 + e, DX1 - e, DH, DZ1, 0.1f, t);
        b.bevelBox(DX0 + e, 0.4f, DZ0 + e, DX1 - e, DYV0 - 0.6f, DZ1, 0.1f, t);
        // colonne de commande, bandeau haut, montant entre vitre et colonne
        b.bevelBox(DXV, DYV0 - 0.6f, DZ1 - 1.2f, DX1 - e, DH - 0.6f, DZ1, 0.1f, t, new Mat[]{null, null, null, null, null, null}, 0);
        b.bevelBox(DX0 + e, DYV1, DZ1 - 1.0f, DXV, DH - 0.6f, DZ1, 0.1f, t);
        b.color(1);
        b.face(E_DISTRIB, v(0, 0, 1), p(DX0 + 0.9f, DYV1 + 0.35f, DZ1 + 0.06f), p(DXV - 0.3f, DYV1 + 0.35f, DZ1 + 0.06f), p(DXV - 0.3f, DH - 0.95f, DZ1 + 0.06f), p(DX0 + 0.9f, DH - 0.95f, DZ1 + 0.06f));
        b.face(PANNEAU, v(0, 0, 1), p(DXV + 0.25f, 10.0f, DZ1 + 0.06f), p(DX1 - e - 0.25f, 10.0f, DZ1 + 0.06f), p(DX1 - e - 0.25f, 24.6f, DZ1 + 0.06f), p(DXV + 0.25f, 24.6f, DZ1 + 0.06f));
        // monnayeur arraché : trou sombre, trappe pendante, fils
        float mx = (DXV + DX1 - e) / 2, my = 14.4f;
        b.box(mx - 1.1f, my - 1.4f, DZ1 + 0.07f, mx + 1.1f, my + 1.4f, DZ1 + 0.11f, INTERIEUR_SALE);
        b.push().translate(mx - 1.1f, my - 1.4f, DZ1 + 0.11f).rotateY(-110);
        b.bevelBox(0, 0, -0.08f, 2.2f, 2.8f, 0.08f, 0.04f, METAL_NU);
        b.pop();
        b.tube(new float[][]{p(mx - 0.2f, my, DZ1), p(mx, my - 1.0f, DZ1 + 0.6f), p(mx + 0.3f, my - 2.6f, DZ1 + 0.5f)}, fill(3, 0.06f), 4, PAINT_RED, false, true, 0, false);
        b.tube(new float[][]{p(mx + 0.3f, my + 0.2f, DZ1), p(mx + 0.5f, my - 0.6f, DZ1 + 0.7f), p(mx + 0.4f, my - 2.0f, DZ1 + 0.9f)}, fill(3, 0.06f), 4, YELLOW, false, true, 0, false);
        // cadre de vitre
        b.color(0.25f, 0.26f, 0.29f);
        b.bevelBox(DX0 + e, DYV0 - 0.6f, DZ1 - 0.8f, DXV, DYV0, DZ1, 0.08f, t);
        b.color(1);
        // trappe de distribution (en bas), poussée vers l'intérieur
        b.box(DX0 + 1.6f, 2.4f, DZ1 + 0.02f, DXV - 1.0f, 5.6f, DZ1 + 0.05f, INTERIEUR_SALE);
        b.push().translate(0, 5.5f, DZ1 - 0.1f).rotateX(-25);
        b.bevelBox(DX0 + 1.8f, -3.0f, -0.12f, DXV - 1.2f, 0, 0.0f, 0.04f, DARK);
        b.pop();
        // intérieur : fond sombre, plateaux, spirales, quelques paquets
        b.face(INTERIEUR_SALE, v(0, 0, 1), p(DX0 + e, DYV0, DZ0 + e + 0.07f), p(DXV, DYV0, DZ0 + e + 0.07f), p(DXV, DYV1, DZ0 + e + 0.07f), p(DX0 + e, DYV1, DZ0 + e + 0.07f));
        b.face(INOX, v(-1, 0, 0), p(DXV - 0.07f, DYV0, DZ1 - 1.2f), p(DXV - 0.07f, DYV0, DZ0 + e), p(DXV - 0.07f, DYV1, DZ0 + e), p(DXV - 0.07f, DYV1, DZ1 - 1.2f));
        float[] plateaux = {8.4f, 12.0f, 15.6f, 19.2f, 22.8f};
        Mat[] sn = {SN_CHIPS, SN_CHOCO, SN_BISCUIT, SN_BONBON};
        Random r = new Random(101);
        for (int k = 0; k < plateaux.length; k++) {
            float y = plateaux[k];
            b.box(DX0 + e + 0.05f, y - 0.12f, DZ0 + e + 0.05f, DXV - 0.05f, y, DZ1 - 1.0f, INOX);
            for (int c = 0; c < 4; c++) {
                float x = DX0 + e + 1.15f + c * 2.25f;
                if ((k + c) % 2 == 0) spirale(b, x, y, DZ0 + 1.6f, DZ1 - 1.4f);
                if (r.nextFloat() < 0.3f) {
                    Mat m = sn[(k + c) % 4];
                    b.push().translate(x, y + 0.05f, DZ1 - 2.4f).rotateX(-12).rotateY((r.nextFloat() - 0.5f) * 20);
                    b.bevelBox(-0.85f, 0, -0.3f, 0.85f, 2.2f, 0.3f, 0.12f, m.asFit().sub(0.4f, 0.4f, 0.6f, 0.6f), new Mat[]{null, null, null, null, m, m}, 0);
                    b.pop();
                }
            }
        }
        // pieds
        for (float[] q : new float[][]{{DX0 + 1.0f, DZ0 + 1.0f}, {DX1 - 1.0f, DZ0 + 1.0f}, {DX0 + 1.0f, DZ1 - 1.0f}, {DX1 - 1.0f, DZ1 - 1.0f}}) {
            b.push().translate(q[0], 0, q[1]);
            b.cylinder(0.45f, 0, 0.42f, 8, DARK, DARK, DARK);
            b.pop();
        }
        // usure
        decalT(b, L_PANNE, (DXV + DX1 - e) / 2, 26.6f, DZ1 + 0.05f, 4.2f, 2.1f, 0, 0, -4, 0.04f);
        decalT(b, GRIFFURES, DX1, 12.0f, 7.0f, 4.0f, 2.0f, 90, 0, -10, 0.06f);
        decalT(b, IMPACTS, DX0, 18.0f, 6.0f, 5.0f, 2.5f, -90, 0, 0, 0.06f);
        decalT(b, BOUE_2, 8.0f, 1.2f, DZ1, 14.0f, 2.0f, 0, 0, 0, 0.07f);
        decalT(b, COULURE_2, DX1, DH - 2.0f, 3.0f, 1.4f, 2.8f, 90, 0, 0, 0.06f);
        decalT(b, CROIX, DX0, 8.0f, 9.0f, 3.0f, 3.0f, -90, 0, 8, 0.06f);
        eclats(b, 6.0f, 14.4f, 1.6f, 14, PLASTIQUE_USE, 102);
        eclats(b, 4.0f, DZ1 - 0.6f, 0.8f, 5, PLASTIQUE_USE, 103);
        b.push().translate(11.6f, 0, 14.8f).rotateY(30);
        b.bevelBox(-0.85f, 0, -1.1f, 0.85f, 0.45f, 1.1f, 0.15f, SN_CHIPS.asFit().sub(0.3f, 0.3f, 0.7f, 0.7f), new Mat[]{null, null, null, SN_CHIPS, null, null}, 0);
        b.pop();
    }

    /** Vitre du distributeur : brisée en bas, sale en haut. */
    static void distributeurVerre(MeshBuilder b) {
        float z = DZ1 - 0.45f, x0 = DX0 + e(), x1 = DXV, ym = 14.0f;
        b.face(VERRE_SALE, v(0, 0, 1), p(x0, ym, z), p(x1, ym, z), p(x1, DYV1, z), p(x0, DYV1, z));
        b.face(VERRE_BRISE, v(0, 0, 1), p(x0, DYV0, z), p(x1, DYV0, z), p(x1, ym, z), p(x0, ym, z));
    }

    static float e() {
        return 0.6f;
    }

    // =================================================================== 13. corbeille de rue

    static final float CBX = 8.0f, CBZ = 5.2f, CBY = 13.6f, CBR = 3.2f;

    /** Support de sac « Vigipirate » contre le mur : poteau scellé, cerceau et collier, sac plein éventré, déchets au pied. */
    static void corbeilleRue(MeshBuilder b) {
        b.color(0.24f, 0.36f, 0.26f);
        float zp = 1.55f;
        b.push().translate(CBX, 0, zp);
        b.cylinder(0.45f, 0, 15.4f, 10, PEINTURE_USEE, null, PEINTURE_USEE);
        b.pop();
        b.bevelBox(CBX - 1.0f, 0, zp - 1.0f, CBX + 1.0f, 0.3f, zp + 1.0f, 0.08f, PEINTURE_USEE);
        b.bevelBox(CBX - 0.35f, CBY - 1.0f, zp, CBX + 0.35f, CBY + 0.6f, CBZ - CBR + 0.1f, 0.1f, PEINTURE_USEE);
        b.push().translate(CBX, CBY, CBZ);
        b.torus(CBR, 0.17f, 24, 5, 0, 360, PEINTURE_USEE);
        b.translate(0, 0.48f, 0);
        b.torus(CBR + 0.12f, 0.12f, 24, 4, 0, 360, PEINTURE_USEE);
        b.pop();
        b.color(1);
        for (float[] q : new float[][]{{-0.7f, -0.7f}, {0.7f, -0.7f}, {-0.7f, 0.7f}, {0.7f, 0.7f}}) {
            b.push().translate(CBX + q[0], 0.3f, zp + q[1]);
            b.cylinder(0.14f, 0, 0.12f, 6, ROUILLE, null, ROUILLE);
            b.pop();
        }
        // contenu du sac (vu à travers le plastique) : ordures tassées, bouteille, canette, papiers
        b.push().translate(CBX, 6.4f, CBZ);
        b.rock(2.5f, 2.7f, 2.3f, 0.25f, 0.8f, 111, 10, ORDURES);
        b.pop();
        b.push().translate(CBX + 0.6f, 10.0f, CBZ + 0.4f);
        b.rock(2.2f, 1.6f, 2.1f, 0.3f, 1.0f, 112, 9, ORDURES);
        b.pop();
        b.push().translate(CBX - 1.2f, 10.4f, CBZ + 0.6f).rotateZ(30).rotateY(20);
        b.lathe(new float[][]{{0.01f, 0}, {0.6f, 0.02f}, {0.72f, 0.25f}, {0.72f, 3.3f}, {0.5f, 4.0f}, {0.24f, 4.5f}}, 8, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        b.pop();
        canette(b, CBX + 1.4f, 11.6f, CBZ + 1.0f, CAN_ORANGE, 60);
        papierFroisse(b, CBX + 0.2f, 12.2f, CBZ - 0.8f, 0.7f, PAPIER, 113);
        // sac déchiré par le fond : déchets tombés au pied
        champ(b, 4.6f, 7.4f, 11.8f, 12.4f, 0.15f, 0.5f, 0.8f, 6, 5, 114, ORDURES);
        canetteEcrasee(b, 11.8f, 13.4f, CAN_COLA, 20, 115);
        bouteillePet(b, 6.0f, 13.6f, 15, PET_JUS, YELLOW, 0.8f);
        papierFroisse(b, 9.4f, 0.3f, 10.0f, 0.8f, JOURNAL, 116);
        papierFroisse(b, 4.0f, 0, 9.0f, 0.6f, PAPIER, 117);
        feuille(b, 10.4f, 0, 14.0f, 2.6f, 3.0f, 30, 0.1f, P_TICKET);
        jonchee(b, 2.0f, 6.0f, 14.0f, 15.6f, 7, FEUILLES, 1.3f, 118);
        // usure : rouille sur le poteau, autocollant, boue
        decalCyl(b, COULURE_1, CBX, zp, 0.47f, 90, 9.0f, 0.6f, 1.2f, 0);
        decalCyl(b, ETIQUETTE_DECHIREE.asFit(), CBX, zp, 0.47f, 70, 6.0f, 0.8f, 0.5f, 0);
        decalCyl(b, BOUE_2, CBX, zp, 0.46f, 110, 0.9f, 0.9f, 0.9f, 0);
    }

    /** Sac transparent (passe translucide) : rabattu sur le cerceau, plein, fond déchiré. */
    static void corbeilleSac(MeshBuilder b) {
        b.push().translate(CBX, 0, CBZ);
        b.lathe(new float[][]{{CBR + 0.25f, CBY - 1.6f}, {CBR + 0.32f, CBY + 0.1f}, {CBR + 0.05f, CBY + 0.35f}, {CBR - 0.15f, CBY + 0.1f}, {CBR - 0.1f, CBY - 1.0f},
                {CBR + 0.2f, CBY - 4.0f}, {CBR + 0.35f, 8.0f}, {CBR + 0.1f, 5.6f}, {2.6f, 4.2f}, {2.0f, 3.4f}}, 18, 0, 360, SAC_TRANSP, 1, 1, 0.06f, 0.5f, 119, false);
        b.pop();
    }

    // =================================================================== 24. bac de recyclage

    /** Bac de tri (corps gris, couvercle jaune) débordant de bouteilles plastique et de cartons, couvercle soulevé. */
    static void bacRecyclage(MeshBuilder b) {
        float cx = 8.0f, cz = 5.3f;
        b.color(0.5f, 0.52f, 0.54f);
        LotT1a.bac(b, cx, cz, PLASTIQUE_USE, null, 0);
        b.color(0.86f, 0.7f, 0.18f);
        LotT1a.couvercleBac(b, cx, cz, PLASTIQUE_USE, 11);
        b.color(1);
        float yp = 13.95f, zp = cz - 4.25f;
        b.push().translate(0, yp, zp).rotateX(-11).translate(0, -yp, -zp);
        decalO(b, L_TRI, 'y', cx, yp + 0.75f, zp + 4.6f, 5.6f, 2.8f, 0, 0.05f);
        decalO(b, FISSURE, 'y', cx + 2.0f, yp + 0.75f, zp + 7.4f, 2.0f, 2.0f, 40, 0.09f);
        b.pop();
        // contenu qui déborde : bouteilles, canettes, cartons pliés
        champ(b, cx - 3.5f, cz - 3.9f, cx + 3.5f, cz + 3.9f, 12.6f, 0.6f, 0.8f, 6, 6, 121, ORDURES);
        Mat[] et = {PET_EAU, PET_SODA, PET_JUS, PET_EAU, PET_HUILE};
        Random r = new Random(122);
        for (int k = 0; k < 6; k++) {
            float x = cx - 2.6f + r.nextFloat() * 5.2f, z = cz - 2.6f + r.nextFloat() * 5.6f;
            b.push().translate(0, 12.5f + (k % 2) * 0.5f + (z > cz + 2 ? 0.6f : 0), 0);
            bouteillePet(b, x, z, r.nextFloat() * 360, et[k % et.length], k % 2 == 0 ? PLASTIC_BLUE : PAINT_RED, 0.75f + r.nextFloat() * 0.25f);
            b.pop();
        }
        b.push().translate(cx - 1.4f, 12.6f, cz + 3.4f).rotateY(12).rotateX(-14);
        b.bevelBox(-2.8f, 0, -0.14f, 2.8f, 3.2f, 0.14f, 0.05f, CARTON, new Mat[]{null, null, null, null, null, CT_LESSIVE}, 0);
        b.pop();
        canette(b, cx + 2.4f, 13.0f, cz + 2.0f, CAN_MENTHE, 90);
        // tombés au pied
        bouteillePet(b, 12.6f, 12.0f, 70, PET_SODA, PAINT_RED, 0.55f);
        bouteillePet(b, 4.2f, 13.6f, -20, PET_EAU, PLASTIC_BLUE, 0.9f);
        canetteEcrasee(b, 9.0f, 14.6f, CAN_ORANGE, 15, 123);
        b.push().translate(2.4f, 0, 9.6f).rotateY(80);
        b.bevelBox(-3.0f, 0, -0.14f, 3.0f, 0.28f, 4.0f, 0.05f, CARTON_MOUILLE);
        b.pop();
        // usure
        decalT(b, L_TRI, cx, 9.0f, LotT1a.avantBac(cz, 9.0f), 4.4f, 2.2f, 0, 2.56f, 0, 0.06f);
        decalT(b, GRIFFURES, cx - 1.4f, 5.0f, LotT1a.avantBac(cz, 5.0f), 2.6f, 1.3f, 0, 2.56f, 25, 0.06f);
        decalT(b, BOUE_1, cx, 1.9f, LotT1a.avantBac(cz, 1.9f), 6.0f, 1.8f, 0, 2.56f, 0, 0.06f);
        decalT(b, AUREOLE, LotT1a.flancBac(cx, 7.0f), 7.0f, cz - 0.1f, 2.4f, 2.4f, 90, 2.33f, 0, 0.06f);
        jonchee(b, 1.0f, 11.0f, 15.0f, 15.6f, 4, PAPIER, 1.0f, 124);
    }

    // =================================================================== 25. présentoir à journaux

    static final float PX0 = 4.3f, PX1 = 11.7f, PZ0 = 0.5f, PZ1 = 7.1f, PY0 = 4.8f, PY1 = 12.8f;

    /** Caisse distributrice de journaux en tôle (≈ 1 m) sur pied, contre le mur : porte vitrée sale entrouverte, rouille. */
    static void presentoir(MeshBuilder b) {
        Mat t = PEINTURE_USEE;
        b.color(0.25f, 0.38f, 0.62f);
        // pied : platine, colonne, berceau
        b.bevelBox(5.0f, 0, 1.0f, 11.0f, 0.3f, 6.6f, 0.08f, t);
        b.bevelBox(7.2f, 0.3f, 2.9f, 8.8f, PY0 - 0.6f, 4.5f, 0.12f, t);
        b.bevelBox(PX0 + 0.4f, PY0 - 0.6f, PZ0 + 0.4f, PX1 - 0.4f, PY0, PZ1 - 0.4f, 0.1f, t);
        // caisse : fond, flancs, toit en pente, bandeau
        b.bevelBox(PX0 + 0.45f, PY0, PZ0 + 0.45f, PX1 - 0.45f, PY0 + 0.6f, PZ1, 0.1f, t);
        b.bevelBox(PX0, PY0, PZ0, PX0 + 0.45f, PY1, PZ1, 0.1f, t);
        b.bevelBox(PX1 - 0.45f, PY0, PZ0, PX1, PY1, PZ1, 0.1f, t);
        b.bevelBox(PX0 + 0.45f, PY0, PZ0, PX1 - 0.45f, PY1, PZ0 + 0.45f, 0.08f, t);
        b.extrudeProfileX(new float[][]{{PZ0 - 0.1f, PY1}, {PZ0 - 0.1f, PY1 + 0.6f}, {PZ1 + 0.3f, PY1 + 1.3f}, {PZ1 + 0.3f, PY1 + 0.9f}}, PX0 - 0.1f, PX1 + 0.1f, t);
        b.face(t, v(-1, 0, 0), p(PX0 - 0.1f, PY1, PZ0 - 0.1f), p(PX0 - 0.1f, PY1 + 0.9f, PZ1 + 0.3f), p(PX0 - 0.1f, PY1 + 1.3f, PZ1 + 0.3f), p(PX0 - 0.1f, PY1 + 0.6f, PZ0 - 0.1f));
        b.face(t, v(1, 0, 0), p(PX1 + 0.1f, PY1, PZ0 - 0.1f), p(PX1 + 0.1f, PY1 + 0.6f, PZ0 - 0.1f), p(PX1 + 0.1f, PY1 + 1.3f, PZ1 + 0.3f), p(PX1 + 0.1f, PY1 + 0.9f, PZ1 + 0.3f));
        b.face(t, v(0, -1, 0), p(PX0 - 0.1f, PY1, PZ0 - 0.1f), p(PX1 + 0.1f, PY1, PZ0 - 0.1f), p(PX1 + 0.1f, PY1 + 0.9f, PZ1 + 0.3f), p(PX0 - 0.1f, PY1 + 0.9f, PZ1 + 0.3f));
        // monnayeur sur le toit
        b.bevelBox(PX1 - 2.6f, PY1 + 0.8f, PZ0 + 2.6f, PX1 - 0.6f, PY1 + 2.6f, PZ1 - 0.6f, 0.15f, t);
        b.color(1);
        b.box(PX1 - 1.75f, PY1 + 1.6f, PZ1 - 0.58f, PX1 - 1.45f, PY1 + 2.3f, PZ1 - 0.55f, DARK);
        b.face(E_PRESSE, v(0, 0, 1), p(PX0 + 0.6f, PY1 - 2.0f, PZ1 + 0.06f), p(PX1 - 0.6f, PY1 - 2.0f, PZ1 + 0.06f), p(PX1 - 0.6f, PY1 - 0.1f, PZ1 + 0.06f), p(PX0 + 0.6f, PY1 - 0.1f, PZ1 + 0.06f));
        b.color(0.25f, 0.38f, 0.62f);
        b.bevelBox(PX0 + 0.45f, PY1 - 2.1f, PZ1 - 0.5f, PX1 - 0.45f, PY1, PZ1, 0.08f, t);
        b.color(1);
        // journaux à l'intérieur (pile, une visible)
        b.bevelBox(PX0 + 0.8f, PY0 + 0.6f, PZ0 + 0.8f, PX1 - 0.8f, PY0 + 2.2f, PZ1 - 1.4f, 0.08f, TRANCHE, new Mat[]{null, null, null, null, null, null}, 0);
        b.push().translate(8.0f, PY0 + 2.2f, PZ0 + 1.0f).rotateX(12);
        b.bevelBox(-2.9f, 0, -0.1f, 2.9f, 4.2f, 0.1f, 0.03f, JOURNAL, new Mat[]{null, null, null, null, null, UNE}, 0);
        b.pop();
        // porte vitrée (charnière en bas), entrouverte
        b.color(0.25f, 0.38f, 0.62f);
        b.push().translate(0, PY0 + 0.6f, PZ1).rotateX(16).translate(0, -(PY0 + 0.6f), -PZ1);
        b.bevelBox(PX0 + 0.45f, PY0 + 0.6f, PZ1 - 0.4f, PX1 - 0.45f, PY0 + 1.6f, PZ1, 0.08f, t);
        b.bevelBox(PX0 + 0.45f, PY1 - 2.6f, PZ1 - 0.4f, PX1 - 0.45f, PY1 - 2.15f, PZ1, 0.08f, t);
        b.bevelBox(PX0 + 0.45f, PY0 + 1.6f, PZ1 - 0.4f, PX0 + 1.1f, PY1 - 2.6f, PZ1, 0.08f, t);
        b.bevelBox(PX1 - 1.1f, PY0 + 1.6f, PZ1 - 0.4f, PX1 - 0.45f, PY1 - 2.6f, PZ1, 0.08f, t);
        b.color(1);
        b.bevelBox(7.2f, PY0 + 0.9f, PZ1, 8.8f, PY0 + 1.3f, PZ1 + 0.5f, 0.12f, CHROME_PIQUE);
        b.pop();
        // usure : rouille, coulures, autocollants, chaîne cassée pendante, boue
        decalT(b, COULURE_1, 8.0f, 3.0f, 4.5f, 1.2f, 2.4f, 0, 0, 0, 0.06f);
        decalT(b, ETIQUETTE_DECHIREE, PX1, PY1 - 1.4f, 3.6f, 2.8f, 1.4f, 90, 0, -6, 0.06f);
        decalT(b, ROUILLE.asFit().sub(0, 0, 0.5f, 0.5f), PX0, PY0 + 2.6f, 3.0f, 3.0f, 2.4f, -90, 0, 10, 0.06f);
        decalT(b, COULURE_2, PX0, PY1 - 1.6f, 5.0f, 1.2f, 2.4f, -90, 0, 0, 0.06f);
        decalT(b, BOUE_1, 8.0f, 1.6f, 4.5f, 1.6f, 2.0f, 0, 0, 0, 0.11f);
        b.chain(new float[][]{p(PX1 + 0.05f, 9.0f, 2.6f), p(PX1 + 0.4f, 6.0f, 3.2f), p(PX1 + 0.3f, 3.6f, 3.4f)}, 0.6f, 0.4f, 0.1f, ROUILLE, 6, 3);
        feuille(b, 11.6f, 0, 11.8f, 4.0f, 3.0f, -25, 0.14f, UNE);
        feuille(b, 4.6f, 0, 10.6f, 3.0f, 2.4f, 40, 0.1f, JOURNAL);
        jonchee(b, 1.0f, 8.6f, 15.0f, 15.6f, 6, FEUILLES, 1.3f, 131);
    }

    /** Vitre de la porte (sale) suivant l'ouverture. */
    static void presentoirVerre(MeshBuilder b) {
        b.push().translate(0, PY0 + 0.6f, PZ1).rotateX(16).translate(0, -(PY0 + 0.6f), -PZ1);
        float z = PZ1 - 0.2f;
        b.face(VERRE_SALE, v(0, 0, 1), p(PX0 + 1.1f, PY0 + 1.6f, z), p(PX1 - 1.1f, PY0 + 1.6f, z), p(PX1 - 1.1f, PY1 - 2.6f, z), p(PX0 + 1.1f, PY1 - 2.6f, z));
        b.pop();
    }

    static Vector3f pt(Matrix4f t, float x, float y, float z) {
        return t.transformPosition(x, y, z, new Vector3f());
    }
}
