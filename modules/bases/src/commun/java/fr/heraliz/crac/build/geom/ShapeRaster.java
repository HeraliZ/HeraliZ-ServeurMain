package fr.heraliz.crac.build.geom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Turns piece solids (building px) into per-block {@link VoxelShape}s through a 16x16x16 px voxel bitset.
 * <ul>
 * <li><b>outward</b> (collision): every voxel a solid touches. Conservative: no ray passes between the boxes of a
 * rotated wall, and a 4 px wall stays within 7 px at 30° and 60°.</li>
 * <li><b>inward</b> (occlusion face flags): only voxels fully inside a solid, so a rotated wall never culls a terrain
 * face it does not cover.</li>
 * </ul>
 * A solid takes part in a block only when it touches it ({@link PieceSolids#TOUCH}, the rule of the reserved
 * blocks). Boxes are emitted greedily (x runs, then z, then y) and joined once.
 */
public final class ShapeRaster {
    /** Snapping tolerance: coordinates within this of a whole px count as that px. */
    private static final double SNAP = 1e-6;
    /** Strips of a slanted outline thinner than this (px²) are rounding noise, not geometry. */
    private static final double SLIVER = 1e-6;

    private ShapeRaster() {
    }

    private static int bit(int x, int y, int z) {
        return x | z << 4 | y << 8;
    }

    private static boolean get(long[] v, int i) {
        return (v[i >> 6] & (1L << (i & 63))) != 0;
    }

    private static void set(long[] v, int i) {
        v[i >> 6] |= 1L << (i & 63);
    }

    private static void clear(long[] v, int i) {
        v[i >> 6] &= ~(1L << (i & 63));
    }

    private static int down(double v) {
        return (int) Math.floor(v + SNAP);
    }

    private static int up(double v) {
        return (int) Math.ceil(v - SNAP);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(16, v));
    }

    /**
     * Voxel bitset (64 longs, bit {@code x | z << 4 | y << 8}) of block (bx, by, bz), given as offsets from the
     * building origin in blocks.
     */
    public static long[] voxels(List<Prism> solids, int bx, int by, int bz, boolean outward) {
        long[] vox = new long[64];
        double x0 = bx * 16.0;
        double y0 = by * 16.0;
        double z0 = bz * 16.0;
        for (Prism p : solids) {
            double ya = Math.max(p.y0(), y0);
            double yb = Math.min(p.y1(), y0 + 16.0);
            if (yb - ya <= PieceSolids.TOUCH) continue;
            if (p.maxX() <= x0 || p.minX() >= x0 + 16 || p.maxZ() <= z0 || p.minZ() >= z0 + 16) continue;
            double[][] poly = Poly.clipRect(p.poly(), x0, z0, x0 + 16, z0 + 16);
            if (poly == null || Poly.area(poly) <= PieceSolids.TOUCH) continue;
            poly = Poly.translate(poly, -x0, -z0);
            int ylo = clamp(outward ? down(ya - y0) : up(ya - y0));
            int yhi = clamp(outward ? up(yb - y0) : down(yb - y0));
            if (yhi <= ylo) continue;
            double[] b = Poly.bounds(poly);
            int xlo = clamp(down(b[0]));
            int xhi = clamp(up(b[2]));
            for (int x = xlo; x < xhi; x++) {
                int zlo;
                int zhi;
                if (outward) {
                    double[][] strip = Poly.clipRect(poly, x, -1, x + 1, 17);
                    if (strip == null || Poly.area(strip) <= SLIVER) continue;
                    double[] sb = Poly.bounds(strip);
                    zlo = clamp(down(sb[1]));
                    zhi = clamp(up(sb[3]));
                } else {
                    double[] a = Poly.crossSection(poly, 0, x);
                    double[] c = Poly.crossSection(poly, 0, x + 1);
                    if (a == null || c == null) continue;
                    zlo = clamp(up(Math.max(a[0], c[0])));
                    zhi = clamp(down(Math.min(a[1], c[1])));
                }
                for (int y = ylo; y < yhi; y++) {
                    for (int z = zlo; z < zhi; z++) {
                        set(vox, bit(x, y, z));
                    }
                }
            }
        }
        return vox;
    }

    /** Greedy boxes {x0, y0, z0, x1, y1, z1} (block-local px) covering exactly the set voxels. */
    public static List<int[]> boxes(long[] voxels) {
        long[] v = voxels.clone();
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!get(v, bit(x, y, z))) continue;
                    int x1 = x + 1;
                    while (x1 < 16 && get(v, bit(x1, y, z))) x1++;
                    int z1 = z + 1;
                    while (z1 < 16 && run(v, x, x1, y, z1)) z1++;
                    int y1 = y + 1;
                    while (y1 < 16 && slab(v, x, x1, z, z1, y1)) y1++;
                    for (int yy = y; yy < y1; yy++) {
                        for (int zz = z; zz < z1; zz++) {
                            for (int xx = x; xx < x1; xx++) {
                                clear(v, bit(xx, yy, zz));
                            }
                        }
                    }
                    out.add(new int[]{x, y, z, x1, y1, z1});
                }
            }
        }
        return out;
    }

    private static boolean run(long[] v, int x0, int x1, int y, int z) {
        for (int x = x0; x < x1; x++) {
            if (!get(v, bit(x, y, z))) return false;
        }
        return true;
    }

    private static boolean slab(long[] v, int x0, int x1, int z0, int z1, int y) {
        for (int z = z0; z < z1; z++) {
            if (!run(v, x0, x1, y, z)) return false;
        }
        return true;
    }

    /** Boxes of the solids inside block (bx, by, bz) of the building, block-local px. */
    public static List<int[]> boxes(List<Prism> solids, int bx, int by, int bz, boolean outward) {
        return boxes(voxels(solids, bx, by, bz, outward));
    }

    /** Shape of the solids inside block (bx, by, bz) (offsets from the building origin), block-local units. */
    public static VoxelShape rasterize(List<Prism> solids, int bx, int by, int bz, boolean outward) {
        List<int[]> boxes = boxes(solids, bx, by, bz, outward);
        if (boxes.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape shape = Shapes.empty();
        for (int[] b : boxes) {
            shape = Shapes.joinUnoptimized(shape, Shapes.box(b[0] / 16.0, b[1] / 16.0, b[2] / 16.0, b[3] / 16.0, b[4] / 16.0, b[5] / 16.0), BooleanOp.OR);
        }
        return shape.optimize();
    }

    /** Shape of building-px solids inside the world block {@code pos} of the building at {@code origin}. */
    public static VoxelShape rasterize(List<Prism> solids, BlockPos origin, BlockPos pos, boolean outward) {
        return rasterize(solids, pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ(), outward);
    }

    /** Bit mask of block faces (by {@link Direction#get3DDataValue()}) fully covered by the shape. */
    public static int fullFaces(VoxelShape shape) {
        if (shape.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (Block.isFaceFull(shape, d)) {
                mask |= 1 << d.get3DDataValue();
            }
        }
        return mask;
    }
}
