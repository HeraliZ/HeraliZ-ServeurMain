package fr.heraliz.crac.build.model;

/**
 * A lattice vertex of a building.
 *
 * @param origin building origin ({@code BlockPos.asLong})
 */
public record VertexKey(long origin, LPoint p) {
}
