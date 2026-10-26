package fr.heraliz.loot;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encombrement d'un conteneur ou d'un coffre (repris du casino) : boites de collision en pixels (repere du modele : origine au coin du bloc principal,
 * face avant vers +Z, +X a droite du joueur qui regarde la machine). Les cellules occupees en sont deduites ;
 * chaque cellule autre que le bloc principal recoit un bloc "partie" invisible.
 */
public final class Encombrement {
    /** Cellules occupees (cx, cy, cz) dans le repere du modele ; la premiere est le bloc principal (0,0,0). */
    public final int[][] cells;
    private final float[][] boxes;
    private final Map<Direction, VoxelShape> outline = new EnumMap<>(Direction.class);
    /** Collision par cellule (clé : {@link #key}) ; table sans boîtes de Long (lue à chaque déplacement d'entité). */
    private final Map<Direction, Long2ObjectOpenHashMap<VoxelShape>> collision = new EnumMap<>(Direction.class);

    public Encombrement(float[]... boxes) {
        this(new float[0][], boxes);
    }

    /**
     * Encombrement avec des places reservees (joueurs debout, jambes sous la table) : volumes sans collision ni forme,
     * mais que personne ne peut occuper avec un bloc.
     */
    public static Encombrement withSpots(float[][] spots, float[]... boxes) {
        return new Encombrement(spots, boxes);
    }

