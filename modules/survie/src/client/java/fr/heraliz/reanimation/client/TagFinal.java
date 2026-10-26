package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class TagFinal extends Finisher {
   static final TagFinal INSTANCE = new TagFinal();
   static final float SHAKE = 2.0F;
   static final float SPRAY = 18.0F;
   static final float DONE = 60.0F;
   static final float LOB = 88.0F;
   static final float TAG_W = 3.4F;
   static final float TAG_H = 1.7F;
   static final float TAG_FWD = -0.45F;
   static final float TAG_UP = 1.75F;
   static final float STAND = 2.4F;
   static final int PINK = 16722100;
   static final int CYAN = 7929855;
   static final int GOLD = 16768040;

   private TagFinal() {
      super(ExecutionType.TAG_FINAL);
   }

   @Override
   float endFwd() {
      return 2.4F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.5F;
   }

   static float painted(float t) {
      return smooth(phase(t, 18.0F, 60.0F));
   }

   static Vector3f tagPoint(float u, float v) {
      return new Vector3f((u - 0.5F) * 3.4F, 1.75F + (0.5F - v) * 1.7F, -0.45F);
   }

   static Vector3f writing(float t) {
      return tagPoint(painted(t), 0.5F + 0.35F * Mth.sin(t * 1.3F));
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float back = ease(t, 10.0F, 18.0F);
      float side = 0.0F;
      if (t >= 14.0F) {
         float follow = ease(t, 14.0F, 18.0F) * (1.0F - ease(t, 60.0F, 66.0F));
         side = -writing(Math.min(t, 60.0F)).x * 0.45F * follow;
      }

      s.actorPoint.set(at(Mth.lerp(back, 1.5F, 2.4F), side, 0.0F));
      s.actorYaw = 180.0F;
      s.lean = 0.2F * Keys.window(t, 88.0F, 94.0F, 2.0F) - 0.08F * Keys.window(t, 18.0F, 60.0F, 3.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 96.0F) {
         prone(v, t, victimYaw, s.side);
      } else {
         float u = phase(t, 96.0F, 108.0F);
         float y = 0.3F + 9.6F * u * (1.0F - u);
         float flip = 360.0F * smooth(u);
         float bounce = 0.1F * Mth.sin((float) Math.PI * phase(t, 108.0F, 112.0F));
         v.turn(90.0F * s.side, -90.0F - flip, 0.0F, 0.0F).anchor(1.0556251F, at(0.5F * u, 0.0F, y + bounce));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float shake = Keys.window(t, 2.0F, 16.0F, 2.0F) + Keys.window(t, 76.0F, 88.0F, 2.0F);
      float rattle = Mth.sin(t * (float) (Math.PI * 2) / 4.0F);
      pose(m.rightArm, shake, -1.4F + 0.45F * rattle, 0.0F, 0.2F);
      float spray = Keys.window(t, 16.0F, 61.0F, 2.0F);
      if (spray > 0.0F) {
         hold(m.rightArm, s.actor.model(writing(t)), spray);
      }

      pose(m.leftArm, spray, -0.25F, 0.0F, -0.35F);
      walk(m, t * 0.5F, 0.6F * spray, 1.0F);
      float watch = Keys.window(t, 64.0F, 77.0F, 2.0F);
      pose(m.rightArm, watch, -1.1F, 0.0F, -0.75F);
      pose(m.leftArm, watch, -1.0F, 0.0F, 0.8F);
      float lob = Keys.window(t, 86.0F, 94.0F, 1.5F);
      pose(m.rightArm, lob, Mth.lerp(ease(t, 86.0F, 89.0F), 0.6F, -2.6F), 0.0F, 0.1F);
      float shrug = Keys.window(t, 104.0F, 122.0F, 4.0F);
      pose(m.rightArm, shrug, -0.4F, 0.0F, 0.6F);
      pose(m.leftArm, shrug, -0.4F, 0.0F, -0.6F);
      look(m.head, s.actor.model(spray > 0.5F ? writing(t) : tagPoint(0.5F, 0.6F)), 1.0F, 1.0F);
      m.head.xRot = m.head.xRot + 0.12F * Mth.sin(t * (float) (Math.PI * 2) / 8.0F) * watch;
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= 96.0F) {
         flail(m, age, 1.0F - ease(t, 108.0F, 111.0F));
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f tag = tagPoint(0.5F, 0.5F);
      if (t < 16.0F) {
         return new CoupDeGrace.Shot(new Vector3f(s.actor.pos).add(at(-2.7F, -1.3F, 1.35F)), s.actor.axis(1.2F), 0, true, 3.0F, 50.0F);
      }

      if (t < 62.0F) {
         return new CoupDeGrace.Shot(at(5.0F, -1.6F, 2.9F), new Vector3f(tag).add(0.0F, -0.35F, 0.0F), 1, true, 3.0F, 52.0F);
      }

      if (t < 88.0F) {
         float k = ease(t, 62.0F, 88.0F);
         return new CoupDeGrace.Shot(at(2.9F, -2.3F + 0.6F * k, 0.45F), new Vector3f(tag).add(0.0F, -0.5F, 0.0F), 2, true, 3.0F, 62.0F);
      }

      if (t < 100.0F) {
         return new CoupDeGrace.Shot(at(2.2F, -4.6F, 1.6F), at(0.3F, 0.0F, 1.4F), 3, true, 3.0F, 64.0F);
      }

      float k = ease(t, 100.0F, 128.0F);
      return new CoupDeGrace.Shot(at(2.2F, -4.6F, 1.6F).lerp(at(3.4F, -2.4F, 2.6F), k), at(0.2F, 0.0F, 1.0F - 0.4F * k), 3, false, 2.5F, 62.0F);
   }

   Vector3f can(Spectacle.Frame s) {
      float t = s.t;
      if (t < 89.0F) {
         return this.actorHand(s, true);
      }

      Spectacle.Frame release = this.other(s, 89.0F);
      Vector3f from = this.actorHand(release, true);
      Vector3f to = tagPoint(0.5F, 0.55F);
      float k = phase(t, 89.0F, 96.0F);
      Vector3f p = new Vector3f(from).lerp(to, k);
      p.y += 6.4F * k * (1.0F - k);
      return p;
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t >= 2.0F && t < 96.0F) {
         Vector3f c = this.can(s);
         Matrix4f m = new Matrix4f().translate(c);
         if (t >= 89.0F) {
            m.rotateX(t * 0.9F).rotateZ(t * 0.4F);
         }

         m.translate(0.0F, -0.12F, 0.0F).scale(0.11F, 0.26F, 0.11F);
         d.cylinder(FxTex.CAN, m, 10, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID, true);
      }

      float u = painted(t);
      if (u > 0.0F && t < 96.0F) {
         float beat = pulse(t, 6.0F);
         float lit = 0.75F + 0.25F * ease(t, 60.0F, 64.0F) + 0.45F * beat * ease(t, 60.0F, 68.0F);
         float flicker = t > 88.0F ? 0.85F + 0.15F * Mth.sin(t * 7.0F) : 1.0F;
         Vector3f left = tagPoint(0.0F, 0.5F);
         Vector3f c = new Vector3f(left).add(3.4F * u / 2.0F, 0.0F, 0.0F);
         Matrix4f m = new Matrix4f().translate(c).scale(3.4F * u, 1.7F, 1.0F);
         d.plane(FxTex.TAG, m, 0.0F, 0.0F, u, 1.0F, 1.0F, 1.0F, 1.0F, Math.min(1.0F, lit * flicker), Draw.Blend.GLOW);
         d.plane(
            FxTex.TAG,
            new Matrix4f(m).translate(0.0F, 0.0F, -0.03F).scale(1.04F, 1.08F, 1.0F),
            0.0F,
            0.0F,
            u,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            0.35F * lit,
            Draw.Blend.GLOW
         );
         if (t >= 60.0F) {
            d.glow(tagPoint(0.5F, 0.55F), 3.2F, r(16722100), g(16722100), b(16722100), 0.25F + 0.35F * beat);
         }
      }

      float spraying = Keys.window(t, 18.0F, 60.0F, 1.0F);
      if (spraying > 0.0F) {
         Vector3f nozzle = this.can(s);
         Vector3f spot = writing(t);
         d.streak(nozzle, spot, 0.3F, r(16722100), g(16722100), b(16722100), 0.45F * spraying);
         d.glow(spot, 0.6F, r(7929855), g(7929855), b(7929855), 0.7F * spraying);
         d.glow(nozzle, 0.3F, 1.0F, 1.0F, 1.0F, 0.6F * spraying);
      }

      if (t >= 96.0F && t < 116.0F) {
         float tau = t - 96.0F;
         float fade = 1.0F - tau / 20.0F;
         int cols = 8;
         int rows = 4;

         for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
               float u0 = (float)i / cols;
               float v0 = (float)j / rows;
               Vector3f p0 = tagPoint(u0 + 0.5F / cols, v0 + 0.5F / rows);
               Vector3f away = new Vector3f(p0).sub(tagPoint(0.5F, 0.55F)).normalize(0.12F + 0.03F * ((i * 7 + j * 3) % 5));
               away.add(0.0F, 0.05F, 0.08F);
               Vector3f p = new Vector3f(p0).add(new Vector3f(away).mul(tau));
               p.y -= 0.012F * tau * tau;
               Matrix4f m = new Matrix4f()
                  .translate(p)
                  .rotateXYZ(tau * 0.15F * ((i + j) % 3 - 1), tau * 0.2F, tau * 0.1F * (i % 2 * 2 - 1))
                  .scale(3.4F / cols, 1.7F / rows, 1.0F);
               d.plane(FxTex.TAG, m, u0, v0, u0 + 1.0F / cols, v0 + 1.0F / rows, 1.0F, 1.0F, 1.0F, fade, Draw.Blend.GLOW);
            }
         }

         float k = tau / 12.0F;
         if (k < 1.0F) {
            Vector3f c = tagPoint(0.5F, 0.55F);
            d.glow(c, 6.0F * (1.0F - k), 1.0F, 0.6F, 0.9F, 1.0F - k);
            d.decal(FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 8.0F * out(k, 2.0F), 0.0F, r(7929855), g(7929855), b(7929855), 0.9F * (1.0F - k), Draw.Blend.GLOW);
            d.decal(
               FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 0.6F + 6.0F * out(k, 2.0F), 0.0F, r(16722100), g(16722100), b(16722100), 0.9F * (1.0F - k), Draw.Blend.GLOW
            );
         }
      }

      float splash = ease(t, 96.0F, 99.0F);
      if (splash > 0.0F) {
         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 3.0F * splash, 0.4F, 1.6F, 0.2F, 1.2F, 0.7F * splash, Draw.Blend.SOFT);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 2) {
         fx.sound(Sfx.SPARKLE, s.actor.axis(1.3F), 0.4F, 1.4F);
      }

      if ((t >= 2.0F && t < 16.0F || t >= 76 && t < 88.0F) && t % 4 == 0) {
         fx.sound(Sfx.CAN_RATTLE, this.can(s), 1.0F, 1.0F + 0.05F * (t / 4 % 2));
      }

      if (t >= 18.0F && t < 60.0F && (t - 18) % 16 == 0) {
         fx.sound(Sfx.SPRAY, this.can(s), 1.0F, 0.95F + 0.1F * Vfx.RANDOM.nextFloat());
      }

      if (t >= 18.0F && t < 60.0F) {
         Vector3f spot = writing(t);
         fx.p(Sprite.DOT, spot)
            .vel(fx.dir((float)Vfx.random(0.02).x, -0.005F, 0.02F))
            .size(0.07F, 0.02F)
            .life(26)
            .color(t % 2 == 0 ? 16722100 : 7929855);
         fx.p(Sprite.SMOKE, spot).vel(fx.dir((float)Vfx.random(0.01).x, 0.005F, 0.01F)).size(0.25F, 0.5F).life(20).color(16722100).soft().alpha(0.3F);
      }

      if (t == 60) {
         fx.sound(Sfx.NEON_IGNITE, tagPoint(0.5F, 0.5F), 0.8F, 1.2F);
         fx.sound(Sfx.SCRATCH, s.actor.axis(1.5F), 0.8F, 1.0F);
      }

      if (t > 60.0F && t < 96.0F && t % 8 == 0) {
         fx.burst(Sprite.SPARK, tagPoint(Vfx.RANDOM.nextFloat(), Vfx.RANDOM.nextFloat()), 4, 0.06F, 0.06F, 12, t % 16 == 0 ? 16722100 : 7929855, false);
      }

      if (t == 88) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.6F), 0.8F, 1.1F);
      }

      if (t == 96.0F) {
         Vector3f c = tagPoint(0.5F, 0.55F);
         fx.sound(Sfx.NEON_IGNITE, c, 1.0F, 0.8F);
         fx.sound(Sfx.BLAST, c, 1.0F, 1.0F);
         fx.sound(Sfx.ICE_SHATTER, c, 0.6F, 1.4F);
         fx.sound(Sfx.IMPACT_HEAVY, at(0.0F, 0.0F, 0.3F), 0.8F, 1.1F);
         fx.flash(c, 0.6F);
         fx.shake(0.9F, 0.8F);

         for (int i = 0; i < 60; i++) {
            fx.p(Sprite.SPLAT, c)
               .vel(fx.dir(new Vector3f((float)Vfx.random(0.3).x, 0.1F + 0.2F * Vfx.RANDOM.nextFloat(), (float)Vfx.random(0.3).z + 0.1F)))
               .gravity(0.02)
               .drag(0.96)
               .size(0.2F + 0.15F * Vfx.RANDOM.nextFloat())
               .life(40 + Vfx.RANDOM.nextInt(20))
               .color(i % 3 == 0 ? 7929855 : (i % 3 == 1 ? 16722100 : 16768040))
               .soft();
         }

         fx.sparks(c, 50, 0.7F, 16777215);
         fx.ring(Sprite.SMOKE, at(0.0F, 0.0F, 0.1F), 24, 0.25F, 0.8F, 30, 10115722, true);
      }

      if (t == 108.0F) {
         fx.sound(Sfx.BODY_FALL, at(0.5F, 0.0F, 0.2F));
         fx.sound(Sfx.BONE_CRACK, at(0.5F, 0.0F, 0.2F), 0.7F, 0.9F);
         fx.ring(Sprite.DUST, at(0.5F, 0.0F, 0.05F), 14, 0.08F, 0.4F, 22, 9075302, true);
      }

      if (t == 112.0F) {
         fx.sound(Sfx.SCRATCH, s.actor.axis(1.5F), 0.7F, 1.2F);
      }
   }
}
