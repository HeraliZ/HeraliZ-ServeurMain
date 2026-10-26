package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : cabane de carton, le grand carton d'un frigo couche sur le flanc (deux blocs de profondeur), ouvert devant.
 * Rabats : celui du bas a plat par terre, celui du haut releve en auvent, un sur le cote entrouvert. Dedans : une
 * couverture, un oreiller, une bougie dans un bocal ; le toit rapiece au chatterton. On s'assoit a l'entree.
 */
public final class CabaneDesign extends DecorDesign {
    static final float X0 = 1, X1 = 15, H = 22, Z0 = 1, Z1 = 30.4f, T = 0.4f;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public CabaneDesign() {
        super("cabane_carton", 0xC0956A);
        itemScale = 0.42f;
        itemCenter = new float[]{8, 11, 16};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // parois du carton : flancs, fond, sol, toit un peu affaisse au milieu
        s.bevelBox(X0, 0, Z0, X0 + T, H, Z1, 0.08f, CARDBOARD);
        s.bevelBox(X1 - T, 0, Z0, X1, H, Z1, 0.08f, CARDBOARD);
        s.bevelBox(X0, 0, Z0, X1, H, Z0 + T, 0.08f, CARDBOARD);
        s.bevelBox(X0, 0, Z0, X1, 0.3f, Z1, 0.05f, CARDBOARD);
        s.push().translate(0, H, Z0).rotateX(1.2f);
        s.bevelBox(X0, -T, 0, X1, 0, (Z1 - Z0) / 2, 0.08f, CARDBOARD);
        s.pop();
        s.push().translate(0, H - 0.3f, (Z0 + Z1) / 2).rotateX(-1.2f);
        s.bevelBox(X0, -T, 0, X1, 0, (Z1 - Z0) / 2, 0.08f, CARDBOARD);
        s.pop();
        // rabats : bas a plat par terre, haut releve en auvent, gauche entrouvert
        s.push().translate(0, 0.3f, Z1).rotateX(4);
        s.bevelBox(X0, 0, 0, X1, 0.25f, 6.4f, 0.05f, CARDBOARD);
        s.pop();
        s.push().translate(0, H, Z1).rotateX(-32);
        s.bevelBox(X0, -T, 0, X1, 0, 6.6f, 0.05f, CARDBOARD);
        s.pop();
        s.push().translate(X0, 0, Z1).rotateY(-130);
        s.bevelBox(-T, 0.4f, 0, 0, H - 0.6f, 6.6f, 0.05f, CARDBOARD);
        s.pop();
        // couverture, oreiller, bocal et bougie
        b.push().translate(8, 0.9f, 16.5f);
        b.blob(5.6f, 0.65f, 10.4f, 0.4f, 0.22f, 2.1f, 131, 22, BLANKET);
        b.pop();
        b.push().translate(8, 1.6f, 4.4f).rotateY(8);
        b.blob(4.0f, 1.4f, 2.2f, 0.45f, 0.08f, 2.4f, 133, 16, TICKING);
        b.pop();
        b.push().translate(13.4f, 0.3f, 28.8f);
        b.cylinder(0.75f, 0, 1.6f, 10, WAX, null, WAX);
        b.pop();
        // chatterton sur le toit et les flancs, etiquette au feutre, autocollant "propriete du casino" chipe
        b.push().translate(8, H + 0.02f, 12).rotateX(-90);
        tape(b, TAPE, 0, 0, 0, 5.2f, 1.1f, 8);
        tape(b, TAPE, 1.0f, -6.0f, 0.01f, 4.0f, 1.1f, -12);
        b.pop();
        b.push().translate(X1 + 0.02f, 13.5f, 15).rotateY(90);
        b.decalZ(TAG, 0, 0, 0, 11, 2.75f);
        tape(b, TAPE, 6.5f, -5, 0.01f, 3.4f, 1.0f, 70);
        b.pop();
        b.push().translate(X0 - 0.02f, 9, 12).rotateY(-90).rotateZ(-4);
        b.decalZ(STICKER, 0, 0, 0, 8, 2);
        b.pop();
        Mesh[] m = bakePair(b, s, 0.4f, 3.0f, 0.5f, 0f);
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
