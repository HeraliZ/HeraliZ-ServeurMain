package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : tabouret de bar, assise ronde capitonnee de cuir sang-de-boeuf sur un fut de laiton, repose-pieds en
 * anneau, pied lourd. On s'y assoit (la tete tourne librement).
 */
public final class TabouretDesign extends DecorDesign {
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public TabouretDesign() {
        super("tabouret_bar", 0xD8B060);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 7, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        b.push().translate(8, 0, 8);
        // pied lourd, fut, bagues
        b.lathe(new float[][]{{3.4f, 0}, {3.4f, 0.3f}, {2.9f, 0.7f}, {1.2f, 1.1f}, {0.55f, 1.6f}}, 22, 0, 360, POLISHED, 1, 1, 0, 0, 0, false);
        b.disc(3.4f, 0, 22, DARK, false);
        b.cylinder(0.5f, 1.4f, 12.4f, 12, POLISHED, null, null);
        for (float y : new float[]{1.7f, 12.0f}) {
            b.push().translate(0, y, 0);
            b.torus(0.6f, 0.15f, 12, 4, 0, 360, POLISHED);
            b.pop();
        }
        // repose-pieds : anneau et trois rayons
        b.push().translate(0, 5.4f, 0);
        b.torus(3.1f, 0.24f, 26, 6, 0, 360, POLISHED);
        for (int k = 0; k < 3; k++) {
            b.push().rotateY(k * 120 + 30).translate(0.5f, 0, 0).rotateZ(-90);
            b.cylinder(0.16f, 0, 2.5f, 6, POLISHED, null, null);
            b.pop();
        }
        b.pop();
        // platine sous l'assise
        b.push().translate(0, 12.3f, 0);
        b.cylinder(2.4f, 0, 0.35f, 18, POLISHED, POLISHED, null);
        b.pop();
        b.pop();
        // assise capitonnee (cuir) et son liseré de clous
        s.push().translate(8, 12.6f, 8);
        s.cylinder(3.8f, 0, 0.7f, 26, OXBLOOD, ChesterfieldDesign.SMOOTH, null);
        s.push().translate(0, 0.7f, 0);
        s.blob(3.8f, 0.55f, 3.8f, 0.5f, 0.0f, 1f, 1, 20, OXBLOOD);
        s.pop();
        s.pop();
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            b.push().translate(8 + (float) Math.cos(a) * 3.82f, 12.75f, 8 + (float) Math.sin(a) * 3.82f).rotateY((float) -Math.toDegrees(a)).rotateZ(-90);
            b.rivet(0.13f, 0.07f, POLISHED);
            b.pop();
        }
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
