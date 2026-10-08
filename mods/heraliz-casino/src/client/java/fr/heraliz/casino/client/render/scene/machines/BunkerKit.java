package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;

/**
 * Pieces de recuperation communes aux machines du bunker : futs, caisses, parpaings, pneus, sacs de sable, baladeuses,
 * jerricans, batteries, cables. Tout ce qui fait qu'une table de jeu a l'air bricolee avec ce qu'on a trouve.
 */
final class BunkerKit {
    private BunkerKit() {
    }

    /**
     * Meme materiau, a la densite ajustee pour que sa texture fasse un nombre entier de tours autour d'une surface de
     * revolution de ce profil (points rayon, y) : pas de raccord visible la ou le tour se referme.
     */
    static Mat seamless(Mat m, float[][] profile) {
        float r = 0;
        for (float[] q : profile) r += q[0];
        float circ = (float) (2 * Math.PI * Math.max(0.5f, r / profile.length));
        float world = m.world > 0 ? m.world : 16f;
        return m.withWorld(circ / Math.max(1, Math.round(circ / world)));
    }

    /** Fut de 200 L debout (pose en y) : corps, quatre cerclages, fond et couvercle bordes. */
    static void drum(MeshBuilder b, float x, float y, float z, float r, float h, int segs, Mat body, Mat rust) {
        b.push().translate(x, y, z);
        b.cylinder(r, 0, h, segs, body, rust, rust);
        for (float k : new float[]{0.03f, 0.34f, 0.66f, 0.97f}) {
            b.push().translate(0, h * k, 0);
            b.torus(r + 0.02f, Math.max(0.12f, r * 0.045f), segs, 4, 0, 360, rust);
            b.pop();
        }
        // bonde sur le couvercle
        b.push().translate(r * 0.55f, h, 0);
        b.cylinder(r * 0.12f, 0, 0.18f, 6, rust, null, rust);
        b.pop();
        b.pop();
    }

    /** Fut couche le long de X (centre en x, pose sur y). */
    static void drumLying(MeshBuilder b, float x, float y, float z, float r, float len, int segs, Mat body, Mat rust) {
        b.push().translate(x - len / 2, y + r, z).rotateZ(-90);
        b.cylinder(r, 0, len, segs, body, rust, rust);
        for (float k : new float[]{0.03f, 0.34f, 0.66f, 0.97f}) {
            b.push().translate(0, len * k, 0);
            b.torus(r + 0.02f, Math.max(0.12f, r * 0.045f), segs, 4, 0, 360, rust);
            b.pop();
        }
        b.pop();
    }

    /** Caisse de bois : panneaux, deux ceintures de lattes et montants d'angle en relief. */
    static void crate(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, Mat board, Mat slat) {
        b.bevelBox(x0, y0, z0, x1, y1, z1, 0.15f, board);
        float t = 0.14f, s = Math.min(0.9f, (y1 - y0) * 0.16f);
        for (float y : new float[]{y0 + s * 0.4f, y1 - s * 1.4f}) {
            b.bevelBox(x0 - t, y, z0 - t, x1 + t, y + s, z1 + t, 0.06f, slat);
        }
        float w = Math.min(0.8f, (x1 - x0) * 0.12f);
        for (float x : new float[]{x0 - t, x1 + t - w}) {
            for (float z : new float[]{z0 - t, z1 + t - w}) {
                b.bevelBox(x, y0, z, x + w, y1, z + w, 0.05f, slat);
            }
        }
    }

    /** Palette : trois chevrons et des planches disjointes par-dessus (dessus en y1). */
    static void pallet(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, int boards, Mat wood) {
        float th = Math.min(0.5f, (y1 - y0) * 0.3f);
        for (float k : new float[]{0.04f, 0.5f, 0.96f}) {
            float z = z0 + (z1 - z0) * k;
            b.bevelBox(x0, y0, z - 0.45f, x1, y1 - th, z + 0.45f, 0.06f, wood);
        }
        float step = (x1 - x0) / boards;
        for (int i = 0; i < boards; i++) {
            b.bevelBox(x0 + i * step + 0.08f, y1 - th, z0 - (i % 2) * 0.12f, x0 + (i + 1) * step - 0.08f, y1, z1 + (i % 3) * 0.08f, 0.05f, wood);
        }
    }

