package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : caddie de supermarche "emprunte" il y a longtemps, toute une vie dedans : sac poubelle bourre, couverture
 * roulee et ficelee, cartons, bouteilles, un sac plastique pendu a la poignee. Panier de fil chrome, poignee rouge,
 * quatre roulettes (une de travers).
 */
public final class CaddieDesign extends DecorDesign {
    /** Panier : rebord en haut (y, x, z) et fond (plus etroit, recule). */
    static final float TOP = 13.2f, TX0 = 3.2f, TX1 = 12.8f, TZ0 = 2.0f, TZ1 = 15.2f;
    static final float BOT = 6.4f, BX0 = 4.2f, BX1 = 11.8f, BZ0 = 4.4f, BZ1 = 14.6f;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public CaddieDesign() {
        super("caddie", 0xB8C0C8);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 8.5f, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        float[] t0 = p(TX0, TOP, TZ0), t1 = p(TX1, TOP, TZ0), t2 = p(TX1, TOP, TZ1), t3 = p(TX0, TOP, TZ1);
        float[] b0 = p(BX0, BOT, BZ0), b1 = p(BX1, BOT, BZ0), b2 = p(BX1, BOT, BZ1), b3 = p(BX0, BOT, BZ1);
        // ---- panier de fil (visible des deux cotes)
        quad2(s, CART, b0, b1, t1, t0);
        quad2(s, CART, b1, b2, t2, t1);
        quad2(s, CART, b2, b3, t3, t2);
        quad2(s, CART, b3, b0, t0, t3);
        quad2(s, CART, b0, b3, b2, b1);
        // ---- armature chromee : rebord, montants, chassis, poignee
        float r = 0.24f;
        s.tube(new float[][]{t0, t1, t2, t3, t0}, new float[]{r, r, r, r, r}, 6, CHROME, false, false, 0, false);
        for (float[][] e : new float[][][]{{b0, t0}, {b1, t1}, {b2, t2}, {b3, t3}}) s.tube(e, new float[]{0.2f, 0.2f}, 5, CHROME, false, false, 0, false);
        for (float x : new float[]{5.0f, 11.0f}) {
            s.tube(new float[][]{p(x, 3.0f, 2.6f), p(x, 3.0f, 14.6f), p(x, BOT, BZ1)}, new float[]{0.28f, 0.28f, 0.24f}, 6, CHROME, true, true, 0, false);
            s.tube(new float[][]{p(x, 3.0f, 2.6f), p(x < 8 ? TX0 : TX1, TOP + 1.8f, 0.9f)}, new float[]{0.28f, 0.26f}, 6, CHROME, true, true, 0, false);
        }
        // tablette du bas
        quad2(s, CART, p(5, 3.25f, 4.2f), p(11, 3.25f, 4.2f), p(11, 3.25f, 13.4f), p(5, 3.25f, 13.4f));
        // poignee : barre chromee et manchon de plastique rouge
        s.tube(new float[][]{p(TX0 - 0.3f, TOP + 1.8f, 0.9f), p(TX1 + 0.3f, TOP + 1.8f, 0.9f)}, new float[]{0.26f, 0.26f}, 6, CHROME, true, true, 0, false);
        s.push().translate(4.6f, TOP + 1.8f, 0.9f).rotateZ(-90);
        s.cylinder(0.55f, 0, 6.8f, 10, RED_PLASTIC, RED_PLASTIC, RED_PLASTIC);
        s.pop();
        s.tube(new float[][]{t0, p(TX0, TOP + 1.8f, 0.9f)}, new float[]{0.22f, 0.22f}, 5, CHROME, false, false, 0, false);
        s.tube(new float[][]{t1, p(TX1, TOP + 1.8f, 0.9f)}, new float[]{0.22f, 0.22f}, 5, CHROME, false, false, 0, false);
        // ---- roulettes (fourche d'acier, roue de caoutchouc ; la derniere de travers)
        float[][] wheels = {{5.0f, 3.0f, 0}, {11.0f, 3.0f, 0}, {5.0f, 14.2f, 0}, {11.0f, 14.2f, 38}};
        for (float[] w : wheels) {
            b.push().translate(w[0], 0, w[1]).rotateY(w[2]);
            b.bevelBox(-0.5f, 1.2f, -0.6f, 0.5f, 3.1f, 0.6f, 0.1f, STEEL);
            b.push().translate(-0.38f, 1.05f, 0).rotateZ(-90);
            b.cylinder(1.0f, 0, 0.76f, 12, RUBBER, RUBBER, RUBBER);
            b.pop();
            b.pop();
        }
        // ---- le chargement
        s.push().translate(8, TOP - 0.6f, 6.4f);
        s.blob(3.6f, 3.4f, 3.2f, 0.7f, 0.14f, 1.6f, 41, 18, TRASH);
        s.pop();
        s.push().translate(8.4f, TOP + 2.4f, 5.4f);
        s.blob(0.7f, 1.0f, 0.6f, 0.8f, 0.2f, 2f, 43, 10, TRASH);
        s.pop();
        // couverture roulee, ficelee
        b.push().translate(3.4f, TOP + 1.1f, 11.6f).rotateY(-6).rotateZ(-90);
        b.cylinder(1.7f, 0, 9.4f, 14, BLANKET, BLANKET, BLANKET);
        b.pop();
        for (float x : new float[]{5.4f, 10.4f}) {
            s.push().translate(x, TOP + 1.1f, 11.6f - (x - 3.4f) * 0.1f).rotateZ(90);
            s.torus(1.78f, 0.12f, 14, 4, 0, 360, TWINE);
            s.pop();
        }
        // cartons plies debout a l'arriere, bouteilles
        s.push().translate(8, TOP - 3.6f, 14.1f).rotateX(-12).rotateZ(5);
        s.bevelBox(-4.3f, 0, -0.18f, 4.3f, 9.4f, 0.18f, 0.05f, CARDBOARD);
        s.pop();
        s.push().translate(7.2f, TOP - 3.4f, 13.2f).rotateX(-6).rotateZ(-9);
        s.bevelBox(-3.5f, 0, -0.18f, 3.5f, 7.6f, 0.18f, 0.05f, CARDBOARD);
        s.pop();
        for (float[] bt : new float[][]{{11.2f, 9.4f, 10}, {10.5f, 8.6f, -14}}) {
            b.push().translate(bt[0], TOP - 3.2f, bt[1]).rotateZ(bt[2]);
            MatelasDesign.bottle(b);
            b.pop();
        }
        // sac plastique pendu a la poignee
        s.push().translate(11.3f, TOP + 0.2f, 0.5f);
        s.blob(1.3f, 1.9f, 1.0f, 0.75f, 0.2f, 2.2f, 47, 12, PLASTIC_W);
        s.pop();
        s.tube(new float[][]{p(11.0f, TOP + 1.8f, 0.9f), p(10.9f, TOP + 1.9f, 0.5f), p(11.1f, TOP + 1.8f, 0.4f)}, new float[]{0.1f, 0.1f, 0.1f}, 4, PLASTIC_W, false, false, 0, false);
        Mesh[] m = bakePair(b, s, 0.3f, 2.2f, 0.4f, 0f);
        body = m[0];
        bodyS = m[1];
    }

    /** Quadrilatere visible des deux cotes (grille ajouree). */
    static void quad2(MeshBuilder b, Mat m, float[] a, float[] c, float[] d, float[] e) {
        float[] n = normal(a, c, d);
        b.face(m, n, a, c, d, e);
        b.face(m, new float[]{-n[0], -n[1], -n[2]}, a, e, d, c);
    }

    static float[] normal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{nx / l, ny / l, nz / l};
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (r.nextInt(160) == 0) fx.particle(FxSink.FLY, 8 + rnd(r, 3), TOP + 2 + rnd(r, 1.5f), 7 + rnd(r, 3), rnd(r, 0.08f), rnd(r, 0.03f), rnd(r, 0.08f), 0x2A2A2A, 0.3f, 50);
    }
}
