package com.caracrea.mixin;

import com.caracrea.client.ClientAppearances;
import com.caracrea.client.CompositeSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every player (including the creator's preview dummy, which has no
 * PlayerInfo) renders with the painted skin and the model of its body type.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void caracrea$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = CompositeSkins.skinFor(((Player) (Object) this).getUUID());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void caracrea$model(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(ClientAppearances.modelName(((Player) (Object) this).getUUID()));
    }
}
