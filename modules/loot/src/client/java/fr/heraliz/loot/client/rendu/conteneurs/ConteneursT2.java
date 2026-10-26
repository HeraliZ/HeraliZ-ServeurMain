package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.Kit.pochoir;
import static fr.heraliz.loot.client.rendu.conteneurs.KitCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * T2 — maisons, villes, villages, fermes : objets du quotidien (carton, bois, émail, plastique, jute, osier, cuir),
 * abandonnés depuis des mois (monde post-apocalyptique : rouille, casse, scotch, objets renversés).
 * Échelle réelle (1 px = 6,25 cm, le joueur fait 1,8 m) ; placements vraisemblables, dos au mur (z = 0) ; les petits
 * objets sont en piles ; frigo et boîte aux lettres montent sur deux blocs, le coffre de jardin s'étale sur deux.
 */
final class ConteneursT2 {
    private ConteneursT2() {
    }

    /** Caisse à outils (55 x 25 x 26 cm) au pied du mur, couvercle forcé de travers, clé rouillée et chiffon devant. */
    static void caisseOutils(MeshBuilder b) {
        b.push().translate(8, 0, 3.0f).rotateY(7);
        float L = 8.8f, W = 4.0f, h = 4.2f, x0 = -L / 2, x1 = L / 2, z0 = -W / 2, z1 = W / 2;
        b.bevelBox(x0, 0, z0, x1, h - 0.9f, z1, 0.2f, PAINT_GREEN);
        // couvercle soulevé côté droit (fermoir arraché)
        b.push().translate(x0, h - 0.92f, 0).rotateZ(-5).translate(-x0, -(h - 0.92f), 0);
        b.bevelBox(x0 - 0.06f, h - 0.92f, z0 - 0.06f, x1 + 0.06f, h, z1 + 0.06f, 0.18f, PAINT_GREEN);
        b.bevelBox(x0 + 0.3f, h - 0.03f, z0 + 0.4f, x1 - 0.3f, h + 0.35f, z1 - 0.4f, 0.16f, PAINT_GREEN);
        for (float x : new float[]{-1.9f, 1.9f}) b.bevelBox(x - 0.25f, h + 0.3f, -0.45f, x + 0.25f, h + 1.0f, 0.45f, 0.08f, DARK);
        b.tube(new float[][]{p(-1.9f, h + 0.95f, 0), p(1.9f, h + 0.95f, 0)}, new float[]{0.22f, 0.22f}, 10, RUBBER, true, true, 0, false);
        b.pop();
        fermoir(b, -3.2f, h - 0.9f, z1 + 0.08f, STEEL);
        boulon(b, 2.9f, h - 1.4f, z1, 0.14f);
        boulon(b, 3.5f, h - 1.4f, z1, 0.14f);
        coulure(b, 'z', -3.2f, h - 1.7f, z1, 0.8f, 0);
        coulure(b, 'z', 3.2f, h - 1.5f, z1, 0.8f, 2);
        pochoir(b, S_OUTILS, 'z', 0, 1.8f, z1 + 0.01f, 3.4f, 0.85f);
        boue(b, 'z', 0, 0, z1, 8.0f, 1.1f, 1);
        b.pop();
        // clé à molette rouillée et chiffon gras
        b.push().translate(11.0f, 0.12f, 7.4f).rotateY(-30);
        b.bevelBox(-2.4f, 0, -0.25f, 1.6f, 0.22f, 0.25f, 0.06f, CHROME_PIQUE);
        b.push().translate(2.0f, 0, 0);
        b.cylinder(0.6f, 0, 0.24f, 12, CHROME_PIQUE, CHROME_PIQUE, CHROME_PIQUE);
        b.pop();
        b.pop();
        b.push().translate(4.6f, 0, 7.6f).scale(1.3f, 0.32f, 1.0f);
        b.color(0.55f, 0.42f, 0.36f);
        b.sphereSection(1.1f, 10, 5, 0, 90, TISSU_USE);
        b.color(1);
        b.pop();
    }

    /** Cagette à claire-voie : montants en retrait derrière les lattes (aucune face confondue) ; cassee : latte avant brisée. */
    static void cagette(MeshBuilder b, float x0, float y0, float z0, float L, float W, float H, Mat m, boolean cassee) {
        float x1 = x0 + L, z1 = z0 + W, e = 0.45f, t = 0.65f;
        b.bevelBox(x0 + 0.1f, y0, z0 + 0.1f, x1 - 0.1f, y0 + 0.4f, z1 - 0.1f, 0.06f, m);
        for (float[] c : new float[][]{{x0 + e, z0 + e}, {x1 - e - t, z0 + e}, {x0 + e, z1 - e - t}, {x1 - e - t, z1 - e - t}}) {
            b.bevelBox(c[0], y0 + 0.05f, c[1], c[0] + t, y0 + H - 0.05f, c[1] + t, 0.08f, m);
        }
        float hl = H * 0.3f;
        for (float y : new float[]{y0 + 0.45f, y0 + H - hl}) {
            boolean brisee = cassee && y > y0 + 1;
            if (brisee) {
                // latte avant cassée : un morceau tient, l'autre pend
                b.bevelBox(x0 + e, y, z1 - e, x0 + L * 0.42f, y + hl, z1, 0.08f, m);
                b.push().translate(x1 - e, y + hl, z1 - e / 2).rotateZ(-38).translate(-(x1 - e), -(y + hl), -(z1 - e / 2));
                b.bevelBox(x0 + L * 0.58f, y, z1 - e, x1 - e, y + hl, z1, 0.08f, m);
                b.pop();
            } else {
                b.bevelBox(x0 + e, y, z1 - e, x1 - e, y + hl, z1, 0.08f, m);
            }
            b.bevelBox(x0 + e, y, z0, x1 - e, y + hl, z0 + e, 0.08f, m);
            b.bevelBox(x0, y, z0 + e, x0 + e, y + hl, z1 - e, 0.08f, m);
            b.bevelBox(x1 - e, y, z0 + e, x1, y + hl, z1 - e, 0.08f, m);
        }
    }

    /** Deux cagettes de récolte empilées (celle du bas cassée), pommes de terre germées, mousse au pied. */
    static void caisseFerme(MeshBuilder b) {
        cagette(b, 3.0f, 0, 0.6f, 10.0f, 6.4f, 4.6f, PINE, true);
        b.push().translate(8, 4.6f, 3.8f).rotateY(6).translate(-8, -4.6f, -3.8f);
        cagette(b, 3.0f, 4.62f, 0.6f, 10.0f, 6.4f, 4.6f, PINE, false);
        b.bevelBox(3.6f, 5.0f, 1.2f, 12.4f, 7.6f, 6.4f, 0.35f, BURLAP);
        java.util.Random r = new java.util.Random(7);
        for (int k = 0; k < 14; k++) {
            float x = 4.3f + r.nextFloat() * 7.4f, z = 1.9f + r.nextFloat() * 3.8f;
            b.push().translate(x, 7.75f + r.nextFloat() * 0.25f, z).scale(1.25f, 0.85f, 1.0f);
            b.color(0.72f, 0.66f, 0.55f);
            b.sphere(0.6f, 10, 7, CARTON);
            b.color(1);
            b.pop();
        }
        b.pop();
        // pommes de terre tombées par la latte cassée
        for (float[] q : new float[][]{{9.6f, 7.9f}, {10.8f, 8.4f}, {8.7f, 9.3f}}) {
            b.push().translate(q[0], 0.5f, q[1]).scale(1.25f, 0.85f, 1.0f);
            b.color(0.62f, 0.55f, 0.45f);
            b.sphere(0.6f, 10, 7, CARTON);
            b.color(1);
            b.pop();
        }
        decal(b, MOUSSE_VERTE, 'z', 5.2f, 0.7f, 7.0f, 3.2f, 1.4f);
    }

