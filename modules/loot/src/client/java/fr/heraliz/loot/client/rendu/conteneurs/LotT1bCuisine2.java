package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bCuisine.amande;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bCuisine.faces;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** T1b — cuisine (suite) : meuble bas, poubelle à pédale, mini-frigo, tiroir renversé, chariot de ménage. */
final class LotT1bCuisine2 {
    private LotT1bCuisine2() {
    }

    // =================================================================== meuble bas de cuisine
    /**
     * Élément bas de cuisine (80 x 58 x 85 cm) façon chêne rustique : porte de droite arrachée, posée contre l'autre ;
     * produits ménagers sur l'étagère, flacon renversé au sol ; chant alu du plan de travail arraché, socle moisi.
     */
    static void elementCuisine(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.4f, z1 = 9.6f, yc0 = 1.0f, yc1 = 13.0f, s = 0.36f, xm = 8.0f;
        b.color(0.55f, 0.5f, 0.44f);
        b.box(x0 + 0.2f, 0, z0 + 0.4f, x1 - 0.2f, yc0, z1 - 0.9f, AGGLO);
        b.color(1);
        decal(b, MOISI, 'z', 5.0f, 0.5f, z1 - 0.9f, 5.0f, 0.9f);
        decal(b, MOUSSE_VERTE, 'z', 11.6f, 0.45f, z1 - 0.9f, 3.0f, 0.85f);
        // caisson blanc, étagère ; côtés extérieurs plaqués chêne
        b.color(0.93f, 0.9f, 0.82f);
        b.box(x0 + s, yc0, z0, x1 - s, yc0 + s, z1, MELAMINE);
        b.box(x0 + s, yc0 + s, z0, x1 - s, yc1, z0 + 0.2f, MELAMINE);
        b.box(x0 + s, 7.0f, z0 + 0.2f, x1 - s, 7.3f, z1 - 0.4f, MELAMINE);
        b.color(1);
        b.bevelBox(x0, yc0, z0, x0 + s, yc1, z1, 0.06f, BOIS_VERNI, faces(null, MELAMINE, null, null, null, null), 0);
        b.bevelBox(x1 - s, yc0, z0, x1, yc1, z1, 0.06f, BOIS_VERNI, faces(MELAMINE, null, null, null, null, null), 0);
        decal(b, MOISI, 'z', 11.0f, 2.6f, z0 + 0.2f, 4.0f, 2.0f, 10);
        // charnières restées sur le côté droit
        for (float y : new float[]{2.6f, 11.4f}) {
            b.box(x1 - s - 0.3f, y - 0.5f, z1 - 2.0f, x1 - s + 0.02f, y + 0.5f, z1 - 0.6f, METAL_NU);
            b.push().translate(x1 - s - 0.3f, y, z1 - 0.3f).rotateZ(90);
            b.cylinder(0.25f, 0, 0.5f, 8, METAL_NU, null, METAL_NU);
            b.pop();
        }
        // plan de travail stratifié, chant alu arraché (aggloméré à nu)
        b.bevelBox(x0 - 0.2f, yc1, z0, x1 + 0.2f, yc1 + 0.6f, z1 + 0.6f, 0.08f, FORMICA);
        b.box(x0 - 0.2f, yc1 + 0.02f, z1 + 0.6f, 9.0f, yc1 + 0.58f, z1 + 0.68f, CHROME_PIQUE);
        b.box(11.2f, yc1 + 0.02f, z1 + 0.6f, x1 + 0.2f, yc1 + 0.58f, z1 + 0.68f, CHROME_PIQUE);
        b.box(9.0f, yc1 + 0.08f, z1 + 0.6f, 11.2f, yc1 + 0.52f, z1 + 0.64f, AGGLO);
        b.tube(new float[][]{p(9.0f, yc1 + 0.3f, z1 + 0.66f), p(8.6f, yc1 - 0.6f, z1 + 1.4f), p(8.9f, yc1 - 1.7f, z1 + 1.8f)}, fill(3, 0.06f), 4, CHROME_PIQUE, false, true, 0, false);
        decal(b, AUREOLE, 'y', 11.6f, yc1 + 0.6f, 6.4f, 2.4f, 2.2f);
        coulure(b, 'X', x1, yc1 - 0.1f, 4.0f, 1.4f, 0);
        coulure(b, 'x', x0, yc1 - 0.1f, 6.6f, 1.2f, 2);
        // bouilloire inox et tasse sur le plan
        b.push().translate(4.0f, yc1 + 0.6f, 3.2f);
        b.lathe(new float[][]{{0.01f, 0}, {1.3f, 0.02f}, {1.4f, 0.25f}, {1.3f, 1.6f}, {0.9f, 2.1f}, {0.45f, 2.25f}, {0.01f, 2.3f}}, 16, 0, 360, INOX, 1, 1, 0, 0, 0, false);
        b.tube(new float[][]{p(-0.8f, 2.1f, 0), p(-0.6f, 2.75f, 0), p(0.6f, 2.75f, 0), p(0.8f, 2.1f, 0)}, fill(4, 0.16f), 6, AtlasCivil.DARK, false, false, 0, false);
        b.tube(new float[][]{p(1.2f, 1.0f, 0), p(1.8f, 1.7f, 0), p(2.1f, 2.1f, 0)}, new float[]{0.3f, 0.2f, 0.15f}, 8, INOX, false, true, 0, false);
        b.pop();
        b.push().translate(11.0f, yc1 + 0.6f, 5.2f);
        b.color(0.5f, 0.62f, 0.7f);
        b.lathe(new float[][]{{0.01f, 0}, {0.6f, 0.02f}, {0.65f, 1.4f}}, 12, 0, 360, CERAMIQUE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.62f, 1.4f}, {0.55f, 0.15f}}, 12, 0, 360, CERAMIQUE, 1, 1, 0, 0, 0, true);
        b.tube(new float[][]{p(0.6f, 1.1f, 0), p(1.1f, 0.9f, 0), p(1.0f, 0.4f, 0), p(0.6f, 0.3f, 0)}, fill(4, 0.1f), 5, CERAMIQUE, false, false, 0, false);
        b.color(1);
        b.disc(0.56f, 0.6f, 12, MOISI, true);
        b.pop();
        // porte de gauche fermée (panneau mouluré, bouton laiton)
        b.bevelBox(x0 + 0.05f, yc0 + 0.2f, z1, xm - 0.05f, yc1 - 0.2f, z1 + 0.45f, 0.12f, BOIS_VERNI);
        b.bevelBox(x0 + 0.9f, yc0 + 1.0f, z1 + 0.4f, xm - 0.9f, yc1 - 1.0f, z1 + 0.62f, 0.22f, BOIS_VERNI);
        bouton(b, xm - 0.8f, yc1 - 1.1f, z1 + 0.45f, 0.3f, AtlasCivil.KNOB);
        // produits : javel et lessive en bas, conserves et éponge sur l'étagère
        flacon(b, 9.4f, yc0 + s, 6.2f, 0.75f, 4.0f, 0xE6E4DE, L_JAVEL, 0x2A5AA0);
        b.push().translate(12.2f, yc0 + s, 5.4f).rotateY(-12);
        b.bevelBox(-1.2f, 0, -0.75f, 1.2f, 3.0f, 0.75f, 0.08f, AtlasCivil.WHITE, faces(null, null, null, null, null, L_LESSIVE), 0);
        b.pop();
        b.push().translate(9.6f, 7.3f, 5.6f);
        b.cylinder(0.7f, 0, 1.8f, 12, L_CONSERVE, METAL_NU, METAL_NU);
        b.translate(1.6f, 0, -0.6f);
        b.cylinder(0.7f, 0, 1.8f, 12, L_CONSERVE, METAL_NU, METAL_NU);
        b.pop();
        b.color(0.85f, 0.78f, 0.3f);
        b.bevelBox(12.4f, 7.3f, 6.4f, 13.6f, 7.75f, 7.3f, 0.12f, PLAST);
        b.color(0.4f, 0.6f, 0.4f);
        b.bevelBox(12.4f, 7.73f, 6.4f, 13.6f, 7.95f, 7.3f, 0.06f, TOILE);
        b.color(1);
        // flacon de liquide vaisselle tombé au sol
        b.push().translate(12.2f, 0.78f, 12.4f).rotateY(-30).rotateZ(90).translate(0, -1.8f, 0);
        flacon(b, 0, 0, 0, 0.7f, 3.6f, 0x4E8A3E, L_VAISSELLE, 0xD8C83A);
        b.pop();
        // porte de droite arrachée, posée contre l'autre (dos vers le meuble)
        b.push().translate(2.3f, 0, 12.96f).rotateY(4).rotateX(-14);
        b.bevelBox(0, 0, 0, 6.3f, 11.4f, 0.45f, 0.12f, BOIS_VERNI);
        b.bevelBox(0.85f, 0.8f, 0.4f, 5.45f, 10.6f, 0.62f, 0.22f, BOIS_VERNI);
        bouton(b, 0.8f, 10.3f, 0.45f, 0.3f, AtlasCivil.KNOB);
        decal(b, GRIFFURES, 'z', 3.2f, 5.0f, 0.62f, 3.0f, 1.5f, -20);
        b.pop();
        boue(b, 'x', x0, yc0, 5.0f, 8.0f, 1.6f, 0);
        boue(b, 'X', x1, yc0, 5.0f, 8.0f, 1.4f, 1);
    }

    // =================================================================== poubelle à pédale
    /**
     * Poubelle à pédale en inox terni (30 cm, 45 cm), cabossée ; couvercle ouvert, sac noir qui déborde, second sac et
     * déchets au pied (canette écrasée, pot de yaourt, papiers, brique de lait).
     */
    static void poubelleCuisine(MeshBuilder b) {
        float cx = 6.0f, cz = 3.5f, h = 7.2f, zc = cz - 2.3f;
        b.push().translate(cx, 0, cz);
        b.color(0.32f);
        b.lathe(new float[][]{{0.01f, 0}, {2.15f, 0}, {2.3f, 0.2f}, {2.3f, 1.0f}, {2.18f, 1.12f}}, 20, 0, 360, PLAST, 1, 1, 0, 0, 0, false);
        b.color(1);
        b.lathe(new float[][]{{2.12f, 1.05f}, {2.18f, 3.0f}, {2.26f, 6.8f}, {2.3f, 7.05f}}, 20, 0, 360, INOX, 1, 1, 0.035f, 0.7f, 4, false);
        b.push().translate(0, 7.05f, 0);
        b.torus(2.3f, 0.1f, 20, 4, 0, 360, INOX);
        b.pop();
        // sac poubelle rabattu sur le bord, puis qui déborde ; nœud
        b.lathe(new float[][]{{1.9f, 6.2f}, {2.36f, 7.15f}, {2.5f, 7.05f}, {2.52f, 6.3f}}, 20, 0, 360, SAC_POUBELLE, 1, 1, 0.05f, 1.2f, 2, false);
        b.push().translate(0.2f, 7.9f, 0.5f);
        b.blob(2.2f, 1.35f, 1.8f, 0.9f, 0.16f, 1.4f, 6, 14, SAC_POUBELLE);
        b.pop();
        b.tube(new float[][]{p(0.3f, 9.1f, 0.6f), p(0.5f, 9.6f, 0.9f), p(0.1f, 9.9f, 1.3f)}, new float[]{0.32f, 0.22f, 0.14f}, 6, SAC_POUBELLE, false, true, 0, false);
        // pédale
        b.color(0.3f);
        b.bevelBox(-0.7f, 0.12f, 2.1f, 0.7f, 0.5f, 3.2f, 0.1f, PLAST);
        b.color(1);
        b.pop();
        // couvercle ouvert à la verticale (charnière arrière)
        b.box(cx - 0.5f, h - 0.3f, zc - 0.25f, cx + 0.5f, h + 0.2f, zc + 0.1f, METAL_NU);
        b.push().translate(cx, h + 0.1f, zc).rotateX(-80).translate(0, 0, 2.3f);
        b.lathe(new float[][]{{2.38f, 0}, {2.32f, 0.22f}, {1.6f, 0.5f}, {0.01f, 0.62f}}, 20, 0, 360, INOX, 1, 1, 0, 0, 0, false);
        b.disc(2.36f, 0.01f, 20, AtlasCivil.DARK, false);
        b.pop();
        // second sac au pied, déchets
        tasSol(b, 11.6f, 4.4f, 2.5f, 1.8f, 2.2f, 0.1f, 9, SAC_POUBELLE);
        b.tube(new float[][]{p(11.4f, 3.6f, 4.2f), p(11.2f, 4.3f, 4.4f), p(11.6f, 4.7f, 4.9f)}, new float[]{0.35f, 0.24f, 0.14f}, 6, SAC_POUBELLE, false, true, 0, false);
        canetteCouchee(b, 9.0f, 9.0f, 30, L_BIERE, true);
        b.push().translate(4.0f, 0, 8.8f);
        b.lathe(new float[][]{{0.01f, 0}, {0.45f, 0}, {0.55f, 1.0f}, {0.6f, 1.02f}}, 10, 0, 360, AtlasCivil.WHITE, 1, 1, 0, 0, 0, false);
        b.pop();
        b.push().translate(13.6f, 0.75f, 9.0f).rotateY(-25);
        b.bevelBox(-1.6f, -0.75f, -0.75f, 1.6f, 0.75f, 0.75f, 0.06f, AtlasCivil.MILK);
        b.pop();
        papiers(b, 2.0f, 6.8f, 14.0f, 11.0f, 7, 31);
        debris(b, 8.0f, 8.0f, 2.0f, 6, PLAST, 33);
    }

    // =================================================================== mini-frigo
    /**
     * Réfrigérateur de chambre (50 x 45 x 50 cm) : porte grande ouverte (autocollants d'enfant), canettes, barquette
     * moisie, freezer givré au clapet arraché ; revues et tasse posées dessus.
     */
    static void miniFrigo(MeshBuilder b) {
        float x0 = 4.0f, x1 = 12.0f, z0 = 0.5f, z1 = 7.4f, y0 = 0.32f, h = 8.0f, t = 0.5f;
        for (float[] q : new float[][]{{4.6f, 1.1f}, {11.4f, 1.1f}, {4.6f, 6.8f}, {11.4f, 6.8f}}) {
            b.push().translate(q[0], 0, q[1]);
            b.cylinder(0.25f, 0, y0 + 0.02f, 8, CAOUTCHOUC_USE, null, null);
            b.pop();
        }
        amande(b);
        b.bevelBox(x0, y0, z0, x0 + t, h, z1, 0.15f, ELECTRO, faces(null, PLAST, null, null, null, null), 0);
        b.bevelBox(x1 - t, y0, z0, x1, h, z1, 0.15f, ELECTRO, faces(PLAST, null, null, null, null, null), 0);
        b.bevelBox(x0 + t - 0.02f, h - t, z0, x1 - t + 0.02f, h, z1, 0.15f, ELECTRO, faces(null, null, PLAST, null, null, null), 0);
        b.bevelBox(x0 + t - 0.02f, y0, z0, x1 - t + 0.02f, y0 + 0.9f, z1, 0.15f, ELECTRO, faces(null, null, null, PLAST, null, null), 0);
        b.color(0.9f, 0.9f, 0.86f);
        b.box(x0 + t, y0 + 0.9f, z0, x1 - t, h - t, z0 + 0.5f, PLAST);
        b.color(1);
        decal(b, MOISI, 'z', 6.4f, 2.4f, z0 + 0.5f, 3.0f, 2.0f, 15);
        // freezer givré, clapet pendant
        b.color(0.86f, 0.9f, 0.92f);
        b.box(x0 + t, h - t - 1.8f, z0 + 0.5f, x1 - t, h - t - 1.6f, z1 - 0.5f, PLAST);
        b.push().translate(x0 + t + 0.2f, h - t - 1.75f, z1 - 0.45f).rotateX(25);
        b.color(0.8f, 0.8f, 0.78f);
        b.box(0, -1.8f, 0, x1 - x0 - 2 * t - 0.4f, 0, 0.12f, PLAST);
        b.pop();
        b.color(1);
        // clayette, canettes, barquette moisie
        b.box(x0 + t, 3.9f, z0 + 0.6f, x1 - t, 4.0f, z1 - 0.6f, METAL_NU);
        canette(b, 5.6f, y0 + 0.9f, 4.4f, L_COLA);
        canette(b, 6.9f, y0 + 0.9f, 5.2f, L_BIERE);
        b.push().translate(9.6f, y0 + 0.9f + 0.53f, 4.0f).rotateY(70).rotateZ(90).translate(0, -0.96f, 0);
        canette(b, 0, 0, 0, L_COLA);
        b.pop();
        b.color(0.9f, 0.88f, 0.8f);
        b.bevelBox(6.0f, 4.0f, 2.4f, 9.6f, 5.0f, 5.4f, 0.1f, PLAST, faces(null, null, null, MOISI, null, null), 0);
        b.color(1);
        // porte (charnière droite) ouverte à 110° : balconnets, bouteille, joint moisi, autocollants
        b.push().translate(x1, 0, z1).rotateY(110).translate(-x1, 0, -z1);
        amande(b);
        b.bevelBox(x0 + 0.05f, y0 + 0.08f, z1 + 0.02f, x1 - 0.05f, h - 0.08f, z1 + 0.9f, 0.2f, ELECTRO);
        b.color(1);
        decal(b, AUTOCOLLANTS, 'z', 7.6f, 5.6f, z1 + 0.9f, 4.0f, 2.0f, -6);
        decal(b, ETIQUETTE_DECHIREE, 'z', 6.4f, 2.4f, z1 + 0.9f, 2.6f, 1.2f, 4);
        b.color(0.9f, 0.9f, 0.86f);
        b.box(x0 + 0.5f, y0 + 0.5f, z1 - 0.06f, x1 - 0.5f, h - 0.5f, z1 + 0.02f, PLAST);
        for (float y : new float[]{1.6f, 4.8f}) b.box(x0 + 0.7f, y, z1 - 1.3f, x1 - 0.7f, y + 0.9f, z1 - 0.06f, PLAST);
        b.color(1);
        b.box(x0 + 0.3f, y0 + 0.3f, z1 - 0.1f, x1 - 0.3f, y0 + 0.5f, z1 + 0.02f, CAOUTCHOUC_USE);
        b.box(x0 + 0.3f, h - 0.5f, z1 - 0.1f, x1 - 0.3f, h - 0.3f, z1 + 0.02f, MOISI);
        b.push().translate(6.2f, 4.85f, z1 - 0.7f);
        b.lathe(new float[][]{{0.01f, 0}, {0.45f, 0.02f}, {0.45f, 1.6f}, {0.2f, 2.1f}, {0.2f, 2.5f}}, 10, 0, 360, AtlasCivil.BOTTLE_GREEN, 1, 1, 0, 0, 0, false);
        b.pop();
        b.pop();
        // dessus : revues, tasse
        b.push().translate(6.6f, h, 3.6f).rotateY(9);
        b.box(-2.2f, 0.01f, -1.6f, 2.2f, 0.22f, 1.6f, JOURNAL);
        b.box(-2.0f, 0.22f, -1.5f, 2.1f, 0.4f, 1.5f, JOURNAL);
        b.pop();
        b.push().translate(10.4f, h, 5.0f);
        b.color(0.75f, 0.42f, 0.3f);
        b.lathe(new float[][]{{0.01f, 0}, {0.55f, 0.02f}, {0.6f, 1.3f}}, 12, 0, 360, CERAMIQUE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.57f, 1.3f}, {0.5f, 0.15f}}, 12, 0, 360, CERAMIQUE, 1, 1, 0, 0, 0, true);
        b.color(1);
        b.pop();
        coulure(b, 'x', x0, h - 0.3f, 3.4f, 1.2f, 0);
        coulure(b, 'X', x1, 3.4f, 5.2f, 1.0f, 1);
        boue(b, 'x', x0, y0, 4.0f, 6.0f, 1.2f, 1);
        decal(b, AUREOLE, 'y', 9.4f, h, 2.0f, 2.0f, 1.8f);
    }

    // =================================================================== tiroir renversé
    /** Tiroir de cuisine arraché posé au sol, contenu éparpillé : couverts, piles, papiers, bougies, ficelle, clés. */
    static void tiroirTombe(MeshBuilder b) {
        b.push().translate(7.4f, 0, 5.2f).rotateY(-16);
        b.bevelBox(-3.9f, 0, 3.2f, 3.9f, 2.4f, 3.6f, 0.1f, BOIS_VERNI);
        bouton(b, 0, 1.3f, 3.6f, 0.3f, AtlasCivil.KNOB);
        decal(b, GRIFFURES, 'z', -1.6f, 1.2f, 3.6f, 2.4f, 1.0f, 12);
        b.color(0.92f, 0.9f, 0.82f);
        b.box(-3.6f, 0, -3.2f, -3.4f, 1.9f, 3.2f, MELAMINE);
        b.box(3.4f, 0, -3.2f, 3.6f, 1.9f, 3.2f, MELAMINE);
        b.box(-3.4f, 0, -3.2f, 3.4f, 1.9f, -3.0f, MELAMINE);
        b.color(1);
        b.box(-3.4f, 0, -3.0f, 3.4f, 0.15f, 3.2f, AGGLO);
        // range-couverts en plastique, quelques couverts restés dedans
        b.color(0.85f, 0.85f, 0.82f);
        b.box(-3.3f, 0.15f, -2.9f, 0.6f, 0.25f, 3.1f, PLAST);
        for (float x : new float[]{-3.3f, -2.0f, -0.7f, 0.5f}) b.box(x, 0.25f, -2.9f, x + 0.1f, 1.1f, 3.1f, PLAST);
        b.color(1);
        LotT1bKit.fourchette(b, -2.65f, 0.28f, 0.6f, 90);
        LotT1bKit.cuillere(b, -1.35f, 0.28f, -0.4f, 95);
        b.push().translate(2.0f, 0.48f, 0.8f).rotateY(30).rotateZ(90).translate(0, -1.3f, 0);
        b.cylinder(0.32f, 0, 2.6f, 8, AtlasCivil.WHITE, AtlasCivil.WHITE, AtlasCivil.WHITE);
        b.pop();
        b.pop();
        // couverts, piles, bougie, clés, pelote, ruban adhésif, papiers et photo au sol
        LotT1bKit.fourchette(b, 3.0f, 0, 11.2f, 20);
        LotT1bKit.fourchette(b, 12.6f, 0, 10.0f, -60);
        LotT1bKit.cuillere(b, 6.4f, 0, 11.6f, 160);
        LotT1bKit.cuillere(b, 1.6f, 0, 8.6f, 70);
        LotT1bKit.couteau(b, 10.0f, 0, 12.4f, 10);
        LotT1bKit.couteau(b, 14.0f, 0, 6.2f, 100);
        for (float[] q : new float[][]{{8.6f, 10.8f, 30}, {9.4f, 11.6f, 70}, {12.0f, 12.8f, -20}}) {
            b.push().translate(q[0], 0.45f, q[1]).rotateY(q[2]).rotateZ(90).translate(0, -1.1f, 0);
            b.cylinder(0.45f, 0, 2.2f, 10, L_PILE, METAL_NU, METAL_NU);
            b.pop();
        }
        b.push().translate(4.6f, 0.4f, 13.0f).rotateY(-40).rotateZ(90).translate(0, -1.8f, 0);
        b.cylinder(0.4f, 0, 3.6f, 8, AtlasCivil.WHITE, AtlasCivil.WHITE, AtlasCivil.DARK);
        b.pop();
        b.color(0.8f, 0.72f, 0.58f);
        tasSol(b, 13.6f, 8.8f, 0.9f, 0.9f, 0.9f, 0.1f, 3, TOILE);
        b.color(1);
        b.tube(new float[][]{p(13.0f, 0.15f, 9.4f), p(11.6f, 0.15f, 9.0f), p(10.6f, 0.15f, 9.8f), p(9.4f, 0.15f, 9.2f)}, fill(4, 0.08f), 4, TOILE, false, true, 0, false);
        b.push().translate(2.4f, 0.12f, 12.6f);
        b.torus(0.6f, 0.1f, 12, 4, 0, 360, METAL_NU);
        b.push().rotateY(-20);
        b.box(0.5f, -0.06f, -0.2f, 2.2f, 0.08f, 0.2f, AtlasCivil.BRASS);
        b.pop();
        b.push().rotateY(40);
        b.box(0.5f, -0.06f, -0.2f, 2.0f, 0.06f, 0.18f, METAL_NU);
        b.pop();
        b.pop();
        b.push().translate(11.6f, 0.45f, 3.4f);
        b.torus(1.0f, 0.4f, 14, 6, 0, 360, SCOTCH_BRUN);
        b.pop();
        papiers(b, 9.5f, 13.6f, 14.2f, 14.6f, 4, 41);
        b.push().translate(5.8f, 0.2f, 13.8f).rotateY(14).rotateX(-90);
        b.decalDouble(PHOTO, 2.4f, 2.4f);
        b.pop();
    }

    // =================================================================== chariot de ménage
    /**
     * Chariot de ménage (95 x 45 x 100 cm) : sac jaune déchiré, seau à essoreur d'eau croupie, balai à franges appuyé,
     * flacons, gants et rouleaux sur les plateaux ; roulettes rouillées.
     */
    static void chariotMenage(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.8f, z1 = 7.8f, xs = 9.0f;
        b.color(0.42f, 0.44f, 0.46f);
        b.bevelBox(x0, 0.9f, z0, x1, 1.6f, z1, 0.15f, PLAST);
        b.color(1);
        for (float[] q : new float[][]{{1.8f, 1.8f}, {14.2f, 1.8f}, {1.8f, 6.8f}, {14.2f, 6.8f}}) {
            b.box(q[0] - 0.3f, 0.75f, q[1] - 0.3f, q[0] + 0.3f, 0.92f, q[1] + 0.3f, METAL_NU);
            b.box(q[0] - 0.25f, 0.4f, q[1] - 0.3f, q[0] + 0.25f, 0.76f, q[1] - 0.22f, ROUILLE);
            b.push().translate(q[0], 0.42f, q[1]).rotateX(90);
            b.cylinder(0.42f, -0.18f, 0.18f, 10, CAOUTCHOUC_USE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
            b.pop();
        }
        // montants et plateaux (partie gauche)
        b.color(0.42f, 0.44f, 0.46f);
        for (float[] q : new float[][]{{x0 + 0.3f, z0 + 0.3f}, {xs - 0.3f, z0 + 0.3f}, {x0 + 0.3f, z1 - 0.3f}, {xs - 0.3f, z1 - 0.3f}}) {
            b.bevelBox(q[0] - 0.25f, 1.6f, q[1] - 0.25f, q[0] + 0.25f, 12.1f, q[1] + 0.25f, 0.08f, PLAST);
        }
        for (float y : new float[]{7.6f, 12.0f}) {
            b.bevelBox(x0, y, z0, xs, y + 0.45f, z1, 0.1f, PLAST);
            b.bevelBox(x0, y + 0.43f, z1 - 0.3f, xs, y + 1.0f, z1, 0.08f, PLAST);
            b.bevelBox(x0, y + 0.43f, z0, xs, y + 1.0f, z0 + 0.3f, 0.08f, PLAST);
        }
        b.color(1);
        // sac jaune (sous le plateau du milieu), rabattu sur un cadre
        b.color(0.86f, 0.74f, 0.24f);
        b.push().translate((x0 + xs) / 2, 1.6f, (z0 + z1) / 2);
        b.lathe(new float[][]{{2.4f, 0}, {3.0f, 0.4f}, {3.2f, 2.8f}, {3.05f, 5.2f}, {3.25f, 5.6f}}, 16, 0, 360, PLAST, 1.15f, 0.92f, 0.06f, 0.6f, 5, false);
        b.disc(2.4f, 0.02f, 16, PLAST, false);
        b.pop();
        b.color(1);
        b.push().translate((x0 + xs) / 2, 7.2f, (z0 + z1) / 2).scale(1.15f, 1, 0.92f);
        b.torus(3.4f, 0.12f, 16, 4, 0, 360, METAL_NU);
        b.pop();
        // plateau du haut : flacons, gants ; milieu : serviettes, rouleaux
        flacon(b, 2.2f, 12.45f, 3.0f, 0.7f, 3.4f, 0xE6E4DE, L_JAVEL, 0x2A5AA0);
        flacon(b, 4.0f, 12.45f, 4.8f, 0.6f, 3.0f, 0xB58AAE, L_SHAMPOOING, 0xE8E4D8);
        flacon(b, 6.0f, 12.45f, 3.2f, 0.65f, 2.8f, 0x4E8A3E, L_VAISSELLE, 0xD8C83A);
        b.color(0.86f, 0.74f, 0.24f);
        b.push().translate(7.6f, 12.65f, 5.4f).rotateY(30);
        b.blob(0.9f, 0.18f, 0.55f, 0.7f, 0.1f, 2f, 3, 10, PLAST);
        b.pop();
        b.color(0.92f, 0.9f, 0.84f);
        b.bevelBox(1.4f, 8.05f, 1.6f, 4.6f, 9.0f, 6.4f, 0.35f, EPONGE);
        b.bevelBox(1.6f, 8.98f, 1.8f, 4.4f, 9.8f, 6.2f, 0.32f, EPONGE);
        for (float[] q : new float[][]{{6.0f, 2.6f}, {7.6f, 2.6f}, {6.8f, 4.2f}}) {
            b.push().translate(q[0], 8.05f, q[1]);
            b.cylinder(0.75f, 0, 1.6f, 12, AtlasCivil.WHITE, null, AtlasCivil.WHITE);
            b.disc(0.25f, 1.62f, 8, AtlasCivil.DARK, true);
            b.pop();
        }
        b.color(1);
        // seau à essoreur, eau croupie, balai à franges appuyé
        b.color(0.36f, 0.5f, 0.72f);
        b.push().translate(12.0f, 1.6f, 4.3f);
        b.lathe(new float[][]{{2.0f, 0}, {2.5f, 4.4f}, {2.65f, 4.6f}}, 18, 0, 360, PLAST, 1.05f, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{2.5f, 4.55f}, {2.0f, 0.2f}}, 18, 0, 360, PLAST, 1.05f, 1, 0, 0, 0, true);
        b.color(0.36f, 0.32f, 0.24f);
        b.disc(2.3f, 3.2f, 18, TERRE, true);
        b.color(1);
        b.pop();
        b.color(0.3f, 0.3f, 0.32f);
        b.bevelBox(12.6f, 5.8f, 2.2f, 14.6f, 7.2f, 6.4f, 0.2f, PLAST);
        b.color(1);
        b.tube(new float[][]{p(14.2f, 7.0f, 4.3f), p(14.6f, 10.0f, 4.3f), p(14.2f, 11.2f, 4.3f)}, fill(3, 0.16f), 6, METAL_NU, false, true, 0, false);
        b.push().translate(11.4f, 4.0f, 4.0f);
        b.color(0.8f, 0.76f, 0.66f);
        b.lathe(new float[][]{{0.01f, -0.3f}, {1.3f, 0}, {1.2f, 1.0f}, {0.4f, 1.6f}, {0.25f, 1.9f}}, 12, 0, 360, TOILE, 1, 1, 0.12f, 1.5f, 2, false);
        b.color(1);
        b.pop();
        b.tube(new float[][]{p(11.4f, 5.6f, 4.0f), p(13.2f, 15.4f, 1.2f)}, fill(2, 0.18f), 6, AtlasCivil.PLASTIC_BLUE, false, true, 0, false);
        coulure(b, 'z', 3.0f, 1.5f, z1, 0.6f, 1);
        boue(b, 'z', 8.0f, 0.9f, z1, 14.0f, 0.66f, 0);
        decal(b, AUREOLE, 'y', 4.2f, 12.45f, 5.8f, 2.0f, 1.8f);
    }
}
