package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.voice.SpeechAnalyzer;
import com.caracrea.voice.VoiceActivity;
import com.caracrea.voice.VoiceCodec;
import com.caracrea.voice.VoiceGate;
import com.caracrea.voice.VoiceProcessor;
import com.caracrea.voice.net.VoiceProtocol;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * The microphone, on its own thread: reads 20 ms at a time at 48 kHz mono (OpenAL capture, the game's own audio
 * library, like the usual voice chat mod; Java Sound if OpenAL cannot open it), cleans it up ({@link VoiceProcessor}:
 * high-pass, noise suppression, automatic gain), measures it, and while the player talks (push-to-talk, or voice
 * activation with a set or automatic threshold) encodes and sends it through the {@link VoiceTransport}. The frame
 * number counts every 20 ms, sent or not, so listeners measure the network's jitter across sentences; the last frame
 * of a sentence carries the "end" flag. Nothing here touches the game thread, and nothing is allocated per frame
 * besides what the Opus codec allocates inside.
 */
final class Microphone {
    private static final int FRAME = VoiceCodec.FRAME;
    private static final float[] RATES = {48_000, 44_100, 32_000, 24_000, 16_000};
    /** Voice activation keeps sending this many frames after the voice drops (end of words). */
    private static final int HANGOVER = 20;

    private final String device;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile Source source;
    /** Loudness of the latest frame, 0..1, for the level meter; and in dBFS with the background level. */
    volatile float level;
    volatile float levelDb = -100;
    volatile float floorDb = -100;
    volatile boolean transmitting;
    /** Whether the latest frame held a voice, as automatic activation sees it. */
    volatile boolean detected;
    /** Set when no microphone could be opened. */
    volatile boolean unavailable;
    volatile String kind = "";

    Microphone(String device) {
        this.device = device == null ? "" : device;
        thread = new Thread(this::run, "CaraCrea microphone");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        thread.start();
    }

    void stop() {
        running = false;
        LockSupport.unpark(thread);
    }

    String device() {
        return device;
    }

    /** A source of 20 ms frames at 48 kHz mono. */
    private interface Source {
        /** Fills {@code frame}; false when the source stopped. */
        boolean read(short[] frame);

        void close();

        String kind();
    }

