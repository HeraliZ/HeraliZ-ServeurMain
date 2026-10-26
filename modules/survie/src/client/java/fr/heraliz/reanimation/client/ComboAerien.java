package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class ComboAerien extends Spectacle {
   static final ComboAerien INSTANCE = new ComboAerien();
   static final int HAUL = 16;
   static final int WIND = 26;
   static final int UPPERCUT = 28;
   static final int JUMP = 34;
   static final int FLY = 44;
   static final int SMASH = 46;
   static final int HAMMER = 48;
   static final int CRASH = 53;
   static final int LANDING = 64;
   static final int END = 104;
   private static final float BODY_TOP = 8.2F;
   private static final float JUMP_TOP = 7.3F;
   private static final float KNEEL_FWD = 0.15F;
   private static final float FLY_FWD = -0.25F;

   private ComboAerien() {
      super(ExecutionType.COMBO_AERIEN);
   }

   @Override
   public float reach() {
      return 0.75F;
   }

   @Override
   float endFwd() {
      return 0.9F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float fwd = 0.75F;
      float up = 0.0F;
      float pitch = 0.0F;
      if (t < 16.0F) {
         fwd = 0.75F;
      } else if (t < 28.0F) {
         fwd = Mth.lerp(smooth(phase(t, 16.0F, 22.0F)), 0.75F, 0.9F);
         up = -0.1F * smooth(phase(t, 20.0F, 26.0F));
      } else if (t < 34.0F) {
         fwd = 0.9F;
         up = -0.1F + 0.35F * Mth.sin((float) Math.PI * phase(t, 26.0F, 32.0F)) - 0.1F * smooth(phase(t, 30.0F, 34.0F));
      } else if (t < 48.0F) {
         float u = phase(t, 34.0F, 46.0F);
         fwd = Mth.lerp(smooth(u), 0.9F, 0.75F);
         up = 7.3F * out(u, 2.5F);
         pitch = -25.0F * smooth(phase(t, 45.0F, 48.0F));
      } else if (t < 64.0F) {
         float w = phase(t, 48.0F, 64.0F);
         fwd = Mth.lerp(smooth(w), 0.75F, 0.9F);
         up = 7.3F * (1.0F - w * w);
         pitch = -25.0F * (1.0F - smooth(phase(t, 48.0F, 52.0F)));
      } else {
         fwd = 0.9F;
         up = -0.22F * window(t, 64.0F, 76.0F, 3.0F);
      }

      s.actorAnchor = 0.9F;
      s.actorPoint.set(at(fwd, 0.0F, up + 0.9F));
      s.actorPitch = pitch;
      s.lean = lean(t);
      CoupDeGrace.Body v = s.victim;
      if (t < 6.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 28.0F) {
         float k = smooth(phase(t, 6.0F, 16.0F));
         float sway = t > 16.0F ? 1.0F : 0.0F;
         Vector3f c = at(0.0F, 0.057F * s.side, 0.3F).lerp(at(0.15F, 0.0F, 0.7356251F), k);
         v.turn(
               Mth.rotLerp(k, 90.0F * s.side, 0.0F),
               Mth.lerp(k, -90.0F, 0.0F) + 3.0F * sway * Mth.sin(t * 0.4F),
               3.0F * sway * Mth.sin(t * 0.3F),
               0.0F
            )
            .anchor(1.0556251F, c);
      } else if (t < 48.0F) {
         float u = phase(t, 28.0F, 44.0F);
         float h = t < 44.0F ? Mth.lerp(out(u, 2.2F), 0.7356251F, 8.2F) : 8.2F + 0.15F * Mth.sin((float) Math.PI * phase(t, 44.0F, 48.0F));
         float spin = 450.0F * smooth(u) + (t >= 44.0F ? 8.0F * Mth.sin(t * 0.9F) : 0.0F);
         v.turn(0.0F, spin, 0.0F, 0.0F).anchor(1.0556251F, at(Mth.lerp(smooth(u), 0.15F, -0.25F), 0.0F, h));
      } else if (t < 53.0F) {
         float w = phase(t, 48.0F, 53.0F);
         v.turn(0.0F, 90.0F, 0.0F, 0.0F).anchor(1.0556251F, at(Mth.lerp(w, -0.25F, -0.3F), 0.0F, Mth.lerp(in(w, 2.0F), 8.2F, 0.15F)));
      } else {
         float b = 0.3F * Mth.sin((float) Math.PI * phase(t, 53.0F, 59.0F)) * (1.0F - 0.5F * phase(t, 53.0F, 59.0F));
         v.turn(0.0F, 90.0F - 40.0F * b, 0.0F, 0.0F).anchor(1.0556251F, at(-0.3F, 0.0F, 0.15F + b));
      }
   }

   private static float lean(float t) {
      if (t < 16.0F) {
         return 0.9F * window(t, 0.0F, 16.0F, 5.0F);
      } else if (t < 26.0F) {
         return 0.3F * smooth(phase(t, 16.0F, 26.0F));
      } else if (t < 30.0F) {
         return Mth.lerp(smooth(phase(t, 26.0F, 29.0F)), 0.3F, -0.25F);
      } else if (t < 34.0F) {
         return Mth.lerp(smooth(phase(t, 30.0F, 34.0F)), -0.25F, 0.4F);
      } else if (t < 46.0F) {
         return Mth.lerp(smooth(phase(t, 34.0F, 38.0F)), 0.4F, 0.05F);
      } else if (t < 50.0F) {
         return 0.5F * smooth(phase(t, 46.0F, 48.0F));
      } else {
         return t < 64.0F ? Mth.lerp(smooth(phase(t, 50.0F, 56.0F)), 0.5F, 0.1F) : 0.5F * window(t, 64.0F, 76.0F, 3.0F);
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      Vector3f head = s.actor.model(s.victim.axis(1.6415626F));
      if (t < 16.0F) {
         hold(m.rightArm, s.actor.model(s.victim.scene(0.0F, 0.0F, 2.0F)), smooth(phase(t, 1.0F, 7.0F)) * (1.0F - smooth(phase(t, 14.0F, 18.0F))));
         m.leftArm.xRot = -0.3F * s.lean;
      } else if (t < 26.0F) {
         float w = smooth(phase(t, 16.0F, 26.0F));
         m.rightArm.xRot = 1.0F * w;
         m.rightArm.zRot = 0.25F * w;
         m.leftArm.xRot = -0.9F * w;
         m.leftArm.zRot = -0.1F * w;
         m.rightLeg.xRot = 0.35F * w;
         m.leftLeg.xRot = -0.3F * w;
      } else if (t < 31.0F) {
         float k = in(phase(t, 26.0F, 28.0F), 2.0F);
         m.rightArm.xRot = Mth.lerp(k, 1.0F, -2.9F);
         m.rightArm.zRot = Mth.lerp(k, 0.25F, 0.05F);
         m.leftArm.xRot = Mth.lerp(k, -0.9F, 0.5F);
         m.rightLeg.xRot = Mth.lerp(k, 0.35F, 0.0F);
         m.leftLeg.xRot = Mth.lerp(k, -0.3F, 0.0F);
      } else if (t < 34.0F) {
         float c = smooth(phase(t, 31.0F, 34.0F));
         m.rightArm.xRot = Mth.lerp(c, -2.9F, 0.8F);
         m.leftArm.xRot = Mth.lerp(c, 0.5F, 0.8F);
         m.rightLeg.xRot = -0.45F * c;
         m.leftLeg.xRot = 0.35F * c;
      } else if (t < 46.0F) {
         float k = smooth(phase(t, 34.0F, 37.0F));
         m.rightArm.xRot = Mth.lerp(k, 0.8F, -2.9F);
         m.leftArm.xRot = m.rightArm.xRot;
         m.rightArm.zRot = -0.25F * k;
         m.leftArm.zRot = 0.25F * k;
         m.rightLeg.xRot = -0.4F;
         m.leftLeg.xRot = -0.15F;
      } else if (t < 50.0F) {
         float k = in(phase(t, 46.0F, 48.0F), 2.0F);
         m.rightArm.xRot = Mth.lerp(k, -2.9F, -0.5F);
         m.leftArm.xRot = m.rightArm.xRot;
         m.rightArm.zRot = -0.25F;
         m.leftArm.zRot = 0.25F;
         m.rightLeg.xRot = -0.4F;
         m.leftLeg.xRot = 0.2F;
      } else if (t < 64.0F) {
         float k = smooth(phase(t, 50.0F, 54.0F));
         m.rightArm.xRot = Mth.lerp(k, -0.5F, -2.2F);
         m.leftArm.xRot = m.rightArm.xRot;
         m.rightArm.zRot = Mth.lerp(k, -0.25F, 0.9F);
         m.leftArm.zRot = -m.rightArm.zRot;
         m.rightLeg.xRot = -0.7F;
         m.leftLeg.xRot = 0.2F;
      } else {
         float k = window(t, 64.0F, 76.0F, 3.0F);
         m.rightArm.zRot = 0.6F * k;
         m.leftArm.zRot = -0.6F * k;
         m.rightLeg.xRot = -0.4F * k;
         m.leftLeg.xRot = 0.3F * k;
         float knuckles = window(t, 80.0F, 94.0F, 3.0F);
         float push = 0.08F * Mth.sin(age * 1.4F);
         m.rightArm.xRot = Mth.lerp(knuckles, m.rightArm.xRot, -1.1F + push);
         m.leftArm.xRot = Mth.lerp(knuckles, m.leftArm.xRot, -1.1F - push);
         m.rightArm.zRot = Mth.lerp(knuckles, m.rightArm.zRot, -0.4F);
         m.leftArm.zRot = Mth.lerp(knuckles, m.leftArm.zRot, 0.4F);
      }

      look(m.head, head, 1.1F, 0.9F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 6.0F) {
         lyingPose(m);
      } else if (t < 28.0F) {
         float k = smooth(phase(t, 6.0F, 12.0F));
         lyingPose(m);
         kneel(m, 0.0F, smooth(phase(t, 6.0F, 16.0F)));
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, 0.15F);
         m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, 0.15F);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.15F);
         m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.15F);
         m.head.xRot = Mth.lerp(k, m.head.xRot, 0.5F + 0.08F * Mth.sin(age * 0.5F));
      } else if (t < 48.0F) {
         flail(m, age, 1.0F);
         m.head.xRot = -0.5F;
      } else if (t < 53.0F) {
         m.rightArm.zRot = 1.3F;
         m.leftArm.zRot = -1.3F;
         m.rightLeg.zRot = 0.3F;
         m.leftLeg.zRot = -0.3F;
         m.head.xRot = 0.3F;
      } else {
         deadPose(m, 0.7F * s.side);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      float side = s.side;
      if (t < 28.0F) {
         return new CoupDeGrace.Shot(at(-0.9F, 2.7F * side, 1.15F), at(0.45F, 0.0F, 0.95F), 0, true, 3.0F);
      }

      if (t < 46.0F) {
         Vector3f middle = s.victimChest().add(s.actorChest()).mul(0.5F);
         return new CoupDeGrace.Shot(at(-3.9F, 3.5F * side, 0.6F + 0.6F * middle.y), middle, 1, true, 8.0F);
      }

      if (t < 53.0F) {
         return new CoupDeGrace.Shot(at(-1.2F, 5.0F * side, 5.2F), s.victimChest(), 2, true, 15.0F);
      }

      if (t < 74.0F) {
         return new CoupDeGrace.Shot(at(-2.4F, 2.3F * side, 0.5F), at(0.3F, 0.0F, 0.6F), 3, true, 3.0F);
      }

      float k = smooth(phase(t, 74.0F, 104.0F));
      Vector3f pos = at(-2.4F, 2.3F * side, 0.5F).lerp(at(-1.4F, 1.9F * side, 3.6F), k);
      return new CoupDeGrace.Shot(pos, at(0.3F, 0.0F, 0.3F), 4, true, 3.0F);
   }

   @Override
   void tick(ExecutionScenes.Scene scene, Spectacle.Frame s, int tick) {
      RandomSource random = scene.level.getRandom();
      if (tick > 18 && tick < 28) {
         Vec3 fist = scene.world(s.actor.scene(-6.0F, 12.0F, 4.0F));

         for (int i = 0; i < 2; i++) {
            Vfx.preset(
               Vfx.Kind.SPARK,
               fist.x + random.nextGaussian() * 0.15,
               fist.y + random.nextGaussian() * 0.15,
               fist.z + random.nextGaussian() * 0.15,
               0.0,
               0.0,
               0.0
            );
         }
      }

      if (tick == 28) {
         Vec3 chin = scene.world(s.victim.axis(1.4071876F));
         Vfx.preset(Vfx.Kind.BOOM, chin.x, chin.y, chin.z, 0.0, 0.0, 0.0);
         ExecutionFx.impact(scene, at(0.15F, 0.0F, 1.2F), 0.6F);
      }

      Vec3 body = scene.world(s.victimChest());
      if (tick > 28 && tick < 44) {
         Vfx.preset(Vfx.Kind.CLOUD, body.x, body.y, body.z, 0.0, -0.05, 0.0);
      }

      if (tick == 48) {
         Vfx.preset(Vfx.Kind.BOOM, body.x, body.y, body.z, 0.0, 0.0, 0.0);
      }

      if (tick >= 48 && tick < 53) {
         for (int i = 0; i < 3; i++) {
            Vfx.preset(
               Vfx.Kind.FLAME,
               body.x + random.nextGaussian() * 0.25,
               body.y + random.nextGaussian() * 0.3,
               body.z + random.nextGaussian() * 0.25,
               0.0,
               0.1,
               0.0
            );
         }

         Vfx.preset(Vfx.Kind.SMOKE, body.x, body.y + 0.4, body.z, 0.0, 0.05, 0.0);
      }

      if (tick == 53) {
         ExecutionFx.impact(scene, at(-0.25F, 0.0F, 0.1F), 1.6F);
         ExecutionFx.flashNear(scene, at(-0.25F, 0.0F, 0.5F), 0.4F);
      }

      if (tick == 64) {
         ExecutionFx.impact(scene, at(0.9F, 0.0F, 0.05F), 0.35F);
      }
   }

   @Override
   void renderExtras(PoseStack ps, MultiBufferSource buffers, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t >= 48.0F && t < 54.0F) {
         Vector3f body = s.victimChest();
         float a = 0.8F * (1.0F - smooth(phase(t, 52.0F, 54.0F)));
         FxDraw.streak(ps, buffers, body, new Vector3f(body).add(0.0F, 3.0F, 0.0F), 0.4F, 1.0F, 0.5F, 0.15F, a);
      }

      if (t >= 53.0F && t < 69.0F) {
         float k = phase(t, 53.0F, 69.0F);
         FxDraw.ring(ps, buffers, at(-0.25F, 0.0F, 0.06F), 0.6F + 7.0F * out(k, 2.0F), 0.7F + 0.5F * k, 1.0F, 0.75F, 0.4F, 0.9F * (1.0F - k));
      }
   }
}
