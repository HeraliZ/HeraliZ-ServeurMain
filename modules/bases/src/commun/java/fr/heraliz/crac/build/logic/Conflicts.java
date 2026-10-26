package fr.heraliz.crac.build.logic;

import java.util.List;
import fr.heraliz.crac.build.geom.Exact;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.geom.Prism;
import fr.heraliz.crac.build.logic.PlacementRules.Result;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Space rules between the pieces of one building (pieces of other buildings are refused block by block). Each piece
 * has a plan region (footprint polygon, or the wall's segment) and a vertical extent; the tests below run in this
 * order and the first failure wins:
 * <ol>
 * <li>exact overlap (Z[sqrt3]): extents overlapping by more than 0 and plan interiors overlapping: OCCUPIED, or
 * STAIRWELL / BLOCKED_ABOVE between surfaces and volumes (a volume's extent against surfaces is
 * {@code [0, HEADROOM]});</li>
 * <li>edge to edge (exact), for extents that overlap or touch: no vertex of one on the open segment of another's
 * edge, no partial collinear edges (T-junctions): MISALIGNED;</li>
 * <li>clearance, for overlapping extents: pieces share an exact vertex or stay {@link #CLEARANCE} px apart, since the
 * dense lattice lets a loop of pieces close a hair off: MISALIGNED;</li>
 * <li>walls ending at the same vertex with overlapping bands must not meet at 30°: SHARP_ANGLE;</li>
 * <li>a door's swing region must not hit another wall, a volume or another door's swing region, and nothing may be
 * placed into it: DOOR_BLOCKED.</li>
 * </ol>
 */
public final class Conflicts {
    /** Minimum distance (px) between pieces of a building that share no exact vertex. */
    public static final double CLEARANCE = 4.0;
    /** Top step (48 px) plus the 28.8 px of a player standing on it, rounded up. */
    public static final double HEADROOM = 77.0;
    /** Tolerance of the door swing test (doubles). */
    public static final double SWING_TOL = 1e-6;

    private Conflicts() {
    }

    // ---------------------------------------------------------------- extents

    /** Vertical extent {lo, hi} of a piece in building px. */
    public static double[] extent(PieceKey key) {
        double base = key.baseY();
        double top = PieceKey.UNIT_H * key.topUnits();
        return switch (key.kind()) {
            case FOUNDATION, TRI_FOUNDATION -> new double[]{base - PieceSolids.FOUNDATION_T, base};
            case FLOOR, TRI_FLOOR -> new double[]{base - PieceSolids.FLOOR_T, base};
            case ROOF, TRI_ROOF -> new double[]{base - PieceSolids.FLOOR_T, base + PieceSolids.ROOF_PEAK};
            default -> new double[]{base, base + top};
        };
    }

    /** Extent of {@code key} when compared with {@code other}: a volume keeps its stairwell open above it. */
    public static double[] extentAgainst(PieceKey key, PieceKey other) {
        if (key.kind().isVolume() && other.kind().isSurface()) {
            return new double[]{key.baseY(), key.baseY() + HEADROOM};
        }
        return extent(key);
    }

    private static double overlap(double[] a, double[] b) {
        return Math.min(a[1], b[1]) - Math.max(a[0], b[0]);
    }

    // ---------------------------------------------------------------- entry points

    /** Space check of a new piece against the pieces of building {@code buildingId}. */
    public static Result check(StructureView view, PieceKey key, long buildingId) {
        return check(view, key, buildingId, Long.MIN_VALUE);
    }

    /** As {@link #check(StructureView, PieceKey, long)}, ignoring the piece {@code ignoreId} (the piece itself). */
    public static Result check(StructureView view, PieceKey key, long buildingId, long ignoreId) {
        if (buildingId == PlacementRules.NEW_BUILDING) {
            return Result.OK;
        }
        List<Piece> near = view.near(key.origin(), queryBox(key));
        near.removeIf(q -> q.id == ignoreId || q.buildingId != buildingId);
        for (Piece q : near) {
            if (overlaps(key, q.key())) {
                return overlapResult(key, q.key());
            }
        }
        for (Piece q : near) {
            if (overlap(extent(key), extent(q.key())) >= 0 && misaligned(key, q.key())) {
                return Result.MISALIGNED;
            }
        }
        for (Piece q : near) {
            if (overlap(extent(key), extent(q.key())) > 0 && tooClose(key, q.key())) {
                return Result.MISALIGNED;
            }
        }
        if (key.kind().isWall()) {
            for (Piece q : near) {
                if (q.kind().isWall() && sharpAngle(key, q.key())) {
                    return Result.SHARP_ANGLE;
                }
            }
        }
        for (Piece q : near) {
            if (swingBlocked(key, q.key())) {
                return Result.DOOR_BLOCKED;
            }
        }
        return Result.OK;
    }

    /**
     * A piece of the same building origin that {@code key} exactly duplicates (same footprint and h, same edge with an
     * overlapping band, or an exact interior overlap), or null. Saved data uses this on load; clearance and joint rules
     * apply to placement only, so tuning them never invalidates saved pieces.
     */
    @Nullable
    public static Piece duplicateOf(StructureView view, PieceKey key, long ignoreId) {
        for (Piece q : view.near(key.origin(), queryBox(key))) {
            if (q.id == ignoreId) continue;
            PieceKey o = q.key();
            // a volume stands on the surface with its footprint at the same h: only the same family duplicates
            if (!key.kind().isWall() && key.kind().family == o.kind().family && o.h() == key.h() && o.footprint().equals(key.footprint())) {
                return q;
            }
            if (overlap(extent(key), extent(o)) > 0 && planOverlap(key, o)) {
                return q;
            }
        }
        return null;
    }

    /** World box around the piece where conflicting pieces can be: clearance, door swings and stairwells. */
    private static AABB queryBox(PieceKey key) {
        return PieceSolids.worldBounds(key).inflate(2.0, 6.0, 2.0);
    }

    // ---------------------------------------------------------------- 1. exact overlap

    /** Extents overlapping by more than 0 and plan interiors overlapping (exact). */
    public static boolean overlaps(PieceKey a, PieceKey b) {
        return overlap(extentAgainst(a, b), extentAgainst(b, a)) > 0 && planOverlap(a, b);
    }

    /** Exact interior overlap of the plan regions (touching never counts). */
    public static boolean planOverlap(PieceKey a, PieceKey b) {
        LPoint[] pa = a.lattice();
        LPoint[] pb = b.lattice();
        boolean wa = a.kind().isWall();
        boolean wb = b.kind().isWall();
        if (wa && wb) {
            return Exact.segmentsOverlap(pa[0], pa[1], pb[0], pb[1]);
        }
        if (wa) {
            return Exact.segmentCrossesPolygon(pa[0], pa[1], pb);
        }
        if (wb) {
            return Exact.segmentCrossesPolygon(pb[0], pb[1], pa);
        }
        return Exact.polygonsOverlap(pa, pb);
    }

    private static Result overlapResult(PieceKey placed, PieceKey existing) {
        if (placed.kind().isSurface() && existing.kind().isVolume()) {
            return Result.STAIRWELL;
        }
        if (placed.kind().isVolume() && existing.kind().isSurface()) {
            return Result.BLOCKED_ABOVE;
        }
        return Result.OCCUPIED;
    }

    // ---------------------------------------------------------------- 2. edge to edge

    /** T-junction: a vertex on another's open edge, or collinear edges sharing part of their length without being equal. */
    public static boolean misaligned(PieceKey a, PieceKey b) {
        LPoint[] pa = a.lattice();
        LPoint[] pb = b.lattice();
        if (vertexOnEdge(pa, pb) || vertexOnEdge(pb, pa)) {
            return true;
        }
        int na = pa.length == 2 ? 1 : pa.length;
        int nb = pb.length == 2 ? 1 : pb.length;
        for (int i = 0; i < na; i++) {
            LPoint a0 = pa[i];
            LPoint a1 = pa[(i + 1) % pa.length];
            for (int j = 0; j < nb; j++) {
                LPoint b0 = pb[j];
                LPoint b1 = pb[(j + 1) % pb.length];
                if (!Exact.sameSegment(a0, a1, b0, b1) && Exact.collinearOverlap(a0, a1, b0, b1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean vertexOnEdge(LPoint[] vertices, LPoint[] outline) {
        int n = outline.length == 2 ? 1 : outline.length;
        for (LPoint v : vertices) {
            for (int j = 0; j < n; j++) {
                if (Exact.onOpenSegment(v, outline[j], outline[(j + 1) % outline.length])) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 3. clearance

    /** No shared exact vertex and closer than {@link #CLEARANCE} px. */
    public static boolean tooClose(PieceKey a, PieceKey b) {
        LPoint[] pa = a.lattice();
        LPoint[] pb = b.lattice();
        for (LPoint p : pa) {
            for (LPoint q : pb) {
                if (p.equals(q)) {
                    return false;
                }
            }
        }
        return Poly.outlineDistanceSq(Footprints.polygon(a), Footprints.polygon(b)) < CLEARANCE * CLEARANCE;
    }

    // ---------------------------------------------------------------- 4. 30° joints

    /** Two walls ending at the same vertex with overlapping bands, 30° apart. */
    public static boolean sharpAngle(PieceKey a, PieceKey b) {
        if (Math.min(a.top(), b.top()) <= Math.max(a.h(), b.h())) {
            return false;
        }
        LPoint[] pa = a.lattice();
        LPoint[] pb = b.lattice();
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                if (pa[i].equals(pb[j])) {
                    int da = a.orient() + 6 * i;
                    int db = b.orient() + 6 * j;
                    if (Dirs.angleSteps(da, db) == 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 5. door swing

    /**
     * The swing region of either piece (if it is a door) hits the other one, or both are doors whose swing regions
     * (open leaves) intersect.
     */
    public static boolean swingBlocked(PieceKey placed, PieceKey existing) {
        if (placed.kind().hasDoor() && hits(PieceSolids.swing(placed), body(existing))) {
            return true;
        }
        if (existing.kind().hasDoor() && hits(PieceSolids.swing(existing), body(placed))) {
            return true;
        }
        if (placed.kind().hasDoor() && existing.kind().hasDoor()) {
            List<Prism> swing = PieceSolids.swing(placed);
            for (Prism leaf : PieceSolids.swing(existing)) {
                if (hits(swing, leaf)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** What a swing region may not enter: a wall's slab, a volume's footprint or a surface's slab, as one prism. */
    private static Prism body(PieceKey key) {
        if (key.kind().isWall()) {
            return PieceSolids.wallSlab(key);
        }
        double[] e = extent(key);
        return new Prism(Footprints.polygon(key), e[0], e[1]);
    }

    private static boolean hits(List<Prism> swing, Prism body) {
        for (Prism s : swing) {
            if (Math.min(s.y1(), body.y1()) - Math.max(s.y0(), body.y0()) > SWING_TOL
                && Poly.overlaps(s.poly(), body.poly(), SWING_TOL)) {
                return true;
            }
        }
        return false;
    }
}
