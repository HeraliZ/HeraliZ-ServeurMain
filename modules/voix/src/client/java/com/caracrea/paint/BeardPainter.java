package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.FaceState;
import com.caracrea.character.Trait;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

/** Facial hair on the jaw, leaving the mouth clear even when it opens. */
final class BeardPainter {
    private BeardPainter() {
    }

    static void paint(Canvas canvas, Appearance a, FaceState state) {
        int style = a.get(Trait.FACIAL_HAIR);
        if (style == 0) {
            return;
        }
        BodyType type = a.bodyType();
        int hair = Colors.shade(Catalog.swatch(a, Trait.HAIR_COLOR).argb(), 0.85F);
        long seed = a.seed(Trait.FACIAL_HAIR) + 100;
        FacePainter.Mouth mouth = FacePainter.mouth(a, state);
        Pen front = Pen.of(canvas, Part.HEAD, Face.FRONT, type);
        Pen hat = Pen.of(canvas, Part.HAT, Face.FRONT, type);

        // The front of the head uses smooth shapes, so beards have clean outlines on the 8x head too.
        switch (style) {
            case 1 -> { // stubble
                front.shadeSmooth((fx, fy, current) -> {
                    float cover = jaw(fx, fy, 22) * (1 - mouthMask(mouth, fx, fy, 0.8F));
                    cover = Math.max(cover, mustache(mouth, fx, fy, 0.6F) * 0.8F);
                    if (cover <= 0) {
                        return current;
                    }
                    float n = Noise.hash((int) Math.floor(fx * 2), (int) Math.floor(fy * 2), seed);
                    return n < 0.55F ? Colors.over(current, Colors.fade(hair, 0.42F * cover * (0.6F + 0.4F * n / 0.55F))) : current;
                });
                sides(canvas, type, hair, seed, 22, 0.3F, false);
            }
            case 2 -> // mustache
                    front.shadeSmooth((fx, fy, current) -> blendHair(current, hair, mustache(mouth, fx, fy, 1.0F) * 0.94F, fx, fy, seed));
            case 3 -> // goatee
                    front.shadeSmooth((fx, fy, current) -> {
                        float cover = mustache(mouth, fx, fy, 1.0F) * 0.94F;
                        float cx = Math.abs(fx - 16);
                        // A chin beard joined to the moustache at the corners of the mouth. A strip of
                        // skin stays under the lower lip, so the beard never reads as an open mouth.
                        float bottom = mouth.bottom();
                        // Down to the bottom of the face, a little wider at the jaw, with rounded shoulders.
                        float halfWidth = 2.9F + 0.6F * Colors.clamp01((fy - bottom) / 5);
                        float chinTop = bottom + 0.1F + 0.8F * (cx / halfWidth) * (cx / halfWidth);
                        float chin = smooth(chinTop - 0.35F, chinTop + 0.35F, fy) * smooth(halfWidth + 0.35F, halfWidth - 0.35F, cx);
                        // A thin soul patch in the middle of that strip.
                        float patch = smooth(0.9F, 0.5F, cx) * smooth(bottom - 0.7F, bottom - 0.3F, fy);
                        float cornerX = mouth.halfWidth() + 0.9F;
                        float corner = smooth(0.7F, 0.3F, Math.abs(cx - cornerX))
                                * smooth(mouth.lineY() - 0.5F, mouth.lineY(), fy) * smooth(bottom + 2.2F, bottom + 1.4F, fy);
                        // Softer, hairier edges than a solid shape.
                        float body = Math.max(chin, corner) * 0.86F;
                        if (body < 0.86F) {
                            body *= 0.7F + 0.3F * Noise.hash((int) Math.floor(fx * 2), (int) Math.floor(fy * 2), seed + 7);
                        }
                        return blendHair(current, hair, Math.max(cover, Math.max(body, patch * 0.8F)), fx, fy, seed);
                    });
            case 4 -> { // chinstrap
                front.shadeSmooth((fx, fy, current) -> {
                    float cx = Math.abs(fx - 16);
                    float jawLine = 29.6F - Math.max(0, cx - 11) * 1.4F;
                    float cover = Math.max(smooth(jawLine - 0.4F, jawLine + 0.4F, fy), smooth(13.1F, 13.9F, cx) * smooth(19.6F, 20.4F, fy));
                    return blendHair(current, hair, cover * 0.9F * (1 - mouthMask(mouth, fx, fy, 0.5F)), fx, fy, seed);
                });
                sides(canvas, type, hair, seed, 20, 0.9F, true);
            }
            case 5 -> { // short beard
                front.shadeSmooth((fx, fy, current) -> {
                    float cover = jaw(fx, fy, 23.5F) * (1 - mouthMask(mouth, fx, fy, 1.0F)) * 0.9F;
                    cover = Math.max(cover, mustache(mouth, fx, fy, 1.0F) * 0.94F);
                    return blendHair(current, hair, cover, fx, fy, seed);
                });
                sides(canvas, type, hair, seed, 22, 0.85F, false);
            }
            default -> { // full beard
                front.shadeSmooth((fx, fy, current) -> {
                    float cover = jaw(fx, fy, 22.5F) * (1 - mouthMask(mouth, fx, fy, 1.0F)) * 0.95F;
                    cover = Math.max(cover, mustache(mouth, fx, fy, 1.3F) * 0.95F);
                    return blendHair(current, hair, cover, fx, fy, seed);
                });
                sides(canvas, type, hair, seed, 20, 0.9F, false);
                // Volume on the outer layer (cubic body only), below the mouth and along the jaw.
                hat.shade((lx, ly, current) -> {
                    float cx = Math.abs(lx + 0.5F - 16);
                    float cover = 0;
                    if (ly >= mouth.bottom() + 1 && ly >= 24 && cx <= 15.5F) {
                        cover = Noise.hash(lx, ly, seed + 1) < 0.78F ? 0.95F : 0;
                    } else if (ly >= 22 && cx >= 11 && Noise.hash(lx, ly, seed + 2) < 0.6F) {
                        cover = 0.9F;
                    }
                    return blendHair(current, hair, cover, lx + 0.5F, ly + 0.5F, seed);
                });
                for (Face side : new Face[]{Face.RIGHT, Face.LEFT}) {
                    Pen pen = Pen.of(canvas, Part.HAT, side, type);
                    pen.shade((lx, ly, current) -> {
                        float frontDist = side == Face.RIGHT ? 31 - lx : lx;
                        float cover = ly >= 22 && frontDist < 10 && Noise.hash(lx, ly, seed + 3) < 0.7F ? 0.9F : 0;
                        return blendHair(current, hair, cover, lx + 0.5F, ly + 0.5F, seed);
                    });
                }
            }
        }
    }

