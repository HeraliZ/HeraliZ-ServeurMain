package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.FaceState;
import com.caracrea.character.Trait;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

/**
 * Eyes, eyebrows, nose, mouth and ears. Everything is parametric: shapes are
 * sampled with anti-aliasing on the 32x32 front of the head, so eye size,
 * spacing, iris size and the blink / talk state are all continuous.
 *
 * <p>Vertical reference: the vanilla Steve has his eyes on texel row 4 of the
 * face (sub-pixels 16..20), so the eye line here sits around y = 18.
 */
public final class FacePainter {
    private FacePainter() {
    }

    /** Geometry shared by the eyes and the eyebrows, in face sub-pixels. */
    record Eyes(float centerX, float centerY, float halfWidth, float halfHeight, float irisRadius) {
    }

    /** Position of the mouth, used by the beard painter to leave it clear. */
    public record Mouth(float centerX, float lineY, float halfWidth, float opening, float upperThickness, float lowerThickness,
                        float roundness, int teeth, float smile, float lift) {
        /** Bottom edge of the upper lip at the middle: above the resting line when the mouth opens round. */
        public float upperEdge() {
            return lineY - lift * opening;
        }

        public float top() {
            return upperEdge() - upperThickness - 1.5F;
        }

        public float bottom() {
            return lineY + opening * (1 - lift) + lowerThickness + 1.0F;
        }
    }

    static Eyes eyes(Appearance a) {
        float size = a.get(Trait.EYE_SIZE);
        float spacing = a.get(Trait.EYE_SPACING);
        float height = a.get(Trait.EYE_HEIGHT);
        float iris = a.get(Trait.IRIS_SIZE);
        float halfWidth = 3.0F + 0.5F * size;
        float halfHeight = 1.7F + 0.3F * size;
        float offset = 5.5F + 0.75F * spacing;
        float centerY = 18.0F + (height - 2) * 0.75F;
        return new Eyes(16 - offset, centerY, halfWidth, halfHeight, 1.5F + 0.3F * iris);
    }

    public static Mouth mouth(Appearance a, FaceState state) {
        int style = a.get(Trait.MOUTH_STYLE);
        float halfWidth = (7 + a.get(Trait.MOUTH_WIDTH)) / 2F;
        float upper = switch (style) {
            case 1 -> 1.5F;
            case 2 -> 0.6F;
            case 3 -> 0.9F;
            default -> 1.0F;
        };
        float lower = switch (style) {
            case 1 -> 2.2F;
            case 2 -> 0.9F;
            case 4 -> 1.4F;
            default -> 1.5F;
        };
        float lips = state.lipScale();
        return new Mouth(16, 26.2F, halfWidth * state.mouthWidth(), state.mouthOpening(), upper * lips, lower * lips,
                state.mouthRoundness(), state.mouthTeeth(), state.mouthSmile(), state.lipLift());
    }

    static void paint(Canvas canvas, Appearance a, FaceState state) {
        BodyType type = a.bodyType();
        Pen pen = Pen.of(canvas, Part.HEAD, Face.FRONT, type);
        int skin = BodyPainter.skinColor(a);
        Eyes eyes = eyes(a);
        if (type == BodyType.FEMALE) {
            blush(pen, eyes, skin);
        }
        nose(pen, a, eyes, skin);
        // Each eye is drawn with the same code: the second one through a mirrored pen.
        eye(pen, a, eyes, state, skin, false);
        eye(pen.mirrored(), a, eyes, state, skin, true);
        brow(pen, a, eyes, false);
        brow(pen.mirrored(), a, eyes, true);
        mouth(pen, a, state, skin);
        ears(canvas, a, skin);
    }

    // -------------------------------------------------------------------- eyes

