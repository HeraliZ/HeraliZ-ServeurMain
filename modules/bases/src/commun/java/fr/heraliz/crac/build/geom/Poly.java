package fr.heraliz.crac.build.geom;

import java.util.ArrayList;
import java.util.List;

/** Small convex-polygon toolkit in the horizontal plane. Polygons are arrays of {x, z} (or {u, v} in a local frame). */
public final class Poly {
    public static final double EPS = 1e-6;

    private Poly() {
    }

    /** Signed area: positive when the vertices turn from +x towards +z (the footprint order). */
    public static double signedArea(double[][] poly) {
        if (poly == null || poly.length < 3) {
            return 0;
        }
        double a = 0;
        for (int i = 0; i < poly.length; i++) {
            double[] p = poly[i];
            double[] q = poly[(i + 1) % poly.length];
            a += p[0] * q[1] - q[0] * p[1];
        }
        return a * 0.5;
    }

    public static double area(double[][] poly) {
        return Math.abs(signedArea(poly));
    }

    public static double[] centroid(double[][] poly) {
        double x = 0;
        double z = 0;
        for (double[] p : poly) {
            x += p[0];
            z += p[1];
        }
        return new double[]{x / poly.length, z / poly.length};
    }

    /** The same polygon turning the other way. */
    public static double[][] reverse(double[][] poly) {
        double[][] out = new double[poly.length][];
        for (int i = 0; i < poly.length; i++) {
            out[i] = poly[poly.length - 1 - i];
        }
        return out;
    }

    /** The polygon with a positive signed area. */
    public static double[][] positive(double[][] poly) {
        return signedArea(poly) < 0 ? reverse(poly) : poly;
    }

    /** Sutherland-Hodgman clip against x >= bound (keepGreater) or x <= bound, on axis 0 (x) or 1 (z). */
    private static double[][] clipAxis(double[][] poly, int axis, double bound, boolean keepGreater) {
        if (poly == null || poly.length == 0) {
            return null;
        }
        List<double[]> out = new ArrayList<>(poly.length + 2);
        for (int i = 0; i < poly.length; i++) {
            double[] cur = poly[i];
            double[] prev = poly[(i + poly.length - 1) % poly.length];
            boolean curIn = keepGreater ? cur[axis] >= bound - EPS : cur[axis] <= bound + EPS;
            boolean prevIn = keepGreater ? prev[axis] >= bound - EPS : prev[axis] <= bound + EPS;
            if (curIn) {
                if (!prevIn) {
                    out.add(intersect(prev, cur, axis, bound));
                }
                out.add(cur);
            } else if (prevIn) {
                out.add(intersect(prev, cur, axis, bound));
            }
        }
        return out.size() < 3 ? null : out.toArray(new double[0][]);
    }

    private static double[] intersect(double[] a, double[] b, int axis, double bound) {
        double t = (bound - a[axis]) / (b[axis] - a[axis]);
        double[] p = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
        p[axis] = bound;
        return p;
    }

    /** Clip a convex polygon to the rectangle [x0,x1] x [z0,z1]; null when nothing is left. */
    public static double[][] clipRect(double[][] poly, double x0, double z0, double x1, double z1) {
        double[][] p = clipAxis(poly, 0, x0, true);
        p = clipAxis(p, 0, x1, false);
        p = clipAxis(p, 1, z0, true);
        p = clipAxis(p, 1, z1, false);
        return p;
    }

