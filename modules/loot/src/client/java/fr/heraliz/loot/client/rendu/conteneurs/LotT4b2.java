package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T4b, suite : sacs de sable, jerricans, rations, caisse médicale. */
final class LotT4b2 {
    private LotT4b2() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t4_sacs_sable", "t4", "tissu", "Sacs de sable", "Sandbags", LotT4b2::sacsSable);
        Conteneurs.ajouter("t4_jerricans", "t4", "metal", "Jerricans militaires", "Military jerrycans", LotT4b2::jerricans);
        Conteneurs.ajouter("t4_rations", "t4", "carton", "Cartons de rations", "Ration boxes", LotT4b2::rations);
        Conteneurs.ajouter("t4_caisse_medicale", "t4", "metal", "Caisse médicale militaire", "Military medical chest", LotT4b2::caisseMedicale);
        LotT4b3.enregistrer();
    }

    // =================================================================== 5. sacs de sable
    /**
     * Muret de sacs de sable (2 m, 70 cm de haut) à moitié éboulé à droite : sacs tombés, un sac crevé qui perd son
     * sable ; derrière (côté mur) une caisse dont on ne voit que le haut. Douilles sur le dessus et au pied.
     */
    static void sacsSable(MeshBuilder b) {
        // caisse cachée derrière (on n'en voit que le haut, au-dessus de la partie éboulée)
        float cx0 = 16.8f, cx1 = 27.6f, cz0 = 0.5f, cz1 = 5.9f, ch = 7.4f;
        b.bevelBox(cx0, 0, cz0, cx1, ch - 0.9f, cz1, 0.12f, BOIS_OLIVE);
        b.bevelBox(cx0 - 0.08f, ch - 0.92f, cz0 - 0.08f + 0.1f, cx1 + 0.08f, ch, cz1 + 0.08f, 0.12f, BOIS_OLIVE);
        for (float x : new float[]{cx0 + 0.8f, cx1 - 1.7f}) b.bevelBox(x, 0.2f, cz1 - 0.02f, x + 0.9f, ch - 1.0f, cz1 + 0.3f, 0.08f, BOIS_OLIVE);
        Kit.poigneeCorde(b, cx1 + 0.05f, 4.6f, (cz0 + cz1) / 2, 2.6f, 1);
        pochoir(b, S_CODE, 'z', 22.2f, 5.6f, cz1, 5.6f, 0.9f);
        pochoir(b, S_HAUT, 'y', 22.2f, ch, 3.2f, 4.0f, 1.0f);
        coulure(b, 'z', cx1 - 1.25f, ch - 1.0f, cz1 + 0.3f, 0.8f, 1);
        // le muret : rangées décalées (sacs entiers et demi-sacs), partie droite éboulée
        float rx = 3.9f, ry = 1.3f, rz = 2.2f, z = 8.7f, pas = 2.2f;
        int g = 1;
        for (float x : new float[]{4.1f, 12.0f, 19.9f, 27.8f}) sacSable(b, SAC_SABLE, x, 0, z + (g % 2) * 0.15f, rx, ry, rz, (g * 37 % 7) - 3, 0, g++, g == 4);
        for (float x : new float[]{8.05f, 15.95f, 23.85f}) sacSable(b, SAC_SABLE, x, pas, z - 0.1f, rx, ry, rz, (g * 37 % 7) - 3, 0, g++, false);
        sacSable(b, SAC_SABLE, 2.1f, pas, z, 1.9f, ry, rz, 2, 0, g++, false);
        sacSable(b, SAC_SABLE, 29.8f, pas, z + 0.2f, 1.9f, ry, rz, -4, 0, g++, false);
        for (float x : new float[]{4.1f, 12.0f}) sacSable(b, SAC_SABLE, x, 2 * pas, z + 0.1f, rx, ry, rz, (g * 37 % 7) - 3, 0, g++, x > 10);
        sacSable(b, SAC_SABLE, 2.1f, 3 * pas, z, 1.9f, ry, rz, -3, 0, g++, false);
        sacSable(b, SAC_SABLE, 8.05f, 3 * pas, z - 0.15f, rx, ry, rz, 4, 0, g++, false);
        b.push().translate(15.6f, 3 * pas + 0.2f, z).rotateZ(-9).translate(-15.6f, -3 * pas - 0.2f, -z);
        sacSable(b, SAC_SABLE, 15.6f, 3 * pas - 0.35f, z + 0.1f, rx, ry, rz, -6, 0, g++, true);
        b.pop();
        sacSable(b, SAC_SABLE, 4.6f, 4 * pas, z + 0.1f, rx, ry, rz, -5, 0, g++, false);
        sacSable(b, SAC_SABLE, 10.9f, 4 * pas, z - 0.1f, rx * 0.9f, ry, rz, 7, 0, g++, false);
        // sac crevé, affaissé sur la rangée basse ; sable répandu ; sac tombé devant
        sacSable(b, SAC_SABLE, 24.6f, 2 * pas - 0.25f, z + 0.5f, rx * 0.95f, ry * 0.62f, rz * 1.1f, 12, 0, g++, false);
        decal(b, FISSURE, 'z', 26.4f, 2 * pas + 0.4f, z + 0.5f + rz * 1.1f - 0.1f, 1.6f, 0.9f, 10);
        b.push().translate(27.2f, 0, 12.6f).scale(1, 0.48f, 0.9f);
        b.sphereSection(2.6f, 14, 4, 0, 90, TERRE);
        b.pop();
        b.push().translate(28.8f, 1.6f, z + rz + 0.6f).rotateZ(-50).scale(1, 0.4f, 1);
        b.sphereSection(1.0f, 10, 3, 0, 90, TERRE);
        b.pop();
        sacSable(b, SAC_SABLE, 21.0f, 0, 12.9f, rx, ry, rz, 24, 0, g++, true);
        // douilles éparses (laiton) sur le dessus et au pied
        java.util.Random r = new java.util.Random(4);
        float[][] douilles = {{6.0f, 4 * pas + 2.3f, z + 0.4f}, {7.2f, 4 * pas + 2.25f, z - 0.6f}, {11.8f, 4 * pas + 2.2f, z + 0.9f}, {6.5f, 0, 13.0f}, {9.0f, 0, 12.2f},
                {14.2f, 0, 13.6f}, {16.4f, 0, 12.0f}};
        for (float[] d : douilles) {
            b.push().translate(d[0], d[1] + 0.17f, d[2]).rotateY(r.nextFloat() * 360).rotateZ(90);
            b.cylinder(0.17f, -0.55f, 0.55f, 7, AtlasLoot.BRASS, null, AtlasLoot.BRASS);
            b.pop();
        }
    }

    // =================================================================== 6. jerricans
    /** Jerrican métallique (47 x 35 x 16,5 cm) debout, face large vers +Z, base en (0, 0, 0). */
    static void jerrican(MeshBuilder b, boolean bouchonOuvert, boolean pochoir) {
        float W = 5.5f, T = 2.64f, H = 7.4f, w = W / 2, t = T / 2;
        b.bevelBox(-w, 0, -t, w, H, t, 0.42f, ACIER);
        // cordon de soudure tout autour (milieu de l'épaisseur)
        b.bevelBox(-w - 0.07f, 0.35f, -0.13f, w + 0.07f, H - 0.35f, 0.13f, 0.05f, ACIER);
        // X embouti sur les deux faces, cadre
        float bx = w - 0.75f, by0 = 0.75f, by1 = H - 1.05f;
        float ang = (float) Math.toDegrees(Math.atan2(by1 - by0, 2 * bx)), lx = (float) Math.hypot(2 * bx, by1 - by0);
        for (int s = -1; s <= 1; s += 2) {
            for (int c = -1; c <= 1; c += 2) {
                float ep = c > 0 ? 0.18f : 0.31f, rentre = c > 0 ? 0.02f : 0.08f;
                b.push().translate(0, (by0 + by1) / 2, s * (t - rentre)).rotateZ(c * ang);
                b.bevelBox(-lx / 2, -0.3f, s > 0 ? 0 : -ep - rentre + 0.02f, lx / 2, 0.3f, s > 0 ? ep + rentre - 0.02f : 0, 0.05f, ACIER);
                b.pop();
            }
        }
        // poignées (trois, dans l'épaisseur), bec verseur au coin
        for (float x : new float[]{-1.7f, 0, 1.7f}) {
            b.bevelBox(x - 0.25f, H - 0.1f, -0.85f, x + 0.25f, H + 0.45f, -0.45f, 0.06f, ACIER);
            b.bevelBox(x - 0.25f, H - 0.1f, 0.45f, x + 0.25f, H + 0.45f, 0.85f, 0.06f, ACIER);
            b.tube(new float[][]{p(x, H + 0.35f, -0.75f), p(x, H + 0.75f, -0.45f), p(x, H + 0.75f, 0.45f), p(x, H + 0.35f, 0.75f)}, fill(4, 0.2f), 6, ACIER, false, false, 0, false);
        }
        b.push().translate(-w + 0.75f, H - 0.35f, 0).rotateZ(35);
        b.cylinder(0.55f, 0, 1.05f, 10, ACIER, null, null);
        if (bouchonOuvert) {
            b.disc(0.5f, 1.04f, 10, INTERIEUR_SALE, true);
            b.translate(-0.75f, 1.25f, 0).rotateZ(-110);
        } else {
            b.translate(0, 1.0f, 0);
        }
        b.cylinder(0.66f, 0, 0.42f, 10, AtlasLoot.STEEL, AtlasLoot.STEEL, AtlasLoot.STEEL);
        b.pop();
        b.bevelBox(-w + 1.2f, H + 0.05f, -0.2f, -w + 2.0f, H + 0.3f, 0.2f, 0.05f, AtlasLoot.STEEL);
        if (pochoir) {
            pochoir(b, S_F34, 'z', 0, H - 1.25f, t, 2.2f, 0.55f);
            pochoir(b, S_LOT, 'z', 0, 0.3f, t, 3.0f, 0.36f);
        }
        coulure(b, 'z', -1.7f, H - 0.4f, t, 0.8f, 0);
        coulure(b, 'Z', 1.2f, H - 0.4f, -t, 0.9f, 1);
        coulure(b, 'X', w, H - 0.6f, 0.6f, 0.7f, 2);
        boue(b, 'z', 0, 0, t, W - 0.5f, 1.3f, 0);
    }

    /**
     * Deux jerricans olive : l'un couché sur une caisse en bois, bouchon ouvert ; l'autre debout au pied du mur ; bec
     * verseur souple tombé au sol, flaque de carburant séchée.
     */
    static void jerricans(MeshBuilder b) {
        float x0 = 1.3f, x1 = 9.5f, z0 = 0.6f, z1 = 7.2f, h = 4.6f;
        b.bevelBox(x0, 0, z0, x1, h - 0.8f, z1, 0.12f, BOIS_KAKI);
        b.bevelBox(x0 - 0.06f, h - 0.82f, z0 - 0.06f + 0.06f, x1 + 0.06f, h, z1 + 0.06f, 0.12f, BOIS_KAKI);
        for (float x : new float[]{x0 + 0.6f, x1 - 1.5f}) b.bevelBox(x, 0.2f, z1 - 0.02f, x + 0.9f, h - 0.9f, z1 + 0.3f, 0.08f, BOIS_KAKI);
        Kit.poigneeCorde(b, x0 - 0.05f, 2.4f, (z0 + z1) / 2, 2.6f, -1);
        pochoir(b, S_CART, 'z', 5.1f, 2.3f, z1, 4.2f, 0.95f);
        boue(b, 'z', 5.1f, 0, z1, 8.0f, 1.4f, 1);
        // jerrican couché sur la caisse, face X vers le haut
        b.push().translate(5.0f, h + 1.32f + 0.02f, 4.1f).rotateY(184).rotateX(-90).translate(0, -3.7f, 0);
        jerrican(b, true, false);
        b.pop();
        // jerrican debout, légèrement tourné
        b.push().translate(12.65f, 0, 2.5f).rotateY(-9);
        jerrican(b, false, true);
        b.pop();
        // bec verseur souple au sol, flaque
        b.tube(new float[][]{p(9.6f, 0.38f, 11.4f), p(11.2f, 0.38f, 12.4f), p(13.0f, 0.38f, 11.8f), p(13.8f, 0.38f, 10.6f)}, fill(4, 0.36f), 8, AtlasLoot.RUBBER, true, false, 0, false);
        b.push().translate(13.8f, 0.52f, 10.6f).rotateZ(90).rotateX(-40);
        b.cylinder(0.5f, -0.2f, 0.6f, 10, ACIER, ACIER, ACIER);
        b.pop();
        b.color(0.45f);
        decal(b, AUREOLE, 'y', 12.2f, 0, 7.8f, 6.0f, 4.6f, 20);
        b.color(1);
    }

    // =================================================================== 7. rations
    /** Carton de rations (coin bas-arrière-gauche) : feuillards, ruban, pochoir, étiquette. */
    static void carton(MeshBuilder b, float x0, float y0, float z0, float L, float H, float W, fr.heraliz.loot.client.rendu.mesh.Mat m, boolean feuillardCoupe) {
        float x1 = x0 + L, z1 = z0 + W, y1 = y0 + H;
        b.bevelBox(x0, y0, z0, x1, y1, z1, 0.1f, m);
        b.bevelBox(x0 + 0.05f, y1 - 0.02f, (z0 + z1) / 2 - 0.45f, x1 - 0.05f, y1 + 0.03f, (z0 + z1) / 2 + 0.45f, 0.01f, SCOTCH_BRUN);
        pochoir(b, S_RATIONS, 'z', x0 + L / 2, y0 + H * 0.68f, z1, L * 0.88f, L * 0.88f / 6.5f);
        pochoir(b, ETIQ_RATION, 'X', x1, y0 + H * 0.45f, z0 + W / 2, 2.4f, 2.4f);
        // deux feuillards plastique (l'un coupé, qui pend)
        b.color(0.75f, 0.7f, 0.55f);
        for (int k = 0; k < 2; k++) {
            float x = x0 + L * (k == 0 ? 0.25f : 0.75f);
            if (k == 1 && feuillardCoupe) {
                b.bevelBox(x - 0.2f, y1 - 0.08f, z0 - 0.02f, x + 0.2f, y1 + 0.09f, z1 - 1.2f, 0.01f, SANGLE);
                b.ribbon(new float[][]{p(x, y1 - 0.2f, z1 + 0.04f), p(x + 0.1f, y0 + H * 0.5f, z1 + 0.08f), p(x + 0.4f, y0 + 0.4f, z1 + 0.35f)}, fill(3, 0.4f), p(1, 0, 0), SANGLE);
            } else {
                b.bevelBox(x - 0.2f, y1 - 0.08f, z0 - 0.04f, x + 0.2f, y1 + 0.09f, z1 + 0.04f, 0.01f, SANGLE);
                b.bevelBox(x - 0.2f, y0 + 0.02f, z1 - 0.02f, x + 0.2f, y1 + 0.08f, z1 + 0.11f, 0.01f, SANGLE);
            }
        }
        b.color(1);
    }

    /** Sachet de ration (18 x 27 cm) debout, face imprimée vers +Z, base en (0, 0, 0). */
    static void sachet(MeshBuilder b, float l, float hh) {
        b.bevelBox(-l / 2, 0, -0.22f, l / 2, hh, 0.22f, 0.16f, SACHET);
        b.bevelBox(-l / 2 + 0.05f, hh - 0.08f, -0.06f, l / 2 - 0.05f, hh + 0.45f, 0.06f, 0.03f, SACHET);
        b.decalZ(ETIQ_SACHET, 0, hh * 0.5f, 0.27f, l - 0.3f, hh - 0.5f);
    }

    /**
     * Cartons de rations de combat empilés contre le mur : deux en bas (l'un détrempé), un dessus, ouvert, rabats
     * relevés, sachets debout dedans (il en manque) ; un sachet tombé au sol, un autre appuyé contre la pile.
     */
    static void rations(MeshBuilder b) {
        float L = 6.7f, H = 4.0f, W = 4.3f;
        carton(b, 1.0f, 0, 0.6f, L, H, W, CARTON, true);
        b.push().translate(11.2f, 0, 2.8f).rotateY(-4).translate(-11.2f, 0, -2.8f);
        carton(b, 7.9f, 0, 0.7f, L, H, W, CARTON_MOUILLE, false);
        b.pop();
        decal(b, AUREOLE, 'z', 10.8f, 1.2f, 0.7f + W + 0.25f, 2.6f, 2.0f);
        // carton ouvert sur le dessus
        b.push().translate(7.8f, H + 0.02f, 3.0f).rotateY(6);
        float x0 = -L / 2, x1 = L / 2, z0 = -W / 2, z1 = W / 2, e = 0.12f;
        b.box(x0 + e, 0, z0 + e, x1 - e, 0.12f, z1 - e, CARTON);
        b.bevelBox(x0, 0, z1 - e, x1, H, z1, 0.03f, CARTON);
        b.bevelBox(x0, 0, z0, x1, H, z0 + e, 0.03f, CARTON);
        b.bevelBox(x0, 0, z0 + e - 0.01f, x0 + e, H, z1 - e + 0.01f, 0.03f, CARTON);
        b.bevelBox(x1 - e, 0, z0 + e - 0.01f, x1, H, z1 - e + 0.01f, 0.03f, CARTON);
        pochoir(b, S_RATIONS, 'z', 0, H * 0.62f, z1, L * 0.88f, L * 0.88f / 6.5f);
        pochoir(b, ETIQ_RATION, 'x', x0, H * 0.45f, 0, 2.4f, 2.4f);
        // rabats : avant rabattu vers le bas (déchiré), arrière relevé, côtés ouverts
        b.push().translate(0, H, z1).rotateX(118);
        b.bevelBox(x0 + 0.02f, 0, -e, x1 - 1.4f, W / 2 - 0.05f, 0, 0.03f, CARTON);
        b.pop();
        b.push().translate(0, H, z0).rotateX(-12);
        b.bevelBox(x0 + 0.02f, 0, 0, x1 - 0.02f, W / 2 - 0.05f, e, 0.03f, CARTON);
        b.pop();
        b.push().translate(x0, H, 0).rotateZ(105);
        b.bevelBox(0, -e, z0 + 0.05f, W / 2 - 0.1f, 0, z1 - 0.05f, 0.03f, CARTON);
        b.pop();
        b.push().translate(x1, H, 0).rotateZ(-128);
        b.bevelBox(-(W / 2 - 0.1f), -e, z0 + 0.05f, 0, 0, z1 - 0.05f, 0.03f, CARTON);
        b.pop();
        b.bevelBox(x0 + 0.5f, H - 0.04f, z0 + 0.02f, x0 + 1.4f, H + 0.07f, z1 - 0.02f, 0.01f, SCOTCH_BRUN);
        // sachets debout (deux colonnes, il en manque)
        float[][] places = {{-1.6f, -1.4f, -3}, {1.6f, -1.4f, 4}, {-1.6f, -0.6f, 2}, {1.6f, -0.6f, -5}, {1.6f, 0.2f, 9}, {-1.5f, 1.1f, -14}};
        for (float[] q : places) {
            b.push().translate(q[0], 0.12f, q[1]).rotateX(q[2]);
            sachet(b, 2.9f, 3.6f);
            b.pop();
        }
        b.pop();
        // sachet tombé au sol, sachet appuyé contre la pile
        b.push().translate(5.0f, 0.22f, 8.6f).rotateY(-25).rotateX(-90);
        sachet(b, 2.9f, 3.6f);
        b.pop();
        b.push().translate(14.75f, 0, 6.6f).rotateY(-80).rotateX(-14);
        sachet(b, 2.9f, 3.6f);
        b.pop();
        boue(b, 'z', 4.35f, 0, 0.6f + W, 6.0f, 1.2f, 0);
    }

    // =================================================================== 8. caisse médicale
    /**
     * Caisse sanitaire en acier olive (60 x 40 x 35 cm), croix rouge sur carré blanc, ouverte, couvercle relevé contre le
     * mur : compartiments, rouleaux de bande, compresses, poche de perfusion et tubulure, flacon ; une bande déroulée au
     * sol, un paquet tombé. Traces de sang séché sur le rebord.
     */
    static void caisseMedicale(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 1.7f, z1 = 8.1f, h = 4.6f, e = 0.2f, lh = 1.0f, yb = 0.3f;
        b.bevelBox(x0 + 0.2f, 0, z0 + 0.2f, x1 - 0.2f, yb + 0.02f, z1 - 0.2f, 0.08f, AtlasLoot.OD_DARK);
        b.bevelBox(x0, yb, z1 - e, x1, h, z1, 0.08f, ACIER);
        b.bevelBox(x0, yb, z0, x1, h, z0 + e, 0.08f, ACIER);
        b.bevelBox(x0, yb, z0 + e - 0.02f, x0 + e, h, z1 - e + 0.02f, 0.06f, ACIER);
        b.bevelBox(x1 - e, yb, z0 + e - 0.02f, x1, h, z1 - e + 0.02f, 0.06f, ACIER);
        b.box6(x0 + 0.1f, yb, z0 + 0.1f, x1 - 0.1f, yb + 0.3f, z1 - 0.1f, null, null, null, INTERIEUR_SALE, null, null);
        // rebord, coins, poignées, fermoirs
        b.bevelBox(x0 - 0.08f, h - 0.3f, z1 - 0.1f, x1 + 0.08f, h + 0.04f, z1 + 0.1f, 0.06f, ACIER);
        b.color(0.8f);
        for (float x : new float[]{x0 - 0.1f, x1 - 0.8f}) b.bevelBox(x, yb - 0.02f, z1 - 0.8f, x + 0.9f, yb + 0.95f, z1 + 0.1f, 0.16f, ACIER);
        b.color(1);
        Kit.poigneeU(b, x1 + 0.1f, 3.2f, (z0 + z1) / 2, 2.4f, 1, AtlasLoot.STEEL);
        Kit.poigneeU(b, x0 - 0.1f, 3.2f, (z0 + z1) / 2, 2.4f, -1, AtlasLoot.STEEL);
        for (float x : new float[]{4.6f, 11.4f}) {
            b.bevelBox(x - 0.55f, h - 1.15f, z1 + 0.06f, x + 0.55f, h - 0.15f, z1 + 0.26f, 0.06f, AtlasLoot.OD_DARK);
            b.bevelBox(x - 0.3f, h - 0.95f, z1 + 0.24f, x + 0.3f, h - 0.4f, z1 + 0.42f, 0.05f, AtlasLoot.STEEL);
        }
        // croix rouge sur carré blanc (avant, côté), pochoir SANTÉ
        pochoir(b, CROIX_ROUGE, 'z', 8.0f, 2.25f, z1, 3.1f, 3.1f);
        pochoir(b, CROIX_ROUGE, 'X', x1, 2.4f, (z0 + z1) / 2, 2.6f, 2.6f);
        pochoir(b, S_SANTE, 'x', x0, 2.6f, (z0 + z1) / 2, 4.6f, 1.15f);
        coulure(b, 'z', 4.6f, h - 1.15f, z1 + 0.06f, 0.8f, 0);
        coulure(b, 'X', x1, h - 0.3f, z0 + 1.4f, 0.9f, 2);
        decal(b, GRIFFURES, 'z', 10.6f, 1.2f, z1, 2.4f, 1.0f, -4);
        decal(b, SANG_1, 'z', 11.4f, h - 0.9f, z1 + 0.42f, 1.0f, 1.0f, 15);
        boue(b, 'z', 8.0f, yb, z1, 9.0f, 1.3f, 0);
        // couvercle relevé contre le mur (inventaire scotché dedans)
        b.push().translate(0, h, z0).rotateX(-95).translate(0, -h, -z0);
        b.bevelBox(x0 - 0.06f, h + lh - 0.2f, z0 - 0.06f, x1 + 0.06f, h + lh, z1 + 0.06f, 0.1f, ACIER);
        b.bevelBox(x0 - 0.06f, h + 0.04f, z1 - 0.16f, x1 + 0.06f, h + lh - 0.18f, z1 + 0.06f, 0.06f, ACIER);
        b.bevelBox(x0 - 0.06f, h + 0.04f, z0 - 0.06f, x0 + 0.1f, h + lh - 0.18f, z1 - 0.14f, 0.05f, ACIER);
        b.bevelBox(x1 - 0.1f, h + 0.04f, z0 - 0.06f, x1 + 0.06f, h + lh - 0.18f, z1 - 0.14f, 0.05f, ACIER);
        b.push().translate(8.0f, h + lh - 0.25f, (z0 + z1) / 2).rotateX(90).rotateZ(-4);
        b.decalZ(PAPIER, 0, 0, 0, 3.4f, 4.4f);
        b.box(-0.5f, 2.0f, 0.04f, 0.5f, 2.35f, 0.1f, SCOTCH);
        b.pop();
        b.pop();
        // contenu : cloisons, rouleaux de bande, compresses, perfusion, flacon
        float yt = 1.9f;
        b.bevelBox(x0 + e - 0.02f, yt - 0.25f, z0 + e - 0.02f, x1 - e + 0.02f, yt, z1 - e + 0.02f, 0.05f, ACIER);
        b.bevelBox(x0 + e, yt, 4.6f, x1 - e, 4.0f, 4.8f, 0.04f, ACIER);
        b.bevelBox(7.6f, yt, z0 + e, 7.8f, 4.0f, 4.62f, 0.04f, ACIER);
        for (int k = 0; k < 4; k++) {
            b.push().translate(4.3f + k * 0.85f, yt + 0.55f + (k % 2) * 0.05f, 3.3f).rotateX(90).rotateZ(k * 7);
            b.cylinder(0.52f, -1.0f, 1.0f, 10, GAZE, GAZE, GAZE);
            b.pop();
        }
        b.push().translate(5.4f, yt + 1.6f, 3.4f).rotateY(70).rotateX(90);
        b.cylinder(0.52f, -1.0f, 1.0f, 10, GAZE, GAZE, GAZE);
        b.pop();
        for (int k = 0; k < 3; k++) {
            b.push().translate(10.3f, yt + k * 0.62f, 3.2f).rotateY(k * 9 - 6);
            b.bevelBox(-2.0f, 0, -1.1f, 2.0f, 0.58f, 1.1f, 0.12f, AtlasLoot.WHITE.asFit());
            b.pop();
        }
        // poche de perfusion posée en travers, tubulure lovée
        b.push().translate(9.6f, yt + 0.1f, 6.35f).rotateY(-10).rotateX(-4);
        b.bevelBox(-2.0f, 0, -1.3f, 2.0f, 0.38f, 1.3f, 0.17f, AtlasCivil.MILK);
        b.push().rotateX(-90);
        b.decalZ(PERFUSION, 0, 0, 0.44f, 3.6f, 2.4f);
        b.pop();
        b.pop();
        float[][] spire = new float[16][];
        for (int k = 0; k < 16; k++) {
            double a = k * 0.85;
            spire[k] = p(5.2f + 0.9f * (float) Math.cos(a), yt + 0.1f + k * 0.012f, 6.4f + 0.7f * (float) Math.sin(a));
        }
        b.tube(spire, fill(16, 0.08f), 4, AtlasCivil.MILK, false, false, 0, false);
        b.push().translate(6.9f, yt, 6.8f);
        b.lathe(new float[][]{{0.75f, 0}, {0.75f, 1.8f}, {0.45f, 2.3f}, {0.3f, 2.5f}, {0.36f, 2.52f}, {0.36f, 2.9f}, {0.01f, 2.91f}}, 12, 0, 360, AtlasCivil.BOTTLE_BROWN, 1, 1, 0, 0, 0, false);
        b.pop();
        // au sol : bande déroulée, paquet de compresses
        b.ribbon(new float[][]{p(10.4f, h + 0.1f, z1 - 0.3f), p(10.5f, h + 0.25f, z1 + 0.2f), p(10.6f, 2.6f, z1 + 0.62f), p(10.8f, 0.6f, z1 + 0.75f), p(11.2f, 0.04f, 9.9f),
                        p(11.6f, 0.04f, 11.4f), p(12.6f, 0.04f, 12.4f)}, fill(7, 0.85f), p(1, 0, 0), GAZE);
        b.push().translate(12.9f, 0.34f, 12.6f).rotateZ(90).rotateY(-60);
        b.cylinder(0.34f, -0.75f, 0.75f, 10, GAZE, GAZE, GAZE);
        b.pop();
        b.push().translate(5.2f, 0, 10.6f).rotateY(28);
        b.bevelBox(-1.4f, 0, -0.9f, 1.4f, 0.45f, 0.9f, 0.1f, AtlasLoot.WHITE.asFit());
        b.pop();
    }
}
