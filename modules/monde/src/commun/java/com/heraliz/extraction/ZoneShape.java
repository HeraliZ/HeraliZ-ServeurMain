package com.heraliz.extraction;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Forme réelle de la zone d'extraction : on part du terminal et on "marche" case par case
 * (comme un joueur : marche d'un bloc max, descente de deux) jusqu'au rayon.
 * La zone suit donc le relief, mais s'arrête sur les murs et n'entre pas dans les bâtiments
 * (les cases sous un toit sont exclues).
 * <p>
 * Le contour affiché est un cercle lisse, raccourci seulement dans les directions où la zone
 * est bloquée (rayons lancés depuis le centre).
 */
public final class ZoneShape {
    /** Nombre de points du contour affiché. */
    public static final int OUTLINE_SEGMENTS = 96;

    private static final int NONE = Integer.MIN_VALUE;
    private static final int MAX_HEIGHT_DIFF = 4;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Pas des rayons qui tracent le contour (blocs). */
    private static final double OUTLINE_STEP = 0.2D;

    private final double centerX;
    private final double centerZ;
    private final float radius;
    private final int originX;
    private final int originZ;
    private final int size;
    private final int[] groundY;
    private final float[] outlineDistance = new float[OUTLINE_SEGMENTS];
    private final int[] outlineGround = new int[OUTLINE_SEGMENTS];

    private ZoneShape(Vec3 center, float radius, int originX, int originZ, int size) {
        this.centerX = center.x;
        this.centerZ = center.z;
        this.radius = radius;
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.groundY = new int[size * size];
        Arrays.fill(this.groundY, NONE);
    }

    /**
     * @param requireSky true pour la fusée (les cases sous un toit sont exclues),
     *                   false pour les zones fixes d'admin (placées où on veut, intérieur compris)
     */
    public static ZoneShape compute(Level level, Vec3 center, float radius, boolean requireSky) {
        BlockPos start = BlockPos.containing(center);
        int r = (int) Math.ceil(radius) + 1;
        ZoneShape shape = new ZoneShape(center, radius, start.getX() - r, start.getZ() - r, r * 2 + 1);

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        shape.set(start.getX(), start.getZ(), start.getY());
        queue.add(new int[]{start.getX(), start.getY(), start.getZ()});

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] dir : DIRS) {
                int nx = cell[0] + dir[0];
                int nz = cell[2] + dir[1];
                if (!shape.withinRadius(nx + 0.5D, nz + 0.5D, 0.0D) || shape.get(nx, nz) != NONE) {
                    continue;
                }
                int y = findStandable(level, pos, nx, cell[1], nz);
                if (y == NONE || Math.abs(y - start.getY()) > MAX_HEIGHT_DIFF) {
                    continue;
                }
                pos.set(nx, y, nz);
                if (requireSky && !isUnderOpenSky(level, pos)) {
                    continue; // intérieur d'un bâtiment / sous un toit
                }
                shape.set(nx, nz, y);
                queue.add(new int[]{nx, y, nz});
            }
        }
        shape.computeOutline(start.getY());
        return shape;
    }

    /** Pour chaque direction, avance depuis le centre tant qu'on reste dans la zone. */
    private void computeOutline(int centerY) {
        for (int i = 0; i < OUTLINE_SEGMENTS; i++) {
            double angle = Mth.TWO_PI * i / OUTLINE_SEGMENTS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double reached = 0.0D;
            int ground = centerY;
            for (double s = OUTLINE_STEP; s <= this.radius + 1.0E-4D; s += OUTLINE_STEP) {
                int cx = Mth.floor(this.centerX + dx * s);
                int cz = Mth.floor(this.centerZ + dz * s);
                int g = get(cx, cz);
                if (g == NONE) {
                    break;
                }
                reached = s;
                ground = g;
            }
            this.outlineDistance[i] = (float) Math.min(this.radius, reached + OUTLINE_STEP * 0.5D);
            this.outlineGround[i] = ground;
        }
    }

    /** Hauteur où un joueur peut se tenir en venant de {@code fromY} (même niveau, +1, -1, -2), ou NONE. */
    private static int findStandable(Level level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
        for (int y : new int[]{fromY, fromY + 1, fromY - 1, fromY - 2}) {
            if (isFree(level, pos, x, y, z) && isFree(level, pos, x, y + 1, z) && !isFree(level, pos, x, y - 1, z)) {
                if (y > fromY && !isFree(level, pos, x, fromY + 2, z)) {
                    continue; // pas la place de monter
                }
                return y;
            }
        }
        return NONE;
    }

    private static boolean isFree(Level level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * "Dehors" = aucun bloc solide au-dessus de la tête jusqu'au ciel (les feuilles d'arbre sont tolérées).
     * <p>
     * On parcourt les blocs à la main : la heightmap "sans feuilles" n'existe que côté serveur,
     * le client (écran du PC, mur de la zone) aurait toujours cru être dehors.
     */
    public static boolean isUnderOpenSky(Level level, BlockPos pos) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < top; y++) {
            cursor.set(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(cursor);
            boolean solid = state.blocksMotion() || !state.getFluidState().isEmpty();
            if (solid && !(state.getBlock() instanceof LeavesBlock)) {
                return false;
            }
        }
        return true;
    }

    private boolean withinRadius(double x, double z, double margin) {
        double dx = x - this.centerX;
        double dz = z - this.centerZ;
        double r = this.radius + margin;
        return dx * dx + dz * dz <= r * r;
    }

    private int index(int x, int z) {
        int ix = x - this.originX;
        int iz = z - this.originZ;
        if (ix < 0 || iz < 0 || ix >= this.size || iz >= this.size) {
            return -1;
        }
        return iz * this.size + ix;
    }

    private void set(int x, int z, int y) {
        int i = index(x, z);
        if (i >= 0) {
            this.groundY[i] = y;
        }
    }

    private int get(int x, int z) {
        int i = index(x, z);
        return i < 0 ? NONE : this.groundY[i];
    }

    /** Le joueur (position des pieds) est-il dans la zone ? Tolère un saut et le bord du cercle. */
    public boolean contains(double x, double y, double z) {
        int ground = get(Mth.floor(x), Mth.floor(z));
        return ground != NONE && withinRadius(x, z, 0.3D) && y >= ground - 0.6D && y <= ground + 1.8D;
    }

    /** Distance du contour (depuis le centre) dans la direction {@code i}. */
    public float outlineDistance(int i) {
        return this.outlineDistance[Math.floorMod(i, OUTLINE_SEGMENTS)];
    }

    /** Hauteur du sol sous le contour dans la direction {@code i}. */
    public int outlineGround(int i) {
        return this.outlineGround[Math.floorMod(i, OUTLINE_SEGMENTS)];
    }
}
