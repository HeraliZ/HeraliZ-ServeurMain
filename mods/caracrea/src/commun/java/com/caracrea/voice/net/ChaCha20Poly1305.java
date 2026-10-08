package com.caracrea.voice.net;

/**
 * ChaCha20-Poly1305 authenticated encryption (RFC 8439), written for the voice packets: plain Java, in place, and
 * without any allocation once the instance exists. An instance keeps its working state, so each thread uses its own;
 * keys are prepared once ({@link Key}) and shared freely.
 *
 * <p>The 96-bit nonce is made of a 32-bit prefix (the direction of the packet) and a 64-bit counter that never repeats
 * for a key, both little-endian, exactly as {@code nonce = le32(prefix) || le64(counter)} would be given to any other
 * RFC 8439 implementation (checked against the JDK's in {@code VoiceNetCheck}).
 */
public final class ChaCha20Poly1305 {
    public static final int KEY_BYTES = 32;
    public static final int TAG_BYTES = 16;
    private static final long M26 = 0x3ffffffL;

    /** A 256-bit key, read once into the eight little-endian words ChaCha20 works with. */
    public static final class Key {
        final int[] words = new int[8];

        public Key(byte[] key) {
            if (key.length != KEY_BYTES) {
                throw new IllegalArgumentException("ChaCha20 key must be 32 bytes");
            }
            for (int i = 0; i < 8; i++) {
                words[i] = le32(key, 4 * i);
            }
        }
    }

    private final int[] state = new int[16];
    private final byte[] stream = new byte[64];
    private final byte[] pad = new byte[16];
    private final byte[] expected = new byte[16];
    private long r0, r1, r2, r3, r4, s1, s2, s3, s4;
    private long h0, h1, h2, h3, h4;
    private long k0, k1, k2, k3;

    /**
     * Encrypts {@code buf[off, off + len)} in place and writes the 16-byte tag right after it, at {@code buf[off + len]}.
     * The associated data (authenticated, not encrypted) is {@code aad[aadOff, aadOff + aadLen)}.
     */
    public void seal(Key key, int noncePrefix, long counter, byte[] aad, int aadOff, int aadLen, byte[] buf, int off, int len) {
        setup(key, noncePrefix, (int) counter, (int) (counter >>> 32));
        xor(buf, off, len);
        mac(aad, aadOff, aadLen, buf, off, len);
        finish(buf, off + len);
    }

    /**
     * Checks the tag found at {@code buf[off + len]} and, only if it is right, decrypts {@code buf[off, off + len)} in
     * place. Returns false (buffer untouched) for a forged, damaged or misdirected packet.
     */
    public boolean open(Key key, int noncePrefix, long counter, byte[] aad, int aadOff, int aadLen, byte[] buf, int off, int len) {
        if (len < 0 || off + len + TAG_BYTES > buf.length) {
            return false;
        }
        setup(key, noncePrefix, (int) counter, (int) (counter >>> 32));
        mac(aad, aadOff, aadLen, buf, off, len);
        finish(expected, 0);
        int diff = 0;
        for (int i = 0; i < TAG_BYTES; i++) {
            diff |= expected[i] ^ buf[off + len + i];
        }
        if (diff != 0) {
            return false;
        }
        xor(buf, off, len);
        return true;
    }

    /** Same as {@link #seal} with a 12-byte nonce given as bytes (tests against reference vectors). */
    public void seal(Key key, byte[] nonce, byte[] aad, int aadOff, int aadLen, byte[] buf, int off, int len) {
        setup(key, le32(nonce, 0), le32(nonce, 4), le32(nonce, 8));
        xor(buf, off, len);
        mac(aad, aadOff, aadLen, buf, off, len);
        finish(buf, off + len);
    }

    /** One ChaCha20 block (64 bytes of key stream) for a 12-byte nonce and a block counter (tests). */
    public byte[] block(Key key, byte[] nonce, int counter) {
        setup(key, le32(nonce, 0), le32(nonce, 4), le32(nonce, 8));
        keystream(counter);
        return stream.clone();
    }

