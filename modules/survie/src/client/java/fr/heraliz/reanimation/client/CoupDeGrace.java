package fr.heraliz.reanimation.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class CoupDeGrace {
   static final int GRAB = 14;
   static final int CARRY = 28;
   static final int PRESS = 50;
   static final int COMMIT = 58;
   static final int SLAM = 64;
   static final int SETTLE = 70;
   static final int WALK = 86;
   static final int WINDUP = 88;
   static final int SWING = 93;
   static final int KICK = 96;
   static final int END = 118;
   static final int CORPSE = 120;
   static final int SINK = 25;
   static final float PX = 0.05859375F;
   static final float NECK = 1.4071876F;
   static final float HIPS = 0.7040626F;
   static final float MID = 1.0556251F;
   static final float LYING = 0.15F;
   private static final float CARRY_FWD = 0.28F;
   private static final float CARRY_UP = 1.12F;
   private static final float OVER_FWD = 0.5F;
   private static final float OVER_UP = 1.99F;
   static final Choreography INSTANCE = new Choreography() {
      @Override
      public CoupDeGrace.State at(float t, int side, float victimYaw, Vector3f actorStart, float actorYaw) {
         return CoupDeGrace.at(t, side, victimYaw, actorStart, actorYaw);
      }

      @Override
      public void poseActor(HumanoidModel<?> m, CoupDeGrace.State s, float age) {
         CoupDeGrace.poseActor(m, s, age);
      }

      @Override
      public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State s, float age) {
         CoupDeGrace.poseVictim(m, s, age);
      }

      @Override
      public CoupDeGrace.Shot shot(CoupDeGrace.State s) {
         return CoupDeGrace.shot(s);
      }

      @Override
      public Vector3f end(int side) {
         return CoupDeGrace.at(0.32F, 0.7F * side, 0.0F);
      }

      @Override
      public Vector3f corpse(int side) {
         return CoupDeGrace.at(-0.3F, 0.0F, 0.15F);
      }

      @Override
      public void tick(ExecutionScenes.Scene s, int before, int now) {
         if (before < 64 && now >= 64) {
            ExecutionFx.slam(s);
         }
      }
   };

   private CoupDeGrace() {
   }

   static Vector3f at(float fwd, float side, float up) {
      return new Vector3f(-side, up, fwd);
   }

   static CoupDeGrace.State at(float t, int side, float victimYaw, Vector3f actorStart, float actorYaw) {
      CoupDeGrace.State s = new CoupDeGrace.State(t, side);
      actor(s, t, actorStart, actorYaw);
      victim(s, t, victimYaw);
      return s;
   }

   private static void actor(CoupDeGrace.State s, float t, Vector3f start, float startYaw) {
      float fwd = 0.75F;
      float side = 0.0F;
      float yaw = 180.0F;
      float up = 0.0F;
      if (t < 70.0F) {
         fwd -= 0.13F * smooth(phase(t, 58.0F, 64.0F)) * (1.0F - smooth(phase(t, 65.0F, 70.0F)));
         up -= 0.06F * smooth(phase(t, 60.0F, 64.0F)) * (1.0F - smooth(phase(t, 66.0F, 70.0F)));
      } else if (t < 86.0F) {
         float u = smooth(phase(t, 71.0F, 85.0F));
         fwd = Mth.lerp(u, 0.75F, 0.32F);
         side = Mth.lerp(u, 0.0F, 0.7F * s.side);
         float heading = (float)Math.toDegrees(Math.atan2(0.7F * s.side, -0.43F));
         yaw = Mth.rotLerp(smooth(phase(t, 70.0F, 73.0F)), 180.0F, heading);
         yaw = Mth.rotLerp(smooth(phase(t, 82.0F, 86.0F)), yaw, 180.0F);
         up = 0.03F * Mth.abs(Mth.sin(walkPhase(t))) * walkAmount(t);
      } else {
         fwd = 0.32F;
         side = 0.7F * s.side;
      }

      s.lean = lean(t);
      float split = smooth(phase(t, 0.0F, 10.0F)) * (1.0F - smooth(phase(t, 14.0F, 20.0F)));
      up -= 0.05F * split;
      float in = smooth(t / 5.0F);
      Vector3f p = at(fwd, side, up);
      p.x = Mth.lerp(in, start.x, p.x);
      p.z = Mth.lerp(in, start.z, p.z);
      s.actorFwd = p.z;
      s.actorSide = -p.x;
      s.actor.turn(Mth.rotLerp(in, startYaw, yaw), 0.0F, 0.0F, 0.0F).feet(p);
   }

   private static float lean(float t) {
      if (t < 14.0F) {
         return 1.15F * smooth(t / 10.0F);
      } else if (t < 28.0F) {
         return Mth.lerp(smooth(phase(t, 14.0F, 24.0F)), 1.15F, 0.08F);
      } else if (t < 50.0F) {
         return Mth.lerp(smooth(phase(t, 28.0F, 50.0F)), 0.08F, -0.12F);
      } else if (t < 58.0F) {
         return Mth.lerp(smooth(phase(t, 50.0F, 58.0F)), -0.12F, -0.2F);
      } else if (t < 64.0F) {
         return Mth.lerp((float)Math.pow(phase(t, 58.0F, 64.0F), 1.3), -0.2F, 0.75F);
      } else if (t < 70.0F) {
         return Mth.lerp(smooth(phase(t, 66.0F, 70.0F)), 0.75F, 0.3F);
      } else if (t < 88.0F) {
         return Mth.lerp(smooth(phase(t, 70.0F, 86.0F)), 0.3F, 0.12F);
      } else if (t < 93.0F) {
         return Mth.lerp(smooth(phase(t, 88.0F, 93.0F)), 0.12F, 0.2F);
      } else if (t < 96.0F) {
         return Mth.lerp(phase(t, 93.0F, 96.0F), 0.2F, -0.2F);
      } else {
         return t < 106.0F ? Mth.lerp(smooth(phase(t, 98.0F, 106.0F)), -0.2F, 0.0F) : 0.28F * smooth(phase(t, 108.0F, 118.0F));
      }
   }

   private static float press(float t) {
      float k = phase(t, 28.0F, 50.0F);
      float e = k < 0.5F ? 0.55F * smooth(k / 0.5F) : 0.55F + 0.45F * smooth((k - 0.5F) / 0.5F);
      return e - 0.05F * Mth.sin((float) Math.PI * phase(k, 0.42F, 0.62F));
   }

   private static void victim(CoupDeGrace.State s, float t, float startYaw) {
      CoupDeGrace.Body v = s.victim;
      float yaw = 90.0F * s.side;
      if (t < 14.0F) {
         float lift = smooth(phase(t, 9.0F, 14.0F));
         float y = Mth.rotLerp(smooth(t / 8.0F), startYaw, yaw);
         float r = y * (float) (Math.PI / 180.0);
         Vector3f c = new Vector3f(-Mth.sin(r) * 0.057F, 0.3F, Mth.cos(r) * 0.057F).add(at(0.1F * lift, 0.0F, 0.12F * lift));
         v.turn(y, -90.0F, 0.0F, 0.0F).anchor(1.0556251F, c);
      } else if (t < 28.0F) {
         float k = smooth(phase(t, 14.0F, 28.0F));
         Vector3f from = at(0.1F, 0.057F * s.side, 0.42F);
         Vector3f c = from.lerp(at(0.28F, 0.0F, 1.12F), k, new Vector3f());
         v.turn(yaw, -90.0F + 5.0F * k * Mth.sin(t * 0.45F), 0.0F, 0.0F).anchor(1.0556251F, c);
      } else if (t < 58.0F) {
         float k = press(t);
         Vector3f c = at(Mth.lerp(k, 0.28F, 0.5F), 0.0F, Mth.lerp(k, 1.12F, 1.99F));
         float tremble = smooth(phase(t, 42.0F, 50.0F)) * (1.0F - smooth(phase(t, 56.0F, 58.0F)));
         c.add(0.012F * tremble * Mth.sin(t * 2.9F), 0.012F * tremble * Mth.sin(t * 3.7F), 0.0F);
         float squirm = 1.0F - 0.6F * smooth(phase(t, 50.0F, 58.0F));
         v.turn(yaw, -90.0F + 5.0F * squirm * Mth.sin(t * 0.45F), 0.0F, 8.0F * squirm * Mth.sin(t * 0.33F)).anchor(1.0556251F, c);
      } else if (t < 64.0F) {
         float tau = phase(t, 58.0F, 64.0F);
         float fwd = Mth.lerp((float)Math.pow(tau, 0.8), 0.5F, -0.3F);
         float up = Mth.lerp(tau * tau, 1.99F, 0.15F) + 0.5F * tau * (1.0F - tau);
         v.turn(yaw, -90.0F, 0.0F, -180.0F * s.side * smooth(tau)).anchor(1.0556251F, at(fwd, 0.0F, up));
      } else {
         float bounce = 0.0F;
         if (t < 70.0F) {
            float u = phase(t, 64.0F, 70.0F);
            bounce = 0.07F * Mth.sin((float) Math.PI * Math.min(1.0F, u * 1.7F)) * (1.0F - u);
         }

         float jolt = smooth(phase(t, 96.0F, 98.0F)) * (1.0F - 0.4F * smooth(phase(t, 98.0F, 106.0F)));
         float sink = 0.5F * smooth(phase(t, 191.0F, 216.0F));
         float breath = t < 96.0F ? Mth.sin(t * 0.2F) : 0.0F;
         v.turn(yaw, -90.0F + 150.0F * bounce + breath, 0.0F, s.side * (-180.0F + 14.0F * jolt))
            .anchor(1.0556251F, at(-0.3F - 0.08F * jolt, 0.0F, 0.15F + bounce - sink));
      }
   }

   static void poseActor(HumanoidModel<?> m, CoupDeGrace.State s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float reach = t < 14.0F ? smooth(phase(t, 1.0F, 10.0F)) : (t < 66.0F ? 1.0F : 1.0F - smooth(phase(t, 66.0F, 72.0F)));
      if (reach > 0.0F) {
         Vector3f chest = s.actor.model(s.victim.scene(0.0F, 3.0F, 0.0F));
         Vector3f hips = s.actor.model(s.victim.scene(0.0F, 10.0F, 0.0F));
         hold(m.rightArm, s.side > 0 ? hips : chest, reach);
         hold(m.leftArm, s.side > 0 ? chest : hips, reach);
         float strain = smooth(phase(t, 42.0F, 50.0F)) * (1.0F - smooth(phase(t, 57.0F, 59.0F)));
         m.rightArm.xRot = m.rightArm.xRot + 0.03F * strain * Mth.sin(age * 2.6F);
         m.leftArm.xRot = m.leftArm.xRot + 0.03F * strain * Mth.sin(age * 2.6F + 1.0F);
      }

      float split = smooth(phase(t, 0.0F, 10.0F)) * (1.0F - smooth(phase(t, 14.0F, 20.0F)));
      float brace = smooth(phase(t, 14.0F, 22.0F)) * (1.0F - smooth(phase(t, 58.0F, 61.0F)));
      float lunge = smooth(phase(t, 58.0F, 64.0F)) * (1.0F - smooth(phase(t, 65.0F, 70.0F)));
      m.rightLeg.xRot = -0.3F * split - 0.45F * lunge;
      m.leftLeg.xRot = 0.25F * split + 0.35F * lunge;
      m.rightLeg.zRot = 0.04F + 0.1F * brace;
      m.leftLeg.zRot = -0.04F - 0.1F * brace;
      float walk = walkAmount(t);
      if (walk > 0.0F) {
         float stride = walkPhase(t);
         m.rightLeg.xRot = Mth.lerp(walk, m.rightLeg.xRot, 0.6F * Mth.sin(stride));
         m.leftLeg.xRot = Mth.lerp(walk, m.leftLeg.xRot, -0.6F * Mth.sin(stride));
         m.rightArm.xRot = Mth.lerp(walk, m.rightArm.xRot, -0.4F * Mth.sin(stride));
         m.leftArm.xRot = Mth.lerp(walk, m.leftArm.xRot, 0.4F * Mth.sin(stride));
         m.rightArm.zRot = Mth.lerp(walk, m.rightArm.zRot, 0.06F);
         m.leftArm.zRot = Mth.lerp(walk, m.leftArm.zRot, -0.06F);
      }

      if (t >= 86.0F) {
         boolean right = s.side > 0;
         ModelPart leg = right ? m.rightLeg : m.leftLeg;
         ModelPart other = right ? m.leftLeg : m.rightLeg;
         ModelPart arm = right ? m.rightArm : m.leftArm;
         ModelPart otherArm = right ? m.leftArm : m.rightArm;
         float out = right ? 1.0F : -1.0F;
         float angle;
         if (t < 88.0F) {
            angle = 0.0F;
         } else if (t < 93.0F) {
            angle = Mth.lerp(smooth(phase(t, 88.0F, 93.0F)), 0.0F, 0.95F);
         } else if (t < 96.0F) {
            angle = Mth.lerp(square(phase(t, 93.0F, 96.0F)), 0.95F, -1.05F);
         } else if (t < 99.0F) {
            angle = Mth.lerp(smooth(phase(t, 96.0F, 99.0F)), -1.05F, -1.3F);
         } else {
            angle = Mth.lerp(smooth(phase(t, 99.0F, 107.0F)), -1.3F, 0.0F);
         }

         float swing = t < 93.0F ? 0.0F : (t < 96.0F ? phase(t, 93.0F, 96.0F) : 1.0F);
         float balance = smooth(phase(t, 88.0F, 93.0F)) * (1.0F - smooth(phase(t, 100.0F, 110.0F)));
         leg.xRot = angle;
         leg.zRot = 0.04F * out;
         other.xRot = 0.12F * balance;
         other.zRot = -(0.06F * balance + 0.04F) * out;
         arm.xRot = Mth.lerp(swing, -0.4F, 0.6F) * balance;
         otherArm.xRot = Mth.lerp(swing, 0.4F, -0.6F) * balance;
         arm.zRot = 0.3F * out * balance;
         otherArm.zRot = -0.3F * out * balance;
         float dust = smooth(phase(t, 108.0F, 112.0F)) * (1.0F - smooth(phase(t, 114.0F, 122.0F)));
         if (dust > 0.0F) {
            float rub = Mth.sin(age * 1.6F);
            m.rightArm.xRot = Mth.lerp(dust, m.rightArm.xRot, -0.75F + 0.08F * rub);
            m.leftArm.xRot = Mth.lerp(dust, m.leftArm.xRot, -0.75F - 0.08F * rub);
            m.rightArm.yRot = Mth.lerp(dust, 0.0F, -0.45F + 0.1F * rub);
            m.leftArm.yRot = Mth.lerp(dust, 0.0F, 0.45F + 0.1F * rub);
            m.rightArm.zRot = Mth.lerp(dust, m.rightArm.zRot, 0.0F);
            m.leftArm.zRot = Mth.lerp(dust, m.leftArm.zRot, 0.0F);
         }
      }

      look(m.head, s.actor.model(s.victim.scene(0.0F, -4.0F, 0.0F)), 1.1F, 0.9F);
   }

   static void poseVictim(HumanoidModel<?> m, CoupDeGrace.State s, float age) {
      float t = s.t;
      stand(m);
      float wave = age * 0.8F;
      if (t < 58.0F) {
         float up = smooth(phase(t, 24.0F, 34.0F));
         m.rightArm.xRot = Mth.lerp(up, -1.35F + 0.45F * Mth.sin(wave), 0.3F * Mth.sin(wave));
         m.leftArm.xRot = Mth.lerp(up, -1.35F + 0.45F * Mth.sin(wave + 2.0F), 0.3F * Mth.sin(wave + 2.0F));
         m.rightArm.zRot = Mth.lerp(up, 0.35F, 1.2F + 0.25F * Mth.sin(wave * 1.3F));
         m.leftArm.zRot = -Mth.lerp(up, 0.35F, 1.2F + 0.25F * Mth.sin(wave * 1.3F + 1.5F));
         float kick = t < 50.0F ? 1.0F : Mth.lerp(smooth(phase(t, 50.0F, 58.0F)), 1.0F, 0.4F);
         m.rightLeg.xRot = 0.15F + 0.45F * kick * Mth.sin(age * 0.9F);
         m.leftLeg.xRot = 0.15F + 0.45F * kick * Mth.sin(age * 0.9F + 2.6F);
         m.rightLeg.zRot = 0.08F + 0.1F * kick * Mth.abs(Mth.sin(age * 0.6F));
         m.leftLeg.zRot = -0.08F - 0.1F * kick * Mth.abs(Mth.sin(age * 0.6F + 1.0F));
         m.head.xRot = -0.7F + 0.08F * Mth.sin(age * 1.7F);
         m.head.yRot = 0.2F * Mth.sin(age * 0.3F);
         if (t < 14.0F) {
            float lift = smooth(phase(t, 9.0F, 14.0F));
            m.rightArm.xRot = Mth.lerp(lift, -3.05F, m.rightArm.xRot);
            m.leftArm.xRot = Mth.lerp(lift, -3.05F, m.leftArm.xRot);
            m.rightArm.zRot = Mth.lerp(lift, -0.35F, m.rightArm.zRot);
            m.leftArm.zRot = Mth.lerp(lift, 0.35F, m.leftArm.zRot);
            m.rightLeg.xRot = Mth.lerp(lift, 0.04F, m.rightLeg.xRot);
            m.leftLeg.xRot = Mth.lerp(lift, 0.04F, m.leftLeg.xRot);
            m.rightLeg.zRot = Mth.lerp(lift, 0.07F, m.rightLeg.zRot);
            m.leftLeg.zRot = Mth.lerp(lift, -0.07F, m.leftLeg.zRot);
            m.head.xRot = Mth.lerp(lift, -0.3F, m.head.xRot);
            m.head.yRot *= lift;
         }
      } else if (t < 64.0F) {
         float k = smooth(phase(t, 58.0F, 61.0F));
         m.rightArm.xRot = Mth.lerp(k, 0.3F * Mth.sin(wave), -0.2F);
         m.leftArm.xRot = Mth.lerp(k, 0.3F * Mth.sin(wave + 2.0F), -0.2F);
         m.rightArm.zRot = Mth.lerp(k, 1.2F, 1.35F);
         m.leftArm.zRot = -Mth.lerp(k, 1.2F, 1.35F);
         m.rightLeg.xRot = Mth.lerp(k, 0.15F, 0.0F);
         m.leftLeg.xRot = Mth.lerp(k, 0.15F, 0.0F);
         m.rightLeg.zRot = 0.08F + 0.14F * k;
         m.leftLeg.zRot = -0.08F - 0.14F * k;
         m.head.xRot = Mth.lerp(k, -0.7F, 0.3F);
      } else {
         float land = smooth(phase(t, 64.0F, 66.0F));
         float beg = smooth(phase(t, 72.0F, 82.0F)) * (1.0F - smooth(phase(t, 95.0F, 97.0F)));
         float dead = smooth(phase(t, 96.0F, 98.0F));
         boolean rightNear = s.side > 0;
         ModelPart near = rightNear ? m.rightArm : m.leftArm;
         ModelPart far = rightNear ? m.leftArm : m.rightArm;
         float out = rightNear ? 1.0F : -1.0F;
         far.xRot = -0.1F;
         far.zRot = -out * Mth.lerp(land, 1.35F, 1.25F + 0.1F * dead);
         near.xRot = Mth.lerp(beg, -0.1F, -1.35F + 0.08F * Mth.sin(age * 1.3F));
         near.zRot = out * Mth.lerp(land, 1.35F, 0.55F);
         near.zRot = Mth.lerp(beg, near.zRot, 0.3F * out);
         near.zRot = Mth.lerp(dead * (1.0F - beg), near.zRot, 0.75F * out);
         float twitch = t < 96.0F ? 0.0F : Mth.sin(phase(t, 101.0F, 107.0F) * (float) Math.PI) * 0.3F;
         m.rightLeg.xRot = t > 71.0F && t < 76.0F ? -0.25F * Mth.sin((t - 71.0F) / 5.0F * (float) Math.PI) : 0.0F;
         m.leftLeg.xRot = -twitch;
         m.rightLeg.zRot = 0.14F + 0.04F * dead;
         m.leftLeg.zRot = -0.14F - 0.04F * dead;
         look(m.head, s.victim.model(s.actor.scene(0.0F, -4.0F, 0.0F)), 0.9F, 0.5F);
         float follow = smooth(phase(t, 70.0F, 76.0F));
         m.head.xRot = Mth.lerp(follow, 0.1F, m.head.xRot);
         m.head.yRot = Mth.lerp(follow, 0.0F, m.head.yRot);
         m.head.xRot = Mth.lerp(dead, m.head.xRot, 0.15F);
         m.head.yRot = Mth.lerp(smooth(phase(t, 96.0F, 97.5F)), m.head.yRot, -1.2F * s.side);
      }
   }

   static void hold(ModelPart arm, Vector3f target, float amount) {
      float x = arm.xRot;
      float z = arm.zRot;
      aim(arm, target);
      arm.xRot = Mth.lerp(amount, x, arm.xRot);
      arm.zRot = Mth.lerp(amount, z, arm.zRot);
   }

   static void stand(HumanoidModel<?> m) {
      m.head.setPos(0.0F, 0.0F, 0.0F);
      m.body.setPos(0.0F, 0.0F, 0.0F);
      m.rightArm.setPos(-5.0F, 2.0F, 0.0F);
      m.leftArm.setPos(5.0F, 2.0F, 0.0F);
      m.rightLeg.setPos(-1.9F, 12.0F, 0.1F);
      m.leftLeg.setPos(1.9F, 12.0F, 0.1F);

      for (ModelPart p : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
         p.setRotation(0.0F, 0.0F, 0.0F);
      }
   }

   static void bend(HumanoidModel<?> m, float lean) {
      float c = Mth.cos(lean);
      float s = Mth.sin(lean);
      m.body.xRot = lean;
      m.body.y = 12.0F - 12.0F * c;
      m.body.z = -12.0F * s;
      m.head.y = m.body.y;
      m.head.z = m.body.z;
      m.rightArm.y = m.body.y + 2.0F * c;
      m.rightArm.z = m.body.z + 2.0F * s;
      m.leftArm.y = m.rightArm.y;
      m.leftArm.z = m.rightArm.z;
   }

   static void aim(ModelPart part, Vector3f target) {
      float dx = target.x - part.x;
      float dy = target.y - part.y;
      float dz = target.z - part.z;
      float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(len < 0.001F)) {
         dx /= len;
         dy /= len;
         dz /= len;
         float c = Mth.sqrt(dx * dx + dy * dy);
         if (dy < 0.0F) {
            c = -c;
         }

         part.xRot = (float)Mth.atan2(dz, c);
         part.yRot = 0.0F;
         part.zRot = Math.abs(c) < 1.0E-4F ? 0.0F : (float)Mth.atan2(-dx / c, dy / c);
      }
   }

   static void look(ModelPart head, Vector3f target, float maxYaw, float maxPitch) {
      float dx = target.x - head.x;
      float dy = target.y - head.y + 4.0F;
      float dz = target.z - head.z;
      float h = Mth.sqrt(dx * dx + dz * dz);
      if (!(h + Math.abs(dy) < 0.001F)) {
         head.yRot = Mth.clamp((float)Mth.atan2(-dx, -dz), -maxYaw, maxYaw);
         head.xRot = Mth.clamp((float)Mth.atan2(dy, h), -maxPitch, maxPitch);
         head.zRot = 0.0F;
      }
   }

   static CoupDeGrace.Shot shot(CoupDeGrace.State s) {
      float t = s.t;
      float side = s.side;
      if (t < 14.0F) {
         return new CoupDeGrace.Shot(at(-1.7F, -1.4F * side, 1.25F), at(0.35F, 0.0F, 0.5F), 0, true, 3.0F);
      }

      if (t < 58.0F) {
         float k = phase(t, 14.0F, 58.0F);
         Vector3f body = s.victim.scene(0.0F, 6.0F, 0.0F);
         Vector3f target = body.lerp(at(0.6F, 0.0F, 1.2F), 0.4F);
         return new CoupDeGrace.Shot(at(-2.1F + 0.35F * k, -0.9F * side, 0.4F - 0.05F * k), target, 1, false, 2.5F);
      }

      if (t < 70.0F) {
         return new CoupDeGrace.Shot(at(-2.4F, -2.3F * side, 2.5F), at(-0.05F, 0.1F * side, 0.45F), 2, true, 6.0F);
      }

      if (t < 88.0F) {
         return new CoupDeGrace.Shot(at(-1.45F, 2.0F * side, 1.3F), at(0.1F, 0.45F * side, 0.6F), 3, true, 3.0F);
      }

      float k = smooth(phase(t, 100.0F, 130.0F));
      Vector3f pos = at(0.0F, 2.6F * side, 0.62F).lerp(at(0.35F, 2.1F * side, 3.1F), k);
      Vector3f target = at(0.0F, 0.62F * side, 0.42F).lerp(at(-0.3F, 0.2F * side, 0.2F), k);
      return new CoupDeGrace.Shot(pos, target, 4, true, 4.0F);
   }

   private static float walkAmount(float t) {
      return smooth(phase(t, 70.0F, 73.0F)) * (1.0F - smooth(phase(t, 83.0F, 86.0F)));
   }

   private static float walkPhase(float t) {
      return (t - 70.0F) * (float) (Math.PI / 7);
   }

   static float phase(float t, float from, float to) {
      return Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
   }

   static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   private static float square(float x) {
      return x * x;
   }

   static final class Body {
      final Vector3f pos = new Vector3f();
      final Quaternionf rot = new Quaternionf();

      CoupDeGrace.Body turn(float yaw, float pitch, float roll, float twist) {
         this.rot
            .rotationY((180.0F - yaw) * (float) (Math.PI / 180.0))
            .rotateX(pitch * (float) (Math.PI / 180.0))
            .rotateZ(roll * (float) (Math.PI / 180.0))
            .rotateY(twist * (float) (Math.PI / 180.0));
         return this;
      }

      CoupDeGrace.Body feet(Vector3f p) {
         this.pos.set(p);
         return this;
      }

      CoupDeGrace.Body anchor(float height, Vector3f p) {
         Vector3f a = this.rot.transform(new Vector3f(0.0F, height, 0.0F));
         this.pos.set(p).sub(a);
         return this;
      }

      Vector3f scene(float px, float py, float pz) {
         return this.rot.transform(new Vector3f(-px * 0.05859375F, 1.4071876F - py * 0.05859375F, pz * 0.05859375F)).add(this.pos);
      }

      Vector3f scene(Vector3f model) {
         return this.scene(model.x, model.y, model.z);
      }

      CoupDeGrace.Body anchorPoint(Vector3f modelPx, Vector3f p) {
         Vector3f local = new Vector3f(-modelPx.x * 0.05859375F, 1.4071876F - modelPx.y * 0.05859375F, modelPx.z * 0.05859375F);
         this.pos.set(p).sub(this.rot.transform(local));
         return this;
      }

      Vector3f model(Vector3f p) {
         Vector3f v = this.rot.transformInverse(new Vector3f(p).sub(this.pos));
         return v.set(-v.x / 0.05859375F, (1.4071876F - v.y) / 0.05859375F, v.z / 0.05859375F);
      }

      Vector3f axis(float height) {
         return this.rot.transform(new Vector3f(0.0F, height, 0.0F)).add(this.pos);
      }
   }

   static final class Shot {
      final Vector3f pos;
      final Vector3f target;
      final int index;
      final boolean cut;
      final float speed;
      final float fov;

      Shot(Vector3f pos, Vector3f target, int index, boolean cut, float speed) {
         this(pos, target, index, cut, speed, 0.0F);
      }

      Shot(Vector3f pos, Vector3f target, int index, boolean cut, float speed, float fov) {
         this.pos = pos;
         this.target = target;
         this.index = index;
         this.cut = cut;
         this.speed = speed;
         this.fov = fov;
      }
   }

   static class State {
      final float t;
      final int side;
      final CoupDeGrace.Body actor = new CoupDeGrace.Body();
      final CoupDeGrace.Body victim = new CoupDeGrace.Body();
      float lean;
      float actorFwd;
      float actorSide;

      State(float t, int side) {
         this.t = t;
         this.side = side;
      }
   }
}
