package fr.heraliz.crac.build.geom;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Collision geometry of every piece kind. Solids are defined in the piece's local frame ({@link Placement}: squares
 * are the v2 cell, walls the v2 north-edge wall in the {@link EdgeFrame}) and returned in building px. The numbers
 * here are the gameplay contract: wall thickness, door sizes, step heights...
 */
public final class PieceSolids {
    public static final double CELL = 48.0;
    /** Half thickness of walls (walls are centred on their edge). */
    public static final double WALL_HALF = 2.0;
    public static final double LEAF_HALF = 1.0;
    public static final double FLOOR_T = 3.0;
    public static final double FOUNDATION_T = 16.0;
    /** Stairs and ramps keep clear of the walls on their edges. */
    public static final double INSET = 2.0;
    public static final double ROOF_PEAK = 24.0;
    public static final double DOOR_W = 16.0;
    public static final double DOOR_H = 32.0;
    public static final double WINDOW_SILL = 16.0;
    public static final double WINDOW_HEAD = 32.0;
    /** Open leaves fold beside the opening on the swing side, n in [LEAF_OPEN_N0, LEAF_OPEN_N1]. */
    public static final double LEAF_OPEN_N0 = 2.0;
    public static final double LEAF_OPEN_N1 = 16.0;
    public static final double LEAF_OPEN_T = 2.0;
    /** L stairs: tread depth of the four steps of each flight (px) and rise per step. */
    public static final double STEP_RUN = 5.5;
    public static final double STEP_RISE = 6.0;
    /**
     * One threshold everywhere (reservation, raster, render): a solid touches a block when its plan area inside the
     * block exceeds TOUCH px² and its y overlap exceeds TOUCH px. Geometry below it is dropped on both sides.
     */
    public static final double TOUCH = 0.05;
    /** Half thickness of the render envelope of a wall body (relief, straw, trim stay inside it). */
    public static final double RENDER_WALL_HALF = 3.5;
    /** Inradius of the render envelope of a corner post (every post contains the disc of radius 3.0 * sqrt2). */
    public static final double RENDER_POST = 4.3;
    /** How far the render envelope of an open leaf extends beyond its collision (handles, hinges). */
    public static final double RENDER_LEAF_SLACK = 1.5;
    /** Lowest render y of walls and leaves (door leaves reach into the slab below, §10.5). */
    public static final double RENDER_FOOT = -1.5;

    private PieceSolids() {
    }

    // ---------------------------------------------------------------- public entry points (building px)

    /**
     * Collision solids of a piece in a given state (door open bit, window insert) and variant (roofs), in building px.
     * Foundations add one skirt prism per column of {@code skirt} (aligned with {@link Footprints#columns}).
     */
    public static List<Prism> solids(PieceKey key, int state, long variant, @Nullable byte[] skirt) {
        List<Prism> out = transform(key, local(key, state, variant));
        if (key.kind().isFoundation() && skirt != null) {
            addSkirt(out, key, skirt);
        }
        return out;
    }

    /**
     * Everything the piece can ever occupy, in building px: closed, open and window-insert solids of walls (doors
     * open and closed), the full {@code [-FLOOR_T, ROOF_PEAK]} envelope of roofs whatever the variant, the solids of
     * other kinds. Collision blocks and world bounds derive from it.
     */
    public static List<Prism> envelope(PieceKey key) {
        PieceKind kind = key.kind();
        List<Prism> local = new ArrayList<>();
        if (kind.isWall()) {
            wall(local, kind, key.rot(), true, true, true);
        } else if (kind.isRoof()) {
            local.add(new Prism(Footprints.local(kind.shape), -FLOOR_T, ROOF_PEAK));
        } else {
            local = local(key, 0, 0);
        }
        return transform(key, local);
    }

    /** The wall's closed body with its window insert (every solid within |n| <= WALL_HALF), in building px. */
    public static List<Prism> body(PieceKey key) {
        if (!key.kind().isWall()) {
            return transform(key, local(key, 0, 0));
        }
        List<Prism> local = new ArrayList<>();
        wall(local, key.kind(), key.rot(), true, false, true);
        return transform(key, local);
    }

