package com.Spartan.scan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Random;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ScanTransferTest {
    /** Rapport réaliste d'un gros modpack : ~300 mods. */
    private static ScanReport big() {
        ScanReport r = ScanTestData.clean();
        Random rnd = new Random(1);
        for (int i = 0; i < 300; i++) {
            StringBuilder sha = new StringBuilder();
            for (int k = 0; k < 64; k++) {
                sha.append("0123456789abcdef".charAt(rnd.nextInt(16)));
            }
            r.files.add(new ScanReport.FileEntry("mods", "mod-numero-" + i + "-1.20.1.jar", 100_000 + i, sha.toString()));
            r.modules.add("GAME/mod" + i + "@mod-numero-" + i + "-1.20.1.jar");
            r.loadedFiles.add("mods folder|mod-numero-" + i + "-1.20.1.jar|mod" + i);
            r.mixins.add(new ScanReport.MixinEntry("mod" + i + ".mixins.json", "com.example.mod" + i + ".mixin", "mod-numero-" + i + "-1.20.1.jar", 12, List.of()));
        }
        return r;
    }

    private static ScanReport reassemble(List<byte[]> chunks, int scanId) {
        ScanTransfer.Assembly a = new ScanTransfer.Assembly(scanId, chunks.size());
        byte[] all = null;
        for (int i = chunks.size() - 1; i >= 0; i--) { // ordre inversé : l'ordre d'arrivée ne compte pas
            all = a.accept(scanId, i, chunks.size(), chunks.get(i));
        }
        return ScanTransfer.unpack(all);
    }

    @Test
    void bigReportRoundTripsWithinPacketLimits() {
        ScanReport r = big();
        List<byte[]> chunks = ScanTransfer.pack(r);
        assertTrue(chunks.size() <= ScanTransfer.MAX_CHUNKS, "trop de morceaux : " + chunks.size());
        for (byte[] c : chunks) {
            assertTrue(c.length <= ScanTransfer.CHUNK_BYTES);
        }
        ScanReport back = reassemble(chunks, r.scanId);
        assertEquals(r.files, back.files);
        assertEquals(r.modules, back.modules);
        assertEquals(r.loadedFiles, back.loadedFiles);
        assertEquals(r.mixins, back.mixins);
        assertEquals(r.jvmFlags, back.jvmFlags);
        assertEquals(r.natives, back.natives);
        assertEquals(r.attachDisabled, back.attachDisabled);
    }

    @Test
    void duplicateChunkIsIgnoredAndIncoherentChunkRejected() {
        ScanTransfer.Assembly a = new ScanTransfer.Assembly(7, 2);
        assertNull(a.accept(7, 0, 2, new byte[] {1, 2}));
        assertNull(a.accept(7, 0, 2, new byte[] {9, 9}), "doublon ignoré");
        assertArrayEquals(new byte[] {1, 2, 3}, a.accept(7, 1, 2, new byte[] {3}));
        ScanTransfer.Assembly b = new ScanTransfer.Assembly(7, 2);
        assertThrows(RuntimeException.class, () -> b.accept(8, 0, 2, new byte[1]), "autre contrôle");
        assertThrows(RuntimeException.class, () -> b.accept(7, 2, 2, new byte[1]), "index hors limites");
        assertThrows(RuntimeException.class, () -> b.accept(7, 0, 3, new byte[1]), "total changé");
        assertThrows(RuntimeException.class, () -> b.accept(7, 0, 2, new byte[ScanTransfer.CHUNK_BYTES + 1]), "trop gros");
        assertThrows(RuntimeException.class, () -> new ScanTransfer.Assembly(1, ScanTransfer.MAX_CHUNKS + 1));
    }

    @Test
    void compressionBombIsRejected() {
        byte[] bomb = ScanTransfer.deflate(new byte[ScanTransfer.MAX_INFLATED + 1024]);
        assertTrue(bomb.length < ScanTransfer.CHUNK_BYTES, "la bombe tient dans un seul morceau");
        assertThrows(RuntimeException.class, () -> ScanTransfer.unpack(bomb));
    }

    @Test
    void garbageAndTruncatedDataAreRejected() {
        assertThrows(RuntimeException.class, () -> ScanTransfer.unpack(new byte[] {1, 2, 3, 4}));
        List<byte[]> chunks = ScanTransfer.pack(ScanTestData.clean());
        byte[] packed = chunks.get(0);
        byte[] cut = new byte[packed.length / 2];
        System.arraycopy(packed, 0, cut, 0, cut.length);
        assertThrows(RuntimeException.class, () -> ScanTransfer.unpack(cut));
    }

    @Test
    void invalidHashInReportIsRejected() {
        ScanReport r = ScanTestData.clean();
        r.files.add(new ScanReport.FileEntry("mods", "x.jar", 1, "pas-une-empreinte"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        r.write(buf);
        assertThrows(RuntimeException.class, () -> ScanReport.read(buf));
    }

    @Test
    void writerNeverProducesAnInvalidReport() {
        // Un client honnête avec des valeurs hors normes : tronquées à l'écriture, jamais refusées.
        ScanReport r = ScanTestData.clean();
        r.java = "x".repeat(10_000);
        for (int i = 0; i < ScanReport.MAX_FLAGS + 50; i++) {
            r.jvmFlags.add("-flag" + i);
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        r.write(buf);
        ScanReport back = ScanReport.read(buf);
        assertEquals(ScanReport.TEXT, back.java.length());
        assertEquals(ScanReport.MAX_FLAGS, back.jvmFlags.size());
    }
}