    /** Poly1305 on its own (RFC 8439 §2.5), for the reference vectors: the 32-byte one-time key and a message. */
    public byte[] poly1305(byte[] oneTimeKey, byte[] message) {
        polyInit(oneTimeKey, 0);
        int full = message.length & ~15;
        for (int i = 0; i < full; i += 16) {
            polyBlock(message, i, 1L << 24);
        }
        int rest = message.length - full;
        if (rest > 0) {
            java.util.Arrays.fill(pad, (byte) 0);
            System.arraycopy(message, full, pad, 0, rest);
            pad[rest] = 1;
            polyBlock(pad, 0, 0);
        }
        byte[] tag = new byte[16];
        finish(tag, 0);
        return tag;
    }

    // ------------------------------------------------------------------ ChaCha20

    private void setup(Key key, int n0, int n1, int n2) {
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;
        System.arraycopy(key.words, 0, state, 4, 8);
        state[12] = 0;
        state[13] = n0;
        state[14] = n1;
        state[15] = n2;
        // The Poly1305 key is the first half of block 0.
        keystream(0);
        polyInit(stream, 0);
    }

    private void xor(byte[] buf, int off, int len) {
        int counter = 1;
        for (int pos = 0; pos < len; pos += 64) {
            keystream(counter++);
            int n = Math.min(64, len - pos);
            int base = off + pos;
            for (int i = 0; i < n; i++) {
                buf[base + i] ^= stream[i];
            }
        }
    }

    private void keystream(int counter) {
        int j0 = state[0], j1 = state[1], j2 = state[2], j3 = state[3];
        int j4 = state[4], j5 = state[5], j6 = state[6], j7 = state[7];
        int j8 = state[8], j9 = state[9], j10 = state[10], j11 = state[11];
        int j12 = counter, j13 = state[13], j14 = state[14], j15 = state[15];
        int x0 = j0, x1 = j1, x2 = j2, x3 = j3, x4 = j4, x5 = j5, x6 = j6, x7 = j7;
        int x8 = j8, x9 = j9, x10 = j10, x11 = j11, x12 = j12, x13 = j13, x14 = j14, x15 = j15;
        for (int i = 0; i < 10; i++) {
            // Columns.
            x0 += x4; x12 = Integer.rotateLeft(x12 ^ x0, 16); x8 += x12; x4 = Integer.rotateLeft(x4 ^ x8, 12);
            x0 += x4; x12 = Integer.rotateLeft(x12 ^ x0, 8); x8 += x12; x4 = Integer.rotateLeft(x4 ^ x8, 7);
            x1 += x5; x13 = Integer.rotateLeft(x13 ^ x1, 16); x9 += x13; x5 = Integer.rotateLeft(x5 ^ x9, 12);
            x1 += x5; x13 = Integer.rotateLeft(x13 ^ x1, 8); x9 += x13; x5 = Integer.rotateLeft(x5 ^ x9, 7);
            x2 += x6; x14 = Integer.rotateLeft(x14 ^ x2, 16); x10 += x14; x6 = Integer.rotateLeft(x6 ^ x10, 12);
            x2 += x6; x14 = Integer.rotateLeft(x14 ^ x2, 8); x10 += x14; x6 = Integer.rotateLeft(x6 ^ x10, 7);
            x3 += x7; x15 = Integer.rotateLeft(x15 ^ x3, 16); x11 += x15; x7 = Integer.rotateLeft(x7 ^ x11, 12);
            x3 += x7; x15 = Integer.rotateLeft(x15 ^ x3, 8); x11 += x15; x7 = Integer.rotateLeft(x7 ^ x11, 7);
            // Diagonals.
            x0 += x5; x15 = Integer.rotateLeft(x15 ^ x0, 16); x10 += x15; x5 = Integer.rotateLeft(x5 ^ x10, 12);
            x0 += x5; x15 = Integer.rotateLeft(x15 ^ x0, 8); x10 += x15; x5 = Integer.rotateLeft(x5 ^ x10, 7);
            x1 += x6; x12 = Integer.rotateLeft(x12 ^ x1, 16); x11 += x12; x6 = Integer.rotateLeft(x6 ^ x11, 12);
            x1 += x6; x12 = Integer.rotateLeft(x12 ^ x1, 8); x11 += x12; x6 = Integer.rotateLeft(x6 ^ x11, 7);
            x2 += x7; x13 = Integer.rotateLeft(x13 ^ x2, 16); x8 += x13; x7 = Integer.rotateLeft(x7 ^ x8, 12);
            x2 += x7; x13 = Integer.rotateLeft(x13 ^ x2, 8); x8 += x13; x7 = Integer.rotateLeft(x7 ^ x8, 7);
            x3 += x4; x14 = Integer.rotateLeft(x14 ^ x3, 16); x9 += x14; x4 = Integer.rotateLeft(x4 ^ x9, 12);
            x3 += x4; x14 = Integer.rotateLeft(x14 ^ x3, 8); x9 += x14; x4 = Integer.rotateLeft(x4 ^ x9, 7);
        }
        put32(stream, 0, x0 + j0);
        put32(stream, 4, x1 + j1);
        put32(stream, 8, x2 + j2);
        put32(stream, 12, x3 + j3);
        put32(stream, 16, x4 + j4);
        put32(stream, 20, x5 + j5);
        put32(stream, 24, x6 + j6);
        put32(stream, 28, x7 + j7);
        put32(stream, 32, x8 + j8);
        put32(stream, 36, x9 + j9);
        put32(stream, 40, x10 + j10);
        put32(stream, 44, x11 + j11);
        put32(stream, 48, x12 + j12);
        put32(stream, 52, x13 + j13);
        put32(stream, 56, x14 + j14);
        put32(stream, 60, x15 + j15);
    }

