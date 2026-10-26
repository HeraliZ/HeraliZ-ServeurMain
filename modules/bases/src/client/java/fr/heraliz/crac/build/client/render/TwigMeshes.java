package fr.heraliz.crac.build.client.render;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.geom.EdgeFrame;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.model.PieceKind;

/**
 * Twig is straw (§10.8): walls are thick straw bundles stacked between a light stick frame (top and bottom rails, one
 * brace per panel, jambs around the openings) lashed with rope, with ragged straw tufts on the top edge. The bundles
 * are solid prisms with an opaque texture: you barely see through twig, it only looks fragile. Gates are woven straw
 * panels on a stick frame. Everything is in the wall frame (u along, n across, y up) and stays within the render
 * envelope of the wall (|n| <= 3.5, y from -1.5 to the wall's top).
 */
final class TwigMeshes {
    /** Half thickness of a straw bundle at its crown and in the grooves between two bundles, px. */
    static final double STRAW = 1.8;
    static final double GROOVE = 1.15;
    /** Height of the bevels on each side of a bundle's crown, px. */
    private static final double BEVEL = 2.0;
    /** Bundle boundaries (wall frame y) of full-height walls: the door head and the window sill and head are on them. */
    static final double[] FULL_BANDS = {-1, 7.5, 16, 24, 32, 39.5, 47};
    static final double[] HALF_BANDS = {-1, 7, 15, 23};
    /** Texture strips of {@code twig/wall.png}: one bundle each, stalks along u, 48 px per tile. */
    private static final int STRIPS = 6;
    /** Sticks of the frame: half width in the wall plane, and their reach across the wall (n). */
    private static final double RAIL = 0.7;
    private static final double RAIL_N0 = 1.1;
    private static final double RAIL_N1 = 2.6;
    private static final double JAMB = 0.8;
    private static final double JAMB_N0 = 1.05;
    private static final double JAMB_N1 = 2.75;
    private static final double BRACE = 0.65;
    private static final double BRACE_N0 = 1.35;
    private static final double BRACE_N1 = 2.35;
    /** Rails start this far from a wall end, inside the corner post that closes the joint. */
    private static final double RAIL_END = 2.4;
    /** Rope lashing half size around a crossing, and its reach across the wall. */
    private static final double ROPE = 1.1;
    private static final double ROPE_N0 = 0.95;
    private static final double ROPE_N1 = 2.95;
    /** Straw tufts on the top edge: below the wall's nominal top (the render envelope ends there). */
    private static final double TUFT_MAX = 0.9;

    private TwigMeshes() {
    }

    /** Bundle boundaries of a half wall whose top is {@code y1} (23, or 19 when nothing rests on it): HALF_BANDS scaled. */
    static double[] halfBands(double y1) {
        double[] out = HALF_BANDS.clone();
        double k = (y1 + 1) / (HALF_BANDS[HALF_BANDS.length - 1] + 1);
        for (int i = 1; i < out.length; i++) {
            out[i] = -1 + (out[i] + 1) * k;
        }
        return out;
    }

