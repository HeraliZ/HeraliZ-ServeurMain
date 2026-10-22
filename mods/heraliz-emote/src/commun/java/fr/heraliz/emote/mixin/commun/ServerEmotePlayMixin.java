package fr.heraliz.emote.mixin.commun;

import fr.heraliz.emote.EmoteRules;
import fr.heraliz.emote.HeralizEmote;
import io.github.kosmx.emotes.common.network.EmotePacket;
import io.github.kosmx.emotes.common.network.objects.NetData;
import io.github.kosmx.emotes.server.network.AbstractServerEmotePlay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Server side, when this jar (which holds Emotecraft) is also on the server: an emote a player starts (or that
 * {@code /emotes play} starts without forcing it) is not passed on to the other players while {@link EmoteRules} refuses
 * it; the player's own animation is stopped and they get the reason as a direct message ({@code sendSystemMessage}, shown
 * even with {@code sendCommandFeedback=false}). Emotes forced by an admin are left alone.
 */
@Mixin(value = AbstractServerEmotePlay.class, remap = false)
public abstract class ServerEmotePlayMixin {
    @Shadow(remap = false)
    protected abstract void sendForPlayer(NetData data, Object player, UUID target);

    @Inject(method = "streamEmote", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void heraliz$rules(NetData data, Object player, boolean isForced, boolean isFromPlayer, CallbackInfo ci) {
        if (isForced || !(player instanceof ServerPlayer sp) || data == null || data.emoteData == null) return;
        Component why = EmoteRules.refusal(sp, data.emoteData.getUuid());
        if (why == null) return;
        ci.cancel();
        try {
            NetData stop = new EmotePacket.Builder().configureToSendStop(data.emoteData.getUuid(), sp.getUUID()).build().data;
            // forced: Emotecraft's own "not allowed" line is left out, our message says why
            stop.isForced = true;
            sendForPlayer(stop, player, sp.getUUID());
        } catch (RuntimeException e) {
            HeralizEmote.LOGGER.warn("Could not stop the refused emote of {}", sp.getGameProfile().getName(), e);
        }
        sp.sendSystemMessage(why);
    }
}