    // ------------------------------------------------------------------ Poly1305 (26-bit limbs)

    private void polyInit(byte[] key, int o) {
        long t0 = le32(key, o) & 0xffffffffL;
        long t1 = le32(key, o + 4) & 0xffffffffL;
        long t2 = le32(key, o + 8) & 0xffffffffL;
        long t3 = le32(key, o + 12) & 0xffffffffL;
        r0 = t0 & 0x3ffffffL;
        r1 = ((t0 >>> 26) | (t1 << 6)) & 0x3ffff03L;
        r2 = ((t1 >>> 20) | (t2 << 12)) & 0x3ffc0ffL;
        r3 = ((t2 >>> 14) | (t3 << 18)) & 0x3f03fffL;
        r4 = (t3 >>> 8) & 0x00fffffL;
        s1 = r1 * 5;
        s2 = r2 * 5;
        s3 = r3 * 5;
        s4 = r4 * 5;
        h0 = h1 = h2 = h3 = h4 = 0;
        k0 = le32(key, o + 16) & 0xffffffffL;
        k1 = le32(key, o + 20) & 0xffffffffL;
        k2 = le32(key, o + 24) & 0xffffffffL;
        k3 = le32(key, o + 28) & 0xffffffffL;
    }

    private void polyBlock(byte[] m, int o, long hibit) {
        long t0 = le32(m, o) & 0xffffffffL;
        long t1 = le32(m, o + 4) & 0xffffffffL;
        long t2 = le32(m, o + 8) & 0xffffffffL;
        long t3 = le32(m, o + 12) & 0xffffffffL;
        long a0 = h0 + (t0 & M26);
        long a1 = h1 + (((t0 >>> 26) | (t1 << 6)) & M26);
        long a2 = h2 + (((t1 >>> 20) | (t2 << 12)) & M26);
        long a3 = h3 + (((t2 >>> 14) | (t3 << 18)) & M26);
        long a4 = h4 + ((t3 >>> 8) | hibit);
        long d0 = a0 * r0 + a1 * s4 + a2 * s3 + a3 * s2 + a4 * s1;
        long d1 = a0 * r1 + a1 * r0 + a2 * s4 + a3 * s3 + a4 * s2;
        long d2 = a0 * r2 + a1 * r1 + a2 * r0 + a3 * s4 + a4 * s3;
        long d3 = a0 * r3 + a1 * r2 + a2 * r1 + a3 * r0 + a4 * s4;
        long d4 = a0 * r4 + a1 * r3 + a2 * r2 + a3 * r1 + a4 * r0;
        long c = d0 >>> 26;
        h0 = d0 & M26;
        d1 += c;
        c = d1 >>> 26;
        h1 = d1 & M26;
        d2 += c;
        c = d2 >>> 26;
        h2 = d2 & M26;
        d3 += c;
        c = d3 >>> 26;
        h3 = d3 & M26;
        d4 += c;
        c = d4 >>> 26;
        h4 = d4 & M26;
        h0 += c * 5;
        c = h0 >>> 26;
        h0 &= M26;
        h1 += c;
    }

