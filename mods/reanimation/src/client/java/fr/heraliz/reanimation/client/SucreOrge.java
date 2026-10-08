package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class SucreOrge extends Finisher {
   static final SucreOrge INSTANCE = new SucreOrge();
   static final float APPEAR = 4.0F;
   static final float TWIRL = 8.0F;
   static final float HOOK = 40.0F;
   static final float SPIN = 48.0F;
   static final float RELEASE = 80.0F;
   static final float LANDS = 94.0F;
   static final float AIM = 84.0F;
   static final float THROW = 91.0F;
   static final Vector3f CENTRE = at(0.6F, 0.0F, 0.0F);
   static final float SWING = 1.9F;
   static final int RED = 14684202;
   static final int WHITE = 16777215;
   static final int MINT = 4243552;
   static final List<Vector3f> SHAPE = new ArrayList<>();

   private SucreOrge() {
      super(ExecutionType.SUCRE_D_ORGE);
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
      return 1.0F;
   }

   static float spin(float t) {
      float u = phase(t, 48.0F, 80.0F);
      return 180.0F + 1080.0F * u * u;
   }

   static float radius(float t) {
      return Mth.lerp(ease(t, 48.0F, 56.0F), 0.62F, 1.9F);
   }

   static Vector3f swung(float t) {
      float h = Mth.lerp(ease(t, 48.0F, 80.0F), 1.25F, 1.75F);
      return new Vector3f(CENTRE).add(orbit(spin(t), radius(t), h));
   }

   static Vector3f flying(float t) {
      float u = phase(t, 80.0F, 94.0F);
      Vector3f from = swung(80.0F);
      Vector3f to = at(0.0F, 0.0F, 0.65F);
      Vector3f p = new Vector3f(from).lerp(to, u);
      p.y += 16.8F * u * (1.0F - u);
      return p;
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      Vector3f p = at(1.0F, 0.0F, 0.0F);
      float yaw = 180.0F;
      if (t >= 40.0F && t < 48.0F) {
         p = at(Mth.lerp(ease(t, 40.0F, 44.0F), 1.0F, 0.6F), 0.0F, 0.0F);
      } else if (t >= 48.0F && t < 82.0F) {
         p = new Vector3f(CENTRE);
         yaw = Script.yawToward(CENTRE, swung(Math.min(t, 80.0F)));
      } else if (t >= 82.0F) {
         float back = ease(t, 82.0F, 86.0F);
         float run = ease(t, 87.0F, 91.0F);
         p = at(Mth.lerp(run, Mth.lerp(back, 0.6F, 2.5F), 1.8F), 0.0F, 0.0F);
         yaw = Mth.rotLerp(ease(t, 82.0F, 86.0F), Script.yawToward(CENTRE, swung(80.0F)), 180.0F);
      }

      s.actorPoint.set(p);
      s.actorYaw = yaw;
      s.lean = 0.15F * Keys.window(t, 48.0F, 80.0F, 4.0F) - 0.25F * Keys.window(t, 84.0F, 92.0F, 2.0F) + 0.35F * Keys.window(t, 91.0F, 97.0F, 1.5F);
      CoupDeGrace.Body v = s.victim;
      if (t < 42.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 48.0F) {
         rise(v, ease(t, 42.0F, 48.0F), s.side, 0.0F, at(0.0F, 0.0F, 1.205625F));
      } else if (t < 80.0F) {
         Vector3f neck = swung(t);
         float flat = ease(t, 48.0F, 58.0F);
         float toCentre = Script.yawToward(neck, new Vector3f(CENTRE.x, neck.y, CENTRE.z));
         v.turn(Mth.rotLerp(flat, 0.0F, toCentre), -90.0F * flat, 0.0F, 0.0F).anchor(1.4071876F, neck);
      } else if (t < 94.0F) {
         float u = phase(t, 80.0F, 94.0F);
         float toCentre = Script.yawToward(swung(80.0F), new Vector3f(CENTRE.x, 0.0F, CENTRE.z));
         v.turn(Mth.rotLerp(u, toCentre, 90.0F * s.side), -90.0F - 360.0F * smooth(u), 0.0F, 0.0F).anchor(1.0556251F, flying(t));
      } else {
         float jolt = Keys.jolt(t, 96.0F, 8.0F, 1.5F);
         v.turn(90.0F * s.side, -90.0F, 0.0F, 0.0F)
            .anchor(1.0556251F, at(0.0F, 0.0F, 0.3F - 0.04F * jolt + 0.12F * Mth.sin((float) Math.PI * phase(t, 94.0F, 97.0F))));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float twirl = Keys.window(t, 4.0F, 41.0F, 3.0F);
      pose(m.rightArm, twirl, -1.25F + 0.15F * Mth.sin(t * 0.9F), 0.0F, 0.15F);
      pose(m.leftArm, twirl, -0.4F, 0.0F, -0.25F);
      float overhead = Keys.window(t, 24.0F, 36.0F, 2.0F);
      pose(m.rightArm, overhead, -2.9F, 0.0F, 0.1F);
      float swing = Keys.window(t, 40.0F, 82.0F, 3.0F);
      Vector3f target = t < 48.0F ? s.victim.axis(1.3F) : swung(Math.min(t, 80.0F));
      hold(m.rightArm, s.actor.model(target), swing);
      hold(m.leftArm, s.actor.model(target), swing);
      float lean = Keys.window(t, 48.0F, 80.0F, 4.0F);
      m.rightLeg.xRot = 0.35F * lean;
      m.leftLeg.xRot = -0.25F * lean;
      float aim = Keys.window(t, 84.0F, 92.0F, 2.0F);
      pose(m.rightArm, aim, -2.8F, 0.0F, 0.35F);
      pose(m.leftArm, aim, -1.6F, 0.0F, -0.1F);
      float run = Keys.window(t, 87.0F, 91.0F, 1.0F);
      walk(m, t * 0.9F, run, 1.0F);
      float thrown = Keys.window(t, 91.0F, 101.0F, 1.5F);
      pose(m.rightArm, thrown, -1.4F, 0.0F, -0.2F);
      pose(m.leftArm, thrown, 0.3F, 0.0F, -0.2F);
      pose(m.rightLeg, thrown, -0.4F, 0.0F, 0.0F);
      pose(m.leftLeg, thrown, 0.4F, 0.0F, 0.0F);
      if (t < 48.0F || t > 84.0F) {
         look(m.head, s.actor.model(t > 80.0F && t < 94.0F ? flying(t) : s.victim.axis(0.8F)), 0.9F, 1.2F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= 42.0F && t < 48.0F) {
         float k = ease(t, 42.0F, 48.0F);
         pose(m.rightArm, k, -2.4F, 0.0F, 0.4F);
         pose(m.leftArm, k, -2.4F, 0.0F, -0.4F);
         pose(m.rightLeg, k, 0.1F, 0.0F, 0.05F);
         pose(m.leftLeg, k, -0.05F, 0.0F, -0.05F);
         pose(m.head, k, 0.5F, 0.0F, 0.0F);
      } else if (t >= 48.0F && t < 94.0F) {
         pose(m.rightArm, 1.0F, -2.8F, 0.0F, 0.3F);
         pose(m.leftArm, 1.0F, -2.8F, 0.0F, -0.3F);
         pose(m.rightLeg, 1.0F, 0.15F, 0.0F, 0.12F);
         pose(m.leftLeg, 1.0F, -0.15F, 0.0F, -0.12F);
         flail(m, age, ease(t, 80.0F, 84.0F));
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      if (t < 40.0F) {
         return new CoupDeGrace.Shot(new Vector3f(s.actor.pos).add(at(-3.0F, -1.4F, 1.4F)), s.actor.axis(1.3F), 0, true, 3.0F, 55.0F);
      } else if (t < 60.0F) {
         return new CoupDeGrace.Shot(at(0.6F, -4.6F, 1.4F), at(0.5F, 0.0F, 1.1F), 1, true, 3.0F, 62.0F);
      } else if (t < 82.0F) {
         return new CoupDeGrace.Shot(at(2.6F, -2.6F, 5.0F), new Vector3f(CENTRE).add(0.0F, 1.0F, 0.0F), 2, true, 3.0F, 66.0F);
      } else if (t < 100.0F) {
         float k = ease(t, 80.0F, 94.0F);
         return new CoupDeGrace.Shot(at(1.6F, -5.0F, 0.8F), at(0.6F, 0.0F, Mth.lerp(k, 2.4F, 1.2F)), 3, true, 3.0F, 68.0F);
      } else {
         float k = ease(t, 100.0F, 128.0F);
         return new CoupDeGrace.Shot(at(1.6F, -5.0F, 0.8F).lerp(at(1.6F, -2.6F, 2.0F), k), at(0.2F, 0.0F, 0.7F), 3, false, 2.5F, 62.0F);
      }
   }

   Matrix4f cane(Spectacle.Frame s) {
      float t = s.t;
      if (t < 4.0F) {
         return null;
      }

      if (t < 91.0F) {
         Vector3f hand = this.actorHand(s, true);
         Quaternionf q;
         if (t < 40.0F) {
            Quaternionf facing = new Quaternionf().rotationY((180.0F - s.actorYaw) * (float) (Math.PI / 180.0));
            float spin = (t - 8.0F) * 0.8F * Keys.window(t, 8.0F, 38.0F, 3.0F);
            float overhead = Keys.window(t, 24.0F, 36.0F, 2.0F);
            q = facing.rotateZ(spin * (1.0F - overhead)).rotateX((float) (-Math.PI / 2) * overhead).rotateZ(spin * overhead);
         } else if (t < 81.0F) {
            Vector3f target = t < 48.0F ? s.victim.axis(1.3F) : swung(t);
            Vector3f dir = new Vector3f(target).sub(hand).normalize();
            q = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x, dir.y, dir.z);
         } else {
            Vector3f dir = at(-1.0F, 0.0F, 0.15F).normalize();
            q = new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, dir.x, dir.y, dir.z);
         }

         float grow = ease(t, 4.0F, 8.0F);
         return new Matrix4f().translate(hand).rotate(q).scale(grow);
      } else {
         Spectacle.Frame release = this.other(s, 91.0F);
         Vector3f from = this.actorHand(release, true);
         Vector3f to = at(0.08F, 0.0F, 0.22F);
         float u = phase(t, 91.0F, 96.0F);
         Vector3f p = new Vector3f(from).lerp(to, u);
         p.y += 6.4F * u * (1.0F - u);
         Vector3f vel = new Vector3f(to).sub(from).add(0.0F, 6.4F * (1.0F - 2.0F * u), 0.0F).normalize();
         if (t >= 96.0F) {
            vel = at(0.35F, 0.0F, -1.0F).normalize();
            p = to;
         }

         Quaternionf q = new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, vel.x, vel.y, vel.z);
         return new Matrix4f().translate(p).rotate(q).translate(0.0F, 0.6F, 0.0F);
      }
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      Matrix4f m = this.cane(s);
      if (m != null) {
         List<Vector3f> pts = new ArrayList<>(SHAPE.size());

         for (Vector3f p : SHAPE) {
            pts.add(m.transformPosition(new Vector3f(p)));
         }

         d.tube(FxTex.CANDY, pts, 0.075F, 8, 0.45F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
         if (t < 10.0F) {
            d.glow(pts.get(6), 1.6F * (1.0F - phase(t, 4.0F, 10.0F)), 1.0F, 0.9F, 0.9F, 0.9F);
         }

         float glint = age * 0.05F % 1.0F;
         d.glow(pts.get((int)(glint * (pts.size() - 1))), 0.3F, 1.0F, 1.0F, 1.0F, 0.6F);
         if (t >= 91.0F && t < 96.0F) {
            d.streak(
               pts.get(0),
               new Vector3f((Vector3fc)pts.get(0)).sub(new Vector3f((Vector3fc)pts.get(pts.size() - 1)).sub((Vector3fc)pts.get(0)).mul(1.2F)),
               0.3F,
               1.0F,
               0.4F,
               0.5F,
               0.6F
            );
         }
      }

      float wheel = Keys.window(t, 10.0F, 38.0F, 3.0F);
      if (wheel > 0.0F && m != null) {
         Vector3f c = m.transformPosition(new Vector3f(0.0F, 0.25F, 0.0F));
         d.sprite(Sprite.RING, c, 2.2F, age * 0.4F, r(14684202), g(14684202), b(14684202), 0.18F * wheel, Draw.Blend.GLOW);
         d.sprite(Sprite.RING, c, 1.8F, -age * 0.4F, 1.0F, 1.0F, 1.0F, 0.14F * wheel, Draw.Blend.GLOW);
      }

      float spinning = Keys.window(t, 56.0F, 81.0F, 3.0F);
      if (spinning > 0.0F) {
         List<Vector3f> ring = new ArrayList<>();
         float a0 = spin(t);

         for (int i = 0; i <= 16; i++) {
            float a = a0 - 140.0F * i / 16.0F;
            ring.add(new Vector3f(CENTRE).add(orbit(a, radius(t) + 0.3F, Mth.lerp(ease(t, 48.0F, 80.0F), 1.25F, 1.75F) - 0.3F)));
         }

         d.ribbon(FxTex.BEAM, ring, 0.6F, 1.0F, age * 0.1F, 1.0F, 1.0F, 1.0F, 0.6F * spinning, 0.0F, Draw.Blend.GLOW);
      }

      if (t >= 96.0F && t < 108.0F) {
         float k = (t - 96.0F) / 12.0F;
         d.glow(at(0.0F, 0.0F, 0.6F), 4.0F * (1.0F - k), 1.0F, 0.9F, 0.9F, 1.0F - k);
         d.decal(FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 7.0F * out(k, 2.0F), 0.0F, 1.0F, 0.4F, 0.45F, 0.9F * (1.0F - k), Draw.Blend.GLOW);
      }

      float snow = ease(t, 96.0F, 100.0F);
      if (snow > 0.0F) {
         d.decal(FxTex.FROST, at(0.0F, 0.0F, 0.0F), 3.4F, 0.5F, 1.0F, 1.0F, 1.0F, 0.6F * snow, Draw.Blend.SOFT);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 4) {
         fx.sound(Sfx.SPARKLE, s.actor.axis(1.3F), 1.0F, 1.0F);
         fx.sound(Sfx.SLEIGH_BELLS, s.actor.axis(1.3F), 0.6F, 1.1F);
         fx.burst(Sprite.STAR5, this.actorHand(s, true), 16, 0.15F, 0.16F, 24, 16769136, false);
         fx.burst(Sprite.CANDY, this.actorHand(s, true), 8, 0.12F, 0.2F, 24, 16777215, false);
      }

      if (t >= 8.0F && t < 40.0F && t % 4 == 0) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.3F), 0.6F, 1.3F + 0.1F * (t / 4 % 2));
      }

      if (t == 42) {
         fx.sound(Sfx.CANDY_CRACK, s.victim.axis(1.2F), 0.4F, 1.5F);
         fx.sound(Sfx.WHOOSH_HEAVY, s.victim.axis(1.0F), 0.7F, 1.1F);
      }

      if (t >= 48.0F && t < 80.0F) {
         float u = phase(t, 48.0F, 80.0F);
         int every = u < 0.4F ? 8 : (u < 0.75F ? 5 : 3);
         if (t % every == 0) {
            fx.sound(Sfx.WHOOSH_HEAVY, swung(t), 0.6F + 0.4F * u, 0.8F + 0.5F * u);
         }

         if (t % 2 == 0) {
            fx.p(Sprite.SNOWFLAKE, at(0.0F, 0.0F, 0.0F).add(swung(t))).vel(fx.dir(0.0F, 0.02F, 0.0F)).size(0.14F).life(20).color(16777215);
         }
      }

      if (t == 80) {
         fx.sound(Sfx.WHOOSH_HEAVY, swung(t), 1.0F, 0.7F);
         fx.sound(Sfx.SLEIGH_BELLS, swung(t), 0.8F, 1.2F);
      }

      if (t == 94) {
         fx.sound(Sfx.BODY_FALL, at(0.0F, 0.0F, 0.2F), 1.0F, 1.0F);
         fx.sound(Sfx.SNOW_CRUNCH, at(0.0F, 0.0F, 0.2F), 1.0F, 0.7F);
         fx.ring(Sprite.SNOWFLAKE, at(0.0F, 0.0F, 0.1F), 20, 0.12F, 0.15F, 24, 16777215, false);
      }

      if (t == 91) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.7F), 1.0F, 0.9F);
      }

      if (t == 96.0F) {
         Vector3f c = at(0.0F, 0.0F, 0.5F);
         fx.sound(Sfx.CANDY_CRACK, c, 1.0F, 1.0F);
         fx.sound(Sfx.SNOW_BURST, c, 1.0F, 1.0F);
         fx.sound(Sfx.JINGLE_CHORD, c, 0.9F, 1.0F);
         fx.sound(Sfx.IMPACT_HEAVY, c, 0.8F, 1.0F);
         fx.flash(c, 0.4F);
         fx.shake(0.9F, 0.7F);
         fx.burst(Sprite.CANDY, c, 30, 0.35F, 0.2F, 40, 16777215, false);
         fx.burst(Sprite.SNOWBALL, c, 20, 0.3F, 0.18F, 40, 16777215, true);
         fx.burst(Sprite.SNOWFLAKE, c, 40, 0.4F, 0.14F, 50, 16777215, false);
         fx.burst(Sprite.DROP, c, 12, 0.25F, 0.1F, 30, 9043984, true);
         fx.ring(Sprite.SMOKE, at(0.0F, 0.0F, 0.1F), 24, 0.25F, 0.7F, 30, 15791871, true);
      }

      if (t > 2 && t < 118.0F && t % 2 == 0) {
         Vector3f p = orbit(Vfx.RANDOM.nextFloat() * 360.0F, 3.5F * Vfx.RANDOM.nextFloat(), 4.0F);
         fx.p(Sprite.SNOWFLAKE, p).vel(fx.dir(0.005F, -0.03F, 0.003F)).size(0.09F).life(100).color(16777215).roll(0.0F, 0.04F);
      }
   }

   static {
      for (int i = 0; i <= 6; i++) {
         SHAPE.add(new Vector3f(0.0F, -0.6F + 1.7F * i / 6.0F, 0.0F));
      }

      for (int i = 1; i <= 10; i++) {
         float a = (float) Math.PI - (float) Math.PI * i / 10.0F;
         SHAPE.add(new Vector3f(0.26F + 0.26F * Mth.cos(a), 1.1F + 0.26F * Mth.sin(a), 0.0F));
      }

      SHAPE.add(new Vector3f(0.52F, 0.92F, 0.0F));
   }
}
