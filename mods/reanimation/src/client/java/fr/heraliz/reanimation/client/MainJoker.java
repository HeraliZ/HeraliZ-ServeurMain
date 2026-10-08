package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class MainJoker extends Finisher {
   static final MainJoker INSTANCE = new MainJoker();
   static final float SNAP = 2.0F;
   static final float TORNADO = 6.0F;
   static final float RISE = 16.0F;
   static final float FLY = 22.0F;
   static final float BOARD_IN = 27.0F;
   static final float ACE = 84.0F;
   static final float FLICK = 92.0F;
   static final float BOARD = -0.32F;
   static final float BOARD_W = 2.0F;
   static final float BOARD_H = 3.0F;
   static final float FLIGHT = 3.0F;
   static final int SWIRL = 24;
   static final float[] THROWS = new float[]{36.0F, 44.0F, 52.0F, 60.0F, 66.0F, 70.0F, 73.0F, 76.0F, 79.0F, 82.0F};
   static final int RIDE = 2;
   static final float[][] TARGETS = new float[][]{
      {0.98F, 1.62F},
      {-0.98F, 1.62F},
      {0.0F, 2.32F},
      {0.34F, 0.3F},
      {-0.34F, 0.3F},
      {0.44F, 2.1F},
      {-0.44F, 2.1F},
      {0.64F, 1.05F},
      {-0.64F, 1.05F},
      {0.0F, 0.72F}
   };
   static final float CARD_W = 0.22F;
   static final float CARD_H = 0.33F;
   static final int RED = 14688314;
   static final int GOLD = 16765040;

   private MainJoker() {
      super(ExecutionType.MAIN_JOKER);
   }

   @Override
   float endFwd() {
      return 1.8F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.8F;
   }

   static Vector3f mid(float t) {
      return at(0.0F, 0.0F, 1.2556251F + 0.04F * Mth.sin(t * 0.15F));
   }

   static Vector3f target(int i) {
      return at(-0.29999998F, TARGETS[i][0], TARGETS[i][1]);
   }

   static Vector3f boardCentre() {
      return at(-0.32F, 0.0F, 1.5F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      s.actorPoint.set(at(1.8F, 0.0F, 0.0F));
      s.actorYaw = 180.0F;
      float bow = Keys.window(t, 102.0F, 116.0F, 4.0F);
      float throwing = 0.0F;

      for (float th : THROWS) {
         throwing = Math.max(throwing, Keys.window(t, th - 1.0F, th + 3.0F, 1.0F));
      }

      s.lean = 0.12F * throwing + 0.15F * Keys.window(t, 22.0F, 29.0F, 2.0F) + 0.25F * Keys.window(t, 92.0F, 100.0F, 2.0F) + 0.65F * bow;
      CoupDeGrace.Body v = s.victim;
      if (t < 16.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         rise(v, ease(t, 16.0F, 32.0F), s.side, 0.0F, mid(t));
      } else {
         float u = phase(t, 96.0F, 101.0F);
         float bounce = 0.08F * Mth.sin((float) Math.PI * phase(t, 101.0F, 105.0F));
         Vector3f mid = mid(96.0F).lerp(at(-0.85F, 0.0F, 0.15F + bounce), u * u);
         v.turn(0.0F, 90.0F * smooth(u), 0.0F, 0.0F).anchor(1.0556251F, mid);
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float snap = Keys.window(t, 0.0F, 6.0F, 1.5F);
      pose(m.leftArm, snap, -1.9F + 0.3F * Keys.jolt(t, 2.0F, 3.0F, 1.0F), 0.0F, -0.35F);
      pose(m.rightArm, snap, -0.9F, 0.0F, -0.2F);
      float raise = Keys.window(t, 6.0F, 22.0F, 2.0F);
      float w = age * 0.35F;
      pose(m.rightArm, raise, -2.7F + 0.15F * Mth.sin(w), 0.0F, 0.2F + 0.15F * Mth.cos(w));
      pose(m.leftArm, raise, -0.5F, 0.0F, -0.6F);
      float send = Keys.window(t, 22.0F, 30.0F, 2.0F);
      pose(m.rightArm, send, -1.55F, -0.2F, 0.0F);
      pose(m.leftArm, send, -1.55F, 0.2F, 0.0F);
      float game = Keys.window(t, 32.0F, 85.0F, 2.0F);
      if (game > 0.0F) {
         float out = 0.0F;

         for (float th : THROWS) {
            out = Math.max(out, Keys.window(t, th - 0.5F, th + 2.5F, 1.0F));
         }

         pose(m.rightArm, game, Mth.lerp(out, -2.1F, -1.5F), Mth.lerp(out, 0.6F, 0.0F), Mth.lerp(out, -0.6F, 0.35F));
         pose(m.leftArm, game, -0.95F, 0.0F, 0.35F);
      }

      float ace = Keys.window(t, 84.0F, 93.0F, 2.0F);
      pose(m.rightArm, ace, -2.3F, 0.0F, -0.35F);
      pose(m.leftArm, ace, -0.1F, 0.0F, -0.08F);
      float flick = Keys.window(t, 92.0F, 102.0F, 1.5F);
      pose(m.rightArm, flick, Mth.lerp(ease(t, 92.0F, 95.0F), -2.6F, -1.35F), 0.0F, 0.2F);
      float bow = Keys.window(t, 102.0F, 116.0F, 4.0F);
      pose(m.rightArm, bow, -0.9F, 0.0F, -0.9F);
      pose(m.leftArm, bow, -0.25F, 0.0F, -0.9F);
      pose(m.rightLeg, bow, 0.3F, 0.0F, 0.0F);
      pose(m.leftLeg, bow, -0.25F, 0.0F, 0.0F);
      if (ace > 0.3F) {
         look(m.head, new Vector3f(-8.0F, -8.0F, -12.0F), 1.0F, 1.0F);
      } else if (raise > 0.5F) {
         look(m.head, s.actor.model(s.actor.axis(3.0F).add(0.0F, 0.0F, 0.0F)), 0.9F, 1.0F);
      } else {
         look(m.head, s.actor.model(s.victim.axis(1.4F)), 0.9F, 0.8F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 20.0F) {
         lyingPose(m);
      }

      if (t >= 16.0F && t < 96.0F) {
         spread(m, ease(t, 18.0F, 32.0F));
         float flinch = 0.0F;

         for (float th : THROWS) {
            flinch = Math.max(flinch, Keys.jolt(t, th + 3.0F, 6.0F, 2.2F));
         }

         m.head.xRot = m.head.xRot + -0.4F * Math.abs(flinch);
         m.head.yRot = 0.3F * flinch;
         m.rightArm.zRot += 0.1F * flinch;
         m.leftArm.zRot -= 0.1F * flinch;
      } else if (t >= 96.0F) {
         deadPose(m, 0.4F * s.side);
         float k = 1.0F - ease(t, 99.0F, 103.0F);
         spread(m, k);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      float ride = THROWS[2];
      if (t < 16.0F) {
         float k = ease(t, 0.0F, 16.0F);
         return new CoupDeGrace.Shot(at(1.1F, -3.4F + 0.3F * k, 2.0F + 0.3F * k), s.actor.axis(1.2F + 0.4F * k), 0, true, 3.0F, 54.0F);
      }

      if (t < 34.0F) {
         return new CoupDeGrace.Shot(at(0.9F, -4.7F, 1.7F), at(0.5F, 0.0F, 1.4F), 1, true, 3.0F, 62.0F);
      }

      if (t < ride) {
         return new CoupDeGrace.Shot(at(4.4F, -1.9F, 2.5F), at(0.3F, 0.0F, 1.3F), 2, true, 3.0F, 50.0F);
      }

      if (t < ride + 5.0F) {
         Spectacle.Frame release = this.other(s, ride);
         Vector3f from = this.actorHand(release, true);
         Vector3f to = target(2);
         Vector3f dir = new Vector3f(to).sub(from).normalize();
         Vector3f p = flight(from, to, Math.min(1.0F, phase(t, ride, ride + 3.0F)));
         Vector3f beside = new Vector3f(dir).cross(0.0F, 1.0F, 0.0F).normalize(0.4F);
         Vector3f pos = new Vector3f(p).sub(new Vector3f(dir).mul(0.8F)).add(beside).add(0.0F, 0.3F, 0.0F);
         return new CoupDeGrace.Shot(pos, new Vector3f(p).lerp(to, 0.6F), 3, false, 12.0F, 52.0F);
      }

      if (t < 84.0F) {
         float k = ease(t, ride + 5.0F, 84.0F);
         return new CoupDeGrace.Shot(at(1.7F - 0.3F * k, -2.3F, 1.5F), at(-0.2F, 0.0F, 1.4F), 4, true, 3.0F, 58.0F);
      }

      if (t < 93.0F) {
         Vector3f hand = this.actorHand(s, true);
         return new CoupDeGrace.Shot(new Vector3f(hand).add(at(-1.0F, -0.9F, 0.0F)), new Vector3f(hand).lerp(s.actor.axis(1.9F), 0.35F), 5, true, 4.0F, 50.0F);
      }

      if (t < 96.0F) {
         return new CoupDeGrace.Shot(at(0.9F, -2.8F, 1.6F), this.acePoint(s), 6, false, 8.0F, 52.0F);
      }

      if (t < 101.0F) {
         return new CoupDeGrace.Shot(at(1.6F, -2.0F, 1.7F), at(-0.3F, 0.0F, 1.4F), 7, true, 3.0F, 62.0F);
      }

      float k = ease(t, 101.0F, 128.0F);
      return new CoupDeGrace.Shot(at(1.6F, -2.0F, 1.7F).lerp(at(3.0F, -3.5F, 2.2F), k), at(0.4F, 0.0F, 0.8F), 7, false, 2.5F, 60.0F);
   }

   void card(Draw d, Matrix4f m, boolean ace, float a) {
      Matrix4f front = new Matrix4f(m).translate(0.0F, 0.0F, 0.004F).scale(0.22F, 0.33F, 1.0F);
      Matrix4f back = new Matrix4f(m).translate(0.0F, 0.0F, -0.004F).rotateY((float) Math.PI).scale(0.22F, 0.33F, 1.0F);
      Draw.Blend blend = a < 1.0F ? Draw.Blend.CLEAR : Draw.Blend.SOLID;
      d.plane(FxTex.CARDS, front, ace ? 0.5F : 0.0F, 0.0F, ace ? 1.0F : 0.5F, 1.0F, 1.0F, 1.0F, 1.0F, a, blend);
      d.plane(FxTex.CARDS, back, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F, 1.0F, 1.0F, a, blend);
   }

   static Vector3f flight(Vector3f from, Vector3f to, float k) {
      return new Vector3f(from).lerp(to, k).add(0.0F, 0.2F * Mth.sin((float) Math.PI * k), 0.0F);
   }

   Matrix4f swirl(int i, float t) {
      float born = 6.0F + i * 0.4F;
      if (t < born) {
         return null;
      }

      float u = t - born;
      float a = i * 0.785F + u * 0.3F;
      float grow = Math.min(1.0F, u / 6.0F);
      Vector3f p = new Vector3f(at(1.8F, 0.0F, 0.0F))
         .add(
            (0.45F + 0.75F * grow) * Mth.cos(a),
            0.3F + 2.3F * (i * 0.37F % 1.0F) * grow + 0.1F * Mth.sin(u * 0.3F + i),
            (0.45F + 0.75F * grow) * Mth.sin(a)
         );
      float yaw = -a * (180.0F / (float)Math.PI) + 90.0F;
      float k = smooth(phase(t, 22.0F + 0.15F * i, 22.0F + 0.15F * i + 4.5F));
      if (k > 0.0F) {
         Vector3f slot = at(-0.26F, (i % 4 - 1.5F) * 0.46F, 0.4F + i / 4 * 0.46F);
         p.lerp(slot, k).add(0.0F, 0.6F * Mth.sin((float) Math.PI * k), 0.0F);
         yaw = Mth.rotLerp(k, yaw, 0.0F);
      }

      return new Matrix4f().translate(p).rotateY(yaw * (float) (Math.PI / 180.0)).rotateZ(0.3F * Mth.sin(u * 0.4F + i) * (1.0F - k));
   }

   Vector3f acePoint(Spectacle.Frame s) {
      float t = s.t;
      Vector3f hand = this.actorHand(s, true);
      if (t < 93.0F) {
         return hand.add(0.0F, 0.12F, 0.0F);
      }

      float k = in(phase(t, 93.0F, 96.0F), 1.4F);
      Spectacle.Frame release = this.other(s, 93.0F);
      Vector3f from = this.actorHand(release, true).add(0.0F, 0.12F, 0.0F);
      return from.lerp(s.victim.axis(1.35F).add(0.0F, 0.0F, 0.12F), k);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float gone = 1.0F - ease(t, 96.0F, 97.0F);
      if (t >= 2.0F && t < 8.0F) {
         Vector3f hand = this.actorHand(s, true);
         this.card(d, new Matrix4f().translate(hand).rotateY((float) Math.PI).rotateX(-0.6F), false, 1.0F);
         d.glow(hand, 0.6F * (1.0F - phase(t, 2.0F, 6.0F)), r(16765040), g(16765040), b(16765040), 1.0F);
      }

      float fade = 1.0F - ease(t, 28.0F, 31.0F);
      if (fade > 0.0F) {
         for (int i = 0; i < 24; i++) {
            Matrix4f m = this.swirl(i, t);
            if (m != null) {
               this.card(d, m, i % 3 == 0, fade);
            }
         }
      }

      float board = out(phase(t, 27.0F, 31.0F), 3.0F);
      if (board > 0.0F && t < 96.0F) {
         float pop = 1.0F + 0.08F * Mth.sin((float) Math.PI * phase(t, 27.0F, 32.0F));
         Matrix4f m = new Matrix4f().translate(boardCentre()).scale(2.0F * board * pop, 3.0F * board * pop, 1.0F);
         d.plane(FxTex.JOKER, m, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         d.plane(
            FxTex.CARDS,
            new Matrix4f(m).translate(0.0F, 0.0F, -0.01F).rotateY((float) Math.PI),
            0.0F,
            0.0F,
            0.5F,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            Draw.Blend.SOLID
         );
         float hw = 1.0F * board;
         float hh = 1.5F * board;
         Vector3f c = new Vector3f(boardCentre()).add(at(0.04F, 0.0F, 0.0F));
         Vector3f[] corner = new Vector3f[]{
            new Vector3f(c).add(at(0.0F, -hw, -hh)),
            new Vector3f(c).add(at(0.0F, hw, -hh)),
            new Vector3f(c).add(at(0.0F, hw, hh)),
            new Vector3f(c).add(at(0.0F, -hw, hh))
         };
         float shine = 0.5F + 0.3F * Mth.sin(age * 0.3F) + 0.5F * (1.0F - phase(t, 27.0F, 33.0F));

         for (int k = 0; k < 4; k++) {
            d.streak(corner[k], corner[(k + 1) % 4], 0.08F, r(16765040), g(16765040), b(16765040), shine);
         }

         d.glow(new Vector3f(c).add(at(-0.2F, 0.0F, 0.0F)), 3.5F * board, r(16765040), g(16765040), b(16765040), 0.25F);
      }

      if (t >= 96.0F && t < 112.0F) {
         float q = t - 96.0F;
         float a = 1.0F - phase(t, 104.0F, 112.0F);

         for (int px = 0; px < 4; px++) {
            for (int py = 0; py < 6; py++) {
               float u0 = px / 4.0F;
               float v0 = py / 6.0F;
               Vector3f home = new Vector3f(boardCentre()).add(at(0.0F, (0.5F - (px + 0.5F) / 4.0F) * 2.0F, (0.5F - (py + 0.5F) / 6.0F) * 3.0F));
               Vector3f away = new Vector3f(home).sub(at(0.0F, 0.0F, 1.6F)).normalize();
               away.add(at(-0.8F, 0.0F, 0.0F)).normalize();
               Vector3f p = new Vector3f(home).add(new Vector3f(away).mul(0.35F * q)).add(0.0F, -0.012F * q * q, 0.0F);
               int seed = px * 7 + py * 3;
               Matrix4f m = new Matrix4f()
                  .translate(p)
                  .rotateX(q * 0.2F * (seed % 3 - 1))
                  .rotateY(q * 0.15F * (seed % 5 - 2))
                  .rotateZ(q * 0.1F * (seed % 2 * 2 - 1))
                  .scale(0.5F, 0.5F, 1.0F);
               d.plane(FxTex.JOKER, m, u0, v0, u0 + 0.25F, v0 + 0.16666667F, 1.0F, 1.0F, 1.0F, a, Draw.Blend.CLEAR);
            }
         }
      }

      for (int i = 0; i < THROWS.length && gone > 0.0F; i++) {
         float th = THROWS[i];
         if (t < th) {
            break;
         }

         Vector3f to = target(i);
         float k = phase(t, th, th + 3.0F);
         Matrix4f m;
         if (k < 1.0F) {
            Spectacle.Frame release = this.other(s, th);
            Vector3f from = this.actorHand(release, true);
            Vector3f p = flight(from, to, k);
            m = new Matrix4f().translate(p).rotateX((float) (-Math.PI / 2)).rotateZ(t * 2.2F + i);
            d.streak(flight(from, to, Math.max(0.0F, k - 0.35F)), p, 0.08F, r(16765040), g(16765040), b(16765040), 0.6F);
         } else {
            m = new Matrix4f().translate(new Vector3f(to).add(at(0.09900001F, 0.0F, 0.0F))).rotateY(0.35F * (i % 2 * 2 - 1)).rotateX(-1.0207963F);
            float shine = 1.0F - phase(t, th + 3.0F, th + 3.0F + 4.0F);
            if (shine > 0.0F) {
               d.glow(to, 0.6F, r(16765040), g(16765040), b(16765040), shine);
               d.sprite(Sprite.STAR4, to, 0.6F * shine, 0.0F, 1.0F, 0.95F, 0.8F, shine, Draw.Blend.GLOW);
            }
         }

         this.card(d, m, i % 4 == 2, gone);
      }

      if (t >= 84.0F && t < 96.5F) {
         Vector3f p = this.acePoint(s);
         float spin = t < 92.0F ? Mth.sin(age * 0.4F) * 0.4F : 0.0F;
         Matrix4f m;
         if (t < 93.0F) {
            m = new Matrix4f().translate(p).rotateY((float) Math.PI + spin).rotateZ(0.2F);
            float big = Keys.window(t, 84.0F, 95.0F, 2.0F);
            Matrix4f giant = new Matrix4f()
               .translate(new Vector3f(s.actor.axis(3.2F)).add(at(0.3F, 0.0F, 0.0F)))
               .rotateY((float) Math.PI)
               .scale(0.9F * big, 1.35F * big, 1.0F);
            d.plane(FxTex.CARDS, giant, 0.5F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.85F * big, Draw.Blend.CLEAR);
            d.glow(s.actor.axis(2.9F), 2.6F * big, r(14688314), g(14688314), b(14688314), 0.3F * big);
         } else {
            m = new Matrix4f().translate(p).rotateX((float) (-Math.PI / 2)).rotateZ(t * 3.0F);
            Spectacle.Frame release = this.other(s, 93.0F);
            d.streak(this.actorHand(release, true), p, 0.12F, r(14688314), g(14688314), b(14688314), 0.8F);
         }

         this.card(d, m, true, 1.0F);
         d.glow(p, 0.7F + 0.2F * Mth.sin(age * 0.9F), r(14688314), g(14688314), b(14688314), 0.8F);
      }

      if (t >= 96.0F && t < 108.0F) {
         float k = (t - 96.0F) / 12.0F;
         Vector3f heart = at(0.0F, 0.0F, 1.6F);
         d.glow(heart, 2.5F * (1.0F - k), 1.0F, 0.3F, 0.35F, 1.0F - k);
         d.sprite(Sprite.HEART, heart, 0.5F + 0.9F * k, 0.0F, r(14688314), g(14688314), b(14688314), 1.0F - k, Draw.Blend.GLOW);
         d.plane(
            FxTex.SHOCK,
            new Matrix4f().translate(boardCentre()).scale(1.0F + 7.0F * out(k, 2.0F)),
            0.0F,
            0.0F,
            1.0F,
            1.0F,
            r(16765040),
            g(16765040),
            b(16765040),
            0.9F * (1.0F - k),
            Draw.Blend.GLOW
         );
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 2) {
         fx.sound(Sfx.SPARKLE, s.actor.axis(1.6F), 0.8F, 1.3F);
         fx.sound(Sfx.CARD_FLICK, s.actor.axis(1.6F), 0.6F, 1.6F);
         fx.burst(Sprite.STAR, this.actorHand(s, false), 10, 0.08F, 0.1F, 18, 16765040, false);
      }

      if (t == 6 || t == 10 || t == 14 || t == 18) {
         fx.sound(Sfx.CARD_SHUFFLE, s.actor.axis(1.4F), 0.9F, 0.9F + 0.08F * ((t - 6) / 4));
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.6F), 0.4F, 1.3F);
      }

      if (t == 16) {
         fx.sound(Sfx.WARP, at(0.0F, 0.0F, 1.0F), 0.6F, 1.2F);
      }

      if (t == 22) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.5F), 0.8F, 1.2F);
         fx.sound(Sfx.CARD_SHUFFLE, s.actor.axis(1.5F), 1.0F, 1.2F);
      }

      if (t == 28) {
         fx.sound(Sfx.CARD_THUD, boardCentre(), 1.0F, 0.6F);
         fx.sound(Sfx.CHIME, boardCentre(), 0.8F, 1.0F);
         fx.sound(Sfx.SPARKLE, boardCentre(), 0.6F, 1.0F);
         fx.burst(Sprite.STAR, boardCentre(), 20, 0.2F, 0.12F, 20, 16765040, false);
      }

      for (int i = 0; i < THROWS.length; i++) {
         if (t == (int)THROWS[i]) {
            fx.sound(Sfx.CARD_FLICK, s.actor.axis(1.3F), 1.0F, 0.9F + 0.04F * i);
         }

         if (t == (int)(THROWS[i] + 3.0F)) {
            Vector3f p = target(i);
            fx.sound(Sfx.CARD_THUD, p, 1.0F, 0.9F + 0.04F * i);
            fx.sparks(p, 6, 0.12F, 16765040);
         }
      }

      if (t == 84) {
         fx.sound(Sfx.CHIME, s.actor.axis(1.6F), 0.8F, 0.9F);
         fx.sound(Sfx.SPARKLE, s.actor.axis(2.6F), 0.7F, 0.8F);
         fx.sound(Sfx.REVERSE_SWELL, s.actor.axis(2.6F), 0.6F, 1.1F);
      }

      if (t == 93) {
         fx.sound(Sfx.CARD_FLICK, s.actor.axis(1.5F), 1.0F, 0.7F);
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.5F), 0.8F, 1.3F);
      }

      if (t == 96.0F) {
         Vector3f heart = at(0.0F, 0.0F, 1.6F);
         fx.sound(Sfx.CARD_THUD, heart, 1.0F, 0.7F);
         fx.sound(Sfx.BLAST, heart, 0.9F, 1.1F);
         fx.sound(Sfx.IMPACT_HEAVY, heart, 0.9F, 1.0F);
         fx.sound(Sfx.FLESH, heart, 1.0F, 0.8F);
         fx.sound(Sfx.CARD_SHUFFLE, boardCentre(), 1.0F, 0.6F);
         fx.flash(heart, 0.5F);
         fx.shake(0.7F, 0.6F);

         for (int i = 0; i < THROWS.length; i++) {
            Vector3f p = target(i);

            for (int k = 0; k < 3; k++) {
               fx.p(Sprite.CARD, p)
                  .vel(fx.dir(new Vector3f(p).sub(heart).normalize(0.25F).add((float)Vfx.random(0.08).x, 0.1F, 0.08F)))
                  .gravity(0.012)
                  .drag(0.95)
                  .size(0.36F)
                  .life(60)
                  .color(16777215)
                  .soft()
                  .roll(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 0.5F);
            }
         }

         fx.burst(Sprite.HEART, heart, 12, 0.3F, 0.25F, 26, 14688314, false);
         fx.burst(Sprite.DROP, heart, 18, 0.25F, 0.12F, 30, 9043984, true);
         fx.sparks(heart, 40, 0.6F, 16765040);
      }

      if (t == 101) {
         fx.sound(Sfx.BODY_FALL, at(-0.85F, 0.0F, 0.2F));
      }

      if (t > 100.0F && t < 118.0F && t % 2 == 0) {
         Vector3f p = at(-0.4F + 2.6F * Vfx.RANDOM.nextFloat() - 1.0F, 2.6F * Vfx.RANDOM.nextFloat() - 1.3F, 3.0F + Vfx.RANDOM.nextFloat());
         fx.p(Sprite.CARD, p)
            .vel(fx.dir(0.0F, -0.035F, 0.0F))
            .size(0.3F)
            .life(70)
            .color(t % 4 == 0 ? 16777215 : 15769768)
            .soft()
            .roll(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 0.3F);
      }

      if (t == 104) {
         fx.sound(Sfx.CHIME, s.actor.axis(1.4F), 0.6F, 1.0F);
      }
   }
}