    /** Boîte aux lettres (1,2 m) sur son poteau penché, cabossée, porte pendante, courrier moisi, fanion arraché. */
    static void boiteAuxLettres(MeshBuilder b) {
        b.push().translate(8, 0, 8).rotateZ(3).rotateX(-2).translate(-8, 0, -8);
        b.bevelBox(7.3f, 0, 7.3f, 8.7f, 14.2f, 8.7f, 0.18f, WALNUT);
        b.bevelBox(6.8f, 13.9f, 4.6f, 9.2f, 14.4f, 11.4f, 0.12f, WALNUT);
        float zc = 8.0f, L = 7.6f, w = 4.0f, yb = 14.4f, hb = 2.0f, zf = zc + L / 2;
        b.push().translate(8, yb, zc).rotateZ(-4).translate(-8, -yb, -zc);
        b.bevelBox(8 - w / 2, yb, zc - L / 2, 8 + w / 2, yb + hb, zf, 0.15f, PAINT_GREEN);
        b.push().translate(8, yb + hb, zc - L / 2).rotateX(90);
        b.lathe(new float[][]{{w / 2, 0}, {w / 2, L}}, 16, 180, 360, PAINT_GREEN, 1, 1, 0, 0, 0, false);
        b.pop();
        b.push().translate(8, yb + hb, zc - L / 2 - 0.02f);
        b.push().rotateX(-90);
        b.disc(w / 2, 0, 16, PAINT_GREEN, false);
        b.pop();
        b.pop();
        // ouverture : fond sombre, lettres moisies
        b.box(8 - w / 2 + 0.25f, yb + 0.25f, zf - 0.3f, 8 + w / 2 - 0.25f, yb + hb + 1.6f, zf - 0.2f, INTERIEUR_SALE);
        for (int k = 0; k < 3; k++) {
            b.push().translate(8 + (k - 1) * 0.5f, yb + 0.5f + k * 0.07f, zf - 1.6f + k * 0.4f).rotateY(k * 9 - 9).rotateX(-90);
            b.card(2.6f, 1.8f, k == 1 ? JOURNAL : PAPIER);
            b.pop();
        }
        // porte arrondie pendante sur sa charnière du bas
        b.push().translate(8, yb + 0.05f, zf + 0.06f).rotateX(118);
        b.bevelBox(-w / 2 - 0.06f, 0, -0.06f, w / 2 + 0.06f, hb, 0.06f, 0.04f, PAINT_GREEN);
        b.push().translate(0, hb, 0).rotateX(-90);
        b.lathe(new float[][]{{w / 2 + 0.06f, -0.06f}, {w / 2 + 0.06f, 0.06f}}, 16, 180, 360, PAINT_GREEN, 1, 1, 0, 0, 0, false);
        b.pop();
        b.bevelBox(-0.4f, 1.0f, 0.04f, 0.4f, 1.4f, 0.42f, 0.08f, CHROME_PIQUE);
        b.pop();
        pochoir(b, S_COURRIER, 'X', 8 + w / 2, yb + 1.0f, zc, 4.0f, 1.0f);
        coulure(b, 'X', 8 + w / 2, yb + 0.5f, zc - 2.6f, 1.2f, 1);
        coulure(b, 'x', 8 - w / 2, yb + 1.4f, zc + 1.8f, 1.3f, 0);
        // fanion : il ne reste que le pivot, le drapeau gît au pied du poteau
        b.bevelBox(10.08f, yb + 0.6f, zc - 1.2f, 10.26f, yb + 1.6f, zc - 0.9f, 0.04f, ROUILLE);
        b.pop();
        b.pop();
        b.push().translate(10.6f, 0.03f, 10.4f).rotateY(28);
        b.bevelBox(-0.15f, 0, -1.5f, 0.15f, 0.18f, 1.5f, 0.04f, PAINT_RED);
        b.bevelBox(-0.15f, 0, 1.5f, 1.6f, 0.18f, 2.6f, 0.04f, PAINT_RED);
        b.pop();
        jonchee(b, 5.0f, 9.0f, 12.0f, 14.0f, 6, FEUILLES, 1.2f, 21);
    }

