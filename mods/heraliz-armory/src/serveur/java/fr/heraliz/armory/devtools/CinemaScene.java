package fr.heraliz.armory.devtools;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decor du tournage video (dev seulement) : poste de controle en ruine facon DayZ autour de l'origine. Sol de terre et
 * d'herbe a taches (boue, gravier, podzol), route de gravier, ruines de beton et de briques en fond (vers -z), murets de
 * sacs de sable, blocs de beton, barils et caisses, barbeles, lanternes sur poteaux, feu de camp, sapins.
 */
final class CinemaScene {
    private CinemaScene() {}

    private static final BlockState LEAVES = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    private static int hash(int x, int z, int s) {
        int h = x * 374761393 + z * 668265263 + s * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double noise(int x, int z, int s, double cell) {
        double fx = x / cell, fz = z / cell;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = (hash(x0, z0, s) & 1023) / 1023.0, b = (hash(x0 + 1, z0, s) & 1023) / 1023.0;
        double c = (hash(x0, z0 + 1, s) & 1023) / 1023.0, d = (hash(x0 + 1, z0 + 1, s) & 1023) / 1023.0;
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 2);
    }

    private static void set(ServerLevel l, int x, int y, int z, Block b) {
        set(l, x, y, z, b.defaultBlockState());
    }

