package fr.heralisz.n07.mixin;

import fr.heralisz.n07.client.Cinematic;
import fr.heralisz.n07.client.Shake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Caméra des cinématiques du boss + tremblements d'impact. */
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
   private void heraliszBosses$camera(BlockGetter level, Entity entity, boolean detachedIn, boolean mirrored, float partialTick, CallbackInfo ci) {
      if (fr.heralisz.n07.client.ScenePlayer.active()) {
         // scene scriptee (N07Api) : prioritaire sur tout le reste
         Vec3[] cam = fr.heralisz.n07.client.ScenePlayer.camera(partialTick);
         Vec3 dir = cam[1].subtract(cam[0]);
         this.setRotation(Cinematic.yaw(dir), Cinematic.pitch(dir));
         this.setPosition(cam[0]);
         this.detached = fr.heralisz.n07.client.ScenePlayer.detached();
      } else if (fr.heralisz.n07.dev.Harness.camera != null && !Cinematic.active()) {
         Vec3[] cam = fr.heralisz.n07.dev.Harness.camera;
         Vec3 dir = cam[1].subtract(cam[0]);
         this.setRotation(Cinematic.yaw(dir), Cinematic.pitch(dir));
         this.setPosition(cam[0]);
         this.detached = true;
      } else if (Cinematic.active()) {
         Vec3[] cam = Cinematic.camera(partialTick);
         Vec3 dir = cam[1].subtract(cam[0]);
         this.setRotation(Cinematic.yaw(dir), Cinematic.pitch(dir));
         this.setPosition(cam[0]);
         this.detached = true;
      } else if (fr.heralisz.n07.client.Ritual.cameraActive()) {
         Vec3[] cam = fr.heralisz.n07.client.Ritual.camera(partialTick);
         if (cam != null) {
            Vec3 dir = cam[1].subtract(cam[0]);
            this.setRotation(Cinematic.yaw(dir), Cinematic.pitch(dir));
            this.setPosition(cam[0]);
            this.detached = true;
         }
      }
      float[] s = Shake.offset(partialTick);
      if (s != null) this.setRotation(this.yRot + s[0], this.xRot + s[1]);
   }
}
