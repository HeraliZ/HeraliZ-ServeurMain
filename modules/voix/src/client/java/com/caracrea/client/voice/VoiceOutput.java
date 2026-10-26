package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.voice.StereoMixer;
import com.caracrea.voice.VoiceCodec;
import com.caracrea.voice.VoiceGate;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.EnumerateAllExt;
import org.lwjgl.openal.SOFTHRTF;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where the voices are played, frame by frame (20 ms), from the playback
 * thread. Positions are in the listener's space, in blocks: x to the right,
 * y up, z behind.
 *
 * <ul>
 *   <li>{@link OpenAl}: true 3D sound with OpenAL, the game's own audio
 *   library, on a device and context of our own, current on the playback
 *   thread only, so the game's sounds are not touched. Each voice is a
 *   source placed around the listener: in front, behind, above, below; with
 *   the game's "Directional Audio" option, through HRTF like the game's
 *   sounds.</li>
 *   <li>{@link JavaSound}: stereo (left / right) mixing, when OpenAL cannot
 *   open the device.</li>
 *   <li>{@link Silent}: no sound device at all; voices are still decoded on
 *   time so mouths keep moving.</li>
 * </ul>
 */
abstract class VoiceOutput {
    abstract void voice(UUID id, short[] pcm, float x, float y, float z, float gain);

    /** Ends the frame; returns when the next one is due. */
    abstract void flush() throws InterruptedException;

    /** A voice has stopped: its resources can go once it has played out. */
    void end(UUID id) {
    }

    /** A voice must stop now, without playing what is queued (end of the microphone test). */
    void stop(UUID id) {
        end(id);
    }

    abstract void close();

    abstract String describe();

    /** The best output that opens: 3D, then stereo, then silent (always silent in test runs: no real device). */
    static VoiceOutput open(String device, boolean hrtf) {
        if (!VoiceGate.realDevices()) {
            return new Silent();
        }
        try {
            VoiceOutput al = OpenAl.open(device, hrtf);
            if (al != null) {
                return al;
            }
        } catch (RuntimeException | LinkageError e) {
            CaraCrea.LOGGER.warn("Voice chat: OpenAL output unavailable ({})", e.toString());
        }
        VoiceOutput java = JavaSound.open();
        return java != null ? java : new Silent();
    }

