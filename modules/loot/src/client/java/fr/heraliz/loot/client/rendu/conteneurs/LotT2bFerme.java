package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Lot T2b, ferme et jardin : bidons à lait, brouette, bottes de paille, sacs d'engrais sur palette, ruche, clapier,
 * tondeuse, outils de jardin. Tôle peinte et rouillée, alu cabossé, sapin grisé, paille, toile de raphia.
 */
final class LotT2bFerme {
    private LotT2bFerme() {
    }

    // =================================================================== bidons à lait
    static final float[][] PROFIL_BIDON = {{0.01f, 0.02f}, {2.1f, 0}, {2.3f, 0.12f}, {2.3f, 0.55f}, {2.2f, 0.62f}, {2.3f, 0.7f}, {2.38f, 1.4f},
            {2.38f, 5.6f}, {2.25f, 6.3f}, {1.7f, 7.4f}, {1.25f, 8.1f}, {1.15f, 8.9f}, {1.32f, 9.05f}, {1.32f, 9.3f}};

    /** Bidon à lait de 20 L en alu (base en y = 0, axe +Y), cabossé (bosse), anses selon X local. */
    static void bidon(MeshBuilder b, float bosse, int graine, boolean couvercle) {
        b.lathe(PROFIL_BIDON, 18, 0, 360, ALU, 1, 1, bosse, 0.55f, graine, false);
        for (float y : new float[]{1.6f, 5.2f}) {
            b.push().translate(0, y, 0);
            b.torus(2.4f, 0.1f, 18, 4, 0, 360, ALU);
            b.pop();
        }
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{p(s * 1.95f, 6.9f, 0), p(s * 2.55f, 7.5f, 0), p(s * 2.35f, 8.35f, 0), p(s * 1.4f, 8.3f, 0)}, fill(4, 0.13f), 6, ALU, false, false, 0, false);
        }
        if (couvercle) {
            b.push().translate(0, 9.12f, 0);
            couvercleBidon(b);
            b.pop();
        } else {
            b.push().translate(0, 9.0f, 0);
            b.disc(1.14f, 0, 14, INTERIEUR_SALE, true);
            b.pop();
        }
    }

    /** Couvercle champignon (base en y = 0). */
    static void couvercleBidon(MeshBuilder b) {
        b.cylinder(1.42f, 0, 0.45f, 14, ALU, ALU, null);
        b.lathe(new float[][]{{1.42f, 0.45f}, {1.15f, 0.75f}, {0.7f, 0.9f}, {0.3f, 0.95f}, {0.01f, 0.96f}}, 14, 0, 360, ALU, 1, 1, 0, 0, 0, false);
        b.tube(new float[][]{p(-0.5f, 0.85f, 0), p(-0.3f, 1.3f, 0), p(0.3f, 1.3f, 0), p(0.5f, 0.85f, 0)}, fill(4, 0.1f), 5, ALU, false, false, 0, false);
    }

    /**
     * Trois bidons à lait de ferme (20 L, alu terni et cabossé) : un fermé avec sa plaque de laiterie, un ouvert dont le
     * couvercle traîne au sol, un renversé devant (lait caillé séché) ; paille éparse.
     */
    static void bidonsLait(MeshBuilder b) {
        b.push().translate(3.7f, 0, 3.0f).rotateY(25);
        bidon(b, 0.03f, 3, true);
        etiquette(b, P_LAIT, 0, 0, 2.58f, 3.0f, 3.6f, 90, 50);
        b.pop();
        b.push().translate(9.6f, 0, 2.8f).rotateY(-40);
        bidon(b, 0.045f, 7, false);
        b.pop();
        b.push().translate(14.0f, 0.12f, 7.4f).rotateY(30).rotateX(4);
        couvercleBidon(b);
        b.pop();
        b.push().translate(8.4f, 2.5f, 10.4f).rotateY(-20).rotateZ(-90).translate(0, -4.65f, 0).rotateY(90);
        bidon(b, 0.06f, 11, false);
        b.pop();
        decal(b, AUREOLE, 'y', 13.6f, 0, 12.4f, 3.0f, 2.2f, 20);
        epars(b, 0.8f, 6.4f, 15.2f, 15.4f, 10, PAILLE, 1.4f, 41);
    }

    // =================================================================== brouette
    /**
     * Brouette de chantier (1,45 m) le long du mur : caisse de tôle peinte vert passé, rouillée, sac de terreau éventré
     * dedans, terre renversée ; pneu à plat, châssis rouillé, poignées de caoutchouc.
     */
    static void brouette(MeshBuilder b) {
        float wx = 3.0f, wy = 2.41f, wz = 6.2f;
        // roue (pneu à plat)
        b.push().translate(wx, wy, wz).scale(1, 0.86f, 1).rotateX(90);
        b.torus(2.25f, 0.55f, 20, 8, 0, 360, CAOUTCHOUC_USE);
        b.cylinder(1.75f, -0.32f, 0.32f, 16, ROUILLE, ROUILLE, ROUILLE);
        b.cylinder(0.45f, -0.9f, 0.9f, 8, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        // châssis, pieds, poignées
        for (int s = -1; s <= 1; s += 2) {
            float zh = s < 0 ? 2.0f : 10.4f;
            b.tube(new float[][]{p(wx, wy, wz + s * 1.15f), p(5.6f, 4.3f, wz + s * 2.4f), p(9.0f, 5.0f, wz + s * 3.6f), p(16.0f, 5.9f, zh), p(21.0f, 7.0f, zh), p(23.4f, 7.5f, zh)},
                    fill(6, 0.2f), 7, ROUILLE, true, true, 0, false);
            b.tube(new float[][]{p(21.3f, 7.06f, zh), p(23.9f, 7.6f, zh)}, fill(2, 0.3f), 8, CAOUTCHOUC_USE, true, true, 0, false);
            b.tube(new float[][]{p(15.2f, 5.75f, zh), p(15.6f, 3.0f, zh), p(15.9f, 0.3f, zh)}, fill(3, 0.18f), 6, ROUILLE, false, false, 0, false);
            b.bevelBox(15.3f, 0, zh - 0.5f, 16.6f, 0.32f, zh + 0.5f, 0.08f, ROUILLE);
        }
        // caisse : parois inclinées (dehors vert, dedans terre et rouille), rebord roulé
        float[] B0 = p(6.6f, 6.2f, 2.6f), B1 = p(14.8f, 6.2f, 2.6f), B2 = p(14.8f, 6.2f, 9.8f), B3 = p(6.6f, 6.2f, 9.8f);
        float[] T0 = p(4.0f, 9.8f, 1.2f), T1 = p(17.0f, 9.8f, 1.2f), T2 = p(17.0f, 9.8f, 11.2f), T3 = p(4.0f, 9.8f, 11.2f);
        float[] c = p(10.6f, 8.6f, 6.2f);
        paroi(b, c, B0, B3, T3, T0);
        paroi(b, c, B1, T1, T2, B2);
        paroi(b, c, B0, T0, T1, B1);
        paroi(b, c, B3, B2, T2, T3);
        paroi(b, c, B0, B1, B2, B3);
        b.tube(new float[][]{T0, T1, T2, T3, T0}, fill(5, 0.2f), 6, ROUILLE, false, false, 0, false);
        // usure sur la paroi de devant
        repere(b, B3, sub(B2, B3), sub(T3, B3));
        coulure(b, 'z', 2.0f, 3.6f, 0.06f, 1.2f, 0);
        coulure(b, 'z', 6.4f, 3.3f, 0.06f, 1.0f, 2);
        b.decalZ(GRIFFURES, 4.4f, 1.6f, 0.06f, 3.0f, 1.4f);
        b.pop();
        // sac de terreau éventré et terre
        b.color(0.34f, 0.46f, 0.32f);
        b.push().translate(10.6f, 7.25f, 6.2f).rotateY(8).rotateZ(-4);
        b.blob(4.3f, 0.95f, 2.9f, 0.35f, 0.05f, 1.4f, 5, 16, PLASTIQUE_USE);
        b.pop();
        b.color(1);
        b.push().translate(10.9f, 8.17f, 6.4f).rotateY(8).rotateX(-90);
        b.decalZ(S_TERREAU, 0, 0, 0.06f, 5.2f, 2.6f);
        b.pop();
        b.push().translate(7.6f, 7.2f, 6.0f).scale(1.2f, 0.7f, 2.0f);
        b.sphereSection(1.2f, 10, 4, 0, 90, TERRE);
        b.pop();
        b.push().translate(3.4f, 0, 9.6f).scale(2.2f, 0.45f, 1.6f);
        b.sphereSection(1.0f, 10, 4, 0, 90, TERRE);
        b.pop();
        eclats(b, 5.6f, 11.6f, 2.2f, 8, TERRE, 17);
    }

    /** Paroi de caisse en tôle : face extérieure (vert passé) et intérieure (décalée de 0,12 vers c). */
    static void paroi(MeshBuilder b, float[] c, float[]... q) {
        float[] n = norm(cross(sub(q[1], q[0]), sub(q[2], q[0])));
        float[] m = {(q[0][0] + q[2][0]) / 2 - c[0], (q[0][1] + q[2][1]) / 2 - c[1], (q[0][2] + q[2][2]) / 2 - c[2]};
        if (n[0] * m[0] + n[1] * m[1] + n[2] * m[2] < 0) n = new float[]{-n[0], -n[1], -n[2]};
        b.color(0.45f, 0.58f, 0.4f);
        b.face(PEINTURE_USEE, n, q);
        float[][] in = new float[4][];
        for (int k = 0; k < 4; k++) in[k] = p(q[k][0] - n[0] * 0.12f, q[k][1] - n[1] * 0.12f, q[k][2] - n[2] * 0.12f);
        boolean fond = n[1] < -0.9f;
        b.color(fond ? 1 : 0.36f, fond ? 1 : 0.42f, fond ? 1 : 0.32f);
        b.face(fond ? TERRE : PEINTURE_USEE, new float[]{-n[0], -n[1], -n[2]}, in);
        b.color(1);
    }

    // =================================================================== bottes de paille
    /** Deux petites bottes de paille (80 x 45 x 35 cm) empilées, ficelles orange ; fourche appuyée contre le mur. */
    static void bottesPaille(MeshBuilder b) {
        botte(b, 7.2f, 0, 4.2f, 0, 1);
        botte(b, 7.0f, 5.62f, 4.6f, 8, 2);
        epars(b, 0.6f, 8.0f, 14.0f, 14.2f, 16, PAILLE, 1.3f, 51);
        fourche(b);
        toile(b, 2.2f, 13.2f, 0.06f, 1.2f, 1.9f, -90, 0);
    }

    static void botte(MeshBuilder b, float cx, float y, float cz, float ang, int graine) {
        float L = 12.8f, W = 7.2f, H = 5.6f, l = L / 2;
        b.push().translate(cx, y, cz).rotateY(ang);
        b.bevelBox(-l, 0, -W / 2, l, H, W / 2, 0.7f, PAILLE, null, MeshBuilder.SKIP_NY);
        for (float z : new float[]{-W * 0.27f, W * 0.27f}) {
            b.tube(new float[][]{p(-l - 0.06f, 0.4f, z), p(-l - 0.06f, H - 0.7f, z), p(-l + 0.7f, H + 0.06f, z), p(l - 0.7f, H + 0.06f, z), p(l + 0.06f, H - 0.7f, z), p(l + 0.06f, 0.4f, z)},
                    fill(6, 0.08f), 4, AtlasCivil.ORANGE, false, false, 0, false);
        }
        Random r = new Random(graine);
        for (int k = 0; k < 7; k++) {
            float x = -l + r.nextFloat() * L, side = r.nextBoolean() ? 1 : -1;
            b.push().translate(x, H * (0.3f + r.nextFloat() * 0.6f), side * (W / 2 + 0.15f)).rotateY(r.nextFloat() * 60 - 30).rotateZ(r.nextFloat() * 70 - 35);
            b.decalDouble(PAILLE.sub(r.nextFloat() * 0.5f, r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.5f), 1.4f, 0.5f);
            b.pop();
        }
        b.pop();
    }

    /** Fourche à foin (dents en haut) appuyée contre le mur. */
    static void fourche(MeshBuilder b) {
        float[] A = p(14.7f, 0.12f, 6.4f), T = p(15.0f, 18.4f, 0.2f);
        float[] d = norm(sub(T, A));
        float L = dist(A, T);
        repere(b, A, p(1, 0, 0), d);
        float lm = L - 4.2f;
        b.color(1.1f, 1.05f, 0.95f);
        b.tube(new float[][]{p(0, 0, 0), p(0, lm, 0)}, new float[]{0.24f, 0.22f}, 8, SAPIN, true, false, 0, false);
        b.color(1);
        b.tube(new float[][]{p(0, lm - 0.6f, 0), p(0, lm + 0.4f, 0)}, new float[]{0.3f, 0.25f}, 8, ROUILLE, true, false, 0, false);
        b.bevelBox(-0.85f, lm + 0.3f, -0.15f, 0.85f, lm + 0.6f, 0.15f, 0.06f, ROUILLE);
        for (int k = 0; k < 4; k++) {
            float x = -0.75f + k * 0.5f;
            b.tube(new float[][]{p(x, lm + 0.5f, 0), p(x * 1.05f, lm + 2.0f, 0.3f), p(x * 1.1f, lm + 3.4f, 0.2f), p(x * 1.1f, lm + 4.1f, 0.02f)},
                    new float[]{0.1f, 0.09f, 0.07f, 0.04f}, 5, ROUILLE, false, true, 0, false);
        }
        b.pop();
    }

    // =================================================================== sacs d'engrais
    /**
     * Sacs d'engrais de 50 kg (raphia imprimé « TERRAGRI NPK ») sur une palette grisée, sur deux blocs : deux couches
     * croisées et un sac au sommet ; un sac tombé à côté, éventré, granulés répandus.
     */
    static void sacsEngrais(MeshBuilder b) {
        palette(b, 0.5f, 0.4f);
        float y = 2.3f;
        sac(b, 5.4f, y, 7.6f, 90, 1);
        sac(b, 14.6f, y, 7.6f, 88, 2);
        sac(b, 10.2f, y + 2.25f, 4.9f, 3, 3);
        sac(b, 9.4f, y + 4.5f, 5.4f, -6, 4);
        // sac tombé, éventré, granulés
        b.push().translate(26.0f, 1.12f, 6.4f).rotateY(15).rotateZ(-2);
        b.blob(5.0f, 1.0f, 4.0f, 0.5f, 0.08f, 1.2f, 9, 16, RAPHIA);
        b.pop();
        b.push().translate(25.8f, 2.2f, 6.2f).rotateY(15).rotateX(-90).rotateZ(4);
        b.decalZ(S_ENGRAIS, 0, 0, 0.06f, 6.2f, 3.1f);
        b.pop();
        for (int k = 0; k < 3; k++) {
            b.push().translate(24.6f + k * 0.9f, 0.7f + k * 0.15f, 10.3f + k * 0.2f).rotateY(-10 + k * 25).rotateX(-35 - k * 10);
            b.decalDouble(RAPHIA, 1.4f, 0.9f);
            b.pop();
        }
        b.push().translate(25.5f, 0, 12.6f).scale(2.0f, 0.5f, 1.3f);
        b.sphereSection(1.0f, 12, 4, 0, 90, GRANULES);
        b.pop();
        eclats(b, 23.6f, 13.0f, 2.2f, 12, GRANULES, 31);
        decal(b, AUREOLE, 'y', 21.0f, 0, 11.8f, 3.0f, 2.0f, -10);
    }

    static void sac(MeshBuilder b, float x, float y, float z, float ang, int graine) {
        b.push().translate(x, y + 1.12f, z).rotateY(ang);
        b.blob(7.1f, 1.15f, 4.35f, 0.3f, 0.02f, 1.2f, graine, 16, RAPHIA);
        b.pop();
        b.push().translate(x, y + 2.45f, z).rotateY(ang).rotateX(-90).rotateZ(graine % 2 == 0 ? 2 : -3);
        b.decalZ(S_ENGRAIS, 0, 0, 0.05f, 8.6f, 4.3f);
        b.pop();
    }

    /** Palette Europe (1,2 x 0,8 m) grisée, coin arrière gauche (x0, z0). */
    static void palette(MeshBuilder b, float x0, float z0) {
        Mat m = SAPIN;
        float L = 19.2f, W = 12.8f;
        b.color(0.9f, 0.86f, 0.78f);
        for (float z : new float[]{z0 + 0.25f, z0 + W / 2 - 0.75f, z0 + W - 1.75f}) bois(b, x0, 0, z, x0 + L, 0.35f, z + 1.5f, 0.05f, m);
        for (float x : new float[]{x0 + 0.3f, x0 + L / 2 - 0.7f, x0 + L - 1.7f}) {
            for (float z : new float[]{z0 + 0.3f, z0 + W / 2 - 0.7f, z0 + W - 1.7f}) bois(b, x, 0.33f, z, x + 1.4f, 1.55f, z + 1.4f, 0.08f, m);
            bois(b, x - 0.05f, 1.53f, z0 + 0.05f, x + 1.45f, 1.88f, z0 + W - 0.05f, 0.05f, m);
        }
        for (int k = 0; k < 5; k++) {
            float z = z0 + 0.1f + k * (W - 2.3f) / 4;
            bois(b, x0, 1.86f, z, x0 + L, 2.3f, z + 2.1f, 0.06f, m);
        }
        b.color(1);
    }

    // =================================================================== ruche
    /**
     * Ruche Dadant abandonnée sur deux parpaings : corps bleu-vert pâle et hausse jaune passé dépareillés, toit de
     * tôle galvanisée de travers lesté d'une pierre, planche d'envol ; un cadre de cire sorti appuyé contre un
     * parpaing, un autre cassé au sol ; mousse, feuilles mortes, toile sur le trou de vol.
     */
    static void ruche(MeshBuilder b) {
        parpaing(b, 2.0f, 0.8f);
        parpaing(b, 10.8f, 0.8f);
        float y0 = 3.2f;
        bois(b, 3.6f, y0, 0.9f, 12.4f, y0 + 0.6f, 9.25f, 0.1f, SAPIN, NZ);
        b.push().translate(8, y0 + 0.6f, 9.2f).rotateX(14);
        bois(b, -4.0f, -0.38f, -0.1f, 4.0f, 0.0f, 1.8f, 0.08f, SAPIN);
        b.pop();
        b.color(0.6f, 0.74f, 0.7f);
        bois(b, 4.0f, y0 + 0.6f, 1.0f, 12.0f, y0 + 5.6f, 9.0f, 0.12f, BOIS_PEINT, NZ);
        b.color(0.86f, 0.8f, 0.56f);
        bois(b, 4.05f, y0 + 5.6f, 1.05f, 11.95f, y0 + 8.3f, 8.95f, 0.12f, BOIS_PEINT, NZ);
        b.color(1);
        b.box(6.0f, y0 + 0.62f, 9.0f, 10.0f, y0 + 1.05f, 9.06f, AtlasCivil.DARK);
        decal(b, P_RUCHE, 'z', 8.0f, y0 + 3.6f, 9.0f, 3.2f, 0.8f);
        for (float x : new float[]{3.88f, 12.07f}) b.box(x, y0 + 3.4f, 3.6f, x + 0.05f, y0 + 4.0f, 6.4f, AtlasCivil.DARK);
        for (float x : new float[]{3.93f, 12.02f}) b.box(x, y0 + 6.6f, 3.8f, x + 0.05f, y0 + 7.1f, 6.2f, AtlasCivil.DARK);
        bois(b, 4.08f, y0 + 8.28f, 1.08f, 11.92f, y0 + 8.5f, 8.92f, 0.05f, SAPIN);
        // toit de travers, tôle, pierre
        b.push().translate(8.4f, y0 + 8.48f, 5.4f).rotateY(7);
        bois(b, -4.45f, 0, -4.45f, 4.45f, 1.3f, 4.45f, 0.1f, SAPIN);
        b.bevelBox(-4.6f, 1.25f, -4.6f, 4.6f, 1.45f, 4.6f, 0.05f, GALVA_ROUILLE);
        for (int s = -1; s <= 1; s += 2) {
            b.box(-4.6f, 0.7f, s * 4.52f - 0.04f, 4.6f, 1.3f, s * 4.52f + 0.04f, GALVA_ROUILLE);
            b.box(s * 4.52f - 0.04f, 0.7f, -4.45f, s * 4.52f + 0.04f, 1.3f, 4.45f, GALVA_ROUILLE);
        }
        coulure(b, 'z', 2.2f, 1.25f, 4.56f, 0.3f, 1);
        coulure(b, 'z', -2.8f, 1.25f, 4.56f, 0.3f, 0);
        b.push().translate(-0.8f, 1.95f, 0.4f);
        b.rock(1.25f, 0.62f, 0.95f, 0.25f, 1.5f, 3, 10, BETON);
        b.pop();
        b.pop();
        // cadres
        b.push().translate(15.3f, 0.06f, 4.8f).rotateY(90).rotateX(-22);
        cadreRuche(b, false);
        b.pop();
        b.push().translate(4.4f, 0.21f, 12.4f).rotateY(-18).rotateX(-90).translate(0, -1.8f, 0);
        cadreRuche(b, true);
        b.pop();
        // usure
        decal(b, MOUSSE_VERTE, 'z', 3.6f, 0.8f, 8.8f, 3.0f, 1.5f);
        decal(b, MOUSSE_VERTE, 'z', 12.4f, 0.75f, 8.8f, 3.0f, 1.4f);
        toile(b, 6.1f, y0 + 1.04f, 9.4f, 2.2f, 1.0f, 0, 0);
        epars(b, 0.8f, 9.2f, 15.2f, 15.0f, 12, FEUILLES, 1.3f, 61);
    }

    /** Parpaing creux (20 x 20 x 50 cm) couché, alvéoles verticales, coin (x0, z0), long selon Z. */
    static void parpaing(MeshBuilder b, float x0, float z0) {
        float L = 8.0f, h = 3.2f;
        bois(b, x0, 0, z0, x0 + 0.5f, h, z0 + L, 0.07f, BETON, NZ);
        bois(b, x0 + 2.7f, 0, z0, x0 + 3.2f, h, z0 + L, 0.07f, BETON, NZ);
        for (float z : new float[]{z0, z0 + 3.75f, z0 + 7.5f}) bois(b, x0 + 0.5f, 0, z, x0 + 2.7f, h, z + 0.5f, 0.07f, BETON, z == z0 ? NZ : 0);
    }

    /** Cadre de ruche (corps Dadant, 45 x 23 cm) avec rayon de cire ; cassé = une barre manque, cire entamée. */
    static void cadreRuche(MeshBuilder b, boolean casse) {
        b.color(1.05f, 1.0f, 0.9f);
        bois(b, -4.0f, 3.4f, -0.2f, 4.0f, 3.7f, 0.2f, 0.05f, SAPIN);
        bois(b, -3.5f, 0.2f, -0.18f, -3.3f, 3.42f, 0.18f, 0.03f, SAPIN);
        if (!casse) bois(b, 3.3f, 0.2f, -0.18f, 3.5f, 3.42f, 0.18f, 0.03f, SAPIN);
        bois(b, -3.5f, 0, -0.15f, casse ? 1.2f : 3.5f, 0.22f, 0.15f, 0.03f, SAPIN);
        b.color(1);
        b.box(-3.32f, 0.22f, -0.07f, casse ? 1.6f : 3.32f, 3.42f, 0.07f, CIRE);
    }

    // =================================================================== clapier
    /**
     * Clapier à lapins (1,2 m, deux cases) en sapin grisé sur pieds : portes grillagées, la droite ouverte qui pend
     * d'un gond, toit pentu couvert de carton bitumé déchiré ; paille, gamelles, râtelier de foin sec ; paille au sol.
     */
    static void clapier(MeshBuilder b) {
        Mat bo = SAPIN;
        float x0 = 0.6f, x1 = 19.8f, z0 = 0.6f, zf = 9.8f, yb = 5.0f, yh = 14.0f;
        b.color(0.86f, 0.84f, 0.79f);
        for (float x : new float[]{x0, x1 - 1.0f}) {
            bois(b, x, 0, z0, x + 1.0f, yh, z0 + 1.0f, 0.1f, bo, NZ);
            bois(b, x, 0, zf - 1.0f, x + 1.0f, yh, zf, 0.1f, bo);
        }
        bois(b, 9.6f, yb + 0.15f, zf - 1.0f, 10.8f, yh - 0.15f, zf + 0.05f, 0.1f, bo);
        bois(b, x0 + 0.1f, yb, z0 + 0.05f, x1 - 0.1f, yb + 0.6f, zf - 0.1f, 0.06f, bo, NZ);
        bois(b, x0 + 1.0f, yb + 0.55f, z0 + 0.1f, x1 - 1.0f, yh - 0.05f, z0 + 0.6f, 0.05f, bo, NZ);
        bois(b, x0 + 0.15f, yb + 0.55f, z0 + 1.0f, x0 + 0.7f, yh, zf - 1.0f, 0.05f, bo);
        bois(b, x1 - 0.7f, yb + 0.55f, z0 + 1.0f, x1 - 0.15f, yh, zf - 1.0f, 0.05f, bo);
        bois(b, 9.9f, yb + 0.55f, z0 + 0.6f, 10.5f, yh - 0.4f, zf - 1.0f, 0.05f, bo);
        bois(b, x0 + 1.0f, yh - 0.5f, z0 + 0.6f, x1 - 1.0f, yh, zf - 0.1f, 0.05f, bo);
        bois(b, x0 + 0.95f, yh - 1.1f, zf - 0.9f, x1 - 0.95f, yh - 0.06f, zf - 0.05f, 0.08f, bo);
        bois(b, x0 + 0.95f, yb + 0.22f, zf - 0.9f, x1 - 0.95f, yb + 0.95f, zf - 0.05f, 0.08f, bo);
        // pignons sous le toit
        for (float x : new float[]{x0 + 0.68f, x1}) {
            b.push().translate(x, 0, 0).rotateY(-90);
            b.prism(new float[][]{{z0, yh - 0.05f}, {zf - 0.1f, yh - 0.05f}, {z0, 15.82f}}, 0.62f, bo, bo);
            b.pop();
        }
        // porte gauche fermée, porte droite ouverte qui pend
        porteClapier(b, 1.7f, yb + 1.0f, 9.45f, yh - 1.2f, zf + 0.06f);
        b.push().translate(10.95f, yb + 1.0f, zf + 0.06f).rotateY(-48).rotateZ(-4).translate(-10.95f, -(yb + 1.0f), -(zf + 0.06f));
        porteClapier(b, 10.95f, yb + 1.0f, 18.7f, yh - 1.2f, zf + 0.06f);
        b.pop();
        b.color(1);
        // toit pentu : planches et carton bitumé déchiré
        b.push().translate(0, 15.9f, 0.3f).rotateX(10.8f);
        b.color(0.86f, 0.84f, 0.79f);
        bois(b, x0 - 0.4f, -0.5f, 0, x1 + 0.4f, 0, 10.7f, 0.08f, bo);
        b.color(1);
        b.bevelBox(x0 - 0.5f, -0.04f, -0.05f, 12.4f, 0.12f, 10.8f, 0.03f, CAOUTCHOUC_USE);
        b.bevelBox(13.2f, -0.04f, -0.05f, x1 + 0.5f, 0.12f, 7.6f, 0.03f, CAOUTCHOUC_USE);
        b.push().translate(13.2f, 0.1f, 7.6f).rotateX(-28);
        b.bevelBox(0, -0.04f, 0, x1 + 0.5f - 13.2f, 0.06f, 2.6f, 0.02f, CAOUTCHOUC_USE);
        b.pop();
        b.pop();
        // intérieur : paille, gamelles, râtelier de foin
        b.push().translate(5.2f, yb + 0.62f, 5.0f).scale(3.8f, 0.35f, 3.4f);
        b.sphereSection(1.0f, 12, 4, 0, 90, PAILLE);
        b.pop();
        b.push().translate(14.8f, yb + 0.62f, 4.6f).scale(3.6f, 0.4f, 3.2f);
        b.sphereSection(1.0f, 12, 4, 0, 90, PAILLE);
        b.pop();
        gamelle(b, 3.2f, yb + 0.6f, 7.4f, false);
        b.push().translate(16.2f, yb + 0.95f, 7.8f).rotateZ(110);
        gamelle(b, 0, 0, 0, true);
        b.pop();
        b.push().translate(14.6f, yh - 1.0f, 1.0f).rotateX(-25);
        b.bevelBox(-2.5f, -3.4f, -0.05f, 2.5f, 0, 0.05f, 0.02f, AtlasUsure.METAL_NU);
        b.pop();
        b.push().translate(14.6f, yh - 3.0f, 1.6f).scale(2.4f, 1.6f, 0.7f);
        b.sphereSection(1.0f, 10, 4, 0, 180, HERBE);
        b.pop();
        // usure : paille et feuilles au sol, mousse, toile
        epars(b, 1.0f, 2.0f, 19.0f, 13.0f, 14, PAILLE, 1.3f, 71);
        epars(b, 3.0f, 9.0f, 18.0f, 14.0f, 8, FEUILLES, 1.2f, 72);
        decal(b, MOUSSE_VERTE, 'z', 1.1f, 0.6f, zf, 0.9f, 1.2f);
        decal(b, MOUSSE_VERTE, 'z', 19.3f, 0.6f, zf, 0.9f, 1.2f);
        toile(b, x0 + 1.02f, yb - 0.02f, zf - 1.1f, 3.4f, 3.0f, 0, 0);
        coulure(b, 'z', 10.2f, yh - 1.2f, zf + 0.05f, 0.8f, 1);
    }

    /** Porte de clapier : cadre de liteaux et grillage soudé (double face) ; face avant en zf. */
    static void porteClapier(MeshBuilder b, float x0, float y0, float x1, float y1, float zf) {
        b.color(0.86f, 0.84f, 0.79f);
        float e = 0.6f, zb = zf - 0.45f;
        bois(b, x0, y0, zb, x0 + e, y1, zf, 0.06f, SAPIN);
        bois(b, x1 - e, y0, zb, x1, y1, zf, 0.06f, SAPIN);
        bois(b, x0 + e, y0, zb + 0.03f, x1 - e, y0 + e, zf - 0.03f, 0.06f, SAPIN);
        bois(b, x0 + e, y1 - e, zb + 0.03f, x1 - e, y1, zf - 0.03f, 0.06f, SAPIN);
        b.color(1);
        grille(b, GRILLAGE, x0 + e - 0.1f, y0 + e - 0.1f, x1 - e + 0.1f, y1 - e + 0.1f, zb + 0.22f);
        b.box(x1 - e - 0.05f, (y0 + y1) / 2 - 0.2f, zf, x1 + 0.6f, (y0 + y1) / 2 + 0.2f, zf + 0.3f, SAPIN);
        b.box(x0 - 0.3f, y0 + 0.8f, zf - 0.1f, x0 + 0.5f, y0 + 1.3f, zf + 0.06f, AtlasCivil.DARK);
        b.box(x0 - 0.3f, y1 - 1.3f, zf - 0.1f, x0 + 0.5f, y1 - 0.8f, zf + 0.06f, AtlasCivil.DARK);
    }

    /** Gamelle en tôle émaillée (renversée si renv). */
    static void gamelle(MeshBuilder b, float x, float y, float z, boolean renv) {
        b.push().translate(x, y, z);
        b.color(0.75f, 0.78f, 0.82f);
        b.lathe(new float[][]{{0.9f, 0}, {1.0f, 0.1f}, {1.25f, 0.7f}, {1.3f, 0.75f}}, 12, 0, 360, EMAIL, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.22f, 0.75f}, {0.95f, 0.13f}, {0.01f, 0.13f}}, 12, 0, 360, EMAIL, 1, 1, 0, 0, 0, true);
        b.disc(0.9f, 0.003f, 12, EMAIL, false);
        b.color(1);
        b.pop();
    }

    // =================================================================== tondeuse
    /**
     * Tondeuse thermique à bac de ramassage, guidon relevé (rangement) : carter vert passé rouillé, moteur à lanceur,
     * réservoir rouge, bac de toile plein d'herbe sèche ; paquets d'herbe collés, tache d'huile, autocollant usé.
     */
    static void tondeuse(MeshBuilder b) {
        b.color(0.45f, 0.58f, 0.4f);
        b.bevelBox(1.2f, 0.9f, 1.6f, 9.2f, 2.6f, 9.4f, 0.35f, PEINTURE_USEE, null, MeshBuilder.SKIP_NY);
        b.bevelBox(1.8f, 2.5f, 2.3f, 8.6f, 3.1f, 8.7f, 0.4f, PEINTURE_USEE);
        b.color(1);
        for (float x : new float[]{2.5f, 8.0f}) {
            for (float z : new float[]{1.25f, 9.75f}) {
                b.push().translate(x, 1.5f, z).rotateX(90);
                b.cylinder(1.5f, -0.3f, 0.3f, 12, CAOUTCHOUC_USE, AtlasCivil.PLASTIC_GREY, AtlasCivil.PLASTIC_GREY);
                b.cylinder(0.4f, -0.4f, 0.4f, 6, METAL_NU, METAL_NU, METAL_NU);
                b.pop();
            }
        }
        // moteur
        b.bevelBox(3.8f, 3.0f, 3.6f, 7.0f, 5.6f, 7.4f, 0.4f, FONTE);
        for (float y = 3.4f; y < 5.4f; y += 0.45f) b.bevelBox(3.0f, y, 4.2f, 3.85f, y + 0.18f, 6.8f, 0.05f, FONTE);
        b.color(0.68f, 0.24f, 0.18f);
        b.bevelBox(4.2f, 5.55f, 3.8f, 6.7f, 6.5f, 5.6f, 0.35f, PEINTURE_USEE);
        b.color(1);
        b.push().translate(5.0f, 6.5f, 4.6f);
        b.cylinder(0.35f, 0, 0.35f, 8, AtlasCivil.DARK, null, AtlasCivil.DARK);
        b.pop();
        b.push().translate(5.6f, 5.6f, 6.6f);
        b.cylinder(1.35f, 0, 1.0f, 14, ALU, null, ALU);
        b.pop();
        b.bevelBox(4.9f, 6.6f, 6.4f, 6.3f, 6.95f, 6.8f, 0.12f, AtlasCivil.DARK);
        b.bevelBox(6.8f, 3.4f, 7.2f, 8.2f, 5.2f, 8.4f, 0.3f, AtlasCivil.DARK);
        b.bevelBox(2.4f, 2.9f, 6.2f, 3.6f, 3.8f, 7.6f, 0.2f, ROUILLE);
        b.push().translate(3.3f, 4.6f, 5.5f).rotateZ(70);
        b.cylinder(0.22f, 0, 0.9f, 6, METAL_NU, null, METAL_NU);
        b.pop();
        // bac de ramassage (toile sur cadre) plein d'herbe
        b.color(0.32f, 0.36f, 0.3f);
        b.push().translate(12.0f, 3.1f, 5.5f).rotateZ(-6);
        b.blob(2.7f, 2.1f, 3.1f, 0.55f, 0.06f, 1.2f, 4, 16, TISSU_USE);
        b.pop();
        b.color(1);
        b.tube(new float[][]{p(9.3f, 4.9f, 2.6f), p(14.4f, 4.6f, 2.6f), p(14.4f, 4.6f, 8.4f), p(9.3f, 4.9f, 8.4f), p(9.3f, 4.9f, 2.6f)}, fill(5, 0.12f), 5, AtlasCivil.DARK, false, false, 0, false);
        b.push().translate(11.8f, 4.75f, 5.5f).scale(2.2f, 0.6f, 2.5f);
        b.sphereSection(1.0f, 10, 4, 0, 90, HERBE);
        b.pop();
        // guidon relevé, barre, levier, câble
        for (float z : new float[]{2.4f, 8.6f}) {
            b.tube(new float[][]{p(8.7f, 2.6f, z), p(10.4f, 6.0f, z), p(13.1f, 11.0f, z), p(15.1f, 14.2f, z)}, fill(4, 0.17f), 6, METAL_NU, true, false, 0, false);
        }
        b.tube(new float[][]{p(15.1f, 14.2f, 2.4f), p(15.35f, 14.55f, 2.9f), p(15.35f, 14.55f, 8.1f), p(15.1f, 14.2f, 8.6f)}, fill(4, 0.18f), 6, CAOUTCHOUC_USE, false, false, 0, false);
        b.tube(new float[][]{p(14.6f, 13.7f, 2.6f), p(14.1f, 13.3f, 3.2f), p(14.1f, 13.3f, 7.8f), p(14.6f, 13.7f, 8.4f)}, fill(4, 0.1f), 5, METAL_NU, false, false, 0, false);
        b.tube(new float[][]{p(14.2f, 13.4f, 2.7f), p(12.0f, 9.4f, 2.2f), p(9.6f, 5.4f, 2.2f), p(7.0f, 5.2f, 3.4f)}, fill(4, 0.05f), 4, AtlasCivil.DARK, false, false, 0, false);
        // herbe sèche collée et répandue, tache d'huile, autocollant, rouille
        for (float[] q : new float[][]{{1.4f, 5.0f}, {5.0f, 9.5f}, {8.8f, 9.4f}, {3.0f, 1.6f}}) {
            b.push().translate(q[0], 0, q[1]).scale(1.3f, 0.5f, 1.0f);
            b.sphereSection(1.0f, 8, 3, 0, 90, HERBE);
            b.pop();
        }
        epars(b, 0.6f, 9.5f, 15.0f, 15.0f, 12, HERBE, 1.1f, 81);
        b.color(0.35f, 0.33f, 0.3f);
        decal(b, AUREOLE, 'y', 6.0f, 0, 11.6f, 3.4f, 2.4f, -20);
        b.color(1);
        decal(b, P_TONDEUSE, 'z', 4.6f, 1.75f, 9.4f, 3.2f, 0.8f);
        coulure(b, 'z', 7.6f, 2.2f, 9.4f, 0.9f, 1);
        coulure(b, 'x', 1.2f, 2.2f, 4.0f, 1.0f, 2);
    }

    // =================================================================== outils de jardin
    /**
     * Outils de jardin appuyés contre le mur (pelle lame en bas, râteau et binette tête en haut), seau galvanisé avec
     * un transplantoir, sac de graines en papier kraft, arrosoir ; terre sur les lames, rouille, feuilles mortes.
     */
    static void outilsJardin(MeshBuilder b) {
        // pelle (lame au sol)
        float[] A = p(3.2f, 0.15f, 6.6f), T = p(3.6f, 21.0f, 0.3f);
        repere(b, A, p(1, 0, 0), sub(T, A));
        float L = dist(A, T);
        b.slab(new float[][]{{0, 0}, {1.8f, 1.1f}, {1.8f, 4.6f}, {-1.8f, 4.6f}, {-1.8f, 1.1f}}, 0.14f, 0.04f, false, ROUILLE, METAL_NU, METAL_NU);
        b.decalZ(BOUE_1, 0, 1.5f, 0.12f, 3.0f, 1.4f);
        b.tube(new float[][]{p(0, 4.4f, 0), p(0, 6.2f, 0)}, new float[]{0.4f, 0.26f}, 8, ROUILLE, false, false, 0, false);
        b.color(1.15f, 1.08f, 0.95f);
        b.tube(new float[][]{p(0, 6.0f, 0), p(0, L - 0.3f, 0)}, fill(2, 0.22f), 8, SAPIN, false, true, 0, false);
        b.color(1);
        b.pop();
        // râteau (tête en haut, dents vers l'avant)
        A = p(6.6f, 0.15f, 6.2f);
        T = p(7.2f, 21.4f, 1.4f);
        repere(b, A, p(1, 0, 0), sub(T, A));
        L = dist(A, T);
        b.color(1.15f, 1.08f, 0.95f);
        b.tube(new float[][]{p(0, 0, 0), p(0, L - 0.2f, 0)}, fill(2, 0.21f), 8, SAPIN, true, false, 0, false);
        b.color(1);
        b.bevelBox(-3.2f, L - 0.3f, -0.2f, 3.2f, L + 0.15f, 0.2f, 0.06f, ROUILLE);
        for (int k = 0; k < 13; k++) {
            float x = -3.0f + k * 0.5f;
            if (k == 4) continue;
            b.tube(new float[][]{p(x, L - 0.1f, 0.1f), p(x, L - 0.2f, 0.8f), p(x, L - 0.5f, 1.15f)}, new float[]{0.06f, 0.06f, 0.04f}, 4, ROUILLE, false, true, 0, false);
        }
        b.pop();
        // binette (lame en haut)
        A = p(10.0f, 0.15f, 6.8f);
        T = p(10.2f, 19.6f, 1.3f);
        repere(b, A, p(1, 0, 0), sub(T, A));
        L = dist(A, T);
        b.color(1.15f, 1.08f, 0.95f);
        b.tube(new float[][]{p(0, 0, 0), p(0, L, 0)}, fill(2, 0.2f), 8, SAPIN, true, false, 0, false);
        b.color(1);
        b.tube(new float[][]{p(0, L - 0.3f, 0), p(0, L + 0.25f, 0.2f), p(0, L + 0.1f, 0.55f)}, fill(3, 0.13f), 5, ROUILLE, false, false, 0, false);
        b.bevelBox(-1.1f, L - 1.9f, 0.5f, 1.1f, L + 0.15f, 0.66f, 0.04f, ROUILLE);
        b.pop();
        // seau galvanisé, transplantoir
        b.push().translate(13.0f, 0, 3.4f);
        b.lathe(new float[][]{{1.9f, 0}, {2.05f, 0.25f}, {2.6f, 4.4f}, {2.72f, 4.62f}}, 18, 0, 360, GALVA_ROUILLE, 1, 1, 0.02f, 0.6f, 5, false);
        b.lathe(new float[][]{{2.58f, 4.55f}, {2.0f, 0.4f}}, 18, 0, 360, GALVA_ROUILLE, 1, 1, 0, 0, 0, true);
        b.disc(1.9f, 0.02f, 18, GALVA_ROUILLE, false);
        b.push().translate(0, 0.42f, 0);
        b.disc(2.0f, 0, 18, TERRE, true);
        b.pop();
        b.push().translate(-0.6f, 2.6f, 0.4f).rotateZ(28).rotateX(-10);
        b.color(1.1f, 1.0f, 0.85f);
        b.cylinder(0.24f, 0, 2.6f, 7, SAPIN, null, SAPIN);
        b.color(1);
        b.push().translate(0, -0.1f, 0).rotateX(90);
        b.slab(new float[][]{{0, -1.8f}, {0.55f, -0.9f}, {0.5f, 0}, {-0.5f, 0}, {-0.55f, -0.9f}}, 0.08f, 0.02f, false, ROUILLE, ROUILLE, ROUILLE);
        b.pop();
        b.pop();
        b.pop();
        b.tube(new float[][]{p(10.3f, 4.3f, 3.4f), p(11.6f, 7.2f, 4.2f), p(14.4f, 7.2f, 4.2f), p(15.7f, 4.3f, 3.4f)}, fill(4, 0.08f), 4, METAL_NU, false, false, 0, false);
        // sac de graines en kraft, haut roulé
        b.push().translate(7.6f, 0, 10.6f).rotateY(-14);
        b.bevelBox(-2.1f, 0, -1.3f, 2.1f, 4.6f, 1.3f, 0.45f, AtlasCivil.CARTON, null, MeshBuilder.SKIP_NY);
        b.push().translate(0, 4.75f, 0).rotateZ(90);
        b.cylinder(0.45f, -2.0f, 2.0f, 10, AtlasCivil.CARTON, AtlasCivil.CARTON, AtlasCivil.CARTON);
        b.pop();
        b.decalZ(GRAINES, 0, 2.3f, 1.35f, 3.4f, 3.4f);
        b.pop();
        // arrosoir galvanisé
        b.push().translate(14.0f, 0, 9.4f).rotateY(-110);
        arrosoir(b);
        b.pop();
        // usure
        epars(b, 1.0f, 7.4f, 15.0f, 15.0f, 12, FEUILLES, 1.3f, 91);
        toile(b, 10.6f, 19.2f, 0.06f, 1.5f, 2.2f, -90, 0);
    }

    /** Arrosoir de tôle galvanisée (base en y = 0), bec vers +X local, anse dessus. */
    static void arrosoir(MeshBuilder b) {
        b.lathe(new float[][]{{0.01f, 0}, {1.75f, 0}, {1.8f, 0.2f}, {1.8f, 3.6f}, {1.65f, 4.0f}, {0.9f, 4.4f}}, 16, 0, 360, GALVA_ROUILLE, 1, 0.75f, 0.02f, 0.6f, 9, false);
        b.push().translate(-0.2f, 4.3f, 0);
        b.cylinder(0.9f, 0, 0.35f, 12, GALVA_ROUILLE, null, null);
        b.disc(0.86f, 0.3f, 12, INTERIEUR_SALE, true);
        b.pop();
        b.tube(new float[][]{p(1.6f, 0.8f, 0), p(3.0f, 2.6f, 0), p(4.4f, 4.6f, 0)}, new float[]{0.32f, 0.24f, 0.2f}, 8, GALVA_ROUILLE, false, false, 0, false);
        b.push().translate(4.4f, 4.6f, 0).rotateZ(-35);
        b.cylinder(0.5f, 0, 0.5f, 10, GALVA_ROUILLE, GALVA_ROUILLE, ROUILLE);
        b.pop();
        b.tube(new float[][]{p(-1.4f, 3.9f, 0), p(-1.0f, 5.6f, 0), p(0.6f, 5.6f, 0), p(1.1f, 4.0f, 0)}, fill(4, 0.13f), 6, GALVA_ROUILLE, false, false, 0, false);
        b.tube(new float[][]{p(-1.75f, 3.2f, 0), p(-2.3f, 2.4f, 0), p(-1.8f, 1.0f, 0)}, fill(3, 0.13f), 6, GALVA_ROUILLE, false, false, 0, false);
    }
}
