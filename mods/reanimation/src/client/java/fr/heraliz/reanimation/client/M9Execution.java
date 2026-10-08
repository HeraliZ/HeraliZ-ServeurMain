package fr.heraliz.reanimation.client;

import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class M9Execution extends Script {
   static final M9Execution INSTANCE = new M9Execution();
   static final float ARRIVE = 5.0F;
   static final float TOSS = 14.0F;
   static final float CATCH = 20.0F;
   static final float REVERSED = 26.0F;
   static final float KICK = 32.0F;
   static final float ROLLED = 40.0F;
   static final float KNEEL = 48.0F;
   static final float[] TAPS = new float[]{51.0F, 55.0F};
   static final float RAISE = 60.0F;
   static final float CAUGHT = 68.0F;
   static final float SLOW = 86.0F;
   static final float SNAP = 94.0F;
   static final float PULL = 106.0F;
   static final float SHOW = 1.35F;
   static final Vector3f SPOT = at(0.85F, -0.05F, 0.0F);
   static final Vector3f KICK_AT = at(0.8F, 0.05F, 0.0F);
   static final Vector3f KNEEL_AT = at(0.56F, 0.16F, 0.0F);
   static final Vector3f STAND_AT = at(0.85F, 0.15F, 0.0F);
   static final Vector3f SHOWN = new Vector3f(-3.5F, -2.0F, -10.0F);
   static final Vector3f HANDLE = new Vector3f(-2.0F, 9.5F, -0.5F);
   static final Vector3f HEART = new Vector3f(1.6F, 3.5F, 0.8F);
   static final Vector3f CHEST = new Vector3f(1.6F, 3.5F, -2.0F);

   private M9Execution() {
   }

   @Override
   Blade blade() {
      return Blade.M9;
   }

   @Override
   float scale(float t) {
      return Keys.key(t, 0.0F, 1.0F, 6.0F, 1.35F, 114.0F, 1.35F, 118.0F, 1.1F);
   }

   @Override
   float slowmo(float t) {
      return t >= 86.0F && t < 94.5F ? 1.0F : 0.0F;
   }

   @Override
   float spin(float t) {
      return Keys.key(t, 21.0F, 0.0F, 26.0F, (float) Math.PI, 110.0F, (float) Math.PI, 116.0F, (float) (Math.PI * 2));
   }

   @Override
   Quaternionf wrist(float t) {
      Vector3f along = new Vector3f(this.blade().tip).sub(this.blade().pivot).normalize();
      return new Quaternionf().rotationAxis(Keys.key(t, 6.0F, 0.0F, 12.0F, (float) Math.PI, 13.5F, (float) (Math.PI * 2)), along);
   }

   @Override
   Vector3f shift(float t) {
      float reversed = CoupDeGrace.smooth(CoupDeGrace.phase(t, 21.0F, 26.0F)) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 110.0F, 116.0F)));
      return reversed <= 0.0F ? new Vector3f() : Knife.regrip(this.blade(), HANDLE, this.spin(t), this.wrist(t), this.scale(t)).mul(reversed);
   }

   static float hold(float t) {
      return Keys.key(t, 68.0F, 0.32F, 76.0F, 0.26F, 82.0F, 0.22F, 86.0F, 0.2F, 94.0F, 0.11F)
         + 0.012F * Mth.sin(t * 2.9F) * Keys.window(t, 68.0F, 94.0F, 2.0F);
   }

   Vector3f heart(Script.Frame f) {
      return f.victim.scene(HEART);
   }

   Matrix4f inChest(Script.Frame f) {
      return this.heldInPart(f, 95.999F, Knife.Part.BODY);
   }

   Vector3f chestGrip(Script.Frame f) {
      return Knife.through(this.victimPart(f, Knife.Part.BODY).mul(this.inChest(f)), HANDLE);
   }

   @Nullable
   @Override
   Vector3f killPoint(Script.Frame f) {
      return this.heart(f);
   }

   static float roll(float t) {
      return 180.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 32.5F, 40.0F));
   }

   @Override
   void victim(Script.Frame f, float t) {
      float roll = roll(t);
      float onBack = CoupDeGrace.smooth(CoupDeGrace.phase(t, 32.0F, 40.0F));
      float height = Mth.lerp(onBack, PRONE.y, 0.15F) + 0.25F * Mth.sin(roll * (float) (Math.PI / 180.0));
      float jolt = Keys.jolt(t, 96.0F, 10.0F, 2.2F);
      float crawl = 0.05F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 9.0F, 14.0F)) + 0.04F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 20.0F, 25.0F));
      float squirm = 3.0F * Mth.sin(t * 0.4F) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 29.0F, 32.0F)));
      float writhe = 4.0F * Mth.sin(t * 0.9F) * Keys.window(t, 68.0F, 96.0F, 3.0F);
      prone(f, t, roll + squirm + writhe + 4.0F * jolt, at(-0.22F * onBack, crawl, height - PRONE.y + 0.01F * Math.abs(jolt)));
      if (t >= 96.0F) {
         f.wounds
            .add(
               new Script.Wound(
                  Knife.Part.BODY,
                  new Vector3f(0.3F, 2.2F, -1.0F),
                  new Vector3f(2.9F, 4.8F, -1.0F),
                  new Vector3f(0.0F, 0.0F, -1.0F),
                  2.4F,
                  CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 98.0F))
               )
            );
      }
   }

   @Override
   void actor(Script.Frame f, float t) {
      CoupDeGrace.Body a = f.actor;
      Vector3f feet = new Vector3f();
      float yaw;
      if (t < 5.0F) {
         yaw = entry(f, t, SPOT, 5.0F, feet);
      } else {
         feet = Keys.vec(
            t,
            5.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            28.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            31.0F,
            KICK_AT.x,
            KICK_AT.y,
            KICK_AT.z,
            41.0F,
            KICK_AT.x,
            KICK_AT.y,
            KICK_AT.z,
            46.0F,
            KNEEL_AT.x,
            KNEEL_AT.y,
            KNEEL_AT.z,
            108.0F,
            KNEEL_AT.x,
            KNEEL_AT.y,
            KNEEL_AT.z,
            112.0F,
            STAND_AT.x,
            STAND_AT.y,
            STAND_AT.z
         );
         yaw = 180.0F;
         f.walk = Keys.window(t, 28.0F, 31.0F, 1.0F) * 0.5F + Keys.window(t, 41.0F, 46.0F, 1.5F) * 0.5F + Keys.window(t, 108.0F, 112.0F, 1.5F) * 0.4F;
         f.stride = t * 0.95F;
      }

      yaw = Mth.rotLerp(Keys.window(t, 42.0F, 110.0F, 3.0F), yaw, yawToward(feet, f.victim.scene(0.0F, 4.0F, 0.0F)));
      f.kneel = Keys.key(t, 43.0F, 0.0F, 48.0F, 1.0F, 108.0F, 1.0F, 111.0F, 0.0F);
      f.drop = Rig.kneelDrop(f.kneel);
      f.lean = Keys.key(
         t,
         0.0F,
         0.0F,
         8.0F,
         0.04F,
         26.0F,
         0.0F,
         31.0F,
         -0.15F,
         33.0F,
         0.1F,
         36.0F,
         0.0F,
         48.0F,
         0.45F,
         60.0F,
         0.3F,
         64.0F,
         -0.1F,
         68.0F,
         0.45F,
         94.0F,
         0.5F,
         96.0F,
         0.75F,
         106.0F,
         0.7F,
         109.0F,
         0.2F,
         118.0F,
         0.0F
      );
      feet.add(0.0F, -f.drop, 0.0F);
      a.turn(yaw, 0.0F, 0.0F, 0.0F).feet(feet);
      f.actorFwd = feet.z;
      f.actorSide = -feet.x;
      Blade blade = this.blade();
      Vector3f tip = this.tipTarget(f, t);
      if (tip != null) {
         float on = Keys.window(t, 49.0F, 59.0F, 1.5F) + Keys.window(t, 65.0F, 97.0F, 1.5F);
         f.lean = Mth.lerp(Math.min(1.0F, on), f.lean, solveLean(a, tip, this.reach(blade.tip, t), f.lean));
      } else if (t >= 96.0F && t < 108.0F) {
         Vector3f grip = this.chestGrip(f);
         f.lean = Mth.lerp(
            1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 106.0F, 108.0F)), f.lean, solveLean(a, grip, this.inHand(HANDLE, t).length(), f.lean)
         );
      }
   }

   @Nullable
   Vector3f tipTarget(Script.Frame f, float t) {
      Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
      if (t >= 49.0F && t < 59.0F) {
         float lift = 0.12F + 0.2F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 56.5F, 59.0F));

         for (float tap : TAPS) {
            lift -= 0.12F * Keys.window(t, tap - 1.5F, tap + 1.5F, 1.5F);
         }

         return f.victim.scene(CHEST).add(new Vector3f(up).mul(Math.max(0.005F, lift)));
      } else if (t >= 65.0F && t < 96.0F) {
         float h = t < 68.0F ? Mth.lerp(CoupDeGrace.smooth(CoupDeGrace.phase(t, 65.0F, 68.0F)), 0.55F, hold(68.0F)) : hold(t);
         Vector3f over = f.victim.scene(CHEST).add(new Vector3f(up).mul(h));
         return t < 94.0F ? over : over.lerp(this.heart(f), Keys.strike(t, 94.0F, 96.0F));
      } else {
         return null;
      }
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      k.blood = Keys.key(t, 95.5F, 0.0F, 96.0F, 1.0F, 112.0F, 1.0F, 113.0F, 0.35F);
      k.trail = Keys.window(t, 13.0F, 27.0F, 1.0F) * 0.8F
         + Keys.window(t, 60.0F, 69.0F, 1.5F)
         + Keys.window(t, 93.5F, 96.5F, 0.5F) * 1.5F
         + Keys.window(t, 106.0F, 109.0F, 1.0F)
         + Keys.window(t, 111.0F, 118.0F, 1.5F) * 0.8F;
      if (t >= 14.0F && t < 20.0F) {
         k.scene(Knife.flight(this.blade(), this.heldAt(f, 14.0F), this.heldAt(f, 20.0F), CoupDeGrace.phase(t, 14.0F, 20.0F), 0.5F, 1.0F));
      } else if (t >= 96.0F && t < 106.0F) {
         k.stuck(Knife.Part.BODY, this.inChest(f));
      } else if (t >= 106.0F && t < 108.0F) {
         Matrix4f stuck = this.victimPart(this.other(f, 105.99F), Knife.Part.BODY).mul(this.inChest(f));
         k.scene(this.pulled(f, t, stuck, 106.0F, 108.0F));
      }
   }

   @Override
   public void poseActor(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      CoupDeGrace.bend(m, f.lean);
      CoupDeGrace.Body a = f.actor;
      Blade blade = this.blade();
      Rig.kneel(m.rightLeg, m.leftLeg, f.kneel, 0.12F);
      Rig.walk(m.rightLeg, m.leftLeg, m.rightArm, m.leftArm, f.stride, f.walk, false);
      m.rightArm.xRot = -0.3F + 0.4F * f.walk * Mth.sin(f.stride);
      m.rightArm.zRot = 0.12F;
      m.leftArm.xRot = -0.2F - 0.4F * f.walk * Mth.sin(f.stride);
      m.leftArm.zRot = -0.1F;
      float kick = Keys.key(t, 29.0F, 0.0F, 31.0F, -0.35F, 32.5F, 1.0F, 35.0F, 0.0F);
      if (kick != 0.0F) {
         m.rightLeg.xRot = -1.25F * Math.max(0.0F, kick) + 0.5F * Math.max(0.0F, -kick);
         m.leftArm.xRot = m.leftArm.xRot - 0.5F * Math.abs(kick);
         m.leftArm.zRot = m.leftArm.zRot - 0.4F * Math.abs(kick);
      }

      Vector3f tip = this.tipTarget(f, t);
      if (t >= 2.0F && t < 14.0F) {
         Rig.aim(
            m.rightArm,
            blade.pivot,
            new Vector3f(SHOWN).add(0.0F, -3.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 12.0F, 14.0F)), 0.0F),
            0.0F,
            Keys.key(t, 2.0F, 0.0F, 6.0F, 1.0F)
         );
      } else if (t >= 14.0F && t < 30.0F) {
         Vector3f at = new Vector3f(SHOWN)
            .add(
               0.0F,
               -3.0F + 2.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 16.0F, 20.0F)) - 2.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 20.0F, 26.0F)),
               0.0F
            );
         Rig.aim(m.rightArm, blade.pivot, at, 0.0F, Keys.key(t, 14.0F, 1.0F, 28.0F, 1.0F, 30.0F, 0.0F));
      } else if (tip != null) {
         Rig.aim(
            m.rightArm,
            this.inHand(blade.tip, t),
            a.model(tip),
            0.0F,
            t < 59.0F ? Keys.window(t, 47.5F, 59.0F, 1.5F) : CoupDeGrace.smooth(CoupDeGrace.phase(t, 63.0F, 65.0F))
         );
      } else if (t >= 96.0F && t < 110.0F) {
         Vector3f grip = this.chestGrip(f);
         Vector3f aim = t < 106.0F
            ? a.model(grip)
            : a.model(grip).lerp(a.model(new Vector3f(grip).add(0.0F, 0.5F, 0.0F)), CoupDeGrace.smooth(CoupDeGrace.phase(t, 106.0F, 108.0F)));
         Rig.aim(m.rightArm, this.inHand(HANDLE, t), aim, 0.0F, 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 108.0F, 110.0F)));
      } else if (t >= 110.0F) {
         Rig.aim(m.rightArm, blade.pivot, new Vector3f(SHOWN).add(-1.0F, 4.0F, 1.0F), 0.0F, Keys.window(t, 110.0F, 122.0F, 2.0F));
      }

      if (t >= 59.0F && t < 65.0F) {
         Vector3f high = new Vector3f(-3.0F, -19.0F, -4.0F);
         float up = Keys.window(t, 58.0F, 66.0F, 2.5F);
         Rig.aim(m.rightArm, this.inHand(HANDLE, t), high, 0.0F, up);
      }

      float both = Keys.window(t, 59.0F, 107.0F, 2.0F);
      if (both > 0.0F) {
         Vector3f fist = Rig.point(m.rightArm, new Vector3f(this.inHand(HANDLE, t)).add(0.0F, 0.0F, -1.5F));
         Rig.aim(m.leftArm, Rig.LEFT_HAND, fist, 0.0F, both);
      } else if (t >= 49.0F && t < 59.0F) {
         Rig.aim(m.leftArm, Rig.LEFT_HAND, a.model(f.victim.scene(-2.0F, 2.0F, -2.5F)), 0.0F, Keys.window(t, 47.0F, 60.0F, 2.0F));
      } else if (t >= 14.0F && t < 22.0F) {
         float open = Keys.window(t, 14.0F, 22.0F, 2.0F);
         m.leftArm.xRot = Mth.lerp(open, m.leftArm.xRot, -0.45F);
         m.leftArm.zRot = Mth.lerp(open, m.leftArm.zRot, -0.45F);
      }

      Vector3f lookAt;
      if (t >= 3.0F && t < 30.0F) {
         lookAt = f.knife.holder == Knife.Holder.SCENE ? a.model(Knife.through(f.knife.matrix, blade.pivot)) : Rig.point(m.rightArm, blade.pivot);
      } else if (t >= 59.0F && t < 64.0F) {
         lookAt = Rig.point(m.rightArm, blade.pivot);
      } else {
         lookAt = a.model(f.victim.scene(0.0F, 2.0F, 0.0F));
      }

      CoupDeGrace.look(m.head, lookAt, 1.2F, 1.1F);
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      float onBack = CoupDeGrace.smooth(CoupDeGrace.phase(t, 32.0F, 40.0F));
      proneLimbs(m, f, age, Keys.window(t, 8.0F, 15.0F, 2.5F), Keys.window(t, 19.0F, 26.0F, 2.5F), 0.0F);
      if (onBack > 0.0F) {
         float tucked = Keys.window(t, 31.0F, 41.0F, 2.0F);
         float out = CoupDeGrace.smooth(CoupDeGrace.phase(t, 38.0F, 42.0F));
         m.rightArm.zRot = Mth.lerp(tucked, m.rightArm.zRot, 0.0F);
         m.leftArm.zRot = Mth.lerp(tucked, m.leftArm.zRot, 0.0F);
         m.rightArm.xRot = Mth.lerp(out, m.rightArm.xRot, -0.2F);
         m.leftArm.xRot = Mth.lerp(out, m.leftArm.xRot, -0.2F);
         m.rightArm.zRot = Mth.lerp(out, m.rightArm.zRot, 0.9F);
         m.leftArm.zRot = Mth.lerp(out, m.leftArm.zRot, -0.9F);
         float kicking = Keys.window(t, 40.0F, 98.0F, 3.0F);
         m.rightLeg.xRot = Mth.lerp(onBack, m.rightLeg.xRot, -0.25F * kicking * Math.abs(Mth.sin(age * 0.8F)));
         m.leftLeg.xRot = Mth.lerp(onBack, m.leftLeg.xRot, -0.25F * kicking * Math.abs(Mth.sin(age * 0.8F + 1.9F)));
         m.rightLeg.zRot = 0.08F;
         m.leftLeg.zRot = -0.08F;
         m.head.xRot = Mth.lerp(onBack, m.head.xRot, 0.35F + 0.25F * Keys.window(t, 50.0F, 96.0F, 3.0F));
         m.head.yRot = Mth.lerp(onBack, m.head.yRot, 0.0F);
      }

      float fight = Keys.window(t, 65.0F, 97.0F, 2.5F);
      if (fight > 0.0F) {
         Vector3f wrists = f.victim.scene(CHEST).add(0.0F, hold(Math.min(t, 94.0F)) + 0.38F, 0.0F);
         float shake = 0.6F * Mth.sin(age * 3.3F) * Keys.window(t, 68.0F, 96.0F, 2.0F);
         Rig.aim(m.rightArm, Rig.HAND, f.victim.model(wrists).add(-2.0F + shake, 0.0F, 0.0F), 0.0F, fight);
         Rig.aim(m.leftArm, Rig.LEFT_HAND, f.victim.model(wrists).add(2.0F - shake, 0.0F, 0.0F), 0.0F, fight);
      }

      float dead = CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 99.0F));
      if (dead > 0.0F) {
         m.rightArm.xRot = Mth.lerp(dead, m.rightArm.xRot, -0.15F);
         m.leftArm.xRot = Mth.lerp(dead, m.leftArm.xRot, -0.15F);
         m.rightArm.yRot = Mth.lerp(dead, m.rightArm.yRot, 0.0F);
         m.leftArm.yRot = Mth.lerp(dead, m.leftArm.yRot, 0.0F);
         m.rightArm.zRot = Mth.lerp(dead, m.rightArm.zRot, 1.1F);
         m.leftArm.zRot = Mth.lerp(dead, m.leftArm.zRot, -1.1F);
         m.head.xRot = Mth.lerp(dead, m.head.xRot, 0.1F);
         m.head.yRot = Mth.lerp(dead, m.head.yRot, 0.5F);
      }

      if (onBack > 0.5F) {
         Rig.keepHeadUp(m.head, 2.4F);
      }
   }

   @Override
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      Blade blade = this.blade();
      if (t < 6.0F) {
         return new CoupDeGrace.Shot(at(2.3F, 1.7F, 1.7F), at(0.3F, -0.1F, 0.6F), 0, true, 3.0F);
      } else if (t < 30.0F) {
         Matrix4f knife = this.knifeInScene(f);
         Vector3f pivot = knife != null ? Knife.through(knife, blade.pivot) : f.actor.scene(SHOWN);
         Vector3f eyes = f.actor.scene(0.0F, -4.0F, 0.0F);
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 6.0F, 30.0F));
         Vector3f pos = f.actor
            .scene(0.0F, -3.0F, 0.0F)
            .add(f.actor.scene(6.0F, -3.0F, -40.0F).sub(f.actor.scene(0.0F, -3.0F, 0.0F)).mul(Mth.lerp(k, 1.0F, 0.9F)));
         return new CoupDeGrace.Shot(pos, new Vector3f(pivot).lerp(eyes, 0.25F), 1, true, 6.0F, Mth.lerp(k, 34.0F, 28.0F));
      } else if (t < 44.0F) {
         return new CoupDeGrace.Shot(at(-1.6F, -0.9F, 0.75F), at(0.25F, 0.05F, 0.45F), 2, true, 3.0F, 52.0F);
      } else if (t < 59.0F) {
         Vector3f chest = f.victim.scene(CHEST);
         return new CoupDeGrace.Shot(new Vector3f(chest).add(at(-0.35F, 1.5F, 1.2F)), new Vector3f(chest).add(0.0F, 0.12F, 0.0F), 3, true, 4.0F, 38.0F);
      } else if (t < 68.0F) {
         return new CoupDeGrace.Shot(at(-0.95F, 0.55F, 0.22F), f.actor.scene(-1.0F, -12.0F, -4.0F), 4, true, 4.0F, 56.0F);
      } else if (t < 86.0F) {
         Vector3f chest = f.victim.scene(CHEST);
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 68.0F, 86.0F));
         Vector3f pos = new Vector3f(chest).add(at(-0.45F, -1.05F, 0.6F)).lerp(new Vector3f(chest).add(at(-0.4F, -0.85F, 0.55F)), k);
         return new CoupDeGrace.Shot(pos, new Vector3f(chest).add(0.0F, 0.28F, 0.0F), 5, true, 3.0F, Mth.lerp(k, 46.0F, 40.0F));
      } else if (t < 102.0F) {
         Vector3f face = f.victim.scene(0.0F, -4.0F, 0.0F);
         Vector3f chest = f.victim.scene(CHEST);
         Vector3f pos = new Vector3f(face).add(at(-0.2F, 0.6F, 0.75F));
         Vector3f aim = new Vector3f(chest).add(0.0F, 0.25F, 0.0F).lerp(f.actor.scene(0.0F, -4.0F, 0.0F), t < 96.0F ? 0.35F : 0.15F);
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 86.0F, 94.0F));
         return new CoupDeGrace.Shot(pos, aim, 6, true, t < 96.0F ? 3.0F : 7.0F, Mth.lerp(k, 52.0F, 42.0F));
      } else {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 102.0F, 128.0F));
         Vector3f pos = at(-1.8F, 1.6F, 1.1F).lerp(at(-2.4F, 2.2F, 2.6F), k);
         return new CoupDeGrace.Shot(pos, at(0.3F, 0.2F, 0.5F), 7, true, 2.0F);
      }
   }

   @Override
   public Vector3f end(int side) {
      return new Vector3f(STAND_AT);
   }

   @Override
   public Vector3f corpse(int side) {
      return new Vector3f(SUPINE);
   }

   @Override
   public void tick(ExecutionScenes.Scene s, int before, int now) {
      ScriptFx.m9(s, before, now);
   }
}
