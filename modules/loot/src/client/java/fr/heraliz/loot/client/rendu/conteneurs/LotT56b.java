package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T56, T5 (suite) : munitions lourdes, lance-roquettes, coffre de véhicule, palette de largage. */
final class LotT56b {
    private LotT56b() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t5_caisse_munitions_lourdes", "t5", "bois", "Caisse de munitions lourdes", "Heavy ammunition crate", LotT56b::munitionsLourdes);
        Conteneurs.ajouter("t5_caisse_lance_roquettes", "t5", "metal", "Caisse de lance-roquettes", "Rocket launcher crate", LotT56b::lanceRoquettes);
        Conteneurs.ajouter("t5_coffre_vehicule", "t5", "metal", "Coffre de véhicule blindé", "Armored vehicle storage box", LotT56b::coffreVehicule);
        Conteneurs.ajouter("t5_palette_largage", "t5", "bois", "Palette de largage", "Airdrop pallet", LotT56b::paletteLargage);
        LotT56c.enregistrer();
    }

    // =================================================================== munitions lourdes
    /**
     * Caisse en bois (75 x 45 x 35 cm) ouverte, couvercle appuyé contre le mur derrière : trois boîtes de 12,7 mm
     * couchées, une place vide ; planche avant arrachée ; une boîte ouverte au sol d'où sort une bande de cartouches.
     */
    static void munitionsLourdes(MeshBuilder b) {
        float x0 = 1.0f, x1 = 13.0f, z0 = 1.8f, z1 = 9.0f, ys = 0.45f, h = 5.6f, e = 0.42f;
        for (float x : new float[]{2.2f, 11.8f}) b.bevelBox(x - 0.6f, 0, z0 + 0.2f, x + 0.6f, ys + 0.03f, z1 - 0.2f, 0.08f, BOIS_GRIS);
        b.bevelBox(x0 + 0.06f, ys + 0.04f, z0 + 0.06f, x1 - 0.06f, ys + 0.5f, z1 - 0.06f, 0.1f, AtlasLoot.WOOD);
        b.bevelBox(x0 + e - 0.02f, ys, z0, x1 - e + 0.02f, h, z0 + e, 0.1f, AtlasLoot.WOOD);
        b.bevelBox(x0, ys, z0, x0 + e, h, z1, 0.1f, AtlasLoot.WOOD);
        b.bevelBox(x1 - e, ys, z0, x1, h, z1, 0.1f, AtlasLoot.WOOD);
        float pl = (h - ys) / 3;
        b.bevelBox(x0 + e - 0.02f, ys, z1 - e, x1 - e + 0.02f, ys + pl - 0.04f, z1, 0.1f, AtlasLoot.WOOD);
        b.bevelBox(x0 + e - 0.02f, ys + pl, z1 - e, x1 - e + 0.02f, ys + 2 * pl - 0.04f, z1, 0.1f, AtlasLoot.WOOD);
        b.bevelBox(x0 + e - 0.02f, ys + 2 * pl, z1 - e, 8.4f, h, z1, 0.1f, AtlasLoot.WOOD);
        b.push().translate(x1 - e, ys + 2 * pl, z1 - e / 2).rotateZ(-13);
        b.bevelBox(-2.4f, 0.02f, -e / 2, 0, pl - 0.12f, e / 2 - 0.07f, 0.08f, AtlasLoot.WOOD);
        b.pop();
        // tasseaux d'angle, poignées de corde, clous rouillés
        b.color(0.8f, 0.78f, 0.74f);
        for (float x : new float[]{x0 - 0.05f, x1 - 0.95f}) b.bevelBox(x, ys + 0.05f, z1 - 0.02f, x + 1.0f, h - 0.05f, z1 + 0.3f, 0.08f, AtlasLoot.WOOD);
        b.color(1);
        poigneeCorde(b, x1 + 0.02f, 3.4f, (z0 + z1) / 2, 2.6f, 1, AtlasLoot.WOOD);
        poigneeCorde(b, x0 - 0.02f, 3.4f, (z0 + z1) / 2, 2.6f, -1, AtlasLoot.WOOD);
        for (float y : new float[]{1.2f, 2.9f, 4.6f}) {
            boulon(b, x0 + 0.45f, y, z1 + 0.3f, 0.1f);
            boulon(b, x1 - 0.45f, y, z1 + 0.3f, 0.1f);
        }
        Kit.pochoir(b, S_UN_NOIR, 'z', 3.4f, 3.25f, z1, 2.6f, 1.3f);
        Kit.pochoir(b, S_127, 'z', 6.4f, 1.55f, z1, 5.2f, 1.3f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 10.4f, 1.6f, z1, 1.8f, 0.9f);
        Kit.pochoir(b, S_127, 'X', x1, 2.2f, (z0 + z1) / 2, 4.4f, 1.1f);
        // couvercle appuyé contre le mur (trois planches, une étiquette)
        b.push().translate(0, 0.07f, 1.45f).rotateX(-9);
        for (int k = 0; k < 3; k++) b.bevelBox(x0 + 0.1f, k * 2.42f, -0.4f, x1 - 0.1f, k * 2.42f + 2.38f, 0, 0.08f, AtlasLoot.WOOD);
        b.push().translate(4.0f, 5.8f, 0);
        b.decalZ(AtlasLoot.LABEL, 0, 0, 0.04f, 2.0f, 1.0f);
        b.pop();
        b.decalZ(S_127.asFit(), 9.0f, 3.6f, 0.04f, 4.0f, 1.0f);
        b.pop();
        // trois boîtes de 12,7 couchées, une place vide
        for (float cx : new float[]{2.9f, 5.6f, 8.3f}) {
            b.push().translate(cx, ys + 0.52f, (z0 + z1) / 2).rotateY(90 + (cx == 5.6f ? 3 : 0)).translate(-2.45f, 0, -1.25f);
            ConteneursT4.boiteMunitions(b, 0, 0, 0, false);
            b.pop();
        }
        // boîte ouverte au sol, bande de cartouches qui en sort jusqu'au sol
        float bx0 = 9.6f, bx1 = 14.5f, bz0 = 10.4f, bz1 = 12.9f, bh = 2.4f;
        b.bevelBox(bx0, 0, bz0, bx0 + 0.16f, bh, bz1, 0.04f, AtlasLoot.OD_STEEL);
        b.bevelBox(bx1 - 0.16f, 0, bz0, bx1, bh, bz1, 0.04f, AtlasLoot.OD_STEEL);
        b.bevelBox(bx0 + 0.14f, 0, bz0, bx1 - 0.14f, bh, bz0 + 0.16f, 0.04f, AtlasLoot.OD_STEEL);
        b.bevelBox(bx0 + 0.14f, 0, bz1 - 0.16f, bx1 - 0.14f, bh, bz1, 0.04f, AtlasLoot.OD_STEEL);
        b.bevelBox(bx0 + 0.14f, 0, bz0 + 0.14f, bx1 - 0.14f, 0.2f, bz1 - 0.14f, 0.04f, AtlasLoot.OD_STEEL);
        Kit.pochoir(b, S_127, 'z', (bx0 + bx1) / 2, 0.9f, bz1, 3.4f, 0.85f);
        b.push().translate(0, bh, bz0).rotateX(-112).translate(0, -bh, -bz0);
        b.bevelBox(bx0 - 0.06f, bh + 0.02f, bz0 - 0.06f, bx1 + 0.06f, bh + 0.5f, bz1 + 0.06f, 0.1f, AtlasLoot.OD_STEEL);
        b.pop();
        // cartouches rangées dans la boîte (têtes visibles), puis la bande
        for (int k = 0; k < 4; k++) {
            b.push().translate(bx0 + 1.4f, 1.9f, bz0 + 0.5f + k * 0.48f);
            cartoucheSimple(b, 2.2f, 0.17f, AtlasLoot.BRASS, AtlasLoot.DARK);
            b.pop();
        }
        float[][] ch = auSol(lisse(new float[][]{p(11.9f, 1.95f, 12.2f), p(12.0f, 2.62f, 12.95f), p(12.1f, 1.4f, 13.2f), p(12.3f, 0.24f, 13.9f),
                p(11.4f, 0.22f, 15.0f), p(9.2f, 0.22f, 15.2f), p(7.6f, 0.22f, 14.4f)}, 6), 0.2f);
        bandeCartouches(b, ch, 0.56f);
        for (float[] q : new float[][]{{5.4f, 13.8f, 30}, {3.6f, 14.6f, -50}, {6.8f, 15.4f, 110}}) {
            b.push().translate(q[0], 0.17f, q[1]).rotateY(q[2]);
            cartoucheSimple(b, 2.2f, 0.17f, AtlasLoot.BRASS, AtlasLoot.DARK);
            b.pop();
        }
        // usure : mousse verte au pied, boue, coulures sous les clous, feuilles
        decal(b, MOUSSE_VERTE, 'z', 2.6f, ys + 0.7f, z1 + 0.3f, 2.6f, 1.3f);
        boue(b, 'X', x1, ys, 5.4f, 6.4f, 1.05f, 1);
        coulure(b, 'z', x1 - 0.45f, 2.9f, z1 + 0.3f, 0.6f, 0);
        coulure(b, 'z', x0 + 0.45f, 4.6f, z1 + 0.3f, 0.6f, 2);
        jonchee(b, 0.5f, 9.6f, 9.0f, 15.5f, 5, FEUILLES, 1.0f, 581);
    }

    /** Bande de cartouches (maillons sombres) le long d'un chemin, cartouches couchées en travers. */
    static void bandeCartouches(MeshBuilder b, float[][] ch, float pas) {
        float acc = 0;
        int n = 0;
        for (int i = 1; i < ch.length; i++) {
            float[] a = ch[i - 1], c = ch[i];
            float dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            while (acc <= l) {
                float t = acc / l;
                float x = a[0] + dx * t, y = a[1] + dy * t, z = a[2] + dz * t;
                float hx = dz, hz = -dx, hl = (float) Math.sqrt(hx * hx + hz * hz);
                float ang = hl < 0.05f * l ? 0 : (float) Math.toDegrees(Math.atan2(-hz, hx));
                b.push().translate(x, y, z).rotateY(ang + (n % 3 - 1) * 4);
                b.translate(-1.1f, 0, 0);
                cartoucheSimple(b, 2.2f, 0.17f, AtlasLoot.BRASS, AtlasLoot.DARK);
                b.box(0.55f, -0.18f, -0.22f, 0.95f, 0.19f, 0.22f, AtlasLoot.DARK);
                b.pop();
                acc += pas;
                n++;
            }
            acc -= l;
        }
    }

    // =================================================================== lance-roquettes
    /**
     * Caisse de lance-roquettes (1,6 x 0,45 x 0,3 m) sur deux blocs, couvercle ouvert contre le mur : mousse
     * découpée, un tube dans son logement, l'autre logement vide ; bouchon tombé au sol, notice, fermoirs ouverts.
     */
    static void lanceRoquettes(MeshBuilder b) {
        float x0 = 3.0f, x1 = 29.0f, z0 = 2.5f, z1 = 9.7f, yb = 0.5f, yc = 4.9f, e = 0.35f;
        for (float x : new float[]{5.0f, 16.0f, 27.0f}) b.bevelBox(x - 0.8f, 0, z0 + 0.2f, x + 0.8f, yb + 0.03f, z1 - 0.2f, 0.1f, AtlasLoot.DARK);
        b.bevelBox(x0 + 0.06f, yb + 0.04f, z0 + 0.06f, x1 - 0.06f, yb + 0.5f, z1 - 0.06f, 0.15f, BLANC_ONU);
        b.bevelBox(x0, yb, z0, x0 + e, yc, z1, 0.12f, BLANC_ONU);
        b.bevelBox(x1 - e, yb, z0, x1, yc, z1, 0.12f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.02f, yb, z0, x1 - e + 0.02f, yc, z0 + e, 0.12f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.02f, yb, z1 - e, x1 - e + 0.02f, yc, z1, 0.12f, BLANC_ONU);
        // bandes noires de renfort aux extrémités
        for (float x : new float[]{x0 + 1.2f, x1 - 2.4f}) b.bevelBox(x, yb - 0.04f, z1 - 0.05f, x + 1.2f, yc + 0.04f, z1 + 0.1f, 0.06f, ACIER_NOIR);
        // mousse : bandes autour de deux logements
        float yf = yb + 0.5f, yt = yc - 0.35f, ax0 = 6.8f, ax1 = 24.8f;
        float[] za = {z0 + e, 3.6f, 5.4f, 6.5f, 8.3f, z1 - e};
        b.box(x0 + e, yf, za[0], x1 - e, yt, za[1], MOUSSE_GRISE);
        b.box(x0 + e, yf, za[2], x1 - e, yt, za[3], MOUSSE_GRISE);
        b.box(x0 + e, yf, za[4], x1 - e, yt, za[5], MOUSSE_GRISE);
        for (int k = 0; k < 2; k++) {
            float zl0 = za[1 + 2 * k], zl1 = za[2 + 2 * k];
            b.box(x0 + e, yf, zl0, ax0, yt, zl1, MOUSSE_GRISE);
            b.box(ax1, yf, zl0, x1 - e, yt, zl1, MOUSSE_GRISE);
            b.box(ax0, yf, zl0, ax1, yt - 1.15f, zl1, AtlasLoot.FOAM);
        }
        // le tube (1 m), couché dans le logement arrière
        float ty = yt - 1.15f + 0.7f, tz = 4.5f, tx0 = 7.4f, tx1 = 24.2f;
        b.push().translate(tx0, ty, tz).rotateZ(-90);
        b.cylinder(0.68f, 0, tx1 - tx0, 16, AtlasLoot.MISSILE, null, null);
        b.cylinder(0.76f, -0.05f, 0.7f, 16, AtlasLoot.RUBBER, AtlasLoot.RUBBER, null);
        b.cylinder(0.76f, tx1 - tx0 - 0.8f, tx1 - tx0 + 0.05f, 16, AtlasLoot.RUBBER, null, AtlasLoot.RUBBER);
        b.cylinder(0.71f, 12.4f, 12.9f, 16, AtlasLoot.YELLOW.withWorld(4), null, null);
        b.pop();
        b.bevelBox(12.0f, ty + 0.5f, tz - 0.45f, 14.6f, ty + 1.15f, tz + 0.45f, 0.12f, AtlasLoot.OD_DARK);
        b.bevelBox(12.6f, ty - 0.2f, tz + 0.45f, 13.6f, ty + 0.9f, tz + 0.75f, 0.08f, AtlasLoot.OD_DARK);
        b.bevelBox(19.6f, ty + 0.55f, tz - 0.3f, 20.4f, ty + 0.95f, tz + 0.3f, 0.06f, AtlasLoot.OD_DARK);
        b.bevelBox(9.0f, ty + 0.55f, tz - 0.25f, 9.6f, ty + 1.05f, tz + 0.25f, 0.06f, AtlasLoot.OD_DARK);
        b.color(0.42f, 0.42f, 0.32f);
        bande(b, new float[][]{p(9.3f, ty - 0.1f, tz + 0.66f), p(12.0f, ty - 0.25f, tz + 0.95f), p(17.0f, ty - 0.3f, tz + 1.0f), p(21.5f, ty - 0.1f, tz + 0.66f)},
                new float[][]{AVANT}, 0.55f, 0.06f, SANGLE);
        b.color(1);
        // couvercle ouvert (100°) contre le mur, mousse alvéolée dedans
        b.push().translate(0, yc, z0).rotateX(-100).translate(0, -yc, -z0);
        b.bevelBox(x0 - 0.06f, yc + 0.02f, z0 - 0.06f, x1 + 0.06f, yc + 1.0f, z1 + 0.06f, 0.25f, BLANC_ONU);
        b.box(x0 + 0.4f, yc - 0.6f, z0 + 0.4f, x1 - 0.4f, yc + 0.04f, z1 - 0.4f, AtlasLoot.FOAM);
        b.pop();
        // fermoirs ouverts, poignées
        for (float x : new float[]{6.0f, 12.0f, 20.0f, 26.0f}) fermoirOuvert(b, x, yc, z1, x == 12.0f ? 38 : 8, METAL_NU);
        Kit.poigneeU(b, x1 + 0.08f, 3.6f, (z0 + z1) / 2, 3.0f, 1, METAL_NU);
        Kit.poigneeU(b, x0 - 0.08f, 3.6f, (z0 + z1) / 2, 3.0f, -1, METAL_NU);
        // marquages, usure
        Kit.pochoir(b, S_UN_NOIR, 'z', 9.0f, 2.8f, z1, 3.2f, 1.6f);
        Kit.pochoir(b, S_ROQUETTE, 'z', 16.4f, 2.6f, z1, 5.6f, 1.4f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 23.2f, 2.3f, z1, 2.0f, 1.0f);
        Kit.pochoir(b, AtlasLoot.S_DANGER, 'X', x1, 2.4f, (z0 + z1) / 2, 4.4f, 1.1f);
        coulure(b, 'z', 6.0f, yc - 1.4f, z1 + 0.02f, 0.9f, 0);
        coulure(b, 'z', 20.0f, yc - 1.4f, z1 + 0.02f, 0.8f, 1);
        boue(b, 'z', 9.0f, yb, z1, 9.0f, 1.15f, 0);
        boue(b, 'z', 24.0f, yb, z1, 7.0f, 1.1f, 1);
        decal(b, GRIFFURES, 'z', 15.0f, 3.6f, z1, 3.0f, 1.2f, -3);
        scotchZ(b, 24.0f, 3.4f, 25.6f, 1.8f, z1, 0.7f, SCOTCH);
        // bouchon tombé, notice, feuilles
        b.push().translate(18.6f, 0.76f, 13.2f).rotateY(-30).rotateZ(90);
        b.cylinder(0.76f, -0.35f, 0.35f, 14, AtlasLoot.RUBBER, AtlasLoot.RUBBER, AtlasLoot.RUBBER);
        b.pop();
        decal(b, PAPIER, 'y', 12.6f, 0, 13.4f, 3.0f, 2.2f, 24);
        jonchee(b, 3.0f, 10.4f, 29.0f, 15.6f, 9, FEUILLES, 1.0f, 591);
    }

    // =================================================================== coffre de véhicule
    /**
     * Coffre de rangement arraché d'un véhicule blindé ONU (90 x 45 x 40 cm), blanc : pattes de fixation tordues et
     * boulons cisaillés, morceau de tôle encore accroché, couvercle bloqué entrouvert, sangle à cliquet coupée,
     * impacts de balles et coulures.
     */
    static void coffreVehicule(MeshBuilder b) {
        float x0 = 1.0f, x1 = 15.0f, z0 = 1.6f, z1 = 8.8f, y0 = 0.6f, yc = 6.0f, e = 0.3f;
        // pattes de fixation tordues sous le coffre
        for (float x : new float[]{3.0f, 13.0f}) {
            b.bevelBox(x - 0.7f, 0, z0 + 0.6f, x + 0.7f, y0 + 0.03f, z1 - 0.6f, 0.06f, METAL_NU);
            b.push().translate(x, 0.62f, z0 + 0.6f).rotateX(-14);
            b.bevelBox(-0.7f, -0.14f, -1.9f, 0.7f, 0.14f, 0, 0.05f, METAL_NU);
            b.pop();
        }
        b.bevelBox(x0 + 0.06f, y0 + 0.04f, z0 + 0.06f, x1 - 0.06f, y0 + 0.4f, z1 - 0.06f, 0.15f, BLANC_ONU);
        b.bevelBox(x0, y0, z0, x0 + e, yc, z1, 0.15f, BLANC_ONU);
        b.bevelBox(x1 - e, y0, z0, x1, yc, z1, 0.15f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.02f, y0, z0, x1 - e + 0.02f, yc, z0 + e, 0.12f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.02f, y0, z1 - e, x1 - e + 0.02f, yc, z1, 0.12f, BLANC_ONU);
        bacInterieur(b, INTERIEUR_SALE, x0 + e + 0.06f, y0 + 0.46f, z0 + e + 0.06f, x1 - e - 0.06f, yc - 0.05f, z1 - e - 0.06f);
        // nervures embouties
        for (float x : new float[]{5.4f, 10.6f}) b.bevelBox(x - 0.4f, y0 + 0.6f, z1 - 0.05f, x + 0.4f, yc - 0.6f, z1 + 0.14f, 0.15f, BLANC_ONU);
        // contenu entrevu : sangle de remorquage enroulée
        b.color(0.75f, 0.55f, 0.16f);
        b.push().translate(4.6f, y0 + 1.0f, 5.0f);
        b.torus(1.5f, 0.55f, 14, 6, 0, 360, SANGLE);
        b.pop();
        b.color(1);
        // couvercle bloqué entrouvert (14°), lèvre avant
        b.push().translate(0, yc, z0).rotateX(-14).translate(0, -yc, -z0);
        b.bevelBox(x0 - 0.12f, yc + 0.02f, z0 - 0.08f, x1 + 0.12f, yc + 0.9f, z1 + 0.12f, 0.25f, BLANC_ONU);
        b.bevelBox(x0 - 0.12f, yc - 0.5f, z1 + 0.02f, x1 + 0.12f, yc + 0.1f, z1 + 0.32f, 0.08f, BLANC_ONU);
        Kit.pochoir(b, S_UN_NOIR, 'y', 8.0f, yc + 0.9f, 5.0f, 6.4f, 3.2f);
        decal(b, IMPACTS, 'y', 4.2f, yc + 0.9f, 3.0f, 3.2f, 1.6f, 15);
        b.pop();
        // serrure forcée qui pend
        b.bevelBox(7.3f, yc - 1.6f, z1 - 0.02f, 8.7f, yc - 0.6f, z1 + 0.22f, 0.07f, AtlasLoot.DARK);
        b.push().translate(8.0f, yc - 0.9f, z1 + 0.25f).rotateZ(28);
        b.bevelBox(-0.25f, -1.7f, 0, 0.25f, 0, 0.2f, 0.05f, METAL_NU);
        b.pop();
        // sangle à cliquet coupée : passe sous le coffre, remonte au flanc droit, pend coupée
        b.color(0.78f, 0.56f, 0.14f);
        bande(b, new float[][]{p(11.6f, 0.04f, 13.6f), p(12.2f, 0.04f, 11.0f), p(12.4f, 0.3f, z1 + 0.35f), p(12.4f, 2.4f, z1 + 0.42f), p(12.4f, yc - 0.3f, z1 + 0.5f)},
                new float[][]{HAUT, HAUT, AVANT, AVANT, AVANT}, 1.1f, 0.08f, SANGLE);
        b.color(1);
        b.bevelBox(11.8f, 2.6f, z1 + 0.42f, 13.0f, 3.8f, z1 + 0.95f, 0.12f, METAL_NU);
        // morceau de tôle arraché au véhicule, encore boulonné à une patte (au sol, à gauche)
        b.push().translate(0.4f, 0.12f, 11.4f).rotateY(18).rotateX(-86);
        b.color(0.84f, 0.84f, 0.8f);
        b.prism(new float[][]{{0, 0}, {3.2f, -0.4f}, {4.6f, 0.6f}, {3.9f, 1.6f}, {4.4f, 2.5f}, {2.0f, 3.1f}, {1.0f, 2.5f}, {0.1f, 1.9f}}, 0.14f, BLANC_ONU, METAL_NU);
        b.color(1);
        b.pop();
        // marquages et usure
        Kit.pochoir(b, S_UN_NOIR, 'z', 8.0f, 3.2f, z1 + 0.14f, 4.4f, 2.2f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 3.4f, 4.2f, z1, 1.8f, 0.9f);
        decal(b, IMPACTS, 'X', x1, 3.4f, 5.4f, 3.6f, 1.8f, -10);
        coulure(b, 'X', x1, 3.2f, 4.6f, 1.0f, 0);
        coulure(b, 'z', 1.7f, yc - 0.3f, z1, 1.0f, 1);
        coulure(b, 'z', 13.8f, yc - 0.3f, z1, 1.0f, 2);
        boue(b, 'z', 8.0f, y0, z1, 13.6f, 2.0f, 0);
        boue(b, 'X', x1, y0, 5.2f, 7.0f, 1.8f, 1);
        decal(b, GRIFFURES, 'x', x0, 3.6f, 5.0f, 3.2f, 1.4f, 6);
        for (float x : new float[]{2.4f, 3.6f}) boulon(b, x, 1.2f, z1, 0.14f);
        jonchee(b, 0.5f, 9.4f, 15.5f, 15.6f, 6, FEUILLES, 1.1f, 601);
    }

    // =================================================================== palette de largage
    /**
     * Palette de largage (1,8 x 0,85 m) sur deux blocs : plateau en contreplaqué sur nid d'abeille écrasé, rails
     * d'aluminium, charge (caisse ONU, caisse olive, sacs) sous un filet, sangles ; parachute affaissé sur le côté droit
     * qui coule jusqu'au sol, suspentes.
     */
    static void paletteLargage(MeshBuilder b) {
        float x0 = 1.4f, x1 = 30.6f, z0 = 0.8f, z1 = 14.2f;
        b.bevelBox(x0, 0, z0, x1, 0.7f, z1, 0.12f, AtlasLoot.WOOD);
        for (float z : new float[]{z0 - 0.08f, z1 - 0.84f}) b.bevelBox(x0 - 0.1f, 0, z, x1 + 0.1f, 0.95f, z + 0.92f, 0.1f, ALU);
        b.color(0.9f, 0.86f, 0.8f);
        b.box(x0 + 0.5f, 0.7f, z0 + 1.0f, x1 - 0.5f, 2.0f, z1 - 1.0f, CARTON_MOUILLE);
        b.color(1);
        b.bevelBox(x0 + 0.3f, 2.0f, z0 + 0.8f, x1 - 0.3f, 2.35f, z1 - 0.8f, 0.08f, AtlasLoot.WOOD);
        for (float x = x0 + 2.5f; x < x1 - 1; x += 6.5f) {
            b.push().translate(x, 0.95f, z1 - 0.44f).rotateX(-90);
            b.torus(0.45f, 0.12f, 10, 4, 0, 180, METAL_NU);
            b.pop();
        }
        float yl = 2.35f;
        // caisse ONU blanche, caisse olive, deux sacs
        b.bevelBox(3.0f, yl, 2.4f, 13.6f, yl + 6.2f, 12.4f, 0.2f, BLANC_ONU);
        Kit.pochoir(b, S_UN_NOIR, 'z', 6.6f, yl + 3.6f, 12.4f, 4.4f, 2.2f);
        Kit.pochoir(b, S_LARGAGE, 'z', 8.3f, yl + 1.4f, 12.4f, 5.6f, 1.4f);
        b.bevelBox(14.2f, yl, 3.2f, 23.4f, yl + 4.8f, 12.0f, 0.15f, AtlasLoot.OD_WOOD);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 18.8f, yl + 2.4f, 12.0f, 2.2f, 1.1f);
        b.push().translate(18.8f, 0, 7.6f).scale(0.6f, 1, 1).translate(-18.8f, 0, -7.6f);
        ConteneursT2.sacJute(b, 18.8f, yl + 6.4f, 7.6f, -4, 4);
        b.pop();
        // sangles de cargaison sous le filet
        b.color(0.72f, 0.64f, 0.3f);
        bande(b, new float[][]{p(7.0f, 0.98f, z1 - 0.44f), p(7.0f, yl + 0.3f, 12.48f), p(7.0f, yl + 6.24f, 12.48f), p(7.0f, yl + 6.26f, 7.4f), p(7.0f, yl + 6.24f, 2.6f)},
                new float[][]{AVANT, AVANT, {0, 0.7f, 0.7f}, HAUT, HAUT}, 1.0f, 0.08f, SANGLE);
        bande(b, new float[][]{p(18.6f, 0.98f, z1 - 0.44f), p(18.6f, yl + 0.3f, 12.08f), p(18.6f, yl + 4.84f, 12.08f), p(18.6f, yl + 7.2f, 10.8f),
                        p(18.6f, yl + 8.1f, 7.6f), p(18.6f, yl + 7.2f, 4.4f), p(18.6f, yl + 4.84f, 3.4f)},
                new float[][]{AVANT, AVANT, AVANT, {0, 0.7f, 0.7f}, HAUT, {0, 0.7f, -0.7f}, {0, 0, -1}}, 1.0f, 0.08f, SANGLE);
        b.color(1);
        // filet drapé sur la charge (découpe) : dessus en paliers, avant, flancs
        float fz0 = 1.8f, fz1 = 12.9f;
        float[] fx = {2.5f, 13.9f, 15.6f, 22.0f, 24.2f};
        float[] fy = {yl + 6.42f, yl + 6.42f, yl + 8.3f, yl + 8.3f, yl + 5.0f};
        float[] ce = p(14.0f, yl + 2.0f, 7.4f);
        for (int i = 0; i < fx.length - 1; i++) {
            b.flatOut(FILET, ce, p(fx[i], fy[i], fz0), p(fx[i + 1], fy[i + 1], fz0), p(fx[i + 1], fy[i + 1], fz1), p(fx[i], fy[i], fz1));
            b.flatOut(FILET, ce, p(fx[i], fy[i], fz1), p(fx[i + 1], fy[i + 1], fz1), p(fx[i + 1], yl + 0.1f, fz1 + 0.6f), p(fx[i], yl + 0.1f, fz1 + 0.6f));
        }
        b.flatOut(FILET, ce, p(fx[0], fy[0], fz0), p(fx[0], fy[0], fz1), p(fx[0] - 0.3f, yl + 0.1f, fz1 + 0.6f), p(fx[0] - 0.5f, yl + 0.1f, fz0));
        b.flatOut(FILET, ce, p(fx[4], fy[4], fz1), p(fx[4], fy[4], fz0), p(fx[4] + 0.6f, yl + 0.1f, fz0), p(fx[4] + 0.4f, yl + 0.1f, fz1 + 0.6f));
        // parachute affaissé à droite : masse de toile qui coule jusqu'au sol
        b.push().translate(27.2f, 0, 7.6f).scale(1.0f, 1.0f, 1.3f);
        b.lathe(new float[][]{{0.01f, 7.0f}, {1.5f, 6.7f}, {2.6f, 5.5f}, {3.1f, 3.2f}, {3.6f, 1.4f}, {4.0f, 0.3f}, {4.2f, 0.02f}}, 20, 0, 360, NYLON_PARA, 0.85f, 1, 0.2f, 0.7f, 7, false);
        b.pop();
        b.push().translate(26.6f, 0, 13.6f).scale(1.6f, 1, 1);
        b.lathe(new float[][]{{0.01f, 1.4f}, {1.1f, 1.2f}, {1.8f, 0.6f}, {2.2f, 0.04f}}, 12, 0, 360, NYLON_PARA, 1, 0.8f, 0.15f, 0.7f, 8, false);
        b.pop();
        // suspentes : du parachute à l'anneau sur la caisse
        float[] anneau = p(10.4f, yl + 6.68f, 7.2f);
        for (int k = 0; k < 5; k++) {
            float[] dep = p(24.6f + k * 0.2f, 5.4f - k * 0.5f, 5.0f + k * 1.0f);
            float[][] ch = lisse(new float[][]{dep, p((dep[0] + anneau[0]) / 2, yl + 9.0f - k * 0.15f, (dep[2] + anneau[2]) / 2), anneau}, 3);
            cable(b, ch, 0.05f, 0.82f, 0.8f, 0.72f);
        }
        b.push().translate(anneau[0], anneau[1], anneau[2]);
        b.torus(0.5f, 0.12f, 10, 4, 0, 360, METAL_NU);
        b.pop();
        // usure : boue, feuilles, terre sur le contreplaqué
        boue(b, 'z', 9.0f, 0, z1, 10.0f, 1.0f, 0);
        decal(b, BOUE_2, 'y', 22.0f, 2.35f, 12.6f, 4.0f, 1.6f, 0);
        decal(b, AUREOLE, 'z', 10.4f, yl + 4.2f, 12.4f, 2.6f, 2.6f, 0);
        jonchee(b, 0.5f, 14.4f, 31.5f, 15.8f, 8, FEUILLES, 1.0f, 611);
        b.push().translate(0, yl, 0);
        jonchee(b, 23.6f, 1.4f, 24.4f, 13.0f, 3, FEUILLES, 0.9f, 612);
        b.pop();
    }
}