    /** The thick wall slab over the whole edge and height (u 0..48, |n| <= WALL_HALF), in building px. */
    public static Prism wallSlab(PieceKey key) {
        double top = PieceKey.UNIT_H * key.topUnits();
        return new Prism(EdgeFrame.of(0).rect(0, CELL, -WALL_HALF, WALL_HALF), 0, top).transform(Placement.of(key));
    }

    /**
     * Render envelope in building px: every mesh vertex of the piece lies in its collision or in these prisms. Walls:
     * the body slab widened to |n| <= {@link #RENDER_WALL_HALF}, a 12-sided post of inradius {@link #RENDER_POST} at
     * each end and the open leaves widened by {@link #RENDER_LEAF_SLACK}, from {@link #RENDER_FOOT} up. Other kinds:
     * their {@link #envelope}.
     */
    public static List<Prism> renderEnvelope(PieceKey key) {
        if (!key.kind().isWall()) {
            return envelope(key);
        }
        List<Prism> local = new ArrayList<>();
        double top = PieceKey.UNIT_H * key.topUnits();
        EdgeFrame f = EdgeFrame.of(0);
        local.add(new Prism(f.rect(0, CELL, -RENDER_WALL_HALF, RENDER_WALL_HALF), RENDER_FOOT, top));
        // regular 12-gon with vertices on the lattice directions: inradius = R cos 15°
        double r = RENDER_POST / 0.9659;
        for (double u : new double[]{0, CELL}) {
            double[][] post = new double[12][];
            for (int k = 0; k < 12; k++) {
                post[k] = new double[]{u + r * Dirs.ex(k), r * Dirs.ez(k)};
            }
            local.add(new Prism(post, RENDER_FOOT, top));
        }
        if (key.kind().hasDoor()) {
            List<Prism> leaves = new ArrayList<>();
            openLeaves(leaves, key.kind(), EdgeFrame.of(key.rot()));
            for (Prism p : leaves) {
                double[] b = Poly.bounds(p.poly());
                local.add(Prism.box(b[0] - RENDER_LEAF_SLACK, b[1] - RENDER_LEAF_SLACK, b[2] + RENDER_LEAF_SLACK, b[3] + RENDER_LEAF_SLACK,
                    RENDER_FOOT, DOOR_H + RENDER_LEAF_SLACK));
            }
        }
        return transform(key, local);
    }

    /** A door's swing region: its open leaves on the swing side, y 0..DOOR_H, in building px; empty for other kinds. */
    public static List<Prism> swing(PieceKey key) {
        List<Prism> local = new ArrayList<>();
        if (key.kind().hasDoor()) {
            openLeaves(local, key.kind(), EdgeFrame.of(key.rot()));
        }
        return transform(key, local);
    }

    /**
     * Les battants d'une porte, ouverts ou fermés, en px du bâtiment (HeraliZ) : là où il faut viser pour l'ouvrir ou la
     * fermer. Vide pour une autre pièce.
     */
    public static List<Prism> battants(PieceKey key, boolean ouvert) {
        List<Prism> local = new ArrayList<>();
        if (key.kind().hasDoor()) {
            EdgeFrame f = EdgeFrame.of(key.rot());
            if (ouvert) {
                openLeaves(local, key.kind(), f);
            } else {
                double c = CELL / 2.0;
                double h = key.kind() == PieceKind.DOORWAY ? DOOR_W / 2 : DOOR_W;
                local.add(new Prism(f.rect(c - h, c + h, -LEAF_HALF, LEAF_HALF), 0, DOOR_H));
            }
        }
        return transform(key, local);
    }

    // ---------------------------------------------------------------- local definitions

