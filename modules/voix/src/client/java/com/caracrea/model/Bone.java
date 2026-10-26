package com.caracrea.model;

import com.caracrea.character.BodyType;

/**
 * The six vanilla player bones. Detail boxes hang from them, so vanilla
 * animations, armour layers and other mods keep working: the pivots are
 * exactly those of the vanilla player model.
 */
public enum Bone {
    HEAD("head", 0, 0, 0),
    BODY("body", 0, 0, 0),
    RIGHT_ARM("right_arm", -5, 2, 0),
    LEFT_ARM("left_arm", 5, 2, 0),
    RIGHT_LEG("right_leg", -1.9F, 12, 0),
    LEFT_LEG("left_leg", 1.9F, 12, 0);

    public final String partName;
    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;

    Bone(String partName, float pivotX, float pivotY, float pivotZ) {
        this.partName = partName;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
    }

    public float pivotX() {
        return pivotX;
    }

    /** Slim arms hang half a pixel lower, as in the vanilla model. */
    public float pivotY(BodyType type) {
        return isArm() && type.slim() ? 2.5F : pivotY;
    }

    public float pivotZ() {
        return pivotZ;
    }

    public boolean isArm() {
        return this == RIGHT_ARM || this == LEFT_ARM;
    }

    public boolean isLeg() {
        return this == RIGHT_LEG || this == LEFT_LEG;
    }

    public boolean isLeft() {
        return this == LEFT_ARM || this == LEFT_LEG;
    }
}
