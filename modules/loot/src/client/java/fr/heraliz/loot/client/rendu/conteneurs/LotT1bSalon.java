package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/** T1b — salon : canapé éventré, fauteuil, meuble télé, poussette. */
final class LotT1bSalon {
    private LotT1bSalon() {
    }

    static void piedBois(MeshBuilder b, float x, float z, float h) {
        b.push().translate(x, 0, z);
        b.lathe(new float[][]{{0.26f, 0}, {0.34f, h * 0.5f}, {0.4f, h + 0.02f}}, 8, 0, 360, BOIS_VERNI, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Déchirure : mousse qui sort, deux lambeaux de tissu retroussés (centre sur la surface, normale n = 'z' ou 'y'). */
    static void eventre(MeshBuilder b, float x, float y, float z, float r, char n, int graine) {
        b.push().translate(x, y, z);
        if (n == 'z') b.rotateX(90);
        b.blob(r, r * 0.45f, r * 0.8f, 0.9f, 0.25f, 2.2f, graine, 12, MOUSSE_JAUNE);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * r * 0.9f, 0, 0).rotateZ(-s * 40);
            b.box(-0.05f, 0, -r * 0.6f, 0.05f, r * 0.8f, r * 0.6f, VELOURS);
            b.pop();
        }
        b.pop();
    }

    // =================================================================== canapé
    /**
     * Canapé deux places des années 80 (165 x 80 x 82 cm, deux blocs de large), velours rayé délavé : coussin d'assise
     * droit arraché et jeté de travers, sangles et ressorts à nu, mousse qui sort des coussins et d'un accoudoir ;
     * plaid sur l'accoudoir, auréoles et moisi.
     */
    static void canape(MeshBuilder b) {
        float x0 = 2.8f, x1 = 29.2f, z0 = 0.4f, z1 = 13.2f, H = 13.1f, ys = 4.4f, bras = 2.6f, hb = 9.4f, xm = 16.0f;
        for (float[] q : new float[][]{{x0 + 0.8f, z0 + 0.9f}, {x1 - 0.8f, z0 + 0.9f}, {x0 + 0.8f, z1 - 0.9f}, {x1 - 0.8f, z1 - 0.9f}}) piedBois(b, q[0], q[1], 1.0f);
        b.bevelBox(x0 + bras - 0.1f, 1.0f, z0 + 2.8f, x1 - bras + 0.1f, ys, z1 - 0.1f, 0.4f, VELOURS);
        b.bevelBox(x0 + bras - 0.1f, 1.0f, z0, x1 - bras + 0.1f, H, z0 + 3.2f, 1.0f, VELOURS);
        b.bevelBox(x0, 1.0f, z0, x0 + bras, hb, z1, 1.0f, VELOURS);
        b.bevelBox(x1 - bras, 1.0f, z0, x1, hb, z1, 1.0f, VELOURS);
        // coussin d'assise gauche (déchiré), coussins de dossier (le droit lacéré)
        b.bevelBox(x0 + bras, ys, z0 + 3.2f, xm, 6.9f, z1 - 0.2f, 0.8f, VELOURS);
        eventre(b, 8.0f, 6.9f, 8.6f, 1.6f, 'y', 3);
        b.bevelBox(x0 + bras, 6.9f, z0 + 3.25f, xm, 12.8f, z0 + 5.9f, 1.0f, VELOURS);
        eventre(b, 22.4f, 9.6f, z0 + 3.2f, 1.3f, 'z', 5);
        // assise droite à nu : toile de fond, ressorts zigzag, miettes de mousse
        b.color(0.3f, 0.26f, 0.22f);
        b.box(xm, ys, z0 + 3.2f, x1 - bras, ys + 0.08f, z1 - 0.4f, TOILE);
        b.color(1);
        for (float x : new float[]{18.4f, 21.6f, 24.8f}) {
            float[][] zz = new float[9][];
            for (int i = 0; i < 9; i++) zz[i] = p(x + (i % 2 == 0 ? -0.5f : 0.5f), ys + 0.22f, z0 + 3.6f + i * 1.05f);
            b.tube(zz, fill(9, 0.08f), 4, METAL_NU, true, true, 0, false);
        }
        for (float[] q : new float[][]{{20.0f, 11.6f}, {26.0f, 8.0f}}) {
            b.push().translate(q[0], ys + 0.4f, q[1]);
            b.blob(0.7f, 0.35f, 0.6f, 0.9f, 0.2f, 2.5f, (int) q[0], 8, MOUSSE_JAUNE);
            b.pop();
        }
        // coussin droit jeté de travers : un bout sur le coussin gauche, l'autre sur les ressorts
        b.push().translate(18.6f, 5.66f, 8.9f).rotateY(-6).rotateZ(-14);
        b.bevelBox(-5.2f, 0, -4.2f, 5.2f, 2.3f, 4.2f, 0.8f, VELOURS);
        b.push().translate(1.8f, 1.15f, 4.2f);
        b.blob(1.2f, 0.9f, 0.5f, 0.9f, 0.25f, 2.2f, 9, 10, MOUSSE_JAUNE);
        b.pop();
        b.pop();
        // accoudoir droit crevé, plaid sur l'accoudoir gauche
        eventre(b, x1 - 1.3f, hb - 0.4f, z1 - 0.1f, 1.0f, 'z', 7);
        drape(b, new float[][]{p(5.0f, hb + 0.06f, 7.0f), p(3.8f, hb + 0.22f, 7.05f), p(2.6f, hb - 0.9f, 7.1f), p(2.5f, 5.0f, 7.15f), p(2.55f, 1.6f, 7.2f), p(2.8f, 0.12f, 7.25f), p(4.2f, 0.08f, 7.3f)},
                4.2f, v(0, 0, 1), PLAID);
        // usure
        decal(b, AUREOLE, 'y', 11.6f, 6.9f, 10.6f, 3.0f, 2.6f, 15);
        decal(b, MOISI, 'z', 8.0f, 9.6f, z0 + 5.9f, 4.0f, 2.8f, -10);
        decal(b, AUREOLE, 'z', 25.4f, 11.4f, z0 + 3.2f, 2.4f, 2.0f);
        decal(b, GRIFFURES, 'X', x1, 5.4f, 7.0f, 4.0f, 1.6f, 10);
        boue(b, 'z', 16.0f, 1.0f, z1 - 0.1f, 20.0f, 1.3f, 0);
        boue(b, 'X', x1, 1.0f, 7.0f, 10.0f, 1.4f, 1);
    }