    /** Names of the capture devices, for the settings screen (OpenAL's, then Java Sound's if OpenAL lists none). */
    static List<String> devices() {
        Set<String> names = new LinkedHashSet<>();
        try {
            List<String> openal = ALUtil.getStringList(0, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            if (openal != null) {
                names.addAll(openal);
            }
        } catch (RuntimeException | LinkageError e) {
            // No OpenAL capture: Java Sound below.
        }
        if (names.isEmpty()) {
            Line.Info capture = new Line.Info(TargetDataLine.class);
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                try {
                    if (AudioSystem.getMixer(info).isLineSupported(capture)) {
                        names.add(info.getName());
                    }
                } catch (RuntimeException e) {
                    // A broken driver: skip it.
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** The chosen device through OpenAL, else through Java Sound, else the default device (none in test runs). */
    private Source open() {
        if (!VoiceGate.realDevices()) {
            return new SilentSource();
        }
        Source opened = openOpenAl(device);
        if (opened == null && !device.isEmpty()) {
            opened = openJavaSound(device);
        }
        if (opened == null) {
            opened = openOpenAl("");
        }
        if (opened == null) {
            opened = openJavaSound("");
        }
        return opened;
    }

    private void run() {
        Source opened;
        try {
            opened = open();
        } catch (RuntimeException | LinkageError e) {
            opened = null;
        }
        if (opened == null) {
            unavailable = true;
            CaraCrea.LOGGER.warn("Voice chat: no microphone could be opened{}", device.isEmpty() ? "" : " (" + device + ")");
            return;
        }
        source = opened;
        kind = opened.kind();
        CaraCrea.LOGGER.info("Voice chat: microphone through {}", kind);
        short[] frame = new short[FRAME];
        byte[] packet = new byte[VoiceCodec.MAX_PACKET];
        VoiceClient.Settings first = VoiceClient.settings();
        VoiceCodec.Encoder encoder = new VoiceCodec.Encoder(first.bitrate(), 10);
        VoiceProcessor processor = new VoiceProcessor();
        int sequence = 0;
        int hangover = 0;
        boolean wasLocal = false;
        boolean wasSent = false;
        try {
            while (running && opened.read(frame)) {
                int number = sequence++;
                VoiceClient.Settings settings = VoiceClient.settings();
                float gain = settings.micGain();
                if (gain != 1) {
                    for (int i = 0; i < FRAME; i++) {
                        frame[i] = (short) Math.max(-32768, Math.min(32767, Math.round(frame[i] * gain)));
                    }
                }
                processor.process(frame, settings.denoise(), settings.automaticGain());
                float loudness = SpeechAnalyzer.level(frame);
                level = loudness;
                levelDb = processor.levelDb();
                floorDb = processor.floorDb();
                detected = processor.voice();

                boolean voice;
                if (settings.pushToTalk()) {
                    voice = VoiceClient.pushToTalkDown();
                } else {
                    boolean heard = settings.automaticActivation() ? processor.voice() : loudness >= settings.threshold();
                    if (heard) {
                        hangover = HANGOVER;
                    }
                    voice = hangover-- > 0;
                }
                boolean test = settings.testing();
                boolean send = voice && !test && settings.canTalk();
                boolean local = voice && (test || send);
                VoiceTransport transport = VoiceClient.transport();
                // Opus redundancy only where packets can be lost (UDP).
                encoder.configure(settings.bitrate(), transport != null && transport.udpConnected() ? 10 : 0);
                if (local && !wasLocal) {
                    encoder.reset(); // a new sentence: start a fresh stream
                }
                boolean ending = !local && wasLocal;
                if (local || ending) {
                    int length = encoder.encode(frame, packet);
                    if (length > 0) {
                        boolean whisper = VoiceClient.whispering();
                        int flags = (whisper ? VoiceProtocol.F_WHISPER : 0) | (ending ? VoiceProtocol.F_END : 0);
                        boolean sent = local ? send : wasSent;
                        if (sent) {
                            if (transport != null) {
                                transport.send(number, flags, packet, length);
                            }
                            if (!ending) {
                                VoiceRecorder.record(VoiceClient.self(), VoiceClient.selfName(), frame);
                            }
                        } else {
                            VoicePlayback playback = VoiceClient.playback();
                            if (playback != null) {
                                playback.loopback(number, flags, packet, length);
                            }
                        }
                        if (!ending) {
                            VoiceActivity.reportLocal(frame, whisper);
                        }
                    }
                }
                wasLocal = local;
                wasSent = send;
                transmitting = send;
            }
        } catch (RuntimeException e) {
            unavailable = true; // also stops a microphone test in progress
            CaraCrea.LOGGER.error("Voice chat: microphone stopped", e);
        } finally {
            transmitting = false;
            opened.close();
        }
    }

    // ------------------------------------------------------------------ test runs

    /** Test runs: silence every 20 ms, no device opened. */
    private final class SilentSource implements Source {
        private long next = System.nanoTime();

        @Override
        public boolean read(short[] frame) {
            next += 20_000_000L;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            } else if (wait < -200_000_000L) {
                next = System.nanoTime();
            }
            java.util.Arrays.fill(frame, (short) 0);
            return running;
        }

        @Override
        public void close() {
        }

        @Override
        public String kind() {
            return "silent (test run)";
        }
    }

    // ------------------------------------------------------------------ OpenAL capture

    private final class OpenAlSource implements Source {
        private final long handle;

        private OpenAlSource(long handle) {
            this.handle = handle;
        }

        @Override
        public boolean read(short[] frame) {
            while (running) {
                int available = ALC10.alcGetInteger(handle, ALC11.ALC_CAPTURE_SAMPLES);
                if (ALC10.alcGetError(handle) != ALC10.ALC_NO_ERROR) {
                    return false;
                }
                if (available >= FRAME) {
                    // Far behind (the computer stalled): drop old audio to stay live.
                    while (available >= FRAME * 4) {
                        ALC11.alcCaptureSamples(handle, frame, FRAME);
                        available -= FRAME;
                    }
                    ALC11.alcCaptureSamples(handle, frame, FRAME);
                    return true;
                }
                // 48 samples per millisecond: sleep about until the frame is complete.
                long ms = Math.max(1, Math.min(5, (FRAME - available) / 48 - 1));
                LockSupport.parkNanos(ms * 1_000_000L);
            }
            return false;
        }

        @Override
        public void close() {
            try {
                ALC11.alcCaptureStop(handle);
                ALC11.alcCaptureCloseDevice(handle);
            } catch (RuntimeException | LinkageError ignored) {
                // Closing anyway.
            }
        }

        @Override
        public String kind() {
            return "OpenAL";
        }
    }

    // ------------------------------------------------------------------ Java Sound capture

    private final class JavaSoundSource implements Source {
        private final TargetDataLine line;
        private final int rate;
        private final int channels;
        private final byte[] bytes;
        private final short[] raw;

        private JavaSoundSource(TargetDataLine line) {
            this.line = line;
            AudioFormat format = line.getFormat();
            rate = Math.round(format.getSampleRate());
            channels = format.getChannels();
            raw = new short[rate / 50];
            bytes = new byte[raw.length * channels * 2];
        }

        @Override
        public boolean read(short[] frame) {
            int read = 0;
            while (read < bytes.length && running) {
                int n = line.read(bytes, read, bytes.length - read);
                if (n <= 0) {
                    return false;
                }
                read += n;
            }
            if (read < bytes.length) {
                return false;
            }
            for (int i = 0; i < raw.length; i++) {
                if (channels == 1) {
                    raw[i] = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
                } else {
                    int l = (short) ((bytes[4 * i] & 0xFF) | (bytes[4 * i + 1] << 8));
                    int r = (short) ((bytes[4 * i + 2] & 0xFF) | (bytes[4 * i + 3] << 8));
                    raw[i] = (short) ((l + r) / 2);
                }
            }
            resample(raw, frame);
            return true;
        }

        @Override
        public void close() {
            line.stop();
            line.close();
        }

        @Override
        public String kind() {
            return "Java Sound " + rate + " Hz";
        }
    }

    private Source openOpenAl(String name) {
        try {
            long handle = ALC11.alcCaptureOpenDevice(name.isEmpty() ? null : name, VoiceCodec.SAMPLE_RATE, AL10.AL_FORMAT_MONO16,
                    VoiceCodec.SAMPLE_RATE / 4);
            if (handle == 0) {
                return null;
            }
            ALC11.alcCaptureStart(handle);
            return new OpenAlSource(handle);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private Source openJavaSound(String name) {
        Mixer mixer = null;
        if (!name.isEmpty()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(name)) {
                    mixer = AudioSystem.getMixer(info);
                }
            }
            if (mixer == null) {
                return null;
            }
        }
        for (float rate : RATES) {
            for (int channels = 1; channels <= 2; channels++) {
                AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                try {
                    TargetDataLine line = (TargetDataLine) (mixer != null ? mixer.getLine(info) : AudioSystem.getLine(info));
                    // About 100 ms of buffer: enough to never drop audio, short enough to stay live.
                    line.open(format, (int) (rate / 10) * channels * 2);
                    line.start();
                    return new JavaSoundSource(line);
                } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                    // Try the next format.
                }
            }
        }
        return null;
    }

    /** Linear resampling to one 48 kHz frame. */
    private static void resample(short[] in, short[] out) {
        int n = in.length;
        if (n == out.length) {
            System.arraycopy(in, 0, out, 0, n);
            return;
        }
        float step = (n - 1) / (float) (out.length - 1);
        for (int i = 0; i < out.length; i++) {
            float pos = i * step;
            int a = (int) pos;
            int b = Math.min(n - 1, a + 1);
            float t = pos - a;
            out[i] = (short) Math.round(in[a] * (1 - t) + in[b] * t);
        }
    }
}
