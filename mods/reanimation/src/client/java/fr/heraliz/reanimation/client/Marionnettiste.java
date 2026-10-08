package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class Marionnettiste extends Finisher {
   static final Marionnettiste INSTANCE = new Marionnettiste();
   static final float RISE = 4.0F;
   static final float STRINGS = 10.0F;
   static final float UP = 16.0F;
   static final float DANCE = 32.0F;
   static final float CROSS = 88.0F;
   static final float CUT = 95.0F;
   static final float HIGH = 1.9F;
   static final int GOLD = 16769162;
   static final int WHITE = 16774880;
   static final Puppet.Move[] MOVES = new Puppet.Move[]{
      Puppet.Move.JACK, Puppet.Move.SLAP, Puppet.Move.BOW, Puppet.Move.PUNCH, Puppet.Move.TWIRL, Puppet.Move.SLAP_BOTH
   };
   private static final HumanoidModel<?> PROBE = Script.probe();
   private static final HumanoidModel<?> ACTOR = Script.probe();

   private Marionnettiste() {
      super(ExecutionType.MARIONNETTISTE);
   }

   @Override
   float endFwd() {
      return 1.9F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.6F;
   }

   static int move(float t) {
      if (!(t < 32.0F) && !(t >= 88.0F)) {
         return t < 80.0F ? (int)((t - 32.0F) / 8.0F) : (t < 84.0F ? 6 : 7);
      } else {
         return -1;
      }
   }

   static float jerk(float t) {
      int m = move(t);
      if (m < 0) {
         return 0.0F;
      }

      float start = m < 6 ? 32.0F + m * 8.0F : (m == 6 ? 80.0F : 84.0F);
      return smooth(phase(t, start, start + 2.0F));
   }

   static float hang(float t) {
      float lift = ease(t, 16.0F, 28.0F) * 0.05F;
      if (move(t) == 6) {
         lift += 0.6F * Mth.sin((float) Math.PI * phase(t, 80.0F, 84.0F));
      }

      return lift + 0.85F * ease(t, 88.0F, 94.0F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float up = ease(t, 4.0F, 18.0F) * (1.0F - ease(t, 100.0F, 112.0F));
      s.actorPoint
         .set(at(Mth.lerp(up, 1.6F, 1.95F) + 0.3F * ease(t, 100.0F, 112.0F) * (1.0F - up), 0.0F, 1.9F * up + 0.06F * Mth.sin(t * 0.12F) * up));
      s.actorYaw = 180.0F;
      s.lean = 0.35F * up;
      CoupDeGrace.Body v = s.victim;
      if (t < 16.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 95.0F) {
         float k = ease(t, 16.0F, 28.0F);
         float yaw = move(t) == 4 ? 360.0F * smooth(phase(t, 64.0F, 69.0F)) : 0.0F;
         float bow = move(t) == 2 ? -45.0F * jerk(t) : 0.0F;
         Vector3f feet = at(0.0F, 0.0F, hang(t));
         float sway = 3.0F * Mth.sin(t * 0.3F);
         rise(v, k, s.side, yaw, at(0.0F, 0.0F, 1.0556251F + hang(t)));
         if (k >= 1.0F) {
            v.turn(yaw, bow + sway, 2.0F * Mth.sin(t * 0.21F), 0.0F).feet(feet);
         }
      } else {
         float drop = in(phase(t, 95.0F, 98.0F), 2.0F);
         float y = Mth.lerp(drop, hang(95.0F), 0.0F);
         float fold = in(phase(t, 98.0F, 104.0F), 1.6F);
         v.turn(0.0F, 90.0F * fold, 0.0F, 0.0F).feet(at(0.0F, 0.0F, y + 0.15F * fold));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float up = ease(t, 4.0F, 18.0F) * (1.0F - ease(t, 100.0F, 112.0F));
      m.rightLeg.xRot = 0.15F * up;
      m.leftLeg.xRot = -0.25F * up;
      float work = ease(t, 6.0F, 10.0F) * (1.0F - ease(t, 95.0F, 97.0F));
      float j = jerk(t);
      int mv = move(t);
      float rx = -1.3F;
      float lx = -1.3F;
      if (mv == 0 || mv == 7) {
         rx -= 0.4F * j;
         lx -= 0.4F * j;
      } else if (mv == 1) {
         rx += 0.3F * j;
         lx += 0.3F * j;
      } else if (mv == 2) {
         rx -= 0.5F * j;
      } else if (mv == 4) {
         lx -= 0.6F * j;
      } else if (mv == 5) {
         float step = Mth.sin(t * 0.8F);
         rx -= 0.3F * step;
         lx += 0.3F * step;
      } else if (mv == 6) {
         rx -= 0.6F * j;
         lx -= 0.6F * j;
      }

      float cross = ease(t, 88.0F, 94.0F);
      pose(m.rightArm, work, rx - 0.4F * cross, 0.0F, 0.35F + 0.4F * cross);
      pose(m.leftArm, work, lx - 0.4F * cross, 0.0F, -0.35F - 0.4F * cross);
      m.rightArm.xRot = m.rightArm.xRot + 0.03F * Mth.sin(age * 1.7F) * work;
      m.leftArm.xRot = m.leftArm.xRot + 0.03F * Mth.sin(age * 1.9F) * work;
      float cut = Keys.window(t, 94.0F, 103.0F, 1.5F);
      float slash = ease(t, 94.0F, 96.0F);
      pose(m.leftArm, cut, -1.5F, Mth.lerp(slash, -0.7F, 0.9F), 0.0F);
      look(m.head, s.actor.model(s.victim.axis(1.2F)), 0.8F, 1.2F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 22.0F) {
         lyingPose(m);
      }

      if (t >= 16.0F && t < 95.0F) {
         limp(m, age, ease(t, 16.0F, 26.0F));
         int mv = move(t);
         float j = jerk(t);
         if (mv >= 0 && mv < MOVES.length) {
            float start = 32.0F + mv * 8.0F;
            Puppet.pose(m, MOVES[mv], j, phase(t, start, start + 8.0F), age);
         } else if (mv == 6) {
            spread(m, j);
            pose(m.head, j, -0.4F, 0.0F, 0.0F);
         } else if (mv == 7) {
            flail(m, age * 1.6F, j);
         }

         float cross = ease(t, 88.0F, 93.0F);
         spread(m, cross);
         pose(m.rightLeg, cross, 0.0F, 0.0F, 0.05F);
         pose(m.leftLeg, cross, 0.0F, 0.0F, -0.05F);
         pose(m.head, cross, 0.7F, 0.0F, 0.0F);
      } else if (t >= 95.0F) {
         float k = ease(t, 95.0F, 98.0F);
         limp(m, age, 1.0F);
         float flat = ease(t, 101.0F, 105.0F);
         pose(m.rightArm, flat, -2.9F, 0.0F, -0.4F);
         pose(m.leftArm, flat, -2.7F, 0.0F, 0.5F);
         pose(m.head, flat, -0.2F, 0.6F, 0.0F);
         m.rightArm.zRot += 0.4F * (1.0F - k);
         m.leftArm.zRot -= 0.4F * (1.0F - k);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      Vector3f body = s.victim.axis(1.1F);
      if (t < 32.0F) {
         return new CoupDeGrace.Shot(at(0.8F, -4.6F, 1.9F), at(0.9F, 0.0F, 1.8F), 0, true, 3.0F, 62.0F);
      }

      if (t < 64.0F) {
         float k = ease(t, 32.0F, 64.0F);
         Vector3f pos = at(-3.2F, -2.3F + 0.5F * k, 0.7F);
         return new CoupDeGrace.Shot(pos, new Vector3f(body).lerp(s.actor.axis(1.4F), 0.5F), 1, true, 3.0F, 60.0F);
      }

      if (t < 88.0F) {
         float k = ease(t, 64.0F, 88.0F);
         return new CoupDeGrace.Shot(orbit(100.0F + 50.0F * k, 3.8F, 1.3F), new Vector3f(body).lerp(at(0.7F, 0.0F, 1.6F), 0.3F), 2, true, 3.0F, 58.0F);
      }

      if (t < 101.0F) {
         return new CoupDeGrace.Shot(at(-2.7F, -2.2F, 1.8F), new Vector3f(body).lerp(s.actor.axis(1.6F), 0.35F), 3, true, 3.0F, 58.0F);
      }

      float k = ease(t, 101.0F, 128.0F);
      return new CoupDeGrace.Shot(at(-2.7F, -2.2F, 1.8F).lerp(at(0.6F, -3.8F, 2.2F), k), at(0.2F, 0.0F, 0.5F), 4, false, 2.5F, 58.0F);
   }

   static float[] tilt(float t, float age) {
      int mv = move(t);
      float j = jerk(t);
      float side = 0.08F * Mth.sin(age * 0.4F);
      float fwd = 0.0F;
      if (mv == 1) {
         side -= 0.45F * j;
      } else if (mv == 3) {
         side += 0.45F * j;
      } else if (mv == 2) {
         fwd += 0.5F * j;
      } else if (mv == 5) {
         side += 0.35F * Mth.sin(age * 2.2F) * j;
      } else if (mv == 0 || mv == 6) {
         fwd -= 0.25F * j;
      }

      return new float[]{side, fwd};
   }

   List<Vector3f[]> strings(Spectacle.Frame s, float age, Draw d, float alpha) {
      List<Vector3f[]> out = new ArrayList<>();
      this.poseActor(ACTOR, s, age);
      Vector3f hand = hand(s.actor, ACTOR.rightArm);
      float[] tilt = tilt(s.t, age);
      Vector3f[] bar = Puppet.bar(d, hand, s.actorYaw, tilt[0], tilt[1], alpha);
      this.poseVictim(PROBE, s, s.t);
      out.add(new Vector3f[]{bar[0], hand(s.victim, PROBE.rightArm)});
      out.add(new Vector3f[]{bar[1], hand(s.victim, PROBE.leftArm)});
      out.add(new Vector3f[]{bar[3], foot(s.victim, PROBE.rightLeg)});
      out.add(new Vector3f[]{bar[3], foot(s.victim, PROBE.leftLeg)});
      out.add(new Vector3f[]{bar[2], s.victim.scene(0.0F, -8.0F, 0.0F)});
      return out;
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float out = ease(t, 10.0F, 14.0F);
      float cut = phase(t, 95.0F, 105.0F);
      float barAlpha = ease(t, 6.0F, 10.0F) * (1.0F - ease(t, 103.0F, 109.0F));
      if (!(barAlpha <= 0.0F)) {
         List<Vector3f[]> strings = this.strings(s, age, d, barAlpha);
         if (!(out <= 0.0F) && !(cut >= 1.0F)) {
            int mv = move(t);
            float pluck = mv >= 0 ? Keys.window(t, t - (t - 32.0F) % 8.0F, t - (t - 32.0F) % 8.0F + 4.0F, 1.0F) : 0.0F;
            float taut = 0.5F + 0.5F * ease(t, 88.0F, 95.0F);
            int i = 0;

            for (Vector3f[] st : strings) {
               Vector3f a = st[0];
               Vector3f b = new Vector3f(a).lerp(st[1], out);
               if (t < 95.0F) {
                  Puppet.string(d, a, b, taut, pluck, 16769162, 1.0F, age, i);
               } else {
                  float fade = 1.0F - cut;
                  Vector3f mid = new Vector3f(a).lerp(b, 0.5F);
                  Puppet.string(d, a, new Vector3f(mid).lerp(a, Math.min(1.0F, cut * 3.0F)).add(0.0F, 0.3F * cut, 0.0F), 0.0F, 1.0F, 16769162, fade, age, i);
                  Puppet.string(d, new Vector3f(mid).lerp(b, Math.min(1.0F, cut * 3.0F)).add(0.0F, -0.4F * cut, 0.0F), b, 0.0F, 1.0F, 16769162, fade, age, i);
               }

               i++;
            }

            if (t >= 94.0F && t < 100.0F) {
               float k = phase(t, 94.0F, 100.0F);
               Vector3f c = new Vector3f(this.actorHand(s, false)).lerp(s.victim.axis(1.6F), 0.5F);
               d.sprite(Sprite.CRESCENT, c, 2.4F + k, 0.3F, r(16774880), g(16774880), b(16774880), 1.0F - k, Draw.Blend.GLOW);
            }
         }
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 4) {
         fx.sound(Sfx.WARP, s.actor.axis(1.0F), 0.6F, 1.3F);
         fx.ring(Sprite.DUST, s.actor.axis(0.05F), 12, 0.06F, 0.35F, 20, 9075302, true);
      }

      if (t == 10) {
         fx.sound(Sfx.STRING_PLUCK, s.actor.axis(2.0F), 1.0F, 1.0F);
         fx.sound(Sfx.SPARKLE, s.victim.axis(1.0556251F), 0.6F, 1.2F);
      }

      if (t == 14) {
         for (Vector3f[] st : this.strings(s, t, null, 0.0F)) {
            fx.sparks(st[1], 6, 0.12F, 16769162);
         }
      }

      if (t == 16) {
         fx.sound(Sfx.STRING_PLUCK, s.victim.axis(1.0556251F), 0.9F, 0.75F);
      }

      if (t >= 32.0F && t < 88.0F) {
         int mv = move(t);
         float start = mv < 6 ? 32.0F + mv * 8.0F : (mv == 6 ? 80.0F : 84.0F);
         if (mv < MOVES.length && Puppet.hurts(MOVES[mv]) && t == (int)(start + 3.6F)) {
            Vector3f face = s.victim.scene(0.0F, -4.0F, -4.0F);
            fx.sound(Sfx.IMPACT, face, 1.0F, MOVES[mv] == Puppet.Move.PUNCH ? 0.8F : 1.3F);
            fx.sound(Sfx.FLESH, face, 0.7F, 1.4F);
            fx.burst(Sprite.STAR, face, 5, 0.1F, 0.15F, 12, 16777215, false);
            fx.burst(Sprite.DROP, face, 3, 0.12F, 0.07F, 18, 9043984, true);
            fx.shake(0.12F, 0.05F);
         }

         if (mv == 5 && (t - (int)start) % 2 == 1) {
            fx.sound(Sfx.IMPACT, s.victim.scene(0.0F, -4.0F, -4.0F), 0.5F, 1.6F);
         }

         if (t == (int)start) {
            float[] pitch = new float[]{1.0F, 0.89F, 1.12F, 1.33F, 1.19F, 1.0F, 1.5F, 1.68F};
            fx.sound(Sfx.STRING_PLUCK, s.victim.axis(1.5F), 0.9F, pitch[mv]);
            fx.burst(Sprite.NOTE, s.victim.axis(1.9F), 3, 0.05F, 0.2F, 24, 16769162, false);
            if (mv == 4) {
               fx.sound(Sfx.WHOOSH, s.victim.axis(1.0F), 0.6F, 1.3F);
            }

            if (mv == 6) {
               fx.sound(Sfx.WHOOSH, s.victim.axis(1.0F), 0.6F, 1.0F);
            }
         }
      }

      if (t == 84) {
         fx.sound(Sfx.REVERSE_SWELL, s.victim.axis(1.0F), 0.6F, 1.2F);
      }

      if (t == 88) {
         fx.sound(Sfx.STRING_PLUCK, s.victim.axis(1.5F), 1.0F, 0.6F);
         fx.sound(Sfx.CHIME, s.victim.axis(1.5F), 0.6F, 0.8F);
      }

      if (t == 95) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(2.0F), 1.0F, 1.4F);
      }

      if (t == 96.0F) {
         fx.sound(Sfx.STRING_SNAP, s.victim.axis(1.5F), 1.0F, 1.0F);
         fx.sound(Sfx.IMPACT, s.victim.axis(1.5F), 0.6F, 1.3F);
         fx.flash(s.victim.axis(1.5F), 0.3F);
         fx.shake(0.5F, 0.3F);

         for (Vector3f[] st : this.strings(this.other(s, 94.5F), t, null, 0.0F)) {
            Vector3f mid = new Vector3f(st[0]).lerp(st[1], 0.5F);
            fx.sparks(mid, 10, 0.3F, 16769162);
            fx.burst(Sprite.STAR, mid, 3, 0.08F, 0.12F, 20, 16774880, false);
         }
      }

      if (t == 98) {
         fx.sound(Sfx.BODY_FALL, at(0.0F, 0.0F, 0.2F), 0.8F, 1.1F);
      }

      if (t == 104) {
         fx.sound(Sfx.BODY_FALL, at(-0.9F, 0.0F, 0.2F), 1.0F, 0.9F);
         fx.sound(Sfx.BONE_CRACK, at(-0.9F, 0.0F, 0.2F), 0.6F, 0.9F);
         fx.ring(Sprite.DUST, at(-0.8F, 0.0F, 0.05F), 14, 0.08F, 0.4F, 22, 9075302, true);
      }

      if (t == 106) {
         fx.sound(Sfx.STRING_PLUCK, s.actor.axis(1.5F), 0.6F, 0.5F);
      }
   }
}
