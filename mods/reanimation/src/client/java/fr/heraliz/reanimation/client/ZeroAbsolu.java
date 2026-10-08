package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ZeroAbsolu extends Finisher {
   static final ZeroAbsolu INSTANCE = new ZeroAbsolu();
   static final float STOMP = 8.0F;
   static final float RISE = 16.0F;
   static final float BACK = 58.0F;
   static final float RUN = 80.0F;
   static final float JUMP = 88.0F;
   static final float PEDESTAL = 0.3F;
   static final float WIDTH = 0.95F;
   static final float DEPTH = 0.66F;
   static final int ICE = 12577023;
   static final int CYAN = 9099519;
   static final int SPIKES = 24;

   private ZeroAbsolu() {
      super(ExecutionType.ZERO_ABSOLU);
   }

   @Override
   float endFwd() {
      return 1.0F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.3F;
   }

   static float block(float t) {
      float h = 0.0F;

      for (int b = 0; b < 5; b++) {
         h += 0.47F * out(phase(t, 24.0F + 8.0F * b, 28.0F + 8.0F * b), 3.0F);
      }

      return h;
   }

   static Vector3f mid(float t) {
      return at(0.0F, 0.0F, Mth.lerp(ease(t, 16.0F, 40.0F), 0.3F, 1.3556252F));
   }

   static float[] spike(int i) {
      float tick = 24.0F + 8.0F * (i / 3);
      float a = (i * 137.5F + i / 3 * 40.0F) % 360.0F;
      float r = 1.0F + 0.5F * (i * 7 % 5) / 4.0F;
      float h = 0.6F + 0.7F * (i * 3 % 4) / 3.0F;
      return new float[]{tick, a, r, h};
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float fwd = 1.3F;
      float y = 0.0F;
      float yaw = 180.0F;
      if (t >= 58.0F && t < 80.0F) {
         fwd = Mth.lerp(ease(t, 58.0F, 76.0F), 1.3F, 2.8F);
      } else if (t >= 80.0F && t < 88.0F) {
         fwd = Mth.lerp(in(phase(t, 80.0F, 88.0F), 1.3F), 2.8F, 1.25F);
      } else if (t >= 88.0F) {
         float air = phase(t, 88.0F, 99.0F);
         fwd = Mth.lerp(smooth(phase(t, 88.0F, 96.0F)), 1.25F, 0.98F);
         y = 0.5F * Mth.sin((float) Math.PI * air);
         float spin = in(phase(t, 88.0F, 96.0F), 1.4F);
         yaw = 180.0F - 450.0F * spin;
         yaw = Mth.rotLerp(ease(t, 100.0F, 108.0F), yaw, 180.0F);
         s.actorRoll = -28.0F * Keys.window(t, 93.0F, 99.0F, 2.0F);
      }

      s.actorPoint.set(at(fwd, 0.0F, y));
      s.actorYaw = yaw;
      s.lean = 0.25F * Keys.window(t, 80.0F, 89.0F, 2.0F) - 0.1F * Keys.window(t, 2.0F, 10.0F, 2.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 16.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         rise(v, ease(t, 16.0F, 40.0F), s.side, 0.0F, mid(t));
      } else {
         float u = phase(t, 96.0F, 101.0F);
         float bounce = 0.1F * Mth.sin((float) Math.PI * phase(t, 101.0F, 105.0F));
         Vector3f mid = mid(96.0F).lerp(at(-1.0F, 0.0F, 0.15F + bounce), u * u);
         mid.y = mid.y + 0.35F * Mth.sin((float) Math.PI * Math.min(1.0F, u * 1.3F));
         v.turn(0.0F, 90.0F * smooth(u), 0.0F, 0.0F).anchor(1.0556251F, mid);
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float lift = Keys.window(t, 2.0F, 9.0F, 4.0F);
      m.rightLeg.xRot = -1.0F * lift * (t < 7.0F ? 1.0F : 0.0F);
      float palm = Keys.window(t, 8.0F, 60.0F, 4.0F);
      hold(m.rightArm, s.actor.model(s.victim.axis(1.2F)), palm);
      m.rightArm.xRot = m.rightArm.xRot + 0.03F * Mth.sin(age * 2.0F) * palm;
      pose(m.leftArm, palm, 0.2F, 0.0F, -0.25F);
      if (t >= 58.0F && t < 80.0F) {
         walk(m, (t - 58.0F) * 0.4F, Keys.window(t, 58.0F, 76.0F, 3.0F), -1.0F);
      } else if (t >= 80.0F && t < 88.0F) {
         float run = Keys.window(t, 80.0F, 89.0F, 1.5F);
         float w = (t - 80.0F) * 0.9F;
         pose(m.rightLeg, run, 0.9F * Mth.sin(w), 0.0F, 0.0F);
         pose(m.leftLeg, run, -0.9F * Mth.sin(w), 0.0F, 0.0F);
         pose(m.rightArm, run, -0.9F * Mth.sin(w), 0.0F, 0.1F);
         pose(m.leftArm, run, 0.9F * Mth.sin(w), 0.0F, -0.1F);
      } else if (t >= 88.0F) {
         float tuck = Keys.window(t, 88.0F, 94.0F, 2.0F);
         float kick = Keys.window(t, 93.0F, 100.0F, 2.5F);
         pose(m.rightLeg, tuck, -1.1F, 0.0F, 0.2F);
         pose(m.leftLeg, tuck * 0.6F, -0.5F, 0.0F, 0.0F);
         pose(m.rightLeg, kick, -0.1F, 0.0F, 1.5F);
         pose(m.leftLeg, kick, 0.2F, 0.0F, -0.1F);
         float arms = Keys.window(t, 88.0F, 102.0F, 2.0F);
         pose(m.rightArm, arms, -0.4F, 0.0F, 1.2F);
         pose(m.leftArm, arms, -0.6F, 0.0F, -1.3F);
         float land = Keys.window(t, 99.0F, 108.0F, 3.0F);
         pose(m.rightLeg, land, -0.4F, 0.0F, 0.15F);
         pose(m.leftLeg, land, 0.3F, 0.0F, -0.15F);
      }

      if (t < 88.0F) {
         look(m.head, s.actor.model(s.victim.axis(1.5F)), 0.9F, 0.8F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 22.0F) {
         lyingPose(m);
      }

      if (t >= 16.0F && t < 96.0F) {
         float k = ease(t, 20.0F, 36.0F);
         float frozen = ease(t, 40.0F, 56.0F);
         float shiver = 0.06F * (1.0F - frozen) * Mth.sin(age * 6.0F);
         pose(m.rightArm, k, -1.7F + shiver, -0.4F, 0.25F);
         pose(m.leftArm, k, -1.5F - shiver, 0.5F, -0.3F);
         pose(m.rightLeg, k, 0.1F, 0.0F, 0.08F);
         pose(m.leftLeg, k, -0.25F, 0.0F, -0.06F);
         pose(m.head, k, -0.35F + shiver, 0.25F, 0.0F);
      } else if (t >= 96.0F) {
         deadPose(m, -0.4F * s.side);
         float k = 1.0F - ease(t, 99.0F, 104.0F);
         pose(m.rightArm, k, -1.7F, -0.4F, 0.25F);
         pose(m.leftArm, k, -1.5F, 0.5F, -0.3F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      if (t < 16.0F) {
         return new CoupDeGrace.Shot(at(-0.7F, -2.6F, 0.3F), new Vector3f(s.actor.axis(0.6F)).lerp(at(0.0F, 0.0F, 0.1F), 0.45F), 0, true, 3.0F, 60.0F);
      }

      if (t < 58.0F) {
         float k = ease(t, 16.0F, 58.0F);
         return new CoupDeGrace.Shot(orbit(-100.0F - 70.0F * k, 3.2F, 1.0F + 0.5F * k), at(0.0F, 0.0F, 1.25F), 1, true, 3.0F, 55.0F);
      }

      if (t < 88.0F) {
         Vector3f look = new Vector3f(s.actor.axis(1.0F)).lerp(at(0.0F, 0.0F, 1.2F), 0.4F);
         return new CoupDeGrace.Shot(at(1.4F, -4.6F, 1.0F), look, 2, true, 4.0F, 58.0F);
      }

      if (t < 101.0F) {
         return new CoupDeGrace.Shot(at(0.5F, -2.7F, 0.45F), at(0.45F, 0.0F, 1.3F), 3, true, 3.0F, 64.0F);
      }

      float k = ease(t, 101.0F, 128.0F);
      return new CoupDeGrace.Shot(at(0.5F, -2.7F, 0.45F).lerp(at(1.6F, -3.4F, 2.5F), k), at(-0.4F, 0.0F, 0.4F), 3, false, 2.5F, 58.0F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float melt = 1.0F - ease(t, 106.0F, 148.0F);
      float frost = out(phase(t, 8.0F, 26.0F), 2.0F) * melt;
      if (frost > 0.0F) {
         Vector3f foot = at(1.25F, 0.15F, 0.0F);

         for (int i = 0; i < 4; i++) {
            float k = i / 3.0F;
            float reach = Mth.clamp(frost * 4.0F - i, 0.0F, 1.0F);
            Vector3f c = new Vector3f(foot).lerp(at(0.0F, 0.0F, 0.0F), k);
            d.decal(FxTex.FROST, c, (1.4F + 3.4F * k * k) * reach, i * 1.3F, 1.0F, 1.0F, 1.0F, 0.85F * reach, Draw.Blend.SOFT);
         }

         d.decal(FxTex.FROST, at(0.0F, 0.0F, 0.0F), 5.2F * frost, 0.5F, r(9099519), g(9099519), b(9099519), 0.35F * frost, Draw.Blend.GLOW);
      }

      if (t < 96.0F) {
         float ped = out(phase(t, 16.0F, 24.0F), 2.0F);
         if (ped > 0.0F) {
            Matrix4f m = new Matrix4f().translate(at(0.0F, 0.0F, 0.3F * ped / 2.0F)).scale(0.85F, 0.3F * ped, 0.75F);
            d.box(FxTex.ICE, m, 0.85F, 0.95F, 1.0F, 0.85F, Draw.Blend.CLEAR);
         }

         float h = block(t);
         if (h > 0.01F) {
            Vector3f base = at(0.0F, 0.0F, 0.3F);
            Matrix4f m = new Matrix4f().translate(base.x, base.y + h / 2.0F, base.z).scale(0.95F, h, 0.66F);
            d.box(FxTex.ICE, m, 0.9F, 0.97F, 1.0F, 0.62F, Draw.Blend.CLEAR);
            float front = 0.4F + 0.3F * pulse(t, 6.0F);
            d.glow(new Vector3f(base).add(0.0F, h * 0.6F, 0.0F), 1.6F + h * 0.5F, r(9099519), g(9099519), b(9099519), 0.25F * front);
            Vector3f top = new Vector3f(base).add(0.0F, h, 0.0F);
            d.streak(new Vector3f(top).add(-0.475F, 0.0F, 0.33F), new Vector3f(top).add(0.475F, 0.0F, 0.33F), 0.08F, 1.0F, 1.0F, 1.0F, 0.6F);
            d.streak(new Vector3f(top).add(-0.475F, 0.0F, -0.33F), new Vector3f(top).add(0.475F, 0.0F, -0.33F), 0.08F, 1.0F, 1.0F, 1.0F, 0.6F);
         }

         for (int i = 0; i < 24; i++) {
            float[] sp = spike(i);
            if (!(t < sp[0])) {
               float grow = out(phase(t, sp[0], sp[0] + 3.0F), 2.5F);
               float h2 = sp[3] * (grow + 0.15F * Mth.sin((float) Math.PI * Mth.clamp((t - sp[0]) / 3.0F, 0.0F, 1.0F)));
               Vector3f at = orbit(sp[1], sp[2], 0.0F);
               Matrix4f m = new Matrix4f().translate(at).rotateY(sp[1] * (float) (Math.PI / 180.0)).rotateZ(0.35F).rotateY(0.785F);
               d.box(FxTex.ICE, new Matrix4f(m).translate(0.0F, h2 * 0.32F, 0.0F).scale(0.22F, h2 * 0.64F, 0.22F), 0.8F, 0.92F, 1.0F, 0.9F, Draw.Blend.CLEAR);
               d.box(FxTex.ICE, new Matrix4f(m).translate(0.0F, h2 * 0.8F, 0.0F).scale(0.12F, h2 * 0.36F, 0.12F), 0.9F, 0.97F, 1.0F, 0.95F, Draw.Blend.CLEAR);
            }
         }
      }

      float aura = Keys.window(t, 80.0F, 98.0F, 3.0F);
      if (aura > 0.0F) {
         Vector3f foot = this.actorFoot(s, true);
         d.glow(foot, 0.7F * aura, r(9099519), g(9099519), b(9099519), 0.8F);
         Spectacle.Frame before = this.other(s, t - 1.5F);
         Vector3f prev = this.actorFoot(before, true);
         d.streak(prev, foot, 0.3F * aura, r(12577023), g(12577023), b(12577023), 0.8F * aura);
      }

      if (t >= 96.0F && t < 118.0F) {
         float tau = t - 96.0F;

         for (int i = 0; i < 12; i++) {
            float a = i * 30.0F + 15.0F * (i % 2);
            Vector3f v0 = orbit(180.0F + (a - 180.0F) * 0.55F, 0.18F + 0.05F * (i % 3), 0.0F).add(0.0F, 0.08F + 0.04F * (i % 4), 0.0F);
            Vector3f p0 = at(0.0F, 0.0F, 0.6F + 0.17F * i);
            Vector3f p = new Vector3f(v0).mul(tau).add(p0);
            p.y -= 0.03F * tau * tau;
            float size = 0.2F + 0.07F * (i % 3);
            p.y = Math.max(p.y, size / 2.0F);
            float fade = 1.0F - ease(tau, 14.0F, 22.0F);
            Matrix4f m = new Matrix4f().translate(p).rotateXYZ(tau * 0.3F * (i % 3 + 1), i, tau * 0.2F).scale(size, size * 0.7F, size);
            d.box(FxTex.ICE, m, 0.9F, 0.97F, 1.0F, 0.75F * fade, Draw.Blend.CLEAR);
         }

         float k = tau / 12.0F;
         if (k < 1.0F) {
            d.decal(
               FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 8.0F * out(k, 2.0F), 0.0F, r(12577023), g(12577023), b(12577023), 0.9F * (1.0F - k), Draw.Blend.GLOW
            );
            d.glow(at(0.0F, 0.0F, 1.3F), 5.0F * (1.0F - k), 0.9F, 0.97F, 1.0F, 1.0F - k);
         }
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      Vector3f body = at(0.0F, 0.0F, 1.2F);
      if (t == 3) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(0.5F), 0.5F, 0.8F);
      }

      if (t == 8) {
         Vector3f foot = at(1.25F, 0.15F, 0.05F);
         fx.sound(Sfx.SLAM, foot, 0.7F, 1.2F);
         fx.sound(Sfx.FREEZE, at(0.0F, 0.0F, 0.2F), 1.0F, 1.0F);
         fx.ring(Sprite.SNOWFLAKE, foot, 16, 0.12F, 0.2F, 24, 12577023, false);
         fx.ring(Sprite.SMOKE, foot, 14, 0.08F, 0.5F, 30, 14544639, true);
         fx.shake(0.35F, 0.2F);
      }

      if (t == 16) {
         fx.sound(Sfx.FREEZE, body, 0.9F, 0.8F);
         fx.sound(Sfx.ICE_CRACK, at(0.0F, 0.0F, 0.2F), 0.8F, 0.8F);
      }

      if (t >= 24 && t <= 56 && t % 8 == 0) {
         float h = block(t + 4);
         fx.sound(Sfx.ICE_CRACK, at(0.0F, 0.0F, 0.3F + h), 1.0F, 0.8F + 0.1F * ((t - 24) / 8));
         fx.burst(Sprite.SNOWFLAKE, at(0.0F, 0.0F, 0.3F + h), 10, 0.1F, 0.12F, 20, 12577023, false);
      }

      for (int i = 0; i < 24; i += 3) {
         float[] sp = spike(i);
         if (t == (int)sp[0] && t > 56) {
            fx.sound(Sfx.ICE_CRACK, orbit(sp[1], sp[2], 0.3F), 0.8F, 1.3F);
         }
      }

      if (t >= 8.0F && t < 118.0F) {
         if (t % 2 == 0) {
            Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 3.5F * Vfx.RANDOM.nextFloat(), 3.0F + Vfx.RANDOM.nextFloat());
            fx.p(Sprite.SNOWFLAKE, p).vel(fx.dir(0.006F, -0.03F, 0.004F)).size(0.09F).life(80).color(16055039).roll(0.0F, 0.05F);
         }

         if (t % 3 == 0 && t < 106.0F) {
            Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 0.5F + 1.6F * Vfx.RANDOM.nextFloat(), 0.1F);
            fx.p(Sprite.SMOKE, p)
               .vel(fx.dir((float)Vfx.random(0.01).x, 0.004F, (float)Vfx.random(0.01).z))
               .size(0.6F, 1.2F)
               .life(50)
               .color(14741759)
               .soft()
               .alpha(0.35F)
               .fadeIn(0.3F);
         }
      }

      if (t >= 58.0F && t < 80.0F && (t - 58) % 6 == 3) {
         fx.sound(Sfx.SNOW_CRUNCH, s.actor.axis(0.1F), 0.6F, 1.0F);
      }

      if (t >= 80.0F && t < 88.0F && t % 3 == 0) {
         fx.sound(Sfx.SNOW_CRUNCH, s.actor.axis(0.1F), 0.8F, 1.1F);
         fx.p(Sprite.SNOWFLAKE, s.actor.axis(0.1F)).vel(fx.dir(0.0F, 0.05F, 0.03F)).size(0.12F).life(14).color(12577023);
      }

      if (t == 88) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(0.8F), 0.9F, 1.0F);
      }

      if (t == 92) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(0.8F), 1.0F, 0.8F);
      }

      if (t == 96.0F) {
         fx.sound(Sfx.ICE_SHATTER, body, 1.0F, 1.0F);
         fx.sound(Sfx.IMPACT_HEAVY, body, 1.0F, 1.0F);
         fx.sound(Sfx.BONE_CRACK, body, 0.8F, 0.8F);
         fx.flash(body, 0.7F);
         fx.shake(0.9F, 0.8F);

         for (int i = 0; i < 70; i++) {
            Vector3f p = at(0.0F, 0.0F, 0.3F + 2.3F * Vfx.RANDOM.nextFloat());
            fx.p(i % 3 == 0 ? Sprite.SHARD_B : Sprite.SHARD, p)
               .vel(fx.dir(new Vector3f((float)Vfx.random(0.25).x, 0.1F + 0.15F * Vfx.RANDOM.nextFloat(), (float)Vfx.random(0.25).z - 0.1F)))
               .gravity(0.025)
               .drag(0.97)
               .floor(fx.scene.origin.y + 0.03, 0.3F)
               .size(0.12F + 0.12F * Vfx.RANDOM.nextFloat())
               .life(50 + Vfx.RANDOM.nextInt(30))
               .color(12577023)
               .roll(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 0.6F);
         }

         fx.burst(Sprite.SNOWFLAKE, body, 30, 0.3F, 0.15F, 40, 16777215, false);
         fx.ring(Sprite.SMOKE, at(0.0F, 0.0F, 0.1F), 28, 0.25F, 0.8F, 40, 15135487, true);
      }

      if (t == 101) {
         fx.sound(Sfx.BODY_FALL, at(-1.0F, 0.0F, 0.2F));
         fx.sound(Sfx.SNOW_CRUNCH, at(-1.0F, 0.0F, 0.2F), 1.0F, 0.8F);
         fx.sound(Sfx.SNOW_CRUNCH, s.actor.axis(0.1F), 0.8F, 0.9F);
      }
   }
}