    /**
     * Clip a convex polygon to the half-plane {@code a x + b z + c >= 0} (exactly; no tolerance), null when nothing
     * of positive extent is left.
     */
    public static double[][] clipHalfPlane(double[][] poly, double a, double b, double c) {
        if (poly == null || poly.length == 0) {
            return null;
        }
        List<double[]> out = new ArrayList<>(poly.length + 2);
        for (int i = 0; i < poly.length; i++) {
            double[] cur = poly[i];
            double[] prev = poly[(i + poly.length - 1) % poly.length];
            double fc = a * cur[0] + b * cur[1] + c;
            double fp = a * prev[0] + b * prev[1] + c;
            if (fc >= 0) {
                if (fp < 0 && fc > 0) {
                    addPoint(out, lerp(prev, cur, fp / (fp - fc)));
                }
                addPoint(out, cur);
            } else if (fp > 0) {
                addPoint(out, lerp(prev, cur, fp / (fp - fc)));
            }
        }
        if (out.size() > 1 && same(out.get(0), out.get(out.size() - 1))) {
            out.remove(out.size() - 1);
        }
        return out.size() < 3 ? null : out.toArray(new double[0][]);
    }

    private static boolean same(double[] p, double[] q) {
        return Math.abs(p[0] - q[0]) <= 1e-12 && Math.abs(p[1] - q[1]) <= 1e-12;
    }

