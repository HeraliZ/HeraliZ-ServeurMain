package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.voice.VoiceActivity;
import com.caracrea.voice.VoiceCodec;
import com.caracrea.voice.net.JitterBuffer;
import com.caracrea.voice.net.VoiceProtocol;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays the voices around, on its own thread: one adaptive jitter buffer ({@link JitterBuffer}) and one decoder per
 * speaker; every 20 ms each voice is decoded (a lost frame rebuilt from the next one's redundancy, or concealed),
 * filtered (muffled when the speaker is downed, behind walls with the option, radio effect for addons), placed around
 * the listener — where its entity is, or where the server says it stands when the entity is too far to be known —
 * and handed to the sound output (3D with OpenAL when possible). Group voices are heard from anywhere, without
 * distance. The mouths of the speakers follow what is played, and a recording, if one runs, gets the same audio.
 * Nothing is allocated per frame here (the Opus decoder allocates inside).
 */
final class VoicePlayback {
    /** The listener's own voice, when testing the microphone. */
    static final UUID LOOPBACK = new UUID(0, 0);
    /** The sound test: a voice-like tone going round the listener. */
    private static final UUID TEST = new UUID(0, 1);
    private static final int FRAME = VoiceCodec.FRAME;
    /** A voice silent for this long is forgotten (its buffers and decoder freed). */
    private static final long FORGET_NS = 30_000_000_000L;
    private static final int NON_POSITIONAL = VoiceProtocol.F_GROUP | VoiceProtocol.F_NO_POSITION | VoiceProtocol.F_RADIO;

    private final Map<UUID, Stream> streams = new ConcurrentHashMap<>();
    private volatile Stream[] list = new Stream[0];
    private final Thread thread;
    private volatile boolean running = true;
    private volatile int testFrames;
    /** Kind of output in use, for the settings screen. */
    volatile String output = "";
    private volatile int lossPermille;
    /** A stream forgotten from another thread, whose sound source the playback thread must end. */
    private volatile Stream dropped;
    private volatile int bufferMs;
    private double lossAverage;

    VoicePlayback() {
        thread = new Thread(this::run, "CaraCrea voice playback");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 2);
        thread.start();
    }

    void stop() {
        running = false;
        thread.interrupt();
    }

    /** Network thread: a frame of someone's voice. {@code dx dy dz}: where they stand relative to us, 1/8 block. */
    void receive(UUID speaker, int flags, int sequence, int dx, int dy, int dz, byte[] buf, int off, int len) {
        Stream stream = streams.get(speaker);
        if (stream == null) {
            stream = add(speaker);
        }
        stream.flags = flags;
        stream.dx = dx;
        stream.dy = dy;
        stream.dz = dz;
        stream.buffer.put(sequence, flags, buf, off, len, System.nanoTime());
        stream.lastHeard = System.nanoTime();
    }

    private synchronized Stream add(UUID speaker) {
        Stream stream = streams.get(speaker);
        if (stream == null) {
            stream = new Stream(speaker, VoiceClient.settings().jitterMs());
            streams.put(speaker, stream);
            Stream[] next = Arrays.copyOf(list, list.length + 1);
            next[next.length - 1] = stream;
            list = next;
        }
        return stream;
    }

    private synchronized void forget(Stream stream) {
        if (streams.remove(stream.speaker, stream)) {
            list = streams.values().toArray(new Stream[0]);
        }
    }

    /** Microphone thread: the player's own voice, played back when testing the microphone (and only then). */
    void loopback(int sequence, int flags, byte[] data, int length) {
        if (VoiceClient.testing()) {
            receive(LOOPBACK, flags | VoiceProtocol.F_NO_POSITION, sequence, 0, 0, 0, data, 0, length);
        }
    }

    /** The microphone test ended: what is left of the loopback is dropped at once. */
    void stopLoopback() {
        Stream stream = streams.get(LOOPBACK);
        if (stream != null) {
            forget(stream);
            dropped = stream;
        }
    }

    /** Plays a three-second test sound that goes round the listener. */
    void testSound() {
        testFrames = 150;
    }

    /** Stops the test sound now. */
    void stopTest() {
        if (testFrames > 0) {
            testFrames = 1;
        }
    }

    int lossPermille() {
        return lossPermille;
    }

    int bufferMs() {
        return bufferMs;
    }

    private static final class Stream {
        final UUID speaker;
        final JitterBuffer buffer;
        final VoiceCodec.Decoder decoder = new VoiceCodec.Decoder();
        final LowPass filter = new LowPass();
        volatile int flags;
        volatile int dx;
        volatile int dy;
        volatile int dz;
        volatile long lastHeard = System.nanoTime();
        boolean playing;

        Stream(UUID speaker, int jitterMs) {
            this.speaker = speaker;
            this.buffer = new JitterBuffer(jitterMs);
        }
    }

    private void run() {
        VoiceOutput out = null;
        String openedDevice = null;
        boolean openedHrtf = false;
        JitterBuffer.Pull pull = new JitterBuffer.Pull();
        short[] pcm = new short[FRAME];
        float[] local = new float[3];
        try {
            while (running) {
                VoiceClient.Settings settings = VoiceClient.settings();
                // (Re)open the output when its device or the game's directional audio changes.
                if (out == null || !settings.outputDevice().equals(openedDevice) || settings.hrtf() != openedHrtf) {
                    if (out != null) {
                        out.close();
                    }
                    openedDevice = settings.outputDevice();
                    openedHrtf = settings.hrtf();
                    out = VoiceOutput.open(openedDevice, openedHrtf);
                    output = out.describe();
                }
                VoiceClient.Listener listener = VoiceClient.listener();
                long now = System.nanoTime();
                int deepest = 0;
                Stream gone = dropped;
                if (gone != null) {
                    dropped = null;
                    out.stop(gone.speaker);
                }
                for (Stream stream : list) {
                    stream.buffer.setFixedMs(settings.jitterMs());
                    int action = stream.buffer.pull(pull, now);
                    if (action == JitterBuffer.NONE) {
                        if (stream.playing) {
                            stream.playing = false;
                            out.end(stream.speaker);
                        }
                        if (stream.buffer.idle() && now - stream.lastHeard > FORGET_NS) {
                            forget(stream);
                        }
                        continue;
                    }
                    if (pull.start || !stream.playing) {
                        stream.decoder.reset();
                        stream.playing = true;
                    }
                    switch (action) {
                        case JitterBuffer.DECODE -> stream.decoder.decode(pull.data, pull.length, pcm);
                        case JitterBuffer.RECOVER -> stream.decoder.recover(pull.data, pull.length, pcm);
                        default -> stream.decoder.conceal(pcm);
                    }
                    if (!pull.stretch) {
                        boolean lost = action != JitterBuffer.DECODE;
                        lossAverage += ((lost ? 1000 : 0) - lossAverage) * 0.01;
                    }
                    deepest = Math.max(deepest, stream.buffer.delayMs());
                    play(out, stream, action == JitterBuffer.DECODE ? pull.flags : stream.flags, pcm, settings, listener, local);
                }
                lossPermille = (int) lossAverage;
                bufferMs = deepest;
                if (testFrames > 0) {
                    testFrames--;
                    // A buzzing vowel-like tone going once round the listener.
                    double angle = (150 - testFrames) / 150.0 * 2 * Math.PI;
                    for (int i = 0; i < pcm.length; i++) {
                        double t = ((150 - testFrames) * FRAME + i) / (double) VoiceCodec.SAMPLE_RATE;
                        pcm[i] = (short) (6000 * (Math.sin(2 * Math.PI * 220 * t) * 0.6 + Math.sin(2 * Math.PI * 660 * t) * 0.25
                                + Math.sin(2 * Math.PI * 1320 * t) * 0.15));
                    }
                    out.voice(TEST, pcm, (float) Math.sin(angle) * 3, 0, (float) -Math.cos(angle) * 3, settings.volume());
                    if (testFrames == 0) {
                        out.end(TEST);
                    }
                }
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | LinkageError e) {
            CaraCrea.LOGGER.error("Voice chat: playback stopped", e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private void play(VoiceOutput out, Stream stream, int flags, short[] pcm, VoiceClient.Settings settings, VoiceClient.Listener listener,
                      float[] local) {
        UUID speaker = stream.speaker;
        boolean self = speaker == LOOPBACK;
        boolean whisper = (flags & VoiceProtocol.F_WHISPER) != 0;
        if (!self) {
            VoiceActivity.report(speaker, pcm, whisper);
            VoiceRecorder.record(speaker, listener.names().get(speaker), pcm);
        }
        float gain = settings.volume() * (self ? 1 : VoiceVolumes.get(speaker));
        float x = 0;
        float y = 0;
        float z = 0;
        double occlusion = 0;
        if (!self && (flags & NON_POSITIONAL) == 0) {
            double[] where = listener.positions().get(speaker);
            double ox;
            double oy;
            double oz;
            if (where != null) {
                ox = where[0] - listener.x();
                oy = where[1] - listener.y();
                oz = where[2] - listener.z();
                occlusion = where[3];
            } else {
                // Not in sight of this client (beyond the entity tracking range): where the server says.
                ox = stream.dx / VoiceProtocol.POSITION_SCALE;
                oy = stream.dy / VoiceProtocol.POSITION_SCALE;
                oz = stream.dz / VoiceProtocol.POSITION_SCALE;
            }
            listener.toLocal(ox, oy, oz, local);
            x = local[0];
            y = local[1];
            z = local[2];
            float range = whisper ? listener.whisperRange() : listener.range();
            gain *= falloff((float) Math.sqrt(x * x + y * y + z * z), range);
        }
        float cutoff = LowPass.OFF;
        if ((flags & VoiceProtocol.F_MUFFLED) != 0) {
            cutoff = 1000;
            gain *= 0.8F;
        }
        if (occlusion > 0) {
            cutoff = Math.min(cutoff, (float) (5000 - 4200 * Math.min(1, (occlusion - 0.5) * 2)));
            gain *= (float) (1 - 0.5 * occlusion);
        }
        if ((flags & VoiceProtocol.F_RADIO) != 0) {
            cutoff = Math.min(cutoff, 3000);
        }
        stream.filter.apply(pcm, cutoff, (flags & VoiceProtocol.F_RADIO) != 0);
        if (gain > 0.001F) {
            out.voice(speaker, pcm, x, y, z, gain);
        }
    }

    /** Full volume up close, then fading out to nothing at the edge of the range. */
    static float falloff(float distance, float range) {
        float near = 3;
        if (distance <= near) {
            return 1;
        }
        float t = Math.min(1, (distance - near) / Math.max(1, range - near));
        return (1 - t) * (1 - t);
    }

    /**
     * Second-order low-pass (RBJ biquad) whose cut-off glides from frame to frame; off above 18 kHz. The radio effect
     * adds a high-pass at 300 Hz and a touch of saturation.
     */
    static final class LowPass {
        static final float OFF = 20_000;
        private float cutoff = OFF;
        private float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;
        private float h1;
        private float hx;

        void apply(short[] pcm, float target, boolean radio) {
            float next = cutoff + (target - cutoff) * 0.5F;
            if (Math.abs(next - target) < 50) {
                next = target;
            }
            if (next >= 18_000 && !radio) {
                cutoff = next;
                x1 = x2 = y1 = y2 = 0;
                return;
            }
            if (next != cutoff || b0 == 0) {
                double w = 2 * Math.PI * Math.min(18_000, next) / VoiceCodec.SAMPLE_RATE;
                double alpha = Math.sin(w) / (2 * 0.7071);
                double cos = Math.cos(w);
                double a0 = 1 + alpha;
                b0 = (float) ((1 - cos) / 2 / a0);
                b1 = (float) ((1 - cos) / a0);
                b2 = b0;
                a1 = (float) (-2 * cos / a0);
                a2 = (float) ((1 - alpha) / a0);
                cutoff = next;
            }
            for (int i = 0; i < pcm.length; i++) {
                float x = pcm[i];
                if (radio) {
                    // One-pole high-pass at ~300 Hz, then a soft clip.
                    float h = 0.962F * (h1 + x - hx);
                    hx = x;
                    h1 = h;
                    x = (float) (Math.tanh(h / 12000.0) * 14000);
                }
                float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                x2 = x1;
                x1 = x;
                y2 = y1;
                y1 = y;
                pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(y)));
            }
        }
    }
}
