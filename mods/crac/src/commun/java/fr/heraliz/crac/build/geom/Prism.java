package fr.heraliz.crac.build.geom;

/**
 * Vertical extrusion of a convex polygon {x, z}[] between {@code y0} and {@code y1}, in px. Collision solids of every
 * piece are lists of prisms: generated in the piece's local frame, then transformed to building px. Immutable.
 */
public final class Prism {
    private final double[][] poly;
    private final double y0;
    private final double y1;
    private final double minX;
    private final double minZ;
    private final double maxX;
    private final double maxZ;

    public Prism(double[][] poly, double y0, double y1) {
        this.poly = poly;
        this.y0 = y0;
        this.y1 = y1;
        double[] b = Poly.bounds(poly);
        this.minX = b[0];
        this.minZ = b[1];
        this.maxX = b[2];
        this.maxZ = b[3];
    }

    public static Prism box(double x0, double z0, double x1, double z1, double y0, double y1) {
        return new Prism(new double[][]{{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}, y0, y1);
    }

    /** The polygon; shared, never modify it. */
    public double[][] poly() {
        return this.poly;
    }

    public double y0() {
        return this.y0;
    }

    public double y1() {
        return this.y1;
    }

    public double minX() {
        return this.minX;
    }

    public double maxX() {
        return this.maxX;
    }

    public double minZ() {
        return this.minZ;
    }

    public double maxZ() {
        return this.maxZ;
    }

    /** This local prism in building px. */
    public Prism transform(Placement p) {
        return new Prism(p.apply(this.poly), p.y(this.y0), p.y(this.y1));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Prism[y ").append(this.y0).append("..").append(this.y1).append(':');
        for (double[] v : this.poly) {
            sb.append(" (").append(v[0]).append(", ").append(v[1]).append(')');
        }
        return sb.append(']').toString();
    }
}
