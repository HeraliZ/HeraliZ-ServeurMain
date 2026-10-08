package com.Spartan.scan;

import java.util.List;

/** Rapports de test : un client propre du modpack officiel, et des variantes. */
final class ScanTestData {
    static final String JEI_SHA = "1".repeat(64);
    static final String CREATE_SHA = "2".repeat(64);
    static final String MC_SHA = "3".repeat(64);
    static final String AUTHLIB_SHA = "4".repeat(64);

    private ScanTestData() {
    }

    /** Ce qu'envoie un client honnête lancé par le launcher officiel. */
    static ScanReport clean() {
        ScanReport r = new ScanReport();
        r.scanId = 42;
        r.java = "17.0.8 (Eclipse Adoptium)";
        r.os = "Windows 10 10.0 amd64";
        r.files.add(new ScanReport.FileEntry("mods", "jei-1.20.1.jar", 1_000_000, JEI_SHA));
        r.files.add(new ScanReport.FileEntry("mods", "create-1.20.1.jar", 15_000_000, CREATE_SHA));
        r.files.add(new ScanReport.FileEntry("layer:GAME", "client-1.20.1-srg.jar", 20_000_000, MC_SHA));
        r.files.add(new ScanReport.FileEntry("agent", "authlib-injector-1.2.5.jar", 300_000, AUTHLIB_SHA));
        r.modules.addAll(List.of("BOOT/cpw.mods.modlauncher@modlauncher-10.0.9.jar", "SERVICE/fml_loader@fmlloader.jar",
                "GAME/minecraft@client-1.20.1-srg.jar", "GAME/forge@forge-client.jar", "GAME/jei@jei-1.20.1.jar",
                "GAME/create@create-1.20.1.jar"));
        r.loadedFiles.addAll(List.of("minecraft|client-1.20.1-srg.jar|minecraft", "mods folder|jei-1.20.1.jar|jei",
                "mods folder|create-1.20.1.jar|create", "JarInJar|flywheel.jar|flywheel"));
        r.mixins.add(new ScanReport.MixinEntry("jei.mixins.json", "mezz.jei.mixin", "jei-1.20.1.jar", 3, List.of()));
        r.mixins.add(new ScanReport.MixinEntry("create.mixins.json", "com.simibubi.create.foundation.mixin", "create-1.20.1.jar", 40, List.of()));
        r.jvmFlags.add("-javaagent:authlib-injector-1.2.5.jar");
        r.attachDisabled = true;
        r.threadCount = 3;
        r.threadNames.addAll(List.of("Render thread", "Server thread", "Worker-Main-1"));
        r.loaders.addAll(List.of("cpw.mods.modlauncher.TransformingClassLoader",
                "jdk.internal.loader.ClassLoaders$AppClassLoader"));
        r.natives.add(new ScanReport.NativeEntry("GameOverlayRenderer64.dll", "%PROGRAMFILES%/steam", ""));
        return r;
    }

    static ReferenceProfile reference() {
        ReferenceProfile ref = new ReferenceProfile();
        ref.learn(clean(), "Admin", true, 1_000L);
        return ref;
    }

    static ScanAnalyzer.Settings settings() {
        return new ScanAnalyzer.Settings(List.of("authlib-injector"), List.of("wurst", "xray"));
    }
}
