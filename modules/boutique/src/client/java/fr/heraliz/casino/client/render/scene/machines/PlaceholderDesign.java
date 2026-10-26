package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

/**
 * Modele provisoire (boite) pour une machine sans modele dedie.
 */
public final class PlaceholderDesign extends MachineDesign<Object> {
    private Mesh box = Mesh.EMPTY;

    public PlaceholderDesign(String id) {
        super(id, 0xFFD36A, 0xFFFFFF);
        labelY = 24;
        itemScale = 0.8f;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        b.bevelBox(1, 0, 1, 15, 16, 15, 1f, Mat.fit(0, 0, 1024, 1024));
        box = b.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, box, 0);
    }

    @Override
    protected void animate(Scene s, Object v, Pose p, DrawList out) {
    }

    @Override
    public Object newView() {
        return new Object();
    }
}
