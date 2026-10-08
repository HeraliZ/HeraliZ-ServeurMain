package com.caracrea.voice.net;

/**
 * Adaptive jitter buffer of one incoming voice (client side): frames come in from the network in any order and at
 * uneven times, and leave at a steady 20 ms pace for the decoder.
 *
 * <ul>
 *   <li>64 preallocated slots indexed by frame number: nothing is allocated per packet.</li>
 *   <li>Jitter measured on every arrival (the estimator of RFC 3550). Automatic target =
 *   ⌈(2 × jitter + 10 ms) / 20 ms⌉ + 1 frames, plus a margin that grows when frames arrive too late and shrinks after
 *   10 calm seconds, between 2 (40 ms) and 10 (200 ms) frames. Or a fixed target.</li>
 *   <li>The delay never builds up: a buffer fuller than its target for a second skips one frame; a buffer that must
 *   grow plays one concealed frame.</li>
 *   <li>A missing frame is rebuilt from the next one's redundancy (Opus FEC) when that one is there, otherwise
 *   concealed (Opus PLC), up to 5 frames; then the voice stops. The "end of sentence" flag stops it at once.</li>
 * </ul>
 *
 * The clock is passed in (nanoseconds), so tests can run it on simulated time. Thread-safe: the network thread puts,
 * the playback thread pulls.
 */
public final class JitterBuffer {
    public static final int SLOTS = 64;
    public static final int FRAME_MS = 20;
    public static final int MIN_FRAMES = 2;
    public static final int MAX_FRAMES = 10;
    private static final int MAX_CONCEALED = 5;
    private static final long FRAME_NS = FRAME_MS * 1_000_000L;

    /** What the playback must do with the frame it pulled. */
    public static final int NONE = 0;
    /** Decode {@link Pull#data} normally. */
    public static final int DECODE = 1;
    /** The frame was lost: rebuild it from {@link Pull#data}, the next frame, with Opus FEC. */
    public static final int RECOVER = 2;
    /** The frame was lost and nothing follows yet: let the decoder conceal it. */
    public static final int CONCEAL = 3;

    /** The playback's reusable result. */
    public static final class Pull {
        public final byte[] data = new byte[VoiceProtocol.MAX_OPUS];
        public int length;
        public int flags;
        public int sequence;
        /** A concealed frame inserted to grow the buffer (nothing was lost). */
        public boolean stretch;
        /** The first frame of a new sentence (the decoder should start fresh). */
        public boolean start;
    }

    private final byte[][] data = new byte[SLOTS][VoiceProtocol.MAX_OPUS];
    private final int[] length = new int[SLOTS];
    private final int[] sequenceOf = new int[SLOTS];
    private final int[] flagsOf = new int[SLOTS];
    private final boolean[] present = new boolean[SLOTS];

    private int fixedFrames;
    private boolean playing;
    private int next;
    private int highest;
    private boolean any;
    private int concealed;
    private long firstArrival;

    // Jitter (RFC 3550): transit = arrival - sequence x 20 ms.
    private double jitterMs;
    private double lastTransitMs;
    private boolean haveTransit;
    private int margin;
    private long lastLate;
    private long lastMarginDrop;
    private int overTarget;
    private int spurtPlayed;
    private int stretchWait;

    // Statistics.
    private long received;
    private long late;
    private long recovered;
    private long concealedTotal;
    private long skipped;
    private long stretched;
    private long played;

    /** {@code fixedMs}: 0 = automatic, otherwise a fixed buffer in milliseconds. */
    public JitterBuffer(int fixedMs) {
        setFixedMs(fixedMs);
    }

    public synchronized void setFixedMs(int fixedMs) {
        fixedFrames = fixedMs <= 0 ? 0 : Math.max(1, Math.min(MAX_FRAMES, Math.round(fixedMs / (float) FRAME_MS)));
    }

    /** Network thread: a frame arrived. False when it came too late or is not usable. */
    public synchronized boolean put(int sequence, int flags, byte[] buf, int off, int len, long now) {
        if (len <= 0 || len > VoiceProtocol.MAX_OPUS) {
            return false;
        }
        received++;
        double transit = now / 1e6 - (double) sequence * FRAME_MS;
        if (haveTransit) {
            double d = Math.abs(transit - lastTransitMs);
            // A pause between two sentences is not jitter.
            if (d < 1000) {
                jitterMs += (d - jitterMs) / 16;
            }
        }
        lastTransitMs = transit;
        haveTransit = true;
        if (playing) {
            if (sequence < next) {
                late++;
                lastLate = now;
                if (margin < 4 && now - lastMarginDrop > FRAME_NS * 25) {
                    margin++;
                    lastMarginDrop = now;
                }
                return false;
            }
            if (sequence - next >= SLOTS) {
                // Far ahead (a long gap): start over from this frame.
                clear();
            }
        }
        int slot = sequence & (SLOTS - 1);
        if (present[slot] && sequenceOf[slot] == sequence) {
            return false; // duplicate
        }
        if (!any) {
            firstArrival = now;
            highest = sequence;
            any = true;
        } else if (!playing && sequence + SLOTS <= highest) {
            return false;
        }
        if (!playing && any && sequence > highest + SLOTS) {
            clear();
            firstArrival = now;
            highest = sequence;
            any = true;
        }
        System.arraycopy(buf, off, data[slot], 0, len);
        length[slot] = len;
        sequenceOf[slot] = sequence;
        flagsOf[slot] = flags;
        present[slot] = true;
        if (sequence > highest) {
            highest = sequence;
        }
        return true;
    }