    private Encombrement(float[][] spots, float[][] boxes) {
        this.boxes = boxes;
        Set<Long> set = new LinkedHashSet<>();
        set.add(key(0, 0, 0));
        List<float[]> claimed = new ArrayList<>(java.util.Arrays.asList(boxes));
        claimed.addAll(java.util.Arrays.asList(spots));
        for (float[] b : claimed) {
            int x0 = (int) Math.floor(b[0] / 16f + 1e-3), y0 = (int) Math.floor(b[1] / 16f + 1e-3), z0 = (int) Math.floor(b[2] / 16f + 1e-3);
            int x1 = (int) Math.ceil(b[3] / 16f - 1e-3) - 1, y1 = (int) Math.ceil(b[4] / 16f - 1e-3) - 1, z1 = (int) Math.ceil(b[5] / 16f - 1e-3) - 1;
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set.add(key(x, y, z));
                    }
                }
            }
        }
        List<int[]> list = new ArrayList<>();
        for (long k : set) list.add(unkey(k));
        cells = list.toArray(new int[0][]);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape all = Shapes.empty();
            for (float[] b : boxes) {
                float[] r = rotateBox(b, d);
                all = Shapes.or(all, Shapes.box(r[0] / 16.0, r[1] / 16.0, r[2] / 16.0, r[3] / 16.0, r[4] / 16.0, r[5] / 16.0));
            }
            all = all.optimize();
            outline.put(d, all);
            Long2ObjectOpenHashMap<VoxelShape> per = new Long2ObjectOpenHashMap<>();
            for (int[] c : cells) {
                int[] w = rotateCell(c[0], c[1], c[2], d);
                VoxelShape local = all.move(-w[0], -w[1], -w[2]);
                per.put(key(w[0], w[1], w[2]), Shapes.join(local, Shapes.block(), BooleanOp.AND).optimize());
            }
            collision.put(d, per);
        }
    }

    public static float[] box(float x0, float y0, float z0, float x1, float y1, float z1) {
        return new float[]{x0, y0, z0, x1, y1, z1};
    }

    // =================================================================== reperes
    /** Cellule du modele -> decalage monde (blocs) pour une orientation de la machine. */
    public static int[] rotateCell(int cx, int cy, int cz, Direction facing) {
        return switch (facing) {
            case NORTH -> new int[]{-cx, cy, -cz};
            case EAST -> new int[]{cz, cy, -cx};
            case WEST -> new int[]{-cz, cy, cx};
            default -> new int[]{cx, cy, cz};
        };
    }

    /** Boite du modele (px) -> boite monde relative au bloc principal (px), rotation autour du centre du bloc. */
    public static float[] rotateBox(float[] b, Direction facing) {
        float[] a = rotatePx(b[0], b[2], facing), c = rotatePx(b[3], b[5], facing);
        return new float[]{Math.min(a[0], c[0]), b[1], Math.min(a[1], c[1]), Math.max(a[0], c[0]), b[4], Math.max(a[1], c[1])};
    }

    private static float[] rotatePx(float x, float z, Direction facing) {
        float dx = x - 8, dz = z - 8;
        return switch (facing) {
            case NORTH -> new float[]{8 - dx, 8 - dz};
            case EAST -> new float[]{8 + dz, 8 - dx};
            case WEST -> new float[]{8 - dz, 8 + dx};
            default -> new float[]{x, z};
        };
    }

    /** Point du repere du modele (px) -> monde (blocs), pour une machine de bloc principal "master". */
    public static net.minecraft.world.phys.Vec3 toWorld(net.minecraft.core.BlockPos master, Direction facing, float px, float py, float pz) {
        float[] r = rotatePx(px, pz, facing);
        return new net.minecraft.world.phys.Vec3(master.getX() + r[0] / 16.0, master.getY() + py / 16.0, master.getZ() + r[1] / 16.0);
    }

    /** Point du monde (blocs) -> repere du modele (px) d'une machine de bloc principal "master" (inverse de toWorld). */
    public static float[] toModel(net.minecraft.core.BlockPos master, Direction facing, net.minecraft.world.phys.Vec3 p) {
        float wx = (float) ((p.x - master.getX()) * 16), wy = (float) ((p.y - master.getY()) * 16), wz = (float) ((p.z - master.getZ()) * 16);
        return switch (facing) {
            case NORTH -> new float[]{16 - wx, wy, 16 - wz};
            case EAST -> new float[]{16 - wz, wy, wx};
            case WEST -> new float[]{wz, wy, 16 - wx};
            default -> new float[]{wx, wy, wz};
        };
    }

    /** Lacet (Minecraft) d'une direction du repere du modele (dx, dz). */
    public static float toWorldYaw(Direction facing, float dx, float dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz)) + facing.toYRot();
    }

    /** Forme de selection complete (vue depuis la cellule monde dx,dy,dz relative au bloc principal). */
    public VoxelShape outline(Direction facing, int dx, int dy, int dz) {
        VoxelShape s = outline.get(facing);
        if (s == null) return Shapes.block();
        // bloc principal : la forme telle quelle (move crée une nouvelle forme à chaque appel)
        return (dx | dy | dz) == 0 ? s : s.move(-dx, -dy, -dz);
    }

    /** Collision limitee a la cellule monde (dx,dy,dz) relative au bloc principal. */
    public VoxelShape collision(Direction facing, int dx, int dy, int dz) {
        Long2ObjectOpenHashMap<VoxelShape> m = collision.get(facing);
        VoxelShape s = m == null ? null : m.get(key(dx, dy, dz));
        return s == null ? Shapes.empty() : s;
    }

    /** Boites (px, repere du modele). */
    public float[][] boxes() {
        return boxes;
    }

    /** Etendue horizontale maximale (blocs) autour du bloc principal (boite de rendu). */
    public int reach() {
        int r = 1;
        for (int[] c : cells) r = Math.max(r, Math.max(Math.abs(c[0]), Math.abs(c[2])) + 1);
        return r;
    }

    public int height() {
        int h = 1;
        for (int[] c : cells) h = Math.max(h, c[1] + 1);
        return h;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 20) | ((long) (y + 512) << 10) | (z + 512);
    }

    private static int[] unkey(long k) {
        return new int[]{(int) ((k >> 20) & 1023) - 512, (int) ((k >> 10) & 1023) - 512, (int) (k & 1023) - 512};
    }
}
