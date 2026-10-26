package fr.heraliz.crates.block;

import fr.heraliz.crates.crate.CrateType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Formes des caisses (selection et collision) : la vraie silhouette de chaque modele, en cubes de 2 px, calculee hors du
 * jeu a partir du modele 3D au repos (CrateFormes, genere par outils/formes_caisses.py) et tournee selon le sens de pose.
 * Le bloc principal est au centre de la couche du bas ; des blocs "parties" invisibles remplissent les cellules que la
 * caisse traverse (au plus 3 x 3 cellules sur 3 couches). Chaque partie renvoie la forme complete pour la selection
 * (le contour suit toute la caisse, ou que l'on vise) et seulement son morceau pour la collision.
 * <p>Formes construites a la premiere demande, une fois par caisse et par orientation, puis gardees.
 */
public final class CrateShapes {
    private CrateShapes() {
    }

    public static final int COUNT = 27;
    public static final int MASTER = 4;

    /** Formes d'une caisse dans une orientation (calculees dans le constructeur : visibles de tous les fils une fois publiees). */
    private static final class Formes {
        final VoxelShape[] outline = new VoxelShape[COUNT], collision = new VoxelShape[COUNT];
        final boolean[] needed = new boolean[COUNT];

        Formes(int[][] boxes, int f) {
            List<VoxelShape> all = new ArrayList<>(boxes.length);
            List<List<VoxelShape>> cells = new ArrayList<>(COUNT);
            for (int i = 0; i < COUNT; i++) cells.add(new ArrayList<>());
            for (int[] b0 : boxes) {
                int[] b = rotate(b0, f);
                all.add(box(b[0], b[1], b[2], b[3], b[4], b[5]));
                // morceaux de la boite dans chaque cellule, dans le repere de la cellule
                for (int i = 0; i < COUNT; i++) {
                    int ox = dx(i) * 16, oy = dy(i) * 16, oz = dz(i) * 16;
                    int x0 = Math.max(b[0], ox), y0 = Math.max(b[1], oy), z0 = Math.max(b[2], oz);
                    int x1 = Math.min(b[3], ox + 16), y1 = Math.min(b[4], oy + 16), z1 = Math.min(b[5], oz + 16);
                    if (x0 < x1 && y0 < y1 && z0 < z1) cells.get(i).add(box(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz));
                }
            }
            VoxelShape full = union(all);
            for (int i = 0; i < COUNT; i++) {
                outline[i] = i == MASTER ? full : full.move(-dx(i), -dy(i), -dz(i));
                collision[i] = union(cells.get(i));
                needed[i] = i == MASTER || !collision[i].isEmpty();
            }
        }
    }

    private static final int TYPES = CrateType.values().length;
    /** [type][orientation (Direction.get2DDataValue)] ; rempli a la demande. */
    private static final Formes[][] FORMES = new Formes[TYPES][4];
    /** Boites de chaque caisse tournee vers le sud (px : x0 y0 z0 x1 y1 z1, origine au coin du bloc principal). */
    private static final int[][][] BOXES = new int[TYPES][][];

    static {
        for (CrateType t : CrateType.values()) {
            String s = CrateFormes.BOXES.get(t.id);
            int n = s == null ? 0 : s.length() / 6;
            int[][] b = new int[n][];
            for (int i = 0; i < n; i++) {
                int[] v = new int[6];
                for (int k = 0; k < 6; k++) v[k] = s.charAt(i * 6 + k) - CrateFormes.BASE;
                // x et z sont comptes depuis -16 px
                b[i] = new int[]{v[0] - 16, v[1], v[2] - 16, v[3] - 16, v[4], v[5] - 16};
            }
            // caisse sans forme calculee (nouvelle caisse, outil pas encore relance) : la boite de son bloc
            BOXES[t.ordinal()] = n > 0 ? b : new int[][]{{0, 0, 0, 16, 16, 16}};
        }
    }

    private static Formes formes(CrateType type, Direction facing) {
        int f = facing.getAxis().isHorizontal() ? facing.get2DDataValue() : 0;
        Formes fo = FORMES[type.ordinal()][f];
        if (fo == null) {
            fo = new Formes(BOXES[type.ordinal()], f);
            // course sans danger : deux calculs donneraient la meme forme
            FORMES[type.ordinal()][f] = fo;
        }
        return fo;
    }

    /** Boite tournee autour du centre du bloc principal (sens de rotation du rendu : CrateRenderer.toLocal). */
    private static int[] rotate(int[] b, int f) {
        return switch (f) {
            case 1 -> new int[]{16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};        // ouest
            case 2 -> new int[]{16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]}; // nord
            case 3 -> new int[]{b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]};        // est
            default -> b;                                                              // sud
        };
    }

    private static VoxelShape box(int x0, int y0, int z0, int x1, int y1, int z1) {
        return Shapes.box(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    /** Union de nombreuses boites : fusion deux a deux (arbre equilibre), optimisee une seule fois a la fin. */
    private static VoxelShape union(List<VoxelShape> shapes) {
        if (shapes.isEmpty()) return Shapes.empty();
        List<VoxelShape> cur = shapes;
        while (cur.size() > 1) {
            List<VoxelShape> next = new ArrayList<>((cur.size() + 1) / 2);
            for (int i = 0; i < cur.size(); i += 2) {
                next.add(i + 1 < cur.size() ? Shapes.joinUnoptimized(cur.get(i), cur.get(i + 1), BooleanOp.OR) : cur.get(i));
            }
            cur = next;
        }
        return cur.get(0).optimize();
    }

    /** Forme de selection complete de la caisse, exprimee dans le repere de la cellule i. */
    public static VoxelShape outline(CrateType type, Direction facing, int i) {
        return formes(type, facing).outline[i];
    }

    /** Forme de collision limitee a la cellule i. */
    public static VoxelShape collision(CrateType type, Direction facing, int i) {
        return formes(type, facing).collision[i];
    }

    /** La cellule i est-elle traversee par la caisse (elle recoit alors un bloc "partie") ? */
    public static boolean needed(CrateType type, Direction facing, int i) {
        return formes(type, facing).needed[i];
    }

    /** Place occupee au sol, caisse tournee vers le sud (cellules en largeur, hauteur, profondeur). */
    public static int[] cells(CrateType type) {
        int minX = 9, maxX = -9, minZ = 9, maxZ = -9, maxY = 0;
        for (int i = 0; i < COUNT; i++) {
            if (!needed(type, Direction.SOUTH, i)) continue;
            minX = Math.min(minX, dx(i));
            maxX = Math.max(maxX, dx(i));
            minZ = Math.min(minZ, dz(i));
            maxZ = Math.max(maxZ, dz(i));
            maxY = Math.max(maxY, dy(i));
        }
        return new int[]{maxX - minX + 1, maxY + 1, maxZ - minZ + 1};
    }

    /** Hauteur de la caisse (px). */
    public static float height(CrateType type) {
        int h = 0;
        for (int[] b : BOXES[type.ordinal()]) h = Math.max(h, b[4]);
        return h;
    }

    /** Plus grand ecart de la caisse au centre du bloc principal, en x ou en z (px), quelle que soit son orientation. */
    public static float halfWidth(CrateType type) {
        int w = 8;
        for (int[] b : BOXES[type.ordinal()]) {
            w = Math.max(w, Math.max(Math.max(8 - b[0], b[3] - 8), Math.max(8 - b[2], b[5] - 8)));
        }
        return w;
    }

    public static int dx(int index) {
        return (index % 9) / 3 - 1;
    }

    public static int dy(int index) {
        return index / 9;
    }

    public static int dz(int index) {
        return index % 3 - 1;
    }
}
