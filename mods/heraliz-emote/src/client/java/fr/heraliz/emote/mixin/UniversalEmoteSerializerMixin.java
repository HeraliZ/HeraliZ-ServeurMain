package fr.heraliz.emote.mixin;

import fr.heraliz.emote.client.HeralizConfig;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out the emotes bundled in Emotecraft's jar (waving, clap, crying, point, here, palm,
 * backflip, roblox_potion_dance, kazotsky_kick) when {@link HeralizConfig#hideEmotecraftEmotes()}:
 * Emotecraft reads each of them through {@code serializeInternalJson}. Optional (require 0): another
 * Emotecraft version without that method just keeps its emotes.
 */
@Mixin(value = UniversalEmoteSerializer.class, remap = false)
public abstract class UniversalEmoteSerializerMixin {
    @Inject(method = "serializeInternalJson", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void heraliz$hideBuiltinEmotes(String name, CallbackInfo ci) {
        if (HeralizConfig.hideEmotecraftEmotes()) ci.cancel();
    }
}
