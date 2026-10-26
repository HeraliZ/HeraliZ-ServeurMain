package com.Spartan.util;

import com.Spartan.Spartan;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Exécuteur dédié aux écritures disque (preuves, logs, bans).
 * Le thread serveur est mono-thread : toute I/O synchrone bloque le jeu entier.
 * Tout ce qui écrit sur le disque passe par ici, hors du thread de tick.
 * Les tâches s'exécutent dans l'ordre de soumission.
 */
public final class AsyncIO {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Spartan-IO");
        t.setDaemon(true);
        return t;
    });

    private AsyncIO() {
    }

    public static void submit(Runnable task) {
        EXEC.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Spartan.LOGGER.warn("[Spartan] Erreur I/O asynchrone : {}", t.getMessage());
            }
        });
    }

    /**
     * Attend que toutes les écritures en file soient faites (arrêt du serveur) :
     * un /banpc juste avant un arrêt ne doit pas être perdu.
     */
    public static void flush(long timeoutMs) {
        try {
            EXEC.submit(() -> { }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Vidage des écritures incomplet : {}", e.getMessage());
        }
    }
}
