package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class LamesFantomes extends KnifeFinisher {
   static final float[] CUTS = new float[]{24.0F, 32.0F, 40.0F, 48.0F, 58.0F, 62.0F, 66.0F, 70.0F, 72.0F, 74.0F, 76.0F, 78.0F};
   static final float[] ANGLE = new float[]{0.0F, 120.0F, 240.0F, 60.0F, 180.0F, 300.0F, 30.0F, 150.0F, 270.0F, 90.0F, 330.0F, 210.0F};
   static final float FOCUS = 12.0F;
   static final float LAND = 80.0F;
   static final float FLICK = 83.0F;
   static final float TOSS = 88.0F;
   static final float RADIUS = 1.15F;
   static final int RED = 16722492;
   static final int WHITE = 16773876;

   LamesFantomes(Blade blade) {
      super(ExecutionType.LAMES_FANTOMES, blade);
   }

   @Override
   float spin(float t) {
      return Keys.key(t, 2.0F, 0.0F, 10.0F, (float) (Math.PI * 4), 98.0F, (float) (Math.PI * 4), 106.0F, (float) (Math.PI * 6));
   }

   @Override
   float scale(float t) {
      return Keys.key(t, 0.0F, 1.0F, 6.0F, 1.35F, 118.0F, 1.35F);
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
      return Finisher.orbit(angleDeg, radius, 0.0F);
   }

   static float lift(float t) {
      float up = 0.75F * ease(t, CUTS[0], 56.0F);
      float drop = t < 96.0F ? 0.0F : Math.min(1.0F, (t - 96.0F) / 3.5F);
      float bounce = t > 99.5F ? 0.06F * Mth.sin((float) Math.PI * Mth.clamp((t - 96.0F - 3.5F) / 4.0F, 0.0F, 1.0F)) : 0.0F;
      return up * (1.0F - drop * drop) + bounce;
   }

   @Override
   void victim(Script.Frame f, float t) {
      if (t < CUTS[0]) {
         prone(f, t, 0.0F, new Vector3f());
      } else {
         float hang = Math.min(t, 96.0F);
         float spin = 40.0F * Mth.sin((hang - CUTS[0]) * 0.05F);
         float sway = 6.0F * Mth.sin(hang * 0.21F) * (1.0F - ease(t, 96.0F, 99.0F));
         f.victim.turn(90.0F + spin, -90.0F + sway, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, 0.27F + lift(t)));
      }
   }

   @Override
   void actor(Script.Frame f, float t) {
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
         yaw = k < 1.0F ? yawToward(from, to) : yawToward(pos, new Vector3f());
      } else {
         Vector3f from = around(ANGLE[CUTS.length - 1], 1.15F);
         Vector3f to = at(-1.0F, 0.0F, 0.0F);
         float k = ease(t, 80.0F, 83.0F);
         pos = new Vector3f(from).lerp(to, k);
         yaw = Mth.rotLerp(k, yawToward(from, to), 180.0F);
      }

      float in = CoupDeGrace.smooth(t / 5.0F);
      pos.x = Mth.lerp(in, f.actorStart.x, pos.x);
      pos.z = Mth.lerp(in, f.actorStart.z, pos.z);
      yaw = Mth.rotLerp(in, f.actorYaw, yaw);
      f.lean = c >= 0 && t < 80.0F ? 0.25F * Keys.window(t, CUTS[c], CUTS[c] + 5.0F, 1.5F) : 0.0F;
      f.lean = f.lean + 0.35F * Keys.window(t, 10.0F, CUTS[0] - 2.0F, 3.0F);
      if (t >= 80.0F) {
         f.lean = 0.3F * Keys.window(t, 81.0F, 88.0F, 2.5F);
      }

      f.actor.turn(yaw, 0.0F, 0.0F, 0.0F).feet(pos);
      f.actorFwd = pos.z;
      f.actorSide = -pos.x;
   }

   @Override
   void knife(Script.Frame f, float t) {
      Knife k = f.knife;
      float trail = 0.0F;

      for (float c : CUTS) {
         trail = Math.max(trail, Keys.window(t, c - 2.5F, c + 2.0F, 1.0F));
      }

      k.trail = trail * 1.5F + Keys.window(t, 82.0F, 85.0F, 1.0F) + Keys.window(t, 88.0F, 97.0F, 1.0F);
      k.blood = t < CUTS[0] ? 0.0F : 1.0F - 0.8F * ease(t, 83.0F, 84.0F);
      if (t >= 88.0F && t < 96.0F) {
         k.scene(Knife.flight(this.blade, this.heldAt(f, 88.0F), this.heldAt(f, 96.0F), CoupDeGrace.phase(t, 88.0F, 96.0F), 1.3F, 3.0F));
      }
   }

   @Override
   public void poseActor(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      CoupDeGrace.bend(m, f.lean);
      int c = cut(t);
      if (t < CUTS[0] - 2.0F) {
         float show = Keys.window(t, 1.0F, 13.0F, 3.0F);
         Finisher.pose(m.rightArm, show, -1.95F, 0.0F, 0.35F);
         float guard = ease(t, 11.0F, 16.0F);
         Finisher.pose(m.rightArm, guard, -1.15F, 0.0F, 0.15F);
         Finisher.pose(m.leftArm, guard, -0.7F, 0.0F, -0.3F);
         m.rightLeg.xRot = -0.4F * guard;
         m.leftLeg.xRot = 0.35F * guard;
         m.rightLeg.zRot = 0.12F * guard;
         m.leftLeg.zRot = -0.12F * guard;
         if (show > 0.5F) {
            CoupDeGrace.look(m.head, Rig.point(m.rightArm, this.blade.pivot), 1.0F, 1.0F);
         } else {
            CoupDeGrace.look(m.head, f.actor.model(f.victim.scene(0.0F, 2.0F, 0.0F)), 1.0F, 0.9F);
         }
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
         float flick = ease(t, 83.0F, 85.0F);
         m.rightArm.xRot = Mth.lerp(flick, -1.9F, -0.35F);
         m.rightArm.zRot = Mth.lerp(flick, 0.5F, 0.85F);
         float toss = Keys.window(t, 86.0F, 91.0F, 1.5F);
         Finisher.pose(m.rightArm, toss, -2.7F, 0.0F, -0.2F);
         float catchIt = Keys.window(t, 91.0F, 103.0F, 2.0F);
         Finisher.pose(m.rightArm, catchIt, 0.75F, 0.0F, 0.35F);
         float show = ease(t, 103.0F, 108.0F);
         Finisher.pose(m.rightArm, show, -1.2F, 0.0F, 0.2F);
         m.leftArm.xRot = -0.2F;
         m.leftArm.zRot = -0.25F;
         m.head.xRot = 0.15F - 0.4F * Keys.window(t, 88.0F, 92.0F, 1.5F);
      }
   }

   @Override
   public void poseVictim(HumanoidModel<?> m, CoupDeGrace.State state, float age) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      CoupDeGrace.stand(m);
      if (t < CUTS[0]) {
         proneLimbs(m, f, age, Keys.window(t, 6.0F, 14.0F, 2.5F), Keys.window(t, 14.0F, 22.0F, 2.5F), 0.0F);
      } else {
         float jerk = 0.0F;

         for (float c : CUTS) {
            jerk = Math.max(jerk, Keys.window(t, c, c + 4.0F, 1.0F));
         }

         float dead = CoupDeGrace.smooth(CoupDeGrace.phase(t, 97.0F, 101.0F));
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
   public CoupDeGrace.Shot shot(CoupDeGrace.State state) {
      Script.Frame f = (Script.Frame)state;
      float t = f.t;
      Vector3f body = f.victim.axis(1.0556251F);
      if (t < CUTS[0] - 2.0F) {
         Vector3f[] k = this.knifePoints(f);
         Vector3f look = k != null ? new Vector3f(k[0]).lerp(f.actor.axis(1.5F), 0.35F) : f.actor.axis(1.4F);
         Vector3f pos = new Vector3f(f.actor.pos).add(at(-3.0F, -1.2F, 1.45F));
         return new CoupDeGrace.Shot(pos, look, 0, true, 5.0F, 44.0F);
      } else if (t < 56.0F) {
         float a = 30.0F + (t - 22.0F) * 2.4F;
         Vector3f pos = new Vector3f(around(a, 3.2F)).add(0.0F, 1.3F, 0.0F);
         return new CoupDeGrace.Shot(pos, new Vector3f(body).add(0.0F, 0.2F, 0.0F), 1, false, 6.0F, 55.0F);
      } else if (t < 80.0F) {
         float a = 110.0F + (t - 56.0F) * 5.5F;
         Vector3f pos = new Vector3f(around(a, 2.9F)).add(0.0F, 0.9F + 0.5F * ease(t, 56.0F, 78.0F), 0.0F);
         return new CoupDeGrace.Shot(pos, body, 2, false, 8.0F, 52.0F);
      } else if (t < 100.0F) {
         Vector3f me = f.actor.axis(1.0F);
         return new CoupDeGrace.Shot(at(-3.7F, 0.9F, 1.05F), new Vector3f(me).lerp(body, 0.3F).add(0.0F, 0.25F, 0.0F), 3, true, 3.0F, 50.0F);
      } else {
         float k = ease(t, 100.0F, 128.0F);
         Vector3f pos = at(-3.7F, 0.9F, 1.05F).lerp(at(-3.9F, 2.4F, 3.0F), k);
         return new CoupDeGrace.Shot(pos, at(-0.5F, 0.0F, 0.5F), 4, true, 2.0F, 55.0F);
      }
   }

   @Override
   public Vector3f end(int side) {
      return at(-1.0F, 0.0F, 0.0F);
   }

   @Override
   public Vector3f corpse(int side) {
      return at(0.0F, 0.0F, 0.15F);
   }

   @Override
   public float reach() {
      return 0.95F;
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

   @Override
   void draw(Draw d, Script.Frame f, float age, Choreography.Extras ex) {
      float t = f.t;
      Vector3f body = f.victim.axis(1.0556251F);
      float charged = ease(t, 12.0F, 18.0F) * (1.0F - ease(t, 98.0F, 106.0F));
      Vector3f[] k = this.knifePoints(f);
      if (charged > 0.0F && k != null) {
         float hot = 0.5F + 0.3F * pulse(t, 5.0F);
         d.streak(k[0], k[1], 0.3F * charged, r(16722492), g(16722492), b(16722492), hot * charged);
         d.glow(k[1], 0.35F * charged, 1.0F, 0.85F, 0.85F, charged);
      }

      if (t >= 12.0F && t < CUTS[0] + 2.0F) {
         float eyes = Keys.window(t, 12.0F, CUTS[0] + 2.0F, 3.0F);

         for (float x : new float[]{-2.0F, 2.0F}) {
            d.glow(f.actor.scene(x, -4.5F, -4.3F), 0.25F, 1.0F, 0.15F, 0.2F, eyes);
         }
      }

      int c = cut(t);
      if (c >= 0 && t < 82.0F) {
         float dash = Keys.window(t, CUTS[c] - 2.5F, CUTS[c] + 3.0F, 1.0F);
         this.afterimages(d, f, ex, age, 3, 0.7F, 1.0F, 0.2F, 0.28F, 0.3F * dash);
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

            for (int n = 0; n < arc.size() - 1; n++) {
               float edge = Mth.sin((float) Math.PI * n / (arc.size() - 1));
               d.band(
                  FxTex.PARTICLES,
                  st.u0(),
                  st.v0(),
                  st.u1(),
                  st.v1(),
                  false,
                  arc.get(n),
                  arc.get(n + 1),
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
                  arc.get(n),
                  arc.get(n + 1),
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
         float q = (t - 96.0F) / 12.0F;
         d.decal(
            FxTex.SHOCK,
            at(0.0F, 0.0F, 0.0F),
            1.0F + 7.0F * Spectacle.out(q, 2.0F),
            0.0F,
            r(16722492),
            g(16722492),
            b(16722492),
            0.9F * (1.0F - q),
            Draw.Blend.GLOW
         );
         d.glow(body, 2.5F * (1.0F - q) + 0.5F, 1.0F, 0.85F, 0.85F, 1.0F - q);
      }
   }

   @Override
   void events(KnifeFinisher.Fx fx, int t) {
      Script.Frame f = fx.f;
      Vector3f body = f.victim.axis(1.0556251F);
      if (t == 3 || t == 7) {
         fx.sound(Sfx.WHOOSH, f.actor.axis(1.5F), 0.5F, 1.6F);
      }

      if (t == 10) {
         fx.sound(Sfx.BLADE_DRAW, f.actor.axis(1.5F), 0.8F, 1.2F);
      }

      if (t == 12) {
         fx.sound(Sfx.HEARTBEAT, f.actor.axis(1.4F), 1.0F, 0.8F);
         fx.sound(Sfx.REVERSE_SWELL, f.actor.axis(1.4F), 0.6F, 1.3F);
         fx.burst(Sprite.EMBER, f.actor.axis(1.0F), 12, 0.06F, 0.1F, 20, 16722492, false);
      }

      for (int i = 0; i < CUTS.length; i++) {
         if (t == (int)CUTS[i] - 2) {
            fx.sound(Sfx.DASH, f.actor.axis(1.0F), 0.8F, 1.0F + 0.04F * i);
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
         fx.sound(Sfx.WHOOSH, f.actor.axis(1.0F), 0.6F, 1.3F);
         Vector3f hand = f.actor.scene(-9.0F, 6.0F, -6.0F);
         Vector3f away = new Vector3f(f.actor.scene(-24.0F, 8.0F, -8.0F)).sub(hand).normalize(0.16F);

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

      if (t == 88) {
         fx.sound(Sfx.WHOOSH, f.actor.axis(1.8F), 0.8F, 1.1F);
      }

      if (t == 96.0F) {
         fx.sound(Sfx.SHEATH_CLICK, f.actor.axis(1.0F), 1.0F, 1.2F);
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

      if (t == 100.0F) {
         fx.sound(Sfx.BODY_FALL, at(0.0F, 0.0F, 0.2F));
      }

      if (t > 96.0F && t < 116.0F && t % 2 == 0) {
         Vector3f at = new Vector3f(body).add((Vfx.RANDOM.nextFloat() - 0.5F) * 2.5F, 1.5F + Vfx.RANDOM.nextFloat(), (Vfx.RANDOM.nextFloat() - 0.5F) * 2.5F);
         fx.p(Sprite.PETAL, at).vel(fx.dir(0.0F, -0.04F, 0.0F)).size(0.18F).life(50).color(12587040).soft().roll(0.0F, 0.15F);
      }
   }
}
