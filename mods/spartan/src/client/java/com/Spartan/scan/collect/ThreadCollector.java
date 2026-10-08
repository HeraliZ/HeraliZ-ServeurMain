package com.Spartan.scan.collect;

import com.Spartan.scan.ScanReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Analyse des threads du jeu : noms, chargeurs de classes, et surtout ORIGINE
 * du code en cours d'exécution.
 *
 * Dans un Forge normal, tout le code tourne dans des modules nommés (JDK,
 * Minecraft, Forge, chaque mod). Un client injecté (DLL + JNI, agent Java,
 * chargeur maison) exécute des classes hors de tout module connu : on les
 * repère dans les piles d'exécution. Le thread de rendu est échantillonné
 * plusieurs fois, car un ESP s'exécute à chaque image dans ce thread.
 */
public final class ThreadCollector {
    /** Thread créé par la JVM quand un programme extérieur s'attache au jeu. */
    public static final String ATTACH_LISTENER = "Attach Listener";

    private ThreadCollector() {
    }

    private record Samples(List<String> classes, List<String> threads) {
    }

    /** Accumulateur des origines suspectes vues dans les piles. */
    public static final class Origins {
        private final Map<String, Samples> byKey = new LinkedHashMap<>();
        private final Set<String> knownModules;

        public Origins(Set<String> knownModules) {
            this.knownModules = knownModules;
        }

        void add(String threadName, StackTraceElement[] stack) {
            for (StackTraceElement e : stack) {
                String key = originKey(e, knownModules);
                if (key == null || (byKey.size() >= ScanReport.MAX_ORIGINS && !byKey.containsKey(key))) {
                    continue;
                }
                Samples s = byKey.computeIfAbsent(key, k -> new Samples(new ArrayList<>(), new ArrayList<>()));
                addBounded(s.classes(), e.getClassName());
                addBounded(s.threads(), threadName);
            }
        }

        public void into(ScanReport r) {
            byKey.forEach((key, s) -> r.origins.add(new ScanReport.OriginEntry(key, s.classes(), s.threads())));
        }
    }

    /** Instantané de tous les threads. */
    public static void collect(ScanReport r, Map<Thread, StackTraceElement[]> stacks, Origins origins) {
        Set<String> names = new TreeSet<>();
        Set<String> loaders = new TreeSet<>(r.loaders);
        for (Map.Entry<Thread, StackTraceElement[]> en : stacks.entrySet()) {
            Thread t = en.getKey();
            String name = t.getName();
            if (name == null || name.isBlank()) {
                r.unnamedThreads++;
                name = "(sans nom)";
            }
            if (ATTACH_LISTENER.equals(name)) {
                r.attachListener = true;
            }
            names.add(ScanReport.cut(name, 120));
            ClassLoader cl = t.getContextClassLoader();
            for (int depth = 0; cl != null && depth < 8; depth++, cl = cl.getParent()) {
                loaders.add(cl.getClass().getName());
            }
            origins.add(name, en.getValue());
        }
        r.threadCount = stacks.size();
        r.threadNames.clear();
        r.threadNames.addAll(names);
        r.loaders.clear();
        r.loaders.addAll(loaders);
    }

    /** Échantillonne la pile d'un thread (le rendu) pendant ~samples × 50 ms. */
    public static void sample(Thread thread, int samples, Origins origins) throws InterruptedException {
        if (thread == null || samples <= 0) {
            return;
        }
        for (int i = 0; i < samples && thread.isAlive(); i++) {
            origins.add(thread.getName(), thread.getStackTrace());
            Thread.sleep(25 + ThreadLocalRandom.current().nextInt(50));
        }
    }

    /**
     * Origine d'une ligne de pile, ou null si elle est normale :
     * - module nommé connu (JDK, Minecraft, Forge, mods) → normal ;
     * - module nommé inconnu → « module:nom » ;
     * - aucun module → « unnamed@chargeur:paquet » (code injecté, agent…).
     */
    static String originKey(StackTraceElement e, Set<String> knownModules) {
        String module = e.getModuleName();
        String cls = e.getClassName();
        if (module != null) {
            if (knownModules.contains(module) || module.startsWith("jdk.proxy")) {
                return null;
            }
            return "module:" + ScanReport.cut(module, 100);
        }
        if (cls.startsWith("jdk.internal.reflect.Generated") || cls.startsWith("jdk.internal.reflect.DelegatingClassLoader")) {
            return null; // accesseurs de réflexion générés par la JVM (Java 17)
        }
        String loader = e.getClassLoaderName();
        return "unnamed@" + (loader == null ? "?" : ScanReport.cut(loader, 60)) + ":" + packageRoot(cls);
    }

    /** Deux premiers segments du paquet (« net.wurstclient »), ou la classe si pas de paquet. */
    static String packageRoot(String cls) {
        int first = cls.indexOf('.');
        if (first < 0) {
            return ScanReport.cut(cls, 80);
        }
        int second = cls.indexOf('.', first + 1);
        int lastDot = cls.lastIndexOf('.');
        int end = second > 0 && second < lastDot ? second : lastDot;
        return ScanReport.cut(cls.substring(0, end), 80);
    }

    private static void addBounded(List<String> list, String value) {
        String v = ScanReport.cut(value, 200);
        if (list.size() < 5 && !list.contains(v)) {
            list.add(v);
        }
    }
}
