package com.caracrea.character;

import java.util.List;

import static com.caracrea.character.HairStyle.Extra;
import static com.caracrea.character.HairStyle.Fringe;
import static com.caracrea.character.HairStyle.Texture;

/**
 * Every preset a character can be made of. Indices into these lists are what
 * {@link Appearance} stores, so entries must only ever be appended.
 */
public final class Catalog {
    private Catalog() {
    }

    // ---------------------------------------------------------------- palettes

    public static final List<Swatch> SKIN_TONES = List.of(
            Swatch.of("porcelain", 247, 224, 208),
            Swatch.of("fair", 241, 211, 189),
            Swatch.of("light", 232, 196, 170),
            Swatch.of("beige", 222, 184, 152),
            Swatch.of("sand", 210, 170, 136),
            Swatch.of("olive", 196, 156, 118),
            Swatch.of("tan", 182, 137, 100),
            Swatch.of("caramel", 160, 114, 78),
            Swatch.of("bronze", 140, 96, 64),
            Swatch.of("brown", 116, 78, 52),
            Swatch.of("deep", 92, 60, 40),
            Swatch.of("ebony", 66, 42, 30));

    public static final List<Swatch> HAIR_COLORS = List.of(
            Swatch.of("black", 24, 22, 24),
            Swatch.of("soft_black", 42, 36, 36),
            Swatch.of("dark_brown", 62, 42, 32),
            Swatch.of("brown", 92, 62, 42),
            Swatch.of("chestnut", 120, 78, 50),
            Swatch.of("light_brown", 150, 106, 70),
            Swatch.of("dark_blonde", 172, 136, 86),
            Swatch.of("blonde", 208, 176, 110),
            Swatch.of("platinum", 232, 220, 190),
            Swatch.of("strawberry", 210, 150, 100),
            Swatch.of("auburn", 140, 62, 36),
            Swatch.of("red", 168, 74, 40),
            Swatch.of("ginger", 200, 100, 50),
            Swatch.of("grey", 140, 140, 140),
            Swatch.of("white", 225, 225, 222),
            Swatch.of("blue", 60, 90, 170),
            Swatch.of("pink", 220, 110, 160),
            Swatch.of("green", 70, 140, 90),
            Swatch.of("purple", 120, 70, 160));

    public static final List<Swatch> EYE_COLORS = List.of(
            Swatch.of("dark_brown", 60, 36, 24),
            Swatch.of("brown", 110, 66, 36),
            Swatch.of("amber", 160, 100, 40),
            Swatch.of("hazel", 120, 110, 50),
            Swatch.of("green", 70, 120, 70),
            Swatch.of("blue_grey", 100, 130, 160),
            Swatch.of("blue", 70, 120, 190),
            Swatch.of("light_blue", 120, 170, 210),
            Swatch.of("grey", 130, 135, 140),
            Swatch.of("black", 30, 28, 30));

    public static final List<Swatch> CLOTH_COLORS = List.of(
            Swatch.of("black", 28, 28, 30),
            Swatch.of("charcoal", 58, 60, 64),
            Swatch.of("grey", 120, 122, 126),
            Swatch.of("light_grey", 176, 178, 182),
            Swatch.of("white", 236, 236, 232),
            Swatch.of("cream", 226, 214, 186),
            Swatch.of("navy", 40, 52, 84),
            Swatch.of("denim", 70, 96, 140),
            Swatch.of("light_blue", 130, 165, 200),
            Swatch.of("olive", 96, 104, 64),
            Swatch.of("forest", 56, 88, 56),
            Swatch.of("khaki", 176, 160, 120),
            Swatch.of("brown", 104, 74, 48),
            Swatch.of("burgundy", 110, 36, 46),
            Swatch.of("red", 170, 46, 40),
            Swatch.of("orange", 210, 110, 40),
            Swatch.of("mustard", 200, 160, 50),
            Swatch.of("purple", 90, 60, 120),
            Swatch.of("pink", 220, 130, 160));

    /** Lip colours; "natural" is derived from the skin tone when painting. */
    public static final List<Swatch> LIP_COLORS = List.of(
            Swatch.of("natural", 0, 0, 0),
            Swatch.of("rosy", 214, 120, 128),
            Swatch.of("red", 184, 40, 56),
            Swatch.of("dark", 96, 32, 52));