    /** Names of the sound output devices, for the settings screen. */
    static List<String> devices() {
        try {
            List<String> names = ALC10.alcIsExtensionPresent(0, "ALC_ENUMERATE_ALL_EXT")
                    ? ALUtil.getStringList(0, EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER)
                    : ALUtil.getStringList(0, ALC10.ALC_DEVICE_SPECIFIER);
            return names == null ? List.of() : names;
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    /** Waits until the next 20 ms frame is due. */
    static final class Clock {
        private long next = System.nanoTime();

        void tick() throws InterruptedException {
            next += 20_000_000L;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } else if (wait < -200_000_000L) {
                next = System.nanoTime(); // fell far behind (the computer was busy): catch up
            }
        }
    }

    // ------------------------------------------------------------------ OpenAL

    static final class OpenAl extends VoiceOutput {
        /** Buffers queued per voice before it starts (40 ms), and at most (drift guard). */
        private static final int START = 2;
        private static final int MAX_QUEUED = 8;

        private final long device;
        private final long context;
        private final boolean hrtf;
        private final Map<UUID, Source> sources = new HashMap<>();
        private final ShortBuffer data = BufferUtils.createShortBuffer(VoiceCodec.FRAME);
        private final Clock clock = new Clock();

        private OpenAl(long device, long context, boolean hrtf) {
            this.device = device;
            this.context = context;
            this.hrtf = hrtf;
        }

        static OpenAl open(String name, boolean hrtf) {
            long device = ALC10.alcOpenDevice(name == null || name.isEmpty() ? null : name);
            if (device == 0) {
                return null;
            }
            ALCCapabilities caps = ALC.createCapabilities(device);
            if (!caps.ALC_EXT_thread_local_context) {
                // Without a context of our own on this thread, we would disturb the game's sounds.
                ALC10.alcCloseDevice(device);
                return null;
            }
            IntBuffer attributes = BufferUtils.createIntBuffer(5);
            attributes.put(ALC10.ALC_FREQUENCY).put(VoiceCodec.SAMPLE_RATE);
            if (caps.ALC_SOFT_HRTF) {
                attributes.put(SOFTHRTF.ALC_HRTF_SOFT).put(hrtf ? ALC10.ALC_TRUE : ALC10.ALC_FALSE);
            }
            attributes.put(0).flip();
            long context = ALC10.alcCreateContext(device, attributes);
            if (context == 0) {
                ALC10.alcCloseDevice(device);
                return null;
            }
            EXTThreadLocalContext.alcSetThreadContext(context);
            AL.createCapabilities(caps);
            // Distance is handled by our own falloff; OpenAL only places the voice around the listener.
            AL10.alDistanceModel(AL10.AL_NONE);
            CaraCrea.LOGGER.info("Voice chat: 3D output on {}{}", ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER),
                    hrtf && caps.ALC_SOFT_HRTF ? " (HRTF)" : "");
            return new OpenAl(device, context, hrtf && caps.ALC_SOFT_HRTF);
        }

        private final class Source {
            final int id = AL10.alGenSources();
            final Deque<Integer> free = new ArrayDeque<>();
            int buffers;
            boolean ended;

            Source() {
                AL10.alSourcei(id, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                // A starved source must stop and stay quiet, never repeat what it played.
                AL10.alSourcei(id, AL10.AL_LOOPING, AL10.AL_FALSE);
                AL10.alSourcef(id, AL10.AL_MAX_GAIN, 4);
                AL10.alSourcef(id, AL10.AL_ROLLOFF_FACTOR, 0);
            }

            void recycle() {
                int processed = AL10.alGetSourcei(id, AL10.AL_BUFFERS_PROCESSED);
                for (int i = 0; i < processed; i++) {
                    free.push(AL10.alSourceUnqueueBuffers(id));
                }
            }

            int queued() {
                return AL10.alGetSourcei(id, AL10.AL_BUFFERS_QUEUED);
            }

            boolean playing() {
                return AL10.alGetSourcei(id, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
            }

            void delete() {
                AL10.alSourceStop(id);
                recycle();
                AL10.alDeleteSources(id);
                while (!free.isEmpty()) {
                    AL10.alDeleteBuffers(free.pop());
                }
            }
        }

        @Override
        void voice(UUID id, short[] pcm, float x, float y, float z, float gain) {
            Source source = sources.get(id);
            if (source == null) {
                source = new Source();
                sources.put(id, source);
            }
            source.ended = false;
            source.recycle();
            int queued = source.queued();
            if (queued >= MAX_QUEUED) {
                return; // the sound card runs a little slower than our clock: drop a frame
            }
            int buffer;
            if (!source.free.isEmpty()) {
                buffer = source.free.pop();
            } else {
                buffer = AL10.alGenBuffers();
                source.buffers++;
            }
            data.clear();
            data.put(pcm).flip();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, VoiceCodec.SAMPLE_RATE);
            AL10.alSourceQueueBuffers(source.id, buffer);
            AL10.alSource3f(source.id, AL10.AL_POSITION, x, y, z);
            AL10.alSourcef(source.id, AL10.AL_GAIN, gain);
            if (!source.playing() && queued + 1 >= START) {
                AL10.alSourcePlay(source.id);
            }
        }

        @Override
        void end(UUID id) {
            Source source = sources.get(id);
            if (source != null) {
                source.ended = true;
            }
        }

        @Override
        void stop(UUID id) {
            Source source = sources.remove(id);
            if (source != null) {
                source.delete();
            }
        }

        @Override
        void flush() throws InterruptedException {
            Iterator<Source> it = sources.values().iterator();
            while (it.hasNext()) {
                Source source = it.next();
                if (source.ended && !source.playing()) {
                    source.delete();
                    it.remove();
                }
            }
            clock.tick();
        }

        @Override
        void close() {
            for (Source source : sources.values()) {
                source.delete();
            }
            sources.clear();
            EXTThreadLocalContext.alcSetThreadContext(0);
            ALC10.alcDestroyContext(context);
            ALC10.alcCloseDevice(device);
        }

        @Override
        String describe() {
            return hrtf ? "3D (HRTF)" : "3D";
        }
    }

    // ------------------------------------------------------------------ Java Sound

    static final class JavaSound extends VoiceOutput {
        private final SourceDataLine line;
        private final StereoMixer mixer = new StereoMixer(VoiceCodec.FRAME);
        private final byte[] out = new byte[VoiceCodec.FRAME * 4];

        private JavaSound(SourceDataLine line) {
            this.line = line;
        }

        static JavaSound open() {
            AudioFormat format = new AudioFormat(VoiceCodec.SAMPLE_RATE, 16, 2, true, false);
            try {
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format, VoiceCodec.FRAME * 4 * 4);
                line.start();
                CaraCrea.LOGGER.info("Voice chat: stereo output");
                return new JavaSound(line);
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                CaraCrea.LOGGER.warn("Voice chat: no audio output could be opened ({}); mouths still move", e.toString());
                return null;
            }
        }

        @Override
        void voice(UUID id, short[] pcm, float x, float y, float z, float gain) {
            mixer.add(pcm, x, z, gain);
        }

        @Override
        void flush() throws InterruptedException {
            if (!mixer.any() && line.available() >= line.getBufferSize() - out.length) {
                Thread.sleep(10); // nothing to play, nothing queued
                return;
            }
            mixer.render(out); // silence when nobody spoke: nothing is ever played twice
            line.write(out, 0, out.length); // blocks while full: paces the loop
        }

        @Override
        void close() {
            line.stop();
            line.close();
        }

        @Override
        String describe() {
            return "stereo";
        }
    }

    // ------------------------------------------------------------------ none

    static final class Silent extends VoiceOutput {
        private final Clock clock = new Clock();

        @Override
        void voice(UUID id, short[] pcm, float x, float y, float z, float gain) {
        }

        @Override
        void flush() throws InterruptedException {
            clock.tick();
        }

        @Override
        void close() {
        }

        @Override
        String describe() {
            return "none";
        }
    }

    static List<String> withDefault(List<String> names) {
        List<String> all = new ArrayList<>();
        all.add("");
        all.addAll(names);
        return all;
    }
}
