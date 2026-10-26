import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Occlusion;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Modele 3D de la barricade en bois (mur de rondins couches), construit avec le moteur de maillage de HeraliZ Crates
 * (Java pur), puis ecrit en donnees brutes lues par le rendu du jeu (BarricadeMesh). Lance par outils/barricade_modele.py.
 * <p>Repere : pixels (16 = 1 bloc), sol en y = 0, barricade le long de X, avant vers -Z, jambes de force vers +Z (x = +-16,
 * jusqu'a z ~ 7,4) : le meme encombrement que la collision (BarricadeEntity : 9 segments de 4,6 px tous les 4,8 px).
 * Textures : atlas des tuiles de Mat (ecorce = MAIN, bois de bout = DECAL_A, fer = DARK), 4 etats d'usure.
 */
public final class BarricadeModele {
    static final Mat BARK = Mat.MAIN.withWorld(40), ENDGRAIN = Mat.DECAL_A, IRON = Mat.DARK;
    static final float LEG_X = 16F;

    /** detail : 1 = proche, ~0,5 = de loin (moins de pans, sans agrafes ni bouts de bois). */
    static Mesh build(float detail) {
        MeshBuilder b = new MeshBuilder();
        Random r = new Random(5);
        int seg = Math.max(6, Math.round(14 * detail));
        boolean near = detail >= 1f;
        // poteaux verticaux (bouts et milieu), coupes nettes en haut
        for (float x : new float[]{-21.3f, 0f, 21.3f}) {
            float h = 43.5f + r.nextFloat() * 1.5f;
            b.push().translate(x, 0, 1.4f);
            b.lathe(new float[][]{{2.0f, 0}, {1.9f, h * 0.5f}, {1.8f, h}}, seg, 0, 360, BARK, 1, 1, 0.05f, 0.3f, (int) x, false);
            b.disc(1.8f, h, seg, ENDGRAIN, true);
            b.pop();
        }
        // rondins couches empiles devant, legerement irreguliers, bouts de bois coupes
        float y = 2.15f;
        for (int i = 0; i < 9; i++) {
            float rad = 2.0f + r.nextFloat() * 0.35f, off = (r.nextFloat() - 0.5f) * 2.4f;
            b.push().translate(off, y, -1.0f).rotateZ(90).rotateY(r.nextFloat() * 360);
            b.lathe(new float[][]{{rad * 0.95f, -23.4f}, {rad, -10}, {rad * 1.02f, 8}, {rad * 0.96f, 23.4f}}, seg, 0, 360, BARK, 1, 1, 0.07f, 0.25f, i * 7, false);
            if (near) {
                b.disc(rad * 0.95f, -23.4f, seg, ENDGRAIN, false);
                b.disc(rad * 0.96f, 23.4f, seg, ENDGRAIN, true);
            }
            b.pop();
            y += rad * 2 * 0.93f;
        }
        // agrafes de fer forge sur les poteaux
        if (near) {
            for (float x : new float[]{-21.3f, 0f, 21.3f}) {
                for (float yy : new float[]{8f, 24f, 38f}) {
                    b.push().translate(x, yy, -3.3f);
                    b.box(-0.45f, -2.4f, -0.3f, 0.45f, 2.4f, 0.3f, IRON);
                    b.box(-0.45f, -2.4f, -0.3f, 0.45f, -1.8f, 4.0f, IRON);
                    b.box(-0.45f, 1.8f, -0.3f, 0.45f, 2.4f, 4.0f, IRON);
                    b.pop();
                }
            }
        }
        // jambes de force derriere
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{v(s * LEG_X, 0.3f, 7.4f), v(s * LEG_X, 30, 2.2f)}, new float[]{1.4f, 1.26f}, Math.max(6, Math.round(10 * detail)), BARK,
                    true, true, 0, false);
        }
        if (near) Occlusion.bake(b, 0.5f, 5f, 0.55f, 0f);
        return b.build();
    }

    /** Format : entier magique, nombre de sommets, puis par sommet x y z u v nx ny nz r g b (flottants). Quads. */
    static void write(Mesh m, String path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
            out.writeInt(0x48424152);   // "HBAR"
            out.writeInt(m.vertexCount());
            float[] d = m.data();
            for (int i = 0; i < m.vertexCount() * Mesh.STRIDE; i++) out.writeFloat(d[i]);
        }
        System.out.println(path + " : " + m.vertexCount() + " sommets");
    }

    public static void main(String[] a) throws IOException {
        write(build(1f), a[0]);
        write(build(0.5f), a[1]);
    }
}
