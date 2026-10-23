package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.client.gameplay.LocalPlayerInspect;
import fr.heraliz.tactique.client.inspection.SonsInspection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inspection d'une arme ou d'un couteau ({@link SonsInspection}) : les sons de l'inspection précédente sont coupés avant
 * la nouvelle, et un appui trop rapproché est ignoré. Noms de TaCZ (non obfusqués) : remap = false.
 */
@Mixin(value = LocalPlayerInspect.class, remap = false)
public abstract class LocalPlayerInspectMixin {
   @Inject(method = "inspect", at = @At("HEAD"), cancellable = true)
   private void heraliz$inspectionUnique(CallbackInfo ci) {
      if (!SonsInspection.nouvelle()) {
         ci.cancel();
      }
   }
}
