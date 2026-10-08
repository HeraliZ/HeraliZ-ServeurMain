package com.caracrea.mixin;

import com.caracrea.client.voice.TabIcons;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Voice icons in the player list (Tab): talking, whispering, talking in your group, own microphone muted, voice chat
 * off, muted by an operator — appended to the name the list draws, so the columns make room for them.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void caracrea$voiceIcon(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component icon = TabIcons.suffix(info.getProfile().getId());
        if (icon != null) {
            cir.setReturnValue(Component.empty().append(cir.getReturnValue()).append(icon));
        }
    }
}
