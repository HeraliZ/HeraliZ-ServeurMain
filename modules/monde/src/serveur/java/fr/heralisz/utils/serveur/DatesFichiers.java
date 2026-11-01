package fr.heralisz.utils.serveur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Date de modification des fichiers relus quand ils changent (zones.json des villes, heraliz_zones.json, zones_vip.json),
 * relevée sur un fil à part toutes les 5 s (01/11, même méthode que la boutique) : le fil du serveur la demandait lui-même
 * au disque, jusqu'à 128 fois par tick et par joueur qui part du spawn (tirage du point d'arrivée), et un disque occupé a
 * déjà gelé un tick 117 ms pour un seul de ces coups d'œil (28/10). Le fil du serveur ne lit plus que la date gardée ici ;
 * il ne touche le disque que quand le fichier a vraiment changé. Première demande d'un fichier : relevée tout de suite.
 */
public final class DatesFichiers {
    private DatesFichiers() {
    }

    /** Fichier -> date de modification (ms), 0 s'il n'existe pas. */
    private static final Map<Path, Long> DATES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService FIL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-Monde-dates-fichiers");
        t.setDaemon(true);
        return t;
    });

    static {
        FIL.scheduleWithFixedDelay(DatesFichiers::relever, 5, 5, TimeUnit.SECONDS);
    }

    /** Date de modification connue du fichier (ms), 0 s'il n'existe pas ; sans accès au disque après la première fois. */
    public static long date(Path f) {
        Long d = DATES.get(f);
        if (d == null) {
            d = lire(f);
            DATES.put(f, d);
        }
        return d;
    }

    private static long lire(Path f) {
        try {
            return Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0L;
        } catch (IOException | RuntimeException e) {
            return 0L;
        }
    }

    private static void relever() {
        for (Path f : DATES.keySet()) DATES.put(f, lire(f));
    }
}