    /**
     * Straw wall of a profile (non-overlapping (u, y) rectangles of the wall body, see {@link PieceMeshes}): bundles,
     * frame, ropes and tufts. {@code y1} is the wall top (47 or 23), {@code c} the opening centre and {@code ow} its
     * half width; {@code seed} varies the texture strips between walls.
     */
    static void wall(MeshBuilder b, MeshBuilder.Frame f, PieceKind kind, List<double[]> prof, double us, double ue, double y1, double c,
                     double ow, int seed) {
        double[] bands = kind == PieceKind.HALF_WALL ? halfBands(y1) : FULL_BANDS;
        int nb = bands.length - 1;
        List<List<double[]>> spans = new ArrayList<>(nb);
        for (int k = 0; k < nb; k++) {
            spans.add(spans(prof, bands[k], bands[k + 1]));
        }
        for (int k = 0; k < nb; k++) {
            double ya = bands[k];
            double yb = bands[k + 1];
            int strip = Math.floorMod(seed * 5 + k * 4 + 1, STRIPS);
            double off = Math.floorMod(seed * 17 + k * 29, 48);
            for (double[] s : spans.get(k)) {
                List<double[]> top = k == nb - 1 ? List.of(s) : minus(s, spans.get(k + 1));
                List<double[]> bottom = k == 0 ? List.of(s) : minus(s, spans.get(k - 1));
                bundle(b, f, s[0], s[1], ya, yb, strip, off, s[0] > us + 1e-6, s[1] < ue - 1e-6, top, bottom);
            }
        }
        for (double[] s : spans.get(nb - 1)) {
            tufts(b, f, Math.max(s[0], us + 1.2), Math.min(s[1], ue - 1.2), y1, seed);
        }

        // the frame: a rail pair in the lowest and the highest groove, wherever straw lies on both sides of it
        List<double[]> ropes = new ArrayList<>();
        double[] railY = {bands[1], bands[nb - 1]};
        for (int r = 0; r < 2; r++) {
            int k = r == 0 ? 1 : nb - 1;
            for (double[] s : intersect(spans.get(k - 1), spans.get(k))) {
                double a = s[0] <= us + 1e-6 ? RAIL_END : s[0] + 0.3;
                double z = s[1] >= ue - 1e-6 ? EdgeFrame.LENGTH - RAIL_END : s[1] - 0.3;
                rails(b, f, a, z, railY[r]);
            }
        }
        // openings: jambs on both faces, a lintel over the head, a sill stick under a window
        boolean door = kind.hasDoor();
        boolean window = kind == PieceKind.WINDOW;
        List<double[]> panels = new ArrayList<>();
        if (door || window) {
            // clear of the planes of the bundle ends at the opening
            double l = c - ow - JAMB - 0.05;
            double rr = c + ow + JAMB + 0.05;
            double head = door ? PieceSolids.DOOR_H : PieceSolids.WINDOW_HEAD;
            double lintel = head + JAMB + 0.15;
            double foot = bands[0] + 0.4;
            pair(b, f, l, foot, l, railY[1], JAMB, JAMB_N0, JAMB_N1);
            pair(b, f, rr, foot, rr, railY[1], JAMB, JAMB_N0, JAMB_N1);
            pair(b, f, l - JAMB, lintel, rr + JAMB, lintel, JAMB, JAMB_N0 - 0.02, JAMB_N1 + 0.02);
            ropes.add(new double[]{l, lintel, 0});
            ropes.add(new double[]{rr, lintel, 0});
            if (window) {
                double sill = PieceSolids.WINDOW_SILL - JAMB - 0.15;
                pair(b, f, l - JAMB, sill, rr + JAMB, sill, JAMB, JAMB_N0 - 0.02, JAMB_N1 + 0.02);
                ropes.add(new double[]{l, sill, 0});
                ropes.add(new double[]{rr, sill, 0});
            }
            panels.add(new double[]{us + 3.0, l - JAMB - 1.0});
            panels.add(new double[]{rr + JAMB + 1.0, ue - 3.0});
        } else {
            panels.add(new double[]{us + 4.2, ue - 4.2});
        }
        // one brace per panel and face, between the rails, rising towards the opening (opposite ways on the faces)
        for (int i = 0; i < panels.size(); i++) {
            double[] p = panels.get(i);
            if (p[1] - p[0] < 6) continue;
            boolean up = panels.size() == 1 || i == 0;
            double ya = railY[0];
            double yb = railY[1];
            double u0 = up ? p[0] : p[1];
            double u1 = up ? p[1] : p[0];
            stick(b, f, u0, ya, u1, yb, BRACE, BRACE_N0, BRACE_N1, true);
            stick(b, f, u1, ya, u0, yb, BRACE, -BRACE_N1, -BRACE_N0, true);
            // lashed where each brace meets the rails
            ropes.add(new double[]{u0, ya, 1});
            ropes.add(new double[]{u1, yb, 1});
            ropes.add(new double[]{u1, ya, -1});
            ropes.add(new double[]{u0, yb, -1});
        }
        MeshBuilder.Style rs = new MeshBuilder.Style(Mat.ROPE).scale(8, 8).edges(8, 8);
        for (double[] r : ropes) {
            if (r[2] >= 0) {
                b.box(f, r[0] - ROPE, r[0] + ROPE, ROPE_N0, ROPE_N1, r[1] - ROPE, r[1] + ROPE, rs, MeshBuilder.SKIP_NNEG);
            }
            if (r[2] <= 0) {
                b.box(f, r[0] - ROPE, r[0] + ROPE, -ROPE_N1, -ROPE_N0, r[1] - ROPE, r[1] + ROPE, rs, MeshBuilder.SKIP_NPOS);
            }
        }
    }

