package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class FrappeOrbitale extends Spectacle {
   static final FrappeOrbitale INSTANCE = new FrappeOrbitale();
   static final int CALL = 12;
   static final int LOCK = 24;
   static final int POINT = 34;
   static final int TURN = 42;
   static final int STRIKE = 62;
   static final int LAND = 80;
   static final int END = 116;
   private static final float BACK = 2.4F;
   private static final float AWAY = 3.35F;

   private FrappeOrbitale() {
      super(ExecutionType.FRAPPE_ORBITALE);
   }

   @Override
   public float reach() {
      return 1.2F;
   }

   @Override
   float endFwd() {
      return 3.5F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   private static float stride(float t) {
      return t * (float) (Math.PI / 6);
   }

   private static float backwards(float t) {
      return window(t, 0.0F, 12.0F, 2.0F);
   }

   private static float away(float t) {
      return window(t, 46.0F, 60.0F, 2.0F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float fwd = t < 46.0F ? Mth.lerp(smooth(phase(t, 0.0F, 12.0F)), 1.2F, 2.4F) : Mth.lerp(smooth(phase(t, 46.0F, 60.0F)), 2.4F, 3.35F);
      fwd += 0.15F * smooth(phase(t, 62.0F, 67.0F));
      float up = 0.025F * Mth.abs(Mth.sin(stride(t))) * Math.max(backwards(t), away(t));
      s.actorPoint.set(at(fwd, 0.0F, up));
      s.actorYaw = 180.0F - 180.0F * smooth(phase(t, 42.0F, 48.0F));
      s.lean = -0.08F * window(t, 12.0F, 34.0F, 4.0F) + 0.06F * away(t) + 0.25F * window(t, 62.0F, 76.0F, 3.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 26.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 62.0F) {
         float k = smooth(phase(t, 26.0F, 34.0F));
         float shake = 1.5F * Mth.sin(t * 2.1F) * smooth(phase(t, 40.0F, 62.0F));
         v.turn(90.0F * s.side, -90.0F + shake, 0.0F, -180.0F * s.side * k)
            .anchor(1.0556251F, at(0.0F, 0.057F * s.side * (1.0F - k), Mth.lerp(k, 0.3F, 0.15F)));
      } else {
         float h;
         if (t < 70.0F) {
            h = 3.05F * out(phase(t, 62.0F, 70.0F), 2.0F);
         } else if (t < 80.0F) {
            h = 3.05F * (1.0F - in(phase(t, 70.0F, 80.0F), 2.0F));
         } else {
            h = 0.16F * Mth.sin((float) Math.PI * phase(t, 80.0F, 86.0F));
         }

         float flip = 360.0F * smooth(phase(t, 62.0F, 80.0F));
         v.turn(90.0F * s.side, -90.0F + flip, 0.0F, -180.0F * s.side).anchor(1.0556251F, at(0.0F, 0.0F, 0.15F + h));
      }
   }

   private static Vector3f laser(float t) {
      float loose = 1.0F - smooth(phase(t, 24.0F, 40.0F));
      return at(0.9F * loose * Mth.sin(t * 0.33F), 0.9F * loose * Mth.cos(t * 0.27F), 0.02F);
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      walk(m, stride(t), backwards(t), -1.0F);
      walk(m, stride(t), away(t), 1.0F);
      Vector3f body = s.actor.model(s.victimChest());
      float raise = smooth(phase(t, 12.0F, 20.0F)) * (1.0F - smooth(phase(t, 34.0F, 39.0F)));
      if (raise > 0.0F) {
         m.rightArm.xRot = Mth.lerp(raise, m.rightArm.xRot, -3.0F + 0.04F * Mth.sin(age * 2.2F));
         m.rightArm.zRot = Mth.lerp(raise, m.rightArm.zRot, 0.12F);
      }

      hold(m.rightArm, body, smooth(phase(t, 34.0F, 39.0F)) * (1.0F - smooth(phase(t, 42.0F, 47.0F))));
      float fist = window(t, 12.0F, 42.0F, 4.0F);
      m.leftArm.xRot = Mth.lerp(fist, m.leftArm.xRot, 0.12F);
      m.leftArm.zRot = Mth.lerp(fist, m.leftArm.zRot, -0.18F);
      float blast = window(t, 62.0F, 74.0F, 3.0F);
      m.rightArm.xRot += 0.5F * blast;
      m.leftArm.xRot += 0.5F * blast;
      m.rightArm.zRot += 0.3F * blast;
      m.leftArm.zRot -= 0.3F * blast;
      float collar = window(t, 100.0F, 112.0F, 3.0F);
      m.rightArm.xRot = Mth.lerp(collar, m.rightArm.xRot, -1.45F);
      m.rightArm.zRot = Mth.lerp(collar, m.rightArm.zRot, -0.5F);
      m.leftArm.xRot = Mth.lerp(collar, m.leftArm.xRot, -1.45F);
      m.leftArm.zRot = Mth.lerp(collar, m.leftArm.zRot, 0.5F);
      if (t < 45.0F) {
         look(m.head, s.actor.model(s.victim.axis(1.6415626F)), 1.0F, 0.9F);
      }

      float sky = window(t, 12.0F, 36.0F, 4.0F);
      m.head.xRot = Mth.lerp(sky, m.head.xRot, -0.8F);
      m.head.yRot *= 1.0F - sky;
      float over = window(t, 84.0F, 98.0F, 4.0F);
      m.head.yRot = Mth.lerp(over, m.head.yRot, 1.1F * s.side);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 26.0F) {
         lyingPose(m);
         float stroke = Mth.sin(age * 0.35F) * window(t, 4.0F, 24.0F, 3.0F);
         m.rightArm.zRot += 0.35F * stroke;
         m.leftArm.zRot += 0.35F * stroke;
      } else if (t < 62.0F) {
         float k = smooth(phase(t, 26.0F, 34.0F));
         lyingPose(m);
         float shake = 0.06F * Mth.sin(age * 3.1F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -2.3F + shake);
         m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -2.3F - shake);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, -0.55F);
         m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, 0.55F);
         m.rightLeg.xRot = -0.35F * k;
         m.leftLeg.xRot = -0.25F * k;
         m.head.xRot = 0.2F * k;
         m.head.yRot = 0.5F * s.side * k;
      } else if (t < 80.0F) {
         flail(m, age, 1.0F);
         m.head.xRot = -0.5F;
      } else {
         deadPose(m, 0.6F * s.side);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      float side = s.side;
      if (t < 22.0F) {
         return new CoupDeGrace.Shot(at(-2.3F, -1.1F * side, 0.45F), at(1.9F, 0.0F, 1.35F), 0, true, 3.0F);
      }

      if (t < 42.0F) {
         return new CoupDeGrace.Shot(at(0.6F, 4.3F * side, 2.4F), at(1.1F, 0.0F, 1.0F), 1, true, 3.0F);
      }

      if (t < 80.0F) {
         return new CoupDeGrace.Shot(at(6.4F, 3.0F * side, 1.2F), at(1.8F, 0.5F * side, 1.5F), 2, true, 3.0F);
      }

      float k = smooth(phase(t, 80.0F, 116.0F));
      Vector3f pos = at(-2.6F, 2.6F * side, 1.5F).lerp(at(-1.5F, 1.7F * side, 4.2F), k);
      return new CoupDeGrace.Shot(pos, at(0.2F, 0.0F, 0.3F), 3, true, 3.0F);
   }

   @Override
   void tick(ExecutionScenes.Scene scene, Spectacle.Frame s, int tick) {
      RandomSource random = scene.level.getRandom();
      if (tick >= 15 && tick < 34) {
         Vec3 hand = scene.world(s.actor.scene(-5.0F, -8.0F, -1.0F));
         Vfx.preset(Vfx.Kind.GLINT, hand.x, hand.y, hand.z, (random.nextDouble() - 0.5) * 0.03, 0.45, (random.nextDouble() - 0.5) * 0.03);
      }

      if (tick >= 20 && tick < 68) {
         ExecutionFx.darken(scene, 0.45F * window(tick, 20.0F, 68.0F, 8.0F));
      }

      if (tick >= 24 && tick < 62) {
         Vec3 dot = scene.world(laser(tick));
         scene.level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 1.2F), dot.x, dot.y + 0.05, dot.z, 0.0, 0.0, 0.0);
      }

      if (tick == 62) {
         ExecutionFx.impact(scene, at(0.0F, 0.0F, 0.1F), 1.5F);
         ExecutionFx.flashNear(scene, at(0.0F, 0.0F, 1.0F), 0.9F);
      }

      if (tick > 62 && tick < 110 && tick % 3 == 0) {
         Vec3 chest = scene.world(s.victimChest());
         Vfx.preset(Vfx.Kind.SMOKE, chest.x, chest.y + 0.2, chest.z, 0.0, 0.04, 0.0);
         if (tick % 6 == 0) {
            Vfx.preset(Vfx.Kind.FLAME, chest.x + random.nextGaussian() * 0.2, chest.y + 0.2, chest.z, 0.0, 0.02, 0.0);
         }
      }
   }

   @Override
   void renderExtras(PoseStack ps, MultiBufferSource buffers, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t >= 24.0F && t < 62.0F) {
         Vector3f dot = laser(t);
         float charge = smooth(phase(t, 48.0F, 62.0F));
         FxDraw.beam(ps, buffers, dot, 256.0F, 0.03F + 0.07F * charge, 0.08F + 0.15F * charge, 1.0F, 0.1F, 0.08F, ex.gameTime(), ex.partialTick());
         float lock = smooth(phase(t, 24.0F, 40.0F));
         FxDraw.ring(ps, buffers, dot, 1.4F - 0.8F * lock + 0.1F * Mth.sin(age * 0.8F), 0.12F, 1.0F, 0.12F, 0.1F, 0.9F);
      }

      if (t >= 60.0F && t < 98.0F) {
         float fade = 1.0F - smooth(phase(t, 70.0F, 98.0F));
         float radius = (0.45F + 0.3F * smooth(phase(t, 62.0F, 66.0F))) * fade;
         float bottom = t < 62.0F ? Mth.lerp(phase(t, 60.0F, 62.0F), 40.0F, 0.0F) : 0.0F;
         FxDraw.beam(ps, buffers, at(0.0F, 0.0F, bottom), 256.0F - bottom, radius, radius * 2.4F, 0.8F, 0.95F, 1.0F, ex.gameTime(), ex.partialTick());
      }

      if (t >= 62.0F && t < 78.0F) {
         float k = phase(t, 62.0F, 78.0F);
         FxDraw.ring(ps, buffers, at(0.0F, 0.0F, 0.06F), 0.5F + 7.0F * out(k, 2.0F), 0.6F + 0.6F * k, 0.85F, 0.95F, 1.0F, 0.9F * (1.0F - k));
      }
   }
}
