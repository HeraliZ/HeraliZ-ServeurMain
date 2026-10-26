package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class Faille extends Finisher {
   static final Faille INSTANCE = new Faille();
   static final float DRAW = 2.0F;
   static final float OPEN = 10.0F;
   static final float DROP = 14.0F;
   static final float LOOP = 18.0F;
   static final float CLAP = 88.0F;
   static final float SHUT = 91.0F;
   static final float SKY = 6.5F;
   static final float RIFT = 1.35F;
   static final int ORANGE = 16751164;
   static final int VIOLET = 11563263;

   private Faille() {
      super(ExecutionType.FAILLE);
   }

   @Override
   float endFwd() {
      return 1.4F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.4F;
   }

   static float fallen(float t) {
      float tau = Math.max(0.0F, t - 18.0F);
      return 0.01012F * (float)Math.pow(tau, 1.93);
   }

   static int loops(float t) {
      return (int)Math.floor(fallen(t) / 6.5F);
   }

   static float height(float t) {
      if (t < 14.0F) {
         return 0.3F;
      }

      if (t < 18.0F) {
         float u = phase(t, 14.0F, 18.0F);
         return 0.3F - 1.6F * u * u;
      }

      if (t < 96.0F) {
         return 6.5F - fallen(t) % 6.5F;
      }

      float bounce = 0.12F * Mth.sin((float) Math.PI * phase(t, 96.0F, 100.0F));
      return 0.3F + bounce;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      s.actorPoint.set(at(1.4F, 0.0F, 0.0F));
      s.actorYaw = 180.0F;
      s.lean = 0.15F * Keys.window(t, 86.0F, 102.0F, 2.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 14.0F) {
         prone(v, t, victimYaw, s.side);
      } else {
         float spin = t < 96.0F ? 3.0F * (t - 14.0F) : 246.0F;
         float wobble = t < 96.0F ? 10.0F * Mth.sin(t * 0.4F) : 0.0F;
         v.turn(90.0F * s.side + spin, -90.0F + wobble, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, height(t)));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      m.rightLeg.zRot = 0.1F;
      m.leftLeg.zRot = -0.1F;
      float trace = Keys.window(t, 2.0F, 12.0F, 2.0F);
      float a = phase(t, 2.0F, 10.0F) * (float) (Math.PI * 2);
      pose(m.rightArm, trace, -1.55F + 0.35F * Mth.sin(a), 0.0F, 0.1F + 0.35F * Mth.cos(a));
      float hold = Keys.window(t, 10.0F, 89.0F, 3.0F);
      pose(m.rightArm, hold, -0.75F + 0.04F * Mth.sin(age * 2.0F), 0.0F, 0.35F);
      pose(m.leftArm, hold, -2.55F + 0.04F * Mth.sin(age * 2.3F), 0.0F, -0.35F);
      float clap = Keys.window(t, 88.0F, 104.0F, 1.5F);
      pose(m.rightArm, clap, -1.45F, -0.35F, -0.1F);
      pose(m.leftArm, clap, -1.45F, 0.35F, 0.1F);
      look(m.head, s.actor.model(s.victim.axis(1.0556251F)), 0.9F, 1.2F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= 14.0F && t < 96.0F) {
         float k = ease(t, 14.0F, 22.0F);
         float wind = Mth.sin(age * (1.2F + 0.02F * (t - 18.0F)));
         pose(m.rightArm, k, -1.6F + 0.35F * wind, 0.0F, 0.9F);
         pose(m.leftArm, k, -1.6F - 0.35F * wind, 0.0F, -0.9F);
         pose(m.rightLeg, k, 0.3F * wind, 0.0F, 0.35F);
         pose(m.leftLeg, k, -0.3F * wind, 0.0F, -0.35F);
         pose(m.head, k, -0.5F, 0.2F * wind, 0.0F);
      } else if (t >= 96.0F) {
         float k = 1.0F - ease(t, 98.0F, 102.0F);
         pose(m.rightArm, k, -1.6F, 0.0F, 0.9F);
         pose(m.leftArm, k, -1.6F, 0.0F, -0.9F);
         pose(m.rightLeg, k, 0.0F, 0.0F, 0.35F);
         pose(m.leftLeg, k, 0.0F, 0.0F, -0.35F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < 14.0F) {
         return new CoupDeGrace.Shot(
            new Vector3f(s.actor.pos).add(at(-2.5F, -1.2F, 1.3F)), new Vector3f(s.actor.axis(1.2F)).lerp(at(0.0F, 0.0F, 0.3F), 0.3F), 0, true, 3.0F, 55.0F
         );
      }

      if (t < 40.0F) {
         return new CoupDeGrace.Shot(at(0.4F, -7.0F, 3.0F), at(0.2F, 0.0F, 3.1F), 1, true, 3.0F, 70.0F);
      }

      if (t < 62.0F) {
         return new CoupDeGrace.Shot(at(-1.1F, 1.2F, 0.25F), at(0.0F, 0.0F, 5.0F), 2, true, 3.0F, 78.0F);
      }

      if (t < 88.0F) {
         float a = -60.0F - (t - 62.0F) * 3.5F;
         return new CoupDeGrace.Shot(orbit(a, 5.4F, 2.6F), at(0.0F, 0.0F, 3.1F), 3, false, 6.0F, 74.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(0.9F, -3.4F, 0.35F), at(0.0F, 0.0F, 1.8F), 4, true, 3.0F, 72.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      return new CoupDeGrace.Shot(
         at(0.9F, -3.4F, 0.35F).lerp(at(1.6F, -2.6F, 2.4F), k), new Vector3f(body).lerp(at(0.7F, 0.0F, 0.4F), 0.3F), 4, false, 2.5F, 60.0F
      );
   }

   static float ground(float t) {
      return out(phase(t, 10.0F, 14.0F), 2.0F) * (1.0F - in(phase(t, 91.0F, 92.5F), 2.0F));
   }

   static float sky(float t) {
      return out(phase(t, 12.0F, 17.0F), 2.0F) * (1.0F - ease(t, 91.5F, 94.0F));
   }

   void rift(Draw d, Vector3f c, float open, float age, boolean up) {
      if (!(open <= 0.0F)) {
         float size = 2.7F * open;
         Matrix4f m = new Matrix4f().translate(c).rotateX((float) (Math.PI / 2)).rotateZ(age * (up ? -0.06F : 0.06F)).scale(size);
         d.plane(FxTex.PORTAL, m, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.95F, Draw.Blend.SOFT);
         d.plane(
            FxTex.PORTAL,
            new Matrix4f(m).translate(0.0F, 0.0F, up ? 0.01F : -0.01F),
            0.0F,
            0.0F,
            1.0F,
            1.0F,
            r(16751164),
            g(16751164),
            b(16751164),
            0.8F,
            Draw.Blend.GLOW
         );
         List<Vector3f> rim = new ArrayList<>();

         for (int i = 0; i <= 32; i++) {
            float a = (float) (Math.PI * 2) * i / 32.0F;
            float r = 1.35F * open * (0.98F + 0.04F * Mth.sin(a * 5.0F + age * 0.5F));
            rim.add(new Vector3f(c).add(r * Mth.cos(a), 0.0F, r * Mth.sin(a)));
         }

         d.ribbon(FxTex.BEAM, rim, 0.18F, 0.6F, age * 0.1F, r(16751164), g(16751164), b(16751164), 1.0F, 1.0F, Draw.Blend.GLOW);
         d.glow(c, 3.2F * open, r(11563263), g(11563263), b(11563263), 0.3F);
      }
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float trace = Keys.window(t, 2.0F, 14.0F, 2.0F);
      if (trace > 0.0F) {
         Vector3f c = new Vector3f(s.actor.pos).add(at(-0.55F, 0.15F, 1.25F));
         float sweep = phase(t, 2.0F, 10.0F) * (float) (Math.PI * 2);
         List<Vector3f> ring = new ArrayList<>();

         for (int i = 0; i <= 24; i++) {
            float a = sweep * i / 24.0F;
            ring.add(new Vector3f(c).add(at(0.0F, -0.35F * Mth.cos(a), 0.35F * Mth.sin(a))));
         }

         d.ribbon(FxTex.BEAM, ring, 0.09F, 0.5F, age * 0.2F, r(16751164), g(16751164), b(16751164), trace, trace, Draw.Blend.GLOW);
         d.glow(c, 0.9F * trace, r(16751164), g(16751164), b(16751164), 0.4F * trace);
      }

      float hold = Keys.window(t, 10.0F, 90.0F, 3.0F);
      if (hold > 0.0F) {
         for (boolean right : new boolean[]{true, false}) {
            Vector3f h = this.actorHand(s, right);
            d.sprite(
               Sprite.RING, h, 0.35F + 0.08F * Mth.sin(age + (right ? 0 : 2)), age * 0.3F, r(16751164), g(16751164), b(16751164), hold, Draw.Blend.GLOW
            );
            d.glow(h, 0.5F, r(16751164), g(16751164), b(16751164), 0.6F * hold);
         }
      }

      this.rift(d, at(0.0F, 0.0F, 0.02F), ground(t), age, false);
      this.rift(d, at(0.0F, 0.0F, 6.5F), sky(t), age, true);
      if (t >= 18.0F && t < 96.0F) {
         Vector3f c = s.victim.axis(1.0556251F);
         float speed = Math.min(1.0F, (fallen(t + 1.0F) - fallen(t)) / 1.1F);

         for (int i = 0; i < 8; i++) {
            float a = i * 0.785F + age * 0.3F;
            Vector3f p = new Vector3f(c).add(0.7F * Mth.cos(a), -0.4F + 0.2F * (i % 3), 0.7F * Mth.sin(a));
            d.streak(p, new Vector3f(p).add(0.0F, 0.6F + 2.4F * speed, 0.0F), 0.05F, 1.0F, 0.9F, 0.85F, 0.25F + 0.45F * speed);
         }
      }

      if (t >= 96.0F) {
         float k = phase(t, 96.0F, 108.0F);
         if (k < 1.0F) {
            d.decal(
               FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 8.0F * out(k, 2.0F), 0.0F, r(16751164), g(16751164), b(16751164), 0.9F * (1.0F - k), Draw.Blend.GLOW
            );
         }

         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 3.4F, 1.3F, 1.0F, 1.0F, 1.0F, 0.9F * ease(t, 96.0F, 98.0F), Draw.Blend.SOFT);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 3) {
         fx.sound(Sfx.PORTAL_OPEN, s.actor.axis(1.3F), 0.6F, 1.5F);
      }

      if (t == 10) {
         fx.sound(Sfx.PORTAL_OPEN, at(0.0F, 0.0F, 0.2F), 1.0F, 1.0F);
         fx.sound(Sfx.WARP, at(0.0F, 0.0F, 0.2F), 0.7F, 0.8F);
         fx.ring(Sprite.EMBER, at(0.0F, 0.0F, 0.1F), 24, 0.15F, 0.15F, 20, 16751164, false);
      }

      if (t == 12) {
         fx.sound(Sfx.PORTAL_OPEN, at(0.0F, 0.0F, 6.5F), 1.0F, 0.8F);
      }

      for (int k = 0; k < 2; k++) {
         float open = k == 0 ? ground(t) : sky(t);
         if (open > 0.5F && t % 2 == 0) {
            float a = Vfx.RANDOM.nextFloat() * (float) (Math.PI * 2);
            Vector3f p = at(1.35F * Mth.cos(a), 1.35F * Mth.sin(a), k == 0 ? 0.05F : 6.5F);
            fx.p(Sprite.SPARK, p)
               .vel(fx.dir(0.04F * Mth.cos(a), k == 0 ? 0.05F : -0.02F, 0.04F * Mth.sin(a)))
               .gravity(0.01)
               .size(0.1F, 0.03F)
               .life(14)
               .color(16751164)
               .stretch(2.0F);
         }
      }

      if (t == 15 || t > 18.0F && t < 96.0F && loops(t) > loops(t - 1)) {
         float pitch = 0.7F + 0.08F * loops(t);
         fx.sound(Sfx.WARP, at(0.0F, 0.0F, 6.5F), 0.8F, pitch);
         fx.sound(Sfx.WHOOSH, at(0.0F, 0.0F, 0.5F), 0.8F, pitch + 0.2F);
         fx.burst(Sprite.EMBER, at(0.0F, 0.0F, 6.3F), 10, 0.1F, 0.12F, 16, 11563263, false);
      }

      if (t == 88) {
         fx.sound(Sfx.IMPACT, s.actor.axis(1.3F), 0.8F, 1.4F);
         fx.sparks(s.actor.axis(1.3F), 14, 0.25F, 16751164);
      }

      if (t == 90) {
         fx.sound(Sfx.PORTAL_CLOSE, at(0.0F, 0.0F, 0.2F), 1.0F, 1.0F);
      }

      if (t == 92) {
         fx.sound(Sfx.PORTAL_CLOSE, at(0.0F, 0.0F, 6.5F), 0.8F, 0.8F);
         fx.ring(Sprite.EMBER, at(0.0F, 0.0F, 0.1F), 30, 0.25F, 0.15F, 18, 16751164, false);
      }

      if (t == 96.0F) {
         Vector3f ground = at(0.0F, 0.0F, 0.2F);
         fx.sound(Sfx.SLAM, ground, 1.0F, 0.9F);
         fx.sound(Sfx.IMPACT_HEAVY, ground, 1.0F, 1.0F);
         fx.sound(Sfx.BONE_CRACK, ground, 1.0F, 0.9F);
         fx.sound(Sfx.SHOCKWAVE, ground, 0.8F, 1.1F);
         fx.flash(ground, 0.4F);
         fx.shake(1.0F, 0.9F);
         fx.ring(Sprite.SMOKE, ground, 32, 0.32F, 0.9F, 34, 6971480, true);
         fx.burst(Sprite.ROCK, ground, 26, 0.35F, 0.18F, 30, 6969930, true);
         fx.burst(Sprite.DROP, s.victim.axis(1.0556251F), 14, 0.25F, 0.12F, 30, 9043984, true);
      }
   }
}