    /** Solids in the local frame (u, y, v), without skirt. */
    public static List<Prism> local(PieceKey key, int state, long variant) {
        List<Prism> out = new ArrayList<>();
        PieceKind kind = key.kind();
        switch (kind) {
            case FOUNDATION, TRI_FOUNDATION -> out.add(new Prism(Footprints.local(kind.shape), -FOUNDATION_T, 0));
            case FLOOR, TRI_FLOOR -> out.add(new Prism(Footprints.local(kind.shape), -FLOOR_T, 0));
            case ROOF, TRI_ROOF -> out.addAll(RoofField.of(kind.shape, variant).collision());
            case WALL, HALF_WALL, DOORWAY, DOUBLE_DOORWAY, WINDOW -> {
                boolean open = (state & Piece.STATE_OPEN) != 0;
                boolean insert = (state & Piece.INSERT_MASK) != 0;
                if (kind.hasDoor()) {
                    // HeraliZ : airlock with both doors open, the leaf collides as if closed (Piece.STATE_SAS)
                    open &= (state & Piece.STATE_SAS) == 0;
                    insert = false;
                }
                wall(out, kind, key.rot(), !open, open, insert);
            }
            case STAIRS_L -> stairsL(out, key.rot());
            case RAMP -> ramp(out, key.rot());
        }
        return out;
    }

    private static List<Prism> transform(PieceKey key, List<Prism> local) {
        Placement p = Placement.of(key);
        List<Prism> out = new ArrayList<>(local.size());
        for (Prism prism : local) {
            out.add(prism.transform(p));
        }
        return out;
    }

    private static void addSkirt(List<Prism> out, PieceKey key, byte[] skirt) {
        List<int[]> cols = Footprints.columns(key);
        double[][] poly = Footprints.solidPolygon(key);
        double bottom = key.baseY() - FOUNDATION_T;
        for (int i = 0; i < cols.size() && i < skirt.length; i++) {
            int d = skirt[i];
            if (d <= 0) continue;
            double x0 = (cols.get(i)[0] - key.origin().getX()) * 16.0;
            double z0 = (cols.get(i)[1] - key.origin().getZ()) * 16.0;
            double[][] col = Poly.clipRect(poly, x0, z0, x0 + 16, z0 + 16);
            if (col != null) {
                out.add(new Prism(col, bottom - 16.0 * d, bottom));
            }
        }
    }

    // ---------------------------------------------------------------- walls

    /** Wall parts in the edge-local frame: the frame, plus the closed leaf, the open leaves and the window insert. */
    private static void wall(List<Prism> out, PieceKind kind, int rot, boolean closedLeaf, boolean openLeaves, boolean insert) {
        EdgeFrame f = EdgeFrame.of(rot);
        double l = CELL;
        double c = l / 2.0;
        double t = WALL_HALF;
        switch (kind) {
            case WALL -> out.add(new Prism(f.rect(0, l, -t, t), 0, 48));
            case HALF_WALL -> out.add(new Prism(f.rect(0, l, -t, t), 0, 24));
            case DOORWAY, DOUBLE_DOORWAY -> {
                double h = kind == PieceKind.DOORWAY ? DOOR_W / 2 : DOOR_W;
                out.add(new Prism(f.rect(0, c - h, -t, t), 0, 48));
                out.add(new Prism(f.rect(c + h, l, -t, t), 0, 48));
                out.add(new Prism(f.rect(c - h, c + h, -t, t), DOOR_H, 48));
                if (closedLeaf) {
                    out.add(new Prism(f.rect(c - h, c + h, -LEAF_HALF, LEAF_HALF), 0, DOOR_H));
                }
                if (openLeaves) {
                    openLeaves(out, kind, f);
                }
            }
            case WINDOW -> {
                double h = DOOR_W / 2;
                out.add(new Prism(f.rect(0, c - h, -t, t), 0, 48));
                out.add(new Prism(f.rect(c + h, l, -t, t), 0, 48));
                out.add(new Prism(f.rect(c - h, c + h, -t, t), 0, WINDOW_SILL));
                out.add(new Prism(f.rect(c - h, c + h, -t, t), WINDOW_HEAD, 48));
                if (insert) {
                    out.add(new Prism(f.rect(c - h, c + h, -LEAF_HALF, LEAF_HALF), WINDOW_SILL, WINDOW_HEAD));
                }
            }
            default -> {
            }
        }
    }