    /** u intervals of the profile rectangles that cover the whole band [ya, yb], merged where they touch. */
    static List<double[]> spans(List<double[]> prof, double ya, double yb) {
        List<double[]> in = new ArrayList<>();
        for (double[] r : prof) {
            if (r[1] <= ya + 1e-6 && r[3] >= yb - 1e-6) {
                in.add(new double[]{r[0], r[2]});
            }
        }
        in.sort((p, q) -> Double.compare(p[0], q[0]));
        List<double[]> out = new ArrayList<>();
        for (double[] s : in) {
            if (!out.isEmpty() && s[0] <= out.get(out.size() - 1)[1] + 1e-6) {
                double[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], s[1]);
            } else {
                out.add(s.clone());
            }
        }
        return out;
    }

    /** Parts of interval {@code s} not covered by {@code cover}. */
    static List<double[]> minus(double[] s, List<double[]> cover) {
        List<double[]> cur = new ArrayList<>();
        cur.add(s.clone());
        for (double[] c : cover) {
            List<double[]> next = new ArrayList<>();
            for (double[] a : cur) {
                if (c[1] <= a[0] || c[0] >= a[1]) {
                    next.add(a);
                    continue;
                }
                if (c[0] > a[0]) next.add(new double[]{a[0], c[0]});
                if (c[1] < a[1]) next.add(new double[]{c[1], a[1]});
            }
            cur = next;
        }
        cur.removeIf(a -> a[1] - a[0] < 1e-6);
        return cur;
    }

    static List<double[]> intersect(List<double[]> a, List<double[]> b) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : a) {
            for (double[] q : b) {
                double lo = Math.max(p[0], q[0]);
                double hi = Math.min(p[1], q[1]);
                if (hi - lo > 1e-6) out.add(new double[]{lo, hi});
            }
        }
        return out;
    }

    /**
     * One straw bundle over u in [ua, ub], y in [ya, yb]: a hexagonal prism (crown {@link #STRAW}, grooves
     * {@link #GROOVE}). The crown and bevels map one strip of the wall texture across the bundle; the top and bottom
     * faces are drawn over the given u intervals only (elsewhere another bundle lies on them), the ends where the
     * bundle meets an opening (the others are inside a corner post).
     */
    private static void bundle(MeshBuilder b, MeshBuilder.Frame f, double ua, double ub, double ya, double yb, int strip, double off,
                               boolean endA, boolean endB, List<double[]> top, List<double[]> bottom) {
        double bv = Math.min(BEVEL, (yb - ya) / 3);
        double t0 = strip / (double) STRIPS;
        double ts = 1.0 / STRIPS;
        double sa = (ua + off) / 48;
        double sb = (ub + off) / 48;
        // section from the top groove down to the bottom groove: (n, y, t)
        double[][] sec = {{GROOVE, yb, 0}, {STRAW, yb - bv, 0.22}, {STRAW, ya + bv, 0.78}, {GROOVE, ya, 1}};
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                double[] p = sec[i];
                double[] q = sec[i + 1];
                double[][] pts = {f.p(ua, side * p[0], p[1]), f.p(ub, side * p[0], p[1]), f.p(ub, side * q[0], q[1]), f.p(ua, side * q[0], q[1])};
                double[][] uv = {{sa, t0 + ts * p[2]}, {sb, t0 + ts * p[2]}, {sb, t0 + ts * q[2]}, {sa, t0 + ts * q[2]}};
                // outward: across the wall, tilted up or down on the bevels
                double oy = i == 0 ? 0.6 : i == 2 ? -0.6 : 0;
                float tone = i == 1 ? 1f : i == 0 ? 0.97f : 0.9f;
                b.poly(Mat.WALL, pts, uv, f.nx() * side, oy, f.nz() * side, tone, 0);
            }
        }
        for (double[] s : top) {
            capY(b, f, s[0], s[1], yb, 1, t0, ts, off);
        }
        for (double[] s : bottom) {
            capY(b, f, s[0], s[1], ya, -1, t0, ts, off);
        }
        for (int e = 0; e < 2; e++) {
            if (e == 0 ? !endA : !endB) continue;
            double u = e == 0 ? ua : ub;
            double[][] hex = {{GROOVE, yb}, {STRAW, yb - bv}, {STRAW, ya + bv}, {GROOVE, ya}, {-GROOVE, ya}, {-STRAW, ya + bv}, {-STRAW, yb - bv},
                {-GROOVE, yb}};
            double[][] pts = new double[hex.length][];
            double[][] uv = new double[hex.length][];
            double jitter = (strip * 0.37 + e * 0.21) % 1.0;
            for (int i = 0; i < hex.length; i++) {
                pts[i] = f.p(u, hex[i][0], hex[i][1]);
                uv[i] = new double[]{(hex[i][0] + 2.0) / 16 + jitter * 0.5, (48 - hex[i][1]) / 16};
            }
            double d = e == 0 ? -1 : 1;
            b.poly(Mat.STRAW_END, pts, uv, f.ax() * d, 0, f.az() * d, 0.95f, Double.NaN);
        }
    }

    /** Horizontal face of a bundle (top or bottom) over u in [ua, ub], |n| <= GROOVE. */
    private static void capY(MeshBuilder b, MeshBuilder.Frame f, double ua, double ub, double y, int dir, double t0, double ts, double off) {
        double[][] pts = {f.p(ua, -GROOVE, y), f.p(ub, -GROOVE, y), f.p(ub, GROOVE, y), f.p(ua, GROOVE, y)};
        double sa = (ua + off) / 48;
        double sb = (ub + off) / 48;
        double[][] uv = {{sa, t0 + ts * 0.3}, {sb, t0 + ts * 0.3}, {sb, t0 + ts * 0.7}, {sa, t0 + ts * 0.7}};
        b.poly(Mat.WALL, pts, uv, 0, dir, 0, dir > 0 ? 1f : 0.85f, Double.NaN);
    }

    /**
     * Ragged straw tufts along the top edge over u in [ua, ub]: small leaning blades in two rows, every one a little
     * different (deterministic), drawn from both sides, below the wall's nominal top.
     */
    private static void tufts(MeshBuilder b, MeshBuilder.Frame f, double ua, double ub, double y1, int seed) {
        long h = 0x9E3779B97F4A7C15L * (seed + 31);
        for (int row = 0; row < 2; row++) {
            double nRow = row == 0 ? 0.55 : -0.55;
            double u = ua + 0.2 + row * 0.7;
            while (u < ub - 0.8) {
                h = mix(h);
                double w = 1.0 + 0.8 * unit(h);
                double ht = 0.35 + (TUFT_MAX - 0.35) * unit(h >>> 13);
                double lean = (unit(h >>> 26) - 0.5) * 1.1;
                double tilt = (unit(h >>> 39) - 0.5) * 0.7;
                double u0 = u;
                double u1 = Math.min(ub, u + w);
                double base = y1 - 0.45;
                double au = Math.max(ua, Math.min(ub, (u0 + u1) / 2 + lean));
                double[][] tri = {f.p(u0, nRow, base), f.p(u1, nRow, base), f.p(au, nRow + tilt, y1 + ht - 0.45 + 0.4)};
                // texture: stalks run up the blade (s along y, t along u)
                // the middle rows of a strip, where the straw is lit (its edges are shaded round)
                double s0 = 0.1 + 0.8 * unit(h >>> 50);
                double tm = (2 + 0.42) / STRIPS;
                double[][] uv = {{s0, tm}, {s0, tm + w / 96}, {s0 + (ht + 0.4) / 48, tm + w / 192}};
                double side = row == 0 ? 1 : -1;
                b.poly(Mat.WALL, tri, uv, f.nx() * side, 0, f.nz() * side, 1f, Double.NaN);
                b.poly(Mat.WALL, tri, uv, -f.nx() * side, 0, -f.nz() * side, 0.88f, Double.NaN);
                u += w * (0.8 + 0.5 * unit(h >>> 7));
            }
        }
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static double unit(long h) {
        return (h & 0x1fff) / 8191.0;
    }

    /** A rail on each face of the wall at height y over u in [u0, u1]. */
    private static void rails(MeshBuilder b, MeshBuilder.Frame f, double u0, double u1, double y) {
        if (u1 - u0 < 1) return;
        stick(b, f, u0, y, u1, y, RAIL, RAIL_N0, RAIL_N1, true);
        stick(b, f, u0, y, u1, y, RAIL, -RAIL_N1, -RAIL_N0, true);
    }

    /** The same stick on both faces. */
    private static void pair(MeshBuilder b, MeshBuilder.Frame f, double u0, double y0, double u1, double y1, double hw, double n0, double n1) {
        stick(b, f, u0, y0, u1, y1, hw, n0, n1, true);
        stick(b, f, u0, y0, u1, y1, hw, -n1, -n0, true);
    }

    /**
     * Stick frame of a woven straw gate in its leaf frame (u from the hinge, leaf n across): stiles, rails and a brace on
     * both faces, lashed at the corners. On the stop side (-n) the frame keeps clear of the stops (1.25 px from the
     * jambs, under the head stop) and of the astragal of a double door.
     */
    static void doorFrame(MeshBuilder b, MeshBuilder.Frame lf, double w0, double width, double yb, double yt, boolean astragal) {
        double l = PieceSolids.LEAF_HALF;
        MeshBuilder.Style rs = new MeshBuilder.Style(Mat.ROPE).scale(8, 8).edges(8, 8);
        for (int side = -1; side <= 1; side += 2) {
            double inset = side > 0 ? 1.2 : 2.35;
            double hw = 0.8;
            double a = w0 + inset;
            double z = width - inset;
            double bot = 1.6;
            double topY = side > 0 ? yt - 2.0 : PieceSolids.DOOR_H - PieceMeshes.STOP - 1.4;
            double n0 = side > 0 ? l - 0.25 : -l - 0.8;
            double n1 = side > 0 ? l + 0.8 : -l + 0.25;
            stick(b, lf, a, 0.2, a, topY + hw, hw, n0, n1, true);
            stick(b, lf, z, 0.2, z, topY + hw, hw, n0, n1, true);
            stick(b, lf, a - hw, bot, z + hw, bot, hw, n0 - 0.03 * side, n1 + 0.03 * side, true);
            stick(b, lf, a - hw, topY, z + hw, topY, hw, n0 - 0.03 * side, n1 + 0.03 * side, true);
            // Z brace from the bottom of the hinge stile to the top of the free one
            stick(b, lf, a + 0.6, bot + 1.2, z - 0.6, topY - 1.2, 0.7, side > 0 ? n0 + 0.15 : n0 + 0.2, side > 0 ? n1 - 0.2 : n1 - 0.15, true);
            double rn0 = side > 0 ? l - 0.1 : -l - 1.0;
            double rn1 = side > 0 ? l + 1.0 : -l + 0.1;
            for (double[] p : new double[][]{{a, bot}, {z, bot}, {a, topY}, {z, topY}}) {
                b.box(lf, p[0] - 1.05, p[0] + 1.05, rn0, rn1, p[1] - 1.05, p[1] + 1.05, rs, side > 0 ? MeshBuilder.SKIP_NNEG : MeshBuilder.SKIP_NPOS);
            }
        }
    }

    /**
     * A square stick from (u0, y0) to (u1, y1) in the wall plane, half width {@code hw} across its axis, between n0 and
     * n1. Bark runs along it (two texels per px); the cut ends show end grain unless {@code ends} is false.
     */
    static void stick(MeshBuilder b, MeshBuilder.Frame f, double u0, double y0, double u1, double y1, double hw, double n0, double n1,
                      boolean ends) {
        double du = u1 - u0;
        double dy = y1 - y0;
        double len = Math.hypot(du, dy);
        if (len < 1e-6) return;
        double au = du / len;
        double ay = dy / len;
        // in-plane perpendicular, pointing "up" for a horizontal stick
        double pu = -ay;
        double py = au;
        if (py < 0 || (py == 0 && pu < 0)) {
            pu = -pu;
            py = -py;
        }
        double[][] q = {
            {u0 - pu * hw, y0 - py * hw}, {u1 - pu * hw, y1 - py * hw},
            {u1 + pu * hw, y1 + py * hw}, {u0 + pu * hw, y0 + py * hw}
        };
        // every stick a little different: its own shade and bark offset
        long h = Double.doubleToLongBits(u0 * 31.7 + y0 * 17.3 + u1 * 13.1 + y1 * 7.9 + hw + n0 * 3.3);
        h = mix(h);
        float shade = (float) (0.88 + 0.18 * ((h & 0xffff) / 65535.0));
        double s0 = ((h >>> 16) & 0xffff) / 65535.0;
        // bark at one texel per px along the stick: half the tile splits of the baked quads
        double s1 = s0 + len / 32;
        double w = 2 * hw / 16;
        double d = (n1 - n0) / 16;
        // the two faces across the wall (normal +n / -n)
        double[][] uvFace = {{s0, 0}, {s1, 0}, {s1, w}, {s0, w}};
        b.poly(Mat.BEAM, pts(f, q, n1), uvFace, f.nx(), 0, f.nz(), shade, 0);
        b.poly(Mat.BEAM, pts(f, q, n0), uvFace, -f.nx(), 0, -f.nz(), shade * 0.96f, 0);
        // the two faces along the stick's sides (normal -p / +p), lit by how much they face up
        double[] wn = world(f, pu, py);
        double[][] lowSide = {f.p(q[0][0], n0, q[0][1]), f.p(q[1][0], n0, q[1][1]), f.p(q[1][0], n1, q[1][1]), f.p(q[0][0], n1, q[0][1])};
        double[][] highSide = {f.p(q[3][0], n0, q[3][1]), f.p(q[2][0], n0, q[2][1]), f.p(q[2][0], n1, q[2][1]), f.p(q[3][0], n1, q[3][1])};
        double[][] uvSide = {{s0, 0}, {s1, 0}, {s1, d}, {s0, d}};
        b.poly(Mat.BEAM, highSide, uvSide, wn[0], wn[1], wn[2], shade * (float) (0.92 + 0.08 * py), 0);
        b.poly(Mat.BEAM, lowSide, uvSide, -wn[0], -wn[1], -wn[2], shade * (float) (0.86 - 0.06 * py), 0);
        if (ends) {
            double[] an = world(f, au, ay);
            double[][] e0 = {f.p(q[0][0], n0, q[0][1]), f.p(q[3][0], n0, q[3][1]), f.p(q[3][0], n1, q[3][1]), f.p(q[0][0], n1, q[0][1])};
            double[][] e1 = {f.p(q[1][0], n0, q[1][1]), f.p(q[2][0], n0, q[2][1]), f.p(q[2][0], n1, q[2][1]), f.p(q[1][0], n1, q[1][1])};
            // the pale middle of the cut-end texture
            double[][] uvEnd = {{0.5 - w / 2, 0.5 - d / 2}, {0.5 + w / 2, 0.5 - d / 2}, {0.5 + w / 2, 0.5 + d / 2}, {0.5 - w / 2, 0.5 + d / 2}};
            b.poly(Mat.POST_TOP, e0, uvEnd, -an[0], -an[1], -an[2], 0.95f, Double.NaN);
            b.poly(Mat.POST_TOP, e1, uvEnd, an[0], an[1], an[2], 0.95f, Double.NaN);
        }
    }

    private static double[][] pts(MeshBuilder.Frame f, double[][] q, double n) {
        double[][] out = new double[q.length][];
        for (int i = 0; i < q.length; i++) {
            out[i] = f.p(q[i][0], n, q[i][1]);
        }
        return out;
    }

    /** World direction of an in-plane (u, y) vector. */
    private static double[] world(MeshBuilder.Frame f, double u, double y) {
        return new double[]{f.ax() * u, y, f.az() * u};
    }
}
