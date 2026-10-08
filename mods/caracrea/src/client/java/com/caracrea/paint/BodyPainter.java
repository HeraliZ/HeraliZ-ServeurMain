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

    /**
     * After the clothes: the arms and legs of the skin shaded like rounded limbs (darker towards the edges of each
     * face, the inner side in shadow), with the creases of the elbow, the wrist and the knee, and the hands detailed
     * (knuckles, fingers, nails, a lighter palm). This skin is what the arms seen in first person, the
     * classic cubic body and the corpses show.
     */
    static void shadeLimbs(Canvas canvas, Appearance appearance) {
        BodyType type = appearance.bodyType();
        for (Part part : new Part[]{Part.RIGHT_ARM, Part.LEFT_ARM, Part.RIGHT_LEG, Part.LEFT_LEG}) {
            boolean arm = part.isArm();
            boolean left = part.isLeft();
            for (Face face : Face.SIDES) {
                Pen pen = Pen.of(canvas, part, face, type);
                int w = pen.width();
                boolean inner = left ? face == Face.RIGHT : face == Face.LEFT;
                boolean outer = left ? face == Face.LEFT : face == Face.RIGHT;
                pen.shade((lx, ly, current) -> {
                    if (Colors.alpha(current) == 0) {
                        return current;
                    }
                    float across = (lx + 0.5F) / w;
                    float c = Math.abs(across * 2 - 1);
                    float light = (1 - 0.13F * c * c * c) * (1.03F - 0.06F * ly / 48F) * (inner ? 0.9F : 1);
                    int color = current;
                    if (arm) {
                        if (face == Face.FRONT && ly >= 23 && ly <= 24) {
                            light *= 0.97F;   // the crease of the elbow
                        }
                        if (ly >= 36 && ly <= 37) {
                            light *= 0.95F;   // the wrist
                        }
                        if (ly >= 38) {
                            // The hand: palm towards the body, four fingers from the front to the back of the hand.
                            boolean gap = (lx % 4) == 0 && lx > 0;
                            if (outer && ly == 40) {
                                light *= gap ? 0.86F : 1.05F;   // knuckles
                            }
                            if ((outer || inner) && ly > 40 && gap) {
                                light *= 0.84F;                 // between the fingers
                            }
                            if (inner && ly < 41) {
                                color = Colors.tint(Colors.lighten(color, 0.04F), Colors.rgb(225, 150, 140), 0.1F);   // the palm
                            }
                        }
                    } else if (face == Face.BACK && ly >= 23 && ly <= 24) {
                        light *= 0.93F;       // behind the knee
                    }
                    return Colors.shade(color, light);
                });
            }
            if (arm) {
                // Fingertips under the hand, with their nails.
                Pen bottom = Pen.of(canvas, part, Face.BOTTOM, type);
                bottom.shade((lx, ly, current) -> {
                    boolean gap = (ly % 4) == 0 && ly > 0;
                    int color = Colors.shade(current, gap ? 0.82F : 0.95F);
                    return !gap && (ly % 4) == 2 ? Colors.lerp(color, Colors.rgb(236, 200, 190), 0.25F) : color;
                });
            }
        }
    }
}