    /** Open leaves folded beside the opening on the swing side (+n of the frame), not into it. */
    private static void openLeaves(List<Prism> out, PieceKind kind, EdgeFrame f) {
        double c = CELL / 2.0;
        double h = kind == PieceKind.DOORWAY ? DOOR_W / 2 : DOOR_W;
        out.add(new Prism(f.rect(c - h - LEAF_OPEN_T, c - h, LEAF_OPEN_N0, LEAF_OPEN_N1), 0, DOOR_H));
        if (kind == PieceKind.DOUBLE_DOORWAY) {
            out.add(new Prism(f.rect(c + h, c + h + LEAF_OPEN_T, LEAF_OPEN_N0, LEAF_OPEN_N1), 0, DOOR_H));
        }
    }

    // ---------------------------------------------------------------- stairs / ramps

    /**
     * L stairs (rot 0, before the mirror u -> 48 - u of bit 2 and the quarter turns of bits 0-1 about (24, 24)): flight
     * 1 along the west side climbing towards -v, a landing in the north-west quarter, flight 2 along the north side
     * climbing towards +u to the next level (exit on the east side). Every step is solid down to y = 0; the south-east
     * quarter is empty.
     */
    private static void stairsL(List<Prism> out, int rot) {
        List<Prism> parts = new ArrayList<>();
        double in = INSET;
        double mid = CELL / 2;
        double far = CELL - in;
        for (int s = 0; s < 4; s++) {
            parts.add(Prism.box(in, far - STEP_RUN * (s + 1), mid, far - STEP_RUN * s, 0, STEP_RISE * (s + 1)));
        }
        parts.add(Prism.box(in, in, mid, mid, 0, 4 * STEP_RISE));
        for (int s = 0; s < 4; s++) {
            parts.add(Prism.box(mid + STEP_RUN * s, in, mid + STEP_RUN * (s + 1), mid, 0, 4 * STEP_RISE + STEP_RISE * (s + 1)));
        }
        turnInto(out, parts, rot);
    }

    /** Low edge on the v = 0 side (rot 0), climbing towards +v in 2 px steps: 48 px, or 24 px for a half ramp (bit 2). */
    private static void ramp(List<Prism> out, int rot) {
        List<Prism> parts = new ArrayList<>();
        double rise = (rot & 4) != 0 ? 1.0 : 2.0;
        for (int s = 0; s < 24; s++) {
            parts.add(Prism.box(INSET, 2 * s, CELL - INSET, 2 * s + 2, 0, rise * (s + 1)));
        }
        turnInto(out, parts, rot & 3);
    }

    /** Mirror (L stairs bit 2) then turn by {@code rot & 3} quarter turns about the square centre. */
    private static void turnInto(List<Prism> out, List<Prism> parts, int rot) {
        boolean mirror = (rot & 4) != 0;
        for (Prism p : parts) {
            double[][] poly = mirror ? Poly.mirrorCell(p.poly()) : p.poly();
            out.add(new Prism(Poly.rotateCell(poly, rot & 3), p.y0(), p.y1()));
        }
    }

    // ---------------------------------------------------------------- blocks

    /** Whether the building-px prism touches block (bx, by, bz) of the building (offsets from the origin, blocks). */
    public static boolean touches(Prism p, int bx, int by, int bz) {
        double y0 = by * 16.0;
        double ya = Math.max(p.y0(), y0);
        double yb = Math.min(p.y1(), y0 + 16.0);
        if (yb - ya <= TOUCH) {
            return false;
        }
        double x0 = bx * 16.0;
        double z0 = bz * 16.0;
        if (p.maxX() <= x0 || p.minX() >= x0 + 16 || p.maxZ() <= z0 || p.minZ() >= z0 + 16) {
            return false;
        }
        double[][] c = Poly.clipRect(p.poly(), x0, z0, x0 + 16, z0 + 16);
        return c != null && Poly.area(c) > TOUCH;
    }

