package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.Kit.pochoir;
import static fr.heraliz.loot.client.rendu.conteneurs.KitCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * T3 — police (nylon et plastique noirs, bande bleue, lettrage POLICE) et chasse (camouflage, noyer, orange fluo),
 * pillés et abandonnés : fermoirs forcés, vitre brisée, râtelier à moitié vide, croix de fouille, boue.
 * Échelle réelle (1 px = 6,25 cm), placements vraisemblables : dos au mur (z = 0), appuyés, couchés.
 */
final class ConteneursT3 {
    private ConteneursT3() {
    }

    /** Valise rigide à fusil (1,2 m) debout, appuyée contre le mur, un fermoir forcé ouvert. */
    static void valiseArme(MeshBuilder b) {
        int m0 = b.mark();
        float w = 6.4f, L = 19.2f, e = 2.6f;
        b.push().translate(8, 0, 3.2f).rotateX(-10).translate(0, L / 2, 0);
        float[][] contour = MeshBuilder.roundRect(w, L, 0.8f, 4);
        b.push().translate(0, 0, -e / 4);
        b.slab(contour, e / 2, 0.25f, true, DARK, DARK, DARK);
        b.pop();
        b.slab(MeshBuilder.roundRect(w + 0.12f, L + 0.12f, 0.86f, 4), 0.16f, 0.03f, true, RUBBER, RUBBER, RUBBER);
        b.push().translate(0, 0, e / 4);
        b.slab(contour, e / 2, 0.25f, true, DARK, DARK, DARK);
        b.pop();
        float fz = e / 2;
        for (float x : new float[]{-1.5f, 0, 1.5f}) b.bevelBox(x - 0.25f, -L / 2 + 2.0f, fz - 0.03f, x + 0.25f, L / 2 - 2.0f, fz + 0.18f, 0.08f, DARK);
        b.bevelBox(-w / 2 - 0.02f, -L / 2 + 1.0f, -0.25f, -w / 2 + 0.06f, L / 2 - 1.0f, 0.25f, 0.02f, POLICE_BLUE);
        b.push().translate(0, 2.0f, fz + 0.24f).rotateZ(90);
        b.decalZ(S_POLICE, 0, 0, 0, 5.6f, 1.4f);
        b.pop();
        decal(b, ETIQUETTE_DECHIREE, 'z', 0.8f, -6.0f, fz + 0.18f, 2.6f, 1.3f, 90);
        decal(b, GRIFFURES, 'z', -0.6f, 6.4f, fz + 0.18f, 4.0f, 2.0f, 70);
        // fermoirs et poignée sur le chant droit ; celui du milieu a été forcé et pend
        for (float y : new float[]{-6.0f, 6.0f}) b.bevelBox(w / 2 - 0.05f, y - 0.6f, -0.45f, w / 2 + 0.3f, y + 0.6f, 0.45f, 0.08f, STEEL);
        b.push().translate(w / 2 + 0.1f, 0.4f, 0.45f).rotateY(-55);
        b.bevelBox(-0.05f, -0.6f, -0.9f, 0.2f, 0.6f, 0, 0.08f, STEEL);
        b.pop();
        b.tube(new float[][]{p(w / 2 + 0.1f, -2.0f, 0), p(w / 2 + 0.8f, -1.6f, 0), p(w / 2 + 0.8f, 1.2f, 0), p(w / 2 + 0.1f, 1.6f, 0)},
                new float[]{0.24f, 0.26f, 0.26f, 0.24f}, 8, RUBBER, false, false, 0, false);
        b.pop();
        caler(b, m0, 0.05f);
        boue(b, 'z', 8, 0, 6.0f, 6.4f, 1.0f, 0);
    }

