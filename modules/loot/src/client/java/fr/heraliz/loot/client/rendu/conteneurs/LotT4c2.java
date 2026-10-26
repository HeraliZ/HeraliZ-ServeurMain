package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T4c, seconde moitié : caisse de fumigènes, porte-cartes d'officier, caisse de rangers, portant à brelages,
 * caisse de conserves, sac à eau, caisse de lampes, cantine d'officier.
 */
final class LotT4c2 {
    private LotT4c2() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t4_caisse_fumigenes", "t4", "bois", "Caisse de fumigènes", "Smoke grenade crate", LotT4c2::caisseFumigenes);
        Conteneurs.ajouter("t4_porte_cartes", "t4", "tissu", "Porte-cartes d'officier", "Officer's map case", LotT4c2::porteCartes);
        Conteneurs.ajouter("t4_caisse_rangers", "t4", "carton", "Caisse de rangers", "Combat boot crate", LotT4c2::caisseRangers);
        Conteneurs.ajouter("t4_portant_brelages", "t4", "metal", "Portant à brelages", "Webbing rack", LotT4c2::portantBrelages);
        Conteneurs.ajouter("t4_caisse_conserves", "t4", "bois", "Caisse de conserves de l'armée", "Army canned food crate", LotT4c2::caisseConserves);
        Conteneurs.ajouter("t4_sac_eau", "t4", "tissu", "Sac à eau sur trépied", "Water bag on tripod", LotT4c2::sacEau);
        Conteneurs.ajouter("t4_caisse_lampes", "t4", "bois", "Caisse de lampes de campagne", "Field lamp crate", LotT4c2::caisseLampes);
        Conteneurs.ajouter("t4_cantine_officier", "t4", "metal", "Cantine d'officier", "Officer's trunk", LotT4c2::cantineOfficier);
    }

    static final Mat CHROME = AtlasLoot.CHROME, SOMBRE = AtlasLoot.DARK;
    static final Mat OLIVE = LotT4bAtlas.BOIS_OLIVE, KAKI = LotT4bAtlas.BOIS_KAKI, TOILE = LotT4bAtlas.TOILE, ACIER = LotT4bAtlas.ACIER;

    // ================================================================================================ 1. fumigènes
    /** Caisse de fumigènes : grenades cylindriques olive à bouchon de couleur (rouge, vert, jaune, violet, blanc)
     *  rangées debout, deux couchées au sol, une ouverte qui a coloré le sol. */
    static void caisseFumigenes(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.6f, 0, 2.4f, 14.4f, 4.6f, 11.2f, OLIVE);
        float[][] couleurs = {{0.8f, 0.15f, 0.12f}, {0.2f, 0.6f, 0.25f}, {0.9f, 0.8f, 0.2f}, {0.5f, 0.25f, 0.6f}, {0.92f, 0.92f, 0.9f}};
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 3; j++) {
                if ((i + j * 5) % 6 == 4) continue;
                b.push().translate(3.6f + i * 2.2f, 0.52f, 4.4f + j * 2.4f);
                b.color(0.42f, 0.46f, 0.32f);
                b.cylinder(0.85f, 0, 3.8f, 12, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
                float[] c = couleurs[(i + j) % 5];
                b.color(c[0], c[1], c[2]);
                b.cylinder(0.88f, 3.8f, 4.3f, 12, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
                b.color(1);
                b.pop();
            }
        }
        for (int k = 0; k < 2; k++) {
            b.push().translate(6.0f + k * 4.0f, 0.93f, 13.6f).rotateY(k * 50 - 20).rotateZ(90);
            b.color(0.42f, 0.46f, 0.32f);
            b.cylinder(0.85f, -2.1f, 2.1f, 12, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
            b.color(1);
            b.pop();
        }
        b.color(0.6f, 0.3f, 0.65f);
        decal(b, AUREOLE, 'y', 12.4f, 0.16f, 13.4f, 3.6f, 2.6f, 0);
        b.color(1);
        Kit.pochoir(b, LotT4bAtlas.S_DANGER, 'z', 8.0f, 2.4f, 11.3f, 5.4f, 1.3f);
        jonchee(b, 0.6f, 12.0f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 3101);
    }

    // ================================================================================================ 2. porte-cartes
    /** Porte-cartes d'officier : sacoche de cuir plate ouverte sur une caisse retournée, cartes pliées, boussole,
     *  rapporteur, crayon gras, carnet d'ordres, jumelles. */
    static void porteCartes(MeshBuilder b) {
        b.bevelBox(3.0f, 0, 2.0f, 13.0f, 5.0f, 10.0f, 0.3f, KAKI);              // caisse retournée (table)
        b.push().translate(8.0f, 5.02f, 6.0f).rotateY(-6);
        b.color(0.35f, 0.25f, 0.16f);
        b.bevelBox(-3.6f, 0, -2.4f, 3.6f, 0.5f, 2.4f, 0.15f, CUIR_USE);
        b.color(1);
        b.box(-3.3f, 0.51f, -2.1f, 3.3f, 0.56f, 2.1f, LotT4bAtlas.CARTE);
        b.push().translate(0, 0.5f, -2.4f).rotateX(-120);                      // rabat ouvert
        b.color(0.35f, 0.25f, 0.16f);
        b.bevelBox(-3.6f, 0, 0, 3.6f, 0.4f, 3.0f, 0.12f, CUIR_USE);
        b.color(1);
        b.pop();
        b.push().translate(2.0f, 0.56f, 1.0f);                                  // boussole
        b.cylinder(0.8f, 0, 0.35f, 14, SOMBRE, SOMBRE, LotT56Atlas.CADRAN);
        b.pop();
        b.pop();
        b.push().translate(4.6f, 5.05f, 8.6f).rotateY(30);                     // carnet d'ordres
        b.color(0.3f, 0.32f, 0.26f);
        b.bevelBox(-1.2f, 0, -0.9f, 1.2f, 0.3f, 0.9f, 0.06f, CUIR_USE);
        b.color(1);
        b.pop();
        b.push().translate(11.4f, 5.05f, 8.8f).rotateY(-25);                   // jumelles
        for (float s : new float[]{-0.55f, 0.55f}) {
            b.push().translate(s, 0.5f, 0).rotateX(90);
            b.cylinder(0.48f, -1.1f, 1.1f, 10, SOMBRE, SOMBRE, SOMBRE);
            b.pop();
        }
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_LOT, 'z', 8.0f, 2.4f, 10.02f, 4.0f, 1.0f);
        jonchee(b, 0.6f, 10.6f, 15.4f, 15.6f, 6, PAPIER, 1.2f, 3111);
    }

    // ================================================================================================ 3. rangers
    /** Caisse en carton de rangers neuves : carton olive éventré, paires de rangers rangées, une paire sortie au sol,
     *  lacets, papier de soie. */
    static void caisseRangers(MeshBuilder b) {
        b.color(0.5f, 0.48f, 0.36f);
        b.bevelBox(2.0f, 0, 2.0f, 14.0f, 6.0f, 10.4f, 0.2f, LotT4bAtlas.CARTON);
        b.color(1);
        b.box(2.5f, 5.98f, 2.5f, 13.5f, 6.04f, 9.9f, INTERIEUR_SALE);
        for (int k = 0; k < 4; k++) {                                            // rabats ouverts
            b.push();
            switch (k) {
                case 0 -> b.translate(2.0f, 6.0f, 6.2f).rotateZ(150);
                case 1 -> b.translate(14.0f, 6.0f, 6.2f).rotateZ(-150);
                case 2 -> b.translate(8.0f, 6.0f, 10.4f).rotateX(110);
                default -> b.translate(8.0f, 6.0f, 2.0f).rotateX(-40);
            }
            b.color(0.5f, 0.48f, 0.36f);
            if (k < 2) b.box(-0.1f, 0, -4.0f, 0.1f, 3.2f, 4.0f, LotT4bAtlas.CARTON);
            else b.box(-5.8f, 0, -0.1f, 5.8f, 3.2f, 0.1f, LotT4bAtlas.CARTON);
            b.color(1);
            b.pop();
        }
        for (int k = 0; k < 3; k++) {                                            // rangers qui dépassent
            b.push().translate(4.2f + k * 3.4f, 3.8f, 3.4f).rotateY(k * 8 - 8);
            LotT3bKit.ranger(b);
            b.pop();
        }
        for (int k = 0; k < 2; k++) {                                            // paire au sol
            b.push().translate(6.0f + k * 2.2f, 0, 12.0f).rotateY(70 + k * 10);
            LotT3bKit.ranger(b);
            b.pop();
        }
        Kit.pochoir(b, LotT4bAtlas.S_MATERIEL, 'z', 8.0f, 3.0f, 10.42f, 5.6f, 1.3f);
        jonchee(b, 0.6f, 11.0f, 15.4f, 15.6f, 4, PAPIER, 1.2f, 3121);
    }

    // ================================================================================================ 4. portant à brelages
    /** Portant à brelages (deux blocs) : barre sur deux montants, gilets de combat et brelages suspendus à des cintres
     *  (poches, porte-chargeurs), casque accroché, ceinturon tombé au sol. */
    static void portantBrelages(MeshBuilder b) {
        float x0 = 1.4f, x1 = 30.6f, h = 26.0f;
        for (float x : new float[]{x0, x1 - 0.8f}) {
            b.bevelBox(x, 0, 1.0f, x + 0.8f, h, 1.8f, 0.1f, ACIER);
            b.bevelBox(x - 1.4f, 0, 0.6f, x + 2.2f, 0.6f, 6.0f, 0.1f, ACIER);
        }
        b.push().translate(x0 + 0.1f, h - 1.2f, 1.4f).rotateZ(-90);
        b.cylinder(0.35f, 0, x1 - x0 - 0.2f, 8, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        for (int k = 0; k < 4; k++) {
            float x = 5.0f + k * 7.0f;
            b.push().translate(x, h - 1.2f, 1.4f);
            b.torus(0.4f, 0.08f, 8, 4, 0, 180, METAL_NU);
            b.translate(0, -1.0f, 0.4f).rotateY(k * 6 - 9);
            b.color(0.4f, 0.43f, 0.32f);
            b.bevelBox(-2.6f, -9.0f, 0, 2.6f, 0, 1.8f, 0.5f, LotT4bAtlas.TOILE);   // gilet
            b.color(1);
            for (int i = 0; i < 3; i++) {                                       // porte-chargeurs
                b.color(0.36f, 0.4f, 0.3f);
                b.bevelBox(-2.2f + i * 1.5f, -7.4f, 1.75f, -1.0f + i * 1.5f, -4.8f, 2.6f, 0.15f, LotT4bAtlas.TOILE);
                b.color(1);
            }
            b.pop();
        }
        b.push().translate(28.0f, h - 3.0f, 2.6f);                             // casque accroché
        b.color(0.42f, 0.46f, 0.32f);
        b.push().scale(1.0f, 0.78f, 1.1f);
        b.sphereSection(2.0f, 14, 5, 0, 90, PEINTURE_USEE);
        b.pop();
        b.color(1);
        b.pop();
        b.push().translate(12.0f, 0.15f, 10.6f).rotateY(-10);                 // ceinturon au sol
        b.color(0.36f, 0.4f, 0.3f);
        b.torus(3.0f, 0.15f, 18, 4, 0, 300, LotT4bAtlas.SANGLE);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 7.0f, 31.4f, 15.6f, 6, FEUILLES, 1.0f, 3131);
    }

    // ================================================================================================ 5. conserves
    /** Caisse de conserves de l'armée : caisse olive ouverte, boîtes de conserve olive à étiquette pochoir, une pile
     *  renversée devant, ouvre-boîte, une boîte cabossée ouverte (rouillée). */
    static void conserve(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z);
        b.color(0.4f, 0.44f, 0.3f);
        b.cylinder(1.0f, 0, 1.8f, 12, PEINTURE_USEE, METAL_NU, METAL_NU);
        b.color(1);
        b.pop();
    }

    static void caisseConserves(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.6f, 0, 2.4f, 14.4f, 4.0f, 11.2f, OLIVE);
        for (int i = 0; i < 5; i++) for (int j = 0; j < 3; j++) conserve(b, 3.8f + i * 2.2f, 0.52f, 4.4f + j * 2.4f);
        for (int i = 0; i < 3; i++) conserve(b, 5.0f + i * 2.2f, 2.34f, 5.6f + (i % 2) * 0.4f);
        for (int k = 0; k < 3; k++) {                                            // pile renversée
            b.push().translate(4.0f + k * 2.4f, 1.0f, 13.4f + (k % 2) * 0.8f).rotateY(k * 35).rotateZ(90);
            b.color(0.4f, 0.44f, 0.3f);
            b.cylinder(1.0f, -0.9f, 0.9f, 12, PEINTURE_USEE, METAL_NU, METAL_NU);
            b.color(1);
            b.pop();
        }
        b.push().translate(12.6f, 0, 13.2f);                                   // boîte ouverte rouillée
        b.cylinder(1.0f, 0, 1.6f, 12, ROUILLE, ROUILLE, INTERIEUR_SALE);
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_RATIONS, 'z', 8.0f, 2.2f, 11.3f, 5.4f, 1.3f);
        jonchee(b, 0.6f, 12.0f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 3141);
    }

    // ================================================================================================ 6. sac à eau
    /** Sac à eau sur trépied (sac Lyster) : poche de toile suspendue, robinets, trépied de perches, gobelets, bidons ;
     *  flaque séchée dessous. */
    static void sacEau(MeshBuilder b) {
        float cx = 8.0f, cz = 6.0f, top = 26.0f;
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(90 + k * 120);
            b.tube(new float[][]{p(cx, top, cz), p(cx + (float) Math.cos(a) * 5.0f, 0.2f, cz + (float) Math.sin(a) * 5.0f)}, fill(2, 0.3f), 6,
                    BOIS_GRIS, true, true, 0, false);
        }
        b.tube(new float[][]{p(cx, top, cz), p(cx, top - 6.0f, cz)}, fill(2, 0.08f), 4, AtlasLoot.ROPE, true, true, 0, false);
        b.push().translate(cx, top - 13.0f, cz);
        b.color(0.48f, 0.5f, 0.38f);
        b.lathe(new float[][]{{0.01f, 0}, {2.4f, 0.4f}, {3.0f, 2.6f}, {2.8f, 5.6f}, {1.8f, 6.8f}, {0.5f, 7.2f}, {0.01f, 7.2f}}, 14, 0, 360,
                TOILE, 1, 1, 0.12f, 2.0f, 3151, false);
        b.color(1);
        for (int k = 0; k < 4; k++) {                                           // robinets
            double a = Math.toRadians(k * 90 + 45);
            b.push().translate((float) Math.cos(a) * 2.6f, 1.2f, (float) Math.sin(a) * 2.6f);
            b.cylinder(0.25f, -0.6f, 0, 6, METAL_NU, METAL_NU, METAL_NU);
            b.pop();
        }
        b.pop();
        for (int k = 0; k < 2; k++) {                                          // gobelets et bidon
            b.push().translate(12.0f + k * 1.6f, 0, 12.4f);
            b.cylinder(0.5f, 0, 1.0f, 8, METAL_NU, METAL_NU, INTERIEUR_SALE);
            b.pop();
        }
        b.push().translate(3.0f, 0, 12.6f).rotateY(20);
        b.color(0.42f, 0.46f, 0.32f);
        b.bevelBox(-1.8f, 0, -1.0f, 1.8f, 4.6f, 1.0f, 0.3f, PEINTURE_USEE);
        b.color(1);
        b.pop();
        decal(b, AUREOLE, 'y', cx, 0.03f, cz, 4.0f, 4.0f, 0);
        jonchee(b, 0.6f, 9.0f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 3152);
    }

    // ================================================================================================ 7. lampes
    /** Caisse de lampes de campagne : lanternes tempête, lampes torches coudées olive, piles, une lanterne posée au sol à
     *  côté (verre fêlé). */
    static void lanterne(MeshBuilder b, float x, float y, float z, Mat verre) {
        b.push().translate(x, y, z);
        b.cylinder(1.1f, 0, 0.6f, 12, METAL_NU, METAL_NU, METAL_NU);
        b.cylinder(0.75f, 0.6f, 2.6f, 12, verre, verre, verre);
        b.lathe(new float[][]{{1.0f, 2.6f}, {1.0f, 2.8f}, {0.4f, 3.4f}, {0.01f, 3.5f}}, 12, 0, 360, METAL_NU, 1, 1, 0, 0, 0, false);
        b.translate(0, 3.5f, 0).rotateX(90);
        b.torus(0.7f, 0.07f, 10, 4, 0, 180, METAL_NU);
        b.pop();
    }

    static void caisseLampes(MeshBuilder b) {
        LotT5c.caisseOuverte(b, 1.6f, 0, 2.4f, 14.4f, 3.6f, 11.2f, KAKI);
        lanterne(b, 4.4f, 0.52f, 5.0f, AtlasLoot.GLASS);
        lanterne(b, 7.4f, 0.52f, 5.2f, AtlasLoot.GLASS);
        for (int k = 0; k < 3; k++) {                                          // lampes coudées
            b.push().translate(10.6f + (k % 2) * 1.6f, 0.52f, 4.6f + k * 1.8f);
            b.color(0.4f, 0.44f, 0.3f);
            b.bevelBox(-0.6f, 0, -0.5f, 0.6f, 2.6f, 0.5f, 0.15f, PEINTURE_USEE);
            b.push().translate(0, 2.6f, 0).rotateX(90);
            b.cylinder(0.6f, -0.42f, 0.8f, 10, PEINTURE_USEE, PEINTURE_USEE, AtlasLoot.GLASS);
            b.pop();
            b.color(1);
            b.pop();
        }
        for (int k = 0; k < 5; k++) {                                          // piles
            b.push().translate(4.0f + k * 0.9f, 0.52f, 9.0f);
            b.cylinder(0.4f, 0, 1.6f, 8, PLASTIQUE_USE, METAL_NU, METAL_NU);
            b.pop();
        }
        lanterne(b, 12.6f, 0, 13.6f, LotT56Atlas.VERRE_FELE);
        Kit.pochoir(b, LotT4bAtlas.S_MATERIEL, 'z', 8.0f, 2.0f, 11.3f, 5.4f, 1.2f);
        jonchee(b, 0.6f, 12.0f, 11.0f, 15.6f, 4, FEUILLES, 1.0f, 3161);
    }

    // ================================================================================================ 8. cantine d'officier
    /** Cantine d'officier : malle de tôle olive à coins de laiton, nom au pochoir, couvercle ouvert sur un plateau
     *  (uniforme plié, képi, lettres, photo), cadenas forcé. */
    static void cantineOfficier(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 3.0f, z1 = 10.6f, h = 6.0f;
        b.color(0.4f, 0.44f, 0.3f);
        b.bevelBox(x0, 0, z0, x1, 0.55f, z1, 0.15f, PEINTURE_USEE);
        b.bevelBox(x0, 0.5f, z0, x0 + 0.45f, h, z1, 0.1f, PEINTURE_USEE);
        b.bevelBox(x1 - 0.45f, 0.5f, z0, x1, h, z1, 0.1f, PEINTURE_USEE);
        b.bevelBox(x0 + 0.4f, 0.5f, z0, x1 - 0.4f, h, z0 + 0.45f, 0.1f, PEINTURE_USEE);
        b.bevelBox(x0 + 0.4f, 0.5f, z1 - 0.45f, x1 - 0.4f, h, z1, 0.1f, PEINTURE_USEE);
        b.color(1);
        Kit.coins(b, x0, 0.6f, z0, x1, h, z1, 0.8f, AtlasCivil.BRASS);
        b.bevelBox(x0 + 0.5f, 3.6f, z0 + 0.5f, x1 - 0.5f, 3.9f, z1 - 0.5f, 0.05f, BOIS_GRIS);    // plateau
        b.push().translate(6.0f, 3.9f, 6.8f).rotateY(4);                       // uniforme plié
        b.color(0.4f, 0.42f, 0.3f);
        b.bevelBox(-3.0f, 0, -2.2f, 3.0f, 1.2f, 2.2f, 0.4f, TISSU_USE);
        b.color(1);
        b.pop();
        b.push().translate(11.4f, 3.9f, 7.0f);                                 // képi
        b.color(0.25f, 0.3f, 0.22f);
        b.cylinder(1.3f, 0, 1.4f, 14, TISSU_USE, TISSU_USE, TISSU_USE);
        b.color(1);
        b.cylinder(1.5f, 0.06f, 0.21f, 14, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.push().translate(0, h, z0).rotateX(-95).translate(0, -h, -z0);      // couvercle ouvert
        b.color(0.4f, 0.44f, 0.3f);
        b.bevelBox(x0 - 0.05f, h + 0.05f, z0, x1 + 0.05f, h + 1.2f, z1, 0.3f, PEINTURE_USEE);
        b.color(1);
        b.box(x0 + 1.0f, h - 0.04f, z0 + 1.6f, x0 + 4.0f, h + 0.0f, z0 + 4.0f, PAPIER);         // photo collée
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_NOM, 'z', 8.0f, 3.0f, z1 + 0.02f, 5.0f, 1.2f);
        LotT3bKit.cadenas(b, 8.0f, h - 0.4f, z1 + 0.02f, true);
        for (int k = 0; k < 3; k++) {                                          // lettres tombées
            b.push().translate(4.0f + k * 2.6f, 0.03f, 12.6f + (k % 2)).rotateY(k * 25 - 10);
            b.box(-1.0f, 0, -0.7f, 1.0f, 0.06f, 0.7f, PAPIER);
            b.pop();
        }
        coulure(b, 'z', x0 + 0.8f, h - 0.6f, z1 + 0.02f, 0.7f, 1);
        jonchee(b, 0.6f, 11.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 3171);
    }
}
