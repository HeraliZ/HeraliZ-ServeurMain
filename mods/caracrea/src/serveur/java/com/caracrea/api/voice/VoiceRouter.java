package com.caracrea.api.voice;

/**
 * Per-frame routing hook for addons (radios, intercoms, loudspeakers…), registered with
 * {@link CaraCreaVoice#addRouter}. Called <b>on the voice thread</b> for every 20 ms frame of every speaker, after the
 * normal rules have chosen who hears it: the router may add listeners (with effects), remove some, or leave the list as
 * it is.
 *
 * <p>Rules: be fast (a few microseconds), never touch the world or block — read only state the addon copied on the
 * server thread (each tick, into volatile fields or thread-safe maps), and do not keep the {@link VoiceFrame} or the
 * {@link VoiceRecipients} after the call (they are reused).
 *
 * <pre>{@code
 * // A radio addon: every tick it notes who carries a radio on which frequency (server thread)...
 * CaraCreaVoice.addRouter((frame, recipients) -> {
 *     Integer channel = radios.get(frame.speaker());        // ...and reads it here (ConcurrentHashMap)
 *     if (channel != null && transmitting.contains(frame.speaker())) {
 *         radios.forEach((player, other) -> {
 *             if (other.equals(channel) && !player.equals(frame.speaker())) {
 *                 recipients.add(player, VoiceRecipients.RADIO | VoiceRecipients.NO_POSITION);
 *             }
 *         });
 *     }
 * });
 * }</pre>
 */
@FunctionalInterface
public interface VoiceRouter {
    void route(VoiceFrame frame, VoiceRecipients recipients);
}