    /** Poly1305 over aad || pad16 || ciphertext || pad16 || le64(aadLen) || le64(len) (RFC 8439 §2.8). */
    private void mac(byte[] aad, int aadOff, int aadLen, byte[] buf, int off, int len) {
        padded(aad, aadOff, aadLen);
        padded(buf, off, len);
        put64(pad, 0, aadLen);
        put64(pad, 8, len);
        polyBlock(pad, 0, 1L << 24);
    }

    private void padded(byte[] data, int off, int len) {
        int full = len & ~15;
        for (int i = 0; i < full; i += 16) {
            polyBlock(data, off + i, 1L << 24);
        }
        int rest = len - full;
        if (rest > 0) {
            for (int i = 0; i < 16; i++) {
                pad[i] = i < rest ? data[off + full + i] : 0;
            }
            polyBlock(pad, 0, 1L << 24);
        }
    }

    private void finish(byte[] out, int o) {
        long c = h1 >>> 26;
        h1 &= M26;
        h2 += c;
        c = h2 >>> 26;
        h2 &= M26;
        h3 += c;
        c = h3 >>> 26;
        h3 &= M26;
        h4 += c;
        c = h4 >>> 26;
        h4 &= M26;
        h0 += c * 5;
        c = h0 >>> 26;
        h0 &= M26;
        h1 += c;
        // h - p, kept only when h >= p.
        long g0 = h0 + 5;
        c = g0 >>> 26;
        g0 &= M26;
        long g1 = h1 + c;
        c = g1 >>> 26;
        g1 &= M26;
        long g2 = h2 + c;
        c = g2 >>> 26;
        g2 &= M26;
        long g3 = h3 + c;
        c = g3 >>> 26;
        g3 &= M26;
        long g4 = h4 + c - (1L << 26);
        long mask = (g4 >>> 63) - 1;
        long f0 = (h0 & ~mask) | (g0 & mask);
        long f1 = (h1 & ~mask) | (g1 & mask);
        long f2 = (h2 & ~mask) | (g2 & mask);
        long f3 = (h3 & ~mask) | (g3 & mask);
        long f4 = (h4 & ~mask) | (g4 & mask);
        long w0 = (f0 | (f1 << 26)) & 0xffffffffL;
        long w1 = ((f1 >>> 6) | (f2 << 20)) & 0xffffffffL;
        long w2 = ((f2 >>> 12) | (f3 << 14)) & 0xffffffffL;
        long w3 = ((f3 >>> 18) | (f4 << 8)) & 0xffffffffL;
        long t = w0 + k0;
        put32(out, o, (int) t);
        t = w1 + k1 + (t >>> 32);
        put32(out, o + 4, (int) t);
        t = w2 + k2 + (t >>> 32);
        put32(out, o + 8, (int) t);
        t = w3 + k3 + (t >>> 32);
        put32(out, o + 12, (int) t);
    }

    // ------------------------------------------------------------------ bytes

    static int le32(byte[] b, int o) {
        return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8 | (b[o + 2] & 0xff) << 16 | (b[o + 3] & 0xff) << 24;
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >>> 8);
        b[o + 2] = (byte) (v >>> 16);
        b[o + 3] = (byte) (v >>> 24);
    }

    private static void put64(byte[] b, int o, long v) {
        put32(b, o, (int) v);
        put32(b, o + 4, (int) (v >>> 32));
    }
}
