package fr.heraliz.crac.build.model;

/** Every building block the Building Plan can place. */
public enum PieceKind {
    FOUNDATION("foundation", "Foundation", Family.SURFACE, Shape.SQUARE, 1),
    TRI_FOUNDATION("triangle_foundation", "Triangle Foundation", Family.SURFACE, Shape.TRIANGLE, 1),
    FLOOR("floor", "Floor", Family.SURFACE, Shape.SQUARE, 1),
    TRI_FLOOR("triangle_floor", "Triangle Floor", Family.SURFACE, Shape.TRIANGLE, 1),
    ROOF("roof", "Roof", Family.SURFACE, Shape.SQUARE, 1),
    TRI_ROOF("triangle_roof", "Triangle Roof", Family.SURFACE, Shape.TRIANGLE, 1),
    WALL("wall", "Wall", Family.WALL, Shape.EDGE, 4),
    HALF_WALL("half_wall", "Half Wall", Family.WALL, Shape.EDGE, 4),
    DOORWAY("doorway", "Doorway", Family.WALL, Shape.EDGE, 4),
    DOUBLE_DOORWAY("double_doorway", "Double Doorway", Family.WALL, Shape.EDGE, 4),
    WINDOW("window", "Window", Family.WALL, Shape.EDGE, 4),
    STAIRS_L("l_stairs", "L-Shaped Stairs", Family.VOLUME, Shape.SQUARE, 8),
    RAMP("ramp", "Ramp", Family.VOLUME, Shape.SQUARE, 8);

    public enum Family { SURFACE, WALL, VOLUME }

    /**
     * Lattice shape of a piece. Footprints list their vertices turning by +steps (towards the interior):
     * {@code turn} is the direction change from one edge to the next, {@code sectors} the number of 30° sectors the
     * footprint covers at each vertex and {@code orients} the number of canonical orientations.
     */
    public enum Shape {
        SQUARE(4, 3, 3, 3),
        TRIANGLE(3, 4, 2, 4),
        EDGE(2, 6, 0, 6);

        public final int vertices;
        public final int turn;
        public final int sectors;
        public final int orients;

        Shape(int vertices, int turn, int sectors, int orients) {
            this.vertices = vertices;
            this.turn = turn;
            this.sectors = sectors;
            this.orients = orients;
        }

        public boolean isFootprint() {
            return this != EDGE;
        }
    }

    /** Highest |h| a key may carry (half-wall units of 24 px). */
    public static final int H_MAX = 2048;

    private static final PieceKind[] VALUES = values();

    public final String id;
    public final String displayName;
    public final Family family;
    public final Shape shape;
    /** Number of distinct rotations the R key cycles through ({@code PieceKey.rot} values). */
    public final int rotations;

    PieceKind(String id, String displayName, Family family, Shape shape, int rotations) {
        this.id = id;
        this.displayName = displayName;
        this.family = family;
        this.shape = shape;
        this.rotations = rotations;
    }

    public static PieceKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    public static PieceKind byId(String id) {
        for (PieceKind k : VALUES) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    public boolean isFoundation() {
        return this == FOUNDATION || this == TRI_FOUNDATION;
    }

    /** Walkable surfaces that walls, stairs and ramps can stand on. Roofs are not. */
    public boolean isWalkableSurface() {
        return this == FOUNDATION || this == TRI_FOUNDATION || this == FLOOR || this == TRI_FLOOR;
    }

    public boolean isSurface() {
        return this.family == Family.SURFACE;
    }

    public boolean isVolume() {
        return this.family == Family.VOLUME;
    }

    public boolean isWall() {
        return this.family == Family.WALL;
    }

    /** ROOF or TRI_ROOF: every roof rule (cantilever, variants, shapes) applies to both. */
    public boolean isRoof() {
        return this == ROOF || this == TRI_ROOF;
    }

    public boolean hasDoor() {
        return this == DOORWAY || this == DOUBLE_DOORWAY;
    }

    public boolean isTriangle() {
        return this.shape == Shape.TRIANGLE;
    }

    /**
     * Height of the piece's top above its base in 24 px units: 2 for full walls and volumes, 1 for the half wall,
     * 0 for surfaces (their top is their base). A half ramp is lower, see {@code PieceKey.topUnits()}.
     */
    public int topUnits() {
        return switch (this.family) {
            case WALL -> this == HALF_WALL ? 1 : 2;
            case VOLUME -> 2;
            case SURFACE -> 0;
        };
    }

    /** Lowest height h (24 px units) this piece may sit at: foundations are at 0, floors and roofs above it. */
    public int minH() {
        return switch (this) {
            case FLOOR, TRI_FLOOR, ROOF, TRI_ROOF -> 1;
            default -> 0;
        };
    }

    /** Highest height h; foundations only exist at h = 0. */
    public int maxH() {
        return this.isFoundation() ? 0 : H_MAX;
    }
}
