package com.dayzhotbar.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Blood on the frame of the weapon in hand: a splatter on the top edge with a
 * drip running down and droplets thrown around it, a smaller stain with its
 * own drip on the left edge, a smear on the bottom edge and a few loose drops.
 * Painted as three masks, tinted when drawn: the blood itself, a darker rim
 * where it dried at the edges (small drops are all rim), and a wet shine.
 */
final class Blood {
    private interface Shape {
        /** How far (x, y) is inside the shape, in GUI pixels; negative outside. */
        float depth(float x, float y);
    }

    private record Blot(float cx, float cy, float r, float rough, int seed) implements Shape {
        public float depth(float x, float y) {
            float dx = x - cx;
            float dy = y - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > r * 2) {
                return -r;
            }
            float c = dist == 0 ? 1 : dx / dist;
            float s = dist == 0 ? 0 : dy / dist;
            float lobes = Noise.value2(c * 1.6f + 7, s * 1.6f + 7, seed) - 0.5f;
            float bumps = Noise.value2(c * 4 + 3, s * 4 + 3, seed + 1) - 0.5f;
            return r * (1 + rough * (2 * lobes + 0.8f * bumps)) - dist;
        }
    }

    /** A drop, stretched into a tail along (dx, dy) when it flew. */
    private record Drop(float cx, float cy, float r, float dx, float dy, float stretch) implements Shape {
        Drop(float cx, float cy, float r) {
            this(cx, cy, r, 1, 0, 1);
        }

        public float depth(float x, float y) {
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float along = ((x - cx) * dx + (y - cy) * dy) / len;
            float across = ((y - cy) * dx - (x - cx) * dy) / len;
            if (along > 0) {
                along /= stretch;
            }
            return r - (float) Math.sqrt(along * along + across * across);
        }
    }

    /** Blood running down from (x0, y0), thinning, ending in a bead. */
    private record Drip(float x0, float y0, float x1, float y1, float w0, float w1, float bead, int seed) implements Shape {
        public float depth(float x, float y) {
            float dx = x1 - x0;
            float dy = y1 - y0;
            float t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)));
            float wobble = (Noise.value1(t * 3, seed) - 0.5f) * 0.35f;
            float px = x0 + dx * t + wobble;
            float py = y0 + dy * t;
            float stream = (w0 + (w1 - w0) * t) / 2 - (float) Math.hypot(x - px, y - py);
            float end = bead - (float) Math.hypot(x - x1, y - y1 - bead * 0.3f);
            return Math.max(stream, end);
        }
    }

    private final List<Shape> shapes = new ArrayList<>();
    /** Centre and radius of the shapes catching the light. */
    private final List<float[]> shiny = new ArrayList<>();

    /** Corners of the slot: top left, top right, bottom right, bottom left. */
    Blood(float[] xs, float[] ys) {
        // Splatter near the top right corner, with its drip and the droplets it threw.
        float ax = lerp(xs[0], xs[1], 0.74f);
        float ay = ys[0] - 0.1f;
        add(new Blot(ax, ay, 1.7f, 0.35f, 51), true);
        add(new Drip(ax - 0.5f, ay + 0.8f, ax - 0.7f, ay + 5.2f, 1.1f, 0.55f, 0.7f, 52), false);
        shiny.add(new float[]{ax - 0.7f, ay + 5.2f, 0.7f});
        add(new Drop(ax + 2.9f, ay - 1.3f, 0.5f, 0.9f, -0.45f, 1.8f), false);
        add(new Drop(ax + 4.1f, ay + 0.2f, 0.35f, 1, 0.05f, 1.6f), false);
        add(new Drop(ax + 0.9f, ay - 2.6f, 0.4f, 0.3f, -1, 1.7f), false);
        add(new Drop(ax - 2.4f, ay - 1.5f, 0.3f, -0.85f, -0.5f, 1.5f), false);
        add(new Drop(ax + 2.2f, ay - 2.7f, 0.25f), false);
        add(new Drop(ax + 5, ay - 1.4f, 0.22f), false);

        // Smaller stain on the left edge, running down.
        float bx = lerp(xs[3], xs[0], 0.38f);
        float by = lerp(ys[3], ys[0], 0.38f);
        add(new Blot(bx, by, 1.15f, 0.45f, 61), false);
        add(new Drip(bx + 0.2f, by + 0.6f, bx + 0.3f, by + 3.4f, 0.8f, 0.45f, 0.5f, 62), false);
        add(new Drop(bx - 1.8f, by - 0.9f, 0.3f, -1, -0.4f, 1.6f), false);
        add(new Drop(bx - 2.6f, by + 0.6f, 0.25f), false);

        // Smear along the bottom edge, and loose drops.
        float cx = lerp(xs[3], xs[2], 0.62f);
        float cy = ys[3] + 0.1f;
        add(new Drop(cx, cy, 0.75f, 1, 0, 3), false);
        add(new Drop(cx + 3.6f, cy + 1, 0.3f), false);
        add(new Drop(lerp(xs[2], xs[1], 0.25f) + 0.9f, lerp(ys[2], ys[1], 0.25f), 0.4f), false);
    }

    private void add(Shape shape, boolean catchesLight) {
        shapes.add(shape);
        if (catchesLight && shape instanceof Blot blot) {
            shiny.add(new float[]{blot.cx(), blot.cy(), blot.r()});
        }
    }

    private float depth(float x, float y) {
        float d = -Float.MAX_VALUE;
        for (Shape shape : shapes) {
            d = Math.max(d, shape.depth(x, y));
        }
        return d;
    }

    void body(Chalk out) {
        paint(out, 0);
    }

    void rim(Chalk out) {
        paint(out, 1);
    }

    void shine(Chalk out) {
        paint(out, 2);
    }

    private void paint(Chalk out, int layer) {
        float s = out.scale;
        for (int y = 0; y < out.height; y++) {
            for (int x = 0; x < out.width; x++) {
                float gx = (x + 0.5f) / s;
                float gy = (y + 0.5f) / s;
                float d = depth(gx, gy);
                float cover = clamp(d * s + 0.5f);
                if (cover <= 0) {
                    continue;
                }
                float a;
                if (layer == 0) {
                    a = cover * (0.86f + 0.14f * Noise.value2(x * 0.3f, y * 0.3f, 71));
                } else if (layer == 1) {
                    float t = clamp(d / 0.55f);
                    a = cover * (1 - t * t * (3 - 2 * t)) * 0.9f;
                } else {
                    a = 0;
                    for (float[] c : shiny) {
                        float hx = c[0] - 0.35f * c[2];
                        float hy = c[1] - 0.4f * c[2];
                        float hr = 0.3f * c[2];
                        a = Math.max(a, clamp((hr - (float) Math.hypot(gx - hx, gy - hy)) * s + 0.5f));
                    }
                    a *= cover;
                }
                out.alpha[y * out.width + x] = a;
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }
}
