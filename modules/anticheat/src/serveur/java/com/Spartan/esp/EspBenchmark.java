package com.Spartan.esp;

import com.Spartan.config.SpartanConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * /spartan esp bench : coût de l'anti-ESP sur la VRAIE carte du serveur, avant d'avoir
 * 70 joueurs connectés. Place des joueurs fictifs autour de l'admin (en surface, sur
 * les toits, dans les bâtiments et les sous-sols : n'importe quel endroit où un joueur
 * peut se tenir), évalue toutes les paires et estime le coût par tick en régime normal.
 */
public final class EspBenchmark {
    private EspBenchmark() {
    }

    public static List<String> run(ServerLevel level, Vec3 center, int count, int radius) {
        Random r = new Random();
        List<Sight.Eye> eyes = new ArrayList<>();
        List<Sight.Body> bodies = new ArrayList<>();
        int indoor = 0;
        for (int attempt = 0; eyes.size() < count && attempt < count * 60; attempt++) {
            int x = Mth.floor(center.x) + r.nextInt(2 * radius + 1) - radius;
            int z = Mth.floor(center.z) + r.nextInt(2 * radius + 1) - radius;
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
                continue;
            }
            int y = standingSpot(level, x, z, r);
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            double px = x + 0.2 + r.nextDouble() * 0.6;
            double pz = z + 0.2 + r.nextDouble() * 0.6;
            double vx = (r.nextDouble() - 0.5) * 0.4;
            double vz = (r.nextDouble() - 0.5) * 0.4;
            eyes.add(new Sight.Eye(px, y + 1.62, pz, vx, 0, vz, true));
            bodies.add(new Sight.Body(px - 0.3, y, pz - 0.3, px + 0.3, y + 1.8, pz + 0.3, vx, 0, vz));
            if (!level.canSeeSky(new BlockPos(x, y + 1, z))) {
                indoor++;
            }
        }
        List<String> out = new ArrayList<>();
        if (eyes.size() < 2) {
            out.add("Pas assez d'emplacements chargés autour de vous pour placer des joueurs fictifs.");
            return out;
        }
        Occluders.refresh();
        LevelVoxels voxels = new LevelVoxels();
        voxels.use(level, level.getMinSection(), level.getSectionsCount());
        Raycaster rc = new Raycaster(voxels);
        Sight sight = new Sight(rc);
        double peek = SpartanConfig.ESP_PEEK.get();
        double jump = SpartanConfig.ESP_JUMP.get();
        double range = level.getServer().getPlayerList().getViewDistance() * 16.0;
        int pairs = 0;
        int visible = 0;
        double perTick = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < eyes.size(); i++) {
            for (int j = 0; j < eyes.size(); j++) {
                if (i == j) {
                    continue;
                }
                Sight.Eye e = eyes.get(i);
                Sight.Body b = bodies.get(j);
                double dx = e.x() - (b.minX() + b.maxX()) / 2;
                double dz = e.z() - (b.minZ() + b.maxZ()) / 2;
                if (dx * dx + dz * dz > range * range) {
                    continue;
                }
                pairs++;
                boolean seen = sight.canSee(e, b, 3, peek, jump);
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (seen) {
                    visible++;
                    perTick += 1.0 / 4; // revérifiée tous les 4 ticks
                } else {
                    perTick += 1.0 / (dist < 16 ? 1 : dist < 32 ? 2 : dist < 64 ? 3 : dist < 128 ? 4 : 6);
                }
            }
        }
        long dt = System.nanoTime() - t0;
        double usPerPair = pairs == 0 ? 0 : dt / 1000.0 / pairs;
        out.add(String.format("%d joueurs fictifs (%d à l'abri du ciel) dans %d blocs : %d paires, %d en vue.",
                eyes.size(), indoor, radius, pairs, visible));
        out.add(String.format("Évaluation complète : %.1f ms (%.1f µs par paire, %.1f rayons, %.0f blocs par rayon).",
                dt / 1e6, usPerPair, pairs == 0 ? 0.0 : (double) rc.rays() / pairs,
                rc.rays() == 0 ? 0.0 : (double) rc.steps() / rc.rays()));
        double ms = perTick * usPerPair / 1000.0;
        int threads = SpartanConfig.ESP_THREADS.get() < 0
                ? Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4)) : SpartanConfig.ESP_THREADS.get();
        out.add(String.format("Joueurs tous en mouvement : ~%.0f calculs par tick, soit ~%.1f ms de calcul par tick "
                + "(les joueurs immobiles ne coûtent presque rien).", perTick, ms));
        out.add(threads > 0
                ? String.format("Réparti sur %d thread(s) séparé(s) : ~%.1f ms par thread et par tick, presque rien sur le "
                        + "thread principal du serveur (limite : 45 ms par thread).", threads, ms / threads)
                : String.format("Sur le thread principal (threads = 0) : ~%.1f ms par tick, budget %.1f ms.",
                        ms, SpartanConfig.ESP_BUDGET_MICROS.get() / 1000.0));
        return out;
    }

    /** Un endroit au hasard où un joueur peut se tenir dans cette colonne, ou MIN_VALUE. */
    private static int standingSpot(ServerLevel level, int x, int z, Random r) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int bottom = Math.max(level.getMinBuildHeight() + 1, top - 48);
        List<Integer> spots = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            if (free(level, pos.set(x, y, z)) && free(level, pos.set(x, y + 1, z)) && !free(level, pos.set(x, y - 1, z))) {
                spots.add(y);
            }
        }
        return spots.isEmpty() ? Integer.MIN_VALUE : spots.get(r.nextInt(spots.size()));
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }
}
