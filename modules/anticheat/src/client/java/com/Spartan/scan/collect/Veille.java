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
 * 19/11 : aussi, sur tous les systèmes, les classes de l'anticheat et les classes sensibles du jeu REDÉFINIES en mémoire
 * depuis le dernier passage ({@link Redefinitions}, lecture d'un entier par classe), et les bibliothèques d'agent Java ou
 * de débogueur (instrument, jdwp…) qui apparaissent, même rangées dans le Java du jeu (Windows ; Linux : /proc/self/maps
 * relu toutes les 30 s).
 * La veille ne JUGE rien : elle dit seulement au serveur « quelque chose a changé » (un mot, aucune donnée) ; le serveur
 * lance alors tout de suite un contrôle complet, qui juge et expulse comme d'habitude. Au plus un signal toutes les 20 s.
 * Ce qui existait déjà au premier passage n'est jamais signalé (le contrôle de connexion le juge).
 */
public final class Veille {
    private static final long PERIODE_MS = 3_000;
    private static final long SILENCE_MS = 20_000;
    private static final String[][] AFFICHAGE = {{"opengl32", "wglSwapBuffers"}, {"gdi32", "SwapBuffers"},
            {"gdi32full", "SwapBuffers"}};
    /**
     * Bibliothèques de Java qu'un jeu lancé par le launcher ne charge jamais (agent Java, débogueur, attache, contrôle à
     * distance, lecture de la mémoire de Java) : leur apparition est signalée même depuis le dossier du Java du jeu. Ce
     * sont des noms de Java, connus de tous : aucun secret ici, le serveur juge.
     */
    private static final java.util.regex.Pattern JAVA_SENSIBLE = java.util.regex.Pattern.compile(
            "^(lib)?(instrument|jdwp|dt_socket|dt_shmem|attach|management_agent|sawindbg|saproc)\\.(dll|so|dylib)$");

    /** Classes surveillées (fournies par le contrôle d'intégrité) et leur dernier nombre de redéfinitions connu. */
    private static Class<?>[] classes = new Class<?>[0];
    private static int[] comptes = new int[0];
    /** Linux : bibliothèques sensibles déjà vues dans /proc/self/maps. */
    private static final Set<String> sensiblesVues = new HashSet<>();

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

    /**
     * Classes à surveiller pour une redéfinition en mémoire (19/11), déjà chargées : appelé par chaque contrôle d'intégrité.
     * Leur nombre de redéfinitions actuel sert de référence (une redéfinition déjà là est jugée par ce contrôle).
     */
    public static void surveiller(List<Class<?>> liste) {
        Class<?>[] c = liste.toArray(new Class<?>[0]);
        int[] n = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            n[i] = Math.max(0, Redefinitions.compte(c[i]));
        }
        synchronized (Veille.class) {
            classes = c;
            comptes = n;
        }
    }

    /** Bibliothèque d'agent, de débogueur ou d'outil de Java (nom de fichier, sans le dossier) ? */
    static boolean javaSensible(String nomFichier) {
        return nomFichier != null && JAVA_SENSIBLE.matcher(nomFichier.toLowerCase(Locale.ROOT)).matches();
    }

    /** Une classe surveillée a-t-elle été redéfinie depuis le dernier passage ? (« memoire », ou null) */
    private static String memoire() {
        Class<?>[] c;
        int[] n;
        synchronized (Veille.class) {
            c = classes;
            n = comptes;
        }
        String quoi = null;
        for (int i = 0; i < c.length; i++) {
            int v = Redefinitions.compte(c[i]);
            if (v > n[i]) {
                n[i] = v;
                quoi = "memoire";
            }
        }
        return quoi;
    }

    /** Linux, toutes les 30 s : bibliothèque sensible de Java apparue dans /proc/self/maps (« module », ou null). */
    private static String natifsLinux() {
        if (!premierPassage && tours % 10 != 0) {
            return null;
        }
        String quoi = null;
        try {
            for (String ligne : java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/self/maps"))) {
                int slash = ligne.lastIndexOf('/');
                if (slash < 0) {
                    continue;
                }
                String nom = ligne.substring(slash + 1).trim();
                if (nom.endsWith(" (deleted)")) {
                    nom = nom.substring(0, nom.length() - " (deleted)".length());
                }
                if (javaSensible(nom) && sensiblesVues.add(nom) && !premierPassage) {
                    quoi = "module";
                }
            }
        } catch (Exception e) {
            return null; // /proc illisible : le contrôle au hasard reste là
        }
        return quoi;
    }

    private static void boucle() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win") && Native.POINTER_SIZE == 8;
        boolean linux = os.contains("linux");
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
                String quoi = verifier(windows, linux);
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
    private static String verifier(boolean windows, boolean linux) {
        String quoi = threadsJava();
        String m = memoire();
        quoi = quoi != null ? quoi : m;
        if (windows) {
            String n = threadsNatifs();
            quoi = quoi != null ? quoi : n;
            n = affichage();
            quoi = quoi != null ? quoi : n;
        } else if (linux) {
            String n = natifsLinux();
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
            // une bibliothèque d'agent ou de débogueur de Java (19/11) compte même rangée dans le Java du jeu
            if (modulesVus.add(m.base()) && !premierPassage && quoi == null && (javaSensible(m.name())
                    || (!InjectionCollector.isSystem(m)
                    && (java.isEmpty() || !m.path().toLowerCase(Locale.ROOT).replace('/', '\\').startsWith(java))))) {
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
