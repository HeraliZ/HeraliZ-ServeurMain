package fr.heraliz.tactique.mixin.client;

import fr.heraliz.tactique.client.hitbox.BoitesDebogage;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F3+B en survie et en aventure (Tactique 2.40.0) : la touche est prise sans rien faire ni rien dire (rien ne bascule,
 * la touche B ne part pas en jeu, le menu F3 ne s'ouvre pas en relâchant F3). Créatif et spectateur : comme avant.
 * require = 0 : si la méthode change, F3+B bascule de nouveau le réglage, mais les boîtes restent cachées en survie
 * (BoiteBlancheJoueurMixin).
 */
@Mixin(KeyboardHandler.class)
public abstract class BoitesDebogageToucheMixin {
   @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$f3bSurvie(int touche, CallbackInfoReturnable<Boolean> cir) {
      if (touche == GLFW.GLFW_KEY_B && !BoitesDebogage.permises()) {
         cir.setReturnValue(true);
      }
   }
}
