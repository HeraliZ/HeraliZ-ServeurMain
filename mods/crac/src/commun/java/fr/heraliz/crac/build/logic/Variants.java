package fr.heraliz.crac.build.logic;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.geom.RoofField;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.VertexKey;
import org.jetbrains.annotations.Nullable;

/**
 * Neighbour-dependent shapes ({@code Piece.variant}).
 * <ul>
 * <li>Walls share corner posts: for each wall end and each 24 px band the wall covers, exactly one of the walls ending
 * at that vertex and covering that band draws the post (highest tier, then lowest id). Bits: 0 = end A lower band,
 * 1 = end A upper band, 2 = end B lower, 3 = end B upper; end A is u = 0 of the unmirrored frame (the anchor).</li>
 * <li>Roofs: the sector masks of {@link RoofField}, from the roofs of the same building at the same h sharing a
 * vertex.</li>
 * </ul>
 */
public final class Variants {
    public static final int POST_A_LOW = 1;
    public static final int POST_A_HIGH = 1 << 1;
    public static final int POST_B_LOW = 1 << 2;
    public static final int POST_B_HIGH = 1 << 3;

    private Variants() {
    }

    public static long of(StructureView view, Piece piece) {
        if (piece.kind().isRoof()) {
            return roofVariant(view, piece.key(), piece.buildingId);
        }
        if (piece.kind().isWall()) {
            return wallPosts(view, piece);
        }
        return 0L;
    }

    // ---------------------------------------------------------------- roofs

    /**
     * Sector masks of a (possibly not yet placed) roof of building {@code buildingId}: its own sectors plus those of
     * the roofs of that building at the same h sharing each of its vertices.
     */
    public static long roofVariant(StructureView view, PieceKey roof, long buildingId) {
        List<PieceKey> around = new ArrayList<>();
        around.add(roof);
        for (Piece r : roofsSharingVertex(view, roof, buildingId)) {
            around.add(r.key());
        }
        return RoofField.variant(roof, around);
    }

    /** Roofs of the building at the same h having one of the vertices of {@code roof} (itself included when placed). */
    public static List<Piece> roofsSharingVertex(StructureView view, PieceKey roof, long buildingId) {
        List<Piece> out = new ArrayList<>();
        for (VertexKey v : roof.vertices()) {
            for (Piece p : view.atVertex(v)) {
                if (p.kind().isRoof() && p.key().h() == roof.h() && p.buildingId == buildingId && !out.contains(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Roofs whose variant may change when the roof {@code key} of {@code buildingId} is added or removed. */
    public static List<Piece> roofsAround(StructureView view, PieceKey key, long buildingId) {
        return roofsSharingVertex(view, key, buildingId);
    }

    // ---------------------------------------------------------------- walls

    /** Every wall ending at the vertex. */
    public static List<Piece> wallsAt(StructureView view, VertexKey v) {
        List<Piece> out = new ArrayList<>(6);
        for (Piece p : view.atVertex(v)) {
            if (p.kind().isWall()) {
                out.add(p);
            }
        }
        return out;
    }

    /** The wall drawing the post of a vertex for the 24 px band {@code band} (absolute h): highest tier, then oldest. */
    @Nullable
    public static Piece postOwner(List<Piece> walls, int band) {
        Piece best = null;
        for (Piece w : walls) {
            if (w.key().h() > band || w.key().top() <= band) continue;
            if (best == null || w.tier.ordinal() > best.tier.ordinal() || (w.tier == best.tier && w.id < best.id)) {
                best = w;
            }
        }
        return best;
    }

    public static long wallPosts(StructureView view, Piece wall) {
        PieceKey k = wall.key();
        List<VertexKey> ends = k.vertices();
        long bits = 0;
        for (int e = 0; e < 2; e++) {
            List<Piece> walls = wallsAt(view, ends.get(e));
            for (int b = 0; b < k.topUnits(); b++) {
                Piece owner = postOwner(walls, k.h() + b);
                if (owner != null && owner.id == wall.id) {
                    bits |= 1L << (2 * e + b);
                }
            }
        }
        return bits;
    }

    /** Walls whose posts may change when the wall {@code key} changes: every wall sharing one of its ends. */
    public static List<Piece> wallsAround(StructureView view, PieceKey key) {
        List<Piece> out = new ArrayList<>();
        for (VertexKey v : key.vertices()) {
            for (Piece w : wallsAt(view, v)) {
                if (!out.contains(w)) {
                    out.add(w);
                }
            }
        }
        return out;
    }
}
