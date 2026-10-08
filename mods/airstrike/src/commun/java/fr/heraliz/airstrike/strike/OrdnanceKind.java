package fr.heraliz.airstrike.strike;

/**
 * Everything that can hang under a wing or fall from the sky. The name matches the top-level part of
 * {@code assets/airstrike/geo/ordnance.geo}.
 */
public enum OrdnanceKind {
    BOMB("bomb", true),
    GBU("gbu", false),
    CLUSTER("cluster", false),
    BOMBLET("bomblet", true),
    NAPALM("napalm", true),
    SMOKE_CAN("smoke_can", true),
    GAS_CAN("gas_can", true),
    MISSILE("missile", false),
    FUEL_TANK("fuel_tank", false),
    BULLET("bullet", false),
    CRATE("crate", false);

    private static final OrdnanceKind[] VALUES = values();

    public final String part;
    /** Whether the object tumbles a little while falling. */
    public final boolean wobbles;

    OrdnanceKind(String part, boolean wobbles) {
        this.part = part;
        this.wobbles = wobbles;
    }

    public static OrdnanceKind byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BOMB;
    }
}
