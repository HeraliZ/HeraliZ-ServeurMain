package fr.heraliz.reanimation.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HomeRunExecution extends Script {
   static final HomeRunExecution INSTANCE = new HomeRunExecution();
   static final float ARRIVE = 6.0F;
   static final float POST_L = 11.0F;
   static final float POST_R = 16.0F;
   static final float BEAM = 23.0F;
   static final float HAUL = 26.0F;
   static final float LOCKED = 40.0F;
   static final float SET = 70.0F;
   static final float FLIP = 111.0F;
   static final Vector3f SPOT = at(0.85F, -0.05F, 0.0F);
   static final Vector3f HAUL_AT = at(0.55F, 0.05F, 0.0F);
   static final Vector3f BAT_AT = at(1.7F, 0.1F, 0.0F);
   static final float POST_SIDE = 0.62F;
   static final float BEAM_LOW = 0.9F;
   static final float BEAM_HIGH = 1.1F;
   static final float BEAM_FWD = -0.02F;
   static final Vector3f KNEELING = at(-0.14F, 0.0F, 0.0F);

   private HomeRunExecution() {
   }

   @Override
   Blade blade() {
      return Blade.BAT;
   }

   @Override
   float spin(float t) {
      return 0.0F;
   }

   @Override
   void props(Script.Frame f, float t) {
      float left = grow(t, 11.0F);
      float right = grow(t, 16.0F);
      Vector3f post = new Vector3f(0.2F, 1.25F, 0.2F);
      if (left > 0.0F) {
         f.props.add(box(Script.Material.POST, at(-0.120000005F, -0.52F, 0.0F).add(0.0F, 0.0F, 0.0F), post, left));
      }

      if (right > 0.0F) {
         f.props.add(box(Script.Material.POST, at(-0.120000005F, 0.72F, 0.0F), post, right));
      }

      if (t >= 19.0F) {
         float fall = Mth.clamp((t - 19.0F) / 4.0F, 0.0F, 1.0F);
         float y = Mth.lerp(fall * fall, 2.6F, 0.9F) + 0.05F * Mth.sin((float) Math.PI * CoupDeGrace.phase(t, 23.0F, 26.0F));
         f.props.add(box(Script.Material.BEAM, at(-0.13F, 0.74F, y), new Vector3f(1.48F, 0.13F, 0.22F), 1.0F));
      }

      if (t >= 26.0F) {
         float shut = CoupDeGrace.smooth(CoupDeGrace.phase(t, 38.0F, 40.0F));
         float y = Mth.lerp(shut, 1.55F, 1.1F);
         f.props.add(box(Script.Material.BEAM, at(-0.13F, 0.74F, y), new Vector3f(1.48F, 0.13F, 0.22F), 1.0F));
      }
   }

   private static float grow(float t, float at) {
      if (t < at - 1.0F) {
         return 0.0F;
      }

      float k = CoupDeGrace.phase(t, at - 1.0F, at + 1.5F);
      return k < 1.0F ? 1.15F * CoupDeGrace.smooth(k) - 0.15F * CoupDeGrace.smooth(CoupDeGrace.phase(t, at + 0.5F, at + 1.5F)) : 1.0F;
   }

   @Override
   void victim(Script.Frame f, float t) {
      CoupDeGrace.Body v = f.victim;
      if (t < 26.0F) {
         prone(f, t, 3.0F * Mth.sin(t * 0.4F), new Vector3f());
      } else {
         Quaternionf proneRot = new CoupDeGrace.Body().turn(90.0F, -90.0F, 0.0F, 0.0F).rot;
         float slump = CoupDeGrace.smooth(CoupDeGrace.phase(t, 97.0F, 106.0F));
         float jolt = Keys.jolt(t, 96.0F, 10.0F, 2.0F);
         float struggle = Keys.window(t, 40.0F, 96.0F, 3.0F) * 3.0F * Mth.sin(t * 0.8F);
         float pitch = 10.0F + 14.0F * slump + 8.0F * jolt;
         f.lift = pitch * (float) (Math.PI / 180.0);
         Quaternionf kneelRot = new CoupDeGrace.Body().turn(struggle, pitch, 0.0F, 0.0F).rot;
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 26.0F, 37.0F));
         v.rot.set(proneRot).slerp(kneelRot, k);
         Vector3f from = new Vector3f(PRONE);
         Vector3f to = new Vector3f(KNEELING).add(0.0F, -Rig.kneelDrop(1.0F), 0.0F);
         if (k < 1.0F) {
            Vector3f chest = new Vector3f(from).lerp(new Vector3f(to).add(0.0F, 1.0556251F, 0.0F), k).add(0.0F, 0.2F * Mth.sin((float) Math.PI * k), 0.0F);
            v.anchor(1.0556251F, chest);
         } else {
            v.feet(to);
         }

         if (t >= 96.0F && f.full) {
            f.headOff = true;
            f.head.set(this.flyingHeadAt(f, t));
            f.wounds
               .add(
                  new Script.Wound(
                     Knife.Part.BODY, new Vector3f(-2.6F, -1.0F, 0.0F), new Vector3f(2.6F, -1.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), 3.2F, 1.0F
                  )
               );
         }
      }
   }

   private Matrix4f flyingHeadAt(Script.Frame f, float t) {
      Script.Frame before = this.other(f, 95.5F);
      Script.Frame after = this.other(f, 96.5F);
      Vector3f a = Knife.through(this.heldAt(before, 95.5F), this.blade().tip);
      Vector3f b = Knife.through(this.heldAt(after, 96.5F), this.blade().tip);
      Vector3f swing = new Vector3f(b).sub(a);
      swing.y = 0.0F;
      if (swing.lengthSquared() < 1.0E-6F) {
         swing.set(at(1.0F, 0.0F, 0.0F));
      }

      Vector3f velocity = swing.normalize().mul(0.95F).add(0.0F, 0.62F, 0.0F);
      Script.Frame cut = this.other(f, 95.99F);
      return flyingHead(this.victimPart(cut, Knife.Part.HEAD), velocity, new Vector3f(0.4F, 1.0F, 0.2F), 0.9F, t - 96.0F, 0.2F);
   }

   @Override
   void actor(Script.Frame f, float t) {
      CoupDeGrace.Body a = f.actor;
      Vector3f feet = new Vector3f();
      Vector3f head = f.victim.scene(0.0F, -4.0F, 0.0F);
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
            24.0F,
            SPOT.x,
            SPOT.y,
            SPOT.z,
            29.0F,
            HAUL_AT.x,
            HAUL_AT.y,
            HAUL_AT.z,
            44.0F,
            HAUL_AT.x,
            HAUL_AT.y,
            HAUL_AT.z,
            54.0F,
            BAT_AT.x,
            BAT_AT.y,
            BAT_AT.z
         );
         float toPost = Keys.key(t, 9.0F, 0.0F, 11.0F, 1.0F, 14.0F, 1.0F, 16.0F, -1.0F, 20.0F, -1.0F, 23.0F, 0.0F);
         yaw = 180.0F - 28.0F * toPost;
         if (t >= 44.0F) {
            yaw = yawToward(feet, head);
         }

         f.walk = Keys.window(t, 24.0F, 29.0F, 1.5F) * 0.6F + Keys.window(t, 44.0F, 54.0F, 2.0F);
         f.stride = t * 0.95F;
      }

      f.lean = Keys.key(
         t,
         0.0F,
         0.0F,
         26.0F,
         0.1F,
         30.0F,
         0.6F,
         36.0F,
         0.2F,
         40.0F,
         0.35F,
         44.0F,
         0.05F,
         70.0F,
         0.1F,
         88.0F,
         -0.05F,
         93.0F,
         -0.1F,
         96.0F,
         0.25F,
         102.0F,
         0.1F,
         118.0F,
         0.0F
      );
      f.kneel = Keys.key(t, 60.0F, 0.0F, 70.0F, 0.18F, 93.0F, 0.25F, 96.0F, 0.1F, 104.0F, 0.0F);
      f.drop = Rig.kneelDrop(f.kneel);
      feet.add(0.0F, -f.drop, 0.0F);
      float turn = Keys.key(t, 86.0F, 0.0F, 92.0F, 18.0F, 96.0F, -25.0F, 102.0F, -55.0F, 110.0F, -30.0F, 118.0F, 0.0F);
      a.turn(yaw + turn, 0.0F, 0.0F, 0.0F).feet(feet);
      f.actorFwd = feet.z;
      f.actorSide = -feet.x;
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      k.blood = Keys.key(t, 95.5F, 0.0F, 96.0F, 0.8F);
      k.trail = Keys.window(t, 90.0F, 101.0F, 1.0F) * 1.5F
         + Keys.window(t, 111.0F, 118.0F, 1.0F) * 0.6F
         + Keys.window(t, 9.0F, 12.0F, 1.0F) * 0.6F
         + Keys.window(t, 14.0F, 17.0F, 1.0F) * 0.6F;
      if (t >= 111.0F && t < 117.0F) {
         k.scene(Knife.flight(this.blade(), this.heldAt(f, 111.0F), this.heldAt(f, 117.0F), CoupDeGrace.phase(t, 111.0F, 117.0F), 0.6F, 1.0F));
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
      m.rightArm.xRot = -0.35F;
      m.rightArm.zRot = 0.1F;
      m.leftArm.xRot = -0.2F;
      m.leftArm.zRot = -0.1F;
      Vector3f sweet = Knife.inHand(blade, blade.tip, this.spin(t), this.wrist(t));
      Vector3f head = a.model(f.victim.scene(0.0F, -4.0F, 0.0F));
      if (t >= 7.0F && t < 25.0F) {
         float[] hits = new float[]{11.0F, 16.0F, 24.0F};
         Vector3f[] tops = new Vector3f[]{at(-0.02F, -0.62F, 1.25F), at(-0.02F, 0.62F, 1.25F), at(-0.02F, 0.0F, 1.03F)};
         int i = t < 13.5F ? 0 : (t < 19.5F ? 1 : 2);
         Vector3f top = a.model(tops[i]);
         Vector3f up = new Vector3f(-4.0F, -20.0F, -4.0F);
         Rig.aim(m.rightArm, sweet, new Vector3f(up).lerp(top, Keys.strike(t, hits[i] - 3.0F, hits[i])), 0.0F, Keys.window(t, 7.0F, 25.0F, 2.0F));
      } else if (t >= 38.0F && t < 41.0F) {
         Vector3f beam = a.model(at(-0.02F, -0.3F, 1.23F));
         Rig.aim(m.rightArm, sweet, new Vector3f(-4.0F, -18.0F, -4.0F).lerp(beam, Keys.strike(t, 38.0F, 40.0F)), 0.0F, Keys.window(t, 36.0F, 43.0F, 2.0F));
      } else if (t >= 58.0F && t < 68.0F) {
         Vector3f tap = new Vector3f(head).add(0.0F, -2.0F * Math.abs(Mth.sin((t - 58.0F) * 0.6F)), 0.0F);
         Rig.aim(m.rightArm, sweet, tap, 1.2F, Keys.window(t, 58.0F, 68.0F, 2.5F));
      } else if (t >= 68.0F && t < 110.0F) {
         Vector3f set = new Vector3f(-6.0F, -14.0F, 12.0F).add(1.5F * Mth.sin(t * 0.7F), 1.0F * Mth.cos(t * 0.7F), 0.0F);
         Vector3f load = new Vector3f(-15.0F, -3.0F, 9.0F);
         Vector3f finish = new Vector3f(13.0F, -9.0F, 9.0F);
         Vector3f aim;
         if (t < 88.0F) {
            aim = set;
         } else if (t < 93.0F) {
            aim = new Vector3f(set).lerp(load, CoupDeGrace.smooth(CoupDeGrace.phase(t, 88.0F, 93.0F)));
         } else if (t < 96.0F) {
            aim = new Vector3f(load).lerp(head, Keys.strike(t, 93.0F, 96.0F));
         } else {
            aim = new Vector3f(head).lerp(finish, CoupDeGrace.smooth(CoupDeGrace.phase(t, 96.0F, 101.0F)));
         }

         Rig.aim(m.rightArm, sweet, aim, 1.2F, Keys.window(t, 68.0F, 110.0F, 3.5F));
      } else if (t >= 110.0F) {
         Rig.aim(m.rightArm, blade.pivot, new Vector3f(-4.0F, 0.0F, -9.0F), 0.0F, Keys.window(t, 110.0F, 120.0F, 1.0F));
      }

      if (t >= 26.0F && t < 42.0F) {
         Rig.aim(m.leftArm, Rig.LEFT_HAND, a.model(f.victim.scene(0.0F, 0.8F, -2.2F)), 0.0F, Keys.window(t, 26.0F, 42.0F, 2.5F));
      } else if (t >= 68.0F && t < 101.0F) {
         Vector3f handle = Rig.point(m.rightArm, Knife.inHand(blade, new Vector3f(blade.pivot).add(0.0F, 0.0F, -3.5F), this.spin(t), this.wrist(t)));
         Rig.aim(m.leftArm, Rig.LEFT_HAND, handle, 0.0F, Keys.window(t, 68.0F, 101.0F, 2.5F));
      } else if (t >= 102.0F && t < 111.0F) {
         float shade = Keys.window(t, 102.0F, 111.0F, 2.0F);
         m.leftArm.xRot = Mth.lerp(shade, m.leftArm.xRot, -2.5F);
         m.leftArm.yRot = Mth.lerp(shade, m.leftArm.yRot, 0.6F);
         m.leftArm.zRot = Mth.lerp(shade, m.leftArm.zRot, 0.4F);
      }

      Vector3f lookAt = t >= 96.0F && f.headOff ? a.model(f.head.transformPosition(new Vector3f(HEAD_CENTRE).div(16.0F))) : head;
      CoupDeGrace.look(m.head, lookAt, 1.3F, 1.2F);
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      if (t < 26.0F) {
         proneLimbs(m, f, age, Keys.window(t, 8.0F, 15.0F, 2.5F), Keys.window(t, 15.0F, 22.0F, 2.5F), 0.0F);
      } else {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 26.0F, 37.0F));
         Rig.kneel(m.rightLeg, m.leftLeg, k, 0.05F);
         m.rightLeg.xRot = m.rightLeg.xRot + k * f.lift;
         m.leftLeg.xRot = m.leftLeg.xRot + k * f.lift;
         float locked = CoupDeGrace.smooth(CoupDeGrace.phase(t, 34.0F, 40.0F));
         m.rightArm.xRot = Mth.lerp(locked, 0.1F + 0.3F * Mth.sin(age * 1.2F), -1.45F);
         m.leftArm.xRot = Mth.lerp(locked, 0.1F - 0.3F * Mth.sin(age * 1.2F), -1.45F);
         m.rightArm.yRot = Mth.lerp(locked, 0.0F, 0.2F);
         m.leftArm.yRot = Mth.lerp(locked, 0.0F, -0.2F);
         m.rightArm.zRot = 0.1F + 0.12F * Keys.window(t, 40.0F, 96.0F, 3.0F) * Mth.sin(age * 1.7F);
         m.leftArm.zRot = -0.1F - 0.12F * Keys.window(t, 40.0F, 96.0F, 3.0F) * Mth.sin(age * 1.7F + 1.0F);
         Vector3f batter = f.victim.model(f.actor.scene(0.0F, -4.0F, 0.0F));
         CoupDeGrace.look(m.head, batter, 1.0F, 0.6F);
         m.head.xRot = m.head.xRot + (0.15F * Keys.jolt(t, 61.0F, 5.0F, 2.5F) + 0.15F * Keys.jolt(t, 66.0F, 5.0F, 2.5F));
         if (t >= 96.0F) {
            float slump = CoupDeGrace.smooth(CoupDeGrace.phase(t, 97.0F, 104.0F));
            m.rightArm.zRot = Mth.lerp(slump, m.rightArm.zRot, 0.05F);
            m.leftArm.zRot = Mth.lerp(slump, m.leftArm.zRot, -0.05F);
         }
      }
   }

   @Override
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      if (t < 8.0F) {
         return new CoupDeGrace.Shot(at(2.3F, 1.7F, 1.7F), at(0.3F, -0.1F, 0.6F), 0, true, 3.0F);
      }

      if (t < 26.0F) {
         return new CoupDeGrace.Shot(at(2.4F, 1.5F, 0.55F), at(0.1F, 0.0F, 0.75F), 1, true, 3.0F);
      }

      if (t < 46.0F) {
         return new CoupDeGrace.Shot(at(0.3F, -2.4F, 1.1F), at(0.1F, 0.0F, 0.9F), 2, true, 3.0F);
      }

      if (t < 72.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 46.0F, 72.0F));
         Vector3f pos = at(1.35F, -1.45F, 1.25F).lerp(at(1.2F, -1.3F, 1.2F), k);
         return new CoupDeGrace.Shot(pos, at(0.2F, 0.35F, 1.1F), 3, true, 2.5F);
      }

      if (t < 99.0F) {
         float k = CoupDeGrace.smooth(CoupDeGrace.phase(t, 72.0F, 96.0F));
         Vector3f pos = at(0.95F, 3.0F, 1.05F).lerp(at(0.85F, 2.6F, 1.1F), k);
         return new CoupDeGrace.Shot(pos, at(0.85F, 0.3F, 1.1F), 4, true, 3.0F);
      }

      if (t < 108.0F) {
         Vector3f headAt = f.head.transformPosition(new Vector3f(HEAD_CENTRE).div(16.0F));
         Vector3f from = f.actor.scene(0.0F, -2.0F, 0.0F);
         Vector3f dir = new Vector3f(headAt).sub(from);
         dir.y = 0.0F;
         if (dir.lengthSquared() > 1.0E-4F) {
            dir.normalize();
         }

         Vector3f pos = new Vector3f(headAt).sub(new Vector3f(dir).mul(3.0F)).add(0.0F, 0.9F, 0.0F);
         return new CoupDeGrace.Shot(pos, headAt, 5, true, 9.0F);
      } else {
         Vector3f chest = f.actor.scene(0.0F, 4.0F, 0.0F);
         return new CoupDeGrace.Shot(new Vector3f(chest).add(at(-2.0F, 0.8F, 0.3F)), chest, 6, true, 3.0F);
      }
   }

   @Override
   public Vector3f end(int side) {
      return new Vector3f(BAT_AT);
   }

   @Override
   public Vector3f corpse(int side) {
      return new Vector3f(KNEELING).add(0.0F, 0.8F, 0.0F);
   }

   @Override
   public void tick(ExecutionScenes.Scene s, int before, int now) {
      ScriptFx.homeRun(s, before, now);
   }
}
