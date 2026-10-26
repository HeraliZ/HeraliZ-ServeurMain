package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class BonhommeNeige extends Finisher {
   static final BonhommeNeige INSTANCE = new BonhommeNeige();
   static final float ROLL = 2.0F;
   static final float STAND = 6.0F;
   static final float BOTTOM = 16.0F;
   static final float MIDDLE = 32.0F;
   static final float HEAD = 40.0F;
   static final float ARMS = 44.0F;
   static final float CARROT = 48.0F;
   static final float EYES = 56.0F;
   static final float BUTTONS = 64.0F;
   static final float SCARF = 72.0F;
   static final float HAT = 80.0F;
   static final float FLIGHT = 4.0F;
   static final float RUN = 86.0F;
   static final float JUMP = 92.0F;
   static final Vector3f BALL_1 = at(0.0F, 0.0F, 0.42F);
   static final Vector3f BALL_2 = at(0.0F, 0.0F, 1.05F);
   static final Vector3f BALL_3 = at(0.0F, 0.0F, 1.68F);
   static final float R1 = 0.52F;
   static final float R2 = 0.46F;
   static final float R3 = 0.38F;
   static final int WHITE = 16777215;

   private BonhommeNeige() {
      super(ExecutionType.BONHOMME_DE_NEIGE);
   }

   @Override
   float endFwd() {
      return 1.0F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.6F;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      Vector3f p = at(1.6F, 0.0F, 0.0F);
      float pitch = 0.0F;
      if (t < 18.0F) {
         float k = ease(t, 2.0F, 16.0F);
         p = at(Mth.lerp(k, 1.6F, 1.0F), 0.0F, 0.0F);
      } else if (t < 86.0F) {
         p = at(Mth.lerp(ease(t, 18.0F, 24.0F), 1.0F, 1.6F), 0.0F, 0.0F);
         p = new Vector3f(p).lerp(at(3.3F, 0.0F, 0.0F), ease(t, 82.0F, 86.0F));
      } else if (t < 96.0F) {
         float run = in(phase(t, 86.0F, 92.0F), 1.3F);
         p = at(Mth.lerp(run, 3.3F, 1.7F), 0.0F, 0.0F);
         float air = smooth(phase(t, 92.0F, 96.0F));
         p.lerp(at(0.5F, 0.0F, 1.0F), air);
         p.y = p.y + 0.35F * Mth.sin((float) Math.PI * air);
         pitch = 82.0F * smooth(phase(t, 92.0F, 95.5F));
      } else {
         float fall = smooth(phase(t, 96.0F, 101.0F));
         float up = smooth(phase(t, 105.0F, 114.0F));
         Vector3f land = at(0.95F, 0.0F, 0.0F);
         p = at(0.5F, 0.0F, 1.0F).lerp(land, fall);
         p.y = p.y + 0.25F * Mth.sin((float) Math.PI * fall);
         pitch = Mth.lerp(up, Mth.lerp(fall, 82.0F, 90.0F), 0.0F);
         p.y += 0.15F * (1.0F - up) * fall;
      }

      s.actorPoint.set(p);
      s.actorYaw = 180.0F;
      s.actorPitch = pitch;
      s.lean = 0.5F * Keys.window(t, 2.0F, 18.0F, 3.0F) + 0.2F * Keys.window(t, 86.0F, 92.0F, 2.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 6.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         rise(v, ease(t, 6.0F, 16.0F), s.side, 0.0F, at(0.0F, 0.0F, 1.0556251F));
      } else {
         float u = phase(t, 96.0F, 101.0F);
         float bounce = 0.08F * Mth.sin((float) Math.PI * phase(t, 101.0F, 105.0F));
         v.turn(0.0F, 90.0F * smooth(u), 0.0F, 0.0F).feet(at(-0.7F * out(u, 2.0F), 0.0F, 0.15F * smooth(u) + bounce));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float push = Keys.window(t, 2.0F, 18.0F, 3.0F);
      pose(m.rightArm, push, -1.25F, 0.0F, -0.1F);
      pose(m.leftArm, push, -1.25F, 0.0F, 0.1F);
      walk(m, t * 0.5F, push, 1.0F);
      float[] throwsAt = new float[]{32.0F, 40.0F, 48.0F, 56.0F, 64.0F, 72.0F, 80.0F};

      for (int i = 0; i < throwsAt.length; i++) {
         float th = throwsAt[i] - 4.0F;
         float w = Keys.window(t, th - 3.0F, th + 2.0F, 1.5F);
         boolean both = i < 2;
         float swing = Mth.lerp(ease(t, th - 3.0F, th), 0.5F, -1.9F);
         pose(m.rightArm, w, swing, 0.0F, 0.15F);
         if (both) {
            pose(m.leftArm, w, swing, 0.0F, -0.15F);
         }
      }

      float admire = Keys.window(t, 81.0F, 86.0F, 2.0F);
      pose(m.rightArm, admire, -0.3F, 0.0F, -0.7F);
      pose(m.leftArm, admire, -0.3F, 0.0F, 0.7F);
      walk(m, t * 0.45F, Keys.window(t, 82.0F, 86.0F, 2.0F), -1.0F);
      float run = Keys.window(t, 86.0F, 93.0F, 1.0F);
      float w = (t - 86.0F) * 1.0F;
      pose(m.rightLeg, run, 0.9F * Mth.sin(w), 0.0F, 0.0F);
      pose(m.leftLeg, run, -0.9F * Mth.sin(w), 0.0F, 0.0F);
      pose(m.rightArm, run, -0.9F * Mth.sin(w), 0.0F, 0.1F);
      pose(m.leftArm, run, 0.9F * Mth.sin(w), 0.0F, -0.1F);
      float kick = Keys.window(t, 92.0F, 100.0F, 1.5F);
      pose(m.rightLeg, kick, -0.25F, 0.0F, 0.05F);
      pose(m.leftLeg, kick, -0.25F, 0.0F, -0.05F);
      pose(m.rightArm, kick, 0.6F, 0.0F, 0.9F);
      pose(m.leftArm, kick, 0.6F, 0.0F, -0.9F);
      if (t < 92.0F) {
         look(m.head, s.actor.model(at(0.0F, 0.0F, 1.2F)), 0.9F, 1.0F);
      } else {
         m.head.xRot = -0.6F * kick;
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 10.0F) {
         lyingPose(m);
      }

      if (t >= 6.0F && t < 96.0F) {
         limp(m, age, ease(t, 6.0F, 14.0F));
         float packed = ease(t, 14.0F, 32.0F);
         pose(m.rightArm, packed, 0.0F, 0.0F, 0.05F);
         pose(m.leftArm, packed, 0.0F, 0.0F, -0.05F);
         pose(m.head, packed, 0.1F, 0.0F, 0.0F);
         m.head.yRot = m.head.yRot + 0.06F * Mth.sin(age * 4.0F) * packed;
      } else if (t >= 96.0F) {
         deadPose(m, 0.6F * s.side);
         float k = 1.0F - ease(t, 99.0F, 104.0F);
         pose(m.rightArm, k, -2.6F, 0.0F, 0.6F);
         pose(m.leftArm, k, -2.4F, 0.0F, -0.6F);
      }
   }

   @Override
   public float victimAlpha(CoupDeGrace.State s) {
      if (s.t >= 40.0F && s.t < 96.0F) {
         return 0.0F;
      } else {
         return s.t >= 14.0F && s.t < 40.0F ? 0.99F : 1.0F;
      }
   }

   @Override
   void drawVictim(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex, float alpha) {
      PlayerModel<?> m = model(1, ex.victimSlim());
      this.poseVictim(m, s, age);
      d.model(m, ex.victimSkin(), s.victim, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      if (t < 24.0F) {
         return new CoupDeGrace.Shot(at(0.9F, -3.6F, 1.1F), at(0.6F, 0.0F, 0.8F), 0, true, 3.0F, 58.0F);
      }

      if (t < 46.0F) {
         float k = ease(t, 24.0F, 46.0F);
         return new CoupDeGrace.Shot(orbit(-60.0F - 70.0F * k, 3.2F, 1.5F), at(0.0F, 0.0F, 1.2F), 1, true, 2.5F, 58.0F);
      }

      if (t < 70.0F) {
         float k = ease(t, 46.0F, 70.0F);
         return new CoupDeGrace.Shot(at(2.0F + 0.3F * k, 1.15F + 0.2F * k, 1.85F), at(0.0F, 0.0F, 1.45F - 0.15F * k), 2, true, 3.0F, 50.0F);
      }

      if (t < 86.0F) {
         return new CoupDeGrace.Shot(at(2.0F, -2.2F, 1.5F), at(0.3F, 0.0F, 1.2F), 3, true, 3.0F, 55.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(1.6F, -4.6F, 1.0F), at(1.1F, 0.0F, 1.0F), 4, true, 3.0F, 64.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      return new CoupDeGrace.Shot(at(1.6F, -4.6F, 1.0F).lerp(at(1.2F, -2.8F, 2.2F), k), at(0.2F, 0.0F, 0.5F), 4, false, 2.5F, 60.0F);
   }

   static float landed(float t, float at) {
      return phase(t, at - 4.0F, at);
   }

   Vector3f lobbed(Spectacle.Frame s, float at, Vector3f to) {
      float u = landed(s.t, at);
      Spectacle.Frame release = this.other(s, at - 4.0F);
      Vector3f from = this.actorHand(release, true);
      Vector3f p = new Vector3f(from).lerp(to, u);
      p.y += 3.6F * u * (1.0F - u);
      return p;
   }

   static Matrix4f wobble(float t) {
      float shake = 0.0F;
      if (t >= 52.0F && t < 96.0F) {
         float beat = (t - 52.0F) % 8.0F;
         float n = (t - 52.0F) / 44.0F;
         shake = (2.0F + 5.0F * n) * Mth.sin(beat * 2.6F) * Math.max(0.0F, 1.0F - beat / 5.0F);
         shake += 2.5F * Mth.sin(t * 3.1F) * ease(t, 86.0F, 92.0F);
      }

      return new Matrix4f().rotateZ(shake * (float) (Math.PI / 180.0)).rotateX(0.6F * shake * (float) (Math.PI / 180.0));
   }

   void ball(Draw d, Matrix4f w, Vector3f c, float r) {
      d.sphere(FxTex.SNOW, new Matrix4f(w).translate(c).scale(r * 2.0F), 20, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
   }

   void bit(Draw d, ResourceLocation tex, Vector3f c, float sx, float sy, float sz, float spin) {
      d.box(tex, new Matrix4f().translate(c).rotateXYZ(spin, spin * 0.7F, spin * 0.4F).scale(sx, sy, sz), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
   }

   static void stick(Draw d, Matrix4f w, Vector3f a, Vector3f b, float thick) {
      Vector3f dir = new Vector3f(b).sub(a);
      float len = dir.length();
      Matrix4f m = new Matrix4f(w)
         .translate(new Vector3f(a).lerp(b, 0.5F))
         .rotate(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x / len, dir.y / len, dir.z / len))
         .scale(thick, len, thick);
      d.box(FxTex.BARK, m, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
   }

   static void twig(Draw d, Matrix4f w, int side, float age) {
      Vector3f root = new Vector3f(BALL_2).add(at(0.02F, side * 0.46F * 0.85F, 0.1F));
      float wave = 0.04F * Mth.sin(age * 0.5F + side);
      Vector3f tip = new Vector3f(root).add(at(0.08F, side * 0.62F, 0.34F + wave));
      stick(d, w, root, tip, 0.05F);
      Vector3f fork = new Vector3f(root).lerp(tip, 0.55F);
      stick(d, w, fork, new Vector3f(fork).add(at(0.02F, side * 0.16F, 0.2F)), 0.035F);
      stick(d, w, tip, new Vector3f(tip).add(at(0.06F, side * 0.12F, 0.1F)), 0.03F);
      stick(d, w, tip, new Vector3f(tip).add(at(-0.04F, side * 0.15F, -0.04F)), 0.03F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t < 96.0F) {
         Matrix4f w = wobble(t);
         float drift = ease(t, 14.0F, 20.0F);
         if (drift > 0.0F) {
            d.sphere(
               FxTex.SNOW,
               new Matrix4f().translate(at(0.0F, 0.0F, 0.0F)).scale(1.75F * drift, 0.24F * drift, 1.75F * drift),
               18,
               1.0F,
               1.0F,
               1.0F,
               1.0F,
               Draw.Blend.SOLID
            );
         }

         if (t >= 2.0F) {
            float k = ease(t, 2.0F, 16.0F);
            float r = Mth.lerp(k, 0.15F, 0.52F);
            Vector3f c = at(Mth.lerp(k, 1.1F, 0.0F), 0.0F, Mth.lerp(k, r * 0.82F, BALL_1.y));
            Matrix4f m = (t >= 16.0F ? new Matrix4f(w) : new Matrix4f())
               .translate(c)
               .rotateX(-(t - 2.0F) * 0.35F * (1.0F - ease(t, 14.0F, 16.0F)))
               .scale(r * 2.0F);
            d.sphere(FxTex.SNOW, m, 20, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         }

         if (t >= 28.0F) {
            this.ball(d, t < 32.0F ? new Matrix4f() : w, t < 32.0F ? this.lobbed(s, 32.0F, BALL_2) : BALL_2, 0.46F);
         }

         if (t >= 36.0F) {
            this.ball(d, t < 40.0F ? new Matrix4f() : w, t < 40.0F ? this.lobbed(s, 40.0F, BALL_3) : BALL_3, 0.38F);
         }

         if (t >= 40.0F) {
            Matrix4f m = new Matrix4f(w);
            if (t < 44.0F) {
               Vector3f off = this.lobbed(s, 44.0F, BALL_2).sub(BALL_2);
               m = new Matrix4f().translate(off);
            }

            twig(d, m, 1, age);
            twig(d, m, -1, age);
         }

         if (t >= 44.0F) {
            Vector3f base = new Vector3f(BALL_3).add(at(0.35999998F, 0.0F, -0.02F));
            Matrix4f m = t < 48.0F
               ? new Matrix4f().translate(this.lobbed(s, 48.0F, base)).rotateXYZ(t * 0.8F, t * 0.8F, 0.0F)
               : new Matrix4f(w).translate(base);
            float[][] parts = new float[][]{{0.06F, 0.08F, 0.13F}, {0.18F, 0.06F, 0.12F}, {0.29F, 0.035F, 0.1F}};

            for (float[] p : parts) {
               d.box(FxTex.CARROT, new Matrix4f(m).translate(at(p[0], 0.0F, 0.0F)).scale(p[1], p[1], p[2]), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
            }
         }

         if (t >= 52.0F) {
            Vector3f[] coal = new Vector3f[]{
               at(0.345F, 0.11F, 0.11F),
               at(0.345F, -0.11F, 0.11F),
               at(0.325F, 0.14F, -0.08F),
               at(0.34F, 0.07F, -0.12F),
               at(0.345F, 0.0F, -0.135F),
               at(0.34F, -0.07F, -0.12F),
               at(0.325F, -0.14F, -0.08F)
            };
            float fear = ease(t, 86.0F, 92.0F);

            for (int i = 0; i < coal.length; i++) {
               Vector3f to = new Vector3f(BALL_3).add(coal[i]);
               float size = i < 2 ? 0.08F * (1.0F + 0.6F * fear) : 0.05F;
               if (t < 56.0F) {
                  this.bit(d, FxTex.COAL, this.lobbed(s, 56.0F, to).add(0.03F * i, 0.0F, 0.0F), size, size, size, t);
               } else {
                  d.box(FxTex.COAL, new Matrix4f(w).translate(to).scale(size), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
               }
            }
         }

         if (t >= 60.0F) {
            for (int i = 0; i < 3; i++) {
               float y = 0.2F - 0.17F * i;
               Vector3f to = new Vector3f(BALL_2).add(at(Mth.sqrt(0.2116F - y * y) - 0.01F, 0.0F, y));
               if (t < 64.0F) {
                  this.bit(d, FxTex.COAL, this.lobbed(s, 64.0F, to).add(0.0F, 0.05F * i, 0.0F), 0.085F, 0.085F, 0.085F, t);
               } else {
                  d.box(FxTex.COAL, new Matrix4f(w).translate(to).scale(0.085F), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
               }
            }
         }

         if (t >= 68.0F) {
            float k = t < 72.0F ? landed(t, 72.0F) : 1.0F;
            Vector3f neck = new Vector3f(BALL_2).lerp(BALL_3, 0.62F);
            Matrix4f base = t < 72.0F ? new Matrix4f().translate(this.lobbed(s, 72.0F, neck)) : new Matrix4f(w).translate(neck);
            d.cylinder(
               FxTex.SCARF,
               new Matrix4f(base).translate(0.0F, -0.08F, 0.0F).scale(0.7F * (0.4F + 0.6F * k), 0.16F, 0.7F * (0.4F + 0.6F * k)),
               16,
               1.0F,
               1.0F,
               1.0F,
               1.0F,
               1.0F,
               Draw.Blend.SOLID,
               true
            );
            if (t >= 72.0F) {
               d.box(
                  FxTex.SCARF,
                  new Matrix4f(base).translate(at(0.34F, -0.13F, -0.2F)).rotateX(0.25F).rotateZ(0.12F * Mth.sin(age * 0.2F)).scale(0.12F, 0.38F, 0.04F),
                  1.0F,
                  1.0F,
                  1.0F,
                  1.0F,
                  Draw.Blend.SOLID
               );
               d.box(
                  FxTex.SCARF,
                  new Matrix4f(base).translate(at(0.15F, 0.3F, -0.05F)).rotateZ(1.1F + 0.25F * Mth.sin(age * 0.45F)).scale(0.11F, 0.34F, 0.04F),
                  1.0F,
                  1.0F,
                  1.0F,
                  1.0F,
                  Draw.Blend.SOLID
               );
            }
         }

         if (t >= 76.0F) {
            Vector3f top = new Vector3f(BALL_3).add(0.0F, 0.32999998F, 0.0F);
            Matrix4f m = t < 80.0F
               ? new Matrix4f().translate(this.lobbed(s, 80.0F, top)).rotateY(t * 0.6F).rotateZ(0.3F)
               : new Matrix4f(w).translate(top).rotateZ(0.1F);
            hat(d, m);
         }
      } else if (t < 122.0F) {
         float tau = t - 96.0F;
         Vector3f hat = new Vector3f(BALL_3).add(at(-0.25F * tau, 0.05F * tau, 0.38F + 0.3F * tau - 0.022F * tau * tau));
         hat.y = Math.max(hat.y, 0.02F);
         hat(d, new Matrix4f().translate(hat).rotateX(tau * 0.5F * (hat.y > 0.03F ? 1.0F : 0.0F)).rotateZ(tau * 0.3F * (hat.y > 0.03F ? 1.0F : 0.0F)));

         for (int side = -1; side <= 1; side += 2) {
            Vector3f p = new Vector3f(BALL_2).add(at(-0.12F * tau, side * (0.5F + 0.1F * tau), 0.25F * tau - 0.024F * tau * tau));
            p.y = Math.max(p.y, 0.03F);
            float spin = p.y > 0.04F ? tau * 0.6F : 1.4F;
            Matrix4f m = new Matrix4f().translate(p).rotateXYZ(spin, side * spin, 0.4F);
            stick(d, m, new Vector3f(0.0F, -0.35F, 0.0F), new Vector3f(0.0F, 0.35F, 0.0F), 0.05F);
         }

         Vector3f carrot = new Vector3f(BALL_3).add(at(-0.18F * tau, -0.1F * tau, 0.2F * tau - 0.025F * tau * tau));
         carrot.y = Math.max(carrot.y, 0.04F);
         this.bit(d, FxTex.CARROT, carrot, 0.07F, 0.07F, 0.3F, carrot.y > 0.05F ? tau * 0.9F : 1.2F);
         Vector3f neck = s.victim.scene(0.0F, 0.5F, 0.0F);
         d.box(FxTex.SCARF, new Matrix4f().translate(neck).rotate(s.victim.rot).scale(0.55F, 0.12F, 0.32F), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         float k = tau / 12.0F;
         if (k < 1.0F) {
            d.glow(BALL_2, 4.0F * (1.0F - k), 1.0F, 1.0F, 1.0F, 1.0F - k);
            d.decal(FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 7.0F * out(k, 2.0F), 0.0F, 0.8F, 0.92F, 1.0F, 0.9F * (1.0F - k), Draw.Blend.GLOW);
         }
      }

      float ground = ease(t, 0.0F, 60.0F);
      d.decal(FxTex.FROST, at(0.0F, 0.0F, 0.0F), 5.0F * ground, 0.3F, 1.0F, 1.0F, 1.0F, 0.5F * ground, Draw.Blend.SOFT);
      if (t >= 96.0F) {
         d.decal(FxTex.SHOCK, at(-0.3F, 0.0F, 0.0F), 3.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.5F * ease(t, 96.0F, 100.0F), Draw.Blend.SOFT);
      }
   }

   static void hat(Draw d, Matrix4f m) {
      d.cylinder(FxTex.HAT, new Matrix4f(m).scale(0.62F, 0.03F, 0.62F), 16, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID, true);
      d.cylinder(FxTex.HAT, new Matrix4f(m).scale(0.38F, 0.36F, 0.38F), 16, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID, true);
      Vector3f band = new Vector3f(0.17F, 0.08F, 0.08F);
      d.box(FxTex.SNOW, new Matrix4f(m).translate(band).rotateY(0.6F).rotateZ(0.5F).scale(0.12F, 0.04F, 0.06F), 0.15F, 0.55F, 0.2F, 1.0F, Draw.Blend.SOLID);
      d.box(FxTex.SNOW, new Matrix4f(m).translate(band).rotateY(-0.3F).rotateZ(-0.4F).scale(0.12F, 0.04F, 0.06F), 0.15F, 0.55F, 0.2F, 1.0F, Draw.Blend.SOLID);

      for (int i = 0; i < 3; i++) {
         d.box(
            FxTex.SNOW,
            new Matrix4f(m).translate(band.x + 0.02F, band.y + 0.04F + 0.02F * (i % 2), band.z - 0.03F + 0.03F * i).scale(0.035F),
            0.85F,
            0.08F,
            0.1F,
            1.0F,
            Draw.Blend.SOLID
         );
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 1) {
         fx.sound(Sfx.SLEIGH_BELLS, s.actor.axis(1.5F), 0.6F, 0.9F);
      }

      if (t > 0 && t < 118.0F && t % 2 == 0) {
         Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 3.5F * Vfx.RANDOM.nextFloat(), 4.0F);
         fx.p(Sprite.SNOWFLAKE, p).vel(fx.dir(0.006F, -0.028F, 0.004F)).size(0.1F).life(110).color(16777215).roll(0.0F, 0.04F);
      }

      if (t >= 2.0F && t < 16.0F && t % 3 == 0) {
         float k = ease(t, 2.0F, 16.0F);
         fx.sound(Sfx.SNOW_CRUNCH, at(Mth.lerp(k, 1.1F, 0.0F), 0.0F, 0.2F), 0.8F, 1.1F - 0.3F * k);
         fx.p(Sprite.SNOWBALL, at(Mth.lerp(k, 1.1F, 0.0F) + 0.2F, 0.0F, 0.05F))
            .vel(fx.dir(0.03F, 0.04F, (float)Vfx.random(0.03).z))
            .gravity(0.01)
            .size(0.08F)
            .life(16)
            .color(16777215)
            .soft();
      }

      if (t == 6) {
         fx.sound(Sfx.WHOOSH, s.victim.axis(1.0F), 0.5F, 1.0F);
      }

      float[] lands = new float[]{16.0F, 32.0F, 40.0F};
      Vector3f[] centres = new Vector3f[]{BALL_1, BALL_2, BALL_3};

      for (int i = 0; i < 3; i++) {
         if (t == (int)lands[i]) {
            fx.sound(Sfx.SNOW_CRUNCH, centres[i], 1.0F, 0.8F + 0.1F * i);
            fx.sound(Sfx.CHIME, centres[i], 0.4F, 1.0F + 0.12F * i);
            fx.burst(Sprite.SNOWBALL, centres[i], 8, 0.08F, 0.1F, 18, 16777215, true);
         }

         if (i > 0 && t == (int)(lands[i] - 4.0F)) {
            fx.sound(Sfx.WHOOSH, s.actor.axis(1.4F), 0.6F, 1.1F);
         }
      }

      if (t == 40) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.4F), 0.5F, 1.2F);
      }

      if (t == 44) {
         fx.sound(Sfx.SNOW_CRUNCH, BALL_2, 0.8F, 1.3F);
         fx.sound(Sfx.CANDY_CRACK, BALL_2, 0.3F, 1.8F);
         fx.burst(Sprite.SNOWBALL, BALL_2, 6, 0.08F, 0.08F, 16, 16777215, true);
      }

      if (t >= 52 && t < 96.0F && (t - 52) % 8 == 0) {
         fx.sound(Sfx.SNOW_CRUNCH, BALL_2, 0.5F + 0.4F * (t - 52) / 44.0F, 0.8F);
         fx.burst(Sprite.SNOWFLAKE, BALL_3, 4, 0.05F, 0.08F, 14, 16777215, false);
      }

      float[] touches = new float[]{48.0F, 56.0F, 64.0F, 72.0F, 80.0F};

      for (int i = 0; i < touches.length; i++) {
         if (t == (int)(touches[i] - 4.0F)) {
            fx.sound(Sfx.WHOOSH, s.actor.axis(1.4F), 0.5F, 1.4F);
         }

         if (t == (int)touches[i]) {
            Vector3f at = i >= 2 && i != 4 ? BALL_2 : BALL_3;
            fx.sound(Sfx.SPARKLE, at, 0.6F, 1.0F + 0.08F * i);
            fx.sound(Sfx.SNOW_CRUNCH, at, 0.5F, 1.4F);
            fx.burst(Sprite.TWINKLE, at, 8, 0.1F, 0.16F, 18, 16769136, false);
         }
      }

      if (t == 82) {
         fx.sound(Sfx.JINGLE_CHORD, BALL_3, 0.8F, 1.0F);
      }

      if (t >= 86.0F && t < 92.0F && t % 3 == 0) {
         fx.sound(Sfx.SNOW_CRUNCH, s.actor.axis(0.1F), 0.8F, 1.1F);
         fx.p(Sprite.SNOWBALL, s.actor.axis(0.05F)).vel(fx.dir(0.0F, 0.05F, 0.04F)).gravity(0.01).size(0.08F).life(14).color(16777215).soft();
      }

      if (t == 92) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.actor.axis(1.0F), 1.0F, 0.9F);
      }

      if (t == 96.0F) {
         fx.sound(Sfx.SNOW_BURST, BALL_2, 1.0F, 1.0F);
         fx.sound(Sfx.IMPACT_HEAVY, BALL_2, 1.0F, 1.0F);
         fx.sound(Sfx.BONE_CRACK, BALL_3, 0.8F, 0.9F);
         fx.flash(BALL_2, 0.4F);
         fx.shake(1.0F, 0.8F);

         for (int i = 0; i < 3; i++) {
            fx.burst(Sprite.SNOWBALL, centres[i], 26, 0.35F, 0.22F, 40, 16777215, true);
         }

         for (int i = 0; i < 60; i++) {
            fx.p(Sprite.SNOWBALL, new Vector3f(BALL_2).add(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 1.4F, 0.0F))
               .vel(fx.dir(new Vector3f((float)Vfx.random(0.25).x, 0.12F + 0.15F * Vfx.RANDOM.nextFloat(), (float)Vfx.random(0.25).z - 0.12F)))
               .gravity(0.025)
               .drag(0.97)
               .size(0.12F + 0.14F * Vfx.RANDOM.nextFloat())
               .life(50 + Vfx.RANDOM.nextInt(20))
               .color(16777215)
               .soft();
         }

         fx.burst(Sprite.SNOWFLAKE, BALL_2, 50, 0.4F, 0.14F, 60, 16777215, false);
         fx.burst(Sprite.SMOKE, BALL_2, 16, 0.15F, 1.0F, 40, 15922943, true);
         fx.burst(Sprite.SHARD_B, BALL_3, 8, 0.25F, 0.08F, 30, 2105380, false);
      }

      if (t == 101.0F) {
         fx.sound(Sfx.BODY_FALL, at(-0.7F, 0.0F, 0.2F));
         fx.sound(Sfx.BODY_FALL, s.actor.axis(0.2F), 0.8F, 1.1F);
         fx.ring(Sprite.SNOWFLAKE, at(-0.7F, 0.0F, 0.1F), 16, 0.1F, 0.14F, 24, 16777215, false);
      }
   }
}
