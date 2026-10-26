package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.mesh.Noise;
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
 * Lot T1a, affaires abandonnées et petits dépôts : sacs de courses, cartable, sac à main, valise, tas de vêtements,
 * pneus, fût brûle-tout, jerricans, pots de peinture, boîtes à pizza, étal de marché.
 */
final class LotT1a3 {
    private LotT1a3() {
    }

    // =================================================================== 14. sacs de courses

    /** Anse de sac plastique (boucle aplatie) entre deux points du bord. */
    static void anseSac(MeshBuilder b, float[] a, float[] c, float haut, Mat m) {
        float[] mid = p((a[0] + c[0]) / 2, Math.max(a[1], c[1]) + haut, (a[2] + c[2]) / 2);
        b.tube(new float[][]{a, p(a[0] * 0.7f + mid[0] * 0.3f, mid[1] - haut * 0.2f, a[2] * 0.7f + mid[2] * 0.3f), mid,
                p(c[0] * 0.7f + mid[0] * 0.3f, mid[1] - haut * 0.2f, c[2] * 0.7f + mid[2] * 0.3f), c}, fill(5, 0.11f), 4, m, false, false, 0, false);
    }

    /** Sacs de courses renversés contre le mur : conserves qui ont roulé, bouteille d'huile, biscottes, baguette. */
    static void sacsCourses(MeshBuilder b) {
        // sac debout affaissé contre le mur
        Matrix4f t1 = place(4.2f, 0, 2.9f, 8, -6, 0);
        mou(b, t1, new float[][]{{0.01f, 0}, {2.2f, 0.05f}, {2.5f, 1.2f}, {2.6f, 3.6f}, {2.4f, 5.2f}, {2.0f, 6.0f}, {1.7f, 6.3f}}, 14, 1.35f, 0.75f, 0.04f, 0.5f, 141, SAC_BLANC, 0);
        Vector3f a = point(t1, -1.9f, 6.2f, 0), c = point(t1, 1.9f, 6.2f, 0);
        anseSac(b, p(a.x, a.y, a.z), p(c.x, c.y, c.z), 1.6f, SAC_BLANC);
        Vector3f f = point(t1, 0, 3.0f, 2.0f);
        decalT(b, L_MARCHE, f.x, f.y, f.z, 2.6f, 1.3f, 8, -6, 0, 0.12f);
        // dedans : biscottes et baguette qui dépassent
        b.push().translate(3.4f, 5.4f, 2.6f).rotateY(10).rotateX(-14);
        b.bevelBox(-1.0f, 0, -0.5f, 1.0f, 2.4f, 0.5f, 0.06f, CARTON, new Mat[]{null, null, null, null, null, CT_BISCOTTES}, 0);
        b.pop();
        b.tube(new float[][]{p(5.2f, 4.0f, 2.6f), p(5.6f, 7.0f, 2.2f), p(6.3f, 9.4f, 1.4f)}, new float[]{0.38f, 0.42f, 0.3f}, 7, TAPE, true, true, 0, false);
        // sac couché, ouvert vers l'avant, contenu renversé
        Matrix4f t2 = place(10.6f, 1.9f, 5.0f, -25, 90, 0);
        mou(b, t2, new float[][]{{0.01f, -3.0f}, {1.9f, -2.9f}, {2.3f, -1.6f}, {2.4f, 0.6f}, {2.1f, 2.4f}, {1.9f, 3.0f}}, 14, 1.3f, 0.8f, 0.06f, 0.5f, 142, SAC_BLANC, 0, false, 0.3f);
        mou(b, t2, new float[][]{{2.2f, 0.6f}, {2.0f, 2.4f}, {1.8f, 3.0f}}, 14, 1.3f, 0.8f, 0.06f, 0.5f, 142, SAC_BLANC, 0, true, 0.3f);
        conserve(b, 11.6f, 0, 10.4f, BOITE_RAVIOLI, 70, true);
        conserve(b, 13.8f, 0, 12.8f, BOITE_THON, 20, true);
        conserve(b, 8.6f, 0, 12.0f, BOITE_TOMATE, 90, false);
        conserve(b, 6.6f, 0, 9.4f, BOITE_POIS, 140, true);
        bouteillePet(b, 13.0f, 15.0f, 5, PET_HUILE, YELLOW, 1);
        // sac vide froissé, ticket de caisse
        mou(b, place(3.2f, 0, 11.0f, 30, 0, 0), new float[][]{{0.01f, 0}, {2.2f, 0.05f}, {2.0f, 0.5f}, {1.0f, 0.8f}, {0.01f, 0.9f}}, 10, 1.3f, 0.8f, 0.2f, 0.9f, 143, SAC_BLANC, 0);
        feuille(b, 9.8f, 0, 14.6f, 1.2f, 2.2f, 30, 0.08f, P_TICKET);
        decalO(b, D_FLAQUE, 'y', 9.4f, 0, 10.0f, 5.0f, 3.6f, -15, 0.07f);
        jonchee(b, 1.0f, 12.0f, 15.0f, 15.6f, 4, FEUILLES, 1.2f, 144);
    }

    // =================================================================== 15. cartable

