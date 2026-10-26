package fr.heraliz.casino.client.render.scene;

/**
 * Courbes d'animation.
 */
public final class Ease {
    private Ease() {
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 0 avant start, 1 apres end, lineaire entre les deux. */
    public static float range(float t, float start, float end) {
        return clamp01((t - start) / (end - start));
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float inQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float outQuad(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t);
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float inOutSine(float t) {
        t = clamp01(t);
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
    }

    public static float outBack(float t) {
        return outBack(t, 1.70158f);
    }

    public static float outBack(float t, float c1) {
        t = clamp01(t);
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float inBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        return c3 * t * t * t - c1 * t * t;
    }

    public static float inOutBack(float t) {
        t = clamp01(t);
        float c2 = 1.70158f * 1.525f;
        return t < 0.5f
                ? (float) (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                : (float) (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
    }

    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        double c4 = (2 * Math.PI) / 3;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
    }

    public static float outBounce(float t) {
        t = clamp01(t);
        float n1 = 7.5625f, d1 = 2.75f;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        }
        if (t < 2.5 / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        }
        t -= 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }

    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Impulsion amortie : 0 a t=0, oscille puis s'eteint (ressort). */
    public static float spring(float t, float freq, float damp) {
        if (t <= 0) return 0;
        return (float) (Math.sin(t * freq) * Math.exp(-t * damp));
    }

    /** Cloche 0 -> 1 -> 0 sur [a, b]. */
    public static float bell(float t, float a, float b) {
        if (t <= a || t >= b) return 0;
        return (float) Math.sin(Math.PI * (t - a) / (b - a));
    }

    /** Couleur rgb interpolee. */
    public static int mix(int c0, int c1, float k) {
        k = clamp01(k);
        int r = (int) lerp((c0 >> 16) & 255, (c1 >> 16) & 255, k);
        int g = (int) lerp((c0 >> 8) & 255, (c1 >> 8) & 255, k);
        int b = (int) lerp(c0 & 255, c1 & 255, k);
        return (r << 16) | (g << 8) | b;
    }

    /** Couleur rgb multipliee (assombrie / eclaircie, bornee). */
    public static int scale(int c, float k) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * k);
        int g = (int) Math.min(255, ((c >> 8) & 255) * k);
        int b = (int) Math.min(255, (c & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** Hachage deterministe -> [0,1). */
    public static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
