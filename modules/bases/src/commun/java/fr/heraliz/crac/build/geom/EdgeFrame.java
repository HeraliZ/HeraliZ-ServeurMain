package fr.heraliz.crac.build.geom;

/**
 * Wall-local frame of a 48 px edge, expressed in the edge's local frame (see {@link Placement}: the canonical edge
 * runs from local (0, 0) to (48, 0), local v points to the side of direction {@code orient + 3}).
 * {@code u} runs along the wall (0..48), {@code n} across it (0 = wall plane), y up. {@code rot} bit 0 flips n (the
 * inside face, where doors swing), bit 1 mirrors u (hinge side), as in v2.
 */
public record EdgeFrame(double ou, double ov, double au, double av, double nu, double nv) {
    public static final double LENGTH = 48.0;

    public static EdgeFrame of(int rot) {
        double ou = 0;
        double au = 1;
        double nv = 1;
        if ((rot & 1) != 0) {
            nv = -1;
        }
        if ((rot & 2) != 0) {
            ou = LENGTH;
            au = -1;
        }
        return new EdgeFrame(ou, 0, au, 0, 0, nv);
    }

    /** Edge-local u of wall point (wu, n). */
    public double u(double wu, double n) {
        return this.ou + this.au * wu + this.nu * n;
    }

    /** Edge-local v of wall point (wu, n). */
    public double v(double wu, double n) {
        return this.ov + this.av * wu + this.nv * n;
    }

    /** Wall rectangle wu in [u0,u1], n in [n0,n1] as an edge-local polygon with a positive orientation. */
    public double[][] rect(double u0, double u1, double n0, double n1) {
        double[][] p = {
            {this.u(u0, n0), this.v(u0, n0)},
            {this.u(u1, n0), this.v(u1, n0)},
            {this.u(u1, n1), this.v(u1, n1)},
            {this.u(u0, n1), this.v(u0, n1)}
        };
        return Poly.positive(p);
    }

    /** Whether the inside face is flipped (bit 0). */
    public boolean flipped() {
        return this.nv < 0;
    }

    /** Whether the hinge is mirrored (bit 1). */
    public boolean mirrored() {
        return this.au < 0;
    }
}
