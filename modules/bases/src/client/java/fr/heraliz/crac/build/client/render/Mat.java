package fr.heraliz.crac.build.client.render;

import fr.heraliz.crac.build.model.Tier;

/**
 * Material slots of the piece meshes. Each slot maps to one texture per tier (or a shared one) under
 * {@code assets/crac/textures/block/building/} and to a chunk layer.
 */
public enum Mat {
    /** Big wall faces, one 96x96 texture per 48x48 px wall face. */
    WALL("wall"),
    /** Narrow faces: wall edges and tops, jambs, sills, floor and roof edges (32x32 per block, grain along v). */
    TRIM("trim"),
    /** Relief elements: cross beams, lintels, angle iron, ribs (32x32 per block, grain along u). */
    BEAM("beam"),
    POST("post"),
    POST_TOP("post_top"),
    /** Door leaf faces, 32x64 for a 16x32 px leaf. */
    DOOR("door"),
    FLOOR("floor"),
    FLOOR_UNDER("floor_under"),
    FOUNDATION_TOP("foundation_top"),
    /** Foundation slab sides, 96x32 for a 48x16 px side. */
    FOUNDATION_SIDE("foundation_side"),
    /** Foundation skirt below the slab, 96x64 for two blocks of depth. */
    SKIRT("skirt"),
    ROOF("roof"),
    ROOF_UNDER("roof_under"),
    /** Solid sides of stairs and ramps. */
    SIDE("side"),
    RAMP("ramp"),
    GLASS("glass", true),
    /** Window bars, door handles, hinges. */
    IRON("iron", true),
    /** Rope lashings of twig sticks. */
    ROPE("rope", true),
    /** Cut ends of the straw bundles of twig walls (twig only). */
    STRAW_END("straw_end"),
    CRACK_1("crack", true),
    CRACK_2("crack", true),
    CRACK_3("crack", true);

    public enum Layer { SOLID, CUTOUT, TRANSLUCENT }

    private static final Mat[] VALUES = values();

    public final String file;
    public final boolean shared;

    Mat(String file) {
        this(file, false);
    }

    Mat(String file, boolean shared) {
        this.file = file;
        this.shared = shared;
    }

    public static Mat crack(int stage) {
        return switch (stage) {
            case 1 -> CRACK_1;
            case 2 -> CRACK_2;
            default -> CRACK_3;
        };
    }

    public boolean isCrack() {
        return this == CRACK_1 || this == CRACK_2 || this == CRACK_3;
    }

    /** Whether damage cracks are drawn over this material. */
    public boolean crackable(Tier tier) {
        return switch (this) {
            case WALL, DOOR -> tier != Tier.TWIG;
            case FLOOR, FLOOR_UNDER, FOUNDATION_TOP, FOUNDATION_SIDE, ROOF, SIDE, RAMP -> true;
            default -> false;
        };
    }

    public Layer layer(Tier tier) {
        if (this == GLASS) {
            return Layer.TRANSLUCENT;
        }
        if (this.isCrack()) {
            return Layer.CUTOUT;
        }
        if (tier == Tier.TWIG && this == DOOR) {
            return Layer.CUTOUT;
        }
        return Layer.SOLID;
    }

    /** Texture path inside the block atlas, without namespace. */
    public String texture(Tier tier) {
        if (this.isCrack()) {
            String family = switch (tier) {
                case TWIG, WOOD -> "wood";
                case STONE -> "stone";
                case METAL, ARMORED -> "metal";
            };
            int stage = this == CRACK_1 ? 1 : this == CRACK_2 ? 2 : 3;
            return "block/building/common/crack_" + family + "_" + stage;
        }
        if (this.shared) {
            return "block/building/common/" + this.file;
        }
        if (this == STRAW_END) {
            return "block/building/twig/" + this.file;
        }
        return "block/building/" + tier.id + "/" + this.file;
    }

    public static Mat byOrdinal(int i) {
        return VALUES[i];
    }

    public static int count() {
        return VALUES.length;
    }
}
