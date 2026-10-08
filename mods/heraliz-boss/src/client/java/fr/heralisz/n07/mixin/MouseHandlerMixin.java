package fr.heralisz.n07.mixin;

import fr.heralisz.n07.client.Cinematic;
import fr.heralisz.n07.client.Ritual;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La souris ne tourne pas le joueur pendant une cinématique ou un rituel (et rien ne s'accumule pour après). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
   @Shadow
   private double accumulatedDX;

   @Shadow
   private double accumulatedDY;

   @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
   private void heraliszBosses$freeze(CallbackInfo ci) {
      if (Cinematic.active() || Ritual.localActive() || fr.heralisz.n07.client.ScenePlayer.active()) {
         this.accumulatedDX = 0;
         this.accumulatedDY = 0;
         ci.cancel();
      }
   }
}
