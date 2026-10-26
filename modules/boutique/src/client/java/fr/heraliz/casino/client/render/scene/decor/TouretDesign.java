package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;
import fr.heraliz.casino.client.render.scene.machines.Recup;

import java.util.Random;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : touret de cable debout en guise de table de jeu. Flasques de contreplaque marquees au pochoir, restes de
 * cable autour du noyau ; dessus, des cartes jetees, des piles de jetons sales, un quart en fer, une bougie plantee
 * dans le goulot d'une bouteille (elle eclaire la table et coule).
 */
public final class TouretDesign extends DecorDesign {
    static final float CX = 8, CZ = 8, TOP = 12.5f;
    /** Bougie : position de la meche. */
    static final float[] WICK = {11.6f, TOP + 9.0f, 5.6f};

    private Mesh body = Mesh.EMPTY, chips = Mesh.EMPTY, candleHot = Mesh.EMPTY;

    public TouretDesign() {
        super("touret_table", 0xFFC870);
        itemScale = 0.8f;
        itemCenter = new float[]{8, 9, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(CX, 0, CZ);
        // flasques et noyau
        b.cylinder(7.2f, 0, 1.0f, 26, PALLET, SPOOL, SPOOL);
        b.cylinder(3.0f, 1.0f, TOP - 1.0f, 18, PALLET, null, null);
        for (int k = 0; k < 5; k++) {
            b.push().translate(0, 1.6f + k * 0.62f, 0).rotateY(k * 37);
            b.torus(3.3f, 0.3f, 20, 5, 0, 360, CABLE);
            b.pop();
        }
        b.push().translate(0, TOP - 1.0f, 0);
        b.cylinder(7.5f, 0, 1.0f, 26, PALLET, SPOOL, TABLETOP);
        b.pop();
        // boulons des flasques
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            b.push().translate((float) Math.cos(a) * 4.6f, TOP, (float) Math.sin(a) * 4.6f);
            b.rivet(0.32f, 0.18f, STEEL);
            b.pop();
        }
        b.pop();
        // quart en fer cabosse
        b.push().translate(4.2f, TOP, 11.4f).rotateZ(3);
        b.cylinder(0.95f, 0, 1.9f, 12, STEEL, STEEL, null);
        b.lathe(new float[][]{{0.85f, 0.25f}, {0.85f, 1.85f}}, 12, 0, 360, SOOT, 1, 1, 0, 0, 0, true);
        b.push().translate(0.95f, 0.95f, 0).rotateX(90);
        b.torus(0.55f, 0.1f, 10, 4, -80, 80, STEEL);
        b.pop();
        b.pop();
        // bouteille et bougie (la cire a coule sur le goulot)
        b.push().translate(WICK[0], TOP, WICK[2]);
        b.cylinder(0.95f, 0, 4.4f, 12, BOTTLE, BOTTLE, null);
        b.lathe(new float[][]{{0.95f, 4.4f}, {0.66f, 5.2f}, {0.36f, 5.8f}}, 12, 0, 360, BOTTLE, 1, 1, 0, 0, 0, false);
        b.push().translate(0, 5.8f, 0);
        b.cylinder(0.36f, 0, 1.0f, 8, BOTTLE, null, null);
        b.cylinder(0.3f, 1.0f, 3.1f, 8, WAX, null, WAX);
        for (int k = 0; k < 4; k++) {
            b.push().translate((float) Math.cos(k * 1.7f) * 0.38f, 1.0f - k * 0.45f, (float) Math.sin(k * 1.7f) * 0.38f);
            b.sphere(0.2f + (k % 2) * 0.06f, 6, 4, WAX);
            b.pop();
        }
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.3f, 2.2f, 0.45f, 0f);
        body = b.build();
        // piles de jetons (feuille commune des jetons de poker)
        MeshBuilder c = new MeshBuilder();
        chipStack(c, 5.2f, 5.0f, 1, 5);
        chipStack(c, 6.9f, 6.1f, 3, 3);
        chipStack(c, 9.6f, 9.8f, 0, 4);
        chipStack(c, 7.6f, 11.6f, 2, 2);
        chips = c.build();
        MeshBuilder h = new MeshBuilder();
        h.push().translate(WICK[0], WICK[1] - 0.1f, WICK[2]);
        h.cylinder(0.05f, 0, 0.35f, 4, HOT, null, HOT);
        h.pop();
        candleHot = h.build();
    }

    /** Pile de n jetons de la valeur k, un peu de travers. */
    private static void chipStack(MeshBuilder c, float x, float z, int k, int n) {
        Mat face = Recup.chipFace(k), edge = Recup.chipEdge(k);
        for (int i = 0; i < n; i++) {
            c.push().translate(x + (i % 2) * 0.06f, TOP + i * 0.24f, z - (i % 3) * 0.05f).rotateY(i * 47 + k * 13);
            c.cylinder(0.78f, 0, 0.23f, 12, edge, null, face);
            c.pop();
        }
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
        out.sheet(Recup.SHEET);
        out.solid(p, chips, 0);
        out.sheet(null);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        if (!v.on || s.item) return;
        float t = s.time + s.seed * 300;
        float f = 0.85f + 0.1f * (float) Math.sin(t * 0.9f) + 0.05f * (float) Math.sin(t * 2.7f + 1);
        out.solid(p, candleHot, f);
        float sway = 0.12f * (float) Math.sin(t * 0.6f);
        out.billboard(p, Sprite.FLAME, WICK[0] + sway, WICK[1] + 0.55f, WICK[2], 1.1f * f, sway * 40, 0xFFC860, 0.95f);
        out.billboard(p, Sprite.HALO, WICK[0], WICK[1] + 0.4f, WICK[2], 4.2f * f, 0, 0xFFA040, 0.4f * f);
        out.ground(p, Sprite.HALO, CX, TOP + 0.02f, CZ, 7.0f, 0, 0xFF9A40, 0.3f * f);
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        if (r.nextInt(14) == 0) fx.particle(FxSink.SMOKE, WICK[0], WICK[1] + 1.2f, WICK[2], rnd(r, 0.005f), 0.02f, rnd(r, 0.005f), 0x6A6460, 0.5f, 40);
    }
}