    /**
     * @param mirrored true when drawing the player's left eye through a mirrored pen:
     *                 the outer corner is then towards larger local x.
     */
    private static void eye(Pen pen, Appearance a, Eyes eyes, FaceState state, int skin, boolean mirrored) {
        int shape = a.get(Trait.EYE_SHAPE);
        boolean female = a.bodyType() == BodyType.FEMALE;
        float open = state.eyeOpenness();
        float cx = eyes.centerX();
        float cy = eyes.centerY();
        float hw = eyes.halfWidth();
        float hh = eyes.halfHeight();
        // Shape presets.
        float upperScale = switch (shape) {
            case 2 -> 0.68F;      // narrow
            case 3 -> 0.82F;      // hooded
            default -> 1.0F;
        };
        float lowerScale = switch (shape) {
            case 1 -> 0.75F;      // almond
            case 2 -> 0.55F;
            case 3 -> 0.7F;
            default -> 0.85F;
        };
        float exponent = shape == 1 ? 0.65F : 0.5F;
        float tilt = switch (shape) {
            case 4 -> -0.75F;     // upturned: outer corner up
            case 5 -> 0.7F;       // downturned
            default -> 0.0F;
        };
        float lidWeight = shape == 3 ? 1.6F : 1.0F;
        // In the (possibly mirrored) pen, the outer corner is always towards x = 0.
        float outerSign = -1;

        int irisBase = Catalog.swatch(a, Trait.EYE_COLOR).argb();
        int sclera = Colors.rgb(234, 230, 224);
        int lash = female ? Colors.rgb(26, 18, 18) : Colors.tint(Colors.shade(skin, 0.42F), Colors.rgb(40, 24, 16), 0.5F);
        float lashThickness = female ? 1.25F : 0.9F;
        float lashAlpha = female ? 0.95F : 0.8F;
        int lowerLine = Colors.shade(skin, 0.72F);
        int crease = Colors.shade(skin, 0.84F);
        // The iris fills most of the eye's height: its top a little under the upper lid, its
        // bottom near the lower lid, as on a relaxed face. A small iris floating in a big eye
        // (white all around, or only below) reads as a startled or rolling stare.
        float opening = hh * (upperScale + lowerScale);
        float irisR = Math.max(eyes.irisRadius(), opening * 0.47F);
        float pupilR = irisR * 0.42F;
        float ix = cx + 0.1F;
        float iy = cy - hh * upperScale + irisR - 0.4F;
        boolean closed = open < 0.15F;

        float x0 = cx - hw - 2.5F;
        float x1 = cx + hw + 2.5F;
        float y0 = cy - hh - 3F;
        float y1 = cy + hh + 2.5F;
        pen.sample(x0, y0, x1, y1, (fx, fy) -> {
            float t = (fx - cx) / hw;
            float tOuter = t * outerSign;
            float base = cy + tilt * tOuter;
            float inside1 = Math.max(0, 1 - t * t);
            float upper = hh * upperScale * (float) Math.pow(inside1, exponent);
            float lower = hh * lowerScale * (float) Math.pow(inside1, 0.6);
            float up = upper * open;
            float lo = lower * (0.35F + 0.65F * open);
            int result = 0;
            if (!closed && Math.abs(t) <= 1 && fy >= base - up && fy <= base + lo) {
                // Sclera, darker under the upper lid and at the corners.
                float depth = (fy - (base - up)) / Math.max(0.5F, up + lo);
                float cornerShade = 1 - 0.18F * Math.max(0, Math.abs(t) - 0.6F) / 0.4F;
                float lidShade = 1 - 0.22F * Math.max(0, 0.35F - depth) / 0.35F * lidWeight;
                result = Colors.shade(sclera, cornerShade * lidShade);
                // Iris and pupil.
                float dx = fx - ix;
                float dy = fy - iy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < irisR) {
                    float tr = dist / irisR;
                    int iris = Colors.lerp(Colors.lighten(irisBase, 0.3F), irisBase, Colors.clamp01((tr - 0.15F) / 0.6F));
                    if (tr > 0.78F) {
                        iris = Colors.lerp(iris, Colors.shade(irisBase, 0.45F), (tr - 0.78F) / 0.22F);
                    }
                    // Radial streaks of the iris.
                    float angle = (float) Math.atan2(dy, dx);
                    iris = Colors.shade(iris, 1 + 0.06F * (float) Math.sin(angle * 9 + tr * 4));
                    if (dist < pupilR) {
                        iris = Colors.lerp(Colors.rgb(14, 10, 12), iris, Colors.clamp01((dist - pupilR + 0.35F) / 0.35F) * 0.4F);
                    }
                    float edge = Colors.clamp01((irisR - dist) / 0.35F);
                    result = Colors.lerp(result, iris, edge);
                    // Specular highlight.
                    float hx = fx - (ix - irisR * 0.38F);
                    float hy = fy - (iy - irisR * 0.42F);
                    float hd = (float) Math.sqrt(hx * hx + hy * hy);
                    if (hd < 0.55F) {
                        result = Colors.lerp(result, Colors.rgb(255, 255, 255), 0.85F * Colors.clamp01((0.55F - hd) / 0.25F));
                    }
                }
            }
            // Upper lid / lashes: a line along the upper boundary, slightly beyond the corners.
            float ext = Math.abs(t) > 1 ? (Math.abs(t) - 1) * hw : 0;
            float wing = tOuter > 1 ? (female ? 1.6F : 0.6F) : 0.4F;
            if (ext <= wing) {
                float lidY = closed ? base + lower * 0.3F : base - up;
                if (Math.abs(t) > 1) {
                    // Continue the lid line past the corner, drooping/lifting with the tilt.
                    lidY = base + (tOuter > 1 ? -0.25F * ext : 0.15F * ext);
                    if (closed) {
                        lidY = base + lower * 0.3F;
                    }
                }
                float dist = Math.abs(fy - lidY);
                float thickness = lashThickness * (closed ? 0.9F : 1.0F) * (1 - 0.35F * Math.max(0, Math.abs(t) - 0.5F));
                if (dist < thickness / 2 + 0.3F) {
                    float cov = Colors.clamp01((thickness / 2 + 0.3F - dist) / 0.5F);
                    result = Colors.over(result, Colors.fade(lash, lashAlpha * cov));
                }
                // Hooded eyes: a heavier shadow above the lid.
                if (!closed && lidWeight > 1 && fy < lidY && fy > lidY - 1.4F && Math.abs(t) <= 1) {
                    result = Colors.over(result, Colors.fade(Colors.shade(skin, 0.78F), 0.45F));
                }
            }
            // Lower lid line and the eyelid crease.
            if (Math.abs(t) <= 1.05F) {
                float lowY = base + lo + 0.35F;
                float dist = Math.abs(fy - lowY);
                if (!closed && dist < 0.55F) {
                    result = Colors.over(result, Colors.fade(lowerLine, (female ? 0.42F : 0.32F) * Colors.clamp01((0.55F - dist) / 0.3F)));
                }
                if (!closed && open > 0.6F) {
                    float creaseY = base - up - 1.5F;
                    float cd = Math.abs(fy - creaseY);
                    if (cd < 0.5F) {
                        result = Colors.over(result, Colors.fade(crease, 0.28F * (1 - Math.abs(t)) * Colors.clamp01((0.5F - cd) / 0.3F)));
                    }
                }
            }
            return result;
        });
    }

    // ------------------------------------------------------------------- brows

    private static void brow(Pen pen, Appearance a, Eyes eyes, boolean mirrored) {
        int style = a.get(Trait.BROW_STYLE);
        int hairColor = Catalog.swatch(a, Trait.HAIR_COLOR).argb();
        int skin = BodyPainter.skinColor(a);
        // Brows are darker than the hair, and always darker than the skin so they stay visible.
        int brow = Colors.lerp(Colors.shade(hairColor, 0.72F), Colors.rgb(48, 34, 26), 0.3F);
        for (int i = 0; i < 6 && Colors.luminance(brow) > Colors.luminance(skin) * 0.72F; i++) {
            brow = Colors.shade(brow, 0.85F);
        }
        int browColor = brow;
        boolean female = a.bodyType() == BodyType.FEMALE;
        float thickness = switch (style) {
            case 1 -> 2.4F;
            case 2 -> 1.0F;
            case 3 -> 1.5F;
            default -> female ? 1.5F : 1.8F;
        };
        float arch = switch (style) {
            case 0 -> 0.8F;
            case 1 -> 0.6F;
            case 2 -> 1.0F;
            case 3 -> 1.9F;
            default -> 0.0F;
        };
        float slope = style == 5 ? 1.1F : 0.0F;
        float cx = eyes.centerX();
        float by = eyes.centerY() - eyes.halfHeight() - 2.6F - (female ? 0.4F : 0);
        float hw = eyes.halfWidth() + 1.2F;
        long seed = a.seed(Trait.BROW_STYLE) ^ (mirrored ? 0x55 : 0x33);
        pen.sample(cx - hw - 1, by - arch - thickness - 1, cx + hw + 1, by + thickness + slope + 1, (fx, fy) -> {
            float t = (fx - cx) / hw;
            if (Math.abs(t) > 1) {
                return 0;
            }
            float tOuter = -t;                 // outer corner towards x = 0 in the (mirrored) pen
            float u = (tOuter + 1) / 2;        // 0 at the inner end, 1 at the outer end
            float peak = (u - 0.62F) / 0.62F;
            float y = by - arch * Math.max(0, 1 - peak * peak) + slope * (0.5F - u) * 2 * 0.5F;
            float th = thickness * (1.05F - 0.55F * u * u) * (u < 0.1F ? 0.6F + 4 * u : 1);
            float dist = Math.abs(fy - y);
            if (dist > th / 2 + 0.3F) {
                return 0;
            }
            float cov = Colors.clamp01((th / 2 + 0.3F - dist) / 0.6F);
            float hairy = 0.72F + 0.28F * Noise.hash((int) (fx * 2), (int) (fy * 2), seed);
            return Colors.fade(browColor, 0.92F * cov * hairy);
        });
    }

    // -------------------------------------------------------------------- nose

    private static void nose(Pen pen, Appearance a, Eyes eyes, int skin) {
        int style = a.get(Trait.NOSE_STYLE);
        float width = switch (style) {
            case 0 -> 3.0F;
            case 2 -> 5.4F;
            case 3 -> 3.4F;
            default -> 4.0F;
        };
        float tipY = switch (style) {
            case 0 -> 23.0F;
            case 2 -> 24.0F;
            case 3 -> 24.2F;
            default -> 23.5F;
        };
        float topY = eyes.centerY() + 0.5F;
        int dark = Colors.shade(skin, 0.68F);
        int nostril = Colors.shade(skin, 0.45F);
        int light = Colors.rgb(255, 250, 240);
        float halfW = width / 2;
        float highlight = style == 3 ? 0.12F : 0.08F;
        pen.sample(16 - halfW - 2, topY, 16 + halfW + 2, tipY + 2.2F, (fx, fy) -> {
            float dx = fx - 16;
            float progress = Colors.clamp01((fy - topY) / (tipY - topY));
            int result = 0;
            // The bridge: lit on one side, shaded on the other, widening towards the tip.
            float edge = halfW * (0.35F + 0.5F * progress);
            if (fy < tipY - 0.6F) {
                float shadowSide = Math.abs(dx - edge);
                if (shadowSide < 1.0F) {
                    result = Colors.over(result, Colors.fade(dark, 0.4F * (float) Math.pow(progress, 0.8) * (1 - shadowSide)));
                }
                float otherSide = Math.abs(dx + edge);
                if (otherSide < 0.8F) {
                    result = Colors.over(result, Colors.fade(dark, 0.14F * progress * (1 - otherSide / 0.8F)));
                }
                if (dx > -edge * 0.7F && dx < -edge * 0.1F) {
                    result = Colors.over(result, Colors.fade(light, highlight * progress));
                }
            }
            // The wings around the nostrils: a soft curved outline.
            for (int s = -1; s <= 1; s += 2) {
                float wx = (fx - (16 + s * (halfW - 0.7F))) / 1.35F;
                float wy = (fy - (tipY - 0.55F)) / 1.05F;
                float ring = Math.abs((float) Math.sqrt(wx * wx + wy * wy) - 1);
                if (ring < 0.22F && wy > -0.6F) {
                    result = Colors.over(result, Colors.fade(dark, 0.4F * (1 - ring / 0.22F)));
                }
            }
            // Nostrils.
            for (int s = -1; s <= 1; s += 2) {
                float nx = fx - (16 + s * (halfW - 0.75F));
                float ny = fy - (tipY - 0.05F);
                float d = (float) Math.sqrt(nx * nx * 1.6F + ny * ny * 3.0F);
                if (d < 0.75F) {
                    result = Colors.over(result, Colors.fade(nostril, 0.75F * Colors.clamp01((0.75F - d) / 0.3F)));
                }
            }
            // Shadow cast under the nose onto the upper lip.
            if (fy > tipY + 0.2F && fy < tipY + 1.3F && Math.abs(dx) < halfW - 0.2F) {
                float taper = 1 - Math.abs(dx) / halfW;
                float fade = 1 - (fy - tipY - 0.2F) / 1.1F;
                result = Colors.over(result, Colors.fade(dark, 0.36F * taper * fade));
            }
            // Tip highlight.
            float tx = fx - 15.7F;
            float ty = fy - (tipY - 1.25F);
            float td = (float) Math.sqrt(tx * tx + ty * ty * 1.3F);
            if (td < 1.0F) {
                result = Colors.over(result, Colors.fade(light, highlight * 1.4F * (1 - td)));
            }
            return result;
        });
    }

    // ------------------------------------------------------------------- mouth

    private static void mouth(Pen pen, Appearance a, FaceState state, int skin) {
        Mouth m = mouth(a, state);
        int style = a.get(Trait.MOUTH_STYLE);
        boolean female = a.bodyType() == BodyType.FEMALE;
        float curve = switch (style) {
            case 3 -> -1.1F;
            case 4 -> 0.9F;
            default -> 0.2F;
        } - m.smile();
        int lipIndex = a.get(Trait.LIP_COLOR);
        int lip;
        if (lipIndex == 0) {
            lip = Colors.tint(Colors.shade(skin, 0.86F), Colors.rgb(190, 90, 100), female ? 0.38F : 0.22F);
        } else {
            lip = Colors.lerp(Colors.shade(skin, 0.9F), Catalog.LIP_COLORS.get(lipIndex).argb(), 0.8F);
        }
        int upperLip = Colors.shade(lip, 0.86F);
        int lowerLip = Colors.shade(lip, 1.06F);
        int line = Colors.shade(lip, 0.42F);
        int interior = Colors.rgb(34, 14, 18);
        int teeth = Colors.rgb(236, 230, 220);
        int tongue = Colors.rgb(150, 60, 72);
        float opening = m.opening();
        float cx = m.centerX();
        float hw = m.halfWidth();
        pen.sample(cx - hw - 1.5F, m.top() - 1, cx + hw + 1.5F, m.bottom() + 1, (fx, fy) -> {
            float t = (fx - cx) / hw;
            if (Math.abs(t) > 1.08F) {
                return 0;
            }
            float inside = Math.max(0, 1 - t * t);
            float gap = opening * (float) Math.pow(inside, m.roundness());
            // Both lips part: the upper one rises by its share of the opening, the lower one drops.
            float lineY = m.lineY() + curve * t * t - m.lift() * gap;
            float upperTop = lineY - m.upperThickness() * (float) Math.pow(inside, 0.7) * (1 - 0.15F * Math.max(0, 0.3F - Math.abs(t)) / 0.3F);
            float lowerTop = lineY + gap;
            float lowerBottom = lowerTop + m.lowerThickness() * (float) Math.pow(inside, 0.6);
            int result = 0;
            if (Math.abs(t) <= 1) {
                if (fy >= upperTop && fy < lineY) {
                    result = upperLip;
                    if (fy - upperTop < 0.5F) {
                        result = Colors.over(result, Colors.fade(Colors.rgb(255, 255, 255), 0.12F));
                    }
                } else if (fy >= lineY && fy < lowerTop && gap > 0.4F) {
                    result = interior;
                    if (m.teeth() == 2) {
                        // Closed teeth: upper and lower rows meeting in a thin dark line.
                        float mid = lineY + gap * 0.5F;
                        result = Math.abs(fy - mid) < 0.22F ? Colors.lerp(interior, teeth, 0.35F) : teeth;
                        result = Colors.lerp(result, interior, Math.max(0, Math.abs(t) - 0.55F) * 1.4F);
                    } else if (m.teeth() == 1 && fy < lineY + Math.min(1.0F, gap * 0.55F)) {
                        result = Colors.lerp(teeth, interior, Math.abs(t) * 0.5F);
                    } else if (gap >= 3.2F && fy > lowerTop - 1.1F) {
                        result = tongue;
                    }
                } else if (fy >= lowerTop && fy < lowerBottom) {
                    result = lowerLip;
                    float mid = (fy - lowerTop) / Math.max(0.1F, lowerBottom - lowerTop);
                    if (mid > 0.25F && mid < 0.6F && Math.abs(t) < 0.5F) {
                        result = Colors.over(result, Colors.fade(Colors.rgb(255, 255, 255), 0.16F));
                    }
                }
                // The parting line (or the edges of the opening).
                float d1 = Math.abs(fy - lineY);
                if (d1 < 0.45F && gap <= 0.4F) {
                    result = Colors.over(result, Colors.fade(line, 0.72F * Colors.clamp01((0.45F - d1) / 0.25F)));
                }
                if (gap > 0.4F) {
                    if (Math.abs(fy - lineY) < 0.35F || Math.abs(fy - lowerTop) < 0.35F) {
                        result = Colors.over(result, Colors.fade(line, 0.7F));
                    }
                }
                // Soft shadow under the lower lip.
                if (fy >= lowerBottom && fy < lowerBottom + 0.9F && Math.abs(t) < 0.8F) {
                    result = Colors.over(result, Colors.fade(Colors.shade(skin, 0.8F), 0.3F));
                }
            } else {
                // Corners of the mouth, a touch darker.
                float lineY2 = m.lineY() + curve;
                if (Math.abs(fy - lineY2) < 0.6F) {
                    result = Colors.fade(line, 0.45F * (1.08F - Math.abs(t)) / 0.08F);
                }
            }
            return result;
        });
    }

    // -------------------------------------------------------------- ears, cheeks

    private static void ears(Canvas canvas, Appearance a, int skin) {
        BodyType type = a.bodyType();
        int outline = Colors.shade(skin, 0.76F);
        int inner = Colors.shade(skin, 0.9F);
        int shadow = Colors.shade(skin, 0.68F);
        for (Face side : new Face[]{Face.RIGHT, Face.LEFT}) {
            Pen pen = Pen.of(canvas, Part.HEAD, side, type);
            // The front of the head is at large x on the RIGHT face and small x on the LEFT face.
            float cx = side == Face.RIGHT ? 14.5F : 17.5F;
            float cy = 19.5F;
            float rx = 2.6F;
            float ry = 3.8F;
            pen.sample(cx - rx - 1, cy - ry - 1, cx + rx + 1, cy + ry + 1, (fx, fy) -> {
                float dx = (fx - cx) / rx;
                float dy = (fy - cy) / ry;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 1.15F) {
                    return 0;
                }
                if (d > 0.82F) {
                    return Colors.fade(outline, 0.42F * Colors.clamp01((1.15F - d) / 0.2F));
                }
                float sx = (fx - (cx - 0.3F)) / (rx * 0.45F);
                float sy = (fy - (cy + 0.6F)) / (ry * 0.45F);
                if (sx * sx + sy * sy < 1) {
                    return Colors.fade(shadow, 0.35F);
                }
                return Colors.fade(inner, 0.3F);
            });
        }
    }

    private static void blush(Pen pen, Eyes eyes, int skin) {
        int blush = Colors.rgb(226, 118, 122);
        float r = 3.6F;
        for (int s = -1; s <= 1; s += 2) {
            float cx = 16 + s * (16 - eyes.centerX());
            float cy = eyes.centerY() + 6.5F;
            pen.sample(cx - r, cy - r, cx + r, cy + r, (fx, fy) -> {
                float dx = fx - cx;
                float dy = (fy - cy) * 1.3F;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                return d >= 1 ? 0 : Colors.fade(blush, 0.11F * (1 - d));
            });
        }
    }
}
