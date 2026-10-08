package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Occlusion;

import java.util.function.BiConsumer;

/**
 * Cles 3D thematiques : pointe a l'origine, tige vers +Y, panneton vers +X, tete (anneau) en haut.
 * Longueur totale ~17 px.
 */
public final class KeySmith {
    private KeySmith() {
    }

    /** Hauteur (px) a laquelle commence la tete de la cle. */
    public static final float BOW_Y = 10.2f;

    /**
     * @param bow construit la tete thematique : (solide, lumineux) avec l'origine a la base de la tete
     * @return {cle, calque lumineux teinte}
     */
    public static Mesh[] build(Mat metal, Mat accent, float shaftR, BiConsumer<MeshBuilder, MeshBuilder> bow) {
        MeshBuilder b = new MeshBuilder();
        MeshBuilder g = new MeshBuilder();
        // pointe arrondie + tige
        b.push().translate(0, 0.5f, 0);
        b.sphereSection(shaftR, 12, 3, 90, 180, metal);
        b.pop();
        b.cylinder(shaftR, 0.5f, 9.4f, 14, metal, null, null);
        // panneton : dents decoupees
        float z = 0.34f;
        b.bevelBox(0.3f, 0.9f, -z, 2.7f, 2.0f, z, 0.14f, metal);
        b.bevelBox(0.3f, 3.1f, -z, 2.3f, 4.1f, z, 0.14f, metal);
        b.bevelBox(0.3f, 1.9f, -z, 1.5f, 3.2f, z, 0.14f, metal);
        b.bevelBox(1.8f, 4.0f, -z * 0.8f, 2.3f, 4.9f, z * 0.8f, 0.1f, metal);
        // bagues
        b.cylinder(shaftR * 1.75f, 8.6f, 9.1f, 16, accent, accent, accent);
        b.push().translate(0, 8.85f, 0);
        b.torus(shaftR * 1.75f, 0.2f, 16, 6, 0, 360, accent);
        b.pop();
        b.cylinder(shaftR * 1.3f, 9.1f, BOW_Y + 0.2f, 14, accent, null, null);
        b.push().translate(0, BOW_Y, 0);
        g.push().translate(0, BOW_Y, 0);
        bow.accept(b, g);
        b.pop();
        g.pop();
        Occlusion.bake(b, 0.22f, 1.6f, 0.4f, Float.NaN);
        return new Mesh[]{b.build(), g.build()};
    }
}
