package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : mallette d'aluminium ouverte par terre, pleine de liasses de billets de la Banque Heraliz (bandes de
 * 10 000), d'autres liasses sanglees dans le couvercle, quelques billets epars.
 */
public final class ValiseDesign extends DecorDesign {
    static final float X0 = 2.2f, X1 = 13.8f, Z0 = 5.2f, Z1 = 12.6f, HB = 2.4f, LID_DEG = 104;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public ValiseDesign() {
        super("valise_billets", 0x7AC07A);
        itemScale = 0.95f;
        itemCenter = new float[]{8, 4, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // coque du bas : aluminium, interieur de mousse sombre, cornieres
        shell(s, b);
        // liasses dans la coque : 3 x 2, bandes de papier
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) bundle(s, X0 + 2.15f + i * 3.75f, HB - 0.9f, Z0 + 1.95f + j * 3.6f, 0, 1.1f);
        }
        // couvercle ouvert : coque fermee par-dessus (interieur vers le bas), pivotee sur l'arete du fond ; liasses
        // sanglees dedans
        for (MeshBuilder m : new MeshBuilder[]{s, b}) m.push().translate(0, HB, Z0).rotateX(-LID_DEG).translate(0, -HB, -Z0);
        lid(s, b);
        for (int i = 0; i < 3; i++) {
            s.push().translate(X0 + 2.15f + i * 3.75f, 2 * HB - 0.45f, Z0 + 3.7f).rotateZ(180);
            bundle(s, 0, 0, 0, 0, 0.9f);
            s.pop();
        }
        for (float z : new float[]{Z0 + 2.9f, Z0 + 4.0f}) s.bevelBox(X0 + 0.6f, 2 * HB - 1.5f, z, X1 - 0.6f, 2 * HB - 1.35f, z + 0.5f, 0.05f, DARK_STRAP);
        for (MeshBuilder m : new MeshBuilder[]{s, b}) m.pop();
        // poignee et fermoirs chromes sur l'avant
        s.tube(new float[][]{p(6.6f, 1.4f, Z1 + 0.1f), p(6.7f, 1.6f, Z1 + 1.2f), p(9.3f, 1.6f, Z1 + 1.2f), p(9.4f, 1.4f, Z1 + 0.1f)}, new float[]{0.28f, 0.3f, 0.3f, 0.28f},
                7, CHROME, true, true, 0, false);
        for (float x : new float[]{4.0f, 12.0f}) s.bevelBox(x - 0.6f, 1.2f, Z1, x + 0.6f, 2.2f, Z1 + 0.35f, 0.08f, CHROME);
        // billets epars
        Random r = new Random(171);
        for (int k = 0; k < 5; k++) {
            float x = 1.2f + r.nextFloat() * 13.6f, z = r.nextBoolean() ? 1.2f + r.nextFloat() * 2.6f : 13.4f + r.nextFloat() * 1.8f;
            s.push().translate(x, 0.04f + k * 0.01f, z).rotateY(r.nextFloat() * 180);
            s.face(BILLS, v(0, 1, 0), p(-1.5f, 0, 0.7f), p(1.5f, 0, 0.7f), p(1.5f, 0, -0.7f), p(-1.5f, 0, -0.7f));
            s.pop();
        }
        Mesh[] m = bakePair(b, s, 0.25f, 2.0f, 0.4f, 0f);
        body = m[0];
        bodyS = m[1];
    }

    /** Sangle de cuir noir du couvercle (le plastique noir des sacs, en plus sombre). */
    private static final Mat DARK_STRAP = TRASH;

    /** Coque d'aluminium ouverte vers le haut (fond, quatre parois, interieur de mousse sombre). */
    private static void shell(MeshBuilder s, MeshBuilder b) {
        float t = 0.35f;
        s.bevelBox(X0, 0, Z0, X1, 0.4f, Z1, 0.15f, ALU);
        s.bevelBox(X0, 0, Z0, X0 + t, HB, Z1, 0.1f, ALU);
        s.bevelBox(X1 - t, 0, Z0, X1, HB, Z1, 0.1f, ALU);
        s.bevelBox(X0, 0, Z0, X1, HB, Z0 + t, 0.1f, ALU);
        s.bevelBox(X0, 0, Z1 - t, X1, HB, Z1, 0.1f, ALU);
        b.box6(X0 + t, 0.4f, Z0 + t, X1 - t, 0.45f, Z1 - t, null, null, null, DARK, null, null);
        // cornieres chromees aux quatre coins
        for (float[] c : new float[][]{{X0, Z0}, {X1, Z0}, {X0, Z1}, {X1, Z1}}) {
            s.push().translate(c[0], 0, c[1]);
            s.cylinder(0.32f, 0, HB, 6, CHROME, CHROME, CHROME);
            s.pop();
        }
    }

    /** Couvercle ferme par-dessus la coque (de HB a 2 HB), interieur de mousse tourne vers le bas. */
    private static void lid(MeshBuilder s, MeshBuilder b) {
        float t = 0.35f, y0 = HB, y1 = 2 * HB;
        s.bevelBox(X0, y1 - 0.4f, Z0, X1, y1, Z1, 0.15f, ALU);
        s.bevelBox(X0, y0, Z0, X0 + t, y1, Z1, 0.1f, ALU);
        s.bevelBox(X1 - t, y0, Z0, X1, y1, Z1, 0.1f, ALU);
        s.bevelBox(X0, y0, Z0, X1, y1, Z0 + t, 0.1f, ALU);
        s.bevelBox(X0, y0, Z1 - t, X1, y1, Z1, 0.1f, ALU);
        b.box6(X0 + t, y1 - 0.45f, Z0 + t, X1 - t, y1 - 0.4f, Z1 - t, null, null, DARK, null, null, null);
    }

    /** Liasse de billets (dessus = billet, tranche empilee, bande de papier au milieu). */
    static void bundle(MeshBuilder s, float x, float y, float z, float rot, float h) {
        s.push().translate(x, y, z).rotateY(rot);
        s.bevelBox(-1.75f, 0, -0.82f, 1.75f, h, 0.82f, 0.05f, BILLS_SIDE, new Mat[]{BILLS_SIDE, BILLS_SIDE, BILLS_SIDE, BILLS, BILLS_SIDE, BILLS_SIDE}, 0);
        s.bevelBox(-0.32f, -0.02f, -0.85f, 0.32f, h + 0.03f, 0.85f, 0.02f, BAND);
        s.pop();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
