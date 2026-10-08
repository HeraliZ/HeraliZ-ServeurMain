package com.Spartan.scan.collect;

import com.Spartan.integrity.LaunchToken;
import com.Spartan.scan.ScanReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Options de lancement de la JVM qui permettent d'injecter du code dans le jeu :
 * agents Java ou natifs, chemin de démarrage modifié, auto-injection autorisée,
 * mods chargés par une option FML plutôt que depuis le dossier mods…
 *
 * Seuls les NOMS de fichiers sont gardés (jamais le chemin complet, qui contient
 * le nom d'utilisateur Windows), et jamais les arguments du programme : ils
 * contiennent le jeton de session du joueur.
 */
public final class JvmCollector {
    /** Options FML normales d'un lancement Forge (les autres « --fml.* » chargent du code en plus). */
    private static final List<String> NORMAL_FML_ARGS = List.of(
            "--fml.forgeversion", "--fml.mcversion", "--fml.forgegroup", "--fml.mcpversion", "--fml.neoforgeversion");

    private JvmCollector() {
    }

    /**
     * @param inputArgs arguments de la JVM (RuntimeMXBean, JAVA_TOOL_OPTIONS compris)
     * @param env       variables d'environnement (System::getenv)
     * @param vmOption  valeur d'une option HotSpot, ou null si inconnue
     * @param command   propriété sun.java.command (classe principale + arguments du programme)
     */
    public static void collect(ScanReport r, List<String> inputArgs, Function<String, String> env,
                               Function<String, String> vmOption, String command) {
        for (String arg : inputArgs) {
            String flag = flag(arg);
            if (flag != null) {
                add(r, flag);
            }
        }
        for (String var : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            String v = env.apply(var);
            if (v != null && !v.isBlank()) {
                add(r, "env:" + var);
            }
        }
        if (command != null) {
            for (String token : command.split("\\s+")) {
                String t = token.toLowerCase(Locale.ROOT);
                if (t.startsWith("--fml.") && !NORMAL_FML_ARGS.contains(t)) {
                    add(r, "arg:" + t);
                }
            }
        }
        r.attachDisabled = "true".equalsIgnoreCase(vmOption.apply("DisableAttachMechanism"));
        if ("true".equalsIgnoreCase(vmOption.apply("StartAttachListener"))) {
            add(r, "-XX:+StartAttachListener");
        }
    }

    /** Version normalisée d'un argument JVM sensible, ou null s'il est anodin. */
    static String flag(String arg) {
        if (arg == null) {
            return null;
        }
        String a = arg.trim();
        String lower = a.toLowerCase(Locale.ROOT);
        if (lower.startsWith("-javaagent:")) {
            return "-javaagent:" + fileName(beforeEquals(a.substring("-javaagent:".length())));
        }
        if (lower.startsWith("-agentpath:")) {
            return "-agentpath:" + fileName(beforeEquals(a.substring("-agentpath:".length())));
        }
        if (lower.startsWith("-agentlib:")) {
            return "-agentlib:" + beforeEquals(a.substring("-agentlib:".length())).toLowerCase(Locale.ROOT);
        }
        if (lower.startsWith("-xbootclasspath")) {
            return "-Xbootclasspath";
        }
        if (lower.startsWith("-djava.system.class.loader=")) {
            return "-Djava.system.class.loader=" + ScanReport.cut(a.substring(a.indexOf('=') + 1), 120);
        }
        if (lower.startsWith("-djdk.attach.allowattachself") && !lower.endsWith("=false")) {
            return "-Djdk.attach.allowAttachSelf";
        }
        if (lower.equals("-xx:+enabledynamicagentloading")) {
            return "-XX:+EnableDynamicAgentLoading";
        }
        if (lower.equals("-xverify:none") || lower.equals("-noverify")) {
            return "-Xverify:none";
        }
        if (lower.equals("-xx:+startattachlistener")) {
            return "-XX:+StartAttachListener";
        }
        return null;
    }

    /** Empreinte des jars d'agents Java (pour reconnaître un authlib-injector officiel d'un faux). */
    public static void hashAgents(ScanReport r, List<String> inputArgs) {
        for (String arg : inputArgs) {
            if (arg == null || !arg.toLowerCase(Locale.ROOT).startsWith("-javaagent:")) {
                continue;
            }
            String spec = beforeEquals(arg.substring("-javaagent:".length()));
            try {
                Path p = Paths.get(spec);
                if (Files.isRegularFile(p)) {
                    r.files.add(new ScanReport.FileEntry("agent", fileName(spec), Files.size(p), LaunchToken.sha256File(p)));
                }
            } catch (Exception e) {
                // chemin illisible : le drapeau -javaagent suffit comme indice
            }
        }
    }

    private static void add(ScanReport r, String flag) {
        if (!r.jvmFlags.contains(flag)) {
            r.jvmFlags.add(flag);
        }
    }

    private static String beforeEquals(String spec) {
        int eq = spec.indexOf('=');
        return eq >= 0 ? spec.substring(0, eq) : spec;
    }

    /** Dernier élément d'un chemin, quel que soit le séparateur. */
    static String fileName(String path) {
        String p = path.replace('\\', '/');
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int slash = p.lastIndexOf('/');
        return ScanReport.cut(slash >= 0 ? p.substring(slash + 1) : p, 120);
    }
}
