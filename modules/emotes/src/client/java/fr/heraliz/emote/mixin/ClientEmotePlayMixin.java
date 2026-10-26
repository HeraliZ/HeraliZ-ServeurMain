package fr.heraliz.emote.mixin;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.ClientRules;
import io.github.kosmx.emotes.main.network.ClientEmotePlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every emote the local player starts (Emotecraft's wheel and menu, the Heraliz menu, borrowing, keys bound to an
 * emote, {@code /emotes-client play}) goes through {@code clientStartLocalEmote}: refused while downed, in a duel or a
 * ClashWar game, combat-tagged, or not unlocked ({@link ClientRules}).
 */
@Mixin(value = ClientEmotePlay.class, remap = false)
public abstract class ClientEmotePlayMixin {
    @Inject(method = "clientStartLocalEmote(Ldev/kosmx/playerAnim/core/data/KeyframeAnimation;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heraliz$rules(KeyframeAnimation emote, CallbackInfoReturnable<Boolean> cir) {
        Component why = ClientRules.refusal(emote == null ? null : emote.getUuid());
        if (why != null) {
            ClientRules.tell(why);
            cir.setReturnValue(false);
        }
    }
}
