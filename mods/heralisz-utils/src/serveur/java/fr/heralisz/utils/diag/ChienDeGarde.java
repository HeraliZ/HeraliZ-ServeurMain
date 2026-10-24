package fr.heralisz.utils.diag;

import fr.heralisz.utils.HeraliszUtils;

import java.lang.management.ThreadInfo;
import java.util.Locale;

/**
 * Chien de garde des ticks : un fil à part (démon, priorité basse) qui regarde le fil du serveur.
 * <p>Le fil du serveur ne fait que noter le début et la fin de chaque tick ({@link #debutTick}, {@link #finTick} : une
 * lecture d'horloge et quelques écritures). Le chien de garde dort jusqu'au moment où le tick en cours dépasserait le
 * seuil ; s'il le dépasse (ou si le tick suivant tarde, travail entre deux ticks), il lit la pile du fil du serveur
 * toutes les {@code intervalle_ms} jusqu'à la fin, puis remet l'épisode au fil d'écriture ({@link Rapports}). Il
 * n'écrit rien sur le disque, ne prend aucun verrou du jeu et n'appelle aucune méthode du jeu (sauf la lecture de
 * champs de l'entité en cours) ; toute erreur est attrapée.
 */
final class ChienDeGarde implements Runnable {
    // ---- notés par le fil du serveur
    static volatile Thread serveur;
    static volatile long debutNs, finNs;
    static volatile boolean dansTick;
    static volatile int numero;
    private static long debutPrecedentNs;

    private static volatile ChienDeGarde courant;
    private volatile boolean arreter;
    private final Thread fil;
    /** Dernier épisode suivi (pour ne pas le reprendre). */
    private int faitNumero = -1;
    private boolean faitDansTick;
    private long dernierePileAttenteMs;

    private ChienDeGarde() {
        fil = new Thread(this, "HeraliZ-diag-chien-de-garde");
        fil.setDaemon(true);
        fil.setPriority(Thread.MIN_PRIORITY);
    }

    // ------------------------------------------------------------------ fil du serveur

    /** Début d'un tick (fil du serveur, première chose du tick). */
    static void debutTick() {
        long t = System.nanoTime();
        Thread ici = Thread.currentThread();
        if (serveur != ici) {
            serveur = ici;
            SuiviEntites.serveur = ici;
        }
        debutPrecedentNs = debutNs;
        debutNs = t;
        numero = numero + 1;
        dansTick = true;
        SuiviEntites.nouveauTick();
    }

