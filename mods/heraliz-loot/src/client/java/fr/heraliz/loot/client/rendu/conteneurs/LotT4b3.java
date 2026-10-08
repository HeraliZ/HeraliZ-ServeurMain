package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T4b, suite : râtelier de campagne, caisse d'obus, poste radio, caisse d'explosifs. */
final class LotT4b3 {
    private LotT4b3() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t4_ratelier_campagne", "t4", "bois", "Râtelier de campagne", "Field rifle rack", LotT4b3::ratelier);
        Conteneurs.ajouter("t4_caisse_obus", "t4", "bois", "Caisse d'obus", "Mortar shell crate", LotT4b3::caisseObus);
        Conteneurs.ajouter("t4_poste_radio", "t4", "metal", "Poste radio de campagne", "Field radio post", LotT4b3::posteRadio);
        Conteneurs.ajouter("t4_caisse_explosifs", "t4", "bois", "Caisse d'explosifs", "Explosives crate", LotT4b3::caisseExplosifs);
        LotT4b4.enregistrer();
    }

    static final Mat BOIS_ARME = AtlasCivil.WALNUT, ACIER_NOIR = AtlasLoot.BLACK_STEEL;

    /** Plaque profilée (contour (z, y) convexe, épaisseur e le long de X), centrée en x = 0. */
    static void profil(MeshBuilder b, float[][] zy, float e, float biseau, Mat m) {
        float[][] o = new float[zy.length][];
        for (int i = 0; i < zy.length; i++) o[i] = new float[]{-zy[i][0], zy[i][1]};
        b.push().rotateY(90);
        b.slab(o, e, biseau, false, m, m, m);
        b.pop();
    }

    /**
     * Fusil de guerre générique (1 m, monture bois) le long de +Y local, de la crosse (y = 0) à la bouche (y = 16) ;
     * le dessus (hausse) vers -Z, le chargeur et la poignée vers +Z.
     */
    static void fusil(MeshBuilder b, boolean bretelle) {
        profil(b, new float[][]{{-0.2f, 0}, {1.95f, 0}, {0.95f, 4.7f}, {-0.05f, 4.7f}}, 0.62f, 0.12f, BOIS_ARME);
        b.bevelBox(-0.34f, -0.04f, -0.22f, 0.34f, 0.32f, 1.98f, 0.06f, AtlasLoot.RUBBER);
        profil(b, new float[][]{{0.7f, 4.4f}, {0.7f, 5.5f}, {2.15f, 5.15f}, {2.15f, 4.35f}}, 0.56f, 0.1f, BOIS_ARME);
        b.bevelBox(-0.3f, 4.6f, -0.3f, 0.3f, 8.7f, 0.78f, 0.1f, ACIER_NOIR);
        b.bevelBox(-0.2f, 7.3f, -0.62f, 0.2f, 8.2f, -0.28f, 0.06f, ACIER_NOIR);
        b.bevelBox(-0.12f, 5.7f, -0.55f, 0.12f, 6.1f, -0.28f, 0.04f, ACIER_NOIR);
        b.push().translate(0, 7.1f, 0.75f).rotateX(12);
        b.bevelBox(-0.24f, -0.55f, 0, 0.24f, 0.55f, 2.3f, 0.08f, ACIER_NOIR);
        b.pop();
        b.push().translate(0, 5.95f, 1.15f).rotateY(90);
        b.torus(0.42f, 0.06f, 8, 3, 180, 360, ACIER_NOIR);
        b.pop();
        b.bevelBox(-0.33f, 8.65f, -0.12f, 0.33f, 12.1f, 0.88f, 0.14f, BOIS_ARME);
        b.push().translate(0, 12.0f, 0.3f);
        b.cylinder(0.15f, 0, 3.5f, 8, ACIER_NOIR, null, null);
        b.cylinder(0.2f, 3.5f, 4.05f, 8, ACIER_NOIR, null, ACIER_NOIR);
        b.pop();
        b.bevelBox(-0.16f, 14.0f, -0.4f, 0.16f, 14.5f, 0.45f, 0.04f, ACIER_NOIR);
        b.bevelBox(-0.07f, 14.15f, -0.75f, 0.07f, 14.35f, -0.38f, 0.02f, ACIER_NOIR);
        b.color(0.9f, 0.7f, 0.6f);
        b.bevelBox(-0.36f, 5.0f, 0.3f, 0.36f, 5.6f, 0.84f, 0.04f, ROUILLE);
        b.color(1);
        if (bretelle) {
            b.tube(new float[][]{p(0, 11.6f, 0.95f), p(0.1f, 9.0f, 2.2f), p(0.15f, 5.5f, 3.0f), p(0.1f, 2.6f, 2.6f), p(0, 1.1f, 2.0f)}, fill(5, 0.14f), 5, SANGLE, false, false, 0, false);
        }
    }

    // =================================================================== 9. râtelier de campagne
    /**
     * Râtelier d'armes de campagne (1,2 m, planches clouées) sur deux blocs : deux fusils appuyés, un emplacement vide
     * (bretelle restée accrochée), chargeur tombé sur la semelle ; contrefiche rafistolée au ruban, boue.
     */
    static void ratelier(MeshBuilder b) {
        float xa = 6.4f, xb = 25.6f;
        for (float x : new float[]{xa, xb - 0.9f}) {
            b.bevelBox(x, 0, 0.6f, x + 0.9f, 13.4f, 1.5f, 0.1f, BOIS_KAKI);
            b.bevelBox(x, 0, 1.45f, x + 0.9f, 0.9f, 5.65f, 0.1f, BOIS_KAKI);
            b.push().translate(x + 0.45f, 0.5f, 8.4f).rotateX(-41.8f);
            b.bevelBox(-0.42f, 0, -0.32f, 0.42f, 11.0f, 0.32f, 0.08f, BOIS_KAKI);
            b.pop();
        }
        b.bevelBox(xa - 0.3f, 12.0f, 1.48f, xb + 0.3f, 12.85f, 2.38f, 0.1f, BOIS_KAKI);
        b.bevelBox(xa + 0.95f, 0, 5.6f, xb - 0.95f, 1.0f, 9.4f, 0.1f, BOIS_KAKI);
        float[] fentes = {11.0f, 16.0f, 21.0f};
        for (float x : new float[]{xa + 1.0f, 13.5f, 18.5f, xb - 1.9f}) {
            b.bevelBox(x, 0.98f, 5.7f, x + 0.9f, 1.85f, 9.3f, 0.08f, BOIS_KAKI);
            b.bevelBox(x, 12.6f, 2.36f, x + 0.9f, 13.6f, 2.95f, 0.08f, BOIS_KAKI);
        }
        for (float x : new float[]{xa + 0.45f, xb - 0.45f}) {
            for (float y : new float[]{12.3f, 12.6f}) boulon(b, x, y, 2.38f, 0.1f);
        }
        b.push().translate(xb - 0.45f, 0.5f, 8.4f).rotateX(-41.8f);
        b.color(0.85f);
        for (float y : new float[]{5.0f, 5.5f, 5.95f}) b.bevelBox(-0.47f, y, -0.37f, 0.47f, y + 0.42f, 0.37f, 0.02f, SCOTCH);
        b.color(1);
        b.pop();
        for (int k = 0; k < 3; k++) {
            if (k == 1) continue;
            b.push().translate(fentes[k], 1.0f, 7.2f).rotateY(k == 0 ? 6 : -4).rotateX(-20.8f).rotateZ(k == 0 ? 1.5f : -1.0f);
            fusil(b, true);
            b.pop();
        }
        b.tube(new float[][]{p(16.0f, 12.9f, 2.4f), p(16.2f, 11.6f, 2.9f), p(16.3f, 9.2f, 3.0f), p(15.9f, 7.6f, 2.7f), p(15.6f, 9.5f, 2.6f), p(15.7f, 12.6f, 2.45f)},
                fill(6, 0.13f), 5, SANGLE, false, false, 0, false);
        b.push().translate(17.0f, 1.24f, 7.8f).rotateY(24).rotateZ(90);
        b.bevelBox(-0.24f, -1.15f, -0.5f, 0.24f, 1.15f, 0.5f, 0.08f, ACIER_NOIR);
        b.pop();
        pochoir(b, S_MATRICULE, 'z', 16.0f, 0.5f, 9.45f, 3.2f, 0.8f);
        boue(b, 'z', 16.0f, 0, 9.4f, 16.0f, 0.9f, 1);
        coulure(b, 'z', xa + 0.45f, 12.2f, 2.38f, 0.6f, 0);
        coulure(b, 'z', xb - 0.45f, 12.2f, 2.38f, 0.6f, 2);
        b.push().translate(26.0f, 0, 12.0f).rotateY(30);
        b.cylinder(0.17f, 0, 0.18f, 7, AtlasLoot.BRASS, null, AtlasLoot.BRASS);
        b.pop();
    }

    // =================================================================== 10. caisse d'obus
    /** Obus de mortier de 81 mm (environ 50 cm) le long de +Y local, de l'empennage (y = 0) à la fusée. */
    static void obus(MeshBuilder b) {
        b.cylinder(0.32f, 0, 2.3f, 8, ACIER_NOIR, ACIER_NOIR, null);
        for (int k = 0; k < 4; k++) {
            b.push().rotateY(45 + k * 90);
            b.bevelBox(0.25f, 0.05f, -0.035f, 0.66f, 1.25f, 0.035f, 0.02f, ACIER_NOIR);
            b.pop();
        }
        b.lathe(new float[][]{{0.32f, 2.2f}, {0.5f, 2.45f}, {0.64f, 3.0f}, {0.65f, 4.4f}, {0.6f, 5.3f}, {0.44f, 6.1f}, {0.32f, 6.45f}}, 12, 0, 360, ACIER, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.655f, 4.55f}, {0.655f, 4.95f}}, 12, 0, 360, JAUNE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.32f, 6.43f}, {0.3f, 6.9f}, {0.22f, 7.5f}, {0.1f, 7.85f}, {0.01f, 7.9f}}, 10, 0, 360, AtlasLoot.STEEL, 1, 1, 0, 0, 0, false);
    }

    /**
     * Caisse d'obus de mortier ouverte (70 x 45 x 24 cm), couvercle relevé contre le mur : quatre logements, trois obus,
     * un manquant ; coiffe de fusée tombée au sol, fermoirs ouverts.
     */
    static void caisseObus(MeshBuilder b) {
        float x0 = 2.4f, x1 = 13.6f, z0 = 1.3f, z1 = 8.3f, h = 3.8f, e = 0.45f, lt = 0.55f;
        for (float x : new float[]{x0 + 1.0f, x1 - 2.0f}) b.bevelBox(x, 0, z0 + 0.1f, x + 1.0f, 0.42f, z1 - 0.1f, 0.06f, BOIS_KAKI);
        float yb = 0.4f;
        b.bevelBox(x0, yb, z1 - e, x1, h, z1, 0.1f, BOIS_OLIVE);
        b.bevelBox(x0, yb, z0, x1, h, z0 + e, 0.1f, BOIS_OLIVE);
        b.bevelBox(x0, yb, z0 + e - 0.02f, x0 + e, h, z1 - e + 0.02f, 0.1f, BOIS_OLIVE);
        b.bevelBox(x1 - e, yb, z0 + e - 0.02f, x1, h, z1 - e + 0.02f, 0.1f, BOIS_OLIVE);
        b.box6(x0 + e, yb, z0 + e, x1 - e, yb + 0.4f, z1 - e, null, null, null, INTERIEUR_SALE, null, null);
        float[] zs = {z0 + 1.35f, z0 + 2.95f, z0 + 4.55f, z1 - 1.25f};
        for (float x : new float[]{x0 + 2.2f, x1 - 3.0f}) {
            b.bevelBox(x, yb + 0.38f, z0 + e, x + 0.9f, yb + 0.9f, z1 - e, 0.06f, BOIS_KAKI);
            for (int k = 0; k <= 4; k++) {
                float za = k == 0 ? z0 + e : (zs[k - 1] + 0.66f), zb = k == 4 ? z1 - e : (zs[k] - 0.66f);
                if (zb - za > 0.1f) b.bevelBox(x, yb + 0.88f, za, x + 0.9f, yb + 1.65f, zb, 0.06f, BOIS_KAKI);
            }
        }
        for (int k = 0; k < 4; k++) {
            if (k == 2) continue;
            b.push().translate(x0 + 0.95f + (k % 2) * 0.15f, yb + 1.55f, zs[k]).rotateX(k * 37).rotateZ(-90);
            obus(b);
            b.pop();
        }
        for (float x : new float[]{4.6f, 11.4f}) {
            b.bevelBox(x - 0.5f, h - 1.0f, z1 + 0.02f, x + 0.5f, h - 0.1f, z1 + 0.22f, 0.05f, AtlasLoot.OD_DARK);
            b.push().translate(x, h - 0.3f, z1 + 0.26f).rotateX(-160);
            b.bevelBox(-0.3f, 0, -0.08f, 0.3f, 1.2f, 0.08f, 0.04f, AtlasLoot.STEEL);
            b.pop();
        }
        Kit.poigneeCorde(b, x1 + 0.05f, 2.2f, (z0 + z1) / 2, 2.6f, 1);
        Kit.poigneeCorde(b, x0 - 0.05f, 2.2f, (z0 + z1) / 2, 2.6f, -1);
        pochoir(b, S_OBUS, 'z', 8.0f, 1.65f, z1, 7.6f, 1.15f);
        pochoir(b, S_LOT, 'X', x1, 3.2f, (z0 + z1) / 2, 4.0f, 0.55f);
        boue(b, 'z', 8.0f, yb, z1, 10.4f, 1.0f, 0);
        coulure(b, 'z', 4.6f, h - 1.0f, z1, 0.7f, 1);
        decal(b, IMPACTS, 'x', x0, 2.1f, (z0 + z1) / 2, 2.6f, 1.3f);
        b.push().translate(0, h, z0).rotateX(-95).translate(0, -h, -z0);
        b.bevelBox(x0 - 0.05f, h + 0.02f, z0 - 0.05f, x1 + 0.05f, h + lt, z1 + 0.05f, 0.1f, BOIS_OLIVE);
        for (float x : new float[]{x0 + 1.4f, x1 - 2.6f}) b.bevelBox(x, h + lt - 0.02f, z0 + 0.3f, x + 1.2f, h + lt + 0.32f, z1 - 0.3f, 0.06f, BOIS_OLIVE);
        b.push().translate(8.0f, h - 0.04f, (z0 + z1) / 2 + 0.4f).rotateX(90).rotateZ(4);
        b.decalZ(PAPIER, 0, 0, 0, 3.0f, 3.8f);
        b.pop();
        b.pop();
        for (float x : new float[]{4.2f, 11.8f}) b.bevelBox(x - 0.4f, h - 0.6f, z0 - 0.12f, x + 0.4f, h + 0.06f, z0 + 0.08f, 0.04f, AtlasLoot.STEEL);
        b.push().translate(13.4f, 0.32f, 10.8f).rotateY(30).rotateZ(90);
        b.cylinder(0.32f, -0.4f, 0.4f, 10, AtlasLoot.RUBBER, AtlasLoot.RUBBER, AtlasLoot.RUBBER);
        b.pop();
    }

    // =================================================================== 11. poste radio
    /**
     * Poste radio de campagne sur une table pliante (80 x 45 x 70 cm) : combiné qui pend au bout de son cordon spiralé,
     * antenne démontée en trois brins sur la table, câble vers une batterie posée au sol ; carte, carnet, quart en alu.
     */
    static void posteRadio(MeshBuilder b) {
        float tx0 = 1.6f, tx1 = 14.4f, tz0 = 0.6f, tz1 = 7.8f, ty = 11.2f, ep = 0.5f;
        b.bevelBox(tx0, ty - ep, tz0, tx1, ty, tz1, 0.1f, BOIS_OLIVE);
        b.bevelBox(tx0 + 0.3f, ty - ep - 0.6f, tz1 - 0.6f, tx1 - 0.3f, ty - ep + 0.02f, tz1 - 0.35f, 0.05f, ACIER);
        b.bevelBox(tx0 + 0.3f, ty - ep - 0.6f, tz0 + 0.35f, tx1 - 0.3f, ty - ep + 0.02f, tz0 + 0.6f, 0.05f, ACIER);
        for (float x : new float[]{tx0 + 0.75f, tx1 - 0.75f}) {
            b.tube(new float[][]{p(x, 0.18f, tz0 + 0.5f), p(x, ty - ep - 0.1f, tz1 - 0.9f)}, fill(2, 0.2f), 8, ACIER, true, true, 0, false);
            b.tube(new float[][]{p(x, 0.18f, tz1 - 0.5f), p(x, ty - ep - 0.1f, tz0 + 0.9f)}, fill(2, 0.2f), 8, ACIER, true, true, 0, false);
            for (float z : new float[]{tz0 + 0.5f, tz1 - 0.5f}) {
                b.push().translate(x, 0, z);
                b.cylinder(0.3f, 0, 0.2f, 8, AtlasLoot.RUBBER, null, AtlasLoot.RUBBER);
                b.pop();
            }
        }
        b.tube(new float[][]{p(tx0 + 0.75f, 5.25f, (tz0 + tz1) / 2), p(tx1 - 0.75f, 5.25f, (tz0 + tz1) / 2)}, fill(2, 0.14f), 6, ACIER, false, false, 0, false);
        decal(b, GRIFFURES, 'z', 5.0f, ty - 0.25f, tz1, 2.4f, 0.45f);
        b.push().translate(10.8f, ty + 0.03f, 4.6f).rotateY(-12).rotateX(-90);
        b.decalZ(CARTE, 0, 0, 0, 5.4f, 4.6f);
        b.pop();
        b.color(0.6f);
        decal(b, AUREOLE, 'y', 12.0f, ty + 0.04f, 5.6f, 1.6f, 1.6f);
        b.color(1);
        b.push().translate(9.2f, ty, 6.6f).rotateY(18);
        b.bevelBox(-0.9f, 0.01f, -0.65f, 0.9f, 0.3f, 0.65f, 0.06f, AtlasLoot.OD_DARK);
        b.bevelBox(-0.85f, 0.29f, -0.6f, 0.85f, 0.33f, 0.6f, 0.01f, PAPIER);
        b.pop();
        b.push().translate(11.6f, ty + 0.1f, 6.9f).rotateY(-40).rotateZ(90);
        b.cylinder(0.08f, -0.9f, 0.9f, 6, JAUNE, AtlasLoot.DARK, JAUNE);
        b.pop();
        b.push().translate(13.2f, ty + 0.01f, 2.4f);
        b.lathe(new float[][]{{0.6f, 0}, {0.62f, 1.1f}, {0.66f, 1.15f}}, 12, 0, 360, ALU, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.6f, 1.12f}, {0.56f, 0.12f}}, 12, 0, 360, ALU, 1, 1, 0, 0, 0, true);
        b.disc(0.56f, 0.1f, 12, INTERIEUR_SALE, true);
        b.tube(new float[][]{p(0.6f, 0.95f, 0), p(1.05f, 0.85f, 0), p(1.05f, 0.35f, 0), p(0.6f, 0.3f, 0)}, fill(4, 0.07f), 4, ALU, false, false, 0, false);
        b.pop();
        // poste : caisson, face avant sérigraphiée, boutons, poignée, plaque
        float rx0 = 3.0f, rx1 = 7.8f, rz0 = 1.4f, rz1 = 4.6f, ry0 = ty, ry1 = ty + 4.3f;
        b.bevelBox(rx0, ry0 + 0.01f, rz0, rx1, ry0 + 1.1f, rz1, 0.15f, AtlasLoot.OD_DARK);
        b.bevelBox(rx0 + 0.05f, ry0 + 1.08f, rz0 + 0.05f, rx1 - 0.05f, ry1, rz1 - 0.05f, 0.22f, ACIER);
        float pcx = (rx0 + rx1) / 2, pcy = ry0 + 1.1f + (ry1 - ry0 - 1.1f) / 2, pw = 4.3f, ph = 3.0f;
        b.decalZ(RADIO_FACE, pcx, pcy, rz1, pw, ph);
        for (int k = -1; k <= 1; k++) {
            b.push().translate(pcx + k * pw * 0.28f, pcy + ph / 2 - 0.61f * ph, rz1 - 0.02f).rotateX(90);
            b.cylinder(0.32f, 0, 0.3f, 10, AtlasLoot.DARK, null, AtlasLoot.DARK);
            b.cylinder(0.14f, 0.3f, 0.38f, 6, AtlasLoot.STEEL, null, AtlasLoot.STEEL);
            b.pop();
        }
        b.tube(new float[][]{p(rx0 + 0.6f, ry1 - 0.05f, 3.0f), p(rx0 + 0.9f, ry1 + 0.55f, 3.0f), p(rx1 - 0.9f, ry1 + 0.55f, 3.0f), p(rx1 - 0.6f, ry1 - 0.05f, 3.0f)},
                fill(4, 0.13f), 6, AtlasLoot.STEEL, false, false, 0, false);
        pochoir(b, PLAQUE, 'X', rx1 - 0.05f, ry0 + 2.8f, 3.0f, 1.6f, 1.6f);
        pochoir(b, S_MATRICULE, 'z', pcx, ry0 + 0.55f, rz1, 2.6f, 0.6f);
        coulure(b, 'X', rx1 - 0.05f, ry1 - 0.3f, 2.0f, 0.5f, 0);
        b.push().translate(rx1 - 0.7f, ry1, rz0 + 0.8f);
        b.cylinder(0.28f, 0, 0.5f, 8, AtlasLoot.DARK, null, AtlasLoot.STEEL);
        b.pop();
        for (int k = 0; k < 3; k++) {
            b.push().translate(4.0f + k * 0.3f, ty + 0.09f, 6.4f + k * 0.25f).rotateY(-6 + k * 4).rotateZ(-90);
            b.cylinder(0.08f, -3.0f, 3.0f, 6, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR);
            b.pop();
        }
        // combiné qui pend sous le bord avant, cordon spiralé
        float hx = 6.2f, hy = 7.4f, hz = tz1 + 0.75f;
        b.push().translate(hx, hy, hz).rotateZ(8).rotateX(10);
        b.bevelBox(-0.32f, -1.7f, -0.3f, 0.32f, 1.7f, 0.3f, 0.15f, AtlasLoot.DARK);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(0, s * 1.75f, 0.2f).rotateX(90);
            b.cylinder(0.55f, -0.1f, 0.45f, 12, AtlasLoot.DARK, AtlasLoot.DARK, AtlasLoot.RUBBER);
            b.pop();
        }
        b.bevelBox(-0.14f, 0.2f, -0.4f, 0.14f, 0.8f, -0.25f, 0.04f, AtlasLoot.STEEL);
        b.pop();
        float[][] spire = new float[40][];
        float[] a = p(pcx - pw * 0.4f, ry0 + 1.5f, rz1 + 0.1f), m1 = p(5.2f, ty + 0.4f, tz1 + 0.3f), c = p(hx + 0.1f, hy - 1.9f, hz - 0.2f);
        for (int k = 0; k < 40; k++) {
            float t = k / 39f, u = 1 - t;
            float[] q = p(u * u * a[0] + 2 * u * t * m1[0] + t * t * c[0], u * u * a[1] + 2 * u * t * m1[1] + t * t * c[1], u * u * a[2] + 2 * u * t * m1[2] + t * t * c[2]);
            if (k > 3 && k < 37) {
                double ang = k * 1.6;
                q = p(q[0] + 0.16f * (float) Math.cos(ang), q[1], q[2] + 0.16f * (float) Math.sin(ang));
            }
            spire[k] = q;
        }
        b.tube(spire, fill(40, 0.055f), 4, AtlasLoot.DARK, false, false, 0, false);
        // câble d'alimentation vers la batterie sous la table
        b.tube(new float[][]{p(rx1 - 0.5f, ry0 + 0.6f, rz0 - 0.05f), p(rx1 + 0.2f, ry0 + 0.3f, tz0 + 0.3f), p(9.2f, ty + 0.1f, tz0 + 0.1f), p(9.6f, ty - 1.4f, tz0 + 0.15f),
                p(9.8f, 5.0f, tz0 + 0.5f), p(10.0f, 2.6f, 2.0f), p(10.4f, 2.3f, 2.6f)}, fill(7, 0.1f), 5, AtlasLoot.DARK, false, false, 0, false);
        b.bevelBox(8.6f, 0, 1.4f, 13.2f, 2.3f, 4.4f, 0.18f, ACIER);
        b.bevelBox(8.8f, 2.28f, 1.6f, 13.0f, 2.5f, 4.2f, 0.1f, AtlasLoot.OD_DARK);
        for (float x : new float[]{10.4f, 11.6f}) {
            b.push().translate(x, 2.48f, 2.6f);
            b.cylinder(0.22f, 0, 0.3f, 8, AtlasLoot.BRASS, null, AtlasLoot.BRASS);
            b.pop();
        }
        pochoir(b, S_LOT, 'z', 10.9f, 1.2f, 4.45f, 4.0f, 0.5f);
        boue(b, 'z', 10.9f, 0, 4.4f, 4.4f, 1.0f, 1);
    }

    // =================================================================== 12. caisse d'explosifs
    /**
     * Caisse d'explosifs (65 x 40 x 35 cm) cadenassée : bande orange, losange de danger, feuillards (l'un sectionné et
     * relevé), traces de levier autour du moraillon ; dessus, une boîte de détonateurs et un rouleau de cordeau.
     */
    static void caisseExplosifs(MeshBuilder b) {
        float x0 = 2.6f, x1 = 13.0f, z0 = 0.6f, z1 = 7.0f, h = 5.6f, yc = h - 1.0f;
        b.color(0.8f, 0.8f, 0.78f);
        for (float x : new float[]{x0 + 1.2f, x1 - 2.2f}) b.bevelBox(x, 0, z0 + 0.15f, x + 1.0f, 0.42f, z1 - 0.15f, 0.06f, BOIS_KAKI);
        b.bevelBox(x0, 0.4f, z0, x1, yc, z1, 0.12f, BOIS_OLIVE);
        b.bevelBox(x0 - 0.06f, yc + 0.02f, z0, x1 + 0.06f, h, z1 + 0.06f, 0.12f, BOIS_OLIVE);
        b.color(1);
        b.bevelBox(x0 - 0.04f, 2.9f, z0 + 0.06f, x1 + 0.04f, 3.95f, z1 + 0.04f, 0.03f, ORANGE);
        pochoir(b, S_EXPLOSIFS, 'z', 4.95f, 3.42f, z1 + 0.04f, 4.3f, 0.8f);
        pochoir(b, S_DANGER, 'z', 10.9f, 3.42f, z1 + 0.04f, 3.4f, 0.85f);
        pochoir(b, LOSANGE, 'z', 4.4f, 1.6f, z1, 2.3f, 2.3f);
        pochoir(b, LOSANGE, 'X', x1, 1.65f, (z0 + z1) / 2, 2.2f, 2.2f);
        pochoir(b, S_CODE, 'z', 9.0f, 1.55f, z1, 3.6f, 0.6f);
        pochoir(b, S_EXPLOSIFS, 'X', x1 + 0.04f, 3.42f, (z0 + z1) / 2, 4.6f, 0.82f);
        // feuillards : l'un en place, l'autre sectionné et relevé
        b.bevelBox(6.3f, h - 0.02f, z0 + 0.02f, 6.8f, h + 0.06f, z1 + 0.14f, 0.01f, AtlasLoot.STEEL);
        b.bevelBox(6.3f, 0.42f, z1 + 0.08f, 6.8f, h + 0.06f, z1 + 0.16f, 0.01f, AtlasLoot.STEEL);
        b.bevelBox(11.0f, h - 0.02f, z0 + 0.02f, 11.5f, h + 0.06f, z1 - 1.6f, 0.01f, AtlasLoot.STEEL);
        b.push().translate(11.25f, h + 0.02f, z1 - 1.6f).rotateX(-35);
        b.bevelBox(-0.25f, 0, 0, 0.25f, 0.08f, 1.6f, 0.01f, AtlasLoot.STEEL);
        b.pop();
        b.bevelBox(11.0f, 0.42f, z1 + 0.08f, 11.5f, 2.4f, z1 + 0.16f, 0.01f, AtlasLoot.STEEL);
        b.push().translate(11.25f, 2.4f, z1 + 0.12f).rotateX(-30);
        b.bevelBox(-0.25f, 0, -0.04f, 0.25f, 0.9f, 0.04f, 0.01f, AtlasLoot.STEEL);
        b.pop();
        // moraillon et cadenas, traces de levier, poignées de corde
        b.bevelBox(7.3f, yc - 1.1f, z1 + 0.02f, 8.7f, yc + 0.7f, z1 + 0.22f, 0.06f, AtlasLoot.STEEL);
        Kit.cadenas(b, 8.0f, yc - 0.3f, z1 + 0.24f);
        decal(b, GRIFFURES, 'z', 5.3f, yc + 0.2f, z1, 1.6f, 0.8f, 12);
        decal(b, GRIFFURES, 'z', 9.6f, yc - 0.5f, z1, 1.6f, 0.7f, -20);
        Kit.poigneeCorde(b, x1 + 0.05f, 2.0f, (z0 + z1) / 2, 2.6f, 1);
        Kit.poigneeCorde(b, x0 - 0.05f, 2.0f, (z0 + z1) / 2, 2.6f, -1);
        coulure(b, 'z', 6.55f, yc, z1 + 0.16f, 0.6f, 0);
        boue(b, 'z', 7.8f, 0.4f, z1, 10.0f, 1.3f, 0);
        boue(b, 'x', x0, 0.4f, (z0 + z1) / 2, 6.0f, 1.2f, 1);
        // boîte de détonateurs (acier, bande rouge) posée de travers sur le couvercle
        b.push().translate(6.2f, h + 0.06f, 3.4f).rotateY(-14);
        b.bevelBox(-2.0f, 0, -1.3f, 2.0f, 1.6f, 1.3f, 0.14f, ACIER);
        b.bevelBox(-2.05f, 1.6f, -1.35f, 2.05f, 1.95f, 1.35f, 0.1f, ACIER);
        b.color(0.75f, 0.3f, 0.25f);
        b.bevelBox(-2.03f, 0.55f, -1.33f, 2.03f, 0.85f, 1.33f, 0.02f, PEINTURE_USEE);
        b.color(1);
        pochoir(b, LOSANGE, 'z', -1.1f, 1.2f, 1.33f, 0.9f, 0.9f);
        b.bevelBox(1.4f, 1.0f, 1.28f, 1.8f, 1.8f, 1.45f, 0.04f, AtlasLoot.STEEL);
        b.pop();
        // rouleau de cordeau détonant (orange) posé à plat
        b.push().translate(10.6f, h + 0.06f + 0.12f, 3.6f);
        for (int k = 0; k < 4; k++) {
            b.push().translate(0, k * 0.2f, 0);
            b.torus(1.3f - k * 0.03f, 0.12f, 18, 4, 0, 360, ORANGE);
            b.pop();
        }
        b.tube(new float[][]{p(1.3f, 0.7f, 0), p(1.9f, 0.5f, 0.8f), p(2.2f, 0.0f, 2.0f), p(2.25f, -0.7f, 3.0f)}, fill(4, 0.12f), 5, ORANGE, false, true, 0, false);
        b.pop();
    }
}
