package com.caracrea.paint;

/** ARGB colour helpers. All colours are 0xAARRGGBB ints. */
public final class Colors {
    private Colors() {
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int alpha(int c) {
        return c >>> 24;
    }

    public static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int withAlpha(int c, int a) {
        return (clamp(a) << 24) | (c & 0xFFFFFF);
    }

    /** Same colour with its alpha multiplied by {@code f} (0..1). */
    public static int fade(int c, float f) {
        return withAlpha(c, Math.round(alpha(c) * clamp01(f)));
    }

    /** Multiplies the RGB channels: < 1 darkens, > 1 lightens. Alpha is kept. */
    public static int shade(int c, float f) {
        return argb(alpha(c), Math.round(red(c) * f), Math.round(green(c) * f), Math.round(blue(c) * f));
    }

    /** Moves the colour towards white by {@code t} (0..1). */
    public static int lighten(int c, float t) {
        return lerp(c, withAlpha(0xFFFFFF, alpha(c)), t);
    }

    /** Moves the colour towards black by {@code t} (0..1). */
    public static int darken(int c, float t) {
        return lerp(c, withAlpha(0, alpha(c)), t);
    }

    /** Linear interpolation of all four channels. */
    public static int lerp(int a, int b, float t) {
        t = clamp01(t);
        return argb(Math.round(alpha(a) + (alpha(b) - alpha(a)) * t),
                Math.round(red(a) + (red(b) - red(a)) * t),
                Math.round(green(a) + (green(b) - green(a)) * t),
                Math.round(blue(a) + (blue(b) - blue(a)) * t));
    }

    /** Mixes the RGB of {@code tint} into {@code c} by {@code t}, keeping the alpha of {@code c}. */
    public static int tint(int c, int tint, float t) {
        return withAlpha(lerp(c, tint, t), alpha(c));
    }

    /** Source-over compositing of {@code src} onto {@code dst}. */
    public static int over(int dst, int src) {
        int sa = alpha(src);
        if (sa == 255) {
            return src;
        }
        if (sa == 0) {
            return dst;
        }
        int da = alpha(dst);
        float fs = sa / 255F;
        float fd = da / 255F * (1 - fs);
        float oa = fs + fd;
        if (oa <= 0) {
            return 0;
        }
        int r = Math.round((red(src) * fs + red(dst) * fd) / oa);
        int g = Math.round((green(src) * fs + green(dst) * fd) / oa);
        int b = Math.round((blue(src) * fs + blue(dst) * fd) / oa);
        return argb(Math.round(oa * 255), r, g, b);
    }

    /** Perceived lightness 0..1. */
    public static float luminance(int c) {
        return (0.299F * red(c) + 0.587F * green(c) + 0.114F * blue(c)) / 255F;
    }

    /** Converts 0xAARRGGBB to the 0xAABBGGRR layout of Minecraft's NativeImage. */
    public static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    public static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(1, v);
    }
}
