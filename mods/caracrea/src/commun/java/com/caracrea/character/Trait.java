package com.caracrea.character;

/**
 * Every choice that makes up a character. Each trait is stored as one byte in
 * {@link Appearance}; {@link Catalog#count(Trait)} gives the number of values.
 */
public enum Trait {
    /** Male (classic 4px arms) or female (slim 3px arms). */
    BODY_TYPE(Kind.OPTION),
    SKIN_TONE(Kind.OPTION),
    EYE_SHAPE(Kind.OPTION),
    EYE_COLOR(Kind.OPTION),
    EYE_SIZE(Kind.SLIDER),
    EYE_SPACING(Kind.SLIDER),
    EYE_HEIGHT(Kind.SLIDER),
    IRIS_SIZE(Kind.SLIDER),
    BROW_STYLE(Kind.OPTION),
    NOSE_STYLE(Kind.OPTION),
    MOUTH_STYLE(Kind.OPTION),
    MOUTH_WIDTH(Kind.SLIDER),
    LIP_COLOR(Kind.OPTION),
    HAIR_STYLE(Kind.OPTION),
    HAIR_COLOR(Kind.OPTION),
    FACIAL_HAIR(Kind.OPTION),
    SHIRT_STYLE(Kind.OPTION),
    SHIRT_COLOR(Kind.OPTION),
    SHIRT_COLOR2(Kind.OPTION),
    PANTS_STYLE(Kind.OPTION),
    PANTS_COLOR(Kind.OPTION),
    SHOES_STYLE(Kind.OPTION),
    SHOES_COLOR(Kind.OPTION),
    /** Bit set of {@link Catalog#MARKS}. */
    MARKS(Kind.FLAGS),
    /** How worn the clothes are: clean, dusty, dirty, bloody. */
    WEAR(Kind.OPTION),
    // Three scars placed freely on the body (see Scar). Appended last: older saves read them as "none".
    SCAR1_ZONE(Kind.SCAR),
    SCAR1_X(Kind.SCAR),
    SCAR1_Y(Kind.SCAR),
    SCAR1_SHAPE(Kind.SCAR),
    SCAR2_ZONE(Kind.SCAR),
    SCAR2_X(Kind.SCAR),
    SCAR2_Y(Kind.SCAR),
    SCAR2_SHAPE(Kind.SCAR),
    SCAR3_ZONE(Kind.SCAR),
    SCAR3_X(Kind.SCAR),
    SCAR3_Y(Kind.SCAR),
    SCAR3_SHAPE(Kind.SCAR);

    public enum Kind { OPTION, SLIDER, FLAGS, SCAR }

    public static final Trait[] VALUES = values();

    /** Number of steps of a slider trait (0..4, 2 being the middle). */
    public static final int SLIDER_STEPS = 5;

    private final Kind kind;

    Trait(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Lower-case name used in lang keys and commands. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
