package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T6c, seconde moitié : valise diplomatique, terminal biométrique, capsule d'échantillons, râtelier mural, coffre à
 * munitions blindé, station satellite, chariot d'urgence, malle à bijoux, brouilleur, armoire à clés.
 */
final class LotT6c2 {
    private LotT6c2() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t6_valise_diplomatique", "t6", "tissu", "Valise diplomatique plombée", "Sealed diplomatic pouch", LotT6c2::valiseDiplomatique);
        Conteneurs.ajouter("t6_terminal_biometrique", "t6", "metal", "Terminal biométrique", "Biometric terminal", LotT6c2::terminalBiometrique);
        Conteneurs.ajouter("t6_capsule_echantillons", "t6", "metal", "Capsule d'échantillons", "Sample transport capsule", LotT6c2::capsuleEchantillons);
        Conteneurs.ajouter("t6_ratelier_mural", "t6", "metal", "Râtelier mural verrouillé", "Locked wall gun rack", LotT6c2::ratelierMural);
        Conteneurs.ajouter("t6_coffre_munitions_blinde", "t6", "metal", "Coffre à munitions blindé", "Armored ammo chest", LotT6c2::coffreMunitionsBlinde);
        Conteneurs.ajouter("t6_station_satellite", "t6", "plastique", "Station satellite portable", "Portable satellite station", LotT6c2::stationSatellite);
        Conteneurs.ajouter("t6_chariot_urgence", "t6", "metal", "Chariot d'urgence", "Crash cart", LotT6c2::chariotUrgence);
        Conteneurs.ajouter("t6_malle_bijoux", "t6", "bois", "Malle à bijoux de la banque", "Bank jewelry trunk", LotT6c2::malleBijoux);
        Conteneurs.ajouter("t6_brouilleur", "t6", "metal", "Brouilleur militaire", "Military jammer", LotT6c2::brouilleur);
        Conteneurs.ajouter("t6_armoire_cles", "t6", "metal", "Armoire à clés", "Key cabinet", LotT6c2::armoireCles);
        for (String n : new String[]{"t6_terminal_biometrique", "t6_armoire_cles", "t6_malle_bijoux"}) Conteneurs.usure(n, 0.6f);
        Conteneurs.collision("t6_ratelier_mural", new float[]{0.8f, 2.0f, 0.05f, 31.2f, 22.0f, 5.4f});
        Conteneurs.collision("t6_armoire_cles", new float[]{2.6f, 9.0f, 0.05f, 13.4f, 22.0f, 3.5f});
    }

    static final Mat CHROME = AtlasLoot.CHROME, CAOUTCHOUC = AtlasLoot.RUBBER, SOMBRE = AtlasLoot.DARK;

    // ================================================================================================ 11. valise diplomatique
    /** Valise diplomatique : grand sac de toile kaki à soufflet, sangles de cuir, fermeture plombée (cordon, sceau de cire
     *  rouge), cadenas ; posée sur une mallette d'acier rayée, une enveloppe « confidentiel » tombée. */
    static void valiseDiplomatique(MeshBuilder b) {
        b.bevelBox(2.4f, 0, 2.0f, 13.6f, 2.6f, 9.6f, 0.25f, ACIER_NOIR);           // mallette dessous
        b.bevelBox(7.0f, 2.6f, 9.4f, 9.0f, 3.2f, 9.8f, 0.08f, CHROME);
        b.push().translate(8.0f, 2.62f + 3.2f, 5.6f).rotateY(-7);
        b.color(0.55f, 0.52f, 0.38f);
        b.blob(5.4f, 3.2f, 3.4f, 0.75f, 0.08f, 2.4f, 1101, 14, TISSU_USE);
        b.color(1);
        for (float x : new float[]{-2.6f, 2.6f}) {                            // sangles de cuir qui ceinturent le sac
            b.push().translate(x, 0, 0).rotateZ(90);
            b.color(0.36f, 0.24f, 0.15f);
            b.hollowCylinder(3.55f, 3.3f, -0.4f, 0.4f, 16, CUIR_USE);
            b.color(1);
            b.pop();
        }
        b.push().translate(0, 3.0f, 0);                                         // col froncé et cordon
        b.color(0.5f, 0.47f, 0.34f);
        b.cylinder(1.2f, 0, 1.4f, 10, TISSU_USE, TISSU_USE, TISSU_USE);
        b.color(1);
        b.translate(0, 0.8f, 0);
        b.torus(1.25f, 0.12f, 12, 5, 0, 360, AtlasLoot.ROPE);
        b.pop();
        b.push().translate(0.6f, 3.6f, 1.35f);                                  // sceau de cire rouge
        b.color(0.6f, 0.08f, 0.08f);
        b.rotateX(90);
        b.cylinder(0.6f, 0, 0.25f, 12, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
        b.color(1);
        b.pop();
        LotT3bKit.cadenas(b, -0.8f, 3.2f, 1.3f, false);
        b.pop();
        Kit.pochoir(b, S_CONFIDENTIEL, 'z', 8.0f, 1.3f, 9.62f, 5.0f, 1.0f);
        b.push().translate(4.0f, 0.04f, 12.4f).rotateY(24);                    // enveloppe tombée
        b.color(0.85f, 0.78f, 0.6f);
        b.box(-1.8f, 0, -1.1f, 1.8f, 0.12f, 1.1f, PAPIER_KRAFT);
        b.color(1);
        b.pop();
        decal(b, GRIFFURES, 'z', 4.0f, 1.2f, 9.65f, 2.2f, 0.9f, 0);
        jonchee(b, 1.0f, 11.0f, 15.0f, 15.4f, 4, FEUILLES, 1.0f, 1102);
    }

    // ================================================================================================ 12. terminal biométrique
    /** Terminal biométrique sur pied (borne d'accès) : colonne d'acier, tête inclinée avec écran fêlé, lecteur d'empreinte
     *  (vitre), clavier, trappe de service forcée en bas d'où pendent des câbles. */
    static void terminalBiometrique(MeshBuilder b) {
        b.bevelBox(4.0f, 0, 2.0f, 12.0f, 0.8f, 9.0f, 0.15f, ACIER_NOIR);
        b.bevelBox(5.4f, 0.8f, 3.0f, 10.6f, 15.0f, 7.6f, 0.3f, INOX);
        b.push().translate(8.0f, 15.0f, 5.3f).rotateX(28);                     // tête inclinée
        b.bevelBox(-3.6f, 0, -3.0f, 3.6f, 6.4f, 1.8f, 0.4f, ACIER_NOIR);
        b.box(-3.0f, 2.6f, 1.86f, 3.0f, 5.8f, 1.92f, VERRE_FELE);
        b.box(-2.6f, 0.6f, 1.86f, -0.4f, 2.2f, 1.92f, AtlasLoot.GLASS);          // lecteur d'empreinte
        b.box(0.4f, 0.6f, 1.86f, 2.8f, 2.2f, 1.92f, KEYPAD_ACIER);
        b.pop();
        // trappe de service forcée, pendue à un gond
        b.push().translate(5.4f, 2.0f, 7.62f).rotateY(-70);
        b.bevelBox(0, 0, 0, 5.0f, 5.0f, 0.3f, 0.06f, INOX);
        b.pop();
        b.box(6.0f, 2.0f, 7.62f, 10.0f, 7.0f, 7.67f, INTERIEUR_SALE);
        cable(b, lisse(new float[][]{p(7.0f, 6.0f, 5.0f), p(6.8f, 4.0f, 7.4f), p(6.4f, 1.0f, 9.6f), p(5.6f, 0.12f, 11.6f)}, 6), 0.14f, 0.7f, 0.15f, 0.12f);
        cable(b, lisse(new float[][]{p(8.6f, 6.4f, 5.0f), p(9.0f, 3.0f, 8.0f), p(9.6f, 0.12f, 11.0f)}, 6), 0.12f, 0.15f, 0.3f, 0.65f);
        cable(b, lisse(new float[][]{p(9.2f, 5.6f, 5.4f), p(10.2f, 2.0f, 8.4f), p(11.4f, 0.12f, 10.2f)}, 6), 0.1f, 0.85f, 0.75f, 0.15f);
        decal(b, GRIFFURES, 'z', 8.0f, 9.0f, 7.65f, 2.6f, 1.0f, 15);
        coulure(b, 'z', 6.0f, 13.0f, 7.62f, 0.7f, 0);
        boue(b, 'z', 8.0f, 0.8f, 7.62f, 5.0f, 1.0f, 1);
        jonchee(b, 1.0f, 9.5f, 15.0f, 15.0f, 5, PAPIER, 1.2f, 1121);
    }

    // ================================================================================================ 13. capsule d'échantillons
    /** Capsule cylindrique de transport d'échantillons (inox, hublot) couchée sur son berceau ; trappe ouverte sur un
     *  portoir de tubes (deux manquent), étiquette biologique, poignées, un tube brisé au sol. */
    static void capsuleEchantillons(MeshBuilder b) {
        for (float x : new float[]{4.0f, 12.0f}) {                              // berceau
            b.bevelBox(x - 0.6f, 0, 1.4f, x + 0.6f, 2.2f, 9.6f, 0.1f, ACIER_NOIR);
        }
        b.bevelBox(2.6f, 0, 2.0f, 13.4f, 0.5f, 3.0f, 0.06f, ACIER_NOIR);
        b.bevelBox(2.6f, 0, 8.0f, 13.4f, 0.5f, 9.0f, 0.06f, ACIER_NOIR);
        b.push().translate(8.0f, 5.2f, 5.5f).rotateZ(90);
        b.cylinder(3.6f, -6.4f, 6.4f, 20, INOX, INOX, INOX);
        for (float y : new float[]{-6.2f, 6.2f}) {
            b.push().translate(0, y, 0);
            b.torus(3.6f, 0.3f, 20, 6, 0, 360, CHROME);
            b.pop();
        }
        b.pop();
        // trappe ouverte sur le dessus : bords, portoir de tubes
        b.bevelBox(5.0f, 8.6f, 3.6f, 11.0f, 8.95f, 7.4f, 0.1f, ACIER_NOIR);
        b.box(5.4f, 8.96f, 4.0f, 10.6f, 9.0f, 7.0f, SOMBRE);
        for (int i = 0; i < 6; i++) {
            if (i == 2 || i == 4) continue;
            b.push().translate(5.9f + i * 0.85f, 9.0f, 5.5f);
            b.color(0.85f, 0.9f, 0.95f);
            b.cylinder(0.25f, 0, 1.3f, 8, VERRE_SALE, VERRE_SALE, i % 2 == 0 ? ROUGE_SECU : AtlasLoot.YELLOW);
            b.color(1);
            b.pop();
        }
        b.push().translate(11.0f, 8.95f, 5.5f).rotateZ(-115);                 // couvercle relevé
        b.bevelBox(0, -0.3f, -1.9f, 6.0f, 0, 1.9f, 0.08f, INOX);
        b.pop();
        decal(b, BIOHAZARD, 'z', 8.0f, 5.2f, 9.15f, 2.4f, 2.4f, 0);
        Kit.pochoir(b, S_ECHANTILLONS, 'z', 3.6f, 3.4f, 8.6f, 2.6f, 0.9f);
        b.push().translate(10.2f, 0.26f, 12.6f).rotateY(40).rotateZ(90);     // tube brisé
        b.color(0.85f, 0.9f, 0.95f);
        b.cylinder(0.25f, -0.6f, 0.4f, 8, VERRE_SALE, VERRE_SALE, VERRE_SALE);
        b.color(1);
        b.pop();
        decal(b, AUREOLE, 'y', 10.6f, 0.03f, 12.4f, 2.2f, 1.8f, 0);
        debris(b, 9.6f, 13.6f, 1.0f, 5, VERRE_SALE, 1131);
        jonchee(b, 1.0f, 10.5f, 15.0f, 15.5f, 4, FEUILLES, 1.0f, 1132);
    }

    // ================================================================================================ 14. râtelier mural
    /** Râtelier mural verrouillé : panneau d'acier noir à cadre rouge, crosses dans leurs berceaux, barre de verrouillage
     *  relevée (cadenas forcé), deux armes restantes (silhouettes), trois places vides, étiquettes de matricule. */
    static void ratelierMural(MeshBuilder b) {
        float x0 = 0.8f, x1 = 31.2f;
        b.bevelBox(x0, 2.0f, 0.05f, x1, 22.0f, 1.2f, 0.2f, ROUGE_SECU);
        b.bevelBox(x0 + 0.7f, 2.7f, 0.6f, x1 - 0.7f, 21.3f, 1.3f, 0.1f, ACIER_NOIR);
        b.bevelBox(x0 + 0.4f, 3.0f, 1.25f, x1 - 0.4f, 4.4f, 5.4f, 0.15f, ACIER_NOIR);   // berceau des crosses
        for (int k = 0; k < 5; k++) {
            float x = 4.0f + k * 6.0f;
            b.bevelBox(x - 1.4f, 4.4f, 2.0f, x + 1.4f, 4.6f, 4.8f, 0.05f, CAOUTCHOUC);
            b.bevelBox(x - 0.4f, 17.0f, 1.3f, x + 0.4f, 18.6f, 3.6f, 0.08f, CHROME);         // crochet du canon
            Kit.pochoir(b, AtlasLoot.LABEL, 'z', x, 3.7f, 5.42f, 1.8f, 0.6f);
            if (k == 1 || k == 3) {                                            // arme restante (silhouette)
                b.push().translate(x, 4.6f, 3.4f);
                b.bevelBox(-0.5f, 0, -0.6f, 0.5f, 4.6f, 0.6f, 0.15f, AtlasCivil.WALNUT);
                b.bevelBox(-0.35f, 4.6f, -0.5f, 0.35f, 7.8f, 0.5f, 0.1f, SOMBRE);
                b.bevelBox(-0.3f, 6.0f, 0.4f, 0.3f, 7.4f, 1.4f, 0.08f, SOMBRE);           // chargeur
                b.push().translate(0, 7.8f, 0);
                b.cylinder(0.22f, 0, 10.4f, 8, SOMBRE, SOMBRE, SOMBRE);
                b.pop();
                b.pop();
            }
        }
        // barre de verrouillage relevée, cadenas forcé pendu
        b.push().translate(x0 + 1.0f, 12.0f, 3.4f).rotateZ(62);
        b.bevelBox(0, -0.35f, -0.35f, 22.0f, 0.35f, 0.35f, 0.08f, METAL_NU);
        b.pop();
        LotT3bKit.cadenas(b, x1 - 2.6f, 11.6f, 1.35f, true);
        Kit.pochoir(b, AtlasLoot.S_FUSILS, 'z', 16.0f, 20.0f, 1.35f, 8.0f, 1.4f);
        decal(b, GRIFFURES, 'z', 26.0f, 14.0f, 1.36f, 3.0f, 1.2f, 25);
        coulure(b, 'z', x0 + 0.6f, 20.0f, 1.25f, 0.8f, 1);
        jonchee(b, 1.0f, 6.0f, 31.0f, 14.0f, 6, PAPIER, 1.2f, 1141);
    }

    // ================================================================================================ 15. coffre à munitions blindé
    /** Coffre à munitions blindé bas (deux blocs) : caisse rouge à nervures, ouïes d'aération, couvercle ouvert sur des
     *  bandes de cartouches et des boîtes, verrous à leviers, roulettes bloquées. */
    static void coffreMunitionsBlinde(MeshBuilder b) {
        float x0 = 1.0f, x1 = 31.0f, z0 = 1.6f, z1 = 11.4f, h = 7.4f;
        b.bevelBox(x0, 1.0f, z0, x1, h, z1, 0.3f, ROUGE_SECU);
        for (float x : new float[]{3.0f, 29.0f}) for (float z : new float[]{3.2f, 9.8f}) {
            b.push().translate(x, 0.5f, z).rotateZ(90);
            b.cylinder(0.5f, -0.3f, 0.3f, 10, CAOUTCHOUC, CAOUTCHOUC, CAOUTCHOUC);
            b.pop();
            b.bevelBox(x - 0.5f, 0.5f, z - 0.4f, x + 0.5f, 1.05f, z + 0.4f, 0.06f, METAL_NU);
        }
        for (int k = 0; k < 6; k++) {                                          // nervures et ouïes
            float x = 4.0f + k * 4.8f;
            b.bevelBox(x - 0.3f, 1.4f, z1 - 0.05f, x + 0.3f, h - 0.4f, z1 + 0.35f, 0.08f, ROUGE_SECU);
        }
        for (int k = 0; k < 5; k++) b.box(5.0f + k * 4.8f, 2.4f, z1 + 0.06f, 7.6f + k * 4.8f, 2.8f, z1 + 0.1f, SOMBRE);
        b.box(x0 + 0.5f, h - 0.02f, z0 + 0.5f, x1 - 0.5f, h + 0.04f, z1 - 0.5f, INTERIEUR_SALE);
        // contenu : bandes de cartouches en vrac et boîtes
        for (int k = 0; k < 3; k++) {
            float xa = 4.0f + k * 8.0f;
            b.push().translate(xa, h + 0.05f, 4.0f + (k % 2) * 3.0f).rotateY(k * 23 - 20);
            for (int i = 0; i < 9; i++) {
                b.push().translate(i * 0.55f, 0.25f, 0).rotateX(90);
                cartoucheSimple(b, 1.6f, 0.2f, AtlasCivil.BRASS, METAL_NU);
                b.pop();
            }
            b.pop();
        }
        b.color(0.4f, 0.44f, 0.3f);
        b.bevelBox(22.0f, h + 0.05f, 3.0f, 28.4f, h + 2.6f, 7.0f, 0.15f, PEINTURE_USEE);
        b.color(1);
        // couvercle ouvert contre le mur
        b.push().translate(0, h, z0).rotateX(-92).translate(0, -h, -z0);
        b.bevelBox(x0 - 0.1f, h + 0.06f, z0, x1 + 0.1f, h + 1.0f, z1 + 0.1f, 0.2f, ROUGE_SECU);
        b.pop();
        for (float x : new float[]{9.0f, 23.0f}) fermoirOuvert(b, x, h - 0.6f, z1 + 0.36f, 40, CHROME);
        Kit.pochoir(b, AtlasLoot.S_DANGER, 'z', 16.0f, 4.6f, z1 + 0.37f, 6.0f, 1.4f);
        decal(b, GRIFFURES, 'z', 26.0f, 3.2f, z1 + 0.38f, 3.0f, 1.0f, 0);
        coulure(b, 'z', 4.0f, h - 0.4f, z1 + 0.36f, 0.8f, 2);
        boue(b, 'z', 16.0f, 1.0f, z1 + 0.37f, 26.0f, 1.0f, 0);
        jonchee(b, 1.0f, 12.2f, 31.0f, 15.5f, 7, FEUILLES, 1.0f, 1151);
    }

    // ================================================================================================ 16. station satellite
    /** Station satellite portable : valise renforcée ouverte (écran, clavier), parabole repliable déployée sur son pied,
     *  câble coaxial, batterie de secours, poussière. */
    static void stationSatellite(MeshBuilder b) {
        b.bevelBox(1.4f, 0, 4.0f, 9.6f, 3.4f, 11.6f, 0.35f, SOMBRE);
        b.box(2.0f, 3.38f, 4.6f, 9.0f, 3.44f, 11.0f, CLAVIER_PC);
        b.push().translate(0, 3.4f, 4.0f).rotateX(-80).translate(0, -3.4f, -4.0f);
        b.bevelBox(1.4f, 3.44f, 4.0f, 9.6f, 4.6f, 11.6f, 0.3f, SOMBRE);
        b.box(2.2f, 3.38f, 4.8f, 8.8f, 3.42f, 10.8f, ECRAN_OFF);
        b.pop();
        Kit.coins(b, 1.4f, 0, 4.0f, 9.6f, 3.4f, 11.6f, 0.7f, CHROME);
        // parabole sur trépied
        float px = 11.8f, pz = 6.4f;
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(90 + k * 120);
            b.tube(new float[][]{p(px, 6.2f, pz), p(px + (float) Math.cos(a) * 3.0f, 0.18f, pz + (float) Math.sin(a) * 3.0f)},
                    fill(2, 0.15f), 6, METAL_NU, true, true, 0, false);
        }
        b.push().translate(px, 6.2f, pz);
        b.cylinder(0.3f, 0, 2.2f, 8, METAL_NU, METAL_NU, METAL_NU);
        b.translate(0, 2.6f, 0).rotateX(-40);
        b.push().scale(1, 0.35f, 1);
        b.sphereSection(4.0f, 18, 5, 0, 70, AtlasLoot.WHITE);
        b.pop();
        b.push().translate(0, 0.2f, 0);
        b.cylinder(0.2f, 0, 3.2f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.translate(0, 3.2f, 0);
        b.cylinder(0.5f, 0, 0.8f, 8, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.pop();
        cable(b, auSol(lisse(new float[][]{p(px, 6.4f, pz), p(px - 1.0f, 3.0f, pz + 2.0f), p(px - 2.6f, 0.14f, pz + 3.6f), p(8.0f, 0.14f, 12.6f),
                p(6.0f, 1.6f, 11.7f)}, 6), 0.13f), 0.12f, 0.1f, 0.1f, 0.1f);
        b.push().translate(4.0f, 0, 13.6f).rotateY(-14);                       // batterie de secours
        b.color(0.3f, 0.34f, 0.28f);
        b.bevelBox(-1.8f, 0, -1.0f, 1.8f, 1.6f, 1.0f, 0.15f, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        decal(b, GRIFFURES, 'z', 5.0f, 1.6f, 11.65f, 2.4f, 1.0f, 0);
        jonchee(b, 1.0f, 1.0f, 15.0f, 3.6f, 4, FEUILLES, 1.0f, 1161);
    }

    // ================================================================================================ 17. chariot d'urgence
    /** Chariot d'urgence d'hôpital militaire : caisson rouge à tiroirs (un ouvert, renversé), défibrillateur dessus avec
     *  palettes pendantes, bouteille d'oxygène sanglée sur le côté, roulettes, ampoules brisées. */
    static void chariotUrgence(MeshBuilder b) {
        float x0 = 2.4f, x1 = 13.6f, z0 = 1.4f, z1 = 9.8f, h = 15.4f;
        for (float x : new float[]{x0 + 1.0f, x1 - 1.0f}) for (float z : new float[]{z0 + 1.0f, z1 - 1.0f}) LotT6c.roulette(b, x, z, 0.6f);
        b.bevelBox(x0, 1.6f, z0, x1, h, z1, 0.3f, ROUGE_SECU);
        b.bevelBox(x0 - 0.3f, h, z0 - 0.3f, x1 + 0.3f, h + 0.5f, z1 + 0.3f, 0.15f, INOX);
        for (int k = 0; k < 4; k++) {
            float ya = 2.2f + k * 3.2f, sort = k == 1 ? 3.8f : 0;
            b.bevelBox(x0 + 0.4f, ya, z1 - 0.2f + sort, x1 - 0.4f, ya + 2.8f, z1 + 0.15f + sort, 0.1f, ROUGE_SECU);
            b.bevelBox(7.0f, ya + 1.9f, z1 + 0.15f + sort, 9.0f, ya + 2.3f, z1 + 0.4f + sort, 0.05f, CHROME);
            if (k == 1) {
                niche(b, INTERIEUR_SALE, x0 + 0.6f, ya + 0.2f, z1 - 0.15f, x1 - 0.6f, ya + 2.6f, z1 - 0.2f + sort - 0.05f);
                for (int i = 0; i < 4; i++) {
                    b.push().translate(4.0f + i * 1.8f, ya + 0.25f, z1 + 1.6f).rotateY(i * 35);
                    b.color(0.9f, 0.92f, 0.95f);
                    b.bevelBox(-0.6f, 0, -0.35f, 0.6f, 0.5f, 0.35f, 0.08f, PLASTIQUE_USE);
                    b.color(1);
                    b.pop();
                }
            }
        }
        // défibrillateur, palettes pendantes
        b.push().translate(6.0f, h + 0.5f, 5.0f).rotateY(8);
        b.color(0.85f, 0.75f, 0.2f);
        b.bevelBox(-2.6f, 0, -2.0f, 2.6f, 3.0f, 2.0f, 0.3f, PLASTIQUE_USE);
        b.color(1);
        b.box(-1.6f, 1.2f, 2.03f, 1.6f, 2.6f, 2.08f, ECRAN_OFF);
        b.pop();
        cable(b, lisse(new float[][]{p(8.0f, h + 1.6f, 6.8f), p(10.4f, h + 0.6f, 9.0f), p(11.6f, 10.0f, 11.0f), p(11.4f, 7.0f, 11.2f)}, 6), 0.12f, 0.15f, 0.15f, 0.15f);
        b.push().translate(11.4f, 6.2f, 11.2f).rotateX(10);
        b.color(0.85f, 0.75f, 0.2f);
        b.bevelBox(-0.9f, 0, -0.5f, 0.9f, 0.8f, 0.5f, 0.12f, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        // bouteille d'oxygène sanglée sur le flanc droit
        b.push().translate(x1 + 1.3f, 1.8f, 5.6f);
        b.color(0.9f, 0.9f, 0.88f);
        b.cylinder(1.2f, 0, 9.0f, 14, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
        b.color(1);
        b.translate(0, 9.0f, 0);
        b.cylinder(0.4f, 0, 1.2f, 8, CHROME, CHROME, CHROME);
        b.pop();
        decal(b, CROIX_MED, 'z', 8.0f, 12.6f, z1 + 0.05f, 2.6f, 2.6f, 0);
        decal(b, GRIFFURES, 'z', 4.6f, 4.0f, z1 + 0.2f, 2.0f, 0.8f, 0);
        debris(b, 6.0f, 13.6f, 1.6f, 6, VERRE_SALE, 1171);
        jonchee(b, 1.0f, 12.0f, 15.0f, 15.5f, 4, PAPIER, 1.2f, 1172);
    }

    // ================================================================================================ 18. malle à bijoux
    /** Malle à bijoux de la salle des coffres : malle de bois sombre ferrée de laiton, couvercle ouvert, plateaux de
     *  velours rouge (montres, colliers, bagues), un plateau sorti et renversé, perles au sol. */
    static void malleBijoux(MeshBuilder b) {
        float x0 = 2.0f, x1 = 14.0f, z0 = 3.0f, z1 = 10.6f, h = 5.6f;
        b.bevelBox(x0, 0.4f, z0, x1, 1.05f, z1, 0.2f, AtlasCivil.WALNUT);                // fond et parois (malle creuse)
        b.bevelBox(x0, 1.0f, z0, x0 + 0.5f, h, z1, 0.12f, AtlasCivil.WALNUT);
        b.bevelBox(x1 - 0.5f, 1.0f, z0, x1, h, z1, 0.12f, AtlasCivil.WALNUT);
        b.bevelBox(x0 + 0.45f, 1.0f, z0, x1 - 0.45f, h, z0 + 0.5f, 0.12f, AtlasCivil.WALNUT);
        b.bevelBox(x0 + 0.45f, 1.0f, z1 - 0.5f, x1 - 0.45f, h, z1, 0.12f, AtlasCivil.WALNUT);
        for (float x : new float[]{x0 + 0.6f, x1 - 1.4f}) b.bevelBox(x, 0, z0 + 0.5f, x + 0.8f, 0.42f, z1 - 0.5f, 0.06f, AtlasCivil.BRASS);
        Kit.coins(b, x0, 0.4f, z0, x1, h, z1, 0.8f, AtlasCivil.BRASS);
        LotT3bKit.interieurHaut(b, x0 + 0.55f, 1.1f, z0 + 0.55f, x1 - 0.55f, h - 0.05f, z1 - 0.55f, AtlasCivil.FABRIC_RED, AtlasCivil.FABRIC_RED);
        b.bevelBox(x0 + 0.6f, 3.6f, z0 + 0.6f, x1 - 0.6f, 4.0f, z1 - 0.6f, 0.05f, AtlasCivil.FABRIC_RED);
        for (int i = 0; i < 4; i++) {                                           // montres et bagues
            b.push().translate(4.0f + i * 2.6f, 4.12f, 5.6f).rotateX(90);
            b.torus(0.55f, 0.12f, 12, 5, 0, 360, i % 2 == 0 ? OR_TERNI : CHROME);
            b.pop();
            b.push().translate(4.0f + i * 2.6f, 4.05f, 8.2f);
            b.cylinder(0.6f, 0, 0.3f, 12, OR_TERNI, OR_TERNI, CADRAN);
            b.pop();
        }
        b.push().translate(0, h, z0).rotateX(-86).translate(0, -h, -z0);       // couvercle bombé ouvert
        b.bevelBox(x0, h + 0.05f, z0, x1, h + 1.6f, z1, 0.5f, AtlasCivil.WALNUT);
        b.box(x0 + 0.6f, h - 0.03f, z0 + 0.6f, x1 - 0.6f, h + 0.0f, z1 - 0.6f, AtlasCivil.FABRIC_RED);
        b.pop();
        Kit.cadenas(b, 8.0f, h - 0.6f, z1);
        // plateau sorti renversé, colliers et perles au sol
        b.push().translate(9.0f, 0.5f, 13.2f).rotateY(-16).rotateZ(-8);
        b.bevelBox(-3.2f, 0, -1.8f, 3.2f, 0.45f, 1.8f, 0.08f, AtlasCivil.FABRIC_RED);
        b.pop();
        chain(b, new float[][]{p(4.0f, 0.12f, 12.0f), p(5.0f, 0.12f, 13.0f), p(6.2f, 0.12f, 13.4f), p(7.0f, 0.12f, 14.6f)});
        for (int i = 0; i < 7; i++) {
            b.push().translate(4.0f + i * 1.3f, 0.2f, 14.6f + (i % 3) * 0.3f);
            b.sphere(0.2f, 6, 4, AtlasLoot.WHITE);
            b.pop();
        }
        decal(b, GRIFFURES, 'z', 11.0f, 2.6f, z1 + 0.05f, 2.4f, 0.9f, 0);
        boue(b, 'z', 8.0f, 0.4f, z1, 10.0f, 0.9f, 0);
    }

    static void chain(MeshBuilder b, float[][] chemin) {
        b.chain(chemin, 0.45f, 0.25f, 0.06f, OR_TERNI);
    }

    // ================================================================================================ 19. brouilleur
    /** Brouilleur militaire : caisson kaki sur trépied, six antennes fouets de longueurs différentes (une pliée),
     *  panneau de commande, ventilateur, groupe de batteries au sol relié par un câble. */
    static void brouilleur(MeshBuilder b) {
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(30 + k * 120);
            b.tube(new float[][]{p(8.0f, 6.0f, 6.0f), p(8.0f + (float) Math.cos(a) * 4.0f, 0.1f, 6.0f + (float) Math.sin(a) * 4.0f)},
                    fill(2, 0.2f), 6, METAL_NU, true, true, 0, false);
        }
        b.color(0.4f, 0.44f, 0.32f);
        b.bevelBox(3.6f, 6.0f, 3.0f, 12.4f, 11.6f, 9.0f, 0.35f, PEINTURE_USEE);
        b.color(1);
        b.box(4.4f, 6.8f, 9.03f, 11.6f, 10.8f, 9.08f, PANNEAU_RADIO);
        b.push().translate(11.0f, 9.0f, 3.0f).rotateX(-90);                   // ventilateur à l'arrière-côté
        b.cylinder(1.2f, -0.05f, 0.05f, 12, TOLE_PERFOREE, TOLE_PERFOREE, TOLE_PERFOREE);
        b.pop();
        float[] hs = {14.0f, 11.0f, 16.0f, 9.0f, 12.0f, 13.0f};
        for (int k = 0; k < 6; k++) {
            float x = 4.6f + k * 1.4f;
            b.push().translate(x, 11.6f, 5.8f + (k % 2) * 0.8f);
            b.cylinder(0.3f, 0, 0.6f, 8, SOMBRE, SOMBRE, SOMBRE);
            if (k == 3) {
                b.translate(0, 0.6f, 0).rotateZ(-70);
            }
            b.cylinder(0.09f, 0.6f, hs[k], 5, SOMBRE, SOMBRE, SOMBRE);
            b.pop();
        }
        b.push().translate(3.0f, 0, 12.6f).rotateY(12);                        // batteries
        b.color(0.3f, 0.3f, 0.32f);
        b.bevelBox(-2.4f, 0, -1.4f, 2.4f, 2.4f, 1.4f, 0.2f, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        cable(b, lisse(new float[][]{p(4.0f, 7.0f, 8.8f), p(3.6f, 3.0f, 10.4f), p(3.2f, 2.4f, 12.0f)}, 6), 0.12f, 0.1f, 0.1f, 0.1f);
        Kit.pochoir(b, AtlasLoot.S_DANGER, 'z', 8.0f, 10.0f, 9.1f, 4.0f, 0.9f);
        decal(b, GRIFFURES, 'X', 12.4f, 8.0f, 6.0f, 2.4f, 1.0f, 0);
        jonchee(b, 1.0f, 10.5f, 15.0f, 15.5f, 5, FEUILLES, 1.0f, 1191);
    }

    // ================================================================================================ 20. armoire à clés
    /** Armoire à clés murale (salle de garde) : boîtier d'acier gris, porte vitrée ouverte et fêlée, cinq rangées de
     *  crochets numérotés, clés (étiquettes de couleur) dont la moitié manque, quelques clés au sol. */
    static void armoireCles(MeshBuilder b) {
        float x0 = 2.6f, x1 = 13.4f, y0 = 9.0f, y1 = 22.0f, z1 = 3.0f;
        b.bevelBox(x0, y0, 0.05f, x1, y1, 0.45f, 0.1f, ACIER_NOIR);
        b.bevelBox(x0, y0, 0.4f, x0 + 0.5f, y1, z1, 0.1f, ACIER_NOIR);
        b.bevelBox(x1 - 0.5f, y0, 0.4f, x1, y1, z1, 0.1f, ACIER_NOIR);
        b.bevelBox(x0 + 0.45f, y0, 0.4f, x1 - 0.45f, y0 + 0.5f, z1, 0.1f, ACIER_NOIR);
        b.bevelBox(x0 + 0.45f, y1 - 0.5f, 0.4f, x1 - 0.45f, y1, z1, 0.1f, ACIER_NOIR);
        niche(b, METAL_NU, x0 + 0.55f, y0 + 0.55f, 0.5f, x1 - 0.55f, y1 - 0.55f, z1 - 0.05f);
        for (int l = 0; l < 5; l++) {
            float y = y0 + 2.2f + l * 2.4f;
            b.box(x0 + 0.6f, y + 0.4f, 0.56f, x1 - 0.6f, y + 0.9f, 0.62f, NUMEROS_PLAQUE);
            for (int c = 0; c < 7; c++) {
                float x = x0 + 1.4f + c * 1.35f;
                b.push().translate(x, y, 0.48f).rotateX(90);
                b.cylinder(0.08f, 0, 0.9f, 4, CHROME, CHROME, CHROME);
                b.pop();
                if ((l * 7 + c) % 3 == 1 || (l + c) % 4 == 0) continue;     // clé absente
                b.push().translate(x, y - 0.1f, 1.1f);
                b.torus(0.3f, 0.06f, 8, 4, 0, 360, CHROME);
                b.translate(0, -0.6f, 0);
                b.bevelBox(-0.12f, -1.0f, -0.04f, 0.12f, 0, 0.04f, 0.03f, AtlasCivil.BRASS);
                float[][] cols = {{0.8f, 0.2f, 0.15f}, {0.2f, 0.45f, 0.8f}, {0.9f, 0.8f, 0.2f}, {0.3f, 0.7f, 0.3f}};
                float[] co = cols[(l + c) % 4];
                b.color(co[0], co[1], co[2]);
                b.bevelBox(-0.35f, 0.3f, -0.07f, 0.35f, 0.9f, 0.07f, 0.04f, PLASTIQUE_USE);
                b.color(1);
                b.pop();
            }
        }
        // porte vitrée ouverte (gond à droite)
        b.push().translate(x1, 0, z1).rotateY(85);
        b.bevelBox(-(x1 - x0), y0, 0, 0, y0 + 0.6f, 0.4f, 0.06f, ACIER_NOIR);
        b.bevelBox(-(x1 - x0), y1 - 0.6f, 0, 0, y1, 0.4f, 0.06f, ACIER_NOIR);
        b.bevelBox(-(x1 - x0), y0 + 0.55f, 0, -(x1 - x0) + 0.6f, y1 - 0.55f, 0.4f, 0.06f, ACIER_NOIR);
        b.bevelBox(-0.6f, y0 + 0.55f, 0, 0, y1 - 0.55f, 0.4f, 0.06f, ACIER_NOIR);
        b.pop();
        // clés tombées au sol
        for (int i = 0; i < 3; i++) {
            b.push().translate(5.0f + i * 2.4f, 0.04f, 7.0f + (i % 2) * 1.6f).rotateY(i * 50).rotateX(90);
            b.bevelBox(-0.12f, -1.0f, -0.04f, 0.12f, 0, 0.04f, 0.03f, AtlasCivil.BRASS);
            b.pop();
        }
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 8.0f, y1 - 0.9f, z1 + 0.03f, 2.6f, 0.7f);
        coulure(b, 'z', x0 + 0.5f, y0 + 1.0f, z1 + 0.01f, 0.6f, 0);
        jonchee(b, 1.0f, 4.0f, 15.0f, 12.0f, 5, PAPIER, 1.2f, 1201);
    }

    static final Mat NUMEROS_PLAQUE = LotT3bAtlas.NUMEROS;
}
