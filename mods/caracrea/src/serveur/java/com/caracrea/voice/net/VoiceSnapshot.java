package com.caracrea.voice.net;

import java.util.UUID;

/**
 * Where everyone is and what they may do, copied once per tick by the server thread into plain arrays, then read by
 * the voice threads without locks. Three of them rotate ({@link Ring}): the one being written is never the one
 * published, nor the one published just before.
 */
public final class VoiceSnapshot {
    /** May be heard: alive, not muted by an operator or an addon. */
    public static final int TALK = 1;
    /** Hears nobody: voice chat turned off by the player, or deafened by an addon. */
    public static final int DEAF = 2;
    public static final int CROUCH = 4;
    /** Spectator game mode. */
    public static final int SPECTATOR = 8;
    /** Spectator of the match the player is in. */
    public static final int MATCH_SPECTATOR = 16;
    /** Downed (Reanimation). */
    public static final int DOWNED = 32;
    /** Has a working way to receive voices (UDP bound, or the game connection). */
    public static final int REACHABLE = 64;

    public int count;
    public Object[] member;
    public UUID[] uuid;
    public int[] voiceId;
    public int[] level;
    public double[] x;
    public double[] y;
    public double[] z;
    public int[] flags;
    public int[] group;
    public int[] groupType;
    public int[] match;
    public int[] team;
    /** Range imposed by an addon, NaN for the normal one. */
    public float[] range;
    public long tick;

    public VoiceSnapshot(int capacity) {
        allocate(Math.max(8, capacity));
    }

    private void allocate(int capacity) {
        member = new Object[capacity];
        uuid = new UUID[capacity];
        voiceId = new int[capacity];
        level = new int[capacity];
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        flags = new int[capacity];
        group = new int[capacity];
        groupType = new int[capacity];
        match = new int[capacity];
        team = new int[capacity];
        range = new float[capacity];
    }

    /** Makes room for {@code n} entries (the content is rewritten after this). */
    public void ensure(int n) {
        if (n > member.length) {
            allocate(Math.max(n, member.length * 2));
        }
    }

    public int capacity() {
        return member.length;
    }

    /** Sets entry {@code i} with the defaults (no group, no match, normal range). */
    public void set(int i, Object owner, UUID id, int voice, int dimension, double px, double py, double pz, int state) {
        member[i] = owner;
        uuid[i] = id;
        voiceId[i] = voice;
        level[i] = dimension;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        flags[i] = state;
        group[i] = 0;
        groupType[i] = 0;
        match[i] = 0;
        team[i] = 0;
        range[i] = Float.NaN;
    }

    /** Entry of a player, or -1. */
    public int indexOf(UUID id) {
        for (int i = 0; i < count; i++) {
            if (id.equals(uuid[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Entry of a game-side object, trying {@code hint} first (its index in the previous snapshot). */
    public int indexOf(Object owner, int hint) {
        if (hint >= 0 && hint < count && member[hint] == owner) {
            return hint;
        }
        for (int i = 0; i < count; i++) {
            if (member[i] == owner) {
                return i;
            }
        }
        return -1;
    }

    /** Triple buffer: {@link #back()} on the server thread, fill, {@link #publish}; {@link #current()} anywhere. */
    public static final class Ring {
        private final VoiceSnapshot[] ring = {new VoiceSnapshot(64), new VoiceSnapshot(64), new VoiceSnapshot(64)};
        private int written;
        private volatile VoiceSnapshot current = ring[0];

        public VoiceSnapshot back() {
            written = (written + 1) % ring.length;
            return ring[written];
        }

        public void publish(VoiceSnapshot snapshot) {
            current = snapshot;
        }

        public VoiceSnapshot current() {
            return current;
        }
    }
}
