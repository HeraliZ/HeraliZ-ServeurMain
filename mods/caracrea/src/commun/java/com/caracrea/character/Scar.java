package com.caracrea.character;

import java.util.ArrayList;
import java.util.List;

/**
 * A scar placed by the player on the front or the back of a body part: one stroke, straight or curved, whose size
 * and bend are limited, and which never comes close to another scar. A single smooth stroke of limited length and
 * bend, kept apart from the others, cannot draw a symbol or an obscene shape (crosses, letters, closed shapes...).
 * The rule is applied wherever an appearance is built ({@link #sanitize}, from {@link Appearance#of}): on the server
 * too, so a modified client cannot get around it.
 *
 * <p>Its position is a point of that face (0..1 across, 0..1 down, as seen by someone looking at that side): the
 * middle of the stroke. {@code angle} is the direction of the stroke there, {@code bend} how much it turns from one
 * end to the other (radians, signed). Scars are drawn on bare skin only, so clothes hide them.
 */
public record Scar(Zone zone, float u, float v, float angle, float length, float bend) {
    /** Number of scars a character can have. */
    public static final int COUNT = 3;
    /** Steps of the position across and down a face. */
    public static final int POSITIONS = 64;
    /** Orientation steps over half a turn (5 degrees each): bending to either side covers the other half. */
    public static final int ANGLES = 36;
    /** Lengths in model units (the head is 8 units wide): from a nick to half the width of the face. */
    public static final float MIN_LENGTH = 1.0F;
    public static final float MAX_LENGTH = 4.0F;
    /** Length steps, from {@link #MIN_LENGTH} to {@link #MAX_LENGTH}. */
    public static final int SIZES = 7;
    /** Bend steps on each side of straight; the most bent scar turns by a quarter of a turn (a shallow arc). */
    public static final int BENDS = 4;
    public static final float MAX_BEND = (float) (Math.PI / 2);
    /** Smallest gap between two scars seen from the same side, in model units: they can never touch, cross or join. */
    public static final float MIN_GAP = 1.5F;
    /** Straight segments a stroke is drawn with. */
    public static final int SEGMENTS = 16;

    /** Index of each field in {@link #traits(int)}. */
    public static final int ZONE = 0, X = 1, Y = 2, ANGLE = 3, SIZE = 4, BEND = 5;

    /** Scars saved before 1.3.0 (size 0): straight, 8 orientations and 3 lengths packed in the angle byte. */
    private static final int OLD_ANGLES = 8;
    private static final float[] OLD_LENGTHS = {1.3F, 2.2F, 3.2F};

    private static final Trait[][] TRAITS = new Trait[COUNT][];

    static {
        for (int i = 0; i < COUNT; i++) {
            int first = Trait.SCAR1_ZONE.ordinal() + i * 4;
            int extra = Trait.SCAR1_SIZE.ordinal() + i * 2;
            TRAITS[i] = new Trait[]{Trait.VALUES[first], Trait.VALUES[first + 1], Trait.VALUES[first + 2], Trait.VALUES[first + 3],
                    Trait.VALUES[extra], Trait.VALUES[extra + 1]};
        }
    }

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

    /** The six traits of scar {@code index} (0..2): zone, x, y, angle, size, bend (see {@link #ZONE}...). */
    public static Trait[] traits(int index) {
        return TRAITS[index].clone();
    }

    /** Number of values of a scar trait. */
    public static int count(Trait trait) {
        for (Trait[] t : TRAITS) {
            if (trait == t[ZONE]) {
                return Zone.VALUES.length + 1;   // 0 = no scar
            }
            if (trait == t[X] || trait == t[Y]) {
                return POSITIONS;
            }
            if (trait == t[ANGLE]) {
                return ANGLES;                   // also holds the packed shape of the old scars (0..23)
            }
            if (trait == t[SIZE]) {
                return SIZES + 1;                // 0 = old scar
            }
            if (trait == t[BEND]) {
                return 2 * BENDS + 1;            // BENDS = straight
            }
        }
        throw new IllegalArgumentException("Not a scar trait: " + trait);
    }

    /** Length in model units of a size step (1..{@link #SIZES}). */
    public static float lengthOf(int size) {
        return MIN_LENGTH + (Math.max(1, Math.min(SIZES, size)) - 1) * (MAX_LENGTH - MIN_LENGTH) / (SIZES - 1);
    }

    /** Bend in radians of a bend step (0..2 * {@link #BENDS}). */
    public static float bendOf(int step) {
        return (Math.max(0, Math.min(2 * BENDS, step)) - BENDS) * MAX_BEND / BENDS;
    }

    /** Scar {@code index} of an appearance, or null when the slot is empty. */
    public static Scar get(Appearance appearance, int index) {
        Trait[] t = TRAITS[index];
        return decode(appearance.get(t[ZONE]), appearance.get(t[X]), appearance.get(t[Y]), appearance.get(t[ANGLE]),
                appearance.get(t[SIZE]), appearance.get(t[BEND]));
    }

    private static Scar decode(int zone, int x, int y, int angle, int size, int bend) {
        if (zone <= 0 || zone > Zone.VALUES.length) {
            return null;
        }
        float u = (x + 0.5F) / POSITIONS;
        float v = (y + 0.5F) / POSITIONS;
        if (size == 0) {
            // Saved before 1.3.0: a straight scar.
            return new Scar(Zone.VALUES[zone - 1], u, v, (float) ((angle % OLD_ANGLES) * Math.PI / OLD_ANGLES),
                    OLD_LENGTHS[Math.min(OLD_LENGTHS.length - 1, angle / OLD_ANGLES)], 0);
        }
        return new Scar(Zone.VALUES[zone - 1], u, v, (float) ((angle % ANGLES) * Math.PI / ANGLES), lengthOf(size), bendOf(bend));
    }

