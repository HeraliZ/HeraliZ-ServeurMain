package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1aAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1aKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T1a — rue, commerces, déchets : le loot le plus commun, partout en ville (sacs poubelle, bacs, bennes, cartons,
 * journaux, caddie, palettes, cagettes, vitrines et distributeurs pillés, affaires abandonnées, pneus, fûts...).
 * Échelle réelle (1 px = 6,25 cm, joueur 1,8 m), dos au mur en z = 0, posés au sol, usure DayZ partout.
 * Modèles : ce fichier (rue, déchets), {@link LotT1a2} (commerces), {@link LotT1a3} (affaires, atelier, marché).
 */
public final class LotT1a {
    private LotT1a() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t1_sacs_poubelle", "t1", "plastique", "Sacs poubelle", "Garbage bags", LotT1a::sacsPoubelle);
        Conteneurs.ajouter("t1_poubelle_metal", "t1", "metal", "Poubelle en tôle", "Metal trash can", LotT1a::poubelleMetal);
        Conteneurs.ajouter("t1_bac_roulant", "t1", "plastique", "Bac à ordures", "Wheelie bin", LotT1a::bacRoulant);
        Conteneurs.ajouter("t1_benne", "t1", "metal", "Benne à ordures", "Dumpster", LotT1a::benne);
        Conteneurs.ajouter("t1_cartons_ecrases", "t1", "carton", "Cartons abandonnés", "Abandoned boxes", LotT1a::cartonsEcrases);
        Conteneurs.ajouter("t1_paquets_journaux", "t1", "carton", "Paquets de journaux", "Newspaper bundles", LotT1a2::paquetsJournaux);
        Conteneurs.ajouter("t1_caddie_renverse", "t1", "metal", "Caddie renversé", "Overturned shopping cart", LotT1a2::caddie);
        Conteneurs.ajouter("t1_palette_cartons", "t1", "bois", "Palette de marchandises", "Pallet of goods", LotT1a2::paletteCartons);
        Conteneurs.verre("t1_palette_cartons", LotT1a2::paletteFilm);
        Conteneurs.ajouter("t1_cagettes_marche", "t1", "plastique", "Cagettes de marché", "Market crates", LotT1a2::cagettesMarche);
        Conteneurs.ajouter("t1_caisses_biere", "t1", "plastique", "Casiers à bière", "Beer crates", LotT1a2::caissesBiere);
        Conteneurs.ajouter("t1_frigo_boissons", "t1", "metal", "Vitrine réfrigérée", "Drinks fridge", LotT1a2::frigoBoissons);
        Conteneurs.verre("t1_frigo_boissons", LotT1a2::frigoVerre);
        Conteneurs.collision("t1_frigo_boissons", new float[]{3.0f, 0, 0.3f, 13.0f, 31.6f, 10.2f});
        Conteneurs.ajouter("t1_distributeur_snacks", "t1", "metal", "Distributeur automatique", "Vending machine", LotT1a2::distributeur);
        Conteneurs.verre("t1_distributeur_snacks", LotT1a2::distributeurVerre);
        Conteneurs.ajouter("t1_corbeille_rue", "t1", "metal", "Corbeille de rue", "Street litter bin", LotT1a2::corbeilleRue);
        Conteneurs.verre("t1_corbeille_rue", LotT1a2::corbeilleSac);
        Conteneurs.collision("t1_corbeille_rue", new float[]{3.6f, 0, 0.8f, 12.8f, 15.6f, 9.4f});
        Conteneurs.ajouter("t1_sacs_courses", "t1", "plastique", "Sacs de courses", "Grocery bags", LotT1a3::sacsCourses);
        Conteneurs.ajouter("t1_cartable", "t1", "tissu", "Cartable d'écolier", "School backpack", LotT1a3::cartable);
        Conteneurs.ajouter("t1_sac_a_main", "t1", "tissu", "Sac à main", "Handbag", LotT1a3::sacAMain);
        Conteneurs.ajouter("t1_valise_ouverte", "t1", "plastique", "Valise ouverte", "Open suitcase", LotT1a3::valiseOuverte);
        Conteneurs.ajouter("t1_tas_vetements", "t1", "tissu", "Tas de vêtements", "Pile of clothes", LotT1a3::tasVetements);
        Conteneurs.ajouter("t1_pneus", "t1", "plastique", "Pneus usagés", "Used tyres", LotT1a3::pneus);
        Conteneurs.ajouter("t1_fut_brule", "t1", "metal", "Fût brûle-tout", "Burn barrel", LotT1a3::futBrule);
        Conteneurs.ajouter("t1_jerricans", "t1", "plastique", "Bidons en plastique", "Plastic jerrycans", LotT1a3::jerricans);
        Conteneurs.ajouter("t1_pots_peinture", "t1", "metal", "Pots de peinture", "Paint cans", LotT1a3::potsPeinture);
        Conteneurs.ajouter("t1_boites_pizza", "t1", "carton", "Boîtes à pizza", "Pizza boxes", LotT1a3::boitesPizza);
        Conteneurs.ajouter("t1_bac_recyclage", "t1", "plastique", "Bac de recyclage", "Recycling bin", LotT1a2::bacRecyclage);
        Conteneurs.ajouter("t1_presentoir_journaux", "t1", "metal", "Présentoir à journaux", "Newspaper box", LotT1a2::presentoir);
        Conteneurs.verre("t1_presentoir_journaux", LotT1a2::presentoirVerre);
        Conteneurs.ajouter("t1_etal_marche", "t1", "bois", "Étal de marché", "Market stall", LotT1a3::etalMarche);
    }

    // =================================================================== 1. sacs poubelle

    /**
     * Quatre sacs noirs de 60 L entassés contre le mur : deux debout noués (l'un gris, plus petit, appuyé sur
     * l'autre), un couché devant, un éventré qui a vomi ses déchets (canette, papiers, brique de lait, barquette,
     * journal), flaque de jus au pied.
     */
    static void sacsPoubelle(MeshBuilder b) {
        // A : debout au fond, à gauche, noué
        float hA = 11.0f;
        Matrix4f tA = place(4.4f, 0, 4.6f, 20, -4, 3);
        mou(b, tA, profilSac(3.5f, hA), 16, 1.05f, 0.92f, 0.07f, 0.35f, 1, SAC_POUBELLE, 0);
        Vector3f s = point(tA, 0, hA * 0.95f, 0);
        noeud(b, s.x, s.y, s.z, 30, SAC_POUBELLE);
        // B : sac gris plus petit, appuyé sur A
        float hB = 9.8f;
        Matrix4f tB = place(11.4f, 0, 4.2f, -35, -4, 9);
        b.color(0.42f, 0.45f, 0.5f);
        mou(b, tB, profilSac(3.2f, hB), 16, 1.0f, 0.95f, 0.08f, 0.4f, 2, SAC_BLANC, 0);
        s = point(tB, 0, hB * 0.95f, 0);
        noeud(b, s.x, s.y, s.z, -20, SAC_BLANC);
        b.color(1);
        // C : couché devant à gauche, le nœud vers la droite
        float hC = 10.2f;
        Matrix4f tC = place(5.6f, 2.35f, 10.3f, -18, 0, -90);
        mou(b, tC, decale(profilSac(3.3f, hC), -hC / 2), 16, 0.72f, 1.0f, 0.08f, 0.4f, 3, SAC_POUBELLE, 0);
        s = point(tC, 0, hC * 0.45f, 0);
        b.push().translate(s.x, s.y, s.z).rotateZ(-80);
        noeud(b, 0, 0, 0, 0, SAC_POUBELLE);
        b.pop();
        // D : éventré, penché vers l'avant, bord déchiqueté, doublure visible
        Matrix4f tD = place(12.3f, 0, 8.3f, -10, 58, 0);
        float[][] pd = {{0.01f, 0}, {2.4f, 0.05f}, {3.2f, 0.8f}, {3.45f, 2.6f}, {3.25f, 4.4f}, {2.8f, 5.7f}, {2.5f, 6.5f}};
        mou(b, tD, pd, 16, 1.0f, 0.88f, 0.08f, 0.45f, 4, SAC_POUBELLE, 0, false, 0.6f);
        float[][] pi = new float[pd.length - 2][];
        for (int i = 0; i < pi.length; i++) pi[i] = new float[]{pd[i + 2][0] * 0.94f, pd[i + 2][1]};
        mou(b, tD, pi, 16, 1.0f, 0.88f, 0.08f, 0.45f, 4, SAC_POUBELLE, 0, true, 0.6f);
        // ce qui en est sorti
        champ(b, 9.4f, 11.6f, 15.2f, 14.6f, 0.2f, 0.35f, 0.9f, 5, 4, 7, ORDURES);
        briqueLait(b, 13.4f, 14.8f, -35);
        canetteEcrasee(b, 9.4f, 14.9f, CAN_COLA, 50, 3);
        papierFroisse(b, 14.8f, 0, 12.4f, 0.85f, PAPIER, 1);
        papierFroisse(b, 10.6f, 0.2f, 12.6f, 0.75f, JOURNAL, 2);
        papierFroisse(b, 8.4f, 0, 12.2f, 0.6f, PAPIER, 3);
        b.push().translate(12.0f, 0.3f, 12.9f).rotateY(25).rotateX(-8);
        b.bevelBox(-1.3f, 0, -0.9f, 1.3f, 0.4f, 0.9f, 0.12f, WHITE);
        b.face(RESTES, MeshBuilder.v(0, 1, 0), p(-1.1f, 0.47f, 0.7f), p(1.1f, 0.47f, 0.7f), p(1.1f, 0.47f, -0.7f), p(-1.1f, 0.47f, -0.7f));
        b.pop();
        bouteillePet(b, 11.6f, 15.0f, 8, PET_EAU, PLASTIC_BLUE, 0.6f);
        feuille(b, 7.8f, 0, 14.2f, 2.8f, 3.6f, 28, 0.12f, UNE);
        decalO(b, D_FLAQUE, 'y', 9.8f, 0, 9.6f, 6.0f, 4.5f, 20, 0.07f);
        jonchee(b, 1.0f, 12.6f, 7.0f, 15.6f, 5, PAPIER, 1.1f, 5);
    }

    // =================================================================== 2. poubelle en tôle

    /**
     * Poubelle ronde galvanisée (75 cm, Ø 50) contre le mur : nervurée, cabossée, piquée de rouille, deux anses ; un sac
     * noir déborde, noué ; le couvercle tombé est appuyé contre elle ; canette et papiers au pied.
     */
    static void poubelleMetal(MeshBuilder b) {
        float cx = 6.1f, cz = 5.0f;
        Bosse coups = (x, y, z) -> coup(cx + 1.7f, 6.0f, cz + 3.9f, 2.4f, 0.55f).enfonce(x, y, z)
                + coup(cx - 3.0f, 10.2f, cz + 2.6f, 1.9f, 0.4f).enfonce(x, y, z);
        float[][] corps = rond(new float[]{7.1f, 0}, new float[]{7.4f, 0.2f}, new float[]{7.4f, 0.6f}, new float[]{7.3f, 0.75f},
                new float[]{7.45f, 3.2f}, new float[]{7.85f, 3.45f}, new float[]{7.85f, 3.65f}, new float[]{7.5f, 3.9f},
                new float[]{7.75f, 7.7f}, new float[]{8.15f, 7.95f}, new float[]{8.15f, 8.15f}, new float[]{7.8f, 8.4f},
                new float[]{8.0f, 11.1f}, new float[]{8.35f, 11.25f}, new float[]{8.4f, 11.55f}, new float[]{8.1f, 11.7f});
        cuve(b, cx, cz, corps, 5, GALVA_ROUILLE, false, coups);
        cuve(b, cx, cz, rond(new float[]{7.95f, 11.68f}, new float[]{7.6f, 8.0f}), 5, GALVA_ROUILLE, true, null);
        // anses
        for (int sg = -1; sg <= 1; sg += 2) {
            float x = cx + sg * 3.95f;
            for (float dz : new float[]{-1.1f, 1.1f}) b.bevelBox(Math.min(x, x + sg * 0.45f), 9.1f, cz + dz - 0.3f, Math.max(x, x + sg * 0.45f), 10.1f, cz + dz + 0.3f, 0.08f, GALVA);
            b.tube(new float[][]{p(x + sg * 0.35f, 9.5f, cz - 1.1f), p(x + sg * 0.85f, 8.95f, cz - 0.9f), p(x + sg * 0.95f, 8.75f, cz),
                    p(x + sg * 0.85f, 8.95f, cz + 0.9f), p(x + sg * 0.35f, 9.5f, cz + 1.1f)}, fill(5, 0.13f), 6, GALVA, false, false, 0, false);
        }
        // sac qui déborde : jupe rabattue sur le bord, dôme noué
        Bosse ondule = (x, y, z) -> -0.13f * (float) Math.sin(Math.atan2(z - cz, x - cx) * 7) * (y < 11.0f ? 1 : 0.3f);
        cuve(b, cx, cz, rond(new float[]{8.55f, 10.1f}, new float[]{8.8f, 11.3f}, new float[]{8.75f, 11.85f}, new float[]{8.05f, 12.02f}, new float[]{7.4f, 11.98f}), 5, SAC_POUBELLE, false, ondule);
        mou(b, place(cx, 11.7f, cz, 0, 0, 0), new float[][]{{3.75f, 0}, {3.9f, 0.7f}, {3.4f, 1.6f}, {2.4f, 2.3f}, {1.0f, 2.75f}, {0.35f, 2.95f}, {0.01f, 3.05f}},
                16, 1, 1, 0.08f, 0.5f, 5, SAC_POUBELLE, 0);
        noeud(b, cx + 0.2f, 14.6f, cz, 40, SAC_POUBELLE);
        // couvercle tombé, appuyé contre la poubelle (bombé vers l'extérieur)
        b.push().translate(11.55f, 4.12f, cz + 0.5f).rotateY(-12).rotateZ(-68);
        b.cylinder(4.2f, -0.25f, 0.25f, 24, GALVA_ROUILLE, GALVA_ROUILLE, null);
        b.push().translate(0, 0.24f, 0).scale(1, 0.3f, 1);
        b.sphereSection(4.0f, 20, 5, 0, 90, GALVA_ROUILLE);
        b.pop();
        b.push().translate(0, 0.3f, 0);
        b.torus(2.6f, 0.1f, 20, 4, 0, 360, GALVA);
        b.pop();
        for (float x : new float[]{-1.3f, 1.3f}) b.bevelBox(x - 0.3f, 1.25f, -0.35f, x + 0.3f, 1.85f, 0.35f, 0.08f, GALVA);
        b.tube(new float[][]{p(-1.3f, 1.8f, 0), p(-0.9f, 2.25f, 0), p(0.9f, 2.25f, 0), p(1.3f, 1.8f, 0)}, fill(4, 0.14f), 6, GALVA, false, false, 0, false);
        b.pop();
        // rouille et coulures sous le bord, sous l'anse ; boue au pied
        decalCyl(b, COULURE_1, cx, cz, 4.05f, 70, 9.9f, 1.1f, 2.2f, 0);
        decalCyl(b, COULURE_3, cx, cz, 4.0f, 118, 10.3f, 0.9f, 1.8f, 0);
        decalCyl(b, ROUILLE.asFit().sub(0.2f, 0.2f, 0.6f, 0.5f), cx, cz, 3.95f, 100, 2.0f, 2.2f, 1.4f, 0);
        decalCyl(b, BOUE_1, cx, cz, 3.92f, 85, 1.3f, 3.6f, 1.6f, 0);
        decalCyl(b, AFFICHE, cx, cz, 3.96f, 58, 5.6f, 2.4f, 2.4f, -6);
        // au pied
        canetteEcrasee(b, 13.9f, 11.6f, CAN_BIERE, -20, 7);
        papierFroisse(b, 2.6f, 0, 11.4f, 0.7f, JOURNAL, 8);
        feuille(b, 7.2f, 0, 12.2f, 2.4f, 3.0f, -15, 0.1f, JOURNAL);
        jonchee(b, 1.5f, 10.5f, 14.5f, 15.0f, 5, FEUILLES, 1.2f, 9);
    }

    // =================================================================== 3. bac roulant

    /**
     * Bac roulant 120 L (48 x 55 x 93 cm) : cuve évasée, rebord, peigne de levage, pieds avant, essieu et roues côté
     * mur, poignée, couvercle articulé ouvert de ang degrés. Repère : centre de la cuve en (cx, cz).
     */
    static void bac(MeshBuilder b, float cx, float cz, Mat corps, Mat couv, float ang) {
        float yb = 0.9f, yt = 13.2f;
        float[][] cuve = {{6.3f, 7.2f, 0.9f, yb}, {7.3f, 8.3f, 1.0f, yt}, {7.9f, 8.9f, 1.1f, yt + 0.06f}, {7.9f, 8.9f, 1.1f, yt + 0.6f}, {7.25f, 8.25f, 0.95f, yt + 0.7f}};
        cuve(b, cx, cz, cuve, 3, corps, false, null);
        cuve(b, cx, cz, new float[][]{{7.25f, 8.25f, 0.95f, yt + 0.68f}, {6.8f, 7.8f, 0.9f, 6.0f}}, 3, corps, true, null);
        capot(b, cx, yb, cz, 6.3f, 7.2f, 0.9f, 3, corps, false);
        float zf = cz + 4.15f;
        // nervures verticales sur les flancs, peigne de levage, pieds avant
        for (int sg = -1; sg <= 1; sg += 2) {
            b.push().translate(cx + sg * 3.35f, yb + 0.6f, cz).rotateZ(sg * -2.3f);
            for (float dz : new float[]{-1.8f, 1.6f}) b.bevelBox(-0.25f, 0, dz - 0.35f, 0.25f, 11.2f, dz + 0.35f, 0.18f, corps);
            b.pop();
            b.bevelBox(cx + sg * 2.4f - 0.6f, 0, cz + 2.3f, cx + sg * 2.4f + 0.6f, yb + 0.4f, cz + 3.3f, 0.2f, corps);
        }
        b.bevelBox(cx - 3.3f, 12.1f, zf - 0.2f, cx + 3.3f, 12.9f, zf + 0.55f, 0.2f, corps);
        // essieu, roues, poignée côté mur
        float zr = cz - 3.4f;
        b.bevelBox(cx - 3.2f, 0.7f, zr - 0.7f, cx + 3.2f, 2.6f, zr + 0.9f, 0.3f, corps);
        b.push().translate(cx, 1.6f, zr).rotateZ(90);
        b.cylinder(0.22f, -4.3f, 4.3f, 8, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        for (int sg = -1; sg <= 1; sg += 2) roue(b, cx + sg * 3.75f, 1.6f, zr, 1.6f, 0.9f, CAOUTCHOUC_USE, PLASTIC_GREY);
        float zh = cz - 4.15f;
        for (int sg = -1; sg <= 1; sg += 2) b.bevelBox(cx + sg * 3.0f - 0.45f, 12.4f, zh - 0.62f, cx + sg * 3.0f + 0.45f, 13.7f, zh + 0.4f, 0.18f, corps);
        b.push().translate(cx, 13.05f, zh - 0.5f).rotateZ(90);
        b.cylinder(0.28f, -3.3f, 3.3f, 10, corps, corps, corps);
        b.pop();
        if (couv != null) couvercleBac(b, cx, cz, couv, ang);
    }

    /** Couvercle du bac roulant, articulé à l'arrière (charnière sous la poignée), ouvert de ang degrés. */
    static void couvercleBac(MeshBuilder b, float cx, float cz, Mat couv, float ang) {
        float yp = 13.2f + 0.75f, zp = cz - 4.15f - 0.1f;
        b.push().translate(0, yp, zp).rotateX(-ang).translate(0, -yp, -zp);
        b.bevelBox(cx - 4.05f, yp, zp, cx + 4.05f, yp + 0.45f, zp + 9.25f, 0.18f, couv);
        b.bevelBox(cx - 3.4f, yp + 0.43f, zp + 0.9f, cx + 3.4f, yp + 0.75f, zp + 8.2f, 0.25f, couv);
        b.bevelBox(cx - 4.05f, yp - 0.75f, zp + 8.85f, cx + 4.05f, yp + 0.1f, zp + 9.25f, 0.12f, couv);
        for (int sg = -1; sg <= 1; sg += 2) b.bevelBox(cx + sg * 4.05f - (sg > 0 ? 0.35f : 0), yp - 0.45f, zp + 0.6f, cx + sg * 4.05f + (sg < 0 ? 0.35f : 0), yp + 0.1f, zp + 8.9f, 0.1f, couv);
        b.bevelBox(cx - 1.2f, yp - 0.55f, zp + 9.2f, cx + 1.2f, yp + 0.3f, zp + 9.6f, 0.15f, couv);
        for (float x : new float[]{cx - 2.6f, cx + 2.6f}) {
            b.push().translate(x, yp, zp + 0.2f).rotateZ(90);
            b.cylinder(0.3f, -0.7f, 0.7f, 8, couv, couv, couv);
            b.pop();
        }
        b.pop();
    }

    /** Bac roulant vert délavé contre le mur : couvercle soulevé par un sac qui déborde, fissuré, brûlé sur un flanc. */
    static void bacRoulant(MeshBuilder b) {
        float cx = 8.0f, cz = 5.3f;
        bac(b, cx, cz, BAC_VERT, BAC_VERT, 9);
        // ordures et sac sous le couvercle entrouvert
        champ(b, cx - 3.4f, cz - 3.9f, cx + 3.4f, cz + 3.9f, 12.4f, 0.8f, 0.7f, 6, 6, 11, ORDURES);
        Matrix4f ts = place(cx + 0.6f, 11.0f, cz + 1.0f, 15, 12, 0);
        mou(b, ts, new float[][]{{3.0f, 0}, {3.4f, 1.2f}, {3.1f, 2.4f}, {2.2f, 3.3f}, {0.9f, 3.8f}, {0.01f, 3.95f}},
                14, 1.05f, 0.95f, 0.1f, 0.45f, 12, SAC_POUBELLE, 0);
        Vector3f nd = point(ts, 0, 3.7f, 0);
        noeud(b, nd.x, nd.y, nd.z, 60, SAC_POUBELLE);
        // déchet coincé dans l'entrebâillement
        feuille(b, cx - 2.2f, 13.95f, cz + 4.5f, 2.0f, 1.6f, 12, 0.12f, JOURNAL);
        // usure : pochoir de la ville, étiquette arrachée, fissure, brûlure, boue
        decalT(b, E_BENNE, cx, 8.7f, avantBac(cz, 8.7f), 5.6f, 1.4f, 0, 2.56f, 0, 0.06f);
        decalT(b, ETIQUETTE_DECHIREE, cx - 1.2f, 4.6f, avantBac(cz, 4.6f), 2.6f, 1.3f, 0, 2.56f, -8, 0.06f);
        decalT(b, GRIFFURES, cx + 1.6f, 6.0f, avantBac(cz, 6.0f), 2.4f, 1.2f, 0, 2.56f, 20, 0.06f);
        decalT(b, BOUE_2, cx, 1.9f, avantBac(cz, 1.9f), 6.0f, 1.8f, 0, 2.56f, 0, 0.06f);
        decalT(b, BRULURE, flancBac(cx, 6.2f), 6.2f, cz - 0.1f, 2.6f, 3.4f, 90, 2.33f, 0, 0.06f);
        decalT(b, AUREOLE, 2 * cx - flancBac(cx, 9.0f), 9.0f, cz - 0.1f, 2.5f, 2.5f, -90, 2.33f, 0, 0.06f);
        float yp = 13.95f, zp = cz - 4.25f;
        b.push().translate(0, yp, zp).rotateX(-9).translate(0, -yp, -zp);
        decalO(b, FISSURE, 'y', cx + 1.4f, yp + 0.75f, cz + 2.0f, 3.2f, 3.2f, 30, 0.06f);
        decalO(b, AUREOLE, 'y', cx - 1.6f, yp + 0.75f, cz - 1.0f, 2.6f, 2.6f, 0, 0.09f);
        b.pop();
        // au pied
        canetteEcrasee(b, 12.6f, 12.4f, CAN_MENTHE, 30, 13);
        papierFroisse(b, 3.4f, 0, 12.0f, 0.7f, PAPIER, 14);
        jonchee(b, 2.0f, 10.8f, 14.0f, 15.2f, 4, PAPIER, 1.0f, 15);
    }

    /** z de la face avant du bac (évasée) à la hauteur y. */
    static float avantBac(float cz, float y) {
        return cz + 3.6f + 0.55f * (y - 0.9f) / 12.3f;
    }

    /** x du flanc droit du bac à la hauteur y. */
    static float flancBac(float cx, float y) {
        return cx + 3.15f + 0.5f * (y - 0.9f) / 12.3f;
    }

    // =================================================================== 4. benne

    /**
     * Benne 770 L (1,26 x 0,77 x 1,33 m) sur deux blocs : tôle peinte rouillée, nervures, tourillons de levage, quatre
     * roulettes pivotantes ; couvercle relevé appuyé contre le mur ; pleine de sacs et d'un carton, coulures, pochoir.
     */
    static void benne(MeshBuilder b) {
        float cx = 16.0f, cz = 6.85f, yb = 3.5f, yt = 19.0f;
        float[][] prof = {{19.4f, 10.7f, 0.5f, yb}, {20.1f, 11.4f, 0.55f, yt}, {20.8f, 12.1f, 0.6f, yt + 0.05f}, {20.8f, 12.1f, 0.6f, yt + 0.6f}, {19.9f, 11.2f, 0.5f, yt + 0.68f}};
        cuve(b, cx, cz, prof, 2, BENNE, false, null);
        cuve(b, cx, cz, new float[][]{{19.9f, 11.2f, 0.5f, yt + 0.66f}, {19.3f, 10.6f, 0.45f, 13.0f}}, 2, INTERIEUR_SALE, true, null);
        capot(b, cx, yb, cz, 19.4f, 10.7f, 0.5f, 2, BENNE, false);
        float pente = (float) Math.toDegrees(Math.atan2(0.35f, yt - yb));
        // nervures embouties en façade et sur les flancs
        for (float x : new float[]{-6.6f, -2.2f, 2.2f, 6.6f}) {
            b.push().translate(cx + x, yb + 0.9f, avantBenne(cz, yb + 0.9f)).rotateX(pente);
            b.bevelBox(-0.55f, 0, -0.2f, 0.55f, 8.6f, 0.32f, 0.2f, BENNE);
            b.pop();
        }
        for (int sg = -1; sg <= 1; sg += 2) {
            b.push().translate(cx + sg * 9.7f, yb + 0.9f, cz).rotateZ(-sg * pente);
            for (float dz : new float[]{-2.6f, 2.6f}) b.bevelBox(-0.25f, 0, dz - 0.5f, 0.3f, 13.6f, dz + 0.5f, 0.2f, BENNE);
            b.pop();
            // tourillon de levage et sa platine
            float x = cx + sg * 10.05f;
            b.bevelBox(Math.min(x, x + sg * 0.3f), 14.3f, cz - 1.4f, Math.max(x, x + sg * 0.3f), 17.3f, cz + 1.4f, 0.12f, BENNE);
            b.push().translate(x + sg * 0.3f, 15.8f, cz).rotateZ(-90);
            b.cylinder(0.75f, sg > 0 ? -0.1f : -1.1f, sg > 0 ? 1.1f : 0.1f, 10, METAL_NU, METAL_NU, METAL_NU);
            b.pop();
        }
        // châssis et roulettes pivotantes
        for (float z : new float[]{cz - 3.6f, cz + 3.6f}) b.bevelBox(cx - 9.2f, yb - 0.43f, z - 0.6f, cx + 9.2f, yb + 0.05f, z + 0.6f, 0.12f, ROUILLE);
        float[][] rl = {{cx - 8.0f, cz - 3.6f, 30}, {cx + 8.0f, cz - 3.6f, -50}, {cx - 8.0f, cz + 3.6f, 80}, {cx + 8.0f, cz + 3.6f, 10}};
        for (float[] q : rl) roulette(b, q[0], q[1], 1.3f, 1.0f, q[2], ROUILLE, CAOUTCHOUC_USE);
        // bonde de vidange
        b.push().translate(cx - 5.0f, yb + 0.8f, cz + 5.42f).rotateX(90);
        b.cylinder(0.55f, 0, 0.35f, 8, ROUILLE, null, ROUILLE);
        b.pop();
        // couvercle relevé jusqu'au mur (charnières à l'arrière)
        float yp = yt + 0.7f, zp = cz - 6.1f;
        for (float x : new float[]{cx - 7.0f, cx + 7.0f}) b.bevelBox(x - 0.6f, yt - 0.3f, zp - 0.15f, x + 0.6f, yp - 0.06f, zp + 0.6f, 0.12f, ROUILLE);
        b.push().translate(0, yp, zp).rotateX(-91).translate(0, -yp, -zp);
        b.bevelBox(cx - 10.5f, yp, zp, cx + 10.5f, yp + 0.5f, zp + 12.0f, 0.15f, BENNE);
        b.bevelBox(cx - 10.5f, yp - 0.8f, zp + 11.6f, cx + 10.5f, yp + 0.05f, zp + 12.0f, 0.12f, BENNE);
        for (float x : new float[]{-7.0f, 0, 7.0f}) b.bevelBox(cx + x - 0.5f, yp - 0.32f, zp + 0.8f, cx + x + 0.5f, yp + 0.02f, zp + 11.2f, 0.15f, BENNE);
        decalT(b, COULURE_2, cx - 3.6f, yp, zp + 9.0f, 2.0f, 4.0f, 0, 90, 0, 0.06f);
        decalT(b, CROIX, cx + 3.6f, yp, zp + 6.0f, 3.0f, 3.0f, 0, 90, 10, 0.06f);
        b.pop();
        // contenu : ordures tassées, sacs, carton qui dépasse, planche
        champ(b, cx - 9.4f, cz - 5.0f, cx + 9.4f, cz + 5.0f, 16.6f, 0.9f, 0.5f, 10, 6, 21, ORDURES);
        mou(b, place(cx - 5.0f, 16.0f, cz + 0.5f, 10, 0, 0), profilSac(3.5f, 7.0f), 14, 1.05f, 0.95f, 0.08f, 0.4f, 22, SAC_POUBELLE, 16.4f);
        mou(b, place(cx + 1.5f, 16.0f, cz - 1.0f, 50, -10, 6), profilSac(3.3f, 7.4f), 14, 1.0f, 0.9f, 0.08f, 0.4f, 23, SAC_POUBELLE, 16.4f);
        Vector3f s = point(place(cx + 1.5f, 16.0f, cz - 1.0f, 50, -10, 6), 0, 7.4f * 0.95f, 0);
        noeud(b, s.x, s.y, s.z, 10, SAC_POUBELLE);
        b.color(0.42f, 0.46f, 0.5f);
        mou(b, place(cx + 6.4f, 16.0f, cz + 1.8f, -20, 0, -10), profilSac(3.0f, 5.6f), 14, 1.0f, 0.95f, 0.1f, 0.45f, 24, SAC_BLANC, 16.4f);
        b.color(1);
        b.push().translate(cx + 2.6f, 15.8f, cz + 3.4f).rotateY(-8).rotateX(-18);
        b.bevelBox(-3.2f, 0, -0.15f, 3.2f, 6.4f, 0.15f, 0.05f, CARTON_MOUILLE, new Mat[]{null, null, null, null, null, CT_BISCOTTES}, 0);
        b.pop();
        b.push().translate(cx - 1.2f, 17.0f, cz + 2.2f).rotateY(70).rotateZ(-28);
        b.bevelBox(-0.3f, 0, -0.9f, 0.3f, 9.0f, 0.9f, 0.08f, BOIS_GRIS);
        b.pop();
        // usure : pochoir, coulures sous le rebord et les tourillons, boue, affiche, autocollant
        decalT(b, E_BENNE, cx, 15.4f, avantBenne(cz, 15.4f), 9.6f, 2.4f, 0, pente, 0, 0.06f);
        float[][] cl = {{cx - 4.4f, 0}, {cx + 3.6f, 1}, {cx + 8.6f, 2}, {cx - 8.8f, 0}};
        for (float[] c : cl) {
            decalT(b, c[1] == 0 ? COULURE_1 : (c[1] == 1 ? COULURE_2 : COULURE_3), c[0], yt - 2.1f, avantBenne(cz, yt - 2.1f), 2.0f, 4.0f, 0, pente, 0, 0.11f);
        }
        decalT(b, BOUE_1, cx - 4.4f, yb + 1.4f, avantBenne(cz, yb + 1.4f), 8.8f, 2.6f, 0, pente, 0, 0.42f);
        decalT(b, BOUE_2, cx + 4.4f, yb + 1.4f, avantBenne(cz, yb + 1.4f), 8.8f, 2.6f, 0, pente, 0, 0.47f);
        decalT(b, AFFICHE, cx + 4.4f, 9.4f, avantBenne(cz, 9.4f), 3.0f, 3.0f, 0, pente, 4, 0.06f);
        decalT(b, ETIQUETTE_DECHIREE, cx - 4.4f, 10.0f, avantBenne(cz, 10.0f), 2.8f, 1.4f, 0, pente, -5, 0.06f);
        decalT(b, COULURE_3, cx + 10.15f, 15.0f, cz + 1.8f, 1.4f, 2.8f, 90, 0, 0, 0.06f);
        decalT(b, COULURE_1, cx - 10.15f, 15.0f, cz - 1.8f, 1.4f, 2.8f, -90, 0, 0, 0.06f);
        // au pied
        mou(b, place(28.6f, 0, 10.6f, 0, 0, 0), profilSac(2.8f, 8.4f), 14, 1.0f, 0.9f, 0.08f, 0.4f, 25, SAC_POUBELLE, 0);
        noeud(b, 28.6f, 8.0f, 10.6f, 30, SAC_POUBELLE);
        papierFroisse(b, 4.0f, 0, 13.0f, 0.8f, PAPIER, 26);
        canetteEcrasee(b, 21.0f, 14.2f, CAN_COLA, -40, 27);
        feuille(b, 11.0f, 0, 14.0f, 2.6f, 3.2f, 20, 0.1f, JOURNAL);
        jonchee(b, 3.0f, 13.0f, 29.0f, 15.6f, 7, PAPIER, 1.1f, 28);
    }

    /** z de la face avant de la benne (évasée) à la hauteur y. */
    static float avantBenne(float cz, float y) {
        return cz + 5.35f + 0.35f * (y - 3.5f) / 15.5f;
    }

    // =================================================================== 5. cartons abandonnés

    /** Carton aplati (deux panneaux, rabats pliés), appuyé contre le mur ; face imprimée optionnelle. */
    static void cartonAplati(MeshBuilder b, float x, float zBas, float ry, float inclin, float l1, float l2, float h, Mat face, Mat m) {
        b.push().translate(x, 0, zBas).rotateY(ry).rotateX(-inclin);
        float hr = h * 0.24f, ep = 0.28f;
        // panneau 1 (imprimé), panneau 2 un peu plié en V
        for (int k = 0; k < 2; k++) {
            float l = k == 0 ? l1 : l2;
            b.push();
            if (k == 1) b.translate(l1 + 0.04f, 0, 0).rotateY(-9);
            b.bevelBox(0, 0, 0, l, hr - 0.03f, ep, 0.06f, m);
            b.push().translate(0, hr, 0).rotateX(-5);
            b.bevelBox(0, 0, 0, l, h - 2 * hr - 0.03f, ep, 0.06f, m, new Mat[]{null, null, null, null, null, k == 0 ? face : null}, 0);
            b.push().translate(0, h - 2 * hr, 0).rotateX(9);
            b.bevelBox(0, 0, 0, l, hr, ep, 0.06f, m);
            b.pop();
            b.pop();
            b.pop();
        }
        b.pop();
    }

    /** Carton de 40 x 30 x 30 cm ouvert, affaissé, rabats ouverts (un arraché), rempli de vieux journaux. */
    static void cartonOuvert(MeshBuilder b, float x, float z, float ry) {
        cartonOuvert(b, x, 0, z, ry, 0);
    }

    /** contenu : 0 = vieux journaux, 1 = bouteilles d'eau (palette). */
    static void cartonOuvert(MeshBuilder b, float x, float y0, float z, float ry, int contenu) {
        b.push().translate(x, y0, z).rotateY(ry);
        float w = 6.4f, d = 4.8f, h = 4.6f;
        float[][] prof = {{w, d, 0.12f, 0}, {w + 0.3f, d + 0.25f, 0.14f, h}, {w + 0.3f - 0.5f, d + 0.25f - 0.5f, 0.14f, h}};
        cuve(b, 0, 0, prof, 1, CARTON, false, null);
        cuve(b, 0, 0, new float[][]{{w - 0.2f, d - 0.25f, 0.12f, h}, {w - 0.5f, d - 0.5f, 0.12f, 0.2f}}, 1, CARTON_MOUILLE, true, null);
        decalT(b, CT_LESSIVE, 0, h / 2, d / 2 + 0.07f, w - 0.4f, h - 0.4f, 0, 1.56f, 0, 0.1f);
        decalT(b, ETIQUETTE_DECHIREE, (w + 0.15f) / 2 + 0.02f, 2.6f, 0.4f, 2.4f, 1.2f, 90, 1.9f, 0, 0.05f);
        if (contenu == 0) {
            // journaux dedans (pile), journal plié qui dépasse
            b.bevelBox(-2.7f, 0.2f, -1.9f, 2.6f, 4.2f, 1.8f, 0.1f, TRANCHE, new Mat[]{null, null, null, UNE, null, null}, 0);
            b.push().translate(-0.6f, 4.0f, 0.8f).rotateY(15).rotateX(-38);
            b.bevelBox(-1.6f, 0, -0.12f, 1.6f, 3.6f, 0.12f, 0.04f, JOURNAL);
            b.pop();
            papierFroisse(b, 1.4f, 4.15f, -0.6f, 0.75f, JOURNAL, 31);
        } else {
            b.face(CARTON_MOUILLE, MeshBuilder.v(0, 1, 0), p(-2.9f, 0.22f, 2.1f), p(2.9f, 0.22f, 2.1f), p(2.9f, 0.22f, -2.1f), p(-2.9f, 0.22f, -2.1f));
            for (float[] q : new float[][]{{-2.0f, -1.1f}, {0, -1.1f}, {2.0f, 1.1f}, {-2.0f, 1.1f}}) {
                b.push().translate(q[0], 0.25f, q[1]);
                b.lathe(new float[][]{{0.72f, 0}, {0.72f, 3.3f}, {0.5f, 4.0f}, {0.24f, 4.5f}}, 8, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
                b.cylinder(0.26f, 4.5f, 4.85f, 8, PLASTIC_BLUE, null, PLASTIC_BLUE);
                b.pop();
            }
        }
        // rabats : avant rabattu dehors, gauche ouvert, arrière dressé, droit arraché
        float yh = h - 0.05f, ep = 0.14f;
        b.push().translate(0, yh, d / 2 + 0.12f).rotateX(118);
        b.bevelBox(-w / 2 - 0.1f, 0, -ep, w / 2 + 0.1f, d / 2, 0, 0.04f, CARTON);
        b.pop();
        b.push().translate(0, yh, -d / 2 - 0.12f).rotateX(-22);
        b.bevelBox(-w / 2 - 0.1f, 0, 0, w / 2 + 0.1f, d / 2 - 0.1f, ep, 0.04f, CARTON);
        b.pop();
        b.push().translate(-w / 2 - 0.15f, yh, 0).rotateZ(76);
        b.bevelBox(-ep, 0, -d / 2, 0, w / 2 - 0.2f, d / 2, 0.04f, CARTON_MOUILLE);
        b.pop();
        b.push().translate(w / 2 + 0.15f, yh, 0).rotateZ(-14);
        b.bevelBox(0, 0, -d / 2 + 0.3f, ep, 0.9f, d / 2 - 1.4f, 0.04f, CARTON);
        b.bevelBox(0, 0, d / 2 - 1.35f, ep, 0.45f, d / 2 - 0.2f, 0.04f, CARTON);
        b.pop();
        b.pop();
    }

    /**
     * Cartons abandonnés contre le mur : deux cartons aplatis appuyés (l'un imprimé, l'autre détrempé) et un carton ouvert
     * affaissé plein de vieux journaux ; journaux épars et flaque au sol.
     */
    static void cartonsEcrases(MeshBuilder b) {
        cartonAplati(b, 0.8f, 2.64f, 0, 14, 6.4f, 4.8f, 9.6f, CT_EAU, CARTON);
        cartonAplati(b, 5.6f, 3.0f, -7, 15, 5.2f, 4.0f, 7.8f, CT_FRAGILE, CARTON_MOUILLE);
        cartonOuvert(b, 11.6f, 10.3f, -14);
        feuille(b, 4.0f, 0, 12.4f, 3.0f, 3.8f, 35, 0.15f, UNE);
        feuille(b, 6.4f, 0, 14.0f, 2.8f, 3.4f, -20, 0.1f, JOURNAL);
        papierFroisse(b, 2.2f, 0, 9.6f, 0.7f, JOURNAL, 32);
        decalO(b, D_FLAQUE, 'y', 4.6f, 0, 7.0f, 6.0f, 4.0f, -10, 0.07f);
        jonchee(b, 8.0f, 13.0f, 15.0f, 15.6f, 4, FEUILLES, 1.2f, 33);
    }
}
