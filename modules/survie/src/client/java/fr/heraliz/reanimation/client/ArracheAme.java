package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class ArracheAme extends Spectacle {
   static final ArracheAme INSTANCE = new ArracheAme();
   static final int ROLL = 13;
   static final int KNEEL = 22;
   static final int PLUNGE = 24;
   static final int RIP = 56;
   static final int CRUSH = 70;
   static final int BURST = 72;
   static final int END = 104;

   private ArracheAme() {
      super(ExecutionType.ARRACHE_AME);
   }

   @Override
   public float reach() {
      return 0.75F;
   }

   @Override
   float endFwd() {
      return 0.6F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public boolean soulRises() {
      return false;
   }

   private static float kneeling(float t) {
      return smooth(phase(t, 12.0F, 22.0F)) * (1.0F - smooth(phase(t, 32.0F, 50.0F)));
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float fwd = Mth.lerp(smooth(phase(t, 0.0F, 10.0F)), 0.75F, 0.6F);
      fwd = Mth.lerp(smooth(phase(t, 12.0F, 22.0F)), fwd, 0.45F);
      fwd = Mth.lerp(smooth(phase(t, 30.0F, 50.0F)), fwd, 0.6F);
      float kneeling = kneeling(t);
      s.actorPoint.set(at(fwd, 0.0F, -0.32F * kneeling));
      s.lean = 0.3F * kneeling + 0.15F * window(t, 72.0F, 86.0F, 3.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 5.0F) {
         prone(v, t, victimYaw, s.side);
      } else {
         float k = smooth(phase(t, 5.0F, 13.0F));
         float arch = 0.07F * window(t, 24.0F, 34.0F, 2.0F);
         float shudder = t > 24.0F ? 3.0F * Mth.sin(t * 2.3F) * (1.0F - phase(t, 24.0F, 56.0F)) : 0.0F;
         v.turn(90.0F * s.side, -90.0F + shudder, 0.0F, -180.0F * s.side * k)
            .anchor(1.0556251F, at(0.0F, 0.057F * s.side * (1.0F - k), Mth.lerp(k, 0.3F, 0.15F) + arch));
      }

      if (t >= 26.0F && t < 72.0F) {
         float e = smooth(phase(t, 26.0F, 56.0F));
         CoupDeGrace.Body soul = new CoupDeGrace.Body();
         soul.rot.set(rotation(90.0F * s.side, -90.0F, 0.0F, -180.0F * s.side)).slerp(rotation(0.0F, 0.0F, 0.0F, 0.0F), smooth(phase(t, 30.0F, 56.0F)));
         Vector3f neck = v.axis(1.4071876F).lerp(at(0.12F, -0.08F, 1.8F), e);
         if (t > 56.0F) {
            neck.add(0.02F * Mth.sin(t * 0.7F), 0.015F * Mth.sin(t * 1.1F), 0.0F);
         }

         soul.anchor(1.4071876F, neck);
         s.soul = soul;
         s.soulAlpha = 0.85F * smooth(phase(t, 26.0F, 34.0F)) + 0.15F * phase(t, 70.0F, 72.0F);
         s.soulScale = 1.0F - 0.85F * in(phase(t, 70.0F, 72.0F), 2.0F);
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      kneel(m, s.lean, kneeling(t));
      float push = window(t, 3.0F, 12.0F, 3.0F);
      m.rightLeg.xRot = Mth.lerp(push, m.rightLeg.xRot, -0.75F);
      if (t < 22.0F) {
         float r = smooth(phase(t, 12.0F, 20.0F));
         m.rightArm.xRot = -2.8F * r;
         m.rightArm.zRot = 0.1F * r;
      } else if (!(t < 26.0F) && (s.soul != null || !(t < 72.0F))) {
         if (s.soul != null) {
            hold(m.rightArm, s.actor.model(s.soul.axis(1.3485938F)), 1.0F);
         }
      } else {
         m.rightArm.xRot = -2.8F;
         m.rightArm.zRot = 0.1F;
         hold(m.rightArm, s.actor.model(s.victim.scene(0.0F, 4.0F, 1.0F)), in(phase(t, 22.0F, 24.0F), 2.0F));
      }

      hold(m.leftArm, s.actor.model(s.victim.scene(0.0F, 2.0F, 0.0F)), window(t, 14.0F, 40.0F, 4.0F));
      if (t >= 58.0F && s.soul != null) {
         float back = smooth(phase(t, 58.0F, 66.0F)) * (1.0F - smooth(phase(t, 70.0F, 71.0F)));
         m.leftArm.xRot = Mth.lerp(back, m.leftArm.xRot, 0.8F);
         m.leftArm.zRot = Mth.lerp(back, m.leftArm.zRot, -0.35F);
         hold(m.leftArm, s.actor.model(s.soul.axis(1.0556251F)), smooth(phase(t, 70.0F, 71.5F)));
      }

      if (t >= 72.0F) {
         float together = 1.0F - smooth(phase(t, 84.0F, 92.0F));
         float rub = window(t, 88.0F, 102.0F, 3.0F) * Mth.sin(age * 1.6F);
         m.rightArm.xRot = -1.1F * together - 0.75F * window(t, 88.0F, 102.0F, 3.0F) + 0.08F * rub;
         m.leftArm.xRot = -1.1F * together - 0.75F * window(t, 88.0F, 102.0F, 3.0F) - 0.08F * rub;
         m.rightArm.zRot = -0.35F * together;
         m.leftArm.zRot = 0.35F * together;
         m.rightArm.yRot = -0.4F * window(t, 88.0F, 102.0F, 3.0F);
         m.leftArm.yRot = 0.4F * window(t, 88.0F, 102.0F, 3.0F);
      }

      Vector3f target = s.soul != null ? s.soul.axis(1.6415626F) : s.victim.axis(1.6415626F);
      look(m.head, s.actor.model(target), 1.0F, 0.9F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (!(t < 5.0F)) {
         if (t < 24.0F) {
            float k = smooth(phase(t, 5.0F, 13.0F));
            float shake = 0.05F * Mth.sin(age * 3.0F);
            m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.5F + shake);
            m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.5F - shake);
            m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, -0.3F);
            m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, 0.3F);
            m.head.xRot = Mth.lerp(k, m.head.xRot, 0.1F);
         } else if (t < 60.0F) {
            float d = 1.0F - phase(t, 24.0F, 60.0F);
            m.rightArm.xRot = -1.4F + 0.7F * Mth.sin(age * 2.5F) * d;
            m.leftArm.xRot = -1.4F + 0.7F * Mth.sin(age * 2.5F + 1.3F) * d;
            m.rightArm.zRot = 0.4F;
            m.leftArm.zRot = -0.4F;
            m.rightLeg.xRot = 0.25F * Mth.sin(age * 2.1F) * d;
            m.leftLeg.xRot = 0.25F * Mth.sin(age * 2.1F + 2.0F) * d;
            m.head.xRot = -0.45F;
            float limp = smooth(phase(t, 50.0F, 60.0F));
            if (limp > 0.0F) {
               float rx = m.rightArm.xRot;
               float lx = m.leftArm.xRot;
               deadPose(m, 0.55F * s.side * limp);
               m.rightArm.xRot = Mth.lerp(limp, rx, m.rightArm.xRot);
               m.leftArm.xRot = Mth.lerp(limp, lx, m.leftArm.xRot);
            }
         } else {
            deadPose(m, 0.55F * s.side);
         }
      }
   }

   @Override
   void poseSoul(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      flail(m, age, 0.7F);
      m.head.xRot = -0.5F + 0.1F * Mth.sin(age * 1.3F);
      m.rightLeg.xRot = 0.15F + 0.2F * Mth.sin(age * 0.9F);
      m.leftLeg.xRot = 0.15F + 0.2F * Mth.sin(age * 0.9F + 2.0F);
      float claw = smooth(phase(t, 52.0F, 60.0F));
      if (claw > 0.0F && s.soul != null) {
         Vector3f shoulder = s.actor.scene(-5.0F, 12.0F - 10.0F * Mth.cos(s.lean), -10.0F * Mth.sin(s.lean));
         Vector3f wrist = s.soul.model(s.soul.axis(1.4071876F).lerp(shoulder, 0.3F));
         hold(m.rightArm, new Vector3f(wrist).add(-1.2F, 0.0F, 0.0F), claw);
         hold(m.leftArm, new Vector3f(wrist).add(1.2F, 0.0F, 0.0F), claw);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      float side = s.side;
      if (t < 24.0F) {
         return new CoupDeGrace.Shot(at(-0.5F, 2.7F * side, 1.3F), at(0.3F, 0.0F, 0.55F), 0, true, 3.0F);
      }

      if (t < 56.0F) {
         Vector3f target = s.soul != null ? s.soul.axis(1.0556251F) : at(0.0F, 0.0F, 0.5F);
         return new CoupDeGrace.Shot(at(-1.35F, 1.5F * side, 0.45F), target, 1, true, 4.0F);
      }

      if (t < 78.0F) {
         return new CoupDeGrace.Shot(at(0.4F, 3.0F * side, 1.35F), at(0.36F, 0.0F, 1.45F), 2, true, 3.0F);
      }

      float k = smooth(phase(t, 78.0F, 104.0F));
      Vector3f pos = at(0.4F, 3.0F * side, 1.35F).lerp(at(-2.6F, 2.6F * side, 3.2F), k);
      return new CoupDeGrace.Shot(pos, at(0.3F, 0.0F, 0.6F), 3, true, 3.0F);
   }

   @Override
   void tick(ExecutionScenes.Scene scene, Spectacle.Frame s, int tick) {
      RandomSource random = scene.level.getRandom();
      if (tick >= 13 && tick < 24) {
         Vec3 hand = scene.world(s.actor.scene(-5.0F, -8.0F, -1.0F));

         for (int i = 0; i < 3; i++) {
            double a = tick * 0.9 + i * (Math.PI * 2.0 / 3.0);
            Vfx.preset(Vfx.Kind.SOUL_FLAME, hand.x + Math.cos(a) * 0.3, hand.y + i * 0.1, hand.z + Math.sin(a) * 0.3, 0.0, 0.02, 0.0);
         }
      }

      Vec3 chest = scene.world(s.victimChest());
      if (tick == 24) {
         ExecutionFx.impact(scene, at(0.0F, 0.0F, 0.3F), 0.35F);
      }

      if (tick > 24 && tick < 56 && s.soul != null) {
         Vec3 soul = scene.world(s.soul.axis(1.0556251F));

         for (int i = 0; i < 2; i++) {
            double k = random.nextDouble();
            Vfx.preset(
               Vfx.Kind.SOUL,
               Mth.lerp(k, chest.x, soul.x),
               Mth.lerp(k, chest.y, soul.y),
               Mth.lerp(k, chest.z, soul.z),
               0.0,
               0.02,
               0.0
            );
         }

         Vfx.preset(Vfx.Kind.SOUL_FLAME, chest.x, chest.y, chest.z, 0.0, 0.03, 0.0);
      }

      if (tick == 72) {
         Vec3 at = scene.world(at(0.12F, -0.08F, 1.4F));

         for (int i = 0; i < 40; i++) {
            Vfx.preset(Vfx.Kind.SOUL, at.x, at.y, at.z, random.nextGaussian() * 0.15, random.nextGaussian() * 0.15, random.nextGaussian() * 0.15);
         }

         ExecutionFx.impact(scene, at(0.0F, 0.0F, 0.1F), 0.9F);
         ExecutionFx.flashNear(scene, at(0.12F, -0.08F, 1.4F), 0.6F);
      }

      if (tick > 72 && tick < 92) {
         Vec3 hands = scene.world(s.actor.axis(1.25F).add(at(-0.4F, 0.0F, 0.0F)));
         Vfx.preset(Vfx.Kind.SOUL, hands.x + random.nextGaussian() * 0.1, hands.y, hands.z + random.nextGaussian() * 0.1, 0.0, 0.03, 0.0);
      }
   }

   @Override
   void renderExtras(PoseStack ps, MultiBufferSource buffers, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (s.soul != null && s.soulAlpha > 0.01F) {
         PlayerModel<LivingEntity> model = ex.soulModel();
         this.poseSoul(model, s, age);
         copyLayers(model);
         ps.pushPose();
         ps.translate(s.soul.pos.x, s.soul.pos.y, s.soul.pos.z);
         ps.mulPose(s.soul.rot);
         ps.translate(0.0F, 1.0556251F, 0.0F);
         ps.scale(s.soulScale, s.soulScale, s.soulScale);
         ps.translate(0.0F, -1.0556251F, 0.0F);
         ps.scale(-1.0F, -1.0F, 1.0F);
         ps.scale(0.9375F, 0.9375F, 0.9375F);
         ps.translate(0.0F, -1.501F, 0.0F);
         FxDraw.soul(ps, buffers, model, s.soulAlpha, age);
         ps.popPose();
         if (t < 50.0F) {
            float a = 0.7F * window(t, 26.0F, 50.0F, 4.0F);
            FxDraw.streak(ps, buffers, s.victimChest(), s.soul.axis(0.4F), 0.08F, 0.4F, 0.85F, 1.0F, a);
         }
      }

      if (t >= 72.0F && t < 88.0F) {
         float k = phase(t, 72.0F, 88.0F);
         FxDraw.ring(ps, buffers, at(0.0F, 0.0F, 0.06F), 0.4F + 5.0F * out(k, 2.0F), 0.6F, 0.3F, 0.85F, 1.0F, 0.9F * (1.0F - k));
      }
   }
}
