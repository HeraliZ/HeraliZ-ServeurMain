package com.dayzhotbar.client;

/**
 * Procedural chalk painted into an alpha mask, one value per screen pixel:
 * hand-drawn lines (ragged edges, gaps where the chalk skips, a little dust,
 * ends that overshoot the corners) and smudged fills. Coordinates are in GUI
 * pixels; {@code scale} screen pixels make one GUI pixel, so the grain is
 * always one screen pixel, sharp at every GUI scale.
 */
final class Chalk {
    final int width;
    final int height;
    final float[] alpha;
    final float scale;

    Chalk(int width, int height, float scale) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.alpha = new float[width * height];
    }

    /** A chalk line from (x0, y0) to (x1, y1), drawn a little imprecisely, as by hand. */
    void line(float x0, float y0, float x1, float y1, float thickness, float pressure, int seed) {
        float s = scale;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / len;
        float uy = dy / len;
        // Overshoot (or stop short) at each end, start and end a bit off the line, bow slightly.
        float over0 = lerp(-0.3f, 1.3f, Noise.hash(seed, 1, 0));
        float over1 = lerp(-0.3f, 1.3f, Noise.hash(seed, 2, 0));
        float off0 = (Noise.hash(seed, 3, 0) - 0.5f) * 0.5f;
        float off1 = (Noise.hash(seed, 4, 0) - 0.5f) * 0.5f;
        float bow = (Noise.hash(seed, 5, 0) - 0.5f) * 0.6f * s;
        float ax = (x0 - ux * over0 - uy * off0) * s;
        float ay = (y0 - uy * over0 + ux * off0) * s;
        float bx = (x1 + ux * over1 - uy * off1) * s;
        float by = (y1 + uy * over1 + ux * off1) * s;
        dx = bx - ax;
        dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        ux = dx / length;
        uy = dy / length;
        float nx = -uy;
        float ny = ux;

        float half = thickness * s / 2;
        float reach = half * 1.4f + 3;
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, bx) - reach));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(ax, bx) + reach));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, by) - reach));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(ay, by) + reach));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float rx = x + 0.5f - ax;
                float ry = y + 0.5f - ay;
                float along = rx * ux + ry * uy;
                float t = along / length;
                float side = rx * nx + ry * ny - bow * 4 * t * (1 - t);
                float across = Math.abs(side);
                float beyond = along < 0 ? -along : Math.max(0, along - length);

                float w = half * (0.8f + 0.45f * Noise.value1(along / (3 * s), seed + 10));
                float ragged = (Noise.value2(x * 0.7f, y * 0.7f, seed + 11) - 0.5f) * 1.8f;
                float cover = clamp(w + ragged - across + 0.5f) * clamp(1 - beyond / 1.5f + ragged * 0.4f);

                float c;
                if (cover > 0) {
                    // Pressure varies along the stroke and fades at the ends; the chalk skips where it is light.
                    float ends = clamp(Math.min(along, length - along) / (2.5f * s));
                    float press = pressure * (0.7f + 0.5f * Noise.value1(along / (7 * s), seed + 12)) * (0.55f + 0.45f * ends);
                    // Streaks along the stroke, where the chalk drags over the grain of the board.
                    float streak = Noise.value2(along / 6, (side + 64) / 0.9f, seed + 14);
                    float tooth = 0.4f * Noise.hash(x, y, seed + 13) + 0.6f * streak;
                    float solid = smooth(1.05f - press - 0.12f, 1.05f - press + 0.12f, tooth);
                    c = cover * (0.12f + 0.88f * solid) * (0.8f + 0.2f * Noise.hash(x, y, seed + 15));
                } else {
                    // Dust next to the line.
                    float d = across - w;
                    c = d < 2.5f && Noise.hash(x, y, seed + 16) > 0.9f ? 0.3f * pressure * (1 - d / 2.5f) : 0;
                }
                if (c > 0) {
                    int i = y * width + x;
                    alpha[i] = 1 - (1 - alpha[i]) * (1 - c);
                }
            }
        }
    }

    /** A smudged fill of the convex polygon (xs, ys), corners in order. */
    void fill(float[] xs, float[] ys, float density, int seed) {
        float s = scale;
        int n = xs.length;
        float minXf = Float.MAX_VALUE, maxXf = -Float.MAX_VALUE, minYf = Float.MAX_VALUE, maxYf = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minXf = Math.min(minXf, xs[i] * s);
            maxXf = Math.max(maxXf, xs[i] * s);
            minYf = Math.min(minYf, ys[i] * s);
            maxYf = Math.max(maxYf, ys[i] * s);
        }
        float orientation = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            orientation += xs[i] * ys[j] - xs[j] * ys[i];
        }
        for (int y = Math.max(0, (int) minYf); y <= Math.min(height - 1, (int) maxYf); y++) {
            for (int x = Math.max(0, (int) minXf); x <= Math.min(width - 1, (int) maxXf); x++) {
                float px = (x + 0.5f) / s;
                float py = (y + 0.5f) / s;
                boolean inside = true;
                for (int i = 0; i < n && inside; i++) {
                    int j = (i + 1) % n;
                    float cross = (xs[j] - xs[i]) * (py - ys[i]) - (ys[j] - ys[i]) * (px - xs[i]);
                    inside = cross * orientation >= 0;
                }
                if (inside) {
                    // Wiped-board look: broad blotches, faint diagonal wipe marks, fine grain.
                    float blotch = Noise.value2(px / 4, py / 4, seed);
                    float wipe = Noise.value1((px + py * 0.6f) * 0.9f, seed + 1);
                    float grain = Noise.hash(x, y, seed + 2);
                    float a = density * (0.78f + 0.3f * blotch + 0.1f * wipe - 0.08f * grain);
                    int i = y * width + x;
                    alpha[i] = 1 - (1 - alpha[i]) * (1 - clamp(a));
                }
            }
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smooth(float edge0, float edge1, float v) {
        float t = clamp((v - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }
}
