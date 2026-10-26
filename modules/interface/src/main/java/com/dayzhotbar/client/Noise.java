package com.dayzhotbar.client;

/** Hash-based value noise, the same on every run, for the chalk and the blood. */
final class Noise {
    private Noise() {
    }

    static float hash(int a, int b, int seed) {
        int h = seed * 0x27D4EB2D ^ a * 0x165667B1 ^ b * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static float value1(float t, int seed) {
        int i = (int) Math.floor(t);
        float f = t - i;
        f = f * f * (3 - 2 * f);
        return lerp(hash(i, 0, seed), hash(i + 1, 0, seed), f);
    }

    static float value2(float x, float y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float top = lerp(hash(ix, iy, seed), hash(ix + 1, iy, seed), fx);
        float bottom = lerp(hash(ix, iy + 1, seed), hash(ix + 1, iy + 1, seed), fx);
        return lerp(top, bottom, fy);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
