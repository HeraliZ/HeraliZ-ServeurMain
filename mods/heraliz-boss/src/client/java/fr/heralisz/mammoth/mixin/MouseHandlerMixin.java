package fr.heralisz.mammoth.mixin;

import fr.heralisz.mammoth.client.Cinematic;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La souris ne tourne pas le joueur pendant une cinématique (et rien ne s'accumule pour après). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
   @Shadow
   private double accumulatedDX;

   @Shadow
   private double accumulatedDY;

   @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
   private void heraliszMammoth$freeze(CallbackInfo ci) {
      if (Cinematic.active()) {
         this.accumulatedDX = 0;
         this.accumulatedDY = 0;
         ci.cancel();
      }
   }
}
