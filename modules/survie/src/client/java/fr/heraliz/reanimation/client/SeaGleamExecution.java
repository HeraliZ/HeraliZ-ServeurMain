package fr.heraliz.reanimation.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class SeaGleamExecution extends Script {
   static final SeaGleamExecution INSTANCE = new SeaGleamExecution();
   static final float ARRIVE = 6.0F;
   static final float GRAB = 26.0F;
   static final float LIFTED = 44.0F;
   static final float COCKED = 62.0F;
   static final float DROP = 104.0F;
   static final float LANDED = 112.0F;
   static final Vector3f SPOT = at(0.85F, -0.05F, 0.0F);
   static final Vector3f STEP = at(0.72F, 0.02F, 0.0F);
   static final Vector3f HELD = at(0.42F, 0.02F, 1.8F);
   static final Vector3f COLLAR = new Vector3f(0.0F, 0.8F, 2.0F);
   static final Vector3f NECK = new Vector3f(0.0F, -0.6F, 0.0F);

   private SeaGleamExecution() {
   }

   @Override
   Blade blade() {
      return Blade.SEA_GLEAM;
   }

   @Override
   void victim(Script.Frame f, float t) {
      CoupDeGrace.Body v = f.victim;
      if (t < 26.0F) {
         prone(f, t, 3.0F * Mth.sin(t * 0.4F), new Vector3f());
      } else if (t < 104.0F) {
         Quaternionf proneRot = new CoupDeGrace.Body().turn(90.0F, -90.0F, 0.0F, 0.0F).rot;
         float sway = 3.0F * Mth.sin(t * 0.5F) * Keys.window(t, 44.0F, 104.0F, 4.0F);
         float jolt = Keys.jolt(t, 96.0F, 8.0F, 2.0F);
         Quaternionf hangRot = new CoupDeGrace.Body().turn(180.0F + sway, 4.0F * jolt, 2.0F * Mth.sin(t * 0.37F), 0.0F).rot;
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 26.0F, 44.0F));
         v.rot.set(proneRot).slerp(hangRot, k);
         Vector3f from = this.proneCollar(f);
         Vector3f held = new Vector3f(HELD).add(0.0F, 0.015F * Mth.sin(t * 1.7F) * Keys.window(t, 44.0F, 96.0F, 3.0F), 0.0F);
         Vector3f collar = new Vector3f(from).lerp(held, k).add(0.0F, 0.25F * Mth.sin((float) Math.PI * k), 0.0F);
         v.anchorPoint(COLLAR, collar);
      } else {
         Vector3f feetHeld = this.feetWhenHeld();
         float fall = Mth.clamp((t - 104.0F) / 3.4F, 0.0F, 1.0F);
         Vector3f feet = new Vector3f(feetHeld.x, feetHeld.y * (1.0F - fall * fall), feetHeld.z);
         float topple = CoupDeGrace.phase(t, 106.5F, 112.0F);
         float angle = 90.0F * topple * topple;
         float bounce = t > 112.0F ? 5.0F * Mth.sin((float) Math.PI * CoupDeGrace.phase(t, 112.0F, 116.0F)) : 0.0F;
         v.turn(180.0F, -angle + bounce, 0.0F, 0.0F).feet(feet.add(0.0F, 0.13F * Mth.sin(angle * (float) (Math.PI / 180.0)), 0.0F));
      }

      if (t >= 96.0F && f.full) {
         f.headOff = true;
         f.head.set(this.flyingHeadAt(f, t));
         f.wounds
            .add(
               new Script.Wound(Knife.Part.BODY, new Vector3f(-2.6F, -1.0F, 0.0F), new Vector3f(2.6F, -1.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), 3.2F, 1.0F)
            );
      }
   }

   private Vector3f proneCollar(Script.Frame f) {
      CoupDeGrace.Body b = new CoupDeGrace.Body();
      Script.Frame g = this.newFrame(26.0F);
      g.victimYaw = f.victimYaw;
      prone(g, 26.0F, 0.0F, new Vector3f());
      b.rot.set(g.victim.rot);
      b.pos.set(g.victim.pos);
      return b.scene(COLLAR);
   }

   private Vector3f feetWhenHeld() {
      CoupDeGrace.Body b = new CoupDeGrace.Body().turn(180.0F, 0.0F, 0.0F, 0.0F);
      b.anchorPoint(COLLAR, HELD);
      return new Vector3f(b.pos);
   }

   private Matrix4f flyingHeadAt(Script.Frame f, float t) {
      Script.Frame cut = this.other(f, 95.99F);
      Matrix4f start = this.victimPart(cut, Knife.Part.HEAD);
      Vector3f velocity = at(-0.03F, 0.085F, 0.045F);
      return flyingHead(start, velocity, new Vector3f(0.3F, 0.2F, 1.0F), 0.32F, t - 96.0F, 0.3F);
   }

   @Override
   void actor(Script.Frame f, float t) {
      CoupDeGrace.Body a = f.actor;
      Vector3f feet = new Vector3f();
      float yaw;
      if (t < 6.0F) {
         yaw = entry(f, t, SPOT, 6.0F, feet);
      } else {
         feet = Keys.vec(
            t,
            6.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            18.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            23.0F,
            STEP.x,
            STEP.y,
            STEP.z,
            36.0F,
            STEP.x,
            STEP.y,
            STEP.z,
            44.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z
         );
         yaw = Keys.key(t, 6.0F, 180.0F, 88.0F, 180.0F, 93.0F, 196.0F, 96.0F, 172.0F, 100.0F, 160.0F, 110.0F, 180.0F);
         f.walk = Keys.window(t, 18.0F, 23.0F, 1.5F) * 0.5F + Keys.window(t, 38.0F, 44.0F, 1.5F) * 0.5F;
         f.stride = t * 0.95F;
      }

      f.kneel = Keys.key(t, 20.0F, 0.0F, 25.0F, 0.4F, 30.0F, 0.4F, 38.0F, 0.0F);
      f.drop = Rig.kneelDrop(f.kneel);
      f.lean = Keys.key(
         t, 0.0F, 0.0F, 18.0F, 0.05F, 24.0F, 0.95F, 28.0F, 0.9F, 44.0F, -0.05F, 88.0F, -0.1F, 92.0F, -0.15F, 96.0F, 0.3F, 100.0F, 0.2F, 110.0F, 0.05F
      );
      feet.add(0.0F, -f.drop, 0.0F);
      a.turn(yaw, 0.0F, 0.0F, 0.0F).feet(feet);
      f.actorFwd = feet.z;
      f.actorSide = -feet.x;
   }

   Vector3f collar(Script.Frame f) {
      return f.victim.scene(COLLAR);
   }

   @Override
   float spin(float t) {
      return Keys.key(t, 8.0F, 0.0F, 17.0F, (float) (Math.PI * 2));
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      k.blood = Keys.key(t, 95.5F, 0.0F, 96.0F, 1.0F);
      k.trail = Keys.window(t, 8.0F, 17.0F, 2.0F) * 0.6F + Keys.window(t, 92.0F, 99.0F, 1.0F) * 1.5F;
   }

   @Override
   public void poseActor(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      CoupDeGrace.bend(m, f.lean);
      CoupDeGrace.Body a = f.actor;
      Blade blade = this.blade();
      Rig.kneel(m.rightLeg, m.leftLeg, f.kneel, 0.0F);
      Rig.walk(m.rightLeg, m.leftLeg, m.rightArm, m.leftArm, f.stride, f.walk, false);
      m.rightArm.xRot = -0.3F;
      m.rightArm.zRot = 0.1F;
      m.leftArm.xRot = -0.2F;
      m.leftArm.zRot = -0.1F;
      Vector3f cut = Knife.inHand(blade, blade.tip, this.spin(t), this.wrist(t));
      Vector3f neck = a.model(f.victim.scene(NECK));
      Vector3f cocked = new Vector3f(-11.0F, -10.0F, 7.0F).add(0.0F, 0.8F * Mth.sin(t * 0.3F), 0.0F);
      Vector3f loaded = new Vector3f(-12.0F, -8.0F, 10.0F);
      Vector3f through = new Vector3f(13.0F, 5.0F, -7.0F);
      if (t >= 6.0F && t < 19.0F) {
         Rig.aim(m.rightArm, blade.pivot, new Vector3f(-8.0F, 3.0F, -6.0F), 0.0F, Keys.window(t, 6.0F, 19.0F, 2.5F));
      } else if (t >= 48.0F && t < 96.0F) {
         Vector3f aim;
         if (t < 88.0F) {
            aim = cocked;
         } else if (t < 92.0F) {
            aim = new Vector3f(cocked).lerp(loaded, CoupDeGrace.smooth(CoupDeGrace.phase(t, 88.0F, 92.0F)));
         } else {
            aim = new Vector3f(loaded).lerp(neck, Keys.strike(t, 92.0F, 96.0F));
         }

         Rig.aim(m.rightArm, cut, aim, Keys.key(t, 86.0F, 0.0F, 92.0F, -1.3F), Keys.key(t, 48.0F, 0.0F, 62.0F, 1.0F));
      } else if (t >= 96.0F && t < 110.0F) {
         Vector3f aim = new Vector3f(neck).lerp(through, CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 100.0F)));
         Rig.aim(m.rightArm, cut, aim, -1.3F, 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 102.0F, 110.0F)));
      } else if (t >= 110.0F) {
         float rest = CoupDeGrace.smooth(CoupDeGrace.phase(t, 110.0F, 115.0F));
         m.rightArm.xRot = Mth.lerp(rest, m.rightArm.xRot, -2.5F);
         m.rightArm.yRot = Mth.lerp(rest, m.rightArm.yRot, -0.45F);
         m.rightArm.zRot = Mth.lerp(rest, m.rightArm.zRot, 0.25F);
      }

      if (t >= 18.0F && t < 106.0F) {
         float grip = Keys.window(t, 18.0F, 106.0F, 2.0F) * Keys.key(t, 18.0F, 0.0F, 26.0F, 1.0F);
         Rig.aim(m.leftArm, Rig.LEFT_HAND, a.model(this.collar(f)), 0.0F, grip);
      }

      Vector3f lookAt;
      if (t < 19.0F && t >= 6.0F) {
         lookAt = Rig.point(m.rightArm, blade.pivot);
      } else if (t >= 96.0F) {
         lookAt = a.model(f.head.transformPosition(new Vector3f(HEAD_CENTRE).div(16.0F)));
      } else {
         lookAt = a.model(f.victim.scene(0.0F, -4.0F, 0.0F));
      }

      CoupDeGrace.look(m.head, lookAt, 1.2F, 1.1F);
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      if (t < 26.0F) {
         float flinch = CoupDeGrace.smooth(CoupDeGrace.phase(t, 22.0F, 26.0F));
         proneLimbs(m, f, age, Keys.window(t, 8.0F, 15.0F, 2.5F), 0.0F, flinch);
      } else {
         CoupDeGrace.Body v = f.victim;
         float alive = 1.0F - CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 98.0F));
         float held = Keys.window(t, 26.0F, 104.0F, 3.0F);
         Vector3f fist = v.model(this.collar(f)).add(0.0F, -2.0F, 1.5F);
         float claw = held * alive;
         Rig.aim(m.rightArm, Rig.HAND, new Vector3f(fist).add(-1.5F, 1.0F * Mth.sin(age * 1.3F), 0.0F), 0.0F, claw);
         Rig.aim(m.leftArm, Rig.LEFT_HAND, new Vector3f(fist).add(1.5F, 1.0F * Mth.sin(age * 1.3F + 1.2F), 0.0F), 0.0F, claw);
         float kick = held * alive;
         m.rightLeg.xRot = 0.55F * kick * Mth.sin(age * 0.9F);
         m.leftLeg.xRot = -0.55F * kick * Mth.sin(age * 0.9F);
         m.rightLeg.zRot = 0.06F;
         m.leftLeg.zRot = -0.06F;
         m.head.yRot = 0.6F * Mth.sin(age * 0.45F) * alive;
         m.head.xRot = (-0.2F + 0.15F * Mth.sin(age * 0.8F)) * alive;
         if (alive < 1.0F) {
            float limp = 1.0F - alive;
            float twitch = Keys.jolt(t, 97.0F, 10.0F, 2.6F);
            m.rightArm.xRot = Mth.lerp(limp, m.rightArm.xRot, 0.1F + 0.2F * twitch);
            m.leftArm.xRot = Mth.lerp(limp, m.leftArm.xRot, 0.1F - 0.2F * twitch);
            m.rightArm.yRot = Mth.lerp(limp, m.rightArm.yRot, 0.0F);
            m.leftArm.yRot = Mth.lerp(limp, m.leftArm.yRot, 0.0F);
            m.rightArm.zRot = Mth.lerp(limp, m.rightArm.zRot, 0.12F);
            m.leftArm.zRot = Mth.lerp(limp, m.leftArm.zRot, -0.12F);
            m.rightLeg.xRot += 0.3F * twitch;
            float fall = CoupDeGrace.smooth(CoupDeGrace.phase(t, 106.0F, 112.0F));
            m.rightArm.xRot = Mth.lerp(fall, m.rightArm.xRot, -0.5F);
            m.leftArm.xRot = Mth.lerp(fall, m.leftArm.xRot, -0.5F);
         }
      }
   }

   @Override
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      if (t < 6.0F) {
         return new CoupDeGrace.Shot(at(2.3F, 1.7F, 1.7F), at(0.3F, -0.1F, 0.6F), 0, true, 3.0F);
      } else if (t < 19.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 6.0F, 19.0F));
         Vector3f pos = at(1.7F, 1.9F, 1.3F).lerp(at(1.5F, 1.7F, 1.2F), k);
         return new CoupDeGrace.Shot(pos, f.actor.scene(-6.0F, 2.0F, -4.0F), 1, true, 3.0F);
      } else if (t < 46.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 19.0F, 46.0F));
         Vector3f pos = at(0.55F, 2.5F, 0.9F).lerp(at(0.5F, 2.4F, 1.3F), k);
         Vector3f target = new Vector3f(f.victim.scene(0.0F, 6.0F, 0.0F)).lerp(f.actor.scene(0.0F, 0.0F, 0.0F), 0.3F);
         return new CoupDeGrace.Shot(pos, target, 2, true, 3.0F);
      } else if (t < 88.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 46.0F, 88.0F));
         Vector3f pos = at(-1.7F, 0.35F, 1.4F).lerp(at(-1.35F, 0.25F, 1.5F), k);
         return new CoupDeGrace.Shot(pos, at(0.5F, 0.05F, 1.55F), 3, true, 2.5F);
      } else if (t < 101.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 88.0F, 101.0F));
         Vector3f pos = at(-1.05F, -1.0F, 1.55F).lerp(at(-0.85F, -0.85F, 1.5F), k);
         return new CoupDeGrace.Shot(pos, f.victim.scene(NECK).lerp(f.actor.scene(0.0F, 0.0F, 0.0F), 0.25F), 4, true, 4.0F);
      } else {
         Vector3f head = f.head.transformPosition(new Vector3f(HEAD_CENTRE).div(16.0F));
         Vector3f pos = at(-1.6F, 2.3F, 1.3F);
         return new CoupDeGrace.Shot(pos, new Vector3f(head).lerp(f.victim.scene(0.0F, 6.0F, 0.0F), 0.5F), 5, true, 3.0F);
      }
   }

   @Override
   public Vector3f end(int side) {
      return new Vector3f(SPOT);
   }

   @Override
   public Vector3f corpse(int side) {
      return at(-0.63F, 0.02F, 0.2F);
   }

   @Override
   public void tick(ExecutionScenes.Scene s, int before, int now) {
      ScriptFx.seaGleam(s, before, now);
   }
}
