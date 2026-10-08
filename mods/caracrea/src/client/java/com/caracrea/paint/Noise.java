package com.caracrea.paint;

/** Deterministic hash and value noise, so every client paints identical skins. */
public final class Noise {
    private Noise() {
    }

    /** White noise in [0, 1) for an integer cell. */
    public static float hash(int x, int y, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** White noise in [-1, 1). */
    public static float signed(int x, int y, long seed) {
        return hash(x, y, seed) * 2 - 1;
    }

    /** Smooth value noise in [0, 1]; {@code scale} is the size of a cell in pixels. */
    public static float smooth(float x, float y, float scale, long seed) {
        float fx = x / scale;
        float fy = y / scale;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = fade(fx - x0);
        float ty = fade(fy - y0);
        float a = hash(x0, y0, seed);
        float b = hash(x0 + 1, y0, seed);
        float c = hash(x0, y0 + 1, seed);
        float d = hash(x0 + 1, y0 + 1, seed);
        float top = a + (b - a) * tx;
        float bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    /** Two octaves of smooth noise, in [0, 1]. */
    public static float fbm(float x, float y, float scale, long seed) {
        return (smooth(x, y, scale, seed) * 2 + smooth(x, y, scale / 2, seed + 77)) / 3;
    }

    private static float fade(float t) {
        return t * t * (3 - 2 * t);
    }
}
