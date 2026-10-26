package com.caracrea.api.voice;

import java.util.UUID;

/** The frame of voice being routed (read-only, valid only during {@link VoiceRouter#route}). */
public interface VoiceFrame {
    /** The player speaking. */
    UUID speaker();

    /** Whether they whisper. */
    boolean whisper();

    /** S'il crie (mode crier, CaraCrea 1.5) ; faux par défaut pour les anciennes implémentations. */
    default boolean shout() {
        return false;
    }

    /** Frame number (one every 20 ms, keeps counting through silences). */
    int sequence();

    /** Dimension of the speaker, e.g. {@code minecraft:overworld}. */
    String dimension();

    /** Eye position of the speaker at the latest server tick. */
    double x();

    double y();

    double z();
}
