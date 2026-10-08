package fr.heraliz.crac.build.model;

/**
 * An exact lattice point of a building: {@code x = 12 * (xa + xb * sqrt3)}, {@code z = 12 * (za + zb * sqrt3)} px in
 * the building frame (relative to the building origin). Lattice points are never produced by rounding doubles: only
 * by adding exact edge vectors ({@link Dirs#D}) to other lattice points.
 */
public record LPoint(int xa, int xb, int za, int zb) {
    public static final LPoint ORIGIN = new LPoint(0, 0, 0, 0);

    public static LPoint of(int[] v) {
        return new LPoint(v[0], v[1], v[2], v[3]);
    }

    public LPoint add(int[] v) {
        return new LPoint(this.xa + v[0], this.xb + v[1], this.za + v[2], this.zb + v[3]);
    }

    public LPoint add(LPoint o) {
        return new LPoint(this.xa + o.xa, this.xb + o.xb, this.za + o.za, this.zb + o.zb);
    }

    public LPoint sub(LPoint o) {
        return new LPoint(this.xa - o.xa, this.xb - o.xb, this.za - o.za, this.zb - o.zb);
    }

    /** This point moved by one 48 px edge in direction k. */
    public LPoint plus(int k) {
        return this.add(Dirs.d(k));
    }

    /** This point moved by half an edge (24 px) in direction k. */
    public LPoint plusHalf(int k) {
        return this.add(Dirs.h(k));
    }

    /** x in building px. */
    public double toX() {
        return this.xa * Dirs.UNIT + this.xb * Dirs.UNIT_R3;
    }

    /** z in building px. */
    public double toZ() {
        return this.za * Dirs.UNIT + this.zb * Dirs.UNIT_R3;
    }

    /** Largest absolute coordinate, for bounds checks. */
    public int maxAbs() {
        return Math.max(Math.max(Math.abs(this.xa), Math.abs(this.xb)), Math.max(Math.abs(this.za), Math.abs(this.zb)));
    }

    public int[] toArray() {
        return new int[]{this.xa, this.xb, this.za, this.zb};
    }
}
