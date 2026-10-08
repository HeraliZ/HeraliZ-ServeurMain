package com.Spartan.mixin;

import com.Spartan.esp.VisibilityEngine;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anti-ESP : vanilla demande à chaque joueur-cible s'il doit être envoyé à un
 * observateur (ChunkMap.TrackedEntity.updatePlayer). On ne fait que retirer : si
 * l'anti-ESP le cache, la réponse devient « non » ; sinon vanilla (et les serveurs
 * hybrides, vanish…) décide comme d'habitude. Les spectateurs ne passent pas ici
 * pour l'observateur (VisibilityEngine les ignore aussi).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void spartan$antiEsp(ServerPlayer observer, CallbackInfoReturnable<Boolean> cir) {
        if (VisibilityEngine.hides((ServerPlayer) (Object) this, observer)) {
            cir.setReturnValue(false);
        }
    }
}
