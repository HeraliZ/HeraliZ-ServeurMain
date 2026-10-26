package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT4bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Lot T4b, fin : caisse de munitions en bois, gilets pare-balles, fûts de carburant, paquetage de tente. */
final class LotT4b4 {
    private LotT4b4() {
    }

    static void enregistrer() {
        Conteneurs.ajouter("t4_caisse_munitions_bois", "t4", "bois", "Caisse de munitions en bois", "Wooden ammo crate", LotT4b4::caisseMunitionsBois);
        Conteneurs.ajouter("t4_gilets_pare_balles", "t4", "tissu", "Gilets pare-balles", "Body armor vests", LotT4b4::gilets);
        Conteneurs.ajouter("t4_futs", "t4", "metal", "Fûts de carburant", "Fuel drums", LotT4b4::futs);
        Conteneurs.collision("t4_futs", new float[]{0.4f, 0, 0.2f, 10.2f, 15.9f, 9.8f}, new float[]{10.8f, 0, 0.2f, 25.4f, 9.9f, 9.8f});
        Conteneurs.ajouter("t4_paquetage_tente", "t4", "tissu", "Paquetage de tente", "Tent pack", LotT4b4::paquetageTente);
    }

    // =================================================================== 13. caisse de munitions en bois
    /** Caisse en bois brut à charnières de fil (48 x 39 cm), corps de (x0, y0, z0) à (x1, y1, z1), sans couvercle. */
    static void corpsCaisse(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float e) {
        b.bevelBox(x0, y0, z1 - e, x1, y1, z1, 0.08f, BOIS_KAKI);
        b.bevelBox(x0, y0, z0, x1, y1, z0 + e, 0.08f, BOIS_KAKI);
        b.bevelBox(x0, y0, z0 + e - 0.02f, x0 + e, y1, z1 - e + 0.02f, 0.08f, BOIS_KAKI);
        b.bevelBox(x1 - e, y0, z0 + e - 0.02f, x1, y1, z1 - e + 0.02f, 0.08f, BOIS_KAKI);
        b.box6(x0 + e, y0, z0 + e, x1 - e, y0 + 0.35f, z1 - e, null, null, null, BOIS_KAKI, null, null);
        // taquets de bout (poignées), loqueteaux à papillon
        for (float x : new float[]{x0 - 0.3f, x1}) b.bevelBox(x, y0 + 0.5f, (z0 + z1) / 2 - 1.3f, x + 0.32f, y1 - 0.35f, (z0 + z1) / 2 + 1.3f, 0.1f, BOIS_KAKI);
        for (float x : new float[]{x0 + 1.6f, x1 - 1.6f}) {
            b.bevelBox(x - 0.35f, y1 - 1.0f, z1 - 0.02f, x + 0.35f, y1 - 0.1f, z1 + 0.14f, 0.04f, AtlasLoot.STEEL);
            b.push().translate(x, y1 - 0.55f, z1 + 0.14f).rotateX(90);
            b.cylinder(0.12f, 0, 0.3f, 6, AtlasLoot.STEEL, null, AtlasLoot.STEEL);
            b.pop();
        }
        pochoir(b, S_762, 'z', (x0 + x1) / 2, y0 + (y1 - y0) * 0.68f, z1, 4.0f, 0.7f);
        pochoir(b, S_CART, 'z', (x0 + x1) / 2, y0 + (y1 - y0) * 0.27f, z1, 3.0f, 0.55f);
    }

    /** Boîte de munitions scellée (zinc peint gris-vert, bande d'arrachage), coin bas-arrière-gauche en (0, 0, 0). */
    static void boiteZinc(MeshBuilder b, float L, float W, float H) {
        b.color(0.72f, 0.78f, 0.68f);
        b.bevelBox(0, 0, 0, L, H, W, 0.1f, METAL_NU);
        b.bevelBox(-0.05f, H - 0.32f, -0.05f, L + 0.05f, H - 0.12f, W + 0.05f, 0.04f, METAL_NU);
        b.bevelBox(0.35f, H - 0.02f, 0.35f, L - 0.35f, H + 0.06f, 0.6f, 0.02f, METAL_NU);
        b.color(1);
        b.bevelBox(L - 0.9f, H - 0.07f, 0.25f, L - 0.35f, H + 0.12f, 0.75f, 0.03f, AtlasLoot.STEEL);
        pochoir(b, S_762, 'y', L / 2, H, W / 2 + 0.2f, 3.6f, 0.75f);
    }

