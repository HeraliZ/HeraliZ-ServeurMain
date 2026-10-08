package com.caracrea.character;

/**
 * The animated part of a face: how open the eyes are and the shape of the
 * mouth. Quantized so that every state can be painted ahead of time; the
 * game only switches between them.
 *
 * <p>Mouth shapes follow the sounds of speech (visemes): closed for silence
 * and M / B / P, open for A, stretched for E / I, round for O, small and
 * pushed out for OU, teeth for S / F / CH.
 */
public record FaceState(int eyes, int mouth) {
    public static final int EYES_CLOSED = 0;
    public static final int EYES_HALF = 1;
    public static final int EYES_OPEN = 2;

    public static final int MOUTH_REST = 0;
    /** Lips just apart: quiet speech, most consonants. */
    public static final int MOUTH_SMALL = 1;
    /** A, spoken normally. */
    public static final int MOUTH_OPEN = 2;
    /** A, loud. */
    public static final int MOUTH_WIDE = 3;
    /** E, É, È, I: stretched sideways, teeth showing. */
    public static final int MOUTH_E = 4;
    /** O: round. */
    public static final int MOUTH_O = 5;
    /** OU: small and round, lips pushed out. */
    public static final int MOUTH_U = 6;
    /** S, Z, F, V, CH: lips apart over closed teeth. */
    public static final int MOUTH_TEETH = 7;

    public static final int MOUTH_SHAPES = 8;

    public static final FaceState NEUTRAL = new FaceState(EYES_OPEN, MOUTH_REST);

    // Per shape: opening, width, roundness of the opening, teeth, lip thickness, smile, upper lip lift.
    private static final float[] OPENING = {0, 1.25F, 2.5F, 3.75F, 1.5F, 2.7F, 1.5F, 1.0F};
    private static final float[] WIDTH = {1, 0.98F, 0.95F, 0.9F, 1.12F, 0.72F, 0.56F, 1.04F};
    private static final float[] ROUNDNESS = {0.55F, 0.55F, 0.55F, 0.5F, 0.28F, 0.5F, 0.5F, 0.22F};
    private static final int[] TEETH = {0, 0, 1, 1, 1, 0, 0, 2};
    private static final float[] LIPS = {1, 1, 0.95F, 0.92F, 0.85F, 1.1F, 1.28F, 0.92F};
    private static final float[] SMILE = {0, 0, 0, 0, 0.35F, -0.1F, 0, 0.15F};
    private static final float[] LIFT = {0, 0.15F, 0.2F, 0.22F, 0.35F, 0.42F, 0.45F, 0.45F};

    public FaceState {
        eyes = Math.max(EYES_CLOSED, Math.min(EYES_OPEN, eyes));
        mouth = Math.max(0, Math.min(MOUTH_SHAPES - 1, mouth));
    }

    /** 1 for fully open eyes, ~0 for closed eyes. */
    public float eyeOpenness() {
        return switch (eyes) {
            case EYES_CLOSED -> 0.08F;
            case EYES_HALF -> 0.5F;
            default -> 1.0F;
        };
    }

    /** Gap between the lips at the middle of the mouth, in face units. */
    public float mouthOpening() {
        return OPENING[mouth];
    }

    /** Width of the mouth relative to its resting width. */
    public float mouthWidth() {
        return WIDTH[mouth];
    }

    /** Shape of the opening across the mouth: 0.5 is an ellipse, lower is flatter (a wide slot). */
    public float mouthRoundness() {
        return ROUNDNESS[mouth];
    }

    /** 0: dark inside; 1: upper teeth at the top of the opening; 2: closed teeth filling it. */
    public int mouthTeeth() {
        return TEETH[mouth];
    }

    /** Lip thickness relative to rest (thicker when pushed out, thinner when stretched). */
    public float lipScale() {
        return LIPS[mouth];
    }

    /** Share of the opening taken by the upper lip moving up (round mouths open both ways). */
    public float lipLift() {
        return LIFT[mouth];
    }

    /** Extra upward curve of the corners. */
    public float mouthSmile() {
        return SMILE[mouth];
    }
}
