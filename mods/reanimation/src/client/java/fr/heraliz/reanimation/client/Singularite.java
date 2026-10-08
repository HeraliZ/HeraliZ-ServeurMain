package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class Singularite extends Finisher {
   static final Singularite INSTANCE = new Singularite();
   static final Vector3f HOLE = at(0.0F, 0.0F, 2.75F);
   static final float THROW = 14.0F;
   static final float ARRIVE = 22.0F;
   static final float LIFT = 28.0F;
   static final float SWALLOW = 80.0F;
   static final float GONE = 84.0F;
   static final float SPIT = 93.0F;
   static final float COLLAPSE = 93.0F;
   static final int PURPLE = 10178047;
   static final int VIOLET = 14268671;
   static final int DUST = 6969930;

   private Singularite() {
      super(ExecutionType.SINGULARITE);
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

   static float radius(float t) {
      if (t < 6.0F) {
         return 0.0F;
      } else if (t < 14.0F) {
         return 0.06F * ease(t, 6.0F, 10.0F);
      } else if (t < 22.0F) {
         return Mth.lerp(ease(t, 14.0F, 22.0F), 0.06F, 0.32F);
      } else if (t < 93.0F) {
         float gulp = 0.12F * Keys.window(t, 82.0F, 88.0F, 2.0F);
         return (Mth.lerp(ease(t, 22.0F, 88.0F), 0.32F, 0.62F) + gulp) * (1.0F + 0.06F * pulse(t, 4.0F));
      } else {
         return 0.62F * (1.0F - in(phase(t, 93.0F, 95.5F), 2.0F));
      }
   }

   static Vector3f palm(Spectacle.Frame s) {
      return s.actor.scene(-4.0F, 1.0F, -11.5F);
   }

   static Vector3f hole(Spectacle.Frame s, float t) {
      Vector3f palm = palm(s).add(0.0F, 0.12F, 0.0F);
      if (t < 14.0F) {
         return palm;
      }

      float k = ease(t, 14.0F, 22.0F);
      Vector3f p = new Vector3f(palm).lerp(HOLE, k);
      p.y = p.y + 0.9F * Mth.sin((float) Math.PI * k);
      return p;
   }

   static float spin(float t) {
      float u = Mth.clamp(t, 28.0F, 94.0F);
      float a = 2.5F * (u - 28.0F);
      if (u > 64.0F) {
         a += 0.32F * (u - 64.0F) * (u - 64.0F);
      }

      return a;
   }

   static float height(float t) {
      float h = Mth.lerp(ease(t, 28.0F, 60.0F), 0.3F, 1.75F) + 0.35F * ease(t, 64.0F, 94.0F);
      return h + 0.05F * Mth.sin(t * 0.25F) * ease(t, 28.0F, 40.0F);
   }

   static float stretch(float t) {
      return t >= 84.0F ? 0.0F : 0.75F * ease(t, 64.0F, 80.0F) + 0.25F * ease(t, 80.0F, 84.0F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float recoil = Keys.window(t, 95.0F, 110.0F, 3.0F);
      s.actorPoint.set(at(1.6F + 0.12F * recoil, 0.0F, 0.0F));
      s.actorYaw = 180.0F;
      s.lean = -0.18F * recoil + 0.1F * Keys.window(t, 80.0F, 96.0F, 4.0F);
      CoupDeGrace.Body v = s.victim;
      int side = s.side;
      if (t < 28.0F) {
         prone(v, t, victimYaw, side);
      } else if (t < 84.0F) {
         float roll = ease(t, 28.0F, 44.0F);
         float tilt = 30.0F * ease(t, 36.0F, 64.0F) + 25.0F * ease(t, 76.0F, 84.0F);
         float in = in(phase(t, 80.0F, 84.0F), 1.6F);
         Vector3f at = at(0.0F, 0.0F, height(t)).lerp(HOLE, in);
         v.turn(90.0F * side + spin(t), -90.0F + tilt, 0.0F, -180.0F * side * roll).anchor(1.0556251F, at);
      } else if (t < 93.0F) {
         v.turn(90.0F * side + spin(t), -90.0F, 0.0F, 0.0F).anchor(1.0556251F, new Vector3f(HOLE));
      } else {
         float u = phase(t, 93.0F, 96.0F);
         float bounce = 0.1F * Mth.sin((float) Math.PI * phase(t, 96.0F, 100.0F));
         float y = Mth.lerp(u * u, HOLE.y - 0.45F, 0.3F) + bounce;
         v.turn(90.0F * side + spin(96.0F), -90.0F, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, y));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float stance = ease(t, 2.0F, 10.0F);
      m.rightLeg.zRot = 0.14F * stance;
      m.leftLeg.zRot = -0.14F * stance;
      m.rightLeg.xRot = -0.2F * stance;
      m.leftLeg.xRot = 0.15F * stance;
      if (t < 14.0F) {
         hold(m.rightArm, s.actor.model(palm(s).add(0.0F, -0.1F, 0.0F)), ease(t, 1.0F, 7.0F));
         look(m.head, s.actor.model(palm(s)), 0.6F, 0.9F);
      } else if (t < 96.0F) {
         float up = ease(t, 14.0F, 18.0F);
         Vector3f hole = hole(s, t);
         hold(m.rightArm, s.actor.model(hole), up);
         float brace = ease(t, 22.0F, 30.0F);
         Vector3f wrist = s.actor.scene(-3.0F, -3.0F, -8.0F);
         hold(m.leftArm, s.actor.model(new Vector3f(wrist).lerp(hole, 0.15F)), brace);
         float strain = ease(t, 64.0F, 94.0F);
         m.rightArm.xRot = m.rightArm.xRot + 0.04F * strain * Mth.sin(age * 3.1F);
         m.rightArm.zRot = m.rightArm.zRot + 0.03F * strain * Mth.sin(age * 2.3F);
         look(m.head, s.actor.model(hole), 0.8F, 1.1F);
      } else {
         float yank = ease(t, 96.0F, 99.0F);
         float rest = ease(t, 104.0F, 114.0F);
         m.rightArm.xRot = Mth.lerp(rest, Mth.lerp(yank, -2.6F, -0.9F), 0.0F);
         m.rightArm.zRot = Mth.lerp(rest, 0.1F, 0.08F);
         m.leftArm.xRot = Mth.lerp(rest, Mth.lerp(yank, -2.1F, -0.4F), 0.0F);
         m.leftArm.zRot = Mth.lerp(rest, -0.25F, -0.08F);
         look(m.head, s.actor.model(s.victim.axis(1.0556251F)), 0.8F, 0.9F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 32.0F) {
         lyingPose(m);
         float k = ease(t, 26.0F, 32.0F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.6F);
         m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.6F);
      } else if (t < 93.0F) {
         float sway = Mth.sin(age * 0.4F);
         float reach = ease(t, 82.0F, 94.0F);
         float hang = ease(t, 32.0F, 42.0F);
         m.rightArm.xRot = Mth.lerp(reach, Mth.lerp(hang, -1.6F, 0.9F + 0.15F * sway), -2.0F);
         m.leftArm.xRot = Mth.lerp(reach, Mth.lerp(hang, -1.6F, 0.9F - 0.15F * sway), -2.1F);
         m.rightArm.zRot = Mth.lerp(reach, 0.55F, 0.35F);
         m.leftArm.zRot = Mth.lerp(reach, -0.55F, -0.35F);
         m.rightLeg.xRot = 0.5F * hang + 0.1F * sway;
         m.leftLeg.xRot = 0.4F * hang - 0.1F * sway;
         m.rightLeg.zRot = 0.12F;
         m.leftLeg.zRot = -0.12F;
         m.head.xRot = Mth.lerp(reach, -0.75F * hang, 0.3F);
      } else {
         flail(m, age * 1.4F, 1.0F - ease(t, 95.0F, 96.0F));
         float k = ease(t, 95.0F, 97.0F);
         pose(m.rightArm, k, -1.5F, 0.0F, 1.2F);
         pose(m.leftArm, k, -1.6F, 0.0F, -1.2F);
         pose(m.rightLeg, k, 0.0F, 0.0F, 0.3F);
         pose(m.leftLeg, k, 0.0F, 0.0F, -0.25F);
         pose(m.head, k, -0.2F, 0.7F * s.side, 0.0F);
      }
   }

   @Override
   public float victimAlpha(CoupDeGrace.State s) {
      if (s.t >= 84.0F && s.t < 93.0F) {
         return 0.0F;
      } else {
         return stretch(s.t) > 0.0F ? 0.99F : 1.0F;
      }
   }

   @Override
   void drawVictim(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex, float alpha) {
      PlayerModel<?> m = model(1, ex.victimSlim());
      this.poseVictim(m, s, age);
      float k = stretch(s.t);
      float in = ease(s.t, 80.0F, 84.0F);
      d.model(m, ex.victimSkin(), s.victim, (1.0F - 0.22F * k) * (1.0F - 0.85F * in), 1.0F + 0.5F * k * k + 1.6F * in, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < 20.0F) {
         Vector3f pos = s.actor.scene(-27.0F, -13.0F, 42.0F);
         Vector3f look = new Vector3f(palm(s)).lerp(hole(s, t), ease(t, 14.0F, 22.0F));
         return new CoupDeGrace.Shot(pos, look, 0, true, 4.0F, 50.0F);
      }

      if (t < 60.0F) {
         float k = ease(t, 22.0F, 60.0F);
         Vector3f pos = at(0.9F - 0.4F * k, -3.7F, 0.35F + 0.2F * k);
         return new CoupDeGrace.Shot(pos, new Vector3f(body).lerp(HOLE, 0.45F), 1, true, 3.0F, 62.0F);
      }

      if (t < 93.0F) {
         float k = ease(t, 60.0F, 93.0F);
         float a = Mth.lerp(k, 5.0F, -50.0F) * (float) (Math.PI / 180.0);
         float r = Mth.lerp(k, 3.8F, 3.2F);
         Vector3f pos = at(r * Mth.sin(a), -r * Mth.cos(a), Mth.lerp(k, 0.8F, 1.4F));
         return new CoupDeGrace.Shot(pos, new Vector3f(body).lerp(HOLE, 0.3F), 2, true, 5.0F, Mth.lerp(k, 55.0F, 46.0F));
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(4.6F, -3.2F, 1.5F), at(0.2F, 0.0F, 1.6F), 3, true, 3.0F, 62.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      Vector3f pos = at(4.6F, -3.2F, 1.5F).lerp(at(2.6F, -2.0F, 3.6F), k);
      return new CoupDeGrace.Shot(pos, at(0.0F, 0.0F, 0.2F), 3, false, 2.5F, 58.0F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float pull = ease(t, 22.0F, 38.0F) * (1.0F - ease(t, 96.0F, 100.0F));
      if (pull > 0.0F) {
         d.decal(FxTex.SWIRL, at(0.0F, 0.0F, 0.0F), 4.6F, -age * 0.07F, r(10178047), g(10178047), b(10178047), 0.3F * pull, Draw.Blend.GLOW);
         d.decal(FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 4.0F - 0.6F * pulse(t, 6.0F), 0.0F, 0.06F, 0.0F, 0.1F, 0.5F * pull, Draw.Blend.SOFT);
      }

      if (t >= 20.0F && t < 96.0F) {
         for (int i = 0; i < 28; i++) {
            float t0 = 20.0F + i * 1.7F;
            if (!(t < t0)) {
               float period = 34.0F + i % 5 * 6.0F;
               float ph = (t - t0) % period / period;
               float suck = ease(t, 89.0F, 95.0F);
               ph = Mth.lerp(suck, ph, 0.98F);
               Vector3f p = this.debris(i, ph);
               Vector3f prev = this.debris(i, Math.max(0.0F, ph - 0.035F));
               float a = Math.min(1.0F, ph / 0.08F) * (1.0F - smooth(phase(ph, 0.85F, 1.0F)));
               float size = (0.12F + 0.06F * (i % 3)) * (1.0F - 0.6F * ph);
               d.sprite(Sprite.ROCK, p, size, i + age * 0.2F, r(6969930), g(6969930), b(6969930), a, Draw.Blend.SOFT);
               d.streak(prev, p, 0.07F, r(10178047), g(10178047), b(10178047), 0.6F * a);
            }
         }
      }

      float rad = radius(t);
      if (rad > 0.0F) {
         Vector3f c = hole(s, t);
         float big = Math.min(1.0F, rad * 4.0F);
         d.sprite(Sprite.GLOW, c, rad * 7.0F, 0.0F, 0.03F, 0.0F, 0.06F, 0.6F * big, Draw.Blend.SOFT);
         d.glow(c, rad * 4.5F, r(10178047) * 0.6F, g(10178047) * 0.6F, b(10178047) * 0.6F, 0.45F + 0.2F * pulse(t, 4.0F));
         d.sprite(Sprite.RING, c, rad * 3.1F, age * 0.05F, r(14268671), g(14268671), b(14268671), 0.75F, Draw.Blend.GLOW);
         d.sphere(FxTex.VOID, new Matrix4f().translate(c).scale(rad * 2.0F), 18, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         float disk = ease(t, 18.0F, 28.0F) * (1.0F - ease(t, 93.0F, 95.0F));
         if (disk > 0.0F) {
            float speed = 0.12F + 0.3F * ease(t, 64.0F, 94.0F);
            Matrix4f m = new Matrix4f().translate(c).rotateX(1.1907964F).rotateZ(age * speed).scale(3.472F * disk * (0.6F + 0.4F * rad / 0.62F));
            d.plane(FxTex.DISK, m, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.95F, Draw.Blend.GLOW);
         }
      }

      if (t >= 93.0F && t < 96.0F) {
         float k = phase(t, 93.0F, 96.0F);
         d.glow(HOLE, 3.0F * (1.0F - k) + 0.3F, 1.0F, 0.95F, 1.0F, 0.5F * k);
      }

      if (t >= 96.0F && t < 110.0F) {
         float k = (t - 96.0F) / 14.0F;
         Matrix4f sm = new Matrix4f().translate(HOLE).scale(0.5F + 10.0F * out(k, 3.0F));
         d.sphere(FxTex.SWIRL, sm, 20, 0.8F, 0.6F, 1.0F, 0.75F * (1.0F - k) * (1.0F - k), Draw.Blend.GLOW);
         d.glow(HOLE, 7.0F * (1.0F - k), 1.0F, 0.95F, 1.0F, 1.0F - k);
         d.decal(
            FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 11.0F * out(k, 2.0F), 0.0F, r(14268671), g(14268671), b(14268671), 0.9F * (1.0F - k), Draw.Blend.GLOW
         );
      }

      float crater = ease(t, 98.0F, 102.0F);
      if (crater > 0.0F) {
         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 3.4F, 0.7F, 1.0F, 1.0F, 1.0F, 0.9F * crater, Draw.Blend.SOFT);
         float glow = crater * (1.0F - ease(t, 104.0F, 138.0F));
         d.decal(FxTex.SWIRL, at(0.0F, 0.0F, 0.0F), 2.6F, age * 0.02F, r(10178047), g(10178047), b(10178047), 0.3F * glow, Draw.Blend.GLOW);
      }
   }

   Vector3f debris(int i, float ph) {
      float a = (i * 137.5F + ph * 600.0F) * (float) (Math.PI / 180.0);
      float r = Mth.lerp(ph * ph, 2.5F + 0.35F * (i % 3), 0.12F);
      float y = Mth.lerp(smooth(ph), 0.05F, HOLE.y) + 0.25F * Mth.sin((float) Math.PI * ph) * (i % 2 * 2 - 1);
      return new Vector3f(HOLE.x + r * Mth.cos(a), y, HOLE.z + r * Mth.sin(a));
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t == 4) {
         fx.sound(Sfx.HUM, palm(s), 0.7F, 0.7F);
      }

      if (t == 8) {
         fx.sound(Sfx.CHARGE, palm(s), 0.5F, 0.6F);
         fx.sparks(palm(s), 10, 0.12F, 10178047);
      }

      if (t == 14) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.4F), 0.9F, 0.8F);
      }

      if (t == 22) {
         fx.sound(Sfx.BLACKHOLE, HOLE, 1.0F, 0.9F);
         fx.sparks(HOLE, 30, 0.4F, 14268671);
         fx.shake(0.3F, 0.15F);
      }

      if (t == 52) {
         fx.sound(Sfx.BLACKHOLE, HOLE, 1.0F, 1.0F);
      }

      if (t >= 22.0F && t < 96.0F) {
         fx.darken(0.35F + 0.25F * ease(t, 64.0F, 94.0F));
      }

      if (t >= 32 && t < 96.0F && t % 8 == 0) {
         float a = t * 67.0F * (float) (Math.PI / 180.0);
         Vector3f ground = at(2.3F * Mth.cos(a), 2.3F * Mth.sin(a), 0.05F);
         fx.sound(Sfx.ROCK_RUMBLE, ground, 0.6F, 0.9F + 0.02F * (t / 8));
         fx.burst(Sprite.ROCK, ground, 6, 0.12F, 0.18F, 24, 6969930, true);
         fx.ring(Sprite.DUST, ground, 10, 0.06F, 0.3F, 20, 6969930, true);
      }

      if (t >= 22.0F && t < 94.0F && t % 2 == 0) {
         for (int i = 0; i < 2; i++) {
            float a = Vfx.RANDOM.nextFloat() * (float) (Math.PI * 2);
            float r = 2.0F + 1.4F * Vfx.RANDOM.nextFloat();
            Vector3f p = at(r * Mth.cos(a), r * Mth.sin(a), 0.1F);
            Vector3f v = new Vector3f(HOLE).sub(p).mul(0.03F).add(new Vector3f(-Mth.sin(a), 0.0F, Mth.cos(a)).mul(0.03F));
            fx.p(Sprite.SMOKE, p).vel(fx.dir(v)).size(0.35F, 0.08F).life(30).color(6969930).soft().drag(1.0);
         }
      }

      if (t == 81) {
         fx.sound(Sfx.IMPLOSION, HOLE, 1.0F, 1.0F);
      }

      if (t == 96.0F) {
         fx.sound(Sfx.SHOCKWAVE, HOLE, 1.0F, 0.9F);
         fx.sound(Sfx.BLAST, HOLE, 0.8F, 0.7F);
         fx.flash(HOLE, 1.0F);
         fx.shake(1.0F, 0.8F);
         fx.burst(Sprite.ROCK, HOLE, 30, 0.6F, 0.25F, 40, 6969930, true);
         fx.burst(Sprite.EMBER, HOLE, 30, 0.5F, 0.12F, 30, 14268671, false);
         fx.sparks(HOLE, 50, 0.9F, 16777215);
         fx.ring(Sprite.SMOKE, at(0.0F, 0.0F, 0.1F), 32, 0.35F, 0.9F, 30, 6969930, true);
      }

      if (t == 80) {
         fx.sound(Sfx.WHOOSH_HEAVY, HOLE, 1.0F, 0.6F);
      }

      if (t == 84) {
         fx.sound(Sfx.WARP, HOLE, 1.0F, 0.5F);
         fx.sound(Sfx.FLESH, HOLE, 0.8F, 0.6F);
         fx.sparks(HOLE, 40, 0.5F, 14268671);
         fx.flash(HOLE, 0.4F);
         fx.shake(0.4F, 0.2F);
      }

      if (t > 84.0F && t < 93.0F && t % 3 == 0) {
         fx.heartbeat(0.6F);
      }

      if (t == 93) {
         fx.sound(Sfx.BLAST, HOLE, 0.7F, 1.4F);
         fx.sound(Sfx.WHOOSH_HEAVY, HOLE, 1.0F, 1.2F);
         fx.burst(Sprite.EMBER, HOLE, 20, 0.3F, 0.12F, 20, 14268671, false);
      }

      if (t == 96.0F) {
         fx.sound(Sfx.SLAM, at(0.0F, 0.0F, 0.2F), 1.0F, 0.9F);
         fx.sound(Sfx.BONE_CRACK, at(0.0F, 0.0F, 0.2F), 1.0F, 0.85F);
         fx.sound(Sfx.BODY_FALL, at(0.0F, 0.0F, 0.2F));
         fx.ring(Sprite.DUST, at(0.0F, 0.0F, 0.05F), 20, 0.12F, 0.45F, 24, 6969930, true);
      }

      if (t > 98.0F && t < 120.0F && t % 3 == 0) {
         Vector3f p = at((Vfx.RANDOM.nextFloat() - 0.5F) * 2.4F, (Vfx.RANDOM.nextFloat() - 0.5F) * 2.4F, 0.1F);
         fx.p(Sprite.EMBER, p).vel(fx.dir(0.0F, 0.03F, 0.0F)).size(0.1F, 0.02F).life(30).color(10178047);
      }
   }
}
