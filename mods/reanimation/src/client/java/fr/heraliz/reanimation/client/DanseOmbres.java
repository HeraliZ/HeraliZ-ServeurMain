package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class DanseOmbres extends Finisher {
   static final DanseOmbres INSTANCE = new DanseOmbres();
   static final float[] SPLIT = new float[]{16.0F, 24.0F, 32.0F, 40.0F};
   static final float[] POST = new float[]{45.0F, 135.0F, 225.0F, 315.0F};
   static final float POST_R = 1.45F;
   static final float[] HITS = new float[]{46.0F, 54.0F, 62.0F, 70.0F, 76.0F, 80.0F, 84.0F, 88.0F};
   static final float[] HIGH = new float[]{0.3F, 1.4F, 1.8F, 2.2F, 2.5F, 2.75F, 3.0F, 3.25F};
   static final float APEX = 3.6F;
   static final float LEAP = 90.0F;
   static final int PURPLE = 8011007;
   static final int DARK = 1706542;

   private DanseOmbres() {
      super(ExecutionType.DANSE_OMBRES);
   }

   @Override
   float endFwd() {
      return 1.1F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.2F;
   }

   static float height(float t) {
      if (t < HITS[0]) {
         return 0.3F;
      }

      for (int k = 0; k < HITS.length - 1; k++) {
         if (t < HITS[k + 1]) {
            float u = phase(t, HITS[k], HITS[k + 1]);
            return Mth.lerp(u, HIGH[k], HIGH[k + 1]) + 2.2F * u * (1.0F - u);
         }
      }

      if (t < 96.0F) {
         return Mth.lerp(out(phase(t, HITS[HITS.length - 1], 96.0F), 2.0F), HIGH[HIGH.length - 1], 3.6F);
      }

      float u = phase(t, 97.0F, 104.0F);
      float bounce = 0.1F * Mth.sin((float) Math.PI * phase(t, 104.0F, 108.0F));
      return Mth.lerp(u * u, 3.6F, 0.3F) + bounce;
   }

   static int hit(float t) {
      int k = -1;

      for (int i = 0; i < HITS.length; i++) {
         if (t >= HITS[i]) {
            k = i;
         }
      }

      return k;
   }

   static float[] clone(int i, float t) {
      if (!(t < SPLIT[i]) && !(t > 102.0F)) {
         Vector3f post = orbit(POST[i], 1.45F, 0.0F);
         Vector3f actor = at(1.2F, 0.0F, 0.0F);
         Vector3f p;
         if (t < SPLIT[i] + 6.0F) {
            float rise = out(phase(t, SPLIT[i], SPLIT[i] + 3.0F), 2.0F);
            float k = smooth(phase(t, SPLIT[i] + 2.0F, SPLIT[i] + 6.0F));
            p = new Vector3f(at(0.6F, 0.0F, 0.0F)).lerp(post, k);
            p.y -= 1.9F * (1.0F - rise);
         } else {
            p = new Vector3f(post);
         }

         for (int k = 1; k < HITS.length; k++) {
            if (k % 4 == i) {
               float in = smooth(phase(t, HITS[k] - 2.5F, HITS[k]));
               float back = smooth(phase(t, HITS[k] + 2.0F, HITS[k] + 5.0F));
               float w = in * (1.0F - back);
               if (w > 0.0F) {
                  Vector3f strike = orbit(POST[i], 0.62F, HIGH[k] - 0.95F);
                  p.lerp(strike, w);
               }
            }
         }

         float leap = smooth(phase(t, 90.0F, 96.0F));
         if (leap > 0.0F) {
            Vector3f meet = orbit(POST[i], 0.72F, 2.6F);
            Vector3f from = new Vector3f(p);
            p = from.lerp(meet, leap);
            p.y = p.y + 0.5F * Mth.sin((float) Math.PI * leap);
         }

         float yaw = Script.yawToward(p, new Vector3f(0.0F, p.y, 0.0F));
         return new float[]{p.x, p.y, p.z, yaw};
      } else {
         return null;
      }
   }

   static float striking(int i, float t) {
      float s = 0.0F;

      for (int k = 1; k < HITS.length; k++) {
         if (k % 4 == i) {
            s = Math.max(s, Keys.window(t, HITS[k] - 1.5F, HITS[k] + 3.0F, 1.0F));
         }
      }

      return Math.max(s, Keys.window(t, 94.0F, 100.0F, 1.5F));
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      Vector3f p = at(1.2F, 0.0F, 0.0F);
      float leap = smooth(phase(t, 90.0F, 96.0F));
      float land = smooth(phase(t, 98.0F, 105.0F));
      if (leap > 0.0F) {
         Vector3f meet = at(0.72F, 0.0F, 2.6F);
         p = new Vector3f(p).lerp(meet, leap);
         p.y = p.y + 0.5F * Mth.sin((float) Math.PI * leap);
         p.lerp(at(1.1F, 0.0F, 0.0F), land);
      }

      s.actorPoint.set(p);
      s.actorYaw = 180.0F;
      s.lean = 0.2F * Keys.window(t, 8.0F, 44.0F, 6.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < HITS[0]) {
         prone(v, t, victimYaw, s.side);
      } else {
         float tumble = 9.0F * (Math.min(t, 96.0F) - HITS[0]);
         float pitch = -90.0F - tumble;
         float fall = ease(t, 97.0F, 103.0F);
         pitch = Mth.rotLerp(fall, pitch, -90.0F);
         float yaw = 90.0F * s.side + 30.0F * Mth.sin(t * 0.13F) * (1.0F - fall);
         v.turn(yaw, pitch, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, height(t)));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float cross = Keys.window(t, 6.0F, 46.0F, 4.0F);
      pose(m.rightArm, cross, -1.15F, 0.0F, -0.75F);
      pose(m.leftArm, cross, -1.0F, 0.0F, 0.8F);
      float guard = Keys.window(t, 46.0F, 91.0F, 3.0F);
      pose(m.rightArm, guard, -1.2F, 0.0F, 0.3F);
      pose(m.leftArm, guard, -1.4F, 0.0F, -0.2F);
      this.strike(m, 4, t, Keys.window(t, 90.0F, 104.0F, 2.0F));
      look(m.head, s.actor.model(s.victim.axis(1.0556251F)), 1.0F, 1.2F);
   }

   void strike(HumanoidModel<?> m, int i, float t, float k) {
      switch (i % 5) {
         case 0:
            pose(m.rightLeg, k, -1.5F, 0.0F, 0.1F);
            pose(m.leftLeg, k, 0.4F, 0.0F, 0.0F);
            pose(m.rightArm, k, 0.5F, 0.0F, 0.3F);
            pose(m.leftArm, k, -1.2F, 0.0F, -0.4F);
            break;
         case 1:
            pose(m.rightArm, k, -1.65F, 0.0F, 0.0F);
            pose(m.leftArm, k, 0.4F, 0.0F, -0.2F);
            pose(m.rightLeg, k, -0.6F, 0.0F, 0.0F);
            pose(m.leftLeg, k, 0.5F, 0.0F, 0.0F);
            break;
         case 2:
            pose(m.leftLeg, k, -1.5F, 0.0F, -0.1F);
            pose(m.rightLeg, k, 0.4F, 0.0F, 0.0F);
            pose(m.leftArm, k, 0.5F, 0.0F, -0.3F);
            pose(m.rightArm, k, -1.2F, 0.0F, 0.4F);
            break;
         case 3:
            pose(m.rightArm, k, -2.6F, 0.0F, 0.2F);
            pose(m.leftArm, k, -2.6F, 0.0F, -0.2F);
            pose(m.rightLeg, k, -0.3F, 0.0F, 0.0F);
            pose(m.leftLeg, k, 0.6F, 0.0F, 0.0F);
            break;
         default:
            pose(m.rightArm, k, -1.6F, 0.0F, 0.1F);
            pose(m.leftArm, k, -1.6F, 0.0F, -0.1F);
            pose(m.rightLeg, k, -0.9F, 0.0F, 0.0F);
            pose(m.leftLeg, k, 0.3F, 0.0F, 0.0F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= HITS[0] && t < 102.0F) {
         float k = ease(t, HITS[0], HITS[0] + 4.0F);
         flail(m, age * 0.6F, 0.8F * k);
         float jolt = 0.0F;

         for (float h : HITS) {
            jolt = Math.max(jolt, Keys.window(t, h, h + 3.0F, 1.0F));
         }

         m.head.xRot = Mth.lerp(k, m.head.xRot, 0.4F - 1.0F * jolt);
         m.rightArm.zRot += 0.5F * jolt;
         m.leftArm.zRot -= 0.5F * jolt;
      }

      if (t >= 102.0F) {
         float k = ease(t, 102.0F, 106.0F);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, -0.9F);
         m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, 1.1F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < SPLIT[0]) {
         float k = ease(t, 0.0F, SPLIT[0]);
         return new CoupDeGrace.Shot(at(3.4F, -2.4F + 0.4F * k, 1.7F), at(0.5F, 0.0F, 0.6F), 0, true, 3.0F, 56.0F);
      }

      if (t < HITS[0]) {
         float k = ease(t, SPLIT[0], HITS[0]);
         return new CoupDeGrace.Shot(orbit(-25.0F - 55.0F * k, 4.6F, 2.5F), at(0.3F, 0.0F, 0.6F), 1, true, 3.0F, 60.0F);
      }

      if (t < 90.0F) {
         float a = -80.0F - (t - HITS[0]) * 2.2F;
         Vector3f pos = orbit(a, 5.6F, 1.6F + 0.7F * body.y);
         return new CoupDeGrace.Shot(pos, new Vector3f(body).lerp(at(0.0F, 0.0F, 1.0F), 0.25F), 2, false, 6.0F, 60.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(-4.2F, 0.4F, 0.9F), at(0.1F, 0.0F, 2.8999999F), 3, true, 3.0F, 64.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      Vector3f pos = at(-4.2F, 0.4F, 0.9F).lerp(at(-1.4F, -3.4F, 1.9F), k);
      return new CoupDeGrace.Shot(pos, new Vector3f(body).lerp(at(0.5F, 0.0F, 0.5F), 0.5F), 3, false, 2.5F, 60.0F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float shadow = ease(t, 2.0F, 12.0F) * (1.0F - ease(t, 44.0F, 52.0F));
      if (shadow > 0.0F) {
         CoupDeGrace.Body sb = new CoupDeGrace.Body();
         float len = 1.0F + 0.7F * ease(t, 4.0F, 14.0F);
         sb.turn(180.0F, -90.0F, 0.0F, 0.0F).anchor(1.0556251F, at(1.2F - 1.0556251F * len, 0.0F, -0.1F));
         PlayerModel<?> sm = model(2, ex.actorSlim());
         stand(sm);
         sm.rightArm.zRot = 0.25F + 0.1F * Mth.sin(age * 0.2F);
         sm.leftArm.zRot = -0.25F - 0.1F * Mth.sin(age * 0.2F);
         d.model(sm, FxTex.UMBRA, sb, 1.0F, len, 1.0F, 1.0F, 1.0F, 0.95F * shadow, Draw.Blend.CLEAR);
         d.decal(FxTex.SCORCH, at(0.6F, 0.0F, 0.0F), 2.6F * shadow, 0.0F, 0.3F, 0.15F, 0.6F, 0.8F * shadow, Draw.Blend.SOFT);
      }

      float ring = ease(t, SPLIT[0], 44.0F) * (1.0F - ease(t, 96.0F, 106.0F));
      if (ring > 0.0F) {
         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 4.2F * ring, age * 0.01F, 0.3F, 0.15F, 0.6F, 0.85F * ring, Draw.Blend.SOFT);
         d.decal(FxTex.SIGIL[2], at(0.0F, 0.0F, 0.0F), 3.4F * ring, -age * 0.02F, r(8011007), g(8011007), b(8011007), 0.45F * ring, Draw.Blend.GLOW);
      }

      PlayerModel<?> m = model(2, ex.actorSlim());

      for (int i = 0; i < 4; i++) {
         float[] c = clone(i, t);
         if (c != null) {
            float born = ease(t, SPLIT[i], SPLIT[i] + 2.0F);
            float gone = 1.0F - ease(t, 97.0F, 102.0F);
            float alpha = born * gone;

            for (int j = 3; j >= 1; j--) {
               float[] past = clone(i, t - 0.9F * j);
               if (past != null && Math.abs(past[0] - c[0]) + Math.abs(past[1] - c[1]) + Math.abs(past[2] - c[2]) > 0.2F) {
                  CoupDeGrace.Body pb = new CoupDeGrace.Body();
                  pb.turn(past[3], 0.0F, 0.0F, 0.0F).feet(new Vector3f(past[0], past[1], past[2]));
                  this.poseClone(m, i, s, t - 0.9F * j, age - 0.9F * j);
                  d.model(m, FxTex.UMBRA, pb, 1.0F, 1.0F, 1.0F, 1.0F, 0.35F * alpha / j, Draw.Blend.SOFT);
               }
            }

            CoupDeGrace.Body b = new CoupDeGrace.Body();
            b.turn(c[3], 0.0F, 0.0F, 0.0F).feet(new Vector3f(c[0], c[1], c[2]));
            this.poseClone(m, i, s, t, age);
            d.model(m, FxTex.UMBRA, b, 1.0F, 1.0F, 1.0F, 1.0F, 0.96F * alpha, Draw.Blend.CLEAR);
            d.model(m, FxTex.HOLOGRAM, b, 1.06F, r(8011007) * 0.4F, g(8011007) * 0.4F, b(8011007) * 0.4F, 0.05F * alpha, Draw.Blend.GLOW);

            for (float sg = -1.0F; sg <= 1.0F; sg += 2.0F) {
               Vector3f eye = b.scene(sg * 2.0F, -4.5F, -4.3F);
               d.glow(eye, 0.16F, 1.0F, 0.1F, 0.15F, alpha);
               d.sprite(Sprite.STAR4, eye, 0.14F, 0.0F, 1.0F, 0.3F, 0.3F, alpha, Draw.Blend.GLOW);
            }

            for (int k = 0; k < 3; k++) {
               float w = (age * 0.05F + k / 3.0F + i * 0.29F) % 1.0F;
               Vector3f wisp = b.axis(0.3F + 1.7F * w).add(0.25F * Mth.sin(i + k * 2.1F), 0.0F, 0.25F * Mth.cos(i + k * 2.1F));
               d.sprite(Sprite.SMOKE_DARK, wisp, 0.5F + 0.5F * w, age * 0.03F + k, 0.08F, 0.04F, 0.14F, 0.55F * (1.0F - w) * alpha, Draw.Blend.SOFT);
            }

            if (c[1] < 0.0F) {
               d.decal(FxTex.SCORCH, new Vector3f(c[0], 0.01F, c[2]), 1.3F, age * 0.05F, 0.2F, 0.1F, 0.45F, 0.9F, Draw.Blend.SOFT);
            }

            Vector3f before = this.clonePoint(i, t - 1.5F);
            if (before != null && before.distance(new Vector3f(c[0], c[1], c[2])) > 0.12F) {
               d.streak(
                  new Vector3f(before).add(0.0F, 1.0F, 0.0F), new Vector3f(c[0], c[1] + 1.0F, c[2]), 0.7F, r(8011007), g(8011007), b(8011007), 0.3F * gone
               );
            }
         }
      }

      int k = hit(t);
      if (k >= 1 && t < 96.0F) {
         float live = t - HITS[k];
         if (live < 4.0F) {
            Vector3f body = s.victim.axis(1.0556251F);
            float a = 1.0F - live / 4.0F;
            d.sprite(Sprite.CRESCENT, body, 1.6F + 0.8F * live, k * 1.3F, 1.0F, 0.9F, 1.0F, a, Draw.Blend.GLOW);
            d.sprite(Sprite.RING, body, 0.6F + 0.9F * live, 0.0F, r(8011007), g(8011007), b(8011007), a, Draw.Blend.GLOW);
         }
      }

      if (t >= 96.0F && t < 110.0F) {
         float q = (t - 96.0F) / 14.0F;
         Vector3f c = at(0.0F, 0.0F, 3.6F);
         d.sprite(Sprite.SMOKE_DARK, c, 2.0F + 6.0F * out(q, 2.0F), age * 0.05F, 0.08F, 0.03F, 0.12F, 0.9F * (1.0F - q), Draw.Blend.SOFT);

         for (int i = 0; i < 6; i++) {
            d.sprite(Sprite.CRESCENT, c, 2.5F + 3.0F * q, i * 1.05F, 1.0F, 0.85F, 1.0F, 1.0F - q, Draw.Blend.GLOW);
         }

         d.glow(c, 4.0F * (1.0F - q), r(8011007), g(8011007), b(8011007), 1.0F - q);
      }

      float mark = ease(t, 103.0F, 105.0F);
      if (mark > 0.0F) {
         float q = phase(t, 104.0F, 116.0F);
         d.decal(FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 6.0F * out(q, 2.0F), 0.0F, r(8011007), g(8011007), b(8011007), 0.8F * (1.0F - q), Draw.Blend.GLOW);
         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 2.6F, 0.3F, 0.5F, 0.3F, 1.0F, 0.8F * mark * (1.0F - ease(t, 116.0F, 148.0F)), Draw.Blend.SOFT);
      }
   }

   Vector3f clonePoint(int i, float t) {
      float[] c = clone(i, t);
      return c == null ? null : new Vector3f(c[0], c[1], c[2]);
   }

   void poseClone(HumanoidModel<?> m, int i, Spectacle.Frame s, float t, float age) {
      stand(m);
      bend(m, 0.3F);
      float sway = Mth.sin(age * 0.3F + i * 1.6F);
      m.rightArm.xRot = -0.9F + 0.15F * sway;
      m.leftArm.xRot = -1.1F - 0.15F * sway;
      m.rightArm.zRot = 0.35F;
      m.leftArm.zRot = -0.35F;
      m.rightLeg.xRot = -0.35F;
      m.leftLeg.xRot = 0.3F;
      m.rightLeg.zRot = 0.15F;
      m.leftLeg.zRot = -0.15F;
      this.strike(m, i, t, striking(i, t));
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      Vector3f body = s.victim.axis(1.0556251F);

      for (int i = 0; i < 4; i++) {
         if (t == (int)SPLIT[i]) {
            fx.sound(Sfx.CLONE_SPLIT, s.actor.axis(1.0F), 1.0F, 0.9F + 0.08F * i);
            fx.burst(Sprite.SMOKE_DARK, s.actor.axis(1.0F), 14, 0.08F, 0.6F, 26, 1706542, true);
            fx.sparks(s.actor.axis(1.0F), 10, 0.2F, 8011007);
         }

         if (t == (int)SPLIT[i] + 5) {
            fx.ring(Sprite.SMOKE_DARK, orbit(POST[i], 1.45F, 0.05F), 10, 0.05F, 0.5F, 24, 1706542, true);
         }
      }

      if (t == (int)HITS[0]) {
         fx.sound(Sfx.WHOOSH_HEAVY, body, 0.8F, 1.0F);
         fx.ring(Sprite.SMOKE_DARK, at(0.0F, 0.0F, 0.05F), 14, 0.08F, 0.5F, 20, 1706542, true);
      }

      for (int k = 1; k < HITS.length; k++) {
         if (t == (int)HITS[k] - 2) {
            fx.sound(Sfx.DASH, orbit(POST[k % 4], 1.0F, HIGH[k]), 0.6F, 1.1F + 0.05F * k);
         }

         if (t == (int)HITS[k]) {
            fx.sound(Sfx.IMPACT, body, 1.0F, 0.85F + 0.05F * k);
            fx.sound(Sfx.WHOOSH, body, 0.6F, 1.2F);
            if (k % 2 == 0) {
               fx.sound(Sfx.CRIT, body, 0.5F, 1.2F);
            }

            fx.sparks(body, 16, 0.4F, 14205183);
            fx.burst(Sprite.SMOKE_DARK, body, 6, 0.1F, 0.5F, 18, 1706542, true);
            fx.shake(0.2F, 0.1F);
         }
      }

      if (t == 90) {
         fx.sound(Sfx.WARP, body, 1.0F, 0.8F);
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.0F), 0.9F, 0.9F);
      }

      if (t == 96.0F) {
         Vector3f c = at(0.0F, 0.0F, 3.6F);
         fx.sound(Sfx.IMPACT_HEAVY, c, 1.0F, 0.9F);
         fx.sound(Sfx.SHOCKWAVE, c, 1.0F, 1.0F);
         fx.sound(Sfx.CLONE_SPLIT, c, 1.0F, 0.6F);
         fx.flash(c, 0.6F);
         fx.shake(0.9F, 0.8F);
         fx.burst(Sprite.SMOKE_DARK, c, 40, 0.35F, 1.0F, 40, 1706542, true);
         fx.sparks(c, 50, 0.8F, 15259903);
         fx.burst(Sprite.EMBER, c, 30, 0.4F, 0.12F, 30, 8011007, false);
      }

      if (t == 104) {
         fx.sound(Sfx.SLAM, at(0.0F, 0.0F, 0.2F), 1.0F, 1.0F);
         fx.sound(Sfx.BODY_FALL, at(0.0F, 0.0F, 0.2F));
         fx.ring(Sprite.DUST, at(0.0F, 0.0F, 0.05F), 20, 0.12F, 0.5F, 24, 5259360, true);
         fx.shake(0.5F, 0.3F);
      }

      if (t == 99) {
         for (int i = 0; i < 4; i++) {
            Vector3f p = orbit(POST[i], 0.72F, 3.3999999F);
            fx.burst(Sprite.SMOKE_DARK, p, 10, 0.06F, 0.6F, 30, 1706542, true);
         }
      }
   }
}
