package fr.heraliz.noyau.api;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L'écrivain en fond commun à tous nos mods (19/11, audit des lenteurs, point C2) : avant, sept copies (HeraliZ Loot,
 * Clans, Quêtes, Événements, Casino, CRac, coffres personnels) avaient chacune leur fil, et level.dat le sien. Ici, une
 * seule réserve de fils d'écriture ({@value #FILS} fils « HeraliZ-ecritures »), et une {@link Voie} par module : les
 * écritures d'une voie se font une à la fois, dans l'ordre d'arrivée (comme avant avec son propre fil) ; deux voies
 * peuvent écrire en même temps. Attendre une voie ({@link Voie#attendre()}) n'attend jamais les autres : une opération
 * d'argent des clans n'attend pas la rotation du loot.
 * <p>Le contenu est toujours relevé par le module sur le fil du serveur, dans un objet neuf (NBT, arbre JSON, texte) jamais
 * partagé avec le jeu ; seuls la mise en texte ou la compression, l'écriture et le renommage partent ici. Plusieurs relevés
 * d'un même fichier en attente : seul le plus récent est écrit ({@link Voie#ecrire}). Arrêt du serveur : dès
 * {@code ServerStopping} (donc pendant la dernière sauvegarde du monde), chaque écriture est attendue, puis tout est attendu
 * à {@code ServerStopped} (rien n'est perdu avant la fin du programme).
 * <p>Classe lue par d'autres mods : ne pas la renommer, ni ses méthodes.
 */
public final class EcrituresFond {
    private EcrituresFond() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    /** Fils d'écriture partagés par toutes les voies. */
    public static final int FILS = 2;
    /** Attente la plus longue d'une voie (arrêt du serveur, fichier qui doit être sur le disque avant de continuer). */
    private static final long ATTENTE_S = 60;

    /** Une écriture à faire sur un fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    public interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final AtomicInteger NUMERO = new AtomicInteger();
    private static final ThreadLocal<Boolean> SUR_UN_FIL = new ThreadLocal<>();
    private static final ExecutorService RESERVE = new ThreadPoolExecutor(FILS, FILS, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(() -> {
            SUR_UN_FIL.set(Boolean.TRUE);
            r.run();
        }, "HeraliZ-ecritures-" + NUMERO.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Map<String, Voie> VOIES = new ConcurrentHashMap<>();
    private static volatile boolean arret;
    private static final AtomicBoolean BRANCHE = new AtomicBoolean();

    // compteurs pour /diag etat (tous fils confondus)
    private static final AtomicLong ECRITS = new AtomicLong(), REMPLACES = new AtomicLong(), ECHECS = new AtomicLong(), NANOS = new AtomicLong();

    /**
     * Une fois (la partie serveur du noyau l'appelle ; un module peut l'appeler aussi, sans effet la 2e fois) : arrêt du
     * serveur suivi (attente de chaque écriture dès ServerStopping, de toutes à ServerStopped).
     */
    public static void brancher() {
        if (!BRANCHE.compareAndSet(false, true)) return;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ServerAboutToStartEvent.class, e -> arret = false);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ServerStoppingEvent.class, e -> arret = true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppedEvent.class, e -> attendreTout());
    }

    /** La voie de ce nom (« loot », « clans »…), créée à la première demande. */
    public static Voie voie(String nom) {
        return VOIES.computeIfAbsent(nom.toLowerCase(Locale.ROOT), Voie::new);
    }

    /** Le serveur s'arrête : chaque écriture est attendue par celui qui la demande. */
    public static boolean arret() {
        return arret;
    }

    /** Attend toutes les voies (arrêt du serveur), {@value #ATTENTE_S} s au plus chacune. */
    public static void attendreTout() {
        for (Voie v : VOIES.values()) v.attendre();
    }

    /** Une ligne pour {@code /diag etat}. */
    public static String etat() {
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT,
                "Écritures en fond (%d fils pour tous les modules) : %d fichiers écrits en %.0f ms, %d relevés remplacés avant d'être écrits, %d échecs ; voies :",
                FILS, ECRITS.get(), NANOS.get() / 1e6, REMPLACES.get(), ECHECS.get()));
        List<Voie> l = new ArrayList<>(VOIES.values());
        l.sort((a, b) -> a.nom.compareTo(b.nom));
        for (Voie v : l) s.append(' ').append(v.nom).append(' ').append(v.ecrits.get()).append(v.file.isEmpty() ? "" : " (" + v.file.size() + " en file)");
        return s.append(String.format("%n")).toString();
    }

    /** Vrai sur un fil d'écriture (attendre sa propre voie depuis une écriture la bloquerait). */
    private static boolean surUnFil() {
        return SUR_UN_FIL.get() != null;
    }

    /**
     * Les écritures d'un module : une à la fois, dans l'ordre d'arrivée, sur les fils partagés. Toutes les méthodes peuvent
     * être appelées de n'importe quel fil (en pratique : le fil du serveur).
     */
    public static final class Voie {
        private final String nom;
        private final ConcurrentLinkedQueue<Runnable> file = new ConcurrentLinkedQueue<>();
        /** Un fil vide la file en ce moment (ou va le faire). */
        private final AtomicBoolean active = new AtomicBoolean();
        /** Dernière écriture en attente de chaque fichier (remplacée si un relevé plus récent arrive avant). */
        private final ConcurrentHashMap<Path, Tache> enAttente = new ConcurrentHashMap<>();
        /** Fichiers en cours d'écriture. */
        private final Set<Path> enCours = ConcurrentHashMap.newKeySet();
        private final AtomicLong ecrits = new AtomicLong();

        private record Tache(Ecriture ecriture, @Nullable Runnable siEchec) {
        }

        private Voie(String nom) {
            this.nom = nom;
        }

        public String nom() {
            return nom;
        }

        /**
         * Met en file l'écriture du fichier {@code f} (remplace celle encore en attente pour ce fichier) ; {@code siEchec}
         * (appelé sur le fil d'écriture : seulement poser un drapeau ou rendre la main au serveur par {@code execute})
         * redonne la donnée « à sauver ». À l'arrêt du serveur, attend la fin de l'écriture.
         */
        public void ecrire(Path f, Ecriture ecriture, @Nullable Runnable siEchec) {
            if (enAttente.put(f, new Tache(ecriture, siEchec)) == null) {
                soumettre(() -> faire(f));
            } else {
                REMPLACES.incrementAndGet();
            }
            if (arret) attendre();
        }

        /** Comme {@link #ecrire(Path, Ecriture, Runnable)}, sans rien à faire si l'écriture rate. */
        public void ecrire(Path f, Ecriture ecriture) {
            ecrire(f, ecriture, null);
        }

        /** Met en file une écriture qui ne se remplace pas (ajout à un journal), dans l'ordre d'arrivée. */
        public void ajouter(Path f, Ecriture ecriture) {
            soumettre(() -> lancer(f, ecriture, null));
            if (arret) attendre();
        }

        /**
         * Fait {@code tache} sur le fil d'écriture, après les écritures déjà en file de cette voie (lire un fichier juste
         * écrit sans faire attendre le fil du serveur). Ne touche à aucun objet du jeu ; la suite revient au serveur par
         * {@code server.execute}.
         */
        public void puis(Runnable tache) {
            soumettre(() -> {
                try {
                    tache.run();
                } catch (Throwable ex) {
                    LOG.error("[{}] tâche après les écritures en erreur", nom, ex);
                }
            });
        }

        /** Une écriture de {@code f} attend ou se fait : le fichier sur le disque n'est peut-être pas encore le dernier. */
        public boolean occupe(Path f) {
            return enAttente.containsKey(f) || enCours.contains(f);
        }

        /** Attend la fin de toutes les écritures déjà en file de cette voie, {@value #ATTENTE_S} s au plus. */
        public void attendre() {
            if (surUnFil()) return;   // depuis une écriture : elle se bloquerait elle-même
            if (file.isEmpty() && !active.get()) return;
            CompletableFuture<Void> fait = new CompletableFuture<>();
            soumettre(() -> fait.complete(null));
            try {
                fait.get(ATTENTE_S, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                LOG.error("[{}] écritures en fond pas terminées", nom, ex);
            }
        }

        /** Attend seulement si le fichier {@code f} est en attente ou en cours d'écriture (lecture d'un fichier juste écrit). */
        public void attendre(Path f) {
            if (occupe(f)) attendre();
        }

        private void soumettre(Runnable r) {
            file.add(r);
            if (active.compareAndSet(false, true)) {
                try {
                    RESERVE.execute(this::vider);
                } catch (RejectedExecutionException ex) {
                    vider();   // réserve fermée (fin du programme) : tout de suite, sur ce fil
                }
            }
        }

        /** Sur un fil d'écriture : vide la file de la voie, puis rend le fil (une autre voie peut le prendre). */
        private void vider() {
            for (;;) {
                Runnable r;
                while ((r = file.poll()) != null) {
                    try {
                        r.run();
                    } catch (Throwable ex) {
                        LOG.error("[{}] écriture en fond en erreur", nom, ex);
                    }
                }
                active.set(false);
                // une tâche arrivée entre le dernier poll et la remise à faux : on reprend la main si personne ne l'a
                if (file.isEmpty() || !active.compareAndSet(false, true)) return;
            }
        }

        private void faire(Path f) {
            enCours.add(f);
            try {
                Tache t = enAttente.remove(f);
                if (t != null) lancer(f, t.ecriture(), t.siEchec());
            } finally {
                enCours.remove(f);
            }
        }

        private void lancer(Path f, Ecriture e, @Nullable Runnable siEchec) {
            long t = System.nanoTime();
            try {
                e.ecrire();
                ecrits.incrementAndGet();
                ECRITS.incrementAndGet();
            } catch (Throwable ex) {
                ECHECS.incrementAndGet();
                LOG.error("[{}] écriture de {} impossible", nom, f, ex);
                if (siEchec != null) {
                    try {
                        siEchec.run();
                    } catch (Throwable ignore) {
                        // rien de plus à faire : l'erreur est déjà dans le journal
                    }
                }
            } finally {
                NANOS.addAndGet(System.nanoTime() - t);
            }
        }
    }
}
