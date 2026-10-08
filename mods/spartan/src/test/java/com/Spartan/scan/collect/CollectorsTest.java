package com.Spartan.scan.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.scan.ScanReport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Collecteurs du contrôle d'intégrité, sur des entrées construites (sans Minecraft). */
class CollectorsTest {
    private static final Function<String, String> NO_ENV = k -> null;

    // ------------------------------------------------------------------ JVM

    @Test
    void normalForgeLaunchIsNotFlagged() {
        ScanReport r = new ScanReport();
        JvmCollector.collect(r, List.of("-Xmx4G", "-XX:+UseG1GC", "-Djava.library.path=C:\\natives",
                        "-DlibraryDirectory=C:\\libs", "-XX:+DisableAttachMechanism"), NO_ENV, n -> "DisableAttachMechanism".equals(n) ? "true" : null,
                "cpw.mods.bootstraplauncher.BootstrapLauncher --username Steve --accessToken SECRET123 --launchTarget forgeclient"
                        + " --fml.forgeVersion 47.4.26 --fml.mcVersion 1.20.1 --fml.forgeGroup net.minecraftforge --fml.mcpVersion 20230612.114412");
        assertTrue(r.jvmFlags.isEmpty(), r.jvmFlags.toString());
        assertTrue(r.attachDisabled);
    }

    @Test
    void injectionOptionsAreFlaggedWithoutLeakingPathsOrToken() {
        ScanReport r = new ScanReport();
        JvmCollector.collect(r, List.of("-javaagent:C:\\Users\\Jean Dupont\\Desktop\\cheat.jar=opts",
                        "-agentpath:C:\\Users\\Jean\\inject.dll", "-agentlib:jdwp=transport=dt_socket",
                        "-Xbootclasspath/a:C:\\x.jar", "-Djdk.attach.allowAttachSelf=true", "-noverify"),
                k -> k.equals("JAVA_TOOL_OPTIONS") ? "-javaagent:x.jar" : null, n -> null,
                "cpw.mods.bootstraplauncher.BootstrapLauncher --accessToken SECRET123 --fml.mods esp:esp:1.0 --fml.mavenRoots libs");
        assertEquals(List.of("-javaagent:cheat.jar", "-agentpath:inject.dll", "-agentlib:jdwp", "-Xbootclasspath",
                "-Djdk.attach.allowAttachSelf", "-Xverify:none", "env:JAVA_TOOL_OPTIONS", "arg:--fml.mods",
                "arg:--fml.mavenroots"), r.jvmFlags);
        String all = String.join(" ", r.jvmFlags);
        assertFalse(all.contains("Jean") || all.contains("Users") || all.contains("SECRET"), all);
        assertFalse(r.attachDisabled);
    }

    @Test
    void attachSelfDisabledExplicitlyIsFine() {
        assertNull(JvmCollector.flag("-Djdk.attach.allowAttachSelf=false"));
        assertEquals("cheat.jar", JvmCollector.fileName("/home/jean/cheat.jar"));
    }

    // ------------------------------------------------------------------ threads

    private static StackTraceElement frame(String loader, String module, String cls) {
        return new StackTraceElement(loader, module, null, cls, "run", "X.java", 1);
    }

    @Test
    void codeOriginKeys() {
        Set<String> known = Set.of("java.base", "minecraft", "jei");
        assertNull(ThreadCollector.originKey(frame("app", "java.base", "java.lang.Thread"), known));
        assertNull(ThreadCollector.originKey(frame("TRANSFORMER", "minecraft", "net.minecraft.client.Minecraft"), known));
        assertNull(ThreadCollector.originKey(frame(null, null, "jdk.internal.reflect.GeneratedMethodAccessor12"), known));
        assertNull(ThreadCollector.originKey(frame(null, "jdk.proxy3", "jdk.proxy3.$Proxy41"), known));
        assertEquals("module:cheatlayer", ThreadCollector.originKey(frame("x", "cheatlayer", "a.b.C"), known));
        assertEquals("unnamed@TRANSFORMER:dev.cheat",
                ThreadCollector.originKey(frame("TRANSFORMER", null, "dev.cheat.modules.Esp"), known));
        assertEquals("unnamed@?:Loader", ThreadCollector.originKey(frame(null, null, "Loader"), known));
        assertEquals("dev.cheat", ThreadCollector.packageRoot("dev.cheat.modules.Esp"));
        assertEquals("a", ThreadCollector.packageRoot("a.B"));
        assertEquals("Loader", ThreadCollector.packageRoot("Loader"));
    }

    @Test
    void threadsSnapshot() {
        Map<Thread, StackTraceElement[]> stacks = new LinkedHashMap<>();
        stacks.put(new Thread(() -> { }, "Render thread"),
                new StackTraceElement[] {frame("TRANSFORMER", "minecraft", "net.minecraft.client.Minecraft")});
        stacks.put(new Thread(() -> { }, ""),
                new StackTraceElement[] {frame(null, null, "dev.cheat.Loop")});
        stacks.put(new Thread(() -> { }, ThreadCollector.ATTACH_LISTENER), new StackTraceElement[0]);
        ScanReport r = new ScanReport();
        ThreadCollector.Origins origins = new ThreadCollector.Origins(Set.of("minecraft"));
        ThreadCollector.collect(r, stacks, origins);
        origins.into(r);
        assertEquals(3, r.threadCount);
        assertEquals(1, r.unnamedThreads);
        assertTrue(r.attachListener);
        assertEquals(1, r.origins.size());
        assertEquals("unnamed@?:dev.cheat", r.origins.get(0).key());
        assertEquals(List.of("dev.cheat.Loop"), r.origins.get(0).classes());
        assertEquals(List.of("(sans nom)"), r.origins.get(0).threads());
    }

