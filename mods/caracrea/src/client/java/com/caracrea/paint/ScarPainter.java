package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Scar;
import com.caracrea.character.Trait;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the scars a player placed. Scars live in bone space (model units on
 * the front or back of a body part), so the same scar lands at the same
 * place on the 2D skin, the high-definition head and the 3D body, and only
 * where skin shows.
 */
final class ScarPainter {
    private ScarPainter() {
    }

    /** A scar ready to draw: centre, direction and half-length in bone space. */
    record Line(Scar.Part part, boolean back, float x, float y, float dx, float dy, float half) {
    }

    static List<Line> lines(Appearance a) {
        List<Line> lines = new ArrayList<>(Scar.COUNT);
        for (Scar scar : Scar.of(a)) {
            float[] l = scar.line(a.bodyType());
            lines.add(new Line(scar.zone().part, scar.zone().back, l[0], l[1], l[2], l[3], scar.length() / 2));
        }
        return lines;
    }

    /** Paints the scars on the bare skin of a vanilla-layout canvas (before the clothes go on). */
    static void paint(Canvas canvas, Appearance a) {
        List<Line> lines = lines(a);
        if (lines.isEmpty()) {
            return;
        }
        BodyType type = a.bodyType();
        int skin = BodyPainter.skinColor(a);
        for (Line line : lines) {
            Part part = Part.valueOf(line.part().name());
            Face face = line.back() ? Face.BACK : Face.FRONT;
            float[] cube = Scar.cube(line.part(), type);
            Pen pen = Pen.of(canvas, part, face, type);
            // Pen units are quarter texels; one texel is one model unit.
            pen.shadeSmooth((fx, fy, current) -> {
                float bx = line.back() ? cube[0] + cube[3] - fx / 4 : cube[0] + fx / 4;
                float by = cube[1] + fy / 4;
                return over(current, skin, line, bx, by);
            });
        }
    }

    /** The colour of a skin pixel at bone point (bx, by) of a front or back face, with the scars that cross it. */
    static int apply(int color, int skin, List<Line> lines, Scar.Part part, boolean back, float bx, float by) {
        for (Line line : lines) {
            if (line.part() == part && line.back() == back) {
                color = over(color, skin, line, bx, by);
            }
        }
        return color;
    }

    private static int over(int color, int skin, Line line, float bx, float by) {
        float rx = bx - line.x();
        float ry = by - line.y();
        float along = rx * line.dx() + ry * line.dy();
        if (Math.abs(along) > line.half() + 0.4F) {
            return color;
        }
        float t = Math.max(-line.half(), Math.min(line.half(), along));
        float px = rx - t * line.dx();
        float py = ry - t * line.dy();
        float dist = (float) Math.sqrt(px * px + py * py);
        if (dist > 0.5F) {
            return color;
        }
        // Thinner towards the ends.
        float end = Math.abs(t) / line.half();
        float width = 1 - end * end * end * end * 0.7F;
        int pale = Colors.tint(Colors.shade(skin, 1.1F), Colors.rgb(225, 130, 130), 0.5F);
        int edge = Colors.shade(skin, 0.72F);
        int result = color;
        if (dist < 0.36F * width) {
            result = Colors.over(result, Colors.fade(edge, 0.45F * Colors.clamp01((0.36F * width - dist) / 0.1F)));
        }
        if (dist < 0.17F * width) {
            result = Colors.over(result, Colors.fade(pale, 0.95F * Colors.clamp01((0.17F * width - dist) / 0.07F)));
        }
        // Stitch marks across the scar.
        if (Math.abs(along) < line.half() - 0.15F) {
            float phase = along / 0.55F;
            float frac = phase - (float) Math.floor(phase);
            if (Math.abs(frac - 0.5F) < 0.09F && dist < 0.42F) {
                result = Colors.over(result, Colors.fade(edge, 0.4F));
            }
        }
        return result;
    }

    /** Whether the appearance has any placed scar. */
    static boolean any(Appearance a) {
        for (int i = 0; i < Scar.COUNT; i++) {
            if (a.get(Scar.traits(i)[0]) != 0) {
                return true;
            }
        }
        return false;
    }

    static Trait zoneTrait(int index) {
        return Scar.traits(index)[0];
    }
}
