package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasLoot.*;
import static fr.heraliz.loot.client.rendu.conteneurs.Kit.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * T4 — militaire : acier et planches peints olive, toile, pochoirs, cordes, ferrures. Petites bases, piste, cité des
 * nains, abandonnées en hâte : caisses forcées, cadenas coupés, boue, rouille. Échelle réelle (1 px = 6,25 cm) ; posés
 * dos au mur (z = 0 : mur, avant vers +Z) ; les longues caisses occupent deux blocs vers la droite (x jusqu'à 32).
 */
final class ConteneursT4 {
    private ConteneursT4() {
    }

    /** Boîte à munitions (type M2A1, 30 x 15 x 19 cm), couchée le long de X, coin bas-arrière-gauche (x0, y0, z0). */
    static void boiteMunitions(MeshBuilder b, float x0, float y0, float z0, boolean pochoir) {
        boiteMunitions(b, x0, y0, z0, pochoir, false);
    }

    /** ouverte : couvercle arraché (il est posé ailleurs), boîte vide. */
    static void boiteMunitions(MeshBuilder b, float x0, float y0, float z0, boolean pochoir, boolean ouverte) {
        float L = 4.9f, W = 2.5f, H = 3.0f;
        float yc = y0 + H * 0.78f, x1 = x0 + L, z1 = z0 + W, zc = (z0 + z1) / 2;
        b.bevelBox(x0, y0, z0, x1, yc, z1, 0.14f, OD_STEEL);
        b.bevelBox(x0 + 0.35f, y0 + 0.55f, z1 - 0.02f, x1 - 0.35f, y0 + 0.7f, z1 + 0.06f, 0.03f, OD_STEEL);
        if (ouverte) {
            b.box(x0 + 0.25f, yc - 0.06f, z0 + 0.25f, x1 - 0.25f, yc + 0.05f, z1 - 0.25f, AtlasUsure.INTERIEUR_SALE);
        } else {
            b.bevelBox(x0 - 0.08f, yc - 0.02f, z0 - 0.08f, x1 + 0.08f, y0 + H, z1 + 0.08f, 0.12f, OD_STEEL);
            b.bevelBox(x0 + 0.5f, y0 + H - 0.03f, z0 + 0.45f, x1 - 0.5f, y0 + H + 0.08f, z1 - 0.45f, 0.04f, OD_STEEL);
            // poignée rabattue, levier de fermeture
            b.tube(new float[][]{p(x0 + 1.3f, y0 + H + 0.12f, zc), p(x1 - 1.3f, y0 + H + 0.12f, zc)}, new float[]{0.12f, 0.12f}, 6, OD_DARK, true, true, 0, false);
        }
        b.bevelBox(x1 + 0.04f, y0 + H * 0.45f, zc - 0.5f, x1 + 0.2f, y0 + H + 0.02f, zc + 0.5f, 0.05f, OD_STEEL);
        if (pochoir) pochoir(b, S_762, 'z', x0 + L / 2, y0 + 1.1f, z1 + 0.07f, 3.2f, 0.8f);
    }

    /** Six boîtes en pyramide contre le mur (3 + 2 + 1, une ouverte et vide), une septième posée en travers devant. */
    static void caissesMunitions(MeshBuilder b) {
        float z = 0.6f;
        for (int k = 0; k < 3; k++) boiteMunitions(b, 0.55f + k * 5.2f, 0, z + (k == 1 ? 0.2f : 0), true);
        for (int k = 0; k < 2; k++) boiteMunitions(b, 3.15f + k * 5.2f, 3.1f, z + 0.1f, k == 0, k == 1);
        b.push().translate(8, 6.2f, z + 1.25f).rotateY(-7).translate(-8, -6.2f, -(z + 1.25f));
        boiteMunitions(b, 5.75f, 6.2f, z, true);
        b.pop();
        b.push().translate(11.2f, 0, 6.8f).rotateY(-28).translate(-11.2f, 0, -6.8f);
        boiteMunitions(b, 8.8f, 0, 5.6f, true);
        b.pop();
        // couvercle de la boîte vidée, tombé à plat devant
        b.push().translate(4.0f, 0, 8.4f).rotateY(22);
        b.bevelBox(-2.53f, 0, -1.33f, 2.53f, 0.68f, 1.33f, 0.12f, OD_STEEL);
        b.pop();
        coulure(b, 'z', 0.95f, 2.2f, z + 2.5f, 0.6f, 0);
        coulure(b, 'z', 15.35f, 2.0f, z + 2.5f, 0.5f, 1);
    }

    /** Petit cadenas (4 cm) ; coupe : anse sciée, ouverte. */
    static void cadenasPetit(MeshBuilder b, float x, float y, float z) {
        cadenasPetit(b, x, y, z, false);
    }

    static void cadenasPetit(MeshBuilder b, float x, float y, float z, boolean coupe) {
        b.bevelBox(x - 0.4f, y - 0.9f, z, x + 0.4f, y - 0.1f, z + 0.3f, 0.1f, BRASS);
        b.push().translate(x, y - 0.1f, z + 0.15f).rotateX(90);
        if (coupe) b.rotateZ(-35);
        b.torus(0.26f, 0.07f, 12, 4, 0, coupe ? 140 : 180, STEEL);
        b.pop();
    }

    /** Caisse à fusils (planches peintes, 1,2 m) sur deux blocs, dos au mur : couvercle forcé, cadenas sciés. */
    static void caisseFusils(MeshBuilder b) {
        float x0 = 6.4f, x1 = 25.6f, z0 = 0.6f, z1 = 8.6f, h = 5.8f;
        b.bevelBox(x0, 0.3f, z0, x1, h - 1.2f, z1, 0.14f, OD_WOOD);
        b.box(x0 + 0.4f, h - 1.3f, z0 + 0.4f, x1 - 0.4f, h - 1.12f, z1 - 0.4f, AtlasUsure.INTERIEUR_SALE);
        // couvercle soulevé au pied-de-biche côté gauche, il retombe de travers
        b.push().translate(x1, h - 1.25f, z0).rotateZ(4).rotateY(-2).translate(-x1, -(h - 1.25f), -z0);
        b.bevelBox(x0 - 0.06f, h - 1.25f, z0 - 0.06f, x1 + 0.06f, h, z1 + 0.06f, 0.14f, OD_WOOD);
        for (float x : new float[]{x0 + 2.2f, x1 - 3.2f}) b.bevelBox(x, h + 0.02f, z0 + 0.4f, x + 1.0f, h + 0.3f, z1 - 0.4f, 0.08f, OD_WOOD);
        pochoir(b, S_T, 'y', 10.0f, h + 0.01f, (z0 + z1) / 2, 1.6f, 0.8f);
        decal(b, AtlasUsure.GRIFFURES, 'z', x0 + 1.6f, h - 0.6f, z1 + 0.06f, 2.4f, 1.0f);
        b.pop();
        for (float x : new float[]{x0 + 1.5f, x1 - 2.7f}) b.bevelBox(x, 0, z0 + 0.2f, x + 1.2f, 0.32f, z1 - 0.2f, 0.06f, OD_DARK);
        for (float x : new float[]{x0 + 2.2f, x1 - 3.2f}) b.bevelBox(x, 0.5f, z1 + 0.02f, x + 1.0f, h - 1.4f, z1 + 0.35f, 0.08f, OD_WOOD);
        coins(b, x0, 0.24f, z0, x1, h - 1.4f, z1, 0.7f, STEEL);
        poigneeCorde(b, x1 + 0.05f, 3.4f, (z0 + z1) / 2, 2.6f, 1);
        poigneeCorde(b, x0 - 0.05f, 3.4f, (z0 + z1) / 2, 2.6f, -1);
        pochoir(b, S_FUSILS, 'z', 13.6f, 2.4f, z1 + 0.04f, 5.0f, 1.25f);
        pochoir(b, LABEL, 'z', 19.6f, 2.8f, z1 + 0.04f, 2.0f, 1.0f);
        for (float x : new float[]{12.0f, 20.0f}) {
            b.bevelBox(x - 0.45f, h - 2.6f, z1 + 0.06f, x + 0.45f, h - 1.3f, z1 + 0.2f, 0.04f, STEEL);
            cadenasPetit(b, x, h - 2.4f, z1 + 0.22f, true);
        }
        coulure(b, 'z', x0 + 0.4f, h - 1.4f, z1 + 0.06f, 1.0f, 0);
        coulure(b, 'z', x1 - 0.4f, h - 1.4f, z1 + 0.06f, 1.0f, 2);
        boue(b, 'z', 16, 0.3f, z1, 18.0f, 1.4f, 0);
    }

    /** Cantine militaire (80 x 40 x 35 cm) au pied du mur : cadenas scié, couvercle entrouvert, étiquette arrachée. */
    static void cantine(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.6f, z1 = 7.0f, yc = 4.4f, h = 5.6f;
        b.bevelBox(x0, 0.25f, z0, x1, yc, z1, 0.12f, OD_WOOD);
        b.box(x0 + 0.3f, yc - 0.06f, z0 + 0.3f, x1 - 0.3f, yc + 0.08f, z1 - 0.3f, AtlasUsure.INTERIEUR_SALE);
        b.push().translate(0, yc, z0).rotateX(-6).translate(0, -yc, -z0);
        b.bevelBox(x0 - 0.04f, yc + 0.02f, z0 - 0.04f, x1 + 0.04f, h, z1 + 0.04f, 0.18f, OD_WOOD);
        pochoir(b, LABEL, 'y', 11.0f, h + 0.01f, 3.4f, 2.0f, 1.0f);
        decal(b, AtlasUsure.AUREOLE, 'y', 5.0f, h, 3.6f, 3.4f, 3.0f);
        b.pop();
        b.bevelBox(x0 - 0.12f, yc - 0.3f, z0 - 0.12f, x1 + 0.12f, yc - 0.04f, z1 + 0.12f, 0.04f, STEEL);
        b.bevelBox(x0 - 0.12f, 0.3f, z0 - 0.12f, x1 + 0.12f, 0.55f, z1 + 0.12f, 0.04f, STEEL);
        for (float[] q : new float[][]{{2.6f, 1.6f}, {13.4f, 1.6f}, {2.6f, 6.0f}, {13.4f, 6.0f}}) pied(b, q[0], q[1], 0.38f, 0.25f);
        coins(b, x0, 0.19f, z0, x1, yc - 0.2f, z1, 0.75f, BRASS);
        b.bevelBox(7.4f, yc - 1.4f, z1 + 0.14f, 8.6f, yc - 0.1f, z1 + 0.32f, 0.06f, BRASS);
        cadenasPetit(b, 8, yc - 1.2f, z1 + 0.34f, true);
        fermoir(b, 3.8f, yc - 0.3f, z1 + 0.14f, BRASS);
        boulon(b, 12.0f, yc - 0.6f, z1 + 0.12f, 0.14f);
        boulon(b, 12.4f, yc - 0.6f, z1 + 0.12f, 0.14f);
        poigneeU(b, x1 + 0.1f, 3.4f, (z0 + z1) / 2, 2.6f, 1, STEEL);
        poigneeU(b, x0 - 0.1f, 3.4f, (z0 + z1) / 2, 2.6f, -1, STEEL);
        pochoir(b, S_ARMY, 'z', 8, 2.1f, z1 + 0.05f, 4.2f, 1.05f);
        coulure(b, 'z', 3.8f, yc - 1.3f, z1 + 0.14f, 0.9f, 1);
        boue(b, 'z', 8, 0.25f, z1 + 0.12f, 12.0f, 1.0f, 1);
    }

    /** Sac marin debout, appuyé contre le mur, tapis de sol sanglé dessus ; casque cabossé posé au pied. */
    static void paquetage(MeshBuilder b) {
        int m0 = b.mark();
        float cx = 7.0f, cz = 2.8f;
        b.push().translate(cx, 0, cz).rotateX(-9);
        b.lathe(new float[][]{{0.01f, 0}, {1.9f, 0.1f}, {2.7f, 0.6f}, {2.9f, 2.0f}, {2.95f, 7.0f}, {2.8f, 10.6f}, {2.2f, 12.6f}, {1.3f, 13.6f}, {0.6f, 14.1f}, {0.01f, 14.3f}},
                26, 0, 360, CANVAS, 1, 0.82f, 0.02f, 0.35f, 3, false);
        for (float y : new float[]{3.4f, 7.6f, 11.4f}) b.lathe(new float[][]{{3.3f, y - 0.3f}, {3.3f, y + 0.3f}}, 26, 0, 360, WEBBING, 1, 0.86f, 0, 0, 0, false);
        b.bevelBox(-0.9f, 4.4f, 2.3f, 0.9f, 7.0f, 2.75f, 0.2f, CANVAS);
        pochoir(b, S_ARMY, 'z', 0, 9.0f, 2.45f, 2.6f, 0.65f);
        b.tube(new float[][]{p(-1.6f, 12.4f, 1.8f), p(-2.2f, 8.0f, 2.6f), p(-1.8f, 3.0f, 2.2f)}, new float[]{0.2f, 0.2f, 0.2f}, 6, WEBBING, false, false, 0, false);
        b.push().translate(0, 14.1f, 0.2f).rotateZ(90);
        b.cylinder(1.15f, -3.8f, 3.8f, 16, TARP, KHAKI, KHAKI);
        b.cylinder(1.2f, -2.6f, -2.1f, 16, WEBBING, null, null);
        b.cylinder(1.2f, 2.1f, 2.6f, 16, WEBBING, null, null);
        b.pop();
        b.pop();
        caler(b, m0, 0.05f);
        int m1 = b.mark();
        b.push().translate(12.2f, 0.2f, 6.4f).rotateY(25).rotateZ(8);
        b.sphereSection(2.0f, 18, 8, 0, 90, OD_STEEL);
        b.push().scale(1.12f, 1, 1.18f);
        b.torus(1.95f, 0.18f, 18, 4, 0, 360, OD_DARK);
        b.pop();
        b.tube(new float[][]{p(-1.7f, 0.1f, 0.6f), p(0, -0.05f, 2.6f), p(1.7f, 0.1f, 0.6f)}, new float[]{0.1f, 0.1f, 0.1f}, 5, WEBBING, false, false, 0, false);
        b.pop();
        float[] bh = b.bounds(m1);
        b.offset(m1, 0, -bh[1], 0);
    }

    /** Caisse de grenades (50 x 30 x 25 cm) ; ouverte : couvercle retiré, alvéoles vides. */
    static void caisseGrenade(MeshBuilder b, float x0, float y0, float z0, boolean ouverte) {
        float L = 8.0f, W = 4.8f, H = 3.9f, x1 = x0 + L, z1 = z0 + W;
        b.bevelBox(x0, y0, z0, x1, y0 + H - 0.7f, z1, 0.1f, OD_WOOD);
        if (ouverte) {
            b.box(x0 + 0.3f, y0 + H - 0.78f, z0 + 0.3f, x1 - 0.3f, y0 + H - 0.62f, z1 - 0.3f, AtlasUsure.INTERIEUR_SALE);
            for (int k = 0; k < 3; k++) {
                b.push().translate(x0 + 1.6f + k * 2.4f, y0 + H - 0.6f, (z0 + z1) / 2);
                b.hollowCylinder(0.9f, 0.7f, -0.02f, 0.15f, 12, OD_DARK);
                b.pop();
            }
        } else {
            b.bevelBox(x0 - 0.05f, y0 + H - 0.72f, z0 - 0.05f, x1 + 0.05f, y0 + H, z1 + 0.05f, 0.1f, OD_WOOD);
        }
        b.bevelBox(x0 - 0.06f, y0 + 1.9f, z0 - 0.06f, x1 + 0.06f, y0 + 2.3f, z1 + 0.06f, 0.02f, YELLOW.withWorld(4));
        pochoir(b, S_GRENADES, 'z', x0 + L / 2, y0 + 1.1f, z1 + 0.02f, 4.6f, 1.15f);
        for (float x : new float[]{x0 + 1.4f, x1 - 1.4f}) b.bevelBox(x - 0.4f, y0 + H - 1.2f, z1 + 0.04f, x + 0.4f, y0 + H - 0.1f, z1 + 0.2f, 0.05f, STEEL);
        poigneeCorde(b, x1 + 0.04f, y0 + 2.0f, (z0 + z1) / 2, 1.8f, 1);
        poigneeCorde(b, x0 - 0.04f, y0 + 2.0f, (z0 + z1) / 2, 1.8f, -1);
    }

    static void caisseGrenade(MeshBuilder b, float x0, float y0, float z0) {
        caisseGrenade(b, x0, y0, z0, false);
    }

    /** Deux caisses de grenades (celle du dessus ouverte et vide), son couvercle par terre, une grenade devant. */
    static void caisseGrenades(MeshBuilder b) {
        caisseGrenade(b, 2.6f, 0, 0.7f, false);
        b.push().translate(8, 3.9f, 3.2f).rotateY(14).translate(-8, -3.9f, -3.2f);
        caisseGrenade(b, 4.0f, 3.9f, 0.9f, true);
        b.pop();
        b.push().translate(5.2f, 0, 9.6f).rotateY(-12);
        b.bevelBox(-4.05f, 0, -2.45f, 4.05f, 0.72f, 2.45f, 0.1f, OD_WOOD);
        b.pop();
        b.push().translate(12.6f, 0.75f, 8.4f).rotateZ(80);
        b.sphere(0.7f, 12, 8, OD_DARK);
        b.cylinder(0.32f, 0.5f, 1.2f, 8, STEEL, null, STEEL);
        b.pop();
        boue(b, 'z', 6.6f, 0, 5.5f, 8.0f, 0.45f, 1);
    }

    static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    /** Radio de campagne (30 x 30 x 35 cm) posée sur une caisse : écran fêlé, antenne pliée, combiné arraché qui pend. */
    static void caisseRadio(MeshBuilder b) {
        float cx0 = 2.6f, cx1 = 13.4f, cz0 = 0.6f, cz1 = 7.4f, ch = 6.0f;
        b.bevelBox(cx0, 0, cz0, cx1, ch - 0.92f, cz1, 0.12f, OD_WOOD);
        b.bevelBox(cx0 - 0.12f, ch - 0.9f, cz0 - 0.12f, cx1 + 0.12f, ch, cz1 + 0.12f, 0.1f, OD_WOOD);
        coins(b, cx0, 0, cz0, cx1, ch - 0.9f, cz1, 0.6f, STEEL);
        poigneeCorde(b, cx1 + 0.04f, 3.4f, 4.0f, 2.2f, 1);
        pochoir(b, LABEL, 'z', 10.6f, 2.6f, cz1 + 0.02f, 2.0f, 1.0f);
        pochoir(b, S_RADIO, 'z', 5.6f, 2.6f, cz1 + 0.02f, 3.6f, 0.9f);
        boue(b, 'z', 8, 0, cz1, 10.0f, 1.2f, 0);
        float x0 = 4.2f, x1 = 9.8f, z0 = 1.2f, z1 = 6.2f, y0 = ch, y1 = ch + 6.0f;
        b.bevelBox(x0, y0 + 0.02f, z0, x1, y1, z1, 0.3f, OD_STEEL);
        b.bevelBox(x0 + 0.3f, y0 + 0.3f, z1 - 0.04f, x1 - 0.3f, y1 - 0.3f, z1 + 0.1f, 0.06f, OD_DARK);
        pochoir(b, RADIO_PANEL, 'z', 7, y0 + 3.0f, z1 + 0.1f, 4.6f, 4.6f);
        for (float dx : new float[]{-1.37f, 1.37f}) {
            b.push().translate(7 + dx, y0 + 1.99f, z1 + 0.12f).rotateX(90);
            b.cylinder(0.4f, 0, 0.35f, 12, DARK, null, DARK);
            b.cylinder(0.16f, 0.35f, 0.45f, 8, CHROME, null, CHROME);
            b.pop();
        }
        pochoir(b, DARK, 'z', 7, y0 + 3.97f, z1 + 0.14f, 3.1f, 1.15f);   // écran mort, batterie vide
        decal(b, AtlasUsure.FISSURE, 'z', 7.4f, y0 + 4.0f, z1 + 0.12f, 2.0f, 2.0f, 30);
        decal(b, AtlasUsure.COULURE_2, 'X', x1, y1 - 0.4f, 3.6f, 1.2f, 2.4f);
        b.tube(new float[][]{p(x0 + 0.8f, y1, 2.6f), p(x0 + 1.1f, y1 + 0.7f, 2.6f), p(x1 - 1.1f, y1 + 0.7f, 2.6f), p(x1 - 0.8f, y1, 2.6f)},
                new float[]{0.13f, 0.13f, 0.13f, 0.13f}, 6, STEEL, false, false, 0, false);
        b.push().translate(x0 + 0.8f, y1, 5.2f);
        b.cylinder(0.32f, 0, 0.6f, 10, DARK, null, DARK);
        b.pop();
        // antenne fouet pliée en deux
        b.tube(new float[][]{p(x0 + 0.8f, y1 + 0.6f, 5.2f), p(x0 + 0.75f, y1 + 4.0f, 4.9f), p(x0 + 2.6f, y1 + 5.4f, 3.4f), p(x0 + 6.0f, y1 + 3.6f, 1.2f)}, new float[]{0.1f, 0.07f, 0.06f, 0.05f}, 5, DARK, false, true, 0, false);
        // combiné arraché : il pend au bout de son cordon le long de la caisse
        b.bevelBox(x1 + 0.3f, 2.6f, 5.6f, x1 + 0.75f, 3.6f, 7.0f, 0.18f, DARK);
        b.bevelBox(x1 + 0.04f, 1.6f, 6.0f, x1 + 0.6f, 5.4f, 7.0f, 0.22f, DARK);
        float[][] spire = new float[20][];
        for (int k = 0; k < 20; k++) {
            double a = k * 0.95;
            spire[k] = p(x1 + 0.35f + 0.22f * (float) Math.cos(a), y0 + 0.9f - k * 0.16f, 4.6f + 0.22f * (float) Math.sin(a) + k * 0.05f);
        }
        b.tube(spire, fill(20, 0.05f), 4, DARK, false, false, 0, false);
    }

    /** Grande caisse (1,2 x 0,75 x 0,7 m) sur deux blocs, sous un filet de camouflage déchiré, feuilles mortes. */
    static void caisseFilet(MeshBuilder b) {
        float x0 = 6.4f, x1 = 25.6f, z0 = 0.6f, z1 = 12.6f, h = 11.0f;
        b.bevelBox(x0, 0, z0, x1, h, z1, 0.2f, OD_WOOD);
        for (float x : new float[]{x0 + 1.8f, x1 - 2.8f}) b.bevelBox(x, 0.2f, z1 + 0.02f, x + 1.0f, h - 0.2f, z1 + 0.35f, 0.1f, OD_WOOD);
        pochoir(b, S_762, 'z', 16, 3.2f, z1 + 0.37f, 5.4f, 1.35f);
        boue(b, 'z', 16, 0, z1 + 0.02f, 4.0f, 1.6f, 1);
        decal(b, AtlasUsure.MOUSSE_VERTE, 'z', 10.6f, 0.9f, z1 + 0.02f, 3.0f, 1.6f);
        float e = 0.35f, yt = h + 0.3f;
        float[] tx = {x0 - e, x1 + e}, tz = {z0 - e, z1 + e};
        b.face(CAMO_NET, v(0, 1, 0), p(tx[0], yt, tz[0]), p(tx[1], yt, tz[0]), p(tx[1], yt, tz[1]), p(tx[0], yt, tz[1]));
        float o = 1.8f;
        b.face(CAMO_NET, v(0, 0.5f, 1), p(tx[0], yt, tz[1]), p(tx[1], yt, tz[1]), p(tx[1] + 0.5f, 0.6f, tz[1] + o), p(tx[0] + 4.0f, 3.6f, tz[1] + o * 0.6f));
        b.face(CAMO_NET, v(1, 0.5f, 0), p(tx[1], yt, tz[1]), p(tx[1], yt, tz[0]), p(tx[1] + o * 0.9f, 2.2f, tz[0] + 0.4f), p(tx[1] + o * 0.9f, 1.4f, tz[1] + 0.3f));
        b.face(CAMO_NET, v(-1, 0.5f, 0), p(tx[0], yt, tz[0]), p(tx[0], yt, tz[1]), p(tx[0] - o * 0.7f, 2.8f, tz[1] + 0.3f), p(tx[0] - o * 0.7f, 3.2f, tz[0] + 0.4f));
        for (float[] c : new float[][]{{x0 + 1.0f, z0 + 1.0f}, {x1 - 1.0f, z1 - 1.0f}, {20.0f, 3.0f}, {11.0f, 9.0f}}) {
            b.push().translate(c[0], yt - 0.1f, c[1]).scale(1.9f, 0.55f, 1.5f);
            b.sphereSection(1.0f, 10, 5, 0, 90, CAMO_NET);
            b.pop();
        }
        jonchee(b, x0, z0 + 1, x1, z1 - 1, 10, AtlasUsure.FEUILLES, 1.4f, 91);
    }
}