    // =================================================================== fauteuil
    /**
     * Fauteuil des années 70 (78 x 80 x 90 cm), velours vert passé et manchettes de bois : assise affaissée, dossier
     * lacéré (mousse), accoudoir crevé, coussin moutarde tassé dans le coin.
     */
    static void fauteuil(MeshBuilder b) {
        float x0 = 1.8f, x1 = 14.2f, z0 = 0.4f, z1 = 12.6f, ys = 5.2f, bras = 1.9f, yt = 7.2f;
        for (float[] q : new float[][]{{x0 + 0.7f, z0 + 2.2f}, {x1 - 0.7f, z0 + 2.2f}, {x0 + 0.7f, z1 - 0.8f}, {x1 - 0.7f, z1 - 0.8f}}) piedBois(b, q[0], q[1], 1.4f);
        b.color(0.82f, 0.94f, 0.7f);
        b.bevelBox(x0 + bras - 0.1f, 1.4f, z0 + 2.6f, x1 - bras + 0.1f, ys, z1 - 0.1f, 0.5f, VELOURS);
        b.push().translate(0, 1.4f, z0 + 1.5f).rotateX(-6).translate(0, -1.4f, -(z0 + 1.5f));
        b.bevelBox(x0 + bras - 0.1f, 1.4f, z0 + 1.5f, x1 - bras + 0.1f, 14.4f, z0 + 4.0f, 1.2f, VELOURS);
        b.color(1);
        eventre(b, 8.0f, 11.0f, z0 + 4.0f, 1.4f, 'z', 4);
        decal(b, AUREOLE, 'z', 6.4f, 12.6f, z0 + 4.0f, 2.2f, 1.8f);
        b.color(0.82f, 0.94f, 0.7f);
        b.pop();
        for (float x : new float[]{x0, x1 - bras}) b.bevelBox(x, 1.4f, z0 + 1.6f, x + bras, 9.0f, z1, 0.8f, VELOURS);
        b.color(1);
        for (float x : new float[]{x0, x1 - bras}) b.bevelBox(x - 0.1f, 9.0f, z0 + 3.2f, x + bras + 0.1f, 9.45f, z1 + 0.15f, 0.15f, BOIS_VERNI);
        // assise affaissée : coussin sans dessus, surface creusée
        float xa = x0 + bras, xb = x1 - bras, za = z0 + 4.0f, zb = z1 - 0.2f, bv = 0.6f;
        b.color(0.82f, 0.94f, 0.7f);
        b.bevelBox(xa, ys, za, xb, yt, zb, bv, VELOURS, null, MeshBuilder.SKIP_PY);
        final float sxa = xa + bv, sxb = xb - bv, sza = za + bv, szb = zb - bv;
        b.surface((u, w) -> p(sxa + (sxb - sxa) * u, yt - 1.0f * (float) (Math.sin(Math.PI * u) * Math.sin(Math.PI * w)), sza + (szb - sza) * w),
                8, 8, VELOURS, (sxb - sxa) / 14f, (szb - sza) / 14f, false, MeshBuilder.Orient.KEEP);
        b.color(1);
        eventre(b, x0 + bras / 2, 7.4f, z1 + 0.02f, 0.8f, 'z', 6);
        // coussin moutarde tassé contre le dossier
        b.color(0.74f, 0.6f, 0.26f);
        b.push().translate(xb - 2.4f, yt + 0.4f, za + 1.4f).rotateY(-20).rotateX(-35);
        b.blob(1.8f, 0.6f, 1.6f, 0.5f, 0.05f, 1.5f, 2, 12, TOILE);
        b.pop();
        b.color(1);
        decal(b, MOISI, 'z', 8.0f, 2.6f, z1 - 0.1f, 5.0f, 1.6f);
        decal(b, GRIFFURES, 'X', x1, 5.0f, 7.0f, 3.6f, 1.4f, -12);
        boue(b, 'x', x0, 1.4f, 7.0f, 9.0f, 1.2f, 0);
    }

