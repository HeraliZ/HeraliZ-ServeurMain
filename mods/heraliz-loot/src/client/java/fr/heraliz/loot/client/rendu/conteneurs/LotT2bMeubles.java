package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Lot T2b, meubles de maison de campagne : armoire, buffet, vaisselier, coffre de bout de lit, machine à coudre,
 * valises anciennes. Chêne ciré grisé, portes à cadre et panneau, ferrures de laiton ternies.
 */
final class LotT2bMeubles {
    private LotT2bMeubles() {
    }

    /**
     * Armoire de campagne en chêne (95 x 55 x 188 cm) : porte gauche à miroir terni et fêlé (un éclat manque), porte
     * droite entrouverte sur des piles de linge, un drap tiré qui pend jusqu'au sol, un autre tombé devant ; corniche
     * moulurée, rosace, pieds miche, toiles d'araignée, auréole et boue au pied.
     */
    static void armoire(MeshBuilder b) {
        Mat bo = CHENE;
        float x0 = 0.5f, x1 = 15.5f, z0 = 0.4f, zf = 8.6f, yb = 1.4f, yh = 27.6f;
        for (float[] q : new float[][]{{1.4f, 1.6f}, {14.6f, 1.6f}, {1.4f, 7.5f}, {14.6f, 7.5f}}) piedMiche(b, q[0], q[1], 0.95f, 1.45f, bo);
        // montants d'angle
        bois(b, x0 - 0.05f, yb, zf - 1.3f, x0 + 1.3f, yh, zf, 0.15f, bo);
        bois(b, x1 - 1.3f, yb, zf - 1.3f, x1 + 0.05f, yh, zf, 0.15f, bo);
        bois(b, x0 - 0.05f, yb, z0, x0 + 1.3f, yh, z0 + 1.3f, 0.15f, bo, NZ);
        bois(b, x1 - 1.3f, yb, z0, x1 + 0.05f, yh, z0 + 1.3f, 0.15f, bo, NZ);
        // façade : traverse haute, ceinture basse
        float fx0 = x0 + 1.25f, fx1 = x1 - 1.25f;
        bois(b, fx0, yh - 1.7f, zf - 1.1f, fx1, yh, zf - 0.08f, 0.12f, bo);
        bois(b, fx0, yb, zf - 1.1f, fx1, 4.4f, zf - 0.08f, 0.12f, bo);
        // côtés à cadre et panneau
        for (int c = 0; c < 2; c++) {
            b.push();
            if (c == 0) b.translate(x0 + 0.1f, 0, z0 + 1.25f).rotateY(-90);
            else b.translate(x1 - 0.1f, 0, zf - 1.25f).rotateY(90);
            float L = zf - z0 - 2.5f;
            bois(b, -0.05f, yh - 1.7f, -1.0f, L + 0.05f, yh, -0.05f, 0.12f, bo);
            bois(b, -0.05f, yb, -1.0f, L + 0.05f, 4.4f, -0.05f, 0.12f, bo);
            panneau(b, 0, 4.4f, L, yh - 1.7f, -0.05f, bo);
            b.pop();
        }
        // intérieur (bois plus sombre) : fond, plancher, ciel, trois tablettes
        b.color(0.6f, 0.56f, 0.52f);
        bois(b, 1.6f, 4.0f, z0 + 0.1f, 14.4f, yh - 1.6f, z0 + 0.5f, 0.05f, bo, NZ);
        bois(b, 1.6f, 4.0f, 0.9f, 14.4f, 4.38f, zf - 1.1f, 0.05f, bo);
        bois(b, 1.6f, yh - 1.75f, 0.9f, 14.4f, yh - 1.2f, zf - 1.1f, 0.05f, bo);
        for (float y : new float[]{10.4f, 16.4f, 21.6f}) bois(b, 1.6f, y, 0.9f, 14.4f, y + 0.4f, 7.0f, 0.06f, bo);
        b.color(1);
        // corniche : frise, gorge, larmier, chapeau ; rosace
        b.bevelBox(x0 - 0.05f, yh - 0.05f, z0 + 0.05f, x1 + 0.05f, yh + 0.9f, zf + 0.15f, 0.12f, bo, null, NZ);
        b.bevelBox(x0 - 0.3f, yh + 0.85f, z0 + 0.1f, x1 + 0.3f, yh + 1.45f, zf + 0.45f, 0.25f, bo, null, NZ);
        b.bevelBox(x0 - 0.45f, yh + 1.4f, z0 + 0.15f, x1 + 0.45f, yh + 2.0f, zf + 0.65f, 0.2f, bo, null, NZ);
        b.bevelBox(x0 - 0.1f, yh + 1.95f, z0 + 0.2f, x1 + 0.1f, yh + 2.4f, zf + 0.3f, 0.15f, bo, null, NZ);
        b.push().translate(8, yh + 0.43f, zf + 0.27f);
        b.slab(MeshBuilder.ngon(0.5f, 12, 0), 0.24f, 0.09f, true, bo, null, bo);
        b.pop();
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(8 + s * 1.5f, yh + 0.43f, zf + 0.2f).rotateZ(s * 12);
            b.slab(MeshBuilder.roundRect(1.8f, 0.42f, 0.2f, 2), 0.14f, 0.05f, true, bo, null, bo);
            b.pop();
        }
        // portes : gauche fermée (miroir), droite entrouverte (gond à droite)
        float dz0 = zf - 0.95f, dz1 = zf - 0.15f, dy0 = 4.45f, dy1 = yh - 1.75f, dxm = 8.0f;
        porte(b, fx0 + 0.05f, dy0, dxm - 0.03f, dy1, dz0, dz1, true);
        bois(b, dxm - 0.35f, dy0 + 0.1f, dz1 - 0.1f, dxm + 0.22f, dy1 - 0.1f, dz1 + 0.12f, 0.08f, bo);
        fiche(b, fx0 + 0.02f, 6.0f, dz1 + 0.12f, 1.6f);
        fiche(b, fx0 + 0.02f, 23.2f, dz1 + 0.12f, 1.6f);
        float hx = fx1 - 0.05f;
        b.push().translate(hx, 0, dz1).rotateY(52).translate(-hx, 0, -dz1);
        porte(b, dxm + 0.03f, dy0, hx, dy1, dz0, dz1, false);
        serrure(b, dxm + 0.55f, 15.2f, dz1);
        b.pop();
        fiche(b, hx + 0.05f, 6.0f, dz1 + 0.12f, 1.6f);
        fiche(b, hx + 0.05f, 23.2f, dz1 + 0.12f, 1.6f);
        // linge : couvertures en bas, draps, pile renversée en haut
        linge(b, 9.2f, 4.38f, 1.3f, 4.4f, 5.0f, 3, new Mat[]{PLAID, LINGE, PLAID}, 1);
        linge(b, 8.8f, 10.8f, 1.2f, 4.6f, 5.0f, 5, new Mat[]{LINGE, TORCHON, LINGE, LINGE}, 2);
        linge(b, 11.4f, 16.8f, 1.2f, 2.6f, 4.4f, 2, new Mat[]{LINGE}, 3);
        b.push().translate(11.6f, 22.0f, 3.6f).rotateY(-25).rotateZ(4);
        b.bevelBox(-1.8f, 0, -1.6f, 1.8f, 0.6f, 1.6f, 0.24f, TORCHON, null, MeshBuilder.SKIP_NY);
        b.pop();
        drap(b, drapArmoire(), LINGE);
        b.push().translate(4.0f, 0, 11.8f).rotateY(16);
        b.bevelBox(-2.3f, 0, -1.7f, 2.3f, 0.55f, 1.7f, 0.24f, LINGE, null, MeshBuilder.SKIP_NY);
        b.bevelBox(-2.1f, 0.53f, -1.5f, 1.6f, 0.95f, 1.5f, 0.2f, LINGE, null, MeshBuilder.SKIP_NY);
        b.pop();
        // usure : miroir fêlé (éclat manquant), auréole, boue, toiles d'araignée
        decal(b, FISSURE, 'z', 4.9f, 18.6f, dz1 - 0.3f, 4.4f, 4.4f, 25);
        b.face(INTERIEUR_SALE, MeshBuilder.v(0, 0, 1), MeshBuilder.p(4.4f, 18.0f, dz1 - 0.2f), MeshBuilder.p(5.7f, 18.4f, dz1 - 0.2f),
                MeshBuilder.p(4.9f, 19.9f, dz1 - 0.2f));
        decal(b, AUREOLE, 'z', 5.0f, 2.9f, zf - 0.08f, 3.4f, 2.4f);
        boue(b, 'z', 9.5f, yb, zf - 0.08f, 8.0f, 1.3f, 0);
        toile(b, 14.38f, yh - 1.77f, 4.5f, 3.0f, 2.6f, 180, 0);
        toile(b, 2.3f, yb - 0.04f, 7.6f, 2.6f, 1.3f, 0, 0);
        toile(b, 13.6f, yh + 2.4f, 0.06f, 2.6f, 2.0f, 90, 180);
    }

    /** Porte d'armoire à cadre : deux panneaux, ou miroir en haut (miroir = true). */
    static void porte(MeshBuilder b, float x0, float y0, float x1, float y1, float zb, float zf, boolean miroir) {
        Mat bo = CHENE;
        float lm = 1.0f, lh = 1.25f, lb = 1.5f, ym = 8.6f;
        cadre(b, x0, y0, x1, y1, zb, zf, lm, lh, lb, bo);
        bois(b, x0 + lm - 0.02f, ym, zb + 0.04f, x1 - lm + 0.02f, ym + 1.0f, zf - 0.04f, 0.12f, bo);
        panneau(b, x0 + lm, y0 + lb, x1 - lm, ym, zf, bo);
        if (miroir) {
            b.bevelBox(x0 + lm - 0.2f, ym + 0.8f, zf - 0.62f, x1 - lm + 0.2f, y1 - lh + 0.2f, zf - 0.3f, 0.04f, bo,
                    new Mat[]{null, null, null, null, null, MIROIR}, 0);
        } else {
            panneau(b, x0 + lm, ym + 1.0f, x1 - lm, y1 - lh, zf, bo);
        }
    }

    /** Drap tiré de la tablette du milieu : il glisse par-dessus le bord, pend devant la ceinture et s'étale au sol. */
    static float[][][] drapArmoire() {
        float[][] yz = {{16.86f, 3.0f}, {16.86f, 4.8f}, {16.86f, 6.5f}, {16.6f, 7.2f}, {15.6f, 7.55f}, {13.2f, 7.8f}, {10.6f, 8.0f},
                {8.2f, 8.25f}, {6.3f, 8.5f}, {5.1f, 8.8f}, {4.2f, 8.95f}, {2.9f, 9.1f}, {1.6f, 9.4f}, {0.7f, 9.9f}, {0.22f, 10.6f},
                {0.12f, 11.6f}, {0.1f, 12.6f}, {0.08f, 13.4f}};
        int ni = yz.length, nj = 9;
        float[][][] P = new float[ni][nj][];
        for (int i = 0; i < ni; i++) {
            float y0 = yz[i][0];
            float pend = Math.max(0, Math.min(1, (16.6f - y0) / 3f));
            float sol = y0 < 0.5f ? (yz[i][1] - 9.9f) / 3.5f : 0;
            for (int j = 0; j < nj; j++) {
                float u = j / (float) (nj - 1);
                float xa = 8.6f - sol * 0.9f + (float) Math.sin(i * 0.7f) * 0.15f, xb = 12.2f + sol * 1.6f;
                float x = xa + (xb - xa) * u;
                float pli = (float) Math.sin(u * Math.PI * 3.0f + i * 0.35f) * 0.38f * pend * (1 - sol * 0.5f);
                float y = y0;
                if (y0 < 0.5f) y = 0.08f + Math.max(0, (float) Math.sin(u * 9 + i * 1.3f)) * 0.35f * (1 - sol * 0.6f);
                else if (y0 > 16.5f) y = y0 + (float) Math.max(0, Math.sin(u * 6 + i)) * 0.12f;
                P[i][j] = new float[]{x, y, yz[i][1] + (y0 < 0.5f ? 0 : pli)};
            }
        }
        return P;
    }
}
