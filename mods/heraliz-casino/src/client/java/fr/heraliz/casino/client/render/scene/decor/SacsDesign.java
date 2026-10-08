package fr.heraliz.casino.client.render.scene.decor;

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
 * Le Trou : tas de sacs poubelle noirs noues, l'un eventre (papiers froisses, canettes, une bouteille), des mouches.
 */
public final class SacsDesign extends DecorDesign {
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public SacsDesign() {
        super("sacs_poubelle", 0x5A5A5A);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 5, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // sacs : x, y, z, rayons, rotation, graine
        float[][] bags = {
                {4.8f, 3.7f, 5.0f, 4.0f, 3.8f, 3.6f, 12, 81},
                {11.2f, 3.4f, 5.6f, 3.7f, 3.5f, 3.4f, -20, 82},
                {7.6f, 3.1f, 11.2f, 4.2f, 3.2f, 3.4f, 35, 83},
                {8.2f, 7.4f, 7.4f, 3.0f, 2.8f, 2.8f, 5, 84}};
        for (float[] g : bags) {
            s.push().translate(g[0], g[1], g[2]).rotateY(g[6]);
            s.blob(g[3], g[4], g[5], 0.75f, 0.16f, 1.5f, (int) g[7], 20, TRASH);
            // noeud et oreilles du sac
            s.push().translate(0, g[4] * 0.92f, 0);
            s.blob(0.8f, 0.7f, 0.8f, 0.9f, 0.2f, 2f, (int) g[7] + 10, 10, TRASH);
            for (int e = -1; e <= 1; e += 2) {
                s.tube(new float[][]{p(0, 0.4f, 0), p(e * 0.9f, 1.2f, 0.2f), p(e * 1.4f, 1.6f, -0.2f)}, new float[]{0.32f, 0.26f, 0.12f}, 6, TRASH, false, true, 0, false);
            }
            s.pop();
            s.pop();
        }
        // sac eventre : dechets repandus devant
        Random r = new Random(85);
        for (int k = 0; k < 7; k++) {
            float x = 9.5f + r.nextFloat() * 5.5f, z = 12.4f + r.nextFloat() * 3.0f, rad = 0.6f + r.nextFloat() * 0.6f;
            s.push().translate(x, rad * 0.7f, z);
            s.rock(rad, rad * 0.8f, rad, 0.35f, 1.6f, 90 + k, 8, PLASTIC_W);
            s.pop();
        }
        b.push().translate(13.6f, 0.62f, 13.2f).rotateY(70).rotateZ(90);
        BotteDesign.can(b);
        b.pop();
        b.push().translate(4.0f, 0.62f, 14.4f).rotateY(-30).rotateZ(90).scale(1, 0.7f, 1);
        BotteDesign.can(b);
        b.pop();
        b.push().translate(14.6f, 0.85f, 9.2f).rotateY(-40).rotateZ(90);
        MatelasDesign.bottle(b);
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

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (r.nextInt(45) == 0) {
            fx.particle(FxSink.FLY, 8 + rnd(r, 5), 6 + rnd(r, 3), 9 + rnd(r, 4), rnd(r, 0.09f), rnd(r, 0.04f), rnd(r, 0.09f), 0x1A1A1A, 0.28f, 60);
        }
    }
}
