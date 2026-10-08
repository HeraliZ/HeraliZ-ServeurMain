package fr.heraliz.crac.build.logic;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceShapes;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.geom.Prism;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lattice rules of the building system: key, height, building and support, space, stability, world blocks and
 * entities. Claims, materials and reach are checked by the server on top of this. Runs on both sides so the ghost
 * preview matches the server.
 */
public final class PlacementRules {
    public static final long NEW_BUILDING = -1L;
    private static final int[] SQUARE_FREE_ORIENTS = freeOrients(PieceKind.FOUNDATION.shape);
    private static final int[] TRIANGLE_FREE_ORIENTS = freeOrients(PieceKind.TRI_FOUNDATION.shape);

    public enum Result {
        OK("crac.rule.ok"),
        BAD_SLOT("crac.rule.bad_slot"),
        BAD_LEVEL("crac.rule.bad_level"),
        OFF_GRID("crac.rule.off_grid"),
        OCCUPIED("crac.rule.occupied"),
        STAIRWELL("crac.rule.stairwell"),
        BLOCKED_ABOVE("crac.rule.blocked_above"),
        MISALIGNED("crac.rule.misaligned"),
        SHARP_ANGLE("crac.rule.sharp_angle"),
        DOOR_BLOCKED("crac.rule.door_blocked"),
        NO_SUPPORT("crac.rule.no_support"),
        UNSTABLE("crac.rule.unstable"),
        TWO_BUILDINGS("crac.rule.two_buildings"),
        OBSTRUCTED("crac.rule.obstructed"),
        NO_GROUND("crac.rule.no_ground"),
        ENTITY("crac.rule.entity"),
        OTHER_BUILDING("crac.rule.other_building");

        /** Translation key of the verdict (assets/crac/lang), shown through {@link fr.heraliz.crac.heraliz.Txt#c}. */
        public final String message;

        Result(String message) {
            this.message = message;
        }
    }

    /**
     * Orientations a new building's first foundation of {@code kind} may take, in R order: those whose footprint,
     * anchored at the building origin (a block corner), fully covers at least one block column, so that it can seat the
     * Claim Block (player rules 2-3). Squares: 0°, 30°, 60°; triangles: 0°, 30°, 90° (turned 60°, no block column is
     * entirely inside it).
     */
    public static int[] newBuildingOrients(PieceKind kind) {
        return (kind.shape == PieceKind.Shape.TRIANGLE ? TRIANGLE_FREE_ORIENTS : SQUARE_FREE_ORIENTS).clone();
    }

    /** Whether a new building may start with a foundation of {@code kind} turned to {@code orient}. */
    public static boolean isFreeOrient(PieceKind kind, int orient) {
        for (int o : kind.shape == PieceKind.Shape.TRIANGLE ? TRIANGLE_FREE_ORIENTS : SQUARE_FREE_ORIENTS) {
            if (o == orient) {
                return true;
            }
        }
        return false;
    }

