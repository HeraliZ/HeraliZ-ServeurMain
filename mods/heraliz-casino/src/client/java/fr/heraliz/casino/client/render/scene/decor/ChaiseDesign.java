package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : chaise de jardin en plastique blanc (monobloc), grisee et tachee de vert, le dossier a fentes, un pied
 * fendu rafistole au chatterton. On s'y assoit.
 */
public final class ChaiseDesign extends DecorDesign {
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public ChaiseDesign() {
        super("chaise_jardin", 0xE8E4D4);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 7, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // assise legerement creusee
        s.push().translate(8, 6.9f, 8.6f);
        s.blob(5.0f, 0.42f, 4.7f, 0.25f, 0.0f, 1f, 1, 18, PLASTIC_W);
        s.pop();
        // pieds evases (section ovale)
        float[][] legs = {{3.4f, 4.4f, 2.5f, 3.1f}, {12.6f, 4.4f, 13.5f, 3.1f}, {3.6f, 12.8f, 2.6f, 14.0f}, {12.4f, 12.8f, 13.4f, 14.0f}};
        for (float[] l : legs) {
            s.tube(new float[][]{p(l[0], 6.9f, l[1]), p((l[0] + l[2]) / 2, 3.4f, (l[1] + l[3]) / 2), p(l[2], 0.05f, l[3])}, new float[]{0.72f, 0.62f, 0.55f}, 8,
                    PLASTIC_W, false, true, 0, false);
        }
        // dossier incline a fentes horizontales
        s.push().translate(8, 7.2f, 3.4f).rotateX(-13);
        s.bevelBox(-5.2f, 0, -0.38f, 5.2f, 8.6f, 0.38f, 0.3f, PLASTIC_W);
        // fentes : creux sombres (le plastique noir des sacs fait l'ombre)
        for (int k = 0; k < 3; k++) s.bevelBox(-3.6f, 3.6f + k * 1.5f, 0.3f, 3.6f, 4.2f + k * 1.5f, 0.42f, 0.05f, TRASH);
        s.pop();
        // accoudoirs
        for (float x : new float[]{2.8f, 13.2f}) {
            s.tube(new float[][]{p(x, 11.6f, 3.9f), p(x, 10.9f, 8.4f), p(x, 10.2f, 12.2f), p(x + (x < 8 ? 0.6f : -0.6f), 7.0f, 12.8f)}, new float[]{0.42f, 0.42f, 0.42f, 0.5f}, 7,
                    PLASTIC_W, true, false, 0, false);
        }
        // pied fendu, chatterton
        b.push().translate(12.95f, 2.8f, 13.45f).rotateY(-30);
        b.cylinder(0.72f, -0.9f, 0.9f, 10, TAPE, TAPE, TAPE);
        b.pop();
        Mesh[] m = bakePair(b, s, 0.25f, 2.0f, 0.4f, 0f);
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
