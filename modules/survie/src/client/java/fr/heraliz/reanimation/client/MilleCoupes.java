package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class MilleCoupes extends Finisher {
   static final MilleCoupes INSTANCE = new MilleCoupes();
   static final float DRAW = 10.0F;
   static final float[] CUTS = new float[]{24.0F, 32.0F, 40.0F, 48.0F, 58.0F, 62.0F, 66.0F, 70.0F, 72.0F, 74.0F, 76.0F, 78.0F};
   static final float[] ANGLE = new float[]{0.0F, 120.0F, 240.0F, 60.0F, 180.0F, 300.0F, 30.0F, 150.0F, 270.0F, 90.0F, 330.0F, 210.0F};
   static final float LAND = 80.0F;
   static final float FLICK = 83.0F;
   static final float SHEATHE = 87.0F;
   static final float RADIUS = 1.15F;
   static final float BLADE = 0.85F;
   static final int RED = 16722492;
   static final int WHITE = 16773876;
   private final HumanoidModel<LivingEntity> probe = Script.probe();

   private MilleCoupes() {
      super(ExecutionType.MILLE_COUPES);
   }

   @Override
   float endFwd() {
      return -1.0F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 0.95F;
   }

   static int cut(float t) {
      int i = -1;

      for (int k = 0; k < CUTS.length; k++) {
         if (t >= CUTS[k] - 2.0F) {
            i = k;
         }
      }

      return i;
   }

   static Vector3f around(float angleDeg, float radius) {
      float a = angleDeg * (float) (Math.PI / 180.0);
      return at(radius * Mth.cos(a), radius * Mth.sin(a), 0.0F);
   }

   static float lift(float t) {
      float up = 0.75F * ease(t, CUTS[0], 56.0F);
      float drop = t < 96.0F ? 0.0F : Math.min(1.0F, (t - 96.0F) / 3.5F);
      float bounce = t > 99.5F ? 0.06F * Mth.sin((float) Math.PI * Mth.clamp((t - 96.0F - 3.5F) / 4.0F, 0.0F, 1.0F)) : 0.0F;
      return up * (1.0F - drop * drop) + bounce;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      int c = cut(t);
      Vector3f pos;
      float yaw;
      if (c < 0) {
         pos = at(0.95F, 0.0F, 0.0F);
         yaw = 180.0F;
      } else if (t < 80.0F) {
         Vector3f from = c == 0 ? at(0.95F, 0.0F, 0.0F) : around(ANGLE[c - 1], 1.15F);
         Vector3f to = around(ANGLE[c], 1.15F);
         float k = Mth.clamp((t - (CUTS[c] - 2.0F)) / 2.0F, 0.0F, 1.0F);
         pos = new Vector3f(from).lerp(to, k);
         pos.mul(1.0F - 0.55F * Mth.sin((float) Math.PI * k));
         yaw = k < 1.0F ? Script.yawToward(from, to) : Script.yawToward(pos, new Vector3f());
      } else {
         Vector3f from = around(ANGLE[CUTS.length - 1], 1.15F);
         Vector3f to = at(-1.0F, 0.0F, 0.0F);
         float k = ease(t, 80.0F, 83.0F);
         pos = new Vector3f(from).lerp(to, k);
         yaw = Mth.rotLerp(k, Script.yawToward(from, to), 180.0F);
      }

      s.actorPoint.set(pos);
      s.actorYaw = yaw;
      s.lean = c >= 0 && t < 80.0F ? 0.25F * Keys.window(t, CUTS[c], CUTS[c] + 5.0F, 1.5F) : 0.0F;
      if (t >= 80.0F) {
         s.lean = 0.3F * Keys.window(t, 81.0F, 89.0F, 2.5F);
      }

      CoupDeGrace.Body v = s.victim;
      if (t < CUTS[0]) {
         prone(v, t, victimYaw, s.side);
      } else {
         float hang = Math.min(t, 96.0F);
         float spin = 40.0F * Mth.sin((hang - CUTS[0]) * 0.05F);
         float sway = 6.0F * Mth.sin(hang * 0.21F) * (1.0F - ease(t, 96.0F, 99.0F));
         v.turn(90.0F * s.side + spin, -90.0F + sway, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, 0.3F + lift(t)));
      }
   }

   static Vector3f mouth(Spectacle.Frame s) {
      return s.actor.scene(4.2F, 11.0F, -3.0F);
   }

   static Vector3f scabbardEnd(Spectacle.Frame s) {
      return s.actor.scene(5.2F, 16.5F, 12.5F);
   }

   static float home(float t) {
      return (float)Math.pow(phase(t, 87.0F, 96.0F), 1.6F);
   }

   static Vector3f sheathGrip(Spectacle.Frame s, float t) {
      Vector3f mouth = mouth(s);
      Vector3f d = new Vector3f(scabbardEnd(s)).sub(mouth).normalize();
      Vector3f start = new Vector3f(s.actor.scene(-7.0F, 4.0F, -11.0F)).sub(mouth).normalize();
      float k = home(t);
      Vector3f u = new Vector3f(start).lerp(new Vector3f(d).negate(), k).normalize();
      return u.mul(0.1F + 0.85F * (1.0F - k)).add(mouth);
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      int c = cut(t);
      if (t < CUTS[0] - 2.0F) {
         float reach = ease(t, 3.0F, 10.0F);
         float draw = ease(t, 10.0F, 17.0F);
         m.rightArm.xRot = Mth.lerp(draw, -0.6F * reach, -1.25F);
         m.rightArm.zRot = Mth.lerp(draw, 1.05F * reach, 0.15F);
         m.leftArm.xRot = -0.5F * draw;
         m.leftArm.zRot = -0.15F;
         m.rightLeg.xRot = -0.35F * draw;
         m.leftLeg.xRot = 0.3F * draw;
         m.head.yRot = 0.6F * reach * (1.0F - draw);
      } else if (t < 80.0F) {
         float hold = Keys.window(t, CUTS[Math.max(c, 0)] - 2.0F, CUTS[Math.max(c, 0)] + 6.0F, 1.0F);
         int style = Math.max(c, 0) % 4;
         float[][] poses = new float[][]{{-1.6F, 0.9F}, {-0.4F, -0.6F}, {-2.4F, 0.3F}, {-1.1F, 1.3F}};
         m.rightArm.xRot = Mth.lerp(hold, -1.0F, poses[style][0]);
         m.rightArm.zRot = Mth.lerp(hold, 0.2F, poses[style][1]);
         m.leftArm.xRot = -0.4F + 0.3F * hold;
         m.leftArm.zRot = -0.5F * hold;
         m.rightLeg.xRot = -0.5F * hold;
         m.leftLeg.xRot = 0.45F * hold;
         m.head.yRot = 0.3F * (style - 1.5F) * hold;
      } else {
         float land = Keys.window(t, 80.0F, 90.0F, 2.0F);
         m.rightLeg.xRot = -0.55F * land;
         m.leftLeg.xRot = 0.5F * land;
         m.leftLeg.zRot = -0.12F * land;
         float flick = ease(t, 83.0F, 85.0F);
         m.rightArm.xRot = Mth.lerp(flick, -1.9F, -0.35F);
         m.rightArm.zRot = Mth.lerp(flick, 0.5F, 0.85F);
         hold(m.leftArm, s.actor.model(mouth(s)), ease(t, 80.0F, 83.0F));
         float sheath = ease(t, 85.0F, 88.0F);
         hold(m.rightArm, s.actor.model(sheathGrip(s, t)), sheath);
         m.head.xRot = 0.2F * sheath;
         m.head.yRot = 0.25F * sheath;
         float after = ease(t, 99.0F, 108.0F);
         m.rightArm.xRot = Mth.lerp(after, m.rightArm.xRot, 0.0F);
         m.rightArm.zRot = Mth.lerp(after, m.rightArm.zRot, 0.08F);
         m.leftArm.xRot = Mth.lerp(after, m.leftArm.xRot, -0.35F);
         m.leftArm.zRot = Mth.lerp(after, m.leftArm.zRot, -0.1F);
         m.head.xRot = Mth.lerp(after, m.head.xRot, -0.15F);
         m.head.yRot = Mth.lerp(after, m.head.yRot, 0.0F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < CUTS[0]) {
         lyingPose(m);
      } else {
         float jerk = 0.0F;

         for (float c : CUTS) {
            jerk = Math.max(jerk, Keys.window(t, c, c + 4.0F, 1.0F));
         }

         float dead = smooth(phase(t, 97.0F, 101.0F));
         float swing = 0.12F * Mth.sin(age * 0.35F);
         float lift = ease(t, CUTS[0], CUTS[0] + 8.0F);
         m.rightArm.xRot = Mth.lerp(dead, Mth.lerp(lift, -3.05F, -1.35F + swing + 0.7F * jerk), -3.05F);
         m.leftArm.xRot = Mth.lerp(dead, Mth.lerp(lift, -3.05F, -1.35F - swing + 0.6F * jerk), -3.05F);
         m.rightArm.zRot = Mth.lerp(dead, 0.15F + 0.5F * jerk, -0.35F);
         m.leftArm.zRot = Mth.lerp(dead, -0.15F - 0.5F * jerk, 0.35F);
         m.rightLeg.xRot = (-0.3F + 0.35F * jerk) * (1.0F - dead) * lift;
         m.leftLeg.xRot = (-0.2F - 0.25F * jerk) * (1.0F - dead) * lift;
         m.head.xRot = Mth.lerp(dead, 0.7F - 1.1F * jerk, -0.3F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t < CUTS[0] - 2.0F) {
         Vector3f me = new Vector3f(s.actor.axis(1.25F)).add(at(0.0F, -0.25F, 0.0F));
         float k = ease(t, 0.0F, 20.0F);
         Vector3f pos = new Vector3f(s.actor.pos).add(at(-2.6F + 0.5F * k, -1.4F, 1.05F));
         return new CoupDeGrace.Shot(pos, me, 0, true, 4.0F, 50.0F);
      } else if (t < 56.0F) {
         float a = 30.0F + (t - 22.0F) * 2.4F;
         Vector3f pos = new Vector3f(around(a, 3.2F)).add(0.0F, 1.3F, 0.0F);
         return new CoupDeGrace.Shot(pos, new Vector3f(body).add(0.0F, 0.2F, 0.0F), 1, false, 6.0F, 55.0F);
      } else if (t < 80.0F) {
         float a = 110.0F + (t - 56.0F) * 5.5F;
         Vector3f pos = new Vector3f(around(a, 2.4F)).add(0.0F, 0.9F + 0.5F * ease(t, 56.0F, 78.0F), 0.0F);
         return new CoupDeGrace.Shot(pos, body, 2, false, 8.0F, 52.0F);
      } else if (t < 100.0F) {
         Vector3f me = s.actor.axis(1.0F);
         return new CoupDeGrace.Shot(at(-3.7F, 0.9F, 1.05F), new Vector3f(me).lerp(body, 0.3F), 3, true, 3.0F, 46.0F);
      } else {
         float k = ease(t, 100.0F, 128.0F);
         Vector3f pos = at(-3.7F, 0.9F, 1.05F).lerp(at(-3.9F, 2.4F, 3.0F), k);
         return new CoupDeGrace.Shot(pos, at(-0.5F, 0.0F, 0.5F), 4, true, 2.0F, 55.0F);
      }
   }

   static List<Vector3f> crescent(int i, Vector3f centre, float radius, float grow) {
      Quaternionf q = new Quaternionf()
         .rotationY(ANGLE[i] * (float) (Math.PI / 180.0))
         .rotateX((i * 47 % 180 - 90) * (float) (Math.PI / 180.0))
         .rotateZ((i * 73 % 120 - 60) * (float) (Math.PI / 180.0));
      List<Vector3f> pts = new ArrayList<>();
      int n = 14;
      float span = 2.3F * grow;

      for (int k = 0; k <= n; k++) {
         float a = -span / 2.0F + span * k / n;
         pts.add(q.transform(new Vector3f(radius * Mth.cos(a), 0.0F, radius * Mth.sin(a))).add(centre));
      }

      return pts;
   }

   Vector3f[] grip(Spectacle.Frame s, float age) {
      this.poseActor(this.probe, s, age);
      Matrix4f arm = Knife.sceneFromModel(s.actor).mul(Knife.partMatrix(this.probe.rightArm));
      Vector3f hand = arm.transformPosition(new Vector3f(-0.0625F, 0.65625F, 0.0F));
      Vector3f dir = arm.transformDirection(new Vector3f(0.0F, 0.75F, -1.0F)).normalize();
      float t = s.t;
      float sheath = ease(t, 85.0F, 88.0F);
      if (sheath > 0.0F) {
         Vector3f toMouth = new Vector3f(mouth(s)).sub(hand).normalize();
         dir.lerp(toMouth, sheath).normalize();
      }

      return new Vector3f[]{hand, dir};
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.0556251F);
      float rift = Keys.window(t, 2.0F, 20.0F, 4.0F);
      if (rift > 0.0F) {
         Vector3f top = s.actor.scene(-13.0F, -10.0F, -3.0F);
         Vector3f bottom = s.actor.scene(-12.0F, 12.0F, -3.0F);
         d.streak(bottom, top, 0.55F * rift, r(16722492), g(16722492), b(16722492), 0.8F * rift);
         d.streak(bottom, top, 0.12F * rift, 1.0F, 1.0F, 1.0F, rift);
         d.glow(new Vector3f(top).lerp(bottom, 0.5F), 0.9F * rift, r(16722492), g(16722492), b(16722492), 0.5F * rift);
      }

      float saya = ease(t, 10.0F, 16.0F) * (1.0F - ease(t, 102.0F, 110.0F));
      if (saya > 0.0F) {
         Vector3f mouth = mouth(s);
         Vector3f end = scabbardEnd(s);
         d.band(FxTex.KATANA, 0.0F, 0.75F, 1.0F, 1.0F, false, mouth, end, 0.085F, 1.0F, 1.0F, 1.0F, saya, saya < 1.0F ? Draw.Blend.CLEAR : Draw.Blend.SOLID);
         d.band(
            FxTex.KATANA,
            0.0F,
            0.5F,
            0.15F,
            0.75F,
            false,
            mouth,
            new Vector3f(mouth).lerp(end, 0.08F),
            0.1F,
            1.0F,
            1.0F,
            1.0F,
            saya,
            saya < 1.0F ? Draw.Blend.CLEAR : Draw.Blend.SOLID
         );
         if (saya < 1.0F) {
            d.streak(mouth, end, 0.3F, r(16722492), g(16722492), b(16722492), 0.6F * (1.0F - saya));
         }
      }

      float out = ease(t, 10.0F, 14.0F);
      if (out > 0.0F && t < 96.5F) {
         Vector3f[] g = this.grip(s, age);
         Vector3f hand = g[0];
         Vector3f dir = g[1];
         Vector3f pommel = new Vector3f(dir).mul(-0.13F).add(hand);
         Vector3f guard = new Vector3f(dir).mul(0.08F).add(hand);
         Vector3f tip = new Vector3f(dir).mul(0.85F * out).add(guard);
         Vector3f mouth = mouth(s);
         float sheath = ease(t, 85.0F, 88.0F);
         if (sheath > 0.5F && guard.distance(mouth) < 0.85F) {
            tip = mouth;
         }

         d.band(FxTex.KATANA, 0.0F, 0.25F, 1.0F, 0.5F, false, pommel, guard, 0.07F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         d.band(
            FxTex.KATANA,
            0.0F,
            0.5F,
            0.2F,
            0.75F,
            false,
            new Vector3f(dir).mul(-0.015F).add(guard),
            new Vector3f(dir).mul(0.015F).add(guard),
            0.13F,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            Draw.Blend.SOLID
         );
         if (guard.distance(tip) > 0.01F) {
            d.band(FxTex.KATANA, 0.0F, 0.0F, 1.0F, 0.25F, false, guard, tip, 0.06F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
            float hot = 0.45F + 0.3F * pulse(t, 5.0F);
            d.streak(guard, tip, 0.22F, r(16722492), g(16722492), b(16722492), hot);
         }
      }

      int c = cut(t);
      if (c >= 0 && t < 82.0F) {
         float dash = Keys.window(t, CUTS[c] - 2.5F, CUTS[c] + 3.0F, 1.0F);
         this.afterimages(d, s, ex, age, 3, 0.7F, 1.0F, 0.2F, 0.28F, 0.3F * dash, true);
      }

      for (int i = 0; i < CUTS.length && !(t < CUTS[i] - 0.5F); i++) {
         float grow = ease(t, CUTS[i] - 0.5F, CUTS[i] + 1.5F);
         float hum = 0.7F + 0.3F * Mth.sin(age * 0.6F + i) + 0.3F * pulse(t, 4.0F);
         float boom = t < 96.0F ? 0.0F : (t - 96.0F) / 5.0F;
         if (!(boom > 1.0F)) {
            float radius = 0.75F + 0.04F * i + 1.6F * boom;
            List<Vector3f> arc = crescent(i, body, radius, grow);
            float a = boom > 0.0F ? 1.0F - boom : Math.min(1.0F, hum);
            Sprite st = Sprite.STREAK;

            for (int k = 0; k < arc.size() - 1; k++) {
               float edge = Mth.sin((float) Math.PI * k / (arc.size() - 1));
               d.band(
                  FxTex.PARTICLES,
                  st.u0(),
                  st.v0(),
                  st.u1(),
                  st.v1(),
                  false,
                  arc.get(k),
                  arc.get(k + 1),
                  0.22F * edge + 0.2F * boom,
                  r(16722492),
                  g(16722492),
                  b(16722492),
                  0.8F * a * edge,
                  Draw.Blend.GLOW
               );
               d.band(
                  FxTex.PARTICLES,
                  st.u0(),
                  st.v0(),
                  st.u1(),
                  st.v1(),
                  false,
                  arc.get(k),
                  arc.get(k + 1),
                  0.07F * edge,
                  1.0F,
                  1.0F,
                  1.0F,
                  a * edge,
                  Draw.Blend.GLOW
               );
            }
         }
      }

      if (t >= 96.0F && t < 108.0F) {
         float k = (t - 96.0F) / 12.0F;
         d.decal(
            FxTex.SHOCK,
            at(0.0F, 0.0F, 0.0F),
            1.0F + 7.0F * (1.0F - (1.0F - k) * (1.0F - k)),
            0.0F,
            r(16722492),
            g(16722492),
            b(16722492),
            0.9F * (1.0F - k),
            Draw.Blend.GLOW
         );
         d.glow(body, 2.5F * (1.0F - k) + 0.5F, 1.0F, 0.85F, 0.85F, 1.0F - k);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      Vector3f body = s.victim.axis(1.0556251F);
      if (t == 4) {
         fx.sound(Sfx.PORTAL_OPEN, s.actor.axis(1.4F), 0.6F, 1.4F);
      }

      if (t == 10.0F) {
         fx.sound(Sfx.BLADE_DRAW, s.actor.axis(1.2F));
         fx.sparks(s.actor.scene(-12.0F, 0.0F, -3.0F), 14, 0.25F, 16722492);
      }

      for (int i = 0; i < CUTS.length; i++) {
         if (t == (int)CUTS[i] - 2) {
            fx.sound(Sfx.DASH, s.actor.axis(1.0F), 0.8F, 1.0F + 0.04F * i);
         }

         if (t == (int)CUTS[i]) {
            fx.sound(Sfx.BLADE_SLASH, body, 1.0F, 0.9F + 0.05F * i);
            fx.sound(Sfx.FLESH, body, 0.5F, 1.2F);
            fx.sparks(body, 18, 0.35F, 16722492);
            fx.burst(Sprite.DROP, body, 6, 0.18F, 0.12F, 22, 10095124, true);
            fx.shake(0.15F, 0.08F);
         }
      }

      if (t == 83) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.0F), 0.6F, 1.3F);
         Vector3f hand = s.actor.scene(-9.0F, 6.0F, -6.0F);
         Vector3f away = new Vector3f(s.actor.scene(-24.0F, 8.0F, -8.0F)).sub(hand).normalize(0.16F);

         for (int i = 0; i < 10; i++) {
            fx.p(Sprite.DROP, hand)
               .vel(fx.dir(new Vector3f(away).mul(0.6F + 0.6F * Vfx.RANDOM.nextFloat())))
               .gravity(0.04)
               .size(0.1F, 0.06F)
               .life(30)
               .color(9043984)
               .soft();
         }
      }

      if (t == 87) {
         fx.sound(Sfx.BLADE_DRAW, mouth(s), 0.5F, 0.6F);
      }

      if (t == 96) {
         fx.sound(Sfx.SHEATH_CLICK, s.actor.axis(1.0F), 1.0F, 1.0F);
         fx.sound(Sfx.IMPACT_HEAVY, body, 1.0F, 0.9F);
         fx.sound(Sfx.SHOCKWAVE, body, 1.0F, 1.0F);
         fx.sound(Sfx.FLESH, body, 1.0F, 0.8F);
         fx.burst(Sprite.SHARD, body, 40, 0.5F, 0.28F, 30, 16722492, false);
         fx.burst(Sprite.PETAL, body, 30, 0.25F, 0.22F, 50, 11536408, true);
         fx.burst(Sprite.DROP, body, 24, 0.3F, 0.14F, 30, 9043984, true);
         fx.sparks(body, 40, 0.6F, 16773876);
         fx.ring(Sprite.SMOKE, at(0.0F, 0.0F, 0.05F), 24, 0.22F, 0.6F, 26, 3803664, true);
         fx.shake(0.9F, 0.7F);
         fx.flash(body, 0.6F);
      }

      if (t == 100) {
         fx.sound(Sfx.BODY_FALL, at(0.0F, 0.0F, 0.2F));
      }

      if (t == 104) {
         fx.burst(Sprite.EMBER, new Vector3f(mouth(s)).lerp(scabbardEnd(s), 0.5F), 16, 0.06F, 0.1F, 30, 16722492, false);
      }

      if (t > 96.0F && t < 116.0F && t % 2 == 0) {
         Vector3f at = new Vector3f(body).add((Vfx.RANDOM.nextFloat() - 0.5F) * 2.5F, 1.5F + Vfx.RANDOM.nextFloat(), (Vfx.RANDOM.nextFloat() - 0.5F) * 2.5F);
         fx.p(Sprite.PETAL, at).vel(fx.dir(0.0F, -0.04F, 0.0F)).size(0.18F).life(50).color(12587040).soft().roll(0.0F, 0.15F);
      }
   }
}
