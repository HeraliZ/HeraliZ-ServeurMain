package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : matelas crasseux pose sur deux palettes (deux blocs de long), oreiller tache, couverture militaire
 * froissee qui pend sur le bord, bouteilles vides par terre.
 */
public final class MatelasDesign extends DecorDesign {
    static final float DECK = 2.0f;

    private Mesh body = Mesh.EMPTY;

    public MatelasDesign() {
        super("matelas", 0xB8A888);
        itemScale = 0.6f;
        itemCenter = new float[]{16, 4, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        pallet(b, 0.3f);
        pallet(b, 16.3f);
        // matelas : bloc mou un peu affaisse au milieu
        b.push().translate(16, DECK + 2.3f, 8).rotateZ(0.8f);
        b.blob(14.9f, 2.3f, 6.7f, 0.2f, 0.03f, 2f, 3, 24, TICKING);
        b.pop();
        // oreiller
        b.push().translate(4.6f, DECK + 5.1f, 8).rotateY(6);
        b.blob(3.1f, 1.1f, 4.6f, 0.45f, 0.08f, 2.5f, 7, 16, TICKING);
        b.pop();
        // couverture froissee, un pan qui pend devant
        b.push().translate(21.5f, DECK + 4.9f, 8.6f).rotateY(-8);
        b.blob(8.4f, 1.3f, 6.6f, 0.6f, 0.28f, 2.2f, 11, 20, BLANKET);
        b.pop();
        b.push().translate(20.5f, DECK + 2.8f, 14.6f).rotateZ(4);
        b.blob(6.2f, 2.7f, 0.7f, 0.55f, 0.18f, 2.6f, 13, 16, BLANKET);
        b.pop();
        // bouteilles : une couchee devant, une debout dans le coin du fond
        b.push().translate(27.5f, 0.85f, 15.0f).rotateY(-20).rotateZ(90);
        bottle(b);
        b.pop();
        b.push().translate(31.0f, 0, 1.4f);
        bottle(b);
        b.pop();
        b.push().translate(29.6f, 0, 1.0f).rotateZ(4);
        b.cylinder(0.75f, 0, 2.6f, 10, STEEL, STEEL, null);
        b.pop();
        Occlusion.bake(b, 0.35f, 2.6f, 0.5f, 0f);
        body = b.build();
    }

    /** Palette (15,4 x 14) : trois traverses, cinq lattes dessus. */
    private static void pallet(MeshBuilder b, float x0) {
        for (float z : new float[]{1.3f, 7.3f, 13.3f}) b.bevelBox(x0, 0, z, x0 + 15.4f, 1.3f, z + 1.4f, 0.12f, PALLET);
        for (int k = 0; k < 5; k++) {
            float x = x0 + 0.2f + k * 3.1f;
            b.bevelBox(x, 1.3f, 1.0f, x + 2.3f, DECK, 15.0f, 0.1f, PALLET);
        }
    }

    /** Bouteille debout (axe Y, base en 0) : corps, epaule, goulot. */
    static void bottle(MeshBuilder b) {
        b.cylinder(0.85f, 0, 4.2f, 10, BOTTLE, BOTTLE, null);
        b.lathe(new float[][]{{0.85f, 4.2f}, {0.6f, 4.9f}, {0.34f, 5.4f}}, 10, 0, 360, BOTTLE, 1, 1, 0, 0, 0, false);
        b.push().translate(0, 5.4f, 0);
        b.cylinder(0.34f, 0, 1.7f, 8, BOTTLE, null, DARK);
        b.pop();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
