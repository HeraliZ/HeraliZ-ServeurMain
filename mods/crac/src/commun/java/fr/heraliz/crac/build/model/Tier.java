package fr.heraliz.crac.build.model;

/** Rust-style building grades. Pieces are always placed as {@link #TWIG} and upgraded with the hammer. */
public enum Tier {
    TWIG("twig", "Twig"),
    WOOD("wood", "Wood"),
    STONE("stone", "Stone"),
    METAL("metal", "Sheet Metal"),
    ARMORED("armored", "Armored");

    private static final Tier[] VALUES = values();

    public final String id;
    public final String displayName;

    Tier(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static Tier byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : TWIG;
    }

    public static Tier byId(String id) {
        for (Tier t : VALUES) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public boolean isMetal() {
        return this == METAL || this == ARMORED;
    }
}