    /** The scars of a character (empty slots skipped). */
    public static List<Scar> of(Appearance appearance) {
        List<Scar> scars = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            Scar scar = get(appearance, i);
            if (scar != null) {
                scars.add(scar);
            }
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

    /** Where the bone of a part sits in the standing pose (the vanilla pivots). */
    private static float[] pivot(Part part, BodyType type) {
        float arm = type.slim() ? 2.5F : 2;
        return switch (part) {
            case HEAD, BODY -> new float[]{0, 0};
            case RIGHT_ARM -> new float[]{-5, arm};
            case LEFT_ARM -> new float[]{5, arm};
            case RIGHT_LEG -> new float[]{-1.9F, 12};
            case LEFT_LEG -> new float[]{1.9F, 12};
        };
    }

    /**
     * The stroke in bone space: {@link #SEGMENTS} + 1 points {x0, y0, x1, y1...} from one end to the other, an arc
     * of the scar's length whose middle is at (u, v) of the face.
     */
    public float[] stroke(BodyType type) {
        float[] c = cube(zone.part, type);
        float cx = zone.back ? c[0] + c[3] - u * c[3] : c[0] + u * c[3];
        float cy = c[1] + v * c[4];
        // As seen by the viewer: on the back of the body, the viewer's right is the bone's -x.
        float flip = zone.back ? -1 : 1;
        float curvature = bend / length;
        float[] points = new float[2 * (SEGMENTS + 1)];
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = (i / (float) SEGMENTS - 0.5F) * length;
            float px;
            float py;
            if (Math.abs(curvature) < 1e-4F) {
                px = s * (float) Math.cos(angle);
                py = s * (float) Math.sin(angle);
            } else {
                float t = angle + curvature * s;
                px = ((float) Math.sin(t) - (float) Math.sin(angle)) / curvature;
                py = ((float) Math.cos(angle) - (float) Math.cos(t)) / curvature;
            }
            points[2 * i] = cx + px * flip;
            points[2 * i + 1] = cy + py;
        }
        return points;
    }

    /** The stroke in the standing pose, seen from its side: neighbouring parts share these coordinates. */
    private float[] inView(BodyType type) {
        float[] points = stroke(type);
        float[] p = pivot(zone.part, type);
        for (int i = 0; i < points.length; i += 2) {
            points[i] += p[0];
            points[i + 1] += p[1];
        }
        return points;
    }

    /** Whether two scars keep {@link #MIN_GAP} between them (always, when they are on opposite sides of the body). */
    public static boolean apart(Scar a, Scar b, BodyType type) {
        if (a.zone.back != b.zone.back) {
            return true;
        }
        return distance(a.inView(type), b.inView(type)) >= MIN_GAP;
    }

    /** Whether every scar of the appearance keeps its distance from the others. */
    public static boolean valid(Appearance appearance) {
        List<Scar> scars = of(appearance);
        for (int i = 0; i < scars.size(); i++) {
            for (int j = i + 1; j < scars.size(); j++) {
                if (!apart(scars.get(i), scars.get(j), appearance.bodyType())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Removes every scar that comes too close to one kept before it. {@code values} are the clamped values of an
     * appearance being built (see {@link Appearance#of}).
     */
    static void sanitize(int[] values) {
        BodyType type = BodyType.of(values[Trait.BODY_TYPE.ordinal()]);
        List<Scar> kept = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            Trait[] t = TRAITS[i];
            Scar scar = decode(values[t[ZONE].ordinal()], values[t[X].ordinal()], values[t[Y].ordinal()],
                    values[t[ANGLE].ordinal()], values[t[SIZE].ordinal()], values[t[BEND].ordinal()]);
            if (scar == null) {
                continue;
            }
            boolean apart = true;
            for (Scar other : kept) {
                apart &= apart(scar, other, type);
            }
            if (apart) {
                kept.add(scar);
            } else {
                for (Trait trait : t) {
                    values[trait.ordinal()] = 0;
                }
            }
        }
    }

    /** Smallest distance between two polylines {x0, y0, x1, y1...}. */
    static float distance(float[] a, float[] b) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i + 3 < a.length; i += 2) {
            for (int j = 0; j + 3 < b.length; j += 2) {
                best = Math.min(best, segments(a[i], a[i + 1], a[i + 2], a[i + 3], b[j], b[j + 1], b[j + 2], b[j + 3]));
                if (best == 0) {
                    return 0;
                }
            }
        }
        return best;
    }

    /** Distance between segments AB and CD (0 when they cross). */
    private static float segments(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float d1 = cross(cx - ax, cy - ay, bx - ax, by - ay);
        float d2 = cross(dx - ax, dy - ay, bx - ax, by - ay);
        float d3 = cross(ax - cx, ay - cy, dx - cx, dy - cy);
        float d4 = cross(bx - cx, by - cy, dx - cx, dy - cy);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return 0;
        }
        return Math.min(Math.min(point(ax, ay, cx, cy, dx, dy), point(bx, by, cx, cy, dx, dy)),
                Math.min(point(cx, cy, ax, ay, bx, by), point(dx, dy, ax, ay, bx, by)));
    }

    private static float cross(float x0, float y0, float x1, float y1) {
        return x0 * y1 - y0 * x1;
    }

    /** Distance from point P to segment AB. */
    private static float point(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax;
        float vy = by - ay;
        float len = vx * vx + vy * vy;
        float t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / len));
        float x = ax + t * vx - px;
        float y = ay + t * vy - py;
        return (float) Math.sqrt(x * x + y * y);
    }
}
