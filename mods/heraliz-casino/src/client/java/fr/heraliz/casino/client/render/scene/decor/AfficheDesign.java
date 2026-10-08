package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Affiche de papier collee au mur : feuille un peu gondolee, un coin qui rebique, tenue par du scotch ou des clous
 * rouilles, parfois collee par-dessus une vieille affiche dechiree. Huit modeles (atlas "decor_affiches").
 */
public final class AfficheDesign extends DecorDesign {
    static final float W = 11.2f, H = 15.4f, CX = 8, CY = 8.05f;
    /** Inclinaison (degres) et fixation de chaque modele : 0 scotch, 1 clous, 2 scotch + vieille affiche dessous. */
    static final float[] TILT = {-2.2f, 1.6f, -1.1f, 2.8f, -0.5f, 2.0f, -3.0f, 0.9f};
    static final int[] FIX = {2, 0, 1, 1, 0, 2, 0, 1};
    /** Coin qui rebique : 0 bas droite, 1 bas gauche, 2 haut droite. */
    static final int[] CURL = {0, 1, 0, 2, 1, 0, 2, 1};

    private final int k;
    private Mesh body = Mesh.EMPTY;

    public AfficheDesign(int k) {
        super("affiche_" + k, POSTERS, 0xE8D8A8);
        this.k = k;
        itemScale = 1.15f;
        itemCenter = new float[]{8, 8, 0.4f};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        int fix = FIX[k % FIX.length];
        // ---- vieille affiche dechiree dessous (pas toujours)
        if (fix == 2) {
            b.push().translate(CX + 1.6f, CY - 1.2f, 0).rotateZ(-TILT[k] * 2.5f);
            paper(b, P_OLD_M, 10.4f, 12.0f, 0.04f, 0.05f, -1, 0);
            b.pop();
        }
        // ---- l'affiche
        b.push().translate(CX, CY, 0).rotateZ(TILT[k % TILT.length]);
        int[] r = poster(k);
        paper(b, Mat.fit(r[0], r[1], r[2], r[3]), W, H, 0.12f, 0.1f, CURL[k % CURL.length], 0.85f);
        // ---- fixations
        float hx = W / 2 - 0.55f, hy = H / 2 - 0.55f;
        if (fix == 1) {
            for (float[] q : new float[][]{{-hx, hy}, {hx, hy}, {-hx, -hy}}) nail(b, q[0], q[1]);
        } else {
            tape(b, P_TAPE_M, -hx + 0.2f, hy + 0.1f, 0.3f, 2.8f, 0.9f, 42);
            tape(b, P_TAPE2_M, hx - 0.2f, hy + 0.1f, 0.3f, 2.6f, 0.85f, -38);
            if (k % 2 == 0) tape(b, P_TAPE_M, -hx + 0.3f, -hy - 0.1f, 0.3f, 2.4f, 0.85f, -46);
        }
        b.pop();
        Occlusion.bake(b, 0.2f, 1.2f, 0.3f, Float.NaN);
        body = b.build();
    }

    /**
     * Feuille de papier (zone FIT) centree, legerement bombee, un coin qui se decolle du mur. curl : coin qui rebique
     * (-1 : aucun), lift : hauteur du coin decolle (px).
     */
    private static void paper(MeshBuilder b, Mat m, float w, float h, float z0, float bulge, int curl, float lift) {
        float cu = curl == 1 ? 0 : 1, cv = curl == 2 ? 0 : 1;
        b.surface((u, v) -> {
            float x = -w / 2 + u * w, y = h / 2 - v * h;
            float z = z0 + bulge * (float) (Math.sin(Math.PI * u) * Math.sin(Math.PI * v));
            if (curl >= 0) {
                float d = (float) Math.hypot(u - cu, v - cv);
                float t = Math.max(0, 1 - d / 0.38f);
                t = t * t;
                z += lift * t;
                // le coin qui se souleve se retracte un peu
                x += (cu == 1 ? -1 : 1) * 0.25f * t;
                y += (cv == 1 ? 1 : -1) * 0.2f * t;
            }
            return p(x, y, z);
        }, 10, 12, m, 1, 1, false, MeshBuilder.Orient.KEEP);
    }

    /** Clou rouille plante a travers le papier. */
    private static void nail(MeshBuilder b, float x, float y) {
        b.push().translate(x, y, 0.1f).rotateX(90);
        b.cylinder(0.07f, 0, 0.35f, 5, P_BACK_M, null, null);
        b.cylinder(0.24f, 0.35f, 0.42f, 8, P_NAIL_M, P_NAIL_M, P_NAIL_M);
        b.pop();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
