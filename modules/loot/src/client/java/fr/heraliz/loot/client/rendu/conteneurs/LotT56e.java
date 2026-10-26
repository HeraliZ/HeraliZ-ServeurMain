package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T56, T6 (fin) : fût biologique, caisse de prototype, baie de serveurs, distributeur, valise de fonds, caisse scellée. */
final class LotT56e {
    private LotT56e() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t6_fut_biologique", "t6", "metal", "Fût de déchets biologiques", "Biohazard waste drum", LotT56e::futBiologique);
        Conteneurs.ajouter("t6_caisse_prototype", "t6", "metal", "Caisse de prototype", "Prototype weapon crate", LotT56e::caissePrototype);
        Conteneurs.verre("t6_caisse_prototype", LotT56e::caissePrototypeVerre);
        Conteneurs.usure("t6_caisse_prototype", 0.6f);
        Conteneurs.ajouter("t6_baie_serveurs", "t6", "metal", "Baie de serveurs", "Server rack", LotT56e::baieServeurs);
        Conteneurs.usure("t6_baie_serveurs", 0.6f);
        Conteneurs.ajouter("t6_distributeur_billets", "t6", "metal", "Distributeur de billets", "Ripped-out cash machine", LotT56e::distributeur);
        Conteneurs.ajouter("t6_valise_fonds", "t6", "metal", "Valise de transport de fonds", "Cash-in-transit case", LotT56e::valiseFonds);
        Conteneurs.usure("t6_valise_fonds", 0.6f);
        Conteneurs.ajouter("t6_caisse_scellee", "t6", "metal", "Caisse militaire scellée", "Sealed military crate", LotT56e::caisseScellee);
        Conteneurs.usure("t6_caisse_scellee", 0.6f);
    }

    // =================================================================== fût biologique
    /**
     * Fût de 200 L (jaune, bande rouge, trèfle danger biologique) au couvercle cerclé, pied rouillé ; un bidon rouge de
     * 30 L renversé devant, bouchon arraché, coulée séchée au sol.
     */
    static void futBiologique(MeshBuilder b) {
        float cx = 6.2f, cz = 5.4f, r = 4.7f, h = 13.8f;
        b.push().translate(cx, 0, cz);
        b.lathe(new float[][]{{r - 0.3f, 0}, {r, 0.25f}, {r, 4.3f}, {r + 0.18f, 4.5f}, {r, 4.7f}, {r, 9.1f}, {r + 0.18f, 9.3f}, {r, 9.5f}, {r, h - 0.25f}, {r - 0.12f, h}},
                24, 0, 360, JAUNE_FUT, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{r + 0.1f, 0.15f}, {r + 0.1f, 1.0f}}, 24, 0, 360, ROUILLE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{r + 0.1f, 6.0f}, {r + 0.1f, 8.0f}}, 24, 0, 360, ROUGE_SECU, 1, 1, 0, 0, 0, false);
        // couvercle bombé, collier de serrage, levier, bondes
        b.lathe(new float[][]{{r + 0.14f, h - 0.15f}, {r + 0.16f, h + 0.12f}, {r - 0.4f, h + 0.18f}, {r - 1.2f, h + 0.34f}, {0.01f, h + 0.42f}}, 24, 0, 360, JAUNE_FUT, 1, 1, 0, 0, 0, false);
        b.push().translate(0, h, 0);
        b.torus(r + 0.2f, 0.22f, 24, 5, 0, 360, METAL_NU);
        b.pop();
        b.push().rotateY(-70).translate(0, h - 0.1f, r + 0.3f);
        b.bevelBox(-0.6f, -0.35f, -0.1f, 0.6f, 0.35f, 0.5f, 0.08f, METAL_NU);
        b.bevelBox(-1.8f, -0.15f, 0.3f, 0.2f, 0.15f, 0.55f, 0.05f, METAL_NU);
        b.pop();
        for (float[] q : new float[][]{{-2.2f, 1.6f}, {2.6f, -1.2f}}) {
            b.push().translate(q[0], h + 0.3f, q[1]);
            b.cylinder(0.55f, 0, 0.3f, 10, METAL_NU, null, METAL_NU);
            b.pop();
        }
        b.pop();
        // décalcomanies enroulées : secteurs calés sur les facettes du fût (15°) pour ne pas scintiller
        decalCylindre(b, BIOHAZARD, cx, cz, r + 0.06f, 9.9f, 13.3f, 97.5f, 45, 3);
        decalCylindre(b, S_BIO, cx, cz, r + 0.06f, 4.85f, 5.85f, 90, 60, 4);
        decalCylindre(b, ETIQUETTE_BIO, cx, cz, r + 0.17f, 6.4f, 7.6f, 142.5f, 45, 3);
        decalCylindre(b, COULURE_2, cx, cz, r + 0.06f, 10.6f, 13.0f, 37.5f, 15, 1);
        decalCylindre(b, GRIFFURES, cx, cz, r + 0.06f, 2.0f, 3.2f, 52.5f, 45, 3);
        // bidon rouge de 30 L renversé, bouchon arraché, coulée
        b.push().translate(10.4f, 2.55f, 12.9f).rotateY(-6).rotateZ(-90);
        b.color(0.62f, 0.2f, 0.15f);
        b.lathe(new float[][]{{2.3f, -3.4f}, {2.55f, -3.2f}, {2.55f, 2.8f}, {2.2f, 3.3f}, {0.9f, 3.45f}, {0.9f, 3.9f}, {0.7f, 3.9f}, {0.7f, 3.5f}}, 16, 0, 360, PLASTIQUE_GRAIN, 1, 1, 0, 0, 0, false);
        b.disc(2.3f, -3.4f, 16, PLASTIQUE_GRAIN, false);
        b.color(1);
        b.push().translate(0, 3.6f, 0);
        b.disc(0.7f, 0, 10, AtlasLoot.DARK, true);
        b.pop();
        decalCylindre(b, ETIQUETTE_BIO, 0, 0, 2.62f, -1.2f, 0.4f, 90, 90, 4);
        b.pop();
        b.color(0.62f, 0.2f, 0.15f);
        b.push().translate(14.4f, 0, 9.2f);
        b.cylinder(1.0f, 0, 0.5f, 12, PLASTIQUE_GRAIN, null, PLASTIQUE_GRAIN);
        b.pop();
        b.color(1);
        decal(b, AUREOLE, 'y', 14.4f, 0, 14.4f, 2.6f, 2.2f, -10);
        decal(b, AUREOLE, 'y', 13.6f, 0, 15.2f, 2.0f, 1.4f, 20);
        decal(b, BOUE_1, 'y', 6.0f, 0, 10.9f, 7.0f, 2.0f, 0);
        jonchee(b, 0.5f, 11.0f, 9.0f, 15.6f, 5, FEUILLES, 1.0f, 801);
    }

    // =================================================================== caisse de prototype
    static final float PR_CX = 8.0f, PR_CZ = 4.2f, PR_R = 2.4f, PR_H = 7.0f;

    /**
     * Caisse noire scellée (95 x 45 x 45 cm) à hublot sur le couvercle : arme expérimentale visible dans la mousse
     * (bobines cuivrées, cellule d'énergie au voyant faible), bandes rouges et blanches, panneau d'état en veille,
     * fermoir forcé.
     */
    static void caissePrototype(MeshBuilder b) {
        float x0 = 0.6f, x1 = 15.4f, z0 = 0.6f, z1 = 7.8f, y0 = 0.6f, h = PR_H, e = 0.4f, cx = PR_CX, cz = PR_CZ, r = PR_R;
        for (float x : new float[]{2.4f, 13.6f}) b.bevelBox(x - 0.7f, 0, z0 + 0.3f, x + 0.7f, y0 + 0.03f, z1 - 0.3f, 0.1f, AtlasLoot.DARK);
        b.bevelBox(x0 + 0.06f, y0 + 0.04f, z0 + 0.06f, x1 - 0.06f, y0 + e, z1 - 0.06f, 0.15f, ACIER_NOIR);
        b.bevelBox(x0, y0, z0, x0 + e, h - 0.4f, z1, 0.15f, ACIER_NOIR);
        b.bevelBox(x1 - e, y0, z0, x1, h - 0.4f, z1, 0.15f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.02f, y0, z0, x1 - e + 0.02f, h - 0.4f, z0 + e, 0.15f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.02f, y0, z1 - e, x1 - e + 0.02f, h - 0.4f, z1, 0.15f, ACIER_NOIR);
        // mousse et arme (seule la partie sous le hublot est modelée)
        float yb = 3.4f;
        b.box(x0 + e, y0 + e, z0 + e, x1 - e, yb, z1 - e, AtlasLoot.FOAM);
        b.push().translate(cx, yb + 0.45f, cz).rotateY(-6);
        b.bevelBox(-3.0f, -0.45f, -0.55f, 0.6f, 0.5f, 0.55f, 0.2f, AtlasLoot.BLACK_STEEL);
        b.push().translate(0.4f, 0.05f, 0).rotateZ(-90);
        b.cylinder(0.3f, 0, 3.4f, 10, AtlasLoot.BLACK_STEEL, null, AtlasLoot.DARK);
        for (int k = 0; k < 4; k++) {
            b.push().translate(0, 0.5f + k * 0.7f, 0);
            b.torus(0.52f, 0.15f, 12, 5, 0, 360, AtlasLoot.BRASS);
            b.pop();
        }
        b.pop();
        b.bevelBox(-2.2f, 0.5f, -0.4f, -0.6f, 1.0f, 0.4f, 0.12f, ACIER_NOIR);
        b.bevelBox(-2.0f, 0.98f, 0.0f, -1.7f, 1.06f, 0.3f, 0.02f, LED_ROUGE);
        b.bevelBox(-2.8f, -0.4f, 0.55f, -1.4f, 0.4f, 1.1f, 0.1f, AtlasLoot.DARK);
        b.pop();
        // couvercle : plaque percée d'un hublot, cerclage boulonné
        b.bevelBox(x0 - 0.08f, h - 0.42f, z0 - 0.08f, x1 + 0.08f, h, z1 + 0.08f, 0.15f, ACIER_NOIR, null, MeshBuilder.SKIP_PY);
        plaqueTrouee(b, ACIER_NOIR, x0 + 0.07f, z0 + 0.07f, x1 - 0.07f, z1 - 0.07f, cx, cz, r + 0.5f, h, 20);
        b.push().translate(cx, 0, cz);
        b.hollowCylinder(r + 0.55f, r, h - 0.9f, h + 0.3f, 20, METAL_NU);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.2;
            b.push().translate((float) Math.cos(a) * (r + 0.28f), h + 0.3f, (float) Math.sin(a) * (r + 0.28f));
            b.cylinder(0.14f, -0.02f, 0.14f, 6, AtlasLoot.CHROME, null, AtlasLoot.CHROME);
            b.pop();
        }
        b.pop();
        // bandes rouges et blanches aux extrémités, marquages, panneau d'état en veille
        for (float x : new float[]{x0 + 0.6f, x1 - 1.8f}) {
            b.box(x, y0 + 0.3f, z1 + 0.01f, x + 1.2f, h - 0.6f, z1 + 0.07f, BANDE_ROUGE);
            b.box(x, h + 0.01f, z0 + 0.3f, x + 1.2f, h + 0.07f, z1 - 0.3f, BANDE_ROUGE);
        }
        Kit.pochoir(b, S_PROTOTYPE, 'z', 6.4f, 3.2f, z1, 5.6f, 1.4f);
        Kit.pochoir(b, S_CONFIDENTIEL, 'z', 6.4f, 1.9f, z1, 4.4f, 1.1f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 12.2f, 1.8f, z1, 1.8f, 0.9f);
        b.bevelBox(10.8f, 3.0f, z1 - 0.02f, 13.4f, 4.6f, z1 + 0.25f, 0.08f, AtlasLoot.DARK);
        Kit.pochoir(b, ECRAN_VEILLE, 'z', 11.7f, 3.8f, z1 + 0.25f, 1.4f, 0.7f);
        b.bevelBox(12.8f, 3.6f, z1 + 0.23f, 13.1f, 3.9f, z1 + 0.32f, 0.03f, LED_ROUGE);
        // fermoirs (un forcé), poignées
        for (float x : new float[]{3.6f, 12.4f}) Kit.fermoir(b, x, h - 0.6f, z1, AtlasLoot.CHROME);
        fermoirOuvert(b, 8.0f, h - 0.4f, z1, 32, AtlasLoot.CHROME);
        Kit.poigneeU(b, x1 + 0.08f, 4.4f, (z0 + z1) / 2, 3.0f, 1, AtlasLoot.CHROME);
        Kit.poigneeU(b, x0 - 0.08f, 4.4f, (z0 + z1) / 2, 3.0f, -1, AtlasLoot.CHROME);
        decal(b, GRIFFURES, 'z', 8.6f, h - 1.2f, z1, 2.6f, 1.0f, 12);
        coulure(b, 'z', 3.6f, h - 1.6f, z1, 0.8f, 1);
        boue(b, 'z', 8.0f, y0, z1, 9.0f, 0.7f, 0);
        decal(b, AUREOLE, 'y', 11.0f, h, 2.0f, 2.0f, 1.6f, 0);
    }

    static void caissePrototypeVerre(MeshBuilder b) {
        b.push().translate(PR_CX, PR_H + 0.12f, PR_CZ);
        b.disc(PR_R + 0.02f, 0, 20, VERRE_SALE, true);
        b.pop();
    }

    // =================================================================== baie de serveurs
    /**
     * Baie de serveurs (60 x 75 x 200 cm) : porte perforée arrachée appuyée sur le flanc droit, serveurs 1U/2U dont
     * certains manquent ou sortent de leurs rails, quelques voyants encore allumés, câbles qui pendent jusqu'au sol,
     * disques arrachés au sol.
     */
    static void baieServeurs(MeshBuilder b) {
        float x0 = 3.0f, x1 = 12.6f, z0 = 0.4f, z1 = 12.4f, y0 = 1.0f, h = 32.0f, t = 0.7f;
        for (float x : new float[]{x0 + 0.8f, x1 - 0.8f}) {
            for (float z : new float[]{z0 + 0.8f, z1 - 0.8f}) {
                b.push().translate(x, 0, z);
                b.cylinder(0.35f, 0, y0 + 0.03f, 8, METAL_NU, METAL_NU, null);
                b.pop();
            }
        }
        b.bevelBox(x0, y0, z0, x1, y0 + 0.8f, z1, 0.15f, ACIER_NOIR);
        b.bevelBox(x0, h - 1.0f, z0, x1, h, z1, 0.2f, ACIER_NOIR);
        b.bevelBox(x0, y0 + 0.78f, z0, x0 + 0.3f, h - 0.98f, z1, 0.1f, ACIER_NOIR);
        b.bevelBox(x1 - 0.3f, y0 + 0.78f, z0, x1, h - 0.98f, z1, 0.1f, ACIER_NOIR);
        b.bevelBox(x0 + 0.28f, y0 + 0.78f, z0, x1 - 0.28f, h - 0.98f, z0 + 0.3f, 0.08f, ACIER_NOIR);
        for (float x : new float[]{x0 + 0.3f, x1 - 0.3f - t}) b.bevelBox(x, y0 + 0.8f, z1 - t - 0.02f, x + t, h - 1.0f, z1, 0.12f, ACIER_NOIR);
        float rx0 = x0 + 0.3f + t, rx1 = x1 - 0.3f - t;
        for (float x : new float[]{rx0, rx1 - 0.25f}) b.box(x, y0 + 0.8f, z1 - 1.6f, x + 0.25f, h - 1.0f, z1 - 1.0f, METAL_NU);
        // serveurs : (hauteur en U, état) 0 en place, 1 sorti sur ses rails, -1 vide
        float u = 0.72f, y = y0 + 1.0f, zf = z1 - 1.2f;
        int[][] pile = {{3, 0}, {2, 0}, {1, 0}, {2, -1}, {2, 0}, {1, 1}, {1, 0}, {2, 0}, {1, -1}, {2, 0}, {1, 0}, {1, 0}, {2, -1}, {2, 0}, {1, 0}, {1, -1}, {2, 0}};
        int led = 0;
        for (int[] s : pile) {
            float hh = s[0] * u;
            if (s[1] >= 0) {
                float sortie = s[1] == 1 ? 2.2f : 0;
                b.box6(rx0 + 0.25f, y + 0.03f, z0 + 1.2f + sortie, rx1 - 0.25f, y + hh - 0.03f, zf + sortie, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR, ACIER_NOIR, null,
                        s[0] == 1 ? SERVEUR_1U : SERVEUR_2U);
                if (s[1] == 0 && led < 5 && (s[0] + led) % 2 == 0) {
                    fr.heraliz.loot.client.rendu.mesh.Mat m = led % 3 == 0 ? LED_VERTE : led % 3 == 1 ? LED_AMBRE : LED_ROUGE;
                    b.box(rx1 - 1.3f, y + hh * 0.5f - 0.12f, zf + 0.01f, rx1 - 1.06f, y + hh * 0.5f + 0.12f, zf + 0.08f, m);
                    led++;
                }
            } else {
                b.box(rx0 + 0.3f, y + 0.02f, z0 + 1.2f, rx1 - 0.3f, y + 0.08f, zf - 0.4f, METAL_NU);
            }
            y += hh + 0.04f;
        }
        b.color(0.6f, 0.6f, 0.62f);
        niche(b, AtlasLoot.INNER, x0 + 0.36f, y, z0 + 0.36f, x1 - 0.36f, h - 1.06f, z1 - t - 0.06f);
        b.color(1);
        // câbles : faisceau qui pend du haut, câbles jusqu'au sol, rouleau
        float[][] tc = {{0.85f, 0.75f, 0.2f}, {0.25f, 0.4f, 0.75f}, {0.3f, 0.3f, 0.32f}, {0.7f, 0.2f, 0.18f}};
        for (int k = 0; k < 4; k++) {
            float xs = rx0 + 1.4f + k * 0.9f;
            float[][] ch = auSol(lisse(new float[][]{p(xs, y - 0.2f, zf - 1.0f), p(xs + 0.3f, 19.0f - k, zf + 1.0f), p(xs - 0.2f, 10.6f - k * 0.5f, 13.9f), p(xs + k * 0.8f, 0.15f, 14.6f + k * 0.25f), p(xs + 1.6f + k * 0.6f, 0.15f, 15.4f)}, 4), 0.14f);
            cable(b, ch, 0.13f, tc[k][0], tc[k][1], tc[k][2]);
        }
        // porte perforée arrachée, appuyée sur le flanc droit
        b.push().translate(15.4f, 0.04f, (z0 + z1) / 2).rotateZ(4.5f);
        b.bevelBox(-0.4f, 0, -5.2f, 0, 30.0f, -4.4f, 0.1f, ACIER_NOIR);
        b.bevelBox(-0.4f, 0, 4.4f, 0, 30.0f, 5.2f, 0.1f, ACIER_NOIR);
        b.bevelBox(-0.4f, 0, -4.42f, 0, 0.8f, 4.42f, 0.1f, ACIER_NOIR);
        b.bevelBox(-0.4f, 29.2f, -4.42f, 0, 30.0f, 4.42f, 0.1f, ACIER_NOIR);
        b.box6(-0.3f, 0.8f, -4.4f, -0.1f, 29.2f, 4.4f, TOLE_PERFOREE, TOLE_PERFOREE, null, null, null, null);
        b.bevelBox(0, 13.0f, 3.2f, 0.5f, 17.0f, 3.8f, 0.12f, AtlasLoot.CHROME);
        b.pop();
        // disques arrachés au sol
        float[][] dq = {{5.0f, 13.6f, 30}, {7.8f, 14.0f, -15}, {10.4f, 13.4f, 70}};
        for (float[] q : dq) {
            b.push().translate(q[0], 0, q[1]).rotateY(q[2]);
            b.bevelBox(-1.0f, 0, -1.6f, 1.0f, 0.6f, 1.6f, 0.06f, ALU);
            b.box6(-1.0f, 0.02f, 1.6f, 1.0f, 0.58f, 1.66f, null, null, null, null, null, DISQUE);
            b.pop();
        }
        // usure
        decal(b, GRIFFURES, 'x', x0, 16.0f, 6.0f, 3.0f, 1.2f, 0);
        decal(b, ETIQUETTE_DECHIREE, 'x', x0, 24.0f, 8.0f, 2.6f, 1.3f, -4);
        coulure(b, 'x', x0, h - 1.4f, 3.0f, 1.2f, 1);
        boue(b, 'x', x0, y0, 6.4f, 10.0f, 1.4f, 0);
        jonchee(b, 1.0f, 12.8f, 15.0f, 15.8f, 4, PAPIER, 1.3f, 811);
    }

    // =================================================================== distributeur
    /**
     * Distributeur de billets arraché du mur (60 x 50 x 140 cm), penché contre le mur, morceaux de béton encore
     * boulonnés au socle : façade éventrée (écran brisé, clavier qui pend), coffre forcé, cassettes vidées au sol,
     * billets tachés d'encre épars.
     */
    static void distributeur(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, d = 8.0f, hh = 22.4f, ys = 11.0f, e = 0.7f;
        b.push().translate(0, 0.02f, 3.2f).rotateX(-8);
        // coffre bas : coque ouverte vers l'avant
        b.bevelBox(x0 + 0.06f, 0.04f, 0.06f, x1 - 0.06f, 0.8f, d - 0.06f, 0.15f, ACIER_NOIR);
        b.bevelBox(x0, 0, 0, x0 + e, ys, d, 0.15f, ACIER_NOIR);
        b.bevelBox(x1 - e, 0, 0, x1, ys, d, 0.15f, ACIER_NOIR);
        b.bevelBox(x0 + e - 0.03f, 0, 0, x1 - e + 0.03f, ys, e, 0.12f, ACIER_NOIR);
        b.color(0.55f, 0.55f, 0.55f);
        niche(b, INOX, x0 + e + 0.06f, 0.86f, e + 0.06f, x1 - e - 0.06f, ys - 0.06f, d);
        b.color(1);
        b.push().translate((x0 + x1) / 2, ys / 2 + 0.4f, e + 0.07f);
        b.decalZ(INTERIEUR_ATM, 0, 0, 0.05f, x1 - x0 - 2 * e - 0.4f, ys - 2.4f);
        b.pop();
        b.bevelBox(x0 + e + 0.4f, 0.9f, e + 0.5f, x1 - e - 0.4f, 3.6f, d - 1.4f, 0.12f, METAL_NU);
        b.bevelBox(x0 + e + 0.4f, 3.8f, e + 0.5f, x1 - e - 0.4f, 6.4f, d + 1.2f, 0.12f, METAL_NU);
        // porte du coffre forcée, entrouverte (charnière à gauche)
        b.push().translate(x0, 0, d).rotateY(-22).translate(-x0, 0, -d);
        b.bevelBox(x0 + 0.05f, 0.85f, d, x1 - 0.05f, ys - 0.05f, d + 1.2f, 0.25f, ACIER_NOIR);
        for (float y : new float[]{3.0f, 8.0f}) {
            b.push().translate(x1 - 0.05f, y, d + 0.6f).rotateZ(-90);
            b.cylinder(0.3f, 0, 0.6f, 8, AtlasLoot.CHROME, null, AtlasLoot.CHROME);
            b.pop();
        }
        decal(b, BRULURE, 'z', x1 - 1.6f, 5.6f, d + 1.2f, 2.6f, 3.4f, 0);
        decal(b, GRIFFURES, 'z', x1 - 1.2f, 8.4f, d + 1.2f, 2.2f, 0.9f, 80);
        b.pop();
        // façade haute : bloc plein, écran brisé, fentes, clavier arraché qui pend, enseigne
        b.bevelBox(x0, ys, 0, x1, hh, d, 0.25f, ACIER_NOIR);
        b.color(0.62f, 0.63f, 0.66f);
        b.bevelBox(x0 + 0.2f, ys + 0.2f, d - 0.05f, x1 - 0.2f, hh - 1.8f, d + 0.3f, 0.15f, INOX);
        b.color(1);
        b.bevelBox(x0 + 0.1f, hh - 1.8f, d - 0.1f, x1 - 0.1f, hh - 0.1f, d + 0.6f, 0.15f, AtlasLoot.DARK);
        Kit.pochoir(b, S_RETRAIT, 'z', (x0 + x1) / 2, hh - 0.95f, d + 0.6f, 6.4f, 1.6f);
        b.bevelBox(x0 + 2.0f, 16.6f, d + 0.1f, x1 - 2.0f, 20.0f, d + 0.42f, 0.08f, AtlasLoot.DARK);
        Kit.pochoir(b, ATM_ECRAN, 'z', (x0 + x1) / 2, 18.3f, d + 0.42f, 5.2f, 3.0f);
        b.box(8.6f, 15.2f, d + 0.25f, 10.6f, 15.5f, d + 0.4f, AtlasLoot.DARK);
        b.box(x0 + 2.0f, 12.2f, d + 0.25f, x1 - 2.0f, 12.9f, d + 0.42f, AtlasLoot.DARK);
        b.push().translate((x0 + x1) / 2, 12.55f, d + 0.43f);
        b.decalZ(INTERIEUR_ATM.sub(0.1f, 0.6f, 0.9f, 0.75f), 0, 0, 0.02f, 5.4f, 0.6f);
        b.pop();
        b.pop();
        // clavier arraché qui pend au bout de ses câbles (repère monde)
        b.push().translate(5.4f, 13.8f, 10.9f).rotateX(62).rotateZ(-8);
        b.color(0.62f, 0.63f, 0.66f);
        b.bevelBox(-1.6f, -0.3f, -1.4f, 1.6f, 0.3f, 1.4f, 0.1f, INOX);
        b.color(1);
        b.push().translate(0, 0.3f, 0).rotateX(-90);
        b.decalZ(ATM_CLAVIER, 0, 0, 0.05f, 2.8f, 2.4f);
        b.pop();
        b.pop();
        cable(b, lisse(new float[][]{p(5.0f, 16.4f, 9.4f), p(4.8f, 15.6f, 10.6f), p(5.2f, 14.4f, 10.8f)}, 3), 0.1f, 0.7f, 0.2f, 0.18f);
        cable(b, lisse(new float[][]{p(6.0f, 16.4f, 9.3f), p(6.4f, 15.8f, 10.4f), p(5.8f, 14.6f, 10.7f)}, 3), 0.1f, 0.25f, 0.4f, 0.7f);
        // socle : béton arraché encore boulonné, tiges d'ancrage tordues
        b.push().translate(4.6f, 0.62f, 10.4f);
        b.rock(1.6f, 0.62f, 1.3f, 0.3f, 0.8f, 3, 10, BETON);
        b.pop();
        b.push().translate(11.4f, 0.55f, 10.6f);
        b.rock(1.5f, 0.55f, 1.2f, 0.3f, 0.9f, 4, 10, BETON);
        b.pop();
        for (float[] q : new float[][]{{4.0f, 11.8f, 0.4f}, {12.0f, 11.9f, -0.5f}}) {
            b.tube(new float[][]{p(q[0], 0.9f, q[1] - 0.6f), p(q[0] + q[2], 1.6f, q[1] + 0.4f), p(q[0] + q[2] * 2, 1.4f, q[1] + 1.4f)}, fill(3, 0.14f), 6, ROUILLE, true, true, 0, false);
        }
        // cassettes vidées au sol, billets tachés d'encre
        b.push().translate(9.8f, 0, 13.4f).rotateY(-22);
        b.bevelBox(-2.94f, 0.02f, -1.24f, 2.94f, 0.15f, 1.24f, 0.04f, METAL_NU);
        b.bevelBox(-3.0f, 0, -1.3f, -2.85f, 2.4f, 1.3f, 0.04f, METAL_NU);
        b.bevelBox(2.85f, 0, -1.3f, 3.0f, 2.4f, 1.3f, 0.04f, METAL_NU);
        b.bevelBox(-2.85f, 0, -1.3f, 2.85f, 2.4f, -1.15f, 0.04f, METAL_NU);
        b.bevelBox(-2.85f, 0, 1.15f, 2.85f, 2.4f, 1.3f, 0.04f, METAL_NU);
        liasse(b, -1.0f, 0.15f, 0, 3, 0.9f);
        b.pop();
        b.push().translate(3.8f, 1.3f, 13.0f).rotateY(40).rotateZ(90);
        b.bevelBox(-1.3f, -3.0f, -1.3f, 1.3f, 3.0f, 1.3f, 0.06f, METAL_NU);
        b.pop();
        Random r = new Random(23);
        for (int k = 0; k < 9; k++) {
            float x = 1.5f + r.nextFloat() * 13.0f, z = 12.4f + r.nextFloat() * 2.2f;
            b.push().translate(x, 0.04f + k * 0.012f, z).rotateY(r.nextFloat() * 360).rotateX(-90 + r.nextFloat() * 10 - 5);
            b.decalDouble(BILLETS, 2.4f, 1.2f);
            b.pop();
        }
        decal(b, TACHE_ENCRE, 'y', 2.4f, 0, 15.0f, 1.8f, 1.8f, 0);
        decal(b, TACHE_ENCRE, 'y', 14.6f, 0, 11.4f, 1.4f, 1.4f, 50);
        debris(b, 8.0f, 11.6f, 2.0f, 10, BETON, 821);
    }

    // =================================================================== valise de fonds
    /**
     * Valise de transport de fonds (45 x 33 x 12 cm) en aluminium couchée au sol : menotte serrée sur la poignée,
     * chaîne et bracelet ouvert (le convoyeur a disparu), tache d'encre ; sac de billets plombé affaissé contre le mur,
     * liasses répandues.
     */
    static void valiseFonds(MeshBuilder b) {
        // sac de billets contre le mur
        b.color(0.6f, 0.58f, 0.5f);
        b.push().translate(11.2f, 0, 3.4f).scale(1.1f, 1, 0.85f);
        b.lathe(new float[][]{{0.01f, 0}, {2.6f, 0.05f}, {3.3f, 0.8f}, {3.4f, 3.0f}, {3.0f, 5.2f}, {2.0f, 6.6f}, {0.9f, 7.3f}, {0.7f, 7.6f}, {1.1f, 8.4f}, {0.9f, 8.9f}, {0.01f, 8.9f}},
                18, 0, 360, TISSU_USE, 1, 1, 0.07f, 0.5f, 21, false);
        b.pop();
        b.color(1);
        b.push().translate(11.2f, 7.5f, 3.4f).scale(1.1f, 1, 0.85f);
        b.torus(0.8f, 0.14f, 12, 4, 0, 360, AtlasLoot.ROPE);
        b.pop();
        b.push().translate(12.6f, 7.5f, 3.6f).rotateY(-30);
        b.tube(new float[][]{p(0, 0.9f, 0), p(0.1f, 0.2f, 0.2f)}, fill(2, 0.04f), 4, METAL_NU, false, false, 0, false);
        b.bevelBox(-0.3f, -0.5f, 0.12f, 0.3f, 0.3f, 0.32f, 0.06f, SCELLE);
        b.pop();
        b.push().translate(11.2f, 0, 3.4f).scale(1.1f, 1, 0.85f);
        decalCylindre(b, S_FONDS, 0, 0, 3.62f, 2.0f, 3.2f, 90, 64, 6);
        decalCylindre(b, TACHE_ENCRE, 0, 0, 3.64f, 3.4f, 5.0f, 118, 34, 4);
        b.pop();
        // liasses répandues
        liasse(b, 13.6f, 0, 8.6f, 25, 0.8f);
        liasse(b, 12.0f, 0, 9.8f, -40, 0.6f);
        liasse(b, 14.2f, 0, 10.4f, 70, 0.7f);
        b.push().translate(13.4f, 0.02f, 9.6f).rotateY(-10).rotateX(-90);
        b.decalDouble(BILLETS, 2.4f, 1.2f);
        b.pop();
        // valise couchée
        float vx = 5.6f, vz = 9.6f;
        b.push().translate(vx, 0, vz).rotateY(14);
        float L = 7.2f, l = 5.3f, hv = 1.9f;
        b.bevelBox(-L / 2, 0, -l / 2, L / 2, hv / 2 - 0.05f, l / 2, 0.4f, ALU);
        b.bevelBox(-L / 2, hv / 2 + 0.05f, -l / 2, L / 2, hv, l / 2, 0.4f, ALU);
        b.bevelBox(-L / 2 + 0.2f, hv / 2 - 0.1f, -l / 2 + 0.2f, L / 2 - 0.2f, hv / 2 + 0.1f, l / 2 - 0.2f, 0.05f, AtlasLoot.DARK);
        for (float zz : new float[]{-1.0f, 1.0f}) b.bevelBox(-L / 2 + 0.6f, hv - 0.02f, zz - 0.25f, L / 2 - 0.6f, hv + 0.12f, zz + 0.25f, 0.06f, ALU);
        for (float xx : new float[]{-2.2f, 2.2f}) {
            b.bevelBox(xx - 0.5f, 0.4f, l / 2 - 0.05f, xx + 0.5f, 1.5f, l / 2 + 0.2f, 0.08f, AtlasLoot.CHROME);
            b.bevelBox(xx - 0.3f, 0.75f, l / 2 + 0.18f, xx + 0.3f, 1.15f, l / 2 + 0.3f, 0.04f, AtlasLoot.DARK);
        }
        b.tube(new float[][]{p(-1.0f, hv / 2, l / 2 + 0.15f), p(-0.8f, hv / 2, l / 2 + 1.0f), p(0.8f, hv / 2, l / 2 + 1.0f), p(1.0f, hv / 2, l / 2 + 0.15f)}, fill(4, 0.22f), 8, POIGNEE_NOIRE, false, false, 0, false);
        Kit.pochoir(b, TACHE_ENCRE, 'y', 1.2f, hv + 0.12f, -0.4f, 3.0f, 3.0f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'y', -2.2f, hv, 1.4f, 1.8f, 0.9f);
        b.push().translate(0.2f, 0.45f, l / 2);
        b.decalZ(GRIFFURES, 0, 0, 0.05f, 1.6f, 0.5f);
        b.pop();
        // menotte serrée sur la poignée
        b.push().translate(0, hv / 2, l / 2 + 0.95f).rotateZ(90);
        b.torus(0.42f, 0.08f, 10, 4, 0, 360, AtlasLoot.CHROME);
        b.pop();
        b.pop();
        // chaîne et bracelet ouvert au sol (repère monde)
        float[] a = p(vx + 0.24f + (float) Math.sin(Math.toRadians(14)) * 3.6f, 0.95f, vz + (float) Math.cos(Math.toRadians(14)) * 3.6f);
        b.chain(new float[][]{a, p(a[0] + 0.6f, 0.2f, a[2] + 1.0f), p(a[0] + 2.4f, 0.18f, a[2] + 1.4f), p(a[0] + 3.4f, 0.18f, a[2] + 0.9f)}, 0.5f, 0.3f, 0.08f, AtlasLoot.CHROME, 8, 4);
        b.push().translate(a[0] + 4.1f, 0.11f, a[2] + 0.7f);
        b.torus(0.5f, 0.1f, 12, 4, 0, 290, AtlasLoot.CHROME);
        b.bevelBox(0.35f, -0.08f, -0.2f, 0.85f, 0.12f, 0.2f, 0.04f, AtlasLoot.CHROME);
        b.pop();
        // usure : boue, feuilles, éraflures
        jonchee(b, 0.5f, 5.0f, 9.0f, 15.6f, 5, FEUILLES, 1.0f, 831);
        jonchee(b, 2.0f, 13.0f, 15.0f, 15.8f, 3, PAPIER, 1.2f, 832);
    }

    // =================================================================== caisse scellée
    /** Caisse militaire noire. */
    static void caisseNoire(MeshBuilder b, float x0, float y0, float z0, float x1, float h, float z1) {
        float yc = y0 + h * 0.8f, y1 = y0 + h;
        b.bevelBox(x0, y0, z0, x1, yc, z1, 0.2f, ACIER_NOIR);
        b.bevelBox(x0 - 0.08f, yc + 0.02f, z0 - 0.08f, x1 + 0.08f, y1, z1 + 0.08f, 0.22f, ACIER_NOIR);
        for (float x : new float[]{x0 + 0.5f, x1 - 1.3f}) {
            b.box(x, y0 + 0.3f, z1 + 0.01f, x + 0.8f, yc - 0.3f, z1 + 0.07f, BANDE_ROUGE);
        }
        Kit.poigneeU(b, x1 + 0.08f, y0 + h * 0.55f, (z0 + z1) / 2, 2.4f, 1, AtlasLoot.STEEL);
        Kit.poigneeU(b, x0 - 0.08f, y0 + h * 0.55f, (z0 + z1) / 2, 2.4f, -1, AtlasLoot.STEEL);
    }

    /** Scellé : fil passé dans le fermoir et plomb de plastique rouge qui pend. */
    static void scelle(MeshBuilder b, float x, float y, float z, boolean coupe) {
        if (!coupe) {
            b.tube(new float[][]{p(x - 0.15f, y, z), p(x - 0.25f, y - 0.6f, z + 0.15f), p(x, y - 0.9f, z + 0.2f), p(x + 0.25f, y - 0.6f, z + 0.15f), p(x + 0.15f, y, z)}, fill(5, 0.035f), 4, METAL_NU, false, false, 0, false);
            b.bevelBox(x - 0.3f, y - 1.6f, z + 0.12f, x + 0.3f, y - 0.85f, z + 0.3f, 0.06f, SCELLE);
        } else {
            b.tube(new float[][]{p(x + 0.15f, y, z), p(x + 0.3f, y - 0.8f, z + 0.15f), p(x + 0.2f, y - 1.5f, z + 0.2f)}, fill(3, 0.035f), 4, METAL_NU, false, true, 0, false);
        }
    }

    /**
     * Caisse militaire noire scellée (80 x 50 x 40 cm), plombs rouges sur les fermoirs, marquage CONFIDENTIEL ; une
     * plus petite posée de travers dessus ; un scellé coupé (plomb tombé au sol) et traces de levier.
     */
    static void caisseScellee(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.8f, z1 = 8.8f, ys = 0.5f, h = 6.0f;
        for (float x : new float[]{3.0f, 13.0f}) b.bevelBox(x - 0.7f, 0, z0 + 0.3f, x + 0.7f, ys + 0.03f, z1 - 0.3f, 0.1f, AtlasLoot.DARK);
        caisseNoire(b, x0, ys, z0, x1, h, z1);
        float yc = ys + h * 0.8f;
        for (float x : new float[]{4.0f, 8.0f, 12.0f}) {
            Kit.fermoir(b, x, yc, z1, AtlasLoot.STEEL);
            scelle(b, x, yc - 0.2f, z1 + 0.5f, x == 12.0f);
        }
        Kit.pochoir(b, S_CONFIDENTIEL, 'z', 6.6f, ys + 1.9f, z1, 6.0f, 1.5f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 11.6f, ys + 1.8f, z1, 2.0f, 1.0f);
        decal(b, GRIFFURES, 'z', 12.6f, yc + 0.5f, z1 + 0.08f, 2.4f, 0.9f, 15);
        // petite caisse de travers dessus (scellée)
        float y2 = ys + h;
        b.push().translate(8.0f, y2, 4.6f).rotateY(11).translate(-8.0f, -y2, -4.6f);
        caisseNoire(b, 3.6f, y2 + 0.02f, 2.0f, 12.4f, 4.6f, 7.4f);
        float yc2 = y2 + 0.02f + 4.6f * 0.8f;
        Kit.fermoir(b, 6.0f, yc2, 7.4f, AtlasLoot.STEEL);
        scelle(b, 6.0f, yc2 - 0.2f, 7.9f, false);
        Kit.fermoir(b, 10.0f, yc2, 7.4f, AtlasLoot.STEEL);
        scelle(b, 10.0f, yc2 - 0.2f, 7.9f, false);
        Kit.pochoir(b, S_CONFIDENTIEL, 'y', 8.0f, y2 + 4.62f, 4.7f, 5.6f, 1.4f);
        decal(b, AUREOLE, 'y', 5.4f, y2 + 4.62f, 3.6f, 2.2f, 1.8f, 0);
        b.pop();
        // plomb coupé tombé au sol, éclats de peinture
        b.push().translate(12.6f, 0.09f, 11.2f).rotateY(35).rotateX(90);
        b.bevelBox(-0.3f, -0.38f, -0.09f, 0.3f, 0.38f, 0.09f, 0.06f, SCELLE);
        b.pop();
        b.tube(new float[][]{p(12.2f, 0.04f, 11.0f), p(11.6f, 0.04f, 11.4f), p(11.2f, 0.04f, 11.0f)}, fill(3, 0.035f), 4, METAL_NU, true, true, 0, false);
        coulure(b, 'z', x0 + 0.6f, yc - 0.2f, z1, 0.9f, 0);
        boue(b, 'z', 8.0f, ys, z1, 9.0f, 1.0f, 1);
        decal(b, GRIFFURES, 'X', x1, ys + 2.0f, 4.8f, 3.0f, 1.2f, 0);
        jonchee(b, 0.5f, 9.6f, 15.5f, 15.6f, 6, FEUILLES, 1.0f, 841);
    }
}
