package fr.heraliz.crac.build.model;

/**
 * A canonical 48 px lattice edge of a building, without height: it starts at {@code start} and runs in direction
 * {@code dir6} (0..5). An edge given from its other end (direction 6..11) is stored from the far end.
 *
 * @param origin building origin ({@code BlockPos.asLong}), so that two buildings never share keys
 */
public record EdgeKey(long origin, LPoint start, int dir6) {
    public static EdgeKey of(long origin, LPoint p, int k) {
        int d = Dirs.norm(k);
        return d < 6 ? new EdgeKey(origin, p, d) : new EdgeKey(origin, p.plus(d), d - 6);
    }

    public LPoint end() {
        return this.start.plus(this.dir6);
    }

    public LPoint mid() {
        return this.start.plusHalf(this.dir6);
    }

    public VertexKey startVertex() {
        return new VertexKey(this.origin, this.start);
    }

    public VertexKey endVertex() {
        return new VertexKey(this.origin, this.end());
    }
}
