package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class OndeCeleste extends Finisher {
   static final OndeCeleste INSTANCE = new OndeCeleste();
   static final float NIGHT = 2.0F;
   static final float FLOAT = 12.0F;
   static final float[] TOUCH = new float[]{24.0F, 32.0F, 40.0F, 48.0F, 56.0F, 64.0F, 72.0F};
   static final int[] PARENT = new int[]{-1, 0, 1, 1, 1, 4, 4};
   static final float CALL = 80.0F;
   static final float FALL = 85.0F;
   static final float HOME = 1.6F;
   static final float LIFT = 0.6F;
   static final Vector3f SKY = at(-1.2F, 0.0F, 8.5F);
   static final float SKY_SCALE = 3.4F;
   static final int STAR = 12575999;
   static final int VIOLET = 10514687;
   static final int GOLD = 16770720;

   private OndeCeleste() {
      super(ExecutionType.ONDE_CELESTE);
   }

   @Override
   float endFwd() {
      return 1.6F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.6F;
   }

   static float night(float t) {
      return ease(t, 2.0F, 16.0F) * (1.0F - ease(t, 106.0F, 148.0F));
   }

   Vector3f point(Spectacle.Frame s, int i) {
      return switch (i) {
         case 0 -> s.victim.scene(0.0F, -6.0F, -4.3F);
         case 1 -> s.victim.scene(1.6F, 3.5F, -2.3F);
         case 2 -> this.victimHand(s, true);
         case 3 -> this.victimHand(s, false);
         case 4 -> s.victim.scene(0.0F, 8.5F, -2.3F);
         case 5 -> this.victimFoot(s, true);
         default -> this.victimFoot(s, false);
      };
   }

   static Vector3f sky(Vector3f p, Vector3f mid) {
      return new Vector3f(p).sub(mid).mul(3.4F).add(SKY);
   }

   static Vector3f post(int i) {
      float[] angle = new float[]{30.0F, -30.0F, 60.0F, -60.0F, 15.0F, 45.0F, -45.0F};
      float[] radius = new float[]{1.0F, 0.95F, 1.25F, 1.25F, 0.9F, 1.0F, 1.0F};
      return orbit(angle[i], radius[i], 0.0F);
   }

   static int touch(float t) {
      int k = -1;

      for (int i = 0; i < TOUCH.length; i++) {
         if (t >= TOUCH[i] - 2.0F && t < TOUCH[i] + 5.0F) {
            k = i;
         }
      }

      return k;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      int k = touch(t);
      Vector3f pos = at(1.6F, 0.0F, 0.0F);
      if (k >= 0) {
         pos = post(k);
      }

      s.actorPoint.set(pos);
      s.actorYaw = k >= 0 ? Script.yawToward(pos, new Vector3f()) : 180.0F;
      s.lean = 0.15F * (k >= 0 ? 1.0F : 0.0F) - 0.12F * Keys.window(t, 2.0F, 14.0F, 3.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 12.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         float lit = 0.0F;

         for (float tt : TOUCH) {
            lit += 0.04F * ease(t, tt, tt + 4.0F);
         }

         Vector3f mid = at(0.0F, 0.0F, 1.6556251F + lit + 0.05F * Mth.sin(t * 0.12F));
         rise(v, ease(t, 12.0F, 26.0F), s.side, 10.0F * Mth.sin(t * 0.05F), mid);
      } else {
         float u = phase(t, 96.0F, 104.0F);
         float y = Mth.lerp(u * u, 1.9356251F, 0.15F) + 0.08F * Mth.sin((float) Math.PI * phase(t, 104.0F, 108.0F));
         v.turn(0.0F, 90.0F * smooth(u), 0.0F, 0.0F).anchor(1.0556251F, at(-0.5F * u, 0.0F, y));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float sky = Keys.window(t, 2.0F, 18.0F, 3.0F);
      pose(m.rightArm, sky, -2.9F, 0.0F, 0.15F);
      int k = touch(t);
      if (k >= 0) {
         float reach = Keys.window(t, TOUCH[k] - 2.0F, TOUCH[k] + 5.0F, 1.5F);
         hold(m.rightArm, s.actor.model(this.point(s, k)), reach);
         pose(m.leftArm, reach, -0.3F, 0.0F, -0.6F);
      }

      float call = Keys.window(t, 80.0F, 102.0F, 2.0F);
      float pull = ease(t, 85.0F, 96.0F);
      pose(m.rightArm, call, Mth.lerp(pull, -3.0F, -0.9F), 0.0F, 0.2F);
      pose(m.leftArm, call, -0.4F, 0.0F, -0.3F);
      look(m.head, s.actor.model(t > 80.0F && t < 87.0F ? SKY : s.victim.axis(1.4F)), 0.9F, 1.2F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 18.0F) {
         lyingPose(m);
      }

      if (t >= 12.0F && t < 96.0F) {
         float k = ease(t, 14.0F, 28.0F);
         float breathe = 0.06F * Mth.sin(age * 0.2F);
         pose(m.rightArm, k, -0.25F, 0.0F, 0.85F + breathe);
         pose(m.leftArm, k, -0.25F, 0.0F, -0.85F - breathe);
         pose(m.rightLeg, k, 0.0F, 0.0F, 0.25F);
         pose(m.leftLeg, k, 0.0F, 0.0F, -0.25F);
         pose(m.head, k, -0.45F, 0.0F, 0.0F);

         for (float tt : TOUCH) {
            float j = Keys.jolt(t, tt, 6.0F, 2.0F);
            m.head.xRot -= 0.3F * j;
            m.rightArm.zRot += 0.15F * j;
            m.leftArm.zRot -= 0.15F * j;
         }
      } else if (t >= 96.0F) {
         deadPose(m, 0.5F * s.side);
         float k = 1.0F - ease(t, 102.0F, 106.0F);
         pose(m.rightArm, k, -0.25F, 0.0F, 1.2F);
         pose(m.leftArm, k, -0.25F, 0.0F, -1.2F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < TOUCH[0] - 2.0F) {
         float k = ease(t, 0.0F, TOUCH[0]);
         return new CoupDeGrace.Shot(at(0.6F, -4.0F, 0.4F), at(0.2F, 0.0F, Mth.lerp(k, 1.4F, 2.6F)), 0, true, 3.0F, 66.0F);
      }

      if (t < 56.0F) {
         float a = 55.0F - (t - TOUCH[0]) * 3.4F;
         return new CoupDeGrace.Shot(orbit(a, 4.4F, 2.0F), new Vector3f(body).add(0.0F, 0.1F, 0.0F), 1, false, 4.0F, 58.0F);
      }

      if (t < 70.0F) {
         float k = ease(t, 56.0F, 70.0F);
         return new CoupDeGrace.Shot(at(2.6F, -2.8F + 0.5F * k, 0.3F), at(-0.6F, 0.3F, Mth.lerp(k, 2.0F, 3.4F)), 2, true, 3.0F, 74.0F);
      }

      if (t < 80.0F) {
         float k = ease(t, 70.0F, 80.0F);
         return new CoupDeGrace.Shot(at(2.6F, 2.6F - 0.4F * k, 0.3F), at(-0.6F, -0.3F, Mth.lerp(k, 3.0F, 4.4F)), 5, true, 3.0F, 74.0F);
      }

      if (t < 94.0F) {
         Vector3f comet = comet(Math.max(t, 85.0F));
         return new CoupDeGrace.Shot(at(1.2F, -3.8F, 0.7F), new Vector3f(comet).lerp(body, 0.5F), 3, true, 5.0F, 74.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(0.6F, -2.4F, 2.0F), new Vector3f(body).add(0.0F, 0.3F, 0.0F), 4, true, 3.0F, 56.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      return new CoupDeGrace.Shot(
         at(0.6F, -2.4F, 2.0F).lerp(at(2.0F, -3.6F, 2.6F), k), new Vector3f(at(-0.2F, 0.0F, 0.6F)).lerp(at(0.6F, 0.0F, 0.6F), k), 4, false, 2.5F, 64.0F
      );
   }

   static Vector3f comet(float t) {
      float u = phase(t, 85.0F, 96.0F);
      Vector3f from = new Vector3f(SKY).add(at(0.5F, 1.2F, 2.0F));
      Vector3f to = at(0.0F, 0.0F, 2.2556252F);
      Vector3f p = new Vector3f(from).lerp(to, u * u);
      p.add(at(0.0F, 1.6F * Mth.sin((float) Math.PI * u), 0.0F));
      return p;
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float night = night(t);
      Vector3f mid = s.victim.axis(1.0556251F);
      if (night > 0.0F) {
         d.sphere(
            FxTex.STARFIELD,
            new Matrix4f().translate(at(0.0F, 0.0F, 2.0F)).rotateY(age * 0.002F).scale(34.0F),
            24,
            0.85F,
            0.9F,
            1.0F,
            0.9F * night,
            Draw.Blend.GLOW
         );

         for (int i = 0; i < 4; i++) {
            Vector3f c = new Vector3f(SKY).add(at(-3.0F + 2.2F * i, 4.0F * Mth.sin(i * 1.7F), 1.5F * Mth.cos(i * 2.3F)));
            Matrix4f m = new Matrix4f().translate(c).rotate(d.facing).rotateZ(age * 0.003F + i).scale(9.0F);
            d.plane(FxTex.NEBULA, m, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F * night, Draw.Blend.GLOW);
         }

         d.decal(
            FxTex.SIGIL[2],
            at(0.0F, 0.0F, 0.0F),
            4.2F * ease(t, 12.0F, 24.0F),
            age * 0.01F,
            r(12575999),
            g(12575999),
            b(12575999),
            0.4F * night,
            Draw.Blend.GLOW
         );
      }

      boolean shattered = t >= 96.0F;
      float flare = Keys.window(t, 95.0F, 102.0F, 1.0F);
      float fade = 1.0F - ease(t, 100.0F, 116.0F);
      Vector3f[] pts = new Vector3f[TOUCH.length];

      for (int i = 0; i < TOUCH.length; i++) {
         if (t >= TOUCH[i]) {
            pts[i] = this.point(s, i);
         }
      }

      for (int i = 0; i < TOUCH.length; i++) {
         if (pts[i] != null && !(fade <= 0.0F)) {
            float lit = ease(t, TOUCH[i], TOUCH[i] + 3.0F);
            float twinkle = 0.8F + 0.2F * Mth.sin(age * 0.7F + i * 1.9F);
            float size = (0.8F + 1.2F * flare) * lit;
            d.sprite(Sprite.STAR4, pts[i], size, age * 0.02F + i, 1.0F, 1.0F, 1.0F, twinkle * fade, Draw.Blend.GLOW);
            d.glow(pts[i], 0.9F * lit + 2.0F * flare, r(12575999), g(12575999), b(12575999), 0.6F * fade);
            Vector3f skyPt = sky(pts[i], mid);
            d.sprite(Sprite.STAR4, skyPt, 2.4F * lit, 0.0F, 1.0F, 1.0F, 1.0F, twinkle * night, Draw.Blend.GLOW);
            d.glow(skyPt, 4.0F * lit, r(12575999), g(12575999), b(12575999), 0.4F * night);
            int p = PARENT[i];
            if (p >= 0 && pts[p] != null && !shattered) {
               Vector3f to = new Vector3f(pts[p]).lerp(pts[i], ease(t, TOUCH[i], TOUCH[i] + 3.0F));
               d.streak(pts[p], to, 0.12F, r(12575999), g(12575999), b(12575999), 0.9F);
               d.streak(pts[p], to, 0.4F, r(10514687), g(10514687), b(10514687), 0.35F);
               Vector3f skyFrom = sky(pts[p], mid);
               Vector3f skyTo = new Vector3f(skyFrom).lerp(skyPt, ease(t, TOUCH[i], TOUCH[i] + 6.0F));
               d.streak(skyFrom, skyTo, 0.3F, r(12575999), g(12575999), b(12575999), 0.8F * night);
            }
         }
      }

      int k = touch(t);
      if (k >= 0) {
         float blink = 1.0F - phase(t, TOUCH[k] - 2.0F, TOUCH[k] + 1.0F);
         d.glow(s.actor.axis(1.0F), 2.2F * blink, r(12575999), g(12575999), b(12575999), blink);
         float thread = Keys.window(t, TOUCH[k] - 1.0F, TOUCH[k] + 4.0F, 1.0F);
         Vector3f finger = this.actorHand(s, true);
         Vector3f star = this.point(s, k);
         d.streak(finger, star, 0.05F, 1.0F, 1.0F, 1.0F, thread);
         d.streak(finger, star, 0.2F, r(12575999), g(12575999), b(12575999), 0.4F * thread);
         d.glow(finger, 0.4F, r(12575999), g(12575999), b(12575999), thread);
      }

      if (t >= 83.0F && t < 96.0F) {
         List<Vector3f> trail = new ArrayList<>();

         for (int i = 0; i <= 12; i++) {
            trail.add(comet(Math.max(85.0F, t - i * 0.6F)));
         }

         Vector3f head = trail.get(0);
         d.ribbon(FxTex.BEAM, trail, 1.4F, 1.0F, age * 0.3F, r(16770720), g(16770720), b(16770720), 1.0F, 0.0F, Draw.Blend.GLOW);
         d.ribbon(FxTex.BEAM, trail, 0.5F, 1.0F, age * 0.5F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, Draw.Blend.GLOW);
         d.glow(head, 2.4F, 1.0F, 0.95F, 0.8F, 1.0F);
         d.sprite(Sprite.STAR4, head, 2.0F, age * 0.3F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.GLOW);
      }

      if (t >= 96.0F && t < 114.0F) {
         float q = (t - 96.0F) / 18.0F;
         Vector3f heart = at(0.0F, 0.0F, 2.2556252F);
         d.glow(heart, 2.5F * (1.0F - q), 1.0F, 0.97F, 0.9F, 0.8F * (1.0F - q) * (1.0F - q));

         for (int i = 0; i < 3; i++) {
            Matrix4f m = new Matrix4f().translate(heart).rotate(d.facing).rotateZ(i * 2.1F + q).scale(1.5F + 6.0F * Spectacle.out(q, 2.0F) * (1.0F - 0.2F * i));
            d.plane(FxTex.NEBULA, m, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.8F * (1.0F - q), Draw.Blend.GLOW);
         }

         d.sprite(Sprite.RING, heart, 1.0F + 10.0F * Spectacle.out(q, 2.0F), 0.0F, r(12575999), g(12575999), b(12575999), 1.0F - q, Draw.Blend.GLOW);
         d.decal(
            FxTex.SHOCK,
            at(0.0F, 0.0F, 0.0F),
            1.0F + 10.0F * Spectacle.out(q, 2.0F),
            0.0F,
            r(10514687),
            g(10514687),
            b(10514687),
            0.8F * (1.0F - q),
            Draw.Blend.GLOW
         );
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t >= 2.0F && t < 108.0F) {
         fx.darken(0.55F * night(t));
      }

      if (t == 2) {
         fx.sound(Sfx.REVERSE_SWELL, s.actor.axis(2.0F), 0.8F, 0.6F);
         fx.sound(Sfx.HUM, at(0.0F, 0.0F, 6.0F), 0.7F, 0.5F);
      }

      if (t == 8) {
         fx.sound(Sfx.SPARKLE, at(0.0F, 0.0F, 6.0F), 0.9F, 0.8F);
      }

      if (t == 12) {
         fx.sound(Sfx.CHIME, s.victim.axis(1.0F), 0.7F, 0.7F);
      }

      if (t >= 12.0F && t < 96.0F && t % 3 == 0) {
         Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 0.4F + 0.8F * Vfx.RANDOM.nextFloat(), 0.2F + 2.0F * Vfx.RANDOM.nextFloat());
         fx.p(Sprite.TWINKLE, p).vel(fx.dir(0.0F, 0.015F, 0.0F)).size(0.12F, 0.03F).life(40).color(12575999);
      }

      for (int i = 0; i < TOUCH.length; i++) {
         if (t == (int)TOUCH[i] - 2) {
            fx.sound(Sfx.WARP, s.actor.axis(1.0F), 0.5F, 1.4F + 0.05F * i);
         }

         if (t == (int)TOUCH[i]) {
            Vector3f p = this.point(s, i);
            float[] pitch = new float[]{1.0F, 1.12F, 1.26F, 1.33F, 1.5F, 1.68F, 1.78F};
            fx.sound(Sfx.CHIME, p, 1.0F, pitch[i]);
            fx.sound(Sfx.SPARKLE, p, 0.6F, pitch[i]);
            fx.burst(Sprite.TWINKLE, p, 10, 0.08F, 0.14F, 20, 12575999, false);
            fx.sparks(p, 8, 0.15F, 16777215);
         }
      }

      if (t == 80) {
         fx.sound(Sfx.CHOIR, at(0.0F, 0.0F, 6.0F), 0.8F, 1.1F);
      }

      if (t == 83) {
         fx.sound(Sfx.SWORD_FALL, comet(85.0F), 1.0F, 0.8F);
      }

      if (t >= 85.0F && t < 96.0F) {
         fx.p(Sprite.EMBER, comet(t))
            .vel(fx.dir((float)Vfx.random(0.04).x, 0.02F, (float)Vfx.random(0.04).z))
            .size(0.2F, 0.04F)
            .life(20)
            .color(16770720);
      }

      if (t == 96.0F) {
         Vector3f heart = at(0.0F, 0.0F, 2.2556252F);
         fx.sound(Sfx.BLAST, heart, 1.0F, 1.0F);
         fx.sound(Sfx.HOLY_IMPACT, heart, 1.0F, 1.2F);
         fx.sound(Sfx.SHOCKWAVE, heart, 0.9F, 1.1F);
         fx.flash(heart, 1.0F);
         fx.shake(1.0F, 0.8F);
         fx.burst(Sprite.TWINKLE, heart, 60, 0.4F, 0.2F, 50, 12575999, false);
         fx.burst(Sprite.STAR4, heart, 20, 0.3F, 0.3F, 30, 16777215, false);
         fx.burst(Sprite.EMBER, heart, 30, 0.35F, 0.12F, 40, 10514687, false);
      }

      if (t == 104.0F) {
         fx.sound(Sfx.BODY_FALL, at(-0.5F, 0.0F, 0.2F));
      }

      if (t > 96.0F && t < 126.0F && t % 2 == 0) {
         Vector3f p = s.victim.axis(0.3F + 1.4F * Vfx.RANDOM.nextFloat());
         fx.p(Sprite.TWINKLE, p).vel(fx.dir(0.0F, 0.03F, 0.0F)).size(0.12F, 0.02F).life(30).color(12575999);
      }
   }
}
