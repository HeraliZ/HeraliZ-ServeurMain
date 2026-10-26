package fr.heraliz.crac.build.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceGeometry;
import fr.heraliz.crac.build.geom.PieceShapes;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.geom.Prism;
import fr.heraliz.crac.build.logic.Conflicts;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.logic.StructureView;
import fr.heraliz.crac.build.logic.Supports;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the socket the Building Plan aims at, Rust style, from what the camera ray hit. Pure: it only reads a
 * {@link StructureView}, so it runs in unit tests and needs no client classes.
 * <p>
 * Aim (§9.1): the camera ray is intersected exactly with the collision solids of the pieces near it
 * ({@link #aim}); the vanilla clip only bounds it (terrain) and catches what the exact solids leave out (a deep
 * foundation skirt, a structure block the mirror does not know).
 * <p>
 * Candidates (§9.2) are generated from the aimed piece (its edges, its top, the surface above it), ordered by the
 * distance of the hit point, and the first one without a conflict ({@link Conflicts#check}) is preferred.
 */
public final class Sockets {
    /** A foundation aimed at the terrain snaps to a free neighbour when the point is this close to it (blocks). */
    public static final double SNAP = 1.5;
    /** Max height difference (blocks) between the aimed terrain and a foundation it may snap to. */
    public static final int SNAP_HEIGHT = 5;
    /**
     * How far (blocks) along the ray the vanilla clip may enter a structure block the exact solids do not explain
     * before an exact hit on another piece, and that hit still wins (rounding of the raster's box faces).
     */
    static final double RASTER_SLACK = 1.5 / 16.0;
    /**
     * How far (blocks) the block raster sticks out of the exact solids: it holds every 1 px voxel a solid touches
     * (§5.9), so each point of it lies within a voxel diagonal (√3 px) of a solid; rounded up.
     */
    static final double RASTER_REACH = 2.0 / 16.0;

    private Sockets() {
    }

    /**
     * What the camera ray hit.
     *
     * @param block the block hit (for a piece: a block the piece occupies at the hit), null when the ray hit nothing
     * @param hit   the hit point, null when the ray hit nothing
     * @param face  the face that was hit (nearest axis of the surface normal for a piece)
     * @param piece the piece under the crosshair, null for terrain
     */
    public record Aim(Vec3 eye, Vec3 look, @Nullable BlockPos block, @Nullable Vec3 hit, @Nullable Direction face, @Nullable Piece piece) {
        public static Aim miss(Vec3 eye, Vec3 look) {
            return new Aim(eye, look, null, null, null, null);
        }
    }

    /** An exact ray hit on a piece's collision solids: the point (world), the outward normal and the distance. */
    public record PieceHit(Piece piece, Vec3 point, Vec3 normal, double distance) {
        public Direction face() {
            return Direction.getNearest(this.normal.x, this.normal.y, this.normal.z);
        }
    }

    /** A block the vanilla clip hit: the block, the point on its collision shape and the face. */
    public record BlockHit(BlockPos block, Vec3 point, Direction face) {
    }

    // ------------------------------------------------------------------ aiming

    /**
     * The aim of a camera ray {@code eye + t look} ({@code look} a unit vector, {@code t <= reach} blocks), §9.1.
     * <p>
     * The exact solids decide: the nearest piece they hit, with a block it occupies there. A structure block raster
     * entered on the way only stops the ray when the exact solids of its known occupants do not explain it (the raster
     * sticks out of them by {@link #RASTER_REACH} at most): a structure block the mirror does not know, a foundation
     * skirt deeper than the solids' one row. The raster is what the ray met first when no exact hit comes before such
     * a block.
     *
     * @param terrain    the first block along the ray that is not a structure block and whose collision shape the ray
     *                   hits (vanilla clip without structure blocks), or null
     * @param structures the structure blocks whose collision shape (the conservative raster) the ray enters before the
     *                   terrain, in order along the ray, with the entry point
     */
    public static Aim aim(StructureView view, Vec3 eye, Vec3 look, double reach, @Nullable BlockHit terrain, List<BlockHit> structures) {
        double bound = terrain != null ? Math.min(reach, terrain.point().distanceTo(eye)) : reach;
        BlockHit first = null;
        BlockHit blocking = null;
        for (BlockHit s : structures) {
            if (s.point().distanceTo(eye) > bound) continue;
            if (first == null) {
                first = s;
            }
            if (!explained(view.occupants(s.block()), s.point())) {
                blocking = s;
                break;
            }
        }
        double blockingDist = blocking != null ? blocking.point().distanceTo(eye) : Double.MAX_VALUE;
        List<Piece> inBlocking = blocking != null ? view.occupants(blocking.block()) : List.of();
        for (PieceHit ph : raycastAll(view, eye, look, bound)) {
            if (ph.distance() > blockingDist + RASTER_SLACK && !inBlocking.contains(ph.piece())) {
                // the ray went into a structure block the solids know nothing of first: that block (or a miss)
                break;
            }
            BlockPos block = blockOf(view, ph.piece(), ph.point(), ph.normal());
            if (block != null) {
                return new Aim(eye, look, block, ph.point(), ph.face(), ph.piece());
            }
        }
        BlockHit raster = blocking != null ? blocking : first;
        if (raster != null) {
            // a deep foundation skirt, a ray grazing a raster: the occupant closest to the hit; a structure block the
            // mirror does not know (yet) is never terrain, the aim is a miss
            Piece p = pieceAt(view, raster.block(), raster.point());
            return p == null ? Aim.miss(eye, look) : new Aim(eye, look, raster.block(), raster.point(), raster.face(), p);
        }
        if (terrain != null && terrain.point().distanceTo(eye) <= reach) {
            return new Aim(eye, look, terrain.block(), terrain.point(), terrain.face(), null);
        }
        return Aim.miss(eye, look);
    }

    /**
     * Whether a point on a block raster lies within {@link #RASTER_REACH} of the exact solids of one of the block's
     * known occupants (the raster there is their over-approximation).
     */
    static boolean explained(List<Piece> occupants, Vec3 point) {
        double reach = RASTER_REACH * 16.0;
        for (Piece p : occupants) {
            BlockPos o = p.key().origin();
            double x = (point.x - o.getX()) * 16.0;
            double y = (point.y - o.getY()) * 16.0;
            double z = (point.z - o.getZ()) * 16.0;
            for (Prism prism : PieceGeometry.solids(p.key(), p.state, p.variant)) {
                if (distanceSq(prism, x, y, z) <= reach * reach) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Squared distance from a point to a prism (same units), 0 inside. */
    static double distanceSq(Prism prism, double x, double y, double z) {
        double dy = y < prism.y0() ? prism.y0() - y : y > prism.y1() ? y - prism.y1() : 0;
        double[][] poly = prism.poly();
        if (Poly.contains(poly, x, z)) {
            return dy * dy;
        }
        double[] q = {x, z};
        double best = Double.MAX_VALUE;
        for (int i = 0; i < poly.length; i++) {
            best = Math.min(best, Poly.pointSegmentSq(q, poly[i], poly[(i + 1) % poly.length]));
        }
        return best + dy * dy;
    }

    /**
     * The nearest exact intersection of the ray {@code from + t look} ({@code look} a unit vector, {@code t <= maxDist}
     * blocks) with the collision solids of the pieces near it ({@link PieceGeometry}, the instance geometry rather than
     * the conservative block raster), or null.
     */
    @Nullable
    public static PieceHit raycast(StructureView view, Vec3 from, Vec3 look, double maxDist) {
        List<PieceHit> all = raycastAll(view, from, look, maxDist);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every piece the ray hits within {@code maxDist}, nearest entry point first (one hit per piece). */
    public static List<PieceHit> raycastAll(StructureView view, Vec3 from, Vec3 look, double maxDist) {
        Vec3 to = from.add(look.scale(maxDist));
        AABB box = new AABB(from, to).inflate(1.0);
        List<PieceHit> out = new ArrayList<>();
        List<Piece> near = new ArrayList<>(view.near(null, box));
        // deterministic ties (a piece's face flush with another's): the older piece first
        near.sort(Comparator.comparingLong(p -> p.id));
        for (Piece p : near) {
            AABB bounds = p.bounds().inflate(1e-3);
            if (p.kind().isFoundation()) {
                // the solids carry one skirt row under the slab, the bounds none
                bounds = bounds.expandTowards(0, -1, 0);
            }
            if (!bounds.contains(from) && bounds.clip(from, to).isEmpty()) {
                // the ray misses the piece's envelope: none of its solids can be hit
                continue;
            }
            PieceKey k = p.key();
            BlockPos o = k.origin();
            double px = (from.x - o.getX()) * 16.0;
            double py = (from.y - o.getY()) * 16.0;
            double pz = (from.z - o.getZ()) * 16.0;
            PieceHit best = null;
            for (Prism prism : PieceGeometry.solids(k, p.state, p.variant)) {
                double[] r = intersect(prism, px, py, pz, look.x * 16.0, look.y * 16.0, look.z * 16.0);
                if (r == null || r[0] > maxDist || (best != null && r[0] >= best.distance())) continue;
                best = new PieceHit(p, from.add(look.scale(r[0])), new Vec3(r[1], r[2], r[3]), r[0]);
            }
            if (best != null) {
                out.add(best);
            }
        }
        out.sort(Comparator.comparingDouble(PieceHit::distance));
        return out;
    }

    /**
     * Entry of the ray {@code p + t d} into a convex prism (same units): {t, nx, ny, nz} with the outward normal of the
     * entry face, or null when it misses or starts inside.
     */
    @Nullable
    static double[] intersect(Prism prism, double px, double py, double pz, double dx, double dy, double dz) {
        double tIn = Double.NEGATIVE_INFINITY;
        double tOut = Double.POSITIVE_INFINITY;
        double nx = 0;
        double ny = 0;
        double nz = 0;
        if (Math.abs(dy) < 1e-12) {
            if (py <= prism.y0() || py >= prism.y1()) {
                return null;
            }
        } else {
            double t0 = (prism.y0() - py) / dy;
            double t1 = (prism.y1() - py) / dy;
            if (t0 < t1) {
                tIn = t0;
                tOut = t1;
                ny = -1;
            } else {
                tIn = t1;
                tOut = t0;
                ny = 1;
            }
        }
        double[][] poly = prism.poly();
        double[] c = Poly.centroid(poly);
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i];
            double[] b = poly[(i + 1) % poly.length];
            double ex = b[0] - a[0];
            double ez = b[1] - a[1];
            double len = Math.sqrt(ex * ex + ez * ez);
            if (len < 1e-12) continue;
            double ox = ez / len;
            double oz = -ex / len;
            if ((c[0] - a[0]) * ox + (c[1] - a[1]) * oz > 0) {
                ox = -ox;
                oz = -oz;
            }
            // inside: (X - a) . o <= 0
            double num = (a[0] - px) * ox + (a[1] - pz) * oz;
            double den = dx * ox + dz * oz;
            if (Math.abs(den) < 1e-12) {
                if (num < 0) {
                    return null;
                }
                continue;
            }
            double t = num / den;
            if (den < 0) {
                if (t > tIn) {
                    tIn = t;
                    nx = ox;
                    ny = 0;
                    nz = oz;
                }
            } else if (t < tOut) {
                tOut = t;
            }
        }
        if (tIn > tOut || tIn < 0 || tIn == Double.NEGATIVE_INFINITY) {
            return null;
        }
        return new double[]{tIn, nx, ny, nz};
    }

    /**
     * A block the piece occupies at {@code point}: the block just behind the hit face when the piece occupies it,
     * else the occupied block nearest to the point among its 26 neighbours (a sliver below the touch threshold, a
     * point on a block boundary), or null.
     */
    @Nullable
    public static BlockPos blockOf(StructureView view, Piece piece, Vec3 point, Vec3 normal) {
        BlockPos at = BlockPos.containing(point.subtract(normal.scale(1e-4)));
        if (view.occupants(at).contains(piece)) {
            return at;
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
            if (!view.occupants(b).contains(piece)) continue;
            double d = new AABB(b).distanceToSqr(point);
            if (d < bestD) {
                bestD = d;
                best = b.immutable();
            }
        }
        return best;
    }

    /** The piece whose shape is closest to {@code hit} among the occupants of a block. */
    @Nullable
    public static Piece pieceAt(StructureView view, BlockPos pos, Vec3 hit) {
        List<Piece> occ = view.occupants(pos);
        if (occ.size() <= 1) {
            return occ.isEmpty() ? null : occ.get(0);
        }
        Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        Piece best = null;
        double bestDist = Double.MAX_VALUE;
        for (Piece p : occ) {
            for (AABB box : PieceShapes.shape(p.key(), p.state, p.variant, pos).toAabbs()) {
                double d = box.inflate(1e-3).contains(local) ? 0 : box.distanceToSqr(local);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        return best != null ? best : occ.get(0);
    }

    // ------------------------------------------------------------------ candidates

    /**
     * The socket a piece of {@code kind} would go to. {@code rot} is the player's rotation counter (R key, modulo
     * {@link #variants}): each kind reads it its own way (free-standing foundation orientation, wall facing and hinge,
     * stairs quarter turns and mirror, ramp quarter turns and half rise).
     */
    @Nullable
    public static PieceKey candidate(StructureView view, PieceKind kind, int rot, Aim aim) {
        if (aim.block() == null || aim.hit() == null) {
            return null;
        }
        Piece p = aim.piece();
        if (p == null) {
            return kind.isFoundation() ? onTerrain(view, kind, rot, aim) : null;
        }
        return switch (kind.family) {
            case SURFACE -> kind.isFoundation() ? foundationOn(view, kind, aim, p) : floorOn(view, kind, aim, p);
            case WALL -> wallOn(view, kind, rot, aim, p);
            case VOLUME -> volumeOn(view, kind, rot, aim, p);
        };
    }

    /**
     * Number of R-key variants of a kind: free-standing foundations their 3 orientations of 30° that can seat a Claim
     * Block ({@link PlacementRules#newBuildingOrients}), else its rotations.
     */
    public static int variants(PieceKind kind) {
        if (kind.isFoundation()) {
            return PlacementRules.newBuildingOrients(kind).length;
        }
        return kind.rotations;
    }

    /** The orientation (30° steps) of a free-standing foundation for the R counter {@code rot}. */
    public static int freeOrient(PieceKind kind, int rot) {
        int[] orients = PlacementRules.newBuildingOrients(kind);
        return orients[Math.floorMod(rot, orients.length)];
    }

    /** Whether a foundation key starts a new building (it shares no edge with a foundation), so R turns it. */
    public static boolean freeStanding(StructureView view, PieceKey key) {
        return key.kind().isFoundation() && Topology.foundationsSharingEdge(view, key).isEmpty();
    }

    // ------------------------------------------------------------------ per family

    /** On a foundation: across its nearest free edge; on another piece: snapped to a foundation near the hit. */
    @Nullable
    private static PieceKey foundationOn(StructureView view, PieceKind kind, Aim aim, Piece p) {
        if (!p.kind().isFoundation()) {
            return snapToFoundation(view, kind, aim.hit());
        }
        List<PieceKey> keys = new ArrayList<>();
        for (int e : edgesByDistance(p.key(), aim.hit())) {
            keys.add(Topology.across(p.key(), e, kind, 0));
        }
        return preferFree(view, keys, p.buildingId);
    }

    @Nullable
    private static PieceKey floorOn(StructureView view, PieceKind kind, Aim aim, Piece p) {
        PieceKey pk = p.key();
        Vec3 hit = aim.hit();
        List<PieceKey> keys = new ArrayList<>();
        if (pk.kind().isWall()) {
            // on the wall's top (a half wall's top is 24 px up): the side in front of a player who looks down on it (a
            // step of a climb, HeraliZ 26/10: it went on the player's side and they stood in its way), else the
            // player's side (a ceiling seen from below)
            keys.addAll(sides(pk, kind, pk.top(), aim.eye(), aboveTop(pk, aim.eye())));
        } else if (pk.kind().isVolume()) {
            // the floor the stairs or ramp lead to, across their exit at their top, else across the nearest edge
            // (over them is the stairwell)
            int exit = exitEdge(pk);
            keys.add(Topology.across(pk, exit, kind, pk.top()));
            for (int e : edgesByDistance(pk, hit)) {
                if (e != exit) {
                    keys.add(Topology.across(pk, e, kind, pk.top()));
                }
            }
        } else if (pk.kind().isFoundation()) {
            // the surface above: two units up, or one over half walls (whichever walls already carry it)
            if (pk.kind().shape == kind.shape) {
                keys.add(supportedHeight(view, new PieceKey(kind, pk.origin(), pk.anchor(), pk.h() + 2, pk.orient(), 0)));
            } else {
                LPoint[] v = pk.lattice();
                for (int e : edgesByDistance(pk, hit)) {
                    keys.add(supportedHeight(view, new PieceKey(kind, pk.origin(), v[e], pk.h() + 2, pk.edgeDir(e), 0)));
                }
            }
        } else {
            // a floor or roof: lean on it across its nearest edge
            for (int e : edgesByDistance(pk, hit)) {
                keys.add(Topology.across(pk, e, kind, pk.h()));
            }
        }
        return preferFree(view, keys, p.buildingId);
    }

    @Nullable
    private static PieceKey wallOn(StructureView view, PieceKind kind, int rot, Aim aim, Piece p) {
        PieceKey pk = p.key();
        List<PieceKey> keys = new ArrayList<>();
        if (pk.kind().isWall()) {
            // stacked on top: a full wall on a half wall starts 24 px up
            keys.addAll(facings(PieceKey.on(kind, pk.origin(), pk.edgeKey(), pk.top(), 0), rot, aim.eye()));
            return preferFree(view, keys, p.buildingId);
        }
        int h = pk.h();
        if (aim.face() == Direction.DOWN && pk.kind().isSurface() && !pk.kind().isFoundation()) {
            // under a floor or roof: the wall that would carry it (a half wall one unit down, a wall two)
            h -= kind.topUnits();
        }
        for (int e : edgesByDistance(pk, aim.hit())) {
            keys.addAll(facings(Topology.wallOn(pk, e, kind, h, 0), rot, aim.eye()));
        }
        return preferFree(view, keys, p.buildingId);
    }

    @Nullable
    private static PieceKey volumeOn(StructureView view, PieceKind kind, int rot, Aim aim, Piece p) {
        PieceKey pk = p.key();
        PieceKey square;
        if (pk.kind().isWall()) {
            List<PieceKey> keys = new ArrayList<>();
            if (kind.isRamp() && (aim.face() == Direction.UP || aboveTop(pk, aim.eye()))) {
                // HeraliZ (26/10): a ramp sits on the wall's top without a floor, in front of the player first
                keys.addAll(sides(pk, kind, pk.top(), aim.eye(), true));
            }
            // the square on the player's side of the wall, else the other side, on a surface the wall stands on
            List<PieceKey> beside = new ArrayList<>(sides(pk, kind, pk.h(), aim.eye(), false));
            beside.sort(Comparator.comparingInt(k -> Topology.walkableUnder(view, k).isEmpty() ? 1 : 0));
            keys.addAll(beside);
            square = preferFree(view, keys, p.buildingId);
        } else if ((pk.kind().isWalkableSurface() || pk.kind().isVolume()) && pk.kind().shape == kind.shape) {
            square = new PieceKey(kind, pk.origin(), pk.anchor(), pk.h(), pk.orient(), 0);
        } else {
            return null;
        }
        return square == null ? null : square.withRot(volumeRot(square, rot, aim.look()));
    }

    @Nullable
    private static PieceKey onTerrain(StructureView view, PieceKind kind, int rot, Aim aim) {
        PieceKey snap = snapToFoundation(view, kind, aim.hit());
        if (snap != null) {
            return snap;
        }
        return freeStanding(kind, rot, aim.block(), aim.hit());
    }

    /**
     * A free-standing foundation (a new building) centred on the crosshair: orientation {@link #freeOrient} of the R
     * counter, origin at the block corner nearest to {@code hit - R(orient) centroid}, the slab on top of the aimed
     * block.
     */
    public static PieceKey freeStanding(PieceKind kind, int rot, BlockPos aimed, Vec3 hit) {
        int orient = freeOrient(kind, rot);
        double[] c = Poly.centroid(Footprints.local(kind.shape));
        double cx = c[0] * Dirs.ex(orient) - c[1] * Dirs.ez(orient);
        double cz = c[0] * Dirs.ez(orient) + c[1] * Dirs.ex(orient);
        BlockPos origin = new BlockPos((int) Math.round(hit.x - cx / 16.0), aimed.getY() + 2, (int) Math.round(hit.z - cz / 16.0));
        return new PieceKey(kind, origin, LPoint.ORIGIN, 0, orient, 0);
    }

    /**
     * A free foundation next to an existing foundation near {@code point}: across the foundation edge whose outward
     * piece is closest to the point, if within {@link #SNAP} blocks of it (and {@link #SNAP_HEIGHT} in height).
     */
    @Nullable
    public static PieceKey snapToFoundation(StructureView view, PieceKind kind, Vec3 point) {
        AABB box = new AABB(point, point).inflate(SNAP + 4, SNAP_HEIGHT + 1, SNAP + 4);
        List<Piece> near = new ArrayList<>(view.near(null, box));
        near.sort(Comparator.comparingLong(f -> f.id));
        record Option(PieceKey key, long building, double score) {
        }
        List<Option> options = new ArrayList<>();
        for (Piece f : near) {
            if (!f.kind().isFoundation()) continue;
            if (Math.abs(point.y - f.key().origin().getY()) > SNAP_HEIGHT) continue;
            for (int e = 0; e < f.kind().shape.vertices; e++) {
                PieceKey key = Topology.across(f.key(), e, kind, 0);
                double d = distanceTo(key, point);
                if (d > SNAP || !key.isWellFormed()) continue;
                options.add(new Option(key, f.buildingId, d * 100 + centreDistance(key, point)));
            }
        }
        // the conflict test is the expensive part: run it in score order and stop at the first free socket
        options.sort(Comparator.comparingDouble(Option::score));
        for (Option o : options) {
            if (Conflicts.check(view, o.key(), o.building()) == PlacementRules.Result.OK) {
                return o.key();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The first offerable key (well formed, at a height its kind allows) without a conflict among the pieces of
     * {@code buildingId}, else the first offerable one; null when none (a wall under a floor on half walls would be
     * below the ground: no ghost rather than one in the ground).
     */
    @Nullable
    private static PieceKey preferFree(StructureView view, List<PieceKey> keys, long buildingId) {
        PieceKey first = null;
        for (PieceKey k : keys) {
            if (!k.isWellFormed() || k.h() < k.kind().minH() || k.h() > k.kind().maxH()) continue;
            if (Conflicts.check(view, k, buildingId) == PlacementRules.Result.OK) {
                return k;
            }
            if (first == null) {
                first = k;
            }
        }
        return first;
    }

    /**
     * A floor or roof over a foundation at {@code key.h()} (two units up), or one unit lower when only half walls
     * carry it there.
     */
    private static PieceKey supportedHeight(StructureView view, PieceKey key) {
        if (!Supports.of(view, key).isEmpty()) {
            return key;
        }
        PieceKey low = key.withH(key.h() - 1);
        return low.h() >= key.kind().minH() && !Supports.of(view, low).isEmpty() ? low : key;
    }

    /**
     * The surface or volume of {@code kind} at {@code h} on each side of a wall's edge, the side of {@code eye} first, or
     * the other side first with {@code farFirst} (for a floor or a ramp on a wall top, a volume beside a wall).
     */
    private static List<PieceKey> sides(PieceKey wall, PieceKind kind, int h, Vec3 eye, boolean farFirst) {
        LPoint a = wall.anchor();
        int d = wall.orient();
        PieceKey plus = new PieceKey(kind, wall.origin(), a, h, d, 0);
        PieceKey minus = new PieceKey(kind, wall.origin(), a.plus(d), h, d + 6, 0);
        return (sideOf(wall, eye) > 0) != farFirst ? List.of(plus, minus) : List.of(minus, plus);
    }

    /** Whether the eye is above the top of a wall (the player looks down on it, standing at its level or higher). */
    static boolean aboveTop(PieceKey wall, Vec3 eye) {
        return eye.y > wall.origin().getY() + (wall.baseY() + PieceKey.UNIT_H * wall.topUnits()) / 16.0;
    }

    /**
     * A wall turned by the rotation counter as seen from {@code eye} ({@link #orientWall}), then, for doors, the
     * alternatives in order of preference when R's choice would hit something: a single door hung on the other jamb
     * (same swing side), the door swinging the other way, then both (a double door's leaves are symmetric, its hinge
     * changes nothing).
     */
    private static List<PieceKey> facings(PieceKey wall, int rot, Vec3 eye) {
        PieceKey w = orientWall(wall, rot, eye);
        if (!wall.kind().hasDoor()) {
            return List.of(w);
        }
        if (wall.kind() != PieceKind.DOORWAY) {
            return List.of(w, orientWall(wall, rot ^ 1, eye));
        }
        return List.of(w, orientWall(wall, rot ^ 2, eye), orientWall(wall, rot ^ 1, eye), orientWall(wall, rot ^ 3, eye));
    }

    /** Edge indices of a footprint ordered by the plan distance of the world point to them (nearest first). */
    public static List<Integer> edgesByDistance(PieceKey key, Vec3 point) {
        double[][] poly = worldPolygon(key);
        int n = poly.length;
        List<Integer> out = new ArrayList<>(n);
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = Poly.pointSegmentSq(new double[]{point.x, point.z}, poly[i], poly[(i + 1) % n]);
            out.add(i);
        }
        out.sort(Comparator.comparingDouble(i -> d[i]));
        return out;
    }

    /** The footprint (or wall segment) of a key in world blocks {x, z}. */
    public static double[][] worldPolygon(PieceKey key) {
        LPoint[] v = key.lattice();
        BlockPos o = key.origin();
        double[][] out = new double[v.length][];
        for (int i = 0; i < v.length; i++) {
            out[i] = new double[]{o.getX() + v[i].toX() / 16.0, o.getZ() + v[i].toZ() / 16.0};
        }
        return out;
    }

    /** Signed side of a world point relative to a wall: positive on the side of its canonical frame's +n (rot 0). */
    public static double sideOf(PieceKey wall, Vec3 p) {
        double[][] s = worldPolygon(wall);
        double mx = (s[0][0] + s[1][0]) / 2;
        double mz = (s[0][1] + s[1][1]) / 2;
        return (p.x - mx) * Dirs.ex(wall.orient() + 3) + (p.z - mz) * Dirs.ez(wall.orient() + 3);
    }

    /**
     * Applies the rotation counter to a wall as seen by the player at {@code eye}: bit 0 clear, the inside face (door
     * swing side) points away from the player, set, towards the player; bit 1 clear, a door's hinge (the low-u jamb of
     * the wall frame) is on the player's right, set, on the left. The wall is described from the end that puts the
     * player on its frame's -n side (the key's constructor rewrites the rotation of the canonical frame), so the meaning
     * does not depend on the side the player stands on.
     */
    public static PieceKey orientWall(PieceKey wall, int rot, Vec3 eye) {
        PieceKey w = wall.withRot(0);
        if (sideOf(w, eye) > 0) {
            return PieceKey.wall(w.kind(), w.origin(), w.anchor().plus(w.orient()), w.orient() + 6, w.h(), rot & 3);
        }
        return w.withRot(rot & 3);
    }

    /**
     * Whether a wall key swings the other way than the rotation counter asks from {@code eye} (a door whose swing
     * side was blocked).
     */
    public static boolean facingFlipped(PieceKey wall, int rot, Vec3 eye) {
        return wall.kind().isWall() && (orientWall(wall, rot, eye).rot() & 1) != (wall.rot() & 1);
    }

    /**
     * Whether a single door key hangs on the other jamb than the rotation counter asks from {@code eye} (its leaf
     * would have hit something, typically another door's leaf in a corner).
     */
    public static boolean hingeMirrored(PieceKey wall, int rot, Vec3 eye) {
        return wall.kind() == PieceKind.DOORWAY && (orientWall(wall, rot, eye).rot() & 2) != (wall.rot() & 2);
    }

    /**
     * Volume rotation: the quarter turn that puts the entry of stairs or the low end of a ramp in front of a player
     * looking along {@code look}, plus the R counter (bits 0-1 more quarter turns, bit 2 mirror or half rise).
     */
    public static int volumeRot(PieceKey square, int rot, Vec3 look) {
        if (square.kind() == PieceKind.TRI_RAMP) {
            // low corner q climbs towards the opposite side: lattice direction orient + 1 + 4 q (local frame)
            int bestQ = 0;
            double best = -Double.MAX_VALUE;
            for (int q = 0; q < 3; q++) {
                int k = square.orient() + 1 + 4 * q;
                double dot = look.x * Dirs.ex(k) + look.z * Dirs.ez(k);
                if (dot > best + 1e-9) {
                    best = dot;
                    bestQ = q;
                }
            }
            int r = Math.floorMod(rot, 6);
            return (bestQ + r % 3) % 3 + (r >= 3 ? 3 : 0);
        }
        // walking direction at the entry for quarter turn 0: ramps climb towards +v, stairs towards -v (local frame);
        // one quarter turn maps +u to +v, so the direction turns by +3 lattice steps
        int base = square.kind() == PieceKind.RAMP ? square.orient() + 3 : square.orient() + 9;
        int bestQ = 0;
        double best = -Double.MAX_VALUE;
        for (int q = 0; q < 4; q++) {
            int k = base + 3 * q;
            double dot = look.x * Dirs.ex(k) + look.z * Dirs.ez(k);
            if (dot > best + 1e-9) {
                best = dot;
                bestQ = q;
            }
        }
        return ((bestQ + rot) & 3) | (rot & 4);
    }

    /**
     * The edge (footprint index) where stairs or a ramp lead to the next level: the L stairs exit (east side at rot 0,
     * west when mirrored), the high end of a ramp (+v side), turned by the quarter turns.
     */
    public static int exitEdge(PieceKey volume) {
        if (volume.kind().isRamp()) {
            return fr.heraliz.crac.build.geom.Rampes.coteHaut(volume);
        }
        int r = volume.rot();
        int base = volume.kind() == PieceKind.RAMP ? 2 : (r & 4) != 0 ? 3 : 1;
        return (base + (r & 3)) & 3;
    }

    /** Horizontal distance from a world point to a footprint (0 inside), in blocks. */
    public static double distanceTo(PieceKey key, Vec3 p) {
        double[][] poly = worldPolygon(key);
        if (Poly.contains(Poly.positive(poly), p.x, p.z)) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < poly.length; i++) {
            best = Math.min(best, Poly.pointSegmentSq(new double[]{p.x, p.z}, poly[i], poly[(i + 1) % poly.length]));
        }
        return Math.sqrt(best);
    }

    private static double centreDistance(PieceKey key, Vec3 p) {
        Vec3 c = key.centreWorld();
        double dx = p.x - c.x;
        double dz = p.z - c.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
