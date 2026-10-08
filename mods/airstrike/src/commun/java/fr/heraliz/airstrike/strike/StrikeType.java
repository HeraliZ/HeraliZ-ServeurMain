package fr.heraliz.airstrike.strike;

/**
 * Every payload the designator can call in. The order is used for network ids and for cycling in the HUD.
 */
public enum StrikeType {
    BOMBING("bombing", PlaneType.ATTACK_JET, 0xE0452B, 0xB3262A),
    PRECISION("precision", PlaneType.ATTACK_JET, 0xFF7A1C, 0xB3262A),
    CLUSTER("cluster", PlaneType.ATTACK_JET, 0xE8C33A, 0xB3262A),
    NAPALM("napalm", PlaneType.ATTACK_JET, 0xFF5A00, 0xB3262A),
    GUN_RUN("gun_run", PlaneType.ATTACK_JET, 0xFFD27A, 0xB3262A),
    SMOKE("smoke", PlaneType.ATTACK_JET, 0xD8D8D8, 0xE0E0E0),
    GAS("gas", PlaneType.ATTACK_JET, 0x9ACD32, 0x9ACD32),
    SUPPLY("supply", PlaneType.CARGO, 0x4FA3FF, 0x3FBF4F);

    private static final StrikeType[] VALUES = values();

    public final String id;
    public final PlaneType plane;
    /** Colour used by the HUD / item tooltips. */
    public final int uiColor;
    /** Colour of the signal smoke popped on the target. */
    public final int markerColor;

    StrikeType(String id, PlaneType plane, int uiColor, int markerColor) {
        this.id = id;
        this.plane = plane;
        this.uiColor = uiColor;
        this.markerColor = markerColor;
    }

    public String translationKey() {
        return "strike.airstrike." + id;
    }

    public String descriptionKey() {
        return "strike.airstrike." + id + ".desc";
    }

    public static StrikeType byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BOMBING;
    }

    public static StrikeType byName(String name) {
        for (StrikeType t : VALUES) {
            if (t.id.equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    /**
     * Stores hanging under the jet's 8 hardpoints (r0..r3 then l0..l3) for this mission. Some are released during
     * the run (see {@link StrikePlanner}), the others stay on the aircraft purely for looks.
     */
    public OrdnanceKind[] loadout() {
        OrdnanceKind[] l = new OrdnanceKind[8];
        switch (this) {
            case BOMBING -> {
                for (int i = 0; i < 8; i++) l[i] = OrdnanceKind.BOMB;
            }
            case PRECISION -> {
                l[1] = OrdnanceKind.GBU;
                l[5] = OrdnanceKind.GBU;
                l[3] = OrdnanceKind.MISSILE;
                l[7] = OrdnanceKind.MISSILE;
            }
            case CLUSTER -> {
                l[1] = OrdnanceKind.CLUSTER;
                l[5] = OrdnanceKind.CLUSTER;
                l[3] = OrdnanceKind.MISSILE;
                l[7] = OrdnanceKind.MISSILE;
            }
            case NAPALM -> {
                l[0] = l[1] = l[4] = l[5] = OrdnanceKind.NAPALM;
                l[3] = l[7] = OrdnanceKind.MISSILE;
            }
            case GUN_RUN -> {
                l[1] = l[5] = OrdnanceKind.FUEL_TANK;
                l[2] = l[6] = OrdnanceKind.MISSILE;
                l[3] = l[7] = OrdnanceKind.MISSILE;
            }
            case SMOKE -> {
                l[0] = l[1] = l[2] = l[4] = l[5] = l[6] = OrdnanceKind.SMOKE_CAN;
            }
            case GAS -> {
                l[1] = l[2] = l[5] = l[6] = OrdnanceKind.GAS_CAN;
                l[3] = l[7] = OrdnanceKind.MISSILE;
            }
            default -> {
            }
        }
        return l;
    }
}
