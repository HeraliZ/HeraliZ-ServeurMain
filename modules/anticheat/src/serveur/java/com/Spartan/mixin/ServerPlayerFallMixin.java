package com.Spartan.mixin;

import com.Spartan.check.movement.NoFallGuard;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NoFall : vanilla calcule les dégâts de chute d'après la déclaration « au sol » du
 * client. Une déclaration faite en plein air (rien sous le joueur) est ramenée à
 * « en l'air » : la distance de chute continue de compter et les dégâts tombent
 * normalement à l'atterrissage.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerFallMixin {
    @ModifyVariable(method = "doCheckFallDamage", at = @At("HEAD"), argsOnly = true)
    private boolean spartan$noFall(boolean onGround) {
        return onGround && NoFallGuard.groundClaimAccepted((ServerPlayer) (Object) this);
    }
}
