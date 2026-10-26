package fr.heraliz.crac.build.geom;

import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;

/**
 * Exact lattice predicates in Z[sqrt3] (see {@link Q3}). Values are in lattice units (12 px), so dot and cross
 * products are in units of 144 px². Touching (a shared edge or a shared vertex) is never an overlap.
 * Polygons are convex, given by their vertices in either turning order.
 */
public final class Exact {
    private Exact() {
    }

    /** {@code a . b} as {p, q}. */
    public static long[] dot(LPoint a, LPoint b) {
        return new long[]{
            Q3.mulP(a.xa(), a.xb(), b.xa(), b.xb()) + Q3.mulP(a.za(), a.zb(), b.za(), b.zb()),
            Q3.mulQ(a.xa(), a.xb(), b.xa(), b.xb()) + Q3.mulQ(a.za(), a.zb(), b.za(), b.zb())};
    }

    /** {@code p . D[k]} as {p, q}. */
    public static long[] dot(LPoint p, int k) {
        return dot(p, LPoint.of(Dirs.d(k)));
    }

    /** 2D cross product {@code a.x * b.z - a.z * b.x} as {p, q}. */
    public static long[] cross(LPoint a, LPoint b) {
        return new long[]{
            Q3.mulP(a.xa(), a.xb(), b.za(), b.zb()) - Q3.mulP(a.za(), a.zb(), b.xa(), b.xb()),
            Q3.mulQ(a.xa(), a.xb(), b.za(), b.zb()) - Q3.mulQ(a.za(), a.zb(), b.xa(), b.xb())};
    }

    /** Sign of {@code cross(b - a, c - a)}: +1 when c is on the +z side of a->b turning from +x, 0 when collinear. */
    public static int orient(LPoint a, LPoint b, LPoint c) {
        long[] v = cross(b.sub(a), c.sub(a));
        return Q3.sign(v[0], v[1]);
    }

    private static int cmp(long[] a, long[] b) {
        return Q3.compare(a[0], a[1], b[0], b[1]);
    }

    /** {min, max} of the projections of {@code pts} on {@code axis}, each as {p, q}. */
    private static long[][] project(LPoint[] pts, LPoint axis) {
        long[] min = null;
        long[] max = null;
        for (LPoint p : pts) {
            long[] d = dot(p, axis);
            if (min == null || cmp(d, min) < 0) min = d;
            if (max == null || cmp(d, max) > 0) max = d;
        }
        return new long[][]{min, max};
    }

    private static LPoint perp(LPoint a, LPoint b) {
        LPoint e = b.sub(a);
        return new LPoint(-e.za(), -e.zb(), e.xa(), e.xb());
    }

    /**
     * Whether the projections overlap the way two interiors must: open intervals with a common part of positive
     * length, or, for a degenerate (single value) interval, that value strictly inside the other interval.
     */
    private static boolean intervalsMeet(long[][] a, long[][] b) {
        boolean aPoint = cmp(a[0], a[1]) == 0;
        boolean bPoint = cmp(b[0], b[1]) == 0;
        if (aPoint && bPoint) {
            return false;
        }
        if (aPoint) {
            return cmp(b[0], a[0]) < 0 && cmp(a[0], b[1]) < 0;
        }
        if (bPoint) {
            return cmp(a[0], b[0]) < 0 && cmp(b[0], a[1]) < 0;
        }
        long[] lo = cmp(a[0], b[0]) >= 0 ? a[0] : b[0];
        long[] hi = cmp(a[1], b[1]) <= 0 ? a[1] : b[1];
        return cmp(lo, hi) < 0;
    }

    /** Separating axis test over the edge normals of both sets (polygons, or a segment as two points). */
    private static boolean sat(LPoint[] a, LPoint[] b) {
        for (int pass = 0; pass < 2; pass++) {
            LPoint[] src = pass == 0 ? a : b;
            int n = src.length == 2 ? 1 : src.length;
            for (int i = 0; i < n; i++) {
                LPoint axis = perp(src[i], src[(i + 1) % src.length]);
                if (!intervalsMeet(project(a, axis), project(b, axis))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the interiors of two convex lattice polygons overlap (touching edges or vertices do not). */
    public static boolean polygonsOverlap(LPoint[] a, LPoint[] b) {
        return sat(a, b);
    }

    /** Whether the open segment s0-s1 passes through the interior of the polygon (running along an edge does not). */
    public static boolean segmentCrossesPolygon(LPoint s0, LPoint s1, LPoint[] poly) {
        return sat(new LPoint[]{s0, s1}, poly);
    }

    /** Whether p lies strictly inside the segment a-b (not on an endpoint). */
    public static boolean onOpenSegment(LPoint p, LPoint a, LPoint b) {
        if (orient(a, b, p) != 0) {
            return false;
        }
        LPoint d = b.sub(a);
        long[] tp = dot(p.sub(a), d);
        long[] tb = dot(d, d);
        return Q3.sign(tp[0], tp[1]) > 0 && cmp(tp, tb) < 0;
    }

    /** Collinear segments sharing a part of positive length (equal segments included). */
    public static boolean collinearOverlap(LPoint a0, LPoint a1, LPoint b0, LPoint b1) {
        if (orient(a0, a1, b0) != 0 || orient(a0, a1, b1) != 0) {
            return false;
        }
        LPoint d = a1.sub(a0);
        long[] ta1 = dot(d, d);
        long[] tb0 = dot(b0.sub(a0), d);
        long[] tb1 = dot(b1.sub(a0), d);
        long[] zero = {0, 0};
        long[] bmin = cmp(tb0, tb1) <= 0 ? tb0 : tb1;
        long[] bmax = cmp(tb0, tb1) <= 0 ? tb1 : tb0;
        long[] lo = cmp(bmin, zero) >= 0 ? bmin : zero;
        long[] hi = cmp(bmax, ta1) <= 0 ? bmax : ta1;
        return cmp(lo, hi) < 0;
    }

    public static boolean sameSegment(LPoint a0, LPoint a1, LPoint b0, LPoint b1) {
        return (a0.equals(b0) && a1.equals(b1)) || (a0.equals(b1) && a1.equals(b0));
    }

    /** Proper crossing of the two open segments, or collinear overlap of positive length. */
    public static boolean segmentsOverlap(LPoint a0, LPoint a1, LPoint b0, LPoint b1) {
        int o1 = orient(a0, a1, b0);
        int o2 = orient(a0, a1, b1);
        if (o1 == 0 && o2 == 0) {
            return collinearOverlap(a0, a1, b0, b1);
        }
        int o3 = orient(b0, b1, a0);
        int o4 = orient(b0, b1, a1);
        return o1 * o2 < 0 && o3 * o4 < 0;
    }
}
