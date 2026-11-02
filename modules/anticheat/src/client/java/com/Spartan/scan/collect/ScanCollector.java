package com.Spartan.scan.collect;

import com.Spartan.integrity.Fingerprint;
import com.Spartan.integrity.LaunchToken;
import com.Spartan.scan.ScanReport;
import com.Spartan.scan.Sondes;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exécute un contrôle complet et produit le rapport. Tourne hors du thread du
 * jeu (quelques centaines de millisecondes, plus l'échantillonnage du rendu) :
 * le joueur ne sent rien. Chaque étape est isolée : une erreur dans l'une est
 * notée dans le rapport et n'empêche pas les autres.
 */
public final class ScanCollector {
    private ScanCollector() {
    }

    /**
     * @param modsInventory inventaire du dossier mods déjà calculé (écran titre), ou null
     * @param watchedThread thread échantillonné (rendu côté client), ou null
     */
    public record Context(int scanId, Path modsDir, Path gameDir, List<LaunchToken.ModFile> modsInventory,
                          Thread watchedThread, int samples, boolean referenceKnown, Set<Long> knownMixins,
                          List<String> watchClasses, Sondes sondes) {
    }

    public static ScanReport collect(Context c) {
        long start = System.nanoTime();
        ScanReport r = new ScanReport();
        r.scanId = c.scanId();
        r.java = ScanReport.cut(System.getProperty("java.version", "?") + " (" + System.getProperty("java.vendor", "?") + ")", 200);
        r.os = ScanReport.cut(System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "")
                + " " + System.getProperty("os.arch", ""), 200);

        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        step(r, "jvm", () -> JvmCollector.collect(r, args, System::getenv, ScanCollector::vmOption,
                System.getProperty("sun.java.command", "")));
        step(r, "agents", () -> JvmCollector.hashAgents(r, args));
        step(r, "mods", () -> {
            List<LaunchToken.ModFile> inv = c.modsInventory() != null ? c.modsInventory()
                    : LaunchToken.modsInventory(c.modsDir());
            for (LaunchToken.ModFile f : inv) {
                r.files.add(new ScanReport.FileEntry("mods", f.directory() ? f.name() + "/" : f.name(), f.size(), f.sha256()));
            }
        });

        Map<ModuleLayer, String> layers = ForgeCollector.layers();
        Set<String> known = ForgeCollector.knownModuleNames(layers);
        step(r, "modlist", () -> ForgeCollector.modsHorsDossier(r, c.modsDir()));
        step(r, "modules", () -> ForgeCollector.modules(r, layers, c.modsDir()));
        step(r, "modlist", () -> ForgeCollector.loadedFiles(r));
        step(r, "mixins", () -> ForgeCollector.mixins(r, c.knownMixins(), c.referenceKnown(), c.watchClasses()));
        step(r, "self", () -> {
            // Les classes Mixin de Spartan ne se chargent pas directement (Mixin l'interdit) et
            // celles de compatibilité dépendent de mods facultatifs : seules les autres sont jugées.
            List<String> own = Fingerprint.classNames().stream()
                    .filter(n -> !n.startsWith("com.Spartan.mixin.") && !n.startsWith("com.Spartan.esp.compat."))
                    .toList();
            SelfCheck.collect(r, own, ScanCollector.class.getClassLoader());
            ForgeCollector.mixinsOnSpartan(r, own);
        });

        ThreadCollector.Origins origins = new ThreadCollector.Origins(known);
        step(r, "threads", () -> ThreadCollector.collect(r, Thread.getAllStackTraces(), origins));
        step(r, "natives", () -> NativeCollector.collect(r, c.sondes(), c.gameDir()));
        step(r, "injection", () -> InjectionCollector.collect(r, c.gameDir()));
        step(r, "classes", () -> probe(r, c.sondes(), c.watchedThread(), layers));
        step(r, "render", () -> ThreadCollector.sample(c.watchedThread(), c.samples(), origins));
        origins.into(r);

        r.durationMs = (System.nanoTime() - start) / 1_000_000L;
        return r;
    }

    /**
     * Clients de triche connus (audit du 02/11, point 13) : le serveur n'envoie que l'empreinte salée du PAQUET de leurs
     * classes ({@link Sondes}) ; on hache les paquets que le jeu connaît (modules de chaque couche, paquets définis par
     * les chargeurs de classes du jeu, de Spartan, du système et de chaque thread : un client injecté a souvent son propre
     * chargeur) et on renvoie seulement les empreintes trouvées. Le serveur les traduit en noms.
     */
    static void probe(ScanReport r, Sondes sondes, Thread game, Map<ModuleLayer, String> layers) {
        if (sondes == null || sondes.paquets.length == 0) {
            return;
        }
        Set<String> vus = new HashSet<>();
        List<String> trouves = new ArrayList<>();
        java.util.function.Consumer<String> voir = p -> {
            if (p != null && vus.add(p)) {
                String m = sondes.paquetCherche(p);
                if (m != null && !trouves.contains(m) && trouves.size() < ScanReport.MAX_HITS) {
                    trouves.add(m);
                }
            }
        };
        for (ModuleLayer layer : layers.keySet()) {
            for (Module m : layer.modules()) {
                m.getPackages().forEach(voir);
            }
        }
        Set<ClassLoader> chargeurs = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<ClassLoader> todo = new ArrayList<>();
        todo.add(game != null ? game.getContextClassLoader() : null);
        todo.add(ScanCollector.class.getClassLoader());
        todo.add(ClassLoader.getSystemClassLoader());
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            todo.add(t.getContextClassLoader());
        }
        for (int i = 0; i < todo.size() && chargeurs.size() < 256; i++) {
            ClassLoader l = todo.get(i);
            if (l == null || !chargeurs.add(l)) {
                continue;
            }
            for (Package p : l.getDefinedPackages()) {
                voir.accept(p.getName());
            }
            todo.add(l.getParent());
        }
        r.classHits.addAll(trouves);
    }

    interface Step {
        void run() throws Exception;
    }

    private static void step(ScanReport r, String name, Step s) {
        try {
            s.run();
        } catch (Throwable t) {
            if (r.errors.size() < ScanReport.MAX_ERRORS) {
                r.errors.add(ScanReport.cut(name + " : " + t, 250));
            }
        }
    }

    /** Valeur d'une option HotSpot, ou null (autre JVM, option absente). */
    static String vmOption(String name) {
        try {
            return ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class)
                    .getVMOption(name).getValue();
        } catch (Throwable t) {
            return null;
        }
    }
}
