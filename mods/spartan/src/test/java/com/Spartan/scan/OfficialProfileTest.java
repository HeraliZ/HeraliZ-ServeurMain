package com.Spartan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.scan.Finding.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Modpack officiel fabriqué avec le serveur : sans aucun client de référence appris, un jar en plus, un jar modifié ou
 * une config Mixin inconnue sont relevés ; le jeu propre ne déclenche rien. Et les traces d'injection native.
 */
class OfficialProfileTest {
    private static OfficialProfile official() {
        return OfficialProfile.fromJson("""
                {"genere": "2026-10-08T00:00:00Z", "empreinteDossier": "%s",
                 "mods": {"JEI-1.20.1.jar": "%s", "create-1.20.1.jar": "%s"},
                 "mixins": {"jei.mixins.json": "mezz.jei.mixin", "create.mixins.json": "com.simibubi.create.foundation.mixin"}}
                """.formatted("9".repeat(64), ScanTestData.JEI_SHA, ScanTestData.CREATE_SHA));
    }

    private static List<Finding> analyze(ScanReport r) {
        return ScanAnalyzer.analyze(r, new ReferenceProfile(), official(), null, ScanTestData.settings());
    }

    private static Finding find(List<Finding> fs, String keyPrefix) {
        return fs.stream().filter(f -> f.key().startsWith(keyPrefix)).findFirst().orElse(null);
    }

    @Test
    void cleanOfficialClientRaisesNothing() {
        List<Finding> fs = analyze(ScanTestData.clean());
        assertNull(ScanAnalyzer.verdict(fs), fs.toString());
    }

    @Test
    void extraJarIsSeenWithoutLearnedReference() {
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("mods", "xray-ultimate.jar", 40_000, "c".repeat(64)));
        Finding f = find(analyze(r), "file:");
        assertNotNull(f);
        assertEquals(Severity.SUSPECT, f.severity());
        assertTrue(f.text().contains("xray-ultimate.jar"), f.text());
    }

    @Test
    void officialJarModifiedIsStrongWhateverTheCase() {
        ScanReport r = ScanTestData.clean();
        r.files.set(0, new ScanReport.FileEntry("mods", "jei-1.20.1.jar", 1_000_000, "d".repeat(64)));
        Finding f = find(analyze(r), "file:");
        assertNotNull(f);
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("MODIFIÉ"), f.text());
    }

    @Test
    void renamedOfficialJarIsStillKnown() {
        ScanReport r = ScanTestData.clean();
        r.files.set(0, new ScanReport.FileEntry("mods", "renomme.jar", 1_000_000, ScanTestData.JEI_SHA));
        assertNull(find(analyze(r), "file:"));
    }

    @Test
    void unknownMixinConfigTouchingRenderingIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.mixins.add(new ScanReport.MixinEntry("esp.mixins.json", "a.b.mixin", "", 2,
                List.of("net.minecraft.client.renderer.LevelRenderer", "net.minecraft.client.renderer.entity.EntityRenderer")));
        Finding f = find(analyze(r), "mixin:");
        assertNotNull(f);
        assertEquals(Severity.STRONG, f.severity());
    }

    @Test
    void cheatHidingBehindAnOfficialMixinNameIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.mixins.set(0, new ScanReport.MixinEntry("jei.mixins.json", "cheat.client.mixin", "", 4, List.of()));
        Finding f = find(analyze(r), "mixin:");
        assertNotNull(f);
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("déguisée"), f.text());
    }

    @Test
    void librariesOutsideModsNeedTheLearnedReference() {
        // le modpack officiel ne connaît pas les bibliothèques du lanceur : pas d'accusation sans référence apprise
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("layer:BOOT", "une-lib.jar", 10_000, "e".repeat(64)));
        assertNull(find(analyze(r), "file:"));
    }

    @Test
    void hiddenInjectedThreadIsStrong() {
        ScanReport r = ScanTestData.clean();
        r.injection.add("thread|0x1F2E0000");
        Finding f = find(analyze(r), "inject:thread:");
        assertNotNull(f);
        assertEquals(Severity.STRONG, f.severity());
    }

    @Test
    void displayHookToUnbackedMemoryIsStrongToUnknownModuleSuspect() {
        ScanReport r = ScanTestData.clean();
        r.injection.add("hook|opengl32!wglSwapBuffers|0x2A0000|");
        r.injection.add("hook|gdi32!SwapBuffers|0x7FF1000|bizarre.dll");
        List<Finding> fs = analyze(r);
        assertEquals(Severity.STRONG, find(fs, "inject:hook:opengl32").severity());
        assertEquals(Severity.SUSPECT, find(fs, "inject:hook:gdi32").severity());
    }

    /** Launcher Helios : mods chargés depuis son dossier (« maven libs », --fml.mavenRoots / --fml.modLists). */
    private static ScanReport launcherClient() {
        ScanReport r = ScanTestData.clean();
        r.files.removeIf(f -> f.origin().equals("mods"));
        r.files.add(new ScanReport.FileEntry("modlist", "jei-1.20.1-11.jar", 1_000_000, ScanTestData.JEI_SHA));
        r.files.add(new ScanReport.FileEntry("modlist", "create-1.20.1-0.5.jar", 15_000_000, ScanTestData.CREATE_SHA));
        r.jvmFlags.add("arg:--fml.mavenRoots");
        r.jvmFlags.add("arg:--fml.modLists");
        r.loadedFiles.add("maven libs|jei-1.20.1-11.jar|jei");
        return r;
    }

    @Test
    void cleanLauncherClientRaisesNothing() {
        List<Finding> fs = analyze(launcherClient());
        assertNull(ScanAnalyzer.verdict(fs) == Severity.INFO ? null : ScanAnalyzer.verdict(fs), fs.toString());
    }

    @Test
    void cheatJarAddedToTheLauncherFolderIsStrong() {
        ScanReport r = launcherClient();
        r.files.add(new ScanReport.FileEntry("modlist", "meteor-client.jar", 3_000_000, "f".repeat(64)));
        Finding f = find(analyze(r), "file:");
        assertNotNull(f);
        assertEquals(Severity.STRONG, f.severity());
        assertTrue(f.text().contains("launcher"), f.text());
    }

    @Test
    void launcherArgsStayStrongWithoutOfficialModpack() {
        List<Finding> fs = ScanAnalyzer.analyze(launcherClient(), new ReferenceProfile(), null, null, ScanTestData.settings());
        assertEquals(Severity.STRONG, ScanAnalyzer.verdict(fs), fs.toString());
    }

    @Test
    void mixinFingerprintsAreSentToTheClient() {
        assertEquals(2, official().mixinFingerprints().length);
        assertTrue(official().knowsName("jei-1.20.1.jar"));
    }
}
