package fr.heraliz.crac.build.client.render;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.geom.EdgeFrame;

/**
 * Collects {@link MeshFace}s. Winding is derived from the outward normal each call is given, so callers never care
 * about vertex order. Texture mapping of vertical faces runs left to right as seen from outside. Coordinates are
 * {x, y, z} = local (u, y, v) of the piece (or building px for geometry tied to the block grid, such as skirts).
 */
final class MeshBuilder {
    final List<MeshFace> faces = new ArrayList<>();

    /** Horizontal frame: u along (ax, az), n along (nx, nz), y up; maps frame coordinates to piece pixels. */
    record Frame(double ox, double oz, double ax, double az, double nx, double nz) {
        static final Frame CELL = new Frame(0, 0, 1, 0, 0, 1);

        /** The wall frame of an edge in the edge's local frame (u along the edge, v across). */
        static Frame of(EdgeFrame f) {
            return new Frame(f.ou(), f.ov(), f.au(), f.av(), f.nu(), f.nv());
        }

        double x(double u, double n) {
            return this.ox + this.ax * u + this.nx * n;
        }

        double z(double u, double n) {
            return this.oz + this.az * u + this.nz * n;
        }

        double[] p(double u, double n, double y) {
            return new double[]{this.x(u, n), y, this.z(u, n)};
        }

        /** Frame whose origin is at (u, n) of this one, with the given axes expressed in this frame. */
        Frame sub(double u, double n, double au, double an, double bu, double bn) {
            return new Frame(this.x(u, n), this.z(u, n),
                this.ax * au + this.nx * an, this.az * au + this.nz * an,
                this.ax * bu + this.nx * bn, this.az * bu + this.nz * bn);
        }
    }

    /** How a box textures its faces. Scales are pixels per texture tile. */
    static final class Style {
        Mat side;
        Mat end;
        Mat top;
        Mat bottom;
        double sideScaleS = 48;
        double sideScaleT = 48;
        double endScale = 16;
        double capScale = 16;
        /** Height mapped to the top row of vertical textures. */
        double topRef = 48;
        /** Vertical faces start their texture at their own left edge instead of the piece origin. */
        boolean local;
        /** Grain of the ±n faces along y instead of along u. */
        boolean vertical;
        /** Caps map their long axis (u) to texture v. */
        boolean capAlongU = true;
        float tone = 1f;
        double contact = Double.NaN;

        Style(Mat all) {
            this.side = all;
            this.end = all;
            this.top = all;
            this.bottom = all;
        }

        Style sides(Mat side, Mat edges) {
            this.side = side;
            this.end = edges;
            this.top = edges;
            this.bottom = edges;
            return this;
        }

        Style scale(double s, double t) {
            this.sideScaleS = s;
            this.sideScaleT = t;
            return this;
        }

        Style edges(double endScale, double capScale) {
            this.endScale = endScale;
            this.capScale = capScale;
            return this;
        }

        Style top(double topRef) {
            this.topRef = topRef;
            return this;
        }

        Style local() {
            this.local = true;
            return this;
        }

        Style vertical() {
            this.vertical = true;
            return this;
        }

        Style contact(double y) {
            this.contact = y;
            return this;
        }

        Style tone(float t) {
            this.tone = t;
            return this;
        }
    }

    static final int SKIP_NPOS = 1;
    static final int SKIP_NNEG = 2;
    static final int SKIP_UPOS = 4;
    static final int SKIP_UNEG = 8;
    static final int SKIP_TOP = 16;
    static final int SKIP_BOTTOM = 32;

    // ------------------------------------------------------------------ raw faces

