package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.machines.Recup;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : gueridon a plateau de marbre et pied de laiton. Dessus : une carafe de whisky taillee, deux verres a
 * moitie pleins, un cendrier ou se consume un cigare (il fume), des piles de jetons.
 */
public final class GueridonDesign extends DecorDesign {
    static final float TOP = 12.4f;
    static final float[] CIGAR_TIP = {10.9f, TOP + 0.75f, 11.6f};

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY, glass = Mesh.EMPTY, chips = Mesh.EMPTY, ember = Mesh.EMPTY;

    public GueridonDesign() {
        super("table_whisky", 0xD8A040);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 8, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder(), g = new MeshBuilder();
        // pied : trois pattes galbees, colonne tournee, plateau de marbre cercle de laiton
        b.push().translate(8, 0, 8);
        for (int k = 0; k < 3; k++) {
            double a = Math.PI * 2 * k / 3 + 0.5;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            b.tube(new float[][]{p(c * 0.6f, 3.6f, sn * 0.6f), p(c * 2.8f, 2.4f, sn * 2.8f), p(c * 4.6f, 0.6f, sn * 4.6f), p(c * 5.0f, 0.15f, sn * 5.0f)},
                    new float[]{0.55f, 0.42f, 0.32f, 0.3f}, 8, POLISHED, false, true, 0, false);
        }
        b.lathe(new float[][]{{0.9f, 2.8f}, {1.1f, 3.6f}, {0.55f, 4.4f}, {0.45f, 8.0f}, {0.8f, 9.0f}, {0.5f, 10.0f}, {0.45f, 11.0f}, {1.6f, 11.6f}}, 14, 0, 360, POLISHED,
                1, 1, 0, 0, 0, false);
        b.push().translate(0, TOP - 0.15f, 0);
        b.torus(6.65f, 0.28f, 32, 5, 0, 360, POLISHED);
        b.pop();
        b.pop();
        s.push().translate(8, TOP - 0.8f, 8);
        s.cylinder(6.6f, 0, 0.8f, 32, MARBLE, MARBLE, MARBLE);
        s.pop();
        // carafe taillee (whisky ambre) et bouchon
        s.push().translate(5.4f, TOP, 6.2f);
        s.lathe(new float[][]{{1.5f, 0}, {1.7f, 0.4f}, {1.75f, 2.8f}, {1.2f, 3.6f}, {0.5f, 4.2f}, {0.45f, 5.2f}}, 8, 0, 360, WHISKY, 1, 1, 0, 0, 0, false);
        s.pop();
        g.push().translate(5.4f, TOP, 6.2f);
        g.lathe(new float[][]{{1.62f, 0}, {1.84f, 0.4f}, {1.88f, 3.0f}, {1.3f, 3.8f}, {0.58f, 4.4f}, {0.55f, 5.4f}, {0.7f, 5.6f}}, 8, 0, 360, GLASS_S, 1, 1, 0, 0, 0,
                false);
        g.pop();
        b.push().translate(5.4f, TOP + 5.6f, 6.2f);
        b.gem(0.9f, 0.5f, 0.9f, 8, GLASSDK);
        b.pop();
        // deux verres a whisky
        for (float[] gl : new float[][]{{9.4f, 6.0f, 0.55f}, {11.2f, 8.4f, 0.35f}}) {
            s.push().translate(gl[0], TOP, gl[1]);
            s.cylinder(0.92f, 0.15f, 0.15f + 1.8f * gl[2] * 2, 10, WHISKY, null, WHISKY);
            s.pop();
            g.push().translate(gl[0], TOP, gl[1]);
            g.cylinder(1.05f, 0, 2.2f, 10, GLASS_S, GLASS_S, null);
            g.pop();
        }
        // cendrier de laiton, cigare pose dessus
        b.push().translate(10.4f, TOP, 11.2f);
        b.lathe(new float[][]{{1.5f, 0}, {1.6f, 0.5f}, {1.3f, 0.55f}, {1.1f, 0.25f}}, 16, 0, 360, POLISHED, 1, 1, 0, 0, 0, false);
        b.disc(1.1f, 0.25f, 16, SOOT, true);
        b.pop();
        s.push().translate(8.4f, TOP + 0.75f, 11.0f).rotateY(-18).rotateZ(-88);
        s.cylinder(0.32f, 0, 2.6f, 10, CIGAR, CIGAR, CIGAR);
        s.pop();
        // jetons empiles
        MeshBuilder c = new MeshBuilder();
        chipStack(c, 3.6f, 10.2f, 4, 6);
        chipStack(c, 5.1f, 11.4f, 2, 4);
        chipStack(c, 12.4f, 5.4f, 3, 5);
        chips = c.build();
        Mesh[] m = bakePair(b, s, 0.25f, 2.0f, 0.45f, 0f);
        body = m[0];
        bodyS = m[1];
        glass = g.build();
        MeshBuilder e = new MeshBuilder();
        e.push().translate(CIGAR_TIP[0], CIGAR_TIP[1], CIGAR_TIP[2]).rotateY(-18).rotateZ(-88);
        e.cylinder(0.33f, 0, 0.12f, 10, HOT, HOT, HOT);
        e.pop();
        ember = e.build();
    }

    private static void chipStack(MeshBuilder c, float x, float z, int k, int n) {
        Mat face = Recup.chipFace(k), edge = Recup.chipEdge(k);
        for (int i = 0; i < n; i++) {
            c.push().translate(x + (i % 2) * 0.05f, TOP + i * 0.24f, z).rotateY(i * 41 + k * 17);
            c.cylinder(0.78f, 0, 0.23f, 12, edge, null, face);
            c.pop();
        }
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
        out.sheet(Recup.SHEET);
        out.solid(p, chips, 0);
        out.sheet(null);
        out.translucent(p, glass, 0xFFFFFF, 0.5f);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        if (s.item) return;
        float t = s.time + s.seed * 200;
        out.solid(p, ember, 0.6f + 0.4f * (float) Math.sin(t * 0.21f));
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (r.nextInt(9) == 0) {
            fx.particle(FxSink.SMOKE, CIGAR_TIP[0] + 0.3f, CIGAR_TIP[1] + 0.2f, CIGAR_TIP[2], rnd(r, 0.005f), 0.018f, rnd(r, 0.005f), 0x9A9490, 0.45f, 50);
        }
    }
}