    /** Fin d'un tick (fil du serveur, dernière chose du tick). */
    static void finTick() {
        long t = System.nanoTime();
        finNs = t;
        dansTick = false;
        long d = debutNs;
        Compteurs.ajouter(numero, t - d, debutPrecedentNs == 0 ? 50_000_000L : d - debutPrecedentNs, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ cycle de vie

    static synchronized void demarrer() {
        if (courant != null && courant.fil.isAlive()) return;
        courant = new ChienDeGarde();
        courant.fil.start();
    }

    static synchronized void arreter() {
        ChienDeGarde c = courant;
        courant = null;
        if (c != null) {
            c.arreter = true;
            c.fil.interrupt();
        }
    }

    // ------------------------------------------------------------------ fil du chien de garde

    @Override
    public void run() {
        long derniereConfig = 0;
        int erreurs = 0;
        while (!arreter) {
            try {
                long now = System.nanoTime();
                if (now - derniereConfig > 5_000_000_000L) {
                    DiagConfig.rafraichir();
                    derniereConfig = now;
                }
                if (!DiagConfig.actif || !Diag.enService || serveur == null || numero == 0) {
                    Thread.sleep(100);
                    continue;
                }
                int n = numero;
                boolean enTick = dansTick;
                long d = debutNs, f = finNs;
                // en tick : depuis le début du tick ; entre deux ticks : depuis l'heure où le tick suivant aurait dû
                // commencer (50 ms après le début du précédent, ou sa fin s'il a débordé)
                long ref = enTick ? d : Math.max(f, d + 50_000_000L);
                long seuil = DiagConfig.seuilMs * 1_000_000L;
                if (now - ref >= seuil && !(n == faitNumero && enTick == faitDansTick) && n == numero && enTick == dansTick) {
                    faitNumero = n;
                    faitDansTick = enTick;
                    suivre(n, enTick, ref);
                    continue;
                }
                long attente = (ref + seuil - now) / 1_000_000L;
                Thread.sleep(Math.max(2, Math.min(50, attente)));
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                if (erreurs++ < 5) HeraliszUtils.LOG.warn("[diag] erreur du chien de garde (il continue) : {}", t.toString());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private static boolean encore(int n, boolean enTick) {
        return numero == n && dansTick == enTick;
    }

    /** Échantillonne l'épisode en cours jusqu'à sa fin. */
    private void suivre(int n, boolean enTick, long ref) throws InterruptedException {
        long debutMs = System.currentTimeMillis() - (System.nanoTime() - ref) / 1_000_000L;
        Episode ep = new Episode(enTick, n, debutMs, Instantane.dernier, Diag.commandesRecentes());
        long intervalle = Math.max(1, ep.intervalleMs) * 1_000_000L;
        long blocage = ep.blocageMs * 1_000_000L;
        long prochainProvisoire = ref + 15_000_000_000L;
        long precedent = 0;
        Thread srv = serveur;
        while (!arreter && Diag.enService && encore(n, enTick)) {
            long t = System.nanoTime();
            ThreadInfo ti = Echantillon.lire(srv);
            if (ti == null || !encore(n, enTick)) break;  // fini pendant la lecture : la pile serait celle d'après
            StackTraceElement[] st = ti.getStackTrace();
            Thread.State etat = ti.getThreadState();
            String entite = Echantillon.entite(false);
            int ms = (int) ((t - ref) / 1_000_000L);
            if (ep.premierMs < 0) ep.premierMs = ms;
            if (precedent != 0 && t - precedent > 3 * intervalle) ep.trouMs += (t - precedent - intervalle) / 1_000_000L;
            precedent = t;
            int[] pile = ep.agregat.ajouter(st, etat, entite);
            if (ep.liste.size() < Episode.MAX_LISTE) {
                String verrou = null;
                if (ti.getLockName() != null && etat != Thread.State.RUNNABLE) {
                    verrou = "sur " + ti.getLockName() + (ti.getLockOwnerName() != null ? " tenu par \"" + ti.getLockOwnerName() + "\"" : "");
                }
                ep.liste.add(new Episode.Ech(ms, etat, verrou, pile, entite));
            }
            // blocage : une fois, les piles de tous les fils
            if (t - ref >= blocage && ep.tousLesFils == null) {
                ep.tousLesFils = Echantillon.tousLesFils();
                ep.tousLesFilsMs = ms;
                precedent = System.nanoTime();   // ce travail du chien de garde n'est pas un trou de la JVM
            }
            // le fil du serveur attend quelque chose (un tronçon, une tâche) : qui travaille pour lui ? (une fois par minute)
            long mur = System.currentTimeMillis();
            if (etat != Thread.State.RUNNABLE && ep.filsEnAttente == null && ep.tousLesFils == null
                    && t - ref >= 2L * ep.seuilMs * 1_000_000L && mur - dernierePileAttenteMs > 60_000) {
                dernierePileAttenteMs = mur;
                ep.filsEnAttente = Echantillon.tousLesFils();
                ep.filsEnAttenteMs = ms;
                precedent = System.nanoTime();
            }
            // blocage qui dure : rapport provisoire toutes les 15 s (avant que le serveur ne soit tué)
            if (t >= prochainProvisoire) {
                prochainProvisoire += 15_000_000_000L;
                ep.dureeMs = ms;
                try {
                    Rapports.texte("blocage-en-cours", ep.texte(true));
                } catch (Throwable ex) {
                    HeraliszUtils.LOG.warn("[diag] rapport provisoire impossible : {}", ex.toString());
                }
                HeraliszUtils.LOG.warn("[diag] serveur bloqué depuis {} s ({}) ; en tête : {}", ms / 1000, ep.phase(), ep.agregat.tete(3));
                precedent = System.nanoTime();
            }
            long reste = intervalle - (System.nanoTime() - t);
            if (reste > 0) Thread.sleep(reste / 1_000_000L, (int) (reste % 1_000_000L));
        }
        // durée réelle
        long fin = System.nanoTime();
        long duree;
        if (enTick) duree = (numero == n && !dansTick) ? finNs - ref : fin - ref;
        else duree = numero == n + 1 ? debutNs - ref : fin - ref;
        ep.dureeMs = Math.max(0, duree) / 1_000_000.0;
        ep.finMs = ep.debutMs + (long) ep.dureeMs;
        ep.fini = true;
        if (ep.blocage()) {
            HeraliszUtils.LOG.warn(String.format(Locale.ROOT, "[diag] BLOCAGE de %.0f ms %s ; en tête : %s", ep.dureeMs, ep.phase(), ep.agregat.tete(4)));
        }
        Rapports.episode(ep);
    }
}
