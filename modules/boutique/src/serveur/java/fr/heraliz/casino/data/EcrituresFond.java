package fr.heraliz.casino.data;

import fr.heraliz.casino.HeralizCasino;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Écritures des données du casino sur un fil à part (27/10, perf : la comptabilité du casino, 120 jours de mises et de
 * joueurs par machine, était compressée et écrite sur le fil serveur à chaque sauvegarde automatique). Le contenu est
 * relevé sur le fil serveur dans un NBT neuf (jamais partagé avec le jeu) ; seuls la compression, l'écriture et le
 * renommage partent ici, un seul fil, dans l'ordre d'arrivée. Plusieurs relevés d'un même fichier en attente : seul le
 * plus récent est écrit. Arrêt du serveur : dès ServerStopping (donc pendant la dernière sauvegarde du monde), chaque
 * écriture est attendue, puis tout est attendu à ServerStopped (rien n'est perdu avant la fin du programme).
 */
public final class EcrituresFond {
    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    public interface Ecriture {
        void ecrire() throws Exception;
    }

    /** siEchec : null si rien à faire quand l'écriture rate. */
    private record Tache(Ecriture ecriture, Runnable siEchec) {
    }

    private static final ExecutorService FIL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-Casino-sauvegarde");
        t.setDaemon(true);
        return t;
    });
    /** Dernière écriture en attente de chaque fichier. */
    private static final ConcurrentHashMap<Path, Tache> EN_ATTENTE = new ConcurrentHashMap<>();
    private static volatile boolean arret;

    /** Une fois, au chargement de la partie serveur. */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ServerAboutToStartEvent.class, e -> arret = false);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ServerStoppingEvent.class, e -> arret = true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppedEvent.class, e -> attendre());
    }

    /**
     * Met en file l'écriture du fichier {@code f} ; {@code siEchec} (appelé sur le fil d'écriture : seulement poser un
     * drapeau) redonne la donnée « à sauver ». À l'arrêt du serveur, attend la fin de l'écriture.
     */
    public static void ecrire(Path f, Ecriture ecriture, @Nullable Runnable siEchec) {
        if (EN_ATTENTE.put(f, new Tache(ecriture, siEchec)) == null) {
            try {
                FIL.execute(() -> faire(f));
            } catch (RejectedExecutionException ex) {
                faire(f);
            }
        }
        if (arret) attendre();
    }

    private static void faire(Path f) {
        Tache t = EN_ATTENTE.remove(f);
        if (t != null) lancer(f, t.ecriture(), t.siEchec());
    }

    private static void lancer(Path f, Ecriture e, @Nullable Runnable siEchec) {
        try {
            e.ecrire();
        } catch (Throwable ex) {
            HeralizCasino.LOGGER.error("[heralizcasino] écriture de {} impossible", f, ex);
            if (siEchec != null) siEchec.run();
        }
    }

    /** Attend la fin de toutes les écritures en file (arrêt du serveur, lecture d'un fichier juste écrit), 60 s au plus. */
    public static void attendre() {
        try {
            FIL.submit(() -> { }).get(60, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException ex) {
            HeralizCasino.LOGGER.error("[heralizcasino] écritures en fond pas terminées", ex);
        }
    }
}
