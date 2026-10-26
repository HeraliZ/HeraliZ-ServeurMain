package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT3bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T3c (10/10) : conteneurs T3 de plus, tous uniques — police, pompiers, chasse, douane et secours (noir, bandes
 * bleues et réfléchissantes, rouge pompier, camo de chasse) : dévidoir de lances, tenue de feu accrochée, sac de saisie,
 * malle de balisage, herse cloutée, sac de carabine, valise de police scientifique, mallette radar, extincteur sur
 * roues, bouée de sauvetage, motopompe, top-case de moto de police.
 */
public final class LotT3c {
    private LotT3c() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t3_devidoir_lances", "t3", "metal", "Dévidoir de lances à incendie", "Fire hose reel", LotT3c::devidoir);
        Conteneurs.ajouter("t3_tenue_feu", "t3", "tissu", "Tenue de feu accrochée", "Hanging firefighter gear", LotT3c::tenueFeu);
        Conteneurs.ajouter("t3_sac_saisie", "t3", "tissu", "Sac de saisie de la police", "Police seizure bag", LotT3c::sacSaisie);
        Conteneurs.ajouter("t3_malle_balisage", "t3", "plastique", "Malle de balisage routier", "Road marking kit", LotT3c::malleBalisage);
        Conteneurs.ajouter("t3_herse_cloutee", "t3", "metal", "Herse cloutée de gendarmerie", "Police spike strip", LotT3c::herse);
        Conteneurs.ajouter("t3_sac_carabine", "t3", "tissu", "Sac de carabine et siège d'affût", "Rifle bag and hunting stool", LotT3c::sacCarabine);
        Conteneurs.ajouter("t3_valise_scientifique", "t3", "plastique", "Valise de police scientifique", "Forensic kit", LotT3c::valiseScientifique);
        Conteneurs.ajouter("t3_mallette_radar", "t3", "plastique", "Mallette radar de vitesse", "Speed radar case", LotT3c::malletteRadar);
        Conteneurs.ajouter("t3_extincteur_chariot", "t3", "metal", "Extincteur sur roues", "Wheeled fire extinguisher", LotT3c::extincteurChariot);
        Conteneurs.ajouter("t3_bouee_sauvetage", "t3", "plastique", "Bouée et sac de sauvetage", "Life ring and rescue bag", LotT3c::boueeSauvetage);
        Conteneurs.ajouter("t3_motopompe", "t3", "metal", "Motopompe des pompiers", "Fire pump", LotT3c::motopompe);
        Conteneurs.ajouter("t3_topcase_moto", "t3", "plastique", "Top-case de moto de police", "Police motorcycle top case", LotT3c::topcaseMoto);
    }

    static final Mat CHROME = AtlasLoot.CHROME, CAOUTCHOUC = AtlasLoot.RUBBER, SOMBRE = AtlasLoot.DARK, GRAIN = PLASTIQUE_USE;

    // ================================================================================================ 1. dévidoir
    /** Dévidoir de lances à incendie : armoire murale rouge à porte ouverte, tambour, tuyau plat enroulé, lance de laiton
     *  pendante, tuyau déroulé au sol. */
    static void devidoir(MeshBuilder b) {
        float x0 = 2.0f, x1 = 14.0f, y0 = 4.0f, y1 = 18.0f, z1 = 5.0f;
        b.bevelBox(x0, y0, 0.05f, x1, y1, 0.5f, 0.1f, ROUGE_POMPIER);
        b.bevelBox(x0, y0, 0.45f, x0 + 0.5f, y1, z1, 0.1f, ROUGE_POMPIER);
        b.bevelBox(x1 - 0.5f, y0, 0.45f, x1, y1, z1, 0.1f, ROUGE_POMPIER);
        b.bevelBox(x0 + 0.45f, y0, 0.45f, x1 - 0.45f, y0 + 0.5f, z1, 0.1f, ROUGE_POMPIER);
        b.bevelBox(x0 + 0.45f, y1 - 0.5f, 0.45f, x1 - 0.45f, y1, z1, 0.1f, ROUGE_POMPIER);
        b.push().translate(8.0f, 11.0f, 2.6f).rotateX(90);                    // tambour et tuyau enroulé
        b.cylinder(5.2f, -1.9f, -1.6f, 18, METAL_NU, METAL_NU, METAL_NU);
        b.cylinder(5.2f, 1.6f, 1.9f, 18, METAL_NU, METAL_NU, METAL_NU);
        b.color(0.75f, 0.2f, 0.16f);
        b.cylinder(4.4f, -1.55f, 1.55f, 18, CAOUTCHOUC_USE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
        b.color(1);
        b.pop();
        b.push().translate(x1, 0, z1).rotateY(95);                              // porte ouverte
        b.bevelBox(-(x1 - x0), y0, 0, 0, y1, 0.4f, 0.1f, ROUGE_POMPIER);
        b.box(-(x1 - x0) + 2.0f, y0 + 4.0f, 0.42f, -2.0f, y0 + 9.0f, 0.46f, L_POMPIERS);
        b.pop();
        b.color(0.75f, 0.2f, 0.16f);                                            // tuyau qui descend et s'étale
        b.tube(auSol(lisse(new float[][]{p(4.0f, 8.0f, 4.8f), p(3.6f, 3.0f, 6.6f), p(4.6f, 0.3f, 9.4f), p(9.0f, 0.3f, 12.6f), p(13.0f, 0.3f, 13.4f)}, 6), 0.3f),
                fill(25, 0.3f), 6, CAOUTCHOUC_USE, true, true, 0, false);
        b.color(1);
        b.push().translate(13.4f, 0.4f, 13.6f).rotateY(-70).rotateZ(-90);   // lance
        b.cylinder(0.4f, 0, 2.6f, 10, AtlasCivil.BRASS, AtlasCivil.BRASS, AtlasCivil.BRASS);
        b.pop();
        coulure(b, 'z', x0 + 1.0f, y0, z1 + 0.02f, 0.7f, 0);
        jonchee(b, 0.6f, 6.0f, 15.4f, 15.6f, 5, FEUILLES, 1.0f, 4001);
    }

    // ================================================================================================ 2. tenue de feu
    /** Tenue de feu accrochée au mur : veste beige à bandes réfléchissantes, casque F1 sur la patère, pantalon replié sur
     *  les bottes, cagoule et gants sur un banc bas (le conteneur). */
    static void tenueFeu(MeshBuilder b) {
        b.bevelBox(1.0f, 4.0f, 0.4f, 15.0f, 4.6f, 4.4f, 0.1f, BOIS_GRIS);          // banc
        for (float x : new float[]{1.6f, 14.0f}) b.bevelBox(x, 0, 0.8f, x + 0.4f, 4.0f, 4.0f, 0.06f, METAL_NU);
        b.bevelBox(2.0f, 22.0f, 0.05f, 14.0f, 22.8f, 0.6f, 0.1f, BOIS_GRIS);       // planche de patères
        b.push().translate(6.0f, 22.2f, 0.6f);
        b.color(0.78f, 0.7f, 0.5f);                                              // veste
        b.bevelBox(-3.4f, -11.0f, 0, 3.4f, 0, 2.6f, 0.8f, TISSU_USE);
        b.color(1);
        for (float y : new float[]{-9.0f, -5.0f}) b.box(-3.45f, y, 2.62f, 3.45f, y + 0.8f, 2.68f, BANDE_REFL);
        b.pop();
        b.push().translate(11.6f, 22.2f, 2.2f);                                 // casque F1 sur la patère
        b.color(0.85f, 0.8f, 0.75f);
        b.push().scale(1.0f, 0.95f, 1.15f);
        b.sphereSection(2.0f, 14, 5, 0, 90, PEINTURE_USEE);
        b.pop();
        b.color(1);
        b.box(-1.4f, 0.2f, 2.1f, 1.4f, 1.0f, 2.2f, AtlasLoot.GLASS);
        b.pop();
        for (int k = 0; k < 2; k++) {                                           // bottes sous le banc
            b.push().translate(5.0f + k * 2.2f, 0, 5.0f).rotateY(k * 8);
            b.color(0.15f, 0.15f, 0.15f);
            b.bevelBox(-0.9f, 0, -1.2f, 0.9f, 7.0f, 1.4f, 0.4f, CAOUTCHOUC_USE);
            b.color(1);
            b.pop();
        }
        b.push().translate(11.0f, 4.6f, 2.6f).rotateY(20);                      // gants et cagoule sur le banc
        b.color(0.6f, 0.5f, 0.35f);
        b.bevelBox(-1.6f, 0, -0.8f, 1.6f, 0.6f, 0.8f, 0.2f, CUIR_USE);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 9.0f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 4011);
    }

    // ================================================================================================ 3. sac de saisie
    /** Sac de saisie de la police : grand sac de sport noir ouvert, scellé « SAISIE », paquets emballés de film
     *  (contrebande saisie), étiquettes de scellé ; sur une caisse grise, un paquet tombé. */
    static void sacSaisie(MeshBuilder b) {
        b.color(0.5f, 0.52f, 0.55f);
        b.bevelBox(2.0f, 0, 2.0f, 14.0f, 4.0f, 10.0f, 0.3f, GRAIN);
        b.color(1);
        b.push().translate(8.0f, 4.0f + 2.2f, 6.0f).rotateY(-8);
        b.color(0.12f, 0.12f, 0.13f);
        b.blob(5.4f, 2.2f, 2.8f, 0.8f, 0.06f, 2.0f, 4021, 14, CUIR_NOIR);
        b.color(1);
        for (int k = 0; k < 3; k++) {
            b.push().translate(-2.0f + k * 2.0f, 1.6f, 0.2f).rotateY(k * 12);
            b.color(0.85f, 0.82f, 0.72f);
            b.bevelBox(-0.9f, 0, -0.6f, 0.9f, 0.9f, 0.6f, 0.2f, SACHET);
            b.color(1);
            b.pop();
        }
        b.pop();
        Kit.pochoir(b, L_SAISIE, 'z', 8.0f, 2.0f, 10.02f, 5.0f, 1.2f);
        decal(b, ETIQ_SCELLE, 'z', 4.0f, 2.4f, 10.05f, 1.6f, 1.0f, -8);
        b.push().translate(11.0f, 0, 12.6f).rotateY(25);                        // paquet tombé
        b.color(0.85f, 0.82f, 0.72f);
        b.bevelBox(-0.9f, 0, -0.6f, 0.9f, 0.9f, 0.6f, 0.2f, SACHET);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 10.6f, 15.4f, 15.6f, 4, PAPIER, 1.1f, 4022);
    }

    // ================================================================================================ 4. malle de balisage
    /** Malle de balisage routier : coffre de plastique orange ouvert, cônes empilés, rubalise enroulée, gilet jaune,
     *  feux à éclats ; deux cônes renversés au sol. */
    static void cone(MeshBuilder b, float x, float y, float z, float rx, float ry) {
        b.push().translate(x, y, z).rotateY(ry).rotateX(rx);
        b.bevelBox(-1.6f, 0, -1.6f, 1.6f, 0.3f, 1.6f, 0.08f, SOMBRE);
        b.color(0.95f, 0.45f, 0.1f);
        b.lathe(new float[][]{{1.2f, 0.3f}, {0.25f, 6.0f}, {0.01f, 6.0f}}, 14, 0, 360, GRAIN, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.pop();
    }

    static void malleBalisage(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 3.4f, z1 = 10.6f, h = 4.6f;
        b.color(0.95f, 0.5f, 0.12f);
        b.bevelBox(x0, 0, z0, x1, 0.6f, z1, 0.2f, GRAIN);
        b.bevelBox(x0, 0.55f, z0, x0 + 0.5f, h, z1, 0.15f, GRAIN);
        b.bevelBox(x1 - 0.5f, 0.55f, z0, x1, h, z1, 0.15f, GRAIN);
        b.bevelBox(x0 + 0.45f, 0.55f, z0, x1 - 0.45f, h, z0 + 0.5f, 0.15f, GRAIN);
        b.bevelBox(x0 + 0.45f, 0.55f, z1 - 0.5f, x1 - 0.45f, h, z1, 0.15f, GRAIN);
        b.color(1);
        b.push().translate(0, h, z0).rotateX(-92).translate(0, -h, -z0);
        b.color(0.95f, 0.5f, 0.12f);
        b.bevelBox(x0, h + 0.05f, z0, x1, h + 0.6f, z1, 0.2f, GRAIN);
        b.color(1);
        b.pop();
        for (int k = 0; k < 3; k++) cone(b, 5.0f, 0.62f + k * 0.9f, 7.0f, 0, k * 10);
        b.push().translate(10.4f, 0.62f, 7.0f);                                  // rubalise rouge et blanche
        b.color(0.9f, 0.3f, 0.25f);
        b.cylinder(1.6f, 0, 1.6f, 14, GRAIN, GRAIN, GRAIN);
        b.color(1);
        b.pop();
        b.box(x0 + 0.6f, 2.2f, z1 + 0.02f, x1 - 0.6f, 3.0f, z1 + 0.08f, BANDE_REFL);
        cone(b, 4.0f, 1.6f, 13.0f, 90, 30);
        cone(b, 11.0f, 1.6f, 13.6f, 90, -60);
        jonchee(b, 0.6f, 11.0f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 4031);
    }

    // ================================================================================================ 5. herse cloutée
    /** Herse cloutée de gendarmerie : sac de transport noir ouvert, herse accordéon à demi dépliée au sol (pointes),
     *  poignée et cordon de traction. */
    static void herse(MeshBuilder b) {
        b.push().translate(3.6f, 1.4f, 4.0f).rotateY(10);
        b.color(0.12f, 0.12f, 0.13f);
        b.blob(3.0f, 1.4f, 2.2f, 0.75f, 0.06f, 2.0f, 4041, 12, CUIR_NOIR);
        b.color(1);
        b.pop();
        for (int k = 0; k < 7; k++) {                                            // éléments en accordéon
            float x = 5.0f + k * 1.4f;
            b.push().translate(x, 0.1f, 8.0f + (k % 2) * 0.4f).rotateY(k % 2 == 0 ? 30 : -30);
            b.bevelBox(-0.15f, 0, -2.4f, 0.15f, 0.5f, 2.4f, 0.05f, ACIER_BRUNI);
            for (int i = 0; i < 4; i++) {
                b.push().translate(0, 0.5f, -1.8f + i * 1.2f);
                b.cylinder(0.12f, 0, 0.7f, 4, METAL_NU, METAL_NU, METAL_NU);
                b.pop();
            }
            b.pop();
        }
        b.push().translate(14.6f, 0.32f, 8.2f).rotateZ(90);                    // poignée
        b.color(0.9f, 0.8f, 0.2f);
        b.cylinder(0.3f, -1.0f, 1.0f, 8, GRAIN, GRAIN, GRAIN);
        b.color(1);
        b.pop();
        b.tube(auSol(lisse(new float[][]{p(14.6f, 0.15f, 9.0f), p(13.4f, 0.12f, 11.6f), p(10.0f, 0.12f, 13.0f), p(7.0f, 0.12f, 12.4f)}, 6), 0.1f),
                fill(19, 0.08f), 4, AtlasLoot.ROPE, true, true, 0, false);
        Kit.pochoir(b, L_SECU_CIVILE, 'z', 3.6f, 1.4f, 6.2f, 2.6f, 0.6f);
        jonchee(b, 0.6f, 11.0f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 4042);
    }

    // ================================================================================================ 6. sac de carabine
    /** Sac de carabine de chasse camo posé sur un siège d'affût pliant, cartouchière, jumelles, sac à dos de chasse au
     *  pied ; un étui de cartouche au sol. */
    static void sacCarabine(MeshBuilder b) {
        for (int k = 0; k < 3; k++) {                                            // siège trépied
            double a = Math.toRadians(90 + k * 120);
            b.tube(new float[][]{p(6.0f, 7.0f, 6.0f), p(6.0f + (float) Math.cos(a) * 2.6f, 0.15f, 6.0f + (float) Math.sin(a) * 2.6f)},
                    fill(2, 0.15f), 6, METAL_NU, true, true, 0, false);
        }
        b.push().translate(6.0f, 7.0f, 6.0f);
        b.cylinder(2.2f, 0, 0.4f, 3, CAMO_CHASSE, CAMO_CHASSE, CAMO_CHASSE);
        b.pop();
        b.push().translate(1.0f, 0.5f, 2.4f).rotateZ(62);                       // sac de carabine appuyé au mur
        b.bevelBox(0, -0.9f, -0.6f, 17.0f, 0.9f, 0.6f, 0.4f, CAMO_CHASSE);
        b.pop();
        b.push().translate(6.0f, 7.4f, 6.0f).rotateY(20);                       // cartouchière
        b.color(0.35f, 0.26f, 0.16f);
        b.torus(1.4f, 0.25f, 14, 5, 0, 360, CUIR_USE);
        b.color(1);
        b.pop();
        b.push().translate(11.6f, 2.6f, 8.0f).rotateY(-15);                     // sac à dos de chasse
        b.blob(2.6f, 2.6f, 1.8f, 0.7f, 0.1f, 2.0f, 4051, 12, CAMO_CHASSE);
        b.pop();
        b.push().translate(4.0f, 0.32f, 12.6f).rotateY(40).rotateZ(90);
        b.color(0.8f, 0.15f, 0.12f);
        b.cylinder(0.3f, -0.6f, 0.6f, 6, GRAIN, AtlasCivil.BRASS, GRAIN);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 9.6f, 15.4f, 15.6f, 6, FEUILLES, 1.0f, 4052);
    }

    // ================================================================================================ 7. valise scientifique
    /** Valise de police scientifique ouverte : coque grise, mousse, pinceaux et poudres à empreintes, sachets de
     *  prélèvement, gants ; plots numérotés jaunes posés au sol. */
    static void valiseScientifique(MeshBuilder b) {
        float x0 = 2.0f, x1 = 14.0f, z0 = 3.6f, z1 = 10.6f, h = 3.4f;
        b.color(0.6f, 0.62f, 0.65f);
        b.bevelBox(x0, 0, z0, x1, h, z1, 0.4f, GRAIN);
        b.color(1);
        b.box(x0 + 0.5f, h - 0.02f, z0 + 0.5f, x1 - 0.5f, h + 0.04f, z1 - 0.5f, LotT56Atlas.MOUSSE_DECOUPE);
        b.push().translate(0, h, z0).rotateX(-95).translate(0, -h, -z0);
        b.color(0.6f, 0.62f, 0.65f);
        b.bevelBox(x0, h + 0.05f, z0, x1, h + 1.8f, z1, 0.4f, GRAIN);
        b.color(1);
        b.pop();
        for (int k = 0; k < 4; k++) {                                           // flacons de poudre
            b.push().translate(4.0f + k * 1.4f, h + 0.05f, 6.0f);
            b.color(k % 2 == 0 ? 0.15f : 0.85f, k % 2 == 0 ? 0.15f : 0.85f, k % 2 == 0 ? 0.15f : 0.82f);
            b.cylinder(0.5f, 0, 1.2f, 8, GRAIN, GRAIN, SOMBRE);
            b.color(1);
            b.pop();
        }
        for (int k = 0; k < 2; k++) {                                           // pinceaux
            b.push().translate(10.0f + k * 1.2f, h + 0.3f, 7.6f).rotateZ(-90);
            b.cylinder(0.15f, 0, 3.0f, 6, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
            b.pop();
        }
        b.box(4.0f, h + 0.06f, 7.8f, 7.6f, h + 0.12f, 9.4f, SACHET);
        for (int k = 0; k < 3; k++) {                                           // plots numérotés
            b.push().translate(4.0f + k * 3.6f, 0, 13.0f + (k % 2) * 0.8f).rotateY(k * 20 - 15);
            b.color(0.95f, 0.8f, 0.15f);
            b.prism(new float[][]{{-0.9f, 0}, {0.9f, 0}, {0, 1.6f}}, 1.0f, GRAIN, GRAIN);
            b.color(1);
            b.pop();
        }
        jonchee(b, 0.6f, 11.6f, 15.4f, 15.6f, 4, PAPIER, 1.0f, 4061);
    }

    // ================================================================================================ 8. mallette radar
    /** Mallette radar de vitesse : valise noire ouverte, pistolet radar dans la mousse, trépied replié, chargeur, carnet
     *  de procès-verbaux ; posée sur un plot de chantier. */
    static void malletteRadar(MeshBuilder b) {
        b.color(0.92f, 0.92f, 0.9f);
        b.bevelBox(3.0f, 0, 3.0f, 13.0f, 4.0f, 10.0f, 0.4f, GRAIN);                // plot (bloc béton plastique)
        b.color(1);
        b.box(3.4f, 1.6f, 10.02f, 12.6f, 2.4f, 10.08f, BANDE_REFL);
        float y = 4.02f;
        b.bevelBox(3.6f, y, 4.0f, 12.4f, y + 1.8f, 9.4f, 0.3f, SOMBRE);
        b.box(4.0f, y + 1.78f, 4.4f, 12.0f, y + 1.84f, 9.0f, LotT56Atlas.MOUSSE_DECOUPE);
        b.push().translate(0, y + 1.8f, 4.0f).rotateX(-100).translate(0, -y - 1.8f, -4.0f);
        b.bevelBox(3.6f, y + 1.85f, 4.0f, 12.4f, y + 3.2f, 9.4f, 0.3f, SOMBRE);
        b.pop();
        b.push().translate(7.0f, y + 1.85f, 6.8f).rotateY(-10);                 // pistolet radar
        b.color(0.9f, 0.85f, 0.2f);
        b.bevelBox(-1.6f, 0, -0.8f, 1.6f, 1.2f, 0.8f, 0.3f, GRAIN);
        b.color(1);
        b.push().translate(1.6f, 0.6f, 0).rotateZ(-90);
        b.cylinder(0.55f, 0, 0.8f, 10, SOMBRE, SOMBRE, AtlasLoot.GLASS);
        b.pop();
        b.pop();
        b.push().translate(10.4f, y + 1.86f, 7.4f).rotateY(15);                  // carnet de PV
        b.box(-0.9f, 0, -1.2f, 0.9f, 0.3f, 1.2f, PV);
        b.pop();
        b.push().translate(13.6f, 0.2f, 12.6f).rotateY(-30).rotateZ(90);         // trépied replié au sol
        b.cylinder(0.2f, -3.0f, 3.0f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        jonchee(b, 0.6f, 11.0f, 12.0f, 15.6f, 4, FEUILLES, 1.0f, 4071);
    }

    // ================================================================================================ 9. extincteur sur roues
    /** Extincteur sur roues (50 kg) : bouteille rouge sur chariot, flexible enroulé, lance, manomètre, étiquette de
     *  contrôle ; roues dégonflées, rouille au pied. */
    static void extincteurChariot(MeshBuilder b) {
        for (float z : new float[]{3.0f, 9.0f}) {
            b.push().translate(8.0f, 1.8f, z).rotateX(90);
            b.cylinder(1.8f, -0.6f, 0.6f, 14, CAOUTCHOUC_USE, METAL_NU, METAL_NU);
            b.pop();
        }
        b.push().translate(8.0f, 1.8f, 6.0f).rotateX(90);
        b.cylinder(0.3f, -3.0f, 3.0f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.push().translate(8.0f, 1.0f, 6.0f);
        b.color(0.8f, 0.14f, 0.1f);
        b.lathe(new float[][]{{0.01f, 0}, {2.6f, 0}, {2.8f, 0.6f}, {2.8f, 13.0f}, {2.0f, 14.4f}, {0.8f, 15.0f}, {0.01f, 15.0f}}, 18, 0, 360,
                PEINTURE_USEE, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.cylinder(0.5f, 15.0f, 16.4f, 10, CHROME, CHROME, CHROME);
        b.push().translate(0.9f, 15.6f, 0.6f).rotateX(90);                     // manomètre
        b.cylinder(0.6f, 0, 0.3f, 12, CHROME, CHROME, LotT56Atlas.CADRAN);
        b.pop();
        b.pop();
        decal(b, ETIQ_EXTINCTEUR, 'z', 8.0f, 8.0f, 8.84f, 2.6f, 3.4f, 0);
        b.push().translate(11.6f, 0, 6.0f);                                     // poignée-chariot
        b.bevelBox(-0.3f, 0.6f, -2.0f, 0.3f, 14.0f, -1.6f, 0.08f, METAL_NU);
        b.bevelBox(-0.3f, 0.6f, 1.6f, 0.3f, 14.0f, 2.0f, 0.08f, METAL_NU);
        b.bevelBox(-0.3f, 13.6f, -1.62f, 0.3f, 14.0f, 1.62f, 0.08f, METAL_NU);
        b.pop();
        b.tube(auSol(lisse(new float[][]{p(8.0f, 16.0f, 6.6f), p(10.0f, 15.0f, 9.0f), p(10.6f, 6.0f, 10.6f), p(9.0f, 0.4f, 12.6f), p(5.0f, 0.4f, 13.6f)}, 6), 0.4f),
                fill(25, 0.35f), 6, CAOUTCHOUC_USE, true, true, 0, false);
        b.push().translate(4.6f, 0.4f, 13.6f).rotateY(80).rotateZ(-90);
        b.cylinder(0.3f, 0, 2.4f, 8, SOMBRE, SOMBRE, SOMBRE);
        b.pop();
        boue(b, 'z', 8.0f, 1.0f, 8.84f, 5.0f, 1.2f, 1);
        jonchee(b, 0.6f, 11.6f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 4081);
    }

    // ================================================================================================ 10. bouée de sauvetage
    /** Bouée et sac de sauvetage (douane, garde-côtes) : bouée couronne orange à bandes réfléchissantes appuyée contre
     *  un coffre de bord blanc, sac à corde, gilet de sauvetage, corde lovée. */
    static void boueeSauvetage(MeshBuilder b) {
        b.color(0.92f, 0.92f, 0.9f);
        b.bevelBox(2.0f, 0, 2.4f, 14.0f, 5.0f, 9.6f, 0.4f, GRAIN);                 // coffre de bord
        b.color(1);
        Kit.pochoir(b, L_DOUANE, 'z', 8.0f, 3.0f, 9.62f, 5.0f, 1.2f);
        b.push().translate(8.0f, 5.0f + 2.9f, 3.2f).rotateX(80);               // bouée appuyée sur le coffre
        b.color(0.95f, 0.45f, 0.1f);
        b.torus(3.0f, 1.0f, 20, 8, 0, 360, GRAIN);
        b.color(1);
        b.pop();
        b.push().translate(12.6f, 1.6f, 12.6f);                                 // sac à corde
        b.color(0.9f, 0.75f, 0.15f);
        b.cylinder(1.4f, -1.6f, 1.4f, 12, TOILE_SECOURS, TOILE_SECOURS, TOILE_SECOURS);
        b.color(1);
        b.pop();
        b.push().translate(5.0f, 0.16f, 13.0f);                                 // corde lovée (à plat)
        for (int k = 0; k < 3; k++) b.torus(1.2f + k * 0.35f, 0.15f, 16, 4, 0, 360, AtlasLoot.ROPE);
        b.pop();
        b.push().translate(9.0f, 0.6f, 12.2f).rotateY(-20);                     // gilet de sauvetage plié
        b.color(0.95f, 0.5f, 0.12f);
        b.bevelBox(-1.8f, -0.6f, -1.2f, 1.8f, 0.6f, 1.2f, 0.4f, GRAIN);
        b.color(1);
        b.pop();
        jonchee(b, 0.6f, 10.6f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 4091);
    }

    // ================================================================================================ 11. motopompe
    /** Motopompe des pompiers : cadre tubulaire rouge, moteur, pompe à volute, réservoir, raccords symétriques, tuyau
     *  d'aspiration annelé ; bidon de mélange à côté. */
    static void motopompe(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 2.0f, z1 = 10.0f, h = 9.0f;
        for (float x : new float[]{x0, x1 - 0.6f}) for (float z : new float[]{z0, z1 - 0.6f}) b.bevelBox(x, 0, z, x + 0.6f, h, z + 0.6f, 0.15f, ROUGE_POMPIER);
        for (float y : new float[]{0.1f, h - 0.6f}) {
            b.bevelBox(x0 + 0.55f, y + 0.05f, z0 + 0.05f, x1 - 0.55f, y + 0.55f, z0 + 0.55f, 0.1f, ROUGE_POMPIER);
            b.bevelBox(x0 + 0.55f, y + 0.05f, z1 - 0.55f, x1 - 0.55f, y + 0.55f, z1 - 0.05f, 0.1f, ROUGE_POMPIER);
        }
        b.color(0.25f, 0.25f, 0.27f);
        b.bevelBox(3.0f, 0.7f, 3.0f, 8.6f, 6.4f, 9.0f, 0.5f, PEINTURE_USEE);         // moteur
        b.color(1);
        b.push().translate(11.0f, 3.6f, 6.0f).rotateX(90);                      // volute
        b.cylinder(2.4f, -1.4f, 1.4f, 16, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.color(0.8f, 0.14f, 0.1f);
        b.bevelBox(3.6f, 6.4f, 3.6f, 8.0f, 8.2f, 8.4f, 0.5f, PEINTURE_USEE);         // réservoir
        b.color(1);
        b.push().translate(11.0f, 3.6f, 7.4f).rotateX(90);                      // raccord de refoulement
        b.cylinder(0.9f, 0, 1.2f, 12, AtlasCivil.BRASS, AtlasCivil.BRASS, AtlasCivil.BRASS);
        b.pop();
        b.tube(auSol(lisse(new float[][]{p(13.4f, 3.6f, 6.0f), p(15.0f, 2.0f, 8.6f), p(14.0f, 0.7f, 12.0f), p(9.0f, 0.7f, 13.6f), p(4.0f, 0.7f, 13.0f)}, 6), 0.7f),
                fill(25, 0.7f), 8, CAOUTCHOUC_USE, true, true, 0, false);
        b.push().translate(2.0f, 0, 13.4f).rotateY(10);                          // bidon de mélange
        b.color(0.8f, 0.14f, 0.1f);
        b.bevelBox(-1.2f, 0, -0.8f, 1.2f, 3.4f, 0.8f, 0.3f, GRAIN);
        b.color(1);
        b.pop();
        Kit.pochoir(b, L_POMPIERS, 'z', 6.0f, 3.6f, 9.04f, 3.0f, 0.8f);
        decal(b, AUREOLE, 'y', 8.0f, 0.03f, 6.0f, 4.0f, 3.0f, 0);
        jonchee(b, 0.6f, 11.6f, 15.4f, 15.6f, 3, FEUILLES, 1.0f, 4101);
    }

    // ================================================================================================ 12. top-case de moto
    /** Top-case de moto de police (arraché avec sa platine) : coffre blanc à bande bleue, couvercle ouvert, radio
     *  portative, carnet, gyrophare bleu tombé, rétroviseur cassé au sol. */
    static void topcaseMoto(MeshBuilder b) {
        float x0 = 3.0f, x1 = 13.0f, z0 = 3.6f, z1 = 10.4f, h = 5.4f;
        b.bevelBox(x0 - 0.4f, 0, z0 - 0.4f, x1 + 0.4f, 0.4f, z1 + 0.4f, 0.1f, SOMBRE);  // platine
        b.color(0.93f, 0.93f, 0.92f);
        b.bevelBox(x0, 0.4f, z0, x1, 1.0f, z1, 0.3f, GRAIN);
        b.bevelBox(x0, 0.95f, z0, x0 + 0.5f, h, z1, 0.25f, GRAIN);
        b.bevelBox(x1 - 0.5f, 0.95f, z0, x1, h, z1, 0.25f, GRAIN);
        b.bevelBox(x0 + 0.45f, 0.95f, z0, x1 - 0.45f, h, z0 + 0.5f, 0.25f, GRAIN);
        b.bevelBox(x0 + 0.45f, 0.95f, z1 - 0.5f, x1 - 0.45f, h, z1, 0.25f, GRAIN);
        b.color(1);
        b.box(x0 + 0.4f, 2.6f, z1 + 0.02f, x1 - 0.4f, 3.4f, z1 + 0.08f, AtlasCivil.POLICE_BLUE);
        b.push().translate(0, h, z0).rotateX(-100).translate(0, -h, -z0);
        b.color(0.93f, 0.93f, 0.92f);
        b.bevelBox(x0, h + 0.05f, z0, x1, h + 1.6f, z1, 0.5f, GRAIN);
        b.color(1);
        b.pop();
        b.push().translate(6.0f, 1.05f, 7.0f).rotateZ(70);                       // radio portative couchée
        b.bevelBox(-0.6f, -0.9f, -0.4f, 0.6f, 2.4f, 0.4f, 0.15f, SOMBRE);
        b.pop();
        b.push().translate(10.0f, 1.05f, 6.8f).rotateY(15);                      // carnet
        b.box(-0.9f, 0, -1.2f, 0.9f, 0.3f, 1.2f, PV);
        b.pop();
        b.push().translate(4.0f, 0, 13.4f);                                      // gyrophare bleu tombé
        b.cylinder(1.0f, 0, 0.5f, 12, SOMBRE, SOMBRE, SOMBRE);
        b.color(0.4f, 0.55f, 1.0f);
        b.lathe(new float[][]{{0.9f, 0.5f}, {0.9f, 1.6f}, {0.5f, 2.2f}, {0.01f, 2.3f}}, 12, 0, 360, AtlasLoot.GLASS, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.pop();
        b.push().translate(11.6f, 0.2f, 13.0f).rotateY(-30).rotateZ(90);         // rétroviseur cassé
        b.cylinder(0.15f, -1.6f, 0.6f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        Kit.pochoir(b, AtlasCivil.S_POLICE, 'z', 8.0f, 4.4f, z1 + 0.02f, 3.6f, 0.9f);
        jonchee(b, 0.6f, 11.2f, 15.4f, 15.6f, 4, FEUILLES, 1.0f, 4111);
    }
}
