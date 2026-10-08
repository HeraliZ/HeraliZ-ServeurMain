package com.caracrea.paint;

import com.caracrea.character.BodyType;

/**
 * Where each face of each body part lives on a vanilla 64x64 skin, scaled by
 * {@link Canvas#SCALE}. Faces are oriented as seen by someone looking at that
 * side of the player: on the FRONT face, x grows towards the player's left.
 */
public final class SkinLayout {
    private SkinLayout() {
    }

    public enum Part {
        HEAD(0, 0, 8, 8, 8, false),
        HAT(32, 0, 8, 8, 8, true),
        BODY(16, 16, 8, 12, 4, false),
        JACKET(16, 32, 8, 12, 4, true),
        RIGHT_ARM(40, 16, 4, 12, 4, false),
        RIGHT_SLEEVE(40, 32, 4, 12, 4, true),
        LEFT_ARM(32, 48, 4, 12, 4, false),
        LEFT_SLEEVE(48, 48, 4, 12, 4, true),
        RIGHT_LEG(0, 16, 4, 12, 4, false),
        RIGHT_PANTS(0, 32, 4, 12, 4, true),
        LEFT_LEG(16, 48, 4, 12, 4, false),
        LEFT_PANTS(0, 48, 4, 12, 4, true);

        public static final Part[] VALUES = values();

        final int u;
        final int v;
        final int w;
        final int h;
        final int d;
        /** Second layer (hat, jacket, sleeves, pants): transparent by default. */
        public final boolean outer;

        Part(int u, int v, int w, int h, int d, boolean outer) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.d = d;
            this.outer = outer;
        }

        public boolean isArm() {
            return this == RIGHT_ARM || this == LEFT_ARM || this == RIGHT_SLEEVE || this == LEFT_SLEEVE;
        }

        public boolean isLeg() {
            return this == RIGHT_LEG || this == LEFT_LEG || this == RIGHT_PANTS || this == LEFT_PANTS;
        }

        public boolean isHead() {
            return this == HEAD || this == HAT;
        }

        public boolean isTorso() {
            return this == BODY || this == JACKET;
        }

        /** The player's left limb (its outer side is the LEFT face). */
        public boolean isLeft() {
            return this == LEFT_ARM || this == LEFT_SLEEVE || this == LEFT_LEG || this == LEFT_PANTS;
        }

        /** Width in vanilla texels for a body type (arms are 3 wide on the slim model). */
        public int width(BodyType type) {
            return isArm() && type.slim() ? 3 : w;
        }

        public Part outerLayer() {
            return switch (this) {
                case HEAD -> HAT;
                case BODY -> JACKET;
                case RIGHT_ARM -> RIGHT_SLEEVE;
                case LEFT_ARM -> LEFT_SLEEVE;
                case RIGHT_LEG -> RIGHT_PANTS;
                case LEFT_LEG -> LEFT_PANTS;
                default -> this;
            };
        }
    }

    public enum Face {
        /** The player's right side. */
        RIGHT,
        FRONT,
        /** The player's left side. */
        LEFT,
        BACK,
        TOP,
        BOTTOM;

        public static final Face[] VALUES = values();
        public static final Face[] SIDES = {RIGHT, FRONT, LEFT, BACK};
    }

    /** Rectangle of a face on a 4x canvas, in canvas pixels. */
    public static Rect rect(Part part, Face face, BodyType type) {
        return rect(part, face, type, Canvas.SCALE);
    }

    /** Rectangle of a face on a canvas of {@code scale} pixels per texel. */
    public static Rect rect(Part part, Face face, BodyType type, int scale) {
        int w = part.width(type);
        int d = part.d;
        int h = part.h;
        int u = part.u;
        int v = part.v;
        int x;
        int y;
        int fw;
        int fh;
        switch (face) {
            case RIGHT -> { x = u; y = v + d; fw = d; fh = h; }
            case FRONT -> { x = u + d; y = v + d; fw = w; fh = h; }
            case LEFT -> { x = u + d + w; y = v + d; fw = d; fh = h; }
            case BACK -> { x = u + 2 * d + w; y = v + d; fw = w; fh = h; }
            case TOP -> { x = u + d; y = v; fw = w; fh = d; }
            default -> { x = u + d + w; y = v; fw = w; fh = d; }
        }
        return new Rect(x * scale, y * scale, fw * scale, fh * scale);
    }

    /** The front of the head on a canvas of {@code scale} pixels per texel. */
    public static Rect faceRect(int scale) {
        return rect(Part.HEAD, Face.FRONT, BodyType.MALE, scale);
    }

    /** Bounding box of all faces of a part. */
    public static Rect bounds(Part part, BodyType type) {
        Rect r = Rect.EMPTY;
        for (Face face : Face.VALUES) {
            r = r.union(rect(part, face, type));
        }
        return r;
    }

    /** The front of the head: the only region that changes with the face state. */
    public static Rect faceRect() {
        return rect(Part.HEAD, Face.FRONT, BodyType.MALE);
    }

    /** Height of a side face in canvas pixels. */
    public static int height(Part part) {
        return part.h * Canvas.SCALE;
    }

    public static int depth(Part part) {
        return part.d * Canvas.SCALE;
    }

    public static int width(Part part, BodyType type) {
        return part.width(type) * Canvas.SCALE;
    }
}
