package com.caracrea.voice.net;

/**
 * The voice datagrams (version 2), all numbers big-endian.
 *
 * <pre>
 * client -&gt; server : magic(1) type(1) session(8) counter(8) | encrypted payload | tag(16)
 * server -&gt; client : magic(1) type(1) counter(8)             | encrypted payload | tag(16)
 * </pre>
 *
 * The clear header is the associated data of ChaCha20-Poly1305; the nonce is the direction ({@link #NONCE_UP} or
 * {@link #NONCE_DOWN}) and the counter, which starts at 1 and never repeats for a session key. Payloads:
 * <ul>
 *   <li>HELLO: version(1) clientTime(4) padding up to {@link #HELLO_PAYLOAD}; HELLO_ACK: clientTime(4) voiceId(2)
 *   padding(2) — always shorter than what it answers, so the server can never be used to amplify traffic;</li>
 *   <li>PING: clientTime(4) lossPermille(2) bufferMs(2); PONG: clientTime(4);</li>
 *   <li>VOICE_UP: flags(1) sequence(4) opus; VOICE_DOWN: speaker voice id(2) flags(1) sequence(4) dx dy dz (3 x int16,
 *   speaker minus listener in 1/8 block) opus.</li>
 * </ul>
 *
 * <p>Version 2 (CaraCrea 1.5) : le mode de voix (chuchoter, parler, crier) passe dans les drapeaux ({@link #F_WHISPER},
 * {@link #F_SHOUT}).
 */
public final class VoiceProtocol {
    /** Taille maximale d'un paquet Opus (octets) ; reprise par VoiceCodec (client). */
    public static final int MAX_PACKET = 512;

    public static final int MAGIC = 0xC7;
    public static final int VERSION = 2;

    public static final int HELLO = 0x01;
    public static final int HELLO_ACK = 0x02;
    public static final int PING = 0x03;
    public static final int PONG = 0x04;
    public static final int BYE = 0x05;
    public static final int VOICE_UP = 0x10;
    public static final int VOICE_DOWN = 0x11;

    public static final int UP_HEADER = 18;
    public static final int DOWN_HEADER = 10;
    public static final int TAG = ChaCha20Poly1305.TAG_BYTES;
    public static final int HELLO_PAYLOAD = 48;
    public static final int VOICE_UP_FIXED = 5;
    public static final int VOICE_DOWN_FIXED = 13;
    /** Largest datagram accepted or sent. */
    public static final int MAX_DATAGRAM = 1200;
    /** Largest Opus frame accepted. */
    public static final int MAX_OPUS = 512;

    public static final int NONCE_UP = 1;
    public static final int NONCE_DOWN = 2;

    // Voice flags.
    public static final int F_WHISPER = 1;
    /** Heard through a group: from anywhere, without position. */
    public static final int F_GROUP = 2;
    /** The speaker is downed: muffled voice. */
    public static final int F_MUFFLED = 4;
    /** Heard without position (match spectators, addons). */
    public static final int F_NO_POSITION = 8;
    /** Radio effect (reserved for addons). */
    public static final int F_RADIO = 16;
    /** Le joueur crie : entendu jusqu'à la portée du cri (réglage serveur {@code porteeCri}). */
    public static final int F_SHOUT = 32;
    /** Last frame of a sentence: the listener stops this voice right after it. */
    public static final int F_END = 0x80;
    /** Flags a client may set on its own frames. */
    public static final int UP_FLAGS = F_WHISPER | F_SHOUT | F_END;

    // Modes de voix, choisis par le joueur (touche « changer de mode ») : chuchoter, parler, crier.
    public static final int MODE_WHISPER = 0;
    public static final int MODE_TALK = 1;
    public static final int MODE_SHOUT = 2;
    public static final int MODES = 3;

    /** Position offsets are sent in 1/8 block. */
    public static final float POSITION_SCALE = 8F;

    private VoiceProtocol() {
    }

    public static void putShort(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 8);
        b[o + 1] = (byte) v;
    }

    public static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    public static void putLong(byte[] b, int o, long v) {
        putInt(b, o, (int) (v >>> 32));
        putInt(b, o + 4, (int) v);
    }

    public static int getShort(byte[] b, int o) {
        return (short) ((b[o] & 0xff) << 8 | (b[o + 1] & 0xff));
    }

    public static int getUnsignedShort(byte[] b, int o) {
        return (b[o] & 0xff) << 8 | (b[o + 1] & 0xff);
    }

    public static int getInt(byte[] b, int o) {
        return (b[o] & 0xff) << 24 | (b[o + 1] & 0xff) << 16 | (b[o + 2] & 0xff) << 8 | (b[o + 3] & 0xff);
    }

    public static long getLong(byte[] b, int o) {
        return (long) getInt(b, o) << 32 | (getInt(b, o + 4) & 0xffffffffL);
    }

    /** Mode d'une trame d'après ses drapeaux ; si un client envoie les deux, le chuchotement l'emporte. */
    public static int mode(int flags) {
        return (flags & F_WHISPER) != 0 ? MODE_WHISPER : (flags & F_SHOUT) != 0 ? MODE_SHOUT : MODE_TALK;
    }

    /** Drapeau qui porte un mode (aucun pour parler normalement). */
    public static int flag(int mode) {
        return mode == MODE_WHISPER ? F_WHISPER : mode == MODE_SHOUT ? F_SHOUT : 0;
    }

    /** A position offset in blocks, as the int16 sent (clamped to about ±4096 blocks). */
    public static int quantize(double blocks) {
        long v = Math.round(blocks * POSITION_SCALE);
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
    }
}
