package fr.heraliz.airstrike.strike;

/**
 * Attach points of the aircraft models, in blocks, aircraft frame (x right, y up, z back).
 * Values come from the generated geometry (tools/modelgen) - keep them in sync.
 */
public final class AircraftData {
    /** Jet hardpoints: indices 0..3 = right wing inner to outer, 4..7 = left wing. */
    public static final double[][] JET_HARDPOINTS = {
            {30.0 / 16, -17.0 / 16, -3.0 / 16},
            {54.0 / 16, -17.0 / 16, -3.0 / 16},
            {86.7622 / 16, -14.3622 / 16, -3.0 / 16},
            {110.5833 / 16, -11.4374 / 16, -3.0 / 16},
            {-30.0 / 16, -17.0 / 16, -3.0 / 16},
            {-54.0 / 16, -17.0 / 16, -3.0 / 16},
            {-86.7622 / 16, -14.3622 / 16, -3.0 / 16},
            {-110.5833 / 16, -11.4374 / 16, -3.0 / 16},
    };
    /** Cannon muzzle. */
    public static final double[] JET_GUN = {-2.0 / 16, -7.5 / 16, -151.0 / 16};
    /** Where the supply crate leaves the cargo ramp (scaled model, blocks). */
    public static final double[] CARGO_RAMP_EXIT = {0.0, -2.03, 10.25};

    private AircraftData() {
    }
}
