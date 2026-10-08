package fr.heraliz.crac.build.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import fr.heraliz.crac.build.geom.EdgeFrame;
import fr.heraliz.crac.build.geom.Placement;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Supports;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lattice helpers of the GameTests. Axis-aligned tests describe squares by a cell reference block (the north-west block
 * at the walking surface of the cell, 3 blocks per cell and per level) and walls by the cell side; the helpers turn
 * them into v3 keys of the right building (its origin found among the buildings already there). Block positions are
 * derived from the placed pieces ({@link PieceIndex#blocksOf}) and their geometry, never assumed.
 */
public final class GT {
    /** A side of a square cell: north (z min), east (x max), south (z max), west (x min). */
    public enum Dir4 { NORTH, EAST, SOUTH, WEST }

    private GT() {
    }

    // ------------------------------------------------------------------ keys

    /**
     * Origin of the building a piece at cell reference {@code ref} belongs to: an existing building whose origin is
     * aligned with it (whole cells and levels away) and which the piece would attach to, else {@code ref} itself (a
     * new building starts at its own origin).
     */
    public static BlockPos originFor(Level level, BlockPos ref, PieceKind kind, Dir4 side, int rot) {
        if (!(level instanceof ServerLevel sl)) {
            return ref;
        }
        StructureStore store = StructureStore.get(sl);
        BlockPos fallback = null;
        for (Building b : store.index.allBuildings()) {
            BlockPos o = b.origin;
            int dx = ref.getX() - o.getX();
            int dy = ref.getY() - o.getY();
            int dz = ref.getZ() - o.getZ();
            if (Math.floorMod(dx, 3) != 0 || Math.floorMod(dy, 3) != 0 || Math.floorMod(dz, 3) != 0) continue;
            if (Math.abs(dx) > 96 || Math.abs(dz) > 96 || Math.abs(dy) > 96) continue;
            PieceKey key = keyAt(kind, o, ref, side, rot);
            boolean attaches = kind.isFoundation()
                ? !Topology.foundationsSharingEdge(store.index, key).isEmpty()
                : !Supports.of(store.index, key).isEmpty();
            if (attaches) {
                return o;
            }
            if (!kind.isFoundation() && fallback == null) {
                fallback = o;
            }
        }
        return fallback != null ? fallback : ref;
    }

    /** Key of a piece of building origin {@code o} at cell reference {@code ref} (and side, for walls). */
    public static PieceKey keyAt(PieceKind kind, BlockPos o, BlockPos ref, Dir4 side, int rot) {
        int i = Math.floorDiv(ref.getX() - o.getX(), 3);
        int l = Math.floorDiv(ref.getY() - o.getY(), 3);
        int j = Math.floorDiv(ref.getZ() - o.getZ(), 3);
        LPoint a = new LPoint(4 * i, 0, 4 * j, 0);
        if (!kind.isWall()) {
            return PieceKey.square(kind, o, a, 0, 2 * l, rot);
        }
        // v2 wall semantics: +n (rot 0) points south for north/south walls and east for west/east walls
        return switch (side) {
            case NORTH -> PieceKey.wall(kind, o, a, 0, 2 * l, rot);
            case SOUTH -> PieceKey.wall(kind, o, a.plus(3), 0, 2 * l, rot);
            case WEST -> PieceKey.wall(kind, o, a, 3, 2 * l, rot ^ 1);
            case EAST -> PieceKey.wall(kind, o, a.plus(0), 3, 2 * l, rot ^ 1);
        };
    }

    /** A square surface (foundation, floor, roof) on the cell at {@code ref}. */
    public static PieceKey surface(Level level, PieceKind kind, BlockPos ref) {
        return keyAt(kind, originFor(level, ref, kind, Dir4.NORTH, 0), ref, Dir4.NORTH, 0);
    }

    /** Stairs or a ramp on the cell at {@code ref}. */
    public static PieceKey volume(Level level, PieceKind kind, BlockPos ref, int rot) {
        return keyAt(kind, originFor(level, ref, kind, Dir4.NORTH, rot), ref, Dir4.NORTH, rot);
    }

    /** A wall on a side of the cell at {@code ref}. */
    public static PieceKey wall(Level level, PieceKind kind, BlockPos ref, Dir4 side, int rot) {
        return keyAt(kind, originFor(level, ref, kind, side, rot), ref, side, rot);
    }

    /** The equilateral triangle of {@code kind} outside the given side of a square (same height). */
    public static PieceKey triangleOn(PieceKey square, Dir4 side, PieceKind kind) {
        return Topology.across(square, edgeIndex(square, side), kind, square.h());
    }

    /** Index of the footprint edge of an axis-aligned square on the given side. */
    public static int edgeIndex(PieceKey square, Dir4 side) {
        double[] dir = switch (side) {
            case NORTH -> new double[]{0, -1};
            case EAST -> new double[]{1, 0};
            case SOUTH -> new double[]{0, 1};
            case WEST -> new double[]{-1, 0};
        };
        LPoint[] v = square.lattice();
        int best = 0;
        double bestD = -Double.MAX_VALUE;
        for (int i = 0; i < v.length; i++) {
            LPoint a = v[i];
            LPoint b = v[(i + 1) % v.length];
            double d = (a.toX() + b.toX()) * dir[0] + (a.toZ() + b.toZ()) * dir[1];
            if (d > bestD + 1e-9) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ positions

    /** World point of wall frame coordinates (u along the wall, n across, y up from its base), in blocks. */
    public static Vec3 wallPoint(PieceKey wall, double u, double n, double y) {
        EdgeFrame f = EdgeFrame.of(wall.rot());
        Placement p = Placement.of(wall);
        double lu = f.u(u, n);
        double lv = f.v(u, n);
        return new Vec3(p.worldX(p.x(lu, lv)) / 16.0, p.worldY(p.y(y)) / 16.0, p.worldZ(p.z(lu, lv)) / 16.0);
    }

    /** World point of local coordinates (u, y, v) of a piece, in blocks. */
    public static Vec3 localPoint(PieceKey key, double u, double y, double v) {
        Placement p = Placement.of(key);
        return new Vec3(p.worldX(p.x(u, v)) / 16.0, p.worldY(p.y(y)) / 16.0, p.worldZ(p.z(u, v)) / 16.0);
    }

    /** The block of the piece containing {@code point}, else the one nearest to it. */
    public static BlockPos blockNear(Piece p, Vec3 point) {
        BlockPos at = BlockPos.containing(point);
        List<BlockPos> blocks = PieceIndex.blocksOf(p);
        if (blocks.contains(at)) {
            return at;
        }
        BlockPos best = blocks.get(0);
        double bestD = Double.MAX_VALUE;
        for (BlockPos b : blocks) {
            double d = Vec3.atCenterOf(b).distanceToSqr(point);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    /** The block of a wall holding the point at wall coordinates (u, n, y). */
    public static BlockPos wallBlock(Piece wall, double u, double n, double y) {
        return blockNear(wall, wallPoint(wall.key(), u, n, y));
    }

    /** The block above the centre of a surface at an even height: where a Claim Block or a placable stands on it. */
    public static BlockPos seat(Piece surface) {
        return seatOf(surface.key());
    }

    /** {@link #seat} of a key (placed or not). */
    public static BlockPos seatOf(PieceKey k) {
        Vec3 c = k.centreWorld();
        return new BlockPos((int) Math.floor(c.x), k.origin().getY() + Math.floorDiv(24 * k.h(), 16), (int) Math.floor(c.z));
    }

    /** {@link #seat} of a square foundation on the cell at {@code ref}. */
    public static BlockPos cellSeat(BlockPos ref) {
        return seatOf(keyAt(PieceKind.FOUNDATION, ref, ref, Dir4.NORTH, 0));
    }

    /** The highest block of the piece (wall tops, the block a floor on it shares). */
    public static BlockPos topBlock(Piece p, Vec3 near) {
        BlockPos best = null;
        for (BlockPos b : PieceIndex.blocksOf(p)) {
            if (best == null || b.getY() > best.getY()
                || b.getY() == best.getY() && Vec3.atCenterOf(b).distanceToSqr(near) < Vec3.atCenterOf(best).distanceToSqr(near)) {
                best = b;
            }
        }
        return best;
    }

    /**
     * A block of {@code wall} also held by {@code other}, on row {@code y}, whose centre projects onto the wall's centre
     * plane inside the block, close to the plane (so either face of the wall can be aimed at there).
     */
    public static BlockPos sharedBlock(Piece wall, Piece other, int y) {
        Vec3 n = wallPoint(wall.key(), 24, 1, 0).subtract(wallPoint(wall.key(), 24, 0, 0)).scale(16.0);
        Vec3 w0 = wallPoint(wall.key(), 24, 0, 0);
        for (BlockPos b : PieceIndex.blocksOf(wall)) {
            if (b.getY() != y || !PieceIndex.blocksOf(other).contains(b)) continue;
            Vec3 c = Vec3.atCenterOf(b);
            Vec3 p = c.subtract(n.scale(c.subtract(w0).dot(n)));
            if (BlockPos.containing(p).equals(b) && Math.abs(c.subtract(w0).dot(n)) < 0.4) {
                return b;
            }
        }
        throw new GameTestAssertException("test setup: " + other.kind().id + " shares no block of row " + y + " with the wall");
    }

    /** Unit vector of a wall's +n side (rot 0 of its canonical edge: the side the door swings to), in blocks. */
    public static Vec3 normal(PieceKey wall) {
        return wallPoint(wall.withRot(0), 24, 1, 0).subtract(wallPoint(wall.withRot(0), 24, 0, 0)).scale(16.0);
    }

    // ------------------------------------------------------------------ test isolation

    /**
     * Removes the pieces other tests left within reach of this test's claim zones: 26 blocks around the test's area
     * ({@code sizeX} x {@code sizeZ} blocks from its relative origin) and from 10 below to 70 above it. A claim zone
     * reaches far past a test's area, and any neighbour's building inside it is "another group's building". Only for a
     * test that runs in a batch of its own: the tests of earlier batches are finished, nothing else is running.
     */
    public static void clearAround(GameTestHelper h, int sizeX, int sizeZ) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(BlockPos.ZERO);
        BlockPos b = h.absolutePos(new BlockPos(sizeX, 0, sizeZ));
        AABB area = new AABB(Math.min(a.getX(), b.getX()) - 26, a.getY() - 10, Math.min(a.getZ(), b.getZ()) - 26,
            Math.max(a.getX(), b.getX()) + 26, a.getY() + 70, Math.max(a.getZ(), b.getZ()) + 26);
        StructureStore store = StructureStore.get(level);
        for (Piece p : new ArrayList<>(store.index.allPieces())) {
            if (store.index.piece(p.id) != null && p.bounds().intersects(area)) {
                StructureService.remove(level, p, StructureService.Cause.ADMIN);
            }
        }
    }

    // ------------------------------------------------------------------ lattice layouts

    /** Index of the edge of {@code s} that has {@code v} as an end and is not {@code other}. */
    public static int edgeAt(PieceKey s, LPoint v, EdgeKey other) {
        List<EdgeKey> edges = s.edges();
        for (int i = 0; i < edges.size(); i++) {
            EdgeKey e = edges.get(i);
            if (!e.equals(other) && (e.start().equals(v) || e.end().equals(v))) {
                return i;
            }
        }
        throw new GameTestAssertException("test setup: no edge at " + v);
    }

    /**
     * The 13-piece round base around square {@code c} (Rust): a triangle on each edge, then at each corner a square
     * leaning on the triangle and a triangle closing exactly against the previous corner's triangle. Same kinds as
     * {@code c} (foundation or floor). Order: c, the 4 triangles, then square and closing triangle per corner.
     */
    public static List<PieceKey> ringAroundASquare(PieceKey c) {
        PieceKind triKind = c.kind() == PieceKind.FOUNDATION ? PieceKind.TRI_FOUNDATION : PieceKind.TRI_FLOOR;
        List<PieceKey> ring = new ArrayList<>();
        ring.add(c);
        PieceKey[] t = new PieceKey[4];
        for (int i = 0; i < 4; i++) {
            t[i] = Topology.across(c, i, triKind, c.h());
            ring.add(t[i]);
        }
        for (int i = 0; i < 4; i++) {
            LPoint v = c.lattice()[i];
            int a = edgeAt(t[i], v, c.edges().get(i));
            PieceKey s = Topology.across(t[i], a, c.kind(), c.h());
            int b = edgeAt(s, v, t[i].edges().get(a));
            PieceKey u = Topology.across(s, b, triKind, c.h());
            ring.add(s);
            ring.add(u);
        }
        return ring;
    }

    /**
     * Index of an edge of the triangle that is not shared with {@code base} and runs in lattice direction {@code dir6}
     * (mod 6), or -1.
     */
    public static int outerEdgeWithDir(PieceKey tri, PieceKey base, int dir6) {
        for (int i = 0; i < tri.kind().shape.vertices; i++) {
            if (Math.floorMod(tri.edgeDir(i), 6) == dir6 && !base.edges().contains(tri.edges().get(i))) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ physics probes

    /**
     * Highest collision surface over world column (x, z) between world heights {@code y0} and {@code y1}, or
     * {@code NaN} when the column is empty: what a player standing there would rest on.
     */
    public static double columnTop(Level level, double x, double z, double y0, double y1) {
        double top = Double.NaN;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int y = (int) Math.floor(y0); y <= (int) Math.floor(y1); y++) {
            pos.set(bx, y, bz);
            for (AABB box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                AABB b = box.move(pos);
                if (b.minX <= x && x <= b.maxX && b.minZ <= z && z <= b.maxZ && b.maxY <= y1 + 1e-9) {
                    top = Double.isNaN(top) ? b.maxY : Math.max(top, b.maxY);
                }
            }
        }
        return top;
    }

    /**
     * Walks an entity towards (x, z) at 0.1 block per tick with gravity, through {@link Entity#move} (vanilla
     * collision and step-up), for at most {@code maxTicks} ticks. Returns the entity's y after each tick.
     */
    public static List<Double> walkTo(Entity e, double x, double z, int maxTicks) {
        List<Double> ys = new ArrayList<>();
        for (int i = 0; i < maxTicks; i++) {
            double dx = Mth.clamp(x - e.getX(), -0.1, 0.1);
            double dz = Mth.clamp(z - e.getZ(), -0.1, 0.1);
            if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6 && e.onGround()) {
                break;
            }
            e.move(MoverType.SELF, new Vec3(dx, -0.1, dz));
            ys.add(e.getY());
        }
        return ys;
    }

    /**
     * Whether a player-sized box (9.6 px wide, 28.8 px high, standing on the wall's base) can cross {@code wall} from
     * {@code n = -reach} to {@code n = +reach} px, its centre kept within {@code u in [u0, u1]} px of the wall frame so it
     * cannot walk round the wall's ends: a breadth-first search on a 0.5 px grid of free positions.
     */
    public static boolean passage(Level level, PieceKey wall, double u0, double u1, double reach) {
        double step = 0.5;
        int nu = (int) Math.round((u1 - u0) / step) + 1;
        int nn = (int) Math.round(2 * reach / step) + 1;
        byte[] state = new byte[nu * nn];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int startU = (int) Math.round((24 - u0) / step);
        if (free(level, wall, u0 + startU * step, -reach)) {
            queue.add(new int[]{startU, 0});
            state[startU * nn] = 1;
        }
        int[][] moves = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[1] == nn - 1) {
                return true;
            }
            for (int[] m : moves) {
                int a = c[0] + m[0];
                int b = c[1] + m[1];
                if (a < 0 || b < 0 || a >= nu || b >= nn || state[a * nn + b] != 0) continue;
                boolean ok = free(level, wall, u0 + a * step, -reach + b * step);
                state[a * nn + b] = (byte) (ok ? 1 : 2);
                if (ok) {
                    queue.add(new int[]{a, b});
                }
            }
        }
        return false;
    }

    /**
     * Shoots {@code rays} random straight lines across {@code wall} (from n = -20 to n = +20 px, crossing its plane
     * within u 6..42 px and y 1..46 px) and returns the first one that meets no collision, or null when every line is
     * stopped: the wall's collision (frame, closed leaves, window inserts) has no gap a line of sight gets through.
     */
    public static String rayLeak(Level level, PieceKey wall, int rays, long seed) {
        Random rng = new Random(seed);
        PieceKey frame = wall.withRot(0);
        for (int i = 0; i < rays; i++) {
            Vec3 from = wallPoint(frame, 6 + 36 * rng.nextDouble(), -20, 1 + 45 * rng.nextDouble());
            Vec3 to = wallPoint(frame, 6 + 36 * rng.nextDouble(), 20, 1 + 45 * rng.nextDouble());
            BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return from + " -> " + to;
            }
        }
        return null;
    }

    private static boolean free(Level level, PieceKey wall, double u, double n) {
        Vec3 c = wallPoint(wall.withRot(0), u, n, 0);
        return level.noCollision(new AABB(c.x - 0.3, c.y + 0.01, c.z - 0.3, c.x + 0.3, c.y + 1.81, c.z + 0.3));
    }
}