    /** Blocks (offsets from the building origin) touched by a set of building-px prisms, added to {@code out}. */
    public static void touchedBlocks(List<Prism> prisms, LongSet out) {
        for (Prism p : prisms) {
            int x0 = (int) Math.floor(p.minX() / 16.0);
            int x1 = (int) Math.floor(p.maxX() / 16.0);
            int z0 = (int) Math.floor(p.minZ() / 16.0);
            int z1 = (int) Math.floor(p.maxZ() / 16.0);
            int y0 = (int) Math.floor(p.y0() / 16.0);
            int y1 = (int) Math.floor(p.y1() / 16.0);
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    for (int bx = x0; bx <= x1; bx++) {
                        if (touches(p, bx, by, bz)) {
                            out.add(BlockPos.asLong(bx, by, bz));
                        }
                    }
                }
            }
        }
    }

    /**
     * Collision blocks of a piece: the blocks touched by its {@link #envelope}, absolute. Derived from the key alone,
     * so both sides agree without knowing skirts or skipped blocks.
     */
    public static List<BlockPos> collisionBlocks(PieceKey key) {
        return reservedBlocks(key, null);
    }

    /**
     * Blocks a piece reserves, absolute and sorted by (y, z, x): its collision blocks plus, for foundations, the skirt
     * blocks of each column below the slab. Placement keeps the ones it may write and the piece persists that list.
     */
    public static List<BlockPos> reservedBlocks(PieceKey key, @Nullable byte[] skirt) {
        LongSet rel = new LongOpenHashSet();
        touchedBlocks(envelope(key), rel);
        BlockPos o = key.origin();
        if (key.kind().isFoundation() && skirt != null) {
            List<int[]> cols = Footprints.columns(key);
            int slab = slabRow(key) - o.getY();
            for (int i = 0; i < cols.size() && i < skirt.length; i++) {
                for (int d = 1; d <= skirt[i]; d++) {
                    rel.add(BlockPos.asLong(cols.get(i)[0] - o.getX(), slab - d, cols.get(i)[1] - o.getZ()));
                }
            }
        }
        List<BlockPos> out = new ArrayList<>(rel.size());
        rel.forEach((long l) -> out.add(o.offset(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l))));
        out.sort(ORDER);
        return out;
    }

    /** Absolute y of a foundation's slab blocks (the skirt starts one block below). */
    public static int slabRow(PieceKey key) {
        return key.origin().getY() + Math.floorDiv(24 * key.h() - (int) FOUNDATION_T, 16);
    }

    /** Reserved block order: by y, then z, then x. */
    public static final Comparator<BlockPos> ORDER = Comparator.comparingInt((BlockPos p) -> p.getY())
        .thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getX());

    /** World box (blocks) of the piece's {@link #envelope}. */
    public static AABB worldBounds(PieceKey key) {
        double[] b = boundsPx(envelope(key));
        BlockPos o = key.origin();
        return new AABB(o.getX() + b[0] / 16.0, o.getY() + b[1] / 16.0, o.getZ() + b[2] / 16.0,
            o.getX() + b[3] / 16.0, o.getY() + b[4] / 16.0, o.getZ() + b[5] / 16.0);
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of building-px prisms. */
    public static double[] boundsPx(List<Prism> prisms) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Prism p : prisms) {
            b[0] = Math.min(b[0], p.minX());
            b[1] = Math.min(b[1], p.y0());
            b[2] = Math.min(b[2], p.minZ());
            b[3] = Math.max(b[3], p.maxX());
            b[4] = Math.max(b[4], p.y1());
            b[5] = Math.max(b[5], p.maxZ());
        }
        return b;
    }

    /** The collision block closest to the piece's centre (first in reserved order on ties). */
    public static BlockPos centreBlock(PieceKey key) {
        Vec3 c = key.centreWorld();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : collisionBlocks(key)) {
            double dx = p.getX() + 0.5 - c.x;
            double dy = p.getY() + 0.5 - c.y;
            double dz = p.getZ() + 0.5 - c.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best != null ? best : key.anchorBlock();
    }
}
