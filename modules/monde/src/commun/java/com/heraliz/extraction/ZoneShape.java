package com.heraliz.extraction;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 * <p>
 * 03/11 (retour du propriétaire : « le cercle est bloqué par des props, il fait des trucs bizarres, il y a des
 * coupures ») : un objet posé dans la zone (caisse, lampadaire, tronc...) n'est pas une case de la zone ; le rayon
 * s'arrêtait sur lui et le contour rentrait vers le centre (encoche), même si la zone continuait derrière. Le rayon va
 * maintenant jusqu'à la dernière case de la zone dans sa direction ; s'il touche le cercle, le contour est le cercle
 * lui-même (avant, il suivait les marches des cases et ondulait). Et la hauteur du contour est celle du vrai sol sous
 * cette case (une case où l'on tient debout sur une caisse remontait le mur d'un bloc) : {@link #solSous}. Même dessin ;
 * la zone elle-même (qui peut s'extraire, où) ne change pas.
 */
public final class ZoneShape {
    /** Nombre de points du contour affiché. */
    public static final int OUTLINE_SEGMENTS = 96;

    private static final int NONE = Integer.MIN_VALUE;
    private static final int MAX_HEIGHT_DIFF = 4;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Pas des rayons qui tracent le contour (blocs). */
    private static final double OUTLINE_STEP = 0.2D;
    /**
     * Une case de la zone touche le cercle si le rayon y passe à moins de cette distance du bord (une case du bord a son
     * centre dans le cercle ; le rayon la quitte entre rayon - 0,7 et rayon + 0,7 bloc selon la direction).
     */
    private static final double BORD_CERCLE = 0.75D;

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
        shape.computeOutline(level, start.getY());
        return shape;
    }

    /**
     * Pour chaque direction, la dernière case de la zone sur le rayon lancé depuis le centre (03/11 : avant, la première
     * case hors zone arrêtait le rayon, même un objet posé au milieu), posée sur le vrai sol de cette case.
     */
    private void computeOutline(Level level, int centerY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < OUTLINE_SEGMENTS; i++) {
            double angle = Mth.TWO_PI * i / OUTLINE_SEGMENTS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double reached = 0.0D;
            int ground = centerY;
            int gx = Mth.floor(this.centerX), gz = Mth.floor(this.centerZ);
            for (double s = OUTLINE_STEP; s <= this.radius + 1.0E-4D; s += OUTLINE_STEP) {
                int cx = Mth.floor(this.centerX + dx * s);
                int cz = Mth.floor(this.centerZ + dz * s);
                int g = get(cx, cz);
                if (g == NONE) {
                    continue;   // hors zone ici (objet posé, mur...) : la zone peut reprendre plus loin
                }
                reached = s;
                ground = g;
                gx = cx;
                gz = cz;
            }
            // zone non bloquée dans cette direction (sa dernière case touche le cercle) : le contour est le cercle lui-même ;
            // avant, il suivait les marches des cases du bord et le cercle ondulait d'un demi-bloc
            this.outlineDistance[i] = reached >= this.radius - BORD_CERCLE ? this.radius
                    : (float) Math.min(this.radius, reached + OUTLINE_STEP * 0.5D);
            this.outlineGround[i] = solSous(level, pos, gx, ground, gz);
        }
    }

    /**
     * Vrai sol sous les pieds d'un joueur debout en (x, feetY, z) : on descend (4 blocs au plus) tant que ce qui le porte
     * n'est pas du terrain (caisse, conteneur, meuble, tronc, feuilles...). Un bloc plein et opaque, la glace, l'eau, ou un
     * bloc bas qui couvre toute la case (chemin, dalle du bas) est du terrain.
     */
    private static int solSous(Level level, BlockPos.MutableBlockPos pos, int x, int feetY, int z) {
        for (int y = feetY; y > feetY - MAX_HEIGHT_DIFF; y--) {
            pos.set(x, y - 1, z);
            BlockState state = level.getBlockState(pos);
            if (terrain(level, pos, state)) {
                return y;
            }
        }
        return feetY;
    }

    private static boolean terrain(Level level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return false;
        }
        if (state.is(BlockTags.ICE) || state.isSolidRender(level, pos)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        AABB b = shape.bounds();
        return b.minX <= 0.01D && b.minZ <= 0.01D && b.maxX >= 0.99D && b.maxZ >= 0.99D && b.minY <= 0.01D
                && b.maxY >= 0.25D && b.maxY < 0.99D;
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

    // ------------------------------------------------------------------ lecture pour le dessin (03/11)

    /** Centre et rayon de la zone. */
    public double centerX() {
        return this.centerX;
    }

    public double centerZ() {
        return this.centerZ;
    }

    public float radius() {
        return this.radius;
    }

    /** Coin et côté de la grille des cases. */
    public int originX() {
        return this.originX;
    }

    public int originZ() {
        return this.originZ;
    }

    public int size() {
        return this.size;
    }

    /** Hauteur des pieds d'un joueur debout sur la case (x, z) de la zone, {@link Integer#MIN_VALUE} hors de la zone. */
    public int groundAt(int x, int z) {
        return get(x, z);
    }

    /** Vrai sol sous les pieds d'un joueur debout en (x, feetY, z) (sans caisse, tronc, feuilles), pour le dessin. */
    public static int solReel(Level level, BlockPos.MutableBlockPos pos, int x, int feetY, int z) {
        return solSous(level, pos, x, feetY, z);
    }

    /** Empreinte de la forme : change dès qu'une case, le contour ou sa hauteur change (dessin refait seulement alors). */
    public int signature() {
        int h = Arrays.hashCode(this.groundY);
        h = 31 * h + Arrays.hashCode(this.outlineDistance);
        return 31 * h + Arrays.hashCode(this.outlineGround);
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
