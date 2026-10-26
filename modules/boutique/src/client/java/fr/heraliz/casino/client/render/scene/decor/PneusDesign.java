package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : trois pneus empiles en guise de tabouret, un coussin de carton plie dessus. On s'y assoit.
 */
public final class PneusDesign extends DecorDesign {
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public PneusDesign() {
        super("pneus", 0x8A8A8A);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 6, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        float[][] tires = {{8.0f, 2.0f, 8.0f, 0}, {8.3f, 6.0f, 7.7f, 25}, {7.8f, 10.0f, 8.2f, 50}};
        for (float[] t : tires) {
            b.push().translate(t[0], t[1], t[2]).rotateY(t[3]).scale(1, 0.95f, 1);
            // bande de roulement, flancs et jante interieure (sombre)
            b.torus(5.1f, 2.05f, 28, 10, 0, 360, RUBBER);
            b.pop();
            b.push().translate(t[0], t[1] - 1.9f, t[2]);
            b.disc(3.2f, 0.02f, 18, DARK, true);
            b.pop();
        }
        // coussin de carton plie sur le dessus
        s.push().translate(8, 12.05f, 8).rotateY(-14);
        s.bevelBox(-4.4f, 0, -4.4f, 4.4f, 0.55f, 4.4f, 0.12f, CARDBOARD);
        s.pop();
        // une vieille jante posee contre la pile
        b.push().translate(13.2f, 2.5f, 13.6f).rotateY(-35).rotateX(78);
        b.cylinder(2.3f, -0.5f, 0.5f, 18, RUST, RUST, RUST);
        b.torus(2.35f, 0.26f, 18, 5, 0, 360, RUST);
        b.pop();
        Mesh[] m = bakePair(b, s, 0.3f, 2.4f, 0.5f, 0f);
        body = m[0];
        bodyS = m[1];
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
