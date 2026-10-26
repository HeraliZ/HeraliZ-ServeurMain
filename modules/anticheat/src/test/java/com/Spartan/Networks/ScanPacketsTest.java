package com.Spartan.Networks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Ce que le serveur encode, le client le relit à l'identique (ordre des champs). */
class ScanPacketsTest {
    @Test
    void scanRequestRoundTrip() {
        ScanRequestPacket p = new ScanRequestPacket(123, 40, true, new long[] {1L, -2L},
                List.of("net.minecraft.client.renderer.LevelRenderer"), List.of("baritone.api.BaritoneAPI"),
                List.of("cheatengine"), List.of("inject"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(buf);
        ScanRequestPacket back = ScanRequestPacket.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(123, back.scanId());
        assertEquals(40, back.samples());
        assertTrue(back.referenceKnown());
        assertArrayEquals(new long[] {1L, -2L}, back.knownMixins());
        assertEquals(List.of("net.minecraft.client.renderer.LevelRenderer"), back.watchClasses());
        assertEquals(List.of("baritone.api.BaritoneAPI"), back.classProbes());
        assertEquals(List.of("cheatengine"), back.processPatterns());
        assertEquals(List.of("inject"), back.nativePatterns());
    }

    @Test
    void scanChunkRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new ScanChunkPacket(7, 1, 3, new byte[] {4, 5, 6}).encode(buf);
        ScanChunkPacket.decode(buf);
        assertFalse(buf.isReadable());
    }
}
