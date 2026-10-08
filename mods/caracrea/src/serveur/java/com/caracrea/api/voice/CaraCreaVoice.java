package com.caracrea.api.voice;

import com.caracrea.server.VoiceMutes;
import com.caracrea.server.VoiceServer;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.UUID;

/**
 * Server API of CaraCrea's voice chat, for addons (radios, admin panel…). No compile dependency on the rest of the
 * mod is needed beyond this package. Unless stated, call from the server thread.
 *
 * <ul>
 *   <li>State: {@link #isActive()}, {@link #isTalking(UUID)} (any thread), {@link #transport(UUID)}.</li>
 *   <li>Moderation (kept in the world, through restarts): {@link #mute}, {@link #unmute}, {@link #isMuted}.</li>
 *   <li>Per-player overrides (not kept): {@link #setRange}, {@link #setSilenced}, {@link #setDeafened}.</li>
 *   <li>Routing: {@link #addRouter(VoiceRouter)} — see {@link VoiceRouter}.</li>
 *   <li>Events: {@link VoiceEvent} on the Forge event bus.</li>
 * </ul>
 */
public final class CaraCreaVoice {
    private CaraCreaVoice() {
    }

    /** Whether CaraCrea's voice chat runs on this server (off while Simple Voice Chat carries the voices). */
    public static boolean isActive() {
        return VoiceServer.active();
    }

    /** Whether the player is talking right now (their voice reached the server less than 250 ms ago). Any thread. */
    public static boolean isTalking(UUID player) {
        return VoiceServer.isTalking(player);
    }

    /** {@code "UDP"}, {@code "TCP"} (game connection), or null when the player has no voice session. */
    @Nullable
    public static String transport(UUID player) {
        return VoiceServer.transport(player);
    }

    /**
     * Mutes a player for everyone: their voice is dropped by the server, they still hear. Kept in the world.
     *
     * @param duration null for no end
     */
    public static void mute(UUID player, @Nullable Duration duration, @Nullable String reason, @Nullable String by) {
        VoiceMutes.mute(player, null, duration, reason, by);
    }

    public static void unmute(UUID player) {
        VoiceMutes.unmute(player);
    }

    public static boolean isMuted(UUID player) {
        return VoiceMutes.isMuted(player);
    }

    /** Imposes the distance at which the player is heard (blocks); {@code Float.NaN} brings back the normal one. */
    public static void setRange(UUID player, float blocks) {
        VoiceServer.override(player, blocks, null, null);
    }

    /** Silences (or lets talk again) a player, without telling them nor keeping it (gag, unconscious…). */
    public static void setSilenced(UUID player, boolean silenced) {
        VoiceServer.override(player, Float.NEGATIVE_INFINITY, silenced, null);
    }

    /** The player hears nobody (or hears again). Not kept. */
    public static void setDeafened(UUID player, boolean deafened) {
        VoiceServer.override(player, Float.NEGATIVE_INFINITY, null, deafened);
    }

    /** Adds a per-frame routing hook (called on the voice thread). */
    public static void addRouter(VoiceRouter router) {
        VoiceServer.addRouter(router);
    }

    public static void removeRouter(VoiceRouter router) {
        VoiceServer.removeRouter(router);
    }
}
