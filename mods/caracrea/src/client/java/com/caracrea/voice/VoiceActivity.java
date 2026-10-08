package com.caracrea.voice;

import com.caracrea.character.FaceState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What each player is saying right now: how loud, and which mouth shape the
 * sound calls for ({@link SpeechAnalyzer}). Fed by the voice chat
 * integration from its audio threads, read by the face animator on the
 * client thread. Has no Minecraft dependency so the server can load it too.
 */
public final class VoiceActivity {
    /** Voice data older than this means the player stopped talking. */
    private static final long SILENCE_MS = 250;
    /** Frames looked at when choosing the mouth shape: the latest ~100 ms of voice. */
    private static final int HISTORY = 5;
    private static final long HISTORY_MS = 110;
    /** Simple Voice Chat's audio format. */
    private static final int SAMPLE_RATE = 48_000;

    private static final Map<UUID, Voice> VOICES = new ConcurrentHashMap<>();
    /** Set by the client so the microphone can be attributed to the local player. */
    public static volatile Supplier<UUID> localPlayer;

    private VoiceActivity() {
    }

    private static final class Voice {
        /** Loudness with a fast attack and a decay of a few frames, so polling never misses a syllable. */
        float level;
        long time;
        boolean whisper;
        final int[] mouths = new int[HISTORY];
        final float[] levels = new float[HISTORY];
        final long[] times = new long[HISTORY];
        int next;
    }

    /** Reports a frame of a player's voice (16-bit PCM, 48 kHz). Safe to call from any thread. */
    public static void report(UUID player, short[] pcm, boolean whispering) {
        if (player == null || pcm == null || pcm.length == 0) {
            return;
        }
        SpeechAnalyzer.Result result = SpeechAnalyzer.analyse(pcm, SAMPLE_RATE);
        float level = result.level();
        if (whispering) {
            level *= 0.6F;
        }
        long now = System.currentTimeMillis();
        Voice voice = VOICES.computeIfAbsent(player, key -> new Voice());
        synchronized (voice) {
            voice.level = Math.max(level, voice.level * 0.7F);
            voice.time = now;
            voice.whisper = whispering;
            voice.mouths[voice.next] = result.mouth();
            voice.levels[voice.next] = level;
            voice.times[voice.next] = now;
            voice.next = (voice.next + 1) % HISTORY;
        }
    }

    /** Reports the local player's microphone. */
    public static void reportLocal(short[] pcm, boolean whispering) {
        Supplier<UUID> supplier = localPlayer;
        if (supplier != null) {
            report(supplier.get(), pcm, whispering);
        }
    }

    /** Current loudness 0..1 of a player, 0 when silent. May be read by several animators. */
    public static float poll(UUID player) {
        Voice voice = VOICES.get(player);
        if (voice == null) {
            return 0;
        }
        synchronized (voice) {
            if (System.currentTimeMillis() - voice.time > SILENCE_MS) {
                voice.level = 0;
                return 0;
            }
            return voice.level;
        }
    }

    /**
     * The mouth shape a player's voice calls for ({@code FaceState.MOUTH_*}): the shape most
     * heard, weighted by loudness, over the latest ~100 ms, so one stray frame does not flicker.
     */
    public static int mouth(UUID player) {
        Voice voice = VOICES.get(player);
        if (voice == null) {
            return FaceState.MOUTH_REST;
        }
        float[] weights = new float[FaceState.MOUTH_SHAPES];
        long now = System.currentTimeMillis();
        synchronized (voice) {
            for (int i = 0; i < HISTORY; i++) {
                if (now - voice.times[i] <= HISTORY_MS) {
                    weights[voice.mouths[i]] += 0.05F + voice.levels[i];
                }
            }
        }
        int best = FaceState.MOUTH_REST;
        for (int i = 1; i < weights.length; i++) {
            if (weights[i] > weights[best]) {
                best = i;
            }
        }
        return best;
    }

    /** Whether the player's current speech is a whisper (for the icon above their head). */
    public static boolean isWhispering(UUID player) {
        Voice voice = VOICES.get(player);
        if (voice == null) {
            return false;
        }
        synchronized (voice) {
            return voice.whisper && System.currentTimeMillis() - voice.time <= SILENCE_MS;
        }
    }

    public static boolean isTalking(UUID player) {
        Voice voice = VOICES.get(player);
        if (voice == null) {
            return false;
        }
        synchronized (voice) {
            return System.currentTimeMillis() - voice.time <= SILENCE_MS;
        }
    }

    /** Forgets the voices of players no longer around. */
    public static void retainOnly(java.util.Set<UUID> players) {
        VOICES.keySet().retainAll(players);
    }

    public static void clear() {
        VOICES.clear();
    }
}
