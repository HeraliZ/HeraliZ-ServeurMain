package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.voice.VoiceCodec;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the voice chat as heard by this player: one WAV file per speaker
 * (yourself included), all on the same timeline so they can be laid side by
 * side in an editor. Files go to {@code voicechat_recordings/<date>/} in the
 * game folder. Only when the server allows it.
 */
public final class VoiceRecorder {
    private static volatile VoiceRecorder current;

    private final Path folder;
    private final long start = System.nanoTime();
    private final Map<UUID, Track> tracks = new ConcurrentHashMap<>();

    private VoiceRecorder(Path folder) {
        this.folder = folder;
    }

    public static boolean recording() {
        return current != null;
    }

    /** Starts or stops recording; returns the folder when a recording ends (null otherwise). */
    public static synchronized Path toggle() {
        VoiceRecorder recorder = current;
        if (recorder != null) {
            current = null;
            recorder.close();
            return recorder.folder;
        }
        Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("voicechat_recordings")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            CaraCrea.LOGGER.warn("Could not create {}", folder, e);
            return null;
        }
        current = new VoiceRecorder(folder);
        return null;
    }

    public static synchronized void stop() {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    /** A frame of someone's voice (any thread). */
    static void record(UUID speaker, String name, short[] pcm) {
        VoiceRecorder recorder = current;
        if (recorder != null) {
            recorder.write(speaker, name, pcm);
        }
    }

    private void write(UUID speaker, String name, short[] pcm) {
        Track track = tracks.computeIfAbsent(speaker, key -> Track.open(folder, name == null ? key.toString() : name));
        if (track != null) {
            long position = (System.nanoTime() - start) * VoiceCodec.SAMPLE_RATE / 1_000_000_000L;
            track.write(position, pcm);
        }
    }

    private void close() {
        tracks.values().forEach(track -> {
            if (track != null) {
                track.close();
            }
        });
        tracks.clear();
    }

    /** One speaker's WAV file: 48 kHz mono 16-bit, silence between their sentences. */
    private static final class Track {
        private final RandomAccessFile file;
        private long samples;

        private Track(RandomAccessFile file) {
            this.file = file;
        }

        static Track open(Path folder, String name) {
            try {
                String safe = name.replaceAll("[^A-Za-z0-9_.-]", "_");
                RandomAccessFile file = new RandomAccessFile(folder.resolve(safe + ".wav").toFile(), "rw");
                file.setLength(0);
                file.write(new byte[44]); // header written on close, once the length is known
                return new Track(file);
            } catch (IOException e) {
                CaraCrea.LOGGER.warn("Could not record {}", name, e);
                return null;
            }
        }

        synchronized void write(long position, short[] pcm) {
            try {
                // Silence up to where this frame belongs on the timeline.
                long gap = position - pcm.length - samples;
                if (gap > 0) {
                    byte[] silence = new byte[(int) Math.min(gap, VoiceCodec.SAMPLE_RATE * 600L) * 2];
                    file.write(silence);
                    samples += silence.length / 2;
                }
                byte[] bytes = new byte[pcm.length * 2];
                for (int i = 0; i < pcm.length; i++) {
                    bytes[2 * i] = (byte) pcm[i];
                    bytes[2 * i + 1] = (byte) (pcm[i] >> 8);
                }
                file.write(bytes);
                samples += pcm.length;
            } catch (IOException e) {
                // Disk full or similar: stop writing this track.
            }
        }

        synchronized void close() {
            try {
                long data = samples * 2;
                file.seek(0);
                file.writeBytes("RIFF");
                file.writeInt(Integer.reverseBytes((int) (36 + data)));
                file.writeBytes("WAVEfmt ");
                file.writeInt(Integer.reverseBytes(16));
                file.writeShort(Short.reverseBytes((short) 1));
                file.writeShort(Short.reverseBytes((short) 1));
                file.writeInt(Integer.reverseBytes(VoiceCodec.SAMPLE_RATE));
                file.writeInt(Integer.reverseBytes(VoiceCodec.SAMPLE_RATE * 2));
                file.writeShort(Short.reverseBytes((short) 2));
                file.writeShort(Short.reverseBytes((short) 16));
                file.writeBytes("data");
                file.writeInt(Integer.reverseBytes((int) data));
                file.close();
            } catch (IOException e) {
                CaraCrea.LOGGER.warn("Could not finish a recording", e);
            }
        }
    }
}
