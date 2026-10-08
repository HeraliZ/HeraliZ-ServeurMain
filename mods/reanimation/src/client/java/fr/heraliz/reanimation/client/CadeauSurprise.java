package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class CadeauSurprise extends Finisher {
   static final CadeauSurprise INSTANCE = new CadeauSurprise();
   static final float SNAP = 2.0F;
   static final float DROP = 6.0F;
   static final float LAND = 14.0F;
   static final float[] RIBBONS = new float[]{16.0F, 24.0F, 32.0F, 40.0F};
   static final float SHAKE = 48.0F;
   static final float PULL = 88.0F;
   static final float SIZE = 1.45F;
   static final int GOLD = 16764992;
   static final int RED = 13637680;
   static final int GREEN = 3055176;

   private CadeauSurprise() {
      super(ExecutionType.CADEAU_SURPRISE);
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
      return 1.4F;
   }

   static float[] box(float t) {
      float y = 0.0F;
      if (t < 14.0F) {
         float u = phase(t, 6.0F, 14.0F);
         y = Mth.lerp(u * u, 7.0F, 0.0F);
      }

      float squash = 1.0F - 0.18F * Keys.jolt(t, 14.0F, 8.0F, 1.2F);
      float tilt = 0.0F;
      float roll = 0.0F;
      if (t >= 48.0F && t < 96.0F) {
         float beat = (t - 48.0F) % 8.0F;
         float n = (t - 48.0F) / 48.0F;
         float hop = (0.08F + 0.45F * n) * Mth.sin((float) Math.PI * Mth.clamp(beat / 5.0F, 0.0F, 1.0F));
         y += hop;
         float rattle = (2.0F + 10.0F * n) * Mth.sin(t * 2.3F);
         tilt = rattle * Mth.sin(t * 0.7F);
         roll = rattle * Mth.cos(t * 0.9F);
         squash *= 1.0F - 0.12F * n * Keys.jolt(t, t - beat, 5.0F, 1.5F);
      }

      return new float[]{y, tilt, roll, squash};
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float pull = Keys.window(t, 84.0F, 98.0F, 3.0F);
      float back = ease(t, 96.0F, 102.0F);
      float fwd = Mth.lerp(pull, 1.4F, 1.15F);
      fwd = Mth.lerp(back, fwd, 1.6F);
      float dance = Keys.window(t, 48.0F, 85.0F, 3.0F);
      float sway = 0.12F * Mth.sin(t * (float) Math.PI / 8.0F) * dance;
      s.actorPoint.set(at(fwd, sway, 0.04F * Math.abs(Mth.sin(t * (float) Math.PI / 8.0F)) * dance));
      s.actorYaw = 180.0F + 14.0F * Mth.sin(t * (float) Math.PI / 8.0F) * dance;
      s.lean = 0.25F * pull - 0.15F * Keys.window(t, 96.0F, 102.0F, 1.5F);
      CoupDeGrace.Body v = s.victim;
      if (t < 96.0F) {
         prone(v, t, victimYaw, s.side);
      } else {
         v.turn(90.0F * s.side, -90.0F, 0.0F, -180.0F * s.side).anchor(1.0556251F, at(0.0F, 0.0F, 0.15F));
      }
   }

   @Override
   public float victimAlpha(CoupDeGrace.State s) {
      return s.t >= 13.5F && s.t < 96.0F ? 0.0F : 1.0F;
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float snap = Keys.window(t, 0.0F, 14.0F, 2.0F);
      pose(m.rightArm, snap, -2.2F, 0.0F, 0.35F);
      float tie = Keys.window(t, RIBBONS[0] - 2.0F, 48.0F, 3.0F);
      float w = t * (float) (Math.PI * 2) / 8.0F;
      pose(m.rightArm, tie, -1.3F + 0.4F * Mth.sin(w), 0.0F, 0.3F);
      pose(m.leftArm, tie, -1.3F - 0.4F * Mth.sin(w), 0.0F, -0.3F);
      float dance = Keys.window(t, 48.0F, 85.0F, 3.0F);
      float sw = Mth.sin(t * (float) Math.PI / 8.0F);
      pose(m.rightArm, dance, -0.6F - 0.6F * sw, 0.0F, 0.5F + 0.3F * sw);
      pose(m.leftArm, dance, -0.6F + 0.6F * sw, 0.0F, -0.5F + 0.3F * sw);
      pose(m.rightLeg, dance, -0.25F * sw, 0.0F, 0.12F);
      pose(m.leftLeg, dance, 0.25F * sw, 0.0F, -0.12F);
      float pull = Keys.window(t, 86.0F, 97.0F, 2.0F);
      Vector3f bow = at(0.0F, 0.0F, box(t)[0] + 1.3775F);
      hold(m.rightArm, s.actor.model(bow), pull);
      float yank = ease(t, 93.0F, 96.0F);
      m.rightArm.xRot += 0.6F * yank * pull;
      float tada = Keys.window(t, 98.0F, 120.0F, 3.0F);
      pose(m.rightArm, tada, -2.6F, 0.0F, 0.7F);
      pose(m.leftArm, tada, -2.6F, 0.0F, -0.7F);
      look(m.head, s.actor.model(at(0.0F, 0.0F, 1.0F)), 0.9F, 0.9F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= 96.0F) {
         deadPose(m, 0.5F * s.side);
         m.rightArm.xRot = -2.6F;
         m.leftArm.xRot = -2.8F;
         m.rightArm.zRot = 0.5F;
         m.leftArm.zRot = -0.5F;
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      if (t < 16.0F) {
         float k = ease(t, 0.0F, 14.0F);
         return new CoupDeGrace.Shot(at(3.4F, -4.2F, 1.4F), at(0.5F, 0.0F, Mth.lerp(k, 2.6F, 0.9F)), 0, true, 4.0F, 64.0F);
      }

      if (t < 48.0F) {
         float k = ease(t, 14.0F, 48.0F);
         return new CoupDeGrace.Shot(orbit(-130.0F + 90.0F * k, 3.0F, 2.0F), at(0.0F, 0.0F, 0.9F), 1, true, 2.5F, 58.0F);
      }

      if (t < 88.0F) {
         return new CoupDeGrace.Shot(at(0.7F, -3.8F, 1.3F), at(0.6F, 0.0F, 0.95F), 2, true, 3.0F, 58.0F);
      }

      if (t < 100.0F) {
         return new CoupDeGrace.Shot(at(1.4F, -2.0F, 2.0F), at(0.3F, 0.0F, 1.3F), 3, true, 3.0F, 55.0F);
      }

      float k = ease(t, 100.0F, 128.0F);
      return new CoupDeGrace.Shot(at(1.4F, -2.0F, 2.0F).lerp(at(2.6F, -3.2F, 2.6F), k), at(0.4F, 0.0F, 0.6F), 4, false, 2.5F, 60.0F);
   }

   static Matrix4f frame(float t) {
      float[] b = box(t);
      return new Matrix4f()
         .translate(at(0.0F, 0.0F, b[0]))
         .rotateX(b[1] * (float) (Math.PI / 180.0))
         .rotateZ(b[2] * (float) (Math.PI / 180.0))
         .scale(1.45F * (2.0F - b[3]) * 0.5F + 0.725F, 1.45F * b[3], 1.45F * (2.0F - b[3]) * 0.5F + 0.725F)
         .translate(0.0F, 0.5F, 0.0F);
   }

   static void part(Draw d, Matrix4f m, ResourceLocation tex, float x, float y, float z, float sx, float sy, float sz, float a) {
      d.box(tex, new Matrix4f(m).translate(x, y, z).scale(sx, sy, sz), 1.0F, 1.0F, 1.0F, a, a < 1.0F ? Draw.Blend.CLEAR : Draw.Blend.SOLID);
   }

   static void present(Draw d, Matrix4f m, float tieA, float tieB) {
      part(d, m, FxTex.WRAP, 0.0F, -0.07F, 0.0F, 1.0F, 0.86F, 1.0F, 1.0F);
      part(d, m, FxTex.WRAP, 0.0F, 0.41F, 0.0F, 1.07F, 0.19F, 1.07F, 1.0F);
      if (tieA > 0.0F) {
         part(d, m, FxTex.RIBBON, 0.0F, -0.07F - 0.43F * (1.0F - tieA), 0.0F, 1.014F, 0.872F * tieA, 0.16F, 1.0F);
         part(d, m, FxTex.RIBBON, 0.0F, 0.41F, 0.0F, 1.08F * tieA, 0.2F, 0.16F, 1.0F);
      }

      if (tieB > 0.0F) {
         part(d, m, FxTex.RIBBON, 0.0F, -0.07F - 0.43F * (1.0F - tieB), 0.0F, 0.16F, 0.872F * tieB, 1.014F, 1.0F);
         part(d, m, FxTex.RIBBON, 0.0F, 0.41F, 0.0F, 0.16F, 0.2F, 1.08F * tieB, 1.0F);
      }
   }

   static void bow(Draw d, Matrix4f m, float grow, float age) {
      if (!(grow <= 0.0F)) {
         part(d, m, FxTex.RIBBON, 0.0F, 0.54F, 0.0F, 0.15F * grow, 0.09F * grow, 0.15F * grow, 1.0F);

         for (int i = 0; i < 4; i++) {
            float big = i < 2 ? 1.0F : 0.75F;
            float side = i % 2 == 0 ? 1.0F : -1.0F;
            float turn = (i < 2 ? 0.0F : (float) (Math.PI / 2)) + 0.06F * Mth.sin(age * 0.2F + i);
            Matrix4f loop = new Matrix4f(m).translate(0.0F, 0.55F, 0.0F).rotateY(turn).rotateZ(side * 0.45F).scale(grow * big);
            List<Vector3f> pts = new ArrayList<>();

            for (int k = 0; k <= 14; k++) {
               float a = (float) Math.PI + (float) (Math.PI * 2) * k / 14.0F;
               pts.add(loop.transformPosition(new Vector3f(side * (0.2F + 0.2F * Mth.cos(a)), 0.12F * Mth.sin(a), 0.0F)));
            }

            d.tube(FxTex.RIBBON, pts, 0.045F * grow, 6, 0.3F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         }

         for (int side = -1; side <= 1; side += 2) {
            Matrix4f tail = new Matrix4f(m)
               .translate(side * 0.12F, 0.5F, 0.14F)
               .rotateY(side * 0.5F)
               .rotateX(0.35F)
               .translate(0.0F, 0.0F, 0.15F)
               .scale(0.09F, 0.02F, 0.32F * grow);
            d.box(FxTex.RIBBON, tail, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         }
      }
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t >= 5.0F && t < 96.0F) {
         Matrix4f m = frame(t);
         float tieA = out(phase(t, RIBBONS[0], RIBBONS[0] + 4.0F), 2.0F);
         float tieB = out(phase(t, RIBBONS[1], RIBBONS[1] + 4.0F), 2.0F);
         present(d, m, tieA, tieB);
         bow(d, m, out(phase(t, RIBBONS[2], RIBBONS[3] + 4.0F), 2.5F), age);

         for (float rb : RIBBONS) {
            float k = phase(t, rb, rb + 6.0F);
            if (k > 0.0F && k < 1.0F) {
               d.glow(m.transformPosition(new Vector3f(0.0F, 0.5F, 0.0F)), 1.4F * (1.0F - k), r(16764992), g(16764992), b(16764992), 0.6F * (1.0F - k));
            }
         }
      }

      if (t >= 96.0F && t < 148.0F) {
         float tau = t - 96.0F;
         float fade = 1.0F - ease(t, 128.0F, 148.0F);
         Matrix4f base = new Matrix4f().translate(at(0.0F, 0.0F, 0.0F)).scale(1.45F).translate(0.0F, 0.5F, 0.0F);
         float open = Math.min(1.0F, out(tau / 5.0F, 2.0F)) - 0.06F * Mth.sin((float) Math.PI * phase(tau, 5.0F, 9.0F));
         float[][] sides = new float[][]{{1.0F, 0.0F}, {-1.0F, 0.0F}, {0.0F, 1.0F}, {0.0F, -1.0F}};

         for (float[] sd : sides) {
            Matrix4f w = new Matrix4f(base).translate(sd[0] * 0.5F, -0.5F, sd[1] * 0.5F);
            if (sd[0] != 0.0F) {
               w.rotateZ(-sd[0] * (float) (Math.PI / 2) * open);
            } else {
               w.rotateX(sd[1] * (float) (Math.PI / 2) * open);
            }

            part(d, w, FxTex.WRAP, -sd[0] * 0.02F, 0.43F, -sd[1] * 0.02F, sd[0] != 0.0F ? 0.04F : 1.0F, 0.86F, sd[1] != 0.0F ? 0.04F : 1.0F, fade);
            part(d, w, FxTex.RIBBON, -sd[0] * 0.02F, 0.43F, -sd[1] * 0.02F, sd[0] != 0.0F ? 0.05F : 0.16F, 0.87F, sd[1] != 0.0F ? 0.05F : 0.16F, fade);
         }

         part(d, base, FxTex.WRAP, 0.0F, -0.49F, 0.0F, 1.0F, 0.02F, 1.0F, fade);
         Vector3f lid = at(-0.25F * tau, 0.12F * tau, 1.2F + 0.42F * tau - 0.026F * tau * tau);
         lid.y = Math.max(lid.y, 0.12F);
         boolean flying = lid.y > 0.13F;
         Matrix4f lm = new Matrix4f()
            .translate(lid)
            .rotateX(flying ? tau * 0.25F : 2.9F)
            .rotateZ(flying ? tau * 0.12F : 0.3F)
            .scale(1.45F)
            .translate(0.0F, -0.41F, 0.0F);
         part(d, lm, FxTex.WRAP, 0.0F, 0.41F, 0.0F, 1.07F, 0.19F, 1.07F, fade);
         part(d, lm, FxTex.RIBBON, 0.0F, 0.41F, 0.0F, 1.08F, 0.2F, 0.16F, fade);
         part(d, lm, FxTex.RIBBON, 0.0F, 0.41F, 0.0F, 0.16F, 0.2F, 1.08F, fade);
         if (fade >= 1.0F) {
            bow(d, lm, 1.0F, age);
         }

         float k = tau / 12.0F;
         if (k < 1.0F) {
            d.glow(at(0.0F, 0.0F, 0.8F), 5.0F * (1.0F - k), 1.0F, 0.9F, 0.6F, 1.0F - k);
            d.decal(
               FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 7.0F * out(k, 2.0F), 0.0F, r(16764992), g(16764992), b(16764992), 0.8F * (1.0F - k), Draw.Blend.GLOW
            );
         }
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 2) {
         fx.sound(Sfx.IMPACT, s.actor.axis(1.8F), 0.4F, 2.0F);
         fx.sound(Sfx.SLEIGH_BELLS, at(0.0F, 0.0F, 4.0F), 1.0F, 1.0F);
         fx.burst(Sprite.STAR5, this.actorHand(s, true), 8, 0.1F, 0.14F, 16, 16764992, false);
      }

      if (t >= 6.0F && t < 14.0F) {
         fx.p(Sprite.TWINKLE, at((float)Vfx.random(0.6).x, (float)Vfx.random(0.6).z, box(t)[0] + 1.45F))
            .vel(fx.dir(0.0F, 0.03F, 0.0F))
            .size(0.2F, 0.05F)
            .life(20)
            .color(16764992);
      }

      if (t == 14) {
         fx.sound(Sfx.SLAM, at(0.0F, 0.0F, 0.2F), 0.9F, 1.2F);
         fx.sound(Sfx.SNOW_CRUNCH, at(0.0F, 0.0F, 0.2F), 1.0F, 0.7F);
         fx.ring(Sprite.SNOWFLAKE, at(0.0F, 0.0F, 0.1F), 24, 0.18F, 0.18F, 30, 16777215, false);
         fx.ring(Sprite.SMOKE, at(0.0F, 0.0F, 0.1F), 18, 0.12F, 0.6F, 30, 15660287, true);
         fx.shake(0.4F, 0.3F);
      }

      for (int i = 0; i < RIBBONS.length; i++) {
         if (t == (int)RIBBONS[i]) {
            fx.sound(Sfx.RIBBON, at(0.0F, 0.0F, 1.0F), 1.0F, 1.0F + 0.1F * i);
            fx.burst(Sprite.TWINKLE, at(0.0F, 0.0F, 1.2F), 8, 0.12F, 0.16F, 18, 16764992, false);
         }
      }

      if (t >= 48.0F && t < 96.0F && (t - 48) % 8 == 0) {
         float n = (t - 48.0F) / 48.0F;
         fx.sound(Sfx.GIFT_SHAKE, at(0.0F, 0.0F, 0.7F), 0.7F + 0.3F * n, 1.0F - 0.2F * n);
         fx.ring(Sprite.SNOWFLAKE, at(0.0F, 0.0F, 0.05F), 10, 0.06F + 0.08F * n, 0.12F, 18, 16777215, false);
      }

      if (t >= 72 && t < 96.0F && t % 4 == 2) {
         fx.sound(Sfx.GIFT_SHAKE, at(0.0F, 0.0F, 0.7F), 0.5F, 1.2F);
      }

      if (t == 88) {
         fx.sound(Sfx.RIBBON, at(0.0F, 0.0F, 1.5F), 1.0F, 0.8F);
      }

      if (t == 96.0F) {
         Vector3f c = at(0.0F, 0.0F, 0.8F);
         fx.sound(Sfx.CONFETTI_POP, c, 1.0F, 1.0F);
         fx.sound(Sfx.JINGLE_CHORD, c, 1.0F, 1.0F);
         fx.sound(Sfx.BLAST, c, 0.7F, 1.3F);
         fx.flash(c, 0.5F);
         fx.shake(0.8F, 0.6F);
         int[] colours = new int[]{13637680, 3055176, 16764992, 16777215, 3833087, 16740304};

         for (int i = 0; i < 120; i++) {
            fx.p(Sprite.CONFETTI, c)
               .vel(fx.dir(new Vector3f((float)Vfx.random(0.32).x, 0.2F + 0.3F * Vfx.RANDOM.nextFloat(), (float)Vfx.random(0.32).z)))
               .gravity(0.012)
               .drag(0.93)
               .size(0.14F)
               .life(70 + Vfx.RANDOM.nextInt(40))
               .color(colours[i % colours.length])
               .soft()
               .roll(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 0.6F);
         }

         fx.burst(Sprite.STAR5, c, 20, 0.35F, 0.25F, 30, 16764992, false);
         fx.sparks(c, 40, 0.6F, 16777215);
      }

      if (t > 96.0F && t < 118.0F && t % 2 == 0) {
         Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 2.6F * Vfx.RANDOM.nextFloat(), 3.5F);
         fx.p(t % 4 == 0 ? Sprite.SNOWFLAKE : Sprite.CONFETTI, p)
            .vel(fx.dir(0.0F, -0.03F, 0.0F))
            .size(0.12F)
            .life(90)
            .color(t % 4 == 0 ? 16777215 : (t % 8 == 2 ? 13637680 : 3055176))
            .soft()
            .roll(0.0F, 0.1F);
      }
   }
}
