package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.Trait;
import com.caracrea.character.Catalog;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

/** Freckles, scars, moles, dirt and blood on the face. */
final class MarksPainter {
    private MarksPainter() {
    }

    static void paint(Canvas canvas, Appearance a) {
        if (a.get(com.caracrea.character.Trait.MARKS) == 0) {
            return;
        }
        int skin = BodyPainter.skinColor(a);
        long seed = a.seed(Trait.MARKS) + 200;
        Pen pen = Pen.of(canvas, Part.HEAD, Face.FRONT, a.bodyType());
        FacePainter.Eyes eyes = FacePainter.eyes(a);

        if (a.hasMark(Catalog.MARK_FRECKLES)) {
            int freckle = Colors.tint(Colors.shade(skin, 0.7F), Colors.rgb(150, 80, 50), 0.4F);
            pen.shade(3, 19, 26, 8, (lx, ly, current) -> {
                float cx = Math.abs(lx + 0.5F - 16);
                boolean cheek = cx > 3 && cx < 13 && ly < 26;
                boolean nose = cx <= 3 && ly >= 20 && ly < 24;
                if (!(cheek || nose)) {
                    return current;
                }
                float n = Noise.hash(lx, ly, seed);
                return n < 0.16F ? Colors.over(current, Colors.fade(freckle, 0.35F + 0.3F * (n / 0.16F))) : current;
            });
        }
        // Scars: a pale line with faint stitches across it. "Left" is the player's left (large x).
        if (a.hasMark(Catalog.MARK_SCAR_LEFT)) {
            scar(pen, skin, 24.5F, 19.0F, 21.0F, 26.5F, seed + 1);
        }
        if (a.hasMark(Catalog.MARK_SCAR_RIGHT)) {
            scar(pen, skin, 6.0F, 20.5F, 10.5F, 27.0F, seed + 2);
        }
        if (a.hasMark(Catalog.MARK_SCAR_BROW)) {
            float x = eyes.centerX() + 1.5F;
            scar(pen, skin, x - 0.8F, eyes.centerY() - eyes.halfHeight() - 5.5F, x + 0.4F, eyes.centerY() - eyes.halfHeight() - 1.0F, seed + 3);
        }
        if (a.hasMark(Catalog.MARK_MOLE)) {
            int mole = Colors.shade(skin, 0.55F);
            pen.sample(19, 24, 22, 27, (fx, fy) -> {
                float dx = fx - 20.6F;
                float dy = fy - 25.3F;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                return d < 0.75F ? Colors.fade(mole, 0.9F * Colors.clamp01((0.75F - d) / 0.3F)) : 0;
            });
        }
        if (a.hasMark(Catalog.MARK_DIRT)) {
            int dirt = Colors.rgb(62, 46, 30);
            pen.shade((lx, ly, current) -> {
                float n = Noise.fbm(lx, ly, 4.5F, seed + 4);
                float amount = Colors.clamp01((n - 0.56F) / 0.3F) * 0.45F;
                // Mostly on the cheeks, the forehead and the chin, not over the eyes.
                float cx = Math.abs(lx + 0.5F - 16);
                if (ly >= 14 && ly <= 21 && cx > 2 && cx < 12) {
                    amount *= 0.3F;
                }
                return amount <= 0.01F ? current : Colors.over(current, Colors.fade(dirt, amount));
            });
        }
        if (a.hasMark(Catalog.MARK_BLOOD)) {
            int blood = Colors.rgb(118, 14, 16);
            int dried = Colors.rgb(80, 16, 18);
            // A drip from the hairline and splatter on one cheek.
            float dripX = 21 + 3 * Noise.hash(1, 1, seed + 5);
            pen.sample(dripX - 1.5F, 0, dripX + 1.5F, 14, (fx, fy) -> {
                float width = fy < 2 ? 1.1F : 0.45F + 0.25F * (float) Math.sin(fy * 1.7F);
                return Math.abs(fx - dripX - 0.3F * (float) Math.sin(fy * 0.9F)) < width
                        ? Colors.fade(fy < 11 ? blood : dried, 0.85F) : 0;
            });
            pen.shade(0, 16, 14, 14, (lx, ly, current) -> {
                float n = Noise.hash(lx, ly, seed + 6);
                if (n < 0.08F) {
                    return Colors.over(current, Colors.fade(dried, 0.8F));
                }
                float blob = Noise.smooth(lx, ly, 2.2F, seed + 7);
                return blob > 0.74F ? Colors.over(current, Colors.fade(blood, 0.75F)) : current;
            });
        }
    }

    private static void scar(Pen pen, int skin, float x0, float y0, float x1, float y1, long seed) {
        int pale = Colors.tint(Colors.shade(skin, 1.1F), Colors.rgb(225, 130, 130), 0.5F);
        int edge = Colors.shade(skin, 0.74F);
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / len;
        float ny = dx / len;
        pen.sample(Math.min(x0, x1) - 2, Math.min(y0, y1) - 2, Math.max(x0, x1) + 2, Math.max(y0, y1) + 2, (fx, fy) -> {
            float px = fx - x0;
            float py = fy - y0;
            float along = (px * dx + py * dy) / (len * len);
            if (along < 0 || along > 1) {
                return 0;
            }
            float across = Math.abs(px * nx + py * ny);
            int result = 0;
            if (across < 0.55F) {
                result = Colors.fade(pale, 0.95F);
            } else if (across < 1.1F) {
                result = Colors.fade(edge, 0.5F);
            }
            // Stitch marks every two pixels.
            float stitch = (along * len) % 2.4F;
            if (stitch < 0.5F && across < 1.5F) {
                result = Colors.over(result, Colors.fade(edge, 0.4F));
            }
            return result;
        });
    }
}
