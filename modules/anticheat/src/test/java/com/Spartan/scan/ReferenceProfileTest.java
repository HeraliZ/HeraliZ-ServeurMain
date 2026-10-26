package com.Spartan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceProfileTest {
    @Test
    void learnMergeAndJsonRoundTrip() {
        ReferenceProfile ref = ScanTestData.reference();
        assertEquals(1, ref.samples);
        assertTrue(ref.fileHashes.contains(ScanTestData.JEI_SHA));
        assertTrue(ref.modules.contains("GAME/jei"));
        assertTrue(ref.agents.contains("authlib-injector-1.2.5.jar:" + ScanTestData.AUTHLIB_SHA));
        assertEquals(2, ref.knownMixins().length);

        // Un 2e PC propre (autre version de Java) complète la référence sans l'effacer.
        ScanReport other = ScanTestData.clean();
        other.loaders.add("jdk.internal.loader.ClassLoaders$PlatformClassLoader");
        ref.learn(other, "Modo", false, 2_000L);
        assertEquals(2, ref.samples);
        assertTrue(ref.learnedFrom.contains("Admin") && ref.learnedFrom.contains("Modo"));

        ReferenceProfile back = ReferenceProfile.fromJson(ref.toJson());
        assertEquals(ref.fileHashes, back.fileHashes);
        assertEquals(ref.mixins, back.mixins);
        assertEquals(ref.loaders, back.loaders);
        assertEquals(ref.samples, back.samples);
        assertEquals(List.of(ref.knownMixins()[0], ref.knownMixins()[1]),
                List.of(back.knownMixins()[0], back.knownMixins()[1]));
    }

    @Test
    void replaceForgetsThePreviousReference() {
        ReferenceProfile ref = ScanTestData.reference();
        ScanReport minimal = new ScanReport();
        minimal.modules.add("GAME/minecraft@x.jar");
        ref.learn(minimal, "Admin", true, 3_000L);
        assertEquals(1, ref.samples);
        assertEquals(1, ref.modules.size());
        assertTrue(ref.fileHashes.isEmpty());
    }

    @Test
    void oldOrPartialFileNeverGivesNulls() {
        ReferenceProfile p = ReferenceProfile.fromJson("{\"samples\": 1}");
        assertTrue(p.fileHashes.isEmpty() && p.modules.isEmpty() && p.mixins.isEmpty() && p.agents.isEmpty());
        assertEquals("", p.learnedFrom);
    }

    @Test
    void mixinFingerprintIsStable() {
        assertEquals(ScanReport.mixinFingerprint("a.json"), ScanReport.mixinFingerprint("a.json"));
        assertTrue(ScanReport.mixinFingerprint("a.json") != ScanReport.mixinFingerprint("b.json"));
        long fp = ScanReport.mixinFingerprint("jei.mixins.json");
        assertEquals(fp, Long.parseUnsignedLong(ScanReport.hex(fp), 16));
    }
}