    /** Parpaing : bloc de beton a deux alveoles (creux marques sur le dessus). */
    static void block(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, Mat concrete, Mat dark) {
        b.bevelBox(x0, y0, z0, x1, y1, z1, 0.12f, concrete);
        float mx = (x1 - x0) * 0.1f, mz = (z1 - z0) * 0.18f, cx = (x0 + x1) / 2;
        b.face(dark, MeshBuilder.v(0, 1, 0), p(x0 + mx, y1 + 0.01f, z1 - mz), p(cx - mx / 2, y1 + 0.01f, z1 - mz), p(cx - mx / 2, y1 + 0.01f, z0 + mz), p(x0 + mx, y1 + 0.01f, z0 + mz));
        b.face(dark, MeshBuilder.v(0, 1, 0), p(cx + mx / 2, y1 + 0.01f, z1 - mz), p(x1 - mx, y1 + 0.01f, z1 - mz), p(x1 - mx, y1 + 0.01f, z0 + mz), p(cx + mx / 2, y1 + 0.01f, z0 + mz));
    }

    /** Pneu couche (axe Y), centre en (x, y, z) : y = le dessous. */
    static void tire(MeshBuilder b, float x, float y, float z, float R, float r, int segs, Mat rubber) {
        b.push().translate(x, y + r, z).scale(1, 0.92f, 1);
        b.torus(R, r, segs, 8, 0, 360, rubber);
        b.pop();
    }

    /** Sac de sable : masse aplatie, legerement deformee, posee sur y. */
    static void sandbag(MeshBuilder b, float x, float y, float z, float lx, float ly, float lz, float yaw, int seed, Mat burlap) {
        b.push().translate(x, y + ly / 2, z).rotateY(yaw);
        b.blob(lx / 2, ly / 2, lz / 2, 0.55f, 0.1f, 1.6f, seed, 10, burlap);
        b.pop();
    }

