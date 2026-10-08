package com.caracrea.client;

import com.caracrea.CaraCreaConfig;
import com.caracrea.character.FaceState;
import com.caracrea.voice.VoiceActivity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Blinks and talking mouths. Runs once per client tick for the players near
 * the camera (plus the creator preview); only changes of the quantized
 * {@link FaceState} reach the GPU. While a player talks, the mouth takes the
 * shape of the sound heard (A, E / I, O, OU, S / F, closed between words).
 */
public final class FaceAnimator {
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** UUIDs animated even though they are not in the level (creator previews). */
    private static final Map<UUID, UUID> EXTRA = new HashMap<>();
    /** Ticks each player has spent out of animation range; their face states are freed after a while. */
    private static final Map<UUID, Integer> AWAY = new HashMap<>();
    private static final int FREE_FACES_AFTER = 30 * 20;
    private static final Random RANDOM = new Random();
    private static int cleanupTimer;
    private static boolean blinkEnabled = true;
    private static boolean voiceEnabled = true;
    private static float voiceSensitivity = 1;

    private FaceAnimator() {
    }

    private static final class State {
        int nextBlink = 40 + RANDOM.nextInt(80);
        /** Remaining ticks of the current blink, 0 when the eyes are open. */
        int blink;
        boolean doubleBlink;
        int mouth;
        int mouthHold;
    }

    /** Also animates {@code uuid}, using the voice of {@code voiceOf} (the local player for the preview). */
    public static void animateAlso(UUID uuid, UUID voiceOf) {
        EXTRA.put(uuid, voiceOf);
    }

    public static void stopAnimating(UUID uuid) {
        EXTRA.remove(uuid);
        STATES.remove(uuid);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        blinkEnabled = CaraCreaConfig.CLIENT.blink.get();
        voiceEnabled = CaraCreaConfig.CLIENT.voiceMouth.get();
        voiceSensitivity = (float) (double) CaraCreaConfig.CLIENT.voiceSensitivity.get();
        double maxDistance = CaraCreaConfig.CLIENT.animationDistance.get();
        maxDistance *= maxDistance;
        Set<UUID> seen = new HashSet<>();
        for (Player player : mc.level.players()) {
            UUID uuid = player.getUUID();
            seen.add(uuid);
            if (player.distanceToSqr(camera) > maxDistance) {
                if (STATES.remove(uuid) != null) {
                    CompositeSkins.setFaceState(uuid, FaceState.NEUTRAL);
                }
                // Out of range for a while: free its face states (a few hundred KB).
                int away = AWAY.merge(uuid, 1, Integer::sum);
                if (away == FREE_FACES_AFTER) {
                    CompositeSkins.dropFaces(uuid);
                }
                continue;
            }
            AWAY.remove(uuid);
            animate(uuid, uuid);
        }
        for (Map.Entry<UUID, UUID> extra : EXTRA.entrySet()) {
            seen.add(extra.getKey());
            animate(extra.getKey(), extra.getValue());
        }
        if (++cleanupTimer >= 100) {
            cleanupTimer = 0;
            STATES.keySet().retainAll(seen);
            AWAY.keySet().retainAll(seen);
            VoiceActivity.retainOnly(seen);
            // Players in other dimensions still show in the tab list: keep their skins too.
            if (mc.getConnection() != null) {
                seen.addAll(mc.getConnection().getOnlinePlayerIds());
            }
            CompositeSkins.retainOnly(seen);
        }
    }

    private static void animate(UUID uuid, UUID voiceOf) {
        CompositeSkins.wantFaces(uuid);
        State state = STATES.computeIfAbsent(uuid, key -> new State());
        int eyes = FaceState.EYES_OPEN;
        if (blinkEnabled) {
            if (state.blink > 0) {
                state.blink--;
                // Closing (half), closed, opening (half): about 200 ms.
                eyes = state.blink == 3 || state.blink == 0 ? FaceState.EYES_HALF
                        : state.blink > 0 ? FaceState.EYES_CLOSED : FaceState.EYES_OPEN;
                if (state.blink == 0 && state.doubleBlink) {
                    state.doubleBlink = false;
                    state.nextBlink = 4;
                }
            } else if (--state.nextBlink <= 0) {
                state.blink = 4;
                state.nextBlink = 50 + RANDOM.nextInt(110);
                state.doubleBlink = RANDOM.nextInt(6) == 0;
                eyes = FaceState.EYES_HALF;
            }
        }
        int mouth = FaceState.MOUTH_REST;
        if (voiceEnabled) {
            float level = VoiceActivity.poll(voiceOf) * voiceSensitivity;
            int target = FaceState.MOUTH_REST;
            if (level >= 0.12F) {
                // The shape of the sound heard; loudness only decides how wide an A opens.
                target = VoiceActivity.mouth(voiceOf);
                if (target == FaceState.MOUTH_OPEN || target == FaceState.MOUTH_WIDE) {
                    target = level > 0.62F ? FaceState.MOUTH_WIDE : FaceState.MOUTH_OPEN;
                }
            }
            // Each shape stays at least two ticks (closing can be immediate: M, B, P, pauses),
            // so the mouth reads as speech rather than flicker.
            if (target != state.mouth && (target == FaceState.MOUTH_REST || state.mouthHold <= 0)) {
                state.mouth = target;
                state.mouthHold = 1;
            } else {
                state.mouthHold = Math.max(0, state.mouthHold - 1);
            }
            mouth = state.mouth;
        }
        CompositeSkins.setFaceState(uuid, new FaceState(eyes, mouth));
    }

    public static void clear() {
        STATES.clear();
        EXTRA.clear();
        AWAY.clear();
    }
}
