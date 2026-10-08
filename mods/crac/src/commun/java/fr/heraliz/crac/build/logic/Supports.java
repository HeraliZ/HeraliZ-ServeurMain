package fr.heraliz.crac.build.logic;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;

/**
 * Which existing pieces carry a given piece, Rust style. Supports are only ever found by exact key equality (edge,
 * footprint, height and building origin), which is what stops arbitrary lattice coordinates from attaching anywhere.
 */
public final class Supports {
    public enum Link {
        /** Standing directly on a walkable surface (wall on a foundation, stairs on a floor). */
        DIRECT,
        /** Resting on the top of the wall below (floor on walls, wall stacked on a wall or a half wall). */
        VERTICAL,
        /** Leaning on a neighbouring floor or roof at the same height. */
        CANTILEVER
    }

    public record Support(Piece piece, Link link) {
    }

    private Supports() {
    }

    /** Supporters of a (possibly not yet placed) piece. Foundations have none: they stand on the ground. */
    public static List<Support> of(StructureView view, PieceKey key) {
        List<Support> out = new ArrayList<>(4);
        PieceKind kind = key.kind();
        switch (kind.family) {
            case WALL -> {
                EdgeKey e = key.edgeKey();
                for (Piece s : view.surfacesOnEdge(e, key.h())) {
                    if (s.kind().isWalkableSurface()) {
                        out.add(new Support(s, Link.DIRECT));
                    }
                }
                for (Piece w : Topology.wallsWithTop(view, e, key.h())) {
                    out.add(new Support(w, Link.VERTICAL));
                }
            }
            case SURFACE -> {
                if (kind.isFoundation()) {
                    break;
                }
                for (EdgeKey e : key.edges()) {
                    for (Piece w : Topology.wallsWithTop(view, e, key.h())) {
                        out.add(new Support(w, Link.VERTICAL));
                    }
                }
                if (Topology.canCantilever(kind)) {
                    for (Piece s : Topology.surfacesAcross(view, key)) {
                        if (Topology.canCantilever(s.kind()) && cantileverAllowed(kind, s.kind())) {
                            out.add(new Support(s, Link.CANTILEVER));
                        }
                    }
                }
            }
            case VOLUME -> {
                for (Piece s : Topology.walkableUnder(view, key)) {
                    out.add(new Support(s, Link.DIRECT));
                }
            }
        }
        return out;
    }

    /** Roofs (either shape) may lean on floors and roofs; floors only on floors (a floor never hangs off a roof). */
    public static boolean cantileverAllowed(PieceKind placed, PieceKind neighbour) {
        return placed.isRoof() || !neighbour.isRoof();
    }

    public static boolean isGrounded(PieceKey key) {
        return key.kind().isFoundation();
    }
}
