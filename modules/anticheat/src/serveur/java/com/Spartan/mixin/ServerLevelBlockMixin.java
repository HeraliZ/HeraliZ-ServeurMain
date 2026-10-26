package com.Spartan.mixin;

import com.Spartan.esp.VisibilityEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti-ESP : chaque bloc modifié et envoyé aux joueurs (porte ouverte, mur cassé,
 * bloc posé) signale les paires immobiles proches comme à recalculer.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void spartan$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags,
                                      CallbackInfo ci) {
        VisibilityEngine.onBlockChanged((ServerLevel) (Object) this, pos);
    }
}
