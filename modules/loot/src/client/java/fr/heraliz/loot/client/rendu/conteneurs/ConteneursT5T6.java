package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasLoot.*;
import static fr.heraliz.loot.client.rendu.conteneurs.Kit.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * T5 — bleu ONU (grosses bases, bunker) ; T6 — sécurité rouge et acier noir, bandes de danger, voyants (carte rouge,
 * crash d'avion, largages). Abandonnés eux aussi : poussière, boue, coulures, traces de pied-de-biche et d'impacts sur
 * le matériel qui a résisté. Échelle réelle, dos au mur ; les objets longs occupent deux blocs vers la droite.
 * Les vitres vont dans {@link Conteneurs#maillages} (passe translucide).
 */
final class ConteneursT5T6 {
    private ConteneursT5T6() {
    }

    // =================================================================== T5
    /** Caisse d'armes ONU (1,5 x 0,6 x 0,5 m) sur deux blocs. */
    static void caisseArmesOnu(MeshBuilder b) {
        float x0 = 4.0f, x1 = 28.0f, z0 = 0.6f, z1 = 10.2f, yc = 6.6f, h = 8.2f;
        for (float z : new float[]{2.0f, 8.8f}) b.bevelBox(x0 + 0.6f, 0, z - 0.6f, x1 - 0.6f, 0.6f, z + 0.6f, 0.15f, DARK);
        b.bevelBox(x0, 0.6f, z0, x1, yc, z1, 0.25f, UN_BLUE);
        // tôle nervurée à l'avant (sauf le cartouche du milieu)
        for (float x = x0 + 1.0f; x < x1 - 1.0f; x += 1.2f) {
            if (x > 12.4f && x < 19.6f) continue;
            b.bevelBox(x, 1.2f, z1 + 0.02f, x + 0.5f, yc - 0.5f, z1 + 0.24f, 0.1f, UN_BLUE);
        }
        b.bevelBox(x0 - 0.15f, yc + 0.02f, z0 - 0.15f, x1 + 0.15f, h, z1 + 0.15f, 0.3f, UN_BLUE);
        pochoir(b, S_UN, 'z', 16, 3.6f, z1 + 0.04f, 4.4f, 2.2f);
        pochoir(b, S_UN, 'y', 16, h + 0.01f, (z0 + z1) / 2, 7.0f, 3.5f);
        pochoir(b, LABEL, 'z', 25.4f, 4.4f, z1 + 0.04f, 2.0f, 1.0f);
        coins(b, x0, 0.6f, z0, x1, h, z1, 1.0f, STEEL);
        fermoir(b, 8.0f, yc, z1 + 0.16f, STEEL);
        boulon(b, 23.6f, yc - 0.5f, z1 + 0.24f, 0.15f);
        boulon(b, 24.4f, yc - 0.5f, z1 + 0.24f, 0.15f);
        decal(b, AtlasUsure.GRIFFURES, 'z', 24.0f, yc - 1.4f, z1 + 0.24f, 3.0f, 1.4f, -10);
        coulure(b, 'z', x0 + 0.35f, yc - 0.3f, z1, 0.6f, 0);
        coulure(b, 'z', x1 - 0.35f, yc - 0.3f, z1, 0.6f, 1);
        boue(b, 'z', 16, 0.6f, z1 + 0.24f, 20.0f, 1.2f, 0);
        poigneeU(b, x1 + 0.08f, 5.2f, (z0 + z1) / 2, 3.4f, 1, STEEL);
        poigneeU(b, x0 - 0.08f, 5.2f, (z0 + z1) / 2, 3.4f, -1, STEEL);
    }

    // =================================================================== T6
    /** Coffre-fort rouge à code (65 x 60 x 80 cm), dos au mur. */
    static void coffreCode(MeshBuilder b) {
        float x0 = 2.8f, x1 = 13.2f, z0 = 0.6f, z1 = 9.6f, y0 = 0.6f, y1 = 13.0f;
        for (float[] q : new float[][]{{3.6f, 1.4f}, {12.4f, 1.4f}, {3.6f, 8.8f}, {12.4f, 8.8f}}) pied(b, q[0], q[1], 0.5f, y0);
        b.bevelBox(x0, y0, z0, x1, y1, z1, 0.5f, RED_STEEL);
        b.bevelBox(x0 + 0.55f, y0 + 0.55f, z1 - 0.08f, x1 - 0.55f, y1 - 0.55f, z1 + 0.06f, 0.06f, DARK);
        b.bevelBox(x0 + 0.75f, y0 + 0.75f, z1 + 0.02f, x1 - 0.75f, y1 - 0.75f, z1 + 0.55f, 0.25f, RED_STEEL);
        for (float y : new float[]{2.8f, 10.6f}) {
            b.push().translate(x0 + 0.62f, y, z1 + 0.32f);
            b.cylinder(0.32f, 0, 1.2f, 10, BLACK_STEEL, BLACK_STEEL, BLACK_STEEL);
            b.pop();
        }
        float fz = z1 + 0.55f;
        b.bevelBox(9.1f, 7.6f, fz, 11.9f, 11.6f, fz + 0.26f, 0.1f, BLACK_STEEL);
        pochoir(b, KEYPAD, 'z', 10.5f, 8.9f, fz + 0.27f, 1.9f, 1.9f);
        pochoir(b, LCD, 'z', 10.5f, 10.8f, fz + 0.28f, 2.1f, 0.85f);
        b.bevelBox(11.25f, 7.8f, fz + 0.26f, 11.6f, 8.15f, fz + 0.34f, 0.03f, LED_RED);
        b.push().translate(10.0f, 5.2f, fz).rotateX(90);
        b.cylinder(0.7f, 0, 0.42f, 16, CHROME, null, CHROME);
        b.pop();
        b.tube(new float[][]{p(10.0f, 5.2f, fz + 0.62f), p(7.2f, 5.2f, fz + 0.62f)}, new float[]{0.24f, 0.21f}, 10, CHROME, true, true, 0, false);
        decal(b, AtlasUsure.IMPACTS, 'z', 6.0f, 9.6f, fz, 4.4f, 2.2f, 8);
        decal(b, AtlasUsure.GRIFFURES, 'z', 5.2f, 2.6f, fz, 3.0f, 1.4f, 0);
        decal(b, AtlasUsure.COULURE_1, 'X', x1, 6.0f, 5.0f, 1.4f, 2.8f);
        boue(b, 'z', 8, y0, z1 + 0.06f, 9.6f, 1.0f, 1);
    }

    /** Pistolet couché (canon vers +X), aligné sur la découpe de FOAM_PISTOL (décalcomanie 8,8 x 5,4 centrée en (8, z 6,6)). */
    static void pistolet(MeshBuilder b, float y) {
        b.bevelBox(4.63f, y, 5.59f, 8.41f, y + 0.55f, 6.43f, 0.1f, BLACK_STEEL);
        b.bevelBox(4.8f, y + 0.5f, 5.78f, 8.25f, y + 0.6f, 6.24f, 0.02f, DARK);
        b.push().translate(5.8f, y, 6.33f).rotateY(-10);
        b.bevelBox(0, 0, 0, 0.82f, 0.48f, 1.75f, 0.12f, DARK);
        b.pop();
        b.bevelBox(9.44f, y, 5.17f, 10.13f, y + 0.45f, 7.86f, 0.08f, DARK);
        b.bevelBox(10.47f, y, 5.17f, 11.16f, y + 0.45f, 7.86f, 0.08f, DARK);
        for (float x : new float[]{9.78f, 10.81f}) b.bevelBox(x - 0.18f, y + 0.45f, 5.25f, x + 0.18f, y + 0.56f, 5.6f, 0.03f, BRASS);
    }

    /** Mallette blindée ouverte (65 x 45 x 12 cm) posée au sol : pistolet et chargeurs dans la mousse. */
    static void malletteOuverte(MeshBuilder b) {
        float x0 = 2.8f, x1 = 13.2f, z0 = 3.0f, z1 = 10.2f, yb = 1.9f, zc = (z0 + z1) / 2;
        float[][] contour = MeshBuilder.roundRect(x1 - x0, z1 - z0, 0.7f, 4);
        b.push().translate(8, yb / 2, zc).rotateX(-90);
        b.slab(contour, yb, 0.25f, true, RED_STEEL, RED_STEEL, RED_STEEL);
        b.pop();
        // joint caoutchouc : sa face haute reste sous le bord (pas de faces confondues)
        b.push().translate(8, yb - 0.17f, zc).rotateX(-90);
        b.slab(MeshBuilder.roundRect(x1 - x0 + 0.14f, z1 - z0 + 0.14f, 0.77f, 4), 0.22f, 0.04f, true, RUBBER, RUBBER, RUBBER);
        b.pop();
        pochoir(b, FOAM_PISTOL, 'y', 8, yb, 6.6f, 8.8f, 5.4f);
        pistolet(b, yb - 0.08f);
        // couvercle ouvert (100°) vers l'arrière
        b.push().translate(0, yb, z0).rotateX(-100).translate(0, -yb, -z0);
        b.push().translate(8, yb + 0.7f, zc).rotateX(-90);
        b.slab(contour, 1.4f, 0.25f, true, RED_STEEL, FOAM, RED_STEEL);
        b.pop();
        b.pop();
        jonchee(b, 1.0f, 10.6f, 15.0f, 14.6f, 4, AtlasUsure.FEUILLES, 1.1f, 101);
        for (float x : new float[]{4.6f, 11.4f}) b.bevelBox(x - 0.55f, 0.6f, z1 - 0.04f, x + 0.55f, 1.6f, z1 + 0.24f, 0.08f, CHROME);
        for (float x : new float[]{6.6f, 9.4f}) b.bevelBox(x - 0.32f, 0.75f, z1 - 0.08f, x + 0.32f, 1.4f, z1 + 0.42f, 0.08f, BLACK_STEEL);
        b.tube(new float[][]{p(6.6f, 1.08f, z1 + 0.34f), p(7.0f, 1.08f, z1 + 1.0f), p(9.0f, 1.08f, z1 + 1.0f), p(9.4f, 1.08f, z1 + 0.34f)},
                new float[]{0.3f, 0.33f, 0.33f, 0.3f}, 10, RUBBER, false, false, 0, false);
    }

    /** Caisse de missile (1,9 x 0,56 x 0,48 m) sur deux blocs, fenêtre sur le missile. */
    static void caisseMissiles(MeshBuilder b) {
        float x0 = 0.8f, x1 = 31.2f, z0 = 0.6f, z1 = 9.6f, yb = 5.6f, h = 7.6f;
        b.bevelBox(x0, 0, z0, x1, yb, z1, 0.25f, BLACK_STEEL);
        float wx0 = 8.0f, wx1 = 24.0f, wz0 = 2.6f, wz1 = 7.6f;
        b.bevelBox(x0, yb + 0.02f, z0, x1, h, wz0, 0.25f, BLACK_STEEL);
        b.bevelBox(x0, yb + 0.02f, wz1, x1, h, z1, 0.25f, BLACK_STEEL);
        b.bevelBox(x0, yb + 0.02f, wz0 - 0.1f, wx0, h, wz1 + 0.1f, 0.25f, BLACK_STEEL);
        b.bevelBox(wx1, yb + 0.02f, wz0 - 0.1f, x1, h, wz1 + 0.1f, 0.25f, BLACK_STEEL);
        b.face(INNER, v(0, 1, 0), p(wx0, yb + 0.04f, wz0), p(wx1, yb + 0.04f, wz0), p(wx1, yb + 0.04f, wz1), p(wx0, yb + 0.04f, wz1));
        float mz = (wz0 + wz1) / 2, my = yb + 0.3f;
        b.push().translate(4.0f, my, mz).rotateZ(-90);
        b.cylinder(1.15f, 0, 20.0f, 20, MISSILE, MISSILE, null);
        b.cylinder(1.18f, 16.4f, 17.2f, 20, RED_STEEL, null, null);
        b.translate(0, 20.0f, 0);
        b.lathe(new float[][]{{1.15f, 0}, {1.0f, 1.2f}, {0.55f, 2.6f}, {0.01f, 3.2f}}, 20, 0, 360, MISSILE, 1, 1, 0, 0, 0, false);
        b.pop();
        for (int k = 0; k < 4; k++) {
            b.push().translate(5.4f, my, mz).rotateX(45 + k * 90);
            b.bevelBox(-1.4f, 1.05f, -0.06f, 1.4f, 2.3f, 0.06f, 0.03f, MISSILE);
            b.pop();
        }
        for (float x : new float[]{5.0f, 26.0f}) b.bevelBox(x, -0.02f, z0 - 0.12f, x + 1.1f, h + 0.12f, z1 + 0.12f, 0.1f, RED_STEEL);
        b.bevelBox(9.0f, 0.5f, z1 + 0.02f, 23.0f, 1.3f, z1 + 0.06f, 0.02f, HAZARD);
        pochoir(b, S_DANGER, 'z', 16, 3.2f, z1 + 0.02f, 5.4f, 1.35f);
        pochoir(b, S_MISSILE, 'y', 16, h + 0.01f, 8.6f, 5.0f, 1.1f);
        for (float x : new float[]{11.0f, 21.0f}) fermoir(b, x, yb + 0.2f, z1 + 0.04f, STEEL);
        coulure(b, 'z', 6.6f, yb - 0.2f, z1 + 0.12f, 1.1f, 2);
        coulure(b, 'z', 27.6f, yb - 0.2f, z1 + 0.12f, 1.1f, 0);
        boue(b, 'z', 16, 0, z1 + 0.06f, 7.0f, 0.45f, 1);
        poigneeU(b, x1 + 0.08f, 4.2f, (z0 + z1) / 2, 3.2f, 1, STEEL);
        poigneeU(b, x0 - 0.08f, 4.2f, (z0 + z1) / 2, 3.2f, -1, STEEL);
    }

    static void caisseMissilesVerre(MeshBuilder b) {
        b.face(GLASS, v(0, 1, 0), p(8.0f, 7.45f, 2.6f), p(24.0f, 7.45f, 2.6f), p(24.0f, 7.45f, 7.6f), p(8.0f, 7.45f, 7.6f));
    }

    /** Fusil d'assaut (1 m) couché le long de X, crosse en x0, axe du canon en (y, zc). */
    static void fusil(MeshBuilder b, float x0, float y, float zc) {
        float z0 = zc - 0.3f;
        b.bevelBox(x0, y - 0.25f, z0, x0 + 3.0f, y + 0.7f, z0 + 0.6f, 0.18f, RUBBER);
        b.bevelBox(x0 + 3.0f, y, z0 - 0.04f, x0 + 7.4f, y + 0.8f, z0 + 0.64f, 0.15f, BLACK_STEEL);
        b.bevelBox(x0 + 7.4f, y + 0.08f, z0, x0 + 11.6f, y + 0.68f, z0 + 0.6f, 0.15f, DARK);
        b.tube(new float[][]{p(x0 + 11.6f, y + 0.4f, zc), p(x0 + 14.4f, y + 0.4f, zc)}, new float[]{0.14f, 0.12f}, 8, BLACK_STEEL, false, true, 0, false);
        b.bevelBox(x0 + 4.2f, y - 1.7f, z0 + 0.06f, x0 + 4.9f, y, z0 + 0.54f, 0.12f, DARK);
        b.push().translate(x0 + 6.0f, y, z0 + 0.08f).rotateZ(-14);
        b.bevelBox(0, -1.3f, 0, 0.55f, 0, 0.44f, 0.12f, RUBBER);
        b.pop();
        b.push().translate(x0 + 4.0f, y + 1.15f, zc).rotateZ(-90);
        b.cylinder(0.25f, 0, 2.8f, 12, DARK, DARK, DARK);
        b.pop();
    }

    /** Vitrine d'armurerie debout (1,75 x 0,5 x 1,4 m) sur deux blocs : fusil en haut, pistolet en bas, éclairée. */
    static void vitrineArme(MeshBuilder b) {
        float x0 = 2.0f, x1 = 30.0f, z0 = 0.6f, z1 = 8.6f, yb = 7.2f, yt = 21.6f;
        b.bevelBox(x0, 0, z0, x1, yb, z1, 0.3f, RED_STEEL);
        b.bevelBox(x0 - 0.12f, yb + 0.02f, z0 - 0.12f, x1 + 0.12f, yb + 0.42f, z1 + 0.12f, 0.1f, BLACK_STEEL);
        for (float xa : new float[]{x0 + 0.7f, 16.25f}) b.bevelBox(xa, 0.7f, z1 + 0.02f, xa + 13.05f, yb - 0.7f, z1 + 0.2f, 0.12f, RED_STEEL);
        for (float x : new float[]{15.2f, 16.8f}) b.bevelBox(x - 0.25f, 3.0f, z1 + 0.2f, x + 0.25f, 4.6f, z1 + 0.42f, 0.06f, BRASS);
        // fond sombre, montants et chapeau, bandeau lumineux
        b.bevelBox(x0 + 0.2f, yb + 0.42f, z0, x1 - 0.2f, yt, z0 + 0.4f, 0.05f, INNER);
        float g = 0.32f;
        for (float[] c : new float[][]{{x0 + 0.15f, z0 + 0.15f}, {x1 - 0.15f - g, z0 + 0.15f}, {x0 + 0.15f, z1 - 0.15f - g}, {x1 - 0.15f - g, z1 - 0.15f - g}, {15.84f, z1 - 0.15f - g}}) {
            b.bevelBox(c[0], yb + 0.42f, c[1], c[0] + g, yt, c[1] + g, 0.06f, BLACK_STEEL);
        }
        b.bevelBox(x0 - 0.12f, yt + 0.02f, z0 - 0.12f, x1 + 0.12f, yt + 0.8f, z1 + 0.12f, 0.2f, BLACK_STEEL);
        b.bevelBox(x0 + 1.0f, yt - 0.14f, 4.0f, x1 - 1.0f, yt - 0.02f, 5.2f, 0.03f, LED_AMBER);
        // étagère de verre (cadre noir), présentoirs
        b.bevelBox(x0 + 0.5f, 13.6f, z0 + 0.5f, x1 - 0.5f, 13.75f, z1 - 0.6f, 0.04f, BLACK_STEEL);
        for (float x : new float[]{10.0f, 20.0f}) {
            b.bevelBox(x - 0.5f, 13.75f, 3.4f, x + 0.5f, 14.0f, 5.2f, 0.06f, BLACK_STEEL);
            b.bevelBox(x - 0.15f, 14.0f, 4.15f, x + 0.15f, 15.6f, 4.45f, 0.04f, CHROME);
        }
        fusil(b, 8.6f, 15.75f, 4.3f);
        b.bevelBox(13.6f, yb + 0.42f, 3.0f, 18.4f, yb + 1.4f, 6.6f, 0.15f, BLACK_STEEL);
        b.push().translate(16, yb + 1.4f, 4.8f).rotateY(-20);
        b.bevelBox(-1.9f, 0.02f, -0.3f, 1.9f, 0.55f, 0.3f, 0.1f, BLACK_STEEL);
        b.bevelBox(-1.6f, 0.02f, 0.3f, -0.9f, 0.45f, 1.7f, 0.1f, DARK);
        b.pop();
        pochoir(b, LABEL, 'z', 16, yb + 0.9f, 6.62f, 1.8f, 0.9f);
        // la vitre a pris des coups : fêlures et impact (décalcomanies devant le verre)
        decal(b, AtlasUsure.FISSURE, 'z', 9.0f, 17.6f, 8.35f, 6.0f, 6.0f, 15);
        decal(b, AtlasUsure.FISSURE, 'z', 23.0f, 11.0f, 8.35f, 5.0f, 5.0f, -40);
        decal(b, AtlasUsure.GRIFFURES, 'z', 22.0f, 3.6f, z1 + 0.2f, 3.6f, 1.6f, 10);
        boue(b, 'z', 16, 0, z1, 24.0f, 0.6f, 0);
    }

    /** Vitres de la vitrine (avant, côtés, étagère). */
    static void vitrineArmeVerre(MeshBuilder b) {
        float x0 = 2.25f, x1 = 29.75f, z0 = 1.0f, z1 = 8.35f, y0 = 7.62f, y1 = 21.6f;
        b.face(GLASS, v(0, 0, 1), p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1));
        b.face(GLASS, v(1, 0, 0), p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1));
        b.face(GLASS, v(-1, 0, 0), p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
    }

    /** Conteneur sécurisé (1 x 1 x 0,85 m), ossature noire, panneaux rouges nervurés, portes verrouillées. */
    static void conteneurSecurise(MeshBuilder b) {
        float x0 = 0.5f, x1 = 15.5f, z0 = 0.5f, z1 = 15.5f, h = 13.4f;
        float t = 1.3f;
        for (float[] c : new float[][]{{x0, z0}, {x1 - t, z0}, {x0, z1 - t}, {x1 - t, z1 - t}}) b.bevelBox(c[0] - 0.02f, 0, c[1] - 0.02f, c[0] + t + 0.02f, h, c[1] + t + 0.02f, 0.2f, BLACK_STEEL);
        b.bevelBox(x0 + 0.1f, 0, z0 + 0.1f, x1 - 0.1f, 1.4f, z1 - 0.1f, 0.2f, BLACK_STEEL);
        b.bevelBox(x0 + 0.1f, h - 1.3f, z0 + 0.1f, x1 - 0.1f, h - 0.08f, z1 - 0.1f, 0.25f, BLACK_STEEL);
        b.bevelBox(x0 + 0.4f, 1.42f, z0 + 0.4f, x1 - 0.4f, h - 1.32f, z1 - 0.4f, 0.1f, RED_STEEL);
        for (float k = 2.4f; k < 13.8f; k += 1.6f) {
            b.bevelBox(k, 1.6f, z0 + 0.15f, k + 0.7f, h - 1.5f, z0 + 0.42f, 0.12f, RED_STEEL);
            b.bevelBox(x0 + 0.15f, 1.6f, k, x0 + 0.42f, h - 1.5f, k + 0.7f, 0.12f, RED_STEEL);
            b.bevelBox(x1 - 0.42f, 1.6f, k, x1 - 0.15f, h - 1.5f, k + 0.7f, 0.12f, RED_STEEL);
        }
        float fz = z1 - 0.35f;
        b.bevelBox(x0 + t + 0.02f, 1.42f, fz, 8 - 0.05f, h - 1.32f, fz + 0.3f, 0.1f, RED_STEEL);
        b.bevelBox(8 + 0.05f, 1.42f, fz, x1 - t - 0.02f, h - 1.32f, fz + 0.3f, 0.1f, RED_STEEL);
        for (float x : new float[]{4.0f, 6.6f, 9.4f, 12.0f}) {
            b.push().translate(x, 1.2f, fz + 0.65f);
            b.cylinder(0.25f, 0, h - 2.4f, 10, STEEL, STEEL, STEEL);
            b.pop();
            for (float y : new float[]{1.8f, h - 1.8f}) b.bevelBox(x - 0.45f, y - 0.35f, fz + 0.32f, x + 0.45f, y + 0.35f, fz + 0.98f, 0.08f, DARK);
        }
        for (float x : new float[]{6.6f, 9.4f}) b.bevelBox(x - 0.25f, 6.0f, fz + 0.75f, x + 0.25f, 7.8f, fz + 1.15f, 0.1f, STEEL);
        b.bevelBox(13.0f, 6.6f, fz + 0.32f, 14.0f, 9.6f, fz + 0.55f, 0.08f, BLACK_STEEL);
        pochoir(b, KEYPAD, 'z', 13.5f, 7.6f, fz + 0.54f, 0.8f, 0.8f);
        b.bevelBox(13.2f, 8.7f, fz + 0.55f, 13.8f, 9.1f, fz + 0.63f, 0.04f, LED_RED);
        b.bevelBox(x0 + t + 0.04f, 0.3f, z1 - 0.02f, x1 - t - 0.04f, 1.2f, z1 + 0.06f, 0.03f, HAZARD);
        decal(b, AtlasUsure.IMPACTS, 'X', x1 - 0.15f, 8.6f, 6.0f, 5.0f, 2.5f, 6);
        coulure(b, 'X', x1 - 0.15f, h - 1.6f, 10.4f, 1.4f, 2);
        coulure(b, 'x', x0 + 0.15f, h - 1.6f, 4.8f, 1.4f, 1);
    }
}