    // ------------------------------------------------------------------ styles

    public static final List<Option> EYE_SHAPES = List.of(
            Option.both("round"), Option.both("almond"), Option.both("narrow"),
            Option.both("hooded"), Option.both("upturned"), Option.both("downturned"));

    public static final List<Option> BROW_STYLES = List.of(
            Option.both("natural"), Option.both("thick"), Option.both("thin"),
            Option.both("arched"), Option.both("straight"), Option.both("angled"));

    public static final List<Option> NOSE_STYLES = List.of(
            Option.both("small"), Option.both("medium"), Option.both("wide"), Option.both("pointed"));

    public static final List<Option> MOUTH_STYLES = List.of(
            Option.both("neutral"), Option.both("full"), Option.both("thin"), Option.both("smile"), Option.both("frown"));

    public static final List<HairStyle> HAIR_STYLES = List.of(
            //            id                pool         fringe shape                side temple back torso top    volume texture           extra
            new HairStyle("bald",            Pool.MALE,   0, Fringe.NONE,        0,  0,  0,  0, false, false, Texture.STRAIGHT, Extra.NONE),
            new HairStyle("buzz",            Pool.MALE,   3, Fringe.STRAIGHT,   10, 12, 12,  0, true,  false, Texture.BUZZ,     Extra.NONE),
            new HairStyle("crew",            Pool.MALE,   5, Fringe.STRAIGHT,    9, 11, 10,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("short_messy",     Pool.MALE,   8, Fringe.MESSY,      10, 12, 11,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("side_part",       Pool.MALE,   9, Fringe.SIDE_SWEPT, 10, 12, 11,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("quiff",           Pool.MALE,   4, Fringe.SPIKY,       9, 11, 10,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("slicked_back",    Pool.MALE,   3, Fringe.STRAIGHT,   10, 12, 12,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("undercut",        Pool.MALE,  10, Fringe.SIDE_SWEPT,  4,  6,  8,  0, true,  true,  Texture.STRAIGHT, Extra.UNDERCUT),
            new HairStyle("fade",            Pool.MALE,   4, Fringe.MESSY,       4,  6,  6,  0, true,  true,  Texture.STRAIGHT, Extra.UNDERCUT),
            new HairStyle("curly_short",     Pool.BOTH,   8, Fringe.MESSY,      11, 12, 12,  0, true,  true,  Texture.CURLY,    Extra.NONE),
            new HairStyle("afro",            Pool.BOTH,  10, Fringe.STRAIGHT,   18, 16, 20,  0, true,  true,  Texture.CURLY,    Extra.AFRO),
            new HairStyle("dreads",          Pool.BOTH,   6, Fringe.STRAIGHT,   24, 16, 32,  0, true,  true,  Texture.DREADS,   Extra.NONE),
            new HairStyle("shoulder_length", Pool.BOTH,   8, Fringe.CURTAIN,    30, 16, 32,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("bob",             Pool.FEMALE, 11, Fringe.STRAIGHT,  26, 26, 26,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("pixie",           Pool.FEMALE, 9, Fringe.SIDE_SWEPT, 12, 14, 12,  0, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("long_straight",   Pool.FEMALE, 9, Fringe.CURTAIN,    32, 14, 32, 18, true,  true,  Texture.STRAIGHT, Extra.NONE),
            new HairStyle("long_wavy",       Pool.FEMALE, 8, Fringe.MIDDLE_PART, 32, 14, 32, 16, true, true,  Texture.WAVY,     Extra.NONE),
            new HairStyle("ponytail",        Pool.FEMALE, 5, Fringe.SIDE_SWEPT, 20, 16, 24, 12, true,  true,  Texture.STRAIGHT, Extra.PONYTAIL),
            new HairStyle("bun",             Pool.FEMALE, 4, Fringe.MIDDLE_PART, 20, 14, 24,  0, true, true,  Texture.STRAIGHT, Extra.BUN),
            new HairStyle("braid",           Pool.FEMALE, 7, Fringe.MIDDLE_PART, 20, 14, 24, 26, true, true,  Texture.STRAIGHT, Extra.BRAID),
            new HairStyle("lob",             Pool.FEMALE, 9, Fringe.SIDE_SWEPT, 32, 28, 32,  0, true,  true,  Texture.STRAIGHT, Extra.NONE));

    public static final List<Option> FACIAL_HAIR = List.of(
            Option.both("none"), Option.male("stubble"), Option.male("mustache"), Option.male("goatee"),
            Option.male("chinstrap"), Option.male("short_beard"), Option.male("full_beard"));

    public static final List<Option> SHIRTS = List.of(
            Option.both("tshirt"), Option.both("vneck"), Option.both("tank_top"), Option.both("long_sleeve"),
            Option.both("hoodie"), Option.both("flannel"), Option.both("jacket"), Option.both("striped"),
            Option.both("henley"), Option.both("sweater"), Option.both("polo"), Option.both("dress_shirt"),
            Option.both("camo"), Option.female("crop_top"));

    public static final List<Option> PANTS = List.of(
            Option.both("jeans"), Option.both("cargo"), Option.both("shorts"), Option.both("joggers"),
            Option.female("skirt"), Option.both("leggings"), Option.both("ripped_jeans"), Option.both("camo"),
            Option.both("trousers"), Option.both("track"));

    public static final List<Option> SHOES = List.of(
            Option.both("sneakers"), Option.both("boots"), Option.both("combat_boots"), Option.both("hiking_boots"),
            Option.both("dress_shoes"), Option.both("sandals"), Option.both("rain_boots"), Option.both("barefoot"));

    /** Marks are flags: the index of an entry is its bit in {@link Trait#MARKS}. */
    public static final List<Option> MARKS = List.of(
            Option.both("freckles"), Option.both("scar_left"), Option.both("scar_right"), Option.both("scar_brow"),
            Option.both("mole"), Option.both("dirt"), Option.both("blood"));

    public static final int MARK_FRECKLES = 1;
    public static final int MARK_SCAR_LEFT = 1 << 1;
    public static final int MARK_SCAR_RIGHT = 1 << 2;
    public static final int MARK_SCAR_BROW = 1 << 3;
    public static final int MARK_MOLE = 1 << 4;
    public static final int MARK_DIRT = 1 << 5;
    public static final int MARK_BLOOD = 1 << 6;

    public static final List<Option> WEAR = List.of(
            Option.both("clean"), Option.both("dusty"), Option.both("dirty"), Option.both("bloody"));

    /**
     * Body types. The female body was retired in 1.3.0 (the server only has men): an old female save reads as male (the
     * value is clamped) and {@link Appearance#of} turns what only women could wear into the closest choice for men.
     */
    public static final List<Option> BODY_TYPES = List.of(Option.both("male"));

    // ----------------------------------------------------------------- lookups

    /** Number of distinct values a trait can take. */
    public static int count(Trait trait) {
        return switch (trait) {
            case BODY_TYPE -> BODY_TYPES.size();
            case SKIN_TONE -> SKIN_TONES.size();
            case EYE_SHAPE -> EYE_SHAPES.size();
            case EYE_COLOR -> EYE_COLORS.size();
            case EYE_SIZE, EYE_SPACING, EYE_HEIGHT, IRIS_SIZE, MOUTH_WIDTH -> Trait.SLIDER_STEPS;
            case BROW_STYLE -> BROW_STYLES.size();
            case NOSE_STYLE -> NOSE_STYLES.size();
            case MOUTH_STYLE -> MOUTH_STYLES.size();
            case LIP_COLOR -> LIP_COLORS.size();
            case HAIR_STYLE -> HAIR_STYLES.size();
            case HAIR_COLOR -> HAIR_COLORS.size();
            case FACIAL_HAIR -> FACIAL_HAIR.size();
            case SHIRT_STYLE -> SHIRTS.size();
            case SHIRT_COLOR, SHIRT_COLOR2, PANTS_COLOR, SHOES_COLOR -> CLOTH_COLORS.size();
            case PANTS_STYLE -> PANTS.size();
            case SHOES_STYLE -> SHOES.size();
            case MARKS -> 1 << MARKS.size();
            case WEAR -> WEAR.size();
            case SCAR1_ZONE, SCAR1_X, SCAR1_Y, SCAR1_SHAPE, SCAR2_ZONE, SCAR2_X, SCAR2_Y, SCAR2_SHAPE,
                    SCAR3_ZONE, SCAR3_X, SCAR3_Y, SCAR3_SHAPE,
                    SCAR1_SIZE, SCAR1_BEND, SCAR2_SIZE, SCAR2_BEND, SCAR3_SIZE, SCAR3_BEND -> Scar.count(trait);
        };
    }

    public static int clamp(Trait trait, int value) {
        return Math.max(0, Math.min(count(trait) - 1, value));
    }

    /** Preset list of an option trait (null for sliders, colours and flags). */
    public static List<Option> options(Trait trait) {
        return switch (trait) {
            case BODY_TYPE -> BODY_TYPES;
            case EYE_SHAPE -> EYE_SHAPES;
            case BROW_STYLE -> BROW_STYLES;
            case NOSE_STYLE -> NOSE_STYLES;
            case MOUTH_STYLE -> MOUTH_STYLES;
            case FACIAL_HAIR -> FACIAL_HAIR;
            case SHIRT_STYLE -> SHIRTS;
            case PANTS_STYLE -> PANTS;
            case SHOES_STYLE -> SHOES;
            case WEAR -> WEAR;
            case MARKS -> MARKS;
            default -> null;
        };
    }

    /** Palette of a colour trait (null otherwise). */
    public static List<Swatch> palette(Trait trait) {
        return switch (trait) {
            case SKIN_TONE -> SKIN_TONES;
            case HAIR_COLOR -> HAIR_COLORS;
            case EYE_COLOR -> EYE_COLORS;
            case LIP_COLOR -> LIP_COLORS;
            case SHIRT_COLOR, SHIRT_COLOR2, PANTS_COLOR, SHOES_COLOR -> CLOTH_COLORS;
            default -> null;
        };
    }

    public static boolean isColor(Trait trait) {
        return palette(trait) != null;
    }

    /** Lang-key id of a value: preset id, swatch id, or the number for sliders. */
    public static String id(Trait trait, int value) {
        if (trait == Trait.HAIR_STYLE) {
            return HAIR_STYLES.get(clamp(trait, value)).id();
        }
        List<Swatch> palette = palette(trait);
        if (palette != null) {
            return palette.get(clamp(trait, value)).id();
        }
        List<Option> options = options(trait);
        if (options != null && trait.kind() == Trait.Kind.OPTION) {
            return options.get(clamp(trait, value)).id();
        }
        return Integer.toString(value);
    }

    /** Whether a preset value is in the default pool of a body type. */
    public static boolean suits(Trait trait, int value, BodyType type) {
        if (trait == Trait.HAIR_STYLE) {
            return HAIR_STYLES.get(clamp(trait, value)).pool().suits(type);
        }
        List<Option> options = options(trait);
        return options == null || options.get(clamp(trait, value)).pool().suits(type);
    }

    /**
     * Whether a preset can still be chosen. The presets made for women only (long and tied-up hairstyles, crop top,
     * skirt) were retired with the female body; they stay in the lists, whose indices are saved.
     */
    public static boolean offered(Trait trait, int value) {
        return switch (trait) {
            case HAIR_STYLE, SHIRT_STYLE, PANTS_STYLE -> suits(trait, value, BodyType.MALE);
            default -> true;
        };
    }

    /** A value of the trait that can still be chosen: clamped, and a retired preset becomes the closest one for men. */
    public static int sanitize(Trait trait, int value) {
        int clamped = clamp(trait, value);
        if (offered(trait, clamped)) {
            return clamped;
        }
        String replacement = switch (trait) {
            case HAIR_STYLE -> switch (id(trait, clamped)) {
                case "pixie" -> "short_messy";
                case "ponytail", "bun" -> "slicked_back";
                default -> "shoulder_length";
            };
            case SHIRT_STYLE -> "tshirt";
            default -> "jeans";
        };
        for (int i = 0; i < count(trait); i++) {
            if (id(trait, i).equals(replacement)) {
                return i;
            }
        }
        return 0;
    }

    public static HairStyle hair(Appearance appearance) {
        return HAIR_STYLES.get(appearance.get(Trait.HAIR_STYLE));
    }

    public static Swatch swatch(Appearance appearance, Trait trait) {
        return palette(trait).get(appearance.get(trait));
    }

    public static String optionId(Appearance appearance, Trait trait) {
        return id(trait, appearance.get(trait));
    }
}
