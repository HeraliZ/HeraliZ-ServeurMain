package fr.heraliz.loot.client.rendu.scene;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import org.joml.Matrix4f;

/**
 * Liste de commandes de dessin d'une image (repère du modèle, en pixels), produite par le modèle d'un coffre et
 * consommée par le rendu Minecraft ou par l'aperçu hors-jeu (moteur repris du casino, sans sprites ni textes).
 */
public final class DrawList {
    /** Maillage opaque texturé (+ calque lumineux de l'atlas si glow &gt; 0). */
    public static final int SOLID = 0;
    /** Maillage additif : calque lumineux de l'atlas, teinté. */
    public static final int ADDITIVE = 1;
    /** Maillage translucide (verre) : texture de l'atlas, mélange alpha. */
    public static final int TRANSLUCENT = 2;

    public static final class Op {
        public int type;
        public Mesh mesh;
        public final Matrix4f m = new Matrix4f();
        public float r, g, b, a;
        public float glow;
    }

    private Op[] ops = new Op[64];
    private int n;

    public DrawList() {
        for (int i = 0; i < ops.length; i++) ops[i] = new Op();
    }

    public int size() {
        return n;
    }

    public Op get(int i) {
        return ops[i];
    }

    public void clear() {
        n = 0;
    }

    private Op next(int type, Pose p) {
        if (n == ops.length) {
            Op[] grown = new Op[ops.length * 2];
            System.arraycopy(ops, 0, grown, 0, ops.length);
            for (int i = ops.length; i < grown.length; i++) grown[i] = new Op();
            ops = grown;
        }
        Op o = ops[n++];
        o.type = type;
        o.mesh = null;
        o.m.set(p.m());
        o.r = o.g = o.b = o.a = 1;
        o.glow = 0;
        return o;
    }

    private static void rgb(Op o, int rgb, float a) {
        o.r = ((rgb >> 16) & 255) / 255f;
        o.g = ((rgb >> 8) & 255) / 255f;
        o.b = (rgb & 255) / 255f;
        o.a = a;
    }

    /** Maillage opaque ; glow = intensité du calque lumineux de l'atlas (0 = aucun). */
    public void solid(Pose p, Mesh mesh, float glow) {
        if (mesh == null || mesh.isEmpty()) return;
        Op o = next(SOLID, p);
        o.mesh = mesh;
        o.glow = glow;
    }

    /** Maillage additif (calque lumineux de l'atlas x couleur). */
    public void additive(Pose p, Mesh mesh, int rgb, float a) {
        if (mesh == null || mesh.isEmpty() || a <= 0.003f) return;
        Op o = next(ADDITIVE, p);
        o.mesh = mesh;
        rgb(o, rgb, a);
    }

    /** Maillage translucide (texture de l'atlas, alpha de la texture x a). */
    public void translucent(Pose p, Mesh mesh, int rgb, float a) {
        if (mesh == null || mesh.isEmpty() || a <= 0.003f) return;
        Op o = next(TRANSLUCENT, p);
        o.mesh = mesh;
        rgb(o, rgb, a);
    }
}
