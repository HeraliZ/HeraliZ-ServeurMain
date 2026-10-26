package fr.heraliz.emote.client;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayer;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** The Heraliz emote a player is playing right now, if any, and where it is in its timeline. */
public record EmotePlayback(EmoteCatalog.EmoteInfo info, KeyframeAnimation animation, IEmotePlayer player) {

    @Nullable
    public static EmotePlayback of(Player entity) {
        if (!(entity instanceof IEmotePlayerEntity<?> holder) || !holder.isPlayingEmote()) return null;
        IEmotePlayer emote = holder.getEmote();
        if (emote == null || emote.getData() == null) return null;
        EmoteCatalog.EmoteInfo info = EmoteCatalog.get(emote.getData().getUuid());
        return info == null ? null : new EmotePlayback(info, emote.getData(), emote);
    }

    /** Current tick plus the frame's partial tick, kept inside the loop. */
    public float tick(float partialTick) {
        float t = player.getTick() + partialTick;
        if (animation.isInfinite && t >= animation.endTick + 1) {
            t -= animation.endTick + 1 - animation.returnToTick;
        }
        return t;
    }
}
