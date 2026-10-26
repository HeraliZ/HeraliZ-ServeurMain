package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : fauteuil club recupere dans un mess d'officiers. Cuir craquele, accoudoirs roules, rangs de clous de
 * laiton, petits pieds tournes ; une dechirure de l'accoudoir rafistolee au chatterton.
 */
public final class FauteuilDesign extends DecorDesign {
    private Mesh body = Mesh.EMPTY;

    public FauteuilDesign() {
        super("fauteuil_club", 0xA86A3A);
        itemScale = 0.85f;
        itemCenter = new float[]{8, 7, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // pieds tournes
        for (float[] f : new float[][]{{2.4f, 2.4f}, {13.6f, 2.4f}, {2.4f, 13.6f}, {13.6f, 13.6f}}) {
            b.push().translate(f[0], 0, f[1]);
            b.lathe(new float[][]{{0.45f, 0}, {0.6f, 0.5f}, {0.5f, 1.0f}, {0.62f, 1.3f}}, 10, 0, 360, TVWOOD, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        // caisse du siege
        b.bevelBox(2.2f, 1.3f, 2.4f, 13.8f, 5.6f, 14.4f, 0.7f, LEATHER);
        // coussin d'assise bombe
        b.push().translate(8, 6.1f, 9.0f);
        b.blob(4.7f, 1.0f, 4.9f, 0.32f, 0.04f, 2f, 5, 18, LEATHER);
        b.pop();
        // dossier legerement incline et son coussin
        b.push().translate(8, 4.8f, 3.0f).rotateX(-9);
        b.bevelBox(-6.6f, 0, -1.7f, 6.6f, 8.8f, 1.5f, 0.9f, LEATHER);
        b.push().translate(0, 5.2f, 2.0f);
        b.blob(4.6f, 3.1f, 1.0f, 0.4f, 0.05f, 2f, 9, 16, LEATHER);
        b.pop();
        b.pop();
        // accoudoirs roules
        for (int sd = -1; sd <= 1; sd += 2) {
            float x = 8 + sd * 5.9f;
            b.bevelBox(x - 1.4f, 1.3f, 2.2f, x + 1.4f, 8.2f, 14.8f, 0.6f, LEATHER);
            b.push().translate(x + sd * 0.3f, 8.3f, 2.0f).rotateX(90);
            b.cylinder(1.65f, 0, 13.0f, 14, LEATHER, LEATHER, LEATHER);
            b.pop();
            // clous de laiton sur la face avant de l'accoudoir
            for (int k = 0; k < 7; k++) {
                double a = Math.toRadians(-60 + k * 40);
                b.push().translate(x + sd * 0.3f + (float) Math.cos(a) * 1.45f, 8.3f + (float) Math.sin(a) * 1.45f, 15.02f).rotateX(90);
                b.rivet(0.16f, 0.1f, POLISHED);
                b.pop();
            }
        }
        // clous le long du bas de la caisse, devant
        for (int k = 0; k < 11; k++) {
            b.push().translate(3.2f + k * 0.96f, 2.0f, 14.42f).rotateX(90);
            b.rivet(0.15f, 0.09f, POLISHED);
            b.pop();
        }
        // dechirure rafistolee au chatterton sur l'accoudoir droit
        b.push().translate(13.9f + 0.3f, 9.96f, 9.0f).rotateX(-90);
        tape(b, TAPE, 0, 0, 0, 3.2f, 0.9f, 30);
        tape(b, TAPE, 0, 0, 0.01f, 3.0f, 0.9f, -35);
        b.pop();
        Occlusion.bake(b, 0.35f, 2.4f, 0.5f, 0f);
        body = b.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
