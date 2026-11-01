package fr.heraliz.grades.serveur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Date de modification des fichiers de réglages relus quand ils changent (boutique.json, hdv.json), relevée sur un fil à
 * part toutes les 5 s (01/11) : le fil du serveur la demandait lui-même au disque toutes les 10 s, et une fois le disque
 * occupé (sauvegarde du monde), ce simple coup d'œil a gelé un tick 117 ms (28/10 à 17:46). Le fil du serveur ne lit plus
 * que la date gardée ici ; il ne touche le disque que quand le fichier a vraiment changé. Première demande d'un fichier
 * (au démarrage) : relevée tout de suite.
 */
final class DatesFichiers {
    private DatesFichiers() {
    }

    /** Fichier -> date de modification (ms), 0 s'il n'existe pas. */
    private static final Map<Path, Long> DATES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService FIL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-Grades-dates-fichiers");
        t.setDaemon(true);
        return t;
    });

    static {
        FIL.scheduleWithFixedDelay(DatesFichiers::relever, 5, 5, TimeUnit.SECONDS);
    }

    /** Date de modification connue du fichier (ms), 0 s'il n'existe pas ; sans accès au disque après la première fois. */
    static long date(Path f) {
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
        for (Path f : DATES.keySet()) {
            DATES.put(f, lire(f));
        }
    }
}
