package com.Spartan.scan.collect;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Veille légère du jeu (08/11) : un client de triche injecté EN COURS de partie n'était vu qu'au contrôle au hasard
 * suivant (une à huit minutes plus tard). Toutes les 3 s, dans un fil à part de basse priorité (jamais sur le fil du
 * rendu ni par image), on regarde seulement ce qui est bon marché et qui change quand on injecte :
 * <ul>
 *    <li>les NOUVEAUX threads Java : sans nom, ou exécutant du code hors de tout module connu (comme
 *    {@link ThreadCollector#originKey}) ;</li>
 *    <li>sous Windows 64 bits : les nouveaux threads natifs démarrés hors de tout module, les nouvelles DLL chargées
 *    hors de Windows et du Java du jeu, et le début des fonctions d'affichage (SwapBuffers) qui change.</li>
 * </ul>
 * La veille ne JUGE rien : elle dit seulement au serveur « quelque chose a changé » (un mot, aucune donnée) ; le serveur
 * lance alors tout de suite un contrôle complet, qui juge et expulse comme d'habitude. Au plus un signal toutes les 20 s.
 * Ce qui existait déjà au premier passage n'est jamais signalé (le contrôle de connexion le juge).
 */
public final class Veille {
    private static final long PERIODE_MS = 3_000;
    private static final long SILENCE_MS = 20_000;
    private static final String[][] AFFICHAGE = {{"opengl32", "wglSwapBuffers"}, {"gdi32", "SwapBuffers"},
            {"gdi32full", "SwapBuffers"}};

    private static volatile Thread fil;
    private static volatile boolean actif;
    private static volatile Consumer<String> signal;

    // état du fil de veille seulement
    private static final Set<Long> javaVus = new HashSet<>();
    private static final Set<Long> threadsVus = new HashSet<>();
    private static final Set<Long> modulesVus = new HashSet<>();
    private static final long[] fonctions = new long[AFFICHAGE.length];
    private static final byte[][] debuts = new byte[AFFICHAGE.length][];
    private static Set<String> modulesJava;
    private static Thread[] tampon = new Thread[256];
    private static boolean premierPassage = true;
    private static long dernierSignal;
    /** Passages depuis le lancement : la liste des DLL est relue au moins un passage sur 10 (30 s). */
    private static int tours;

    private Veille() {
    }

    /** Lance la veille une fois (connexion à un serveur) ; {@code envoi} est appelé hors du fil du jeu. */
    public static synchronized void demarrer(Consumer<String> envoi) {
        signal = envoi;
        actif = true;
        if (fil != null) {
            return;
        }
        Thread t = new Thread(Veille::boucle, "Spartan-veille");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        fil = t;
        t.start();
    }

    /** Hors d'un serveur : la veille dort (rien n'est lu). */
    public static void arreter() {
        actif = false;
    }

    private static void boucle() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win") && Native.POINTER_SIZE == 8;
        while (true) {
            try {
                Thread.sleep(PERIODE_MS);
            } catch (InterruptedException e) {
                return;
            }
            if (!actif) {
                continue;
            }
            try {
                tours++;
                String quoi = verifier(windows);
                premierPassage = false;
                long now = System.currentTimeMillis();
                Consumer<String> s = signal;
                if (quoi != null && s != null && now - dernierSignal >= SILENCE_MS) {
                    dernierSignal = now;
                    s.accept(quoi);
                }
            } catch (Throwable t) {
                // jamais faire planter le jeu : le contrôle au hasard reste là
            }
        }
    }

    /** Ce qui vient d'apparaître (premier trouvé), ou null. Au premier passage, tout est seulement noté. */
    private static String verifier(boolean windows) {
        String quoi = threadsJava();
        if (windows) {
            String n = threadsNatifs();
            quoi = quoi != null ? quoi : n;
            n = affichage();
            quoi = quoi != null ? quoi : n;
        }
        return premierPassage ? null : quoi;
    }

    private static String threadsJava() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        int n = root.enumerate(tampon, true);
        if (n >= tampon.length) {
            tampon = new Thread[n * 2];
            n = root.enumerate(tampon, true);
        }
        String quoi = null;
        for (int i = 0; i < n; i++) {
            Thread t = tampon[i];
            tampon[i] = null;
            if (t == null || !javaVus.add(t.getId()) || premierPassage || quoi != null) {
                continue;
            }
            String nom = t.getName();
            if (nom == null || nom.isBlank()) {
                quoi = "java";
                continue;
            }
            if (modulesJava == null) {
                modulesJava = ForgeCollector.knownModuleNames(ForgeCollector.layers());
            }
            for (StackTraceElement e : t.getStackTrace()) {
                if (ThreadCollector.originKey(e, modulesJava) != null) {
                    quoi = "java";
                    break;
                }
            }
        }
        return quoi;
    }

    private static String threadsNatifs() {
        List<Long> starts = InjectionCollector.threadStarts();
        boolean nouveau = false;
        for (long a : starts) {
            nouveau |= threadsVus.add(a);
        }
        // la liste des modules n'est relue que si un thread inconnu est apparu (une DLL chargée de force en crée un),
        // sinon un passage sur 10 (30 s) pour les DLL
        if (!nouveau && !premierPassage && tours % 10 != 0) {
            return null;
        }
        List<InjectionCollector.Module> modules = InjectionCollector.modules();
        if (modules.isEmpty()) {
            return null;
        }
        String quoi = null;
        String java = System.getProperty("java.home", "").toLowerCase(Locale.ROOT).replace('/', '\\');
        for (InjectionCollector.Module m : modules) {
            if (modulesVus.add(m.base()) && !premierPassage && quoi == null && !InjectionCollector.isSystem(m)
                    && (java.isEmpty() || !m.path().toLowerCase(Locale.ROOT).replace('/', '\\').startsWith(java))) {
                quoi = "module";
            }
        }
        if (nouveau && !premierPassage) {
            for (long a : starts) {
                if (a != 0 && InjectionCollector.module(modules, a) == null) {
                    return "thread";
                }
            }
        }
        return quoi;
    }

    /** Début des fonctions d'affichage : un crochet posé en cours de partie le change. */
    private static String affichage() {
        String quoi = null;
        for (int i = 0; i < AFFICHAGE.length; i++) {
            if (fonctions[i] == 0) {
                if (!premierPassage && tours % 10 != 0) {
                    continue; // DLL pas encore chargée : on réessaie toutes les 30 s seulement
                }
                fonctions[i] = adresse(AFFICHAGE[i][0], AFFICHAGE[i][1]);
                if (fonctions[i] == 0) {
                    continue;
                }
            }
            byte[] b = InjectionCollector.lire(fonctions[i], 14);
            if (b == null) {
                continue;
            }
            if (debuts[i] == null) {
                debuts[i] = b; // première lecture : référence
            } else if (!Arrays.equals(debuts[i], b)) {
                debuts[i] = b;
                quoi = "affichage";
            }
        }
        return quoi;
    }

    /** Adresse de la fonction si sa DLL est DÉJÀ chargée (on ne charge jamais une DLL pour la lire), sinon 0. */
    private static long adresse(String dll, String fonction) {
        boolean chargee = false;
        for (InjectionCollector.Module m : InjectionCollector.modules()) {
            chargee |= m.name().equalsIgnoreCase(dll + ".dll");
        }
        if (!chargee) {
            return 0;
        }
        try {
            return Pointer.nativeValue(NativeLibrary.getInstance(dll).getFunction(fonction));
        } catch (Throwable t) {
            return 0;
        }
    }
}
