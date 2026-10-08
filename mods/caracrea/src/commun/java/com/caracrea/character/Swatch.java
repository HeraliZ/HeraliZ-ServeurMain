package com.caracrea.character;

/** A named colour of a palette (0xRRGGBB). */
public record Swatch(String id, int rgb) {
    public static Swatch of(String id, int r, int g, int b) {
        return new Swatch(id, (r << 16) | (g << 8) | b);
    }

    public int argb() {
        return 0xFF000000 | rgb;
    }
}
