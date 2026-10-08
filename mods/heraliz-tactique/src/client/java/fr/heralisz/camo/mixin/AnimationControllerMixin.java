package fr.heralisz.camo.mixin;

import com.tacz.guns.api.client.animation.AnimationController;
import fr.heralisz.camo.client.SkinInspect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Inspection d'une arme skinnée : l'animation « inspect* » demandée par TaCZ est remplacée par celle du skin (SkinInspect). */
@Mixin(value = AnimationController.class, remap = false)
public abstract class AnimationControllerMixin {
   @ModifyVariable(method = "runAnimation", at = @At("HEAD"), argsOnly = true)
   private String heralisz$skinInspect(String name) {
      return SkinInspect.redirect((AnimationController) (Object) this, name);
   }
}