    /** Adds a planar polygon; reorders it so that its normal points along (ox, oy, oz). */
    void poly(Mat m, double[][] pts, double[][] uv, double ox, double oy, double oz, float tone, double contact) {
        int n = pts.length;
        double ax = 0;
        double ay = 0;
        double az = 0;
        for (int i = 0; i < n; i++) {
            double[] p = pts[i];
            double[] q = pts[(i + 1) % n];
            ax += (p[1] - q[1]) * (p[2] + q[2]);
            ay += (p[2] - q[2]) * (p[0] + q[0]);
            az += (p[0] - q[0]) * (p[1] + q[1]);
        }
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 2e-6) {
            return;
        }
        boolean flip = ax * ox + ay * oy + az * oz < 0;
        double[] pos = new double[n * 3];
        double[] tex = new double[n * 2];
        for (int i = 0; i < n; i++) {
            int j = flip ? n - 1 - i : i;
            pos[i * 3] = pts[j][0];
            pos[i * 3 + 1] = pts[j][1];
            pos[i * 3 + 2] = pts[j][2];
            tex[i * 2] = uv[j][0];
            tex[i * 2 + 1] = uv[j][1];
        }
        double s = flip ? -1 / len : 1 / len;
        this.faces.add(new MeshFace(m, pos, tex, ax * s, ay * s, az * s, tone, contact));
    }

    /**
     * Vertical face with horizontal outward normal (ox, oz): s runs left to right seen from outside, t down from
     * {@code topRef}. {@code local} starts s at the face's own left edge.
     */
    void vert(Mat m, double[][] pts, double ox, double oz, double scaleS, double scaleT, double topRef, boolean local, float tone, double contact) {
        double rx = oz;
        double rz = -ox;
        double min = 0;
        if (local) {
            min = Double.MAX_VALUE;
            for (double[] p : pts) min = Math.min(min, p[0] * rx + p[2] * rz);
        }
        double[][] uv = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            uv[i] = new double[]{(p[0] * rx + p[2] * rz - min) / scaleS, (topRef - p[1]) / scaleT};
        }
        this.poly(m, pts, uv, ox, 0, oz, tone, contact);
    }

    /** Vertical face whose texture grain runs along y: s = height, t = position along the face. */
    void vertRotated(Mat m, double[][] pts, double ox, double oz, double scale, double topRef, float tone, double contact) {
        double rx = oz;
        double rz = -ox;
        double[][] uv = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            uv[i] = new double[]{(topRef - p[1]) / scale, (p[0] * rx + p[2] * rz) / scale};
        }
        this.poly(m, pts, uv, ox, 0, oz, tone, contact);
    }

    /** Horizontal face facing up (dir 1) or down (-1): s along (ax, az), t along the perpendicular. */
    void horiz(Mat m, double[][] pts, int dir, double ax, double az, double scaleS, double scaleT, float tone) {
        double bx = -az;
        double bz = ax;
        double[][] uv = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i];
            uv[i] = new double[]{(p[0] * ax + p[2] * az) / scaleS, (p[0] * bx + p[2] * bz) / scaleT};
        }
        this.poly(m, pts, uv, 0, dir, 0, tone, Double.NaN);
    }

    // ------------------------------------------------------------------ boxes

    /** Box u in [u0,u1], n in [n0,n1], y in [y0,y1] of a frame. */
    void box(Frame f, double u0, double u1, double n0, double n1, double y0, double y1, Style s, int skip) {
        double[] a00 = {f.x(u0, n0), f.z(u0, n0)};
        double[] a10 = {f.x(u1, n0), f.z(u1, n0)};
        double[] a11 = {f.x(u1, n1), f.z(u1, n1)};
        double[] a01 = {f.x(u0, n1), f.z(u0, n1)};
        double nlx = f.nx();
        double nlz = f.nz();
        double alx = f.ax();
        double alz = f.az();
        if ((skip & SKIP_NPOS) == 0) this.side(s, a01, a11, y0, y1, nlx, nlz);
        if ((skip & SKIP_NNEG) == 0) this.side(s, a00, a10, y0, y1, -nlx, -nlz);
        if ((skip & SKIP_UPOS) == 0) this.end(s, a10, a11, y0, y1, alx, alz);
        if ((skip & SKIP_UNEG) == 0) this.end(s, a00, a01, y0, y1, -alx, -alz);
        double[][] top = {{a00[0], y1, a00[1]}, {a10[0], y1, a10[1]}, {a11[0], y1, a11[1]}, {a01[0], y1, a01[1]}};
        double[][] bot = {{a00[0], y0, a00[1]}, {a10[0], y0, a10[1]}, {a11[0], y0, a11[1]}, {a01[0], y0, a01[1]}};
        double cx = s.capAlongU ? nlx : alx;
        double cz = s.capAlongU ? nlz : alz;
        if ((skip & SKIP_TOP) == 0) this.horiz(s.top, top, 1, cx, cz, s.capScale, s.capScale, s.tone);
        if ((skip & SKIP_BOTTOM) == 0) this.horiz(s.bottom, bot, -1, cx, cz, s.capScale, s.capScale, s.tone * 0.92f);
    }

    private void side(Style s, double[] a, double[] b, double y0, double y1, double ox, double oz) {
        double[][] pts = {{a[0], y0, a[1]}, {b[0], y0, b[1]}, {b[0], y1, b[1]}, {a[0], y1, a[1]}};
        if (s.vertical) {
            this.vertRotated(s.side, pts, ox, oz, s.sideScaleS, s.topRef, s.tone, s.contact);
        } else {
            this.vert(s.side, pts, ox, oz, s.sideScaleS, s.sideScaleT, s.topRef, s.local, s.tone, s.contact);
        }
    }

    private void end(Style s, double[] a, double[] b, double y0, double y1, double ox, double oz) {
        double[][] pts = {{a[0], y0, a[1]}, {b[0], y0, b[1]}, {b[0], y1, b[1]}, {a[0], y1, a[1]}};
        this.vert(s.end, pts, ox, oz, s.endScale, s.endScale, s.topRef, false, s.tone, s.contact);
    }

    // ------------------------------------------------------------------ prisms

    /**
     * Vertical prism over a convex xz polygon. Sides use {@code side} with s along the edge (scaleS) and t down from
     * topRef (scaleT); caps are mapped in cell coordinates at {@code capScale}.
     */
    void prism(double[][] poly, double y0, double y1, Mat top, Mat bottom, Mat side, double scaleS, double scaleT, double topRef,
               double capScale, boolean drawTop, boolean drawBottom, float tone, double contact) {
        int n = poly.length;
        double area2 = 0;
        for (int i = 0; i < n; i++) {
            double[] p = poly[i];
            double[] q = poly[(i + 1) % n];
            area2 += p[0] * q[1] - q[0] * p[1];
        }
        double sign = area2 >= 0 ? 1 : -1;
        if (side != null) {
            for (int i = 0; i < n; i++) {
                double[] p = poly[i];
                double[] q = poly[(i + 1) % n];
                double ex = q[0] - p[0];
                double ez = q[1] - p[1];
                double len = Math.sqrt(ex * ex + ez * ez);
                if (len < 1e-6) continue;
                // outward normal of a counter-clockwise (x right, z down) polygon
                double ox = ez / len * sign;
                double oz = -ex / len * sign;
                double[][] pts = {{p[0], y0, p[1]}, {q[0], y0, q[1]}, {q[0], y1, q[1]}, {p[0], y1, p[1]}};
                this.vert(side, pts, ox, oz, scaleS, scaleT, topRef, false, tone, contact);
            }
        }
        if (drawTop) {
            double[][] pts = new double[n][];
            for (int i = 0; i < n; i++) pts[i] = new double[]{poly[i][0], y1, poly[i][1]};
            this.horiz(top, pts, 1, 1, 0, capScale, capScale, tone);
        }
        if (drawBottom) {
            double[][] pts = new double[n][];
            for (int i = 0; i < n; i++) pts[i] = new double[]{poly[i][0], y0, poly[i][1]};
            this.horiz(bottom, pts, -1, 1, 0, capScale, capScale, tone * 0.9f);
        }
    }
}
