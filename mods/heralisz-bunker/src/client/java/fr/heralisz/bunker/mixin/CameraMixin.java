package fr.heralisz.bunker.mixin;

import fr.heralisz.bunker.keycard.client.KeycardCinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pendant la cinématique de carte, la caméra suit le plan calculé par KeycardCinematic. */
@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Inject(method = "setup", at = @At("TAIL"))
   private void heralisz$keycardCinematic(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
      if (KeycardCinematic.active()) {
         Vec3[] cam = KeycardCinematic.camera(partialTick);
         Vec3 dir = cam[1].subtract(cam[0]);
         this.setRotation(KeycardCinematic.yaw(dir), KeycardCinematic.pitch(dir));
         this.setPosition(cam[0]);
      }
   }
}
