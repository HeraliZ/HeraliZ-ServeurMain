package fr.heraliz.quetes.serveur;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Écritures des progressions des joueurs (heraliz_quetes/joueurs/<uuid>.json) sur un fil à part (01/11, perf : toutes les
 * 30 s, chaque progression changée était écrite et renommée sur le fil du serveur, soit des dizaines de fichiers dans le
 * même tick avec 150 joueurs, plus à chaque déconnexion). Même méthode que HeraliZ Loot, Clans et Événements : le texte
 * est fait sur le fil du serveur (copie, jamais l'objet du jeu) ; seuls l'écriture et le renommage partent ici, un seul
 * fil, dans l'ordre d'arrivée. Plusieurs textes d'un même fichier en attente : seul le plus récent est écrit. Ce qui doit
 * être sur le disque avant de continuer (quête payée) attend la fin ({@link #attendre}) ; la relecture d'un fichier attend
 * aussi qu'il soit écrit ({@link #occupe}). Arrêt du serveur : dès ServerStopping, chaque écriture est attendue, puis tout
 * est attendu à ServerStopped.
 */
final class EcrituresFond {
    private static final Logger LOG = LogUtils.getLogger();

    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final ExecutorService FIL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-Quetes-sauvegarde");
        t.setDaemon(true);
        return t;
    });
    /** Dernière écriture en attente de chaque fichier. */
    private static final ConcurrentHashMap<Path, Ecriture> EN_ATTENTE = new ConcurrentHashMap<>();
    /** Fichiers en cours d'écriture sur le fil. */
    private static final Set<Path> EN_COURS = ConcurrentHashMap.newKeySet();
    private static volatile boolean arret;

    /** Une fois, au chargement de la partie serveur. */
    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ServerAboutToStartEvent.class, e -> arret = false);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ServerStoppingEvent.class, e -> arret = true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppedEvent.class, e -> attendre());
    }

    /** Met en file l'écriture du fichier {@code f} (remplace celle encore en attente). À l'arrêt du serveur, l'attend. */
    static void ecrire(Path f, Ecriture ecriture) {
        if (EN_ATTENTE.put(f, ecriture) == null) {
            try {
                FIL.execute(() -> faire(f));
            } catch (RejectedExecutionException ex) {
                faire(f);
            }
        }
        if (arret) attendre();
    }

    /** Une écriture de {@code f} attend ou se fait : le fichier sur le disque n'est peut-être pas encore le dernier. */
    static boolean occupe(Path f) {
        return EN_ATTENTE.containsKey(f) || EN_COURS.contains(f);
    }

    private static void faire(Path f) {
        EN_COURS.add(f);
        try {
            Ecriture e = EN_ATTENTE.remove(f);
            if (e != null) e.ecrire();
        } catch (Throwable ex) {
            LOG.error("[heraliz_quetes] écriture de {} impossible", f, ex);
        } finally {
            EN_COURS.remove(f);
        }
    }

    /** Attend la fin de toutes les écritures en file (quête payée, relecture, arrêt du serveur), 60 s au plus. */
    static void attendre() {
        try {
            FIL.submit(() -> { }).get(60, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException ex) {
            LOG.error("[heraliz_quetes] écritures en fond pas terminées", ex);
        }
    }
}
