package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.machines.Recup;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : pancarte de carton ecrite au feutre, appuyee contre le mur, et la boite de conserve ou tombent les jetons
 * (trois jetons dedans, un a cote). Variantes : "1 JETON S.V.P.", "J'AI TOUT PERDU AU BLACKJACK", "LA MAISON GAGNE
 * TOUJOURS", "DIEU VOUS BENISSE".
 */
public final class PancarteDesign extends DecorDesign {
    private final int variant;
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY, chips = Mesh.EMPTY;

    public PancarteDesign(int variant) {
        super("pancarte_carton_" + variant, 0xD8B080);
        this.variant = variant;
        itemScale = 0.95f;
        itemCenter = new float[]{8, 6, 6};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // pancarte appuyee contre le mur (bas en avant), recto ecrit, verso carton
        s.push().translate(8, 0, 2.6f).rotateX(-17).rotateZ(1.5f);
        Mat front = sign(variant);
        float w = 12.4f, h = 6.2f;
        s.face(front, v(0, 0, 1), p(-w / 2, 0.2f, 0.15f), p(w / 2, 0.2f, 0.15f), p(w / 2, 0.2f + h * 1.6f, 0.15f), p(-w / 2, 0.2f + h * 1.6f, 0.15f));
        s.face(CARDBOARD, v(0, 0, -1), p(w / 2, 0.2f, -0.15f), p(-w / 2, 0.2f, -0.15f), p(-w / 2, 0.2f + h * 1.6f, -0.15f), p(w / 2, 0.2f + h * 1.6f, -0.15f));
        s.pop();
        // boite de conserve (etiquette de haricots dechiree) et jetons
        s.push().translate(11.4f, 0, 11.4f);
        s.cylinder(1.4f, 0.4f, 2.6f, 16, CAN, null, null);
        s.pop();
        b.push().translate(11.4f, 0, 11.4f);
        b.cylinder(1.45f, 0, 0.4f, 16, STEEL, STEEL, null);
        b.push().translate(0, 2.6f, 0);
        b.cylinder(1.45f, 0, 0.6f, 16, STEEL, null, null);
        b.pop();
        b.lathe(new float[][]{{1.35f, 3.2f}, {1.35f, 0.5f}}, 16, 0, 360, SOOT, 1, 1, 0, 0, 0, true);
        b.disc(1.35f, 0.5f, 16, SOOT, true);
        b.pop();
        MeshBuilder c = new MeshBuilder();
        for (int k = 0; k < 3; k++) {
            c.push().translate(11.4f + (k - 1) * 0.5f, 0.6f + k * 0.24f, 11.4f + (k % 2) * 0.3f).rotateY(k * 50).rotateX(k == 2 ? 20 : 0);
            c.cylinder(0.78f, 0, 0.23f, 12, Recup.chipEdge(k), null, Recup.chipFace(k));
            c.pop();
        }
        c.push().translate(8.4f, 0, 12.6f).rotateY(30);
        c.cylinder(0.78f, 0, 0.23f, 12, Recup.chipEdge(0), null, Recup.chipFace(0));
        c.pop();
        chips = c.build();
        Mesh[] m = bakePair(b, s, 0.25f, 1.8f, 0.4f, 0f);
        body = m[0];
        bodyS = m[1];
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
        out.sheet(Recup.SHEET);
        out.solid(p, chips, 0);
        out.sheet(null);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
