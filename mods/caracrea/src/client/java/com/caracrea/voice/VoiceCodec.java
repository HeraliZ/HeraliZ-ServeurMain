package com.caracrea.voice;

import com.caracrea.shaded.concentus.OpusApplication;
import com.caracrea.shaded.concentus.OpusDecoder;
import com.caracrea.shaded.concentus.OpusEncoder;
import com.caracrea.shaded.concentus.OpusException;
import com.caracrea.shaded.concentus.OpusSignal;

/**
 * Voice compression for the built-in voice chat: Opus (the codec of most
 * voice chats), through Concentus, a pure-Java port shipped inside the mod,
 * so there is no native library to install. 48 kHz mono, 20 ms frames,
 * 24 kbit/s by default (set by the server): a frame of speech is 50 to 70 bytes.
 *
 * <p>Opus' own redundancy (in-band FEC) is on when the voice travels over UDP, so a lost packet is rebuilt from the
 * next one, and off over the game connection, which loses nothing. Encoding and decoding work in the caller's buffers.
 */
public final class VoiceCodec {
    public static final int SAMPLE_RATE = 48_000;
    /** Samples in one 20 ms frame. */
    public static final int FRAME = 960;
    /** Largest encoded frame accepted on the network. */
    public static final int MAX_PACKET = com.caracrea.voice.net.VoiceProtocol.MAX_PACKET;
    public static final int DEFAULT_BITRATE = 24_000;

    private VoiceCodec() {
    }

    /** One speaker's encoder (not thread-safe; one per microphone). */
    public static final class Encoder {
        private final OpusEncoder opus;
        private final byte[] buffer = new byte[MAX_PACKET];
        private int bitrate = -1;
        private int loss = -1;

        public Encoder() {
            this(DEFAULT_BITRATE, 5);
        }

        public Encoder(int bitrate, int expectedLossPercent) {
            try {
                opus = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            } catch (OpusException e) {
                throw new IllegalStateException(e);
            }
            opus.setComplexity(5);
            opus.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            configure(bitrate, expectedLossPercent);
        }

        /**
         * Bit rate (bit/s) and the packet loss to prepare for (percent): with a loss above zero, Opus adds redundancy
         * so a lost packet can be partly rebuilt from the next one.
         */
        public void configure(int bitrate, int expectedLossPercent) {
            int rate = Math.max(8_000, Math.min(96_000, bitrate));
            if (rate != this.bitrate) {
                opus.setBitrate(rate);
                this.bitrate = rate;
            }
            int lossPercent = Math.max(0, Math.min(30, expectedLossPercent));
            if (lossPercent != loss) {
                opus.setUseInbandFEC(lossPercent > 0);
                opus.setPacketLossPercent(lossPercent);
                loss = lossPercent;
            }
        }

        /** Encodes one frame of {@link #FRAME} samples into {@code out}; returns the length, or -1. */
        public int encode(short[] pcm, byte[] out) {
            try {
                return opus.encode(pcm, 0, FRAME, out, 0, Math.min(out.length, MAX_PACKET));
            } catch (OpusException | RuntimeException e) {
                return -1;
            }
        }

        /** Encodes one frame into a new array (tests and tools). */
        public byte[] encode(short[] pcm) {
            int length = encode(pcm, buffer);
            if (length < 0) {
                return null;
            }
            byte[] packet = new byte[length];
            System.arraycopy(buffer, 0, packet, 0, length);
            return packet;
        }

        /** Starts a new stream (after a pause), so the next frames do not depend on old ones. */
        public void reset() {
            opus.resetState();
        }
    }

    /** One incoming voice's decoder (not thread-safe; one per speaker). */
    public static final class Decoder {
        private final OpusDecoder opus;

        public Decoder() {
            try {
                opus = new OpusDecoder(SAMPLE_RATE, 1);
            } catch (OpusException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Decodes {@code packet[0, length)} into {@code pcm} ({@link #FRAME} samples). */
        public boolean decode(byte[] packet, int length, short[] pcm) {
            try {
                opus.decode(packet, 0, length, pcm, 0, FRAME, false);
                return true;
            } catch (OpusException | RuntimeException e) {
                java.util.Arrays.fill(pcm, (short) 0);
                return false;
            }
        }

        /** Decodes a frame into {@code pcm}; a null packet conceals a lost frame. */
        public boolean decode(byte[] packet, short[] pcm) {
            if (packet == null) {
                return conceal(pcm);
            }
            return decode(packet, packet.length, pcm);
        }

        /** Lets Opus make up a lost frame from what came before (packet loss concealment). */
        public boolean conceal(short[] pcm) {
            try {
                opus.decode(null, 0, 0, pcm, 0, FRAME, false);
                return true;
            } catch (OpusException | RuntimeException e) {
                java.util.Arrays.fill(pcm, (short) 0);
                return false;
            }
        }

        /** Rebuilds a lost frame from the redundancy carried by the packet after it ({@code next[0, length)}). */
        public boolean recover(byte[] next, int length, short[] pcm) {
            try {
                opus.decode(next, 0, length, pcm, 0, FRAME, true);
                return true;
            } catch (OpusException | RuntimeException e) {
                return conceal(pcm);
            }
        }

        public boolean recover(byte[] next, short[] pcm) {
            return recover(next, next.length, pcm);
        }

        public void reset() {
            opus.resetState();
        }
    }
}
