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

    /**
     * A scar ready to draw: its stroke in bone space ({@link Scar#SEGMENTS} + 1 points, ends first and last), its
     * length, and the box around it where it can change a pixel.
     */
    record Line(Scar.Part part, boolean back, float[] points, float length, float minX, float minY, float maxX, float maxY) {
    }

    /** Reach of the brush around the stroke, in model units. */
    private static final float REACH = 0.5F;

    static List<Line> lines(Appearance a) {
        List<Line> lines = new ArrayList<>(Scar.COUNT);
        for (Scar scar : Scar.of(a)) {
            float[] p = scar.stroke(a.bodyType());
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = 0; i < p.length; i += 2) {
                minX = Math.min(minX, p[i]);
                maxX = Math.max(maxX, p[i]);
                minY = Math.min(minY, p[i + 1]);
                maxY = Math.max(maxY, p[i + 1]);
            }
            lines.add(new Line(scar.zone().part, scar.zone().back, p, scar.length(),
                    minX - REACH, minY - REACH, maxX + REACH, maxY + REACH));
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
        if (bx < line.minX() || bx > line.maxX() || by < line.minY() || by > line.maxY()) {
            return color;
        }
        // Nearest point of the stroke: its distance, and how far along the stroke it is (from the middle).
        float[] p = line.points();
        float segment = line.length() / Scar.SEGMENTS;
        float dist = Float.MAX_VALUE;
        float along = 0;
        for (int i = 0; i + 3 < p.length; i += 2) {
            float vx = p[i + 2] - p[i];
            float vy = p[i + 3] - p[i + 1];
            float len = vx * vx + vy * vy;
            float t = len == 0 ? 0 : Math.max(0, Math.min(1, ((bx - p[i]) * vx + (by - p[i + 1]) * vy) / len));
            float dx = p[i] + t * vx - bx;
            float dy = p[i + 1] + t * vy - by;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < dist) {
                dist = d;
                along = (i / 2 + t) * segment - line.length() / 2;
            }
        }
        if (dist > REACH) {
            return color;
        }
        float half = line.length() / 2;
        // Thinner towards the ends.
        float end = Math.min(1, Math.abs(along) / half);
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
        if (Math.abs(along) < half - 0.15F) {
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
