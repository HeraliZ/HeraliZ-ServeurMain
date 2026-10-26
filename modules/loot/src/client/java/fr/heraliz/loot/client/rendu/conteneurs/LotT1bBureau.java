package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/** T1b — bureau, garage, salle de bain : bureau d'écolier, classeur, vestiaire, étagère, bibliothèque, vasque. */
final class LotT1bBureau {
    private LotT1bBureau() {
    }

    static void orange(MeshBuilder b) {
        b.color(0.86f, 0.52f, 0.3f);
    }

    /** Niche vide (intérieur d'un caisson vu par une ouverture) : cinq faces tournées vers l'intérieur. */
    static void niche(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, Mat m) {
        b.face(m, v(0, 0, 1), p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0));
        b.face(m, v(1, 0, 0), p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
        b.face(m, v(-1, 0, 0), p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1));
        b.face(m, v(0, 1, 0), p(x0, y0, z1), p(x1, y0, z1), p(x1, y0, z0), p(x0, y0, z0));
        b.face(m, v(0, -1, 0), p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1));
    }

    // =================================================================== bureau d'écolier
    /**
     * Bureau d'écolier (90 x 50 x 75 cm) à piètement tubulaire orange passé : tiroir ouvert (crayons, gomme),
     * cahiers, cahier ouvert, pot à crayons, lampe d'architecte affaissée, dessin d'enfant ; cartable au sol.
     */
    static void bureauEcolier(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.6f, z1 = 8.6f, h = 12.0f;
        float[][] pieds = {{x0 + 0.5f, z0 + 0.5f}, {x1 - 0.5f, z0 + 0.5f}, {x0 + 0.5f, z1 - 0.5f}, {x1 - 0.5f, z1 - 0.5f}};
        for (float[] q : pieds) {
            b.push().translate(q[0], 0, q[1]);
            b.cylinder(0.3f, 0, 0.32f, 8, CAOUTCHOUC_USE, null, null);
            b.pop();
        }
        orange(b);
        for (float[] q : pieds) b.bevelBox(q[0] - 0.3f, 0.3f, q[1] - 0.3f, q[0] + 0.3f, h - 0.5f, q[1] + 0.3f, 0.1f, PEINT);
        for (float x : new float[]{x0 + 0.5f, x1 - 0.5f}) {
            b.bevelBox(x - 0.22f, 1.2f, z0 + 0.8f, x + 0.22f, 1.6f, z1 - 0.8f, 0.08f, PEINT);
            b.bevelBox(x - 0.22f, h - 1.2f, z0 + 0.8f, x + 0.22f, h - 0.52f, z1 - 0.8f, 0.08f, PEINT);
        }
        b.bevelBox(x0 + 0.8f, h - 1.2f, z0 + 0.3f, x1 - 0.8f, h - 0.52f, z0 + 0.7f, 0.08f, PEINT);
        b.color(0.9f, 0.86f, 0.74f);
        b.bevelBox(x0, h - 0.5f, z0, x1, h, z1, 0.12f, MELAMINE);
        b.color(1);
        b.box(x0 + 0.1f, h - 0.46f, z1, x1 - 0.1f, h - 0.06f, z1 + 0.06f, CAOUTCHOUC_USE);
        // caisson du tiroir (sous le plateau, à droite) et tiroir sorti
        float cx0 = 8.8f, cx1 = 14.4f, cy0 = h - 3.0f, cy1 = h - 0.52f;
        b.color(0.9f, 0.86f, 0.74f);
        b.box(cx0, cy0, z0 + 0.8f, cx0 + 0.25f, cy1, z1 - 0.3f, MELAMINE);
        b.box(cx1 - 0.25f, cy0, z0 + 0.8f, cx1, cy1, z1 - 0.3f, MELAMINE);
        b.box(cx0 + 0.25f, cy0, z0 + 0.8f, cx1 - 0.25f, cy0 + 0.2f, z1 - 0.3f, MELAMINE);
        b.box(cx0 + 0.25f, cy0 + 0.2f, z0 + 0.8f, cx1 - 0.25f, cy1, z0 + 1.0f, MELAMINE);
        float fz = z1 - 0.3f + 3.2f;
        b.box(cx0 + 0.45f, cy0 + 0.3f, fz - 6.0f, cx0 + 0.6f, cy1 - 0.7f, fz - 0.3f, MELAMINE);
        b.box(cx1 - 0.6f, cy0 + 0.3f, fz - 6.0f, cx1 - 0.45f, cy1 - 0.7f, fz - 0.3f, MELAMINE);
        b.box(cx0 + 0.6f, cy0 + 0.3f, fz - 6.0f, cx1 - 0.6f, cy0 + 0.42f, fz - 0.3f, MELAMINE);
        b.box(cx0 + 0.6f, cy0 + 0.42f, fz - 6.0f, cx1 - 0.6f, cy1 - 0.7f, fz - 5.85f, MELAMINE);
        orange(b);
        b.bevelBox(cx0 + 0.3f, cy0 + 0.25f, fz - 0.3f, cx1 - 0.3f, cy1 - 0.2f, fz, 0.08f, PEINT);
        b.color(1);
        poigneeBarre(b, 10.6f, 12.6f, cy0 + 1.6f, fz, CHROME_PIQUE);
        decal(b, AUTOCOLLANTS, 'z', 11.6f, cy0 + 0.75f, fz, 3.4f, 1.0f, 2);
        int[] cc = {0xB84A3A, 0x3A6AA8, 0xD8B040, 0x4A8A4A};
        for (int k = 0; k < 4; k++) {
            teinte(b, cc[k]);
            b.push().translate(10.0f + k * 0.9f, cy0 + 0.6f, fz - 2.6f).rotateY(80 + k * 9).rotateZ(90).translate(0, -1.6f, 0);
            b.cylinder(0.16f, 0, 3.2f, 6, PLAST, PLAST, PLAST);
            b.pop();
        }
        b.color(0.86f, 0.55f, 0.55f);
        b.bevelBox(12.6f, cy0 + 0.42f, fz - 1.8f, 13.6f, cy0 + 0.82f, fz - 1.2f, 0.12f, PLAST);
        b.color(1);
        // dessus : pile de cahiers, cahier ouvert, pot à crayons, règle, dessin
        b.push().translate(11.6f, h, 3.4f).rotateY(6);
        livreCouche(b, -1.6f, 0.01f, -2.0f, 3.2f, 0.22f, 4.0f, 0x3A5A94);
        livreCouche(b, -1.5f, 0.23f, -1.9f, 3.2f, 0.22f, 4.0f, 0xB84A3A);
        livreCouche(b, -1.7f, 0.45f, -2.1f, 3.2f, 0.22f, 4.0f, 0x4A8A4A);
        decal(b, CAHIER, 'y', -0.1f, 0.67f, -0.1f, 3.0f, 1.5f, 90);
        b.pop();
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(6.2f, h, 5.0f).rotateY(-12).rotateZ(s * 3);
            b.box(s < 0 ? -2.8f : 0, 0.02f, -1.9f, s < 0 ? 0 : 2.8f, 0.14f, 1.9f, PAGES);
            decal(b, SEYES, 'y', s * 1.4f, 0.14f, 0, 2.6f, 3.6f);
            b.pop();
        }
        b.push().translate(2.6f, h, 5.6f);
        b.color(0.72f, 0.3f, 0.26f);
        b.lathe(new float[][]{{0.01f, 0}, {0.6f, 0.02f}, {0.62f, 1.7f}}, 12, 0, 360, PLAST, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.58f, 1.7f}, {0.55f, 0.2f}}, 12, 0, 360, PLAST, 1, 1, 0, 0, 0, true);
        b.disc(0.56f, 0.2f, 12, PLAST, true);
        for (int k = 0; k < 4; k++) {
            teinte(b, cc[k]);
            b.push().translate(-0.25f + (k % 2) * 0.45f, 0.2f, -0.2f + (k / 2) * 0.4f).rotateZ(-12 + k * 8).rotateX(-8 + k * 5);
            b.cylinder(0.1f, 0, 2.8f, 6, PLAST, null, AtlasCivil.DARK);
            b.pop();
        }
        b.color(1);
        b.pop();
        b.color(0.9f, 0.84f, 0.5f);
        b.push().translate(9.4f, h + 0.02f, 7.4f).rotateY(-8);
        b.box(-2.4f, 0, -0.3f, 2.4f, 0.05f, 0.3f, PLAST);
        b.pop();
        b.color(1);
        b.push().translate(4.0f, h + 0.05f, 2.3f).rotateY(16).rotateX(-90);
        b.decalDouble(DESSIN, 2.6f, 2.6f);
        b.pop();
        decal(b, GRIFFURES, 'y', 9.0f, h, 4.6f, 2.6f, 1.0f, 30);
        decal(b, AUREOLE, 'y', 13.4f, h, 7.2f, 1.6f, 1.4f);
        // lampe d'architecte au bras affaissé, tête tombée sur le plateau
        b.color(0.32f, 0.42f, 0.36f);
        b.push().translate(1.8f, h, 1.6f);
        b.cylinder(1.0f, 0, 0.35f, 14, PEINT, null, PEINT);
        b.pop();
        b.tube(new float[][]{p(1.8f, h + 0.35f, 1.6f), p(2.3f, h + 2.4f, 1.8f), p(2.8f, h + 4.1f, 2.0f)}, fill(3, 0.13f), 6, PEINT, false, true, 0, false);
        b.tube(new float[][]{p(2.8f, h + 4.1f, 2.0f), p(4.0f, h + 3.4f, 3.0f), p(5.0f, h + 2.2f, 3.6f)}, fill(3, 0.12f), 6, PEINT, true, true, 0, false);
        b.push().translate(5.0f, h + 2.2f, 3.6f).rotateZ(-140).rotateX(10);
        b.lathe(new float[][]{{0.25f, 0}, {0.45f, 0.3f}, {1.0f, 1.3f}, {1.1f, 1.5f}}, 12, 0, 360, PEINT, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.05f, 1.48f}, {0.42f, 0.32f}}, 12, 0, 360, AtlasCivil.WHITE, 1, 1, 0, 0, 0, true);
        b.pop();
        b.color(1);
        b.tube(new float[][]{p(1.6f, h + 0.2f, 1.0f), p(1.2f, h - 0.2f, 0.5f), p(1.4f, 6.0f, 0.4f), p(1.6f, 0.12f, 0.8f)}, fill(4, 0.08f), 4, AtlasCivil.DARK, false, true, 0, false);
        // cartable couché au sol devant
        b.push().translate(4.4f, 0, 11.6f).rotateY(18);
        b.color(0.62f, 0.3f, 0.22f);
        b.bevelBox(-2.8f, 0, -2.1f, 2.8f, 1.8f, 2.1f, 0.5f, CUIR_USE);
        b.bevelBox(-2.7f, 1.6f, -0.4f, 2.7f, 1.95f, 2.25f, 0.2f, CUIR_USE);
        b.color(1);
        for (float x : new float[]{-1.4f, 1.4f}) b.bevelBox(x - 0.3f, 1.0f, 2.18f, x + 0.3f, 1.6f, 2.3f, 0.05f, AtlasCivil.BRASS);
        b.tube(new float[][]{p(-1.0f, 1.8f, -1.6f), p(-0.6f, 2.5f, -1.6f), p(0.6f, 2.5f, -1.6f), p(1.0f, 1.8f, -1.6f)}, fill(4, 0.14f), 6, CUIR_USE, false, false, 0, false);
        b.pop();
    }

    // =================================================================== armoire à dossiers
    /**
     * Classeur métallique 4 tiroirs (47 x 62 x 132 cm) gris beige : tiroir du 3e niveau grand ouvert sur ses dossiers
     * suspendus, tiroir du 2e entrouvert, tiroir du bas cabossé ; rouille, papiers au sol, dossier tombé.
     */
    static void classeurMetal(MeshBuilder b) {
        float x0 = 4.2f, x1 = 11.7f, z0 = 0.4f, z1 = 10.3f, h = 21.1f, s = 0.35f, xc = (x0 + x1) / 2;
        b.color(0.94f, 0.92f, 0.86f);
        b.bevelBox(x0, 0, z0, x1, h, z1, 0.15f, TOLE, null, MeshBuilder.SKIP_PZ);
        b.box(x0 + 0.15f, 0, z1 - 0.3f, x0 + s, h - 0.15f, z1, TOLE);
        b.box(x1 - s, 0, z1 - 0.3f, x1 - 0.15f, h - 0.15f, z1, TOLE);
        b.color(0.4f);
        b.box(x0 + s, 0.15f, z1 - 0.6f, x1 - s, 0.6f, z1 - 0.3f, TOLE);
        b.color(0.94f, 0.92f, 0.86f);
        for (int k = 0; k <= 4; k++) {
            float y = 0.6f + k * 5.05f;
            b.box(x0 + s, Math.max(0.15f, y - 0.2f), z1 - 0.3f, x1 - s, k == 4 ? h - 0.15f : y, z1, TOLE);
        }
        b.color(1);
        for (int k = 0; k < 4; k++) {
            float ya = 0.6f + k * 5.05f, yb = ya + 4.85f;
            float sortie = k == 2 ? 5.0f : (k == 1 ? 1.2f : 0);
            float zf = z1 - 0.05f + sortie;
            if (sortie > 0) {
                niche(b, x0 + s, ya, z0 + 0.4f, x1 - s, yb, z1 - 0.3f, INTERIEUR_SALE);
                float prof = k == 2 ? 9.0f : 4.0f;
                b.color(0.86f, 0.84f, 0.78f);
                b.box(x0 + s + 0.3f, ya + 0.3f, zf - prof, x0 + s + 0.4f, yb - 0.9f, zf, TOLE);
                b.box(x1 - s - 0.4f, ya + 0.3f, zf - prof, x1 - s - 0.3f, yb - 0.9f, zf, TOLE);
                b.box(x0 + s + 0.4f, ya + 0.3f, zf - prof, x1 - s - 0.4f, ya + 0.4f, zf, TOLE);
                b.box(x0 + s + 0.4f, ya + 0.4f, zf - prof, x1 - s - 0.4f, yb - 0.9f, zf - prof + 0.1f, TOLE);
                b.color(1);
            }
            if (sortie == 0) b.face(INTERIEUR_SALE, v(0, 0, 1), p(x0 + s, ya, z1 - 0.35f), p(x1 - s, ya, z1 - 0.35f), p(x1 - s, yb, z1 - 0.35f), p(x0 + s, yb, z1 - 0.35f));
            if (k == 2) dossiers(b, x0 + s + 0.4f, x1 - s - 0.4f, ya, yb, zf);
            b.push();
            if (k == 0) b.translate(xc, ya + 2.4f, zf).rotateY(2.5f).rotateX(-2.5f).translate(-xc, -(ya + 2.4f), -zf);
            b.color(0.94f, 0.92f, 0.86f);
            b.bevelBox(x0 + s + 0.03f, ya + 0.08f, zf, x1 - s - 0.03f, yb - 0.08f, zf + 0.35f, 0.12f, TOLE);
            b.color(1);
            b.box(xc - 1.2f, yb - 1.5f, zf + 0.35f, xc + 1.2f, yb - 0.7f, zf + 0.39f, METAL_NU);
            b.box(xc - 1.0f, yb - 1.4f, zf + 0.39f, xc + 1.0f, yb - 0.8f, zf + 0.45f, k == 1 ? ETIQUETTE_DECHIREE.asFit() : PAPIER);
            poigneeBarre(b, xc - 1.3f, xc + 1.3f, yb - 2.3f, zf + 0.35f, CHROME_PIQUE);
            if (k == 3) {
                b.push().translate(x1 - s - 0.9f, yb - 0.6f, zf + 0.35f).rotateX(90);
                b.cylinder(0.3f, 0, 0.2f, 10, CHROME_PIQUE, null, AtlasCivil.DARK);
                b.pop();
            }
            if (k == 0) {
                decal(b, GRIFFURES, 'z', xc - 0.8f, ya + 1.6f, zf + 0.35f, 3.0f, 1.2f, -14);
                coulure(b, 'z', x1 - s - 0.6f, yb - 0.3f, zf + 0.35f, 0.8f, 1);
            }
            b.pop();
        }
        // dessus : classeur à levier, liasse
        b.push().translate(6.6f, h, 4.6f).rotateY(-14);
        b.color(0.3f, 0.4f, 0.62f);
        b.bevelBox(-1.6f, 0.01f, -2.2f, 1.6f, 0.75f, 2.2f, 0.08f, PLAST);
        b.color(1);
        b.box(-1.0f, 0.75f, -1.6f, 1.0f, 0.79f, 1.6f, PAPIER);
        b.pop();
        b.push().translate(9.6f, h, 6.8f).rotateY(20);
        b.box(-1.3f, 0.01f, -1.8f, 1.3f, 0.3f, 1.8f, PAPIER);
        b.pop();
        // usure : rouille, étiquette, boue ; papiers et dossier tombés
        coulure(b, 'X', x1, h - 0.3f, 3.0f, 1.8f, 0);
        coulure(b, 'X', x1, 11.0f, 7.2f, 1.4f, 2);
        coulure(b, 'x', x0, h - 0.3f, 6.0f, 1.6f, 1);
        decal(b, ETIQUETTE_DECHIREE, 'X', x1, 15.0f, 5.0f, 3.0f, 1.4f, 6);
        decal(b, GRIFFURES, 'x', x0, 8.0f, 4.0f, 3.0f, 1.2f, 20);
        boue(b, 'X', x1, 0, 5.4f, 9.0f, 1.6f, 0);
        boue(b, 'x', x0, 0, 5.4f, 9.0f, 1.4f, 1);
        papiers(b, 2.0f, 10.8f, 14.0f, 14.6f, 9, 61);
        b.push().translate(2.6f, 0.02f, 12.8f).rotateY(32);
        b.color(0.6f, 0.75f, 0.5f);
        b.box(-1.9f, 0, -1.5f, 1.9f, 0.08f, 1.5f, KRAFT);
        b.color(1);
        b.box(-1.6f, 0.08f, -1.3f, 1.5f, 0.14f, 1.25f, PAPIER);
        b.pop();
    }

    /** Dossiers suspendus dans un tiroir ouvert (tringles, onglets, feuilles qui dépassent). */
    static void dossiers(MeshBuilder b, float xa, float xb, float ya, float yb, float zf) {
        int[] tons = {0x9ABC88, 0xD4B88A, 0x9ABC88, 0xC8A070};
        int[] onglets = {0xC84A3A, 0x3A6AB0, 0xD8B040, 0xE6E4DE};
        for (int i = 0; i < 12; i++) {
            float z = zf - 0.8f - i * 0.66f;
            b.push().translate(0, ya + 0.45f, z).rotateX((i % 3 - 1) * 4);
            teinte(b, tons[i % 4]);
            b.box(xa + 0.15f, 0, -0.03f, xb - 0.15f, yb - ya - 1.5f - (i % 3) * 0.1f, 0.03f, KRAFT);
            b.color(1);
            b.box(xa - 0.05f, yb - ya - 1.55f, -0.1f, xb + 0.05f, yb - ya - 1.45f, 0.1f, METAL_NU);
            if (i % 2 == 0) {
                teinte(b, onglets[(i / 2) % 4]);
                float xt = xa + 0.6f + (i % 5) * 0.9f;
                b.box(xt, yb - ya - 1.48f, -0.04f, xt + 1.0f, yb - ya - 1.0f, 0.04f, PLAST);
                b.color(1);
            }
            if (i % 4 == 1) {
                b.push().translate((xa + xb) / 2 + 0.4f, 0.2f, 0.2f).rotateZ(-6);
                b.box(-1.5f, 0, -0.02f, 1.5f, yb - ya - 0.9f, 0.02f, PAPIER);
                b.pop();
            }
            b.pop();
        }
    }

    // =================================================================== vestiaire métallique
    /**
     * Casier de vestiaire double (60 x 50 x 180 cm), tôle bleu-gris : porte gauche fermée au cadenas scié, porte droite
     * forcée, entrouverte et pliée ; parka pendue, chaussures, casquette ; persiennes, rouille, autocollants.
     */
    static void vestiaire(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.4f, z1 = 8.4f, h = 28.8f, e = 0.3f, xm = 8.0f, yb = 0.8f;
        b.box(x0 + 0.3f, 0, z0 + 0.3f, x1 - 0.3f, yb, z1 - 0.6f, AtlasCivil.DARK);
        bleu(b);
        b.bevelBox(x0, yb, z0, x0 + e, h - e, z1, 0.08f, TOLE);
        b.bevelBox(x1 - e, yb, z0, x1, h - e, z1, 0.08f, TOLE);
        b.box(x0 + e, yb, z0, x1 - e, h - e, z0 + e, TOLE);
        b.bevelBox(x0, h - e, z0, x1, h, z1, 0.08f, TOLE);
        b.box(x0 + e, yb, z0 + e, x1 - e, yb + 0.5f, z1, TOLE);
        b.box(xm - 0.12f, yb + 0.5f, z0 + e, xm + 0.12f, h - e, z1, TOLE);
        b.box(xm + 0.12f, 24.0f, z0 + e, x1 - e, 24.2f, z1 - 0.4f, TOLE);
        b.color(1);
        // patère, parka pendue, manches ; casquette sur la tablette ; chaussures
        b.tube(new float[][]{p(10.4f, 23.2f, z0 + e), p(10.4f, 23.0f, z0 + 1.2f), p(10.4f, 23.5f, z0 + 1.6f)}, fill(3, 0.1f), 5, METAL_NU, false, true, 0, false);
        b.color(0.4f, 0.44f, 0.32f);
        b.push().translate(10.4f, 18.0f, 2.6f);
        b.blob(1.8f, 4.6f, 1.1f, 0.75f, 0.08f, 1.2f, 8, 12, TOILE);
        b.pop();
        b.push().translate(10.4f, 22.4f, 2.0f).scale(1, 0.7f, 1);
        b.sphereSection(1.1f, 10, 4, 0, 90, TOILE);
        b.pop();
        for (int sd = -1; sd <= 1; sd += 2) {
            b.tube(new float[][]{p(10.4f + sd * 1.5f, 21.2f, 2.6f), p(10.4f + sd * 1.8f, 18.0f, 2.8f), p(10.4f + sd * 1.7f, 14.6f, 2.9f)}, new float[]{0.55f, 0.5f, 0.45f}, 8, TOILE, true, true, 0, false);
        }
        b.color(0.25f, 0.3f, 0.4f);
        b.push().translate(9.8f, 24.2f, 3.6f).scale(1, 0.62f, 1.1f);
        b.sphereSection(1.0f, 10, 4, 0, 90, TOILE);
        b.pop();
        b.push().translate(9.8f, 24.24f, 4.6f);
        b.box(-0.9f, 0, 0, 0.9f, 0.1f, 1.3f, TOILE);
        b.pop();
        b.color(1);
        chaussure(b, 9.6f, yb + 0.5f, 4.0f, -80, 2, 0x3A2E26);
        chaussure(b, 11.5f, yb + 0.5f, 5.0f, -102, 0, 0xC8C4B8);
        // porte gauche fermée : persiennes, numéro, moraillon, cadenas scié
        bleu(b);
        b.bevelBox(x0 + 0.05f, yb + 0.6f, z1, xm - 0.06f, h - 0.4f, z1 + 0.3f, 0.1f, TOLE);
        b.color(1);
        float zl = z1 + 0.3f, xl = (x0 + xm) / 2;
        decal(b, PERSIENNES, 'z', xl, h - 3.0f, zl, 3.6f, 1.8f);
        decal(b, PERSIENNES, 'z', xl, yb + 2.6f, zl, 3.6f, 1.8f);
        decal(b, NUMEROS.sub(0, 0, 0.5f, 1), 'z', xl, h - 5.6f, zl, 1.6f, 1.2f);
        decal(b, AUTOCOLLANTS, 'z', xl, 18.0f, zl, 3.6f, 1.8f, -8);
        decal(b, GRIFFURES, 'z', xl, 9.0f, zl, 3.0f, 1.2f, 25);
        coulure(b, 'z', xl - 1.0f, h - 4.1f, zl, 1.0f, 0);
        coulure(b, 'z', xl + 1.2f, yb + 1.6f, zl, 0.7f, 2);
        b.bevelBox(xm - 0.9f, 14.2f, zl - 0.02f, xm - 0.3f, 15.6f, zl + 0.35f, 0.08f, AtlasCivil.DARK);
        b.box(xm - 0.3f, 14.6f, zl - 0.02f, xm + 0.5f, 15.2f, zl + 0.12f, METAL_NU);
        b.push().translate(xm + 0.2f, 14.9f, zl + 0.3f).rotateZ(-25);
        b.bevelBox(-0.55f, -1.9f, -0.2f, 0.55f, -0.6f, 0.2f, 0.12f, AtlasCivil.BRASS);
        b.push().translate(0, -0.6f, 0).rotateX(90);
        b.torus(0.38f, 0.09f, 12, 4, 15, 120, METAL_NU);
        b.pop();
        b.pop();
        // porte droite forcée : entrouverte, pliée en deux (bas tordu)
        b.push().translate(x1, 0, z1).rotateY(42).translate(-x1, 0, -z1);
        b.push().translate(x1, 13.0f, z1).rotateY(7).rotateX(-4).translate(-x1, -13.0f, -z1);
        bleu(b);
        b.bevelBox(xm + 0.06f, yb + 0.6f, z1, x1 - 0.05f, 13.0f, z1 + 0.3f, 0.1f, TOLE);
        b.color(1);
        decal(b, PERSIENNES, 'z', (xm + x1) / 2, yb + 2.6f, zl, 3.6f, 1.8f);
        decal(b, IMPACTS, 'z', (xm + x1) / 2, 8.0f, zl, 3.4f, 1.7f, 10);
        b.pop();
        bleu(b);
        b.bevelBox(xm + 0.06f, 12.98f, z1, x1 - 0.05f, h - 0.4f, z1 + 0.3f, 0.1f, TOLE);
        b.color(1);
        decal(b, PERSIENNES, 'z', (xm + x1) / 2, h - 3.0f, zl, 3.6f, 1.8f);
        decal(b, NUMEROS.sub(0.5f, 0, 1, 1), 'z', (xm + x1) / 2, h - 5.6f, zl, 1.6f, 1.2f);
        b.box(xm + 0.3f, 14.2f, zl - 0.02f, xm + 0.9f, 15.6f, zl + 0.3f, AtlasCivil.DARK);
        b.pop();
        coulure(b, 'X', x1, h - 0.4f, 3.0f, 1.8f, 1);
        coulure(b, 'x', x0, 12.0f, 5.0f, 1.4f, 0);
        boue(b, 'X', x1, yb, 4.4f, 7.6f, 1.6f, 0);
        boue(b, 'x', x0, yb, 4.4f, 7.6f, 1.6f, 1);
        decal(b, ETIQUETTE_DECHIREE, 'x', x0, 20.0f, 4.0f, 3.0f, 1.4f);
    }

    static void bleu(MeshBuilder b) {
        b.color(0.62f, 0.72f, 0.8f);
    }

    // =================================================================== étagère de garage
    /**
     * Étagère métallique de garage (90 x 40 x 180 cm), cornières rouillées, une tablette affaissée ; pots de peinture,
     * jerrican, cartons, caisse à outils, rallonge, bidons, bâche ; pot renversé et flaque séchée, marteau, scie pendue.
     */
    static void etagereGarage(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 0.5f, z1 = 6.9f, h = 28.8f, c = 0.6f, t = 0.1f;
        for (float x : new float[]{x0, x1 - c}) {
            b.box(x, 0, z0, x + c, h, z0 + t, GALVA_ROUILLE);
            b.box(x, 0, z1 - t, x + c, h, z1, GALVA_ROUILLE);
        }
        for (float x : new float[]{x0, x1 - t}) {
            b.box(x, 0, z0 + t, x + t, h, z0 + c, GALVA_ROUILLE);
            b.box(x, 0, z1 - c, x + t, h, z1 - t, GALVA_ROUILLE);
        }
        b.tube(new float[][]{p(x0 + 0.5f, 1.2f, z0 + 0.25f), p(x1 - 0.5f, h - 1.2f, z0 + 0.25f)}, fill(2, 0.07f), 4, METAL_NU, false, false, 0, false);
        b.tube(new float[][]{p(x1 - 0.5f, 1.2f, z0 + 0.3f), p(x0 + 0.5f, h - 1.2f, z0 + 0.3f)}, fill(2, 0.07f), 4, METAL_NU, false, false, 0, false);
        float[] ys = {0.9f, 7.7f, 14.5f, 21.3f, 28.2f};
        for (int k = 0; k < ys.length; k++) {
            b.push();
            if (k == 2) b.translate(x1, ys[k], 0).rotateZ(3).translate(-x1, -ys[k], 0);
            tablette(b, x0 + 0.12f, x1 - 0.12f, ys[k], z0 + 0.12f, z1 - 0.12f);
            float y = ys[k] + 0.12f;
            switch (k) {
                case 0 -> {
                    potPeinture(b, 2.8f, y, 3.6f, 0xE6E2D8);
                    potPeinture(b, 5.8f, y, 3.4f, 0x6E8AA6);
                    b.color(0.72f, 0.24f, 0.2f);
                    b.bevelBox(9.0f, y, 2.0f, 13.0f, y + 4.6f, 3.9f, 0.3f, PLAST);
                    b.tube(new float[][]{p(9.6f, y + 4.5f, 2.95f), p(10.0f, y + 5.4f, 2.95f), p(11.2f, y + 5.4f, 2.95f), p(11.6f, y + 4.5f, 2.95f)}, fill(4, 0.25f), 6, PLAST, false, false, 0, false);
                    b.push().translate(12.4f, y + 4.5f, 2.95f).rotateZ(-20);
                    b.cylinder(0.32f, 0, 1.0f, 8, PLAST, null, AtlasCivil.DARK);
                    b.pop();
                    b.color(1);
                }
                case 1 -> {
                    b.push().translate(4.4f, y, 3.6f).rotateY(4);
                    b.bevelBox(-3.0f, 0, -2.5f, 3.0f, 4.0f, 2.6f, 0.12f, AtlasCivil.CARTON);
                    decal(b, T_PAPIERS, 'z', 0, 2.0f, 2.6f, 4.6f, 1.15f, -3);
                    b.pop();
                    b.color(0.68f, 0.22f, 0.18f);
                    b.bevelBox(9.0f, y, 2.0f, 14.6f, y + 2.4f, 5.0f, 0.15f, PEINT);
                    b.bevelBox(9.1f, y + 2.38f, 2.0f, 14.5f, y + 2.8f, 5.0f, 0.12f, PEINT);
                    b.color(1);
                    b.tube(new float[][]{p(10.4f, y + 2.8f, 3.5f), p(10.6f, y + 3.6f, 3.5f), p(13.0f, y + 3.6f, 3.5f), p(13.2f, y + 2.8f, 3.5f)}, fill(4, 0.15f), 6, AtlasCivil.DARK, false, false, 0, false);
                    b.push().translate(11.8f, y + 0.4f, 6.0f).rotateY(-20);
                    b.bevelBox(-2.2f, -0.25f, -0.25f, 1.0f, 0.25f, 0.25f, 0.1f, BOIS_GRIS);
                    b.bevelBox(1.0f, -0.4f, -0.6f, 1.6f, 0.4f, 0.6f, 0.1f, METAL_NU);
                    b.pop();
                }
                case 2 -> {
                    b.color(0.84f, 0.52f, 0.2f);
                    for (int r = 0; r < 3; r++) {
                        b.push().translate(4.2f, y + 0.36f + r * 0.6f, 3.6f);
                        b.torus(1.7f - r * 0.12f, 0.3f, 18, 6, 0, 360, PLAST);
                        b.pop();
                    }
                    b.color(1);
                    flacon(b, 9.6f, y, 3.0f, 0.75f, 4.0f, 0x2A2A2A, null, 0xD8B040);
                    flacon(b, 11.4f, y, 3.8f, 0.7f, 3.6f, 0xC8A030, null, 0x2A2A2A);
                    b.push().translate(13.4f, y, 2.8f);
                    b.cylinder(0.5f, 0, 2.6f, 10, METAL_NU, null, METAL_NU);
                    b.cylinder(0.3f, 2.6f, 3.0f, 8, AtlasCivil.PLASTIC_BLUE, null, AtlasCivil.PLASTIC_BLUE);
                    b.pop();
                }
                case 3 -> {
                    b.color(0.34f, 0.46f, 0.62f);
                    b.push().translate(6.0f, y + 1.3f, 3.6f).rotateZ(90);
                    b.lathe(new float[][]{{0.01f, -4.6f}, {1.1f, -4.5f}, {1.3f, -4.0f}, {1.3f, 4.0f}, {1.1f, 4.5f}, {0.01f, 4.6f}}, 12, 0, 360, TOILE, 1, 1, 0.05f, 1.0f, 3, false);
                    b.pop();
                    b.color(1);
                    b.bevelBox(11.4f, y, 1.6f, 14.6f, y + 2.2f, 5.6f, 0.1f, CARTON_MOUILLE);
                }
                default -> b.bevelBox(3.0f, y, 1.4f, 9.0f, y + 1.6f, 5.6f, 0.1f, AtlasCivil.CARTON);
            }
            b.pop();
        }
        // pot renversé, flaque séchée, marteau ; scie pendue au montant droit
        b.push().translate(6.4f, 1.3f, 11.0f).rotateY(30).rotateZ(90).translate(0, -1.4f, 0);
        b.cylinder(1.3f, 0, 2.6f, 14, L_PEINTURE, METAL_NU, null);
        b.color(0.9f, 0.88f, 0.82f);
        b.disc(1.2f, 2.5f, 14, PEINT, true);
        b.color(1);
        b.pop();
        b.color(0.9f, 0.88f, 0.82f);
        b.push().translate(9.4f, 0, 11.8f);
        b.lathe(new float[][]{{0.01f, 0.06f}, {2.6f, 0.05f}, {2.8f, 0.01f}}, 16, 0, 360, PEINT, 1.2f, 0.8f, 0.15f, 0.6f, 6, false);
        b.pop();
        b.color(1);
        b.push().translate(12.6f, 0, 9.6f).rotateY(-50);
        b.bevelBox(-2.0f, 0, -0.25f, 1.2f, 0.5f, 0.25f, 0.12f, BOIS_GRIS);
        b.bevelBox(1.2f, 0, -0.7f, 1.8f, 0.7f, 0.7f, 0.1f, METAL_NU);
        b.pop();
        b.box(x1 + 0.02f, 4.8f, 3.4f, x1 + 0.08f, 12.2f, 5.4f, METAL_NU);
        b.bevelBox(x1 + 0.06f, 12.2f, 3.0f, x1 + 0.46f, 14.2f, 5.8f, 0.12f, BOIS_GRIS);
        boue(b, 'z', 8.0f, 0, z1, 14.0f, 0.8f, 1);
    }

    static void tablette(MeshBuilder b, float xa, float xb, float y, float za, float zb) {
        b.box(xa, y, za, xb, y + 0.12f, zb, AtlasCivil.GALVA);
        b.box(xa, y - 0.7f, zb - 0.12f, xb, y, zb, AtlasCivil.GALVA);
        b.box(xa, y - 0.7f, za, xb, y, za + 0.12f, AtlasCivil.GALVA);
        coulure(b, 'z', (xa + xb) * 0.3f + y * 0.2f, y - 0.05f, zb, 0.35f, (int) y);
    }

    /** Pot de peinture (Ø 21 cm) avec étiquette, couvercle croûté et coulures. */
    static void potPeinture(MeshBuilder b, float x, float y, float z, int rgb) {
        b.push().translate(x, y, z);
        b.cylinder(1.3f, 0, 2.6f, 14, L_PEINTURE, null, null);
        b.push().translate(0, 2.6f, 0);
        b.torus(1.28f, 0.08f, 14, 4, 0, 360, METAL_NU);
        b.pop();
        teinte(b, rgb);
        b.cylinder(1.25f, 2.6f, 2.75f, 14, PEINT, null, PEINT);
        b.box(-0.2f, 1.2f, 1.28f, 0.2f, 2.7f, 1.36f, PEINT);
        b.color(1);
        b.pop();
    }

    // =================================================================== petite bibliothèque
    /**
     * Petite bibliothèque en mélaminé (80 x 30 x 95 cm) : encyclopédie au complet en bas, romans penchés, cadre photo ;
     * tablette du haut décrochée, livres glissés ; livres tombés au sol, l'un ouvert face contre terre.
     */
    static void bibliotheque(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.5f, z1 = 5.3f, h = 15.2f, e = 0.36f;
        b.color(0.92f, 0.89f, 0.8f);
        b.bevelBox(x0, 0, z0, x0 + e, h - e, z1, 0.06f, MELAMINE);
        b.bevelBox(x1 - e, 0, z0, x1, h - e, z1, 0.06f, MELAMINE);
        b.bevelBox(x0 - 0.1f, h - e, z0, x1 + 0.1f, h, z1 + 0.1f, 0.08f, MELAMINE);
        b.box(x0 + e, 0, z1 - 0.5f, x1 - e, 0.8f, z1 - 0.2f, MELAMINE);
        b.box(x0 + e, 0.8f, z0, x1 - e, 0.8f + e, z1, MELAMINE);
        b.box(x0 + e, 5.6f, z0 + 0.12f, x1 - e, 5.6f + e, z1, MELAMINE);
        b.color(0.7f, 0.62f, 0.5f);
        b.box(x0 + e, 0.8f + e, z0, x1 - e, h - e, z0 + 0.12f, AGGLO);
        b.color(1);
        // bas : encyclopédie (une case vide), trois livres couchés
        for (int k = 0; k < 10; k++) {
            if (k == 6) continue;
            livre(b, 2.1f + k * 0.66f, 0.8f + e, 1.3f, 0.62f, 4.1f, 3.4f, 0x2E3E5A);
        }
        livreCouche(b, 9.4f, 0.8f + e, 1.2f, 4.2f, 0.6f, 3.2f, 0x7A3A30);
        livreCouche(b, 9.6f, 0.8f + e + 0.6f, 1.4f, 3.8f, 0.5f, 2.9f, 0x8A7A5A);
        livreCouche(b, 9.5f, 0.8f + e + 1.1f, 1.3f, 3.4f, 0.7f, 2.6f, 0x3A5A3A);
        // milieu : romans, dont deux penchés ; cadre photo
        int[] cols = {0x7A3A30, 0x2E4A6A, 0x3A5A3A, 0x8A7A5A, 0x5A3A5A, 0x2A2A2A, 0xA8823A, 0x6A2A2A};
        float x = 2.1f, yb = 5.6f + e;
        java.util.Random r = new java.util.Random(5);
        for (int k = 0; k < 10; k++) {
            float ep = 0.35f + r.nextFloat() * 0.4f, hh = 2.8f + r.nextFloat() * 1.2f, d = 2.4f + r.nextFloat() * 1.0f;
            livre(b, x, yb, 1.3f, ep, hh, d, cols[k % cols.length]);
            x += ep + 0.03f;
        }
        b.push().translate(x + 0.6f, yb, 1.3f).rotateZ(-15).translate(-0.5f, 0, 0);
        livre(b, 0, 0, 0, 0.5f, 3.6f, 3.0f, 0x4A6A8A);
        b.pop();
        b.push().translate(12.5f, yb, 2.6f).rotateY(-12);
        b.bevelBox(-1.1f, 0.6f, -0.1f, 1.1f, 3.4f, 0.15f, 0.08f, BOIS_VERNI);
        b.decalZ(PHOTO, 0, 2.0f, 0.2f, 1.8f, 2.4f);
        b.push().translate(0, 0.3f, -0.6f).rotateX(-20);
        b.box(-0.2f, 0, -0.05f, 0.2f, 2.0f, 0.05f, BOIS_VERNI);
        b.pop();
        b.box(-1.1f, 0, -0.1f, 1.1f, 0.62f, 0.15f, BOIS_VERNI);
        b.pop();
        // tablette du haut décrochée à gauche, livres glissés
        b.push().translate(x1 - e, 10.4f, 0).rotateZ(6).translate(-(x1 - e), -10.4f, 0);
        b.color(0.92f, 0.89f, 0.8f);
        b.box(x0 + e + 0.12f, 10.4f, z0 + 0.12f, x1 - e, 10.4f + e, z1, MELAMINE);
        b.color(1);
        livreCouche(b, x0 + e + 0.2f, 10.4f + e, 1.3f, 3.6f, 0.55f, 2.8f, 0x5A3A5A);
        livreCouche(b, x0 + e + 0.3f, 10.4f + e + 0.55f, 1.5f, 3.2f, 0.45f, 2.6f, 0xA8823A);
        b.push().translate(7.0f, 10.4f + e, 1.4f).rotateZ(28);
        livre(b, 0, 0, 0, 0.45f, 3.4f, 2.8f, 0x7A3A30);
        b.pop();
        b.pop();
        // dessus : deux livres couchés
        livreCouche(b, 9.0f, h, 1.4f, 4.0f, 0.6f, 3.0f, 0x3A5A3A);
        livreCouche(b, 9.3f, h + 0.6f, 1.6f, 3.4f, 0.5f, 2.6f, 0x6A2A2A);
        // au sol : livre ouvert face contre terre (en toit), deux livres à plat
        b.push().translate(5.0f, 0, 9.0f).rotateY(25);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(0, 0.9f, 0).rotateZ(-s * 12);
            b.color(0.62f, 0.32f, 0.26f);
            b.box(s < 0 ? -2.5f : 0, -0.14f, -1.7f, s < 0 ? 0 : 2.5f, 0, 1.7f, LIVRE);
            b.color(1);
            b.box(s < 0 ? -2.42f : 0.04f, -0.36f, -1.6f, s < 0 ? -0.04f : 2.42f, -0.14f, 1.6f, PAGES);
            b.pop();
        }
        b.pop();
        b.push().translate(10.4f, 0, 10.2f).rotateY(-35);
        livreCouche(b, -1.8f, 0, -1.3f, 3.6f, 0.55f, 2.6f, 0x2E3E5A);
        b.pop();
        b.push().translate(12.8f, 0, 7.8f).rotateY(60);
        livreCouche(b, -1.6f, 0, -1.2f, 3.2f, 0.45f, 2.4f, 0x8A7A5A);
        b.pop();
        papiers(b, 2.0f, 12.4f, 8.0f, 14.2f, 3, 71);
        decal(b, MOISI, 'z', 8.0f, 0.4f, z1 - 0.2f, 6.0f, 0.7f);
        decal(b, GRIFFURES, 'X', x1, 8.0f, 3.0f, 3.0f, 1.2f, -15);
        decal(b, AUREOLE, 'y', 4.4f, h, 3.0f, 2.4f, 2.0f);
        boue(b, 'x', x0, 0, 2.9f, 4.4f, 1.0f, 1);
    }

    // =================================================================== meuble sous-vasque
    /**
     * Meuble de salle de bain (60 x 45 x 85 cm) : plan vasque en faïence entartré, mitigeur piqué ; porte gauche ouverte
     * sur le siphon et les flacons ; verre à dents (passe translucide), savon, shampooing ; socle gonflé et moisi.
     */
    static void meubleVasque(MeshBuilder b) {
        float x0 = 3.2f, x1 = 12.8f, z0 = 0.4f, z1 = 7.6f, yb = 0.9f, yt = 13.0f, e = 0.36f, xm = 8.0f;
        float vcx = 8.0f, vcz = (z0 + z1 + 0.6f) / 2;
        b.color(0.6f, 0.52f, 0.42f);
        b.box(x0 + 0.3f, 0, z0 + 0.3f, x1 - 0.3f, yb, z1 - 0.6f, AGGLO);
        b.color(0.94f, 0.92f, 0.86f);
        b.bevelBox(x0, yb, z0, x0 + e, yt, z1, 0.06f, MELAMINE);
        b.bevelBox(x1 - e, yb, z0, x1, yt, z1, 0.06f, MELAMINE);
        b.box(x0 + e, yb, z0, x1 - e, yb + e, z1, MELAMINE);
        b.box(x0 + e, yb + e, z0, x1 - e, yt, z0 + 0.2f, MELAMINE);
        b.color(1);
        // plan en faïence percé, cuvette (dedans et dessous), bonde
        b.box6(x0 - 0.1f, yt, z0, x1 + 0.1f, yt + 0.6f, z1 + 0.6f, CERAMIQUE, CERAMIQUE, CERAMIQUE, null, null, CERAMIQUE);
        plaqueTroueeY(b, CERAMIQUE, vcx, yt + 0.6f, vcz, (x1 - x0 + 0.2f) / 2, (z1 + 0.6f - z0) / 2, 3.0f, 2.3f, 24);
        b.push().translate(vcx, 0, vcz);
        b.lathe(new float[][]{{1.0f, yt + 0.6f}, {0.97f, yt}, {0.85f, yt - 0.8f}, {0.55f, yt - 1.3f}, {0.01f, yt - 1.4f}}, 20, 0, 360, CERAMIQUE, 3.0f, 2.3f, 0, 0, 0, true);
        b.lathe(new float[][]{{1.01f, yt}, {0.88f, yt - 0.85f}, {0.57f, yt - 1.4f}, {0.01f, yt - 1.5f}}, 20, 0, 360, CERAMIQUE, 3.0f, 2.3f, 0, 0, 0, false);
        b.color(0.25f);
        b.disc(0.4f, yt - 1.33f, 10, METAL_NU, true);
        b.color(1);
        b.pop();
        decal(b, AUREOLE, 'y', vcx + 1.6f, yt + 0.6f, 1.2f, 1.6f, 1.2f);
        // mitigeur
        b.push().translate(vcx, yt + 0.6f, 1.3f);
        b.cylinder(0.45f, 0, 1.6f, 12, CHROME_PIQUE, null, CHROME_PIQUE);
        b.pop();
        b.tube(new float[][]{p(vcx, yt + 1.7f, 1.6f), p(vcx, yt + 1.9f, 2.6f), p(vcx, yt + 1.5f, 3.3f)}, fill(3, 0.22f), 8, CHROME_PIQUE, false, true, 0, false);
        b.push().translate(vcx, yt + 2.2f, 1.1f).rotateX(-20);
        b.bevelBox(-0.2f, 0, -0.2f, 0.2f, 0.25f, 1.6f, 0.08f, CHROME_PIQUE);
        b.pop();
        // brosses à dents (dans le verre), savon, shampooing
        b.color(0.4f, 0.6f, 0.8f);
        b.push().translate(4.3f, yt + 0.8f, 1.6f).rotateZ(12);
        b.box(-0.08f, 0, -0.06f, 0.08f, 2.8f, 0.06f, PLAST);
        b.pop();
        b.color(0.85f, 0.4f, 0.35f);
        b.push().translate(4.5f, yt + 0.8f, 1.7f).rotateZ(-10).rotateX(8);
        b.box(-0.08f, 0, -0.06f, 0.08f, 2.6f, 0.06f, PLAST);
        b.pop();
        b.color(0.86f, 0.62f, 0.6f);
        b.bevelBox(10.6f, yt + 0.6f, 1.0f, 12.0f, yt + 1.05f, 1.9f, 0.18f, PLAST);
        b.color(1);
        flacon(b, 11.7f, yt + 0.6f, 6.8f, 0.6f, 3.0f, 0xB58AAE, L_SHAMPOOING, 0xE8E4D8);
        // portes : gauche ouverte, droite fermée
        b.push().translate(x0, 0, z1).rotateY(-100).translate(-x0, 0, -z1);
        b.color(0.94f, 0.92f, 0.86f);
        b.bevelBox(x0 + 0.04f, yb + 0.5f, z1, xm - 0.04f, yt - 0.2f, z1 + 0.36f, 0.1f, MELAMINE);
        b.color(1);
        bouton(b, xm - 0.8f, yt - 1.6f, z1 + 0.36f, 0.3f, CHROME_PIQUE);
        b.pop();
        b.color(0.94f, 0.92f, 0.86f);
        b.bevelBox(xm + 0.04f, yb + 0.5f, z1, x1 - 0.04f, yt - 0.2f, z1 + 0.36f, 0.1f, MELAMINE);
        b.color(1);
        bouton(b, xm + 0.8f, yt - 1.6f, z1 + 0.36f, 0.3f, CHROME_PIQUE);
        coulure(b, 'z', 10.6f, yt - 0.4f, z1 + 0.36f, 1.0f, 2);
        // intérieur : siphon, javel, shampooing
        b.color(0.62f);
        b.tube(new float[][]{p(vcx, yt - 1.4f, vcz), p(vcx, yt - 3.2f, vcz), p(vcx, yt - 4.2f, vcz - 0.8f), p(vcx, yt - 3.6f, vcz - 2.2f), p(vcx, yt - 3.0f, z0 + 0.25f)}, fill(5, 0.42f), 8, PLAST, false, false, 0, false);
        b.color(1);
        flacon(b, 4.8f, yb + e, 4.6f, 0.75f, 3.8f, 0xE6E4DE, L_JAVEL, 0x2A5AA0);
        flacon(b, 6.6f, yb + e, 3.0f, 0.6f, 3.0f, 0x8AB4D4, null, 0xE8E4D8);
        // usure : moisi, mousse verte, boue
        decal(b, MOISI, 'z', 8.0f, 0.5f, z1 - 0.6f, 6.0f, 0.8f);
        decal(b, MOUSSE_VERTE, 'X', x1, yb + 1.0f, 4.0f, 4.0f, 1.8f);
        decal(b, MOISI, 'z', 6.0f, yb + 2.0f, z0 + 0.2f, 3.0f, 2.0f, 12);
        boue(b, 'x', x0, yb, 4.0f, 6.0f, 1.2f, 0);
    }

    static void meubleVasqueVerre(MeshBuilder b) {
        b.push().translate(4.4f, 13.62f, 1.6f);
        b.lathe(new float[][]{{0.45f, 0}, {0.55f, 1.6f}}, 12, 0, 360, VERRE_SALE, 1, 1, 0, 0, 0, false);
        b.disc(0.45f, 0.02f, 12, VERRE_SALE, true);
        b.pop();
    }
}
