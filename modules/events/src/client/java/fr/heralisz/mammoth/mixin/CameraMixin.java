package fr.heralisz.mammoth.mixin;

import fr.heralisz.mammoth.client.Cinematic;
import fr.heralisz.mammoth.client.Shake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Caméra des cinématiques d'Ymirak + tremblements d'impact. */
@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   private boolean detached;

   @Shadow
   private float xRot;

   @Shadow
   private float yRot;

   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Inject(method = "setup", at = @At("TAIL"))
   private void heraliszMammoth$camera(BlockGetter level, Entity entity, boolean detachedIn, boolean mirrored, float partialTick, CallbackInfo ci) {
      if (Cinematic.active()) {
         Vec3[] cam = Cinematic.camera(partialTick);
         Vec3 dir = cam[1].subtract(cam[0]);
         this.setRotation(Cinematic.yaw(dir), Cinematic.pitch(dir));
         this.setPosition(cam[0]);
         this.detached = true;
      }
      float[] s = Shake.offset(partialTick);
      if (s != null) this.setRotation(this.yRot + s[0], this.xRot + s[1]);
   }
}
