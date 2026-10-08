package com.Spartan.scan.collect;

import com.Spartan.scan.ScanReport;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Code natif : DLL (Windows) ou bibliothèques .so (Linux) chargées DANS le
 * processus du jeu, et programmes de triche connus ouverts sur le PC.
 *
 * Un client injecté passe en général par une DLL injectée dans javaw.exe, qui
 * définit ses classes par JNI. On liste les DLL du jeu hors des dossiers
 * système, avec un emplacement anonymisé (%USERPROFILE%… au lieu du nom
 * d'utilisateur). Pour les programmes, seuls ceux qui correspondent à la
 * liste envoyée par le serveur sont signalés : la liste complète ne quitte
 * jamais le PC du joueur.
 */
public final class NativeCollector {
    /** Bibliothèques normales du jeu, parfois extraites dans le dossier temporaire. */
    private static final List<String> GAME_NATIVES = List.of(
            "lwjgl", "glfw", "openal", "jemalloc", "jnidispatch", "jna", "liblwjgl", "libglfw", "libopenal",
            "libjemalloc", "libjnidispatch", "jinput", "sqlite", "netty", "libnetty");

    private NativeCollector() {
    }

    /** Lieu d'une bibliothèque : catégorie + dossier anonymisé. */
    public record Place(String category, String shown) {
    }

    public static void collect(ScanReport r, List<String> nativePatterns, List<String> processPatterns, Path gameDir) {
        Function<String, String> env = System::getenv;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String javaHome = System.getProperty("java.home", "");
        List<String> libs = os.contains("win") ? windowsModules() : os.contains("linux") ? linuxModules() : List.of();
        for (String lib : libs) {
            if (r.natives.size() >= ScanReport.MAX_NATIVES) {
                break;
            }
            Place place = classify(lib, env, javaHome, gameDir == null ? "" : gameDir.toAbsolutePath().toString(),
                    System.getProperty("user.home", ""), os.contains("win"));
            if ("system".equals(place.category()) || "java".equals(place.category())) {
                continue; // Windows / Linux et la JVM elle-même
            }
            String name = JvmCollector.fileName(lib);
            r.natives.add(new ScanReport.NativeEntry(name, place.shown(), reason(name, place, nativePatterns)));
        }
        if (!processPatterns.isEmpty()) {
            for (String p : os.contains("win") ? windowsProcesses() : javaProcesses()) {
                String lower = p.toLowerCase(Locale.ROOT);
                for (String pattern : processPatterns) {
                    if (!pattern.isBlank() && lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                        if (!r.processHits.contains(p) && r.processHits.size() < ScanReport.MAX_HITS) {
                            r.processHits.add(ScanReport.cut(p, 100));
                        }
                        break;
                    }
                }
            }
        }
    }

    /** Pourquoi une bibliothèque est suspecte (vide = simple information). */
    static String reason(String name, Place place, List<String> patterns) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (!p.isBlank() && lower.contains(p.toLowerCase(Locale.ROOT))) {
                return "nom:" + p;
            }
        }
        boolean gameNative = GAME_NATIVES.stream().anyMatch(lower::startsWith);
        if (!gameNative && (place.category().equals("temp") || place.category().equals("downloads")
                || place.category().equals("desktop"))) {
            return "dossier:" + place.category();
        }
        return "";
    }

    /**
     * Catégorie d'un chemin : system, java, game, temp, downloads, desktop, user, programs, other.
     * Le chemin affiché remplace le profil de l'utilisateur par %USERPROFILE%.
     */
    public static Place classify(String path, Function<String, String> env, String javaHome, String gameDir,
                                 String userHome, boolean windows) {
        String p = norm(path, windows);
        String dir = parent(p);
        Map<String, String> roots = new LinkedHashMap<>();
        if (windows) {
            put(roots, "temp", env.apply("TEMP"), windows);
            put(roots, "temp", env.apply("TMP"), windows);
            String local = env.apply("LOCALAPPDATA");
            if (local != null) {
                put(roots, "temp", local + "\\Temp", windows);
            }
            put(roots, "system", env.apply("SystemRoot"), windows);
            put(roots, "system", env.apply("windir"), windows);
            put(roots, "java", javaHome, windows);
            put(roots, "game", gameDir, windows);
            put(roots, "programs", env.apply("ProgramFiles"), windows);
            put(roots, "programs", env.apply("ProgramFiles(x86)"), windows);
            put(roots, "programs", env.apply("ProgramW6432"), windows);
            String profile = env.apply("USERPROFILE") != null ? env.apply("USERPROFILE") : userHome;
            if (profile != null && !profile.isBlank()) {
                put(roots, "downloads", profile + "\\Downloads", windows);
                put(roots, "desktop", profile + "\\Desktop", windows);
                put(roots, "user", profile, windows);
            }
        } else {
            put(roots, "java", javaHome, windows);
            put(roots, "game", gameDir, windows);
            put(roots, "temp", "/tmp", windows);
            put(roots, "temp", "/var/tmp", windows);
            for (String sys : List.of("/usr", "/lib", "/lib32", "/lib64", "/etc", "/opt", "/snap", "/nix", "/run")) {
                put(roots, "system", sys, windows);
            }
            if (userHome != null && !userHome.isBlank()) {
                put(roots, "downloads", userHome + "/Downloads", windows);
                put(roots, "desktop", userHome + "/Desktop", windows);
                put(roots, "user", userHome, windows);
            }
        }
        // Le plus long préfixe gagne (Temp est dans le profil, le jeu aussi parfois).
        String bestRoot = null;
        String bestCat = "other";
        for (Map.Entry<String, String> en : roots.entrySet()) {
            String root = en.getKey();
            if ((dir.equals(root) || dir.startsWith(root + "/")) && (bestRoot == null || root.length() > bestRoot.length())) {
                bestRoot = root;
                bestCat = en.getValue();
            }
        }
        String shown;
        if (bestRoot == null) {
            shown = anonymize(dir, userHome, env.apply("USERPROFILE"), windows);
        } else {
            String label = switch (bestCat) {
                case "temp" -> "%TEMP%";
                case "system" -> windows ? "%SYSTEMROOT%" : "";
                case "java" -> "%JAVA%";
                case "game" -> "%JEU%";
                case "programs" -> "%PROGRAMFILES%";
                case "downloads" -> "%USERPROFILE%/Downloads";
                case "desktop" -> "%USERPROFILE%/Desktop";
                case "user" -> "%USERPROFILE%";
                default -> "";
            };
            shown = bestCat.equals("system") && !windows ? dir : label + dir.substring(bestRoot.length());
        }
        return new Place(bestCat, ScanReport.cut(shown, 200));
    }

    private static void put(Map<String, String> roots, String category, String path, boolean windows) {
        if (path != null && !path.isBlank()) {
            roots.putIfAbsent(norm(path, windows), category);
        }
    }

    private static String norm(String path, boolean windows) {
        String p = path.replace('\\', '/');
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return windows ? p.toLowerCase(Locale.ROOT) : p;
    }

    private static String parent(String p) {
        int slash = p.lastIndexOf('/');
        return slash > 0 ? p.substring(0, slash) : p;
    }

    private static String anonymize(String dir, String userHome, String profile, boolean windows) {
        String out = dir;
        for (String home : new String[] {profile, userHome}) {
            if (home != null && !home.isBlank()) {
                String h = norm(home, windows);
                if (out.startsWith(h)) {
                    return "%USERPROFILE%" + out.substring(h.length());
                }
            }
        }
        return out;
    }

    private static List<String> windowsModules() {
        List<String> out = new ArrayList<>();
        try {
            for (Tlhelp32.MODULEENTRY32W m : Kernel32Util.getModules(Kernel32.INSTANCE.GetCurrentProcessId())) {
                out.add(m.szExePath());
            }
        } catch (Throwable t) {
            // JNA indisponible : pas de liste
        }
        return out;
    }

    private static List<String> linuxModules() {
        Set<String> out = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(Paths.get("/proc/self/maps"))) {
                int slash = line.indexOf('/');
                if (slash < 0) {
                    continue;
                }
                String path = line.substring(slash).trim();
                if (path.endsWith(" (deleted)")) {
                    path = path.substring(0, path.length() - " (deleted)".length());
                }
                if (path.endsWith(".so") || path.contains(".so.")) {
                    out.add(path);
                }
            }
        } catch (Exception e) {
            // /proc indisponible
        }
        return new ArrayList<>(out);
    }

    private static List<String> windowsProcesses() {
        List<String> out = new ArrayList<>();
        WinNT.HANDLE snap = null;
        try {
            snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
            Tlhelp32.PROCESSENTRY32.ByReference e = new Tlhelp32.PROCESSENTRY32.ByReference();
            for (boolean ok = Kernel32.INSTANCE.Process32First(snap, e); ok; ok = Kernel32.INSTANCE.Process32Next(snap, e)) {
                out.add(Native.toString(e.szExeFile));
            }
        } catch (Throwable t) {
            return javaProcesses();
        } finally {
            if (snap != null) {
                try {
                    Kernel32.INSTANCE.CloseHandle(snap);
                } catch (Throwable ignored) {
                    // rien à faire
                }
            }
        }
        return out;
    }

    private static List<String> javaProcesses() {
        List<String> out = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(ph -> ph.info().command()
                .ifPresent(c -> out.add(JvmCollector.fileName(c))));
        return out;
    }
}
