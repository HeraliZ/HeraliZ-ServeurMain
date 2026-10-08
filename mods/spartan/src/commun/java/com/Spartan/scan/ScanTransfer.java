package com.Spartan.scan;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Transport d'un rapport du client vers le serveur.
 *
 * Minecraft refuse tout paquet client → serveur de plus de 32 767 octets : le
 * rapport est compressé puis découpé en morceaux, et le serveur le recompose.
 * Côté serveur tout est borné : nombre de morceaux, taille de chaque morceau,
 * taille une fois décompressé (une « bombe » de compression ne passe pas).
 */
public final class ScanTransfer {
    public static final int CHUNK_BYTES = 30_000;
    public static final int MAX_CHUNKS = 16;
    public static final int MAX_INFLATED = 4 << 20;

    private ScanTransfer() {
    }

    /** Encode, compresse et découpe un rapport. */
    public static List<byte[]> pack(ScanReport r) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        r.write(buf);
        byte[] raw = new byte[buf.readableBytes()];
        buf.readBytes(raw);
        byte[] packed = deflate(raw);
        List<byte[]> out = new ArrayList<>();
        for (int off = 0; off < packed.length || out.isEmpty(); off += CHUNK_BYTES) {
            int len = Math.min(CHUNK_BYTES, packed.length - off);
            byte[] part = new byte[Math.max(0, len)];
            System.arraycopy(packed, off, part, 0, part.length);
            out.add(part);
        }
        return out;
    }

    /** Décompresse et décode un rapport recomposé (lève une exception s'il est invalide). */
    public static ScanReport unpack(byte[] packed) {
        byte[] raw = inflate(packed, MAX_INFLATED);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(raw));
        ScanReport r = ScanReport.read(buf);
        if (buf.isReadable()) {
            throw new DecoderException("Spartan: octets en trop dans le rapport");
        }
        return r;
    }

    static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
            byte[] tmp = new byte[8192];
            while (!d.finished()) {
                int n = d.deflate(tmp);
                out.write(tmp, 0, n);
            }
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    static byte[] inflate(byte[] packed, int max) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, packed.length * 4 + 64));
            byte[] tmp = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(tmp);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    throw new DecoderException("Spartan: rapport compressé tronqué");
                }
                if (out.size() + n > max) {
                    throw new DecoderException("Spartan: rapport trop volumineux une fois décompressé");
                }
                out.write(tmp, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new DecoderException("Spartan: rapport compressé invalide", e);
        } finally {
            inf.end();
        }
    }

    /** Recomposition des morceaux d'un rapport (un seul rapport en cours par joueur). */
    public static final class Assembly {
        private final int scanId;
        private final byte[][] parts;
        private int received;
        private int bytes;

        public Assembly(int scanId, int total) {
            if (total < 1 || total > MAX_CHUNKS) {
                throw new DecoderException("Spartan: nombre de morceaux invalide (" + total + ")");
            }
            this.scanId = scanId;
            this.parts = new byte[total][];
        }

        public int scanId() {
            return scanId;
        }

        public int total() {
            return parts.length;
        }

        /**
         * Ajoute un morceau. Un doublon est ignoré ; un morceau incohérent lève une exception.
         * @return le rapport compressé complet quand tous les morceaux sont arrivés, sinon null
         */
        public byte[] accept(int scanId, int index, int total, byte[] data) {
            if (scanId != this.scanId || total != parts.length || index < 0 || index >= parts.length
                    || data == null || data.length > CHUNK_BYTES) {
                throw new DecoderException("Spartan: morceau de rapport incohérent");
            }
            if (parts[index] != null) {
                return null;
            }
            parts[index] = data;
            received++;
            bytes += data.length;
            if (received < parts.length) {
                return null;
            }
            byte[] all = new byte[bytes];
            int off = 0;
            for (byte[] p : parts) {
                System.arraycopy(p, 0, all, off, p.length);
                off += p.length;
            }
            return all;
        }
    }
}
