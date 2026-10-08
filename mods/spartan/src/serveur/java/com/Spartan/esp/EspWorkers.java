package com.Spartan.esp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import net.minecraft.server.level.ServerLevel;

/**
 * Threads de calcul de l'anti-ESP : les lignes de vue sont calculées sur d'autres
 * cœurs, en parallèle du tick du serveur, qui ne fait que préparer le lot et en
 * appliquer le résultat au tick suivant.
 *
 * Un lot est découpé en tranches entrelacées (la tranche k prend les paires k,
 * k+n, k+2n…) : comme le lot est trié par urgence, tous les threads traitent
 * d'abord les paires les plus urgentes. Passé l'échéance, le reste est abandonné
 * (ces paires seront montrées si elles restent trop longtemps sans vérification).
 */
final class EspWorkers {
    /** Une paire à vérifier, avec tout ce qu'il faut pour le faire hors du thread serveur. */
    record Job(Object pair, ServerLevel level, int minSection, int sectionCount, Sight.Eye eye, Sight.Body body,
               int lookahead) {
    }

    /** Un lot en cours : résultats écrits par les threads, lus par le serveur une fois chaque tranche finie. */
    static final class Batch {
        final Job[] jobs;
        final long tick;
        final double peek;
        final double jump;
        final long deadline;
        final int slices;
        final boolean[] seen;
        final boolean[] done;
        final long[] rays;
        final long[] steps;
        final AtomicIntegerArray finished;
        final boolean[] collected;
        volatile boolean cancel;

        Batch(Job[] jobs, long tick, double peek, double jump, long deadline, int slices) {
            this.jobs = jobs;
            this.tick = tick;
            this.peek = peek;
            this.jump = jump;
            this.deadline = deadline;
            this.slices = slices;
            this.seen = new boolean[jobs.length];
            this.done = new boolean[jobs.length];
            this.rays = new long[slices];
            this.steps = new long[slices];
            this.finished = new AtomicIntegerArray(slices);
            this.collected = new boolean[slices];
        }
    }

    /** Outils d'un thread de calcul (non partagés). */
    private static final class Worker {
        final LevelVoxels voxels = new LevelVoxels();
        final Raycaster rc = new Raycaster(voxels);
        final Sight sight = new Sight(rc);
    }

    private static final ThreadLocal<Worker> WORKER = ThreadLocal.withInitial(Worker::new);
    private static final AtomicInteger IDS = new AtomicInteger();

    private final ExecutorService pool;
    private final int threads;

    EspWorkers(int threads) {
        this.threads = threads;
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Spartan-AntiESP-" + IDS.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    int threads() {
        return threads;
    }

    Batch submit(Job[] jobs, long tick, double peek, double jump, long deadline) {
        int slices = Math.max(1, Math.min(jobs.length, threads * 2));
        Batch b = new Batch(jobs, tick, peek, jump, deadline, slices);
        for (int k = 0; k < slices; k++) {
            int slice = k;
            pool.execute(() -> run(b, slice));
        }
        return b;
    }

    private static void run(Batch b, int k) {
        Worker w = WORKER.get();
        long r0 = w.rc.rays();
        long s0 = w.rc.steps();
        try {
            w.voxels.reset();
            for (int i = k; i < b.jobs.length; i += b.slices) {
                if (b.cancel || System.nanoTime() > b.deadline) {
                    break;
                }
                Job j = b.jobs[i];
                try {
                    w.voxels.use(j.level(), j.minSection(), j.sectionCount());
                    b.seen[i] = w.sight.canSee(j.eye(), j.body(), j.lookahead(), b.peek, b.jump);
                } catch (Throwable t) {
                    b.seen[i] = true; // lecture d'un chunk modifié au même instant : dans le doute, visible
                }
                b.done[i] = true;
            }
        } finally {
            b.rays[k] = w.rc.rays() - r0;
            b.steps[k] = w.rc.steps() - s0;
            b.finished.set(k, 1);
        }
    }

    void shutdown() {
        pool.shutdownNow();
    }
}
