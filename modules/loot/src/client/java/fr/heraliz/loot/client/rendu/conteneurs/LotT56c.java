package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T56, T5 (fin) : optiques, poste de commandement, sac de parachute, transmissions. */
final class LotT56c {
    private LotT56c() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t5_caisse_optiques", "t5", "plastique", "Malette d'optiques", "Optics case", LotT56c::optiques);
        Conteneurs.ajouter("t5_poste_commandement", "t5", "metal", "Poste de commandement", "Command post", LotT56c::posteCommandement);
        Conteneurs.ajouter("t5_sac_parachute", "t5", "tissu", "Sac de parachute", "Parachute bag", LotT56c::sacParachute);
        Conteneurs.ajouter("t5_caisse_transmissions", "t5", "plastique", "Caisse de transmissions", "Signals equipment case", LotT56c::transmissions);
        LotT56d.enregistrer();
    }

    /** Caisse étanche en plastique grenu (teinte), coins arrondis, nervures sur l'avant. */
    static void caisseEtanche(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float cr, float cg, float cbl) {
        b.color(cr, cg, cbl);
        b.bevelBox(x0, y0, z0, x1, y1, z1, 0.55f, PLASTIQUE_GRAIN);
        for (float y = y0 + 0.9f; y < y1 - 0.8f; y += 1.3f) b.bevelBox(x0 + 0.8f, y, z1 - 0.05f, x1 - 0.8f, y + 0.45f, z1 + 0.12f, 0.12f, PLASTIQUE_GRAIN);
        b.color(1);
    }

    // =================================================================== optiques
    /**
     * Malette d'optiques étanche (50 x 38 x 18 cm) ouverte sur une caisse olive : lunette de tir dans la mousse,
     * logement des jumelles vide ; les jumelles et un cache d'objectif tombés au sol.
     */
    static void optiques(MeshBuilder b) {
        float bx0 = 2.4f, bx1 = 12.4f, bz0 = 0.7f, bz1 = 8.7f, bh = 6.2f;
        caisseEtanche(b, bx0, 0.35f, bz0, bx1, bh, bz1, 0.42f, 0.45f, 0.34f);
        for (float x : new float[]{3.6f, 11.2f}) b.bevelBox(x - 0.7f, 0, bz0 + 0.6f, x + 0.7f, 0.38f, bz1 - 0.6f, 0.1f, CAOUTCHOUC_USE);
        Kit.pochoir(b, S_OPTIQUE, 'z', 7.4f, 1.3f, bz1 + 0.12f, 5.0f, 1.25f);
        Kit.pochoir(b, S_UN_BLANC, 'X', bx1, 3.4f, (bz0 + bz1) / 2, 3.0f, 1.5f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'x', bx0, 3.4f, 4.0f, 2.0f, 1.0f);
        Kit.poigneeU(b, bx1 + 0.08f, 4.6f, (bz0 + bz1) / 2, 2.6f, 1, POIGNEE_NOIRE);
        Kit.poigneeU(b, bx0 - 0.08f, 4.6f, (bz0 + bz1) / 2, 2.6f, -1, POIGNEE_NOIRE);
        // malette noire ouverte posée dessus
        float x0 = 3.0f, x1 = 11.2f, z0 = 2.3f, z1 = 8.3f, y0 = bh + 0.02f, yc = y0 + 2.1f;
        b.color(0.27f, 0.28f, 0.27f);
        b.bevelBox(x0, y0, z0, x0 + 0.3f, yc, z1, 0.1f, PLASTIQUE_GRAIN);
        b.bevelBox(x1 - 0.3f, y0, z0, x1, yc, z1, 0.1f, PLASTIQUE_GRAIN);
        b.bevelBox(x0 + 0.28f, y0, z0, x1 - 0.28f, yc, z0 + 0.3f, 0.1f, PLASTIQUE_GRAIN);
        b.bevelBox(x0 + 0.28f, y0, z1 - 0.3f, x1 - 0.28f, yc, z1, 0.1f, PLASTIQUE_GRAIN);
        b.bevelBox(x0 + 0.28f, y0, z0 + 0.28f, x1 - 0.28f, y0 + 0.4f, z1 - 0.28f, 0.05f, PLASTIQUE_GRAIN);
        b.color(1);
        float yf = yc - 0.3f;
        b.box(x0 + 0.3f, y0 + 0.4f, z0 + 0.3f, x1 - 0.3f, yf, z1 - 0.3f, AtlasLoot.FOAM);
        Kit.pochoir(b, MOUSSE_DECOUPE, 'y', (x0 + x1) / 2, yf, (z0 + z1) / 2, x1 - x0 - 0.6f, z1 - z0 - 0.6f);
        // lunette dans sa découpe (texture : rangée du haut = arrière)
        float lz = z0 + 0.3f + (z1 - z0 - 0.6f) * 0.21f, lx0 = x0 + 0.3f + (x1 - x0 - 0.6f) * 0.1f, lx1 = x0 + 0.3f + (x1 - x0 - 0.6f) * 0.9f;
        b.push().translate(lx0, yf + 0.05f, lz).rotateZ(-90);
        b.lathe(new float[][]{{0.01f, 0}, {0.36f, 0}, {0.36f, 0.9f}, {0.26f, 1.3f}, {0.24f, (lx1 - lx0) - 1.9f}, {0.42f, (lx1 - lx0) - 1.1f}, {0.42f, lx1 - lx0}, {0.01f, lx1 - lx0}},
                12, 0, 360, AtlasLoot.BLACK_STEEL, 1, 1, 0, 0, 0, false);
        b.pop();
        b.bevelBox((lx0 + lx1) / 2 - 0.55f, yf + 0.1f, lz - 0.35f, (lx0 + lx1) / 2 + 0.55f, yf + 0.65f, lz + 0.35f, 0.08f, AtlasLoot.BLACK_STEEL);
        b.push().translate((lx0 + lx1) / 2, yf + 0.65f, lz);
        b.cylinder(0.22f, 0, 0.3f, 8, AtlasLoot.DARK, null, AtlasLoot.DARK);
        b.pop();
        // couvercle ouvert contre le mur, mousse alvéolée, fermoirs ouverts, poignée
        b.push().translate(0, yc, z0).rotateX(-100).translate(0, -yc, -z0);
        b.color(0.27f, 0.28f, 0.27f);
        b.bevelBox(x0 - 0.04f, yc + 0.02f, z0 - 0.04f, x1 + 0.04f, yc + 0.9f, z1 + 0.04f, 0.3f, PLASTIQUE_GRAIN);
        b.color(1);
        b.box(x0 + 0.35f, yc - 0.45f, z0 + 0.35f, x1 - 0.35f, yc + 0.04f, z1 - 0.35f, AtlasLoot.FOAM);
        b.pop();
        b.color(0.27f, 0.28f, 0.27f);
        for (float x : new float[]{4.4f, 9.8f}) fermoirOuvert(b, x, yc, z1, x < 5 ? 25 : 6, PLASTIQUE_GRAIN);
        b.bevelBox(6.2f, y0 + 0.6f, z1 + 0.05f, 6.6f, y0 + 1.5f, z1 + 0.5f, 0.1f, PLASTIQUE_GRAIN);
        b.bevelBox(7.6f, y0 + 0.6f, z1 + 0.05f, 8.0f, y0 + 1.5f, z1 + 0.5f, 0.1f, PLASTIQUE_GRAIN);
        b.tube(new float[][]{p(6.4f, y0 + 1.3f, z1 + 0.4f), p(6.6f, y0 + 1.2f, z1 + 1.1f), p(7.6f, y0 + 1.2f, z1 + 1.1f), p(7.8f, y0 + 1.3f, z1 + 0.4f)}, fill(4, 0.22f), 8, PLASTIQUE_GRAIN, false, false, 0, false);
        b.color(1);
        // jumelles tombées au sol, cache d'objectif, sangle
        b.push().translate(11.6f, 0, 12.4f).rotateY(-28);
        for (float zz : new float[]{-0.75f, 0.75f}) {
            b.push().translate(-1.6f, 0.62f, zz).rotateZ(-90);
            b.lathe(new float[][]{{0.01f, 0}, {0.5f, 0}, {0.55f, 0.4f}, {0.55f, 1.9f}, {0.62f, 2.4f}, {0.62f, 3.2f}, {0.01f, 3.2f}}, 12, 0, 360, AtlasLoot.RUBBER, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        b.bevelBox(-0.9f, 0.4f, -0.4f, 0.6f, 1.0f, 0.4f, 0.1f, AtlasLoot.RUBBER);
        b.pop();
        b.push().translate(8.4f, 0, 13.8f);
        b.cylinder(0.62f, 0, 0.24f, 12, AtlasLoot.RUBBER, AtlasLoot.RUBBER, AtlasLoot.RUBBER);
        b.pop();
        b.color(0.3f, 0.3f, 0.28f);
        bande(b, new float[][]{p(9.6f, 0.04f, 12.6f), p(8.6f, 0.04f, 11.2f), p(6.8f, 0.04f, 11.4f), p(5.8f, 0.04f, 12.8f)}, new float[][]{HAUT}, 0.6f, 0.06f, SANGLE);
        b.color(1);
        // usure
        boue(b, 'x', bx0, 0.35f, (bz0 + bz1) / 2, 7.0f, 1.4f, 1);
        decal(b, GRIFFURES, 'X', bx1, 2.0f, 6.4f, 2.6f, 1.2f, 4);
        decal(b, AUREOLE, 'y', 4.6f, bh, 1.6f, 2.2f, 1.6f, 0);
        jonchee(b, 1.0f, 9.4f, 15.0f, 15.6f, 6, FEUILLES, 1.0f, 621);
    }

    // =================================================================== poste de commandement
    /**
     * Poste de commandement abandonné (table pliante 1,2 x 0,6 m) sur deux blocs : ordinateur durci ouvert (écran
     * éteint, fêlé), carte d'état-major annotée, combiné radio et son cordon, tasse renversée ; caisse ONU dessous,
     * chaise pliante renversée à droite.
     */
    static void posteCommandement(MeshBuilder b) {
        float x0 = 5.6f, x1 = 24.8f, z0 = 0.8f, z1 = 10.4f, yt = 11.2f, ep = 0.4f;
        // pieds en U à chaque bout, traverse
        for (float x : new float[]{x0 + 1.0f, x1 - 1.0f}) {
            for (float z : new float[]{z0 + 0.9f, z1 - 0.9f}) {
                b.push().translate(x, 0, z);
                b.cylinder(0.24f, 0.0f, yt - 0.02f, 8, ACIER_NOIR, AtlasLoot.RUBBER, null);
                b.pop();
            }
            b.push().translate(x, 1.4f, z0 + 0.9f).rotateX(90);
            b.cylinder(0.2f, 0, z1 - z0 - 1.8f, 8, ACIER_NOIR, null, null);
            b.pop();
        }
        b.color(0.5f, 0.52f, 0.44f);
        b.bevelBox(x0, yt - ep, z0, x1, yt, z1, 0.12f, PLASTIQUE_GRAIN);
        b.color(1);
        b.bevelBox(x0 - 0.05f, yt - ep - 0.05f, z1 - 0.1f, x1 + 0.05f, yt - 0.06f, z1 + 0.05f, 0.04f, ALU);
        // carte annotée (un coin relevé), papiers
        decal(b, CARTE, 'y', 10.4f, yt, 5.2f, 8.4f, 7.4f, 6);
        b.push().translate(13.8f, yt + 0.06f, 8.2f).rotateY(6).rotateX(-28);
        b.decalZ(CARTE.sub(0.8f, 0.8f, 1, 1), 0, 0.6f, 0.02f, 1.6f, 1.4f);
        b.pop();
        decal(b, PAPIER, 'y', 22.8f, yt, 8.0f, 2.6f, 3.4f, -14);
        decal(b, PAPIER, 'y', 22.2f, yt + 0.03f, 7.4f, 2.6f, 3.4f, 9);
        // ordinateur durci ouvert
        float lx = 17.6f, lz = 4.6f;
        b.push().translate(lx, yt, lz).rotateY(-8);
        b.color(0.36f, 0.38f, 0.33f);
        b.bevelBox(-2.4f, 0, -1.9f, 2.4f, 0.65f, 1.9f, 0.18f, PLASTIQUE_GRAIN);
        b.color(1);
        b.push().translate(0, 0.65f, 0).rotateX(-90);
        b.decalZ(CLAVIER_PC, 0, 0.2f, 0.05f, 4.2f, 3.0f);
        b.pop();
        b.push().translate(0, 0.65f, -1.9f).rotateX(-102);
        b.color(0.36f, 0.38f, 0.33f);
        b.bevelBox(-2.4f, -0.45f, 0, 2.4f, 0, 3.8f, 0.15f, PLASTIQUE_GRAIN);
        b.color(1);
        b.push().translate(0, -0.45f, 1.9f).rotateX(90);
        b.decalZ(ECRAN_OFF, 0, 0, 0.06f, 4.0f, 3.2f);
        b.pop();
        b.pop();
        b.bevelBox(-1.0f, 0.15f, 1.85f, 1.0f, 0.5f, 2.25f, 0.1f, POIGNEE_NOIRE);
        b.pop();
        // combiné radio et cordon spiralé, tasse renversée
        b.push().translate(8.0f, yt + 0.3f, 8.8f).rotateY(30);
        b.bevelBox(-1.4f, -0.22f, -0.4f, 1.4f, 0.3f, 0.4f, 0.2f, AtlasLoot.DARK);
        b.bevelBox(-1.6f, -0.3f, -0.5f, -0.8f, 0.4f, 0.5f, 0.2f, AtlasLoot.DARK);
        b.bevelBox(0.8f, -0.3f, -0.5f, 1.6f, 0.4f, 0.5f, 0.2f, AtlasLoot.DARK);
        b.pop();
        spirale(b, p(7.0f, yt + 0.3f, 9.4f), p(6.8f, yt + 0.3f, 10.75f), 5, 0.24f, 0.07f);
        spirale(b, p(6.8f, yt + 0.2f, 10.9f), p(6.3f, yt - 4.6f, 11.2f), 12, 0.24f, 0.07f);
        b.push().translate(21.0f, yt + 0.62f, 3.4f).rotateY(40).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -0.8f}, {0.6f, -0.8f}, {0.62f, 0.8f}, {0.52f, 0.8f}, {0.5f, -0.6f}}, 10, 0, 360, BLANC_ONU, 1, 1, 0, 0, 0, false);
        b.pop();
        decal(b, AUREOLE, 'y', 22.6f, yt, 4.6f, 2.0f, 1.6f, 0);
        // caisse ONU sous la table, chaise pliante renversée
        b.bevelBox(9.0f, 0, 1.4f, 17.0f, 5.2f, 6.6f, 0.2f, BLANC_ONU);
        Kit.pochoir(b, S_UN_NOIR, 'z', 11.6f, 2.8f, 6.6f, 3.0f, 1.5f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 15.2f, 2.4f, 6.6f, 1.6f, 0.8f);
        coulure(b, 'z', 16.2f, 4.8f, 6.6f, 0.9f, 1);
        // chaise pliante repliée, tombée à plat
        b.push().translate(27.4f, 0, 8.6f).rotateY(-24);
        for (float z : new float[]{-2.1f, 2.1f}) {
            b.tube(new float[][]{p(-3.8f, 0.22f, z), p(3.8f, 0.22f, z)}, fill(2, 0.22f), 8, ACIER_NOIR, true, true, 0, false);
            b.tube(new float[][]{p(-3.2f, 0.66f, z * 0.92f), p(3.4f, 0.66f, z * 0.92f)}, fill(2, 0.2f), 8, ACIER_NOIR, true, true, 0, false);
        }
        b.color(0.5f, 0.52f, 0.44f);
        b.bevelBox(-3.6f, 0.42f, -2.2f, -0.4f, 0.74f, 2.2f, 0.1f, PLASTIQUE_GRAIN);
        b.bevelBox(0.2f, 0.86f, -1.9f, 3.4f, 1.16f, 1.9f, 0.1f, PLASTIQUE_GRAIN);
        b.color(1);
        b.pop();
        // usure : boue au pied, feuilles, papiers au sol
        jonchee(b, 4.0f, 11.0f, 31.0f, 15.6f, 9, FEUILLES, 1.0f, 631);
        jonchee(b, 18.0f, 10.8f, 24.0f, 14.6f, 3, PAPIER, 1.6f, 632);
        decal(b, BOUE_1, 'y', 14.0f, 0, 12.0f, 6.0f, 2.4f, 0);
    }

    // =================================================================== sac de parachute
    /**
     * Sac de parachute (olive) affaissé contre le mur, voilure qui dépasse, harnais aux sangles emmêlées et boucles,
     * casque bleu ONU posé devant, suspentes en vrac.
     */
    static void sacParachute(MeshBuilder b) {
        // sac de déploiement couché contre le mur
        b.push().translate(7.0f, 2.45f, 3.4f).rotateY(4).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -5.6f}, {1.6f, -5.4f}, {2.4f, -4.6f}, {2.7f, -2.0f}, {2.7f, 2.4f}, {2.4f, 4.6f}, {1.5f, 5.4f}, {0.01f, 5.6f}},
                20, 0, 360, NYLON_PARA, 0.85f, 1.12f, 0.05f, 0.4f, 11, false);
        b.pop();
        // voilure qui déborde du sac (masse de toile), suspentes
        b.push().translate(12.4f, 0, 5.0f).scale(1.0f, 1.0f, 1.2f);
        b.lathe(new float[][]{{0.01f, 3.6f}, {1.1f, 3.4f}, {1.9f, 2.4f}, {2.4f, 1.0f}, {2.7f, 0.3f}, {2.8f, 0.02f}}, 14, 0, 360, NYLON_PARA, 1, 1, 0.16f, 0.6f, 12, false);
        b.pop();
        for (int k = 0; k < 4; k++) {
            float[][] ch = auSol(lisse(new float[][]{p(10.6f, 3.0f - k * 0.3f, 5.0f + k * 0.4f), p(9.6f - k * 0.5f, 1.2f, 8.0f + k * 0.5f), p(8.0f - k * 0.8f, 0.08f, 9.2f + k * 0.4f), p(5.0f - k * 0.4f, 0.08f, 8.8f + k * 0.8f)}, 3), 0.07f);
            cable(b, ch, 0.05f, 0.82f, 0.8f, 0.7f);
        }
        // harnais : sangles qui pendent du sac jusqu'au sol, boucles
        b.color(0.36f, 0.38f, 0.28f);
        bande(b, new float[][]{p(3.2f, 4.6f, 3.6f), p(3.4f, 3.6f, 5.6f), p(3.6f, 1.6f, 6.4f), p(4.4f, 0.05f, 8.0f), p(6.6f, 0.05f, 10.6f), p(9.6f, 0.05f, 11.0f)},
                new float[][]{HAUT, {0, 0.5f, 0.85f}, AVANT, HAUT, HAUT, HAUT}, 1.0f, 0.08f, SANGLE);
        bande(b, new float[][]{p(5.6f, 4.9f, 3.0f), p(6.2f, 4.0f, 5.4f), p(6.6f, 2.2f, 6.2f), p(7.4f, 0.05f, 7.6f), p(9.0f, 0.05f, 8.4f)},
                new float[][]{HAUT, {0, 0.5f, 0.85f}, AVANT, HAUT, HAUT}, 1.0f, 0.08f, SANGLE);
        b.color(1);
        for (float[] q : new float[][]{{4.4f, 0.12f, 8.0f, 40}, {7.2f, 0.12f, 7.5f, -20}, {3.5f, 2.6f, 6.3f, 0}}) {
            b.push().translate(q[0], q[1], q[2]).rotateY(q[3]);
            b.bevelBox(-0.75f, -0.08f, -0.45f, 0.75f, 0.12f, 0.45f, 0.06f, METAL_NU);
            b.pop();
        }
        // casque bleu ONU posé de travers devant
        b.push().translate(11.6f, 0, 11.4f).rotateY(-30);
        b.translate(0, 0.2f, 0);
        b.push().scale(1.0f, 0.95f, 1.15f);
        b.sphereSection(2.4f, 18, 8, 0, 90, BLEU_ONU);
        b.pop();
        b.push().scale(1.06f, 1, 1.22f);
        b.torus(2.3f, 0.2f, 18, 4, 0, 360, BLEU_ONU);
        b.pop();
        b.push().translate(0, 1.0f, 2.44f).rotateX(-22);
        b.decalZ(S_UN_BLANC, 0, 0, 0.16f, 1.4f, 0.7f);
        b.pop();
        b.color(0.3f, 0.3f, 0.26f);
        bande(b, new float[][]{p(-2.0f, -0.1f, 0.6f), p(-1.0f, -0.15f, 2.2f), p(1.0f, -0.15f, 2.2f), p(2.0f, -0.1f, 0.6f)}, new float[][]{HAUT}, 0.4f, 0.05f, SANGLE);
        b.color(1);
        b.pop();
        // usure : boue, feuilles, griffure de rouille sur la boucle
        decal(b, BOUE_2, 'y', 4.0f, 0, 13.4f, 5.0f, 2.0f, 0);
        jonchee(b, 0.5f, 6.0f, 15.5f, 15.6f, 7, FEUILLES, 1.0f, 641);
    }

    // =================================================================== transmissions
    /**
     * Malle de radio-relais (60 x 50 x 45 cm) olive, capot avant retiré et appuyé à droite : deux tiroirs radio,
     * câbles branchés qui pendent jusqu'au sol, combiné décroché, antenne fouet repliée sur le dessus, rouleau de câble.
     */
    static void transmissions(MeshBuilder b) {
        float x0 = 1.2f, x1 = 10.8f, z0 = 0.6f, z1 = 8.6f, y0 = 0.5f, y1 = 7.7f, e = 0.45f;
        for (float x : new float[]{2.4f, 9.6f}) {
            for (float z : new float[]{1.6f, 7.6f}) {
                b.push().translate(x, 0, z);
                b.cylinder(0.5f, 0, y0 + 0.03f, 10, CAOUTCHOUC_USE, CAOUTCHOUC_USE, null);
                b.pop();
            }
        }
        b.color(0.42f, 0.45f, 0.33f);
        b.bevelBox(x0, y0, z0, x0 + e, y1, z1, 0.35f, PLASTIQUE_GRAIN);
        b.bevelBox(x1 - e, y0, z0, x1, y1, z1, 0.35f, PLASTIQUE_GRAIN);
        b.bevelBox(x0 + e - 0.05f, y0, z0, x1 - e + 0.05f, y0 + e, z1, 0.15f, PLASTIQUE_GRAIN);
        b.bevelBox(x0 + e - 0.05f, y1 - e, z0, x1 - e + 0.05f, y1, z1, 0.15f, PLASTIQUE_GRAIN);
        b.bevelBox(x0 + e - 0.05f, y0 + e - 0.05f, z0, x1 - e + 0.05f, y1 - e + 0.05f, z0 + 0.4f, 0.1f, PLASTIQUE_GRAIN);
        for (float y : new float[]{y0 + 0.2f, y1 - 1.1f}) b.bevelBox(x0 - 0.1f, y, z1 - 0.7f, x1 + 0.1f, y + 0.9f, z1 + 0.1f, 0.2f, PLASTIQUE_GRAIN);
        b.color(1);
        // rails et deux tiroirs radio (façades)
        float zf = z1 - 0.9f;
        b.bevelBox(x0 + e, y0 + e + 0.02f, 1.2f, x1 - e, 3.6f, zf, 0.1f, AtlasLoot.OD_DARK);
        Kit.pochoir(b, PANNEAU_RADIO, 'z', (x0 + x1) / 2, 2.27f, zf, x1 - x0 - 2 * e - 0.2f, 2.5f);
        b.bevelBox(x0 + e, 3.7f, 1.2f, x1 - e, 6.5f, zf - 0.3f, 0.1f, AtlasLoot.OD_DARK);
        Kit.pochoir(b, PANNEAU_RADIO.sub(0, 0, 1, 0.6f), 'z', (x0 + x1) / 2, 5.1f, zf - 0.3f, x1 - x0 - 2 * e - 0.2f, 2.6f);
        for (float x : new float[]{x0 + e + 0.35f, x1 - e - 0.35f}) {
            for (float y : new float[]{2.2f, 5.1f}) b.bevelBox(x - 0.25f, y - 0.7f, zf - 0.02f - (y > 4 ? 0.3f : 0), x + 0.25f, y + 0.7f, zf + 0.5f - (y > 4 ? 0.3f : 0), 0.1f, METAL_NU);
        }
        // câbles branchés qui pendent jusqu'au sol, rouleau
        cable(b, auSol(lisse(new float[][]{p(3.5f, 1.5f, zf + 0.1f), p(3.6f, 1.2f, zf + 1.4f), p(3.9f, 0.16f, z1 + 2.2f), p(5.6f, 0.16f, 12.4f), p(8.4f, 0.16f, 12.2f)}, 4), 0.15f), 0.14f, 0.2f, 0.2f, 0.2f);
        cable(b, auSol(lisse(new float[][]{p(6.6f, 1.5f, zf + 0.1f), p(6.8f, 0.9f, zf + 1.6f), p(7.6f, 0.14f, z1 + 1.6f), p(10.4f, 0.14f, 11.0f)}, 4), 0.13f), 0.12f, 0.42f, 0.45f, 0.3f);
        for (int t = 0; t < 4; t++) {
            b.push().translate(10.8f, 0.14f + t * 0.24f, 12.6f).scale(1.0f, 1, 0.8f);
            b.torus(1.6f - t * 0.05f, 0.13f, 16, 5, 0, 360, CABLE);
            b.pop();
        }
        // combiné décroché qui pend par son cordon
        b.push().translate(x1 + 0.6f, 3.0f, 6.0f).rotateZ(12);
        b.bevelBox(-0.35f, -1.4f, -0.4f, 0.35f, 1.4f, 0.4f, 0.2f, AtlasLoot.DARK);
        b.pop();
        float[][] spire = new float[20][];
        for (int k = 0; k < 20; k++) {
            double a = k * 1.2;
            float t = k / 19f;
            spire[k] = p(x1 + 0.2f + 0.2f * (float) Math.cos(a), 6.2f - t * 1.8f, 6.3f + 0.2f * (float) Math.sin(a));
        }
        b.tube(spire, fill(20, 0.06f), 4, AtlasLoot.DARK, false, false, 0, false);
        // antenne fouet repliée sur le dessus, embase
        b.push().translate(3.0f, y1, 3.0f);
        b.cylinder(0.35f, 0, 0.5f, 10, METAL_NU, null, METAL_NU);
        b.pop();
        b.tube(new float[][]{p(3.0f, y1 + 0.55f, 3.0f), p(5.0f, y1 + 0.25f, 3.4f), p(9.6f, y1 + 0.14f, 4.4f)}, fill(3, 0.07f), 5, AtlasLoot.DARK, false, true, 0, false);
        // capot avant retiré, appuyé contre la malle à droite
        b.push().translate(14.4f, 0.36f, 4.6f).rotateZ(30);
        b.color(0.42f, 0.45f, 0.33f);
        b.bevelBox(-0.7f, 0, -3.9f, 0, 7.0f, 3.9f, 0.3f, PLASTIQUE_GRAIN);
        b.color(1);
        b.push().translate(0, 3.4f, 0).rotateY(90);
        b.decalZ(S_TRANSMISSIONS, 0, 0, 0.06f, 6.0f, 1.5f);
        b.pop();
        b.pop();
        Kit.pochoir(b, S_UN_BLANC, 'x', x0, 4.0f, 4.6f, 3.2f, 1.6f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'y', 7.6f, y1, 6.6f, 2.0f, 1.0f);
        decal(b, GRIFFURES, 'x', x0, 2.2f, 4.0f, 2.8f, 1.2f, -4);
        boue(b, 'x', x0, y0, 4.6f, 7.0f, 1.4f, 0);
        decal(b, AUREOLE, 'y', 5.0f, y1, 3.6f, 2.4f, 2.0f, 0);
        jonchee(b, 0.5f, 9.4f, 15.0f, 15.6f, 5, FEUILLES, 1.0f, 651);
    }
}