    /** Cartable d'enfant délavé appuyé contre le mur, trousse ouverte et crayons, cahiers détrempés au sol. */
    static void cartable(MeshBuilder b) {
        float w = 4.8f, h = 6.1f, d = 2.4f;
        b.push().translate(5.4f, 0, 1.62f).rotateY(6).rotateX(-14);
        b.color(0.36f, 0.52f, 0.74f);
        b.bevelBox(-w / 2, 0, 0, w / 2, h, d, 0.75f, RIPSTOP);
        b.bevelBox(-w / 2 + 0.4f, 0.5f, d - 0.3f, w / 2 - 0.4f, 2.6f, d + 0.55f, 0.4f, RIPSTOP);
        b.color(0.82f, 0.42f, 0.24f);
        b.bevelBox(-w / 2 - 0.06f, h - 3.1f, d - 0.5f, w / 2 + 0.06f, h + 0.1f, d + 0.22f, 0.5f, RIPSTOP);
        b.color(1);
        for (float x : new float[]{-1.2f, 1.2f}) {
            b.bevelBox(x - 0.28f, h - 4.0f, d + 0.15f, x + 0.28f, h - 2.9f, d + 0.32f, 0.06f, DARK);
            b.bevelBox(x - 0.36f, h - 3.2f, d + 0.2f, x + 0.36f, h - 2.7f, d + 0.4f, 0.08f, PLASTIC_GREY);
        }
        b.box(-w / 2 + 0.6f, 1.4f, d + 0.56f, w / 2 - 0.6f, 1.75f, d + 0.6f, WHITE);
        b.tube(new float[][]{p(-0.8f, h + 0.02f, d * 0.4f), p(-0.6f, h + 0.7f, d * 0.4f), p(0.6f, h + 0.7f, d * 0.4f), p(0.8f, h + 0.02f, d * 0.4f)}, fill(4, 0.16f), 6, DARK, false, false, 0, false);
        // porte-clés (petit ours en peluche délavé)
        b.tube(new float[][]{p(1.6f, h - 0.4f, d + 0.24f), p(1.75f, h - 1.0f, d + 0.4f)}, fill(2, 0.04f), 4, CHROME_PIQUE, false, false, 0, false);
        b.color(0.8f, 0.68f, 0.4f);
        b.push().translate(1.8f, h - 1.6f, d + 0.5f);
        b.sphere(0.42f, 8, 5, TISSU_USE);
        b.translate(0, 0.55f, 0);
        b.sphere(0.3f, 8, 5, TISSU_USE);
        b.pop();
        b.color(1);
        decal(b, BOUE_1, 'z', 0, 0.75f, d + 0.55f, 3.6f, 0.9f);
        decal(b, GRIFFURES, 'z', -0.8f, h - 1.4f, d + 0.22f, 2.4f, 1.2f, 20);
        b.pop();
        // trousse ouverte, crayons
        b.color(0.8f, 0.45f, 0.55f);
        b.push().translate(11.2f, 0.62f, 5.2f).rotateY(-20).rotateZ(90).scale(0.7f, 1, 1);
        b.lathe(new float[][]{{0.01f, -2.2f}, {0.6f, -2.1f}, {0.9f, -1.6f}, {0.95f, 1.6f}, {0.6f, 2.1f}, {0.01f, 2.2f}}, 10, 30, 330, TISSU_USE, 1, 1, 0.04f, 1.0f, 151, false);
        b.lathe(new float[][]{{0.01f, -2.2f}, {0.6f, -2.1f}, {0.9f, -1.6f}, {0.95f, 1.6f}, {0.6f, 2.1f}, {0.01f, 2.2f}}, 10, 30, 330, TISSU_USE, 0.96f, 0.96f, 0.04f, 1.0f, 151, true);
        b.pop();
        b.color(1);
        Mat[] cols = {PAINT_RED, YELLOW, PLASTIC_BLUE, PLASTIC_GREEN, ORANGE};
        Random r = new Random(152);
        for (int k = 0; k < 6; k++) {
            float x = 9.0f + r.nextFloat() * 5.0f, z = 7.6f + r.nextFloat() * 3.6f, a = r.nextFloat() * 360;
            b.push().translate(x, 0.2f, z).rotateY(a).rotateZ(90);
            b.cylinder(0.2f, -1.4f, 1.1f, 6, cols[k % cols.length], WALNUT, null);
            b.push().translate(0, 1.1f, 0);
            b.lathe(new float[][]{{0.2f, 0}, {0.01f, 0.5f}}, 6, 0, 360, PINE, 1, 1, 0, 0, 0, false);
            b.pop();
            b.pop();
        }
        b.push().translate(12.6f, 0, 12.4f).rotateY(35);
        b.box(-2.4f, 0.02f, -0.3f, 2.4f, 0.1f, 0.3f, WHITE);
        b.pop();
        // cahiers au sol (dont un ouvert, détrempé)
        b.push().translate(4.6f, 0, 10.6f).rotateY(-18);
        b.bevelBox(-1.8f, 0.0f, -2.5f, 1.8f, 0.2f, 2.5f, 0.04f, PAPIER, new Mat[]{null, null, null, CAHIER_BLEU, null, null}, 0);
        b.pop();
        b.push().translate(5.4f, 0.2f, 10.2f).rotateY(14);
        b.bevelBox(-1.8f, 0.0f, -2.5f, 1.8f, 0.18f, 2.5f, 0.04f, PAPIER, new Mat[]{null, null, null, CAHIER_ROUGE, null, null}, 0);
        b.pop();
        feuille(b, 7.8f, 0, 13.4f, 3.4f, 2.4f, 25, 0.3f, P_LETTRE);
        decalO(b, AUREOLE, 'y', 5.4f, 0.38f, 10.2f, 2.2f, 2.2f, 0, 0.06f);
        jonchee(b, 1.0f, 12.0f, 15.0f, 15.6f, 5, FEUILLES, 1.2f, 153);
    }

    // =================================================================== 16. sac à main

    /** Sac à main en simili cuir couché, contenu renversé : portefeuille, papiers, rouge à lèvres, clés, téléphone. */
    static void sacAMain(MeshBuilder b) {
        b.push().translate(6.0f, 1.6f, 3.4f).rotateY(-12).rotateX(84).rotateZ(4);
        b.color(0.42f, 0.17f, 0.15f);
        cuve(b, 0, 0, new float[][]{{4.8f, 1.8f, 0.5f, -1.75f}, {5.0f, 2.0f, 0.6f, 0.2f}, {4.2f, 1.7f, 0.5f, 1.75f}}, 2, SIMILI, false, null);
        cuve(b, 0, 0, new float[][]{{4.0f, 1.5f, 0.45f, 1.75f}, {4.4f, 1.6f, 0.5f, 0.2f}}, 2, DOUBLURE, true, null);
        capot(b, 0, -1.75f, 0, 4.8f, 1.8f, 0.5f, 2, SIMILI, false);
        b.push().translate(0, 1.7f, 0.9f).rotateX(20);
        b.bevelBox(-2.2f, -1.6f, 0, 2.2f, 0.1f, 0.18f, 0.08f, SIMILI);
        b.pop();
        b.color(1);
        b.bevelBox(-0.35f, 0.4f, 1.12f, 0.35f, 0.9f, 1.3f, 0.06f, BRASS);
        b.pop();
        // anse (bandoulière) au sol
        float[][] pa = new float[10][];
        for (int k = 0; k < 10; k++) {
            double ang = k / 9.0 * Math.PI * 1.3;
            pa[k] = p(3.4f + (float) Math.cos(ang) * 2.4f, 0.2f, 6.8f + (float) Math.sin(ang) * 1.6f);
        }
        b.color(0.42f, 0.17f, 0.15f);
        b.tube(pa, fill(10, 0.18f), 5, SIMILI, true, true, 0, false);
        // portefeuille entrouvert
        b.color(0.3f, 0.2f, 0.14f);
        b.push().translate(10.4f, 0, 7.8f).rotateY(28);
        b.bevelBox(-1.6f, 0, -1.0f, 1.6f, 0.22f, 1.0f, 0.08f, SIMILI);
        b.push().translate(0, 0.22f, -1.0f).rotateX(-28);
        b.bevelBox(-1.6f, 0, 0, 1.6f, 0.18f, 2.0f, 0.08f, SIMILI);
        b.pop();
        b.pop();
        b.color(1);
        feuille(b, 10.6f, 0.3f, 7.2f, 1.6f, 1.0f, 20, 0.04f, P_CARTE);
        feuille(b, 12.8f, 0, 10.6f, 1.6f, 1.6f, -25, 0.06f, P_PHOTO);
        feuille(b, 7.6f, 0, 11.6f, 2.4f, 2.4f, 40, 0.15f, P_LETTRE);
        feuille(b, 9.0f, 0, 13.4f, 1.0f, 2.0f, -10, 0.05f, P_TICKET);
        // rouge à lèvres, clés, téléphone fêlé, poudrier
        b.push().translate(8.6f, 0.3f, 9.0f).rotateY(60).rotateZ(90);
        b.cylinder(0.3f, -0.9f, 0.4f, 8, BRASS, BRASS, null);
        b.cylinder(0.22f, 0.4f, 0.9f, 8, PAINT_RED, null, PAINT_RED);
        b.pop();
        b.push().translate(13.2f, 0.08f, 6.6f);
        b.torus(0.6f, 0.07f, 10, 4, 0, 360, CHROME_PIQUE);
        b.pop();
        for (int k = 0; k < 2; k++) {
            b.push().translate(13.2f, 0.06f + k * 0.1f, 6.6f).rotateY(330 + k * 50).translate(1.5f, 0, 0);
            b.bevelBox(-0.9f, 0, -0.18f, 0.9f, 0.08f, 0.18f, 0.03f, k == 0 ? CHROME_PIQUE : BRASS);
            b.bevelBox(-1.4f, 0, -0.35f, -0.85f, 0.09f, 0.35f, 0.05f, k == 0 ? CHROME_PIQUE : BRASS);
            b.pop();
        }
        b.push().translate(5.0f, 0, 12.0f).rotateY(-15);
        b.bevelBox(-0.75f, 0, -1.5f, 0.75f, 0.16f, 1.5f, 0.12f, DARK);
        b.face(INTERIEUR_SALE, v(0, 1, 0), p(-0.6f, 0.21f, 1.3f), p(0.6f, 0.21f, 1.3f), p(0.6f, 0.21f, -1.3f), p(-0.6f, 0.21f, -1.3f));
        decalO(b, FISSURE, 'y', 0.1f, 0.21f, 0.2f, 1.1f, 1.6f, 30, 0.05f);
        b.pop();
        b.push().translate(11.6f, 0, 12.4f);
        b.cylinder(0.75f, 0, 0.22f, 10, BRASS, null, BRASS);
        b.pop();
        decalO(b, BOUE_2, 'y', 9.0f, 0, 9.2f, 7.0f, 3.0f, 15, 0.08f);
        jonchee(b, 1.0f, 10.0f, 15.0f, 15.6f, 5, FEUILLES, 1.2f, 161);
    }

