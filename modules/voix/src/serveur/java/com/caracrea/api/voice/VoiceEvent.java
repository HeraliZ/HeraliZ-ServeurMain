package com.caracrea.api.voice;

import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Voice chat events, posted on {@code MinecraftForge.EVENT_BUS} from the <b>server thread</b> (at most once per tick and
 * player): subscribe with {@code @SubscribeEvent public void talk(VoiceEvent.TalkStart e)}.
 */
public abstract class VoiceEvent extends Event {
    private final UUID player;

    protected VoiceEvent(UUID player) {
        this.player = player;
    }

    public UUID getPlayer() {
        return player;
    }

    /** A player started talking (their voice reaches the server). */
    public static final class TalkStart extends VoiceEvent {
        private final boolean whisper;
        private final boolean shout;

        public TalkStart(UUID player, boolean whisper) {
            this(player, whisper, false);
        }

        /** Avec le mode crier (CaraCrea 1.5) ; l'ancien constructeur reste pour les mods qui l'appellent. */
        public TalkStart(UUID player, boolean whisper, boolean shout) {
            super(player);
            this.whisper = whisper;
            this.shout = shout && !whisper;
        }

        public boolean isWhisper() {
            return whisper;
        }

        /** Le joueur crie : sa voix porte jusqu'à {@code voix.porteeCri} (au lieu de {@code voix.portee}). */
        public boolean isShout() {
            return shout;
        }
    }

    /** A player stopped talking. */
    public static final class TalkStop extends VoiceEvent {
        public TalkStop(UUID player) {
            super(player);
        }
    }

    /** A player was muted (with an end time, 0 for no end) or unmuted by an operator, a command or an addon. */
    public static final class MuteChanged extends VoiceEvent {
        private final boolean muted;
        private final long until;
        @Nullable
        private final String reason;

        public MuteChanged(UUID player, boolean muted, long until, @Nullable String reason) {
            super(player);
            this.muted = muted;
            this.until = until;
            this.reason = reason;
        }

        public boolean isMuted() {
            return muted;
        }

        /** End of the mute (epoch milliseconds), 0 for none. */
        public long getUntil() {
            return until;
        }

        @Nullable
        public String getReason() {
            return reason;
        }
    }

    /** A player's voice connection is ready or changed: {@code "UDP"} (dedicated channel) or {@code "TCP"} (game connection). */
    public static final class Connected extends VoiceEvent {
        private final String transport;

        public Connected(UUID player, String transport) {
            super(player);
            this.transport = transport;
        }

        public String getTransport() {
            return transport;
        }
    }
}
