package fr.heraliz.tournage.mixin;

import fr.heraliz.tournage.TournageClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Caméra libre du tournage : position et regard imposés à chaque image. */
@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Shadow
   private boolean detached;

   @Inject(method = "setup", at = @At("TAIL"))
   private void tournage$camera(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
      double[] c = TournageClient.camera(partial);
      if (c != null) {
         this.detached = true;   // le joueur filmé de l'extérieur : son corps est dessiné, pas sa main
         this.setRotation((float) c[3], (float) c[4]);
         this.setPosition(new Vec3(c[0], c[1], c[2]));
      }
   }
}
