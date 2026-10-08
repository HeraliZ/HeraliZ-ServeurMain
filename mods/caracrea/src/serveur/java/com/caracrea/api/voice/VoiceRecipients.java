package com.caracrea.api.voice;

import com.caracrea.voice.net.VoiceProtocol;

import java.util.UUID;

/** Who will hear the frame being routed (valid only during {@link VoiceRouter#route}). */
public interface VoiceRecipients {
    /** Heard without position, at full volume (like a group member). */
    int NO_POSITION = VoiceProtocol.F_NO_POSITION;
    /** Radio effect on the listener's side (band-limited voice). */
    int RADIO = VoiceProtocol.F_RADIO;
    /** Muffled voice. */
    int MUFFLED = VoiceProtocol.F_MUFFLED;

    /**
     * Adds a listener with these effects (merged if already there). False when the player is not online with the voice
     * chat, or has it turned off.
     */
    boolean add(UUID listener, int effects);

    void remove(UUID listener);

    boolean contains(UUID listener);

    int size();

    /** The i-th listener (0 ≤ i &lt; size()). */
    UUID get(int i);
}
