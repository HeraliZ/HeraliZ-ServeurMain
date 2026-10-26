package fr.heralisz.camo.mixin;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import fr.heralisz.camo.client.SkinInspect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inspection d'un skin qui a sa propre scène : certaines armes (AWP du pack) jouent un seul son d'inspection calé sur leur
 * animation d'origine ; il ne suivrait plus la scène, qui porte ses propres bruitages (sound_effects de son animation).
 */
@Mixin(value = SoundPlayManager.class, remap = false)
public abstract class SoundPlayManagerMixin {
   @Inject(method = "playInspectSound", at = @At("HEAD"), cancellable = true)
   private static void heralisz$skinInspectSound(LivingEntity entity, GunDisplayInstance display, boolean noAmmo, CallbackInfo ci) {
      if (SkinInspect.hasScene(entity.getMainHandItem())) ci.cancel();
   }
}