    /**
     * Caisses de munitions en bois brut de type soviétique : une fermée au sol, une ouverte dessus (couvercle relevé
     * contre le mur), deux boîtes métalliques scellées dedans, l'ouvre-boîte posé dessus. Pochoirs noirs, boue, traces.
     */
    static void caisseMunitionsBois(MeshBuilder b) {
        // caisse du bas, fermée
        float x0 = 3.6f, x1 = 11.4f, z0 = 0.6f, z1 = 6.8f, h = 2.6f;
        corpsCaisse(b, x0, 0, z0, x1, h - 0.45f, z1, 0.4f);
        b.bevelBox(x0 - 0.04f, h - 0.43f, z0, x1 + 0.04f, h, z1 + 0.05f, 0.08f, BOIS_KAKI);
        boue(b, 'z', 7.5f, 0, z1, 7.4f, 1.2f, 0);
        decal(b, GRIFFURES, 'z', 7.5f, 1.1f, z1 + 0.1f, 1.6f, 0.6f, 8);
        // caisse du haut, ouverte, un peu de travers
        float y0 = h + 0.02f, H = 2.5f, ox0 = 3.4f, ox1 = 11.2f, oz0 = 1.25f, oz1 = 7.45f;
        b.push().translate(7.3f, 0, 4.3f).rotateY(5).translate(-7.3f, 0, -4.3f);
        corpsCaisse(b, ox0, y0, oz0, ox1, y0 + H, oz1, 0.4f);
        b.bevelBox(ox0 + 0.45f, y0 + 0.37f, oz0 + 0.45f, ox0 + 0.47f + 6.9f, y0 + 0.4f, oz1 - 0.45f, 0.01f, PAPIER);
        b.push().translate(ox0 + 0.5f, y0 + 0.41f, oz0 + 0.5f);
        boiteZinc(b, 6.8f, 2.55f, 1.75f);
        b.translate(0, 0, 2.7f);
        boiteZinc(b, 6.8f, 2.55f, 1.75f);
        b.pop();
        // ouvre-boîte posé sur les boîtes
        b.push().translate(6.6f, y0 + 0.41f + 1.75f + 0.2f, 4.9f).rotateY(-28).rotateZ(3);
        b.bevelBox(-1.3f, 0, -0.18f, 1.0f, 0.1f, 0.18f, 0.03f, AtlasLoot.STEEL);
        b.push().translate(1.0f, 0.1f, 0).rotateX(90);
        b.prism(new float[][]{{0, -0.18f}, {0.6f, -0.05f}, {0.75f, 0.25f}, {0, 0.18f}}, 0.1f, AtlasLoot.STEEL, AtlasLoot.STEEL);
        b.pop();
        b.push().translate(-1.3f, 0.05f, 0).rotateZ(90);
        b.torus(0.32f, 0.06f, 10, 3, 0, 360, AtlasLoot.STEEL);
        b.pop();
        b.pop();
        // couvercle relevé (charnières de fil)
        float lt = 0.45f;
        b.push().translate(0, y0 + H, oz0).rotateX(-92).translate(0, -(y0 + H), -oz0);
        b.bevelBox(ox0 - 0.04f, y0 + H + 0.02f, oz0, ox1 + 0.04f, y0 + H + lt, oz1 + 0.05f, 0.08f, BOIS_KAKI);
        b.bevelBox(ox0 + 0.6f, y0 + H - 0.2f, oz0 + 0.5f, ox0 + 1.3f, y0 + H + 0.04f, oz1 - 0.5f, 0.05f, BOIS_KAKI);
        b.bevelBox(ox1 - 1.3f, y0 + H - 0.2f, oz0 + 0.5f, ox1 - 0.6f, y0 + H + 0.04f, oz1 - 0.5f, 0.05f, BOIS_KAKI);
        b.push().translate(7.3f, y0 + H - 0.05f, (oz0 + oz1) / 2).rotateX(90);
        b.decalZ(S_LOT, 0, 0, 0, 4.2f, 0.6f);
        b.pop();
        b.pop();
        for (float x : new float[]{ox0 + 1.4f, ox1 - 1.4f}) {
            b.push().translate(x - 0.6f, y0 + H + 0.05f, oz0 - 0.05f).rotateZ(-90);
            b.torus(0.12f, 0.04f, 6, 3, 0, 360, AtlasLoot.STEEL);
            b.pop();
        }
        coulure(b, 'z', ox0 + 3.0f, y0 + H - 0.2f, oz1, 0.6f, 1);
        b.pop();
    }

