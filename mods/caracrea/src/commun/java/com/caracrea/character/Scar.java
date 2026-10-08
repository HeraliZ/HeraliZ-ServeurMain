package com.caracrea.character;

import java.util.ArrayList;
import java.util.List;

/**
 * A scar placed by the player: a short line on the front or the back of a
 * body part. Its position is a point of that face (0..1 across, 0..1 down,
 * as seen by someone looking at that side), its shape an angle and a length.
 * Scars are drawn on bare skin only, so clothes hide them.
 */
public record Scar(Zone zone, float u, float v, float angle, float length) {
    /** Number of scars a character can have. */
    public static final int COUNT = 3;
    /** Steps of the position across and down a face. */
    public static final int POSITIONS = 64;
    public static final int ANGLES = 8;
    public static final int LENGTHS = 3;
    /** Lengths in model units (the head is 8 units wide). */
    private static final float[] LENGTH_UNITS = {1.3F, 2.2F, 3.2F};

    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Where a scar can go: the front or the back of a body part. */
    public enum Zone {
        HEAD_FRONT(Part.HEAD, false), HEAD_BACK(Part.HEAD, true),
        BODY_FRONT(Part.BODY, false), BODY_BACK(Part.BODY, true),
        RIGHT_ARM_FRONT(Part.RIGHT_ARM, false), RIGHT_ARM_BACK(Part.RIGHT_ARM, true),
        LEFT_ARM_FRONT(Part.LEFT_ARM, false), LEFT_ARM_BACK(Part.LEFT_ARM, true),
        RIGHT_LEG_FRONT(Part.RIGHT_LEG, false), RIGHT_LEG_BACK(Part.RIGHT_LEG, true),
        LEFT_LEG_FRONT(Part.LEFT_LEG, false), LEFT_LEG_BACK(Part.LEFT_LEG, true);

        public static final Zone[] VALUES = values();

        public final Part part;
        public final boolean back;

        Zone(Part part, boolean back) {
            this.part = part;
            this.back = back;
        }

        public static Zone of(Part part, boolean back) {
            return VALUES[part.ordinal() * 2 + (back ? 1 : 0)];
        }
    }

    /** The four traits of scar {@code index} (0..2): zone, x, y, shape. */
    public static Trait[] traits(int index) {
        int first = Trait.SCAR1_ZONE.ordinal() + index * 4;
        return new Trait[]{Trait.VALUES[first], Trait.VALUES[first + 1], Trait.VALUES[first + 2], Trait.VALUES[first + 3]};
    }

    /** Number of values of a scar trait. */
    public static int count(Trait trait) {
        int field = (trait.ordinal() - Trait.SCAR1_ZONE.ordinal()) % 4;
        return switch (field) {
            case 0 -> Zone.VALUES.length + 1;   // 0 = no scar
            case 1, 2 -> POSITIONS;
            default -> ANGLES * LENGTHS;
        };
    }

    public static int shape(int angle, int length) {
        return Math.floorMod(angle, ANGLES) + ANGLES * Math.max(0, Math.min(LENGTHS - 1, length));
    }

    public static int angleStep(int shape) {
        return shape % ANGLES;
    }

    public static int lengthStep(int shape) {
        return shape / ANGLES;
    }

    /** The scars of a character (empty slots skipped). */
    public static List<Scar> of(Appearance appearance) {
        List<Scar> scars = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            Trait[] t = traits(i);
            int zone = appearance.get(t[0]);
            if (zone == 0) {
                continue;
            }
            int shape = appearance.get(t[3]);
            scars.add(new Scar(Zone.VALUES[zone - 1],
                    (appearance.get(t[1]) + 0.5F) / POSITIONS, (appearance.get(t[2]) + 0.5F) / POSITIONS,
                    (float) (angleStep(shape) * Math.PI / ANGLES), LENGTH_UNITS[lengthStep(shape)]));
        }
        return scars;
    }

    /**
     * The vanilla cube of a part in bone space: {minX, minY, minZ, sizeX, sizeY, sizeZ}.
     * Scars are positioned on its front or back face.
     */
    public static float[] cube(Part part, BodyType type) {
        boolean slim = type.slim();
        return switch (part) {
            case HEAD -> new float[]{-4, -8, -4, 8, 8, 8};
            case BODY -> new float[]{-4, 0, -2, 8, 12, 4};
            case RIGHT_ARM -> new float[]{slim ? -2 : -3, -2, -2, slim ? 3 : 4, 12, 4};
            case LEFT_ARM -> new float[]{-1, -2, -2, slim ? 3 : 4, 12, 4};
            case RIGHT_LEG, LEFT_LEG -> new float[]{-2, 0, -2, 4, 12, 4};
        };
    }

    /** The scar's centre in bone space (x, y), and its direction (dx, dy) as a unit vector. */
    public float[] line(BodyType type) {
        float[] c = cube(zone.part, type);
        float x = zone.back ? c[0] + c[3] - u * c[3] : c[0] + u * c[3];
        float y = c[1] + v * c[4];
        // As seen by the viewer: on the back of the body, the viewer's right is the bone's -x.
        float dx = (float) Math.cos(angle) * (zone.back ? -1 : 1);
        float dy = (float) Math.sin(angle);
        return new float[]{x, y, dx, dy};
    }
}
