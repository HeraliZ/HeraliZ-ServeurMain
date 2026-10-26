package com.caracrea.voice.net;

import static com.caracrea.voice.net.VoiceProtocol.F_GROUP;
import static com.caracrea.voice.net.VoiceProtocol.F_MUFFLED;
import static com.caracrea.voice.net.VoiceProtocol.F_NO_POSITION;
import static com.caracrea.voice.net.VoiceProtocol.MODE_SHOUT;
import static com.caracrea.voice.net.VoiceProtocol.MODE_WHISPER;
import static com.caracrea.voice.net.VoiceSnapshot.*;

/**
 * Who hears a frame of voice, decided on the voice threads from a {@link VoiceSnapshot}. No Minecraft here, so every
 * rule is tested on its own ({@code VoiceNetCheck}).
 *
 * <ol>
 *   <li>The speaker must be allowed to talk (alive, not muted) and, as a spectator outside a match, the spectator
 *   rule must let them.</li>
 *   <li>Listeners who turned their voice chat off, or cannot receive, are skipped.</li>
 *   <li>Matches: with {@code isolateMatches}, only players of the same match hear each other; inside a match, voice
 *   groups do not apply, spectators of the match only hear (and are heard by) spectators of the match, from anywhere
 *   in it, and {@code teamOnly} keeps each team to itself.</li>
 *   <li>Spectators outside a match: muted, heard by spectators only, or heard like everyone.</li>
 *   <li>Groups: members hear each other from anywhere; a normal or isolated group keeps its members' words inside;
 *   members of an isolated group hear nobody else.</li>
 *   <li>Otherwise: same dimension and within range (whisper range when whispering, shout range when shouting, times the
 *   crouch multiplier, or the range imposed by an addon).</li>
 * </ol>
 */
public final class VoiceRouting {
    public static final int GROUP_NORMAL = 0;
    public static final int GROUP_OPEN = 1;
    public static final int GROUP_ISOLATED = 2;

    public static final int SPECTATORS_MUTE = 0;
    public static final int SPECTATORS_AMONG_THEMSELVES = 1;
    public static final int SPECTATORS_ALL = 2;

    /** The server's voice settings, as the voice threads read them ({@code shoutRange} : portée du mode crier). */
    public record Rules(boolean enabled, float range, float whisperRange, float shoutRange, float crouch, int spectators, boolean groups,
                        boolean isolateMatches, boolean teamOnly, boolean matchSpectatorsTogether, boolean downedMuffled) {
        public static final Rules DEFAULT = new Rules(true, 48, 24, 96, 1, SPECTATORS_AMONG_THEMSELVES, true, true, false, true, true);
    }

    /** The listeners of one frame and how each hears it (voice flags); reused frame after frame, no allocation. */
    public static final class Recipients {
        private int[] index = new int[64];
        private int[] flags = new int[64];
        private int[] mark = new int[64];
        private int epoch;
        private int count;

        public void reset(int capacity) {
            if (mark.length < capacity) {
                mark = new int[Math.max(capacity, mark.length * 2)];
                index = new int[mark.length];
                flags = new int[mark.length];
            }
            epoch++;
            if (epoch == Integer.MAX_VALUE) {
                java.util.Arrays.fill(mark, 0);
                epoch = 1;
            }
            count = 0;
        }

        /** Adds a listener (by snapshot index); a listener already there keeps one entry, flags merged. */
        public void add(int listener, int voiceFlags) {
            if (listener < 0 || listener >= mark.length) {
                return;
            }
            if (mark[listener] == epoch) {
                for (int i = 0; i < count; i++) {
                    if (index[i] == listener) {
                        flags[i] |= voiceFlags;
                        return;
                    }
                }
            }
            mark[listener] = epoch;
            index[count] = listener;
            flags[count] = voiceFlags;
            count++;
        }