    /** 0 below {@code edge0}, 1 above {@code edge1}, smooth in between (also works with edge0 > edge1). */
    private static float smooth(float edge0, float edge1, float x) {
        float t = Colors.clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }

    private static int blendHair(int current, int hair, float cover, float fx, float fy, long seed) {
        if (cover <= 0.01F) {
            return current;
        }
        float grain = 0.88F + 0.24F * Noise.hash((int) Math.floor(fx * 2), (int) Math.floor(fy * 2), seed + 50);
        return Colors.over(current, Colors.fade(Colors.shade(hair, grain), Math.min(1, cover)));
    }

    /**
     * Jaw region of the front face: below a line that sits under the cheeks
     * in the middle and rises to meet the sideburns at the sides.
     */
    private static float jaw(float fx, float fy, float top) {
        float cx = Math.abs(fx - 16);
        float line = top + 2.5F - 2.5F * smooth(8, 14, cx);
        return smooth(line - 0.45F, line + 0.45F, fy);
    }

    /** A moustache: arched on top, drooping at the corners, thinner under the nose. */
    private static float mustache(FacePainter.Mouth mouth, float fx, float fy, float thickness) {
        float cx = Math.abs(fx - 16);
        float halfWidth = mouth.halfWidth() + 1.3F * thickness;
        float t = Math.min(1, cx / halfWidth);
        float lip = mouth.upperEdge() - mouth.upperThickness();
        float top = lip - 1.0F - 1.5F * thickness * (1 - 0.45F * t * t) + (cx < 0.9F ? 0.5F : 0);
        float bottom = lip + 0.15F + 1.1F * t * t;
        float cover = smooth(top - 0.35F, top + 0.35F, fy) * smooth(bottom + 0.35F, bottom - 0.35F, fy);
        return cover * smooth(halfWidth + 0.3F, halfWidth - 0.4F, cx);
    }

    /** 1 inside the mouth (with a margin), 0 outside, smooth at the edge. */
    private static float mouthMask(FacePainter.Mouth mouth, float fx, float fy, float margin) {
        float dx = Math.abs(fx - 16) / (mouth.halfWidth() + margin + 0.6F);
        float midY = (mouth.top() + mouth.bottom()) / 2;
        float dy = (fy - midY) / ((mouth.bottom() - mouth.top()) / 2 + margin);
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        return smooth(1.1F, 0.9F, d);
    }

    /** Sideburns and jaw hair on the side faces of the head. */
    private static void sides(Canvas canvas, BodyType type, int hair, long seed, float top, float alpha, boolean strap) {
        for (Face side : new Face[]{Face.RIGHT, Face.LEFT}) {
            Pen pen = Pen.of(canvas, Part.HEAD, side, type);
            pen.shade((lx, ly, current) -> {
                float frontDist = side == Face.RIGHT ? 31 - lx : lx; // 0 next to the face
                float y = ly + 0.5F;
                float cover = 0;
                if (strap) {
                    if ((y >= 29 && frontDist < 24) || (frontDist < 4 && y >= 14)) {
                        cover = alpha;
                    }
                } else {
                    float limit = top + frontDist * 0.35F;
                    if (frontDist < 4 && y >= 12) {
                        cover = alpha; // sideburns
                    } else if (y >= limit && frontDist < 22) {
                        cover = alpha * Colors.clamp01(y - limit + 0.5F);
                    }
                }
                if (cover <= 0) {
                    return current;
                }
                if (alpha < 0.5F) {
                    return Noise.hash(lx, ly, seed + 60) < 0.55F ? Colors.over(current, Colors.fade(hair, cover)) : current;
                }
                return blendHair(current, hair, cover, lx + 0.5F, ly + 0.5F, seed);
            });
        }
    }
}
