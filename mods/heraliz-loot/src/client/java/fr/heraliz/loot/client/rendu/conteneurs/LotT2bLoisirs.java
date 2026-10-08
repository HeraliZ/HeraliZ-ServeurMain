package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.BOTTLE_BROWN;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.CORDURA;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.DARK;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.ORANGE;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.PAINT_RED;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.PLASTIC_GREEN;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.WHITE;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT2bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/** Lot T2b, loisirs et secours : sac de randonnée, matériel de camping, matériel de pêche, trousse de secours. */
final class LotT2bLoisirs {
    private LotT2bLoisirs() {
    }

    // =================================================================== sac de randonnée
    /**
     * Grand sac de randonnée (70 L) rouge passé appuyé contre le mur : rabat, poche avant, sangles de compression,
     * ceinture qui pend, gourde d'alu, tapis de sol roulé sanglé dessus ; sale, taché, déchiré (mousse qui sort).
     */
    static void sacRandonnee(MeshBuilder b) {
        recaler(b, true, LotT2bLoisirs::sacRando);
        epars(b, 3.0f, 9.0f, 13.0f, 13.0f, 6, FEUILLES, 1.2f, 111);
    }

    static void sacRando(MeshBuilder b) {
        b.push().translate(8.0f, 0, 6.0f).rotateY(8).rotateX(-14);
        b.color(0.62f, 0.27f, 0.21f);
        b.push().translate(0, 6.2f, 0);
        b.blob(2.9f, 5.6f, 2.4f, 0.55f, 0.03f, 1.0f, 3, 18, TISSU_USE);
        b.pop();
        b.push().translate(0, 3.8f, 1.95f);
        b.blob(2.2f, 2.3f, 0.95f, 0.6f, 0.04f, 1.2f, 5, 14, TISSU_USE);
        b.pop();
        b.push().translate(0, 11.6f, 0.35f);
        b.blob(3.0f, 0.95f, 2.6f, 0.6f, 0.03f, 1.2f, 7, 14, TISSU_USE);
        b.pop();
        b.color(1);
        b.push().translate(0, 1.15f, 0);
        b.blob(2.95f, 1.0f, 2.45f, 0.6f, 0, 1, 2, 14, CORDURA);
        b.pop();
        // sangles de compression et boucles
        for (float y : new float[]{4.8f, 8.4f}) {
            float[][] arc = new float[7][];
            for (int k = 0; k < 7; k++) {
                double a = Math.PI * k / 6;
                arc[k] = p((float) Math.cos(a) * 2.98f, y, (float) Math.sin(a) * 2.47f);
            }
            b.tube(arc, fill(7, 0.07f), 4, CORDURA, false, false, 0, false);
            b.bevelBox(-0.35f, y - 0.22f, 2.42f, 0.35f, y + 0.22f, 2.58f, 0.04f, DARK);
        }
        b.tube(new float[][]{p(-1.2f, 12.4f, 2.6f), p(-1.2f, 10.8f, 2.95f), p(-1.2f, 9.8f, 2.4f)}, fill(3, 0.07f), 4, CORDURA, false, false, 0, false);
        b.tube(new float[][]{p(1.2f, 12.4f, 2.6f), p(1.2f, 10.8f, 2.95f), p(1.2f, 9.8f, 2.4f)}, fill(3, 0.07f), 4, CORDURA, false, false, 0, false);
        // ceinture ventrale qui pend
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 2.9f, 1.8f, -0.6f).rotateZ(s * -15);
            b.blob(0.5f, 1.2f, 0.9f, 0.6f, 0, 1, 4, 10, CORDURA);
            b.pop();
            b.tube(new float[][]{p(s * 3.0f, 0.9f, -0.3f), p(s * 3.1f, 0.3f, 0.6f), p(s * 2.6f, 0.15f, 1.8f)}, fill(3, 0.06f), 4, CORDURA, false, false, 0, false);
        }
        // tapis de sol roulé, sanglé sur le rabat
        b.push().translate(0, 13.75f, -0.2f).rotateZ(90);
        b.cylinder(1.2f, -4.3f, 4.3f, 14, TAPIS_SOL, TAPIS_SOL, TAPIS_SOL);
        for (float y : new float[]{-2.4f, 2.4f}) {
            b.push().translate(0, y, 0);
            b.torus(1.22f, 0.07f, 14, 3, 0, 360, CORDURA);
            b.pop();
        }
        b.pop();
        // gourde d'alu dans la poche de côté
        b.push().translate(3.1f, 3.0f, 0.4f).rotateZ(-8);
        b.cylinder(0.62f, 0, 2.6f, 10, ALU, ALU, null);
        b.lathe(new float[][]{{0.62f, 2.6f}, {0.35f, 3.0f}, {0.3f, 3.3f}, {0.01f, 3.32f}}, 10, 0, 360, ALU, 1, 1, 0, 0, 0, false);
        b.pop();
        // étiquette, usure : déchirure (mousse), boue, tache
        b.decalZ(P_SAC.sub(0, 0, 1, 0.5f), 0, 11.6f, 3.05f, 2.2f, 0.55f);
        b.push().translate(-1.4f, 7.0f, 2.3f).rotateY(-20);
        b.decalZ(MOUSSE_JAUNE, 0, 0, 0.12f, 0.9f, 0.5f);
        b.pop();
        b.decalZ(BOUE_2, 0, 1.0f, 2.52f, 4.0f, 1.2f);
        b.pop();
    }

    // =================================================================== camping
    /**
     * Matériel de camping laissé contre le mur : sac de tente kaki (sangles, cordon), réchaud à cartouche de gaz avec
     * sa casserole cabossée, jerrican d'eau de 10 L, quart en émail ; feuilles, boue.
     */
    static void materielCamping(MeshBuilder b) {
        // sac de tente
        b.color(0.36f, 0.44f, 0.3f);
        b.push().translate(6.0f, 1.86f, 2.2f).rotateY(4).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -5.2f}, {1.2f, -5.1f}, {1.6f, -4.6f}, {1.65f, 0}, {1.6f, 4.4f}, {1.3f, 4.9f}, {0.6f, 5.3f}, {0.2f, 5.6f}, {0.01f, 5.65f}},
                16, 0, 360, TISSU_USE, 1, 1, 0.04f, 0.6f, 3, false);
        b.color(1);
        for (float y : new float[]{-2.6f, 2.4f}) {
            b.push().translate(0, y, 0);
            b.torus(1.7f, 0.1f, 16, 4, 0, 360, CORDURA);
            b.pop();
        }
        b.tube(new float[][]{p(0, 5.6f, 0), p(0.3f, 5.85f, 0.4f), p(0.9f, 5.7f, 0.9f)}, fill(3, 0.05f), 4, DARK, false, false, 0, false);
        b.push().translate(0.9f, 5.7f, 0.9f);
        b.sphere(0.2f, 6, 4, DARK);
        b.pop();
        b.pop();
        decal(b, P_SAC.sub(0, 0.5f, 1, 1), 'z', 6.6f, 1.86f, 4.0f, 2.6f, 0.65f);
        decal(b, BOUE_1, 'z', 3.0f, 0.9f, 3.95f, 3.0f, 0.9f);
        // réchaud à cartouche, casserole
        b.push().translate(12.6f, 0, 3.4f);
        b.cylinder(0.72f, 0, 1.7f, 12, P_GAZ, null, METAL_NU);
        b.lathe(new float[][]{{0.7f, 1.7f}, {0.4f, 1.95f}, {0.3f, 2.1f}, {0.3f, 2.3f}}, 10, 0, 360, METAL_NU, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.3f, 2.3f}, {0.55f, 2.45f}, {0.6f, 2.6f}, {0.01f, 2.62f}}, 10, 0, 360, ROUILLE, 1, 1, 0, 0, 0, false);
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(k * 120 + 15);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            b.tube(new float[][]{p(c * 0.5f, 2.3f, s * 0.5f), p(c * 1.1f, 2.55f, s * 1.1f), p(c * 1.1f, 2.85f, s * 1.1f)}, fill(3, 0.05f), 3, METAL_NU, false, false, 0, false);
        }
        b.push().translate(0.75f, 2.0f, 0).rotateZ(-90);
        b.cylinder(0.12f, 0, 0.5f, 6, METAL_NU, null, PAINT_RED);
        b.pop();
        b.push().translate(0, 2.86f, 0).rotateZ(4);
        b.lathe(new float[][]{{0.01f, 0}, {1.15f, 0}, {1.2f, 0.1f}, {1.25f, 1.1f}, {1.3f, 1.15f}}, 12, 0, 360, ALU, 1, 1, 0.03f, 0.8f, 7, false);
        b.lathe(new float[][]{{1.22f, 1.1f}, {1.15f, 0.11f}, {0.01f, 0.11f}}, 12, 0, 360, INTERIEUR_SALE, 1, 1, 0, 0, 0, true);
        b.tube(new float[][]{p(-1.25f, 0.9f, 0), p(-2.6f, 1.1f, 0.3f), p(-3.3f, 1.15f, 0.4f)}, fill(3, 0.1f), 4, ALU, false, true, 0, false);
        b.pop();
        b.pop();
        // jerrican d'eau
        b.color(0.62f, 0.75f, 0.86f);
        b.push().translate(5.4f, 0, 8.8f).rotateY(20);
        b.bevelBox(-2.0f, 0, -1.05f, 2.0f, 4.6f, 1.05f, 0.5f, PLASTIQUE_USE);
        b.tube(new float[][]{p(-1.3f, 4.5f, 0), p(-1.0f, 5.4f, 0), p(0.6f, 5.4f, 0), p(0.9f, 4.5f, 0)}, fill(4, 0.2f), 6, PLASTIQUE_USE, false, false, 0, false);
        b.color(1);
        b.push().translate(1.35f, 4.5f, 0);
        b.cylinder(0.42f, 0, 0.5f, 10, AtlasCivil.PLASTIC_BLUE, null, AtlasCivil.PLASTIC_BLUE);
        b.pop();
        b.push().translate(-1.1f, 0.9f, 1.0f).rotateX(90);
        b.cylinder(0.3f, 0, 0.6f, 8, AtlasCivil.PLASTIC_BLUE, null, AtlasCivil.PLASTIC_BLUE);
        b.pop();
        b.bevelBox(-1.3f, 0.75f, 1.45f, -0.9f, 1.4f, 1.65f, 0.05f, AtlasCivil.PLASTIC_BLUE);
        b.pop();
        // quart en émail renversé
        b.push().translate(10.4f, 0.98f, 9.6f).rotateZ(85).rotateY(30);
        b.color(0.85f, 0.82f, 0.75f);
        b.lathe(new float[][]{{0.01f, -0.8f}, {0.7f, -0.8f}, {0.75f, -0.7f}, {0.78f, 0.6f}, {0.82f, 0.68f}}, 12, 0, 360, EMAIL, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.74f, 0.65f}, {0.72f, -0.61f}, {0.01f, -0.61f}}, 12, 0, 360, INTERIEUR_SALE, 1, 1, 0, 0, 0, true);
        b.color(1);
        b.pop();
        epars(b, 1.0f, 6.0f, 15.0f, 14.0f, 10, FEUILLES, 1.3f, 121);
        decal(b, AUREOLE, 'y', 7.6f, 0, 11.6f, 3.0f, 2.0f, 15);
    }

    // =================================================================== pêche
    /**
     * Matériel de pêche : boîte à plateaux en porte-à-faux ouverte (leurres, flotteurs, plombs), deux cannes et une
     * épuisette appuyées contre le mur ; bobine de fil et flotteur tombés, plastique décoloré et sale.
     */
    static void caissePeche(MeshBuilder b) {
        b.push().translate(5.4f, 0, 8.0f).rotateY(-8);
        float L = 6.4f, W = 3.4f, H = 2.6f;
        b.bevelBox(-L / 2, 0, -W / 2, L / 2, H, W / 2, 0.25f, PLASTIC_GREEN, new Mat[]{null, null, null, LEURRES, null, null}, MeshBuilder.SKIP_NY);
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 2; k++) {
                float ty = H + 0.15f + k * 0.85f, tx = s * (1.7f + k * 1.2f);
                b.bevelBox(tx - 1.6f, ty, -W / 2 + 0.1f, tx + 1.6f, ty + 0.7f, W / 2 - 0.1f, 0.12f, PLASTIC_GREEN,
                        new Mat[]{null, null, null, LEURRES.sub(k * 0.5f, (s + 1) * 0.25f, k * 0.5f + 0.5f, (s + 1) * 0.25f + 0.5f), null, null}, 0);
                for (float z : new float[]{-W / 2 + 0.05f, W / 2 - 0.15f}) {
                    b.push().translate(s * (0.4f + k * 0.6f), H - 0.2f + k * 0.4f, z);
                    b.rotateZ(s * -40);
                    b.bevelBox(-0.12f, 0, 0, 0.12f, 1.6f, 0.1f, 0.03f, METAL_NU);
                    b.pop();
                }
            }
        }
        b.tube(new float[][]{p(-1.0f, H + 2.6f, 0), p(-0.8f, H + 3.1f, 0), p(0.8f, H + 3.1f, 0), p(1.0f, H + 2.6f, 0)}, fill(4, 0.12f), 5, DARK, false, false, 0, false);
        b.pop();
        // cannes et épuisette appuyées contre le mur
        canne(b, p(10.6f, 0.12f, 6.4f), p(11.2f, 19.6f, 0.15f), 0);
        canne(b, p(12.0f, 0.12f, 5.6f), p(12.8f, 18.4f, 0.15f), 1);
        epuisette(b);
        // bobine, flotteur tombés, ligne
        b.push().translate(9.0f, 0.5f, 12.4f).rotateX(90);
        b.cylinder(0.5f, -0.3f, 0.3f, 10, AtlasCivil.PLASTIC_BLUE, AtlasCivil.PLASTIC_BLUE, AtlasCivil.PLASTIC_BLUE);
        b.cylinder(0.32f, -0.25f, 0.25f, 10, ORANGE, null, null);
        b.pop();
        b.push().translate(3.0f, 0.3f, 12.6f).rotateZ(80).rotateY(30);
        b.lathe(new float[][]{{0.01f, -0.9f}, {0.25f, -0.4f}, {0.3f, 0}, {0.25f, 0.3f}, {0.01f, 0.5f}}, 8, 0, 360, PAINT_RED, 1, 1, 0, 0, 0, false);
        b.cylinder(0.04f, 0.4f, 1.3f, 4, WHITE, null, WHITE);
        b.pop();
        b.tube(new float[][]{p(9.0f, 0.5f, 12.4f), p(7.6f, 0.05f, 11.6f), p(6.2f, 0.05f, 12.8f), p(4.8f, 0.05f, 12.0f), p(3.4f, 0.25f, 12.6f)}, fill(5, 0.025f), 3, WHITE, false, false, 0, false);
        decal(b, BOUE_2, 'y', 6.0f, 0, 12.0f, 4.0f, 1.6f, 0);
        epars(b, 1.0f, 10.0f, 15.0f, 14.5f, 6, FEUILLES, 1.1f, 131);
    }

    /** Canne à pêche appuyée de a (talon au sol) à t (scion contre le mur), moulinet. */
    static void canne(MeshBuilder b, float[] a, float[] t, int n) {
        repere(b, a, p(1, 0, 0), sub(t, a));
        float L = dist(a, t);
        b.color(1.25f, 1.05f, 0.8f);
        b.tube(new float[][]{p(0, 0, 0), p(0, 2.4f, 0)}, new float[]{0.22f, 0.2f}, 7, SAPIN, true, false, 0, false);
        b.color(1);
        b.tube(new float[][]{p(0, 2.3f, 0), p(0, L * 0.5f, 0), p(0, L, 0)}, new float[]{0.15f, 0.08f, 0.03f}, 6, n == 0 ? DARK : AtlasCivil.BOTTLE_GREEN, false, true, 0, false);
        b.bevelBox(-0.15f, 2.6f, 0.12f, 0.15f, 3.2f, 0.4f, 0.04f, METAL_NU);
        b.push().translate(0, 2.9f, 0.85f).rotateZ(90);
        b.cylinder(0.45f, -0.35f, 0.35f, 10, DARK, METAL_NU, METAL_NU);
        b.pop();
        b.tube(new float[][]{p(0.35f, 2.9f, 0.85f), p(0.75f, 2.9f, 0.85f), p(0.75f, 2.5f, 1.2f)}, fill(3, 0.05f), 3, METAL_NU, false, true, 0, false);
        for (float y : new float[]{5.0f, 8.5f, 12.0f, L - 1.6f}) {
            b.push().translate(0, y, 0.14f).rotateX(90);
            b.torus(0.14f, 0.025f, 6, 3, 0, 360, METAL_NU);
            b.pop();
        }
        b.pop();
    }

    /** Épuisette appuyée contre le mur : manche, cercle, filet (double face). */
    static void epuisette(MeshBuilder b) {
        float[] a = p(14.4f, 0.12f, 5.0f), t = p(14.2f, 11.0f, 1.6f);
        repere(b, a, p(1, 0, 0), sub(t, a));
        float L = dist(a, t);
        b.tube(new float[][]{p(0, 0, 0), p(0, L, 0)}, new float[]{0.16f, 0.14f}, 7, METAL_NU, true, false, 0, false);
        b.push().translate(0, L + 1.45f, 0).rotateX(90);
        b.torus(1.45f, 0.08f, 16, 4, 0, 360, METAL_NU);
        b.pop();
        b.push().translate(0, L + 1.45f, 0).rotateX(115);
        b.lathe(new float[][]{{1.44f, 0}, {1.3f, 1.0f}, {0.9f, 2.0f}, {0.3f, 2.6f}, {0.01f, 2.7f}}, 14, 0, 360, FILET, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.42f, 0}, {1.28f, 1.0f}, {0.88f, 2.0f}, {0.28f, 2.6f}, {0.01f, 2.68f}}, 14, 0, 360, FILET, 1, 1, 0, 0, 0, true);
        b.pop();
        b.pop();
    }

    // =================================================================== trousse de secours
    /**
     * Boîte de premiers secours en tôle blanche à croix verte, ouverte sur un tabouret de ferme : bandes, flacon,
     * compresses, ciseaux ; une bande déroulée qui pend jusqu'au sol, boîte de cachets et emballages par terre.
     */
    static void trousseSecours(MeshBuilder b) {
        float cx = 8.0f, cz = 5.4f, ys = 7.2f;
        // tabouret
        b.color(1.02f, 0.96f, 0.88f);
        b.push().translate(cx, ys - 0.6f, cz);
        b.lathe(new float[][]{{0.01f, 0}, {2.3f, 0}, {2.5f, 0.18f}, {2.5f, 0.42f}, {2.35f, 0.6f}, {0.01f, 0.6f}}, 18, 0, 360, CHENE, 1, 1, 0, 0, 0, false);
        b.pop();
        float[][] pieds = new float[3][];
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(k * 120 + 90);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            pieds[k] = new float[]{c, s};
            b.tube(new float[][]{p(cx + c * 1.5f, ys - 0.55f, cz + s * 1.5f), p(cx + c * 2.3f, 0.02f, cz + s * 2.3f)}, new float[]{0.3f, 0.25f}, 7, CHENE, false, true, 0, false);
        }
        for (int k = 0; k < 3; k++) {
            float[] u = pieds[k], w = pieds[(k + 1) % 3];
            float r = 1.5f + 0.8f * (ys - 0.55f - 2.6f) / (ys - 0.55f);
            b.tube(new float[][]{p(cx + u[0] * r, 2.6f, cz + u[1] * r), p(cx + w[0] * r, 2.6f, cz + w[1] * r)}, fill(2, 0.14f), 5, CHENE, true, true, 0, false);
        }
        b.color(1);
        // boîte de tôle blanche ouverte
        float bx0 = cx - 2.3f, bx1 = cx + 2.3f, bz0 = cz - 1.5f, bz1 = cz + 1.3f, by = ys, bh = 2.0f, e = 0.12f;
        b.color(0.94f, 0.94f, 0.9f);
        b.bevelBox(bx0, by, bz0, bx1, by + bh, bz1, 0.12f, PEINTURE_USEE, null, MeshBuilder.SKIP_PY);
        float yi = by + 0.25f, yr = by + bh - 0.02f;
        b.face(PEINTURE_USEE, v(0, 0, -1), p(bx1 - e, yi, bz1 - e), p(bx0 + e, yi, bz1 - e), p(bx0 + e, yr, bz1 - e), p(bx1 - e, yr, bz1 - e));
        b.face(PEINTURE_USEE, v(0, 0, 1), p(bx0 + e, yi, bz0 + e), p(bx1 - e, yi, bz0 + e), p(bx1 - e, yr, bz0 + e), p(bx0 + e, yr, bz0 + e));
        b.face(PEINTURE_USEE, v(1, 0, 0), p(bx0 + e, yi, bz1 - e), p(bx0 + e, yi, bz0 + e), p(bx0 + e, yr, bz0 + e), p(bx0 + e, yr, bz1 - e));
        b.face(PEINTURE_USEE, v(-1, 0, 0), p(bx1 - e, yi, bz0 + e), p(bx1 - e, yi, bz1 - e), p(bx1 - e, yr, bz1 - e), p(bx1 - e, yr, bz0 + e));
        b.face(PEINTURE_USEE, v(0, 1, 0), p(bx0 + e, yi, bz1 - e), p(bx1 - e, yi, bz1 - e), p(bx1 - e, yi, bz0 + e), p(bx0 + e, yi, bz0 + e));
        // couvercle ouvert vers l'arrière
        b.push().translate(0, by + bh, bz0).rotateX(-105).translate(0, -(by + bh), -bz0);
        b.bevelBox(bx0 - 0.05f, by + bh, bz0 - 0.05f, bx1 + 0.05f, by + bh + 0.35f, bz1 + 0.05f, 0.1f, PEINTURE_USEE);
        b.color(1);
        b.push().translate(cx, by + bh, (bz0 + bz1) / 2).rotateX(90);
        b.decalZ(ETIQUETTE_DECHIREE, 0, 0, 0.05f, 3.4f, 1.7f);
        b.pop();
        b.pop();
        b.color(1);
        decal(b, SECOURS, 'z', cx, by + 1.0f, bz1, 1.75f, 1.75f);
        b.tube(new float[][]{p(bx0 + 0.6f, by + bh - 0.05f, bz0 - 0.08f), p(bx1 - 0.6f, by + bh - 0.05f, bz0 - 0.08f)}, fill(2, 0.1f), 5, METAL_NU, true, true, 0, false);
        coulure(b, 'z', bx0 + 0.5f, by + bh - 0.2f, bz1, 0.6f, 1);
        // contenu : bandes, flacon, compresses, ciseaux
        for (int k = 0; k < 3; k++) {
            b.push().translate(cx - 1.4f + k * 0.95f, yi + 0.45f, cz - 0.4f).rotateX(90);
            b.cylinder(0.45f, -0.45f, 0.45f, 10, GAZE(), GAZE(), GAZE());
            b.pop();
        }
        b.push().translate(cx + 1.4f, yi, cz + 0.2f);
        b.lathe(new float[][]{{0.01f, 0}, {0.45f, 0}, {0.48f, 0.1f}, {0.48f, 1.1f}, {0.25f, 1.4f}, {0.2f, 1.65f}}, 10, 0, 360, BOTTLE_BROWN, 1, 1, 0, 0, 0, false);
        b.cylinder(0.24f, 1.6f, 1.95f, 8, WHITE, null, WHITE);
        b.pop();
        b.push().translate(cx - 0.6f, yi + 0.9f, cz + 0.55f).rotateX(-75);
        b.bevelBox(-1.0f, -0.5f, -0.25f, 1.0f, 0.5f, 0.25f, 0.06f, PAPIER);
        b.pop();
        b.push().translate(cx + 1.5f, ys + 0.04f, cz + 1.75f).rotateY(-30);
        b.bevelBox(-1.0f, 0, -0.08f, 0.6f, 0.08f, 0.08f, 0.02f, CHROME_PIQUE);
        b.push().translate(0.85f, 0.04f, 0.2f);
        b.torus(0.25f, 0.06f, 8, 3, 0, 360, AtlasCivil.PLASTIC_GREY);
        b.translate(0, 0, -0.5f);
        b.torus(0.25f, 0.06f, 8, 3, 0, 360, AtlasCivil.PLASTIC_GREY);
        b.pop();
        b.pop();
        // bande déroulée qui pend du tabouret jusqu'au sol
        drap(b, bande(cx - 2.0f, ys, cz + 1.6f), GAZE());
        b.push().translate(cx - 2.5f, 0.45f, cz + 6.4f).rotateZ(90);
        b.cylinder(0.45f, -0.45f, 0.45f, 10, GAZE(), GAZE(), GAZE());
        b.pop();
        // boîte de cachets et emballages au sol
        b.push().translate(cx + 3.0f, 0.45f, cz + 5.6f).rotateY(40).rotateZ(90);
        b.cylinder(0.45f, -0.6f, 0.6f, 10, ORANGE, WHITE, WHITE);
        b.pop();
        epars(b, cx - 3.0f, cz + 3.0f, cx + 5.0f, cz + 8.0f, 6, PAPIER, 1.1f, 141);
        eclats(b, cx + 2.0f, cz + 6.6f, 1.2f, 4, WHITE, 144);
    }

    /** Gaze, bandes : toile blanche tachée. */
    static Mat GAZE() {
        return LINGE;
    }

    /** Bande de gaze déroulée : part de la boîte, passe sur le bord de l'assise et pend jusqu'au sol. */
    static float[][][] bande(float x, float ys, float z) {
        float[][] pts = {{x + 1.0f, ys + 0.12f, z - 1.6f}, {x + 0.4f, ys + 0.05f, z - 0.4f}, {x - 0.2f, ys + 0.03f, z + 0.3f}, {x - 0.42f, ys - 0.4f, z + 0.55f},
                {x - 0.5f, ys - 2.0f, z + 0.7f}, {x - 0.45f, ys - 4.2f, z + 0.85f}, {x - 0.5f, 1.4f, z + 1.1f}, {x - 0.6f, 0.25f, z + 1.6f},
                {x - 0.7f, 0.06f, z + 2.8f}, {x - 0.5f, 0.06f, z + 4.0f}};
        float[][][] P = new float[pts.length][2][];
        for (int i = 0; i < pts.length; i++) {
            float w = 0.35f;
            P[i][0] = new float[]{pts[i][0] - w, pts[i][1], pts[i][2]};
            P[i][1] = new float[]{pts[i][0] + w, pts[i][1], pts[i][2]};
        }
        return P;
    }
}
