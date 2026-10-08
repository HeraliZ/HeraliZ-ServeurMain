package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BOTTLE_BROWN;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BOTTLE_GREEN;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BRASS;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.DARK;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.ENAMEL;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.PAINT_RED;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.PLASTIC_GREY;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.WHITE;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Lot T2b, atelier, cave et cuisine : établi, servante d'atelier, tonneau sur chantier, casier à bouteilles, étagère
 * de cave, congélateur coffre, cuisinière à bois.
 */
final class LotT2bAtelier {
    private LotT2bAtelier() {
    }

    // =================================================================== établi
    /**
     * Établi de garage (1,5 m x 60 x 85 cm) : plateau épais taché d'huile, étau bleu-gris rouillé, dosseret à râtelier
     * (tournevis, ciseaux à bois), tiroirs (l'un tiré, vis et clous), tablette basse (pots de peinture, caisse) ;
     * marteau, scie, burette, chiffon, chutes de bois ; brûlure, toile sous l'établi.
     */
    static void etabli(MeshBuilder b) {
        Mat bo = CHENE;
        float x0 = 0.5f, x1 = 24.5f, z0 = 0.5f, zf = 10.1f, yt = 13.6f, ep = 1.0f;
        b.color(1.05f, 0.98f, 0.9f);
        for (float x : new float[]{x0 + 0.5f, x1 - 1.9f}) {
            bois(b, x, 0, z0 + 0.3f, x + 1.4f, yt - ep + 0.02f, z0 + 1.7f, 0.12f, bo, NZ);
            bois(b, x, 0, zf - 1.7f, x + 1.4f, yt - ep + 0.02f, zf - 0.3f, 0.12f, bo);
        }
        bois(b, x0 + 1.88f, 2.2f, zf - 1.6f, x1 - 1.88f, 3.2f, zf - 0.4f, 0.1f, bo);
        bois(b, x0 + 1.88f, 2.2f, z0 + 0.4f, x1 - 1.88f, 3.2f, z0 + 1.6f, 0.1f, bo, NZ);
        for (float x : new float[]{x0 + 0.6f, x1 - 1.8f}) bois(b, x, 2.2f, z0 + 1.68f, x + 1.2f, 3.2f, zf - 1.68f, 0.1f, bo);
        b.color(0.9f, 0.86f, 0.8f);
        for (int k = 0; k < 4; k++) bois(b, x0 + 1.95f, 3.18f, z0 + 1.65f + k * 1.6f, x1 - 1.95f, 3.5f, z0 + 1.65f + k * 1.6f + 1.56f, 0.05f, SAPIN);
        b.color(1.12f, 1.04f, 0.94f);
        bois(b, x0, yt - ep, z0, x1, yt, zf, 0.15f, bo, NZ);
        // caisson de tiroirs sous le plateau
        b.color(0.95f, 0.9f, 0.84f);
        bois(b, 12.8f, 9.6f, z0 + 1.0f, 21.8f, yt - ep + 0.02f, zf - 0.6f, 0.08f, bo);
        tiroirAtelier(b, 12.95f, 9.75f, 17.2f, 12.45f, zf - 0.45f, 3.0f);
        b.color(0.95f, 0.9f, 0.84f);
        tiroirAtelier(b, 17.4f, 9.75f, 21.65f, 12.45f, zf - 0.45f, 0);
        b.color(1);
        // dosseret à râtelier
        b.color(0.95f, 0.9f, 0.84f);
        bois(b, x0 + 0.5f, yt - 0.05f, z0 + 0.05f, x1 - 0.5f, yt + 2.5f, z0 + 0.75f, 0.1f, SAPIN, NZ);
        bois(b, x0 + 3.0f, yt + 1.3f, z0 + 0.7f, 14.0f, yt + 1.8f, z0 + 1.9f, 0.06f, SAPIN);
        b.color(1);
        float[] cols = {0.7f, 0.2f, 0.18f, 0.85f, 0.7f, 0.2f, 0.25f, 0.4f, 0.65f};
        for (int k = 0; k < 6; k++) {
            float x = x0 + 4.0f + k * 1.6f;
            if (k == 3) continue;
            boolean ciseau = k >= 4;
            b.push().translate(x, yt + 1.8f, z0 + 1.3f);
            if (ciseau) {
                b.color(1.0f, 0.92f, 0.8f);
                b.cylinder(0.28f, 0, 1.0f, 7, CHENE, null, CHENE);
            } else {
                b.color(cols[k % 3 * 3], cols[k % 3 * 3 + 1], cols[k % 3 * 3 + 2]);
                b.lathe(new float[][]{{0.2f, 0}, {0.32f, 0.15f}, {0.32f, 0.8f}, {0.22f, 1.0f}, {0.01f, 1.02f}}, 8, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
            }
            b.color(1);
            b.cylinder(ciseau ? 0.16f : 0.07f, -1.5f, 0.02f, 5, METAL_NU, METAL_NU, null);
            b.pop();
        }
        b.push().translate(19.0f, yt + 0.1f, 7.6f).rotateX(-90);
        cle(b, 0, 0, 0, 35);
        cle(b, 1.4f, 0.4f, 0, -20);
        b.pop();
        // étau
        etau(b, 4.0f, yt, zf - 1.2f);
        // outils et objets sur le plateau
        marteau(b, 9.6f, yt, 7.4f, 30);
        scie(b, 15.0f, yt, 5.0f, -12);
        potPeinture(b, 20.6f, yt, 4.0f, 0.42f, 0.55f, 0.62f);
        burette(b, 22.6f, yt, 7.2f);
        b.push().translate(11.6f, yt + 0.35f, 3.2f).scale(1.4f, 0.45f, 1.1f);
        b.sphereSection(1.0f, 10, 4, 0, 90, TORCHON);
        b.pop();
        b.color(0.95f, 0.88f, 0.78f);
        b.push().translate(7.2f, yt, 3.4f).rotateY(14);
        b.bevelBox(-1.8f, 0, -0.6f, 1.8f, 0.45f, 0.6f, 0.06f, SAPIN);
        b.bevelBox(-1.2f, 0.43f, -0.45f, 1.6f, 0.75f, 0.35f, 0.05f, SAPIN);
        b.pop();
        b.color(1);
        // tablette basse : pots de peinture, caisse
        potPeinture(b, 5.0f, 3.5f, 6.0f, 0.68f, 0.24f, 0.2f);
        potPeinture(b, 7.1f, 3.5f, 5.6f, 0.82f, 0.8f, 0.72f);
        potPeinture(b, 6.0f, 3.5f + 2.1f, 5.8f, 0.4f, 0.55f, 0.38f);
        b.push().translate(17.0f, 3.5f, 4.8f).rotateY(-6);
        b.bevelBox(-2.6f, 0, -1.8f, 2.6f, 2.4f, 1.8f, 0.1f, CARTON_MOUILLE);
        b.pop();
        // usure
        b.color(0.5f, 0.45f, 0.4f);
        decal(b, AUREOLE, 'y', 17.6f, yt, 7.6f, 3.4f, 2.6f, 25);
        decal(b, AUREOLE, 'y', 6.6f, yt, 6.0f, 2.4f, 1.8f, -10);
        b.color(1);
        decal(b, BRULURE, 'y', 13.2f, yt, 8.4f, 1.4f, 1.4f, 0);
        toile(b, x1 - 1.95f, 3.18f, zf - 1.8f, 3.0f, 3.0f, 180, 0);
        toile(b, x0 + 1.95f, yt - ep, 4.0f, 2.6f, 2.4f, 0, 0);
        epars(b, 2.0f, 10.4f, 22.0f, 13.5f, 10, PAPIER, 0.7f, 101);
    }

    /** Tiroir d'établi (façade simple, poignée de fer) ; sortie > 0 : tiré, vis et clous dedans. */
    static void tiroirAtelier(MeshBuilder b, float x0, float y0, float x1, float y1, float zf, float s) {
        bois(b, x0, y0, zf - 0.5f + s, x1, y1, zf + s, 0.1f, CHENE);
        b.color(1);
        float xm = (x0 + x1) / 2;
        b.tube(new float[][]{p(xm - 0.9f, (y0 + y1) / 2 + 0.2f, zf + s), p(xm - 0.7f, (y0 + y1) / 2 + 0.2f, zf + s + 0.45f), p(xm + 0.7f, (y0 + y1) / 2 + 0.2f, zf + s + 0.45f), p(xm + 0.9f, (y0 + y1) / 2 + 0.2f, zf + s)},
                fill(4, 0.09f), 5, ROUILLE, false, false, 0, false);
        if (s > 0) {
            float zr = zf - 0.5f + s - 6.0f;
            b.color(0.85f, 0.8f, 0.72f);
            bois(b, x0 + 0.1f, y0 + 0.1f, zr, x0 + 0.35f, y1 - 0.5f, zf - 0.47f + s, 0.04f, CHENE);
            bois(b, x1 - 0.35f, y0 + 0.1f, zr, x1 - 0.1f, y1 - 0.5f, zf - 0.47f + s, 0.04f, CHENE);
            bois(b, x0 + 0.33f, y0 + 0.1f, zr, x1 - 0.33f, y0 + 0.32f, zf - 0.47f + s, 0.03f, CHENE);
            b.color(1);
            java.util.Random r = new java.util.Random(5);
            for (int k = 0; k < 9; k++) {
                b.push().translate(x0 + 0.8f + r.nextFloat() * (x1 - x0 - 1.6f), y0 + 0.4f, zf + s - 0.9f - r.nextFloat() * 2.6f).rotateY(r.nextFloat() * 180).rotateZ(90);
                b.cylinder(0.05f, -0.4f, 0.4f, 4, ROUILLE, null, ROUILLE);
                b.pop();
            }
        }
    }

    /** Étau d'établi (fonte peinte bleu-gris) fixé au bord avant du plateau (bord en z). */
    static void etau(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z);
        b.color(0.42f, 0.5f, 0.58f);
        b.lathe(new float[][]{{1.4f, 0}, {1.4f, 0.25f}, {1.1f, 0.4f}}, 14, 0, 360, PEINTURE_USEE, 1, 1, 0, 0, 0, false);
        b.bevelBox(-1.1f, 0.38f, -1.0f, 1.1f, 1.4f, 1.6f, 0.2f, PEINTURE_USEE);
        b.bevelBox(-1.6f, 1.35f, -1.2f, 1.6f, 2.3f, -0.4f, 0.2f, PEINTURE_USEE);
        b.bevelBox(-1.6f, 1.35f, 0.6f, 1.6f, 2.3f, 1.4f, 0.2f, PEINTURE_USEE);
        b.bevelBox(-0.7f, 0.6f, 1.3f, 0.7f, 1.6f, 2.6f, 0.15f, PEINTURE_USEE);
        b.color(1);
        b.bevelBox(-1.55f, 1.5f, -0.42f, 1.55f, 2.25f, -0.32f, 0.03f, METAL_NU);
        b.bevelBox(-1.55f, 1.5f, 0.52f, 1.55f, 2.25f, 0.62f, 0.03f, METAL_NU);
        b.push().translate(0, 1.0f, 2.6f).rotateX(90);
        b.cylinder(0.22f, 0, 1.0f, 8, METAL_NU, null, METAL_NU);
        b.pop();
        b.tube(new float[][]{p(-1.5f, 1.0f, 3.4f), p(1.4f, 1.0f, 3.4f)}, fill(2, 0.11f), 6, METAL_NU, true, true, 0, false);
        for (float s : new float[]{-1.5f, 1.4f}) {
            b.push().translate(s, 1.0f, 3.4f);
            b.sphere(0.2f, 6, 4, METAL_NU);
            b.pop();
        }
        coulure(b, 'z', 0.6f, 1.3f, 1.6f, 0.7f, 0);
        b.pop();
    }

