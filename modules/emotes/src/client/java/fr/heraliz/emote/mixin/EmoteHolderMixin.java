package fr.heraliz.emote.mixin;

import fr.heraliz.emote.client.EmoteMotion;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import io.github.kosmx.emotes.main.EmoteHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Emotecraft stops the local player's emote as soon as they move. The Heraliz emotes made for
 * walking (the shopping cart...) say themselves when moving is fine: see {@link EmoteMotion}.
 */
@Mixin(value = EmoteHolder.class, remap = false)
public abstract class EmoteHolderMixin {
    @Inject(method = "canRunEmote", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heraliz$walkingEmotes(IEmotePlayerEntity<?> player, CallbackInfoReturnable<Boolean> cir) {
        if (!player.isPlayingEmote()) return;
        Boolean keep = EmoteMotion.mayKeepPlaying(player);
        if (keep != null) cir.setReturnValue(keep);
    }
}
