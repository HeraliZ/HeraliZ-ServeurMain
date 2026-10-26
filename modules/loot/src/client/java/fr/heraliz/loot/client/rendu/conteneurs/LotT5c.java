package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T5c (10/10) : conteneurs T5 de plus, tous uniques — militaire lourd et forces de l'ONU (bleu ONU passé, blanc
 * sale, kaki, acier) : caisse de mortier, bidons d'eau ONU, groupe électrogène, caisse de mines antichar, râtelier à
 * obus, caisse de casques bleus, mitrailleuse sur trépied, réservoir souple, robot démineur, kit de décontamination
 * (puis LotT5c2). Abandonnés depuis des mois : boue, rouille, toile délavée, caisses forcées.
 */
public final class LotT5c {
    private LotT5c() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t5_caisse_mortier", "t5", "bois", "Caisse de mortier", "Mortar crate", LotT5c::caisseMortier);
        Conteneurs.ajouter("t5_bidons_eau_onu", "t5", "plastique", "Bidons d'eau ONU", "UN water cans", LotT5c::bidonsEauOnu);
        Conteneurs.ajouter("t5_groupe_electrogene", "t5", "metal", "Groupe électrogène", "Field generator", LotT5c::groupeElectrogene);
        Conteneurs.ajouter("t5_caisse_mines", "t5", "bois", "Caisse de mines antichar", "Anti-tank mine crate", LotT5c::caisseMines);
        Conteneurs.ajouter("t5_rack_obus", "t5", "metal", "Râtelier à obus", "Artillery shell rack", LotT5c::rackObus);
        Conteneurs.ajouter("t5_caisse_casques_bleus", "t5", "bois", "Caisse de casques bleus", "Blue helmet crate", LotT5c::caisseCasquesBleus);
        Conteneurs.ajouter("t5_mitrailleuse_trepied", "t5", "metal", "Mitrailleuse lourde sur trépied", "Tripod heavy machine gun", LotT5c::mitrailleuse);
        Conteneurs.ajouter("t5_reservoir_souple", "t5", "tissu", "Réservoir souple de carburant", "Fuel bladder", LotT5c::reservoirSouple);
        Conteneurs.ajouter("t5_robot_demineur", "t5", "metal", "Robot démineur", "Bomb disposal robot", LotT5c::robotDemineur);
        Conteneurs.ajouter("t5_kit_decontamination", "t5", "plastique", "Kit de décontamination", "Decontamination kit", LotT5c::kitDecontamination);
        LotT5c2.enregistrer();
    }

    static final Mat CHROME = AtlasLoot.CHROME, CAOUTCHOUC = AtlasLoot.RUBBER, SOMBRE = AtlasLoot.DARK;
    static final Mat OLIVE = LotT4bAtlas.BOIS_OLIVE, KAKI = LotT4bAtlas.BOIS_KAKI, TOILE = LotT4bAtlas.TOILE;

    /** Caisse ouverte (fond + 4 parois de planches, creuse) de x0..x1, z0..z1, hauteur h. */
    static void caisseOuverte(MeshBuilder b, float x0, float y0, float z0, float x1, float h, float z1, Mat m) {
        float e = 0.45f, y1 = y0 + h;
        b.bevelBox(x0, y0, z0, x1, y0 + 0.5f, z1, 0.08f, m);
        b.bevelBox(x0, y0 + 0.45f, z0, x0 + e, y1, z1, 0.08f, m);
        b.bevelBox(x1 - e, y0 + 0.45f, z0, x1, y1, z1, 0.08f, m);
        b.bevelBox(x0 + e - 0.05f, y0 + 0.45f, z0, x1 - e + 0.05f, y1, z0 + e, 0.08f, m);
        b.bevelBox(x0 + e - 0.05f, y0 + 0.45f, z1 - e, x1 - e + 0.05f, y1, z1, 0.08f, m);
        for (float y = y0 + 2.0f; y < y1 - 0.5f; y += 2.0f) {                     // joints de planches (rainures sombres)
            b.box(x0 + 0.5f, y, z1 + 0.05f, x1 - 0.5f, y + 0.12f, z1 + 0.09f, SOMBRE);
        }
        Kit.poigneeCorde(b, x0 - 0.05f, y0 + h * 0.6f, (z0 + z1) / 2, 2.2f, -1);
        Kit.poigneeCorde(b, x1 + 0.05f, y0 + h * 0.6f, (z0 + z1) / 2, 2.2f, 1);
    }

    /** Couvercle de caisse appuyé contre le mur derrière : pied posé en avant (yBas), haut qui touche le mur. */
    static void couvercleAppuye(MeshBuilder b, float x0, float x1, float yBas, float zMur, float largeur, float ang, Mat m) {
        float avance = (float) (largeur * Math.sin(Math.toRadians(ang))) + 0.1f;
        b.push().translate(0, yBas, zMur + avance).rotateX(-ang);
        b.bevelBox(x0, 0, 0, x1, largeur, 0.55f, 0.08f, m);
        b.pop();
    }

    // ================================================================================================ 1. caisse de mortier
    /** Caisse de mortier de 81 mm (deux blocs) : caisse olive ouverte, tube posé dans ses berceaux, plaque de base à
     *  côté, bipied replié, trois obus dans leurs tubes de carton, couvercle appuyé contre le mur. */
    static void caisseMortier(MeshBuilder b) {
        float x0 = 1.0f, x1 = 31.0f, z0 = 3.2f, z1 = 11.8f, h = 4.6f;
        caisseOuverte(b, x0, 0, z0, x1, h, z1, OLIVE);
        couvercleAppuye(b, x0, x1, 0.2f, 0.0f, 8.4f, 14, OLIVE);
        Kit.pochoir(b, LotT4bAtlas.S_OBUS, 'z', 16.0f, 2.4f, z1 + 0.1f, 6.0f, 1.4f);
        for (float x : new float[]{6.0f, 24.0f}) b.bevelBox(x - 0.8f, 0.5f, z0 + 0.5f, x + 0.8f, 2.6f, z1 - 0.5f, 0.08f, SOMBRE);
        b.push().translate(3.0f, 3.3f, 7.4f).rotateZ(-90);                     // tube
        b.cylinder(1.25f, 0, 25.0f, 16, SOMBRE, SOMBRE, SOMBRE);
        b.cylinder(1.5f, 23.4f, 25.15f, 16, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        // plaque de base (ronde) appuyée contre la caisse à droite, bipied replié dessus
        b.push().translate(29.0f, 2.92f, 13.2f).rotateX(-62);
        b.cylinder(3.0f, 0, 0.4f, 18, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR);
        b.pop();
        // obus dans leurs tubes de carton, un sorti
        for (int k = 0; k < 2; k++) {
            b.push().translate(6.0f + k * 2.0f, 1.0f, 13.4f).rotateY(80 + k * 6).rotateZ(90);
            b.color(0.45f, 0.42f, 0.3f);
            b.cylinder(0.9f, -3.2f, 3.2f, 12, CARTON_MOUILLE, CARTON_MOUILLE, SOMBRE);
            b.color(1);
            b.pop();
        }
        b.push().translate(11.0f, 0.82f, 14.2f).rotateY(30).rotateZ(90);
        b.color(0.4f, 0.42f, 0.32f);
        b.lathe(new float[][]{{0.01f, -2.6f}, {0.5f, -2.5f}, {0.7f, -1.2f}, {0.72f, 1.2f}, {0.4f, 2.4f}, {0.01f, 2.8f}}, 12, 0, 360, PEINTURE_USEE, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.pop();
        boue(b, 'z', 16.0f, 0, z1 + 0.12f, 26.0f, 1.2f, 0);
        coulure(b, 'z', 4.0f, h - 0.4f, z1 + 0.11f, 0.8f, 1);
        jonchee(b, 1.0f, 12.4f, 31.0f, 15.5f, 7, FEUILLES, 1.1f, 2001);
    }

    // ================================================================================================ 2. bidons d'eau ONU
    /** Bidons d'eau rigides (20 L) bleus marqués UN : six sur une palette, deux rangs, un renversé à terre bouchon perdu,
     *  sangle de cerclage coupée qui pend. */
    static void bidon(MeshBuilder b, float x, float y, float z, float rotY, int v) {
        b.push().translate(x, y, z).rotateY(rotY);
        b.color(v == 1 ? 0.75f : 0.85f, v == 1 ? 0.82f : 0.9f, 1.0f);
        b.bevelBox(-2.2f, 0, -1.3f, 2.2f, 6.2f, 1.3f, 0.45f, BLEU_ONU);
        b.bevelBox(-1.9f, 6.15f, -0.4f, -0.2f, 6.9f, 0.4f, 0.15f, BLEU_ONU);      // poignée
        b.color(1);
        b.push().translate(1.2f, 6.2f, 0);
        b.cylinder(0.5f, 0, 0.7f, 10, AtlasLoot.WHITE, AtlasLoot.WHITE, AtlasLoot.WHITE);
        b.pop();
        Kit.pochoir(b, S_UN_BLANC, 'z', 0, 3.6f, 1.32f, 2.2f, 1.1f);
        b.pop();
    }

    static void bidonsEauOnu(MeshBuilder b) {
        LotT56.palette(b, 1.6f, 14.4f, 0.6f, 12.6f, false);
        float y = 2.32f;
        bidon(b, 4.2f, y, 3.2f, 2, 0);
        bidon(b, 8.8f, y, 3.0f, -3, 1);
        bidon(b, 4.0f, y, 7.6f, -2, 1);
        bidon(b, 8.6f, y, 7.8f, 4, 0);
        bidon(b, 12.0f, y, 5.2f, 88, 0);
        bidon(b, 5.6f, y + 6.95f, 4.6f, 12, 0);                                 // rang du dessus
        // bidon renversé au sol, flaque séchée
        b.push().translate(10.0f, 1.55f, 14.0f).rotateZ(90).rotateY(-20);
        b.color(0.85f, 0.9f, 1.0f);
        b.bevelBox(-1.3f, -2.2f, -1.3f, 1.3f, 2.2f, 1.3f, 0.45f, BLEU_ONU);
        b.color(1);
        b.pop();
        decal(b, AUREOLE, 'y', 6.0f, 0.03f, 14.4f, 3.0f, 2.0f, 0);
        b.tube(new float[][]{p(14.5f, 6.0f, 10.0f), p(15.0f, 3.0f, 11.6f), p(14.6f, 0.15f, 13.0f)}, fill(3, 0.12f), 4, SANGLE, true, true, 0, false);
        jonchee(b, 1.0f, 12.8f, 15.0f, 15.6f, 4, FEUILLES, 1.0f, 2011);
    }

    // ================================================================================================ 3. groupe électrogène
    /** Groupe électrogène militaire (deux blocs) : châssis tubulaire, moteur, réservoir kaki, tableau de commande, pot
     *  d'échappement rouillé, roues de transport, jerrican à côté, câbles. */
    static void groupeElectrogene(MeshBuilder b) {
        float x0 = 2.0f, x1 = 26.0f, z0 = 1.5f, z1 = 11.5f, h = 10.0f;
        for (float x : new float[]{x0, x1 - 0.7f}) for (float z : new float[]{z0, z1 - 0.7f}) b.bevelBox(x, 0.9f, z, x + 0.7f, h, z + 0.7f, 0.15f, ACIER_NOIR);
        for (float y : new float[]{1.0f, h - 0.7f}) {
            b.bevelBox(x0 + 0.65f, y + 0.07f, z0 + 0.07f, x1 - 0.65f, y + 0.63f, z0 + 0.63f, 0.12f, ACIER_NOIR);
            b.bevelBox(x0 + 0.65f, y + 0.07f, z1 - 0.63f, x1 - 0.65f, y + 0.63f, z1 - 0.07f, 0.12f, ACIER_NOIR);
        }
        for (float x : new float[]{x0 + 2.0f, x1 - 2.0f}) {                      // roues
            b.push().translate(x, 0.9f, z1 + 0.6f).rotateX(90);
            b.cylinder(0.9f, -0.5f, 0.5f, 14, CAOUTCHOUC, CAOUTCHOUC, CAOUTCHOUC);
            b.pop();
        }
        b.color(0.42f, 0.46f, 0.32f);                                            // moteur
        b.bevelBox(4.0f, 1.7f, 2.6f, 15.0f, 8.0f, 10.4f, 0.5f, PEINTURE_USEE);
        b.color(1);
        for (int k = 0; k < 5; k++) b.bevelBox(5.0f + k * 1.8f, 8.0f, 4.0f, 5.8f + k * 1.8f, 8.8f, 9.0f, 0.1f, METAL_NU);  // ailettes
        b.color(0.4f, 0.44f, 0.3f);                                              // réservoir
        b.bevelBox(15.6f, 4.0f, 2.6f, 25.0f, 9.0f, 10.4f, 0.8f, PEINTURE_USEE);
        b.color(1);
        b.push().translate(22.0f, 9.0f, 6.5f);
        b.cylinder(0.7f, 0, 0.5f, 12, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_GAZOLE, 'z', 20.0f, 6.6f, 10.42f, 5.0f, 1.2f);
        b.box(16.5f, 1.8f, 10.42f, 24.2f, 3.6f, 10.48f, PANNEAU_RADIO);            // tableau de commande
        b.push().translate(4.6f, 6.0f, 6.5f).rotateZ(90);                      // échappement
        b.color(0.55f, 0.3f, 0.18f);
        b.cylinder(0.7f, 0, 2.4f, 10, ROUILLE, ROUILLE, SOMBRE);
        b.color(1);
        b.pop();
        // jerrican à côté, câbles d'alimentation
        b.push().translate(29.0f, 0, 6.0f).rotateY(80);
        b.color(0.4f, 0.44f, 0.3f);
        b.bevelBox(-2.2f, 0, -1.1f, 2.2f, 5.6f, 1.1f, 0.3f, PEINTURE_USEE);
        b.color(1);
        b.pop();
        cable(b, auSol(lisse(new float[][]{p(18.0f, 2.6f, 10.6f), p(17.0f, 1.0f, 12.0f), p(14.0f, 0.14f, 13.4f), p(9.0f, 0.14f, 14.6f), p(4.0f, 0.14f, 13.2f)}, 6), 0.13f),
                0.18f, 0.12f, 0.12f, 0.12f);
        boue(b, 'z', 14.0f, 0.9f, z1 + 0.05f, 22.0f, 1.0f, 1);
        coulure(b, 'z', 20.0f, 4.0f, 10.41f, 0.8f, 2);
        jonchee(b, 1.0f, 12.8f, 31.0f, 15.6f, 6, FEUILLES, 1.0f, 2021);
    }

    // ================================================================================================ 4. caisse de mines
    /** Caisse de mines antichar (rondes, vert olive, fusée au centre) : caisse kaki ouverte avec séparations, trois mines
     *  dedans, une posée contre la caisse, panneau « danger » au pochoir, couvercle arraché. */
    static void mine(MeshBuilder b, float x, float y, float z, float rx, float rz) {
        b.push().translate(x, y, z).rotateX(rx).rotateZ(rz);
        b.color(0.42f, 0.46f, 0.32f);
        b.lathe(new float[][]{{0.01f, 0}, {2.9f, 0}, {3.1f, 0.4f}, {3.1f, 1.6f}, {2.6f, 2.0f}, {1.0f, 2.1f}, {0.01f, 2.1f}}, 18, 0, 360, PEINTURE_USEE, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.push().translate(0, 2.05f, 0);
        b.cylinder(0.75f, 0, 0.45f, 12, METAL_NU, METAL_NU, SOMBRE);
        b.pop();
        b.pop();
    }

    static void caisseMines(MeshBuilder b) {
        float x0 = 1.4f, x1 = 14.6f, z0 = 1.2f, z1 = 11.6f, h = 5.0f;
        caisseOuverte(b, x0, 0, z0, x1, h, z1, KAKI);
        b.bevelBox(7.8f, 0.5f, z0 + 0.4f, 8.2f, h - 0.6f, z1 - 0.4f, 0.05f, KAKI);   // séparation
        mine(b, 4.6f, 0.52f, 4.4f, 0, 0);
        mine(b, 11.4f, 0.52f, 4.4f, 0, 0);
        mine(b, 4.6f, 0.52f, 8.6f, 4, -3);
        Kit.pochoir(b, LotT4bAtlas.S_DANGER, 'z', 8.0f, 2.6f, z1 + 0.1f, 6.0f, 1.4f);
        // une mine appuyée contre la face avant, une autre couchée au sol
        b.push().translate(13.6f, 3.05f, 13.6f).rotateX(-70);
        mine(b, 0, 0, 0, 0, 0);
        b.pop();
        mine(b, 3.6f, 0, 14.0f, 0, 0);
        b.push().translate(0.2f, 0, 8.6f);                                     // couvercle arraché au sol, à gauche
        b.bevelBox(0, 0, -4.0f, 0.5f, 0.5f, 4.0f, 0.06f, KAKI);
        b.pop();
        boue(b, 'z', 8.0f, 0, z1 + 0.12f, 12.0f, 1.2f, 1);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 2031);
    }

    // ================================================================================================ 5. râtelier à obus
    /** Râtelier à obus de 155 mm (deux blocs) : cadre d'acier à berceaux, quatre obus couchés (un manque), anneaux de
     *  levage, caisse de fusées à côté, coulures. */
    static void obus(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z).rotateZ(-90);
        b.color(0.42f, 0.44f, 0.34f);
        b.lathe(new float[][]{{0.01f, 0}, {1.9f, 0}, {1.95f, 0.6f}, {1.95f, 8.6f}, {1.6f, 11.0f}, {0.8f, 12.6f}, {0.01f, 13.0f}}, 16, 0, 360,
                PEINTURE_USEE, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.push().translate(0, 1.0f, 0);
        b.hollowCylinder(2.0f, 1.85f, 0, 0.7f, 16, AtlasCivil.BRASS);
        b.pop();
        b.pop();
    }

    static void rackObus(MeshBuilder b) {
        float x0 = 2.0f, x1 = 30.0f;
        for (float x : new float[]{x0, 16.0f - 0.4f, x1 - 0.8f}) {
            b.bevelBox(x, 0, 1.2f, x + 0.8f, 0.8f, 11.8f, 0.1f, ACIER_NOIR);
            for (int l = 0; l < 2; l++) {
                float y = 0.8f + l * 4.6f;
                b.bevelBox(x + 0.05f, y, 1.6f, x + 0.75f, y + 0.6f, 11.4f, 0.1f, ACIER_NOIR);
                for (float z : new float[]{2.8f, 9.6f}) b.bevelBox(x + 0.1f, y + 0.6f, z - 1.2f, x + 0.7f, y + 1.4f, z - 0.6f, 0.06f, ACIER_NOIR);
            }
            b.bevelBox(x + 0.05f, 0.75f, 1.2f, x + 0.75f, 10.4f, 1.8f, 0.08f, ACIER_NOIR);
            b.bevelBox(x + 0.05f, 0.75f, 11.2f, x + 0.75f, 10.4f, 11.8f, 0.08f, ACIER_NOIR);
        }
        obus(b, 3.4f, 3.2f, 3.6f);
        obus(b, 3.4f, 3.2f, 8.8f);
        obus(b, 3.4f, 7.8f, 3.6f);
        b.push().translate(16.4f, 0, 0);
        obus(b, 0, 3.2f, 3.6f);
        b.pop();
        // caisse de fusées ouverte au sol devant, une fusée sortie
        b.push().translate(20.0f, 0, 12.6f).rotateY(-8);
        caisseOuverte(b, -3.0f, 0, -1.6f, 3.0f, 3.0f, 1.6f, OLIVE);
        b.pop();
        b.push().translate(25.0f, 0.62f, 14.6f).rotateZ(90);
        b.cylinder(0.5f, -0.9f, 0.9f, 10, AtlasCivil.BRASS, AtlasCivil.BRASS, METAL_NU);
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_OBUS, 'z', 16.0f, 9.4f, 1.85f, 5.0f, 1.0f);
        coulure(b, 'z', x0 + 0.4f, 9.6f, 11.85f, 0.6f, 0);
        jonchee(b, 1.0f, 12.0f, 31.0f, 15.6f, 6, FEUILLES, 1.0f, 2041);
    }

    // ================================================================================================ 6. caisse de casques bleus
    /** Caisse de casques bleus : caisse blanche marquée UN, couvercle relevé, casques bleus empilés (un posé au sol à
     *  l'envers), gilet pare-balles bleu plié sur le bord, brassard. */
    static void casque(MeshBuilder b, float x, float y, float z, float rx, float ry) {
        b.push().translate(x, y, z).rotateY(ry).rotateX(rx);
        b.color(0.55f, 0.72f, 0.95f);
        b.push().scale(1.0f, 0.78f, 1.12f);
        b.sphereSection(2.1f, 16, 5, 0, 90, PEINTURE_USEE);
        b.pop();
        b.torus(2.1f, 0.12f, 16, 4, 0, 360, PEINTURE_USEE);
        b.color(1);
        Kit.pochoir(b, S_UN_BLANC, 'z', 0, 0.8f, 2.25f, 1.6f, 0.8f);
        b.pop();
    }

    static void caisseCasquesBleus(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 3.0f, z1 = 11.8f, h = 5.4f;
        b.color(0.92f, 0.92f, 0.88f);
        caisseOuverte(b, x0, 0, z0, x1, h, z1, BLANC_ONU);
        b.color(1);
        b.color(0.92f, 0.92f, 0.88f);
        couvercleAppuye(b, x0, x1, h - 0.2f, 0.0f, 8.0f, 10, BLANC_ONU);
        b.color(1);
        Kit.pochoir(b, S_UN_NOIR, 'z', 8.0f, 2.8f, z1 + 0.1f, 3.6f, 1.8f);
        casque(b, 4.8f, h - 1.6f, 6.0f, -8, 10);
        casque(b, 9.6f, h - 1.5f, 6.6f, 6, -20);
        casque(b, 7.0f, h - 0.2f, 7.8f, -14, 40);
        casque(b, 11.2f, 2.0f, 14.0f, 180, 30);                                  // à l'envers au sol
        // gilet bleu plié sur le bord avant
        b.push().translate(4.8f, h - 0.4f, z1 - 0.6f).rotateX(55);
        b.color(0.3f, 0.45f, 0.75f);
        b.bevelBox(-2.6f, 0, -0.5f, 2.6f, 4.0f, 0.5f, 0.3f, TISSU_USE);
        b.color(1);
        b.pop();
        boue(b, 'z', 8.0f, 0, z1 + 0.12f, 12.0f, 1.0f, 0);
        jonchee(b, 0.6f, 12.4f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2051);
    }

    // ================================================================================================ 7. mitrailleuse sur trépied
    /** Mitrailleuse lourde sur trépied (12,7) pointée vers la droite, bande de cartouches qui pend, caisse à munitions
     *  ouverte au pied (le conteneur), étuis vides au sol. */
    static void mitrailleuse(MeshBuilder b) {
        float px = 9.0f, pz = 6.0f, hy = 8.6f;
        float[][] pieds = {{px - 5.5f, 1.8f}, {px + 4.0f, 1.6f}, {px, 11.6f}};
        for (float[] q : pieds) b.tube(new float[][]{p(px, hy - 1.0f, pz), p(q[0], 0.25f, q[1])}, fill(2, 0.25f), 6, SOMBRE, true, true, 0, false);
        b.push().translate(px, hy - 1.2f, pz);
        b.cylinder(0.6f, 0, 1.2f, 10, SOMBRE, SOMBRE, SOMBRE);
        b.translate(0, 1.2f, 0).rotateY(-8);
        b.bevelBox(-3.6f, 0, -0.9f, 3.0f, 2.0f, 0.9f, 0.2f, SOMBRE);            // boîte de culasse
        b.bevelBox(-5.0f, 0.4f, -0.3f, -3.6f, 1.6f, 0.3f, 0.1f, SOMBRE);         // poignées
        b.push().translate(3.0f, 1.1f, 0).rotateZ(-90);
        b.cylinder(0.75f, 0, 3.0f, 12, TOLE_PERFOREE, TOLE_PERFOREE, SOMBRE);   // manchon
        b.cylinder(0.3f, 3.0f, 10.8f, 8, SOMBRE, SOMBRE, SOMBRE);               // canon
        b.cylinder(0.55f, 10.2f, 11.2f, 8, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.pop();
        // bande de cartouches qui pend jusqu'à la caisse
        float[][] bande = lisse(new float[][]{p(px - 0.6f, hy + 0.9f, pz + 0.9f), p(px - 1.4f, hy - 1.0f, pz + 2.6f), p(px - 2.2f, 4.0f, pz + 4.4f),
                p(px - 3.4f, 2.8f, pz + 6.4f)}, 5);
        for (int i = 0; i + 1 < bande.length; i++) {
            b.push().translate(bande[i][0], bande[i][1], bande[i][2]).rotateY(-10);
            b.rotateX(90);
            cartoucheSimple(b, 1.6f, 0.2f, AtlasCivil.BRASS, METAL_NU);
            b.pop();
        }
        // caisse à munitions ouverte au pied (gros conteneur), étuis
        b.push().translate(4.6f, 0, 13.0f).rotateY(6);
        b.color(0.4f, 0.44f, 0.3f);
        b.bevelBox(-2.8f, 0, -1.6f, 2.8f, 3.4f, 1.6f, 0.2f, PEINTURE_USEE);
        b.color(1);
        b.box(-2.5f, 3.38f, -1.3f, 2.5f, 3.44f, 1.3f, INTERIEUR_SALE);
        Kit.pochoir(b, AtlasLoot.S_762, 'z', 0, 1.6f, 1.62f, 3.6f, 1.0f);
        b.pop();
        Random r = new Random(2061);
        for (int i = 0; i < 9; i++) {
            b.push().translate(11.0f + r.nextFloat() * 4.0f, 0.22f, 9.0f + r.nextFloat() * 6.0f).rotateY(r.nextFloat() * 180).rotateZ(90);
            b.cylinder(0.2f, -0.6f, 0.6f, 6, AtlasCivil.BRASS, AtlasCivil.BRASS, AtlasCivil.BRASS);
            b.pop();
        }
        jonchee(b, 0.6f, 1.0f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 2062);
    }

    // ================================================================================================ 8. réservoir souple
    /** Réservoir souple de carburant (outre de caoutchouc kaki à moitié vide) sur une bâche, vanne et tuyau, panneau
     *  « F-34 », extincteur couché, traces d'hydrocarbure. */
    static void reservoirSouple(MeshBuilder b) {
        b.color(0.5f, 0.52f, 0.42f);
        b.bevelBox(1.0f, 0, 1.0f, 31.0f, 0.2f, 14.0f, 0.05f, LotT4bAtlas.BACHE);
        b.color(1);
        b.push().translate(15.0f, 2.62f, 7.2f);
        b.color(0.36f, 0.38f, 0.3f);
        b.blob(12.4f, 2.3f, 5.6f, 0.82f, 0.14f, 1.4f, 2071, 18, CAOUTCHOUC_USE);
        b.color(1);
        b.pop();
        b.push().translate(26.8f, 2.6f, 9.6f);                                 // vanne
        b.cylinder(0.7f, 0, 1.4f, 10, METAL_NU, METAL_NU, METAL_NU);
        b.translate(0, 1.4f, 0).rotateX(90);
        b.torus(0.7f, 0.12f, 12, 4, 0, 360, ROUGE_SECU);
        b.pop();
        b.tube(auSol(lisse(new float[][]{p(27.4f, 3.0f, 10.4f), p(28.6f, 1.4f, 12.0f), p(27.0f, 0.65f, 14.0f), p(22.0f, 0.65f, 14.6f)}, 6), 0.65f), fill(19, 0.45f), 8, CAOUTCHOUC, true, true, 0, false);
        b.push().translate(4.0f, 0.2f, 1.5f);                                  // panneau F-34 appuyé contre le mur
        b.rotateX(-12);
        b.bevelBox(-2.6f, 0, 0, 2.6f, 4.0f, 0.3f, 0.06f, METAL_NU);
        Kit.pochoir(b, LotT4bAtlas.S_F34, 'z', 0, 2.0f, 0.32f, 4.0f, 1.6f);
        b.pop();
        b.push().translate(8.0f, 0.8f, 14.8f).rotateY(70).rotateZ(90);          // extincteur couché
        b.cylinder(0.8f, -2.6f, 2.6f, 12, ROUGE_SECU, ROUGE_SECU, ROUGE_SECU);
        b.pop();
        decal(b, AUREOLE, 'y', 21.0f, 0.22f, 12.0f, 5.0f, 3.0f, 0);
        decal(b, BOUE_2, 'y', 9.0f, 0.22f, 3.0f, 4.0f, 2.4f, 30);
        jonchee(b, 1.0f, 1.0f, 31.0f, 15.6f, 10, FEUILLES, 1.1f, 2072);
    }

    // ================================================================================================ 9. robot démineur
    /** Robot démineur à chenilles : châssis, deux chenilles, bras articulé replié vers le sol avec pince, caméra, antenne
     *  cassée ; malle de transport ouverte derrière, télécommande posée dessus. */
    static void robotDemineur(MeshBuilder b) {
        b.color(0.36f, 0.4f, 0.3f);
        b.bevelBox(1.6f, 0, 1.0f, 14.4f, 3.0f, 4.6f, 0.3f, PEINTURE_USEE);       // malle derrière
        b.color(1);
        b.box(2.0f, 2.98f, 1.4f, 14.0f, 3.04f, 4.2f, MOUSSE_DECOUPE);
        b.push().translate(9.0f, 3.04f, 2.8f).rotateY(10);                     // télécommande
        b.bevelBox(-2.0f, 0, -1.0f, 2.0f, 0.8f, 1.0f, 0.25f, SOMBRE);
        b.box(-1.2f, 0.81f, -0.7f, 1.2f, 0.85f, 0.5f, ECRAN_OFF);
        b.pop();
        float z0 = 6.0f, z1 = 13.0f;
        for (float z : new float[]{z0, z1 - 1.6f}) {                           // chenilles
            b.bevelBox(2.4f, 0.05f, z, 13.0f, 2.2f, z + 1.6f, 0.9f, CAOUTCHOUC_USE);
            for (float x : new float[]{3.6f, 7.7f, 11.8f}) {
                b.push().translate(x, 1.1f, z - 0.05f).rotateX(90);
                b.cylinder(0.8f, -0.02f, 0.05f, 10, METAL_NU, METAL_NU, METAL_NU);
                b.pop();
            }
        }
        b.color(0.42f, 0.44f, 0.36f);
        b.bevelBox(3.2f, 2.2f, z0 + 1.55f, 12.2f, 4.6f, z1 - 1.55f, 0.3f, PEINTURE_USEE);
        b.color(1);
        // bras articulé : épaule, segment 1 levé, segment 2 vers le sol, pince
        b.push().translate(10.0f, 4.6f, 9.5f);
        b.cylinder(0.9f, 0, 0.8f, 12, SOMBRE, SOMBRE, SOMBRE);
        b.translate(0, 0.8f, 0).rotateZ(-50);
        b.bevelBox(-0.45f, 0, -0.45f, 0.45f, 6.0f, 0.45f, 0.12f, METAL_NU);
        b.translate(0, 6.0f, 0).rotateZ(130);
        b.bevelBox(-0.4f, 0, -0.4f, 0.4f, 5.0f, 0.4f, 0.12f, METAL_NU);
        b.translate(0, 5.0f, 0);
        for (int s = -1; s <= 1; s += 2) {
            b.push().rotateX(s * 18);
            b.bevelBox(-0.2f, 0, -0.15f, 0.2f, 1.4f, 0.15f, 0.05f, SOMBRE);
            b.pop();
        }
        b.pop();
        b.push().translate(5.0f, 4.6f, 9.5f);                                 // caméra et antenne cassée
        b.bevelBox(-0.8f, 0, -0.8f, 0.8f, 1.2f, 0.8f, 0.15f, SOMBRE);
        b.translate(0.9f, 0.6f, 0).rotateZ(-90);
        b.cylinder(0.4f, 0, 0.5f, 10, SOMBRE, SOMBRE, AtlasLoot.GLASS);
        b.pop();
        b.push().translate(4.0f, 4.6f, 7.8f).rotateZ(35);
        b.cylinder(0.1f, 0, 3.0f, 5, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        Kit.pochoir(b, LotT4bAtlas.S_DANGER, 'z', 7.6f, 3.4f, z1 - 1.5f + 0.02f, 3.6f, 0.9f);
        boue(b, 'z', 7.7f, 0.05f, z1 + 0.05f, 9.0f, 1.2f, 1);
        jonchee(b, 0.6f, 13.6f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 2081);
    }

    // ================================================================================================ 10. kit de décontamination
    /** Kit de décontamination NRBC : pulvérisateur dorsal jaune, bidons de produit, seau, brosse, bâche verte au sol,
     *  masque à gaz posé dessus, tuyau et lance. */
    static void kitDecontamination(MeshBuilder b) {
        b.color(0.4f, 0.48f, 0.36f);
        b.bevelBox(1.0f, 0, 1.0f, 15.0f, 0.18f, 14.0f, 0.05f, LotT4bAtlas.BACHE);
        b.color(1);
        b.push().translate(5.2f, 0.18f, 2.6f).rotateX(-14);                   // pulvérisateur dorsal appuyé au mur
        b.color(0.9f, 0.78f, 0.22f);
        b.bevelBox(-2.4f, 0, 0.2f, 2.4f, 8.2f, 3.4f, 0.8f, PLASTIQUE_USE);
        b.color(1);
        b.push().translate(0, 8.2f, 1.8f);
        b.cylinder(0.9f, 0, 0.6f, 12, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.pop();
        b.tube(lisse(new float[][]{p(6.8f, 2.4f, 4.6f), p(8.0f, 0.6f, 7.0f), p(10.0f, 0.3f, 9.0f), p(12.6f, 0.3f, 10.2f)}, 6), fill(19, 0.2f), 6, CAOUTCHOUC, true, true, 0, false);
        b.push().translate(12.6f, 0.4f, 10.2f).rotateY(-20).rotateZ(-90);    // lance
        b.cylinder(0.18f, 0, 3.0f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        for (int k = 0; k < 2; k++) {                                          // bidons de produit
            b.push().translate(10.4f + k * 2.8f, 0.18f, 3.0f).rotateY(k * 12);
            b.color(k == 0 ? 0.92f : 0.8f, k == 0 ? 0.92f : 0.85f, k == 0 ? 0.9f : 0.95f);
            b.bevelBox(-1.2f, 0, -1.2f, 1.2f, 4.2f, 1.2f, 0.3f, PLASTIQUE_USE);
            b.color(1);
            b.push().translate(0, 4.2f, 0);
            b.cylinder(0.4f, 0, 0.5f, 8, BLEU_ONU, BLEU_ONU, BLEU_ONU);
            b.pop();
            b.pop();
        }
        Kit.pochoir(b, AtlasLoot.S_DANGER, 'z', 10.4f, 2.2f, 4.22f, 2.0f, 0.8f);
        b.push().translate(3.6f, 0.18f, 10.6f);                                // seau
        b.color(0.6f, 0.65f, 0.7f);
        b.lathe(new float[][]{{0.01f, 0}, {1.5f, 0}, {1.9f, 3.2f}, {1.75f, 3.2f}, {1.35f, 0.15f}, {0.01f, 0.15f}}, 14, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.pop();
        b.push().translate(7.4f, 0.4f, 12.4f).rotateY(30);                    // masque à gaz
        b.color(0.3f, 0.32f, 0.3f);
        b.push().scale(1, 0.7f, 1);
        b.sphereSection(1.4f, 12, 5, 0, 90, CAOUTCHOUC_USE);
        b.pop();
        b.color(1);
        b.push().translate(0, 0.2f, 1.2f).rotateX(90);
        b.cylinder(0.6f, 0, 0.9f, 10, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        b.pop();
        jonchee(b, 1.0f, 1.0f, 15.0f, 15.6f, 5, FEUILLES, 1.0f, 2091);
    }
}
