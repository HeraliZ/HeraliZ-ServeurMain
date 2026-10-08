package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : canape Chesterfield deux places en cuir sang-de-boeuf. Dossier et accoudoirs de meme hauteur,
 * capitonnes (losanges et boutons), accoudoirs roules, rangs de clous de laiton, pieds tournes a roulettes de laiton,
 * deux coussins d'assise lisses.
 */
public final class ChesterfieldDesign extends DecorDesign {
    /** Cuir lisse : le coeur d'un losange du cuir capitonne (sans plis ni boutons). */
    static final Mat SMOOTH = OXBLOOD.sub(0.2f, 0.08f, 0.3f, 0.2f);

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public ChesterfieldDesign() {
        super("canape_chesterfield", 0xB0402A);
        itemScale = 0.5f;
        itemCenter = new float[]{16, 7, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // pieds tournes et roulettes de laiton
        for (float[] f : new float[][]{{2.2f, 2.6f}, {29.8f, 2.6f}, {2.2f, 13.6f}, {29.8f, 13.6f}, {16, 2.6f}}) {
            b.push().translate(f[0], 0, f[1]);
            b.sphere(0.5f, 8, 6, POLISHED);
            b.lathe(new float[][]{{0.45f, 0.4f}, {0.7f, 0.8f}, {0.55f, 1.2f}, {0.75f, 1.5f}}, 10, 0, 360, TVWOOD, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        // caisse : capitonnee sur l'avant
        Mat[] boxFaces = {OXBLOOD, OXBLOOD, SMOOTH, SMOOTH, OXBLOOD, OXBLOOD};
        s.bevelBox(1.0f, 1.5f, 1.4f, 31.0f, 6.0f, 14.6f, 0.7f, SMOOTH, boxFaces, 0);
        // dossier droit, capitonne, boudin sommital
        s.bevelBox(1.0f, 5.8f, 1.2f, 31.0f, 12.6f, 4.4f, 0.8f, SMOOTH, new Mat[]{OXBLOOD, OXBLOOD, SMOOTH, SMOOTH, OXBLOOD, OXBLOOD}, 0);
        s.push().translate(1.0f, 12.7f, 2.9f).rotateZ(-90);
        s.cylinder(1.65f, 0, 30.0f, 16, OXBLOOD, SMOOTH, SMOOTH);
        s.pop();
        // accoudoirs a la hauteur du dossier, roules vers l'exterieur, face avant en volute
        for (int sd = 0; sd < 2; sd++) {
            float x0 = sd == 0 ? 0.2f : 28.4f, x1 = x0 + 3.4f, cx = (x0 + x1) / 2 + (sd == 0 ? -0.3f : 0.3f);
            s.bevelBox(x0, 1.5f, 1.2f, x1, 11.2f, 14.9f, 0.7f, SMOOTH, new Mat[]{OXBLOOD, OXBLOOD, SMOOTH, SMOOTH, SMOOTH, SMOOTH}, 0);
            s.push().translate(cx, 11.4f, 1.1f).rotateX(90);
            s.cylinder(2.05f, 0, 14.0f, 16, OXBLOOD, SMOOTH, SMOOTH);
            s.pop();
            // volute : disque et plis rayonnants
            s.push().translate(cx, 11.4f, 15.12f).rotateX(90);
            s.cylinder(1.7f, 0, 0.12f, 16, SMOOTH, null, SMOOTH);
            s.pop();
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                b.push().translate(cx + (float) Math.cos(a) * 1.0f, 11.4f + (float) Math.sin(a) * 1.0f, 15.2f).rotateX(90);
                b.rivet(0.14f, 0.08f, POLISHED);
                b.pop();
            }
            // clous de laiton le long du bas de l'accoudoir
            for (int k = 0; k < 9; k++) {
                b.push().translate(x0 + 0.4f + k * (x1 - x0 - 0.8f) / 8, 2.1f, 14.95f).rotateX(90);
                b.rivet(0.14f, 0.08f, POLISHED);
                b.pop();
            }
        }
        // clous le long de la caisse, devant
        for (int k = 0; k < 30; k++) {
            b.push().translate(4.0f + k * 0.83f, 2.0f, 14.65f).rotateX(90);
            b.rivet(0.14f, 0.08f, POLISHED);
            b.pop();
        }
        // deux coussins d'assise lisses
        for (int k = 0; k < 2; k++) {
            s.push().translate(9.1f + k * 13.8f, 6.85f, 9.3f);
            s.blob(6.6f, 0.95f, 5.2f, 0.28f, 0.03f, 2f, 141 + k, 20, SMOOTH);
            s.pop();
        }
        Mesh[] m = bakePair(b, s, 0.35f, 2.6f, 0.5f, 0f);
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
