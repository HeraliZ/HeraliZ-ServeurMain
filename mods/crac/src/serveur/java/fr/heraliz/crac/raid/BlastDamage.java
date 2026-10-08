package fr.heraliz.crac.raid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import fr.heraliz.crac.build.geom.PieceShapes;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.placable.MultiBlocks;
import fr.heraliz.crac.placable.PlacableHpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Explosive damage to building pieces and placable blocks. A blast hits every distinct target in its cells at most
 * once, whatever the number of blocks the target has in range, and only the targets it can see: the straight line from
 * the blast to one of the target's blocks must meet the target before anything else, so a wall shields the room behind
 * it. Every hit counts as raid damage unless said otherwise.
 */
public final class BlastDamage {
    /** A piece (or a placable block when {@code piece} is null) and its squared distance to the blast centre. */
    public record Target(@Nullable Piece piece, BlockPos pos, double distSqr) {
        public boolean isPiece() {
            return this.piece != null;
        }
    }

    /** Sight lines traced per target, spread over its blocks ({@link #sightOrder}). */
    static final int SIGHT_LINES = 16;

    private BlastDamage() {
    }

    /** C4: only the piece the charge is stuck to (the shape closest to the hit point), else the placable there. */
    public static boolean stuck(ServerLevel level, BlockPos pos, Vec3 hit, int damage) {
        Piece p = StructureService.pieceAt(level, pos, hit);
        if (p != null) {
            StructureService.damage(level, p, damage, true);
            return true;
        }
        if (level.isLoaded(pos) && isClaimedPlacable(level, pos, level.getBlockState(pos))) {
            PlacableHpManager.damage(level, pos, damage);
            return true;
        }
        return false;
    }

    /**
     * Placables have HP inside claims only: elsewhere a chest, a furnace or a barrel is an ordinary block of the map
     * (HeraliZ: loot spots, decor) that blasts treat as vanilla does.
     */
    static boolean isClaimedPlacable(ServerLevel level, BlockPos pos, BlockState state) {
        return PlacableHpManager.isTracked(state) && (fr.heraliz.crac.heraliz.Heraliz.cracRulesOutsideClaims() || ClaimManager.getAt(level, pos) != null);
    }

    /** The blast centre and its six neighbours (Explosive Charge splash). */
    public static List<BlockPos> cross(BlockPos center) {
        List<BlockPos> out = new ArrayList<>(7);
        out.add(center.immutable());
        for (Direction d : Direction.values()) {
            out.add(center.relative(d));
        }
        return out;
    }

