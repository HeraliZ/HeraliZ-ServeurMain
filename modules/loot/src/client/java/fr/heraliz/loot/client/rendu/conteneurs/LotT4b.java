package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T4b — militaire léger (camps, checkpoints, casernes), tiers T4 olive : camp abandonné en hâte. Caisses forcées,
 * cantine fouillée, sacs, sacs de sable, jerricans, rations, matériel sanitaire, râtelier, obus, poste radio, explosifs,
 * gilets, fûts, paquetage de tente. Échelle réelle (1 px = 6,25 cm), dos au mur (z = 0), les objets longs s'étendent
 * vers +X. Matières : page {@link LotT4bAtlas} + usure commune ({@link AtlasUsure}) + quelques pièces de {@link AtlasLoot}.
 */
public final class LotT4b {
    private LotT4b() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t4_caisse_bois_longue", "t4", "bois", "Caisse militaire longue", "Long military crate", LotT4b::caisseBoisLongue);
        Conteneurs.ajouter("t4_cantine_ouverte", "t4", "metal", "Cantine ouverte", "Open footlocker", LotT4b::cantineOuverte);
        Conteneurs.ajouter("t4_sac_marin", "t4", "tissu", "Sac marin", "Duffel bag", LotT4b::sacMarin);
        Conteneurs.ajouter("t4_sac_a_dos", "t4", "tissu", "Sac à dos militaire", "Military backpack", LotT4b::sacADos);
        LotT4b2.enregistrer();
    }

    // =================================================================== 1. caisse longue
    /**
     * Caisse militaire en planches peintes (1,5 x 0,5 x 0,45 m) sur deux blocs : couvercle forcé, repoussé de travers
     * (planche avant cassée, morceau tombé devant), clous tordus, traces de pied-de-biche ; pied-de-biche abandonné au sol.
     */
    static void caisseBoisLongue(MeshBuilder b) {
        float x0 = 4.0f, x1 = 28.0f, z0 = 0.6f, z1 = 8.6f, yb = 0.45f, h = 6.6f, e = 0.55f, zc = (z0 + z1) / 2;
        // patins, parois, fond
        for (float x : new float[]{x0 + 2.2f, x1 - 3.4f}) b.bevelBox(x, 0, z0 + 0.15f, x + 1.2f, yb + 0.03f, z1 - 0.15f, 0.08f, BOIS_KAKI);
        b.bevelBox(x0, yb, z1 - e, x1, h, z1, 0.1f, BOIS_OLIVE);
        b.bevelBox(x0, yb, z0, x1, h, z0 + e, 0.1f, BOIS_OLIVE);
        b.bevelBox(x0, yb, z0 + e - 0.02f, x0 + e, h, z1 - e + 0.02f, 0.1f, BOIS_OLIVE);
        b.bevelBox(x1 - e, yb, z0 + e - 0.02f, x1, h, z1 - e + 0.02f, 0.1f, BOIS_OLIVE);
        b.box6(x0 + e, yb, z0 + e, x1 - e, yb + 0.5f, z1 - e, null, null, null, INTERIEUR_SALE, null, null);
        // contenu : fibre de bois, long colis sous bâche ficelé
        b.push().translate(16, h - 1.9f, zc);
        b.blob((x1 - x0) / 2 - e - 0.1f, 1.5f, (z1 - z0) / 2 - e - 0.1f, 0.35f, 0.18f, 0.9f, 7, 16, AtlasLoot.ROPE);
        b.pop();
        b.tube(new float[][]{p(x0 + 1.4f, h - 1.5f, zc - 0.6f), p(12, h - 1.2f, zc - 0.3f), p(20, h - 1.25f, zc), p(x1 - 2.0f, h - 1.6f, zc + 0.2f)},
                new float[]{0.9f, 1.25f, 1.25f, 0.95f}, 10, BACHE, true, true, 0, false);
        for (float x : new float[]{x0 + 2.6f, 15.0f}) {
            b.push().translate(x, h - 1.25f, zc - 0.45f).rotateZ(90).rotateX(-4);
            b.torus(1.26f, 0.08f, 12, 3, 0, 360, AtlasLoot.ROPE);
            b.pop();
        }
        // tasseaux (avant et bouts), cornières olive, poignées de corde
        for (float x : new float[]{x0 + 0.9f, x1 - 1.9f}) b.bevelBox(x, yb + 0.1f, z1 - 0.02f, x + 1.0f, h - 0.15f, z1 + 0.35f, 0.08f, BOIS_OLIVE);
        b.bevelBox(x0 + 1.95f, yb + 0.1f, z1 - 0.02f, x1 - 1.95f, yb + 0.95f, z1 + 0.3f, 0.08f, BOIS_OLIVE);
        for (float z : new float[]{z0 + 0.8f, z1 - 1.8f}) {
            b.bevelBox(x1 - 0.02f, yb + 0.1f, z, x1 + 0.35f, h - 0.15f, z + 0.9f, 0.08f, BOIS_OLIVE);
            b.bevelBox(x0 - 0.35f, yb + 0.1f, z, x0 + 0.02f, h - 0.15f, z + 0.9f, 0.08f, BOIS_OLIVE);
        }
        Kit.poigneeCorde(b, x1 + 0.05f, 3.5f, zc, 3.0f, 1);
        Kit.poigneeCorde(b, x0 - 0.05f, 3.5f, zc, 3.0f, -1);
        b.color(0.85f);
        for (float x : new float[]{x0 - 0.08f, x1 - 0.82f}) {
            for (float y : new float[]{yb - 0.02f, h - 0.82f}) b.bevelBox(x, y, z1 - 0.82f, x + 0.9f, y + 0.9f, z1 + 0.42f, 0.14f, ACIER);
        }
        b.color(1);
        // pochoirs, traces de levier, coulures, boue
        pochoir(b, S_MATERIEL, 'z', 16.0f, 4.3f, z1, 7.2f, 1.8f);
        pochoir(b, S_CODE, 'z', 15.6f, 2.55f, z1, 6.4f, 1.0f);
        pochoir(b, S_HAUT, 'z', 22.9f, 4.2f, z1, 4.0f, 1.0f);
        pochoir(b, S_LOT, 'X', x1, 5.55f, zc, 4.2f, 0.62f);
        decal(b, GRIFFURES, 'z', 10.6f, h - 0.75f, z1, 3.4f, 1.5f, -6);
        decal(b, GRIFFURES, 'z', 20.4f, h - 0.6f, z1, 2.4f, 1.0f, 5);
        decal(b, AUREOLE, 'x', x0, 3.0f, 3.2f, 3.4f, 3.0f);
        coulure(b, 'z', x0 + 0.4f, h - 0.82f, z1, 0.8f, 0);
        coulure(b, 'z', x1 - 0.4f, h - 0.82f, z1, 0.9f, 1);
        coulure(b, 'z', 6.2f, h - 0.3f, z1, 1.0f, 2);
        boue(b, 'z', 16.0f, yb + 0.95f, z1 + 0.3f, 19.6f, 1.6f, 0);
        boue(b, 'X', x1 + 0.35f, yb + 0.1f, z0 + 1.25f, 0.9f, 1.8f, 1);
        boue(b, 'x', x0, yb, 4.6f, 5.0f, 2.0f, 0);
        // clous arrachés qui dépassent du chant
        clou(b, 9.0f, h, z1 - 0.28f, 0.7f, 0.25f, 0.15f);
        clou(b, 13.2f, h, z1 - 0.28f, 0.6f, -0.2f, 0.2f);
        clou(b, 21.4f, h, z1 - 0.3f, 0.65f, 0.1f, 0.3f);
        clou(b, 25.0f, h, z0 + 0.28f, 0.6f, -0.2f, 0.1f);
        // couvercle repoussé de travers : trois planches et deux traverses, planche avant cassée
        float lw = x1 - x0 + 0.3f, ld = z1 - z0 + 0.2f, lt = 0.5f, pw = ld / 3;
        b.push().translate(16 + 3.4f, h + 0.02f, zc + 0.55f).rotateY(-4);
        for (int k = 0; k < 3; k++) {
            float za = -ld / 2 + k * pw + 0.04f, zb = za + pw - 0.08f;
            float xb = k == 2 ? lw / 2 - 8.6f : lw / 2;
            b.bevelBox(-lw / 2, 0, za, xb, lt, zb, 0.1f, BOIS_OLIVE);
            if (k == 2) {
                // bout fendu en dents
                b.push().translate(xb - 0.05f, lt * 0.95f, 0).rotateX(90);
                b.prism(new float[][]{{0, za + 0.05f}, {1.4f, za + 0.5f}, {0, za + 1.0f}}, lt * 0.9f, BOIS_GRIS, BOIS_OLIVE);
                b.prism(new float[][]{{0, za + 1.0f}, {1.0f, za + 1.6f}, {0, zb - 0.05f}}, lt * 0.86f, BOIS_GRIS, BOIS_OLIVE);
                b.pop();
            }
        }
        b.bevelBox(-lw / 2 + 1.2f, lt - 0.02f, -ld / 2 + 0.1f, -lw / 2 + 2.3f, lt + 0.38f, ld / 2 - 0.1f, 0.08f, BOIS_OLIVE);
        b.bevelBox(lw / 2 - 2.4f, lt - 0.02f, -ld / 2 + 0.1f, lw / 2 - 1.3f, lt + 0.38f, -ld / 2 + 2 * pw - 0.15f, 0.08f, BOIS_OLIVE);
        pochoir(b, S_MATRICULE, 'y', 1.5f, lt, -ld / 2 + pw * 1.5f, 5.2f, 1.3f);
        decal(b, COULURE_2, 'y', -5.5f, lt, -ld / 2 + pw * 0.5f, 1.2f, 2.0f, 70);
        clou(b, lw / 2 - 1.85f, lt + 0.38f, -ld / 2 + 2 * pw - 0.4f, 0.55f, 0.2f, 0.2f);
        b.pop();
        // morceau de planche tombé devant, pied-de-biche (peint rouge, rouillé)
        b.push().translate(8.6f, 0, 11.8f).rotateY(16);
        b.bevelBox(-3.6f, 0, -1.25f, 3.6f, lt, 1.25f, 0.1f, BOIS_OLIVE);
        b.push().translate(-3.55f, lt * 0.95f, 0).rotateY(180).rotateX(90);
        b.prism(new float[][]{{0, -1.2f}, {1.2f, -0.6f}, {0, 0}}, lt * 0.9f, BOIS_GRIS, BOIS_OLIVE);
        b.prism(new float[][]{{0, 0}, {0.9f, 0.7f}, {0, 1.2f}}, lt * 0.86f, BOIS_GRIS, BOIS_OLIVE);
        b.pop();
        b.bevelBox(1.0f, lt - 0.02f, -1.15f, 2.1f, lt + 0.36f, 1.15f, 0.08f, BOIS_OLIVE);
        clou(b, 1.6f, lt + 0.36f, 0.6f, 0.5f, 0.2f, -0.1f);
        b.pop();
        b.color(0.62f, 0.2f, 0.16f);
        b.tube(new float[][]{p(16.5f, 0.2f, 12.6f), p(22.0f, 0.2f, 11.2f), p(25.6f, 0.2f, 10.3f), p(26.4f, 0.45f, 10.0f), p(26.6f, 0.95f, 10.2f), p(26.2f, 1.25f, 10.6f)},
                fill(6, 0.2f), 6, PEINTURE_USEE, true, true, 0, false);
        b.color(1);
        b.push().translate(16.5f, 0.2f, 12.6f).rotateY(-14);
        b.bevelBox(-0.7f, -0.2f, -0.28f, 0.05f, 0.08f, 0.28f, 0.06f, METAL_NU);
        b.pop();
    }

    // =================================================================== 2. cantine ouverte
    /**
     * Cantine métallique (80 x 40 x 35 cm) fouillée : couvercle relevé contre le mur (carte scotchée dedans), loquet
     * arraché, cadenas cisaillé au sol ; couverture et chemise pliées, manche qui pend, gamelle, conserves, lettres.
     */
    static void cantineOuverte(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 1.95f, z1 = 8.35f, h = 4.4f, e = 0.22f, lh = 1.15f, yb = 0.32f;
        // socle, parois, fond intérieur
        b.bevelBox(x0 + 0.2f, 0, z0 + 0.2f, x1 - 0.2f, yb + 0.02f, z1 - 0.2f, 0.08f, AtlasLoot.OD_DARK);
        b.bevelBox(x0, yb, z1 - e, x1, h, z1, 0.08f, ACIER);
        b.bevelBox(x0, yb, z0, x1, h, z0 + e, 0.08f, ACIER);
        b.bevelBox(x0, yb, z0 + e - 0.02f, x0 + e, h, z1 - e + 0.02f, 0.06f, ACIER);
        b.bevelBox(x1 - e, yb, z0 + e - 0.02f, x1, h, z1 - e + 0.02f, 0.06f, ACIER);
        b.box6(x0 + 0.1f, yb, z0 + 0.1f, x1 - 0.1f, yb + 0.35f, z1 - 0.1f, null, null, null, INTERIEUR_SALE, null, null);
        // nervures, rebord, coins emboutis
        for (float y : new float[]{1.0f, 3.5f}) {
            b.bevelBox(x0 + 0.95f, y, z1 - 0.03f, x1 - 0.95f, y + 0.28f, z1 + 0.22f, 0.05f, ACIER);
            b.bevelBox(x1 - 0.03f, y, z0 + 0.95f, x1 + 0.22f, y + 0.28f, z1 - 0.95f, 0.05f, ACIER);
            b.bevelBox(x0 - 0.22f, y, z0 + 0.95f, x0 + 0.03f, y + 0.28f, z1 - 0.95f, 0.05f, ACIER);
        }
        b.bevelBox(x0 - 0.08f, h - 0.3f, z1 - 0.1f, x1 + 0.08f, h + 0.04f, z1 + 0.1f, 0.06f, ACIER);
        b.color(0.8f);
        for (float x : new float[]{x0 - 0.1f, x1 - 0.8f}) {
            for (float z : new float[]{z0 - 0.1f, z1 - 0.8f}) b.bevelBox(x, yb - 0.07f, z, x + 0.9f, yb + 0.95f, z + 0.97f, 0.16f, ACIER);
            b.bevelBox(x, h - 0.9f, z1 - 0.8f, x + 0.9f, h + 0.16f, z1 + 0.17f, 0.16f, ACIER);
        }
        b.color(1);
        Kit.poigneeU(b, x1 + 0.12f, 3.0f, (z0 + z1) / 2, 2.4f, 1, AtlasLoot.STEEL);
        Kit.poigneeU(b, x0 - 0.12f, 3.0f, (z0 + z1) / 2, 2.4f, -1, AtlasLoot.STEEL);
        // loquets : celui de droite en place, celui de gauche arraché (deux trous rouillés), moraillon tordu
        b.bevelBox(11.8f, h - 1.25f, z1 + 0.08f, 13.0f, h - 0.15f, z1 + 0.3f, 0.06f, AtlasLoot.OD_DARK);
        b.bevelBox(12.1f, h - 1.0f, z1 + 0.28f, 12.7f, h - 0.4f, z1 + 0.5f, 0.06f, AtlasLoot.STEEL);
        boulon(b, 3.0f, h - 0.75f, z1 + 0.06f, 0.14f);
        boulon(b, 4.2f, h - 0.75f, z1 + 0.06f, 0.14f);
        b.push().translate(8.0f, h - 0.2f, z1 + 0.1f).rotateX(-25);
        b.bevelBox(-0.5f, -1.3f, 0, 0.5f, 0, 0.14f, 0.04f, AtlasLoot.STEEL);
        b.pop();
        coulure(b, 'z', 3.0f, h - 0.75f, z1, 0.9f, 0);
        coulure(b, 'z', 12.4f, h - 1.25f, z1, 0.9f, 1);
        coulure(b, 'X', x1, 3.1f, z0 + 1.2f, 0.8f, 2);
        // pochoirs, usure
        pochoir(b, S_NOM, 'z', 8.6f, 2.65f, z1, 5.4f, 0.95f);
        pochoir(b, S_MATRICULE, 'z', 8.6f, 1.75f, z1, 3.0f, 0.7f);
        decal(b, GRIFFURES, 'z', 6.0f, h - 0.6f, z1 + 0.02f, 2.6f, 1.1f, 4);
        decal(b, IMPACTS, 'X', x1, 2.2f, 5.6f, 2.2f, 1.1f);
        boue(b, 'z', 8.0f, yb, z1, 12.0f, 1.6f, 1);
        boue(b, 'x', x0, yb, (z0 + z1) / 2, 6.0f, 1.4f, 0);
        // couvercle relevé (charnière piano à l'arrière), appuyé contre le mur
        b.push().translate(0, h, z0).rotateX(-96).translate(0, -h, -z0);
        b.bevelBox(x0 - 0.06f, h + lh - 0.22f, z0 - 0.06f, x1 + 0.06f, h + lh, z1 + 0.06f, 0.1f, ACIER);
        b.bevelBox(x0 - 0.06f, h + 0.04f, z1 - 0.16f, x1 + 0.06f, h + lh - 0.2f, z1 + 0.06f, 0.06f, ACIER);
        b.bevelBox(x0 - 0.06f, h + 0.04f, z0 - 0.06f, x0 + 0.1f, h + lh - 0.2f, z1 - 0.14f, 0.05f, ACIER);
        b.bevelBox(x1 - 0.1f, h + 0.04f, z0 - 0.06f, x1 + 0.06f, h + lh - 0.2f, z1 - 0.14f, 0.05f, ACIER);
        // carte d'état-major scotchée à l'intérieur du couvercle
        b.push().translate(6.0f, h + lh - 0.27f, (z0 + z1) / 2 + 0.2f).rotateX(90).rotateZ(3);
        b.decalZ(CARTE, 0, 0, 0, 7.4f, 4.8f);
        b.color(0.9f);
        for (float[] c : new float[][]{{-3.5f, 2.3f}, {3.5f, 2.3f}, {-3.5f, -2.3f}}) b.box(c[0] - 0.45f, c[1] - 0.18f, 0.04f, c[0] + 0.45f, c[1] + 0.18f, 0.1f, SCOTCH);
        b.color(1);
        b.pop();
        b.push().translate(11.6f, h + lh - 0.27f, (z0 + z1) / 2 + 0.6f).rotateX(90).rotateZ(-8);
        b.decalZ(PAPIER, 0, 0, 0, 2.4f, 3.2f);
        b.box(-0.4f, 1.45f, 0.04f, 0.4f, 1.8f, 0.1f, SCOTCH);
        b.pop();
        b.pop();
        b.push().translate(x0 + 0.4f, h + 0.06f, z0 - 0.08f).rotateZ(-90);
        b.cylinder(0.14f, 0, x1 - x0 - 0.8f, 8, AtlasLoot.STEEL, AtlasLoot.STEEL, AtlasLoot.STEEL);
        b.pop();
        // contenu : couverture pliée, chemise pliée et manche qui pend dehors
        b.bevelBox(2.0f, yb + 0.3f, z0 + 0.4f, 8.4f, 2.0f, z1 - 0.4f, 0.4f, LAINE);
        b.bevelBox(2.1f, 1.95f, z0 + 0.5f, 8.2f, 2.75f, z1 - 0.5f, 0.35f, LAINE);
        b.push().translate(5.2f, 2.72f, 5.1f).rotateY(5);
        b.bevelBox(-2.7f, 0, -2.3f, 2.7f, 0.7f, 2.3f, 0.3f, KAKI_CLAIR);
        b.bevelBox(-2.5f, 0.66f, -0.6f, 2.5f, 0.85f, 0.4f, 0.08f, KAKI_CLAIR);
        b.pop();
        b.tube(new float[][]{p(4.2f, 3.2f, 6.9f), p(4.4f, 4.4f, 7.9f), p(4.55f, h + 0.45f, z1 + 0.05f), p(4.7f, 3.6f, z1 + 0.62f), p(4.8f, 2.0f, z1 + 0.6f), p(4.9f, 1.2f, z1 + 0.52f)},
                new float[]{0.5f, 0.42f, 0.38f, 0.4f, 0.42f, 0.44f}, 8, KAKI_CLAIR, false, true, 0, false);
        // lettres, chaussettes roulées
        b.push().translate(4.0f, 3.52f, 4.0f).rotateY(-20).rotateX(-90);
        b.decalDouble(PAPIER, 2.2f, 1.5f);
        b.pop();
        b.push().translate(3.2f, 3.52f, 6.4f).rotateY(30).scale(1.0f, 0.62f, 0.7f);
        b.sphere(0.9f, 10, 6, LAINE);
        b.pop();
        // gamelle (aluminium, anse rabattue), conserves olive, une renversée
        b.push().translate(11.2f, yb + 0.35f, 4.0f);
        b.lathe(new float[][]{{1.3f, 0}, {1.42f, 0.2f}, {1.45f, 2.0f}, {1.5f, 2.08f}}, 18, 0, 360, ALU, 1.25f, 0.82f, 0, 0, 0, false);
        b.lathe(new float[][]{{1.4f, 2.0f}, {1.3f, 0.3f}}, 18, 0, 360, ALU, 1.25f, 0.82f, 0, 0, 0, true);
        b.disc(1.3f, 0.32f, 18, INTERIEUR_SALE, true);
        b.tube(new float[][]{p(-1.85f, 1.9f, 0), p(-1.3f, 2.25f, 0.6f), p(0, 2.35f, 1.0f), p(1.3f, 2.25f, 0.6f), p(1.85f, 1.9f, 0)}, fill(5, 0.08f), 5, AtlasLoot.STEEL, false, false, 0, false);
        b.pop();
        b.color(0.8f);
        for (float[] c : new float[][]{{10.4f, 6.6f}, {12.4f, 6.4f}}) {
            b.push().translate(c[0], yb + 0.35f, c[1]);
            b.cylinder(0.7f, 0, 1.7f, 12, ACIER, null, AtlasLoot.STEEL);
            b.pop();
        }
        b.push().translate(11.5f, 2.75f, 6.6f).rotateY(70).rotateZ(84);
        b.cylinder(0.7f, -0.85f, 0.85f, 12, ACIER, AtlasLoot.STEEL, AtlasLoot.STEEL);
        b.pop();
        b.color(1);
        // au sol : cadenas cisaillé, conserve roulée
        b.push().translate(4.6f, 0, 10.6f).rotateY(-35);
        b.bevelBox(-0.75f, 0, -0.28f, 0.75f, 0.56f, 0.28f, 0.14f, AtlasLoot.BRASS);
        b.push().translate(0, 0.38f, 0).rotateX(90).rotateZ(-8).translate(0, -1.0f, 0);
        b.torus(0.48f, 0.12f, 12, 4, 200, 320, AtlasLoot.STEEL);
        b.pop();
        b.pop();
        b.push().translate(13.2f, 0.7f, 11.4f).rotateY(25).rotateZ(90);
        b.color(0.8f);
        b.cylinder(0.7f, -0.85f, 0.85f, 12, ACIER, AtlasLoot.STEEL, AtlasLoot.STEEL);
        b.color(1);
        b.pop();
    }

    // =================================================================== 3. sac marin
    /**
     * Sac marin (90 cm) couché le long du mur, tassé : toile bosselée, col froncé et lacé, sangle d'épaule, nom et
     * matricule au pochoir, pièce cousue, déchirure rafistolée au ruban adhésif, boue ; ceinturon tombé devant.
     */
    static void sacMarin(MeshBuilder b) {
        SacMou sac = new SacMou(new float[][]{{0.6f, 0.01f, 0}, {0.66f, 1.7f, 0}, {0.98f, 2.45f, 0}, {1.6f, 2.75f, 0.1f}, {3.5f, 2.88f, 0.2f}, {6.5f, 2.82f, 0.15f},
                {9.5f, 2.7f, 0.25f}, {11.6f, 2.45f, 0.4f}, {12.8f, 1.95f, 0.85f}, {13.7f, 1.25f, 1.0f}, {14.2f, 0.8f, 0.6f}, {14.45f, 0.74f, 0.3f},
                {14.7f, 1.0f, 1.0f}, {15.0f, 1.08f, 1.0f}, {15.25f, 0.62f, 0.5f}, {15.32f, 0.01f, 0}}, 0.8f, 0.62f, 0.07f, 5);
        b.push().translate(7.95f, 0, 3.95f).rotateY(-5).translate(-7.95f, 0, 0);
        sac.construire(b, TOILE, 22);
        // col : œillets et cordelette, mousqueton
        float yc = sac.dy;
        b.push().translate(14.45f, yc, 0).rotateZ(90).scale(0.8f, 1, 1);
        b.torus(0.8f, 0.12f, 14, 4, 0, 360, AtlasLoot.BRASS);
        b.pop();
        b.push().translate(14.9f, yc - 0.2f, 1.05f).rotateX(80).rotateZ(20);
        b.torus(0.42f, 0.08f, 10, 4, 0, 330, AtlasLoot.STEEL);
        b.pop();
        // sangle d'épaule posée sur le dessus, accrochée au fond et au col ; poignée de portage
        float[][] chemin = new float[9][];
        float[] ts = {0.35f, 1.2f, 2.4f, 3.6f, 5.0f, 6.4f, 7.6f, 8.6f, 9.6f};
        float[] phis = {175, 125, 108, 104, 104, 106, 110, 128, 170};
        for (int k = 0; k < 9; k++) chemin[k] = sac.point(ts[k], phis[k], 0.1f);
        b.color(0.62f);
        sangle(b, SANGLE, chemin, 1.05f, p(0, 0, -1));
        b.color(1);
        float[] bo = sac.point(3.6f, 104, 0.18f);
        b.bevelBox(bo[0] - 0.4f, bo[1] - 0.08f, bo[2] - 0.55f, bo[0] + 0.4f, bo[1] + 0.14f, bo[2] + 0.55f, 0.05f, AtlasLoot.STEEL);
        float[] h0 = sac.point(4.35f, 78, 0.02f), h1 = sac.point(4.75f, 78, 0.02f);
        b.tube(new float[][]{h0, p(h0[0] + 0.2f, h0[1] + 0.75f, h0[2] + 0.05f), p(h1[0] - 0.2f, h1[1] + 0.75f, h1[2] + 0.05f), h1}, fill(4, 0.18f), 6, SANGLE, false, false, 0, false);
        // pochoirs (nom, matricule), pièce cousue, ruban adhésif sur une déchirure, boue, auréole
        b.color(0.9f);
        sac.decal(b, S_NOM, 3.2f, 5.35f, 14, 56, 8, 3, 0.22f);
        sac.decal(b, S_MATRICULE, 3.5f, 4.55f, -8, 12, 4, 2, 0.22f);
        b.color(0.95f, 0.92f, 0.8f);
        sac.decal(b, KAKI_CLAIR, 2.3f, 2.9f, -8, 34, 2, 3, 0.12f);
        b.color(1);
        sac.decal(b, BRULURE, 6.0f, 6.7f, 2, 40, 2, 3, 0.13f);
        sac.decal(b, BOUE_1, 2.1f, 7.2f, -44, -12, 6, 2, 0.12f);
        sac.decal(b, AUREOLE, 5.9f, 7.0f, 58, 100, 2, 3, 0.18f);
        b.pop();
        // ceinturon tombé au sol devant, boucle
        b.ribbon(new float[][]{p(9.4f, 0.04f, 9.5f), p(11.2f, 0.04f, 10.2f), p(12.9f, 0.04f, 9.9f), p(14.2f, 0.04f, 8.9f)}, fill(4, 0.75f), p(0, 0, -1), SANGLE);
        b.push().translate(9.2f, 0, 9.4f).rotateY(-20);
        b.bevelBox(-0.6f, 0, -0.5f, 0.6f, 0.22f, 0.5f, 0.06f, AtlasLoot.STEEL);
        b.pop();
    }

    // =================================================================== 4. sac à dos sur armature
    /**
     * Sac à dos d'infanterie sur armature tubulaire (type ALICE), penché contre le mur : rabat, trois poches, gourde
     * accrochée, pelle pliante dans sa housse, sangle qui traîne ; toile délavée, rabat déchiré, boue.
     */
    static void sacADos(MeshBuilder b) {
        b.push().translate(7.6f, 0, 3.1f).rotateX(-14).rotateY(4);
        // armature (alu) : montants, traverses, sangle lombaire, bretelles derrière
        for (float x : new float[]{-2.7f, 2.7f}) {
            b.tube(new float[][]{p(x, 0.1f, 0.1f), p(x, 5.0f, -0.05f), p(x * 0.97f, 9.4f, 0), p(x * 0.85f, 10.0f, 0.2f)}, fill(4, 0.17f), 8, ALU, true, false, 0, false);
        }
        b.tube(new float[][]{p(-2.33f, 10.0f, 0.2f), p(0, 10.3f, 0.3f), p(2.33f, 10.0f, 0.2f)}, fill(3, 0.17f), 8, ALU, false, false, 0, false);
        for (float y : new float[]{1.2f, 6.3f}) b.tube(new float[][]{p(-2.7f, y, 0.0f), p(2.7f, y, 0.0f)}, fill(2, 0.15f), 8, ALU, false, false, 0, false);
        b.bevelBox(-2.8f, 0.7f, -0.22f, 2.8f, 2.2f, -0.04f, 0.08f, SANGLE);
        for (float x : new float[]{-1.6f, 1.6f}) b.bevelBox(x - 0.6f, 4.5f, -0.6f, x + 0.6f, 9.9f, -0.2f, 0.25f, SANGLE);
        // corps du sac, rabat, poches
        b.push().rotateZ(2);
        b.bevelBox(-3.6f, 2.6f, 0.25f, 3.6f, 10.0f, 3.7f, 1.3f, TOILE);
        b.bevelBox(-3.75f, 9.1f, 0.15f, 3.75f, 10.55f, 4.05f, 0.7f, TOILE);
        b.push().translate(0, 10.2f, 4.0f).rotateX(10);
        b.bevelBox(-3.5f, -3.4f, -0.25f, 3.5f, 0.2f, 0.25f, 0.22f, TOILE);
        b.pop();
        for (float x : new float[]{-2.35f, 0, 2.35f}) {
            b.push().translate(x, 3.0f, 3.55f).rotateX(x < 0 ? 3 : -2).rotateZ(x * 0.8f);
            b.bevelBox(-1.1f, 0, 0, 1.1f, 3.5f, 1.25f, 0.55f, TOILE);
            boue(b, 'z', 0, 0.05f, 1.25f, 1.9f, 1.2f, x > 0 ? 1 : 0);
            b.pop();
            b.bevelBox(x - 1.18f, 5.6f, 3.7f, x + 1.18f, 6.75f, 4.98f, 0.3f, TOILE);
            b.bevelBox(x - 0.18f, 4.6f, 4.86f, x + 0.18f, 6.0f, 5.0f, 0.04f, SANGLE);
            b.bevelBox(x - 0.28f, 4.55f, 4.9f, x + 0.28f, 4.9f, 5.06f, 0.04f, AtlasLoot.STEEL);
        }
        for (float x : new float[]{-1.6f, 1.6f}) {
            b.bevelBox(x - 0.2f, 6.6f, 4.18f, x + 0.2f, 7.3f, 4.36f, 0.05f, SANGLE);
            b.bevelBox(x - 0.32f, 6.95f, 4.3f, x + 0.32f, 7.35f, 4.46f, 0.05f, AtlasLoot.STEEL);
        }
        // rabat de poche gauche arraché d'un côté, bout de sangle qui pend jusqu'au sol
        b.push().translate(-3.45f, 6.75f, 4.95f).rotateZ(-28);
        b.bevelBox(0, -1.1f, -0.16f, 1.2f, 0, 0.0f, 0.06f, TOILE);
        b.pop();
        b.ribbon(new float[][]{p(1.6f, 6.6f, 4.42f), p(1.7f, 4.5f, 5.2f), p(1.95f, 2.4f, 5.4f), p(2.3f, 0.9f, 5.0f)}, fill(4, 0.38f), p(1, 0, 0), SANGLE);
        // usure : pochoir, boue, auréole
        pochoir(b, S_MATRICULE, 'z', 0, 8.4f, 4.3f, 2.8f, 0.7f);
        decal(b, AUREOLE, 'X', 3.6f, 5.0f, 2.0f, 2.4f, 2.2f);
        b.pop();
        // gourde (plastique olive, bouchon) pendue à droite par sa chaînette
        b.push().translate(4.55f, 3.4f, 2.6f).rotateZ(-4);
        b.color(0.36f, 0.4f, 0.27f);
        b.lathe(new float[][]{{0.01f, 0}, {1.0f, 0.05f}, {1.35f, 0.5f}, {1.4f, 2.4f}, {1.15f, 3.2f}, {0.5f, 3.6f}, {0.45f, 3.62f}}, 14, 0, 360, PLASTIQUE_USE, 0.6f, 1.0f, 0, 0, 0, false);
        b.color(0.2f, 0.21f, 0.18f);
        b.push().translate(0, 3.6f, 0);
        b.cylinder(0.52f, 0, 0.55f, 10, PLASTIQUE_USE, null, PLASTIQUE_USE);
        b.pop();
        b.color(1);
        b.tube(new float[][]{p(0.3f, 3.95f, 0.2f), p(-0.4f, 4.6f, 0.1f), p(-1.0f, 5.0f, 0)}, fill(3, 0.05f), 4, AtlasLoot.STEEL, false, false, 0, false);
        b.pop();
        // pelle pliante dans sa housse, accrochée à gauche, manche qui dépasse
        b.push().translate(-4.5f, 3.1f, 2.2f).rotateZ(5);
        b.bevelBox(-0.85f, 0, -1.2f, 0.85f, 4.4f, 1.2f, 0.5f, TOILE);
        b.bevelBox(-0.9f, 3.0f, -1.25f, 0.9f, 4.5f, 1.25f, 0.35f, TOILE);
        b.bevelBox(-0.2f, 1.6f, 1.15f, 0.2f, 3.4f, 1.3f, 0.04f, SANGLE);
        b.push().translate(0, 4.4f, 0);
        b.cylinder(0.32f, 0, 2.6f, 10, AtlasCivil.WALNUT, null, AtlasCivil.WALNUT);
        b.translate(0, 2.6f, 0);
        b.sphere(0.4f, 10, 6, AtlasCivil.WALNUT);
        b.pop();
        b.pop();
    }
}