        public void remove(int listener) {
            if (listener < 0 || listener >= mark.length || mark[listener] != epoch) {
                return;
            }
            mark[listener] = 0;
            for (int i = 0; i < count; i++) {
                if (index[i] == listener) {
                    count--;
                    index[i] = index[count];
                    flags[i] = flags[count];
                    return;
                }
            }
        }

        public boolean contains(int listener) {
            return listener >= 0 && listener < mark.length && mark[listener] == epoch;
        }

        public int count() {
            return count;
        }

        public int listener(int i) {
            return index[i];
        }

        public int flags(int i) {
            return flags[i];
        }
    }

    private VoiceRouting() {
    }

    /**
     * Fills {@code out} with who hears {@code from}'s frame; returns how many. {@code mode} : chuchoter, parler ou crier
     * ({@code VoiceProtocol.MODE_*}), qui choisit la portée et passe dans les drapeaux de chaque auditeur.
     */
    public static int route(VoiceSnapshot s, Rules rules, int from, int mode, Recipients out) {
        out.reset(s.count);
        if (!rules.enabled() || from < 0 || from >= s.count) {
            return 0;
        }
        int f = s.flags[from];
        int match = s.match[from];
        boolean spectator = (f & SPECTATOR) != 0;
        if ((f & TALK) == 0 || match == 0 && spectator && rules.spectators() == SPECTATORS_MUTE) {
            return 0;
        }
        float modeRange = mode == MODE_WHISPER ? rules.whisperRange() : mode == MODE_SHOUT ? rules.shoutRange() : rules.range();
        float range = Float.isNaN(s.range[from]) ? modeRange : s.range[from];
        if ((f & CROUCH) != 0) {
            range *= rules.crouch();
        }
        double rangeSq = (double) range * range;
        int base = VoiceProtocol.flag(mode) | ((f & DOWNED) != 0 && rules.downedMuffled() ? F_MUFFLED : 0);
        boolean matchSpectator = (f & MATCH_SPECTATOR) != 0;
        int group = rules.groups() ? s.group[from] : 0;
        int groupType = s.groupType[from];
        for (int l = 0; l < s.count; l++) {
            if (l == from) {
                continue;
            }
            int lf = s.flags[l];
            if ((lf & DEAF) != 0 || (lf & REACHABLE) == 0) {
                continue;
            }
            int listenerMatch = s.match[l];
            if (match != 0 || listenerMatch != 0) {
                if (rules.isolateMatches() && match != listenerMatch) {
                    continue;
                }
                if (match != 0 && match == listenerMatch) {
                    boolean listenerSpectator = (lf & MATCH_SPECTATOR) != 0;
                    if (rules.matchSpectatorsTogether()) {
                        if (matchSpectator != listenerSpectator) {
                            continue;
                        }
                        if (matchSpectator) {
                            out.add(l, base | F_NO_POSITION);
                            continue;
                        }
                    }
                    if (rules.teamOnly() && !matchSpectator && s.team[from] != s.team[l]) {
                        continue;
                    }
                    if (near(s, from, l, rangeSq)) {
                        out.add(l, base);
                    }
                    continue;
                }
            }
            if (spectator && rules.spectators() == SPECTATORS_AMONG_THEMSELVES && (lf & SPECTATOR) == 0) {
                continue;
            }
            if (group != 0) {
                if (s.group[l] == group) {
                    out.add(l, base | F_GROUP);
                    continue;
                }
                if (groupType != GROUP_OPEN) {
                    continue;
                }
            }
            if (rules.groups() && s.group[l] != 0 && s.groupType[l] == GROUP_ISOLATED) {
                continue;
            }
            if (near(s, from, l, rangeSq)) {
                out.add(l, base);
            }
        }
        return out.count();
    }

    private static boolean near(VoiceSnapshot s, int a, int b, double rangeSq) {
        if (s.level[a] != s.level[b]) {
            return false;
        }
        double dx = s.x[a] - s.x[b];
        double dy = s.y[a] - s.y[b];
        double dz = s.z[a] - s.z[b];
        return dx * dx + dy * dy + dz * dz <= rangeSq;
    }
}
