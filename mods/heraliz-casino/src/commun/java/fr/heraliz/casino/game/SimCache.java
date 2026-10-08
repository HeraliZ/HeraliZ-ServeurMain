package fr.heraliz.casino.game;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Simulations en arriere-plan, gardees en memoire par reglages (redistribution affichee par la Tablette du Directeur,
 * page "Probabilites", commande /casino rtp). Une simulation avance par tranches sur son propre fil : son rapport
 * s'affine a chaque tranche, et elle s'arrete d'elle-meme quand plus personne ne la demande (les reglages ont change)
 * pour reprendre la ou elle en etait si on la redemande.
 */
public final class SimCache {
    private SimCache() {
    }

    /** Simulations en cours au plus ; rapports gardes en memoire ; delai sans demande avant l'arret (ms). */
    private static final int MAX_RUNNING = 2, KEEP = 24;
    private static final long STALE_MS = 2500;

    private static final class Job {
        final Simulator.Session session;
        final long chunk;
        volatile Simulator.Report report;
        volatile long lastAsk;
        boolean running, failed;

        Job(Simulator.Session session, long chunk) {
            this.session = session;
            this.chunk = chunk;
        }
    }

    /** Dans l'ordre du dernier acces (le plus ancien d'abord). */
    private static final Map<String, Job> JOBS = new LinkedHashMap<>(32, 0.75f, true);
    private static int running;

    /**
     * Rapport de la simulation "key" (la cle decrit la machine et tous ses reglages). null tant que la premiere tranche
     * n'est pas finie : redemander plus tard (a chaque image pour un ecran). Le rapport rendu peut encore s'affiner.
     * @param make  cree la simulation (appele une seule fois par cle ; doit travailler sur une copie des reglages)
     * @param chunk parties par tranche
     */
    public static Simulator.Report get(String key, Supplier<Simulator.Session> make, long chunk) {
        synchronized (JOBS) {
            Job j = JOBS.get(key);
            if (j == null) {
                j = new Job(make.get(), chunk);
                JOBS.put(key, j);
                // les plus anciennes simulations a l'arret sont oubliees
                Iterator<Map.Entry<String, Job>> it = JOBS.entrySet().iterator();
                while (JOBS.size() > KEEP && it.hasNext()) {
                    Job old = it.next().getValue();
                    if (!old.running && old != j) it.remove();
                }
            }
            j.lastAsk = System.currentTimeMillis();
            if (!j.running && !j.failed && !j.session.done() && running < MAX_RUNNING) {
                j.running = true;
                running++;
                start(j);
            }
            // echec avant le premier rapport : un rapport vide plutot qu'une attente sans fin
            return j.failed && j.report == null ? new Simulator.Report() : j.report;
        }
    }

    /** La simulation "key" est-elle terminee (rapport definitif) ? */
    public static boolean done(String key) {
        synchronized (JOBS) {
            Job j = JOBS.get(key);
            return j != null && (j.session.done() || j.failed);
        }
    }

    private static void start(Job j) {
        Thread t = new Thread(() -> {
            try {
                while (!j.session.done() && System.currentTimeMillis() - j.lastAsk < STALE_MS) {
                    j.report = j.session.advance(j.chunk);
                }
            } catch (RuntimeException e) {
                // reglages impossibles a simuler : on garde le dernier rapport, sans reessayer en boucle
                synchronized (JOBS) {
                    j.failed = true;
                }
            } finally {
                synchronized (JOBS) {
                    j.running = false;
                    running--;
                }
            }
        }, "HeralizCasino-Sim");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