    // =================================================================== 14. gilets pare-balles
    /** Gilet porte-plaques posé à plat (50 x 34 cm, encolure vers -Z) : panneau, épaulières, bandes MOLLE, poches. */
    static void gilet(MeshBuilder b, boolean poches) {
        float w = 4.0f, d = 2.7f, e = 0.85f;
        b.bevelBox(-w, 0, -d, w, e, d, 0.4f, CAMO_CE);
        // encolure (creux sombre), épaulières qui passent derrière
        b.color(0.5f);
        b.bevelBox(-1.4f, e - 0.03f, -d - 0.02f, 1.4f, e + 0.07f, -d + 1.0f, 0.02f, CAMO_CE);
        b.color(1);
        for (float x : new float[]{-2.7f, 2.7f}) {
            b.bevelBox(x - 0.75f, e - 0.05f, -d - 0.9f, x + 0.75f, e + 0.25f, -d + 0.8f, 0.12f, CAMO_CE);
            b.push().translate(x, e * 0.5f, -d - 0.9f).rotateZ(90);
            b.torus(0.55f, 0.14f, 10, 3, 90, 270, CAMO_CE);
            b.pop();
        }
        // bandes MOLLE
        for (int k = 0; k < 4; k++) {
            float z = -0.9f + k * 0.62f;
            b.bevelBox(-w + 0.4f, e - 0.06f, z, w - 0.4f, e + 0.06f, z + 0.28f, 0.02f, SANGLE);
        }
        // ceinture abdominale qui dépasse des côtés
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * w, 0.2f, 1.2f).rotateZ(s * -8);
            b.bevelBox(s > 0 ? -0.2f : -1.6f, 0, -0.8f, s > 0 ? 1.6f : 0.2f, 0.4f, 0.8f, 0.15f, CAMO_CE);
            b.pop();
        }
        if (poches) {
            for (int k = 0; k < 3; k++) {
                b.push().translate(-2.4f + k * 1.6f, e, 1.15f).rotateY(k == 1 ? 4 : -3);
                b.bevelBox(-0.7f, 0, -1.0f, 0.7f, 1.0f, 1.0f, 0.22f, CAMO_CE);
                b.bevelBox(-0.75f, 0.85f, -1.05f, 0.75f, 1.12f, 0.6f, 0.1f, CAMO_CE);
                b.bevelBox(-0.15f, 0.95f, 0.55f, 0.15f, 1.15f, 1.06f, 0.03f, SANGLE);
                b.pop();
            }
            b.bevelBox(-3.2f, e + 0.05f, -2.2f, -1.2f, e + 0.12f, -1.5f, 0.02f, AtlasLoot.WEBBING);
            pochoir(b, S_NOM, 'y', -2.2f, e + 0.12f, -1.85f, 2.0f, 0.5f);
        }
    }

    /** Casque de combat (couvre-casque camouflé, bande, jugulaire) posé sur son bord en (0, 0, 0). */
    static void casque(MeshBuilder b) {
        b.push().translate(0, 0.16f, 0).scale(1.0f, 0.95f, 1.12f);
        b.sphereSection(1.95f, 18, 7, 0, 90, CAMO_CE);
        b.torus(1.95f, 0.16f, 18, 4, 0, 360, AtlasLoot.OD_DARK);
        b.push().translate(0, 0.75f, 0);
        b.torus(1.84f, 0.1f, 18, 3, 0, 360, SANGLE);
        b.pop();
        b.pop();
        b.tube(new float[][]{p(-1.9f, 0.2f, 0.3f), p(-1.6f, 0.06f, 1.8f), p(0, 0.05f, 2.7f), p(1.5f, 0.06f, 1.9f), p(1.9f, 0.2f, 0.3f)}, fill(5, 0.08f), 4, SANGLE, false, false, 0, false);
        b.push().translate(1.1f, 1.45f, 1.45f).rotateY(40).rotateX(-38);
        b.disc(0.16f, 0.02f, 8, AtlasLoot.DARK, true);
        b.pop();
    }

    /**
     * Deux gilets pare-balles empilés sur une caisse (le dessus de travers, poches de chargeurs ouvertes), casque posé
     * au pied, impact sur le couvre-casque ; caisse olive écaillée, boue.
     */
    static void gilets(MeshBuilder b) {
        float x0 = 3.0f, x1 = 12.6f, z0 = 0.6f, z1 = 7.0f, h = 5.2f, yc = h - 0.9f;
        b.bevelBox(x0, 0, z0, x1, yc, z1, 0.12f, BOIS_OLIVE);
        b.bevelBox(x0 - 0.06f, yc + 0.02f, z0, x1 + 0.06f, h, z1 + 0.06f, 0.12f, BOIS_OLIVE);
        for (float x : new float[]{x0 + 0.8f, x1 - 1.7f}) b.bevelBox(x, 0.2f, z1 - 0.02f, x + 0.9f, yc - 0.1f, z1 + 0.3f, 0.08f, BOIS_OLIVE);
        Kit.poigneeCorde(b, x1 + 0.05f, 2.4f, (z0 + z1) / 2, 2.6f, 1);
        Kit.poigneeCorde(b, x0 - 0.05f, 2.4f, (z0 + z1) / 2, 2.6f, -1);
        pochoir(b, S_MATERIEL, 'z', (x0 + x1) / 2, 2.6f, z1, 4.8f, 1.2f);
        pochoir(b, S_LOT, 'z', (x0 + x1) / 2, 1.3f, z1, 4.0f, 0.5f);
        boue(b, 'z', (x0 + x1) / 2, 0, z1, 8.0f, 1.4f, 1);
        coulure(b, 'z', x1 - 0.6f, h - 0.3f, z1 + 0.06f, 0.7f, 2);
        decal(b, GRIFFURES, 'X', x1 + 0.06f, h - 0.5f, 4.0f, 2.0f, 0.6f);
        b.push().translate(7.8f, h + 0.02f, 4.55f).rotateY(4);
        gilet(b, false);
        b.pop();
        b.push().translate(7.6f, h + 0.91f, 4.95f).rotateY(-11).rotateZ(1.5f);
        gilet(b, true);
        b.pop();
        b.push().translate(13.4f, 0, 10.2f).rotateY(-30);
        casque(b);
        b.pop();
    }

    // =================================================================== 15. fûts
    /** Fût de 200 L (58 x 88 cm) d'axe Y local, base en y = 0 : cerclages, bondes, rouille, coulures, pochoirs. */
    static void fut(MeshBuilder b, int graine, boolean pochoirs) {
        float r = 4.62f, h = 14.0f;
        b.lathe(new float[][]{{4.4f, 0}, {4.72f, 0.15f}, {4.72f, 0.5f}, {r, 0.62f}, {r, 4.4f}, {4.82f, 4.6f}, {4.82f, 4.9f}, {r, 5.1f}, {r, 8.9f}, {4.82f, 9.1f},
                {4.82f, 9.4f}, {r, 9.6f}, {r, 13.4f}, {4.72f, 13.5f}, {4.72f, 13.95f}, {4.45f, 14.0f}, {4.4f, 13.7f}}, 22, 0, 360, ACIER, 1, 1, 0.007f, 0.35f, graine, false);
        b.disc(4.4f, 13.7f, 22, ACIER, true);
        // plaques de rouille, bondes
        float pas = 360f / 22;
        b.lathe(new float[][]{{r + 0.1f, 0.7f}, {r + 0.1f, 3.6f}}, 3, (12 + graine) * pas, (15 + graine) * pas, ROUILLE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{r + 0.1f, 10.0f}, {r + 0.1f, 12.4f}}, 2, (7 - graine) * pas, (9 - graine) * pas, ROUILLE, 1, 1, 0, 0, 0, false);
        b.push().translate(-2.6f, 13.7f, -1.2f);
        b.cylinder(0.62f, 0, 0.3f, 10, AtlasLoot.STEEL, null, AtlasLoot.STEEL);
        b.pop();
        b.push().translate(2.9f, 13.7f, -1.0f);
        b.cylinder(0.38f, 0, 0.26f, 8, AtlasLoot.STEEL, null, AtlasLoot.STEEL);
        b.pop();
        if (pochoirs) {
            decalFut(b, S_GAZOLE, r + 0.2f, 4, 4, 7.6f, 8.6f);
            decalFut(b, S_LOT, r + 0.2f, 5, 2, 6.4f, 6.9f);
        }
        decalFut(b, COULURE_1, r + 0.15f, 3 + graine * 2, 1, 10.3f, 13.3f);
        decalFut(b, COULURE_3, r + 0.15f, 8 - graine, 1, 1.4f, 4.3f);
        decalFut(b, BOUE_2, r + 0.25f, 3, 6, 0.62f, 2.0f);
    }

    /**
     * Deux fûts de carburant olive (200 L) : l'un debout avec sa pompe à main (manivelle, tuyau qui pend), l'autre couché
     * et calé par des coins de bois, percé d'un impact qui a fui ; flaque séchée au sol.
     */
    static void futs(MeshBuilder b) {
        // fût debout et sa pompe rotative
        b.push().translate(5.3f, 0, 5.0f).rotateY(18);
        fut(b, 1, true);
        b.pop();
        float px = 5.3f - 2.6f * (float) Math.cos(Math.toRadians(18)) - 1.2f * (float) Math.sin(Math.toRadians(18));
        float pz = 5.0f + 2.6f * (float) Math.sin(Math.toRadians(18)) - 1.2f * (float) Math.cos(Math.toRadians(18));
        b.push().translate(px, 14.0f, pz);
        b.cylinder(0.24f, 0, 1.3f, 8, AtlasLoot.STEEL, null, null);
        b.translate(0, 1.3f, 0);
        b.color(0.55f, 0.18f, 0.14f);
        b.cylinder(0.75f, 0, 1.1f, 12, PEINTURE_USEE, PEINTURE_USEE, PEINTURE_USEE);
        b.color(1);
        b.push().translate(0, 0.55f, 0.75f).rotateX(90);
        b.cylinder(0.16f, 0, 0.3f, 6, AtlasLoot.STEEL, null, null);
        b.pop();
        b.tube(new float[][]{p(0, 0.55f, 1.05f), p(0.25f, 0.55f, 1.4f), p(0.45f, 1.05f, 1.6f), p(0.45f, 1.7f, 1.45f)}, fill(4, 0.08f), 5, AtlasLoot.STEEL, false, false, 0, false);
        b.push().translate(0.45f, 1.85f, 1.45f).rotateZ(90);
        b.cylinder(0.12f, -0.1f, 0.6f, 6, AtlasLoot.DARK, AtlasLoot.DARK, AtlasLoot.DARK);
        b.pop();
        b.tube(new float[][]{p(0, 0.7f, 0.75f), p(0, 0.55f, 1.25f), p(0, 0.2f, 1.4f)}, fill(3, 0.13f), 6, AtlasLoot.STEEL, false, true, 0, false);
        b.pop();
        b.tube(new float[][]{p(px, 15.5f, pz + 1.4f), p(px + 0.2f, 14.6f, pz + 2.6f), p(px + 0.3f, 14.3f, 8.9f), p(px + 0.4f, 12.5f, 9.4f), p(px + 0.5f, 8.0f, 9.6f),
                p(px + 0.6f, 3.0f, 9.9f), p(px + 1.2f, 0.6f, 11.0f), p(px + 3.0f, 0.3f, 12.2f), p(px + 5.0f, 0.3f, 11.6f)}, fill(9, 0.3f), 8, AtlasLoot.RUBBER, false, true, 0, false);
        // fût couché, calé
        float cy = 4.86f, cz = 5.0f, xa = 11.0f;
        b.push().translate(xa, cy, cz).rotateZ(-90).rotateY(-60);
        fut(b, 2, true);
        decalFut(b, IMPACTS, 4.62f + 0.3f, 9, 1, 6.0f, 7.4f);
        b.pop();
        for (float x : new float[]{13.2f, 22.8f}) {
            for (int s = -1; s <= 1; s += 2) {
                b.push().translate(x, 0, cz + (s > 0 ? 3.9f : -3.55f)).rotateY(s > 0 ? 0 : 180);
                b.push().translate(-0.6f, 0, 0).rotateY(90);
                b.prism(new float[][]{{-1.2f, 0}, {1.4f, 0}, {-1.2f, 1.7f}}, 1.2f, BOIS_KAKI, BOIS_KAKI);
                b.pop();
                b.pop();
            }
        }
        // coulure de carburant sous l'impact et flaque séchée
        b.color(0.4f);
        decal(b, AUREOLE, 'y', 18.0f, 0, 10.6f, 6.4f, 3.4f, 8);
        decal(b, AUREOLE, 'y', 5.0f, 0, 11.4f, 4.0f, 2.6f, -20);
        b.color(1);
    }

    // =================================================================== 16. paquetage de tente
    /**
     * Paquetage de tente : bâche pliée au pied du mur (œillets, coin qui retombe), sac de tente sanglé posé dessus, botte
     * de piquets ficelée et deux piquets épars, pelle pliante appuyée contre le mur, lame boueuse.
     */
    static void paquetageTente(MeshBuilder b) {
        // bâche pliée en trois épaisseurs, coin qui retombe, œillets
        float x0 = 0.8f, x1 = 11.8f, z0 = 0.7f, z1 = 7.0f;
        b.bevelBox(x0, 0, z0, x1, 0.62f, z1, 0.28f, BACHE);
        b.bevelBox(x0 + 0.2f, 0.56f, z0 + 0.1f, x1 - 0.15f, 1.14f, z1 - 0.15f, 0.26f, BACHE);
        b.bevelBox(x0 + 0.1f, 1.08f, z0 + 0.25f, x1 - 0.35f, 1.62f, z1 - 0.1f, 0.24f, BACHE);
        b.push().translate(x1 - 2.4f, 1.45f, z1 - 0.1f).rotateX(64).rotateY(-12);
        b.bevelBox(-1.8f, -0.12f, 0, 1.6f, 0.12f, 1.3f, 0.06f, BACHE);
        b.push().translate(0.9f, 0.13f, 0.75f);
        b.torus(0.22f, 0.07f, 8, 3, 0, 360, AtlasLoot.BRASS);
        b.pop();
        b.pop();
        for (float x : new float[]{x0 + 0.7f, x1 - 0.7f}) {
            b.push().translate(x, 1.64f, z0 + 0.8f);
            b.torus(0.22f, 0.07f, 8, 3, 0, 360, AtlasLoot.BRASS);
            b.pop();
        }
        boue(b, 'z', 4.6f, 0, z1, 6.0f, 1.2f, 0);
        // sac de tente posé sur la bâche
        SacMou sac = new SacMou(new float[][]{{2.0f, 0.01f, 0}, {2.05f, 1.2f, 0}, {2.3f, 1.62f, 0.1f}, {3.5f, 1.72f, 0.15f}, {7.0f, 1.7f, 0.2f}, {10.0f, 1.62f, 0.3f},
                {10.8f, 1.2f, 0.9f}, {11.15f, 0.55f, 1.0f}, {11.3f, 0.7f, 0.8f}, {11.5f, 0.45f, 0.4f}, {11.55f, 0.01f, 0}}, 0.85f, 0.7f, 0.05f, 9);
        b.push().translate(6.6f, 1.6f, 3.9f).rotateY(-7).translate(-6.6f, 0, 0);
        sac.construire(b, TOILE, 16);
        for (float t : new float[]{2.4f, 4.4f}) {
            float[][] tour = new float[11][];
            for (int k = 0; k < 11; k++) tour[k] = sac.point(t, -60 + k * 30, 0.08f);
            sangle(b, SANGLE, tour, 0.6f, p(-1, 0, 0));
        }
        b.color(0.9f);
        sac.decal(b, S_TENTE, 3.1f, 4.15f, 10, 62, 6, 3, 0.07f);
        b.color(1);
        sac.decal(b, AUREOLE, 4.6f, 5.6f, 20, 80, 2, 2, 0.06f);
        b.pop();
        // botte de piquets ficelée, deux piquets épars
        for (int k = 0; k < 6; k++) {
            b.push().translate(2.2f + (k % 3) * 0.24f, 0.12f + (k / 3) * 0.2f, 9.0f + (k % 3) * 0.05f).rotateY(-12 + k * 2).rotateZ(-90);
            b.cylinder(0.1f, -2.0f, 1.8f, 5, METAL_NU, METAL_NU, METAL_NU);
            b.translate(0, 1.8f, 0).rotateZ(90);
            b.torus(0.25f, 0.07f, 6, 3, 0, 180, METAL_NU);
            b.pop();
        }
        b.push().translate(2.5f, 0.5f, 9.1f).rotateY(-6).rotateZ(90);
        b.torus(0.42f, 0.06f, 8, 3, 0, 360, AtlasLoot.ROPE);
        b.pop();
        for (float[] q : new float[][]{{5.6f, 11.6f, 40}, {8.4f, 10.2f, -70}}) {
            b.push().translate(q[0], 0.1f, q[1]).rotateY(q[2]).rotateZ(-90);
            b.cylinder(0.1f, -1.9f, 1.9f, 5, METAL_NU, METAL_NU, METAL_NU);
            b.pop();
        }
        // pelle pliante appuyée contre le mur, lame au sol
        b.push().translate(13.8f, 0, 4.7f).rotateY(-8).rotateX(-24);
        b.color(0.75f, 0.72f, 0.65f);
        b.prism(new float[][]{{0, 0}, {1.2f, 0.7f}, {1.25f, 3.1f}, {-1.25f, 3.1f}, {-1.2f, 0.7f}}, 0.12f, METAL_NU, METAL_NU);
        b.color(1);
        b.push().translate(0, 1.5f, 0.13f);
        b.decalZ(BOUE_2, 0, 0, 0.04f, 2.4f, 1.6f);
        b.pop();
        b.push().translate(0, 3.1f, 0.06f);
        b.cylinder(0.32f, 0, 0.5f, 10, ACIER, ACIER, ACIER);
        b.cylinder(0.45f, 0.5f, 0.95f, 10, ACIER, ACIER, ACIER);
        b.cylinder(0.3f, 0.95f, 6.6f, 10, AtlasCivil.WALNUT, null, null);
        b.translate(0, 6.6f, 0);
        b.sphere(0.4f, 10, 6, AtlasCivil.WALNUT);
        b.pop();
        b.pop();
    }
}
