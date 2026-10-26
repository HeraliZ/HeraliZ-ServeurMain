package com.Spartan.esp;

import java.util.HashMap;
import java.util.Map;

/** Monde de test : grille de blocs creuse (plein, boîtes partielles) en mémoire. */
final class Grid implements Raycaster.Voxels {
    private final Map<Long, double[]> blocks = new HashMap<>();

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
    }

    @Override
    public double[] at(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    Grid full(int x, int y, int z) {
        blocks.put(key(x, y, z), Raycaster.FULL);
        return this;
    }

    Grid boxes(int x, int y, int z, double... boxes) {
        blocks.put(key(x, y, z), boxes);
        return this;
    }

    /** Remplit le pavé [x0..x1]×[y0..y1]×[z0..z1] de blocs pleins. */
    Grid fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    full(x, y, z);
                }
            }
        }
        return this;
    }

    void clear(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }
}
