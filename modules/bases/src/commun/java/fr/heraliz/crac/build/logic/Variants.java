package fr.heraliz.crac.build.logic;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.geom.Rampes;
import fr.heraliz.crac.build.geom.RoofField;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKind;
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
 * <li>Ramps: the joined side edges ({@link Rampes}), one bit per footprint edge.</li>
 * </ul>
 */
public final class Variants {
    public static final int POST_A_LOW = 1;
    public static final int POST_A_HIGH = 1 << 1;
    public static final int POST_B_LOW = 1 << 2;
    public static final int POST_B_HIGH = 1 << 3;
    /**
     * Demi-mur chargé (HeraliZ, 26/10) : un plancher, une rampe ou un mur repose sur son dessus, il fait 24 px ; sinon
     * {@link fr.heraliz.crac.build.geom.PieceSolids#HALF_WALL_LOW}.
     */
    public static final int HALF_LOADED = 1 << 4;

    private Variants() {
    }

    public static long of(StructureView view, Piece piece) {
        if (piece.kind().isRoof()) {
            return roofVariant(view, piece.key(), piece.buildingId);
        }
        if (piece.kind() == PieceKind.HALF_WALL) {
            return wallPosts(view, piece) | (loaded(view, piece.key()) ? HALF_LOADED : 0);
        }
        if (piece.kind().isWall()) {
            return wallPosts(view, piece);
        }
        if (piece.kind().isRamp()) {
            return rampJoins(view, piece);
        }
        return 0L;
    }

    // ---------------------------------------------------------------- half walls (HeraliZ, 26/10)

    /** Whether something rests on the top of the half wall {@code wall}: a surface, a wall or a ramp starting there. */
    public static boolean loaded(StructureView view, PieceKey wall) {
        EdgeKey e = wall.edgeKey();
        int top = wall.top();
        if (!view.surfacesOnEdge(e, top).isEmpty()) {
            return true;
        }
        for (Piece w : view.wallsOnEdge(e)) {
            if (w.key().h() == top) {
                return true;
            }
        }
        for (Piece r : rampsOnWall(view, wall)) {
            if (r.key().h() == top) {
                return true;
            }
        }
        return false;
    }

    /** Half walls whose top {@code key} rests on (their height may change when it comes or goes). */
    public static List<Piece> halfWallsUnder(StructureView view, PieceKey key) {
        List<Piece> out = new ArrayList<>(2);
        List<EdgeKey> edges = key.kind().isWall() ? List.of(key.edgeKey()) : key.edges();
        for (EdgeKey e : edges) {
            for (Piece w : view.wallsOnEdge(e)) {
                if (w.kind() == PieceKind.HALF_WALL && w.key().top() == key.h() && !out.contains(w)) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- ramps (HeraliZ, 26/10)

    /**
     * Côtés latéraux d'une rampe raccordés à une rampe voisine ({@link Rampes}) : la voisine partage exactement ce côté,
     * c'en est aussi un côté latéral avec le même bout bas, même niveau, même montée (pleine ou demie), même palier, même
     * bâtiment, et aucun mur ne coupe ce côté sur la hauteur des rampes. Un bit par côté du contour.
     */
    public static long rampJoins(StructureView view, Piece ramp) {
        PieceKey k = ramp.key();
        List<EdgeKey> edges = k.edges();
        long bits = 0;
        for (int c : Rampes.cotesLateraux(k)) {
            if (joinedTo(view, ramp, edges.get(c), Rampes.pointBas(k, c)) != null) {
                bits |= 1L << c;
            }
        }
        return bits;
    }

    /** La rampe raccordée à {@code ramp} par le côté {@code edge} (bout bas {@code low}), ou null. */
    @Nullable
    private static Piece joinedTo(StructureView view, Piece ramp, EdgeKey edge, LPoint low) {
        PieceKey k = ramp.key();
        for (Piece w : view.wallsOnEdge(edge)) {
            if (w.key().h() < k.top() && w.key().top() > k.h()) {
                return null;
            }
        }
        for (Piece n : rampsAcross(view, k, edge)) {
            if (n.id == ramp.id || n.tier != ramp.tier || n.buildingId != ramp.buildingId || n.key().h() != k.h()
                || n.key().halfRamp() != k.halfRamp()) {
                continue;
            }
            List<EdgeKey> ne = n.key().edges();
            for (int c : Rampes.cotesLateraux(n.key())) {
                if (ne.get(c).equals(edge) && Rampes.pointBas(n.key(), c).equals(low)) {
                    return n;
                }
            }
        }
        return null;
    }

    /** Ramps (square or triangle, any height) whose footprint lies across {@code edge} from the footprint of {@code key}. */
    private static List<Piece> rampsAcross(StructureView view, PieceKey key, EdgeKey edge) {
        List<Piece> out = new ArrayList<>(2);
        List<EdgeKey> edges = key.edges();
        for (int e = 0; e < edges.size(); e++) {
            if (!edges.get(e).equals(edge)) continue;
            for (PieceKind kind : new PieceKind[]{PieceKind.RAMP, PieceKind.TRI_RAMP}) {
                PieceKey across = Topology.across(key, e, kind, key.h());
                for (Piece p : view.withFootprint(across.footprint())) {
                    if (p.kind().isRamp() && !out.contains(p)) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Rampes dont le raccord peut changer quand {@code key} change : pour une rampe, celles de l'autre côté de ses côtés ;
     * pour un mur, celles des deux côtés de son bord.
     */
    public static List<Piece> rampsAround(StructureView view, PieceKey key) {
        List<Piece> out = new ArrayList<>();
        if (key.kind().isRamp()) {
            for (EdgeKey e : key.edges()) {
                for (Piece p : rampsAcross(view, key, e)) {
                    if (!out.contains(p)) out.add(p);
                }
            }
        } else if (key.kind().isWall()) {
            out.addAll(rampsOnWall(view, key));
        }
        return out;
    }

    /** Ramps (any height) having the wall's edge as one of their edges, on either side of it. */
    private static List<Piece> rampsOnWall(StructureView view, PieceKey wall) {
        List<Piece> out = new ArrayList<>(2);
        EdgeKey e = wall.edgeKey();
        for (PieceKind kind : new PieceKind[]{PieceKind.RAMP, PieceKind.TRI_RAMP}) {
            for (PieceKey side : new PieceKey[]{
                new PieceKey(kind, wall.origin(), wall.anchor(), wall.h(), wall.orient(), 0),
                new PieceKey(kind, wall.origin(), wall.anchor().plus(wall.orient()), wall.h(), wall.orient() + 6, 0)}) {
                for (Piece p : view.withFootprint(side.footprint())) {
                    if (p.kind().isRamp() && !out.contains(p) && p.key().edges().contains(e)) out.add(p);
                }
            }
        }
        return out;
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
