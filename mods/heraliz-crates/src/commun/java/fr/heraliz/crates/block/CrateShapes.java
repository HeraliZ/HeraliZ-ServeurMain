package fr.heraliz.crates.block;

import fr.heraliz.crates.crate.CrateType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Formes des caisses : une boite a base carree centree sur le bloc principal, a la taille du modele de chaque caisse
 * (mesuree avec l'apercu : Preview bounds). Le bloc principal est au centre de la couche du bas ; des blocs "parties"
 * invisibles remplissent les cellules que la boite traverse (au plus 3 x 3 cellules sur 3 couches).
 */
public final class CrateShapes {
    private CrateShapes() {
    }

    public static final int COUNT = 27;
    public static final int MASTER = 4;
    /** Demi-largeur maximale (px depuis le centre du bloc) et hauteur maximale (px). */
    public static final double MAX_HALF = 24, MAX_HEIGHT = 48;

    private static final Map<CrateType, VoxelShape[]> OUTLINE = new EnumMap<>(CrateType.class);
    private static final Map<CrateType, VoxelShape[]> COLLISION = new EnumMap<>(CrateType.class);
    private static final Map<CrateType, boolean[]> NEEDED = new EnumMap<>(CrateType.class);

    /**
     * Demi-largeur (px depuis le centre du bloc) et hauteur (px) de la caisse, mesurees sur le modele au repos, echelle
     * comprise. A mettre a jour si le modele ou Design.size change.
     */
    public static double[] dims(CrateType type) {
        return switch (type) {
            case SUJET_N07 -> new double[]{15.6, 29.5};
            case SYLVORN -> new double[]{16.8, 27.1};
            case MERE_DES_RUINES -> new double[]{22.1, 25.9};
            case VULKHAR -> new double[]{21.4, 26.1};
            case PORTEUR_DE_PESTE -> new double[]{16.8, 22.3};
            case CHEVALIER_SANS_VISAGE -> new double[]{18.1, 24.0};
            case KRAKEN -> new double[]{19.2, 22.7};
            case VOTE -> new double[]{19.5, 40.2};
            case QUETE -> new double[]{13.4, 21.0};
            case EVENT -> new double[]{20.3, 38.7};
            case GEMME -> new double[]{22.1, 45.2};
            case CAMO -> new double[]{20.3, 35.5};
            case PENDENTIF -> new double[]{13.2, 36.5};
            case STICKER -> new double[]{19.2, 30.0};
            case BUILD -> new double[]{20.6, 40.5};
            case TRAUMA -> new double[]{18.9, 32.6};
            case LARGAGE -> new double[]{19.6, 28.7};
            default -> new double[]{15.5, 24.0};
        };
    }

    static {
        for (CrateType type : CrateType.values()) {
            double[] d = dims(type);
            double h = Math.min(MAX_HALF, d[0]), top = Math.min(MAX_HEIGHT, d[1]);
            double min = 8 - h, max = 8 + h;
            VoxelShape[] out = new VoxelShape[COUNT], col = new VoxelShape[COUNT];
            boolean[] need = new boolean[COUNT];
            for (int i = 0; i < COUNT; i++) {
                int dx = dx(i), dy = dy(i), dz = dz(i);
                VoxelShape full = Block.box(min - dx * 16, -dy * 16, min - dz * 16, max - dx * 16, top - dy * 16, max - dz * 16);
                out[i] = full;
                col[i] = Shapes.join(full, Shapes.block(), BooleanOp.AND);
                need[i] = i == MASTER || !col[i].isEmpty();
            }
            OUTLINE.put(type, out);
            COLLISION.put(type, col);
            NEEDED.put(type, need);
        }
    }

    /** Forme de selection complete de la caisse, exprimee dans le repere de la cellule i. */
    public static VoxelShape outline(CrateType type, int i) {
        return OUTLINE.get(type)[i];
    }

    /** Forme de collision limitee a la cellule i. */
    public static VoxelShape collision(CrateType type, int i) {
        return COLLISION.get(type)[i];
    }

    /** La cellule i est-elle traversee par la caisse (elle recoit alors un bloc "partie") ? */
    public static boolean needed(CrateType type, int i) {
        return NEEDED.get(type)[i];
    }

    /** Place occupee au sol (cellules en largeur, hauteur, profondeur). */
    public static int[] cells(CrateType type) {
        int minX = 9, maxX = -9, maxY = 0;
        for (int i = 0; i < COUNT; i++) {
            if (!needed(type, i)) continue;
            minX = Math.min(minX, dx(i));
            maxX = Math.max(maxX, dx(i));
            maxY = Math.max(maxY, dy(i));
        }
        int w = maxX - minX + 1;
        return new int[]{w, maxY + 1, w};
    }

    /** Hauteur de la caisse (px). */
    public static float height(CrateType type) {
        return (float) Math.min(MAX_HEIGHT, dims(type)[1]);
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
