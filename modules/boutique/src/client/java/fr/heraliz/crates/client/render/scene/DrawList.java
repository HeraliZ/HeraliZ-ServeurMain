package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.client.render.mesh.Mesh;
import org.joml.Matrix4f;

/**
 * Liste de commandes de dessin d'une image (repere du modele, en pixels).
 * Produite par les designs, consommee par le rendu Minecraft ou par l'apercu hors-jeu.
 */
public final class DrawList {
    /** Maillage opaque texture (+ calque lumineux de l'atlas si glow &gt; 0). */
    public static final int SOLID = 0;
    /** Maillage additif : calque lumineux de l'atlas, teinte. */
    public static final int ADDITIVE = 1;
    /** Maillage translucide (verre, liquide) : texture de l'atlas, melange alpha. */
    public static final int TRANSLUCENT = 2;
    /** Sprite lumineux toujours face a la camera. */
    public static final int BILLBOARD = 3;
    /** Sprite lumineux dans le plan XY de la pose. */
    public static final int QUAD = 4;
    /** Faisceau vertical tourne vers la camera. */
    public static final int BEAM = 5;
    /** Eventail de rayons face a la camera. */
    public static final int RAYS = 6;
    /** Vrai objet du jeu (id + NBT), dessine par Minecraft a la pose donnee ; ignore hors du jeu (apercu). */
    public static final int ITEM = 7;

    public static final class Op {
        public int type;
        public Mesh mesh;
        public final Matrix4f m = new Matrix4f();
        public float r, g, b, a;
        public float glow;
        public Sprite sprite;
        /** ITEM : identifiant et NBT de l'objet. */
        public String item, nbt;
        /** ITEM : plus grande dimension alignee sur Y (lame verticale...). */
        public boolean upright;
        public float x, y, z, size, rot, len, width, x1, y1;
        public int count;
    }

    private Op[] ops = new Op[128];
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
        o.sprite = null;
        o.item = null;
        o.nbt = null;
        o.upright = false;
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

    /** Ajoute une copie d'une commande (listes figees). */
    public void addCopy(Op src) {
        Op o = next(src.type, new Pose());
        o.m.set(src.m);
        o.mesh = src.mesh;
        o.sprite = src.sprite;
        o.r = src.r;
        o.g = src.g;
        o.b = src.b;
        o.a = src.a;
        o.glow = src.glow;
        o.x = src.x;
        o.y = src.y;
        o.z = src.z;
        o.size = src.size;
        o.rot = src.rot;
        o.len = src.len;
        o.width = src.width;
        o.x1 = src.x1;
        o.y1 = src.y1;
        o.count = src.count;
        o.item = src.item;
        o.nbt = src.nbt;
        o.upright = src.upright;
    }

    /** Maillage opaque ; glow = intensite du calque lumineux de l'atlas (0 = aucun). */
    public void solid(Pose p, Mesh mesh, float glow) {
        if (mesh == null || mesh.isEmpty()) return;
        Op o = next(SOLID, p);
        o.mesh = mesh;
        o.glow = glow;
    }

    /** Maillage opaque assombri/teinte (multiplicateur de couleur). */
    public void solidTinted(Pose p, Mesh mesh, float glow, float r, float g, float b) {
        if (mesh == null || mesh.isEmpty()) return;
        Op o = next(SOLID, p);
        o.mesh = mesh;
        o.glow = glow;
        o.r = r;
        o.g = g;
        o.b = b;
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

    /**
     * Vrai objet du jeu dessine par Minecraft (vue FIXED), centre sur l'origine de la pose ; size = sa plus grande
     * dimension en px (l'objet est mesure une fois). upright : cette dimension est alignee sur Y, la deuxieme sur X.
     * Seulement en jeu (CrateState.realItems) : prevoir un modele de remplacement pour l'apercu et le rendu lointain.
     */
    public void item(Pose p, String item, String nbt, float size, boolean upright) {
        Op o = next(ITEM, p);
        o.item = item;
        o.nbt = nbt;
        o.size = size;
        o.upright = upright;
    }

    public void item(Pose p, String item, String nbt, float size) {
        item(p, item, nbt, size, false);
    }

    /** Sprite face camera, centre (x,y,z) dans le repere de la pose, demi-taille en px. */
    public void billboard(Pose p, Sprite s, float x, float y, float z, float half, float rotDeg, int rgb, float a) {
        if (a <= 0.003f || half <= 0) return;
        Op o = next(BILLBOARD, p);
        o.sprite = s;
        o.x = x;
        o.y = y;
        o.z = z;
        o.size = half;
        o.rot = rotDeg;
        rgb(o, rgb, a);
    }

    /** Sprite dans le plan XY de la pose (x0,y0)-(x1,y1) en px. */
    public void quad(Pose p, Sprite s, float x0, float y0, float x1, float y1, int rgb, float a) {
        if (a <= 0.003f) return;
        Op o = next(QUAD, p);
        o.sprite = s;
        o.x = x0;
        o.y = y0;
        o.x1 = x1;
        o.y1 = y1;
        rgb(o, rgb, a);
    }

    /** Disque a plat (plan XZ) centre en (cx, y, cz), rayon en px. */
    public void ground(Pose p, Sprite s, float cx, float y, float cz, float radius, float rotDeg, int rgb, float a) {
        if (a <= 0.003f || radius <= 0) return;
        p.push().translate(cx, y, cz).rotY(rotDeg).rotX(-90);
        quad(p, s, -radius, -radius, radius, radius, rgb, a);
        p.pop();
    }

    /** Faisceau vertical (base en x,y,z), largeur et hauteur en px. */
    public void beam(Pose p, float x, float y, float z, float halfWidth, float height, int rgb, float a) {
        if (a <= 0.003f) return;
        Op o = next(BEAM, p);
        o.sprite = Sprite.BEAM;
        o.x = x;
        o.y = y;
        o.z = z;
        o.width = halfWidth;
        o.len = height;
        rgb(o, rgb, a);
    }

    /** Eventail de rayons face camera. */
    public void rays(Pose p, float x, float y, float z, int count, float len, float halfWidth, float rotDeg, int rgb, float a) {
        if (a <= 0.003f || count <= 0) return;
        Op o = next(RAYS, p);
        o.sprite = Sprite.RAY;
        o.x = x;
        o.y = y;
        o.z = z;
        o.count = count;
        o.len = len;
        o.width = halfWidth;
        o.rot = rotDeg;
        rgb(o, rgb, a);
    }

    /** Colonne de lumiere : plans verticaux croises (ne suit pas la camera). */
    public void shaft(Pose p, float x, float y, float z, float halfWidth, float height, float rotDeg, int planes, int rgb, float a) {
        if (a <= 0.003f) return;
        for (int i = 0; i < planes; i++) {
            p.push().translate(x, y, z).rotY(rotDeg + i * 180f / planes);
            Op o = next(QUAD, p);
            o.sprite = Sprite.BEAM;
            o.x = -halfWidth;
            o.y = 0;
            o.x1 = halfWidth;
            o.y1 = height;
            rgb(o, rgb, a);
            p.pop();
        }
    }
}
