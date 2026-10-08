package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class ChuteMeteore extends Spectacle {
   static final ChuteMeteore INSTANCE = new ChuteMeteore();
   static final int CROUCH = 12;
   static final int APEX = 30;
   static final int DIVE = 40;
   static final int IMPACT = 48;
   static final int KNEEL_END = 60;
   static final int RISEN = 74;
   static final int END = 100;
   private static final float HEIGHT = 9.0F;
   private static final float CHEST = 0.27F;

   private ChuteMeteore() {
      super(ExecutionType.CHUTE_METEORE);
   }

   @Override
   public float reach() {
      return 0.75F;
   }

   @Override
   float endFwd() {
      return 0.5F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float pitch = 0.0F;
      float fwd;
      float up;
      if (t < 12.0F) {
         fwd = Mth.lerp(smooth(phase(t, 0.0F, 8.0F)), 0.75F, 0.95F);
         up = -0.18F * smooth(phase(t, 3.0F, 12.0F));
      } else if (t < 30.0F) {
         float u = phase(t, 12.0F, 30.0F);
         fwd = Mth.lerp(smooth(u), 0.95F, 0.0F);
         up = 9.0F * out(u, 3.0F);
         pitch = 360.0F * smooth(phase(t, 14.0F, 28.0F));
      } else if (t < 40.0F) {
         fwd = 0.0F;
         up = 9.0F + 0.12F * Mth.sin((float) Math.PI * phase(t, 30.0F, 40.0F));
      } else if (t < 48.0F) {
         fwd = 0.0F;
         up = Mth.lerp(in(phase(t, 40.0F, 48.0F), 2.0F), 9.0F, 0.27F);
         pitch = -8.0F;
      } else if (t < 60.0F) {
         float k = smooth(phase(t, 48.0F, 52.0F));
         fwd = Mth.lerp(k, 0.0F, 0.5F);
         up = Mth.lerp(k, 0.27F, -0.3F);
      } else {
         fwd = 0.5F;
         up = Mth.lerp(smooth(phase(t, 60.0F, 74.0F)), -0.3F, 0.0F);
      }

      s.actorAnchor = 0.9F;
      s.actorPoint.set(at(fwd, 0.0F, up + 0.9F));
      s.actorPitch = pitch;
      s.lean = t < 12.0F ? 0.45F * smooth(phase(t, 2.0F, 12.0F)) : (t < 40.0F ? 0.15F * window(t, 30.0F, 40.0F, 3.0F) : 0.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 20.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 48.0F) {
         float k = smooth(phase(t, 20.0F, 28.0F));
         v.turn(90.0F * s.side, -90.0F, 0.0F, -180.0F * s.side * k).anchor(1.0556251F, at(0.0F, 0.057F * s.side * (1.0F - k), Mth.lerp(k, 0.3F, 0.15F)));
      } else {
         float h = t < 49.0F ? -0.06F : 0.35F * Mth.sin((float) Math.PI * phase(t, 49.0F, 56.0F)) * (1.0F - 0.3F * phase(t, 49.0F, 56.0F));
         v.turn(90.0F * s.side, -90.0F + 60.0F * Math.max(0.0F, h), 0.0F, -180.0F * s.side).anchor(1.0556251F, at(0.0F, 0.0F, 0.15F + h));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      Vector3f head = s.actor.model(s.victim.axis(1.6415626F));
      if (t < 12.0F) {
         float c = smooth(phase(t, 2.0F, 12.0F));
         m.rightLeg.xRot = -0.55F * c;
         m.leftLeg.xRot = 0.45F * c;
         float back = smooth(phase(t, 6.0F, 12.0F));
         m.rightArm.xRot = 0.9F * back;
         m.leftArm.xRot = 0.9F * back;
         look(m.head, head, 1.0F, 0.9F);
      } else if (t < 30.0F) {
         float lift = smooth(phase(t, 12.0F, 15.0F));
         float tuck = window(t, 15.0F, 29.0F, 3.0F);
         m.rightArm.xRot = Mth.lerp(tuck, Mth.lerp(lift, 0.9F, -2.8F), -0.9F);
         m.leftArm.xRot = m.rightArm.xRot;
         m.rightArm.zRot = 0.15F;
         m.leftArm.zRot = -0.15F;
         m.rightLeg.xRot = -1.2F * tuck;
         m.leftLeg.xRot = -1.2F * tuck;
      } else if (t < 40.0F) {
         m.rightArm.zRot = 1.3F;
         m.leftArm.zRot = -1.3F;
         m.rightArm.xRot = 0.1F * Mth.sin(age * 0.5F);
         m.leftArm.xRot = -0.1F * Mth.sin(age * 0.5F);
         m.rightLeg.zRot = 0.12F;
         m.leftLeg.zRot = -0.12F;
         look(m.head, head, 1.0F, 0.9F);
      } else if (t < 48.0F) {
         m.rightLeg.xRot = -1.1F;
         m.leftLeg.xRot = 0.15F;
         m.rightArm.xRot = 0.55F;
         m.leftArm.xRot = 0.55F;
         m.rightArm.zRot = 0.3F;
         m.leftArm.zRot = -0.3F;
         look(m.head, head, 1.0F, 0.9F);
      } else {
         float k = 1.0F - smooth(phase(t, 60.0F, 74.0F));
         bend(m, 0.75F * k);
         m.rightLeg.xRot = 1.2F * k;
         m.leftLeg.xRot = -1.1F * k;
         m.rightArm.xRot = -0.35F * k;
         m.rightArm.zRot = 0.1F * k;
         m.leftArm.xRot = 0.6F * k;
         m.leftArm.zRot = -0.55F * k;
         look(m.head, head, 1.0F, 0.9F);
         m.head.xRot = Mth.lerp(k, m.head.xRot, 0.5F);
         float neck = window(t, 80.0F, 94.0F, 2.0F);
         m.head.zRot = 0.35F * neck * Mth.sin((float) (Math.PI * 2) * phase(t, 80.0F, 94.0F));
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 20.0F) {
         lyingPose(m);
      } else if (t < 48.0F) {
         float k = smooth(phase(t, 20.0F, 28.0F));
         lyingPose(m);
         boolean rightNear = s.side > 0;
         float shake = 0.05F * Mth.sin(age * 3.3F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, rightNear ? -1.6F + shake : -0.2F);
         m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, rightNear ? -0.2F : -1.6F + shake);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, rightNear ? 0.2F : 0.9F);
         m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, rightNear ? -0.9F : -0.2F);
         m.rightLeg.xRot = -0.2F * k;
         m.leftLeg.xRot = 0.0F;
         look(m.head, s.victim.model(s.actor.axis(1.5F)), 0.9F, 0.6F);
      } else if (t < 56.0F) {
         float k = Mth.sin((float) Math.PI * phase(t, 48.0F, 56.0F));
         deadPose(m, 0.5F * s.side);
         m.rightArm.xRot = -1.0F * k - 0.1F;
         m.leftArm.xRot = -1.0F * k - 0.1F;
         m.rightLeg.xRot = -0.6F * k;
         m.leftLeg.xRot = -0.5F * k;
      } else {
         deadPose(m, 0.5F * s.side);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      float side = s.side;
      if (t < 12.0F) {
         return new CoupDeGrace.Shot(at(-1.7F, -2.1F * side, 0.55F), at(0.45F, 0.0F, 0.7F), 0, true, 3.0F);
      }

      Vector3f waist = s.actor.axis(0.9F);
      if (t < 30.0F) {
         return new CoupDeGrace.Shot(at(-1.4F, -3.6F * side, 0.5F + 0.8F * waist.y), new Vector3f(waist).add(0.0F, 0.3F, 0.0F), 1, true, 25.0F);
      }

      if (t < 48.0F) {
         Vector3f pos = new Vector3f(waist).add(at(-2.0F, -2.2F * side, 5.0F));
         return new CoupDeGrace.Shot(pos, s.victimChest().lerp(waist, 0.65F), 2, true, 30.0F);
      }

      if (t < 70.0F) {
         return new CoupDeGrace.Shot(at(-2.4F, 2.5F * side, 0.55F), at(0.3F, 0.0F, 0.5F), 3, true, 3.0F);
      }

      float k = smooth(phase(t, 70.0F, 100.0F));
      Vector3f pos = at(-2.4F, 2.5F * side, 0.55F).lerp(at(-1.6F, 2.0F * side, 3.8F), k);
      return new CoupDeGrace.Shot(pos, at(0.25F, 0.0F, 0.3F), 4, true, 3.0F);
   }

   @Override
   void tick(ExecutionScenes.Scene scene, Spectacle.Frame s, int tick) {
      RandomSource random = scene.level.getRandom();
      if (tick == 12) {
         ExecutionFx.impact(scene, at(0.95F, 0.0F, 0.05F), 0.4F);
      }

      Vec3 waist = scene.world(s.actor.axis(0.9F));
      if (tick > 12 && tick < 30) {
         Vfx.preset(Vfx.Kind.CLOUD, waist.x, waist.y - 0.6, waist.z, 0.0, -0.05, 0.0);
      }

      if (tick >= 40 && tick < 48) {
         for (int i = 0; i < 3; i++) {
            Vfx.preset(
               Vfx.Kind.FLAME,
               waist.x + random.nextGaussian() * 0.25,
               waist.y + random.nextGaussian() * 0.4,
               waist.z + random.nextGaussian() * 0.25,
               0.0,
               0.1,
               0.0
            );
         }

         Vfx.preset(Vfx.Kind.SMOKE, waist.x, waist.y + 0.5, waist.z, 0.0, 0.05, 0.0);
         Vfx.preset(Vfx.Kind.EMBER, waist.x, waist.y, waist.z, 0.0, 0.0, 0.0);
      }

      if (tick == 48) {
         ExecutionFx.impact(scene, at(0.0F, 0.0F, 0.1F), 1.7F);
         ExecutionFx.flashNear(scene, at(0.0F, 0.0F, 0.5F), 0.5F);
      }

      if (tick > 48 && tick < 76 && tick % 2 == 0) {
         Vec3 p = scene.world(at((float)random.nextGaussian() * 1.2F, (float)random.nextGaussian() * 1.2F, 0.1F));
         Vfx.preset(Vfx.Kind.DARK_SMOKE, p.x, p.y, p.z, 0.0, 0.015, 0.0);
      }
   }

   @Override
   void renderExtras(PoseStack ps, MultiBufferSource buffers, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t >= 40.0F && t < 50.0F) {
         Vector3f waist = s.actor.axis(0.9F);
         float a = 0.8F * (1.0F - smooth(phase(t, 48.0F, 50.0F)));
         FxDraw.streak(ps, buffers, waist, new Vector3f(waist).add(0.0F, 3.5F, 0.0F), 0.35F, 1.0F, 0.55F, 0.15F, a);
         FxDraw.streak(ps, buffers, waist, new Vector3f(waist).add(0.0F, 2.0F, 0.0F), 0.15F, 1.0F, 0.9F, 0.6F, a);
      }

      if (t >= 48.0F && t < 64.0F) {
         float k = phase(t, 48.0F, 64.0F);
         FxDraw.ring(ps, buffers, at(0.0F, 0.0F, 0.06F), 0.6F + 8.0F * out(k, 2.0F), 0.7F + 0.6F * k, 1.0F, 0.7F, 0.35F, 0.9F * (1.0F - k));
      }
   }
}
