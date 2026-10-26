package fr.heraliz.reanimation.client;

import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class ButterflyExecution extends Script {
   static final ButterflyExecution INSTANCE = new ButterflyExecution();
   static final float ARRIVE = 5.0F;
   static final float TOSS = 40.0F;
   static final float CATCH = 50.0F;
   static final float KNEEL = 60.0F;
   static final float HAIR = 60.0F;
   static final float REVEAL = 66.0F;
   static final float REVEALED = 75.0F;
   static final float TOSS2 = 80.0F;
   static final float SLAMMED = 83.0F;
   static final float SLOW = 85.0F;
   static final float CATCH2 = 91.0F;
   static final float SNAP = 94.0F;
   static final float PULL = 104.0F;
   static final float SHOW = 1.45F;
   static final Vector3f SPOT = at(0.85F, -0.05F, 0.0F);
   static final Vector3f KNEEL_AT = at(0.6F, 0.52F, 0.0F);
   static final Vector3f STAND_AT = at(0.85F, 0.5F, 0.0F);
   static final Vector3f FLIP = new Vector3f(-9.0F, 1.0F, -8.0F);
   static final Vector3f SHOWN = new Vector3f(0.7F, -0.15F, -1.0F).normalize();
   static final Vector3f FLICK = new Vector3f(-4.0F, -12.0F, -9.0F);
   static final Vector3f HIGH = new Vector3f(-5.0F, -18.0F, -3.0F);
   static final Vector3f HANDLE = new Vector3f(-1.0F, 9.3F, 0.0F);
   static final Vector3f NAPE = new Vector3f(0.0F, -1.6F, 1.6F);
   static final Vector3f EYES = new Vector3f(2.0F, -7.0F, -13.0F);
   static final Vector3f HAIR_AT = new Vector3f(0.0F, -7.4F, 2.6F);
   static final Vector3f CROWN = new Vector3f(0.0F, -7.0F, 1.0F);

   private ButterflyExecution() {
   }

   @Override
   Blade blade() {
      return Blade.BUTTERFLY;
   }

   @Override
   float scale(float t) {
      return Keys.key(t, 0.0F, 1.0F, 6.0F, 1.45F, 114.0F, 1.45F, 118.0F, 1.15F);
   }

   @Override
   float slowmo(float t) {
      return t >= 85.0F && t < 94.5F ? 1.0F : 0.0F;
   }

   static float open(float t) {
      return Keys.key(
         t,
         8.0F,
         0.0F,
         13.0F,
         1.0F,
         16.0F,
         1.0F,
         21.0F,
         0.0F,
         23.0F,
         0.0F,
         25.0F,
         1.0F,
         38.0F,
         1.0F,
         40.0F,
         0.0F,
         43.0F,
         0.0F,
         47.0F,
         1.0F,
         50.0F,
         1.0F,
         54.0F,
         0.0F,
         66.0F,
         0.0F,
         75.0F,
         1.0F,
         80.0F,
         1.0F,
         83.0F,
         0.0F,
         85.0F,
         0.0F,
         89.0F,
         1.0F,
         108.0F,
         1.0F,
         110.0F,
         0.0F,
         112.0F,
         1.0F,
         114.0F,
         0.0F,
         116.0F,
         1.0F,
         117.5F,
         0.0F
      );
   }

   @Override
   float spin(float t) {
      return Keys.key(
         t,
         16.0F,
         0.0F,
         21.0F,
         (float) (Math.PI * 2),
         25.0F,
         (float) (Math.PI * 2),
         33.0F,
         (float) (Math.PI * 6),
         50.0F,
         (float) (Math.PI * 6),
         54.0F,
         (float) (Math.PI * 8),
         91.0F,
         (float) (Math.PI * 9),
         106.0F,
         (float) (Math.PI * 9),
         116.0F,
         (float) (Math.PI * 12)
      );
   }

   @Override
   Quaternionf wrist(float t) {
      return new Quaternionf().rotationY(Keys.key(t, 33.0F, 0.0F, 38.0F, (float) (Math.PI * 2)));
   }

   @Override
   Vector3f shift(float t) {
      float reversed = CoupDeGrace.smooth(CoupDeGrace.phase(t, 89.0F, 91.0F)) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 106.0F, 110.0F)));
      return reversed <= 0.0F ? new Vector3f() : Knife.regrip(this.blade(), HANDLE, this.spin(t), this.wrist(t), this.scale(t)).mul(reversed);
   }

   Matrix4f inHead(Script.Frame f) {
      return this.heldInPart(f, 95.999F, Knife.Part.HEAD);
   }

   Vector3f nape(Script.Frame f) {
      return this.victimPoint(f, Knife.Part.HEAD, NAPE);
   }

   Vector3f headPin(Script.Frame f) {
      return Knife.through(this.victimPart(f, Knife.Part.HEAD).mul(this.inHead(f)), this.blade().pivot);
   }

   @Nullable
   @Override
   Vector3f killPoint(Script.Frame f) {
      return this.nape(f);
   }

   static float fall(float t) {
      float u = CoupDeGrace.phase(t, 80.0F, 91.0F);
      return u < 0.36F ? u / 0.36F * 0.5F : 0.5F + 0.5F * CoupDeGrace.smooth((u - 0.36F) / 0.64F);
   }

   @Override
   void victim(Script.Frame f, float t) {
      float jolt = Keys.jolt(t, 83.0F, 6.0F, 2.4F) + Keys.jolt(t, 96.0F, 10.0F, 2.1F);
      float crawl = 0.05F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 9.0F, 14.0F))
         + 0.05F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 22.0F, 27.0F))
         + 0.04F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 36.0F, 41.0F));
      float squirm = 3.0F * Mth.sin(t * 0.4F) * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 56.0F, 60.0F)));
      prone(f, t, squirm + 5.0F * jolt, at(0.0F, crawl, 0.008F * Math.abs(jolt)));
      float raise = Keys.key(t, 60.0F, 0.0F, 66.0F, 34.0F, 80.0F, 36.0F, 82.5F, 0.0F, 83.5F, 3.0F, 85.0F, 0.0F);
      raiseChest(f, raise + 1.5F * Mth.sin(t * 0.25F) * Keys.window(t, 64.0F, 80.0F, 2.0F));
      if (t >= 96.0F) {
         f.wounds
            .add(
               new Script.Wound(
                  Knife.Part.HEAD,
                  new Vector3f(-1.2F, -2.6F, 3.0F),
                  new Vector3f(1.2F, -0.4F, 3.0F),
                  new Vector3f(0.0F, 0.0F, 1.0F),
                  2.0F,
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
            52.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            58.0F,
            KNEEL_AT.x,
            KNEEL_AT.y,
            KNEEL_AT.z,
            106.0F,
            KNEEL_AT.x,
            KNEEL_AT.y,
            KNEEL_AT.z,
            111.0F,
            STAND_AT.x,
            STAND_AT.y,
            STAND_AT.z
         );
         yaw = 180.0F;
         f.walk = Keys.window(t, 52.0F, 58.0F, 1.5F) * 0.6F + Keys.window(t, 106.0F, 111.0F, 1.5F) * 0.4F;
         f.stride = t * 0.95F;
      }

      yaw = Mth.rotLerp(Keys.window(t, 55.0F, 109.0F, 3.0F), yaw, yawToward(feet, f.victim.scene(0.0F, -4.0F, 0.0F)));
      f.kneel = Keys.key(t, 55.0F, 0.0F, 60.0F, 1.0F, 106.0F, 1.0F, 110.0F, 0.0F);
      f.drop = Rig.kneelDrop(f.kneel);
      f.lean = Keys.key(
         t,
         0.0F,
         0.0F,
         10.0F,
         0.04F,
         40.0F,
         -0.06F,
         50.0F,
         0.0F,
         60.0F,
         0.3F,
         80.0F,
         0.3F,
         83.0F,
         0.55F,
         88.0F,
         0.2F,
         94.0F,
         -0.05F,
         96.0F,
         0.5F,
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
      if (t >= 94.0F && t < 96.0F) {
         f.lean = Mth.lerp(CoupDeGrace.smooth(CoupDeGrace.phase(t, 94.0F, 96.0F)), f.lean, solveLean(a, this.nape(f), this.reach(blade.tip, t), f.lean));
      } else if (t >= 96.0F && t < 106.0F) {
         Vector3f pin = this.headPin(f);
         f.lean = Mth.lerp(
            1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 104.0F, 106.0F)), f.lean, solveLean(a, pin, this.inHand(blade.pivot, t).length(), f.lean)
         );
      }
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      k.open = open(t);
      k.blood = Keys.key(t, 95.5F, 0.0F, 96.0F, 1.0F, 110.0F, 1.0F, 112.0F, 0.3F);
      k.trail = Keys.window(t, 8.0F, 40.0F, 1.5F) * 0.9F
         + Keys.window(t, 39.0F, 54.0F, 1.0F)
         + Keys.window(t, 66.0F, 76.0F, 1.0F) * 0.5F
         + Keys.window(t, 79.0F, 92.0F, 1.0F)
         + Keys.window(t, 93.5F, 96.5F, 0.5F) * 1.5F
         + Keys.window(t, 107.0F, 118.0F, 1.5F) * 0.8F;
      if (t >= 40.0F && t < 50.0F) {
         k.scene(Knife.flight(this.blade(), this.heldAt(f, 40.0F), this.heldAt(f, 50.0F), CoupDeGrace.phase(t, 40.0F, 50.0F), 0.85F, 2.0F));
      } else if (t >= 80.0F && t < 91.0F) {
         k.scene(Knife.flight(this.blade(), this.heldAt(f, 80.0F), this.heldAt(f, 91.0F), fall(t), 1.5F, 2.0F));
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
      m.rightArm.xRot = -0.3F + 0.4F * f.walk * Mth.sin(f.stride);
      m.rightArm.zRot = 0.12F;
      m.leftArm.xRot = -0.2F - 0.4F * f.walk * Mth.sin(f.stride);
      m.leftArm.zRot = -0.1F;
      if (t >= 2.0F && t < 40.0F) {
         float still = 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 36.0F, 39.0F));
         Vector3f at = new Vector3f(FLIP)
            .add(
               0.3F * Mth.sin(t * 0.7F) * still,
               0.3F * Mth.sin(t * 0.9F) * still - 4.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 37.0F, 40.0F)),
               0.0F
            );
         Rig.aim(m.rightArm, blade.pivot, at, Rig.twistToward(m.rightArm, blade.pivot, at, blade.axis, SHOWN), Keys.key(t, 2.0F, 0.0F, 6.0F, 1.0F));
      } else if (t >= 40.0F && t < 56.0F) {
         Vector3f up = new Vector3f(FLIP).add(0.0F, -4.0F + 3.0F * CoupDeGrace.smooth(CoupDeGrace.phase(t, 44.0F, 50.0F)), 0.0F);
         Rig.aim(
            m.rightArm, blade.pivot, up, Rig.twistToward(m.rightArm, blade.pivot, up, blade.axis, SHOWN), Keys.key(t, 40.0F, 1.0F, 54.0F, 1.0F, 56.0F, 0.0F)
         );
      } else if (t >= 62.0F && t < 80.0F) {
         Vector3f eyes = a.model(this.victimPoint(f, Knife.Part.HEAD, EYES));
         Vector3f aim = eyes.lerp(FLICK, CoupDeGrace.smooth(CoupDeGrace.phase(t, 77.5F, 80.0F)));
         Rig.aim(m.rightArm, blade.pivot, aim, 0.0F, Keys.key(t, 62.0F, 0.0F, 65.0F, 1.0F));
      } else if (t >= 80.0F && t < 94.0F) {
         Vector3f aim = new Vector3f(FLICK).lerp(HIGH, CoupDeGrace.smooth(CoupDeGrace.phase(t, 80.0F, 84.0F)));
         aim.add(0.0F, 0.3F * Mth.sin(age * 3.0F) * Keys.window(t, 91.0F, 94.0F, 1.0F), 0.0F);
         Rig.aim(m.rightArm, blade.pivot, aim, 0.0F);
      }

      if (t >= 94.0F && t < 96.0F) {
         Rig.aim(m.rightArm, blade.pivot, HIGH, 0.0F);
         Vector3f from = Rig.point(m.rightArm, this.inHand(blade.tip, t));
         Rig.aim(m.rightArm, this.inHand(blade.tip, t), from.lerp(a.model(this.nape(f)), Keys.strike(t, 94.0F, 96.0F)), 0.0F);
      } else if (t >= 96.0F && t < 108.0F) {
         Vector3f pin = this.headPin(f);
         Vector3f aim = t < 104.0F
            ? a.model(pin)
            : a.model(pin).lerp(a.model(new Vector3f(pin).add(0.0F, 0.5F, 0.0F)), CoupDeGrace.smooth(CoupDeGrace.phase(t, 104.0F, 106.0F)));
         Rig.aim(m.rightArm, this.inHand(blade.pivot, t), aim, 0.0F, 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 106.0F, 108.0F)));
      } else if (t >= 106.0F) {
         Rig.aim(m.rightArm, blade.pivot, new Vector3f(FLIP).add(-0.5F, 4.0F, 1.0F), 0.0F, Keys.window(t, 106.0F, 122.0F, 2.0F));
      }

      if (t >= 57.0F && t < 80.0F) {
         Rig.aim(m.leftArm, Rig.LEFT_HAND, a.model(this.victimPoint(f, Knife.Part.HEAD, HAIR_AT)), 0.0F, Keys.window(t, 57.0F, 81.0F, 2.0F));
      } else if (t >= 80.0F && t < 106.0F) {
         Rig.aim(m.leftArm, Rig.LEFT_HAND, a.model(this.victimPoint(f, Knife.Part.HEAD, CROWN)), 0.0F, Keys.window(t, 79.0F, 106.0F, 1.0F));
      } else if (t >= 39.0F && t < 52.0F) {
         float open = Keys.window(t, 39.0F, 52.0F, 2.0F);
         m.leftArm.xRot = Mth.lerp(open, m.leftArm.xRot, -0.5F);
         m.leftArm.zRot = Mth.lerp(open, m.leftArm.zRot, -0.5F);
      }

      Vector3f lookAt;
      if ((!(t >= 3.0F) || !(t < 54.0F)) && (!(t >= 80.0F) || !(t < 92.0F)) && !(t >= 106.0F)) {
         lookAt = a.model(f.victim.scene(0.0F, -4.0F, 0.0F));
      } else {
         Matrix4f knife = f.knife.holder == Knife.Holder.SCENE ? f.knife.matrix : null;
         lookAt = knife != null ? a.model(Knife.through(knife, blade.pivot)) : Rig.point(m.rightArm, blade.pivot);
      }

      CoupDeGrace.look(m.head, lookAt, 1.2F, 1.1F);
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      proneLimbs(m, f, age, Keys.window(t, 8.0F, 15.0F, 2.5F), Keys.window(t, 21.0F, 28.0F, 2.5F) + Keys.window(t, 35.0F, 42.0F, 2.5F), 0.0F);
      legsDown(m, f);
      float held = Keys.window(t, 58.0F, 158.0F, 2.0F);
      if (held > 0.0F) {
         float up = held * (1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 80.0F, 83.0F)));
         handOnGround(m.rightArm, Rig.HAND, f.victim, at(-0.35F, 1.0F, 0.0F), up);
         handOnGround(m.leftArm, Rig.LEFT_HAND, f.victim, at(0.35F, 1.0F, 0.0F), up);
         float down = CoupDeGrace.smooth(CoupDeGrace.phase(t, 80.0F, 83.0F));
         float claw = Keys.window(t, 83.0F, 97.0F, 1.5F);
         m.rightArm.zRot = m.rightArm.zRot + 0.35F * claw * Mth.sin(age * 1.9F);
         m.leftArm.zRot = m.leftArm.zRot - 0.35F * claw * Mth.sin(age * 1.9F + 1.4F);
         float stare = Mth.lerp(CoupDeGrace.smooth(CoupDeGrace.phase(t, 60.0F, 64.0F)), -0.8F, -0.55F);
         m.head.xRot = Mth.lerp(held, m.head.xRot, Mth.lerp(down, stare, -0.05F));
         m.head.yRot = Mth.lerp(held, m.head.yRot, 0.0F);
      }

      float dead = CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 99.0F));
      if (dead > 0.0F) {
         m.rightArm.xRot = Mth.lerp(dead, m.rightArm.xRot, -3.0F);
         m.leftArm.xRot = Mth.lerp(dead, m.leftArm.xRot, -3.0F);
         m.rightArm.zRot = Mth.lerp(dead, m.rightArm.zRot, -0.6F);
         m.leftArm.zRot = Mth.lerp(dead, m.leftArm.zRot, 0.6F);
         m.rightLeg.xRot = m.rightLeg.xRot + 0.3F * Math.abs(Keys.jolt(t, 97.0F, 10.0F, 1.7F));
      }
   }

   @Override
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      Blade blade = this.blade();
      if (t < 6.0F) {
         return new CoupDeGrace.Shot(at(2.3F, 1.7F, 1.7F), at(0.3F, -0.1F, 0.6F), 0, true, 3.0F);
      }

      if (t < 40.0F) {
         Matrix4f held = this.heldAt(f, 14.0F);
         Vector3f pin = Knife.through(this.heldAt(f, t), blade.pivot);
         Vector3f axis = spinAxis(held, blade);
         Vector3f eyes = f.actor.scene(0.0F, -4.0F, 0.0F);
         Vector3f front = f.actor.scene(SHOWN.x, SHOWN.y, SHOWN.z).sub(f.actor.scene(0.0F, 0.0F, 0.0F));
         if (axis.dot(front) < 0.0F) {
            axis.negate();
         }

         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 6.0F, 40.0F));
         Vector3f pos = new Vector3f(pin).add(new Vector3f(axis).mul(Mth.lerp(k, 2.6F, 2.2F))).add(0.0F, 0.12F, 0.0F);
         return new CoupDeGrace.Shot(pos, new Vector3f(pin).lerp(eyes, 0.12F), 1, true, 4.0F, Mth.lerp(k, 32.0F, 28.0F));
      } else if (t < 56.0F) {
         Matrix4f knife = this.knifeInScene(f);
         Vector3f pin = knife != null ? Knife.through(knife, blade.pivot) : f.actor.scene(0.0F, 0.0F, -8.0F);
         Vector3f pos = f.actor.scene(0.0F, 0.0F, 0.0F).add(at(-1.4F, 1.2F, 0.3F)).setComponent(1, 0.4F);
         return new CoupDeGrace.Shot(pos, pin, 2, true, 8.0F, 46.0F);
      } else if (t < 64.0F) {
         Vector3f pos = at(-1.3F, 1.6F, 1.0F);
         return new CoupDeGrace.Shot(pos, f.victim.scene(0.0F, -2.0F, 0.0F).lerp(f.actor.scene(0.0F, 0.0F, 0.0F), 0.5F), 3, true, 3.0F, 50.0F);
      } else if (t < 80.0F) {
         Vector3f face = f.victim.scene(0.0F, -4.0F, 0.0F);
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 64.0F, 80.0F));
         Vector3f pos = new Vector3f(face).add(at(-0.55F, 1.1F, 0.15F)).lerp(new Vector3f(face).add(at(-0.45F, 0.95F, 0.2F)), k);
         Vector3f eyes = this.victimPoint(f, Knife.Part.HEAD, EYES);
         return new CoupDeGrace.Shot(pos, new Vector3f(face).lerp(eyes, 0.6F), 4, true, 3.0F, Mth.lerp(k, 40.0F, 34.0F));
      } else if (t < 92.0F) {
         Matrix4f knife = this.knifeInScene(f);
         Vector3f pin = knife != null ? Knife.through(knife, blade.pivot) : f.actor.scene(0.0F, -12.0F, 0.0F);
         Vector3f pos = f.victim.scene(0.0F, -4.0F, 0.0F).add(at(-0.9F, 0.9F, 0.15F));
         return new CoupDeGrace.Shot(pos, pin, 5, true, 6.0F, 48.0F);
      } else if (t < 102.0F) {
         Vector3f nape = this.nape(f);
         Vector3f pos = new Vector3f(nape).add(at(-0.7F, 0.9F, 0.55F));
         return new CoupDeGrace.Shot(pos, new Vector3f(nape).add(0.0F, 0.3F, 0.0F), 6, true, 5.0F, 42.0F);
      } else {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 102.0F, 128.0F));
         Vector3f pos = at(2.2F, 2.0F, 1.2F).lerp(at(2.6F, 2.6F, 2.6F), k);
         return new CoupDeGrace.Shot(pos, at(0.5F, 0.55F, 0.7F), 7, true, 2.0F);
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
      ScriptFx.butterfly(s, before, now);
   }
}
