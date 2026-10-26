package com.caracrea.mixin;

import com.caracrea.client.ClientAppearances;
import com.caracrea.client.CompositeSkins;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tab list, and any mod reading skins through PlayerInfo, see the
 * painted skin too. Returning early also skips the Mojang skin download.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void caracrea$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        if (profile.getId() != null) {
            ResourceLocation skin = CompositeSkins.skinFor(profile.getId());
            if (skin != null) {
                cir.setReturnValue(skin);
            }
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void caracrea$model(CallbackInfoReturnable<String> cir) {
        if (profile.getId() != null) {
            cir.setReturnValue(ClientAppearances.modelName(profile.getId()));
        }
    }
}
