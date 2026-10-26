package com.caracrea.model;

/** What a detail box is made of; decides how its faces are painted. */
public enum Material {
    /** The head cube: painted with the high-definition face, not by the detail painter. */
    HEAD,
    HAIR,
    /** The torso: the shirt, or skin where the shirt does not reach. */
    SHIRT,
    /** Legs: trousers, or skin where they do not reach. */
    PANTS,
    SHOE,
    SOLE,
    BELT,
    /** Collar, hood, pocket: the shirt's main colour. */
    SHIRT_TRIM
}
