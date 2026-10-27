package com.Spartan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.scan.Finding.Severity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Le client propre ne doit JAMAIS déclencher d'alerte ; chaque façon connue de
 * charger une triche (jar, agent, injection, Mixin, DLL…) doit être relevée.
 */
class ScanAnalyzerTest {
    private static List<Finding> analyze(ScanReport r) {
        return ScanAnalyzer.analyze(r, ScanTestData.reference(), null, ScanTestData.settings());
    }

    private static Finding find(List<Finding> fs, String keyPrefix) {
        return fs.stream().filter(f -> f.key().startsWith(keyPrefix)).findFirst().orElse(null);
    }

    private static Severity verdict(ScanReport r) {
        return ScanAnalyzer.verdict(analyze(r));
    }

    @Test
    void cleanClientRaisesNothing() {
        assertNull(verdict(ScanTestData.clean()), analyze(ScanTestData.clean()).toString());
    }

    @Test
    void cleanClientWithoutReferenceRaisesNothing() {
        List<Finding> fs = ScanAnalyzer.analyze(ScanTestData.clean(), new ReferenceProfile(), null, ScanTestData.settings());
        assertNull(ScanAnalyzer.verdict(fs), fs.toString());
    }

    @Test
    void unknownJarInModsFolderIsSuspect() {
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("mods", "optimisation.jar", 50_000, "a".repeat(64)));
        Finding f = find(analyze(r), "file:");
        assertEquals(Severity.SUSPECT, f.severity());
        assertTrue(f.text().contains("optimisation.jar") && f.text().contains("aaaaaaaaaaaa"), f.text());
    }

    @Test
    void modifiedOfficialJarIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.files.set(0, new ScanReport.FileEntry("mods", "jei-1.20.1.jar", 1_000_123, "b".repeat(64)));
        Finding f = find(analyze(r), "file:");
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("MODIFIÉ"), f.text());
    }

    @Test
    void modifiedMinecraftJarIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.files.set(2, new ScanReport.FileEntry("layer:GAME", "client-1.20.1-srg.jar", 20_000_000, "c".repeat(64)));
        assertEquals(Severity.STRONG, find(analyze(r), "file:").severity());
    }

    @Test
    void renamedOfficialJarIsNotAccused() {
        // Même contenu, autre nom : ce n'est pas une triche.
        ScanReport r = ScanTestData.clean();
        r.files.set(0, new ScanReport.FileEntry("mods", "JEI.jar", 1_000_000, ScanTestData.JEI_SHA));
        assertNull(find(analyze(r), "file:"));
    }

    @Test
    void foreignAgentIsStrongEvenWithoutReference() {
        ScanReport r = ScanTestData.clean();
        r.jvmFlags.add("-javaagent:optifine-helper.jar");
        List<Finding> fs = ScanAnalyzer.analyze(r, new ReferenceProfile(), null, ScanTestData.settings());
        Finding f = find(fs, "jvm:-javaagent:optifine");
        assertEquals(Severity.STRONG, f.severity());
    }

    @Test
    void fakeAuthlibInjectorIsCaughtByItsHash() {
        // Un agent de triche renommé « authlib-injector » passe le contrôle par nom, pas l'empreinte.
        ScanReport r = ScanTestData.clean();
        r.files.set(3, new ScanReport.FileEntry("agent", "authlib-injector-1.2.5.jar", 400_000, "d".repeat(64)));
        Finding f = find(analyze(r), "jvm:-javaagent:authlib");
        assertEquals(Severity.STRONG, f.severity(), String.valueOf(f));
    }

    @Test
    void nativeAgentsDebuggerAndLaunchTricksAreStrong() {
        for (String flag : List.of("-agentpath:inject.dll", "-agentlib:jdwp", "-Xbootclasspath",
                "-Djava.system.class.loader=x.Loader", "-Djdk.attach.allowAttachSelf", "arg:--fml.mods")) {
            ScanReport r = ScanTestData.clean();
            r.jvmFlags.add(flag);
            assertEquals(Severity.STRONG, verdict(r), flag);
        }
    }

    @Test
    void attachedInjectorIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.attachListener = true;
        assertEquals(Severity.STRONG, find(analyze(r), "attach").severity());
    }

    @Test
    void tamperedAnticheatIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.tamper.add("com.Spartan.Client.ClientEvents : méthode ajoutée handler$zza000$skip");
        assertEquals(Severity.STRONG, verdict(r));
    }

    @Test
    void unknownMixinOnRenderingIsStrongAndExplained() {
        ScanReport r = ScanTestData.clean();
        r.mixins.add(new ScanReport.MixinEntry("kestrel.mixins.json", "com.kestrel.mixin", "kestrel-esp.jar", 2,
                List.of("net.minecraft.client.renderer.LevelRenderer", "net.minecraft.client.renderer.entity.EntityRenderer")));
        Finding f = find(analyze(r), "mixin:");
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("ESP"), f.text());
    }

    @Test
    void unknownHarmlessMixinIsOnlySuspect() {
        ScanReport r = ScanTestData.clean();
        r.mixins.add(new ScanReport.MixinEntry("zoom.mixins.json", "com.zoom.mixin", "zoom.jar", 1,
                List.of("net.minecraft.client.gui.screens.TitleScreen")));
        assertEquals(Severity.SUSPECT, find(analyze(r), "mixin:").severity());
    }

    @Test
    void unknownMixinIsCaughtEvenWhenTheReferenceHasNoMixinAtAll() {
        // Cas réel : modpack de référence sans aucun Mixin, puis un mod ESP à base de Mixin.
        ScanReport cleanNoMixin = ScanTestData.clean();
        cleanNoMixin.mixins.clear();
        ReferenceProfile ref = new ReferenceProfile();
        ref.learn(cleanNoMixin, "Admin", true, 1L);
        ScanReport r = ScanTestData.clean();
        r.mixins.clear();
        r.mixins.add(new ScanReport.MixinEntry("fakeesp.mixins.json", "dev.cheat.esp.mixin", "fake-esp-mod.jar", 1,
                List.of("net.minecraft.client.renderer.LevelRenderer")));
        Finding f = find(ScanAnalyzer.analyze(r, ref, null, ScanTestData.settings()), "mixin:");
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("fake-esp-mod.jar") && f.text().contains("LevelRenderer"), f.text());
    }

    @Test
    void spartanOwnMixinConfigIsKnownButItsNameCannotBeBorrowed() {
        // Après une mise à jour de Spartan, sa propre config Mixin (anti-ESP) ne doit alerter personne…
        ScanReport own = ScanTestData.clean();
        own.mixins.add(new ScanReport.MixinEntry("spartan.mixins.json", "com.Spartan.mixin", "spartan-1.8.0.jar", 2,
                List.of("net.minecraft.server.level.ServerPlayer", "net.minecraft.server.level.ServerLevel")));
        assertNull(find(analyze(own), "mixin:"));
        // … mais une config qui prend son nom pour modifier le rendu reste un indice fort.
        ScanReport fake = ScanTestData.clean();
        fake.mixins.add(new ScanReport.MixinEntry("spartan.mixins.json", "com.Spartan.mixin", "x.jar", 1,
                List.of("net.minecraft.client.renderer.LevelRenderer")));
        assertEquals(Severity.STRONG, find(analyze(fake), "mixin:").severity());
    }

    @Test
    void mixinKnownOnlyByNameIsSuspect() {
        // Config vue seulement dans un manifeste (classes visées encore inconnues).
        ScanReport r = ScanTestData.clean();
        r.mixins.add(new ScanReport.MixinEntry("mystery.mixins.json", "", "mystery.jar", 0, List.of()));
        Finding f = find(analyze(r), "mixin:");
        assertEquals(Severity.SUSPECT, f.severity());
        assertTrue(f.text().contains("classes visées inconnues"), f.text());
    }

    @Test
    void codeOutsideAnyModuleIsCaught() {
        ScanReport r = ScanTestData.clean();
        r.origins.add(new ScanReport.OriginEntry("unnamed@?:dev.cheat", List.of("dev.cheat.modules.Esp"),
                List.of("Render thread")));
        Finding f = find(analyze(r), "origin:");
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("dev.cheat.modules.Esp"), f.text());
    }

    @Test
    void launchLayerModuleIsStrongAndGameModuleSuspect() {
        ScanReport r = ScanTestData.clean();
        r.modules.add("SERVICE/cheatloader@loader.jar");
        r.modules.add("GAME/minimap@minimap.jar");
        List<Finding> fs = analyze(r);
        assertEquals(Severity.STRONG, find(fs, "module:SERVICE").severity());
        assertEquals(Severity.SUSPECT, find(fs, "module:GAME").severity());
    }

    @Test
    void modLoadedOutsideModsFolderIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.loadedFiles.add("maven libs|esp-1.0.jar|esp");
        assertEquals(Severity.STRONG, find(analyze(r), "provider:").severity());
    }

    @Test
    void blacklistedModIdIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.loadedFiles.add("mods folder|advanced-xray.jar|xray");
        assertEquals(Severity.STRONG, find(analyze(r), "banned:xray").severity());
    }

    @Test
    void dllFromTempIsSuspectAndNormalDllIsIgnored() {
        ScanReport r = ScanTestData.clean();
        r.natives.add(new ScanReport.NativeEntry("x7f3a.dll", "%TEMP%", "dossier:temp"));
        List<Finding> fs = analyze(r);
        assertEquals(1, fs.stream().filter(f -> f.key().startsWith("dll:")).count());
        assertEquals(Severity.SUSPECT, find(fs, "dll:").severity());
    }

    @Test
    void cheatToolsAndKnownClassesAreReported() {
        ScanReport r = ScanTestData.clean();
        r.processHits.add("cheatengine-x86_64.exe");
        r.classHits.add("baritone.api.BaritoneAPI");
        List<Finding> fs = analyze(r);
        assertEquals(Severity.SUSPECT, find(fs, "proc:").severity());
        assertEquals(Severity.STRONG, find(fs, "class:").severity());
    }

    @Test
    void injectionAfterJoiningIsRaised() {
        ScanReport first = ScanTestData.clean();
        Set<String> baseline = ScanAnalyzer.inventory(first);
        ScanReport later = ScanTestData.clean();
        later.natives.add(new ScanReport.NativeEntry("helper.dll", "%USERPROFILE%/documents", ""));
        later.modules.add("GAME/injected@?");
        List<Finding> fs = ScanAnalyzer.analyze(later, ScanTestData.reference(), baseline, ScanTestData.settings());
        assertEquals(Severity.STRONG, find(fs, "late:d:").severity(), fs.toString());
        Finding module = find(fs, "module:GAME/injected");
        assertEquals(Severity.STRONG, module.severity());
        assertTrue(module.text().contains("en cours de partie"), module.text());
        // Le même contrôle sans changement depuis le début de la session : rien.
        assertNull(ScanAnalyzer.verdict(ScanAnalyzer.analyze(ScanTestData.clean(), ScanTestData.reference(),
                baseline, ScanTestData.settings())));
    }

    @Test
    void lateDllFromInstalledProgramOrOverlayStaysSuspect() {
        Set<String> baseline = ScanAnalyzer.inventory(ScanTestData.clean());
        ScanReport later = ScanTestData.clean();
        later.natives.add(new ScanReport.NativeEntry("rgbhelper.dll", "%PROGRAMFILES%/vendor", ""));
        later.natives.add(new ScanReport.NativeEntry("discord_hook64.dll", "%USERPROFILE%/appdata/local/discord", ""));
        List<Finding> fs = ScanAnalyzer.analyze(later, ScanTestData.reference(), baseline, ScanTestData.settings());
        for (Finding f : fs) {
            if (f.key().startsWith("late:d:")) {
                assertEquals(Severity.SUSPECT, f.severity(), f.toString());
            }
        }
    }

    @Test
    void gameJavaLibraryLoadedLaterIsNormal() {
        // 27/10 : javajpeg.dll du Java du jeu, chargée à la demande (capture d'écran de l'anticheat), était signalée
        // « injection probable » chez le propriétaire
        Set<String> baseline = ScanAnalyzer.inventory(ScanTestData.clean());
        ScanReport later = ScanTestData.clean();
        later.natives.add(new ScanReport.NativeEntry("javajpeg.dll", "%JAVA%/bin", ""));
        later.natives.add(new ScanReport.NativeEntry("lcms.dll", "%JAVA%/bin", ""));
        later.natives.add(new ScanReport.NativeEntry("jvm.dll", "%JAVA%/bin/server", ""));
        List<Finding> fs = ScanAnalyzer.analyze(later, ScanTestData.reference(), baseline, ScanTestData.settings());
        assertNull(ScanAnalyzer.verdict(fs), fs.toString());
    }

    @Test
    void unknownDllInGameJavaLoadedLaterStaysSuspect() {
        Set<String> baseline = ScanAnalyzer.inventory(ScanTestData.clean());
        ScanReport later = ScanTestData.clean();
        later.natives.add(new ScanReport.NativeEntry("helper.dll", "%JAVA%/bin", ""));
        List<Finding> fs = ScanAnalyzer.analyze(later, ScanTestData.reference(), baseline, ScanTestData.settings());
        assertEquals(Severity.SUSPECT, ScanAnalyzer.verdict(fs), fs.toString());
        Finding late = find(fs, "late:d:helper.dll");
        assertEquals(Severity.SUSPECT, late.severity());
        assertTrue(!late.text().contains("injection"), late.text());
        assertEquals(Severity.SUSPECT, find(fs, "dll:jdk:helper.dll").severity());
    }

    @Test
    void unknownCodeAppearingAfterJoiningIsStrongWithoutReference() {
        Set<String> baseline = ScanAnalyzer.inventory(ScanTestData.clean());
        ScanReport later = ScanTestData.clean();
        later.origins.add(new ScanReport.OriginEntry("unnamed@app:a.b", List.of("a.b.Ghost"), List.of("Render thread")));
        List<Finding> fs = ScanAnalyzer.analyze(later, null, baseline, ScanTestData.settings());
        Finding f = find(fs, "origin:unnamed@app:a.b");
        assertEquals(Severity.STRONG, f.severity(), fs.toString());
        assertTrue(f.text().contains("en cours de partie"), f.text());
        // Au premier contrôle (pas encore d'inventaire de session) et sans référence : soupçon seulement.
        assertEquals(Severity.SUSPECT, find(ScanAnalyzer.analyze(later, null, null, ScanTestData.settings()),
                "origin:unnamed@app:a.b").severity());
    }

    @Test
    void scrambledInjectedClientIsStrongWithoutReference() {
        // relevé réel d'un client injecté (11/10) : classes aux noms brouillés, dans des threads sans nom
        ScanReport r = ScanTestData.clean();
        r.unnamedThreads = 8;
        r.origins.add(new ScanReport.OriginEntry("unnamed@?:\u001ad\u0010s",
                List.of("\u001ad\u0010s.\u0014b4od\u0018\u0003i\t\n00"), List.of("(sans nom)")));
        List<Finding> fs = ScanAnalyzer.analyze(r, null, null, ScanTestData.settings());
        Finding f = find(fs, "origin:unnamed@?:");
        assertEquals(Severity.STRONG, f.severity(), fs.toString());
        assertTrue(f.text().contains("\\x1A"), f.text());
        assertEquals(Severity.STRONG, find(fs, "threads:unnamed").severity());
    }

    @Test
    void unknownReadableCodeWithoutReferenceStaysSuspect() {
        ScanReport r = ScanTestData.clean();
        r.unnamedThreads = 1;
        r.origins.add(new ScanReport.OriginEntry("unnamed@app:org.example", List.of("org.example.Helper"), List.of("Worker")));
        List<Finding> fs = ScanAnalyzer.analyze(r, null, null, ScanTestData.settings());
        assertEquals(Severity.SUSPECT, find(fs, "origin:unnamed@app:org.example").severity());
        assertEquals(Severity.SUSPECT, find(fs, "threads:unnamed").severity());
    }

    @Test
    void strongFindingsComeFirst() {
        ScanReport r = ScanTestData.clean();
        r.processHits.add("x64dbg.exe");
        r.attachListener = true;
        assertEquals(Severity.STRONG, analyze(r).get(0).severity());
    }
}