    /** Housse de fusil matelassée (1,1 m) couchée en travers, dans la poussière et les feuilles. */
    static void housseFusil(MeshBuilder b) {
        int m0 = b.mark();
        b.push().translate(8, 0, 8).rotateY(-45).scale(0.9f);
        b.push().translate(0, 1.15f, 0).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -9.6f}, {0.9f, -9.4f}, {1.3f, -8.4f}, {1.4f, -3}, {1.8f, 2.6f}, {2.1f, 7.0f}, {1.5f, 9.1f}, {0.01f, 9.6f}},
                20, 0, 360, CAMO, 0.8f, 1.85f, 0.02f, 0.4f, 5, false);
        b.pop();
        for (float x = -7.0f; x < 8.0f; x += 1.9f) b.bevelBox(x, 2.0f, -1.5f, x + 0.12f, 2.28f, 1.5f, 0.03f, DARK);
        glissiere(b, -9.0f, 9.0f, 1.2f, 2.85f);
        anse(b, -1.2f, 1.2f, 2.3f, 0, 0.9f, DARK);
        b.tube(new float[][]{p(-9.0f, 0.9f, 2.4f), p(-8.0f, 0.12f, 4.8f), p(0, 0.08f, 5.8f), p(7.6f, 0.12f, 4.6f), p(9.0f, 0.9f, 2.4f)},
                new float[]{0.12f, 0.12f, 0.12f, 0.12f, 0.12f}, 6, DARK, false, false, 0, false);
        b.bevelBox(5.0f, 1.9f, 1.4f, 6.8f, 2.25f, 2.1f, 0.08f, ORANGE);
        b.pop();
        float[] bo = b.bounds(m0);
        b.offset(m0, 8 - (bo[0] + bo[3]) / 2, -bo[1], 8 - (bo[2] + bo[5]) / 2);
        jonchee(b, 1.0f, 1.0f, 15.0f, 15.0f, 10, FEUILLES, 1.3f, 61);
    }

    /** Caisse d'équipement POLICE (60 x 40 x 40 cm) : couvercle forcé de travers, croix de fouille, lampe brisée. */
    static void caissePolice(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.6f, z1 = 7.0f, yc = 5.0f, h = 6.4f;
        b.bevelBox(x0, 0, z0, x1, yc, z1, 0.3f, DARK);
        b.box(x0 + 0.4f, yc - 0.06f, z0 + 0.4f, x1 - 0.4f, yc + 0.06f, z1 - 0.4f, INTERIEUR_SALE);
        b.push().translate(x0, yc, z0).rotateZ(-6).rotateX(-4).translate(-x0, -yc, -z0);
        b.bevelBox(x0 - 0.1f, yc + 0.02f, z0 - 0.1f, x1 + 0.1f, h, z1 + 0.1f, 0.35f, DARK);
        b.bevelBox(x0 + 1.5f, h - 0.02f, z0 + 1.4f, x1 - 1.5f, h + 0.18f, z1 - 1.4f, 0.12f, DARK);
        pochoir(b, S_POLICE, 'y', 8, h + 0.19f, (z0 + z1) / 2, 4.2f, 1.05f);
        b.pop();
        b.bevelBox(x0 - 0.06f, 2.8f, z0 - 0.06f, x1 + 0.06f, 3.6f, z1 + 0.06f, 0.03f, POLICE_BLUE);
        pochoir(b, S_POLICE, 'z', 8, 1.6f, z1 + 0.01f, 4.8f, 1.2f);
        fermoir(b, 4.6f, yc, z1 + 0.08f, STEEL);
        boulon(b, 11.1f, 4.6f, z1, 0.14f);
        boulon(b, 11.7f, 4.6f, z1, 0.14f);
        poigneeU(b, x1 + 0.1f, 4.4f, (z0 + z1) / 2, 2.6f, 1, STEEL);
        poigneeU(b, x0 - 0.1f, 4.4f, (z0 + z1) / 2, 2.6f, -1, STEEL);
        decal(b, CROIX, 'X', x1 + 0.06f, 2.2f, 3.8f, 2.6f, 2.6f);
        boue(b, 'z', 8, 0, z1, 9.0f, 0.9f, 1);
        // lampe torche tombée au sol, verre fêlé
        b.push().translate(6.4f, 0.55f, 9.6f).rotateY(-30).rotateZ(-90);
        b.cylinder(0.4f, 0, 2.0f, 12, DARK, DARK, null);
        b.cylinder(0.55f, 2.0f, 2.8f, 12, DARK, null, CHROME_PIQUE);
        b.pop();
    }

    /** Sac de patrouille POLICE (75 cm) tassé contre le mur, ouvert, papiers éparpillés. */
    static void sacPolice(MeshBuilder b) {
        float zc = 3.0f, yc = 2.4f;
        b.push().translate(8, yc, zc).rotateY(4).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -6.0f}, {1.5f, -5.8f}, {2.3f, -5.2f}, {2.75f, -4.0f}, {2.8f, 0}, {2.75f, 4.0f}, {2.3f, 5.2f}, {1.5f, 5.8f}, {0.01f, 6.0f}},
                24, 0, 360, CORDURA, 0.86f, 1.05f, 0.02f, 0.35f, 9, false);
        b.lathe(new float[][]{{2.95f, -4.4f}, {2.95f, 4.4f}}, 24, 55, 125, POLICE_BLUE, 0.87f, 1.06f, 0, 0, 0, false);
        b.pop();
        pochoir(b, S_POLICE, 'z', 8.2f, yc + 0.15f, zc + 3.32f, 3.6f, 0.9f);
        anse(b, 5.2f, 7.2f, yc + 2.3f, zc - 0.4f, 1.0f, DARK);
        anse(b, 8.8f, 10.8f, yc + 2.3f, zc - 0.4f, 1.0f, DARK);
        glissiere(b, 3.0f, 7.4f, yc + 2.38f, zc + 0.3f);
        b.bevelBox(1.6f, 0.4f, zc - 1.2f, 2.3f, 3.8f, zc + 1.2f, 0.25f, DARK);
        jonchee(b, 3.0f, 6.6f, 13.0f, 12.0f, 7, PAPIER, 1.6f, 71);
        boue(b, 'z', 8, 0, zc + 2.9f, 9.0f, 1.0f, 0);
    }

    /** Armoire à fusils en noyer (65 x 45 x 170 cm) pillée : vitre brisée, deux fusils sur quatre, éclats au sol. */
    static void armoireFusils(MeshBuilder b) {
        float x0 = 2.8f, x1 = 13.2f, z0 = 0.4f, z1 = 7.6f, h = 27.2f;
        b.bevelBox(x0, 0, z0, x1, 1.4f, z1, 0.15f, WALNUT);
        b.bevelBox(x0, 1.4f, z0, x0 + 0.8f, h - 1.2f, z1, 0.1f, WALNUT);
        b.bevelBox(x1 - 0.8f, 1.4f, z0, x1, h - 1.2f, z1, 0.1f, WALNUT);
        b.bevelBox(x0 + 0.8f, 1.4f, z0, x1 - 0.8f, h - 1.2f, z0 + 0.6f, 0.06f, INNER);
        b.bevelBox(x0 - 0.2f, h - 1.18f, z0 - 0.1f, x1 + 0.2f, h, z1 + 0.25f, 0.2f, WALNUT);
        float fz = z1 - 0.38f;
        b.bevelBox(x0 + 0.82f, 1.42f, fz, x0 + 1.7f, h - 1.22f, z1, 0.08f, WALNUT);
        b.bevelBox(x1 - 1.7f, 1.42f, fz, x1 - 0.82f, h - 1.22f, z1, 0.08f, WALNUT);
        b.bevelBox(x0 + 1.72f, 1.42f, fz, x1 - 1.72f, 3.0f, z1, 0.08f, WALNUT);
        b.bevelBox(x0 + 1.72f, h - 2.8f, fz, x1 - 1.72f, h - 1.22f, z1, 0.08f, WALNUT);
        // râtelier : il ne reste que deux fusils sur quatre
        b.bevelBox(x0 + 0.82f, 1.42f, z0 + 0.62f, x1 - 0.82f, 2.4f, z0 + 3.2f, 0.08f, WALNUT);
        b.bevelBox(x0 + 0.82f, 17.0f, z0 + 0.62f, x1 - 0.82f, 17.7f, z0 + 2.0f, 0.08f, WALNUT);
        for (float x : new float[]{5.0f, 9.0f}) {
            b.bevelBox(x - 0.35f, 2.4f, z0 + 1.2f, x + 0.35f, 6.4f, z0 + 2.7f, 0.18f, WALNUT);
            b.bevelBox(x - 0.27f, 6.4f, z0 + 1.4f, x + 0.27f, 10.4f, z0 + 2.3f, 0.1f, DARK);
            b.bevelBox(x - 0.25f, 7.6f, z0 + 2.3f, x + 0.25f, 9.2f, z0 + 2.9f, 0.08f, DARK);
            b.push().translate(x, 10.4f, z0 + 1.85f);
            b.cylinder(0.13f, 0, 7.6f, 8, DARK, null, DARK);
            b.pop();
        }
        // serrure forcée (laiton tordu), poignée
        b.bevelBox(x1 - 1.5f, 13.0f, z1 + 0.02f, x1 - 1.0f, 14.4f, z1 + 0.24f, 0.07f, BRASS);
        b.push().translate(x1 - 1.25f, 12.4f, z1 + 0.02f).rotateX(90);
        b.cylinder(0.25f, 0, 0.16f, 10, BRASS, null, DARK);
        b.pop();
        decal(b, GRIFFURES, 'z', x1 - 1.3f, 11.6f, z1, 2.2f, 1.1f, 80);
        debris(b, 7.6f, 10.0f, 2.6f, 12, WHITE, 77);
        coulure(b, 'X', x1, h - 1.6f, 3.6f, 1.4f, 2);
    }

    /** Vitre de la porte : brisée (trou en étoile, fêlures). */
    static void armoireFusilsVerre(MeshBuilder b) {
        b.face(VERRE_BRISE, v(0, 0, 1), p(4.5f, 3.0f, 7.4f), p(11.5f, 3.0f, 7.4f), p(11.5f, 24.4f, 7.4f), p(4.5f, 24.4f, 7.4f));
    }

    /** Bac à scellés (60 x 40 x 32 cm) ; scelle : ruban jaune (arraché, il pend). */
    static void bac(MeshBuilder b, float x0, float y0, float z0, boolean scelle) {
        float L = 9.6f, W = 6.4f, H = 5.1f, x1 = x0 + L, z1 = z0 + W;
        b.bevelBox(x0 + 0.25f, y0, z0 + 0.25f, x1 - 0.25f, y0 + 2.0f, z1 - 0.25f, 0.3f, PLASTIC_GREY);
        b.bevelBox(x0, y0 + 1.9f, z0, x1, y0 + H - 0.6f, z1, 0.3f, PLASTIC_GREY);
        b.bevelBox(x0 - 0.18f, y0 + H - 0.62f, z0 - 0.18f, (x0 + x1) / 2 + 0.02f, y0 + H, z1 + 0.18f, 0.15f, PLASTIC_GREY);
        b.bevelBox((x0 + x1) / 2 - 0.02f, y0 + H - 0.6f, z0 - 0.17f, x1 + 0.18f, y0 + H - 0.02f, z1 + 0.17f, 0.15f, PLASTIC_GREY);
        for (float x = x0 + 0.9f; x < x1 - 0.8f; x += 1.3f) b.bevelBox(x, y0 + 2.3f, z1 + 0.02f, x + 0.4f, y0 + H - 1.0f, z1 + 0.16f, 0.08f, PLASTIC_GREY);
        if (scelle) {
            b.bevelBox(x0 - 0.22f, y0 + 2.9f, z0 - 0.22f, x1 + 0.22f, y0 + 3.5f, z1 + 0.22f, 0.02f, YELLOW);
            pochoir(b, S_PREUVES, 'z', (x0 + x1) / 2, y0 + 3.2f, z1 + 0.23f, 8.6f, 0.6f);
            // ruban du couvercle coupé : il pend sur le devant
            b.push().translate((x0 + x1) / 2, y0 + H - 0.4f, z1 + 0.25f).rotateX(-12);
            b.box(-0.6f, -2.0f, 0, 0.6f, 0, 0.04f, YELLOW);
            b.pop();
        }
        poigneeU(b, x1 + 0.08f, y0 + 3.8f, (z0 + z1) / 2, 2.0f, 1, PLASTIC_GREY);
        poigneeU(b, x0 - 0.08f, y0 + 3.8f, (z0 + z1) / 2, 2.0f, -1, PLASTIC_GREY);
    }

    /** Deux bacs à scellés empilés contre le mur (scellé coupé), sachet de pièce à conviction éventré dessus. */
    static void caissePreuves(MeshBuilder b) {
        bac(b, 3.2f, 0, 0.6f, true);
        b.push().translate(8, 5.1f, 3.8f).rotateY(-9).translate(-8, -5.1f, -3.8f);
        bac(b, 3.4f, 5.12f, 0.7f, false);
        b.pop();
        b.push().translate(7.0f, 10.25f, 3.6f).rotateY(16);
        b.bevelBox(-1.6f, 0, -1.1f, 1.6f, 0.25f, 1.1f, 0.1f, WHITE);
        b.decalZ(LABEL, 0, 0.12f, 1.16f, 1.6f, 0.2f);
        b.pop();
        pochoir(b, LABEL, 'y', 9.8f, 10.25f, 2.0f, 1.8f, 0.9f);
        decal(b, AUREOLE, 'X', 12.8f, 3.4f, 3.8f, 3.0f, 2.4f);
        boue(b, 'z', 8, 0, 7.0f, 9.0f, 1.2f, 1);
    }

    /** Gilet pare-balles troué de balles posé sur une caisse, pan avant qui retombe sur le devant. */
    static void giletCaisse(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.6f, z1 = 7.0f, h = 5.6f;
        b.bevelBox(x0, 0, z0, x1, h - 0.7f, z1, 0.3f, DARK);
        b.bevelBox(x0 - 0.12f, h - 0.72f, z0 - 0.12f, x1 + 0.12f, h, z1 + 0.12f, 0.2f, DARK);
        for (float x : new float[]{4.6f, 11.4f}) fermoir(b, x, h - 0.7f, z1 + 0.14f, STEEL);
        // gilet : dos à plat sur le couvercle, épaulières, pan avant qui pend
        float y = h + 0.02f;
        b.bevelBox(4.0f, y, 1.0f, 12.0f, y + 0.85f, 6.6f, 0.38f, CORDURA);
        for (float x : new float[]{4.6f, 10.0f}) b.bevelBox(x, y + 0.6f, 0.6f, x + 1.4f, y + 1.15f, 2.2f, 0.25f, CORDURA);
        decal(b, IMPACTS, 'y', 8.6f, y + 0.85f, 3.4f, 4.4f, 2.2f, 12);
        b.push().translate(0, y + 0.4f, 7.0f).rotateX(80);
        b.bevelBox(4.2f, -0.35f, 0, 11.8f, 0.45f, 4.6f, 0.35f, CORDURA);
        b.push().rotateX(-90);
        b.decalZ(S_POLICE, 8, -2.3f, 0.52f, 4.4f, 1.1f);
        b.pop();
        b.pop();
        for (float z : new float[]{2.4f, 4.6f}) b.bevelBox(3.6f, y + 0.25f, z, 12.4f, y + 0.95f, z + 0.5f, 0.08f, DARK);
        b.bevelBox(4.6f, y + 0.84f, 4.4f, 7.4f, y + 0.9f, 5.0f, 0.02f, POLICE_BLUE);
        boue(b, 'X', x1, 0, 3.8f, 6.0f, 1.2f, 0);
    }

    /** Sac à dos de chasse (35 x 22 x 55 cm) appuyé de biais contre le mur, au bord du bloc ; boue, feuilles. */
    static void sacChasse(MeshBuilder b) {
        int m0 = b.mark();
        b.push().translate(10.0f, 0, 2.6f).rotateY(-12).rotateX(-14);
        b.bevelBox(-2.8f, 0, -1.7f, 2.8f, 8.4f, 1.7f, 1.2f, CAMO);
        b.bevelBox(-2.2f, 0.7f, 1.4f, 2.2f, 4.6f, 2.8f, 0.8f, CAMO);
        b.bevelBox(-2.9f, 7.0f, -1.5f, 2.9f, 9.0f, 2.2f, 0.8f, CAMO);
        for (float x : new float[]{-1.9f, 1.9f}) b.bevelBox(x - 0.25f, 4.4f, 1.95f, x + 0.25f, 7.6f, 2.25f, 0.08f, DARK);
        for (float x : new float[]{-1.9f, 1.9f}) b.bevelBox(x - 0.42f, 4.3f, 2.15f, x + 0.42f, 4.85f, 2.4f, 0.08f, STEEL);
        b.bevelBox(-1.6f, 2.4f, 2.76f, 1.6f, 3.0f, 2.84f, 0.03f, ORANGE);
        b.push().translate(2.8f, 3.6f, 0).rotateZ(-90);
        b.cylinder(0.75f, 0, 0.7f, 12, CAMO, null, CAMO);
        b.pop();
        b.decalZ(S_CHASSE, 0, 5.6f, 1.78f, 3.0f, 0.75f);
        b.pop();
        caler(b, m0, 0.05f);
        jonchee(b, 4.0f, 5.0f, 15.0f, 11.0f, 6, FEUILLES, 1.2f, 81);
    }

    /** Coffre de chasseur en noyer (90 x 50 x 50 cm) contre le mur, ramure posée dessus ; sangle cassée, serrure rouillée. */
    static void coffreChasse(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.6f, z1 = 8.6f, yc = 6.2f, h = 8.0f;
        b.bevelBox(x0, 0.5f, z0, x1, yc, z1, 0.2f, WALNUT);
        b.bevelBox(x0 - 0.14f, yc + 0.02f, z0 - 0.14f, x1 + 0.14f, h, z1 + 0.14f, 0.28f, WALNUT);
        b.bevelBox(3.0f, 0.45f, z1 + 0.02f, 3.7f, h + 0.06f, z1 + 0.2f, 0.04f, DARK);
        b.bevelBox(3.0f, h + 0.02f, z0 - 0.1f, 3.7f, h + 0.1f, z1 + 0.2f, 0.03f, DARK);
        // sangle droite coupée : un bout pend
        b.bevelBox(12.2f, 0.45f, z1 + 0.02f, 12.9f, 3.0f, z1 + 0.2f, 0.04f, DARK);
        b.push().translate(12.55f, h + 0.1f, z1 + 0.2f).rotateX(170);
        b.bevelBox(-0.35f, 0, -0.06f, 0.35f, 2.2f, 0.04f, 0.04f, DARK);
        b.pop();
        b.bevelBox(12.2f, h + 0.02f, z0 - 0.1f, 12.9f, h + 0.1f, z1 + 0.2f, 0.03f, DARK);
        coins(b, x0, 0.5f, z0, x1, h, z1, 0.9f, BRASS);
        b.bevelBox(7.35f, yc - 1.4f, z1 + 0.1f, 8.65f, yc + 0.8f, z1 + 0.32f, 0.08f, BRASS);
        coulure(b, 'z', 8.0f, yc - 1.5f, z1 + 0.1f, 1.0f, 1);
        for (float[] q : new float[][]{{1.8f, 1.6f}, {14.2f, 1.6f}, {1.8f, 7.6f}, {14.2f, 7.6f}}) pied(b, q[0], q[1], 0.5f, 0.5f, DARK);
        pochoir(b, S_CHASSE, 'z', 8, 3.2f, z1 + 0.03f, 4.8f, 1.2f);
        decal(b, AUREOLE, 'y', 5.6f, h, 3.0f, 3.2f, 3.0f);
        float ay = h + 0.2f, az = 5.0f;
        b.bevelBox(6.8f, h + 0.02f, az - 0.7f, 9.2f, h + 0.55f, az + 0.7f, 0.22f, WALNUT);
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{p(8 + s * 0.8f, ay + 0.3f, az), p(8 + s * 3.0f, ay + 1.4f, az - 0.5f), p(8 + s * 5.0f, ay + 1.0f, az - 2.2f)}, new float[]{0.28f, 0.2f, 0.08f}, 7, BONE, true, true, 0, false);
            b.tube(new float[][]{p(8 + s * 2.1f, ay + 1.0f, az - 0.3f), p(8 + s * 2.5f, ay + 2.6f, az + 0.3f)}, new float[]{0.15f, 0.05f}, 6, BONE, false, true, 0, false);
            b.tube(new float[][]{p(8 + s * 3.7f, ay + 1.4f, az - 1.0f), p(8 + s * 4.3f, ay + 2.7f, az - 0.9f)}, new float[]{0.13f, 0.04f}, 6, BONE, false, true, 0, false);
        }
    }
}