    /**
     * Réfrigérateur abandonné (60 x 65 x 185 cm), dos au mur : émail jauni et rouillé, porte du haut entrouverte sur un
     * intérieur moisi, congélateur fermé au scotch (poignée arrachée), grille du bas rouillée, avis d'évacuation collé,
     * boue au pied. Dessus : boîte de céréales et bouteille renversée.
     */
    static void frigo(MeshBuilder b) {
        float x0 = 2.8f, x1 = 13.2f, z0 = 0.5f, zf = 10.4f, h = 29.6f, ep = 0.72f, zc = zf - ep, sep = 10.85f;
        // caisse : congélateur plein en bas, compartiment du haut ouvert (parois, fond, plafond)
        b.bevelBox(x0, 0.6f, z0, x1, sep, zc, 0.5f, ENAMEL);
        b.bevelBox(x0, sep, z0, x1, h, zc - 1.7f, 0.5f, ENAMEL);
        b.bevelBox(x0, sep - 0.1f, zc - 1.9f, x0 + 0.5f, h, zc, 0.12f, ENAMEL);
        b.bevelBox(x1 - 0.5f, sep - 0.1f, zc - 1.9f, x1, h, zc, 0.12f, ENAMEL);
        b.bevelBox(x0 + 0.4f, h - 0.55f, zc - 1.9f, x1 - 0.4f, h, zc, 0.12f, ENAMEL);
        b.box(x0 + 0.48f, sep + 0.02f, zc - 1.74f, x1 - 0.48f, h - 0.53f, zc - 1.62f, INTERIEUR_SALE);
        b.box(x0 + 0.48f, sep - 0.05f, zc - 1.62f, x1 - 0.48f, sep + 0.12f, zc - 0.05f, INTERIEUR_SALE);
        // clayettes rouillées, bocal moisi, brique renversée
        for (float y : new float[]{16.2f, 21.8f}) b.box(x0 + 0.5f, y, zc - 1.6f, x1 - 0.5f, y + 0.14f, zc - 0.3f, CHROME_PIQUE);
        b.push().translate(5.2f, 16.36f, zc - 0.9f);
        b.cylinder(0.75f, 0, 1.5f, 12, BOTTLE_BROWN, null, BOTTLE_BROWN);
        b.cylinder(0.8f, 1.5f, 1.85f, 12, STEEL, null, ROUILLE);
        b.pop();
        b.push().translate(9.6f, 22.0f, zc - 0.95f).rotateZ(90).translate(-0.6f, 0, 0);
        b.bevelBox(0, -0.55f, -0.45f, 1.2f, 0.55f, 0.45f, 0.08f, WHITE);
        b.pop();
        // grille du bas, sans plinthe : rouillée
        b.box(x0 + 0.6f, 0, z0 + 0.8f, x1 - 0.6f, 0.62f, zc - 0.9f, ROUILLE);
        // congélateur : porte fermée, poignée arrachée (deux vis), scotch en travers du joint
        b.bevelBox(x0 + 0.08f, 0.75f, zc, x1 - 0.08f, sep - 0.25f, zf, 0.4f, ENAMEL);
        boulon(b, x0 + 2.2f, 9.25f, zf, 0.16f);
        boulon(b, x1 - 2.2f, 9.25f, zf, 0.16f);
        decal(b, ETIQUETTE_DECHIREE, 'z', 8.0f, 9.25f, zf, 5.6f, 0.9f);
        scotchZ(b, x0 + 0.6f, sep - 0.05f, x1 - 1.4f, sep + 0.3f, zf + 0.02f, 0.9f, SCOTCH);
        // porte du haut entrouverte (charnière à droite), joint, poignée, avis collé, traces
        float hx = x1 - 0.1f;
        b.push().translate(hx, 0, zc).rotateY(11).translate(-hx, 0, -zc);
        b.bevelBox(x0 + 0.08f, sep + 0.05f, zc, x1 - 0.08f, h - 0.12f, zf, 0.4f, ENAMEL);
        b.box(x0 + 0.5f, sep + 0.5f, zc - 0.12f, x1 - 0.5f, h - 0.55f, zc - 0.02f, CAOUTCHOUC_USE);
        b.bevelBox(x0 + 1.0f, 13.0f, zc - 0.62f, x1 - 1.0f, 13.6f, zc - 0.12f, 0.1f, PLASTIC_GREY);
        b.bevelBox(x1 - 1.4f, 13.0f, zf + 0.02f, x1 - 0.9f, 21.0f, zf + 0.55f, 0.2f, CHROME_PIQUE);
        decal(b, AFFICHE, 'z', 6.6f, 21.4f, zf, 5.2f, 5.2f, -4);
        b.bevelBox(4.3f, 25.6f, zf + 0.02f, 5.3f, 26.6f, zf + 0.14f, 0.1f, PAINT_RED);
        decal(b, MAIN_SANG, 'z', x1 - 2.4f, 17.2f, zf, 2.2f, 2.2f, 12);
        coulure(b, 'z', x1 - 0.6f, h - 0.4f, zf, 1.4f, 0);
        b.pop();
        // rouille et boue : sous la charnière, au pied, sur le flanc
        coulure(b, 'X', x1, h - 1.2f, 3.4f, 1.8f, 1);
        coulure(b, 'X', x1, 9.8f, 6.8f, 1.6f, 2);
        coulure(b, 'x', x0, 20.0f, 4.0f, 2.2f, 0);
        boue(b, 'z', 8.0f, 0.6f, zf, 9.6f, 2.4f, 0);
        boue(b, 'X', x1, 0.6f, 5.4f, 9.0f, 2.0f, 1);
        boue(b, 'x', x0, 0.6f, 5.4f, 9.0f, 2.2f, 0);
        decal(b, AUREOLE, 'X', x1, 15.0f, 4.0f, 4.4f, 4.4f);
        // dessus : boîte de céréales décolorée, bouteille couchée
        b.push().translate(5.6f, h, 3.6f).rotateY(-8);
        b.bevelBox(-1.6f, 0.02f, -0.7f, 1.6f, 4.4f, 0.7f, 0.06f, RED_BOX);
        b.pop();
        b.push().translate(10.2f, h + 0.62f, 4.4f).rotateY(30).rotateZ(90);
        b.lathe(new float[][]{{0.6f, -2.1f}, {0.6f, 0.3f}, {0.3f, 1.1f}, {0.3f, 1.7f}, {0.34f, 1.72f}, {0.34f, 2.1f}, {0.01f, 2.11f}}, 12, 0, 360, BOTTLE_GREEN, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Valise trolley (70 x 45 x 27 cm) debout contre le mur, poignée sortie, une roue arrachée, fente scotchée. */
    static void valise(MeshBuilder b) {
        int m0 = b.mark();
        b.push().translate(8, 0, 2.4f).rotateY(-8).rotateX(-6);
        float w = 7.0f, e = 4.2f, h = 11.0f;
        b.bevelBox(-w / 2, 0.5f, -e / 2, w / 2, h, e / 2, 0.9f, PLASTIC_BLUE);
        for (float x = -2.4f; x <= 2.5f; x += 1.2f) b.bevelBox(x - 0.18f, 1.4f, e / 2 - 0.06f, x + 0.18f, h - 0.9f, e / 2 + 0.12f, 0.08f, PLASTIC_BLUE);
        b.bevelBox(-w / 2 - 0.06f, 0.6f, -0.12f, w / 2 + 0.06f, h - 0.1f, 0.12f, 0.06f, DARK);
        // roue gauche seule, l'axe droit est nu
        b.push().translate(-2.6f, 0.5f, -e / 2 + 0.5f).rotateZ(90);
        b.cylinder(0.5f, -0.35f, 0.35f, 12, RUBBER, RUBBER, RUBBER);
        b.pop();
        b.push().translate(2.6f, 0.5f, -e / 2 + 0.5f).rotateZ(90);
        b.cylinder(0.12f, -0.3f, 0.3f, 6, ROUILLE, null, ROUILLE);
        b.pop();
        for (float x : new float[]{-1.6f, 1.6f}) {
            b.push().translate(x, h - 0.2f, -e / 2 + 0.6f);
            b.cylinder(0.16f, 0, 3.6f, 8, CHROME_PIQUE, null, CHROME_PIQUE);
            b.pop();
        }
        b.bevelBox(-2.0f, h + 3.3f, -e / 2 + 0.3f, 2.0f, h + 4.0f, -e / 2 + 0.9f, 0.25f, DARK);
        b.bevelBox(-1.0f, h - 0.05f, 0.3f, 1.0f, h + 0.5f, 1.1f, 0.2f, DARK);
        // coque fendue rafistolée au scotch, étiquette déchirée
        b.push().translate(0, 0, 0.14f);
        scotchZ(b, -3.0f, 7.6f, 1.2f, 6.2f, e / 2, 0.9f, SCOTCH);
        scotchZ(b, -1.2f, 8.8f, -0.6f, 5.2f, e / 2 + 0.09f, 0.9f, SCOTCH);
        b.pop();
        decal(b, ETIQUETTE_DECHIREE, 'z', 1.8f, 3.0f, e / 2 + 0.12f, 2.4f, 1.2f, 8);
        decal(b, FISSURE, 'x', -w / 2 - 0.07f, 4.0f, 1.0f, 2.6f, 2.6f);
        b.pop();
        caler(b, m0, 0.05f);
    }

    /** Carton (scotch qui pend), coin bas-arrière-gauche (x0, y0, z0) ; ouvert : rabats relevés, journaux dedans. */
    static void carton(MeshBuilder b, float x0, float y0, float z0, float w, float h, float d, boolean fragile, boolean ouvert, Mat m) {
        if (!ouvert) {
            b.bevelBox(x0, y0, z0, x0 + w, y0 + h, z0 + d, 0.1f, m);
            b.box(x0 + w / 2 - 0.45f, y0 + h - 0.01f, z0 + 0.3f, x0 + w / 2 + 0.45f, y0 + h + 0.07f, z0 + d + 0.03f, TAPE);
            b.box(x0 + w / 2 - 0.45f, y0 + h - 0.9f, z0 + d - 0.01f, x0 + w / 2 + 0.45f, y0 + h, z0 + d + 0.07f, TAPE);
            // le bout de scotch décollé pend
            b.push().translate(x0 + w / 2, y0 + h - 0.9f, z0 + d + 0.07f).rotateX(-25);
            b.box(-0.45f, -1.1f, -0.02f, 0.45f, 0, 0.04f, TAPE);
            b.pop();
        } else {
            float e = 0.12f;
            b.bevelBox(x0, y0, z0, x0 + w, y0 + 0.2f, z0 + d, 0.05f, m);
            b.box(x0, y0 + 0.2f, z0, x0 + e, y0 + h, z0 + d, m);
            b.box(x0 + w - e, y0 + 0.2f, z0, x0 + w, y0 + h, z0 + d, m);
            b.box(x0 + e, y0 + 0.2f, z0, x0 + w - e, y0 + h, z0 + e, m);
            b.box(x0 + e, y0 + 0.2f, z0 + d - e, x0 + w - e, y0 + h, z0 + d, m);
            // rabats : avant rabattu vers l'extérieur, arrière dressé, côtés mous
            b.push().translate(0, y0 + h, z0 + d).rotateX(118);
            b.box(x0 + 0.1f, 0, -e, x0 + w - 0.1f, d * 0.48f, 0, m);
            b.pop();
            b.push().translate(0, y0 + h, z0).rotateX(-12);
            b.box(x0 + 0.1f, 0, 0, x0 + w - 0.1f, d * 0.48f, e, m);
            b.pop();
            b.push().translate(x0, y0 + h, 0).rotateZ(-60);
            b.box(-e, 0, z0 + 0.2f, 0, w * 0.4f, z0 + d - 0.2f, m);
            b.pop();
            // journaux en vrac
            for (int k = 0; k < 4; k++) {
                b.push().translate(x0 + w / 2 + (k - 1.5f) * 0.4f, y0 + h - 1.6f + k * 0.12f, z0 + d / 2).rotateY(k * 23 - 20).rotateZ(k * 4 - 6);
                b.box(-w * 0.36f, 0, -d * 0.34f, w * 0.36f, 0.1f, d * 0.34f, JOURNAL);
                b.pop();
            }
        }
        if (fragile) pochoir(b, S_FRAGILE, 'z', x0 + w / 2, y0 + h * 0.4f, z0 + d + 0.01f, Math.min(w * 0.7f, 4.6f), Math.min(w * 0.7f, 4.6f) / 4);
    }

    /** Trois cartons : celui du bas a pris l'eau, celui du haut est ouvert (journaux), le petit s'est affaissé. */
    static void cartons(MeshBuilder b) {
        carton(b, 1.0f, 0, 0.6f, 9.6f, 6.4f, 6.4f, true, false, CARTON_MOUILLE);
        b.push().translate(5.8f, 6.4f, 3.8f).rotateY(11).translate(-5.8f, -6.4f, -3.8f);
        carton(b, 2.4f, 6.42f, 1.0f, 7.0f, 5.0f, 5.0f, false, true, CARTON);
        b.pop();
        b.push().translate(13.0f, 0, 8.0f).rotateY(-18).translate(-13.0f, 0, -8.0f);
        b.push().translate(10.6f, 0, 0).rotateZ(4).scale(1, 0.72f, 1).translate(-10.6f, 0, 0);
        carton(b, 10.6f, 0, 6.2f, 5.0f, 4.0f, 4.0f, false, false, CARTON);
        b.pop();
        b.pop();
        decal(b, ETIQUETTE_DECHIREE, 'z', 2.6f, 1.0f, 7.0f, 2.4f, 1.2f);
        decal(b, AUREOLE, 'z', 8.4f, 4.8f, 7.0f, 3.6f, 3.0f);
    }

    /** Commode (90 x 45 x 85 cm) contre le mur : tiroir du milieu arraché et renversé devant, cadre photo tombé. */
    static void commode(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.5f, z1 = 7.7f, h = 13.6f;
        for (float[] q : new float[][]{{1.6f, 1.3f}, {14.4f, 1.3f}, {1.6f, 6.9f}, {14.4f, 6.9f}}) pied(b, q[0], q[1], 0.4f, 1.0f, WALNUT);
        b.bevelBox(x0, 1.0f, z0, x1, h - 0.5f, z1, 0.15f, PAINT_GREEN);
        b.bevelBox(x0 - 0.25f, h - 0.48f, z0 - 0.1f, x1 + 0.25f, h, z1 + 0.3f, 0.15f, WALNUT);
        for (int k = 0; k < 3; k++) {
            float y0 = 1.5f + k * 3.65f;
            if (k == 1) {
                // logement vide : fond sombre
                b.box(x0 + 0.45f, y0, z1 - 0.3f, x1 - 0.45f, y0 + 3.3f, z1 + 0.04f, INTERIEUR_SALE);
                continue;
            }
            float ouvert = k == 2 ? 2.2f : 0;
            b.bevelBox(x0 + 0.45f, y0, z1 - 0.06f + ouvert - 2.0f * (ouvert > 0 ? 1 : 0), x1 - 0.45f, y0 + 3.3f, z1 + 0.3f + ouvert, 0.2f, PAINT_GREEN);
            if (ouvert > 0) b.bevelBox(x0 + 0.9f, y0 + 2.6f, z1 - 1.2f, x1 - 0.9f, y0 + 3.4f, z1 + ouvert - 0.2f, 0.3f, CHECK);
            for (float x : new float[]{5.0f, 11.0f}) {
                if (k == 0 && x > 8) continue;   // bouton perdu
                b.push().translate(x, y0 + 1.65f, z1 + 0.3f + ouvert).rotateX(90);
                b.cylinder(0.2f, 0, 0.35f, 10, BRASS, null, null);
                b.translate(0, 0.35f, 0);
                b.sphere(0.36f, 10, 7, BRASS);
                b.pop();
            }
        }
        // le tiroir arraché, renversé devant, linge éparpillé
        b.push().translate(9.0f, 0, 11.2f).rotateY(-14).rotateZ(180).translate(0, -3.0f, 0);
        b.bevelBox(-6.4f, 0, -2.4f, 6.4f, 0.3f, 2.4f, 0.08f, PAINT_GREEN);
        b.bevelBox(-6.4f, 0.3f, -2.4f, 6.4f, 3.0f, -2.1f, 0.06f, PAINT_GREEN);
        b.bevelBox(-6.4f, 0.3f, 2.1f, 6.4f, 3.0f, 2.4f, 0.06f, PAINT_GREEN);
        b.bevelBox(-6.4f, 0.3f, -2.1f, -6.1f, 3.0f, 2.1f, 0.06f, PAINT_GREEN);
        b.bevelBox(6.1f, 0.3f, -2.1f, 6.4f, 3.0f, 2.1f, 0.06f, PAINT_GREEN);
        b.pop();
        b.push().translate(3.6f, 0, 10.2f).scale(1.6f, 0.3f, 1.1f);
        b.color(0.62f, 0.66f, 0.74f);
        b.sphereSection(1.3f, 10, 5, 0, 90, TISSU_USE);
        b.color(1);
        b.pop();
        // cadre photo tombé à plat sur le dessus
        b.push().translate(4.0f, h + 0.02f, 3.4f).rotateY(15);
        b.bevelBox(-1.4f, 0, -1.7f, 1.4f, 0.3f, 1.7f, 0.08f, WALNUT);
        b.pop();
        coulure(b, 'z', 5.0f, 4.6f, z1 + 0.3f, 0.9f, 1);
        decal(b, AUREOLE, 'X', x1, 7.0f, 4.0f, 4.0f, 3.6f);
    }

    /** Sac de jute plein (90 x 50 x 22 cm) couché, le long de X. */
    static void sacJute(MeshBuilder b, float cx, float y, float cz, float ang, int graine) {
        b.push().translate(cx, y, cz).rotateY(ang).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -7.0f}, {1.3f, -6.7f}, {2.4f, -5.6f}, {2.6f, 0}, {2.4f, 5.4f}, {1.6f, 6.6f}, {0.6f, 7.2f}, {0.01f, 7.5f}},
                20, 0, 360, BURLAP, 0.66f, 1.45f, 0.04f, 0.6f, graine, false);
        b.pop();
    }

    /** Trois sacs de grain empilés contre le mur ; celui du bas, rongé, laisse couler son grain. */
    static void sacsGrain(MeshBuilder b) {
        sacJute(b, 8, 1.7f, 4.2f, 0, 1);
        sacJute(b, 8, 4.9f, 4.0f, 6, 2);
        sacJute(b, 7.6f, 8.0f, 4.3f, -5, 3);
        pochoir(b, S_GRAIN, 'z', 7.2f, 1.9f, 8.0f, 5.6f, 1.4f);
        b.tube(new float[][]{p(14.6f, 8.6f, 3.6f), p(15.1f, 9.0f, 4.1f), p(14.8f, 8.3f, 4.8f)}, new float[]{0.1f, 0.1f, 0.1f}, 5, TAPE, false, false, 0, false);
        // trou rongé et tas de grain au pied
        decal(b, BRULURE, 'z', 13.0f, 1.4f, 7.3f, 2.0f, 1.6f);
        b.push().translate(13.4f, 0, 9.6f).scale(1.5f, 0.42f, 1.15f);
        b.color(0.92f, 0.82f, 0.55f);
        b.sphereSection(2.0f, 12, 5, 0, 90, BURLAP);
        b.color(1);
        b.pop();
    }

    /** Cagettes de pommes (50 x 30 x 30 cm) : deux au sol (une cassée), une dessus ; pommes blettes, certaines tombées. */
    static void caissesPommes(MeshBuilder b) {
        cagette(b, 0.6f, 0, 0.6f, 7.6f, 4.8f, 4.6f, PINE, false);
        cagette(b, 8.3f, 0, 0.8f, 7.6f, 4.8f, 4.6f, PINE, true);
        b.push().translate(8, 4.6f, 3.0f).rotateY(-5).translate(-8, -4.6f, -3.0f);
        cagette(b, 4.2f, 4.62f, 0.6f, 7.6f, 4.8f, 4.6f, PINE, false);
        java.util.Random r = new java.util.Random(11);
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 2; k++) {
                if (i == 3 && k == 0) continue;
                float x = 5.4f + i * 1.75f + r.nextFloat() * 0.2f, z = 1.9f + k * 1.8f + r.nextFloat() * 0.2f;
                b.push().translate(x, 8.7f + r.nextFloat() * 0.2f, z);
                b.sphere(0.72f, 10, 7, APPLE);
                b.tube(new float[][]{p(0, 0.62f, 0), p(0.08f, 0.95f, 0.04f)}, new float[]{0.05f, 0.04f}, 4, WALNUT, false, false, 0, false);
                b.pop();
            }
        }
        b.pop();
        pochoir(b, S_POMMES, 'z', 4.4f, 1.6f, 5.41f, 4.4f, 1.1f);
        for (float[] q : new float[][]{{12.6f, 9.4f}, {10.2f, 10.6f}, {14.1f, 8.1f}}) {
            b.push().translate(q[0], 0.6f, q[1]).scale(1, 0.84f, 1);
            b.color(0.78f, 0.7f, 0.6f);
            b.sphere(0.72f, 10, 7, APPLE);
            b.color(1);
            b.pop();
        }
        jonchee(b, 1.0f, 7.0f, 15.0f, 12.0f, 5, FEUILLES, 1.0f, 33);
    }

    /** Glacière (60 x 40 x 40 cm) contre le mur : couvercle fendu de travers, anse cassée d'un côté, traces. */
    static void glaciere(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.6f, z1 = 7.0f, h = 6.4f;
        b.bevelBox(x0, 0, z0, x1, h - 1.4f, z1, 0.7f, PLASTIC_BLUE);
        b.push().translate(x0, h - 1.4f, z0).rotateZ(-4).rotateY(3).translate(-x0, -(h - 1.4f), -z0);
        b.bevelBox(x0 - 0.12f, h - 1.38f, z0 - 0.12f + 0.1f, x1 + 0.12f, h, z1 + 0.12f, 0.6f, WHITE);
        decal(b, FISSURE, 'y', 9.0f, h, 3.8f, 4.4f, 4.4f, 20);
        b.pop();
        // anse : pivot gauche tient, la poignée pend côté droit
        b.bevelBox(x0 - 0.3f, 2.6f, 2.8f, x0 + 0.06f, h - 1.6f, 4.8f, 0.12f, WHITE);
        b.bevelBox(x1 - 0.06f, 2.6f, 2.8f, x1 + 0.3f, h - 1.6f, 4.8f, 0.12f, WHITE);
        b.tube(new float[][]{p(x0 - 0.12f, h - 1.9f, 3.8f), p(x0 - 0.5f, 1.8f, 4.6f), p(x0 + 1.2f, 0.25f, 7.8f), p(x0 + 4.0f, 0.25f, 8.6f)},
                new float[]{0.24f, 0.26f, 0.26f, 0.24f}, 10, WHITE, false, false, 0, false);
        b.bevelBox(6.8f, 1.6f, z1 - 0.04f, 9.2f, 2.4f, z1 + 0.1f, 0.06f, WHITE);
        boue(b, 'z', 8, 0, z1, 9.0f, 1.6f, 0);
        decal(b, AUREOLE, 'z', 4.6f, 3.2f, z1, 2.4f, 2.0f);
    }

    /** Panier à pique-nique en osier : anse cassée qui pend, torchon taché, baguette moisie. */
    static void panier(MeshBuilder b) {
        b.push().translate(8, 0, 4.4f).scale(1.2f, 1, 0.8f);
        b.lathe(new float[][]{{2.6f, 0}, {3.0f, 0.4f}, {3.3f, 2.0f}, {3.5f, 3.8f}}, 24, 0, 360, WICKER, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{3.25f, 3.8f}, {3.05f, 0.5f}}, 24, 0, 360, WICKER, 1, 1, 0, 0, 0, true);
        b.disc(2.6f, 0.03f, 24, WICKER, false);
        b.push().translate(0, 3.8f, 0);
        b.torus(3.4f, 0.24f, 24, 6, 0, 360, WICKER);
        b.pop();
        b.pop();
        // anse : tient à gauche, cassée en haut, le bout droit pend le long du flanc
        b.tube(new float[][]{p(4.0f, 3.8f, 4.4f), p(4.6f, 6.6f, 4.4f), p(6.8f, 7.6f, 4.4f)},
                new float[]{0.22f, 0.24f, 0.2f}, 8, WICKER, false, true, 0, false);
        b.tube(new float[][]{p(12.0f, 3.8f, 4.4f), p(12.7f, 2.6f, 5.0f), p(12.9f, 1.0f, 5.6f)},
                new float[]{0.22f, 0.22f, 0.2f}, 8, WICKER, false, true, 0, false);
        b.push().translate(8, 3.4f, 4.4f).scale(1.15f, 0.42f, 0.75f);
        b.sphereSection(3.0f, 16, 6, 0, 80, CHECK);
        b.pop();
        b.color(0.62f, 0.6f, 0.5f);
        b.tube(new float[][]{p(6.0f, 3.6f, 4.9f), p(7.6f, 5.0f, 4.6f), p(9.4f, 6.6f, 4.0f)}, new float[]{0.5f, 0.55f, 0.48f}, 10, TAPE, true, true, 0, false);
        b.color(1);
        decal(b, MOUSSE_VERTE, 'z', 8.2f, 5.6f, 4.98f, 1.2f, 0.6f, 50);
    }

    /** Bac de rangement (60 x 40 x 35 cm) ; ouvert : couvercle retiré, vêtements dedans. */
    static void bacPlastique(MeshBuilder b, float x0, float y0, float z0, boolean ouvert) {
        float L = 9.6f, W = 6.4f, H = 5.6f, x1 = x0 + L, z1 = z0 + W;
        b.bevelBox(x0 + 0.3f, y0, z0 + 0.3f, x1 - 0.3f, y0 + 2.0f, z1 - 0.3f, 0.35f, PLASTIC_GREEN);
        b.bevelBox(x0, y0 + 1.9f, z0, x1, y0 + H - 0.6f, z1, 0.35f, PLASTIC_GREEN);
        if (ouvert) {
            b.box(x0 + 0.4f, y0 + H - 0.65f, z0 + 0.4f, x1 - 0.4f, y0 + H - 0.54f, z1 - 0.4f, INTERIEUR_SALE);
            b.push().translate((x0 + x1) / 2, y0 + H - 0.6f, (z0 + z1) / 2).scale(1.9f, 0.5f, 1.1f);
            b.color(0.5f, 0.52f, 0.6f);
            b.sphereSection(2.4f, 12, 5, 0, 90, TISSU_USE);
            b.color(1);
            b.pop();
        } else {
            b.bevelBox(x0 - 0.2f, y0 + H - 0.62f, z0 - 0.2f, x1 + 0.2f, y0 + H, z1 + 0.2f, 0.22f, PLASTIC_GREEN);
            b.bevelBox(x0 + 0.8f, y0 + H - 0.02f, z0 + 0.8f, x1 - 0.8f, y0 + H + 0.16f, z1 - 0.8f, 0.14f, PLASTIC_GREEN);
            for (float x : new float[]{x0 - 0.3f, x1 + 0.04f}) b.bevelBox(x, y0 + H - 1.8f, z0 + 2.2f, x + 0.26f, y0 + H + 0.06f, z1 - 2.2f, 0.08f, WHITE);
        }
        decal(b, ETIQUETTE_DECHIREE, 'z', (x0 + x1) / 2, y0 + 3.4f, z1, 2.4f, 1.2f);
    }

    /** Deux bacs empilés : celui du haut ouvert (couvercle tombé à côté, fendu). */
    static void bacRangement(MeshBuilder b) {
        bacPlastique(b, 3.2f, 0, 0.6f, false);
        b.push().translate(8, 5.6f, 3.8f).rotateY(-6).translate(-8, -5.6f, -3.8f);
        bacPlastique(b, 3.3f, 5.78f, 0.7f, true);
        b.pop();
        b.push().translate(8.4f, 0, 11.0f).rotateY(12);
        b.bevelBox(-5.0f, 0, -3.4f, 5.0f, 0.6f, 3.4f, 0.22f, PLASTIC_GREEN);
        decal(b, FISSURE, 'y', 1.6f, 0.6f, 0.4f, 4.0f, 4.0f, 60);
        b.pop();
        decal(b, BOUE_2, 'z', 8.0f, 0.7f, 7.0f, 9.0f, 1.4f);
    }

    /** Casier de bouteilles (40 x 30 x 30 cm) ; quelques bouteilles manquent ou sont cassées (goulot brisé). */
    static void casier(MeshBuilder b, float x0, float y0, float z0, boolean plein, int graine) {
        float L = 6.4f, W = 4.8f, H = 3.4f, x1 = x0 + L, z1 = z0 + W;
        b.bevelBox(x0 + 0.1f, y0, z0 + 0.1f, x1 - 0.1f, y0 + 0.35f, z1 - 0.1f, 0.06f, PLASTIC_GREEN);
        float e = 0.32f;
        for (float y : new float[]{y0 + 0.35f, y0 + H - 0.7f}) {
            b.bevelBox(x0 + e, y, z1 - e, x1 - e, y + 0.7f, z1, 0.08f, PLASTIC_GREEN);
            b.bevelBox(x0 + e, y, z0, x1 - e, y + 0.7f, z0 + e, 0.08f, PLASTIC_GREEN);
            b.bevelBox(x0, y, z0 + e, x0 + e, y + 0.7f, z1 - e, 0.08f, PLASTIC_GREEN);
            b.bevelBox(x1 - e, y, z0 + e, x1, y + 0.7f, z1 - e, 0.08f, PLASTIC_GREEN);
        }
        for (float[] c : new float[][]{{x0 + 0.05f, z0 + 0.05f}, {x1 - e - 0.05f, z0 + 0.05f}, {x0 + 0.05f, z1 - e - 0.05f}, {x1 - e - 0.05f, z1 - e - 0.05f}}) {
            b.bevelBox(c[0], y0 + 0.35f, c[1], c[0] + e, y0 + H, c[1] + e, 0.06f, PLASTIC_GREEN);
        }
        if (!plein) return;
        java.util.Random r = new java.util.Random(graine);
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 3; k++) {
                float t = r.nextFloat();
                if (t < 0.22f) continue;                       // bouteille manquante
                float x = x0 + 0.95f + i * 1.5f, z = z0 + 0.9f + k * 1.5f;
                Mat m = (i + k) % 3 == 0 ? BOTTLE_BROWN : BOTTLE_GREEN;
                b.push().translate(x, y0 + 0.36f, z);
                if (t < 0.36f) {
                    // goulot brisé : bord dentelé
                    b.lathe(new float[][]{{0.5f, 0}, {0.5f, 2.9f}, {0.4f, 3.3f}, {0.34f, 3.0f}, {0.3f, 0.4f}}, 8, 0, 360, m, 1, 1, 0.05f, 2f, i * 7 + k, false);
                } else {
                    b.lathe(new float[][]{{0.5f, 0}, {0.5f, 2.9f}, {0.36f, 3.5f}, {0.18f, 4.0f}, {0.18f, 4.5f}, {0.22f, 4.52f}, {0.22f, 4.8f}, {0.01f, 4.81f}},
                            8, 0, 360, m, 1, 1, 0, 0, 0, false);
                }
                b.pop();
            }
        }
    }

    /** Deux casiers au sol, un troisième dessus ; une bouteille couchée et des éclats de verre devant. */
    static void caisseBouteilles(MeshBuilder b) {
        casier(b, 1.4f, 0, 0.6f, true, 3);
        casier(b, 8.2f, 0, 0.7f, true, 4);
        b.push().translate(8, 3.4f, 3.0f).rotateY(7).translate(-8, -3.4f, -3.0f);
        casier(b, 4.8f, 3.42f, 0.7f, true, 5);
        b.pop();
        b.push().translate(11.0f, 0.5f, 8.6f).rotateY(-35).rotateZ(90);
        b.lathe(new float[][]{{0.5f, -2.4f}, {0.5f, 0.5f}, {0.36f, 1.1f}, {0.18f, 1.6f}, {0.18f, 2.1f}, {0.22f, 2.12f}, {0.22f, 2.4f}, {0.01f, 2.41f}}, 8, 0, 360, BOTTLE_GREEN, 1, 1, 0, 0, 0, false);
        b.pop();
        debris(b, 6.0f, 9.2f, 1.8f, 7, BOTTLE_BROWN, 12);
        debris(b, 3.4f, 7.8f, 1.2f, 4, BOTTLE_GREEN, 13);
    }

    /** Sac de sport (60 x 30 x 30 cm) tassé contre le mur, fermeture à moitié ouverte d'où dépasse un t-shirt. */
    static void sacSport(MeshBuilder b) {
        int m0 = b.mark();
        float zc = 2.9f, yc = 2.3f;
        b.push().translate(8, yc, zc).rotateY(-5).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -5.0f}, {1.6f, -4.9f}, {2.3f, -4.4f}, {2.6f, -3.4f}, {2.6f, 3.4f}, {2.3f, 4.4f}, {1.6f, 4.9f}, {0.01f, 5.0f}},
                24, 0, 360, FABRIC_RED, 0.88f, 1.1f, 0.02f, 0.35f, 4, false);
        b.lathe(new float[][]{{2.64f, -4.9f}, {2.64f, -3.4f}}, 24, 0, 360, DARK, 0.89f, 1.11f, 0, 0, 0, false);
        b.lathe(new float[][]{{2.64f, 3.4f}, {2.64f, 4.9f}}, 24, 0, 360, DARK, 0.89f, 1.11f, 0, 0, 0, false);
        b.pop();
        glissiere(b, 4.0f, 8.6f, yc + 2.25f, zc + 0.3f);
        b.push().translate(10.0f, yc + 2.3f, zc + 0.2f).scale(1.6f, 0.55f, 0.9f);
        b.color(0.72f, 0.74f, 0.7f);
        b.sphereSection(1.2f, 10, 5, 0, 90, TISSU_USE);
        b.color(1);
        b.pop();
        anse(b, 5.6f, 7.4f, yc + 2.1f, zc - 0.4f, 1.0f, DARK);
        anse(b, 8.6f, 10.4f, yc + 2.1f, zc - 0.4f, 1.0f, DARK);
        pochoir(b, S_SPRINT, 'z', 8, yc + 0.1f, zc + 2.9f, 3.4f, 0.85f);
        caler(b, m0, 0.05f);
        boue(b, 'z', 8, 0, 5.4f, 8.4f, 1.2f, 0);
    }

    /** Malle de voyage (90 x 50 x 60 cm) contre le mur : couvercle bombé entrouvert, sangle cassée, coins ternis. */
    static void malle(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.5f, z1 = 8.5f, yc = 6.6f, h = 9.6f;
        b.bevelBox(x0, 0, z0, x1, yc, z1, 0.2f, PINE);
        b.box(x0 + 0.4f, yc - 0.04f, z0 + 0.4f, x1 - 0.4f, yc + 0.1f, z1 - 0.4f, INTERIEUR_SALE);
        float zm = (z0 + z1) / 2;
        // couvercle soulevé de quelques degrés sur sa charnière arrière
        b.push().translate(0, yc, z0).rotateX(-7).translate(0, -yc, -z0);
        b.extrudeProfileX(new float[][]{{z0, yc}, {z0 + 0.3f, yc + 1.6f}, {z0 + 1.8f, h - 0.3f}, {zm, h}, {z1 - 1.8f, h - 0.3f}, {z1 - 0.3f, yc + 1.6f}, {z1, yc}}, x0, x1, PINE);
        b.face(PINE, MeshBuilder.v(-1, 0, 0), p(x0, yc, z0), p(x0, yc, z1), p(x0, yc + 1.6f, z1 - 0.3f), p(x0, h - 0.3f, z1 - 1.8f), p(x0, h, zm), p(x0, h - 0.3f, z0 + 1.8f), p(x0, yc + 1.6f, z0 + 0.3f));
        b.face(PINE, MeshBuilder.v(1, 0, 0), p(x1, yc, z1), p(x1, yc, z0), p(x1, yc + 1.6f, z0 + 0.3f), p(x1, h - 0.3f, z0 + 1.8f), p(x1, h, zm), p(x1, h - 0.3f, z1 - 1.8f), p(x1, yc + 1.6f, z1 - 0.3f));
        b.face(PINE, MeshBuilder.v(0, -1, 0), p(x0, yc, z0), p(x1, yc, z0), p(x1, yc, z1), p(x0, yc, z1));
        b.pop();
        // sangles : la droite est cassée et pend
        b.bevelBox(3.2f, -0.04f, z1 - 0.02f, 4.1f, yc + 0.1f, z1 + 0.14f, 0.05f, LEATHER);
        b.bevelBox(3.2f, -0.04f, z0 - 0.14f, 4.1f, yc + 0.1f, z0 + 0.02f, 0.05f, LEATHER);
        b.bevelBox(11.9f, -0.04f, z1 - 0.02f, 12.8f, 3.4f, z1 + 0.14f, 0.05f, LEATHER);
        b.push().translate(12.35f, 3.4f, z1 + 0.14f).rotateX(-160);
        b.bevelBox(-0.45f, 0, -0.08f, 0.45f, 2.4f, 0.06f, 0.05f, LEATHER);
        b.pop();
        coins(b, x0, 0, z0, x1, yc, z1, 0.9f, BRASS);
        b.bevelBox(7.2f, yc - 1.8f, z1 + 0.06f, 8.8f, yc - 0.2f, z1 + 0.3f, 0.1f, BRASS);
        coulure(b, 'z', 8.0f, yc - 1.9f, z1 + 0.04f, 1.2f, 2);
        poigneeU(b, x1 + 0.08f, 5.0f, zm, 3.2f, 1, LEATHER);
        poigneeU(b, x0 - 0.08f, 5.0f, zm, 3.2f, -1, LEATHER);
        decal(b, ETIQUETTE_DECHIREE, 'z', 10.4f, 3.2f, z1, 2.4f, 1.2f, -6);
        boue(b, 'z', 8, 0, z1, 13.0f, 1.4f, 1);
    }

    /** Seau galvanisé (30 cm). */
    static void seauSimple(MeshBuilder b, float x, float z, boolean eau) {
        b.push().translate(x, 0, z);
        b.lathe(new float[][]{{2.0f, 0}, {2.15f, 0.25f}, {2.7f, 4.6f}, {2.85f, 4.85f}}, 20, 0, 360, GALVA, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{2.7f, 4.75f}, {2.1f, 0.4f}}, 20, 0, 360, GALVA, 1, 1, 0, 0, 0, true);
        b.disc(2.0f, 0.02f, 20, GALVA, false);
        for (float y : new float[]{1.5f, 3.4f}) {
            b.push().translate(0, y, 0);
            b.torus(2.28f + (y - 1.5f) * 0.12f, 0.08f, 20, 4, 0, 360, GALVA);
            b.pop();
        }
        if (eau) {
            b.push().translate(0, 3.6f, 0);
            b.disc(2.5f, 0, 20, MOISI, true);
            b.pop();
        }
        b.pop();
        b.tube(new float[][]{p(x - 2.7f, 4.3f, z), p(x - 1.6f, 6.6f, z + 0.6f), p(x, 7.4f, z + 0.9f), p(x + 1.6f, 6.6f, z + 0.6f), p(x + 2.7f, 4.3f, z)},
                new float[]{0.09f, 0.09f, 0.09f, 0.09f, 0.09f}, 5, ROUILLE, false, false, 0, false);
    }

    /** Deux seaux (l'un retourné, cabossé) et un balai-serpillière appuyé contre le mur. */
    static void seau(MeshBuilder b) {
        seauSimple(b, 6.0f, 3.6f, true);
        int m1 = b.mark();
        b.push().translate(12.0f, 4.85f, 4.0f).rotateZ(180).rotateX(4).translate(-12.0f, 0, -4.0f);
        b.push().translate(12.0f, 0, 4.0f).scale(1, 1, 0.9f);
        b.lathe(new float[][]{{2.0f, 0}, {2.15f, 0.25f}, {2.7f, 4.6f}, {2.85f, 4.85f}}, 20, 0, 360, GALVA_ROUILLE, 1, 1, 0.04f, 1.2f, 5, false);
        b.disc(2.0f, 0.02f, 20, GALVA_ROUILLE, false);
        b.pop();
        b.pop();
        float[] bo = b.bounds(m1);
        b.offset(m1, 0, -bo[1], 0);
        // manche appuyé : son haut touche le mur (z = 0,3), rien derrière
        int m2 = b.mark();
        b.push().translate(6.6f, 0.4f, 3.6f).rotateX(-9.6f).rotateZ(5);
        b.cylinder(0.22f, 0, 19.0f, 8, PINE, null, PINE);
        b.push().translate(0, 0.2f, 0);
        b.color(0.7f, 0.66f, 0.58f);
        b.lathe(new float[][]{{1.8f, -0.4f}, {1.4f, 0.4f}, {0.5f, 1.2f}, {0.25f, 1.6f}}, 14, 0, 360, BURLAP, 1, 0.8f, 0.08f, 1.5f, 3, false);
        b.color(1);
        b.pop();
        b.pop();
        float[] bm = b.bounds(m2);
        b.offset(m2, 0, Math.max(0, 0.02f - bm[1]), Math.max(0, 0.05f - bm[2]));
    }

    /** Casier à lait (33 cm). */
    static void casierLait(MeshBuilder b, float x0, float y0, float z0, boolean bouteilles, boolean cassee) {
        float L = 5.4f, H = 5.0f, x1 = x0 + L, z1 = z0 + L, e = 0.32f;
        b.bevelBox(x0 + 0.08f, y0, z0 + 0.08f, x1 - 0.08f, y0 + 0.32f, z1 - 0.08f, 0.06f, PLASTIC_BLUE);
        for (float y : new float[]{y0 + 0.32f, y0 + H - 0.8f}) {
            if (!(cassee && y > y0 + 1)) b.bevelBox(x0 + e, y, z1 - e, x1 - e, y + 0.8f, z1, 0.08f, PLASTIC_BLUE);
            b.bevelBox(x0 + e, y, z0, x1 - e, y + 0.8f, z0 + e, 0.08f, PLASTIC_BLUE);
            b.bevelBox(x0, y, z0 + e, x0 + e, y + 0.8f, z1 - e, 0.08f, PLASTIC_BLUE);
            b.bevelBox(x1 - e, y, z0 + e, x1, y + 0.8f, z1 - e, 0.08f, PLASTIC_BLUE);
        }
        for (float k = 0.9f; k < L - 0.6f; k += 1.1f) {
            if (!(cassee && k > 2)) b.bevelBox(x0 + k, y0 + 1.12f, z1 - e + 0.04f, x0 + k + 0.3f, y0 + H - 0.8f, z1 - 0.04f, 0.05f, PLASTIC_BLUE);
            b.bevelBox(x0 + k, y0 + 1.12f, z0 + 0.04f, x0 + k + 0.3f, y0 + H - 0.8f, z0 + e - 0.04f, 0.05f, PLASTIC_BLUE);
        }
        for (float[] c : new float[][]{{x0 + 0.04f, z0 + 0.04f}, {x1 - e - 0.04f, z0 + 0.04f}, {x0 + 0.04f, z1 - e - 0.04f}, {x1 - e - 0.04f, z1 - e - 0.04f}}) {
            b.bevelBox(c[0], y0 + 0.32f, c[1], c[0] + e, y0 + H, c[1] + e, 0.05f, PLASTIC_BLUE);
        }
        if (!bouteilles) return;
        int n = 0;
        for (float[] q : new float[][]{{1.4f, 1.4f}, {4.0f, 1.4f}, {1.4f, 4.0f}, {4.0f, 4.0f}}) {
            if (n++ == 3 && cassee) continue;
            b.push().translate(x0 + q[0], y0 + 0.33f, z0 + q[1]);
            b.lathe(new float[][]{{0.95f, 0}, {0.95f, 4.0f}, {0.65f, 5.0f}, {0.45f, 5.8f}, {0.5f, 5.82f}, {0.5f, 6.1f}, {0.01f, 6.11f}}, 10, 0, 360, MILK, 1, 1, 0, 0, 0, false);
            b.pop();
        }
    }

    /** Deux casiers à lait empilés, un troisième (fendu) au pied ; une bouteille brisée devant. */
    static void caisseLait(MeshBuilder b) {
        casierLait(b, 2.4f, 0, 0.6f, false, false);
        b.push().translate(5.1f, 5.0f, 3.3f).rotateY(8).translate(-5.1f, -5.0f, -3.3f);
        casierLait(b, 2.4f, 5.02f, 0.6f, true, false);
        b.pop();
        casierLait(b, 9.0f, 0, 2.2f, true, true);
        pochoir(b, S_LAIT, 'z', 5.1f, 4.6f, 6.01f, 2.4f, 0.6f);
        debris(b, 12.0f, 10.4f, 1.6f, 8, MILK, 9);
    }

    /** Coffre de jardin (1,45 x 0,6 x 0,6 m) sur deux blocs : plastique passé, couvercle fendu, mousse, feuilles. */
    static void coffreJardin(MeshBuilder b) {
        float x0 = 4.4f, x1 = 27.6f, z0 = 0.5f, z1 = 9.7f, yc = 8.0f, h = 9.6f;
        b.bevelBox(x0, 0, z0, x1, yc, z1, 0.4f, PLASTIC_GREEN);
        for (float y = 1.0f; y < yc - 0.6f; y += 1.5f) b.bevelBox(x0 + 0.5f, y, z1 - 0.02f, x1 - 0.5f, y + 1.0f, z1 + 0.18f, 0.2f, PLASTIC_GREEN);
        b.push().translate(0, yc, z0).rotateX(-3).translate(0, -yc, -z0);
        b.bevelBox(x0 - 0.3f, yc + 0.02f, z0 - 0.3f + 0.3f, x1 + 0.3f, h, z1 + 0.5f, 0.5f, PLASTIC_GREEN);
        decal(b, FISSURE, 'y', 20.0f, h, 5.0f, 6.0f, 6.0f, 35);
        jonchee(b, x0 + 1, z0 + 1.5f, x1 - 1, z1 - 0.5f, 9, FEUILLES, 1.3f, 41);
        b.pop();
        for (float x : new float[]{9.0f, 23.0f}) b.bevelBox(x - 1.0f, yc - 1.0f, z1 + 0.2f, x + 1.0f, yc - 0.4f, z1 + 0.65f, 0.15f, DARK);
        poigneeU(b, x1 + 0.08f, 6.4f, (z0 + z1) / 2, 3.2f, 1, DARK);
        decal(b, MOUSSE_VERTE, 'z', 8.0f, 0.9f, z1 + 0.18f, 5.0f, 1.8f);
        decal(b, MOUSSE_VERTE, 'z', 22.0f, 0.8f, z1 + 0.18f, 4.0f, 1.6f, 180);
        boue(b, 'z', 15.5f, 0, z1 + 0.18f, 8.0f, 1.4f, 1);
    }

    /** Boîte à chaussures (33 x 20 x 12 cm) ; ecrasee : affaissée ; sans couvercle : couvercle posé à côté. */
    static void boiteChaussures(MeshBuilder b, float x0, float y0, float z0, Mat m, boolean couvercle) {
        float L = 5.3f, W = 3.2f, H = 2.0f;
        b.bevelBox(x0, y0, z0, x0 + L, y0 + H - 0.42f, z0 + W, 0.06f, m);
        if (couvercle) {
            b.bevelBox(x0 - 0.08f, y0 + H - 0.62f, z0 - 0.08f, x0 + L + 0.08f, y0 + H, z0 + W + 0.08f, 0.06f, m);
            pochoir(b, S_SPRINT, 'z', x0 + L / 2, y0 + H * 0.45f, z0 + W + 0.09f, 2.6f, 0.65f);
        } else {
            b.box(x0 + 0.1f, y0 + H - 0.47f, z0 + 0.1f, x0 + L - 0.1f, y0 + H - 0.36f, z0 + W - 0.1f, INTERIEUR_SALE);
        }
    }

    /** Deux colonnes de boîtes à chaussures contre le mur (l'une penche), une boîte ouverte tombée devant. */
    static void boitesChaussures(MeshBuilder b) {
        Mat[] c = {RED_BOX, BLUE_BOX, GREY_BOX, CARTON};
        for (int k = 0; k < 5; k++) boiteChaussures(b, 2.0f + (k % 2) * 0.2f, k * 2.02f, 0.6f + (k % 3) * 0.12f, c[k % 4], true);
        for (int k = 0; k < 4; k++) {
            // pile de travers : chaque boîte glisse un peu sur celle du dessous
            b.push().translate(11.0f, 0, 2.3f).rotateY(k * 3 - 4).translate(-11.0f, 0, -2.3f);
            boiteChaussures(b, 8.4f + k * 0.3f, k * 2.02f, 0.7f, c[(k + 2) % 4], k != 3);
            b.pop();
        }
        b.push().translate(11.0f, 0, 6.6f).rotateY(-24).translate(-11.0f, 0, -6.6f);
        boiteChaussures(b, 8.4f, 0, 5.0f, BLUE_BOX, false);
        b.pop();
        b.push().translate(4.2f, 0, 8.8f).rotateY(14);
        b.bevelBox(-2.7f, 0, -1.7f, 2.7f, 0.62f, 1.7f, 0.06f, BLUE_BOX);
        b.pop();
    }

    /** Besace de cuir appuyée contre le mur, bandoulière arrachée enroulée au sol. */
    static void sacoche(MeshBuilder b) {
        int m0 = b.mark();
        b.push().translate(7.0f, 0, 1.6f).rotateY(6).rotateX(-15);
        float w = 6.8f, e = 2.4f, h = 5.2f;
        b.bevelBox(-w / 2, 0, -e / 2, w / 2, h, e / 2, 0.7f, CUIR_USE);
        b.bevelBox(-w / 2 - 0.08f, h - 3.2f, e / 2 - 0.25f, w / 2 + 0.08f, h + 0.15f, e / 2 + 0.3f, 0.4f, CUIR_USE);
        for (float x : new float[]{-1.8f, 1.8f}) {
            b.bevelBox(x - 0.28f, 1.0f, e / 2 + 0.25f, x + 0.28f, h - 0.6f, e / 2 + 0.4f, 0.05f, DARK);
            b.bevelBox(x - 0.42f, 1.8f, e / 2 + 0.36f, x + 0.42f, 2.4f, e / 2 + 0.52f, 0.06f, BRASS);
        }
        b.pop();
        caler(b, m0, 0.05f);
        float[][] p = new float[18][];
        for (int k = 0; k < 18; k++) {
            double a = k / 17.0 * Math.PI * 1.7;
            p[k] = MeshBuilder.p(11.8f + (float) Math.cos(a) * 1.5f, 0.25f + k * 0.015f, 5.4f + (float) Math.sin(a) * 2.0f);
        }
        float[] rr = new float[18];
        java.util.Arrays.fill(rr, 0.24f);
        b.tube(p, rr, 6, CUIR_USE, true, true, 0, false);
    }

    // ------------------------------------------------------------------ anciennes signatures (utilisables par les lots)
    static void cagette(MeshBuilder b, float x0, float y0, float z0, float L, float W, float H, Mat m) {
        cagette(b, x0, y0, z0, L, W, H, m, false);
    }

    static void carton(MeshBuilder b, float x0, float y0, float z0, float w, float h, float d, boolean fragile) {
        carton(b, x0, y0, z0, w, h, d, fragile, false, CARTON);
    }

    static void bacPlastique(MeshBuilder b, float x0, float y0, float z0) {
        bacPlastique(b, x0, y0, z0, false);
    }

    static void casier(MeshBuilder b, float x0, float y0, float z0, boolean plein) {
        casier(b, x0, y0, z0, plein, 1);
    }

    static void casierLait(MeshBuilder b, float x0, float y0, float z0, boolean bouteilles) {
        casierLait(b, x0, y0, z0, bouteilles, false);
    }

    static void boiteChaussures(MeshBuilder b, float x0, float y0, float z0, Mat m) {
        boiteChaussures(b, x0, y0, z0, m, true);
    }
}
