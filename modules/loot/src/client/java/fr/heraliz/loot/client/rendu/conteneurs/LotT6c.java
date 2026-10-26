package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Lot T6c (10/10, « que les joueurs ne croisent pas toujours le même bloc à loot ») : conteneurs T6 de plus, tous
 * uniques (aucun modèle repris d'un autre tiers) — coffre mural forcé, caisson de plomb, poste de sécurité, cage à
 * munitions, dewar d'azote, chariot de fonds, valise de drone, coffre de sol, boîte noire, pupitre de lancement.
 * Haute sécurité abandonnée : acier noir, rouge sécurité, inox, forcé, poussiéreux. Échelle réelle, dos au mur (z = 0).
 */
public final class LotT6c {
    private LotT6c() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t6_coffre_mural", "t6", "metal", "Coffre mural forcé", "Pried-open wall safe", LotT6c::coffreMural);
        Conteneurs.ajouter("t6_caisson_plomb", "t6", "metal", "Caisson de plomb sur chariot", "Lead cask on a dolly", LotT6c::caissonPlomb);
        Conteneurs.ajouter("t6_poste_securite", "t6", "metal", "Poste de sécurité", "Security desk", LotT6c::posteSecurite);
        Conteneurs.ajouter("t6_cage_munitions", "t6", "metal", "Cage à munitions", "Ammunition cage", LotT6c::cageMunitions);
        Conteneurs.ajouter("t6_dewar_azote", "t6", "metal", "Dewar d'azote liquide", "Liquid nitrogen dewar", LotT6c::dewarAzote);
        Conteneurs.ajouter("t6_chariot_fonds", "t6", "metal", "Chariot de transport de fonds", "Cash transport trolley", LotT6c::chariotFonds);
        Conteneurs.ajouter("t6_valise_drone", "t6", "plastique", "Valise de drone militaire", "Military drone case", LotT6c::valiseDrone);
        Conteneurs.ajouter("t6_coffre_sol", "t6", "metal", "Coffre de sol", "Floor safe", LotT6c::coffreSol);
        Conteneurs.ajouter("t6_boite_noire", "t6", "metal", "Boîte noire d'avion", "Flight recorder", LotT6c::boiteNoire);
        Conteneurs.ajouter("t6_pupitre_lancement", "t6", "metal", "Pupitre de lancement", "Launch console", LotT6c::pupitreLancement);
        for (String n : new String[]{"t6_coffre_mural", "t6_poste_securite", "t6_pupitre_lancement", "t6_coffre_sol"}) Conteneurs.usure(n, 0.6f);
        LotT6c2.enregistrer();
    }

    static final Mat ACIER = AtlasLoot.STEEL, CHROME = AtlasLoot.CHROME, CAOUTCHOUC = AtlasLoot.RUBBER, SOMBRE = AtlasLoot.DARK;

    /** Roulette pivotante (chape + roue) : sol en y = 0, centre (x, z). */
    static void roulette(MeshBuilder b, float x, float z, float r) {
        b.push().translate(x, r, z).rotateZ(90);
        b.cylinder(r, -0.32f, 0.32f, 10, CAOUTCHOUC, CAOUTCHOUC, CAOUTCHOUC);
        b.pop();
        b.bevelBox(x - 0.45f, r - 0.1f, z - 0.35f, x + 0.45f, 2 * r + 0.25f, z + 0.35f, 0.06f, METAL_NU);
    }

    /** Boîte de munitions en acier (30 x 18 x 14 cm), couvercle à levier. */
    static void boiteMunitions(MeshBuilder b, float x, float y, float z, float ang, boolean ouverte) {
        b.push().translate(x, y, z).rotateY(ang);
        b.color(0.42f, 0.46f, 0.32f);
        b.bevelBox(-2.4f, 0, -1.4f, 2.4f, 2.5f, 1.4f, 0.15f, PEINTURE_USEE);
        b.color(1);
        if (ouverte) {
            b.push().translate(0, 2.5f, -1.4f).rotateX(-70);
            b.color(0.42f, 0.46f, 0.32f);
            b.bevelBox(-2.45f, 0, 0, 2.45f, 0.3f, 2.85f, 0.1f, PEINTURE_USEE);
            b.color(1);
            b.pop();
            for (int k = 0; k < 5; k++) {
                b.push().translate(-1.6f + k * 0.8f, 2.0f, 0).rotateX(90);
                cartoucheSimple(b, 1.2f, 0.18f, AtlasCivil.BRASS, METAL_NU);
                b.pop();
            }
        } else {
            b.color(0.38f, 0.42f, 0.3f);
            b.bevelBox(-2.45f, 2.5f, -1.45f, 2.45f, 2.85f, 1.45f, 0.1f, PEINTURE_USEE);
            b.color(1);
            b.bevelBox(-0.6f, 2.85f, -0.25f, 0.6f, 3.05f, 0.25f, 0.06f, METAL_NU);
        }
        Kit.pochoir(b, AtlasLoot.S_762, 'z', 0, 1.2f, 1.42f, 3.2f, 1.0f);
        b.pop();
    }

    // ================================================================================================ 1. coffre mural
    /**
     * Coffre-fort mural dans un pan de mur de béton arraché : caisson d'acier noir encastré, porte (molette, croisillon,
     * pênes) forcée et grande ouverte, étagère intérieure avec dossiers et liasses, trou percé près de la serrure,
     * pied-de-biche au sol, papiers.
     */
    static void coffreMural(MeshBuilder b) {
        // pan de béton (dos au mur), arêtes cassées
        b.bevelBox(0.6f, 0, 0.05f, 15.4f, 15.2f, 3.2f, 0.35f, BETON);
        b.bevelBox(1.2f, 15.2f, 0.05f, 9.0f, 16.0f, 2.6f, 0.3f, BETON);
        // caisson
        float x0 = 3.2f, x1 = 12.8f, y0 = 3.6f, y1 = 12.4f, zf = 3.7f;
        b.bevelBox(x0, y0, 0.4f, x1, y1, 0.95f, 0.1f, ACIER_NOIR);
        b.bevelBox(x0, y0, 0.9f, x0 + 0.6f, y1, zf, 0.1f, ACIER_NOIR);
        b.bevelBox(x1 - 0.6f, y0, 0.9f, x1, y1, zf, 0.1f, ACIER_NOIR);
        b.bevelBox(x0 + 0.55f, y0, 0.9f, x1 - 0.55f, y0 + 0.6f, zf, 0.1f, ACIER_NOIR);
        b.bevelBox(x0 + 0.55f, y1 - 0.6f, 0.9f, x1 - 0.55f, y1, zf, 0.1f, ACIER_NOIR);
        niche(b, INOX, x0 + 0.65f, y0 + 0.65f, 1.0f, x1 - 0.65f, y1 - 0.65f, zf - 0.05f);
        b.bevelBox(x0 + 0.62f, 7.7f, 0.97f, x1 - 0.62f, 8.0f, zf - 0.2f, 0.04f, INOX);
        // contenu : dossiers debout, liasses
        for (int k = 0; k < 3; k++) {
            b.color(k == 1 ? 0.55f : 0.8f, k == 1 ? 0.62f : 0.72f, k == 1 ? 0.75f : 0.5f);
            b.push().translate(x0 + 1.3f + k * 0.55f, 8.0f, 1.0f).rotateZ(k == 2 ? -14 : 0);
            b.box(0, 0, 0, 0.45f, 3.2f, 2.4f, DOCUMENTS);
            b.pop();
            b.color(1);
        }
        liasse(b, 9.4f, 4.25f, 2.0f, 6, 0.6f);
        liasse(b, 9.6f, 4.85f, 2.1f, -10, 0.6f);
        liasse(b, 6.6f, 8.0f, 2.2f, 80, 0.45f);
        // porte forcée, ouverte à 115° (gond à gauche)
        b.push().translate(x0, 0, zf).rotateY(-100);
        b.bevelBox(0, y0 + 0.2f, 0, x1 - x0 - 0.1f, y1 - 0.2f, 1.1f, 0.14f, ACIER_NOIR);
        b.box(0.8f, y0 + 1.0f, -0.14f, x1 - x0 - 0.9f, y1 - 1.0f, -0.07f, METAL_NU);
        for (float y : new float[]{y0 + 2.2f, y1 - 2.2f}) {                  // pênes sortis
            b.push().translate(x1 - x0 + 0.85f, y, 0.55f).rotateZ(90);
            b.cylinder(0.32f, 0, 1.15f, 8, CHROME, CHROME, CHROME);
            b.pop();
        }
        b.push().translate((x1 - x0) / 2, (y0 + y1) / 2, 1.1f).rotateX(90);   // molette et croisillon
        b.cylinder(1.25f, 0, 0.45f, 18, CHROME, CHROME, CADRAN);
        b.pop();
        b.push().translate((x1 - x0) / 2, (y0 + y1) / 2 - 2.6f, 1.1f).rotateX(90);
        b.cylinder(0.35f, 0, 0.55f, 8, CHROME, CHROME, CHROME);
        b.pop();
        for (int k = 0; k < 3; k++) {                                          // croisillon dans le plan de la porte
            b.push().translate((x1 - x0) / 2, (y0 + y1) / 2 - 2.6f, 1.38f).rotateZ(k * 120);
            b.cylinder(0.13f, 0.3f, 1.6f, 6, CHROME, CHROME, CHROME);
            b.pop();
        }
        decal(b, BRULURE, 'z', (x1 - x0) / 2 + 2.2f, (y0 + y1) / 2 + 0.4f, 1.13f, 1.4f, 1.4f, 0);
        b.pop();
        // trou percé dans le caisson, éclats
        decal(b, IMPACTS, 'z', x1 - 0.4f, 8.6f, zf + 0.05f, 1.2f, 1.2f, 20);
        decal(b, GRIFFURES, 'z', x1 - 1.0f, 6.2f, zf + 0.05f, 2.2f, 1.0f, -10);
        // pied-de-biche rouge au sol, papiers, gravats
        b.color(0.7f, 0.14f, 0.12f);
        b.push().translate(11.0f, 0.22f, 9.5f).rotateY(28);
        b.tube(new float[][]{p(-4.2f, 0, 0), p(2.6f, 0, 0), p(3.4f, 0.1f, 0.4f), p(3.6f, 0.2f, 1.1f)}, fill(4, 0.2f), 6, PEINTURE_USEE, true, true, 0, false);
        b.pop();
        b.color(1);
        debris(b, 4.0f, 7.5f, 2.5f, 7, BETON, 931);
        jonchee(b, 1.0f, 5.0f, 15.0f, 14.5f, 6, PAPIER, 1.3f, 932);
        coulure(b, 'z', x0 + 0.6f, y0, 3.21f, 0.8f, 1);
        boue(b, 'z', 8.0f, 0, 3.21f, 13.0f, 1.4f, 0);
    }

    // ================================================================================================ 2. caisson de plomb
    /**
     * Caisson de transport de source radioactive (plomb gainé d'acier, couvercle boulonné, poignée) sanglé sur un chariot
     * à roulettes ; bande de danger, étiquette, boulons manquants, un boulon au sol, coulures.
     */
    static void caissonPlomb(MeshBuilder b) {
        b.bevelBox(2.6f, 1.25f, 1.0f, 13.4f, 1.75f, 12.0f, 0.12f, ACIER_NOIR);
        for (float x : new float[]{3.6f, 12.4f}) for (float z : new float[]{2.0f, 11.0f}) roulette(b, x, z, 0.62f);
        b.bevelBox(12.9f, 1.75f, 4.0f, 13.4f, 8.5f, 4.6f, 0.08f, METAL_NU);        // timon
        b.bevelBox(12.9f, 1.75f, 8.4f, 13.4f, 8.5f, 9.0f, 0.08f, METAL_NU);
        b.bevelBox(12.82f, 8.0f, 4.08f, 13.48f, 8.6f, 8.92f, 0.08f, CAOUTCHOUC);
        float cx = 7.6f, cz = 6.5f, r = 4.2f, y0 = 1.75f, y1 = 10.6f;
        b.push().translate(cx, 0, cz);
        b.color(0.5f, 0.52f, 0.56f);
        b.cylinder(r, y0, y1, 20, METAL_NU, METAL_NU, METAL_NU);
        b.color(1);
        b.cylinder(r + 0.25f, y1 - 0.02f, y1 + 1.1f, 20, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR);
        b.cylinder(r + 0.3f, y0 + 1.2f, y0 + 1.8f, 20, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR);
        for (int k = 0; k < 10; k++) {                                         // boulons du couvercle (deux manquent)
            if (k == 3 || k == 7) continue;
            double a = Math.toRadians(k * 36 + 10);
            b.push().translate((float) (Math.cos(a) * (r - 0.6f)), y1 + 1.1f, (float) (Math.sin(a) * (r - 0.6f)));
            b.cylinder(0.32f, 0, 0.4f, 6, METAL_NU, METAL_NU, METAL_NU);
            b.pop();
        }
        b.push().translate(0, y1 + 1.1f, 0).rotateX(90);
        b.torus(1.6f, 0.22f, 14, 6, 0, 180, CHROME);
        b.pop();
        b.pop();
        decalCylindre(b, AtlasLoot.HAZARD, cx, cz, r + 0.06f, 5.6f, 6.6f, 90, 300, 20);
        decalCylindre(b, AtlasLoot.S_DANGER, cx, cz, r + 0.1f, 7.0f, 9.4f, 75, 70, 8);
        decalCylindre(b, ETIQUETTE_BIO, cx, cz, r + 0.06f, 2.6f, 4.4f, 130, 40, 6);
        // sangle qui tient le caisson au timon
        b.color(0.6f, 0.45f, 0.12f);
        b.push().translate(cx, 4.0f, cz);
        b.hollowCylinder(r + 0.35f, r + 0.05f, 0, 0.9f, 20, SANGLE);
        b.pop();
        b.color(1);
        // boulon tombé au sol, coulures et boue
        b.push().translate(4.2f, 0, 13.6f);
        b.cylinder(0.32f, 0, 0.4f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        coulure(b, 'z', cx - 1.6f, y1, cz + r + 0.06f, 0.8f, 2);
        boue(b, 'z', 8.0f, 0, 12.05f, 11.0f, 1.0f, 1);
        jonchee(b, 1.0f, 12.5f, 15.0f, 15.5f, 4, FEUILLES, 1.0f, 941);
    }

    // ================================================================================================ 3. poste de sécurité
    /**
     * Poste de sécurité (deux blocs) : bureau métallique, trois moniteurs éteints (un écran fêlé, un renversé), clavier,
     * classeur des rondes, radio, mug, câbles qui pendent, tiroir sorti, papiers au sol.
     */
    static void posteSecurite(MeshBuilder b) {
        float y = 11.6f, z1 = 10.4f;
        b.bevelBox(0.6f, y, 0.3f, 30.4f, y + 0.6f, z1, 0.12f, ACIER_NOIR);
        b.bevelBox(0.8f, 0, 0.5f, 1.6f, y, z1 - 0.4f, 0.08f, ACIER_NOIR);
        b.bevelBox(29.4f, 0, 0.5f, 30.2f, y, z1 - 0.4f, 0.08f, ACIER_NOIR);
        b.bevelBox(1.6f, 3.0f, 0.5f, 29.4f, y - 0.05f, 1.1f, 0.06f, ACIER_NOIR);   // voile de fond
        // caisson à tiroirs, le tiroir du haut sorti
        b.bevelBox(22.0f, 0.2f, 1.2f, 29.3f, y - 0.05f, 9.6f, 0.1f, ACIER_NOIR);
        for (int k = 0; k < 3; k++) {
            float ya = 0.6f + k * 3.6f;
            float sort = k == 2 ? 4.0f : 0;
            b.bevelBox(22.3f, ya, 9.55f + sort - 0.3f, 29.0f, ya + 3.2f, 9.75f + sort, 0.08f, METAL_NU);
            b.bevelBox(24.8f, ya + 1.9f, 9.75f + sort, 26.5f, ya + 2.3f, 10.05f + sort, 0.05f, CHROME);
            if (k == 2) {
                niche(b, INTERIEUR_SALE, 22.4f, ya + 0.2f, 9.68f, 28.9f, ya + 3.0f, 9.55f + sort - 0.3f);
                b.box(22.6f, ya + 0.21f, 10.0f, 28.7f, ya + 0.5f, 13.4f, DOCUMENTS);
            }
        }
        // moniteurs
        float[][] ecr = {{5.0f, 0, 0}, {13.5f, 6, 0}, {21.0f, 14, 1}};
        for (float[] e : ecr) {
            b.push().translate(e[0], y + 0.6f, 4.2f).rotateY(e[1]);
            b.bevelBox(-1.2f, 0, -1.0f, 1.2f, 0.3f, 1.0f, 0.1f, SOMBRE);
            b.bevelBox(-0.3f, 0.3f, -0.3f, 0.3f, 2.2f, 0.3f, 0.08f, SOMBRE);
            b.bevelBox(-3.6f, 1.8f, -0.4f, 3.6f, 6.6f, 0.45f, 0.18f, SOMBRE);
            b.box(-3.25f, 2.15f, 0.46f, 3.25f, 6.25f, 0.5f, e[2] > 0 ? VERRE_FELE : ECRAN_OFF);
            b.pop();
        }
        // un quatrième, renversé à plat
        b.push().translate(9.0f, y + 0.66f, 8.0f).rotateY(-12);
        b.bevelBox(-3.0f, 0, -2.0f, 3.0f, 0.75f, 2.0f, 0.15f, SOMBRE);
        b.pop();
        // clavier, souris, classeur, radio, mug
        b.push().translate(12.5f, y + 0.6f, 7.8f).rotateY(-4);
        b.bevelBox(-3.4f, 0, -1.1f, 3.4f, 0.4f, 1.1f, 0.1f, SOMBRE);
        b.box(-3.2f, 0.41f, -0.95f, 3.2f, 0.45f, 0.95f, CLAVIER_PC);
        b.pop();
        b.push().translate(18.6f, y + 0.6f, 7.2f).rotateY(18);
        b.color(0.25f, 0.3f, 0.55f);
        b.bevelBox(-2.2f, 0, -1.6f, 2.2f, 0.9f, 1.6f, 0.12f, PLASTIQUE_USE);
        b.color(1);
        b.box(-2.0f, 0.91f, -1.4f, 2.0f, 0.94f, 1.4f, DOCUMENTS);
        b.pop();
        b.push().translate(26.8f, y + 0.6f, 5.0f).rotateY(-20);
        b.bevelBox(-0.9f, 0, -0.5f, 0.9f, 3.2f, 0.5f, 0.15f, SOMBRE);
        b.box(-0.6f, 1.6f, 0.51f, 0.6f, 2.8f, 0.54f, PANNEAU_RADIO);
        b.push().translate(0.55f, 3.2f, 0);
        b.cylinder(0.12f, 0, 2.6f, 6, CAOUTCHOUC, CAOUTCHOUC, CAOUTCHOUC);
        b.pop();
        b.pop();
        b.push().translate(3.0f, y + 0.6f, 8.2f);
        b.color(0.75f, 0.72f, 0.66f);
        b.cylinder(0.75f, 0, 1.5f, 10, PEINTURE_USEE, PEINTURE_USEE, INTERIEUR_SALE);
        b.color(1);
        b.pop();
        // câbles qui pendent sous le plateau
        cable(b, lisse(new float[][]{p(6.0f, y, 1.5f), p(6.4f, 6.0f, 2.2f), p(7.4f, 1.2f, 3.0f), p(9.0f, 0.12f, 4.6f)}, 6), 0.14f, 0.15f, 0.15f, 0.15f);
        cable(b, lisse(new float[][]{p(14.0f, y, 1.5f), p(13.4f, 7.0f, 2.0f), p(12.2f, 2.0f, 2.8f), p(11.0f, 0.12f, 5.0f)}, 6), 0.12f, 0.3f, 0.3f, 0.32f);
        jonchee(b, 2.0f, 10.8f, 30.0f, 15.5f, 9, PAPIER, 1.4f, 951);
        decal(b, AUREOLE, 'y', 5.0f, y + 0.61f, 6.5f, 2.0f, 1.8f, 0);
        decal(b, GRIFFURES, 'z', 26.0f, 5.0f, 9.76f, 2.4f, 1.0f, 0);
        boue(b, 'z', 15.0f, 0, z1, 26.0f, 1.0f, 0);
    }

    // ================================================================================================ 4. cage à munitions
    /**
     * Cage à munitions (1 x 2) : cadre de tubes rouge sécurité, grillage sur les côtés et le fond, porte grillagée
     * ouverte, cadenas coupé au sol ; trois étagères de boîtes de munitions (une ouverte), une caisse longue en bas.
     */
    static void cageMunitions(MeshBuilder b) {
        float x0 = 1.2f, x1 = 14.8f, z0 = 0.4f, z1 = 11.6f, h = 27.0f, t = 0.7f;
        for (float x : new float[]{x0, x1 - t}) for (float z : new float[]{z0, z1 - t}) b.bevelBox(x, 0, z, x + t, h, z + t, 0.1f, ROUGE_SECU);
        float e = 0.07f;
        for (float y : new float[]{0.3f, h - t}) {
            b.bevelBox(x0 + t - 0.05f, y + e, z0 + e, x1 - t + 0.05f, y + t - e, z0 + t - e, 0.06f, ROUGE_SECU);
            b.bevelBox(x0 + t - 0.05f, y + e, z1 - t + e, x1 - t + 0.05f, y + t - e, z1 - e, 0.06f, ROUGE_SECU);
            b.bevelBox(x0 + e, y + e, z0 + t - 0.05f, x0 + t - e, y + t - e, z1 - t + 0.05f, 0.06f, ROUGE_SECU);
            b.bevelBox(x1 - t + e, y + e, z0 + t - 0.05f, x1 - e, y + t - e, z1 - t + 0.05f, 0.06f, ROUGE_SECU);
        }
        LotT3bKit.grillageRect(b, false, z0 + 0.35f, x0 + t, x1 - t, 1.0f, h - t, 1);
        LotT3bKit.grillageRect(b, true, x0 + 0.35f, z0 + t, z1 - t, 1.0f, h - t, 1);
        LotT3bKit.grillageRect(b, true, x1 - 0.35f, z0 + t, z1 - t, 1.0f, h - t, -1);
        b.bevelBox(x0 + t - 0.05f, h - t + 0.12f, z0 + t - 0.05f, x1 - t + 0.05f, h - 0.2f, z1 - t + 0.05f, 0.05f, TOLE_PERFOREE);
        // étagères et contenu
        float[] ys = {1.0f, 9.4f, 17.8f};
        for (int k = 0; k < 3; k++) {
            float y = ys[k];
            b.bevelBox(x0 + t - 0.05f, y + 0.07f, z0 + t - 0.05f, x1 - t + 0.05f, y + 0.4f, z1 - t - 0.6f, 0.06f, INOX);
            if (k == 0) {
                b.color(0.36f, 0.4f, 0.26f);
                b.bevelBox(x0 + t + 0.4f, y + 0.4f, 2.0f, x1 - t - 0.4f, y + 3.6f, 8.0f, 0.15f, PEINTURE_USEE);
                b.color(1);
                Kit.pochoir(b, AtlasLoot.S_MISSILE, 'z', 8.0f, y + 2.0f, 8.01f, 6.0f, 1.4f);
            } else {
                boiteMunitions(b, 4.6f, y + 0.4f, 4.5f, 0, false);
                boiteMunitions(b, 10.6f, y + 0.4f, 4.6f, k == 1 ? 4 : -3, k == 2);
                if (k == 1) boiteMunitions(b, 4.8f, y + 3.45f, 4.4f, 7, false);
            }
        }
        // porte grillagée ouverte (gond à droite), cadenas coupé au sol
        b.push().translate(x1 - t / 2, 0, z1).rotateY(20);
        b.bevelBox(-(x1 - x0) + t, 0.5f, -0.3f, -(x1 - x0) + t + 0.6f, h - 0.5f, 0.3f, 0.06f, ROUGE_SECU);
        b.bevelBox(-0.6f, 0.5f, -0.3f, 0, h - 0.5f, 0.3f, 0.06f, ROUGE_SECU);
        b.bevelBox(-(x1 - x0) + t + 0.55f, 0.57f, -0.23f, -0.55f, 1.1f, 0.23f, 0.05f, ROUGE_SECU);
        b.bevelBox(-(x1 - x0) + t + 0.55f, h - 1.1f, -0.23f, -0.55f, h - 0.57f, 0.23f, 0.05f, ROUGE_SECU);
        b.pop();
        b.push().translate(x1 - t / 2, 0, z1).rotateY(20);
        LotT3bKit.grillageRect(b, false, 0.0f, -(x1 - x0) + t + 0.6f, -0.6f, 1.2f, h - 1.2f, 1);
        b.pop();
        LotT3bKit.cadenas(b, 4.2f, 1.25f, 13.4f, true);
        coulure(b, 'z', x0 + 0.35f, 20.0f, z1 + 0.02f, 0.6f, 0);
        boue(b, 'z', 8.0f, 0, z1, 14.0f, 1.0f, 0);
        jonchee(b, 0.5f, 12.0f, 15.5f, 15.5f, 4, FEUILLES, 1.0f, 961);
    }

    // ================================================================================================ 5. dewar d'azote
    /**
     * Dewar d'azote liquide (vase de 1,1 m) en inox sur sa base à roulettes : col, couvercle, manomètre, vannes,
     * flexible qui pend ; givre ancien, étiquette « échantillons », un portoir de tubes tombé au sol.
     */
    static void dewarAzote(MeshBuilder b) {
        float cx = 7.4f, cz = 6.4f;
        b.push().translate(cx, 0, cz);
        b.hollowCylinder(5.0f, 4.2f, 0.9f, 1.6f, 20, ACIER_NOIR);
        b.pop();
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            roulette(b, cx + (float) Math.cos(a) * 4.4f, cz + (float) Math.sin(a) * 4.4f, 0.45f);
        }
        b.push().translate(cx, 0, cz);
        b.lathe(new float[][]{{0.01f, 1.6f}, {4.0f, 1.6f}, {4.3f, 2.4f}, {4.3f, 14.2f}, {3.9f, 15.6f}, {2.4f, 16.6f}, {1.6f, 16.9f},
                {1.6f, 17.9f}, {0.01f, 17.9f}}, 22, 0, 360, INOX, 1, 1, 0, 0, 0, false);
        b.cylinder(2.0f, 17.9f, 18.6f, 16, CHROME, CHROME, ACIER_NOIR);
        b.push().translate(1.3f, 17.2f, 1.1f).rotateX(90);                    // manomètre
        b.cylinder(0.85f, 0, 0.35f, 14, CHROME, CHROME, CADRAN);
        b.pop();
        for (float a : new float[]{-0.9f, 0.8f}) {                             // vannes
            b.push().translate(a, 18.6f, -0.4f);
            b.cylinder(0.25f, 0, 0.9f, 8, AtlasCivil.BRASS, AtlasCivil.BRASS, AtlasCivil.BRASS);
            b.translate(0, 0.9f, 0).rotateX(90);
            b.torus(0.45f, 0.1f, 10, 5, 0, 360, ROUGE_SECU);
            b.pop();
        }
        b.pop();
        decalCylindre(b, S_ECHANTILLONS, cx, cz, 4.52f, 8.0f, 10.4f, 90, 70, 10);
        decalCylindre(b, ETIQUETTE_BIO, cx, cz, 4.48f, 11.0f, 12.6f, 60, 26, 4);
        b.color(0.92f, 0.95f, 1.0f);                                            // givre ancien au pied
        decalCylindre(b, MOISI, cx, cz, 4.54f, 2.4f, 4.6f, 90, 200, 14);
        b.color(1);
        // flexible qui pend jusqu'au sol
        cable(b, lisse(new float[][]{p(cx - 0.9f, 19.4f, cz - 0.4f), p(cx - 2.5f, 19.0f, cz + 1.0f), p(cx - 4.8f, 12.0f, cz + 3.6f),
                p(cx - 5.4f, 4.0f, cz + 5.4f), p(cx - 4.0f, 0.15f, cz + 7.4f)}, 7), 0.22f, 0.75f, 0.75f, 0.78f);
        // portoir renversé et tubes
        b.push().translate(11.8f, 0, 12.6f).rotateY(-25);
        b.bevelBox(-1.8f, 0, -0.9f, 1.8f, 0.9f, 0.9f, 0.08f, PLASTIQUE_USE);
        b.pop();
        for (int k = 0; k < 3; k++) {
            b.push().translate(10.0f + k * 0.9f, 0.18f, 14.2f - k * 0.3f).rotateY(30 * k - 20).rotateZ(90);
            b.color(0.85f, 0.85f, 0.9f);
            b.cylinder(0.18f, -0.9f, 0.9f, 6, VERRE_SALE, VERRE_SALE, ROUGE_SECU);
            b.color(1);
            b.pop();
        }
        boue(b, 'z', cx, 0, cz + 4.31f, 6.0f, 1.2f, 1);
        jonchee(b, 1.0f, 11.0f, 15.0f, 15.5f, 3, PAPIER, 1.2f, 971);
    }

    // ================================================================================================ 6. chariot de fonds
    /**
     * Chariot-cage de transport de fonds : plateau sur roulettes, grillage sur trois côtés, portillon ouvert ; sacs de
     * billets plombés (un éventré), liasses tombées, scellé coupé.
     */
    static void chariotFonds(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.6f, z1 = 12.4f, y0 = 1.5f, h = 15.0f, t = 0.45f;
        b.bevelBox(x0, y0, z0, x1, y0 + 0.5f, z1, 0.1f, INOX);
        for (float x : new float[]{x0 + 1.0f, x1 - 1.0f}) for (float z : new float[]{z0 + 1.0f, z1 - 1.0f}) roulette(b, x, z, 0.62f);
        for (float x : new float[]{x0 + 0.05f, x1 - t - 0.05f}) for (float z : new float[]{z0 + 0.05f, z1 - t - 0.05f}) b.bevelBox(x, y0 + 0.45f, z, x + t, h, z + t, 0.08f, INOX);
        b.bevelBox(x0 + t - 0.05f, h - t + 0.06f, z0 + 0.06f, x1 - t + 0.05f, h - 0.06f, z0 + t - 0.06f, 0.05f, INOX);
        b.bevelBox(x0 + 0.06f, h - t + 0.06f, z0 + t - 0.05f, x0 + t - 0.06f, h - 0.06f, z1 - t + 0.05f, 0.05f, INOX);
        b.bevelBox(x1 - t + 0.06f, h - t + 0.06f, z0 + t - 0.05f, x1 - 0.06f, h - 0.06f, z1 - t + 0.05f, 0.05f, INOX);
        LotT3bKit.grillageRect(b, false, z0 + 0.2f, x0 + t, x1 - t, y0 + 0.5f, h - t, 1);
        LotT3bKit.grillageRect(b, true, x0 + 0.2f, z0 + t, z1 - t, y0 + 0.5f, h - t, 1);
        LotT3bKit.grillageRect(b, true, x1 - 0.2f, z0 + t, z1 - t, y0 + 0.5f, h - t, -1);
        // portillon ouvert (gond à gauche)
        b.push().translate(x0 + t / 2, 0, z1 - t / 2).rotateY(-18);
        b.bevelBox(0, y0 + 0.6f, -0.2f, x1 - x0 - t - 0.35f, y0 + 1.0f, 0.2f, 0.05f, INOX);
        b.bevelBox(0, h - 1.0f, -0.2f, x1 - x0 - t - 0.35f, h - 0.6f, 0.2f, 0.05f, INOX);
        b.bevelBox(x1 - x0 - t - 0.4f, y0 + 0.65f, -0.26f, x1 - x0 - t, h - 0.65f, 0.26f, 0.05f, INOX);
        LotT3bKit.grillageRect(b, false, 0, 0.2f, x1 - x0 - t - 0.4f, y0 + 1.0f, h - 1.0f, 1);
        b.pop();
        // sacs plombés
        float[][] sacs = {{5.0f, 3.6f, 0, 3.0f}, {10.6f, 4.0f, 25, 3.2f}, {7.6f, 8.6f, -15, 2.8f}};
        for (int k = 0; k < sacs.length; k++) {
            float[] s = sacs[k];
            b.push().translate(s[0], y0 + 0.5f + s[3] * 0.8f, s[1]).rotateY(s[2]);
            b.color(0.62f, 0.6f, 0.52f);
            b.blob(2.4f, s[3] * 0.8f, 2.0f, 0.55f, 0.12f, 2.0f, 970 + k, 12, TISSU_USE);
            b.color(1);
            b.translate(0, s[3] * 0.8f, 0);
            b.cylinder(0.45f, -0.2f, 0.9f, 8, TISSU_USE, TISSU_USE, TISSU_USE);
            b.bevelBox(-0.3f, 0.5f, 0.42f, 0.3f, 1.1f, 0.62f, 0.05f, SCELLE);
            b.pop();
        }
        Kit.pochoir(b, S_FONDS, 'z', 5.0f, y0 + 2.8f, 5.6f, 2.6f, 1.0f);
        // liasses tombées, scellé coupé
        liasse(b, 9.0f, y0 + 0.52f, 10.6f, 12, 0.5f);
        liasse(b, 11.6f, 0, 14.4f, -30, 0.5f);
        liasse(b, 13.8f, 0.5f, 14.0f, 50, 0.45f);
        b.push().translate(4.0f, 0.06f, 14.3f).rotateY(20).rotateX(90);
        b.bevelBox(-0.3f, -0.38f, -0.06f, 0.3f, 0.38f, 0.06f, 0.05f, SCELLE);
        b.pop();
        boue(b, 'z', 8.0f, 0, z1, 12.0f, 0.9f, 1);
        jonchee(b, 0.5f, 13.0f, 15.5f, 15.6f, 4, FEUILLES, 1.0f, 981);
    }

    // ================================================================================================ 7. valise de drone
    /**
     * Valise de drone militaire (deux blocs) : coque noire à angles renforcés, couvercle ouvert, mousse découpée ; le
     * quadricoptère (bras repliés d'un côté, une hélice cassée), la radiocommande et une batterie sortie à côté.
     */
    static void valiseDrone(MeshBuilder b) {
        float x0 = 1.4f, x1 = 30.6f, z0 = 2.6f, z1 = 11.8f, h = 4.8f;
        b.bevelBox(x0, 0.4f, z0, x1, h, z1, 0.4f, ACIER_NOIR);
        for (float x : new float[]{x0 + 2.0f, x1 - 4.0f}) b.bevelBox(x, 0, z0 + 1.0f, x + 2.0f, 0.42f, z1 - 1.0f, 0.1f, CAOUTCHOUC);
        Kit.coins(b, x0, 0.4f, z0, x1, h, z1, 0.9f, CHROME);
        b.box(x0 + 0.6f, h - 0.02f, z0 + 0.6f, x1 - 0.6f, h + 0.03f, z1 - 0.6f, MOUSSE_DECOUPE);
        for (float x : new float[]{9.0f, 23.0f}) Kit.fermoir(b, x, h - 0.9f, z1, CHROME);
        Kit.pochoir(b, S_CONFIDENTIEL, 'z', 16.0f, 2.4f, z1 + 0.01f, 6.4f, 1.4f);
        // couvercle ouvert à 100°, mousse en pyramides
        b.push().translate(0, h, z0).rotateX(-84).translate(0, -h, -z0);
        b.bevelBox(x0, h + 0.06f, z0, x1, h + 2.2f, z1, 0.35f, ACIER_NOIR);
        b.box(x0 + 0.6f, h - 0.03f, z0 + 0.6f, x1 - 0.6f, h + 0.02f, z1 - 0.6f, MOUSSE_GRISE);
        b.pop();
        // drone : corps, bras, moteurs, hélices
        float dx = 13.0f, dz = 7.2f, dy = h + 0.05f;
        b.push().translate(dx, dy, dz).rotateY(8);
        b.bevelBox(-2.2f, 0.4f, -1.4f, 2.2f, 1.6f, 1.4f, 0.35f, SOMBRE);
        b.push().translate(0, 0.4f, 1.3f);
        b.sphere(0.6f, 10, 6, AtlasLoot.GLASS);
        b.pop();
        float[][] bras = {{40, 1}, {140, 1}, {220, 0}, {320, 1}};
        for (float[] br : bras) {
            b.push().rotateY(br[0]).translate(0, 1.0f, 0);
            b.bevelBox(1.6f, -0.25f, -0.3f, 5.6f, 0.25f, 0.3f, 0.1f, SOMBRE);
            b.translate(5.8f, 0, 0);
            b.cylinder(0.65f, -0.3f, 0.6f, 10, METAL_NU, METAL_NU, SOMBRE);
            if (br[1] > 0) {
                b.translate(0, 0.65f, 0).rotateY(br[0] * 0.7f);
                b.bevelBox(-2.4f, 0, -0.25f, 2.4f, 0.12f, 0.25f, 0.05f, PLASTIQUE_USE);
            } else {
                b.translate(0, 0.65f, 0);
                b.bevelBox(-0.9f, 0, -0.25f, 1.1f, 0.12f, 0.25f, 0.05f, PLASTIQUE_USE);
            }
            b.pop();
        }
        b.pop();
        // radiocommande et batterie à côté, sur la mousse et au sol
        b.push().translate(25.0f, h + 0.05f, 7.2f).rotateY(-12);
        b.bevelBox(-2.2f, 0, -1.4f, 2.2f, 0.8f, 1.4f, 0.3f, SOMBRE);
        b.box(-1.2f, 0.81f, -0.8f, 1.2f, 0.84f, 0.5f, ECRAN_OFF);
        for (float sx : new float[]{-1.6f, 1.6f}) {
            b.push().translate(sx, 0.8f, 0.6f);
            b.cylinder(0.15f, 0, 0.7f, 6, CHROME, CHROME, CAOUTCHOUC);
            b.pop();
        }
        b.pop();
        b.push().translate(6.0f, 0, 13.6f).rotateY(22);
        b.color(0.3f, 0.32f, 0.3f);
        b.bevelBox(-1.6f, 0, -0.8f, 1.6f, 1.2f, 0.8f, 0.15f, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        decal(b, GRIFFURES, 'z', 26.0f, 2.2f, z1 + 0.05f, 3.0f, 1.0f, 0);
        decal(b, BOUE_1, 'z', 6.0f, 1.0f, z1 + 0.05f, 4.0f, 1.4f, 0);
        jonchee(b, 1.0f, 12.4f, 31.0f, 15.2f, 6, FEUILLES, 1.1f, 991);
    }

    // ================================================================================================ 8. coffre de sol
    /**
     * Coffre de sol dans un socle de béton arraché du plancher : trappe d'acier à molette relevée contre le mur, cuve
     * intérieure (lingots, documents, boîte à bijoux), carrelage cassé autour, ciseau à froid et masse au sol.
     */
    static void coffreSol(MeshBuilder b) {
        float x0 = 1.0f, x1 = 15.0f, z0 = 0.6f, z1 = 13.4f, h = 4.4f;
        b.bevelBox(x0, 0, z0, x1, h, z1, 0.3f, BETON);
        b.color(0.8f, 0.75f, 0.68f);                                           // carrelage cassé sur le dessus
        b.box(x0 + 0.3f, h, z0 + 0.3f, 6.0f, h + 0.25f, z1 - 0.3f, LotT3bAtlas.LINO);
        b.color(1);
        float cx0 = 6.6f, cx1 = 13.8f, cz0 = 3.0f, cz1 = 11.0f;
        b.bevelBox(cx0 - 0.6f, h - 0.2f, cz0 - 0.6f, cx1 + 0.6f, h + 0.25f, cz1 + 0.6f, 0.08f, ACIER_NOIR);
        LotT3bKit.interieurHaut(b, cx0, 0.6f, cz0, cx1, h + 0.24f, cz1, ACIER_NOIR, INTERIEUR_SALE);
        lingot(b, 8.6f, 0.62f, 5.0f, 8, 2.6f, 1.1f, 0.65f);
        lingot(b, 8.8f, 0.62f, 6.4f, -4, 2.6f, 1.1f, 0.65f);
        lingot(b, 8.7f, 1.27f, 5.7f, 3, 2.6f, 1.1f, 0.65f);
        b.color(0.8f, 0.72f, 0.5f);
        b.push().translate(11.8f, 0.62f, 8.4f).rotateY(-10);
        b.box(-1.4f, 0, -1.8f, 1.4f, 0.9f, 1.8f, DOCUMENTS);
        b.pop();
        b.color(1);
        b.push().translate(11.4f, 0.62f, 4.6f).rotateY(15);
        b.color(0.35f, 0.08f, 0.1f);
        b.bevelBox(-1.0f, 0, -0.7f, 1.0f, 1.0f, 0.7f, 0.15f, CUIR_USE);
        b.color(1);
        b.pop();
        // trappe relevée contre le mur (charnière au fond)
        b.push().translate(0, h + 0.25f, cz0 - 0.6f).rotateX(-96).translate(0, -h - 0.25f, -cz0 + 0.6f);
        b.bevelBox(cx0 - 0.5f, h + 0.25f, cz0 - 0.5f, cx1 + 0.5f, h + 1.3f, cz1 + 0.5f, 0.15f, ACIER_NOIR);
        b.push().translate((cx0 + cx1) / 2, h + 1.3f, (cz0 + cz1) / 2);
        b.cylinder(1.3f, 0, 0.4f, 18, CHROME, CHROME, CADRAN);
        b.pop();
        b.pop();
        // ciseau à froid et masse au sol, éclats de carrelage
        b.push().translate(4.0f, 0.2f, 14.4f).rotateY(8).rotateZ(90);
        b.cylinder(0.2f, -2.4f, 2.4f, 6, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        b.push().translate(11.5f, 0, 14.4f).rotateY(-15);
        b.bevelBox(-0.7f, 0, -0.7f, 0.7f, 1.4f, 0.7f, 0.1f, METAL_NU);
        b.push().translate(0.7f, 0.7f, 0).rotateZ(-90);
        b.cylinder(0.25f, 0, 4.0f, 6, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
        b.pop();
        b.pop();
        debris(b, 3.0f, 14.6f, 1.6f, 6, LotT3bAtlas.LINO, 1001);
        debris(b, 14.0f, 14.6f, 1.4f, 4, BETON, 1002);
        boue(b, 'z', 8.0f, 0, z1, 13.0f, 1.2f, 0);
    }

    // ================================================================================================ 9. boîte noire
    /**
     * Boîte noire d'avion (enregistreur orange à bandes réfléchissantes) arrachée de son plateau de fixation : connecteur
     * arraché, câbles coupés, brûlures, plaque « NE PAS OUVRIR », posée de travers sur un débris de cloison.
     */
    static void boiteNoire(MeshBuilder b) {
        // débris de cloison d'avion (tôle grise rivetée) au sol
        b.push().translate(8.0f, 0, 6.2f).rotateY(-8);
        b.bevelBox(-6.4f, 0, -4.6f, 6.4f, 0.4f, 4.6f, 0.06f, METAL_NU);
        rivetsZ(b, -6.0f, 6.0f, 0.4f, 4.4f, 9, 0.18f, METAL_NU);
        decal(b, BRULURE, 'y', 3.0f, 0.42f, -1.0f, 4.0f, 3.4f, 30);
        b.pop();
        // plateau de fixation tordu
        b.push().translate(6.0f, 0.4f, 6.0f).rotateY(5).rotateZ(-6);
        b.bevelBox(-3.8f, 0.05f, -2.6f, 3.8f, 0.3f, 2.6f, 0.06f, ACIER_NOIR);
        b.bevelBox(-3.8f, 0.25f, -2.55f, -3.3f, 1.4f, 2.55f, 0.06f, ACIER_NOIR);
        b.pop();
        // l'enregistreur, de travers
        b.push().translate(8.4f, 0.9f, 6.6f).rotateY(-22).rotateZ(14);
        b.color(0.95f, 0.45f, 0.12f);
        b.bevelBox(-3.6f, 0, -2.4f, 3.6f, 4.4f, 2.4f, 0.3f, PEINTURE_USEE);
        b.color(1);
        for (float x : new float[]{-2.2f, 2.2f}) b.box(x - 0.4f, -0.06f, -2.46f, x + 0.4f, 4.46f, 2.46f, LotT3bAtlas.BANDE_REFL);
        b.push().translate(-1.0f, 4.4f, 0).rotateY(90);
        b.color(0.95f, 0.45f, 0.12f);
        b.cylinder(1.4f, 0, 0.9f, 16, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
        b.color(1);
        b.pop();
        Kit.pochoir(b, S_FRAGILE, 'z', -0.1f, 2.2f, 2.5f, 3.0f, 1.0f);
        decal(b, BRULURE, 'z', 2.9f, 1.0f, 2.55f, 1.0f, 1.6f, 20);
        b.bevelBox(3.6f, 1.0f, -1.2f, 4.4f, 3.0f, 1.2f, 0.08f, SOMBRE);        // connecteur arraché
        b.pop();
        cable(b, lisse(new float[][]{p(12.3f, 2.4f, 5.0f), p(13.2f, 1.6f, 6.2f), p(13.6f, 0.5f, 8.4f), p(12.6f, 0.45f, 10.6f)}, 6), 0.16f, 0.12f, 0.12f, 0.12f);
        cable(b, lisse(new float[][]{p(12.4f, 2.0f, 6.0f), p(13.8f, 1.2f, 7.4f), p(14.6f, 0.45f, 9.6f)}, 6), 0.12f, 0.6f, 0.15f, 0.12f);
        debris(b, 3.0f, 12.0f, 2.4f, 8, METAL_NU, 1011);
        jonchee(b, 1.0f, 11.0f, 15.0f, 15.5f, 5, FEUILLES, 1.0f, 1012);
    }

    // ================================================================================================ 10. pupitre de lancement
    /**
     * Pupitre de lancement (deux blocs) : meuble en acier à plan incliné, deux serrures à clé (une clé encore en place),
     * capot rouge relevé sur le bouton de tir, rangées de voyants (éteints, un rouge), écrans éteints, classeur des
     * procédures ouvert, fauteuil absent ; poussière et griffures.
     */
    static void pupitreLancement(MeshBuilder b) {
        float x0 = 0.8f, x1 = 31.2f, z0 = 0.3f;
        // corps : profil (z, y) extrudé sur x : plan incliné vers le joueur
        float[][] prof = {{z0, 0}, {11.4f, 0}, {11.4f, 11.4f}, {5.2f, 14.6f}, {z0, 14.6f}};
        b.color(0.46f, 0.5f, 0.46f);                                            // vert-de-gris militaire
        b.extrudeProfileX(prof, x0, x1, PEINTURE_USEE);
        b.profileCapX(prof, x0, false, PEINTURE_USEE);
        b.profileCapX(prof, x1, true, PEINTURE_USEE);
        b.color(1);
        b.box(x0 + 0.5f, 0.2f, 11.46f, x1 - 0.5f, 1.0f, 11.6f, CAOUTCHOUC);
        // panneau incliné : plaque de commande
        float ang = (float) Math.toDegrees(Math.atan2(14.6f - 11.4f, 11.4f - 5.2f));
        b.push().translate(0, 11.4f, 11.4f).rotateX(ang);
        b.box(x0 + 1.0f, 0.1f, -6.4f, x1 - 1.0f, 0.16f, -0.6f, METAL_NU);
        for (int k = 0; k < 12; k++) {                                          // voyants
            Mat led = k == 7 ? LED_ROUGE : LED_ETEINTE;
            b.push().translate(3.0f + k * 1.6f, 0.15f, -5.0f);
            b.cylinder(0.32f, 0, 0.25f, 8, CHROME, CHROME, led);
            b.pop();
        }
        for (float x : new float[]{6.0f, 12.0f}) {                              // serrures à clé
            b.push().translate(x, 0.15f, -2.6f);
            b.cylinder(0.75f, 0, 0.35f, 12, CHROME, CHROME, KEYPAD_ACIER);
            b.pop();
        }
        b.push().translate(6.0f, 0.44f, -2.6f).rotateY(20);                    // clé restée en place
        b.bevelBox(-0.12f, 0, -0.5f, 0.12f, 0.7f, 0.5f, 0.05f, AtlasCivil.BRASS);
        b.pop();
        b.push().translate(18.0f, 0.15f, -2.8f);                                // bouton de tir sous capot rouge relevé
        b.bevelBox(-1.4f, 0, -1.4f, 1.4f, 0.4f, 1.4f, 0.1f, ACIER_NOIR);
        b.cylinder(0.8f, 0.4f, 0.9f, 14, ROUGE_SECU, ROUGE_SECU, ROUGE_SECU);
        b.push().translate(0, 0.4f, -1.4f).rotateX(-105);
        b.bevelBox(-1.5f, 0, 0, 1.5f, 0.3f, 2.8f, 0.1f, ROUGE_SECU);
        b.pop();
        b.pop();
        b.box(22.0f, 0.15f, -5.6f, 29.6f, 0.24f, -1.4f, LCD_OFF);
        decal(b, AtlasLoot.HAZARD, 'y', 18.0f, 0.23f, -0.9f, 4.4f, 0.6f, 0);
        b.pop();
        // écrans dans le meuble du haut
        b.bevelBox(x0 + 0.4f, 14.55f, z0 + 0.05f, x1 - 0.4f, 21.6f, 3.4f, 0.2f, ACIER_NOIR);
        for (float x : new float[]{6.0f, 16.0f, 26.0f}) b.box(x - 3.6f, 15.6f, 3.45f, x + 3.6f, 20.6f, 3.5f, x == 16.0f ? VERRE_FELE : ECRAN_OFF);
        // classeur des procédures ouvert sur le plan (posé sur l'écran)
        b.push().translate(0, 11.4f, 11.4f).rotateX(ang).translate(26.0f, 0.32f, -3.2f).rotateY(-6);
        b.color(0.75f, 0.2f, 0.18f);
        b.bevelBox(-2.6f, 0, -1.8f, 2.6f, 0.24f, 1.8f, 0.06f, PLASTIQUE_USE);
        b.color(1);
        b.box(-2.4f, 0.25f, -1.6f, 2.4f, 0.28f, 1.6f, DOCUMENTS);
        b.pop();
        Kit.pochoir(b, S_CONFIDENTIEL, 'z', 16.0f, 6.0f, 11.42f, 7.0f, 1.6f);
        decal(b, GRIFFURES, 'z', 7.0f, 4.0f, 11.45f, 3.0f, 1.2f, 0);
        decal(b, GRIFFURES, 'z', 25.0f, 8.0f, 11.45f, 2.6f, 1.0f, 30);
        boue(b, 'z', 16.0f, 1.0f, 11.62f, 28.0f, 1.0f, 1);
        jonchee(b, 1.0f, 12.0f, 31.0f, 15.6f, 8, PAPIER, 1.3f, 1021);
    }
}
