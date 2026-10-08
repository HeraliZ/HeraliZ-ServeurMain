package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class Apesanteur extends Finisher {
   static final Apesanteur INSTANCE = new Apesanteur();
   static final float LIFT = 4.0F;
   static final float FLOAT = 12.0F;
   static final float TIGHT = 64.0F;
   static final float FIST = 88.0F;
   static final float SLAM = 92.0F;
   static final int ROCKS = 11;
   static final int HAZE = 9207039;
   static final int DUST = 8023650;

   private Apesanteur() {
      super(ExecutionType.APESANTEUR);
   }

   @Override
   float endFwd() {
      return 2.6F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 2.6F;
   }

   static float[] rock(int i) {
      float torn = 12.0F + 3.5F * i;
      float a = (i * 137.5F + 20.0F) % 360.0F;
      float r = 1.3F + 0.6F * (i * 5 % 7) / 6.0F;
      float size = 0.24F + 0.3F * (i * 3 % 5) / 4.0F;
      float h = 0.9F + 2.4F * (i * 4 % 9) / 8.0F;
      return new float[]{torn, a, r, size, h};
   }

   static float spin(float t) {
      float u = Math.min(t, 92.0F);
      return 0.9F * u + 0.045F * Math.max(0.0F, u - 64.0F) * Math.max(0.0F, u - 64.0F);
   }

   static Vector3f rockAt(int i, float t) {
      float[] k = rock(i);
      Vector3f ground = orbit(k[1], k[2], k[3] * 0.35F);
      if (t < k[0]) {
         return ground;
      }

      float up = ease(t, k[0], k[0] + 20.0F);
      float tight = ease(t, 64.0F, 92.0F);
      float a = k[1] + (spin(t) - spin(k[0])) * (1.0F + 0.25F * (i % 3));
      float r = Mth.lerp(tight, k[2], 0.9F + 0.3F * (i % 3));
      float h = Mth.lerp(tight, k[4], 1.4F + 0.9F * (i * 2 % 3) / 2.0F) + 0.12F * Mth.sin(t * 0.1F + i);
      Vector3f flying = orbit(a, r, h);
      Vector3f p = new Vector3f(ground).lerp(flying, up);
      float fall = in(phase(t, 92.0F + 0.3F * (i % 4), 96.0F + 0.6F * (i % 4)), 2.0F);
      if (fall > 0.0F) {
         Vector3f landed = new Vector3f(flying.x, k[3] * 0.35F, flying.z);
         p = flying.lerp(landed, fall);
      }

      return p;
   }

   static float height(float t) {
      float up = Mth.lerp(ease(t, 12.0F, 44.0F), 0.3F, 2.05F) + 0.1F * Mth.sin(t * 0.09F) * ease(t, 30.0F, 50.0F);
      float fall = in(phase(t, 93.0F, 96.0F), 2.0F);
      return Mth.lerp(fall, up, 0.3F) + 0.1F * Mth.sin((float) Math.PI * phase(t, 96.0F, 100.0F));
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float hover = ease(t, 24.0F, 48.0F) * (1.0F - in(phase(t, 92.0F, 95.0F), 2.0F));
      s.actorPoint.set(at(2.6F, 0.0F, 0.28F * hover + 0.05F * Mth.sin(t * 0.12F) * hover));
      s.actorYaw = 180.0F;
      s.lean = 0.3F * Keys.window(t, 92.0F, 106.0F, 2.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 12.0F) {
         prone(v, t, victimYaw, s.side);
      } else {
         float u = Math.min(t, 92.0F);
         float tumble = 4.5F * (u - 12.0F) * ease(t, 12.0F, 30.0F) + 0.28F * Math.max(0.0F, u - 64.0F) * Math.max(0.0F, u - 64.0F);
         float back = ease(t, 93.0F, 96.0F);
         float yaw = 90.0F * s.side + 0.8F * tumble * (1.0F - back);
         float pitch = Mth.rotLerp(back, -90.0F + 1.25F * tumble, -90.0F);
         float roll = Mth.rotLerp(back, 0.6F * tumble, 0.0F);
         v.turn(yaw, pitch, roll, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, height(t)));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float lift = ease(t, 4.0F, 20.0F) * (1.0F - ease(t, 90.0F, 92.0F));
      pose(m.rightArm, lift, -1.6F - 0.5F * ease(t, 20.0F, 40.0F), 0.0F, 0.15F);
      pose(m.leftArm, lift, -0.6F, 0.0F, -0.9F);
      float hover = ease(t, 24.0F, 48.0F) * (1.0F - ease(t, 92.0F, 95.0F));
      float drift = Mth.sin(age * 0.15F) * hover;
      m.rightLeg.xRot = 0.15F * drift;
      m.leftLeg.xRot = -0.12F * drift - 0.15F * hover;
      float raise = Keys.window(t, 86.0F, 93.0F, 2.0F);
      pose(m.rightArm, raise, -2.95F, 0.0F, 0.1F);
      float down = ease(t, 92.0F, 94.0F) * (1.0F - ease(t, 104.0F, 114.0F));
      pose(m.rightArm, down, -0.35F, 0.0F, 0.15F);
      pose(m.leftArm, down, -0.2F, 0.0F, -0.4F);
      look(m.head, s.actor.model(s.victim.axis(1.0556251F)), 0.9F, 1.0F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= 12.0F && t < 96.0F) {
         float k = ease(t, 12.0F, 30.0F) * (1.0F - ease(t, 93.0F, 96.0F));
         float w = age * 0.12F;
         pose(m.rightArm, k, -1.3F + 0.6F * Mth.sin(w), 0.0F, 0.6F + 0.3F * Mth.sin(w * 1.3F));
         pose(m.leftArm, k, -1.5F + 0.6F * Mth.sin(w + 2.0F), 0.0F, -0.7F - 0.3F * Mth.sin(w * 1.1F));
         pose(m.rightLeg, k, 0.3F * Mth.sin(w + 1.0F), 0.0F, 0.2F);
         pose(m.leftLeg, k, -0.3F * Mth.sin(w + 2.5F), 0.0F, -0.2F);
         pose(m.head, k, 0.2F * Mth.sin(w * 0.7F), 0.3F * Mth.sin(w * 0.5F), 0.0F);
      } else if (t >= 96.0F) {
         m.rightArm.zRot = -0.9F;
         m.leftArm.zRot = 1.0F;
         m.rightArm.xRot = -2.2F;
         m.leftArm.xRot = -2.5F;
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < 26.0F) {
         float k = ease(t, 0.0F, 26.0F);
         return new CoupDeGrace.Shot(at(1.0F, -4.6F + 0.5F * k, 0.35F), at(1.2F, 0.0F, 0.6F + 0.6F * k), 0, true, 3.0F, 62.0F);
      }

      if (t < 64.0F) {
         float k = ease(t, 26.0F, 64.0F);
         return new CoupDeGrace.Shot(orbit(-100.0F - 60.0F * k, 4.6F, 1.6F + 0.8F * k), at(0.2F, 0.0F, 1.7F), 1, true, 2.0F, 62.0F);
      }

      if (t < 92.0F) {
         float a = -160.0F - (t - 64.0F) * 3.5F;
         return new CoupDeGrace.Shot(orbit(a, 4.4F, 2.2F), body, 2, false, 5.0F, 60.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(1.2F, -2.6F, 4.6F), at(0.2F, 0.0F, 0.6F), 3, true, 3.0F, 66.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      return new CoupDeGrace.Shot(at(1.2F, -2.6F, 4.6F).lerp(at(0.4F, -3.6F, 1.0F), k), at(0.3F, 0.0F, 0.4F), 3, false, 2.5F, 62.0F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float field = ease(t, 4.0F, 24.0F) * (1.0F - ease(t, 96.0F, 102.0F));
      if (field > 0.0F) {
         d.decal(FxTex.SIGIL[3], at(0.0F, 0.0F, 0.0F), 6.0F * field, age * 0.006F, r(9207039), g(9207039), b(9207039), 0.35F * field, Draw.Blend.GLOW);

         for (int i = 0; i < 40; i++) {
            float a = i * 97.0F + spin(t) * 0.5F * (i % 2 == 0 ? 1.0F : -1.0F);
            Vector3f p = orbit(a, 0.6F + 3.0F * (i * 7 % 11) / 10.0F, 0.2F + 3.4F * (i * 13 % 17) / 16.0F + 0.15F * Mth.sin(age * 0.05F + i));
            d.sprite(Sprite.DOT, p, 0.07F, 0.0F, 0.9F, 0.85F, 1.0F, 0.7F * field, Draw.Blend.GLOW);
         }
      }

      for (int i = 0; i < 11; i++) {
         float[] k = rock(i);
         if (t >= k[0]) {
            d.decal(FxTex.SCORCH, orbit(k[1], k[2], 0.0F), k[3] * 2.0F, i, 1.0F, 1.0F, 1.0F, 0.85F * ease(t, k[0], k[0] + 3.0F), Draw.Blend.SOFT);
         }

         Vector3f p = rockAt(i, t);
         float tumble = t < k[0] ? 0.0F : (Math.min(t, 92.0F) - k[0]) * 0.02F * (1 + i % 3);
         Matrix4f m = new Matrix4f().translate(p).rotateXYZ(tumble + i, tumble * 0.7F + i * 2.0F, tumble * 0.4F).scale(k[3], k[3] * 0.7F, k[3] * 0.9F);
         d.box(FxTex.ROCK, m, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         if (t >= k[0] && t < 92.0F) {
            d.glow(p, k[3] * 2.2F, r(9207039), g(9207039), b(9207039), 0.18F);
         }
      }

      if (t >= 92.0F && t < 110.0F) {
         float k = phase(t, 92.0F, 96.0F);
         float after = phase(t, 96.0F, 110.0F);
         d.decal(
            FxTex.SHOCK,
            at(0.0F, 0.0F, 0.0F),
            9.0F - 7.0F * k + 9.0F * out(after, 2.0F),
            0.0F,
            r(9207039),
            g(9207039),
            b(9207039),
            0.8F * (1.0F - after),
            Draw.Blend.GLOW
         );
         if (t >= 96.0F) {
            d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 3.6F, 0.4F, 1.0F, 1.0F, 1.0F, 0.9F * ease(t, 96.0F, 98.0F), Draw.Blend.SOFT);
         }
      }

      if (t >= 110.0F) {
         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 3.6F, 0.4F, 1.0F, 1.0F, 1.0F, 0.9F, Draw.Blend.SOFT);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 4) {
         fx.sound(Sfx.HUM, s.actor.axis(1.2F), 0.8F, 0.55F);
         fx.sound(Sfx.REVERSE_SWELL, at(0.0F, 0.0F, 1.0F), 0.5F, 0.6F);
      }

      if (t >= 4.0F && t < 92.0F && t % 2 == 0) {
         Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 3.4F * Vfx.RANDOM.nextFloat(), 0.05F);
         fx.p(Sprite.DUST, p)
            .vel(fx.dir(0.0F, 0.012F + 0.012F * Vfx.RANDOM.nextFloat(), 0.0F))
            .drag(1.0)
            .size(0.12F + 0.15F * Vfx.RANDOM.nextFloat())
            .life(70)
            .color(8023650)
            .soft()
            .roll(0.0F, 0.02F);
      }

      for (int i = 0; i < 11; i++) {
         float[] k = rock(i);
         if (t == (int)k[0]) {
            Vector3f p = orbit(k[1], k[2], 0.1F);
            fx.sound(Sfx.ROCK_RUMBLE, p, 0.5F + 0.5F * k[3], 1.2F - 0.4F * k[3]);
            fx.burst(Sprite.ROCK, p, 6, 0.05F, 0.12F, 30, 8023650, true);
            fx.ring(Sprite.DUST, p, 8, 0.04F, 0.3F, 24, 8023650, true);
         }
      }

      if (t == 40 || t == 64) {
         fx.sound(Sfx.HUM, at(0.0F, 0.0F, 2.0F), 0.8F, t == 40 ? 0.6F : 0.75F);
      }

      if (t == 68) {
         fx.sound(Sfx.REVERSE_SWELL, at(0.0F, 0.0F, 2.0F), 1.0F, 1.0F);
      }

      if (t >= 64.0F && t < 92.0F && t % 4 == 0) {
         fx.sound(Sfx.WHOOSH, rockAt(t % 11, t), 0.5F, 0.7F + 0.02F * (t - 64));
      }

      if (t == 88) {
         fx.sound(Sfx.CHARGE, s.actor.axis(1.8F), 0.7F, 0.5F);
      }

      if (t == 92) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.4F), 1.0F, 0.7F);
      }

      if (t == 96.0F) {
         Vector3f ground = at(0.0F, 0.0F, 0.2F);
         fx.sound(Sfx.SLAM, ground, 1.0F, 0.85F);
         fx.sound(Sfx.IMPACT_HEAVY, ground, 1.0F, 0.9F);
         fx.sound(Sfx.BONE_CRACK, ground, 1.0F, 0.85F);
         fx.sound(Sfx.SHOCKWAVE, ground, 0.8F, 0.9F);
         fx.flash(ground, 0.35F);
         fx.shake(1.0F, 1.0F);
         fx.ring(Sprite.SMOKE, ground, 36, 0.35F, 1.0F, 36, 8023650, true);
         fx.burst(Sprite.ROCK, ground, 20, 0.25F, 0.16F, 30, 8023650, true);
      }

      for (int i = 0; i < 11; i++) {
         int land = (int)(96.0F + 0.6F * (i % 4));
         if (t == land) {
            Vector3f p = rockAt(i, t);
            fx.sound(Sfx.ROCK_HIT, p, 1.0F, 0.8F + 0.1F * (i % 3));
            fx.ring(Sprite.DUST, new Vector3f(p.x, 0.05F, p.z), 10, 0.1F, 0.4F, 22, 8023650, true);
         }
      }
   }
}