    static void build(ServerLevel l, int y) {
        int R = 44;
        // degagement et sol
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int h = 0; h < 14; h++) set(l, x, y + h, z, Blocks.AIR);
                double n = noise(x, z, 1, 5.0), m = noise(x, z, 2, 3.0);
                Block g;
                if (Math.abs(x) <= 3) g = m > 0.55 ? Blocks.COARSE_DIRT : (m > 0.25 ? Blocks.GRAVEL : Blocks.DIRT_PATH);
                else if (n > 0.72) g = Blocks.MUD;
                else if (n > 0.6) g = Blocks.COARSE_DIRT;
                else if (n < 0.18) g = Blocks.PODZOL;
                else if (m > 0.82) g = Blocks.GRAVEL;
                else g = Blocks.GRASS_BLOCK;
                set(l, x, y - 1, z, g);
                set(l, x, y - 2, z, Blocks.DIRT);
            }
        }
        // ruines en fond : grand batiment de beton eventre a gauche, pan de briques a droite
        for (int x = -16; x <= -4; x++) {
            for (int z = -22; z <= -14; z++) {
                boolean wall = x == -16 || x == -4 || z == -22 || z == -14;
                if (!wall) continue;
                int top = 6 + (int) (noise(x, z, 3, 2.0) * 5) - (x > -8 ? 3 : 0);
                for (int h = 0; h < top; h++) {
                    boolean window = (h == 2 || h == 3) && ((x + z) % 4 == 0);
                    boolean hole = noise(x * 3, h * 5 + z, 4, 1.5) > 0.86;
                    if (window || hole) continue;
                    double r = noise(x * 7 + h, z * 5 - h, 5, 1.0);
                    Block b = r > 0.7 ? Blocks.CRACKED_STONE_BRICKS : (r > 0.35 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.ANDESITE);
                    set(l, x, y + h, z, b);
                }
            }
        }
        for (int x = 7; x <= 15; x++) {
            int top = 3 + (int) (noise(x, 0, 6, 2.0) * 5);
            for (int h = 0; h < top; h++) {
                if ((h == 2 && x % 3 == 0) || noise(x * 5, h * 3, 7, 1.2) > 0.85) continue;
                set(l, x, y + h, -18, noise(x, h, 8, 1.0) > 0.5 ? Blocks.BRICKS : Blocks.MUD_BRICKS);
            }
        }
        // gravats au pied des ruines
        for (int i = 0; i < 40; i++) {
            int x = -18 + Math.floorMod(hash(i, 1, 9), 34), z = -13 + Math.floorMod(hash(i, 2, 9), 4);
            if (Math.abs(x) <= 3) continue;
            set(l, x, y, z, (i % 3 == 0) ? Blocks.COBBLESTONE_SLAB : (i % 3 == 1 ? Blocks.ANDESITE_SLAB : Blocks.GRAVEL));
        }
        // murets de sacs de sable (derriere et sur les cotes de l'escouade)
        int[][] bags = {{-9, -7, -3, -7}, {3, -7, 9, -7}, {-10, -6, -10, -2}, {10, -6, 10, -2}};
        for (int[] b : bags) {
            for (int x = b[0]; x <= b[2]; x++) {
                for (int z = b[1]; z <= b[3]; z++) {
                    set(l, x, y, z, Blocks.PACKED_MUD);
                    if ((x + z) % 3 != 0) set(l, x, y + 1, z, Blocks.MUD_BRICK_SLAB);
                }
            }
        }
        // blocs de beton le long de la route
        for (int z = -4; z <= 24; z += 4) {
            for (int sx : new int[]{-5, 5}) {
                set(l, sx, y, z, Blocks.LIGHT_GRAY_CONCRETE);
                set(l, sx, y + 1, z, Blocks.SMOOTH_STONE_SLAB);
            }
        }
        // barils et caisses
        int[][] barrels = {{-7, -5}, {-8, -5}, {-7, -4}, {7, -5}};
        for (int[] b : barrels) set(l, b[0], y, b[1], Blocks.BARREL);
        set(l, -8, y + 1, -5, Blocks.BARREL);
        int[][] crates = {{8, -5}, {8, -4}, {9, -5}};
        for (int[] c : crates) set(l, c[0], y, c[1], Blocks.SPRUCE_PLANKS);
        set(l, 8, y + 1, -5, Blocks.SPRUCE_PLANKS);
        // barbeles
        for (int z = -6; z <= 14; z++) {
            for (int sx : new int[]{-13, 13}) {
                set(l, sx, y, z, z % 4 == 0 ? Blocks.SPRUCE_FENCE : Blocks.IRON_BARS);
                if (z % 2 == 0) set(l, sx, y + 1, z, Blocks.COBWEB);
            }
        }
        // lanternes sur poteaux, feu de camp
        for (int[] p : new int[][]{{-6, -8}, {6, -8}, {-11, 2}, {11, 2}}) {
            for (int h = 0; h < 3; h++) set(l, p[0], y + h, p[1], Blocks.SPRUCE_FENCE);
            set(l, p[0], y + 3, p[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        set(l, -5, y, -10, Blocks.CAMPFIRE);
        // sapins
        int[][] trees = {{-24, -30}, {-18, -34}, {-8, -36}, {4, -33}, {14, -30}, {22, -26}, {-28, -16}, {28, -12}, {-30, 0},
                {30, 4}, {-26, 14}, {26, 18}, {-20, -40}, {18, -40}, {0, -42}};
        for (int i = 0; i < trees.length; i++) spruce(l, trees[i][0], y, trees[i][1], 9 + Math.floorMod(hash(i, 3, 11), 5));
        // lisiere de foret tout autour (horizon des plans larges), plus dense au loin
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        for (int[] t : trees) placed.add(t);
        for (int i = 0; placed.size() < 150 && i < 4000; i++) {
            double a = (hash(i, 4, 12) & 0xFFFF) / 65536.0 * Math.PI * 2;
            double r = 24 + (hash(i, 5, 12) & 0xFFFF) / 65536.0 * 19;
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            if (Math.abs(x) <= 4 && z > 0) continue;               // la route reste degagee vers le sud
            if (x >= -17 && x <= -3 && z >= -23 && z <= -13) continue;  // ruines
            boolean free = true;
            for (int[] q : placed) {
                if ((q[0] - x) * (q[0] - x) + (q[1] - z) * (q[1] - z) < 13) {
                    free = false;
                    break;
                }
            }
            if (!free) continue;
            placed.add(new int[]{x, z});
            spruce(l, x, y, z, 8 + Math.floorMod(hash(i, 6, 12), 7));
        }
        // mirador de bois a l'arriere-plan
        watchtower(l, -11, y, -10);
    }

    private static void watchtower(ServerLevel l, int x0, int y, int z0) {
        int top = 6;
        for (int[] c : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            for (int h = 0; h <= top + 3; h++) set(l, x0 + c[0], y + h, z0 + c[1], Blocks.STRIPPED_SPRUCE_LOG);
        }
        for (int dx = 0; dx <= 3; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                boolean corner = (dx == 0 || dx == 3) && (dz == 0 || dz == 3);
                if (!corner) set(l, x0 + dx, y + top, z0 + dz, Blocks.SPRUCE_PLANKS);
                boolean edge = dx == 0 || dx == 3 || dz == 0 || dz == 3;
                if (edge && !corner) set(l, x0 + dx, y + top + 1, z0 + dz, Blocks.SPRUCE_FENCE);
                set(l, x0 + dx, y + top + 4, z0 + dz, Blocks.SPRUCE_SLAB);
            }
        }
        for (int h = 1; h < top; h++) set(l, x0 + 1, y + h, z0, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH));
        // croisillons
        for (int h = 1; h < top; h += 2) {
            set(l, x0 + 1, y + h, z0 + 3, Blocks.SPRUCE_FENCE);
            set(l, x0 + 2, y + h + 1, z0 + 3, Blocks.SPRUCE_FENCE);
        }
    }

    private static void spruce(ServerLevel l, int x, int y, int z, int h) {
        for (int k = 0; k < h; k++) set(l, x, y + k, z, Blocks.SPRUCE_LOG);
        for (int k = 2; k <= h; k++) {
            int r = Math.max(0, (h - k) / 3 + ((h - k) % 3 == 0 ? 1 : 0));
            if (k == h) r = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r + 1 || (dx == 0 && dz == 0 && k < h)) continue;
                    set(l, x + dx, y + k, z + dz, LEAVES);
                }
            }
        }
        set(l, x, y + h, z, LEAVES);
    }
}