    /** Every block within {@code radius} of the centre. */
    public static List<BlockPos> sphere(BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        double r2 = (double) radius * radius;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (center.distSqr(p) <= r2) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /**
     * Distinct targets among the cells, nearest first, pieces and placables alike: whether a placable behind a wall is
     * hit is up to the sight lines ({@link #visibleTargets}).
     */
    public static List<Target> targets(ServerLevel level, BlockPos center, Iterable<BlockPos> cells) {
        StructureStore store = StructureStore.get(level);
        Map<Long, Target> pieces = new LinkedHashMap<>();
        Map<BlockPos, Target> placables = new LinkedHashMap<>();
        for (BlockPos c : cells) {
            if (!level.isLoaded(c)) continue;
            double d = center.distSqr(c);
            List<Piece> occ = store.index.occupants(c);
            if (!occ.isEmpty()) {
                for (Piece p : occ) {
                    Target t = pieces.get(p.id);
                    if (t == null || d < t.distSqr()) {
                        pieces.put(p.id, new Target(p, c.immutable(), d));
                    }
                }
            } else {
                BlockState s = level.getBlockState(c);
                if (isClaimedPlacable(level, c, s)) {
                    // both halves of a bed share one health pool: one target, at its nearest half
                    BlockPos key = MultiBlocks.canonical(s, c);
                    Target t = placables.get(key);
                    if (t == null || d < t.distSqr()) {
                        placables.put(key, new Target(null, c.immutable(), d));
                    }
                }
            }
        }
        List<Target> out = new ArrayList<>(pieces.size() + placables.size());
        out.addAll(pieces.values());
        out.addAll(placables.values());
        out.sort(Comparator.comparingDouble(Target::distSqr));
        return out;
    }

    /**
     * Damages the targets in the cells that a blast at {@code origin} can see, once each (nearest first, at most
     * {@code cap} when cap > 0). {@code raid} false for blasts that must not start raid block.
     */
    public static int splash(ServerLevel level, Vec3 origin, Iterable<BlockPos> cells, int damage, int cap, boolean raid) {
        if (damage <= 0) {
            return 0;
        }
        return hit(level, visibleTargets(level, origin, cells, null, cap), damage, raid);
    }

    /**
     * Explosive Charge stuck at {@code hit} on the block {@code pos}, going off at {@code origin}: the piece it is
     * stuck to (or the placable there) and every other target in the cross around that block that it can see, so
     * nothing behind the panel it is stuck to.
     */
    public static int charge(ServerLevel level, BlockPos pos, Vec3 hit, Vec3 origin, int damage) {
        if (damage <= 0) {
            return 0;
        }
        Piece stuckOn = StructureService.pieceAt(level, pos, hit);
        return hit(level, visibleTargets(level, origin, cross(pos), stuckOn, 0), damage, true);
    }

    /**
     * The targets in the cells that a blast at {@code origin} can see, nearest first and at most {@code cap} of them
     * when cap > 0; {@code seen} always counts as seen.
     */
    public static List<Target> visibleTargets(ServerLevel level, Vec3 origin, Iterable<BlockPos> cells, @Nullable Piece seen, int cap) {
        Set<BlockPos> range = new HashSet<>();
        for (BlockPos c : cells) {
            range.add(c.immutable());
        }
        List<Target> out = new ArrayList<>();
        for (Target t : targets(level, BlockPos.containing(origin), range)) {
            if (cap > 0 && out.size() >= cap) {
                break;
            }
            // nearest first: the sight lines of a capped blast stop being traced once it has its targets
            if (seen != null && t.piece() == seen || inSight(level, origin, t, range)) {
                out.add(t);
            }
        }
        return out;
    }

    private static int hit(ServerLevel level, List<Target> targets, int damage, boolean raid) {
        for (Target t : targets) {
            if (t.piece() != null) {
                StructureService.damage(level, t.piece(), damage, raid);
            } else {
                PlacableHpManager.damage(level, t.pos(), damage, raid);
            }
        }
        return targets.size();
    }

    /**
     * Whether a blast at {@code origin} sees the target: the blast is inside one of its blocks and nothing else shares
     * that block, or the straight line to one of its sample points in {@code range} meets the target before any other
     * block, piece or placable. A piece is sampled on its own collision boxes ({@link StructureService#sightPoints}),
     * not at block centres, so a blast never sees a floor or ramp behind a rotated wall through a block they share.
     */
    public static boolean inSight(ServerLevel level, Vec3 origin, Target t, Set<BlockPos> range) {
        BlockPos at = BlockPos.containing(origin);
        if (t.piece() == null) {
            return t.pos().equals(at) || reaches(level, origin, Vec3.atCenterOf(t.pos()), t);
        }
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos b : PieceIndex.blocksOf(t.piece())) {
            if (range.contains(b)) {
                blocks.add(b);
            }
        }
        // a block shared with other pieces (a rotated wall and the ramp or floor behind it) may hold the blast on
        // either side of the target: the line decides, as for any other block
        if (blocks.contains(at) && StructureStore.get(level).index.occupants(at).size() <= 1) {
            return true;
        }
        List<List<Vec3>> perBlock = new ArrayList<>(blocks.size());
        for (BlockPos b : blocks) {
            perBlock.add(sightPoints(level, t.piece(), b));
        }
        for (Vec3 p : sightOrder(origin, perBlock, SIGHT_LINES)) {
            if (reaches(level, origin, p, t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The (at most {@code max}) sample points the sight lines go to, spread over the blocks: the blocks ordered by their
     * point nearest to the blast, then the nearest point of each block, then the second nearest of each, and so on. A
     * rotated piece rasterizes into many small boxes per block, so the globally nearest points would all lie in the 2 or
     * 3 nearest blocks of one row, and a single block in front of them would hide the whole piece.
     */
    static List<Vec3> sightOrder(Vec3 origin, List<List<Vec3>> perBlock, int max) {
        List<List<Vec3>> sorted = new ArrayList<>(perBlock.size());
        for (List<Vec3> points : perBlock) {
            if (points.isEmpty()) continue;
            List<Vec3> s = new ArrayList<>(points);
            s.sort(Comparator.comparingDouble(p -> p.distanceToSqr(origin)));
            sorted.add(s);
        }
        sorted.sort(Comparator.comparingDouble(s -> s.get(0).distanceToSqr(origin)));
        List<Vec3> out = new ArrayList<>(max);
        for (int round = 0; out.size() < max; round++) {
            boolean any = false;
            for (List<Vec3> s : sorted) {
                if (round >= s.size()) continue;
                any = true;
                out.add(s.get(round));
                if (out.size() == max) break;
            }
            if (!any) break;
        }
        return out;
    }

    /**
     * Where the sight lines of a blast end on a piece in one of its blocks: the centres of its collision boxes there
     * ({@link StructureService#sightPoints}), plus the middle of each box face pushed 1/64 block out of the box when
     * that point is still in the block and in free space (a face in plain view, flush with a neighbour's face, such as
     * the outer face of the wall standing on the wall a charge is stuck to). A point in front of a face of a floor
     * behind a rotated wall lies inside the wall: it is never reached.
     */
    static List<Vec3> sightPoints(ServerLevel level, Piece piece, BlockPos pos) {
        List<Vec3> out = new ArrayList<>(StructureService.sightPoints(piece, pos));
        VoxelShape all = level.getBlockState(pos).getCollisionShape(level, pos);
        double e = 1.0 / 64;
        for (AABB b : PieceShapes.shape(piece.key(), piece.state, piece.variant, pos).toAabbs()) {
            Vec3 c = b.getCenter();
            Vec3[] faces = {
                new Vec3(b.minX - e, c.y, c.z), new Vec3(b.maxX + e, c.y, c.z),
                new Vec3(c.x, b.minY - e, c.z), new Vec3(c.x, b.maxY + e, c.z),
                new Vec3(c.x, c.y, b.minZ - e), new Vec3(c.x, c.y, b.maxZ + e)
            };
            for (Vec3 f : faces) {
                if (f.x <= 0 || f.x >= 1 || f.y <= 0 || f.y >= 1 || f.z <= 0 || f.z >= 1 || inside(all, f)) continue;
                out.add(f.add(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return out;
    }

    private static boolean inside(VoxelShape shape, Vec3 p) {
        for (AABB b : shape.toAabbs()) {
            if (b.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the line from {@code from} to {@code to} meets the target first, or nothing at all. Collision shapes count
     * (an open door or a window opening lets the blast through); unloaded blocks stop it and are never loaded.
     */
    private static boolean reaches(ServerLevel level, Vec3 from, Vec3 to, Target target) {
        return BlockGetter.traverseBlocks(from, to, target, (t, pos) -> {
            if (!level.isLoaded(pos)) {
                return Boolean.FALSE;
            }
            BlockHitResult hit = level.getBlockState(pos).getCollisionShape(level, pos).clip(from, to, pos);
            return hit == null ? null : isTarget(level, pos, hit.getLocation(), t);
        }, t -> Boolean.TRUE);
    }

    /** Whether the shape hit at {@code hit} in the block at {@code pos} belongs to the target. */
    private static boolean isTarget(ServerLevel level, BlockPos pos, Vec3 hit, Target t) {
        if (t.piece() == null) {
            // either half of a bed is the bed: both share one health pool and one target
            return pos.equals(t.pos()) || MultiBlocks.canonical(level.getBlockState(pos), pos)
                .equals(MultiBlocks.canonical(level.getBlockState(t.pos()), t.pos()));
        }
        List<Piece> occ = StructureStore.get(level).index.occupants(pos);
        if (occ.isEmpty()) {
            return false;
        }
        Piece first = occ.size() == 1 ? occ.get(0) : StructureService.pieceAt(level, pos, hit);
        return first == t.piece();
    }
}
