package com.Spartan.esp;

/**
 * Ligne de vue sur la grille de blocs, sans dépendance Minecraft (testable).
 *
 * Traversée DDA (Amanatides & Woo) : on visite exactement les blocs que le
 * segment traverse, du départ vers l'arrivée, et on s'arrête au premier bloc
 * qui le coupe. Un bloc est soit transparent (null), soit opaque plein (FULL),
 * soit partiellement opaque : liste de boîtes locales (dalle, escalier, porte
 * fermée…) testées exactement contre la portion du segment qui traverse le bloc.
 *
 * Utilisée uniquement sur le thread serveur (les compteurs ne sont pas atomiques).
 */
public final class Raycaster {
    /** Bloc opaque plein. */
    public static final double[] FULL = new double[0];
    /** Les boîtes sont rétrécies d'autant : un rayon qui frôle une face passe (on préfère montrer). */
    private static final double GRAZE = 0.01;

    /** Occultation d'un bloc : null = transparent, FULL, ou boîtes locales à plat (minX,minY,minZ,maxX,maxY,maxZ…). */
    public interface Voxels {
        double[] at(int x, int y, int z);
    }

    private final Voxels voxels;
    private long rays;
    private long steps;

    public Raycaster(Voxels voxels) {
        this.voxels = voxels;
    }

    public long rays() {
        return rays;
    }

    public long steps() {
        return steps;
    }

    /**
     * Le segment [a, b] est-il coupé par un bloc ? Le bloc de départ n'est jamais
     * compté comme plein (œil collé à un bloc, posture accroupie / rampante) :
     * seules ses boîtes partielles comptent.
     */
    public boolean blocked(double ax, double ay, double az, double bx, double by, double bz) {
        rays++;
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        int x = floor(ax);
        int y = floor(ay);
        int z = floor(az);
        int ex = floor(bx);
        int ey = floor(by);
        int ez = floor(bz);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tdy = sy != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tdz = sz != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
        double tmx = sx > 0 ? (x + 1 - ax) * tdx : sx < 0 ? (ax - x) * tdx : Double.POSITIVE_INFINITY;
        double tmy = sy > 0 ? (y + 1 - ay) * tdy : sy < 0 ? (ay - y) * tdy : Double.POSITIVE_INFINITY;
        double tmz = sz > 0 ? (z + 1 - az) * tdz : sz < 0 ? (az - z) * tdz : Double.POSITIVE_INFINITY;
        double t = 0;
        boolean first = true;
        int guard = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z) + 3;
        while (guard-- > 0) {
            double tNext = Math.min(tmx, Math.min(tmy, tmz));
            double[] occ = voxels.at(x, y, z);
            steps++;
            if (occ != null) {
                if (occ == FULL) {
                    if (!first) {
                        return true;
                    }
                } else if (hitsBoxes(occ, x, y, z, ax, ay, az, dx, dy, dz, t, Math.min(tNext, 1.0))) {
                    return true;
                }
            }
            first = false;
            if ((x == ex && y == ey && z == ez) || tNext >= 1.0) {
                return false;
            }
            if (tmx <= tmy && tmx <= tmz) {
                x += sx;
                t = tmx;
                tmx += tdx;
            } else if (tmy <= tmz) {
                y += sy;
                t = tmy;
                tmy += tdy;
            } else {
                z += sz;
                t = tmz;
                tmz += tdz;
            }
        }
        return false;
    }

    /** La portion [t0, t1] du segment coupe-t-elle une des boîtes du bloc (x, y, z) ? */
    static boolean hitsBoxes(double[] boxes, int x, int y, int z, double ax, double ay, double az,
                             double dx, double dy, double dz, double t0, double t1) {
        for (int i = 0; i + 5 < boxes.length; i += 6) {
            if (segmentHitsBox(ax, ay, az, dx, dy, dz, t0, t1,
                    x + boxes[i] + GRAZE, y + boxes[i + 1] + GRAZE, z + boxes[i + 2] + GRAZE,
                    x + boxes[i + 3] - GRAZE, y + boxes[i + 4] - GRAZE, z + boxes[i + 5] - GRAZE)) {
                return true;
            }
        }
        return false;
    }

    /** Méthode des « slabs » : le segment a + t·d, t ∈ [t0, t1], traverse-t-il la boîte ? */
    static boolean segmentHitsBox(double ax, double ay, double az, double dx, double dy, double dz,
                                  double t0, double t1, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return false; // boîte plus fine que la tolérance
        }
        double lo = t0;
        double hi = t1;
        if (dx == 0) {
            if (ax <= minX || ax >= maxX) {
                return false;
            }
        } else {
            double ta = (minX - ax) / dx;
            double tb = (maxX - ax) / dx;
            lo = Math.max(lo, Math.min(ta, tb));
            hi = Math.min(hi, Math.max(ta, tb));
            if (lo > hi) {
                return false;
            }
        }
        if (dy == 0) {
            if (ay <= minY || ay >= maxY) {
                return false;
            }
        } else {
            double ta = (minY - ay) / dy;
            double tb = (maxY - ay) / dy;
            lo = Math.max(lo, Math.min(ta, tb));
            hi = Math.min(hi, Math.max(ta, tb));
            if (lo > hi) {
                return false;
            }
        }
        if (dz == 0) {
            return az > minZ && az < maxZ;
        }
        double ta = (minZ - az) / dz;
        double tb = (maxZ - az) / dz;
        lo = Math.max(lo, Math.min(ta, tb));
        hi = Math.min(hi, Math.max(ta, tb));
        return lo <= hi;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
