package com.caracrea.paint;

import com.caracrea.character.BodyType;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

/**
 * Paints in the local coordinates of one face of a body part, with optional
 * horizontal mirroring (to draw the second eye with the same code) and
 * anti-aliased sampling for curved shapes. Everything outside the face is
 * dropped, so painters never bleed into neighbouring faces.
 *
 * <p>Local coordinates are always in units of the 4x skin (the front of the
 * head is 32 units wide), whatever the resolution of the canvas: on the 8x
 * high-definition head each unit covers 2x2 pixels, and {@link #sample}
 * renders curves at the full resolution.
 */
public final class Pen {
    private final Canvas canvas;
    private final Rect face;
    private final boolean mirror;
    /** Pixels per unit. */
    private final int f;

    public Pen(Canvas canvas, Rect face) {
        this(canvas, face, false);
    }

    private Pen(Canvas canvas, Rect face, boolean mirror) {
        this.canvas = canvas;
        this.face = face;
        this.mirror = mirror;
        this.f = Math.max(1, canvas.scale() / Canvas.SCALE);
    }

    /** A pen on a face of a body part, at the canvas's resolution. */
    public static Pen of(Canvas canvas, Part part, Face face, BodyType type) {
        return new Pen(canvas, SkinLayout.rect(part, face, type, canvas.scale()));
    }

    /** The face's rectangle in canvas pixels. */
    public Rect face() {
        return face;
    }

    /** Width of the face in units. */
    public int width() {
        return face.w() / f;
    }

    public int height() {
        return face.h() / f;
    }

    /** Position of the face on the texture in units (for patterns continuous across faces). */
    public int ux() {
        return face.x() / f;
    }

    public int uy() {
        return face.y() / f;
    }

    /** Canvas rectangle of a part of the face given in units. */
    public Rect region(int lx, int ly, int w, int h) {
        return face.sub(lx * f, ly * f, w * f, h * f);
    }

    /** A pen drawing the same face flipped left/right. */
    public Pen mirrored() {
        return new Pen(canvas, face, !mirror);
    }

    public boolean isMirrored() {
        return mirror;
    }

    /** Canvas x of a pixel column of the face. */
    private int cx(int px) {
        return face.x() + (mirror ? face.w() - 1 - px : px);
    }

    private boolean visible() {
        return canvas.touches(face);
    }

    public int get(int lx, int ly) {
        if (lx < 0 || ly < 0 || lx >= width() || ly >= height()) {
            return 0;
        }
        return canvas.get(cx(lx * f), face.y() + ly * f);
    }

    public void set(int lx, int ly, int argb) {
        if (lx < 0 || ly < 0 || lx >= width() || ly >= height()) {
            return;
        }
        for (int dy = 0; dy < f; dy++) {
            for (int dx = 0; dx < f; dx++) {
                canvas.set(cx(lx * f + dx), face.y() + ly * f + dy, argb);
            }
        }
    }

    public void blend(int lx, int ly, int argb) {
        if (lx < 0 || ly < 0 || lx >= width() || ly >= height()) {
            return;
        }
        for (int dy = 0; dy < f; dy++) {
            for (int dx = 0; dx < f; dx++) {
                canvas.blend(cx(lx * f + dx), face.y() + ly * f + dy, argb);
            }
        }
    }

    public void blend(int lx, int ly, int argb, float coverage) {
        if (coverage > 0) {
            blend(lx, ly, coverage >= 1 ? argb : Colors.fade(argb, coverage));
        }
    }

    public void fillRect(int lx, int ly, int w, int h, int argb) {
        for (int y = ly; y < ly + h; y++) {
            for (int x = lx; x < lx + w; x++) {
                set(x, y, argb);
            }
        }
    }

    public void blendRect(int lx, int ly, int w, int h, int argb) {
        for (int y = ly; y < ly + h; y++) {
            for (int x = lx; x < lx + w; x++) {
                blend(x, y, argb);
            }
        }
    }

    /** Transforms every pixel of the face; the shader gets the unit the pixel belongs to. */
    public void shade(LocalShader shader) {
        shade(0, 0, width(), height(), shader);
    }

    /** Transforms the pixels of a part of the face given in units. */
    public void shade(int lx, int ly, int w, int h, LocalShader shader) {
        if (!visible()) {
            return;
        }
        int x0 = Math.max(0, lx * f);
        int y0 = Math.max(0, ly * f);
        int x1 = Math.min(face.w(), (lx + w) * f);
        int y1 = Math.min(face.h(), (ly + h) * f);
        for (int py = y0; py < y1; py++) {
            int y = face.y() + py;
            for (int px = x0; px < x1; px++) {
                int x = cx(px);
                if (canvas.inClip(x, y)) {
                    canvas.set(x, y, shader.shade(px / f, py / f, canvas.get(x, y)));
                }
            }
        }
    }

    /** Transforms every pixel of the face with smooth coordinates (the pixel centre, in units). */
    public void shadeSmooth(SmoothShader shader) {
        if (!visible()) {
            return;
        }
        for (int py = 0; py < face.h(); py++) {
            int y = face.y() + py;
            float fy = (py + 0.5F) / f;
            for (int px = 0; px < face.w(); px++) {
                int x = cx(px);
                if (canvas.inClip(x, y)) {
                    canvas.set(x, y, shader.shade((px + 0.5F) / f, fy, canvas.get(x, y)));
                }
            }
        }
    }

    /**
     * Anti-aliased drawing: {@code sampler} gives the colour (with alpha, 0 for
     * nothing) at any point of the given bounding box (in units); each pixel
     * takes the average of four samples and is blended over the face.
     */
    public void sample(float x0, float y0, float x1, float y1, Sampler sampler) {
        if (!visible()) {
            return;
        }
        int ix0 = Math.max(0, (int) Math.floor(x0 * f));
        int iy0 = Math.max(0, (int) Math.floor(y0 * f));
        int ix1 = Math.min(face.w(), (int) Math.ceil(x1 * f));
        int iy1 = Math.min(face.h(), (int) Math.ceil(y1 * f));
        for (int py = iy0; py < iy1; py++) {
            int y = face.y() + py;
            for (int px = ix0; px < ix1; px++) {
                int x = cx(px);
                if (!canvas.inClip(x, y)) {
                    continue;
                }
                float a = 0;
                float r = 0;
                float g = 0;
                float b = 0;
                for (int s = 0; s < 4; s++) {
                    float fx = (px + ((s & 1) == 0 ? 0.25F : 0.75F)) / f;
                    float fy = (py + ((s & 2) == 0 ? 0.25F : 0.75F)) / f;
                    int c = sampler.sample(fx, fy);
                    int ca = Colors.alpha(c);
                    if (ca == 0) {
                        continue;
                    }
                    float fa = ca / 255F;
                    a += fa;
                    r += Colors.red(c) * fa;
                    g += Colors.green(c) * fa;
                    b += Colors.blue(c) * fa;
                }
                if (a > 0) {
                    canvas.blend(x, y, Colors.argb(Math.round(a / 4 * 255), Math.round(r / a), Math.round(g / a), Math.round(b / a)));
                }
            }
        }
    }

    @FunctionalInterface
    public interface LocalShader {
        int shade(int lx, int ly, int current);
    }

    @FunctionalInterface
    public interface SmoothShader {
        int shade(float fx, float fy, int current);
    }

    @FunctionalInterface
    public interface Sampler {
        int sample(float x, float y);
    }
}
