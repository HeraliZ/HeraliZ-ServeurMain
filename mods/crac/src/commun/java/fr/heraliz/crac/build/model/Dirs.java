package fr.heraliz.crac.build.model;

/**
 * The 12 lattice directions. Direction {@code k} is the angle {@code 30° * k} measured from +x (east) towards +z
 * (south). Turning by +3 steps (+90°) from an edge direction points to the interior of every footprint.
 * <p>
 * Determinism: client and server must compute bit-identical shapes, so geometry never calls {@code Math.sin/cos}:
 * unit vectors come from the table below, built from {@link #SQRT3} only. The arrays are shared: never modify them.
 */
public final class Dirs {
    public static final double SQRT3 = Math.sqrt(3.0);
    public static final double HALF_SQRT3 = SQRT3 / 2.0;
    /** Lattice unit in px: a lattice coordinate pair (a, b) is the length {@code 12 * (a + b * sqrt3)}. */
    public static final double UNIT = 12.0;
    /** {@code 12 * sqrt3}, the px length of one irrational lattice unit. */
    public static final double UNIT_R3 = UNIT * SQRT3;

    /** 48 px edge vector of direction k in lattice units: {xa, xb, za, zb}. */
    public static final int[][] D = {
        {4, 0, 0, 0},
        {0, 2, 2, 0},
        {2, 0, 0, 2},
        {0, 0, 4, 0},
        {-2, 0, 0, 2},
        {0, -2, 2, 0},
        {-4, 0, 0, 0},
        {0, -2, -2, 0},
        {-2, 0, 0, -2},
        {0, 0, -4, 0},
        {2, 0, 0, -2},
        {0, 2, -2, 0}
    };
    /** 24 px half vectors {@code D[k] / 2}: edge midpoints are lattice points. */
    public static final int[][] H = new int[12][4];
    /** Unit vector components of direction k: x = COS[k], z = SIN[k]. */
    public static final double[] COS = {1, HALF_SQRT3, 0.5, 0, -0.5, -HALF_SQRT3, -1, -HALF_SQRT3, -0.5, 0, 0.5, HALF_SQRT3};
    public static final double[] SIN = {0, 0.5, HALF_SQRT3, 1, HALF_SQRT3, 0.5, 0, -0.5, -HALF_SQRT3, -1, -HALF_SQRT3, -0.5};

    static {
        for (int k = 0; k < 12; k++) {
            for (int i = 0; i < 4; i++) {
                H[k][i] = D[k][i] / 2;
            }
        }
    }

    private Dirs() {
    }

    public static int norm(int k) {
        return Math.floorMod(k, 12);
    }

    /** x component of the unit vector of direction k (any integer, taken mod 12). */
    public static double ex(int k) {
        return COS[norm(k)];
    }

    /** z component of the unit vector of direction k (any integer, taken mod 12). */
    public static double ez(int k) {
        return SIN[norm(k)];
    }

    public static int[] d(int k) {
        return D[norm(k)];
    }

    public static int[] h(int k) {
        return H[norm(k)];
    }

    /** Smallest angle between two directions, in 30° steps (0..6). */
    public static int angleSteps(int a, int b) {
        int d = norm(a - b);
        return Math.min(d, 12 - d);
    }
}