    // =================================================================== meuble télé
    /**
     * Meuble télé bas (120 x 45 x 50 cm, deux blocs de large) en placage sombre : téléviseur cathodique à l'écran fêlé,
     * antenne d'intérieur, magnétoscope, cassettes ; porte vitrée brisée (passe translucide), plante morte, cassette
     * à la bande dévidée au sol.
     */
    static void meubleTv(MeshBuilder b) {
        float x0 = 1.4f, x1 = 20.6f, z0 = 0.4f, z1 = 7.6f, h = 8.0f, e = 0.4f, xm = 11.0f;
        b.box(x0 + 0.3f, 0, z0 + 0.3f, x1 - 0.3f, 0.6f, z1 - 0.5f, AtlasCivil.DARK);
        b.color(0.6f, 0.52f, 0.48f);
        b.bevelBox(x0, 0.6f, z0, x0 + e, h - e, z1, 0.06f, BOIS_VERNI);
        b.bevelBox(x1 - e, 0.6f, z0, x1, h - e, z1, 0.06f, BOIS_VERNI);
        b.bevelBox(xm - e / 2, 0.6f + e, z0 + 0.2f, xm + e / 2, h - e, z1, 0.06f, BOIS_VERNI);
        b.bevelBox(x0 + e, 0.6f, z0, x1 - e, 0.6f + e, z1, 0.06f, BOIS_VERNI);
        b.bevelBox(x0 - 0.1f, h - e, z0, x1 + 0.1f, h, z1 + 0.1f, 0.1f, BOIS_VERNI);
        b.bevelBox(x0 + e, 4.2f, z0 + 0.2f, xm - e / 2, 4.5f, z1, 0.05f, BOIS_VERNI);
        b.color(0.5f, 0.45f, 0.4f);
        b.box(x0 + e, 0.6f + e, z0, x1 - e, h - e, z0 + 0.2f, AGGLO);
        b.color(1);
        // magnétoscope, cassettes couchées et debout
        b.color(0.55f);
        b.bevelBox(2.4f, 1.0f, 1.2f, 9.8f, 2.6f, 6.6f, 0.12f, AtlasCivil.DARK);
        b.color(1);
        decal(b, MAGNETO, 'z', 6.1f, 1.8f, 6.6f, 7.2f, 1.5f);
        for (int k = 0; k < 4; k++) vhs(b, 2.2f + (k % 2) * 0.2f, 4.5f + k * 0.42f, 2.0f + (k % 3) * 0.15f, 0);
        for (int k = 0; k < 3; k++) {
            b.push().translate(6.6f + k * 0.45f, 4.5f, 2.0f).rotateZ(k == 2 ? -12 : 0).rotateZ(90);
            vhs(b, 0, -0.42f, 0, 0);
            b.pop();
        }
        // compartiment droit : albums ; porte vitrée entrouverte (cadre ; verre à part)
        livre(b, 12.0f, 0.6f + e, 1.4f, 0.9f, 5.6f, 4.4f, 0x5A2E26);
        livre(b, 12.95f, 0.6f + e, 1.4f, 0.9f, 5.4f, 4.2f, 0x2E3A4A);
        b.push().translate(14.0f, 0.6f + e, 1.4f).rotateZ(-20);
        livre(b, 0, 0, 0, 0.8f, 5.0f, 4.0f, 0x3A4A2E);
        b.pop();
        float dx0 = xm + e / 2 + 0.05f, dx1 = x1 - e - 0.05f, dy0 = 0.6f + e + 0.05f, dy1 = h - e - 0.05f;
        b.push().translate(dx1, 0, z1).rotateY(25).translate(-dx1, 0, -z1);
        b.color(0.6f, 0.52f, 0.48f);
        b.bevelBox(dx0, dy0, z1, dx0 + 0.6f, dy1, z1 + 0.3f, 0.06f, BOIS_VERNI);
        b.bevelBox(dx1 - 0.6f, dy0, z1, dx1, dy1, z1 + 0.3f, 0.06f, BOIS_VERNI);
        b.bevelBox(dx0 + 0.6f, dy0, z1, dx1 - 0.6f, dy0 + 0.6f, z1 + 0.3f, 0.06f, BOIS_VERNI);
        b.bevelBox(dx0 + 0.6f, dy1 - 0.6f, z1, dx1 - 0.6f, dy1, z1 + 0.3f, 0.06f, BOIS_VERNI);
        b.color(1);
        bouton(b, dx0 + 0.3f, (dy0 + dy1) / 2, z1 + 0.3f, 0.22f, AtlasCivil.BRASS);
        b.pop();
        // téléviseur cathodique : façade, cul du tube, écran fêlé, cerclage, antenne
        float tx0 = 6.9f, tx1 = 15.1f, ty0 = h, ty1 = h + 7.2f, tz1 = z1 - 0.2f, tcx = (tx0 + tx1) / 2;
        b.color(0.34f);
        b.bevelBox(tx0, ty0 + 0.02f, tz1 - 3.0f, tx1, ty1, tz1, 0.4f, PLAST);
        b.bevelBox(tx0 + 0.9f, ty0 + 0.6f, z0 + 0.3f, tx1 - 0.9f, ty1 - 0.7f, tz1 - 2.98f, 0.8f, PLAST);
        b.color(0.26f);
        b.box(tcx - 3.5f, ty0 + 1.6f, tz1 - 0.02f, tcx + 3.5f, ty0 + 1.75f, tz1 + 0.18f, PLAST);
        b.box(tcx - 3.5f, ty1 - 0.55f, tz1 - 0.02f, tcx + 3.5f, ty1 - 0.4f, tz1 + 0.18f, PLAST);
        b.box(tcx - 3.5f, ty0 + 1.75f, tz1 - 0.02f, tcx - 3.35f, ty1 - 0.55f, tz1 + 0.18f, PLAST);
        b.box(tcx + 3.35f, ty0 + 1.75f, tz1 - 0.02f, tcx + 3.5f, ty1 - 0.55f, tz1 + 0.18f, PLAST);
        b.color(1);
        decal(b, ECRAN_TV, 'z', tcx, (ty0 + 1.75f + ty1 - 0.55f) / 2, tz1, 6.7f, ty1 - ty0 - 2.3f);
        decal(b, FACADE_TV, 'z', tcx, ty0 + 0.85f, tz1, 6.8f, 1.3f);
        decal(b, PERSIENNES, 'y', tcx, ty1, tz1 - 1.6f, 4.0f, 2.0f);
        decal(b, ETIQUETTE_DECHIREE, 'X', tx1, ty0 + 3.6f, tz1 - 1.6f, 2.4f, 1.1f, -8);
        b.push().translate(tcx, ty1, 3.0f);
        b.sphereSection(0.8f, 10, 4, 0, 90, AtlasCivil.DARK);
        b.pop();
        b.tube(new float[][]{p(tcx - 0.2f, ty1 + 0.5f, 3.0f), p(tcx - 2.6f, ty1 + 4.6f, 2.6f), p(tcx - 3.3f, ty1 + 5.6f, 2.5f)}, fill(3, 0.07f), 5, CHROME_PIQUE, false, true, 0, false);
        b.tube(new float[][]{p(tcx + 0.2f, ty1 + 0.5f, 3.0f), p(tcx + 2.2f, ty1 + 3.0f, 3.2f), p(tcx + 4.4f, ty1 + 3.4f, 3.4f)}, fill(3, 0.07f), 5, CHROME_PIQUE, false, true, 0, false);
        // plante morte dans son pot, sur le meuble
        b.push().translate(3.6f, h, 4.0f);
        b.color(0.7f, 0.42f, 0.3f);
        b.lathe(new float[][]{{0.01f, 0}, {0.9f, 0.02f}, {1.2f, 2.0f}, {1.32f, 2.1f}}, 12, 0, 360, PEINT, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.24f, 2.08f}, {1.1f, 1.7f}}, 12, 0, 360, PEINT, 1, 1, 0, 0, 0, true);
        b.color(1);
        b.disc(1.12f, 1.72f, 12, TERRE, true);
        b.color(0.5f, 0.42f, 0.3f);
        b.tube(new float[][]{p(0, 1.7f, 0), p(0.2f, 3.4f, 0.1f), p(-0.5f, 4.6f, 0.3f), p(-1.3f, 4.2f, 0.5f)}, new float[]{0.08f, 0.06f, 0.04f, 0.03f}, 4, BOIS_GRIS, false, true, 0, false);
        b.tube(new float[][]{p(0.1f, 1.7f, 0), p(0.6f, 3.0f, -0.2f), p(1.4f, 3.6f, -0.4f)}, new float[]{0.07f, 0.05f, 0.03f}, 4, BOIS_GRIS, false, true, 0, false);
        b.color(1);
        b.pop();
        decal(b, AUREOLE, 'y', 18.2f, h, 4.0f, 2.2f, 2.0f);
        // au sol : cassettes, bande dévidée
        b.push().translate(14.2f, 0, 10.6f).rotateY(35);
        vhs(b, -1.5f, 0, -0.8f, 0);
        b.pop();
        b.push().translate(18.6f, 0, 9.6f).rotateY(-20);
        vhs(b, -1.5f, 0, -0.8f, 0);
        b.pop();
        b.color(0.3f, 0.24f, 0.2f);
        float[][] bande = new float[16][];
        for (int i = 0; i < bande.length; i++) {
            double a = i * 0.9;
            bande[i] = p(17.4f - i * 0.45f + (float) Math.cos(a) * 0.9f, 0.18f, 10.6f + (float) Math.sin(a) * 1.1f + i * 0.08f);
        }
        drape(b, bande, 0.26f, v(0, 1, 0), AtlasCivil.DARK);
        b.color(1);
        decal(b, GRIFFURES, 'x', x0, 4.0f, 4.0f, 3.0f, 1.2f, 18);
        decal(b, MOISI, 'z', 6.0f, 0.3f, z1 - 0.5f, 6.0f, 0.5f);
        boue(b, 'X', x1, 0.6f, 4.0f, 6.6f, 1.2f, 0);
    }

    /** Cassette vidéo VHS (18,7 x 10,2 x 2,5 cm) à plat, étiquette sur la tranche avant. */
    static void vhs(MeshBuilder b, float x, float y, float z, int ignore) {
        b.color(0.6f);
        b.bevelBox(x, y, z, x + 3.0f, y + 0.4f, z + 1.63f, 0.05f, AtlasCivil.DARK);
        b.color(1);
        b.decalZ(VHS, x + 1.5f, y + 0.2f, z + 1.68f, 2.7f, 0.34f);
    }

    static void meubleTvVerre(MeshBuilder b) {
        float x1 = 20.6f, z1 = 7.6f, e = 0.4f, xm = 11.0f, h = 8.0f;
        float dx0 = xm + e / 2 + 0.05f, dx1 = x1 - e - 0.05f, dy0 = 0.6f + e + 0.05f, dy1 = h - e - 0.05f;
        b.push().translate(dx1, 0, z1).rotateY(25).translate(-dx1, 0, -z1);
        b.face(VERRE_BRISE, v(0, 0, 1), p(dx0 + 0.6f, dy0 + 0.6f, z1 + 0.15f), p(dx1 - 0.6f, dy0 + 0.6f, z1 + 0.15f), p(dx1 - 0.6f, dy1 - 0.6f, z1 + 0.15f), p(dx0 + 0.6f, dy1 - 0.6f, z1 + 0.15f));
        b.face(VERRE_BRISE, v(0, 0, -1), p(dx1 - 0.6f, dy0 + 0.6f, z1 + 0.13f), p(dx0 + 0.6f, dy0 + 0.6f, z1 + 0.13f), p(dx0 + 0.6f, dy1 - 0.6f, z1 + 0.13f), p(dx1 - 0.6f, dy1 - 0.6f, z1 + 0.13f));
        b.pop();
        debris(b, 15.6f, 10.4f, 2.4f, 10, VERRE_SALE, 77);
    }

    // =================================================================== poussette
    /**
     * Poussette des années 90 (80 x 50 x 100 cm) le long du mur : toile marine délavée, capote à moitié repliée,
     * châssis chromé piqué, panier dessous, couverture écossaise qui pend, doudou oublié sur l'assise.
     */
    static void poussette(MeshBuilder b) {
        float za = 1.4f, zb = 7.8f, zm = (za + zb) / 2;
        float[][] roues = {{3.6f, za - 0.25f, 1.25f}, {3.6f, zb + 0.25f, 1.25f}, {12.4f, za + 0.5f, 0.95f}, {12.4f, zb - 0.5f, 0.95f}};
        for (float[] r : roues) roue(b, r[0], r[1], r[2]);
        b.tube(new float[][]{p(3.6f, 1.25f, za - 0.25f), p(3.6f, 1.25f, zb + 0.25f)}, fill(2, 0.1f), 6, METAL_NU, false, false, 0, false);
        b.tube(new float[][]{p(12.4f, 0.95f, za + 0.5f), p(12.4f, 0.95f, zb - 0.5f)}, fill(2, 0.1f), 6, METAL_NU, false, false, 0, false);
        for (float z : new float[]{za, zb}) {
            float zf = z == za ? za + 0.5f : zb - 0.5f;
            b.tube(new float[][]{p(3.6f, 1.25f, z), p(2.8f, 7.0f, z), p(2.2f, 12.0f, z), p(1.9f, 15.0f, z)}, fill(4, 0.18f), 8, CHROME_PIQUE, false, false, 0, false);
            b.tube(new float[][]{p(12.4f, 1.0f, zf), p(10.6f, 5.6f, z), p(6.0f, 7.4f, z), p(3.0f, 7.9f, z)}, fill(4, 0.16f), 8, CHROME_PIQUE, false, false, 0, false);
        }
        b.tube(new float[][]{p(1.9f, 15.0f, za), p(1.9f, 15.0f, zb)}, fill(2, 0.18f), 8, CHROME_PIQUE, true, true, 0, false);
        b.tube(new float[][]{p(1.9f, 15.0f, za + 0.7f), p(1.9f, 15.0f, zb - 0.7f)}, fill(2, 0.3f), 8, CAOUTCHOUC_USE, true, true, 0, false);
        // hamac : assise, dossier incliné, repose-pieds, flancs
        b.color(0.32f, 0.36f, 0.48f);
        b.bevelBox(4.4f, 6.3f, za + 0.3f, 10.0f, 7.0f, zb - 0.3f, 0.3f, TOILE);
        b.push().translate(4.6f, 6.6f, 0).rotateZ(28);
        b.bevelBox(-0.35f, 0, za + 0.3f, 0.35f, 6.2f, zb - 0.3f, 0.3f, TOILE);
        b.pop();
        b.bevelBox(10.0f, 4.4f, za + 0.5f, 10.6f, 7.0f, zb - 0.5f, 0.25f, TOILE);
        for (float z : new float[]{za + 0.2f, zb - 0.45f}) b.bevelBox(4.4f, 7.0f, z, 9.6f, 8.6f, z + 0.25f, 0.1f, TOILE);
        // capote à moitié repliée (demi-coque d'axe Z) : dessus et doublure
        b.push().translate(3.6f, 11.0f, za + 0.1f).rotateX(90);
        b.lathe(new float[][]{{2.2f, 0}, {2.7f, 0.5f}, {2.8f, 3.2f}, {2.7f, 5.9f}, {2.2f, 6.4f}}, 12, -200, -60, TOILE, 1, 1, 0.03f, 1.2f, 3, false);
        b.color(0.2f, 0.22f, 0.28f);
        b.lathe(new float[][]{{2.12f, 0.05f}, {2.62f, 0.5f}, {2.72f, 3.2f}, {2.62f, 5.9f}, {2.12f, 6.35f}}, 12, -200, -60, TOILE, 1, 1, 0.03f, 1.2f, 3, true);
        b.pop();
        // panier dessous (toile), biberon dedans
        b.color(0.2f);
        b.box6(4.8f, 2.6f, za + 0.5f, 9.8f, 4.2f, zb - 0.5f, TOILE, TOILE, TOILE, TOILE, TOILE, TOILE);
        b.color(1);
        b.push().translate(6.4f, 4.72f, zm).rotateY(20).rotateZ(90).translate(0, -1.2f, 0);
        b.lathe(new float[][]{{0.01f, 0}, {0.5f, 0.02f}, {0.5f, 1.8f}, {0.3f, 2.1f}, {0.12f, 2.6f}}, 10, 0, 360, AtlasCivil.MILK, 1, 1, 0, 0, 0, false);
        b.pop();
        // arceau avant en mousse, couverture écossaise qui pend, doudou
        b.tube(new float[][]{p(9.8f, 8.9f, za), p(10.3f, 9.1f, zm), p(9.8f, 8.9f, zb)}, fill(3, 0.26f), 8, CAOUTCHOUC_USE, true, true, 0, false);
        drape(b, new float[][]{p(6.0f, 7.12f, 4.6f), p(6.1f, 7.6f, 6.6f), p(6.2f, 8.75f, 7.45f), p(6.3f, 8.2f, 8.1f), p(6.4f, 5.0f, 8.3f), p(6.6f, 2.4f, 8.4f)}, 3.4f, v(1, 0, 0), PLAID);
        b.color(0.86f, 0.66f, 0.68f);
        b.push().translate(8.4f, 7.75f, 3.2f).scale(1, 1.1f, 0.9f);
        b.sphere(0.75f, 10, 6, PELUCHE);
        b.pop();
        b.push().translate(8.6f, 8.85f, 3.0f);
        b.sphere(0.55f, 10, 6, PELUCHE);
        b.pop();
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(8.6f + s * 0.25f, 9.7f, 2.9f).rotateZ(-s * 15).scale(0.3f, 1, 0.22f);
            b.sphere(0.7f, 8, 5, PELUCHE);
            b.pop();
        }
        b.color(1);
        decal(b, AUREOLE, 'y', 7.0f, 7.0f, 5.2f, 2.0f, 1.8f);
        boue(b, 'z', 7.3f, 2.6f, zb - 0.5f, 4.8f, 0.8f, 1);
    }

    /** Roue de poussette (axe Z) : bandage, moyeu, trois rayons. */
    static void roue(MeshBuilder b, float x, float z, float r) {
        b.push().translate(x, r, z).rotateX(90);
        b.torus(r - 0.25f, 0.25f, 16, 6, 0, 360, CAOUTCHOUC_USE);
        b.cylinder(0.35f, -0.22f, 0.22f, 10, METAL_NU, METAL_NU, METAL_NU);
        for (int k = 0; k < 3; k++) {
            b.push().rotateY(k * 60);
            b.box(-(r - 0.4f), -0.05f, -0.08f, r - 0.4f, 0.05f, 0.08f, METAL_NU);
            b.pop();
        }
        b.pop();
    }
}
