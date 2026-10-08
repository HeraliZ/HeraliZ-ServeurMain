package com.caracrea.paint;

import java.util.Arrays;

/**
 * An ARGB image being painted, addressed in the coordinates of a square
 * texture of {@link #size()} pixels. It may store only a {@link #window()}
 * of that texture: repainting the face for a blink then allocates and
 * touches only the face, while the painters keep using texture coordinates.
 * A list of clip rectangles further restricts what gets painted.
 *
 * <p>Two textures exist per character: the skin, in the vanilla 64x64
 * layout at {@link #SCALE} pixels per texel, and the detail atlas holding
 * the faces of the 3D detail boxes.
 */
public final class Canvas {
    /** Pixels per texel, in both textures. */
    public static final int SCALE = 4;
    /** The skin: the vanilla 64x64 layout. */
    public static final int SKIN_SIZE = 64 * SCALE;
    /** The detail atlas. */
    public static final int DETAIL_SIZE = com.caracrea.model.Slot.TEXTURE * SCALE;

    /** The vanilla layout at 8 pixels per texel: used for the high-definition head. */
    public static final int HD_SCALE = 8;

    private final int size;
    private final int scale;
    private final Rect window;
    private final int[] pixels;
    private Rect[] clips;

    /** A canvas storing the whole texture, at {@link #SCALE} pixels per texel. */
    public Canvas(int size) {
        this(size, SCALE, new Rect(0, 0, size, size));
    }

    /** A canvas storing only {@code window} of a texture of {@code size} pixels. */
    public Canvas(int size, Rect window) {
        this(size, SCALE, window);
    }

    /** A canvas at {@code scale} pixels per texel, storing only {@code window}. */
    public Canvas(int size, int scale, Rect window) {
        this.size = size;
        this.scale = scale;
        this.window = window.intersect(new Rect(0, 0, size, size));
        this.pixels = new int[this.window.w() * this.window.h()];
        this.clips = new Rect[]{this.window};
    }

    public int size() {
        return size;
    }

    /** Pixels per texel: 4 for the skin and the detail atlas, 8 for the high-definition head. */
    public int scale() {
        return scale;
    }

    /** Whether anything inside {@code rect} can be painted (painters skip the faces outside). */
    public boolean touches(Rect rect) {
        for (Rect clip : clips) {
            if (!rect.intersect(clip).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public Rect window() {
        return window;
    }

    /** The stored pixels, row by row over the window. */
    public int[] pixels() {
        return pixels;
    }

    /** Whether a pixel may be painted under the current clip. */
    public boolean inClip(int x, int y) {
        for (Rect clip : clips) {
            if (clip.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /** Restricts painting to the given rectangles (none: the whole window). */
    public void setClip(Rect... rects) {
        if (rects == null || rects.length == 0 || rects[0] == null) {
            clips = new Rect[]{window};
            return;
        }
        clips = new Rect[rects.length];
        for (int i = 0; i < rects.length; i++) {
            clips[i] = rects[i].intersect(window);
        }
    }

    private int index(int x, int y) {
        return (y - window.y()) * window.w() + (x - window.x());
    }

    public int get(int x, int y) {
        return window.contains(x, y) ? pixels[index(x, y)] : 0;
    }

    public void set(int x, int y, int argb) {
        if (inClip(x, y)) {
            pixels[index(x, y)] = argb;
        }
    }

    /** Alpha-blends {@code argb} over the existing pixel. */
    public void blend(int x, int y, int argb) {
        if (Colors.alpha(argb) != 0 && inClip(x, y)) {
            int index = index(x, y);
            pixels[index] = Colors.over(pixels[index], argb);
        }
    }

    /** Blends with an extra coverage factor (0..1), for anti-aliased shapes. */
    public void blend(int x, int y, int argb, float coverage) {
        if (coverage > 0) {
            blend(x, y, coverage >= 1 ? argb : Colors.fade(argb, coverage));
        }
    }

    public void fill(Rect rect, int argb) {
        for (Rect clip : clips) {
            Rect r = rect.intersect(clip);
            for (int y = r.y(); y < r.y1(); y++) {
                Arrays.fill(pixels, index(r.x(), y), index(r.x(), y) + r.w(), argb);
            }
        }
    }

    /** Calls {@code shader} for every pixel of {@code rect} inside the clip, replacing the pixel with its result. */
    public void shade(Rect rect, Shader shader) {
        for (Rect clip : clips) {
            Rect r = rect.intersect(clip);
            for (int y = r.y(); y < r.y1(); y++) {
                int index = index(r.x(), y);
                for (int x = r.x(); x < r.x1(); x++, index++) {
                    pixels[index] = shader.shade(x, y, pixels[index]);
                }
            }
        }
    }

    /** Pixel transformation; return the new ARGB value. */
    @FunctionalInterface
    public interface Shader {
        int shade(int x, int y, int current);
    }
}