    // ------------------------------------------------------------------ modules

    @Test
    void unionModuleLocations(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path jar = java.nio.file.Files.writeString(dir.resolve("mon mod+1.jar"), "x");
        String encoded = jar.toUri().getRawPath();
        java.net.URI top = java.net.URI.create("union:" + encoded + "%2364!/");
        assertEquals(jar.toAbsolutePath(), ForgeCollector.fileOf(top).toAbsolutePath());
        assertEquals("mon mod+1.jar", ForgeCollector.lastSegment(top));
        java.net.URI nested = java.net.URI.create("union:" + encoded + "%2312!/META-INF/jarjar/inner-lib.jar%2313!/");
        assertEquals("inner-lib.jar", ForgeCollector.lastSegment(nested));
        assertEquals(jar.toAbsolutePath(), ForgeCollector.fileOf(nested).toAbsolutePath(), "jar imbriqué → jar qui le contient");
        assertNull(ForgeCollector.fileOf(java.net.URI.create("jrt:/java.base")));
        assertNull(ForgeCollector.fileOf(java.net.URI.create("union:" + dir.toUri().getRawPath() + "absent.jar%231!/")));
    }

    // ------------------------------------------------------------------ natives

    private static final Map<String, String> WIN_ENV = Map.of(
            "SystemRoot", "C:\\Windows",
            "ProgramFiles", "C:\\Program Files",
            "ProgramFiles(x86)", "C:\\Program Files (x86)",
            "TEMP", "C:\\Users\\Jean\\AppData\\Local\\Temp",
            "LOCALAPPDATA", "C:\\Users\\Jean\\AppData\\Local",
            "USERPROFILE", "C:\\Users\\Jean");

    private static NativeCollector.Place win(String path) {
        return NativeCollector.classify(path, WIN_ENV::get, "C:\\Program Files\\Java\\jdk-17",
                "C:\\Users\\Jean\\AppData\\Roaming\\.minecraft", "C:\\Users\\Jean", true);
    }

    @Test
    void windowsPlacesAreClassifiedAndAnonymized() {
        assertEquals("system", win("C:\\Windows\\System32\\kernel32.dll").category());
        assertEquals("java", win("C:\\Program Files\\Java\\jdk-17\\bin\\jvm.dll").category());
        assertEquals("programs", win("C:\\Program Files (x86)\\Steam\\GameOverlayRenderer64.dll").category());
        NativeCollector.Place temp = win("C:\\Users\\Jean\\AppData\\Local\\Temp\\a1b2\\x.dll");
        assertEquals("temp", temp.category());
        assertEquals("%TEMP%/a1b2", temp.shown());
        NativeCollector.Place game = win("C:\\Users\\Jean\\AppData\\Roaming\\.minecraft\\bin\\lwjgl.dll");
        assertEquals("game", game.category());
        NativeCollector.Place dl = win("C:\\Users\\Jean\\Downloads\\inject.dll");
        assertEquals("downloads", dl.category());
        NativeCollector.Place other = win("D:\\Jeux\\Outils\\x.dll");
        assertEquals("other", other.category());
        for (NativeCollector.Place p : List.of(temp, game, dl, win("C:\\Users\\Jean\\Documents\\h.dll"))) {
            assertFalse(p.shown().toLowerCase().contains("jean"), "nom d'utilisateur dans " + p.shown());
        }
    }

    @Test
    void suspiciousNativeReasons() {
        NativeCollector.Place temp = win("C:\\Users\\Jean\\AppData\\Local\\Temp\\x7.dll");
        assertEquals("dossier:temp", NativeCollector.reason("x7.dll", temp, List.of()));
        assertEquals("", NativeCollector.reason("lwjgl_opengl.dll", temp, List.of()), "LWJGL extrait dans Temp : normal");
        NativeCollector.Place prog = win("C:\\Program Files\\Tools\\fastinject64.dll");
        assertEquals("nom:inject", NativeCollector.reason("fastinject64.dll", prog, List.of("inject")));
        assertEquals("", NativeCollector.reason("nvoglv64.dll", prog, List.of("inject")));
    }

    @Test
    void linuxPlaces() {
        Function<String, String> env = k -> null;
        NativeCollector.Place sys = NativeCollector.classify("/usr/lib/x86_64-linux-gnu/libGL.so.1", env, "/opt/jdk",
                "/home/jean/.minecraft", "/home/jean", false);
        assertEquals("system", sys.category());
        NativeCollector.Place tmp = NativeCollector.classify("/tmp/x/libcheat.so", env, "/opt/jdk",
                "/home/jean/.minecraft", "/home/jean", false);
        assertEquals("temp", tmp.category());
        NativeCollector.Place home = NativeCollector.classify("/home/jean/stuff/libx.so", env, "/opt/jdk",
                "/home/jean/.minecraft", "/home/jean", false);
        assertEquals("%USERPROFILE%/stuff", home.shown());
    }
}