    // =================================================================== 17. valise ouverte

    static void coqueValise(MeshBuilder b, float L, float W, float hc) {
        b.color(0.42f, 0.16f, 0.2f);
        cuve(b, 0, 0, new float[][]{{L - 0.3f, W - 0.3f, 0.9f, 0}, {L, W, 1.0f, 0.4f}, {L, W, 1.0f, hc}, {L - 0.25f, W - 0.25f, 0.9f, hc + 0.02f}}, 3, PLASTIQUE_USE, false, null);
        b.color(1);
        cuve(b, 0, 0, new float[][]{{L - 0.25f, W - 0.25f, 0.9f, hc + 0.01f}, {L - 0.4f, W - 0.4f, 0.85f, 0.3f}}, 3, DOUBLURE, true, null);
        capot(b, 0, 0.3f, 0, L - 0.4f, W - 0.4f, 0.85f, 3, DOUBLURE, true);
    }

    /** Valise cabine ouverte à plat : coques rouge bordeaux, doublure, sangles, vêtements qui débordent. */
    static void valiseOuverte(MeshBuilder b) {
        float L = 8.8f, W = 6.4f, hc = 1.6f;
        b.push().translate(7.6f, 0, 3.9f).rotateY(4);
        coqueValise(b, L, W, hc);
        // vêtements pliés
        b.bevelBox(-3.6f, 0.3f, -2.6f, 0.4f, 1.5f, 0.2f, 0.35f, JEAN);
        b.color(0.55f, 0.24f, 0.26f);
        b.bevelBox(0.6f, 0.3f, -2.5f, 3.7f, 1.7f, 0.4f, 0.35f, TRICOT);
        b.color(1);
        b.bevelBox(-3.4f, 0.3f, 0.5f, -0.2f, 1.9f, 2.6f, 0.4f, MARINIERE);
        b.bevelBox(0.2f, 0.3f, 0.7f, 3.6f, 1.3f, 2.7f, 0.35f, FLEURI);
        // chemise qui déborde jusqu'au sol (à droite)
        doubleFace(b, FLEURI, p(2.0f, 1.32f, 0.9f), p(4.2f, 1.7f, 0.9f), p(4.2f, 1.7f, 2.9f), p(2.0f, 1.32f, 2.9f));
        doubleFace(b, FLEURI, p(4.2f, 1.7f, 0.9f), p(5.8f, 0.06f, 0.6f), p(5.8f, 0.06f, 3.2f), p(4.2f, 1.7f, 2.9f));
        // roulettes et poignée côté gauche (face extérieure de la coque arrière)
        for (float z : new float[]{-2.2f, 2.2f}) {
            b.push().translate(-L / 2 + 0.1f, 0.9f, z).rotateZ(90);
            b.cylinder(0.55f, -0.1f, 0.5f, 8, CAOUTCHOUC_USE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
            b.pop();
        }
        b.bevelBox(-L / 2 - 0.25f, 0.4f, -1.4f, -L / 2 + 0.05f, 1.3f, 1.4f, 0.1f, DARK);
        decal(b, GRIFFURES, 'X', L / 2, 1.0f, 1.0f, 2.4f, 0.9f);
        decal(b, ETIQUETTE_DECHIREE, 'Z', 1.6f, 1.0f, -W / 2, 2.0f, 0.9f);
        b.pop();
        // couvercle ouvert à plat devant (charnière côté avant de la coque arrière)
        b.push().translate(7.6f, 0, 3.9f + W + 0.15f).rotateY(4);
        b.push().translate(0, 0, 0);
        coqueValise(b, L, W, hc);
        for (int s = -1; s <= 1; s += 2) b.box(-3.6f, 0.31f, s * 1.6f - 0.15f, 3.6f, 0.37f, s * 1.6f + 0.15f, DARK);
        b.push().translate(0, 0.36f, 0).rotateY(32);
        b.box(-3.8f, 0, -0.15f, 3.8f, 0.06f, 0.15f, DARK);
        b.pop();
        b.push().translate(0, 0.41f, 0).rotateY(-32);
        b.box(-3.8f, 0, -0.15f, 3.8f, 0.06f, 0.15f, DARK);
        b.pop();
        b.bevelBox(-0.4f, 0.3f, -0.4f, 0.4f, 0.6f, 0.4f, 0.1f, CHROME_PIQUE);
        b.color(0.9f, 0.9f, 0.85f);
        b.push().translate(-2.0f, 0.3f, 1.4f).rotateY(30);
        b.tube(new float[][]{p(-0.9f, 0.25f, 0), p(0, 0.3f, 0.2f), p(0.9f, 0.25f, 0)}, fill(3, 0.28f), 6, TISSU_USE, true, true, 0, false);
        b.pop();
        b.color(1);
        b.pop();
        b.pop();
        // usure
        decalO(b, BOUE_1, 'y', 12.6f, 0, 14.2f, 5.0f, 2.0f, 10, 0.07f);
        jonchee(b, 1.0f, 13.6f, 15.0f, 15.6f, 4, FEUILLES, 1.1f, 171);
    }

    // =================================================================== 18. tas de vêtements

    /** Vêtements entassés contre le mur : pull, jean, veste, chemise, une chaussure. */
    static void tasVetements(MeshBuilder b) {
        // masse du tas (plusieurs boules de tissu)
        b.color(0.34f, 0.36f, 0.42f);
        mou(b, place(7.6f, 0, 4.4f, 0, 0, 0), new float[][]{{0.01f, 0}, {5.6f, 0.05f}, {5.8f, 1.0f}, {5.0f, 2.6f}, {3.6f, 3.8f}, {1.6f, 4.6f}, {0.01f, 4.9f}}, 16, 1.25f, 0.72f, 0.12f, 0.45f, 181, TISSU_USE, 0);
        b.color(1);
        // pull bordeaux étalé dessus, manche qui pend
        b.color(0.5f, 0.2f, 0.22f);
        mou(b, place(5.8f, 3.0f, 4.6f, 20, -10, 0), new float[][]{{0.01f, 0}, {2.8f, 0.1f}, {3.0f, 0.6f}, {2.4f, 1.2f}, {0.01f, 1.4f}}, 12, 1.2f, 0.9f, 0.12f, 0.8f, 182, TRICOT, 0);
        b.tube(new float[][]{p(3.4f, 3.6f, 6.0f), p(2.6f, 2.6f, 7.4f), p(2.2f, 1.0f, 8.4f), p(2.6f, 0.5f, 9.6f)}, new float[]{0.62f, 0.55f, 0.5f, 0.45f}, 7, TRICOT, false, true, 0, false);
        b.color(1);
        // jean : une jambe pend vers l'avant
        b.push().translate(10.4f, 3.6f, 4.0f).rotateY(-30).scale(1, 0.55f, 1);
        b.bevelBox(-1.8f, -0.6f, -1.4f, 1.8f, 0.6f, 1.4f, 0.5f, JEAN);
        b.pop();
        b.tube(new float[][]{p(11.0f, 3.6f, 5.0f), p(11.6f, 2.8f, 7.0f), p(12.2f, 1.2f, 8.6f), p(12.4f, 0.8f, 10.6f), p(12.8f, 0.72f, 12.4f)}, new float[]{0.85f, 0.8f, 0.75f, 0.72f, 0.68f}, 8, JEAN, false, true, 0, false);
        b.tube(new float[][]{p(9.6f, 3.4f, 5.2f), p(9.6f, 2.0f, 6.6f), p(9.2f, 0.76f, 7.8f), p(8.6f, 0.7f, 9.4f)}, new float[]{0.8f, 0.75f, 0.7f, 0.65f}, 8, JEAN, false, true, 0, false);
        // veste kaki roulée en boule à gauche
        b.color(0.48f, 0.47f, 0.33f);
        mou(b, place(2.8f, 0, 6.4f, 40, 0, 0), new float[][]{{0.01f, 0}, {2.4f, 0.05f}, {2.6f, 1.0f}, {2.1f, 2.2f}, {0.9f, 2.8f}, {0.01f, 3.0f}}, 12, 1.1f, 0.9f, 0.15f, 0.7f, 183, TISSU_USE, 0);
        b.bevelBox(1.4f, 2.4f, 6.2f, 2.2f, 2.6f, 6.6f, 0.05f, BRASS);
        b.color(1);
        // chemise rayée qui pend du tas sur le sol
        doubleFace(b, MARINIERE, p(6.0f, 2.6f, 7.4f), p(9.0f, 2.2f, 7.6f), p(9.4f, 0.06f, 10.6f), p(6.2f, 0.06f, 10.4f));
        // chaussure (basket) renversée
        b.push().translate(13.2f, 0.42f, 13.0f).rotateY(-35).rotateZ(-12);
        b.bevelBox(-1.9f, 0.1f, -0.65f, 1.9f, 0.5f, 0.65f, 0.25f, WHITE);
        b.color(0.35f, 0.38f, 0.42f);
        b.bevelBox(-1.7f, 0.45f, -0.6f, 1.0f, 1.5f, 0.6f, 0.45f, TISSU_USE);
        b.bevelBox(-1.8f, 0.52f, -0.54f, -0.6f, 2.0f, 0.54f, 0.4f, TISSU_USE);
        b.color(1);
        for (float x : new float[]{-0.2f, 0.25f, 0.7f}) b.box(x - 0.08f, 1.45f, -0.45f, x + 0.08f, 1.55f, 0.45f, WHITE);
        b.pop();
        decalO(b, BOUE_2, 'y', 8.0f, 0, 9.8f, 9.0f, 3.0f, 0, 0.08f);
        jonchee(b, 1.0f, 11.0f, 15.0f, 15.6f, 6, FEUILLES, 1.2f, 184);
    }

    // =================================================================== 19. pneus

    static final float RP = 5.04f;

    /** Pneu 185/65 R15 (Ø 63 cm, 20 cm de large) couché, axe Y, posé en y = 0 ; dessous : flanc inférieur. */
    static void pneu(MeshBuilder b, boolean dessous) {
        b.lathe(new float[][]{{RP - 0.35f, 0.1f}, {RP - 0.08f, 0.45f}, {RP, 0.8f}, {RP, 2.4f}, {RP - 0.08f, 2.75f}, {RP - 0.35f, 3.1f}}, 24, 0, 360, PNEU, 1, 1, 0, 0, 0, false);
        anneau(b, 3.1f, 3.05f, RP - 0.35f, RP, 0.25f, 24, 2, FLANC_PNEU, true);
        if (dessous) anneau(b, 0.1f, 3.05f, RP - 0.35f, RP, 0.25f, 24, 2, FLANC_PNEU, false);
        b.lathe(new float[][]{{3.05f, 3.1f}, {2.95f, 1.6f}, {3.05f, 0.1f}}, 24, 0, 360, CAOUTCHOUC_USE, 1, 1, 0, 0, 0, true);
    }

    /** Pile de trois pneus usés et un quatrième debout appuyé contre le mur ; flaque, terre et feuilles. */
    static void pneus(MeshBuilder b) {
        float cx = 5.6f, cz = 10.3f;
        b.push().translate(cx, 0, cz).rotateY(10);
        pneu(b, false);
        b.pop();
        b.push().translate(cx + 0.3f, 3.18f, cz - 0.2f).rotateY(55).rotateZ(1.5f);
        pneu(b, false);
        b.pop();
        b.push().translate(cx - 0.2f, 6.38f, cz + 0.1f).rotateY(-20).rotateX(-1.5f);
        pneu(b, false);
        b.pop();
        // debout, appuyé contre le mur
        b.push().translate(10.6f, 5.1f, 2.78f).rotateX(80).translate(0, -1.6f, 0);
        pneu(b, true);
        b.pop();
        // feuilles et eau croupie au fond de la pile, terre
        jonchee(b, cx - 2.2f, cz - 2.2f, cx + 2.2f, cz + 2.2f, 5, FEUILLES, 1.2f, 191);
        decalO(b, D_FLAQUE, 'y', 12.6f, 0, 8.8f, 5.4f, 4.4f, 30, 0.07f);
        champ(b, 11.0f, 11.0f, 15.4f, 15.2f, 0.12f, 0.35f, 0.8f, 4, 4, 192, TERRE);
        decalO(b, BOUE_1, 'y', 3.6f, 0, 4.0f, 6.0f, 2.0f, 0, 0.08f);
    }

    // =================================================================== 20. fût brûle-tout

    /** Fût de 200 L rouillé, haut découpé au burin, trous d'aération, cendres et bois calciné, suie et traces de chauffe. */
    static void futBrule(MeshBuilder b) {
        float cx = 8.0f, cz = 5.5f;
        int m0 = b.mark();
        Bosse coups = coup(cx + 2.6f, 5.6f, cz + 3.8f, 2.2f, 0.45f);
        cuve(b, cx, cz, rond(new float[]{9.0f, 0}, new float[]{9.28f, 0.3f}, new float[]{9.28f, 0.6f}, new float[]{9.2f, 0.7f}, new float[]{9.2f, 4.0f},
                new float[]{9.5f, 4.2f}, new float[]{9.5f, 4.45f}, new float[]{9.2f, 4.65f}, new float[]{9.2f, 8.2f}, new float[]{9.5f, 8.4f},
                new float[]{9.5f, 8.65f}, new float[]{9.2f, 8.85f}, new float[]{9.2f, 11.0f}), 5, ROUILLE_FUT, false, coups);
        mou(b, place(cx, 0, cz, 0, 0, 0), new float[][]{{4.6f, 11.0f}, {4.6f, 12.0f}}, 24, 1, 1, 0, 0, 201, ROUILLE_FUT, 0, false, 0.45f);
        // chauffe : le haut noircit et brunit
        b.shade(m0, (x, y, z, nx, ny, nz, c) -> {
            float k = Math.max(0, Math.min(1, (y - 6.0f) / 6.0f));
            c[0] *= 1 - 0.55f * k;
            c[1] *= 1 - 0.62f * k;
            c[2] *= 1 - 0.58f * k;
        });
        mou(b, place(cx, 0, cz, 0, 0, 0), new float[][]{{4.5f, 6.5f}, {4.5f, 12.0f}}, 24, 1, 1, 0, 0, 201, CENDRES, 0, true, 0.45f);
        // cendres, charbons, bouts de bois calcinés qui dépassent
        b.push().translate(cx, 8.6f, cz);
        b.disc(4.5f, 0, 20, CENDRES, true);
        b.pop();
        champ(b, cx - 3.0f, cz - 3.0f, cx + 3.0f, cz + 3.0f, 8.8f, 0.6f, 0.9f, 5, 5, 202, CENDRES);
        b.color(0.3f, 0.28f, 0.26f);
        b.push().translate(cx - 1.0f, 8.8f, cz + 0.6f).rotateZ(-38).rotateY(20);
        b.bevelBox(-0.4f, 0, -0.25f, 0.4f, 6.8f, 0.25f, 0.08f, BOIS_GRIS);
        b.pop();
        b.push().translate(cx + 1.4f, 8.8f, cz - 0.8f).rotateZ(30).rotateY(-40);
        b.bevelBox(-0.3f, 0, -0.3f, 0.3f, 5.6f, 0.3f, 0.1f, BOIS_GRIS);
        b.pop();
        b.color(1);
        b.push().translate(cx + 0.4f, 8.8f, cz + 1.0f).rotateZ(-12).rotateX(25);
        b.cylinder(0.22f, 0, 4.4f, 6, CENDRES, null, CENDRES);
        b.pop();
        // trous d'aération, suie, coulures
        decalCyl(b, D_TROUS, cx, cz, 4.62f, 100, 2.2f, 2.2f, 2.2f, 0);
        decalCyl(b, D_TROUS, cx, cz, 4.62f, 30, 2.2f, 2.0f, 2.0f, 0);
        decalCyl(b, D_TROUS, cx, cz, 4.62f, 160, 2.4f, 2.0f, 2.0f, 0);
        decalCyl(b, BRULURE, cx, cz, 4.66f, 70, 10.2f, 3.0f, 3.0f, 0);
        decalCyl(b, BRULURE, cx, cz, 4.66f, 140, 10.0f, 2.6f, 2.6f, 20);
        decalCyl(b, COULURE_2, cx, cz, 4.64f, 115, 7.4f, 1.2f, 2.4f, 0);
        decalCyl(b, COULURE_1, cx, cz, 4.64f, 50, 7.6f, 1.2f, 2.4f, 0);
        // au sol : cendres répandues, planche calcinée, canette
        champ(b, 2.4f, 10.0f, 13.0f, 14.6f, 0.1f, 0.3f, 1.0f, 6, 4, 203, CENDRES);
        b.push().translate(12.6f, 0.2f, 12.0f).rotateY(-60);
        b.color(0.35f, 0.32f, 0.3f);
        b.bevelBox(-3.0f, 0, -0.7f, 3.0f, 0.4f, 0.7f, 0.08f, BOIS_GRIS);
        b.color(1);
        b.pop();
        decalO(b, BRULURE, 'y', 12.0f, 0.62f, 12.6f, 2.6f, 2.6f, 0, 0.06f);
        canetteEcrasee(b, 3.6f, 13.6f, CAN_BIERE, 10, 204);
    }

    // =================================================================== 21. jerricans

    /** Jerrican plastique 20 L (35 x 16 x 45 cm), coin bas au centre (0,0,0) local, poignée, bec bouchonné. */
    static void jerrican(MeshBuilder b, Bosse bosse) {
        float L = 5.6f, W = 2.6f, H = 6.6f;
        cuve(b, 0, 0, new float[][]{{L - 0.3f, W - 0.3f, 0.5f, 0}, {L, W, 0.6f, 0.35f}, {L, W, 0.6f, H - 0.5f}, {L - 0.3f, W - 0.3f, 0.5f, H}}, 2, PLASTIQUE_USE, false, bosse);
        capot(b, 0, H, 0, L - 0.3f, W - 0.3f, 0.5f, 2, PLASTIQUE_USE, true);
        // poignée moulée (trois montants, barre)
        for (float x : new float[]{-1.6f, 0.2f}) b.bevelBox(x - 0.3f, H - 0.05f, -0.4f, x + 0.3f, H + 1.0f, 0.4f, 0.12f, PLASTIQUE_USE);
        b.tube(new float[][]{p(-2.0f, H + 0.9f, 0), p(0.6f, H + 0.9f, 0)}, fill(2, 0.32f), 8, PLASTIQUE_USE, true, true, 0, false);
        // bec et bouchon
        b.push().translate(1.8f, H - 0.05f, 0).rotateZ(-20);
        b.cylinder(0.5f, 0, 0.8f, 10, PLASTIQUE_USE, null, null);
        b.lathe(new float[][]{{0.62f, 0.75f}, {0.62f, 1.25f}, {0.5f, 1.32f}, {0.01f, 1.34f}}, 10, 0, 360, DARK, 1, 1, 0, 0, 0, false);
        b.pop();
        // nervures en relief sur les flancs
        for (int s = -1; s <= 1; s += 2) {
            b.box(-L / 2 + 0.6f, 1.0f, s * W / 2 - (s > 0 ? 0.04f : 0.12f), L / 2 - 0.6f, 1.25f, s * W / 2 + (s > 0 ? 0.12f : 0.04f), PLASTIQUE_USE);
            b.box(-L / 2 + 0.6f, H - 1.5f, s * W / 2 - (s > 0 ? 0.04f : 0.12f), L / 2 - 0.6f, H - 1.25f, s * W / 2 + (s > 0 ? 0.12f : 0.04f), PLASTIQUE_USE);
        }
    }

    /** Trois jerricans 20 L (bleu, blanc jauni, rouge couché) et un entonnoir ; flaque de gazole, étiquette arrachée. */
    static void jerricans(MeshBuilder b) {
        b.push().translate(4.0f, 0, 2.2f).rotateY(4);
        b.color(0.28f, 0.42f, 0.68f);
        jerrican(b, coup(1.2f, 3.0f, 1.3f, 1.4f, 0.3f));
        b.color(1);
        decalT(b, ETIQUETTE_DECHIREE, -0.6f, 3.4f, 1.32f, 2.6f, 1.3f, 0, 0, -6, 0.06f);
        b.pop();
        b.push().translate(10.6f, 0, 2.3f).rotateY(-9);
        b.color(0.84f, 0.8f, 0.64f);
        jerrican(b, null);
        b.color(1);
        decalT(b, AUREOLE, 0.8f, 2.4f, 1.32f, 2.2f, 2.2f, 0, 0, 0, 0.06f);
        decalT(b, BOUE_1, 0, 0.55f, 1.32f, 5.0f, 0.9f, 0, 0, 0, 0.06f);
        b.pop();
        // rouge couché devant
        b.push().translate(7.0f, 1.46f, 9.6f).rotateY(-18).rotateX(90).translate(0, -3.3f, 0);
        b.color(0.66f, 0.2f, 0.15f);
        jerrican(b, coup(-1.0f, 4.0f, -1.3f, 1.6f, 0.4f));
        b.color(1);
        b.pop();
        // entonnoir
        b.color(0.36f, 0.55f, 0.36f);
        b.push().translate(12.6f, 1.05f, 11.6f).rotateY(40).rotateZ(72);
        b.lathe(new float[][]{{0.22f, -2.0f}, {0.24f, 0}, {1.4f, 1.6f}, {1.5f, 1.85f}}, 12, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.4f, 1.8f}, {0.2f, 0.1f}}, 12, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, true);
        b.pop();
        b.color(1);
        decalO(b, D_GRAS, 'y', 9.4f, 0, 12.4f, 6.4f, 5.0f, 20, 0.07f);
        decalO(b, D_FLAQUE, 'y', 4.0f, 0, 9.0f, 4.0f, 3.0f, -20, 0.1f);
        jonchee(b, 1.0f, 13.0f, 15.0f, 15.6f, 4, FEUILLES, 1.1f, 211);
    }

    // =================================================================== 22. pots de peinture

    /** Pot de peinture métallique (rayon r, hauteur h) : étiquette, sertis, anse ; ouvert : peinture séchée. */
    static void pot(MeshBuilder b, float x, float y, float z, float r, float h, Mat lab, float ang, boolean ouvert, float cr, float cg, float cb) {
        b.push().translate(x, y, z);
        etiquetteCyl(b, r, 0.15f, h - 0.15f, 14, lab, ang, null, METAL_NU);
        b.lathe(new float[][]{{r - 0.05f, 0}, {r + 0.05f, 0.05f}, {r + 0.05f, 0.18f}, {r, 0.2f}}, 14, 0, 360, METAL_NU, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{r, h - 0.2f}, {r + 0.06f, h - 0.15f}, {r + 0.06f, h}, {r - 0.15f, h}}, 14, 0, 360, METAL_NU, 1, 1, 0, 0, 0, false);
        if (ouvert) {
            b.lathe(new float[][]{{r - 0.15f, h}, {r - 0.15f, h - 0.6f}}, 14, 0, 360, METAL_NU, 1, 1, 0, 0, 0, true);
            b.color(cr, cg, cb);
            b.disc(r - 0.15f, h - 0.6f, 14, PEINTURE_SECHE, true);
            b.color(1);
        } else {
            b.disc(r - 0.15f, h - 0.04f, 14, METAL_NU, true);
        }
        for (int s = -1; s <= 1; s += 2) b.bevelBox(s * r - 0.12f, h - 1.0f, -0.3f, s * r + 0.12f, h - 0.4f, 0.3f, 0.06f, METAL_NU);
        b.tube(new float[][]{p(-r - 0.1f, h - 0.7f, 0), p(-r * 0.7f, h - 0.1f, r * 0.75f), p(0, h + 0.1f, r * 1.05f), p(r * 0.7f, h - 0.1f, r * 0.75f), p(r + 0.1f, h - 0.7f, 0)}, fill(5, 0.06f), 4, METAL_NU, false, false, 0, false);
        b.pop();
    }

    /** Pots de peinture empilés, coulures sèches, un pot ouvert, couvercle, pinceau durci, rouleau et bac. */
    static void potsPeinture(MeshBuilder b) {
        pot(b, 3.4f, 0, 3.2f, 2.24f, 4.8f, POT_BLANC, 100, false, 1, 1, 1);
        pot(b, 8.4f, 0, 3.0f, 2.24f, 4.8f, POT_BLANC, 70, false, 1, 1, 1);
        pot(b, 3.6f, 4.82f, 3.0f, 1.44f, 3.0f, POT_ROUGE, 95, false, 1, 1, 1);
        pot(b, 12.8f, 0, 3.4f, 1.44f, 3.0f, POT_ROUGE, 110, true, 0.66f, 0.16f, 0.13f);
        pot(b, 8.2f, 4.82f, 2.8f, 1.44f, 3.0f, POT_BLANC, 80, true, 0.95f, 0.94f, 0.9f);
        // coulures de peinture séchée le long des pots
        decalCyl(b, CP_BLANC, 8.4f, 3.0f, 2.32f, 80, 3.6f, 1.6f, 2.6f, 0);
        decalCyl(b, CP_VERT, 3.4f, 3.2f, 2.32f, 60, 3.4f, 1.4f, 2.4f, 0);
        decalCyl(b, CP_BLANC, 12.8f, 3.4f, 1.52f, 100, 2.0f, 1.2f, 2.0f, 0);
        // couvercle au sol, pinceau dessus
        b.push().translate(11.4f, 0, 9.2f);
        b.cylinder(1.6f, 0, 0.18f, 14, METAL_NU, null, METAL_NU);
        b.color(0.66f, 0.16f, 0.13f);
        b.disc(1.2f, 0.25f, 14, PEINTURE_SECHE, true);
        b.color(1);
        b.pop();
        b.push().translate(11.2f, 0.42f, 9.4f).rotateY(-30);
        b.bevelBox(-2.6f, -0.2f, -0.18f, -0.2f, 0.12f, 0.18f, 0.08f, WALNUT);
        b.bevelBox(-0.2f, -0.24f, -0.55f, 0.6f, 0.16f, 0.55f, 0.06f, CHROME_PIQUE);
        b.color(0.66f, 0.2f, 0.16f);
        b.bevelBox(0.6f, -0.22f, -0.5f, 1.8f, 0.12f, 0.5f, 0.1f, PEINTURE_SECHE);
        b.color(1);
        b.pop();
        // rouleau et bac
        b.push().translate(4.6f, 0, 10.6f).rotateY(14);
        b.color(0.25f, 0.25f, 0.26f);
        b.bevelBox(-2.4f, 0, -1.8f, 2.4f, 0.25f, 1.8f, 0.1f, PLASTIQUE_USE);
        b.push().translate(0, 0.24f, 0);
        b.bevelBox(-2.4f, 0, -1.8f, 2.4f, 0.6f, -1.6f, 0.06f, PLASTIQUE_USE);
        b.bevelBox(-2.4f, 0, 1.6f, 2.4f, 0.6f, 1.8f, 0.06f, PLASTIQUE_USE);
        b.bevelBox(-2.4f, 0, -1.6f, -2.2f, 0.9f, 1.6f, 0.06f, PLASTIQUE_USE);
        b.pop();
        b.color(0.92f, 0.9f, 0.86f);
        b.face(PEINTURE_SECHE, v(0, 1, 0), p(-2.2f, 0.31f, 1.55f), p(2.2f, 0.31f, 1.55f), p(2.2f, 0.31f, -1.55f), p(-2.2f, 0.31f, -1.55f));
        b.push().translate(0.8f, 0.85f, 0).rotateX(90);
        b.cylinder(0.6f, -1.5f, 1.5f, 10, TISSU_USE, TISSU_USE, TISSU_USE);
        b.pop();
        b.color(1);
        b.tube(new float[][]{p(0.8f, 0.85f, 1.6f), p(0.8f, 0.9f, 1.9f), p(2.0f, 1.0f, 2.2f), p(3.6f, 0.5f, 2.6f)}, fill(4, 0.08f), 4, METAL_NU, false, false, 0, false);
        b.push().translate(4.2f, 0.8f, 2.8f).rotateZ(-14).rotateY(20);
        b.bevelBox(-0.8f, -0.25f, -0.25f, 1.6f, 0.25f, 0.25f, 0.1f, PLASTIC_GREY);
        b.pop();
        b.pop();
        // flaque de peinture séchée, coulures au sol
        b.color(0.93f, 0.92f, 0.88f);
        b.push().translate(8.2f, 0.06f, 7.4f).scale(1.6f, 1, 1);
        b.disc(1.4f, 0, 12, PEINTURE_SECHE, true);
        b.pop();
        b.color(1);
        decalO(b, CP_VERT, 'y', 2.4f, 0, 7.6f, 1.6f, 3.2f, 70, 0.07f);
        jonchee(b, 1.0f, 12.6f, 15.0f, 15.6f, 4, FEUILLES, 1.1f, 221);
    }

    // =================================================================== 23. boîtes à pizza

    /** Pile de boîtes à pizza grasses sur un carton aplati, la dernière ouverte (restes), canettes écrasées. */
    static void boitesPizza(MeshBuilder b) {
        float s = 5.3f, e = 0.64f;
        b.push().translate(7.4f, 0, 5.2f).rotateY(6);
        b.bevelBox(-5.6f, 0, -4.4f, 5.4f, 0.28f, 4.4f, 0.06f, CARTON_MOUILLE);
        b.pop();
        Random r = new Random(231);
        float y = 0.28f;
        for (int k = 0; k < 5; k++) {
            b.push().translate(6.0f + (r.nextFloat() - 0.5f) * 0.9f, y, 4.4f + (r.nextFloat() - 0.5f) * 0.8f).rotateY((r.nextFloat() - 0.5f) * 30);
            b.bevelBox(-s / 2, 0, -s / 2, s / 2, e, s / 2, 0.06f, k % 2 == 0 ? CARTON : CARTON_MOUILLE, new Mat[]{null, null, null, PIZZA, null, null}, 0);
            b.pop();
            y += e + 0.02f;
        }
        // boîte ouverte à côté : fond avec croûtes, couvercle relevé contre le mur
        b.push().translate(11.6f, 0.28f, 6.4f).rotateY(-10);
        b.bevelBox(-s / 2, 0, -s / 2, s / 2, 0.14f, s / 2, 0.04f, CARTON);
        for (int q = 0; q < 4; q++) {
            b.push().rotateY(q * 90);
            b.bevelBox(-s / 2, 0.1f, s / 2 - 0.12f, s / 2, e, s / 2, 0.03f, CARTON);
            b.pop();
        }
        b.face(RESTES.withWorld(10), v(0, 1, 0), p(-s / 2 + 0.15f, 0.2f, s / 2 - 0.15f), p(s / 2 - 0.15f, 0.2f, s / 2 - 0.15f), p(s / 2 - 0.15f, 0.2f, -s / 2 + 0.15f), p(-s / 2 + 0.15f, 0.2f, -s / 2 + 0.15f));
        decalO(b, D_GRAS, 'y', 0.6f, 0.2f, 0.4f, 3.6f, 3.6f, 0, 0.06f);
        for (int k = 0; k < 3; k++) {
            b.push().translate(-1.2f + k * 1.2f, 0.22f, 0.6f - k * 0.7f).rotateY(k * 70 + 20);
            b.prism(new float[][]{{0, 0}, {1.6f, -0.3f}, {1.6f, 0.3f}}, 0.18f, RESTES, RESTES);
            b.pop();
        }
        b.push().translate(0, e, -s / 2).rotateX(-100);
        b.bevelBox(-s / 2, 0, 0, s / 2, 0.1f, s, 0.03f, CARTON, new Mat[]{null, null, PIZZA.asFit().sub(0.3f, 0.3f, 0.7f, 0.7f), PIZZA, null, null}, 0);
        b.pop();
        b.pop();
        canetteEcrasee(b, 3.2f, 11.2f, CAN_BIERE, 30, 232);
        canetteEcrasee(b, 9.6f, 12.6f, CAN_COLA, -60, 233);
        canetteEcrasee(b, 13.6f, 11.0f, CAN_BIERE, 80, 234);
        canette(b, 13.4f, 0, 8.8f, CAN_BIERE, 120);
        feuille(b, 6.8f, 0, 13.4f, 2.2f, 2.2f, 15, 0.06f, PAPIER);
        decalO(b, D_GRAS, 'y', 9.0f, 0, 10.6f, 3.6f, 3.0f, 30, 0.07f);
        jonchee(b, 1.0f, 12.0f, 15.0f, 15.6f, 5, PAPIER, 1.0f, 235);
    }

    // =================================================================== 26. étal de marché

    /** Tréteau en bois (barre en z, deux cadres en A ouverts suivant x), centré en (x, z0..z1). */
    static void treteau(MeshBuilder b, float x, float z0, float z1, float h) {
        b.bevelBox(x - 0.45f, h - 0.6f, z0, x + 0.45f, h, z1, 0.1f, PINE);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(x, h - 0.6f, 0).rotateZ(s * 13);
            for (float z : new float[]{z0 + 0.4f, z1 - 1.1f}) b.bevelBox(-0.35f, -(h - 0.6f) / (float) Math.cos(Math.toRadians(13)) + 0.1f, z, 0.35f, 0, z + 0.7f, 0.1f, BOIS_GRIS);
            b.bevelBox(-0.25f, -h * 0.55f, z0 + 0.6f, 0.25f, -h * 0.55f + 0.6f, z1 - 0.6f, 0.08f, BOIS_GRIS);
            b.pop();
        }
    }

    /** Étal de marché sur deux blocs : planche sur tréteaux, cagettes renversées, légumes pourris, bâche bleue déchirée. */
    static void etalMarche(MeshBuilder b) {
        float h = 12.2f, z0 = 0.8f, z1 = 12.6f;
        treteau(b, 6.0f, z0 + 0.4f, z1 - 0.4f, h - 0.5f);
        treteau(b, 25.6f, z0 + 0.4f, z1 - 0.4f, h - 0.5f);
        b.push().translate(16.0f, h - 0.5f, 6.7f);
        b.bevelBox(-14.8f, 0, -5.9f, 14.8f, 0.5f, 5.9f, 0.12f, CONTREPLAQUE);
        b.pop();
        float yt = h + 0.02f;
        // cagettes sur l'étal (une renversée), une dessous
        b.push().translate(3.4f, yt, 2.0f).rotateY(4);
        LotT1a2.cagette(b, 9.6f, 6.4f, 2.8f, 0.42f, 0.6f, 0.4f);
        LotT1a2.legumes(b, 0, 0, 9.6f, 6.4f, 0.25f, 3, 261);
        b.pop();
        b.push().translate(15.4f, yt + 3.4f, 1.6f).rotateY(-6).rotateX(90);
        LotT1a2.cagette(b, 6.4f, 3.4f, 2.8f, 0.26f, 0.27f, 0.28f);
        b.pop();
        LotT1a2.tomates(b, 15.6f, 4.6f, 8.0f, 4.0f, yt, 8, 262);
        LotT1a2.legumes(b, 22.6f, 3.4f, 6.0f, 5.0f, yt, 1, 263);
        b.push().translate(10.6f, 0, 5.6f).rotateY(12);
        LotT1a2.cagette(b, 9.6f, 6.4f, 2.8f, 0.35f, 0.45f, 0.6f);
        LotT1a2.legumes(b, 0, 0, 9.6f, 6.4f, 0.25f, 2, 264);
        b.pop();
        // bâche bleue qui pend de l'avant, déchirée
        bache(b, 1.2f, 30.8f, yt + 0.52f, z1 + 0.1f, 9);
        // usure : jus de pourriture, feuilles, légumes tombés, étiquette prix
        LotT1a2.tomates(b, 20.0f, 13.0f, 6.0f, 2.6f, 0, 4, 265);
        decalO(b, D_SAUCE, 'y', 22.0f, 0, 14.0f, 4.0f, 2.6f, 0, 0.07f);
        decalO(b, D_FLAQUE, 'y', 9.0f, 0, 14.4f, 5.0f, 2.4f, 10, 0.07f);
        decalT(b, L_PROMO, 8.2f, yt + 1.4f, 2.0f + 6.4f + 0.18f, 2.4f, 1.2f, 4, 0, -5, 0.06f);
        jonchee(b, 1.0f, 13.6f, 31.0f, 15.6f, 10, SALADE, 1.3f, 266);
    }

    /** Bâche plastique tombant de l'avant de l'étal : plis, bord inférieur déchiqueté, deux faces. */
    static void bache(MeshBuilder b, float x0, float x1, float y0, float z, int g) {
        int nu = 22, nv = 6;
        MeshBuilder.SurfaceFn f = (u, v) -> {
            float x = x0 + (x1 - x0) * u;
            float bas = 4.6f + 2.2f * Noise.fbm(x * 0.35f + g, 1.7f, 0.3f, 2) + (u > 0.55f && u < 0.75f ? 3.5f : 0);
            float y = y0 - (y0 - bas) * v;
            float zz = z + 0.15f + v * (0.5f + 0.4f * (float) Math.sin(x * 1.3f)) + 0.2f * v * v;
            return new float[]{x, Math.max(0.1f, y), zz};
        };
        b.surface(f, nu, nv, BACHE, (x1 - x0) / 10f, 0.8f, false, MeshBuilder.Orient.KEEP);
        b.surface(f, nu, nv, BACHE, (x1 - x0) / 10f, 0.8f, false, MeshBuilder.Orient.FLIP);
        b.surface((u, v) -> new float[]{x0 + (x1 - x0) * u, y0 + 0.02f * v, z + 0.15f - 3.0f * v}, 8, 2, BACHE, (x1 - x0) / 10f, 0.3f, false, MeshBuilder.Orient.KEEP);
        for (float x : new float[]{x0 + 1.0f, (x0 + x1) / 2, x1 - 1.0f}) {
            b.push().translate(x, y0 - 0.2f, z + 0.25f).rotateX(90);
            b.torus(0.25f, 0.06f, 8, 4, 0, 360, METAL_NU);
            b.pop();
        }
    }
}
