package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T4c (10/10) : conteneurs T4 de plus, tous uniques — militaire léger (vert olive, kaki, toile, pochoirs) :
 * norvégiens de cuisine, lit picot, table d'armurier, caisse du génie, bobine de câble, filet de camouflage roulé,
 * sac d'infirmier, caisse de mines Claymore (puis LotT4c2). Échelle réelle, dos au mur, abandonnés.
 */
public final class LotT4c {
    private LotT4c() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t4_norvegiens_cuisine", "t4", "metal", "Norvégiens de cuisine roulante", "Field kitchen food containers", LotT4c::norvegiens);
        Conteneurs.ajouter("t4_lit_picot", "t4", "tissu", "Lit picot et paquetage", "Camp cot with kit", LotT4c::litPicot);
        Conteneurs.ajouter("t4_table_armurier", "t4", "bois", "Table d'armurier", "Armorer's bench", LotT4c::tableArmurier);
        Conteneurs.ajouter("t4_caisse_genie", "t4", "bois", "Caisse du génie", "Combat engineer crate", LotT4c::caisseGenie);
        Conteneurs.ajouter("t4_bobine_cable", "t4", "bois", "Bobine de câble et téléphone", "Cable reel and field phone", LotT4c::bobineCable);
        Conteneurs.ajouter("t4_filet_camouflage", "t4", "tissu", "Filet de camouflage roulé", "Rolled camouflage net", LotT4c::filetCamouflage);
        Conteneurs.ajouter("t4_sac_infirmier", "t4", "tissu", "Sac d'infirmier de combat", "Combat medic bag", LotT4c::sacInfirmier);
        Conteneurs.ajouter("t4_caisse_claymore", "t4", "bois", "Caisse de mines Claymore", "Claymore mine crate", LotT4c::caisseClaymore);
        LotT4c2.enregistrer();
    }

    static final Mat CHROME = AtlasLoot.CHROME, CAOUTCHOUC = AtlasLoot.RUBBER, SOMBRE = AtlasLoot.DARK;
    static final Mat OLIVE = LotT4bAtlas.BOIS_OLIVE, KAKI = LotT4bAtlas.BOIS_KAKI, TOILE = LotT4bAtlas.TOILE, ACIER = LotT4bAtlas.ACIER;

    static void olive(MeshBuilder b) {
        b.color(0.42f, 0.46f, 0.32f);
    }

    // ================================================================================================ 1. norvégiens
    /** Norvégiens (conteneurs isothermes de cuisine roulante) : trois caissons olive à couvercle à clips, un ouvert avec
     *  la louche dedans, plateaux empilés, gamelles au sol. */
    static void norvegien(MeshBuilder b, float x, float y, float z, float ry, boolean ouvert) {
        b.push().translate(x, y, z).rotateY(ry);
        olive(b);
        b.bevelBox(-3.0f, 0, -2.2f, 3.0f, 4.6f, 2.2f, 0.5f, PEINTURE_USEE);
        b.color(1);
        for (float s : new float[]{-3.05f, 3.05f}) b.bevelBox(s - 0.15f, 2.6f, -0.8f, s + 0.15f, 3.4f, 0.8f, 0.06f, ACIER);
        if (ouvert) {
            b.box(-2.6f, 4.58f, -1.8f, 2.6f, 4.64f, 1.8f, INTERIEUR_SALE);
            b.push().translate(0, 4.6f, -2.2f).rotateX(-100);
            olive(b);
            b.bevelBox(-3.0f, 0, 0.05f, 3.0f, 0.6f, 4.4f, 0.25f, PEINTURE_USEE);
            b.color(1);
            b.pop();
            b.push().translate(0.6f, 4.0f, 0.2f).rotateZ(30);
            b.cylinder(0.12f, 0, 4.4f, 6, METAL_NU, METAL_NU, METAL_NU);
            b.pop();
        } else {
            olive(b);
            b.bevelBox(-3.05f, 4.6f, -2.25f, 3.05f, 5.2f, 2.25f, 0.25f, PEINTURE_USEE);
            b.color(1);
            for (float s : new float[]{-2.0f, 2.0f}) b.bevelBox(s - 0.3f, 4.2f, 2.2f, s + 0.3f, 5.3f, 2.45f, 0.06f, ACIER);
        }
        Kit.pochoir(b, LotT4bAtlas.S_RATIONS, 'z', 0, 2.2f, 2.22f, 3.6f, 1.0f);
        b.pop();
    }

    static void norvegiens(MeshBuilder b) {
        norvegien(b, 4.4f, 0, 3.4f, 4, false);
        norvegien(b, 11.4f, 0, 3.4f, -3, true);
        norvegien(b, 4.6f, 5.25f, 3.5f, -8, false);
        for (int k = 0; k < 4; k++) {                                            // plateaux empilés
            b.bevelBox(9.0f, 0.02f + k * 0.35f, 8.4f, 14.0f, 0.32f + k * 0.35f, 12.0f, 0.06f, METAL_NU);
        }
        for (int k = 0; k < 2; k++) {                                            // gamelles
            b.push().translate(3.0f + k * 3.0f, 0, 12.6f + k * 0.8f).rotateY(k * 40);
            b.lathe(new float[][]{{0.01f, 0}, {1.0f, 0}, {1.2f, 1.2f}, {1.1f, 1.2f}, {0.9f, 0.12f}, {0.01f, 0.12f}}, 12, 0, 360, METAL_NU, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        boue(b, 'z', 8.0f, 0, 5.62f, 12.0f, 1.0f, 0);
        jonchee(b, 0.6f, 6.0f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 3001);
    }

    // ================================================================================================ 2. lit picot
    /** Lit picot (deux blocs) : cadre de tubes et toile kaki affaissée, sac de couchage froissé, paquetage et rangers
     *  dessous, gourde, lampe frontale. */
    static void litPicot(MeshBuilder b) {
        float x0 = 1.0f, x1 = 31.0f, z0 = 1.6f, z1 = 11.6f, y = 6.4f;
        for (float x : new float[]{x0 + 1.0f, 16.0f, x1 - 1.0f}) {               // pieds en X
            b.tube(new float[][]{p(x, 0.15f, z0 + 0.6f), p(x, y, z1 - 0.6f)}, fill(2, 0.2f), 6, ACIER, true, true, 0, false);
            b.tube(new float[][]{p(x + 0.05f, 0.15f, z1 - 0.6f), p(x + 0.05f, y, z0 + 0.6f)}, fill(2, 0.2f), 6, ACIER, true, true, 0, false);
        }
        for (float z : new float[]{z0 + 0.5f, z1 - 0.5f}) {
            b.push().translate(x0, y, z).rotateZ(-90);
            b.cylinder(0.3f, 0, x1 - x0, 8, ACIER, ACIER, ACIER);
            b.pop();
        }
        b.color(0.5f, 0.5f, 0.38f);
        b.bevelBox(x0 + 0.3f, y + 0.05f, z0 + 0.6f, x1 - 0.3f, y + 0.35f, z1 - 0.6f, 0.08f, TOILE);
        b.color(1);
        b.push().translate(17.0f, y + 0.6f, 6.6f).rotateY(-4);                  // sac de couchage froissé
        b.color(0.36f, 0.38f, 0.28f);
        b.blob(9.0f, 0.9f, 3.6f, 0.6f, 0.25f, 2.2f, 3011, 14, TISSU_USE);
        b.color(1);
        b.pop();
        // paquetage et rangers dessous, gourde
        b.push().translate(8.0f, 2.0f, 6.6f);
        olive(b);
        b.blob(3.4f, 2.0f, 2.6f, 0.75f, 0.1f, 2.0f, 3012, 12, TISSU_USE);
        b.color(1);
        b.pop();
        for (int k = 0; k < 2; k++) {
            b.push().translate(22.0f + k * 2.0f, 0, 9.0f).rotateY(k * 15 - 5);
            LotT3bKit.ranger(b);
            b.pop();
        }
        b.push().translate(27.0f, y + 0.2f, 3.0f);
        b.color(0.42f, 0.46f, 0.32f);
        b.lathe(new float[][]{{0.01f, 0}, {1.0f, 0}, {1.1f, 2.0f}, {0.5f, 2.6f}, {0.5f, 3.0f}, {0.01f, 3.0f}}, 12, 0, 360, PEINTURE_USEE, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 12.4f, 31.4f, 15.6f, 6, FEUILLES, 1.0f, 3013);
    }

    // ================================================================================================ 3. table d'armurier
    /** Table d'armurier : établi pliant, fusil démonté (culasse, chargeur, canon), tapis, chiffons, burette, boîte de
     *  pièces, cible de papier punaisée au mur derrière. */
    static void tableArmurier(MeshBuilder b) {
        float x0 = 1.2f, x1 = 14.8f, z0 = 1.0f, z1 = 9.0f, y = 11.0f;
        b.bevelBox(x0, y, z0, x1, y + 0.6f, z1, 0.12f, KAKI);
        for (float x : new float[]{x0 + 0.8f, x1 - 1.4f}) for (float z : new float[]{z0 + 0.6f, z1 - 1.2f}) {
            b.bevelBox(x, 0, z, x + 0.6f, y, z + 0.6f, 0.08f, ACIER);
        }
        b.bevelBox(x0 + 1.0f, 3.0f, z0 + 0.66f, x1 - 1.0f, 3.4f, z1 - 0.66f, 0.06f, KAKI);     // tablette basse
        b.color(0.3f, 0.32f, 0.28f);
        b.box(x0 + 1.0f, y + 0.62f, z0 + 1.0f, x1 - 1.0f, y + 0.7f, z1 - 1.0f, CAOUTCHOUC_USE);
        b.color(1);
        // fusil démonté : carcasse + crosse, canon, culasse, chargeur
        b.push().translate(6.0f, y + 0.72f, 4.6f).rotateY(8);
        b.bevelBox(-4.0f, 0, -0.5f, 0.5f, 1.0f, 0.5f, 0.2f, AtlasCivil.WALNUT);
        b.bevelBox(0.5f, 0, -0.45f, 3.6f, 1.2f, 0.45f, 0.15f, SOMBRE);
        b.pop();
        b.push().translate(9.6f, y + 0.9f, 7.0f).rotateY(-12).rotateZ(-90);
        b.cylinder(0.22f, 0, 6.0f, 8, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.bevelBox(10.6f, y + 0.72f, 3.0f, 12.2f, y + 1.3f, 3.6f, 0.1f, METAL_NU);           // culasse
        b.push().translate(12.6f, y + 0.72f, 5.6f).rotateY(30);                // chargeur
        b.bevelBox(-0.4f, 0, -1.1f, 0.4f, 0.5f, 1.1f, 0.1f, SOMBRE);
        b.pop();
        b.push().translate(3.6f, y + 0.72f, 7.2f);                             // burette
        b.cylinder(0.6f, 0, 1.0f, 10, METAL_NU, METAL_NU, METAL_NU);
        b.translate(0, 1.0f, 0).rotateZ(-40);
        b.cylinder(0.1f, 0, 1.6f, 5, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.push().translate(6.0f, 3.4f, 5.0f);                                  // boîte de pièces sur la tablette
        olive(b);
        b.bevelBox(-2.0f, 0, -1.4f, 2.0f, 1.8f, 1.4f, 0.15f, PEINTURE_USEE);
        b.color(1);
        b.pop();
        b.box(9.0f, 18.0f, 0.06f, 14.0f, 24.0f, 0.12f, PAPIER);                // cible punaisée au mur
        decal(b, IMPACTS, 'z', 11.5f, 21.0f, 0.18f, 2.4f, 2.4f, 0);
        jonchee(b, 0.6f, 9.6f, 15.4f, 15.6f, 6, PAPIER, 1.0f, 3021);
    }

    // ================================================================================================ 4. caisse du génie
    /** Caisse du génie : caisse longue kaki ouverte, pelles et pioches debout dedans, pied-de-biche, cordeau détonant en
     *  bobine, piquets de signalisation rouge et blanc. */
    static void caisseGenie(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.4f, 0, 3.0f, 14.6f, 5.0f, 11.6f, KAKI);
        for (int k = 0; k < 3; k++) {                                            // pelles
            b.push().translate(3.6f + k * 2.4f, 0.55f, 6.6f).rotateZ(k * 6 - 6).rotateX(-12);
            b.cylinder(0.22f, 0, 10.0f, 6, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
            b.translate(0, 0.0f, 0);
            b.bevelBox(-1.0f, -0.05f, -0.1f, 1.0f, 2.8f, 0.1f, 0.05f, METAL_NU);
            b.pop();
        }
        b.push().translate(11.0f, 0.55f, 6.0f).rotateZ(-10);                  // pioche
        b.cylinder(0.25f, 0, 11.0f, 6, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
        b.translate(0, 10.4f, 0).rotateZ(90);
        b.cylinder(0.3f, -3.0f, 3.0f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.push().translate(12.6f, 0.55f, 9.6f);                               // bobine de cordeau
        b.color(0.85f, 0.75f, 0.2f);
        b.cylinder(1.4f, 0, 1.6f, 14, PLASTIQUE_USE, PLASTIQUE_USE, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        for (int k = 0; k < 3; k++) {                                            // piquets au sol
            b.push().translate(4.0f + k * 3.0f, 0.22f, 13.6f + (k % 2) * 0.6f).rotateY(k * 10).rotateZ(90);
            b.color(k % 2 == 0 ? 0.8f : 0.92f, k % 2 == 0 ? 0.15f : 0.92f, k % 2 == 0 ? 0.12f : 0.9f);
            b.cylinder(0.2f, -2.4f, 2.4f, 6, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
            b.color(1);
            b.pop();
        }
        Kit.pochoir(b, LotT4bAtlas.S_MATERIEL, 'z', 8.0f, 2.6f, 11.7f, 5.4f, 1.3f);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 3031);
    }

    // ================================================================================================ 5. bobine de câble
    /** Bobine de câble de campagne (tambour de bois) sur son dévidoir, câble qui part au sol, téléphone de campagne dans
     *  sa sacoche de toile, manivelle. */
    static void bobineCable(MeshBuilder b) {
        for (float z : new float[]{2.0f, 9.0f}) {
            b.tube(new float[][]{p(4.0f, 0.15f, z), p(8.0f, 7.6f, z)}, fill(2, 0.3f), 6, ACIER, true, true, 0, false);
            b.tube(new float[][]{p(12.0f, 0.15f, z + 0.07f), p(8.1f, 7.5f, z + 0.07f)}, fill(2, 0.3f), 6, ACIER, true, true, 0, false);
        }
        b.push().translate(8.0f, 7.0f, 5.5f).rotateX(90);
        b.cylinder(0.35f, -4.4f, 4.4f, 8, METAL_NU, METAL_NU, METAL_NU);
        for (float y : new float[]{-3.4f, 3.0f}) b.cylinder(5.0f, y, y + 0.4f, 18, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
        b.cylinder(0.5f, -3.5f, 3.5f, 8, METAL_NU, METAL_NU, METAL_NU);
        b.color(0.2f, 0.22f, 0.18f);
        b.cylinder(3.6f, -2.95f, 2.95f, 18, CAOUTCHOUC_USE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
        b.color(1);
        b.pop();
        cable(b, auSol(lisse(new float[][]{p(8.0f, 3.6f, 9.0f), p(9.0f, 1.0f, 11.6f), p(11.0f, 0.14f, 13.6f), p(14.6f, 0.14f, 14.2f)}, 6), 0.13f),
                0.12f, 0.12f, 0.12f, 0.12f);
        b.push().translate(3.0f, 0, 12.6f).rotateY(18);                        // téléphone de campagne dans sa sacoche
        olive(b);
        b.bevelBox(-1.8f, 0, -1.2f, 1.8f, 2.8f, 1.2f, 0.3f, TISSU_USE);
        b.color(1);
        b.box(-1.4f, 1.0f, 1.21f, 1.4f, 2.4f, 1.26f, LotT4bAtlas.RADIO_FACE);
        b.bevelBox(-1.6f, 2.8f, -0.4f, 1.6f, 3.4f, 0.4f, 0.2f, SOMBRE);
        b.pop();
        jonchee(b, 0.6f, 11.0f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 3041);
    }

    // ================================================================================================ 6. filet de camouflage
    /** Filet de camouflage roulé et sanglé, posé sur deux mâts télescopiques couchés, piquets, sac de transport ouvert ;
     *  un pan déroulé qui traîne au sol. */
    static void filetCamouflage(MeshBuilder b) {
        for (float z : new float[]{2.4f, 3.8f}) {                                // mâts couchés
            b.push().translate(1.4f, 0.32f, z).rotateZ(-90);
            b.cylinder(0.3f, 0, 29.0f, 8, ACIER, ACIER, ACIER);
            b.pop();
        }
        b.push().translate(2.0f, 2.6f, 7.0f).rotateZ(-90);                    // rouleau
        b.cylinder(2.3f, 0, 27.6f, 16, LotT4bAtlas.CAMO_CE, LotT4bAtlas.CAMO_CE, LotT4bAtlas.CAMO_CE);
        for (float y : new float[]{5.0f, 13.8f, 22.6f}) {
            b.push().translate(0, y, 0);
            b.hollowCylinder(2.4f, 2.2f, 0, 0.8f, 16, LotT4bAtlas.SANGLE);
            b.pop();
        }
        b.pop();
        b.push().translate(14.0f, 0.12f, 12.4f).rotateY(-4);                  // pan déroulé au sol
        b.bevelBox(-11.0f, 0, -2.4f, 11.0f, 0.15f, 2.4f, 0.05f, LotT4bAtlas.CAMO_CE);
        b.pop();
        b.push().translate(28.0f, 1.4f, 12.0f).rotateY(20);                   // sac de transport
        olive(b);
        b.blob(2.2f, 1.4f, 1.6f, 0.7f, 0.1f, 2.0f, 3051, 12, TISSU_USE);
        b.color(1);
        b.pop();
        for (int k = 0; k < 4; k++) {
            b.push().translate(5.0f + k * 1.0f, 0.2f, 15.0f).rotateY(k * 12).rotateZ(90);
            b.cylinder(0.15f, -1.2f, 1.2f, 5, METAL_NU, METAL_NU, METAL_NU);
            b.pop();
        }
    }

    // ================================================================================================ 7. sac d'infirmier
    /** Sac d'infirmier de combat ouvert (toile olive, croix rouge), compartiments : garrots, pansements, ciseaux,
     *  poche de perfusion ; brancard plié appuyé derrière. */
    static void sacInfirmier(MeshBuilder b) {
        b.push().translate(0, 0.2f, 3.0f).rotateX(-10);                        // brancard plié appuyé au mur
        b.color(0.4f, 0.42f, 0.32f);
        b.bevelBox(2.0f, 0, 0.1f, 14.0f, 14.0f, 1.0f, 0.2f, TOILE);
        b.color(1);
        for (float x : new float[]{1.8f, 14.2f}) {
            b.push().translate(x, 0, 0.55f);
            b.cylinder(0.3f, 0, 14.6f, 8, ACIER, ACIER, ACIER);
            b.pop();
        }
        b.pop();
        b.push().translate(8.0f, 0, 8.0f).rotateY(-8);
        olive(b);
        b.bevelBox(-4.0f, 0, -2.6f, 4.0f, 4.2f, 2.6f, 0.8f, TISSU_USE);
        b.color(1);
        b.box(-3.4f, 4.18f, -2.0f, 3.4f, 4.24f, 2.0f, INTERIEUR_SALE);
        b.push().translate(0, 4.2f, -2.6f).rotateX(-115);                     // rabat ouvert
        olive(b);
        b.bevelBox(-4.0f, 0, 0, 4.0f, 0.5f, 4.6f, 0.2f, TISSU_USE);
        b.color(1);
        b.pop();
        decal(b, LotT4bAtlas.CROIX_ROUGE, 'z', 0, 2.2f, 2.65f, 2.0f, 2.0f, 0);
        for (int k = 0; k < 3; k++) {                                          // contenu qui dépasse
            b.push().translate(-2.4f + k * 2.2f, 3.8f, 0.4f + (k % 2) * 0.6f);
            b.color(k == 0 ? 0.95f : 0.25f, k == 0 ? 0.95f : 0.25f, k == 0 ? 0.92f : 0.25f);
            b.bevelBox(-0.8f, 0, -0.5f, 0.8f, 1.4f, 0.5f, 0.15f, LotT4bAtlas.GAZE);
            b.color(1);
            b.pop();
        }
        b.pop();
        b.push().translate(13.6f, 0.05f, 13.0f).rotateY(25);                 // poche de perfusion
        b.box(-1.0f, 0, -1.4f, 1.0f, 0.4f, 1.4f, LotT4bAtlas.PERFUSION);
        b.pop();
        b.push().translate(3.0f, 0.12f, 13.8f).rotateY(-30);                 // garrot
        b.color(0.15f, 0.15f, 0.15f);
        b.bevelBox(-1.6f, 0, -0.25f, 1.6f, 0.2f, 0.25f, 0.06f, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 11.4f, 15.4f, 15.6f, 5, PAPIER, 1.0f, 3061);
    }

    // ================================================================================================ 8. caisse de Claymore
    /** Caisse de mines Claymore : caisse olive ouverte, mines incurvées dans leur musette, bobines de fil, boîtiers de
     *  mise à feu, inscription « FRONT TOWARD ENEMY » (pochoir) ; une musette ouverte posée devant. */
    static void claymore(MeshBuilder b, float x, float y, float z, float ry) {
        b.push().translate(x, y, z).rotateY(ry);
        olive(b);
        b.extrudeProfileX(new float[][]{{-0.5f, 0}, {0.5f, 0}, {0.8f, 1.8f}, {0.3f, 2.0f}, {0.0f, 1.8f}}, -1.8f, 1.8f, PEINTURE_USEE);
        b.color(1);
        for (float s : new float[]{-1.4f, 1.4f}) {
            b.push().translate(s, 0, 0);
            b.cylinder(0.08f, -0.6f, 0.05f, 4, SOMBRE, SOMBRE, SOMBRE);
            b.pop();
        }
        b.pop();
    }

    static void caisseClaymore(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.6f, 0, 2.4f, 14.4f, 4.4f, 11.2f, OLIVE);
        for (int k = 0; k < 4; k++) claymore(b, 4.0f + (k % 2) * 6.0f, 1.2f, 5.0f + (k / 2) * 3.4f, (k * 13) % 20 - 10);
        for (int k = 0; k < 2; k++) {                                          // bobines de fil
            b.push().translate(6.6f + k * 3.0f, 0.55f, 9.6f);
            b.cylinder(0.9f, 0, 1.0f, 10, SOMBRE, SOMBRE, SOMBRE);
            b.pop();
        }
        b.push().translate(4.0f, 1.2f, 13.4f).rotateY(-15);                    // musette ouverte devant
        olive(b);
        b.blob(2.2f, 1.2f, 1.4f, 0.75f, 0.08f, 2.0f, 3071, 12, TISSU_USE);
        b.color(1);
        b.pop();
        claymore(b, 9.6f, 0.64f, 13.6f, 25);
        Kit.pochoir(b, LotT4bAtlas.S_EXPLOSIFS, 'z', 8.0f, 2.2f, 11.3f, 5.6f, 1.3f);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 3072);
    }
}
