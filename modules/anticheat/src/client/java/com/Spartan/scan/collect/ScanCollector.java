package com.Spartan.scan.collect;

import com.Spartan.integrity.Fingerprint;
import com.Spartan.integrity.LaunchToken;
import com.Spartan.scan.ScanReport;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
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
                          List<String> watchClasses, List<String> classProbes, List<String> processPatterns,
                          List<String> nativePatterns) {
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
        step(r, "natives", () -> NativeCollector.collect(r, c.nativePatterns(), c.processPatterns(), c.gameDir()));
        step(r, "injection", () -> InjectionCollector.collect(r));
        step(r, "classes", () -> probe(r, c.classProbes(), c.watchedThread()));
        step(r, "render", () -> ThreadCollector.sample(c.watchedThread(), c.samples(), origins));
        origins.into(r);

        r.durationMs = (System.nanoTime() - start) / 1_000_000L;
        return r;
    }

    /** Classes de triche connues (liste envoyée par le serveur, jamais écrite dans le jar). */
    static void probe(ScanReport r, List<String> names, Thread game) {
        ClassLoader[] loaders = {
                game != null ? game.getContextClassLoader() : null,
                ScanCollector.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()};
        for (String name : names) {
            for (ClassLoader l : loaders) {
                if (l == null) {
                    continue;
                }
                try {
                    Class.forName(name, false, l);
                    r.classHits.add(ScanReport.cut(name, 200));
                    break;
                } catch (Throwable notFound) {
                    // absente de ce chargeur
                }
            }
        }
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