    /** Frames to hold before playing, as the buffer sees it now. */
    public synchronized int targetFrames() {
        if (fixedFrames > 0) {
            return fixedFrames;
        }
        int base = (int) Math.ceil((2 * jitterMs + 10) / FRAME_MS) + 1;
        return Math.max(MIN_FRAMES, Math.min(MAX_FRAMES, base + margin));
    }

    /** Playback thread, every 20 ms: what to play for this voice. */
    public synchronized int pull(Pull out, long now) {
        if (fixedFrames == 0 && margin > 0 && now - lastLate > 10_000_000_000L && now - lastMarginDrop > 10_000_000_000L) {
            margin--;
            lastMarginDrop = now;
        }
        int target = targetFrames();
        out.stretch = false;
        out.start = false;
        if (!playing) {
            if (!any) {
                return NONE;
            }
            int oldest = oldest();
            if (oldest == Integer.MIN_VALUE) {
                any = false;
                return NONE;
            }
            int span = highest - oldest + 1;
            if (span < target && now - firstArrival < (long) target * FRAME_NS) {
                return NONE;
            }
            playing = true;
            next = oldest;
            concealed = 0;
            overTarget = 0;
            spurtPlayed = 0;
            stretchWait = 0;
            out.start = true;
        }
        int buffered = highest - next + 1;
        // Not enough margin any more (the target grew): play one concealed frame without moving on, at most every 10.
        if (stretchWait > 0) {
            stretchWait--;
        }
        if (buffered < target && spurtPlayed > 0 && stretchWait == 0 && concealed == 0 && fixedFrames == 0) {
            stretchWait = 10;
            stretched++;
            out.stretch = true;
            out.sequence = next - 1;
            out.length = 0;
            out.flags = 0;
            return CONCEAL;
        }
        // More than the target for a whole second: skip one frame (never the last one).
        if (buffered > target) {
            if (++overTarget >= 50 && buffered > 1) {
                overTarget = 0;
                drop(next);
                next++;
                skipped++;
                buffered--;
            }
        } else {
            overTarget = 0;
        }
        int slot = next & (SLOTS - 1);
        if (present[slot] && sequenceOf[slot] == next) {
            copy(slot, out);
            present[slot] = false;
            out.sequence = next;
            next++;
            concealed = 0;
            played++;
            spurtPlayed++;
            if ((out.flags & VoiceProtocol.F_END) != 0) {
                stop();
            }
            return DECODE;
        }
        // Missing: wait for nothing more if it is over.
        boolean newer = highest > next;
        if (concealed >= MAX_CONCEALED || !newer && concealed >= 2) {
            stop();
            return NONE;
        }
        int after = (next + 1) & (SLOTS - 1);
        out.sequence = next;
        next++;
        concealed++;
        concealedTotal++;
        if (present[after] && sequenceOf[after] == out.sequence + 1) {
            copy(after, out);
            out.flags &= ~VoiceProtocol.F_END;
            recovered++;
            return RECOVER;
        }
        out.length = 0;
        out.flags = 0;
        return CONCEAL;
    }

    private void copy(int slot, Pull out) {
        System.arraycopy(data[slot], 0, out.data, 0, length[slot]);
        out.length = length[slot];
        out.flags = flagsOf[slot];
    }

    private int oldest() {
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < SLOTS; i++) {
            if (present[i] && (best == Integer.MIN_VALUE || sequenceOf[i] < best)) {
                best = sequenceOf[i];
            }
        }
        return best;
    }

    private void drop(int sequence) {
        int slot = sequence & (SLOTS - 1);
        if (present[slot] && sequenceOf[slot] == sequence) {
            present[slot] = false;
        }
    }

    private void stop() {
        playing = false;
        // Whatever is older than the next frame is of no use any more.
        for (int i = 0; i < SLOTS; i++) {
            if (present[i] && sequenceOf[i] < next) {
                present[i] = false;
            }
        }
        any = oldest() != Integer.MIN_VALUE;
        if (any) {
            firstArrival = Long.MIN_VALUE / 2; // already waited: may start at once
        }
    }

    private void clear() {
        for (int i = 0; i < SLOTS; i++) {
            present[i] = false;
        }
        playing = false;
        any = false;
        concealed = 0;
    }

    public synchronized boolean playing() {
        return playing;
    }

    /** Nothing buffered and not playing: the voice can be forgotten. */
    public synchronized boolean idle() {
        return !playing && !any;
    }

    /** Current delay of this buffer in milliseconds (frames held). */
    public synchronized int delayMs() {
        if (!playing) {
            return 0;
        }
        return Math.max(0, highest - next + 1) * FRAME_MS;
    }

    public synchronized double jitterMs() {
        return jitterMs;
    }

    /** received, late, recovered by FEC, concealed (PLC + FEC), skipped, stretched, played. */
    public synchronized long[] stats() {
        return new long[]{received, late, recovered, concealedTotal, skipped, stretched, played};
    }

    /** Lost or late frames among those due, in per mille (concealed / (played + concealed)). */
    public synchronized int lossPermille() {
        long due = played + concealedTotal;
        return due == 0 ? 0 : (int) (concealedTotal * 1000 / due);
    }
}
