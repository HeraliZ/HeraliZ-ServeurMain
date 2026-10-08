package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.ClientConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;

public final class CinematicCamera {
   private static CameraType savedCamera;
   private static Vec3 pos;
   private static Vec3 target;
   private static int shot = -1;
   private static long lastNanos;
   private static float fovWanted;
   private static float fovNow;

   private CinematicCamera() {
   }

   static void tick(Minecraft mc) {
      ExecutionScenes.Scene s = ExecutionScenes.local();
      boolean on = s != null && (Boolean)ClientConfig.EXECUTION_CAMERA.get();
      if (on) {
         if (savedCamera == null) {
            savedCamera = mc.options.getCameraType();
            if (savedCamera.isFirstPerson()) {
               mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
         }
      } else if (savedCamera != null) {
         mc.options.setCameraType(savedCamera);
         reset();
      }
   }

   static void reset() {
      savedCamera = null;
      pos = null;
      target = null;
      shot = -1;
      lastNanos = 0L;
      fovWanted = 0.0F;
      fovNow = 0.0F;
   }

   static float fov() {
      return savedCamera != null && fovWanted > 0.0F ? fovNow : 0.0F;
   }

   static void onCameraAngles(ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      if (savedCamera != null && CameraAccess.available() && mc.player != null && mc.level != null && !mc.options.getCameraType().isFirstPerson()) {
         ExecutionScenes.Scene s = ExecutionScenes.local();
         if (s != null) {
            CoupDeGrace.Shot sh = s.choreo().shot(s.state((float)event.getPartialTick()));
            Vec3 wantPos = s.world(sh.pos);
            Vec3 wantTarget = s.world(sh.target);
            long now = System.nanoTime();
            float dt = lastNanos == 0L ? 0.05F : Mth.clamp((float)(now - lastNanos) / 1.0E9F, 0.0F, 0.1F);
            lastNanos = now;
            float base = ((Integer)mc.options.fov().get()).intValue();
            float wantFov = sh.fov > 0.0F ? sh.fov : base;
            if (pos == null || sh.index != shot && sh.cut) {
               pos = wantPos;
               target = wantTarget;
               fovNow = wantFov;
            } else {
               double a = 1.0 - Math.exp(-sh.speed * dt);
               pos = pos.lerp(wantPos, a);
               target = target.lerp(wantTarget, a);
               fovNow = Mth.lerp((float)a, fovNow, wantFov);
            }

            fovWanted = wantFov;
            shot = sh.index;
            Vec3 at = pos;
            BlockHitResult hit = mc.level.clip(new ClipContext(target, pos, Block.VISUAL, Fluid.NONE, mc.player));
            if (hit.getType() != Type.MISS) {
               at = hit.getLocation().add(target.subtract(hit.getLocation()).normalize().scale(0.2));
            }

            CameraAccess.move(event.getCamera(), at);
            Vec3 d = target.subtract(at);
            double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
            event.setYaw((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            event.setPitch((float)Mth.clamp(-Math.toDegrees(Math.atan2(d.y, horizontal)), -89.0, 89.0));
            event.setRoll(0.0F);
         }
      }
   }
}