    private static int[] freeOrients(PieceKind.Shape shape) {
        PieceKind kind = shape == PieceKind.Shape.TRIANGLE ? PieceKind.TRI_FOUNDATION : PieceKind.FOUNDATION;
        List<Integer> out = new ArrayList<>();
        for (int orient = 0; orient < shape.orients; orient++) {
            PieceKey key = new PieceKey(kind, BlockPos.ZERO, LPoint.ORIGIN, 0, orient, 0);
            double[][] poly = Footprints.solidPolygon(key);
            for (int[] c : Footprints.columns(key)) {
                double[][] clip = Poly.clipRect(poly, c[0] * 16.0, c[1] * 16.0, c[0] * 16.0 + 16, c[1] * 16.0 + 16);
                if (clip != null && Poly.area(clip) >= 256.0 - 1e-6) {
                    out.add(orient);
                    break;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Outcome of a check. On success {@code blocks} lists the absolute blocks to write (terrain-covered wall halves are
     * left out; the piece persists this list) and {@code skirt} the foundation skirt depths per column.
     */
    public record Plan(Result result, long buildingId, int stability, byte[] skirt, List<BlockPos> blocks) {
        public boolean ok() {
            return this.result == Result.OK;
        }

        static Plan fail(Result r) {
            return new Plan(r, 0, 0, null, List.of());
        }
    }

    private PlacementRules() {
    }

    public static Plan check(StructureView view, PlacementEnv env, PieceKey key, Stability.Params params, int maxSkirt) {
        PieceKind kind = key.kind();

        // 0. well-formed key at a height the kind allows
        if (!key.isWellFormed()) {
            return Plan.fail(Result.BAD_SLOT);
        }
        if (key.h() < kind.minH() || key.h() > kind.maxH()) {
            return Plan.fail(Result.BAD_LEVEL);
        }

        // 1. which building
        long buildingId;
        int stability;
        if (kind.isFoundation()) {
            LongSet attached = new LongOpenHashSet();
            for (Piece f : Topology.foundationsSharingEdge(view, key)) {
                attached.add(f.buildingId);
            }
            if (attached.size() > 1) {
                return Plan.fail(Result.TWO_BUILDINGS);
            }
            buildingId = attached.isEmpty() ? NEW_BUILDING : attached.iterator().nextLong();
            if (buildingId == NEW_BUILDING && (!key.anchor().equals(LPoint.ORIGIN) || !isFreeOrient(kind, key.orient()))) {
                // a new building starts at its own origin (anything else would be an arbitrary lattice point), turned so
                // that it can take the Claim Block
                return Plan.fail(Result.OFF_GRID);
            }
            stability = Stability.MAX;
        } else {
            List<Supports.Support> supports = Supports.of(view, key);
            if (supports.isEmpty()) {
                return Plan.fail(Result.NO_SUPPORT);
            }
            LongSet buildings = new LongOpenHashSet();
            for (Supports.Support s : supports) {
                buildings.add(s.piece().buildingId);
            }
            if (buildings.size() > 1) {
                return Plan.fail(Result.TWO_BUILDINGS);
            }
            buildingId = buildings.iterator().nextLong();
            stability = Stability.estimate(view, key, params);
        }
        if (buildingId != NEW_BUILDING) {
            Building b = view.building(buildingId);
            if (b == null) {
                return Plan.fail(Result.NO_SUPPORT);
            }
            if (!b.origin.equals(key.origin())) {
                return Plan.fail(Result.OFF_GRID);
            }
        }

        // 2. space among the pieces of the building
        Result space = Conflicts.check(view, key, buildingId);
        if (space != Result.OK) {
            return Plan.fail(space);
        }

        // 3. stability
        if (!kind.isFoundation() && stability < params.minToPlace()) {
            return Plan.fail(Result.UNSTABLE);
        }

        // 4. space in the world
        byte[] skirt = null;
        if (kind.isFoundation()) {
            List<int[]> cols = Footprints.columns(key);
            skirt = new byte[cols.size()];
            int slabY = PieceSolids.slabRow(key);
            for (int i = 0; i < cols.size(); i++) {
                BlockPos slab = new BlockPos(cols.get(i)[0], slabY, cols.get(i)[1]);
                if (env.isStructure(slab)) {
                    if (!compatibleStructure(view, env, slab, buildingId)) {
                        return Plan.fail(Result.OTHER_BUILDING);
                    }
                } else if (!env.isFree(slab)) {
                    return Plan.fail(Result.OBSTRUCTED);
                }
                int depth = 0;
                BlockPos p = slab.below();
                while (depth <= maxSkirt && (env.isFree(p) || (buildingId != NEW_BUILDING && compatibleStructure(view, env, p, buildingId)))) {
                    depth++;
                    p = p.below();
                }
                if (depth > maxSkirt || !env.isGround(p)) {
                    return Plan.fail(Result.NO_GROUND);
                }
                skirt[i] = (byte) depth;
            }
        }
        List<BlockPos> reserved = PieceSolids.reservedBlocks(key, skirt);
        LongSet placed = new LongOpenHashSet();
        List<BlockPos> terrain = new ArrayList<>();
        for (BlockPos pos : reserved) {
            if (env.isStructure(pos)) {
                if (!compatibleStructure(view, env, pos, buildingId)) {
                    return Plan.fail(Result.OTHER_BUILDING);
                }
                placed.add(pos.asLong());
            } else if (env.isFree(pos)) {
                placed.add(pos.asLong());
            } else if (kind.isWall() && env.isTerrain(pos)) {
                terrain.add(pos);
            } else {
                return Plan.fail(Result.OBSTRUCTED);
            }
        }
        // a wall block buried in terrain may be left out when the wall's solids in it lie on one side of the centre
        // plane and the blocks holding their mirror image are written: that half still seals the edge (a Claim Block,
        // a chest or any other block in the way refuses the wall instead, or it would have a hole where it stands)
        for (BlockPos pos : terrain) {
            if (!mayBury(key, pos, placed)) {
                return Plan.fail(Result.OBSTRUCTED);
            }
        }
        List<BlockPos> blocks = new ArrayList<>(placed.size());
        for (BlockPos pos : reserved) {
            if (placed.contains(pos.asLong())) {
                blocks.add(pos);
            }
        }

        // 5. nobody inside the new collision volume
        for (AABB box : worldBoxes(key, reserved)) {
            if (env.entityIn(box)) {
                return Plan.fail(Result.ENTITY);
            }
        }
        return new Plan(Result.OK, buildingId, stability, skirt, List.copyOf(blocks));
    }

    /** Wall burial rule for one terrain block, see {@link #check}. */
    static boolean mayBury(PieceKey key, BlockPos pos, LongSet placed) {
        BlockPos o = key.origin();
        int bx = pos.getX() - o.getX();
        int by = pos.getY() - o.getY();
        int bz = pos.getZ() - o.getZ();
        double ax = key.anchor().toX();
        double az = key.anchor().toZ();
        double nx = Dirs.ex(key.orient() + 3);
        double nz = Dirs.ez(key.orient() + 3);
        int side = 0;
        List<Prism> mirrored = new ArrayList<>();
        for (Prism p : PieceSolids.body(key)) {
            if (!PieceSolids.touches(p, bx, by, bz)) continue;
            double[][] part = Poly.clipRect(p.poly(), bx * 16.0, bz * 16.0, bx * 16.0 + 16.0, bz * 16.0 + 16.0);
            if (part == null) continue;
            double[][] mirror = new double[part.length][];
            for (int i = 0; i < part.length; i++) {
                double n = (part[i][0] - ax) * nx + (part[i][1] - az) * nz;
                int s = n > 1e-6 ? 1 : n < -1e-6 ? -1 : 0;
                if (s != 0) {
                    if (side != 0 && s != side) {
                        return false;
                    }
                    side = s;
                }
                mirror[i] = new double[]{part[i][0] - 2 * n * nx, part[i][1] - 2 * n * nz};
            }
            double y0 = Math.max(p.y0(), by * 16.0);
            double y1 = Math.min(p.y1(), by * 16.0 + 16.0);
            mirrored.add(new Prism(Poly.reverse(mirror), y0, y1));
        }
        LongSet need = new LongOpenHashSet();
        PieceSolids.touchedBlocks(mirrored, need);
        for (long rel : need) {
            BlockPos m = o.offset(BlockPos.getX(rel), BlockPos.getY(rel), BlockPos.getZ(rel));
            if (m.equals(pos) || !placed.contains(m.asLong())) {
                return false;
            }
        }
        return true;
    }

    private static boolean compatibleStructure(StructureView view, PlacementEnv env, BlockPos pos, long buildingId) {
        if (!env.isStructure(pos) || env.holdsOldData(pos)) {
            return false;
        }
        List<Piece> occ = view.occupants(pos);
        if (buildingId == NEW_BUILDING) {
            return occ.isEmpty();
        }
        for (Piece p : occ) {
            if (p.buildingId != buildingId) {
                return false;
            }
        }
        return true;
    }

    /** World-space boxes of the piece's collision shape (closed state) in the given blocks. */
    public static List<AABB> worldBoxes(PieceKey key, List<BlockPos> blocks) {
        List<AABB> out = new ArrayList<>();
        for (BlockPos pos : blocks) {
            VoxelShape shape = PieceShapes.shape(key, 0, 0L, pos);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                out.add(box.move(pos).deflate(1.0 / 64));
            }
        }
        return out;
    }

    /** World-space boxes of the piece's collision shape (closed state), block by block over its reserved blocks. */
    public static List<AABB> worldBoxes(PieceKey key, byte[] skirt) {
        return worldBoxes(key, PieceSolids.reservedBlocks(key, skirt));
    }
}
