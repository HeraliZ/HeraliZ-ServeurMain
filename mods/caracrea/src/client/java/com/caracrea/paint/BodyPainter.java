package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.Trait;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

/** Bare skin on every inner layer, with soft shading so it does not look flat. */
final class BodyPainter {
    private BodyPainter() {
    }

    static int skinColor(Appearance appearance) {
        return Catalog.swatch(appearance, Trait.SKIN_TONE).argb();
    }

    static void paint(Canvas canvas, Appearance appearance) {
        int skin = skinColor(appearance);
        BodyType type = appearance.bodyType();
        long seed = appearance.seed(Trait.BODY_TYPE);
        for (Part part : Part.VALUES) {
            if (part.outer) {
                continue;
            }
            for (Face face : Face.VALUES) {
                Rect rect = SkinLayout.rect(part, face, type, canvas.scale());
                float faceLight = switch (face) {
                    case TOP -> part == Part.HEAD ? 1.0F : 1.03F;
                    case BOTTOM -> part == Part.HEAD ? 0.8F : 0.9F;
                    default -> 1.0F;
                };
                if (!canvas.touches(rect)) {
                    continue;
                }
                float unit = (float) Canvas.SCALE / canvas.scale();
                canvas.shade(rect, (x, y, current) -> {
                    float ux = x * unit;
                    float uy = y * unit;
                    float grain = 1 + 0.016F * (Noise.fbm(ux, uy, 6, seed) - 0.5F) * 2 + 0.01F * Noise.signed((int) ux, (int) uy, seed + 1);
                    return Colors.shade(skin, faceLight * grain);
                });
            }
        }
        contourFace(canvas, appearance, skin);
        shadeLimbs(canvas, type, skin);
    }

    /** Forehead highlight, jaw and temple shadows on the front of the head. */
    private static void contourFace(Canvas canvas, Appearance appearance, int skin) {
        Pen pen = Pen.of(canvas, Part.HEAD, Face.FRONT, appearance.bodyType());
        boolean female = appearance.bodyType() == BodyType.FEMALE;
        pen.shadeSmooth((fx, fy, current) -> {
            float dx = (fx - 16) / 16F;
            float dy = (fy - 10) / 16F;
            float light = 1 + 0.035F * Math.max(0, 1 - (dx * dx + dy * dy));
            if (fy >= 24) {
                light -= (fy - 24) / 8F * 0.07F * (0.4F + 0.6F * Math.abs(dx));
            }
            // Temples: a soft falloff towards the sides.
            float side = Math.max(0, Math.abs(fx - 16) - 12.5F) / 3.5F;
            light -= 0.035F * side;
            float cheek = Math.abs(Math.abs(dx) - 0.7F);
            if (fy >= 19 && fy <= 24.5F && cheek < 0.25F) {
                light -= (female ? 0.035F : 0.025F) * (1 - cheek / 0.25F);
            }
            // Eye sockets: a faint shadow between the brow and the eye, beside the nose.
            float socket = Math.abs(Math.abs(fx - 16) - 6.5F);
            if (fy > 13.5F && fy < 17F && socket < 3.2F) {
                light -= 0.025F * (1 - socket / 3.2F);
            }
            if (fy >= 31) {
                light -= 0.06F;
            }
            return Colors.shade(current, light);
        });
        // Shadow under the chin and the jaw line on the sides.
        Pen bottom = Pen.of(canvas, Part.HEAD, Face.BOTTOM, appearance.bodyType());
        bottom.shade((lx, ly, current) -> Colors.shade(current, 0.9F + 0.1F * (ly / 32F)));
        for (Face side : new Face[]{Face.RIGHT, Face.LEFT}) {
            Pen sidePen = Pen.of(canvas, Part.HEAD, side, appearance.bodyType());
            sidePen.shade((lx, ly, current) -> ly >= 28 ? Colors.shade(current, 1 - (ly - 27) * 0.02F) : current);
        }
    }

    /** Hands slightly darker at the knuckles, a hint of shading at the elbows and knees. */
    private static void shadeLimbs(Canvas canvas, BodyType type, int skin) {
        for (Part part : new Part[]{Part.RIGHT_ARM, Part.LEFT_ARM}) {
            for (Face face : Face.SIDES) {
                Pen pen = Pen.of(canvas, part, face, type);
                pen.shade(0, 36, pen.width(), 12, (lx, ly, current) -> {
                    float light = ly == 39 || ly == 40 ? 0.93F : 1.0F;
                    if (ly >= 44 && face == Face.FRONT && (lx % 4) == 1) {
                        light *= 0.95F; // fingers
                    }
                    return Colors.shade(current, light);
                });
            }
            Pen bottom = Pen.of(canvas, part, Face.BOTTOM, type);
            bottom.shade((lx, ly, current) -> Colors.shade(current, (lx % 4) == 0 ? 0.88F : 1.0F));
        }
    }
}
