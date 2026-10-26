package com.Spartan.esp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Ordre de grandeur du coût de l'anti-ESP : une ville de 300 × 300 blocs (bâtiments
 * creux avec portes et fenêtres) et 70 joueurs, dont la moitié à l'intérieur.
 * Toutes les paires (dans les deux sens) à moins de 160 blocs sont évaluées.
 */
class SightBenchmarkTest {
    /** Grille dense (comme les sections de chunk) : 0 = air, 1 = plein. */
    static final class Dense implements Raycaster.Voxels {
        final int sx = 320;
        final int sy = 40;
        final int sz = 320;
        final byte[] data = new byte[sx * sy * sz];

        void set(int x, int y, int z) {
            if (x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz) {
                data[(y * sz + z) * sx + x] = 1;
            }
        }

        void unset(int x, int y, int z) {
            if (x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz) {
                data[(y * sz + z) * sx + x] = 0;
            }
        }

        @Override
        public double[] at(int x, int y, int z) {
            if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) {
                return null;
            }
            return data[(y * sz + z) * sx + x] != 0 ? Raycaster.FULL : null;
        }
    }

    record Building(int x0, int z0, int x1, int z1, int h) {
        boolean inside(double x, double z) {
            return x > x0 + 1 && x < x1 && z > z0 + 1 && z < z1;
        }
    }

    @Test
    void cityWithSeventyPlayers() {
        Random r = new Random(42);
        Dense w = new Dense();
        int ground = 10;
        for (int x = 0; x < w.sx; x++) {
            for (int z = 0; z < w.sz; z++) {
                for (int y = 0; y < ground; y++) {
                    w.set(x, y, z);
                }
            }
        }
        List<Building> buildings = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            int x0 = 10 + r.nextInt(280);
            int z0 = 10 + r.nextInt(280);
            int x1 = x0 + 6 + r.nextInt(9);
            int z1 = z0 + 6 + r.nextInt(9);
            int h = 4 + r.nextInt(7);
            buildings.add(new Building(x0, z0, x1, z1, h));
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                    for (int y = ground; y < ground + h; y++) {
                        if (wall) {
                            w.set(x, y, z);
                        }
                    }
                    w.set(x, ground + h, z); // toit
                }
            }
            // porte et deux fenêtres
            w.unset(x0, ground, (z0 + z1) / 2);
            w.unset(x0, ground + 1, (z0 + z1) / 2);
            w.unset(x1, ground + 1, z0 + 2);
            w.unset((x0 + x1) / 2, ground + 1, z1);
        }
        List<Sight.Eye> eyes = new ArrayList<>();
        List<Sight.Body> bodies = new ArrayList<>();
        int inside = 0;
        while (eyes.size() < 70) {
            double x = 5 + r.nextDouble() * 300;
            double z = 5 + r.nextDouble() * 300;
            boolean wantInside = eyes.size() % 2 == 0;
            Building in = null;
            for (Building b : buildings) {
                if (b.inside(x, z)) {
                    in = b;
                }
            }
            if (wantInside != (in != null)) {
                continue;
            }
            boolean blockedFeet = w.at((int) x, ground, (int) z) != null || w.at((int) x, ground + 1, (int) z) != null;
            if (blockedFeet) {
                continue;
            }
            inside += in != null ? 1 : 0;
            double vx = (r.nextDouble() - 0.5) * 0.4;
            double vz = (r.nextDouble() - 0.5) * 0.4;
            eyes.add(new Sight.Eye(x, ground + 1.62, z, vx, 0, vz, true));
            bodies.add(new Sight.Body(x - 0.3, ground, z - 0.3, x + 0.3, ground + 1.8, z + 0.3, vx, 0, vz));
        }
        Raycaster rc = new Raycaster(w);
        Sight sight = new Sight(rc);
        // Échauffement du JIT.
        for (int k = 0; k < 3; k++) {
            run(eyes, bodies, sight);
        }
        long rays0 = rc.rays();
        long steps0 = rc.steps();
        long t0 = System.nanoTime();
        int[] res = run(eyes, bodies, sight);
        long dt = System.nanoTime() - t0;
        long rays = rc.rays() - rays0;
        long steps = rc.steps() - steps0;
        double perPair = dt / 1000.0 / res[0];
        System.out.printf("[anti-ESP bench] %d joueurs (%d dedans), %d paires orientées, %d visibles, "
                        + "%.1f ms au total, %.1f µs/paire, %.1f rayons/paire, %.0f blocs/rayon%n",
                eyes.size(), inside, res[0], res[1], dt / 1e6, perPair, (double) rays / res[0],
                (double) steps / Math.max(1, rays));
        assertTrue(perPair < 500, "trop lent : " + perPair + " µs par paire");
    }

    private static int[] run(List<Sight.Eye> eyes, List<Sight.Body> bodies, Sight sight) {
        int pairs = 0;
        int visible = 0;
        for (int i = 0; i < eyes.size(); i++) {
            for (int j = 0; j < eyes.size(); j++) {
                if (i == j) {
                    continue;
                }
                Sight.Eye e = eyes.get(i);
                Sight.Body b = bodies.get(j);
                double dx = e.x() - (b.minX() + 0.3);
                double dz = e.z() - (b.minZ() + 0.3);
                if (dx * dx + dz * dz > 160 * 160) {
                    continue;
                }
                pairs++;
                if (sight.canSee(e, b, 4, 0.6, 0.6)) {
                    visible++;
                }
            }
        }
        return new int[] {pairs, visible};
    }
}
