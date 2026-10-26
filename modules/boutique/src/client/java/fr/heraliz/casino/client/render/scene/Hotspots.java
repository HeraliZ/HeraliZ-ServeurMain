package fr.heraliz.casino.client.render.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Zones cliquables d'une machine (repere du modele, px) : parallelogrammes o + s*u + t*v, s et t dans [0, 1].
 * Le joueur clique directement dans la scene 3D (case du tapis, dalle, carte...) : on lance un rayon depuis la souris.
 */
public final class Hotspots {
    /** Une zone : identifiant (case, dalle, carte...) et geometrie. */
    public static final class Spot {
        public int id;
        public float ox, oy, oz, ux, uy, uz, vx, vy, vz;

        public void copyFrom(Spot s) {
            id = s.id;
            ox = s.ox;
            oy = s.oy;
            oz = s.oz;
            ux = s.ux;
            uy = s.uy;
            uz = s.uz;
            vx = s.vx;
            vy = s.vy;
            vz = s.vz;
        }

        /** Centre de la zone. */
        public float[] center() {
            return new float[]{ox + (ux + vx) / 2, oy + (uy + vy) / 2, oz + (uz + vz) / 2};
        }
    }

    private Spot[] spots = new Spot[64];
    private int n;
    private final Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();

    public Hotspots() {
        for (int i = 0; i < spots.length; i++) spots[i] = new Spot();
    }

    public void clear() {
        n = 0;
    }

    public int size() {
        return n;
    }

    public Spot get(int i) {
        return spots[i];
    }

    /** Ajoute une zone : coin o, cotes u et v. */
    public void add(int id, float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz) {
        if (n == spots.length) {
            Spot[] grown = new Spot[spots.length * 2];
            System.arraycopy(spots, 0, grown, 0, spots.length);
            for (int i = spots.length; i < grown.length; i++) grown[i] = new Spot();
            spots = grown;
        }
        Spot s = spots[n++];
        s.id = id;
        s.ox = ox;
        s.oy = oy;
        s.oz = oz;
        s.ux = ux;
        s.uy = uy;
        s.uz = uz;
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
    }

    /** Rectangle horizontal (plan XZ) a la hauteur y. */
    public void flat(int id, float x0, float z0, float x1, float z1, float y) {
        add(id, x0, y, z0, x1 - x0, 0, 0, 0, 0, z1 - z0);
    }

    /** Rectangle (x0, y0)-(x1, y1) dans le plan XY de la pose (comme un texte ou un quad). */
    public void rect(int id, Pose p, float x0, float y0, float x1, float y1) {
        Matrix4f m = p.m();
        m.transformPosition(x0, y0, 0, a);
        m.transformPosition(x1, y0, 0, b);
        m.transformPosition(x0, y1, 0, c);
        add(id, a.x, a.y, a.z, b.x - a.x, b.y - a.y, b.z - a.z, c.x - a.x, c.y - a.y, c.z - a.z);
    }

    /**
     * Zone touchee par le rayon (origine ro, direction rd, repere du modele) parmi celles acceptees par le filtre.
     * @return indice de la zone la plus proche, ou -1
     */
    public int pick(float rox, float roy, float roz, float rdx, float rdy, float rdz, java.util.function.IntPredicate enabled) {
        int best = -1;
        float bestT = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Spot s = spots[i];
            if (enabled != null && !enabled.test(s.id)) continue;
            float t = hit(s, rox, roy, roz, rdx, rdy, rdz);
            if (t > 0 && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    /** Distance le long du rayon jusqu'a la zone, ou -1 si le rayon la manque. */
    public static float hit(Spot s, float rox, float roy, float roz, float rdx, float rdy, float rdz) {
        // normale = u x v
        float nx = s.uy * s.vz - s.uz * s.vy, ny = s.uz * s.vx - s.ux * s.vz, nz = s.ux * s.vy - s.uy * s.vx;
        float den = rdx * nx + rdy * ny + rdz * nz;
        if (Math.abs(den) < 1e-9f) return -1;
        float t = ((s.ox - rox) * nx + (s.oy - roy) * ny + (s.oz - roz) * nz) / den;
        if (t <= 0) return -1;
        float px = rox + rdx * t - s.ox, py = roy + rdy * t - s.oy, pz = roz + rdz * t - s.oz;
        // coordonnees (a, b) dans la base (u, v) : systeme 2x2 via les produits scalaires
        float uu = s.ux * s.ux + s.uy * s.uy + s.uz * s.uz, vv = s.vx * s.vx + s.vy * s.vy + s.vz * s.vz;
        float uv = s.ux * s.vx + s.uy * s.vy + s.uz * s.vz;
        float pu = px * s.ux + py * s.uy + pz * s.uz, pv = px * s.vx + py * s.vy + pz * s.vz;
        float det = uu * vv - uv * uv;
        if (Math.abs(det) < 1e-12f) return -1;
        float ca = (pu * vv - pv * uv) / det, cb = (pv * uu - pu * uv) / det;
        if (ca < 0 || ca > 1 || cb < 0 || cb > 1) return -1;
        return t;
    }
}