    private static void addPoint(List<double[]> out, double[] p) {
        if (out.isEmpty() || !same(out.get(out.size() - 1), p)) {
            out.add(p);
        }
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    /** Bounds {minX, minZ, maxX, maxZ}. */
    public static double[] bounds(double[][] poly) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : poly) {
            b[0] = Math.min(b[0], p[0]);
            b[1] = Math.min(b[1], p[1]);
            b[2] = Math.max(b[2], p[0]);
            b[3] = Math.max(b[3], p[1]);
        }
        return b;
    }

    /** True when the polygon fills its own bounding box (axis-aligned rectangle). */
    public static boolean isAxisRect(double[][] poly) {
        double[] b = bounds(poly);
        return Math.abs(area(poly) - (b[2] - b[0]) * (b[3] - b[1])) < 1e-4;
    }

    /** Point inside or on the boundary (within {@link #EPS}) of a convex polygon of either orientation. */
    public static boolean contains(double[][] poly, double x, double z) {
        boolean pos = false;
        boolean neg = false;
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i];
            double[] b = poly[(i + 1) % poly.length];
            double cross = (b[0] - a[0]) * (z - a[1]) - (b[1] - a[1]) * (x - a[0]);
            if (cross > EPS) pos = true;
            if (cross < -EPS) neg = true;
            if (pos && neg) return false;
        }
        return true;
    }

    public static double[][] translate(double[][] poly, double dx, double dz) {
        double[][] out = new double[poly.length][];
        for (int i = 0; i < poly.length; i++) {
            out[i] = new double[]{poly[i][0] + dx, poly[i][1] + dz};
        }
        return out;
    }

    /** Rotate around the square centre (24, 24) by {@code quarterTurns} (u towards v), {@code (u, v) -> (48 - v, u)}. */
    public static double[][] rotateCell(double[][] poly, int quarterTurns) {
        int r = Math.floorMod(quarterTurns, 4);
        if (r == 0) {
            return poly;
        }
        double[][] out = new double[poly.length][];
        for (int i = 0; i < poly.length; i++) {
            double x = poly[i][0];
            double z = poly[i][1];
            for (int k = 0; k < r; k++) {
                double nx = 48.0 - z;
                z = x;
                x = nx;
            }
            out[i] = new double[]{x, z};
        }
        return out;
    }

    /** Mirror {@code u -> 48 - u} (reverses the vertex order to keep the orientation). */
    public static double[][] mirrorCell(double[][] poly) {
        double[][] out = new double[poly.length][];
        for (int i = 0; i < poly.length; i++) {
            double[] p = poly[poly.length - 1 - i];
            out[i] = new double[]{48.0 - p[0], p[1]};
        }
        return out;
    }

    /**
     * The z interval {zmin, zmax} of a convex polygon on the vertical line x = {@code x} (axis 0), or the x interval
     * on the line z = {@code x} (axis 1); null when the line misses the polygon.
     */
    public static double[] crossSection(double[][] poly, int axis, double x) {
        int other = 1 - axis;
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        boolean hit = false;
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i];
            double[] b = poly[(i + 1) % poly.length];
            double a0 = a[axis];
            double b0 = b[axis];
            if ((a0 - x) * (b0 - x) > 0) {
                continue;
            }
            if (a0 == b0) {
                lo = Math.min(lo, Math.min(a[other], b[other]));
                hi = Math.max(hi, Math.max(a[other], b[other]));
            } else {
                double t = (x - a0) / (b0 - a0);
                double v = a[other] + (b[other] - a[other]) * t;
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
            hit = true;
        }
        return hit ? new double[]{lo, hi} : null;
    }

    // ---------------------------------------------------------------- distances and overlap (doubles)

    /** Squared distance between segments p0-p1 and q0-q1 (0 when they cross). */
    public static double segmentDistanceSq(double[] p0, double[] p1, double[] q0, double[] q1) {
        if (segmentsCross(p0, p1, q0, q1)) {
            return 0;
        }
        return Math.min(Math.min(pointSegmentSq(p0, q0, q1), pointSegmentSq(p1, q0, q1)),
            Math.min(pointSegmentSq(q0, p0, p1), pointSegmentSq(q1, p0, p1)));
    }

    private static boolean segmentsCross(double[] a, double[] b, double[] c, double[] d) {
        double d1 = cross(a, b, c);
        double d2 = cross(a, b, d);
        double d3 = cross(c, d, a);
        double d4 = cross(c, d, b);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    public static double pointSegmentSq(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double len = dx * dx + dz * dz;
        double t = len <= 0 ? 0 : ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / len;
        t = Math.max(0, Math.min(1, t));
        double ex = a[0] + dx * t - p[0];
        double ez = a[1] + dz * t - p[1];
        return ex * ex + ez * ez;
    }

    /**
     * Squared distance between two convex outlines (polygons, or segments given as two points), measured between
     * their boundaries: callers rule out overlapping interiors first.
     */
    public static double outlineDistanceSq(double[][] a, double[][] b) {
        double best = Double.MAX_VALUE;
        int na = a.length == 2 ? 1 : a.length;
        int nb = b.length == 2 ? 1 : b.length;
        for (int i = 0; i < na; i++) {
            double[] a0 = a[i];
            double[] a1 = a[(i + 1) % a.length];
            for (int j = 0; j < nb; j++) {
                best = Math.min(best, segmentDistanceSq(a0, a1, b[j], b[(j + 1) % b.length]));
            }
        }
        return best;
    }

    /**
     * Whether the interiors of two convex polygons overlap by more than {@code tol} along every separating axis
     * candidate (the edge normals of both).
     */
    public static boolean overlaps(double[][] a, double[][] b, double tol) {
        return !separated(a, b, tol) && !separated(b, a, tol);
    }

    private static boolean separated(double[][] src, double[][] other, double tol) {
        for (int i = 0; i < src.length; i++) {
            double[] p = src[i];
            double[] q = src[(i + 1) % src.length];
            double nx = q[1] - p[1];
            double nz = p[0] - q[0];
            // tolerance scaled by the larger normal component (no square root: geometry stays + - * / only)
            double scale = Math.max(Math.abs(nx), Math.abs(nz));
            if (scale < 1e-12) continue;
            nx /= scale;
            nz /= scale;
            double amin = Double.MAX_VALUE;
            double amax = -Double.MAX_VALUE;
            for (double[] v : src) {
                double d = v[0] * nx + v[1] * nz;
                amin = Math.min(amin, d);
                amax = Math.max(amax, d);
            }
            double bmin = Double.MAX_VALUE;
            double bmax = -Double.MAX_VALUE;
            for (double[] v : other) {
                double d = v[0] * nx + v[1] * nz;
                bmin = Math.min(bmin, d);
                bmax = Math.max(bmax, d);
            }
            if (amax <= bmin + tol || bmax <= amin + tol) {
                return true;
            }
        }
        return false;
    }
}
