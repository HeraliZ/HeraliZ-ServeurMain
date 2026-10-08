package fr.heraliz.crac.build.logic;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.FootprintKey;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;

/** Shared-edge queries used by supports, stability, placement rules and building splits. */
public final class Topology {
    private Topology() {
    }

    /**
     * The surface or volume of {@code kind} (square or triangle) on the other side of edge {@code edge} of the
     * footprint of {@code piece}, at height {@code h}: it shares that exact edge, so it attaches.
     */
    public static PieceKey across(PieceKey piece, int edge, PieceKind kind, int h) {
        LPoint v = piece.lattice()[edge];
        int d = piece.edgeDir(edge);
        return new PieceKey(kind, piece.origin(), v.plus(d), h, d + 6, 0);
    }

    /** A wall of {@code kind} on edge {@code edge} of the footprint of {@code piece} ({@code rot} in that edge's frame). */
    public static PieceKey wallOn(PieceKey piece, int edge, PieceKind kind, int h, int rot) {
        return PieceKey.wall(kind, piece.origin(), piece.lattice()[edge], piece.edgeDir(edge), h, rot);
    }

    /** Surfaces that can lean on each other sideways (cantilever). Foundations are grounded and never need it. */
    public static boolean canCantilever(PieceKind kind) {
        return kind == PieceKind.FLOOR || kind == PieceKind.TRI_FLOOR || kind.isRoof();
    }

    /** Height h of a wall's top: a full wall at h - 2 or a half wall at h - 1 both end at h. */
    public static int wallTop(PieceKey wall) {
        return wall.top();
    }

    /** Walls on {@code edge} whose top is {@code top}. */
    public static List<Piece> wallsWithTop(StructureView view, EdgeKey edge, int top) {
        List<Piece> out = new ArrayList<>(2);
        for (Piece w : view.wallsOnEdge(edge)) {
            if (w.key().top() == top) {
                out.add(w);
            }
        }
        return out;
    }

    /** Surfaces at the same h sharing an edge with the footprint of {@code key} (not on that same footprint). */
    public static List<Piece> surfacesAcross(StructureView view, PieceKey key) {
        List<Piece> out = new ArrayList<>(4);
        FootprintKey own = key.footprint();
        for (EdgeKey e : key.edges()) {
            for (Piece s : view.surfacesOnEdge(e, key.h())) {
                if (!s.key().footprint().equals(own) && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /**
     * Foundations at h = 0 sharing an edge with the footprint of {@code key}: the buildings a new foundation attaches
     * to, and the ground adjacency of a split.
     */
    public static List<Piece> foundationsSharingEdge(StructureView view, PieceKey key) {
        List<Piece> out = new ArrayList<>(4);
        FootprintKey own = key.footprint();
        for (EdgeKey e : key.edges()) {
            for (Piece s : view.surfacesOnEdge(e, 0)) {
                if (s.kind().isFoundation() && !s.key().footprint().equals(own) && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Walkable surfaces at the volume's height with exactly its footprint (what stairs and ramps stand on). */
    public static List<Piece> walkableUnder(StructureView view, PieceKey volume) {
        List<Piece> out = new ArrayList<>(1);
        for (Piece s : view.withFootprint(volume.footprint())) {
            if (s.key().h() == volume.h() && s.kind().isWalkableSurface()) {
                out.add(s);
            }
        }
        return out;
    }
}
