package fr.heraliz.reanimation.client;

import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class KarambitDance extends Script {
   static final KarambitDance INSTANCE = new KarambitDance();
   static final float ARRIVE = 5.0F;
   static final float TOSS = 26.0F;
   static final float CATCH = 36.0F;
   static final float KNEEL = 45.0F;
   static final float PLANT = 49.0F;
   static final float[] JERKS = new float[]{52.0F, 58.0F, 64.0F};
   static final float JERK = 4.0F;
   static final float OUT = 72.0F;
   static final float HAIR = 78.0F;
   static final float TAUNT = 84.0F;
   static final float SHOVE = 88.5F;
   static final float SLOW = 86.0F;
   static final float SNAP = 94.0F;
   static final float DROP = 98.5F;
   static final float SLAM = 100.0F;
   static final float PULL = 104.0F;
   static final float FLICK = 111.0F;
   static final float SHOW = 1.3F;
   static final Vector3f SPOT = at(0.85F, -0.05F, 0.0F);
   static final Vector3f KNEEL_AT = at(0.62F, 0.36F, 0.0F);
   static final Vector3f HEAD_AT = at(0.66F, 0.62F, 0.0F);
   static final Vector3f BACK_AT = at(0.98F, 0.6F, 0.0F);
   static final Vector3f PULL_AT = at(0.55F, 0.98F, 0.0F);
   static final Vector3f STAND_AT = at(1.0F, 0.45F, 0.0F);
   static final Vector3f FACE = new Vector3f(-3.0F, -1.0F, -9.5F);
   static final Vector3f GASH_FROM = new Vector3f(0.0F, 10.5F, 1.2F);
   static final Vector3f GASH_TO = new Vector3f(0.0F, 1.8F, 1.2F);
   static final Vector3f TEAR_DIR = new Vector3f(-0.3F, 0.55F, -0.78F);
   static final Vector3f TEAR_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);
   static final Vector3f TEMPLE = new Vector3f(2.6F, -4.6F, -0.8F);
   static final Vector3f EYE = new Vector3f(2.2F, -4.5F, -6.5F);
   static final Vector3f HAIR_AT = new Vector3f(0.0F, -7.4F, 2.6F);

   private KarambitDance() {
   }

   @Override
   Blade blade() {
      return Blade.KARAMBIT;
   }

   @Override
   float scale(float t) {
      return Keys.key(t, 0.0F, 1.0F, 6.0F, 1.3F, 113.0F, 1.3F, 118.0F, 1.1F);
   }

   @Override
   float slowmo(float t) {
      return t >= 86.0F && t < 94.5F ? 1.0F : 0.0F;
   }

   static float tear(float t) {
      float[] amount = new float[]{0.3F, 0.35F, 0.35F};
      float k = 0.0F;

      for (int i = 0; i < JERKS.length; i++) {
         float u = CoupDeGrace.phase(t, JERKS[i], JERKS[i] + 4.0F);
         k += amount[i] * (1.0F - (1.0F - u) * (1.0F - u) * (1.0F - u));
      }

      return k;
   }

   static Script.Plant tearPlant(float t) {
      return new Script.Plant(Knife.Part.BODY, new Vector3f(GASH_FROM).lerp(GASH_TO, tear(t)), TEAR_DIR, TEAR_AXIS);
   }

   Vector3f tearTip(Script.Frame f, float t) {
      return f.victim.scene(tearPlant(t).tip());
   }

   Vector3f temple(Script.Frame f) {
      return this.victimPoint(f, Knife.Part.HEAD, TEMPLE);
   }

   Matrix4f inHead(Script.Frame f) {
      return this.heldInPart(f, 95.999F, Knife.Part.HEAD);
   }

   Vector3f headRing(Script.Frame f) {
      return Knife.through(this.victimPart(f, Knife.Part.HEAD).mul(this.inHead(f)), this.blade().pivot);
   }

   @Nullable
   @Override
   Vector3f killPoint(Script.Frame f) {
      return this.temple(f);
   }

   static float onHandle(float t) {
      return 1.0F
         - CoupDeGrace.smooth(CoupDeGrace.phase(t, 98.5F, 100.0F))
         + CoupDeGrace.smooth(CoupDeGrace.phase(t, 101.0F, 103.5F)) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 104.0F, 106.0F)));
   }

   @Override
   float spin(float t) {
      return Keys.key(
         t,
         6.0F,
         (float) Math.PI,
         14.0F,
         15.707964F,
         16.0F,
         15.707964F,
         23.0F,
         (float) (Math.PI * 9),
         36.0F,
         (float) (Math.PI * 9),
         41.0F,
         34.55752F,
         75.0F,
         34.55752F,
         81.0F,
         (float) (Math.PI * 15),
         112.0F,
         (float) (Math.PI * 15),
         117.0F,
         (float) (Math.PI * 19)
      );
   }

   @Override
   void victim(Script.Frame f, float t) {
      float jolt = Keys.jolt(t, 49.0F, 8.0F, 2.2F) + Keys.jolt(t, 96.0F, 10.0F, 2.0F) + 0.6F * Keys.jolt(t, 100.0F, 6.0F, 2.5F);

      for (float at : JERKS) {
         jolt += Keys.jolt(t, at, 7.0F, 2.4F);
      }

      float squirm = Mth.sin(t * 0.37F) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 40.0F, 47.0F)));
      float crawl = 0.05F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 9.0F, 14.0F)) + 0.05F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 16.0F, 21.0F));
      prone(f, t, 4.0F * squirm + 5.0F * jolt, at(0.0F, crawl, 0.01F * Math.abs(jolt)));
      float breath = 1.5F * Mth.sin(t * 0.25F) * Keys.window(t, 82.0F, 96.0F, 2.0F);
      float raise = Keys.key(
         t,
         78.0F,
         0.0F,
         84.0F,
         34.0F,
         88.5F,
         36.0F,
         89.5F,
         29.0F,
         94.0F,
         31.0F,
         96.0F,
         31.0F,
         97.0F,
         35.0F,
         98.5F,
         33.0F,
         100.0F,
         0.0F,
         101.0F,
         2.5F,
         102.5F,
         0.0F
      );
      raiseChest(f, raise + breath);
      f.wounds
         .add(
            new Script.Wound(
               Knife.Part.BODY,
               new Vector3f(GASH_FROM).setComponent(2, 1.0F),
               new Vector3f(GASH_TO).setComponent(2, 1.0F),
               new Vector3f(0.0F, 0.0F, 1.0F),
               1.7F,
               tear(t)
            )
         );
      if (t >= 96.0F) {
         f.wounds
            .add(
               new Script.Wound(
                  Knife.Part.HEAD,
                  new Vector3f(3.0F, -6.0F, -1.6F),
                  new Vector3f(3.0F, -3.0F, 0.6F),
                  new Vector3f(1.0F, 0.0F, 0.0F),
                  1.6F,
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
            38.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            43.0F,
            KNEEL_AT.x,
            KNEEL_AT.y,
            KNEEL_AT.z,
            77.0F,
            KNEEL_AT.x,
            KNEEL_AT.y,
            KNEEL_AT.z,
            83.0F,
            HEAD_AT.x,
            HEAD_AT.y,
            HEAD_AT.z,
            88.5F,
            HEAD_AT.x,
            HEAD_AT.y,
            HEAD_AT.z,
            92.0F,
            BACK_AT.x,
            BACK_AT.y,
            BACK_AT.z,
            100.0F,
            BACK_AT.x,
            BACK_AT.y,
            BACK_AT.z,
            103.0F,
            PULL_AT.x,
            PULL_AT.y,
            PULL_AT.z,
            106.0F,
            PULL_AT.x,
            PULL_AT.y,
            PULL_AT.z,
            111.0F,
            STAND_AT.x,
            STAND_AT.y,
            STAND_AT.z
         );
         yaw = 180.0F;
         f.walk = Keys.window(t, 38.0F, 43.0F, 1.5F) * 0.5F + Keys.window(t, 88.5F, 92.0F, 1.0F) * 0.4F + Keys.window(t, 106.0F, 111.0F, 1.5F) * 0.4F;
         f.stride = t < 100.0F && t > 88.5F ? -t * 0.95F : t * 0.95F;
      }

      Vector3f look = t < 74.0F ? this.tearTip(f, Math.max(t, 49.0F)) : f.victim.scene(0.0F, -4.0F, 0.0F);
      yaw = Mth.rotLerp(Keys.window(t, 42.0F, 108.0F, 3.0F), yaw, yawToward(feet, look));
      f.kneel = Keys.key(t, 40.0F, 0.0F, 45.0F, 1.0F, 79.0F, 1.0F, 84.0F, 0.0F, 100.0F, 0.0F, 103.0F, 1.0F, 105.0F, 1.0F, 109.0F, 0.0F);
      f.drop = Rig.kneelDrop(f.kneel);
      f.lean = Keys.key(
         t,
         0.0F,
         0.0F,
         8.0F,
         0.06F,
         26.0F,
         -0.08F,
         36.0F,
         0.0F,
         45.0F,
         0.4F,
         74.0F,
         0.4F,
         84.0F,
         0.3F,
         88.5F,
         0.35F,
         92.0F,
         -0.08F,
         94.0F,
         -0.12F,
         96.0F,
         0.45F,
         101.0F,
         0.55F,
         103.0F,
         0.75F,
         106.0F,
         0.4F,
         110.0F,
         0.05F,
         118.0F,
         0.0F
      );
      feet.add(0.0F, -f.drop, 0.0F);
      a.turn(yaw, 0.0F, 0.0F, 0.0F).feet(feet);
      f.actorFwd = feet.z;
      f.actorSide = -feet.x;
      Blade blade = this.blade();
      if (t >= 44.0F && t < 49.0F) {
         Vector3f start = this.tearTip(f, 49.0F);
         f.lean = Mth.lerp(Keys.window(t, 44.0F, 50.0F, 2.0F), f.lean, solveLean(a, start, this.reach(blade.tip, t), f.lean));
      } else if (t >= 49.0F && t < 74.0F) {
         Vector3f ring = this.plantedPivot(f, tearPlant(Math.min(t, 72.0F)));
         f.lean = Mth.lerp(1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 72.0F, 74.0F)), f.lean, solveLean(a, ring, blade.pivot.length(), f.lean));
      } else if (t >= 94.0F && t < 96.0F) {
         f.lean = Mth.lerp(CoupDeGrace.smooth(CoupDeGrace.phase(t, 94.0F, 96.0F)), f.lean, solveLean(a, this.temple(f), this.reach(blade.tip, t), f.lean));
      } else if (t >= 96.0F && t < 106.0F) {
         f.lean = Mth.lerp(onHandle(t), f.lean, solveLean(a, this.headRing(f), blade.pivot.length(), f.lean));
      }
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      k.blood = Keys.key(t, 48.5F, 0.0F, 49.0F, 0.5F, 72.0F, 1.0F, 80.0F, 0.35F, 96.0F, 1.0F, 111.0F, 1.0F, 112.0F, 0.25F);
      k.trail = Keys.window(t, 6.0F, 24.0F, 2.0F)
         + Keys.window(t, 25.0F, 41.0F, 1.0F)
         + Keys.window(t, 46.0F, 50.0F, 1.0F)
         + Keys.window(t, 72.0F, 82.0F, 1.5F) * 1.2F
         + Keys.window(t, 93.5F, 96.5F, 0.5F) * 1.5F
         + Keys.window(t, 104.0F, 107.0F, 1.0F)
         + Keys.window(t, 110.0F, 118.0F, 1.5F);
      if (t >= 26.0F && t < 36.0F) {
         k.scene(Knife.flight(this.blade(), this.heldAt(f, 26.0F), this.heldAt(f, 36.0F), CoupDeGrace.phase(t, 26.0F, 36.0F), 1.1F, 2.0F));
      } else if (t >= 47.5F && t < 49.0F) {
         k.scene(this.driving(f, t, tearPlant(49.0F), 47.5F, 49.0F));
      } else if (t >= 49.0F && t < 72.0F) {
         this.plant(k, tearPlant(t));
      } else if (t >= 72.0F && t < 74.0F) {
         Matrix4f stuck = this.planted(this.other(f, 71.99F), tearPlant(72.0F));
         k.scene(this.pulled(f, t, stuck, 72.0F, 74.0F));
      } else if (t >= 96.0F && t < 104.0F) {
         k.stuck(Knife.Part.HEAD, this.inHead(f));
      } else if (t >= 104.0F && t < 106.0F) {
         Matrix4f stuck = this.victimPart(this.other(f, 103.99F), Knife.Part.HEAD).mul(this.inHead(f));
         k.scene(this.pulled(f, t, stuck, 104.0F, 106.0F));
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
      Rig.kneel(m.rightLeg, m.leftLeg, f.kneel, 0.08F);
      Rig.walk(m.rightLeg, m.leftLeg, m.rightArm, m.leftArm, f.stride, f.walk, false);
      m.rightArm.xRot = -0.25F + 0.4F * f.walk * Mth.sin(f.stride);
      m.rightArm.zRot = 0.12F;
      m.leftArm.xRot = -0.2F - 0.4F * f.walk * Mth.sin(f.stride);
      m.leftArm.zRot = -0.1F;
      if (t >= 2.0F && t < 26.0F) {
         Vector3f at = new Vector3f(FACE).add(0.0F, 0.4F * Mth.sin(t * 0.45F) - 3.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 23.0F, 26.0F)), 0.0F);
         Rig.aim(m.rightArm, blade.pivot, at, 0.0F, Keys.key(t, 2.0F, 0.0F, 6.0F, 1.0F));
      } else if (t >= 26.0F && t < 44.0F) {
         Vector3f high = new Vector3f(FACE).add(0.0F, -4.0F, 0.0F);
         Vector3f catchAt = new Vector3f(FACE).add(0.5F, -1.5F, 0.5F);
         Vector3f aim = high.lerp(catchAt, CoupDeGrace.smooth(CoupDeGrace.phase(t, 26.0F, 33.0F)));
         Rig.aim(m.rightArm, blade.pivot, aim, 0.0F, Keys.key(t, 26.0F, 1.0F, 41.0F, 1.0F, 44.0F, 0.0F));
      } else if (t >= 44.0F && t < 49.0F) {
         Vector3f start = this.tearTip(f, 49.0F);
         Vector3f above = new Vector3f(start).add(0.0F, 0.5F, 0.0F).add(new Vector3f(a.scene(0.0F, 0.0F, 0.0F)).sub(start).mul(0.25F).setComponent(1, 0.0F));
         Vector3f aim = above.lerp(start, Keys.strike(t, 46.5F, 49.0F));
         Rig.aim(m.rightArm, this.inHand(blade.tip, t), a.model(aim), 0.0F, Keys.key(t, 44.0F, 0.0F, 46.0F, 1.0F));
      } else if (t >= 49.0F && t < 76.0F) {
         Vector3f ring = this.plantedPivot(f, tearPlant(Math.min(t, 72.0F)));
         Vector3f up = new Vector3f(ring).add(0.0F, 0.45F, 0.0F);
         Vector3f aim = new Vector3f(ring).lerp(up, CoupDeGrace.smooth(CoupDeGrace.phase(t, 72.0F, 75.0F)));
         Rig.aim(m.rightArm, blade.pivot, a.model(aim), 0.0F, 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 74.0F, 76.0F)));
      } else if (t >= 76.0F && t < 84.0F) {
         Rig.aim(m.rightArm, blade.pivot, new Vector3f(FACE).add(-1.0F, 1.0F, 0.0F), 0.0F, Keys.window(t, 74.0F, 86.0F, 2.0F));
      } else if (t >= 84.0F && t < 88.5F) {
         Vector3f eye = a.model(this.victimPoint(f, Knife.Part.HEAD, EYE));
         Rig.aim(m.rightArm, this.inHand(blade.tip, t), eye, 0.0F, Keys.window(t, 82.0F, 89.5F, 2.0F));
      } else if (t >= 88.5F && t < 96.0F) {
         Vector3f high = new Vector3f(-6.5F, -17.0F + 0.4F * Mth.sin(age * 3.1F) * Keys.window(t, 90.0F, 94.0F, 1.0F), 3.0F);
         if (t < 94.0F) {
            Rig.aim(m.rightArm, blade.pivot, high, 0.0F, Keys.key(t, 88.5F, 0.3F, 91.0F, 1.0F));
         } else {
            Rig.aim(m.rightArm, blade.pivot, high, 0.0F);
            Vector3f from = Rig.point(m.rightArm, this.inHand(blade.tip, t));
            Vector3f temple = a.model(this.temple(f));
            Rig.aim(m.rightArm, this.inHand(blade.tip, t), from.lerp(temple, Keys.strike(t, 94.0F, 96.0F)), 0.0F);
         }
      } else if (t >= 96.0F && t < 108.0F) {
         Vector3f ring = this.headRing(f);
         Vector3f aim;
         if (t < 104.0F) {
            Vector3f released = a.model(this.headRing(this.other(f, 98.5F)).add(0.0F, 0.3F, 0.0F));
            aim = released.lerp(a.model(ring), onHandle(t));
         } else {
            aim = a.model(ring).lerp(a.model(new Vector3f(ring).add(0.0F, 0.5F, 0.0F)), CoupDeGrace.smooth(CoupDeGrace.phase(t, 104.0F, 106.0F)));
         }

         Rig.aim(m.rightArm, blade.pivot, aim, 0.0F, 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 106.0F, 108.0F)));
      } else if (t >= 108.0F && t < 113.0F) {
         float snap = Keys.key(t, 108.0F, 0.0F, 110.0F, 1.0F, 111.0F, -0.6F, 113.0F, 0.0F);
         m.rightArm.xRot = -0.25F - 1.0F * snap;
         m.rightArm.zRot = 0.12F + 0.4F * Math.abs(snap);
      }

      if (t >= 112.0F) {
         Rig.aim(m.rightArm, blade.pivot, new Vector3f(FACE).add(-2.0F, 3.0F, 1.5F), 0.0F, Keys.key(t, 112.0F, 0.0F, 113.0F, 1.0F));
      }

      if (t >= 46.0F && t < 75.0F) {
         Rig.aim(m.leftArm, Rig.LEFT_HAND, a.model(f.victim.scene(2.0F, 2.5F, 2.4F)), 0.0F, Keys.window(t, 46.0F, 75.0F, 2.5F));
      } else if (t >= 74.0F && t < 90.5F) {
         Vector3f hair = a.model(this.victimPoint(f, Knife.Part.HEAD, HAIR_AT));
         hair.add(0.0F, 0.0F, -3.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 88.0F, 89.5F)));
         Rig.aim(m.leftArm, Rig.LEFT_HAND, hair, 0.0F, Keys.window(t, 74.0F, 90.5F, 2.0F));
      } else if (t >= 27.0F && t < 38.0F) {
         float open = Keys.window(t, 27.0F, 38.0F, 3.0F);
         m.leftArm.xRot = Mth.lerp(open, m.leftArm.xRot, -0.5F);
         m.leftArm.zRot = Mth.lerp(open, m.leftArm.zRot, -0.55F);
      } else if (t >= 90.5F && t < 100.0F) {
         float out = Keys.window(t, 89.5F, 100.0F, 2.0F);
         m.leftArm.xRot = Mth.lerp(out, m.leftArm.xRot, -0.6F);
         m.leftArm.zRot = Mth.lerp(out, m.leftArm.zRot, -0.7F);
      }

      Vector3f lookAt;
      if ((!(t >= 3.0F) || !(t < 26.0F)) && (!(t >= 75.0F) || !(t < 78.0F)) && !(t >= 112.0F)) {
         if (t >= 26.0F && t < 38.0F) {
            lookAt = f.knife.holder == Knife.Holder.SCENE ? a.model(Knife.through(f.knife.matrix, blade.pivot)) : new Vector3f(FACE).add(0.0F, -4.0F, 0.0F);
         } else if (t >= 44.0F && t < 75.0F) {
            lookAt = a.model(this.tearTip(f, Math.max(t, 49.0F)));
         } else {
            lookAt = a.model(f.victim.scene(0.0F, -4.0F, 0.0F));
         }
      } else {
         lookAt = Rig.point(m.rightArm, blade.pivot);
      }

      CoupDeGrace.look(m.head, lookAt, 1.2F, 1.1F);
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      float flinch = Keys.window(t, 49.0F, 74.0F, 1.0F);
      proneLimbs(m, f, age, Keys.window(t, 8.0F, 15.0F, 2.5F), Keys.window(t, 15.0F, 22.0F, 2.5F), flinch);
      legsDown(m, f);
      float tearing = Keys.window(t, 49.0F, 75.0F, 2.0F);
      m.rightArm.zRot = m.rightArm.zRot + 0.4F * tearing * Mth.sin(age * 1.7F);
      m.leftArm.zRot = m.leftArm.zRot - 0.4F * tearing * Mth.sin(age * 1.7F + 1.7F);
      m.rightLeg.xRot = m.rightLeg.xRot + tearing * 0.45F * Math.abs(Mth.sin(age * 0.9F));
      m.leftLeg.xRot = m.leftLeg.xRot + tearing * 0.45F * Math.abs(Mth.sin(age * 0.9F + 2.4F));

      for (float at : JERKS) {
         m.head.xRot = m.head.xRot - 0.35F * Keys.window(t, at, at + 4.0F, 1.0F);
      }

      float held = Keys.window(t, 76.0F, 158.0F, 2.0F);
      if (held > 0.0F) {
         float push = held * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 98.5F, 100.0F)));
         handOnGround(m.rightArm, Rig.HAND, f.victim, at(-0.35F, 1.0F, 0.0F), push);
         handOnGround(m.leftArm, Rig.LEFT_HAND, f.victim, at(0.35F, 1.0F, 0.0F), push);
         float free = CoupDeGrace.smooth(CoupDeGrace.phase(t, 88.5F, 90.5F));
         m.head.xRot = Mth.lerp(held, m.head.xRot, Mth.lerp(free, -0.8F, -0.5F) + 0.05F * Mth.sin(age * 2.3F) * free);
         m.head.yRot = Mth.lerp(held, m.head.yRot, 0.0F);
      }

      float dead = CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 99.0F));
      if (dead > 0.0F) {
         float down = CoupDeGrace.smooth(CoupDeGrace.phase(t, 98.5F, 101.0F));
         m.head.xRot = Mth.lerp(dead, m.head.xRot, Mth.lerp(down, -0.45F, -0.3F));
         float limp = CoupDeGrace.smooth(CoupDeGrace.phase(t, 98.5F, 102.0F));
         m.rightArm.xRot = Mth.lerp(limp, m.rightArm.xRot, -3.0F);
         m.leftArm.xRot = Mth.lerp(limp, m.leftArm.xRot, -3.0F);
         m.rightArm.yRot = Mth.lerp(limp, m.rightArm.yRot, 0.0F);
         m.leftArm.yRot = Mth.lerp(limp, m.leftArm.yRot, 0.0F);
         m.rightArm.zRot = Mth.lerp(limp, m.rightArm.zRot, -0.5F);
         m.leftArm.zRot = Mth.lerp(limp, m.leftArm.zRot, 0.5F);
         m.rightLeg.xRot = 0.04F + f.lift + 0.3F * Math.abs(Keys.jolt(t, 102.0F, 10.0F, 1.6F));
         m.leftLeg.xRot = 0.04F + f.lift + 0.2F * Math.abs(Keys.jolt(t, 104.0F, 8.0F, 1.9F));
      }
   }

   @Override
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      if (t < 6.0F) {
         return new CoupDeGrace.Shot(at(2.3F, 1.7F, 1.7F), at(0.3F, -0.1F, 0.6F), 0, true, 3.0F);
      }

      if (t < 26.0F) {
         Matrix4f held = this.heldAt(f, Math.min(t, 23.0F));
         Vector3f ring = Knife.through(held, this.blade().pivot);
         Vector3f axis = spinAxis(held, this.blade());
         Vector3f eyes = f.actor.scene(0.0F, -4.0F, 0.0F);
         if (axis.dot(new Vector3f(ring).sub(eyes)) < 0.0F) {
            axis.negate();
         }

         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 6.0F, 26.0F));
         Vector3f pos = new Vector3f(ring).add(new Vector3f(axis).mul(Mth.lerp(k, 3.0F, 2.5F))).add(0.0F, 0.15F, 0.0F);
         return new CoupDeGrace.Shot(pos, new Vector3f(ring).lerp(eyes, 0.15F), 1, true, 5.0F, Mth.lerp(k, 34.0F, 30.0F));
      } else if (t < 42.0F) {
         Matrix4f knife = this.knifeInScene(f);
         Vector3f ring = knife != null ? Knife.through(knife, this.blade().pivot) : f.actor.scene(0.0F, 0.0F, -6.0F);
         Vector3f pos = f.actor.scene(0.0F, 0.0F, 0.0F).add(at(-1.5F, 1.1F, 0.25F)).setComponent(1, 0.35F);
         return new CoupDeGrace.Shot(pos, ring, 2, true, 9.0F, 46.0F);
      } else if (t < 48.0F) {
         Vector3f pos = at(-0.7F, -1.7F, 1.0F);
         return new CoupDeGrace.Shot(pos, f.victim.scene(0.0F, 8.0F, 0.0F).lerp(f.actor.scene(0.0F, 0.0F, 0.0F), 0.5F), 3, true, 3.0F, 50.0F);
      } else if (t < 76.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 49.0F, 76.0F));
         Vector3f pos = at(0.3F, -1.25F, 1.15F).lerp(at(0.35F, -0.95F, 1.05F), k);
         Vector3f tip = this.tearTip(f, Mth.clamp(t, 49.0F, 72.0F));
         return new CoupDeGrace.Shot(pos, new Vector3f(tip).lerp(f.actor.scene(0.0F, -2.0F, 0.0F), 0.3F), 4, true, 4.0F, 42.0F);
      } else if (t < 86.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 76.0F, 86.0F));
         Vector3f pos = at(-1.5F, 1.0F, 0.9F).lerp(at(-1.3F, 1.05F, 1.0F), k);
         return new CoupDeGrace.Shot(pos, f.victim.scene(0.0F, -6.0F, 0.0F).lerp(f.actor.scene(0.0F, -8.0F, 0.0F), 0.5F), 5, true, 3.0F, 50.0F);
      } else if (t < 103.0F) {
         Vector3f face = f.victim.scene(0.0F, -4.0F, 0.0F);
         Vector3f pos = new Vector3f(face).add(at(-0.6F, 1.5F, 0.0F)).setComponent(1, Math.max(0.35F, face.y - 0.1F));
         Vector3f aim = new Vector3f(face).lerp(f.actor.scene(-4.0F, -14.0F, 0.0F), t < 96.0F ? 0.45F : 0.15F);
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 86.0F, 94.0F));
         return new CoupDeGrace.Shot(pos, aim, 6, true, t < 96.0F ? 3.0F : 8.0F, t < 96.0F ? Mth.lerp(k, 50.0F, 42.0F) : 48.0F);
      } else {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 103.0F, 128.0F));
         Vector3f pos = at(2.1F, -1.4F, 1.2F).lerp(at(2.8F, -2.0F, 2.9F), k);
         return new CoupDeGrace.Shot(pos, at(0.5F, 0.15F, 0.55F), 7, true, 2.0F);
      }
   }

   @Override
   public Vector3f end(int side) {
      return new Vector3f(STAND_AT);
   }

   @Override
   public Vector3f corpse(int side) {
      return new Vector3f(PRONE);
   }

   @Override
   public void tick(ExecutionScenes.Scene s, int before, int now) {
      ScriptFx.karambit(s, before, now);
   }
}
