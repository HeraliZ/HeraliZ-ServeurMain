package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T5c, seconde moitié : bloc de batteries, caisse de tenues NRBC, caisse de pièces de blindé, poste d'observation,
 * caisse de fusées éclairantes, sac radio, table d'opérations, projecteur de campagne, caisse de tentes ONU, glacière
 * de vaccins.
 */
final class LotT5c2 {
    private LotT5c2() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t5_bloc_batteries", "t5", "metal", "Bloc de batteries militaires", "Military battery pack", LotT5c2::blocBatteries);
        Conteneurs.ajouter("t5_caisse_nrbc", "t5", "bois", "Caisse de tenues NRBC", "CBRN suit crate", LotT5c2::caisseNrbc);
        Conteneurs.ajouter("t5_caisse_pieces_blinde", "t5", "metal", "Caisse de pièces de blindé", "Armored vehicle spares", LotT5c2::caissePiecesBlinde);
        Conteneurs.ajouter("t5_poste_observation", "t5", "metal", "Poste d'observation", "Observation post", LotT5c2::posteObservation);
        Conteneurs.ajouter("t5_caisse_fusees", "t5", "bois", "Caisse de fusées éclairantes", "Flare crate", LotT5c2::caisseFusees);
        Conteneurs.ajouter("t5_sac_radio", "t5", "tissu", "Sac radio de patrouille", "Manpack radio", LotT5c2::sacRadio);
        Conteneurs.ajouter("t5_table_operations", "t5", "metal", "Table d'opérations ONU", "UN operations table", LotT5c2::tableOperations);
        Conteneurs.ajouter("t5_projecteur_campagne", "t5", "metal", "Projecteur de campagne", "Field floodlight", LotT5c2::projecteur);
        Conteneurs.ajouter("t5_caisse_tentes_onu", "t5", "bois", "Caisse de tentes ONU", "UN tent crate", LotT5c2::caisseTentes);
        Conteneurs.ajouter("t5_glaciere_vaccins", "t5", "plastique", "Glacière de vaccins ONU", "UN vaccine cooler", LotT5c2::glaciereVaccins);
    }

    static final Mat CHROME = AtlasLoot.CHROME, CAOUTCHOUC = AtlasLoot.RUBBER, SOMBRE = AtlasLoot.DARK;
    static final Mat OLIVE = LotT4bAtlas.BOIS_OLIVE, KAKI = LotT4bAtlas.BOIS_KAKI;

    // ================================================================================================ 1. batteries
    /** Batteries militaires (boîtiers kaki à bornes) empilées dans un bac de transport, chargeur relié, une batterie
     *  ouverte qui a coulé, câbles. */
    static void batterie(MeshBuilder b, float x, float y, float z, float ry) {
        b.push().translate(x, y, z).rotateY(ry);
        b.color(0.36f, 0.4f, 0.3f);
        b.bevelBox(-1.6f, 0, -1.0f, 1.6f, 2.6f, 1.0f, 0.2f, PLASTIQUE_USE);
        b.color(1);
        for (float s : new float[]{-0.8f, 0.8f}) {
            b.push().translate(s, 2.6f, 0);
            b.cylinder(0.25f, 0, 0.4f, 8, s < 0 ? ROUGE_SECU : SOMBRE, SOMBRE, s < 0 ? ROUGE_SECU : SOMBRE);
            b.pop();
        }
        b.pop();
    }

    static void blocBatteries(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.4f, 0, 2.0f, 14.6f, 3.6f, 11.6f, OLIVE);
        for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) batterie(b, 4.0f + i * 3.8f, 0.52f, 4.6f + j * 4.4f, (i + j) % 2 * 4 - 2);
        batterie(b, 6.0f, 3.15f, 6.4f, 12);
        batterie(b, 10.4f, 3.15f, 7.0f, -9);
        b.push().translate(4.0f, 0, 13.6f).rotateY(30);                         // chargeur au sol
        b.bevelBox(-2.0f, 0, -1.0f, 2.0f, 1.4f, 1.0f, 0.2f, SOMBRE);
        b.box(-1.2f, 1.41f, -0.6f, 0.4f, 1.45f, 0.4f, LCD_OFF);
        b.pop();
        cable(b, auSol(lisse(new float[][]{p(5.4f, 1.0f, 13.0f), p(7.0f, 0.15f, 12.6f), p(9.6f, 1.0f, 11.8f), p(10.4f, 3.9f, 7.6f)}, 6), 0.13f),
                0.12f, 0.12f, 0.12f, 0.12f);
        decal(b, AUREOLE, 'y', 11.6f, 0.03f, 13.6f, 2.4f, 1.6f, 0);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2101);
    }

    // ================================================================================================ 2. tenues NRBC
    /** Caisse de tenues NRBC : caisse kaki ouverte, tenues pliées sous plastique, masques à gaz, cartouches filtrantes ;
     *  une tenue dépliée pend du bord, un masque au sol. */
    static void masque(MeshBuilder b, float x, float y, float z, float rx, float ry) {
        b.push().translate(x, y, z).rotateY(ry).rotateX(rx);
        b.color(0.25f, 0.27f, 0.25f);
        b.push().scale(1, 0.7f, 1);
        b.sphereSection(1.4f, 12, 5, 0, 90, CAOUTCHOUC_USE);
        b.pop();
        b.color(1);
        for (float s : new float[]{-0.6f, 0.6f}) {
            b.push().translate(s, 0.55f, 0.95f).rotateX(70);
            b.cylinder(0.42f, 0, 0.12f, 10, AtlasLoot.GLASS, AtlasLoot.GLASS, AtlasLoot.GLASS);
            b.pop();
        }
        b.push().translate(0, 0.1f, 1.3f).rotateX(90);
        b.cylinder(0.6f, 0, 1.0f, 10, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.pop();
    }

    static void caisseNrbc(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.4f, 0, 2.4f, 14.6f, 5.0f, 11.6f, KAKI);
        for (int k = 0; k < 3; k++) {
            b.push().translate(4.6f + k * 3.6f, 0.55f, 6.6f).rotateY(k * 7 - 6);
            b.color(0.36f, 0.42f, 0.3f);
            b.bevelBox(-1.6f, 0, -3.4f, 1.6f, 2.6f + (k % 2) * 0.6f, 3.4f, 0.5f, TISSU_USE);
            b.color(1);
            b.box(-1.66f, 0.3f, -3.46f, 1.66f, 2.3f, 3.46f, FILM);
            b.pop();
        }
        masque(b, 5.0f, 3.9f, 6.0f, -10, 20);
        masque(b, 10.4f, 3.9f, 7.2f, 6, -15);
        masque(b, 11.4f, 1.45f, 13.6f, -80, 40);                                 // au sol
        for (int k = 0; k < 3; k++) {                                            // cartouches filtrantes
            b.push().translate(2.6f + k * 1.4f, 0, 13.4f + (k % 2) * 0.6f);
            b.cylinder(0.6f, 0, 0.9f, 10, METAL_NU, METAL_NU, SOMBRE);
            b.pop();
        }
        b.push().translate(14.0f, 5.0f, 9.0f).rotateZ(-80);                     // tenue qui pend du bord droit
        b.color(0.36f, 0.42f, 0.3f);
        b.bevelBox(-0.2f, 0, -2.0f, 0.3f, 4.4f, 2.0f, 0.2f, TISSU_USE);
        b.color(1);
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_MATERIEL, 'z', 8.0f, 2.6f, 11.7f, 5.4f, 1.3f);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2111);
    }

    // ================================================================================================ 3. pièces de blindé
    /** Caisse de pièces de blindé (deux blocs) : caisse métallique à claire-voie, galets de roulement, maillons de
     *  chenille, roue de transmission appuyée contre la caisse, filtre et bidon d'huile. */
    static void caissePiecesBlinde(MeshBuilder b) {
        float x0 = 1.2f, x1 = 20.0f, z0 = 2.0f, z1 = 11.6f, h = 6.0f;
        for (float x : new float[]{x0, x1 - 0.6f}) for (float z : new float[]{z0, z1 - 0.6f}) b.bevelBox(x, 0, z, x + 0.6f, h, z + 0.6f, 0.1f, ACIER_NOIR);
        for (float y : new float[]{0.2f, h * 0.5f, h - 0.6f}) {
            b.bevelBox(x0 + 0.55f, y + 0.05f, z0 + 0.05f, x1 - 0.55f, y + 0.55f, z0 + 0.55f, 0.08f, ACIER_NOIR);
            b.bevelBox(x0 + 0.55f, y + 0.05f, z1 - 0.55f, x1 - 0.55f, y + 0.55f, z1 - 0.05f, 0.08f, ACIER_NOIR);
        }
        b.bevelBox(x0 + 0.55f, 0.1f, z0 + 0.55f, x1 - 0.55f, 0.6f, z1 - 0.55f, 0.06f, ACIER_NOIR);
        for (int k = 0; k < 3; k++) {                                           // galets de roulement
            b.push().translate(4.6f + k * 5.2f, 3.1f, 6.8f).rotateX(90);
            b.cylinder(2.5f, -1.2f, 1.2f, 18, CAOUTCHOUC_USE, METAL_NU, METAL_NU);
            b.pop();
        }
        for (int k = 0; k < 5; k++) {                                           // maillons de chenille au sol devant
            b.push().translate(3.0f + k * 2.3f, 0, 13.4f).rotateY(k * 4);
            b.bevelBox(-1.1f, 0, -1.4f, 1.1f, 0.8f, 1.4f, 0.12f, ROUILLE);
            b.pop();
        }
        b.push().translate(26.0f, 0.2f, 5.0f).rotateZ(14);                     // roue de transmission appuyée
        b.push().translate(0, 4.4f, 0).rotateX(90);
        b.cylinder(4.4f, -0.6f, 0.6f, 20, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.pop();
        b.push().translate(23.0f, 0, 12.6f);                                   // bidon d'huile
        b.color(0.25f, 0.3f, 0.22f);
        b.cylinder(1.6f, 0, 4.4f, 14, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
        b.color(1);
        b.pop();
        decal(b, AUREOLE, 'y', 20.0f, 0.03f, 14.0f, 3.0f, 2.0f, 30);
        Kit.pochoir(b, LotT4bAtlas.S_MATERIEL, 'z', 10.6f, 4.6f, z1 + 0.02f, 5.6f, 1.2f);
        jonchee(b, 0.6f, 12.4f, 31.4f, 15.6f, 6, FEUILLES, 1.0f, 2121);
    }

    // ================================================================================================ 4. poste d'observation
    /** Poste d'observation : jumelles de forte puissance sur trépied, télémètre, valise de transport ouverte, carnet,
     *  muret de sacs de sable bas devant. */
    static void posteObservation(MeshBuilder b) {
        float px = 8.0f, pz = 5.0f;
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(90 + k * 120);
            b.tube(new float[][]{p(px, 9.0f, pz), p(px + (float) Math.cos(a) * 3.6f, 0.2f, pz + (float) Math.sin(a) * 3.6f)},
                    fill(2, 0.18f), 6, SOMBRE, true, true, 0, false);
        }
        b.push().translate(px, 9.0f, pz);
        b.cylinder(0.4f, 0, 1.0f, 8, SOMBRE, SOMBRE, SOMBRE);
        b.translate(0, 1.4f, 0).rotateY(-20);
        for (float s : new float[]{-0.9f, 0.9f}) {                               // jumelles (deux tubes)
            b.push().translate(s, 0, -1.0f).rotateX(90);
            b.cylinder(0.75f, 0, 4.4f, 12, SOMBRE, SOMBRE, AtlasLoot.GLASS);
            b.pop();
        }
        b.bevelBox(-1.0f, -0.4f, -0.2f, 1.0f, 0.5f, 1.4f, 0.1f, SOMBRE);
        b.pop();
        b.push().translate(3.0f, 0, 3.0f).rotateY(15);                         // valise ouverte au sol
        b.bevelBox(-2.4f, 0, -1.4f, 2.4f, 1.6f, 1.4f, 0.25f, SOMBRE);
        b.box(-2.1f, 1.58f, -1.1f, 2.1f, 1.64f, 1.1f, MOUSSE_DECOUPE);
        b.pop();
        b.push().translate(12.6f, 0, 3.0f).rotateY(-25);                       // télémètre
        b.bevelBox(-1.0f, 0, -0.7f, 1.0f, 1.4f, 0.7f, 0.2f, SOMBRE);
        b.push().translate(0, 0.7f, 0.7f).rotateX(90);
        b.cylinder(0.45f, 0, 0.6f, 10, SOMBRE, SOMBRE, AtlasLoot.GLASS);
        b.pop();
        b.pop();
        for (int k = 0; k < 4; k++) {                                          // muret bas de sacs de sable
            LotT4bKit.sacSable(b, LotT4bAtlas.SAC_SABLE, 2.4f + k * 3.6f, 0, 13.0f, 1.8f, 0.9f, 1.1f, k * 6 - 8, 0, 2141 + k, k % 2 == 0);
        }
        b.push().translate(10.4f, 0.04f, 9.6f).rotateY(20);                    // carnet
        b.color(0.4f, 0.3f, 0.2f);
        b.box(-0.9f, 0, -1.2f, 0.9f, 0.25f, 1.2f, CUIR_USE);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 7.0f, 15.4f, 11.6f, 5, FEUILLES, 1.0f, 2142);
    }

    // ================================================================================================ 5. fusées éclairantes
    /** Caisse de fusées éclairantes : caisse olive ouverte, tubes rouges à capuchon rangés en quinconce, quelques-uns
     *  sortis au sol, pistolet lance-fusées orange posé sur le bord. */
    static void caisseFusees(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.6f, 0, 2.4f, 14.4f, 4.0f, 11.2f, OLIVE);
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 4; j++) {
                if ((i * 4 + j) % 7 == 3) continue;
                b.push().translate(3.4f + i * 1.8f, 0.52f, 4.0f + j * 1.9f);
                b.color(0.75f, 0.15f, 0.12f);
                b.cylinder(0.7f, 0, 3.4f, 10, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
                b.color(1);
                b.cylinder(0.75f, 3.4f, 3.9f, 10, SOMBRE, SOMBRE, SOMBRE);
                b.pop();
            }
        }
        for (int k = 0; k < 3; k++) {                                           // fusées au sol
            b.push().translate(5.0f + k * 2.6f, 0.72f, 13.6f).rotateY(k * 25 - 20).rotateZ(90);
            b.color(0.75f, 0.15f, 0.12f);
            b.cylinder(0.7f, -1.9f, 1.9f, 10, PEINTURE_USEE, PEINTURE_USEE, SOMBRE);
            b.color(1);
            b.pop();
        }
        b.push().translate(12.6f, 4.05f, 9.8f).rotateY(-30);                   // pistolet lance-fusées
        b.color(0.95f, 0.45f, 0.1f);
        b.bevelBox(-0.4f, 0, -0.4f, 0.4f, 2.0f, 0.4f, 0.12f, PLASTIQUE_USE);
        b.push().translate(0, 1.6f, 0).rotateX(90);
        b.cylinder(0.55f, 0, 3.0f, 10, PLASTIQUE_USE, PLASTIQUE_USE, SOMBRE);
        b.pop();
        b.color(1);
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_DANGER, 'z', 8.0f, 2.2f, 11.3f, 5.0f, 1.2f);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2151);
    }

    // ================================================================================================ 6. sac radio
    /** Sac radio de patrouille : sac à dos kaki à armature appuyé contre le mur, poste radio dedans (face visible), antenne
     *  fouet, combiné qui pend, poches latérales, sangles. */
    static void sacRadio(MeshBuilder b) {
        b.push().translate(8.0f, 0, 3.0f).rotateX(-8);
        b.color(0.4f, 0.42f, 0.32f);
        b.bevelBox(-3.6f, 0, 0.2f, 3.6f, 11.0f, 4.6f, 1.0f, TISSU_USE);
        b.color(1);
        b.box(-2.6f, 4.0f, 4.62f, 2.6f, 9.4f, 4.68f, LotT4bAtlas.RADIO_FACE);
        for (float s : new float[]{-4.2f, 3.6f}) {                              // poches latérales
            b.color(0.38f, 0.4f, 0.3f);
            b.bevelBox(s, 1.0f, 1.2f, s + 0.6f, 6.0f, 4.0f, 0.25f, TISSU_USE);
            b.color(1);
        }
        b.push().translate(2.6f, 11.0f, 2.0f);                                  // antenne
        b.cylinder(0.3f, 0, 0.8f, 8, SOMBRE, SOMBRE, SOMBRE);
        b.rotateZ(-8);
        b.cylinder(0.08f, 0.8f, 16.0f, 5, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.pop();
        cable(b, lisse(new float[][]{p(6.0f, 9.0f, 5.4f), p(5.0f, 6.0f, 7.0f), p(4.6f, 3.6f, 7.6f)}, 6), 0.1f, 0.1f, 0.1f, 0.1f);
        b.push().translate(4.6f, 2.0f, 7.8f).rotateX(10);                       // combiné
        b.bevelBox(-0.5f, 0, -0.4f, 0.5f, 2.6f, 0.4f, 0.2f, SOMBRE);
        b.pop();
        jonchee(b, 0.6f, 9.0f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 2161);
    }

    // ================================================================================================ 7. table d'opérations
    /** Table d'opérations ONU (deux blocs) : table pliante, carte d'état-major punaisée, rapporteur, jumelles, ordinateur
     *  portable fermé blanc UN, tasse, chaise pliante renversée. */
    static void tableOperations(MeshBuilder b) {
        float x0 = 1.2f, x1 = 30.8f, z0 = 1.0f, z1 = 11.0f, y = 11.6f;
        b.bevelBox(x0, y, z0, x1, y + 0.5f, z1, 0.1f, METAL_NU);
        for (float x : new float[]{x0 + 1.2f, x1 - 1.8f}) {
            b.tube(new float[][]{p(x, y, z0 + 1.0f), p(x + 0.3f, 0.1f, z1 - 1.0f)}, fill(2, 0.25f), 6, METAL_NU, true, true, 0, false);
            b.tube(new float[][]{p(x, y, z1 - 1.0f), p(x + 0.3f, 0.1f, z0 + 1.0f)}, fill(2, 0.25f), 6, METAL_NU, true, true, 0, false);
        }
        b.push().translate(14.0f, y + 0.56f, 6.0f).rotateY(-3);                // carte
        b.box(-8.0f, 0, -4.2f, 8.0f, 0.06f, 4.2f, LotT4bAtlas.CARTE);
        b.pop();
        b.push().translate(26.0f, y + 0.5f, 5.0f).rotateY(14);                 // portable blanc UN fermé
        b.color(0.92f, 0.92f, 0.9f);
        b.bevelBox(-2.6f, 0, -1.8f, 2.6f, 0.7f, 1.8f, 0.15f, PLASTIQUE_USE);
        b.color(1);
        Kit.pochoir(b, S_UN_NOIR, 'y', 0, 0.72f, 0, 1.6f, 0.8f);
        b.pop();
        b.push().translate(5.0f, y + 0.5f, 8.4f).rotateY(35);                  // jumelles
        for (float s : new float[]{-0.6f, 0.6f}) {
            b.push().translate(s, 0.5f, 0).rotateX(90);
            b.cylinder(0.5f, -1.2f, 1.2f, 10, SOMBRE, SOMBRE, SOMBRE);
            b.pop();
        }
        b.pop();
        b.push().translate(20.0f, y + 0.5f, 8.6f);                              // tasse
        b.color(0.9f, 0.9f, 0.88f);
        b.cylinder(0.7f, 0, 1.4f, 10, PEINTURE_USEE, PEINTURE_USEE, INTERIEUR_SALE);
        b.color(1);
        b.pop();
        // chaise pliante renversée devant
        b.push().translate(12.0f, 0.75f, 14.2f).rotateY(20).rotateX(-80);
        b.bevelBox(-2.2f, 0, -0.1f, 2.2f, 4.4f, 0.4f, 0.1f, METAL_NU);
        b.bevelBox(-2.2f, 4.0f, 0.3f, 2.2f, 4.4f, 4.2f, 0.1f, METAL_NU);
        b.pop();
        jonchee(b, 0.6f, 1.0f, 31.4f, 15.6f, 10, PAPIER, 1.3f, 2171);
    }

    // ================================================================================================ 8. projecteur de campagne
    /** Projecteur de campagne sur mât télescopique (remorque à béquilles) : quatre phares, mât rentré à moitié, coffret
     *  électrique ouvert (le conteneur), câbles, un phare brisé. */
    static void projecteur(MeshBuilder b) {
        b.color(0.9f, 0.75f, 0.2f);
        b.bevelBox(2.0f, 1.6f, 2.0f, 14.0f, 4.6f, 10.0f, 0.3f, PEINTURE_USEE);      // coffret
        b.color(1);
        b.box(3.0f, 2.0f, 10.03f, 13.0f, 4.2f, 10.09f, INTERIEUR_SALE);
        for (float[] c : new float[][]{{2.4f, 2.4f}, {13.6f, 2.4f}, {2.4f, 9.6f}, {13.6f, 9.6f}}) {
            b.bevelBox(c[0] - 0.4f, 0, c[1] - 0.4f, c[0] + 0.4f, 1.62f, c[1] + 0.4f, 0.08f, METAL_NU);
        }
        b.push().translate(8.0f, 4.6f, 4.0f);                                   // mât
        b.cylinder(0.7f, 0, 9.0f, 10, METAL_NU, METAL_NU, METAL_NU);
        b.cylinder(0.5f, 9.0f, 15.0f, 10, METAL_NU, METAL_NU, METAL_NU);
        b.translate(0, 15.0f, 0);
        b.bevelBox(-4.4f, 0, -0.4f, 4.4f, 0.6f, 0.4f, 0.1f, METAL_NU);
        for (int k = 0; k < 4; k++) {
            float x = -3.3f + k * 2.2f;
            b.push().translate(x, 0.6f, 0).rotateX(-15);
            b.bevelBox(-1.0f, 0, -0.9f, 1.0f, 1.8f, 0.9f, 0.2f, SOMBRE);
            b.box(-0.8f, 0.2f, 0.92f, 0.8f, 1.6f, 0.98f, k == 2 ? VERRE_FELE : AtlasLoot.GLASS);
            b.pop();
        }
        b.pop();
        cable(b, auSol(lisse(new float[][]{p(13.0f, 3.0f, 10.2f), p(14.0f, 0.15f, 12.6f), p(10.0f, 0.15f, 14.4f), p(5.0f, 0.15f, 13.6f)}, 6), 0.13f),
                0.15f, 0.12f, 0.12f, 0.12f);
        debris(b, 10.6f, 12.4f, 1.0f, 5, VERRE_SALE, 2181);
        jonchee(b, 0.6f, 11.0f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2182);
    }

    // ================================================================================================ 9. tentes ONU
    /** Caisse de tentes ONU : caisse blanche ouverte, toiles bleues roulées et sanglées, sac de piquets, maillet ; une
     *  toile déroulée qui déborde au sol. */
    static void caisseTentes(MeshBuilder b) {
        b.color(0.92f, 0.92f, 0.88f);
        LotT5c.caisseOuverte(b, 1.2f, 0, 2.6f, 14.8f, 4.6f, 11.4f, BLANC_ONU);
        b.color(1);
        for (int k = 0; k < 3; k++) {
            b.push().translate(8.0f, 2.2f + (k == 2 ? 2.4f : 0), 5.0f + k * 2.6f - (k == 2 ? 3.6f : 0)).rotateZ(90);
            b.color(0.45f, 0.6f, 0.82f);
            b.cylinder(1.4f, -5.6f + k * 0.2f, 5.6f - k * 0.15f, 14, TISSU_USE, TISSU_USE, TISSU_USE);
            b.color(1);
            for (float s : new float[]{-3.0f, 3.0f}) {
                b.push().translate(0, s, 0);
                b.hollowCylinder(1.5f, 1.38f, -0.3f, 0.3f, 14, SANGLE);
                b.pop();
            }
            b.pop();
        }
        b.push().translate(7.0f, 0.1f, 13.0f).rotateY(-6);                      // toile déroulée au sol
        b.color(0.45f, 0.6f, 0.82f);
        b.bevelBox(-5.6f, 0, -1.6f, 5.6f, 0.2f, 1.6f, 0.05f, TISSU_USE);
        b.color(1);
        b.pop();
        b.push().translate(13.6f, 0.3f, 14.4f).rotateY(40).rotateZ(90);         // maillet
        b.cylinder(0.12f, -2.0f, 1.0f, 6, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
        b.pop();
        Kit.pochoir(b, S_UN_NOIR, 'z', 8.0f, 2.4f, 11.5f, 3.0f, 1.5f);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 3, FEUILLES, 1.0f, 2191);
    }

    // ================================================================================================ 10. glacière de vaccins
    /** Glacière de vaccins ONU : caisse isotherme blanche à couvercle bleu entrouvert, thermomètre, boîtes de flacons
     *  dedans, pains de glace fondus, autocollant « fragile ». */
    static void glaciereVaccins(MeshBuilder b) {
        float x0 = 2.6f, x1 = 13.4f, z0 = 2.6f, z1 = 10.4f, h = 6.0f;
        b.color(0.92f, 0.93f, 0.92f);
        b.bevelBox(x0, 0, z0, x1, 0.8f, z1, 0.4f, PLASTIQUE_USE);
        b.bevelBox(x0, 0.75f, z0, x0 + 0.8f, h, z1, 0.3f, PLASTIQUE_USE);
        b.bevelBox(x1 - 0.8f, 0.75f, z0, x1, h, z1, 0.3f, PLASTIQUE_USE);
        b.bevelBox(x0 + 0.75f, 0.75f, z0, x1 - 0.75f, h, z0 + 0.8f, 0.3f, PLASTIQUE_USE);
        b.bevelBox(x0 + 0.75f, 0.75f, z1 - 0.8f, x1 - 0.75f, h, z1, 0.3f, PLASTIQUE_USE);
        b.color(1);
        for (int k = 0; k < 3; k++) {
            b.push().translate(5.0f + k * 3.0f, 0.82f, 6.4f).rotateY(k * 9 - 9);
            b.color(0.95f, 0.95f, 0.92f);
            b.bevelBox(-1.2f, 0, -2.2f, 1.2f, 3.0f, 2.2f, 0.12f, CARTON_BLANC);
            b.color(1);
            b.pop();
        }
        b.push().translate(0, h, z0).rotateX(-58).translate(0, -h, -z0);      // couvercle bleu entrouvert
        b.color(0.6f, 0.75f, 0.95f);
        b.bevelBox(x0 - 0.1f, h + 0.05f, z0, x1 + 0.1f, h + 1.4f, z1 + 0.1f, 0.4f, BLEU_ONU);
        b.color(1);
        b.pop();
        Kit.pochoir(b, S_UN_NOIR, 'z', 8.0f, 3.4f, z1 + 0.02f, 3.0f, 1.5f);
        Kit.pochoir(b, S_FRAGILE, 'z', 8.0f, 1.6f, z1 + 0.02f, 4.0f, 0.9f);
        b.push().translate(x1 + 0.05f, 3.6f, 6.0f).rotateY(90);                 // thermomètre
        b.bevelBox(-0.6f, -1.0f, 0, 0.6f, 1.0f, 0.2f, 0.06f, SOMBRE);
        b.box(-0.4f, -0.6f, 0.21f, 0.4f, 0.6f, 0.25f, LCD_OFF);
        b.pop();
        decal(b, AUREOLE, 'y', 9.0f, 0.03f, 12.6f, 4.0f, 2.4f, 0);
        jonchee(b, 0.6f, 11.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2201);
    }
}
