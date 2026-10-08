package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.mesh.Occlusion;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Ease;
import fr.heraliz.loot.client.rendu.scene.Pose;

/**
 * Modèle 3D d'un coffre T7 (Java pur, aucun appel à Minecraft : partagé par le rendu en jeu et l'aperçu hors-jeu).
 * Repère : pixels (1/16 de bloc), le bloc va de 0 à 16 en x et z, sol en y = 0, face avant vers +Z.
 * build() construit une fois les maillages (pièces fixes et mobiles, occlusion ambiante cuite) ; dessiner() pose
 * chaque pièce selon le moment de l'animation d'ouverture t (0 = fermé, ouverture = grand ouvert) et l'heure h
 * (animations continues : voyants, noyau qui tourne...).
 */
public abstract class DesignCoffre {
    public final String nom;
    /** Durée de l'animation d'ouverture (ticks) : l'écran du coffre s'ouvre à la fin. */
    public final int ouverture;
    private boolean pret;

    protected DesignCoffre(String nom, int ouverture) {
        this.nom = nom;
        this.ouverture = ouverture;
    }

    public final void preparer() {
        if (!pret) {
            build();
            pret = true;
        }
    }

    protected abstract void build();

    /**
     * @param t     moment de l'animation (0 fermé .. ouverture ouvert ; la fermeture le fait redescendre)
     * @param h     heure continue (ticks + fraction)
     * @param ferme coffre fermé au repos (animations d'attente)
     */
    public abstract void dessiner(float t, float h, boolean ferme, Pose p, DrawList out);

    /**
     * Apparition (disparait = false) ou disparition, f de 0 à 1 : le coffre fermé se matérialise (grandit avec un léger
     * dépassement) ou se résorbe, sur un halo rouge au sol.
     */
    public void dessinerApparition(float f, float h, boolean disparait, Pose p, DrawList out) {
        float[] bo = bornes();
        float cx = (bo[0] + bo[3]) / 2, cz = (bo[2] + bo[5]) / 2, taille = Math.max(bo[3] - bo[0], bo[5] - bo[2]) / 16f;
        float s = disparait ? 1 - Ease.inBack(f) : Ease.outBack(Math.min(1, f * 1.15f));
        if (s > 0.002f) {
            p.push().translate(cx, 0, cz).scale(s).translate(-cx, 0, -cz);
            dessiner(0, h, true, p, out);
            p.pop();
        }
        float a = disparait ? Ease.bell(f, 0, 1) : 1 - f;
        float k = (disparait ? 1.2f - 0.6f * f : 0.6f + 0.7f * f) * taille;
        p.push().translate(cx, 0.06f, cz).scale(k, 1, k);
        out.additive(p, halo(), 0xFF5A3A, 0.9f * a);
        p.pop();
    }

    private static Mesh HALO;

    /** Anneau lumineux à plat (centre 0,0), vif au milieu de l'anneau, nul aux bords. */
    protected static Mesh halo() {
        if (HALO == null) {
            MeshBuilder b = new MeshBuilder();
            int n = 40;
            float r0 = 1.5f, r1 = 6f, r2 = 11f;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                lavis(b, AtlasT7.LED_RED, MeshBuilder.v(0, 1, 0), new float[][]{MeshBuilder.p(c0 * r0, 0, s0 * r0), MeshBuilder.p(c1 * r0, 0, s1 * r0),
                        MeshBuilder.p(c1 * r1, 0, s1 * r1), MeshBuilder.p(c0 * r1, 0, s0 * r1)}, 0.15f, 0.15f, 1f, 1f);
                lavis(b, AtlasT7.LED_RED, MeshBuilder.v(0, 1, 0), new float[][]{MeshBuilder.p(c0 * r1, 0, s0 * r1), MeshBuilder.p(c1 * r1, 0, s1 * r1),
                        MeshBuilder.p(c1 * r2, 0, s1 * r2), MeshBuilder.p(c0 * r2, 0, s0 * r2)}, 1f, 1f, 0f, 0f);
            }
            HALO = b.build();
        }
        return HALO;
    }

    /** Boîte englobante du modèle fermé (px, avant vers +Z) : cadrage de l'objet dans l'inventaire, apparition. */
    public float[] bornes() {
        return new float[]{0, 0, 0, 16, 16, 16};
    }

    /** Centre du modèle pour l'objet (inventaire) et son échelle. */
    public float[] centreObjet() {
        return new float[]{8, 7, 8};
    }

    public float echelleObjet() {
        return 0.62f;
    }

    // =================================================================== outils communs
    protected static float seg(float t, float a, float b) {
        return Ease.range(t, a, b);
    }

    /** Occlusion ambiante cuite (creux, contact avec le sol en y = sol, ou NaN). */
    protected static Mesh cuire(MeshBuilder b, float sol, Mesh... autres) {
        Occlusion.bake(b, 0.35f, 2.6f, 0.55f, sol, autres);
        return b.build();
    }

    /** Vis à tête bombée tournée vers +Z (repère courant). */
    protected static void vis(MeshBuilder b, float x, float y, float z, float r) {
        b.push().translate(x, y, z).rotateX(90);
        b.cylinder(r, 0, r * 0.35f, 10, AtlasT7.CHROME, null, null);
        b.push().translate(0, r * 0.35f, 0);
        b.sphereSection(r, 10, 4, 0, 50, AtlasT7.CHROME);
        b.pop();
        b.pop();
    }

    /** Pied caoutchouc (cylindre) posé au sol. */
    protected static void pied(MeshBuilder b, float x, float z, float r, float h) {
        b.push().translate(x, 0, z);
        b.cylinder(r, 0, h, 14, AtlasT7.RUBBER, AtlasT7.RUBBER, AtlasT7.RUBBER);
        b.pop();
    }

    /** Décalcomanie face +Z (centre, taille), à la profondeur z. */
    protected static void decal(MeshBuilder b, Mat m, float cx, float cy, float z, float w, float h) {
        b.decalZ(m, cx, cy, z, w, h);
    }

    /** Décalcomanie à plat, face +Y, à la hauteur y. */
    protected static void decalHaut(MeshBuilder b, Mat m, float cx, float y, float cz, float w, float d) {
        b.push().translate(cx, y, cz).rotateX(-90);
        b.decalZ(m, 0, 0, 0, w, d);
        b.pop();
    }

    /**
     * Lavis de lumière (pour le calque additif) : la face pts reçoit un dégradé d'intensité par sommet (k), dans la
     * tuile lumineuse m. Simule une lampe qui éclaire l'intérieur d'un coffre.
     */
    protected static void lavis(MeshBuilder b, Mat m, float[] n, float[][] pts, float... k) {
        float[][] col = new float[pts.length][];
        for (int i = 0; i < pts.length; i++) col[i] = new float[]{k[i], k[i], k[i]};
        b.polyColored(pts, m.project(pts, n, null), null, n, col);
    }

    /** Rotation autour d'un pivot (px) selon un axe ('x', 'y', 'z'). */
    protected static Pose autour(Pose p, float px, float py, float pz, char axe, float deg) {
        p.translate(px, py, pz);
        switch (axe) {
            case 'x' -> p.rotX(deg);
            case 'y' -> p.rotY(deg);
            default -> p.rotZ(deg);
        }
        return p.translate(-px, -py, -pz);
    }
}
