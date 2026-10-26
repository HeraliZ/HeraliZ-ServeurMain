package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : botte de paille pressee, ficelee de deux tours de ficelle agricole orange, des brins qui depassent et de
 * la paille par terre. On s'y assoit. Variantes : nue, une couverture militaire jetee dessus, un carton aplati en
 * guise de coussin et des canettes.
 */
public final class BotteDesign extends DecorDesign {
    static final float W = 6.5f, H = 8.2f, D = 4.4f;

    private final int variant;
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public BotteDesign(int variant) {
        super("botte_paille_" + variant, 0xE8C860);
        this.variant = variant;
        itemScale = 0.9f;
        itemCenter = new float[]{8, 5, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        Random r = new Random(311 + variant);
        s.push().translate(8, 0, 8).rotateY(3);
        // la botte : bouts coupes sur les cotes, brins dans la longueur ailleurs
        Mat[] faces = {STRAW_END, STRAW_END, STRAW, STRAW, STRAW, STRAW};
        s.bevelBox(-W, 0, -D, W, H, D, 1.1f, STRAW, faces, MeshBuilder.SKIP_NY);
        // deux tours de ficelle
        for (float x : new float[]{-3.3f, 3.4f}) {
            float o = 0.16f;
            s.tube(new float[][]{p(x, 0.1f, -D - o), p(x, H - 1.1f, -D - o), p(x, H - 0.25f, -D + 0.25f), p(x, H + o, -D + 1.1f),
                    p(x, H + o, D - 1.1f), p(x, H - 0.25f, D - 0.25f), p(x, H - 1.1f, D + o), p(x, 0.1f, D + o)},
                    new float[]{0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f}, 5, TWINE, false, false, 0, false);
        }
        // brins qui depassent des bouts et du dessus
        for (int k = 0; k < 14; k++) {
            float[] a;
            float[] dir;
            int side = r.nextInt(3);
            if (side == 0) {
                float sx = r.nextBoolean() ? -W : W;
                a = p(sx, 0.6f + r.nextFloat() * (H - 1.2f), -D + 0.6f + r.nextFloat() * (2 * D - 1.2f));
                dir = p(Math.signum(sx) * (1.2f + r.nextFloat() * 1.6f), (r.nextFloat() - 0.3f) * 1.4f, (r.nextFloat() - 0.5f) * 1.6f);
            } else {
                a = p(-W + 0.8f + r.nextFloat() * (2 * W - 1.6f), H, -D + 0.6f + r.nextFloat() * (2 * D - 1.2f));
                dir = p((r.nextFloat() - 0.5f) * 3.2f, 0.4f + r.nextFloat() * 0.8f, (r.nextFloat() - 0.5f) * 1.4f);
            }
            s.tube(new float[][]{a, p(a[0] + dir[0], a[1] + dir[1], a[2] + dir[2])}, new float[]{0.09f, 0.06f}, 4, STRAW, false, false, 0, false);
        }
        s.pop();
        // paille par terre
        for (int k = 0; k < 22; k++) {
            float ang = r.nextFloat() * 6.283f, rad = 7 + r.nextFloat() * 2.5f;
            float x = 8 + (float) Math.cos(ang) * rad * 0.95f, z = 8 + (float) Math.sin(ang) * rad * 0.7f;
            if (x < 0.3f || x > 15.7f || z < 0.3f || z > 15.7f) continue;
            float da = r.nextFloat() * 6.283f, len = 1.2f + r.nextFloat() * 2.2f;
            s.tube(new float[][]{p(x, 0.08f, z), p(x + (float) Math.cos(da) * len, 0.08f, z + (float) Math.sin(da) * len)},
                    new float[]{0.08f, 0.07f}, 4, STRAW, false, false, 0, false);
        }
        if (variant == 1) {
            // vieille couverture militaire jetee dessus, un pan qui pend devant
            b.push().translate(8.6f, H + 0.35f, 7.6f).rotateY(-7);
            b.blob(5.6f, 0.6f, 4.0f, 0.6f, 0.25f, 2.2f, 17, 20, BLANKET);
            b.pop();
            b.push().translate(9.6f, H - 2.0f, 12.55f).rotateZ(6);
            b.blob(4.1f, 2.6f, 0.45f, 0.55f, 0.2f, 2.4f, 23, 16, BLANKET);
            b.pop();
        } else if (variant == 2) {
            // carton aplati en guise de coussin, deux canettes (une ecrasee)
            s.push().translate(8, H + 0.05f, 8).rotateY(-9);
            s.bevelBox(-4.8f, 0, -3.6f, 4.8f, 0.32f, 3.6f, 0.05f, CARDBOARD);
            s.pop();
            b.push().translate(13.6f, 0, 13.6f);
            can(b);
            b.pop();
            b.push().translate(2.4f, 0.55f, 13.4f).rotateY(35).rotateZ(90).scale(1, 0.55f, 1);
            can(b);
            b.pop();
        }
        Mesh[] m = bakePair(b, s, 0.3f, 2.2f, 0.45f, 0f);
        body = m[0];
        bodyS = m[1];
    }

    /** Canette : corps d'acier, bande rouge, dessus. */
    static void can(MeshBuilder b) {
        b.cylinder(0.66f, 0, 2.4f, 10, STEEL, STEEL, STEEL);
        b.push().translate(0, 0.7f, 0);
        b.cylinder(0.68f, 0, 1.0f, 10, PAINT_RED, null, null);
        b.pop();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
