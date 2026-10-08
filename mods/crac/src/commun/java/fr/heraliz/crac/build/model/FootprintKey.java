package fr.heraliz.crac.build.model;

/**
 * A canonical square or triangle footprint of a building, without height.
 *
 * @param origin building origin ({@code BlockPos.asLong})
 * @param anchor canonical vertex (the one whose outgoing direction is {@code orient})
 * @param orient 0..2 for squares, 0..3 for triangles
 */
public record FootprintKey(long origin, PieceKind.Shape shape, LPoint anchor, int orient) {
}
