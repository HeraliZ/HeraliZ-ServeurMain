package com.Spartan.Networks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.scan.Sondes;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Ce que le serveur encode, le client le relit à l'identique (ordre des champs). */
class ScanPacketsTest {
    @Test
    void scanRequestRoundTrip() {
        ScanRequestPacket p = new ScanRequestPacket(123, 40, true, new long[] {1L, -2L},
                List.of("net.minecraft.client.renderer.LevelRenderer"),
                Sondes.preparer(99L, List.of("cheatengine"), List.of("baritone.api.BaritoneAPI")));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(buf);
        ScanRequestPacket back = ScanRequestPacket.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(123, back.scanId());
        assertEquals(40, back.samples());
        assertTrue(back.referenceKnown());
        assertArrayEquals(new long[] {1L, -2L}, back.knownMixins());
        assertEquals(List.of("net.minecraft.client.renderer.LevelRenderer"), back.watchClasses());
        assertEquals(99L, back.sondes().sel);
        assertArrayEquals(p.sondes().programmes, back.sondes().programmes);
        assertArrayEquals(p.sondes().longueurs, back.sondes().longueurs);
        assertArrayEquals(p.sondes().paquets, back.sondes().paquets);
        assertTrue(back.sondes().programmeCherche("cheatengine-x86_64.exe"));
        assertTrue(back.sondes().paquetCherche("baritone.api") != null);
    }

    @Test
    void scanChunkRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new ScanChunkPacket(7, 1, 3, new byte[] {4, 5, 6}).encode(buf);
        ScanChunkPacket.decode(buf);
        assertFalse(buf.isReadable());
    }
}
