package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Animation « allongé » de TaCZ (3e personne, arme en main) calée sur le corps (14/10). TaCZ la lance dès que la pose
 * passe à « nage » hors de l'eau, alors que Minecraft couche le corps en une demi-seconde : en se couchant, la tête de
 * l'animation (penchée pour regarder devant à plat ventre) partait vers le ciel sur un corps encore debout, et pareil en
 * se relevant. Ici : allongé tant que le corps est couché à plus de moitié ({@code getSwimAmount}), dans l'eau jamais.
 * Méthode propre à TaCZ : remap = false ; sans effet si TaCZ change (require = 0).
 */
@Mixin(value = AnimationManager.class, remap = false)
public abstract class TaczAllongeAnimationMixin {
   @Inject(method = "isPlayerLie", at = @At("HEAD"), cancellable = true, require = 0)
   private static void heraliz$allongeSelonLeCorps(AbstractClientPlayer joueur, CallbackInfoReturnable<Boolean> cir) {
      if (joueur.isSwimming() || joueur.isInWater()) {
         cir.setReturnValue(false);
         return;
      }
      cir.setReturnValue(joueur.getSwimAmount(Minecraft.getInstance().getFrameTime()) > 0.5F);
   }
}
