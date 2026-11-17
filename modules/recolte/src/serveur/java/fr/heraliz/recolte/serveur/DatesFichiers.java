package fr.heraliz.recolte.serveur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Date de modification des fichiers relus quand ils changent (recolte.json, villes des clans, zones VIP), relevée sur un
 * fil à part toutes les 5 s, comme dans HeraliZ Utils et Grades (01/11 : un simple coup d'œil au disque sur le fil du
 * serveur a déjà gelé un tick 117 ms quand le disque était occupé). Le fil du serveur ne lit que la date gardée ici ; il
 * ne touche le disque que quand le fichier a vraiment changé. Première demande d'un fichier : relevée tout de suite.
 */
final class DatesFichiers {
    private DatesFichiers() {
    }

    /** Fichier -> date de modification (ms), 0 s'il n'existe pas. */
    private static final Map<Path, Long> DATES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService FIL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-Recolte-dates-fichiers");
        t.setDaemon(true);
        return t;
    });

    static {
        FIL.scheduleWithFixedDelay(DatesFichiers::releverTout, 5, 5, TimeUnit.SECONDS);
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

    /** Date relevée maintenant (lecture demandée par le staff : /recharger). */
    static long relever(Path f) {
        long d = lire(f);
        DATES.put(f, d);
        return d;
    }

    private static long lire(Path f) {
        try {
            return Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0L;
        } catch (IOException | RuntimeException e) {
            return 0L;
        }
    }

    private static void releverTout() {
        for (Path f : DATES.keySet()) {
            DATES.put(f, lire(f));
        }
    }
}