    /**
     * Baladeuse : culot et grille de protection d'une ampoule centree en (x, y, z), pendue par le haut. L'ampoule
     * (sphere de rayon 0.42) est dessinee a part pour pouvoir briller.
     */
    static void cageLamp(MeshBuilder b, float x, float y, float z, Mat metal) {
        b.push().translate(x, y, z);
        b.cylinder(0.3f, 0.45f, 1.0f, 8, metal, metal, metal);
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6;
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            b.tube(new float[][]{p(cx * 0.3f, 0.5f, cz * 0.3f), p(cx * 0.62f, 0.1f, cz * 0.62f), p(cx * 0.62f, -0.45f, cz * 0.62f), p(cx * 0.2f, -0.78f, cz * 0.2f)},
                    new float[]{0.045f, 0.045f, 0.045f, 0.045f}, 4, metal, false, false, 0, false);
        }
        b.push().translate(0, -0.15f, 0);
        b.torus(0.62f, 0.045f, 12, 4, 0, 360, metal);
        b.pop();
        b.pop();
    }

    /** Applique de bunker : socle rond et grille en croix devant une ampoule centree en (x, y, z), face a +Z. */
    static void bulkhead(MeshBuilder b, float x, float y, float z, float r, Mat metal) {
        b.push().translate(x, y, z).rotateX(90);
        b.cylinder(r * 1.25f, -0.35f, 0, 10, metal, metal, metal);
        b.torus(r * 1.12f, r * 0.16f, 12, 4, 0, 360, metal);
        b.pop();
        b.bevelBox(x - r * 1.15f, y - 0.06f, z + r * 0.75f, x + r * 1.15f, y + 0.06f, z + r * 0.88f, 0.02f, metal);
        b.bevelBox(x - 0.06f, y - r * 1.15f, z + r * 0.75f, x + 0.06f, y + r * 1.15f, z + r * 0.88f, 0.02f, metal);
    }

    /** Jerrican de 20 L (pose sur y, face large vers +Z). */
    static void jerrycan(MeshBuilder b, float x, float y, float z, float s, float yaw, Mat paint, Mat rust) {
        b.push().translate(x, y, z).rotateY(yaw).scale(s);
        b.bevelBox(-1.7f, 0, -0.85f, 1.7f, 4.4f, 0.85f, 0.35f, paint);
        // croix emboutie sur le flanc, poignees, bouchon
        b.bevelBox(-1.2f, 1.9f, 0.82f, 1.2f, 2.3f, 0.95f, 0.05f, paint);
        b.bevelBox(-0.2f, 0.8f, 0.82f, 0.2f, 3.4f, 0.95f, 0.05f, paint);
        for (float dx : new float[]{-0.55f, 0, 0.55f}) b.bevelBox(dx - 0.13f, 4.4f, -0.7f, dx + 0.13f, 5.0f, 0.2f, 0.06f, rust);
        b.push().translate(1.15f, 4.4f, 0.35f);
        b.cylinder(0.32f, 0, 0.55f, 8, rust, null, rust);
        b.pop();
        b.pop();
    }

    /** Batterie de voiture (posee sur y) avec ses deux bornes. */
    static void battery(MeshBuilder b, float x, float y, float z, float w, float h, float d, Mat box, Mat rust, Mat dark) {
        b.bevelBox(x - w / 2, y, z - d / 2, x + w / 2, y + h, z + d / 2, 0.2f, box);
        for (int k = -1; k <= 1; k += 2) {
            b.push().translate(x + k * w * 0.3f, y + h, z - d * 0.2f);
            b.cylinder(0.3f, 0, 0.45f, 8, k < 0 ? rust : dark, null, k < 0 ? rust : dark);
            b.pop();
        }
    }

    /** Cable qui pend entre deux points (fleche en y). */
    static void cable(MeshBuilder b, float[] from, float[] to, float sag, float r, Mat m) {
        int n = 6;
        float[][] path = new float[n + 1][];
        float[] radii = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            path[i] = p(from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t - sag * 4 * t * (1 - t), from[2] + (to[2] - from[2]) * t);
            radii[i] = r;
        }
        b.tube(path, radii, 5, m, true, true, 0, false);
    }

    /** Cornieres d'acier aux quatre aretes verticales d'un caisson. */
    static void corners(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float w, Mat rust) {
        float t = 0.12f;
        for (float[] c : new float[][]{{x0 - t, z0 - t, 1, 1}, {x1 + t, z0 - t, -1, 1}, {x0 - t, z1 + t, 1, -1}, {x1 + t, z1 + t, -1, -1}}) {
            float xa = Math.min(c[0], c[0] + c[2] * w), xb = Math.max(c[0], c[0] + c[2] * w);
            float za = Math.min(c[1], c[1] + c[3] * w), zb = Math.max(c[1], c[1] + c[3] * w);
            b.bevelBox(xa, y0, Math.min(c[1], c[1] + c[3] * t * 1.4f), xb, y1, Math.max(c[1], c[1] + c[3] * t * 1.4f), 0.03f, rust);
            b.bevelBox(Math.min(c[0], c[0] + c[2] * t * 1.4f), y0, za, Math.max(c[0], c[0] + c[2] * t * 1.4f), y1, zb, 0.03f, rust);
        }
    }

    /** Rangee de rivets le long d'un segment horizontal (axe X) sur une face tournee vers +Z. */
    static void rivetsX(MeshBuilder b, float x0, float x1, float y, float z, int n, float r, Mat m) {
        for (int i = 0; i < n; i++) {
            float x = x0 + (x1 - x0) * (i + 0.5f) / n;
            b.push().translate(x, y, z).rotateX(90);
            b.rivet(r, r * 0.6f, m);
            b.pop();
        }
    }
}