    static void marteau(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y + 0.28f, z).rotateY(ang);
        b.color(1.15f, 1.05f, 0.9f);
        b.push().rotateZ(90);
        b.cylinder(0.2f, -2.4f, 1.6f, 7, SAPIN, SAPIN, SAPIN);
        b.pop();
        b.color(1);
        b.bevelBox(-2.75f, -0.28f, -0.3f, -2.15f, 1.05f, 0.3f, 0.08f, ROUILLE);
        b.pop();
    }

    static void scie(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y + 0.04f, z).rotateY(ang).rotateX(-90);
        b.slab(new float[][]{{-4.6f, -0.9f}, {1.8f, -1.6f}, {1.8f, 1.2f}, {-4.6f, 0.0f}}, 0.06f, 0.01f, false, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.push().translate(x, y, z).rotateY(ang).translate(2.9f, 0, 0);
        b.color(1.05f, 0.95f, 0.85f);
        b.bevelBox(-1.2f, 0, -1.2f, 1.0f, 0.4f, 1.2f, 0.12f, CHENE);
        b.color(1);
        b.pop();
    }

    /** Pot de peinture (étiquette teintée, coulures sèches, couvercle). */
    static void potPeinture(MeshBuilder b, float x, float y, float z, float r, float g, float bl) {
        b.push().translate(x, y, z);
        b.cylinder(1.0f, 0, 2.0f, 12, METAL_NU, null, METAL_NU);
        b.color(r, g, bl);
        b.cylinder(1.04f, 0.4f, 1.6f, 12, PEINTURE_USEE, null, null);
        b.push().translate(0, 2.0f, 0);
        b.torus(0.96f, 0.06f, 12, 3, 0, 360, PEINTURE_USEE);
        b.pop();
        b.color(1);
        b.tube(new float[][]{p(-0.98f, 1.7f, 0), p(-0.7f, 2.4f, 0), p(0.7f, 2.4f, 0), p(0.98f, 1.7f, 0)}, fill(4, 0.05f), 4, METAL_NU, false, false, 0, false);
        b.pop();
    }

    static void burette(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z);
        b.lathe(new float[][]{{0.01f, 0}, {0.9f, 0}, {0.95f, 0.2f}, {0.9f, 0.8f}, {0.35f, 1.3f}, {0.15f, 1.5f}}, 10, 0, 360, ROUILLE, 1, 1, 0, 0, 0, false);
        b.tube(new float[][]{p(0, 1.45f, 0), p(0.4f, 2.0f, 0.2f), p(1.1f, 2.6f, 0.5f)}, new float[]{0.12f, 0.08f, 0.04f}, 5, BRASS, false, true, 0, false);
        b.pop();
    }

    /** Clé plate suspendue (vue de face). */
    static void cle(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y, z).rotateZ(ang);
        b.bevelBox(-0.16f, -1.4f, -0.05f, 0.16f, 0.4f, 0.05f, 0.03f, METAL_NU);
        b.push().translate(0, 0.55f, 0).rotateX(90);
        b.torus(0.32f, 0.1f, 8, 3, 0, 360, METAL_NU);
        b.pop();
        b.push().translate(0, -1.6f, 0).rotateX(90);
        b.torus(0.3f, 0.1f, 8, 3, 20, 340, METAL_NU);
        b.pop();
        b.pop();
    }

    // =================================================================== servante
    /**
     * Servante d'atelier à roulettes (70 x 45 x 85 cm), tôle rouge passé, rouillée, poussée de travers : six tiroirs à
     * poignées alu, le deuxième ouvert (douilles, clés), bac du dessus à rebord (clé, chiffon), poignée latérale,
     * autocollant usé, coulures.
     */
    static void servante(MeshBuilder b) {
        b.push().translate(8.0f, 0, 4.9f).rotateY(-6);
        float w = 5.6f, d = 3.6f, yb = 1.7f, yt = 13.2f;
        rouge(b);
        b.bevelBox(-w, yb, -d, w, yt, d, 0.25f, PEINTURE_USEE, null, NZ);
        b.bevelBox(-w - 0.15f, yt - 0.02f, -d - 0.15f, w + 0.15f, yt + 0.25f, d + 0.15f, 0.08f, PEINTURE_USEE);
        b.bevelBox(-w - 0.15f, yt + 0.2f, d - 0.05f, w + 0.15f, yt + 0.9f, d + 0.15f, 0.04f, PEINTURE_USEE);
        b.bevelBox(-w - 0.15f, yt + 0.2f, -d - 0.15f, w + 0.15f, yt + 0.9f, -d + 0.05f, 0.04f, PEINTURE_USEE);
        b.bevelBox(-w - 0.15f, yt + 0.2f, -d + 0.07f, -w + 0.05f, yt + 0.9f, d - 0.07f, 0.04f, PEINTURE_USEE);
        b.bevelBox(w - 0.05f, yt + 0.2f, -d + 0.07f, w + 0.15f, yt + 0.9f, d - 0.07f, 0.04f, PEINTURE_USEE);
        float[] hs = {1.4f, 1.4f, 1.6f, 1.8f, 2.2f, 2.6f};
        float y = yt - 0.35f;
        for (int k = 0; k < hs.length; k++) {
            float y1 = y, y0 = y - hs[k], s = k == 1 ? 4.0f : 0;
            rouge(b);
            b.bevelBox(-w + 0.3f, y0 + 0.08f, d - 0.05f + s, w - 0.3f, y1 - 0.08f, d + 0.14f + s, 0.06f, PEINTURE_USEE);
            b.color(1);
            b.bevelBox(-w + 0.8f, y1 - 0.5f, d + 0.12f + s, w - 0.8f, y1 - 0.22f, d + 0.36f + s, 0.06f, ALU);
            if (s > 0) {
                b.bevelBox(-w + 0.4f, y0 + 0.15f, d - 6.4f + s, -w + 0.6f, y1 - 0.3f, d + s, 0.03f, METAL_NU);
                b.bevelBox(w - 0.6f, y0 + 0.15f, d - 6.4f + s, w - 0.4f, y1 - 0.3f, d + s, 0.03f, METAL_NU);
                b.bevelBox(-w + 0.58f, y0 + 0.15f, d - 6.4f + s, w - 0.58f, y0 + 0.4f, d + s, 0.03f, DARK);
                for (int q = 0; q < 6; q++) {
                    b.push().translate(-w + 1.3f + q * 1.6f, y0 + 0.4f, d + s - 1.2f);
                    b.cylinder(0.32f + q * 0.04f, 0, 0.7f, 8, CHROME_PIQUE, null, DARK);
                    b.pop();
                }
                for (int q = 0; q < 3; q++) {
                    b.push().translate(-w + 2.2f + q * 2.6f, y0 + 0.45f, d + s - 3.2f).rotateY(80 + q * 9);
                    b.bevelBox(-1.6f, 0, -0.14f, 1.6f, 0.1f, 0.14f, 0.03f, CHROME_PIQUE);
                    b.pop();
                }
            }
            y = y0;
        }
        decal(b, P_SERVANTE, 'z', 0, yt - 1.35f, d + 0.14f, 2.8f, 0.7f);
        b.push().translate(w - 0.9f, yt - 0.18f, d - 0.02f).rotateX(90);
        b.cylinder(0.28f, 0, 0.18f, 8, CHROME_PIQUE, null, DARK);
        b.pop();
        // roulettes
        for (float x : new float[]{-w + 0.9f, w - 0.9f}) {
            for (float z : new float[]{-d + 0.9f, d - 0.9f}) {
                b.bevelBox(x - 0.4f, yb - 0.35f, z - 0.4f, x + 0.4f, yb + 0.02f, z + 0.4f, 0.05f, METAL_NU);
                b.bevelBox(x - 0.32f, 0.6f, z - 0.3f, x - 0.22f, yb - 0.33f, z + 0.3f, 0.02f, METAL_NU);
                b.bevelBox(x + 0.22f, 0.6f, z - 0.3f, x + 0.32f, yb - 0.33f, z + 0.3f, 0.02f, METAL_NU);
                b.push().translate(x, 0.62f, z).rotateZ(90);
                b.cylinder(0.62f, -0.2f, 0.2f, 10, CAOUTCHOUC_USE, DARK, DARK);
                b.pop();
            }
        }
        // poignée latérale
        b.tube(new float[][]{p(w, yt - 1.0f, -d + 0.8f), p(w + 0.9f, yt - 1.0f, -d + 1.1f), p(w + 0.9f, yt - 1.0f, d - 1.1f), p(w, yt - 1.0f, d - 0.8f)}, fill(4, 0.16f), 6, CHROME_PIQUE, false, false, 0, false);
        // bac : clé, chiffon
        b.push().translate(-2.0f, yt + 0.24f, 0.2f).rotateY(25);
        b.bevelBox(-1.8f, 0, -0.16f, 1.8f, 0.1f, 0.16f, 0.03f, CHROME_PIQUE);
        b.pop();
        b.push().translate(2.4f, yt + 0.25f, -0.4f).scale(1.6f, 0.5f, 1.2f);
        b.sphereSection(1.0f, 10, 4, 0, 90, TORCHON);
        b.pop();
        // usure
        coulure(b, 'z', -w + 0.6f, yt - 0.4f, d + 0.14f, 0.8f, 0);
        coulure(b, 'X', w, 7.0f, 0.0f, 1.2f, 2);
        decal(b, GRIFFURES, 'X', w, 4.6f, -0.6f, 3.0f, 1.4f, 0);
        b.pop();
        decal(b, AUREOLE, 'y', 11.6f, 0, 11.4f, 3.0f, 2.2f, 10);
    }

    static void rouge(MeshBuilder b) {
        b.color(0.66f, 0.25f, 0.2f);
    }

    // =================================================================== tonneau
    static float rayonTonneau(float y, float L, float rb, float rt) {
        return rt + (rb - rt) * (float) Math.sin(Math.PI * Math.max(0, Math.min(1, y / L)));
    }

    /**
     * Tonneau de vin (110 L) couché sur son chantier, fond tourné vers l'avant : douelles tachées de vin et de
     * moisissure, cercles rouillés, fond marqué à la craie, bonde, cannelle de laiton qui a coulé (flaque séchée) ;
     * bouteille vide au sol, toiles d'araignée.
     */
    static void tonneau(MeshBuilder b) {
        float cx = 8.0f, cy = 6.9f, zA = 1.0f, L = 12.0f, rb = 4.5f, rt = 3.65f;
        // chantier : longrines, traverses à cales
        b.color(0.72f, 0.68f, 0.62f);
        for (float x : new float[]{3.0f, 11.8f}) bois(b, x, 0, zA + 0.2f, x + 1.2f, 0.9f, zA + L - 0.2f, 0.1f, SAPIN);
        for (float z : new float[]{zA + 2.0f, zA + L - 3.2f}) {
            bois(b, 2.4f, 0.85f, z, 13.6f, 2.4f, z + 1.2f, 0.1f, SAPIN);
            b.push().translate(0, 0, z + 0.05f);
            b.prism(new float[][]{{3.2f, 2.38f}, {6.0f, 2.38f}, {3.2f, 6.6f}}, 1.1f, SAPIN, SAPIN);
            b.prism(new float[][]{{12.8f, 2.38f}, {12.8f, 6.6f}, {10.0f, 2.38f}}, 1.1f, SAPIN, SAPIN);
            b.pop();
        }
        b.color(1);
        // tonneau
        b.push().translate(cx, cy, zA).rotateX(90);
        float[][] prof = new float[11][];
        for (int k = 0; k <= 10; k++) {
            float y = k * L / 10;
            prof[k] = new float[]{rayonTonneau(y, L, rb, rt), y};
        }
        b.lathe(prof, 20, 0, 360, TONNEAU, 1, 1, 0, 0, 0, false);
        b.hollowCylinder(rt, rt - 0.3f, L - 0.4f, L, 20, TONNEAU);
        b.push().translate(0, L - 0.4f, 0);
        b.disc(rt - 0.29f, 0, 20, FOND_TONNEAU, true);
        b.pop();
        b.disc(rt, 0.02f, 20, TONNEAU, false);
        // cercles : chacun sur un seul segment du profil, parallèle aux douelles (décalé de 0,15 le long de la normale)
        for (float y : new float[]{0.3f, 1.5f, 2.65f, 8.65f, 9.95f, 11.15f}) {
            int k = Math.min(9, (int) (y / (L / 10)));
            float ya = prof[k][1], yb = prof[k + 1][1], ra = prof[k][0], rb2 = prof[k + 1][0];
            float t0 = (y - ya) / (yb - ya), t1 = (y + 0.55f - ya) / (yb - ya);
            float dr = rb2 - ra, dy = yb - ya, ln = (float) Math.hypot(dr, dy), nr = dy / ln * 0.15f, ny = -dr / ln * 0.15f;
            float r0 = ra + dr * t0, y0 = ya + dy * t0, r1 = ra + dr * t1, y1 = ya + dy * t1;
            b.lathe(new float[][]{{r0, y0}, {r0 + nr, y0 + ny}, {r1 + nr, y1 + ny}, {r1, y1}}, 20, 0, 360, ROUILLE, 1, 1, 0, 0, 0, false);
        }
        // bonde sur le dessus (local -Z = monde +Y)
        b.push().translate(0, L * 0.5f, -rb + 0.15f).rotateX(-90);
        b.cylinder(0.6f, 0, 0.4f, 10, CHENE, null, CHENE);
        b.pop();
        b.pop();
        // cannelle de laiton (robinet) sur le fond, en bas
        float yr = cy - 2.1f, zr = zA + L - 0.4f;
        b.push().translate(cx, yr, zr).rotateX(90);
        b.cylinder(0.32f, 0, 1.7f, 10, BRASS, null, BRASS);
        b.pop();
        b.push().translate(cx, yr - 0.9f, zr + 1.5f);
        b.cylinder(0.2f, 0, 0.9f, 8, BRASS, BRASS, null);
        b.pop();
        b.bevelBox(cx - 0.12f, yr + 0.25f, zr + 1.05f, cx + 0.12f, yr + 1.1f, zr + 1.35f, 0.05f, BRASS);
        b.bevelBox(cx - 0.6f, yr + 1.05f, zr + 1.08f, cx + 0.6f, yr + 1.3f, zr + 1.32f, 0.05f, BRASS);
        b.color(0.55f, 0.24f, 0.28f);
        decal(b, AUREOLE, 'y', cx + 0.3f, 0, zr + 2.0f, 3.6f, 2.6f, 10);
        b.color(1);
        // bouteille vide couchée, toiles
        b.push().translate(3.0f, 0.6f, 14.2f).rotateY(-20).rotateZ(-90).translate(0, -2.4f, 0);
        bouteille(b, BOTTLE_GREEN, DARK, 8);
        b.pop();
        toile(b, 12.3f, 6.2f, 4.6f, 1.4f, 5.2f, 0, 0);
        toile(b, 8.0f, 12.6f, 0.06f, 2.4f, 2.0f, -90, 0);
        toile(b, 3.7f, 6.3f, zA + L - 0.6f, 1.6f, 3.6f, 180, 0);
    }

    // =================================================================== casier à vin
    /**
     * Casier à bouteilles de cave en sapin assombri (62 x 30 x 80 cm) : bouteilles couchées col en avant, poussiéreuses,
     * quelques places vides, une bouteille cassée au sol (tesson, flaque séchée) ; bougie coulée sur le dessus, toiles.
     */
    static void casierVin(MeshBuilder b) {
        float x0 = 3.0f, x1 = 13.0f, zb = 0.4f, zf = 5.4f, H = 13.0f;
        b.color(0.62f, 0.58f, 0.52f);
        for (float x : new float[]{x0, x1 - 0.8f}) {
            bois(b, x, 0, zb, x + 0.8f, H, zb + 0.8f, 0.08f, SAPIN, NZ);
            bois(b, x, 0, zf - 0.8f, x + 0.8f, H, zf, 0.08f, SAPIN);
        }
        float[] ys = {0.9f, 3.9f, 6.9f, 9.9f, 12.7f};
        for (float y : ys) {
            bois(b, x0 + 0.78f, y - 0.6f, zf - 0.7f, x1 - 0.78f, y, zf - 0.1f, 0.06f, SAPIN);
            bois(b, x0 + 0.78f, y - 0.6f, zb + 0.1f, x1 - 0.78f, y, zb + 0.7f, 0.06f, SAPIN, NZ);
            bois(b, x0 + 0.1f, y - 0.55f, zb + 0.78f, x0 + 0.7f, y - 0.05f, zf - 0.78f, 0.05f, SAPIN);
            bois(b, x1 - 0.7f, y - 0.55f, zb + 0.78f, x1 - 0.1f, y - 0.05f, zf - 0.78f, 0.05f, SAPIN);
        }
        bois(b, x0, H - 0.05f, zb + 0.02f, x1, H + 0.35f, zf + 0.1f, 0.08f, SAPIN, NZ);
        b.color(1);
        float[] xs = {4.7f, 6.9f, 9.1f, 11.3f};
        int[] vides = {2, 5, 11, 14};
        Mat[] caps = {PAINT_RED, BRASS, DARK, PAINT_RED};
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int i = r * 4 + c;
                boolean vide = false;
                for (int v : vides) vide |= v == i;
                if (vide) continue;
                Mat verre = (r + c) % 3 == 0 ? BOTTLE_BROWN : BOTTLE_GREEN;
                b.push().translate(xs[c], ys[r] + 0.6f, zb + 0.5f).rotateX(90);
                bouteille(b, verre, caps[(r + 2 * c) % 4], 8);
                b.pop();
            }
        }
        // bouteille cassée au sol
        eclats(b, 9.0f, 9.0f, 1.6f, 8, BOTTLE_GREEN, 7);
        b.push().translate(7.4f, 0.52f, 9.8f).rotateY(35).rotateZ(-90).translate(0, -3.4f, 0);
        b.lathe(new float[][]{{0.5f, 3.0f}, {0.5f, 3.35f}, {0.27f, 3.72f}, {0.235f, 3.95f}}, 8, 0, 360, BOTTLE_GREEN, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.235f, 3.95f}, {0.235f, 4.38f}, {0.27f, 4.42f}, {0.27f, 4.8f}, {0.01f, 4.81f}}, 8, 0, 360, PAINT_RED, 1, 1, 0, 0, 0, false);
        b.pop();
        b.color(0.5f, 0.22f, 0.25f);
        decal(b, AUREOLE, 'y', 8.6f, 0, 9.4f, 3.6f, 2.8f, -15);
        b.color(1);
        // bougie coulée dans son bougeoir
        b.push().translate(6.0f, H + 0.35f, 2.8f);
        b.lathe(new float[][]{{0.01f, 0}, {0.9f, 0}, {0.95f, 0.12f}, {0.4f, 0.2f}, {0.32f, 0.6f}}, 10, 0, 360, BRASS, 1, 1, 0, 0, 0, false);
        b.cylinder(0.3f, 0.55f, 1.6f, 8, WHITE, null, WHITE);
        for (int k = 0; k < 3; k++) {
            b.push().translate((float) Math.cos(k * 2.1f) * 0.32f, 1.1f - k * 0.2f, (float) Math.sin(k * 2.1f) * 0.32f).scale(0.6f, 1.6f, 0.6f);
            b.sphere(0.14f, 6, 4, WHITE);
            b.pop();
        }
        b.pop();
        toile(b, x0 + 0.8f, H - 0.3f, zf - 0.3f, 2.6f, 2.2f, 0, 0);
        toile(b, x1 - 0.8f, ys[2] - 0.6f, zf + 0.15f, 2.0f, 1.8f, 180, 0);
        toile(b, x1 - 0.1f, 1.0f, 3.0f, 1.2f, 0.95f, 0, 0);
    }

    // =================================================================== étagère de cave
    /**
     * Étagère de cave en cornières perforées (90 x 40 x 180 cm) et tablettes d'aggloméré gonflé : bocaux de conserves
     * maison (tomates, haricots, abricots, confitures), boîtes de conserve, pots de terre ; un bocal cassé au sol
     * (jus séché), toiles d'araignée, rouille.
     */
    static void etagereConserves(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, zb = 0.4f, zf = 6.8f, H = 28.6f;
        float[] ys = {1.0f, 7.4f, 13.8f, 20.2f, 26.6f};
        b.color(0.52f, 0.57f, 0.52f);
        for (float x : new float[]{x0, x1 - 0.9f}) {
            boolean g = x == x0;
            for (float z : new float[]{zb, zf - 0.9f}) {
                boolean ar = z == zb;
                bois(b, g ? x : x + 0.78f, 0, z, g ? x + 0.12f : x + 0.9f, H, z + 0.9f, 0.03f, PEINTURE_USEE, ar ? NZ : 0);
                bois(b, g ? x + 0.12f : x, 0, ar ? z : z + 0.78f, g ? x + 0.9f : x + 0.78f, H, ar ? z + 0.12f : z + 0.9f, 0.03f, PEINTURE_USEE, ar ? NZ : 0);
            }
        }
        for (float y : ys) {
            b.color(0.52f, 0.57f, 0.52f);
            bois(b, x0 + 0.12f, y - 0.5f, zf - 0.22f, x1 - 0.12f, y, zf - 0.1f, 0.02f, PEINTURE_USEE);
            bois(b, x0 + 0.12f, y - 0.5f, zb + 0.02f, x1 - 0.12f, y, zb + 0.22f, 0.02f, PEINTURE_USEE, NZ);
            b.color(0.92f, 0.8f, 0.64f);
            bois(b, x0 + 0.15f, y - 0.02f, zb + 0.22f, x1 - 0.15f, y + 0.45f, zf - 0.22f, 0.05f, SAPIN);
        }
        b.color(1);
        float[] t = new float[5];
        for (int k = 0; k < 5; k++) t[k] = ys[k] + 0.45f;
        // étage 0 : gros bocaux à joint, pots de terre
        bocal(b, 2.6f, t[0], 3.4f, 1.1f, 3.0f, BOCAL_TOMATE, true);
        bocal(b, 5.2f, t[0], 3.2f, 1.1f, 3.0f, BOCAL_HARICOT, true);
        bocal(b, 7.8f, t[0], 3.6f, 1.1f, 3.0f, BOCAL_TOMATE, true);
        potTerre(b, 11.0f, t[0], 3.4f, 1.3f);
        potTerre(b, 13.4f, t[0], 3.2f, 1.1f);
        potTerre(b, 13.4f, t[0] + 1.6f, 3.2f, 0.95f);
        // étage 1 : bocaux moyens (un manque)
        Mat[] mb = {BOCAL_ABRICOT, BOCAL_HARICOT, BOCAL_CONFITURE, BOCAL_TOMATE, BOCAL_ABRICOT};
        for (int k = 0; k < 5; k++) {
            if (k == 2) continue;
            bocal(b, 2.4f + k * 2.7f, t[1], 3.4f + (k % 2) * 0.4f, 0.9f, 2.4f, mb[k], false);
        }
        // étage 2 : conserves, dont une couchée
        Mat[] mc = {CONSERVE_1, CONSERVE_2, CONSERVE_3};
        for (int k = 0; k < 5; k++) conserve(b, 2.3f + k * 2.4f, t[2], 3.2f + (k % 2) * 0.5f, 0.8f, 1.8f, mc[k % 3]);
        conserve(b, 2.3f, t[2] + 1.8f, 3.2f, 0.8f, 1.8f, CONSERVE_2);
        conserve(b, 4.7f, t[2] + 1.8f, 3.7f, 0.8f, 1.8f, CONSERVE_1);
        b.push().translate(13.6f, t[2] + 0.84f, 4.4f).rotateY(70).rotateZ(90).translate(0, -0.9f, 0);
        conserve(b, 0, 0, 0, 0.8f, 1.8f, CONSERVE_3);
        b.pop();
        // étage 3 : confitures
        for (int k = 0; k < 6; k++) bocal(b, 2.0f + k * 2.2f, t[3], 3.0f + (k % 2) * 0.6f, 0.7f, 1.5f, k % 2 == 0 ? BOCAL_CONFITURE : BOCAL_ABRICOT, false);
        // étage 4 : bocaux vides, carton
        Mat vide = BOCAL_HARICOT.sub(0, 0, 1, 0.12f);
        bocal(b, 2.6f, t[4], 3.4f, 0.9f, 2.2f, vide, false);
        bocal(b, 4.8f, t[4], 3.0f, 0.9f, 2.2f, vide, true);
        b.push().translate(10.6f, t[4], 3.4f).rotateY(-8);
        b.bevelBox(-2.4f, 0, -2.2f, 2.4f, 1.6f, 2.2f, 0.08f, CARTON_MOUILLE);
        b.pop();
        // bocal cassé au sol
        eclats(b, 7.4f, 9.4f, 1.6f, 9, BOCAL_TOMATE, 13);
        b.color(0.55f, 0.22f, 0.18f);
        decal(b, AUREOLE, 'y', 7.0f, 0, 9.0f, 3.6f, 2.8f, 20);
        b.color(1);
        b.push().translate(4.0f, 0.15f, 10.6f).rotateX(8);
        b.cylinder(0.85f, 0, 0.3f, 12, ROUILLE, METAL_NU, METAL_NU);
        b.pop();
        // usure
        coulure(b, 'z', x0 + 0.5f, 14.0f, zf, 0.8f, 1);
        coulure(b, 'z', x1 - 0.5f, 7.0f, zf, 0.8f, 2);
        toile(b, x0 + 0.9f, ys[4] - 0.5f, 3.6f, 3.0f, 2.6f, 0, 0);
        toile(b, x1 - 0.9f, ys[2] - 0.5f, 2.6f, 2.4f, 2.0f, 180, 0);
        toile(b, x1 - 0.9f, H, 0.06f, 1.6f, 1.6f, 90, 180);
    }

    /** Pot en terre cuite (base en y). */
    static void potTerre(MeshBuilder b, float x, float y, float z, float r) {
        b.push().translate(x, y, z);
        b.color(0.86f, 0.52f, 0.36f);
        b.lathe(new float[][]{{0.01f, 0}, {r * 0.7f, 0}, {r, r * 1.2f}, {r * 1.12f, r * 1.25f}, {r * 1.12f, r * 1.45f}, {r * 0.98f, r * 1.45f}, {r * 0.9f, r * 0.4f}}, 12, 0, 360, BETON, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.pop();
    }

    // =================================================================== congélateur coffre
    /**
     * Congélateur coffre (110 x 60 x 85 cm) sur deux blocs : émail jauni et rouillé en bas, couvercle entrouvert calé
     * par un manche cassé sur un intérieur moisi (sacs de congélation pourris, flaque), grille de compresseur,
     * thermostat, badge ; fuite séchée au pied.
     */
    static void congelateur(MeshBuilder b) {
        float x0 = 0.5f, x1 = 18.1f, z0 = 0.6f, z1 = 10.2f, yb = 0.6f, yt = 13.6f, e = 0.6f;
        b.bevelBox(x0 + 0.4f, 0, z0 + 0.4f, x1 - 0.4f, yb + 0.05f, z1 - 0.4f, 0.1f, DARK, null, NZ);
        b.bevelBox(x0, yb, z0, x1, yt, z1, 0.3f, ENAMEL, null, PY | NZ);
        // rebord et parois intérieures
        float yi = 2.4f, ix0 = x0 + e, ix1 = x1 - e, iz0 = z0 + e, iz1 = z1 - e, yr = yt - 0.03f;
        b.face(ENAMEL, v(0, 1, 0), p(x0 + 0.29f, yr, z1 - 0.29f), p(x1 - 0.29f, yr, z1 - 0.29f), p(ix1, yr, iz1), p(ix0, yr, iz1));
        b.face(ENAMEL, v(0, 1, 0), p(ix0, yr, iz0), p(ix1, yr, iz0), p(x1 - 0.29f, yr, z0 + 0.29f), p(x0 + 0.29f, yr, z0 + 0.29f));
        b.face(ENAMEL, v(0, 1, 0), p(x0 + 0.29f, yr, z0 + 0.29f), p(ix0, yr, iz0), p(ix0, yr, iz1), p(x0 + 0.29f, yr, z1 - 0.29f));
        b.face(ENAMEL, v(0, 1, 0), p(ix1, yr, iz0), p(x1 - 0.29f, yr, z0 + 0.29f), p(x1 - 0.29f, yr, z1 - 0.29f), p(ix1, yr, iz1));
        b.face(INTERIEUR_SALE, v(0, 0, -1), p(ix1, yi, iz1), p(ix0, yi, iz1), p(ix0, yr, iz1), p(ix1, yr, iz1));
        b.face(INTERIEUR_SALE, v(0, 0, 1), p(ix0, yi, iz0), p(ix1, yi, iz0), p(ix1, yr, iz0), p(ix0, yr, iz0));
        b.face(INTERIEUR_SALE, v(1, 0, 0), p(ix0, yi, iz1), p(ix0, yi, iz0), p(ix0, yr, iz0), p(ix0, yr, iz1));
        b.face(INTERIEUR_SALE, v(-1, 0, 0), p(ix1, yi, iz0), p(ix1, yi, iz1), p(ix1, yr, iz1), p(ix1, yr, iz0));
        b.face(MOISI, v(0, 1, 0), p(ix0, yi, iz1), p(ix1, yi, iz1), p(ix1, yi, iz0), p(ix0, yi, iz0));
        // contenu pourri
        for (int k = 0; k < 5; k++) {
            b.push().translate(3.0f + k * 3.0f, yi + 0.9f + (k % 2) * 0.4f, 3.4f + (k % 3) * 1.6f).rotateY(k * 37);
            b.blob(1.5f, 0.9f, 1.1f, 0.7f, 0.12f, 1.4f, k, 10, SAC_POUBELLE);
            b.pop();
        }
        b.color(0.4f, 0.36f, 0.3f);
        decal(b, AUREOLE, 'y', 12.0f, yi, 7.0f, 4.0f, 3.0f, 30);
        b.color(1);
        // couvercle entrouvert (charnières à l'arrière), calé par un manche cassé
        float hz = z0 + 0.1f, ang = -11.5f;
        b.push().translate(0, yt, hz).rotateX(ang).translate(0, -yt, -hz);
        b.bevelBox(x0 - 0.05f, yt + 0.02f, z0 + 0.05f, x1 + 0.05f, yt + 1.25f, z1 + 0.15f, 0.4f, ENAMEL, new Mat[]{null, null, PLASTIC_GREY, null, null, null}, 0);
        b.box(x0 + 0.5f, yt - 0.1f, z0 + 0.5f, x1 - 0.5f, yt + 0.03f, z0 + 0.8f, CAOUTCHOUC_USE);
        b.box(x0 + 0.5f, yt - 0.1f, z1 - 0.7f, x1 - 0.5f, yt + 0.03f, z1 - 0.4f, CAOUTCHOUC_USE);
        b.bevelBox(7.6f, yt + 0.3f, z1 + 0.1f, 11.0f, yt + 0.95f, z1 + 0.6f, 0.15f, PLASTIC_GREY);
        b.push().translate(12.2f, yt + 0.62f, z1 + 0.15f).rotateX(90);
        b.cylinder(0.25f, 0, 0.15f, 8, CHROME_PIQUE, null, DARK);
        b.pop();
        b.pop();
        b.color(1.1f, 1.0f, 0.86f);
        b.tube(new float[][]{p(3.2f, yt - 0.3f, iz1 - 0.3f), p(3.4f, yt + 1.95f, iz1 - 0.55f)}, fill(2, 0.24f), 7, SAPIN, true, true, 0, false);
        b.color(1);
        // grille de compresseur (flanc droit), thermostat et voyants, badge
        for (int k = 0; k < 6; k++) b.box(x1 - 0.05f, 1.4f + k * 0.55f, 2.6f, x1 + 0.08f, 1.7f + k * 0.55f, 7.6f, DARK);
        b.bevelBox(1.6f, 10.8f, z1 - 0.05f, 4.4f, 12.6f, z1 + 0.2f, 0.1f, PLASTIC_GREY);
        bouton(b, 2.4f, 11.7f, z1 + 0.2f, 0.4f, DARK);
        b.box(3.4f, 11.9f, z1 + 0.19f, 3.8f, 12.2f, z1 + 0.26f, PAINT_RED);
        b.box(3.4f, 11.2f, z1 + 0.19f, 3.8f, 11.5f, z1 + 0.26f, BOTTLE_GREEN);
        decal(b, P_CONGEL, 'z', 7.0f, 11.8f, z1, 3.2f, 0.8f);
        // usure : rouille en bas, coulures, fuite au pied
        coulure(b, 'z', 6.0f, 3.2f, z1, 1.4f, 0);
        coulure(b, 'z', 14.6f, 4.4f, z1, 1.6f, 2);
        coulure(b, 'X', x1, 9.0f, 9.0f, 1.2f, 1);
        boue(b, 'z', 9.0f, yb, z1, 16.0f, 1.6f, 1);
        decal(b, AFFICHE, 'X', x1 + 0.08f, 9.4f, 4.4f, 3.2f, 3.2f, 6);
        b.color(0.3f, 0.28f, 0.24f);
        decal(b, AUREOLE, 'y', 12.4f, 0, 12.2f, 4.4f, 2.6f, -15);
        b.color(1);
    }

    // =================================================================== cuisinière à bois
    /**
     * Cuisinière mixte bois-charbon de ferme (85 x 55 x 85 cm) en fonte émaillée vert amande : porte du foyer ouverte
     * sur les cendres et un reste de bûche, cendrier tiré (cendre répandue), four à thermomètre, barre chromée,
     * plaque à rondelles, dosseret, tuyau qui rentre dans le mur ; bouilloire, bûches au pied ; suie, rouille, toile.
     */
    static void cuisiniere(MeshBuilder b) {
        float x0 = 1.2f, x1 = 14.8f, z0 = 0.8f, zf = 9.6f, yb = 1.0f, yt = 12.6f;
        for (float x : new float[]{x0 + 0.5f, x1 - 1.3f}) {
            for (float z : new float[]{z0 + 0.4f, zf - 1.2f}) b.bevelBox(x, 0, z, x + 0.8f, yb + 0.05f, z + 0.8f, 0.15f, FONTE);
        }
        vert(b);
        b.bevelBox(x0, yb, z0, x1, yt, zf, 0.3f, EMAIL, null, NZ | PZ);
        // façade autour de l'ouverture du foyer (x 2.0..6.0, y 7.4..11.0)
        float fx0 = 2.0f, fx1 = 6.0f, fy0 = 7.4f, fy1 = 11.0f, a = 0.3f;
        b.face(EMAIL, v(0, 0, 1), p(x0 + a, yb + a, zf), p(x1 - a, yb + a, zf), p(x1 - a, fy0, zf), p(x0 + a, fy0, zf));
        b.face(EMAIL, v(0, 0, 1), p(x0 + a, fy1, zf), p(x1 - a, fy1, zf), p(x1 - a, yt - a, zf), p(x0 + a, yt - a, zf));
        b.face(EMAIL, v(0, 0, 1), p(x0 + a, fy0, zf), p(fx0, fy0, zf), p(fx0, fy1, zf), p(x0 + a, fy1, zf));
        b.face(EMAIL, v(0, 0, 1), p(fx1, fy0, zf), p(x1 - a, fy0, zf), p(x1 - a, fy1, zf), p(fx1, fy1, zf));
        b.color(1);
        float zr = zf - 2.0f;
        b.face(FONTE, v(1, 0, 0), p(fx0, fy0, zf), p(fx0, fy0, zr), p(fx0, fy1, zr), p(fx0, fy1, zf));
        b.face(FONTE, v(-1, 0, 0), p(fx1, fy0, zr), p(fx1, fy0, zf), p(fx1, fy1, zf), p(fx1, fy1, zr));
        b.face(FONTE, v(0, -1, 0), p(fx0, fy1, zf), p(fx0, fy1, zr), p(fx1, fy1, zr), p(fx1, fy1, zf));
        b.face(CENDRE, v(0, 1, 0), p(fx0, fy0, zr), p(fx0, fy0, zf), p(fx1, fy0, zf), p(fx1, fy0, zr));
        b.face(FONTE, v(0, 0, 1), p(fx0, fy0, zr), p(fx1, fy0, zr), p(fx1, fy1, zr), p(fx0, fy1, zr));
        b.push().translate(4.0f, fy0 + 0.6f, zf - 1.0f).rotateY(20);
        b.color(0.35f);
        buche(b, 3.2f, 0.55f);
        b.color(1);
        b.pop();
        b.push().translate(4.0f, fy0, zf - 1.0f).scale(1.6f, 0.4f, 0.8f);
        b.sphereSection(1.0f, 8, 3, 0, 90, CENDRE);
        b.pop();
        decal(b, BRULURE, 'z', 4.0f, fy1 + 0.6f, zf, 3.6f, 1.1f);
        // porte du foyer ouverte (gond à gauche)
        float hx = fx0 - 0.15f, dz = zf + 0.05f;
        b.push().translate(hx, 0, dz).rotateY(-105).translate(-hx, 0, -dz);
        b.bevelBox(hx, fy0 - 0.2f, dz, hx + 4.3f, fy1 + 0.2f, dz + 0.3f, 0.1f, FONTE);
        b.bevelBox(hx + 0.4f, fy0 + 0.3f, dz + 0.28f, hx + 3.9f, fy1 - 0.3f, dz + 0.45f, 0.1f, FONTE);
        b.bevelBox(hx + 3.5f, (fy0 + fy1) / 2 - 0.2f, dz + 0.4f, hx + 4.6f, (fy0 + fy1) / 2 + 0.2f, dz + 0.7f, 0.08f, CHROME_PIQUE);
        b.pop();
        // cendrier tiré
        b.push().translate(0, 0, 1.6f);
        b.bevelBox(fx0, 2.0f, zf - 0.4f, fx1, 4.0f, zf + 0.2f, 0.1f, FONTE);
        b.bevelBox(fx0 + 0.2f, 2.1f, zf - 3.4f, fx1 - 0.2f, 3.4f, zf - 0.35f, 0.05f, ROUILLE);
        b.bevelBox(3.4f, 2.9f, zf + 0.15f, 4.6f, 3.2f, zf + 0.5f, 0.06f, CHROME_PIQUE);
        b.pop();
        b.push().translate(4.0f, 0, zf + 3.2f).scale(2.0f, 0.35f, 1.2f);
        b.sphereSection(1.0f, 10, 3, 0, 90, CENDRE);
        b.pop();
        // four : porte émaillée, thermomètre, poignée chromée ; tiroir à bois
        vert(b);
        b.bevelBox(6.6f, 3.0f, zf - 0.05f, 14.2f, 10.6f, zf + 0.3f, 0.2f, EMAIL);
        b.bevelBox(6.6f, 1.25f, zf - 0.05f, 14.2f, 2.75f, zf + 0.25f, 0.12f, EMAIL);
        b.color(1);
        b.push().translate(10.4f, 8.4f, zf + 0.3f).rotateX(90);
        b.cylinder(1.0f, 0, 0.12f, 14, BRASS, null, THERMO);
        b.pop();
        b.tube(new float[][]{p(7.4f, 10.1f, zf + 0.3f), p(7.6f, 10.1f, zf + 0.85f), p(13.2f, 10.1f, zf + 0.85f), p(13.4f, 10.1f, zf + 0.3f)}, fill(4, 0.14f), 6, CHROME_PIQUE, false, false, 0, false);
        b.bevelBox(9.6f, 1.8f, zf + 0.24f, 11.2f, 2.2f, zf + 0.5f, 0.06f, CHROME_PIQUE);
        decal(b, P_FOURNEAU, 'z', 10.4f, 11.6f, zf, 5.0f, 1.25f);
        bouton(b, 1.65f, 5.4f, zf, 0.25f, CHROME_PIQUE);
        // plaque de cuisson en fonte, rondelles, dosseret, barre chromée
        bois(b, x0 - 0.2f, yt - 0.05f, z0 + 0.05f, x1 + 0.2f, yt + 0.8f, zf + 0.3f, 0.12f, FONTE, NZ);
        float yp = yt + 0.8f;
        for (float r : new float[]{0.7f, 1.4f, 2.1f}) {
            b.push().translate(4.6f, yp, 5.0f);
            b.torus(r, 0.06f, 16, 3, 0, 360, METAL_NU);
            b.pop();
        }
        b.push().translate(10.6f, yp, 5.0f);
        b.torus(1.5f, 0.06f, 16, 3, 0, 360, METAL_NU);
        b.pop();
        vert(b);
        bois(b, x0, yp - 0.05f, z0 + 0.1f, x1, yp + 0.8f, z0 + 0.85f, 0.12f, EMAIL, NZ);
        b.color(1);
        b.tube(new float[][]{p(x0 + 0.4f, 11.7f, zf + 1.0f), p(x1 - 0.4f, 11.7f, zf + 1.0f)}, fill(2, 0.15f), 8, CHROME_PIQUE, true, true, 0, false);
        for (float x : new float[]{x0 + 0.6f, x1 - 0.6f}) {
            b.tube(new float[][]{p(x, 11.7f, zf - 0.05f), p(x, 11.7f, zf + 1.0f)}, fill(2, 0.1f), 5, CHROME_PIQUE, false, false, 0, false);
            coulure(b, 'z', x, 11.5f, zf, 0.7f, (int) x);
        }
        // tuyau : monte de la plaque puis rentre dans le mur
        b.tube(new float[][]{p(12.0f, yp - 0.2f, 2.6f), p(12.0f, 14.8f, 2.6f), p(12.0f, 15.5f, 2.15f), p(12.0f, 15.6f, 1.2f), p(12.0f, 15.6f, 0.06f)},
                fill(5, 0.82f), 12, FONTE, false, false, 0, false);
        b.tube(new float[][]{p(11.1f, 14.2f, 2.6f), p(12.9f, 14.2f, 2.6f)}, fill(2, 0.07f), 4, METAL_NU, false, false, 0, false);
        b.push().translate(12.0f, 15.6f, 0.06f).rotateX(90);
        b.cylinder(1.15f, 0, 0.14f, 14, ROUILLE, null, ROUILLE);
        b.pop();
        // bouilloire émaillée sur le feu
        b.push().translate(4.6f, yp, 5.0f);
        b.color(0.5f, 0.6f, 0.76f);
        b.lathe(new float[][]{{0.01f, 0}, {1.2f, 0}, {1.35f, 0.25f}, {1.35f, 1.3f}, {1.0f, 1.8f}, {0.5f, 1.95f}}, 14, 0, 360, EMAIL, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.cylinder(0.52f, 1.9f, 2.05f, 10, DARK, null, DARK);
        b.tube(new float[][]{p(1.2f, 0.6f, 0), p(1.8f, 1.2f, 0), p(2.2f, 1.9f, 0)}, new float[]{0.24f, 0.17f, 0.12f}, 6, EMAIL, false, true, 0, false);
        b.tube(new float[][]{p(-0.9f, 1.6f, 0), p(-0.6f, 2.7f, 0), p(0.6f, 2.7f, 0), p(0.9f, 1.6f, 0)}, fill(4, 0.08f), 5, METAL_NU, false, false, 0, false);
        b.pop();
        // bûches au pied, petit bois
        b.push().translate(11.0f, 0.75f, 12.2f).rotateY(8);
        buche(b, 5.2f, 0.75f);
        b.pop();
        b.push().translate(11.4f, 0.75f, 13.8f).rotateY(-4);
        buche(b, 4.8f, 0.7f);
        b.pop();
        b.push().translate(11.2f, 2.0f, 13.0f).rotateY(15);
        buche(b, 4.6f, 0.65f);
        b.pop();
        for (int k = 0; k < 4; k++) {
            b.push().translate(7.0f + k * 0.5f, 0.15f, 12.6f + k * 0.3f).rotateY(30 + k * 25);
            b.bevelBox(-1.4f, 0, -0.15f, 1.4f, 0.3f, 0.15f, 0.05f, SAPIN);
            b.pop();
        }
        // usure
        coulure(b, 'x', x0, 6.0f, 5.0f, 1.2f, 1);
        decal(b, AUREOLE, 'X', x1, 5.0f, 4.6f, 3.0f, 2.4f);
        toile(b, 13.2f, 16.4f, 0.06f, 1.6f, 1.6f, -90, 0);
    }

    static void vert(MeshBuilder b) {
        b.color(0.6f, 0.74f, 0.62f);
    }
}
