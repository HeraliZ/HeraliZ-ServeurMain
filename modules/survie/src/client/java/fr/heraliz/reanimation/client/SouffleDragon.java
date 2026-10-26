package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class SouffleDragon extends Finisher {
   static final SouffleDragon INSTANCE = new SouffleDragon();
   static final float EMERGE = 32.0F;
   static final float COIL = 48.0F;
   static final float CLIMB = 64.0F;
   static final float TOP = 84.0F;
   static final float DIVE = 90.0F;
   static final float LENGTH = 20.0F;
   static final int ORANGE = 16742938;
   static final int YELLOW = 16765008;
   static final int RED = 14690832;

   private SouffleDragon() {
      super(ExecutionType.SOUFFLE_DRAGON);
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

   static Vector3f chest() {
      return at(1.6F, 0.0F, 1.15F);
   }

   static float angle(float t) {
      return 20.0F + (t - 32.0F) * 9.0F + Math.max(0.0F, t - 48.0F) * 3.0F;
   }

   static Vector3f head(float t) {
      if (t <= 32.0F) {
         return chest();
      } else if (t < 40.0F) {
         float k = smooth(phase(t, 32.0F, 40.0F));
         Vector3f to = orbit(angle(40.0F), 2.4F, 1.9F);
         Vector3f p = chest().lerp(to, k);
         p.y = p.y + 1.4F * Mth.sin((float) Math.PI * k);
         return p;
      } else if (t < 64.0F) {
         float r = Mth.lerp(ease(t, 48.0F, 58.0F), 2.4F, 0.95F);
         float y = Mth.lerp(ease(t, 48.0F, 58.0F), 1.9F + 0.35F * Mth.sin(t * 0.35F), 1.35F + 0.25F * Mth.sin(t * 0.9F));
         return orbit(angle(t), r, y);
      } else if (t < 84.0F) {
         float k = phase(t, 64.0F, 84.0F);
         return orbit(angle(t), Mth.lerp(smooth(k), 0.95F, 2.2F), Mth.lerp(k * k * (3.0F - 2.0F * k), 1.35F, 7.6F));
      } else if (t < 90.0F) {
         float k = smooth(phase(t, 84.0F, 90.0F));
         Vector3f p = orbit(angle(84.0F), 2.2F, 7.6F).lerp(at(0.0F, 0.0F, 8.6F), k);
         p.y = p.y + 0.6F * Mth.sin((float) Math.PI * k);
         return p;
      } else {
         float u = (t - 90.0F) / 6.0F;
         return at(0.0F, 0.0F, 8.6F - 8.0F * u * u);
      }
   }

   static List<Vector3f> body(float t, int n) {
      List<Vector3f> pts = new ArrayList<>(n);

      for (int i = 0; i < n; i++) {
         pts.add(head(t - 20.0F * i / (n - 1)));
      }

      return pts;
   }

   static float stance(float t) {
      return Keys.window(t, 8.0F, 31.0F, 3.0F) + Keys.window(t, 88.0F, 108.0F, 3.0F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float horse = stance(t);
      float lunge = Keys.window(t, 90.0F, 106.0F, 3.0F);
      s.actorPoint.set(at(1.6F - 0.2F * lunge, 0.0F, -0.07F * horse));
      s.actorYaw = 180.0F;
      s.lean = 0.5F * Keys.window(t, 0.0F, 8.0F, 3.0F) + 0.3F * ease(t, 92.0F, 96.0F) * (1.0F - ease(t, 102.0F, 110.0F));
      CoupDeGrace.Body v = s.victim;
      if (t < 52.0F) {
         prone(v, t, victimYaw, s.side);
      } else if (t < 96.0F) {
         float k = ease(t, 52.0F, 62.0F);
         float y = Mth.lerp(k, 0.3F, 1.45F) + 0.08F * Mth.sin(t * 0.2F) * k;
         float turn = 2.5F * Math.max(0.0F, t - 48.0F - 4.0F) * k;
         v.turn(90.0F * s.side + turn, -90.0F + 8.0F * Mth.sin(t * 0.25F) * k, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, y));
      } else {
         float u = phase(t, 96.0F, 98.0F);
         float bounce = 0.12F * Mth.sin((float) Math.PI * phase(t, 98.0F, 102.0F));
         float turn = 110.0F;
         v.turn(90.0F * s.side + turn, -90.0F, 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, Mth.lerp(u * u, 1.45F, 0.3F) + bounce));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float horse = stance(t);
      m.rightLeg.zRot = 0.45F * horse;
      m.leftLeg.zRot = -0.45F * horse;
      float bow = Keys.window(t, 0.0F, 9.0F, 3.0F);
      pose(m.rightArm, bow, -1.1F, 0.0F, -0.4F);
      pose(m.leftArm, bow, -1.1F, 0.0F, 0.4F);
      float guard = Keys.window(t, 8.0F, 32.0F, 3.0F);
      pose(m.rightArm, guard, -0.6F, 0.0F, 0.25F);
      pose(m.leftArm, guard, -0.6F, 0.0F, -0.25F);
      pose(m.rightArm, Keys.window(t, 15.0F, 22.0F, 1.5F), -1.6F, 0.0F, 0.0F);
      pose(m.leftArm, Keys.window(t, 23.0F, 30.0F, 1.5F), -1.6F, 0.0F, 0.0F);
      float crane = Keys.window(t, 30.0F, 42.0F, 3.0F);
      pose(m.rightLeg, crane, -1.25F, 0.0F, 0.05F);
      pose(m.rightArm, crane, -0.5F, 0.0F, 2.1F);
      pose(m.leftArm, crane, -0.5F, 0.0F, -2.1F);
      pose(m.head, crane, -0.3F, 0.0F, 0.0F);
      float guide = Keys.window(t, 40.0F, 66.0F, 3.0F);
      float w = age * 0.24F;
      pose(m.rightArm, guide, -1.4F + 0.6F * Mth.sin(w), 0.0F, 0.6F + 0.5F * Mth.cos(w));
      pose(m.leftArm, guide, -1.2F + 0.6F * Mth.sin(w + 2.0F), 0.0F, -0.6F - 0.5F * Mth.cos(w + 2.0F));
      armsUp(m, Keys.window(t, 64.0F, 91.0F, 4.0F));
      float strike = Keys.window(t, 90.0F, 108.0F, 2.0F);
      pose(m.rightArm, strike, -1.45F, -0.2F, -0.05F);
      pose(m.leftArm, strike, -1.45F, 0.2F, 0.05F);
      if (!(t < 64.0F) && !(t > 96.0F)) {
         look(m.head, s.actor.model(head(t)), 1.0F, 1.2F);
      } else {
         look(m.head, s.actor.model(t < 32.0F ? s.victim.axis(0.5F) : head(t)), 0.9F, 0.8F);
      }
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      lyingPose(m);
      if (t >= 52.0F && t < 96.0F) {
         float k = ease(t, 52.0F, 60.0F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.3F + 0.5F * Mth.sin(age * 0.7F));
         m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.3F + 0.5F * Mth.sin(age * 0.7F + 2.0F));
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.4F);
         m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.4F);
         m.rightLeg.xRot = -0.35F * k + 0.25F * Mth.sin(age * 0.8F) * k;
         m.leftLeg.xRot = -0.3F * k + 0.25F * Mth.sin(age * 0.8F + 2.5F) * k;
         m.head.xRot = 0.4F * k;
      } else if (t >= 96.0F) {
         float k = ease(t, 98.0F, 102.0F);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, -1.0F);
         m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, 1.0F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -2.4F);
         m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -2.6F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      if (t < 32.0F) {
         float k = ease(t, 0.0F, 32.0F);
         Vector3f pos = at(-0.9F + 0.3F * k, -1.2F - 0.5F * k, 1.0F);
         return new CoupDeGrace.Shot(pos, s.actor.axis(1.0F), 0, true, 3.0F, 55.0F);
      }

      if (t < 64.0F) {
         float k = ease(t, 32.0F, 64.0F);
         return new CoupDeGrace.Shot(orbit(-70.0F + 55.0F * k, 4.5F, 2.0F), at(0.3F, 0.0F, 1.6F), 1, true, 3.0F, 64.0F);
      }

      if (t < 90.0F) {
         float k = ease(t, 64.0F, 90.0F);
         return new CoupDeGrace.Shot(at(-2.7F, -3.3F, 0.4F), at(0.0F, 0.0F, Mth.lerp(k, 2.2F, 5.5F)), 2, true, 4.0F, 72.0F);
      }

      if (t < 102.0F) {
         return new CoupDeGrace.Shot(at(1.2F, -6.4F, 1.4F), at(0.3F, 0.0F, 3.4F), 3, true, 3.0F, 72.0F);
      }

      float k = ease(t, 102.0F, 128.0F);
      return new CoupDeGrace.Shot(at(1.2F, -6.4F, 1.4F).lerp(at(0.6F, -3.0F, 2.2F), k), at(0.4F, 0.0F, 0.5F), 3, false, 2.5F, 62.0F);
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      float fire = Keys.window(t, 10.0F, 36.0F, 3.0F) + Keys.window(t, 90.0F, 102.0F, 2.0F);
      if (fire > 0.0F) {
         for (boolean right : new boolean[]{true, false}) {
            Vector3f hand = this.actorHand(s, right);
            d.sprite(Sprite.FLAME, new Vector3f(hand).add(0.0F, 0.12F, 0.0F), 0.45F * fire, 0.0F, r(16742938), g(16742938), b(16742938), 0.9F, Draw.Blend.GLOW);
            d.glow(hand, 0.6F * fire, r(16765008), g(16765008), b(16765008), 0.6F);
         }
      }

      float alive = ease(t, 32.0F, 35.0F) * (1.0F - ease(t, 112.0F, 116.0F));
      if (alive > 0.0F && t < 116.0F) {
         this.dragon(d, t, age, alive);
      }

      float rings = ease(t, 58.0F, 64.0F) * (1.0F - ease(t, 96.0F, 98.0F));
      if (rings > 0.0F) {
         Vector3f c = s.victim.axis(1.0556251F);

         for (int k = 0; k < 2; k++) {
            List<Vector3f> ring = new ArrayList<>();
            float r = 0.8F + 0.12F * k;

            for (int i = 0; i <= 24; i++) {
               float a = (float) (Math.PI * 2) * i / 24.0F + age * (k == 0 ? 0.15F : -0.12F);
               ring.add(new Vector3f(c).add(r * Mth.cos(a), (k == 0 ? 0.25F : -0.2F) + 0.08F * Mth.sin(a * 2.0F), r * Mth.sin(a)));
            }

            d.ribbon(FxTex.BEAM, ring, 0.2F, 1.0F, age * 0.05F, r(16742938), g(16742938), b(16742938), rings, rings, Draw.Blend.GLOW);
         }
      }

      if (t >= 96.0F && t < 112.0F) {
         float k = (t - 96.0F) / 16.0F;
         Vector3f base = at(0.0F, 0.0F, 0.0F);
         d.beam(base, at(0.0F, 0.0F, 7.0F * out(k * 3.0F, 2.0F)), 1.6F * (1.0F - k), age * 0.2F, r(16742938), g(16742938), b(16742938), 1.0F - k);
         d.beam(base, at(0.0F, 0.0F, 6.0F * out(k * 3.0F, 2.0F)), 0.6F * (1.0F - k), age * 0.3F, r(16765008), g(16765008), b(16765008), 1.0F - k);
         d.decal(FxTex.SHOCK, base, 1.0F + 9.0F * out(k, 2.0F), 0.0F, r(16742938), g(16742938), b(16742938), 0.9F * (1.0F - k), Draw.Blend.GLOW);
         d.glow(at(0.0F, 0.0F, 0.8F), 5.0F * (1.0F - k), 1.0F, 0.8F, 0.5F, 1.0F - k);
      }

      float scorch = ease(t, 96.0F, 99.0F);
      if (scorch > 0.0F) {
         d.decal(FxTex.SCORCH, at(0.0F, 0.0F, 0.0F), 3.6F, 2.0F, 1.0F, 1.0F, 1.0F, 0.95F * scorch, Draw.Blend.SOFT);
      }
   }

   static Matrix4f frame(Vector3f c, Vector3f dir, Vector3f up, Vector3f side) {
      return new Matrix4f(side.x, side.y, side.z, 0.0F, up.x, up.y, up.z, 0.0F, dir.x, dir.y, dir.z, 0.0F, c.x, c.y, c.z, 1.0F);
   }

   static Vector3f[] basis(List<Vector3f> body, int i) {
      Vector3f a = body.get(Math.max(0, i - 1));
      Vector3f b = body.get(Math.min(body.size() - 1, i + 1));
      Vector3f dir = new Vector3f(a).sub(b);
      if (dir.lengthSquared() < 1.0E-6F) {
         dir.set(0.0F, 1.0F, 0.0F);
      }

      dir.normalize();
      Vector3f side = new Vector3f(dir).cross(0.0F, 1.0F, 0.0F);
      if (side.lengthSquared() < 0.001F) {
         side.set(1.0F, 0.0F, 0.0F);
      }

      side.normalize();
      Vector3f up = new Vector3f(side).cross(dir).normalize();
      return new Vector3f[]{dir, up, side};
   }

   static float girth(float s) {
      return s < 0.22F
         ? Mth.lerp(s / 0.22F, 0.22F, 0.3F)
         : (s < 0.6F ? Mth.lerp((s - 0.22F) / 0.38F, 0.3F, 0.17F) : Mth.lerp((s - 0.6F) / 0.4F, 0.17F, 0.03F));
   }

   void dragon(Draw d, float t, float age, float alive) {
      int n = 34;
      List<Vector3f> body = body(t, n);
      int first = 0;

      while (first < n && body.get(first).y <= 0.05F) {
         first++;
      }

      if (first < n - 1) {
         int[] cuts = new int[]{0, 7, 20, n - 1};

         for (int k = 0; k < 3; k++) {
            int from = Math.max(cuts[k], first);
            int to = cuts[k + 1];
            if (to - from >= 1) {
               List<Vector3f> seg = new ArrayList<>(body.subList(from, to + 1));
               float r0 = girth((float)from / (n - 1)) * alive;
               float r1 = girth((float)to / (n - 1)) * alive;
               d.tube(FxTex.SCALES, seg, r0, r1, 10, 0.7F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
            }
         }

         List<Vector3f> above = new ArrayList<>(body.subList(first, n));
         d.ribbon(FxTex.BEAM, above, 1.1F * alive, 1.4F, -age * 0.15F, r(16742938), g(16742938), b(16742938), 0.35F * alive, 0.05F * alive, Draw.Blend.GLOW);

         for (int i = Math.max(first, 2); i < n - 3; i += 2) {
            Vector3f[] f = basis(body, i);
            float s = (float)i / (n - 1);
            float rad = girth(s) * alive;
            Matrix4f m = frame(body.get(i), f[0], f[1], f[2])
               .translate(0.0F, rad + 0.06F, 0.0F)
               .rotateX(-0.6F)
               .scale(0.05F, 0.12F + 0.9F * rad, 0.06F + 0.5F * rad);
            d.box(FxTex.HORN, m, 1.0F, 0.8F, 0.45F, 1.0F, Draw.Blend.SOLID);
            if (i % 4 == 0) {
               Vector3f top = frame(body.get(i), f[0], f[1], f[2]).transformPosition(new Vector3f(0.0F, rad + 0.25F, -0.1F));
               float flicker = 0.8F + 0.2F * Mth.sin(age * 1.3F + i);
               d.sprite(Sprite.FLAME, top, (0.3F + 1.2F * rad) * flicker, age * 0.1F + i, r(16742938), g(16742938), b(16742938), 0.8F, Draw.Blend.GLOW);
            }
         }

         int[] hips = new int[]{6, 6, 17, 17};

         for (int l = 0; l < 4; l++) {
            int i = hips[l];
            if (i >= first) {
               Vector3f[] f = basis(body, i);
               float sign = l % 2 == 0 ? 1.0F : -1.0F;
               float rad = girth((float)i / (n - 1)) * alive;
               float paddle = Mth.sin(age * 0.35F + l * 1.6F);
               Matrix4f m = frame(body.get(i), f[0], f[1], f[2]);
               Vector3f hip = m.transformPosition(new Vector3f(sign * rad * 0.8F, -0.05F, 0.0F));
               Vector3f knee = m.transformPosition(new Vector3f(sign * (rad + 0.2F), -0.3F, 0.12F * paddle));
               Vector3f foot = m.transformPosition(new Vector3f(sign * (rad + 0.22F), -0.5F, 0.18F + 0.1F * paddle));
               d.tube(FxTex.SCALES, List.of(hip, knee, foot), 0.1F * alive, 0.05F * alive, 6, 0.4F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);

               for (int c = -1; c <= 1; c++) {
                  Vector3f tip = m.transformPosition(new Vector3f(sign * (rad + 0.22F) + 0.05F * c, -0.6F, 0.32F + 0.1F * paddle));
                  d.tube(FxTex.HORN, List.of(foot, tip), 0.025F * alive, 0.004F, 4, 0.2F, 1.0F, 0.95F, 0.85F, 1.0F, Draw.Blend.SOLID);
               }
            }
         }

         Vector3f tail = body.get(n - 1);
         d.sprite(Sprite.FLAME, tail, 0.7F * alive, age * 0.2F, r(16765008), g(16765008), b(16765008), 0.9F, Draw.Blend.GLOW);
         if (first <= 0) {
            Vector3f h = body.get(0);
            Vector3f dir = new Vector3f(h).sub(head(t - 0.6F));
            if (dir.lengthSquared() < 1.0E-6F) {
               dir.set(0.0F, 1.0F, 0.0F);
            }

            dir.normalize();
            Vector3f side = new Vector3f(dir).cross(0.0F, 1.0F, 0.0F);
            if (side.lengthSquared() < 0.001F) {
               side.set(1.0F, 0.0F, 0.0F);
            }

            side.normalize();
            Vector3f up = new Vector3f(side).cross(dir).normalize();
            Matrix4f hm = frame(h, dir, up, side).scale(1.25F * alive);
            float roar = 0.5F + 0.5F * Mth.sin(age * 0.5F);
            float open = 0.15F + 0.45F * roar + 0.4F * Keys.window(t, 90.0F, 96.0F, 1.0F);
            d.box(FxTex.SCALES, new Matrix4f(hm).scale(0.5F, 0.4F, 0.55F), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
            d.box(FxTex.SCALES, new Matrix4f(hm).translate(0.0F, 0.18F, 0.1F).scale(0.58F, 0.1F, 0.3F), 0.75F, 0.7F, 0.7F, 1.0F, Draw.Blend.SOLID);
            d.box(FxTex.SCALES, new Matrix4f(hm).translate(0.0F, -0.02F, 0.46F).scale(0.36F, 0.24F, 0.5F), 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
            d.box(FxTex.SCALES, new Matrix4f(hm).translate(0.0F, 0.07F, 0.7F).scale(0.3F, 0.12F, 0.1F), 0.8F, 0.75F, 0.75F, 1.0F, Draw.Blend.SOLID);
            Matrix4f jaw = new Matrix4f(hm).translate(0.0F, -0.15F, 0.1F).rotateX(open);
            d.box(FxTex.SCALES, new Matrix4f(jaw).translate(0.0F, -0.05F, 0.3F).scale(0.32F, 0.09F, 0.62F), 0.8F, 0.75F, 0.75F, 1.0F, Draw.Blend.SOLID);

            for (int k = 0; k < 4; k++) {
               for (float sg = -1.0F; sg <= 1.0F; sg += 2.0F) {
                  d.box(
                     FxTex.HORN,
                     new Matrix4f(hm).translate(sg * 0.14F, -0.16F, 0.3F + 0.12F * k).scale(0.04F, 0.1F, 0.04F),
                     1.0F,
                     1.0F,
                     1.0F,
                     1.0F,
                     Draw.Blend.SOLID
                  );
                  d.box(
                     FxTex.HORN,
                     new Matrix4f(jaw).translate(sg * 0.12F, 0.02F, 0.12F + 0.12F * k).scale(0.035F, 0.09F, 0.035F),
                     1.0F,
                     1.0F,
                     1.0F,
                     1.0F,
                     Draw.Blend.SOLID
                  );
               }
            }

            Vector3f mouth = hm.transformPosition(new Vector3f(0.0F, -0.14F, 0.45F));
            d.glow(mouth, 0.7F * alive, r(16765008), g(16765008), b(16765008), 0.6F + 0.4F * roar);
            d.sprite(
               Sprite.FLAME,
               new Vector3f(mouth).add(new Vector3f(dir).mul(0.35F)),
               (0.4F + 0.5F * roar) * alive,
               age * 0.3F,
               r(16765008),
               g(16765008),
               b(16765008),
               0.9F,
               Draw.Blend.GLOW
            );

            for (float sg = -1.0F; sg <= 1.0F; sg += 2.0F) {
               Vector3f eye = hm.transformPosition(new Vector3f(sg * 0.255F, 0.08F, 0.14F));
               d.box(FxTex.HORN, new Matrix4f(hm).translate(sg * 0.255F, 0.08F, 0.14F).scale(0.02F, 0.07F, 0.13F), 1.0F, 0.95F, 0.3F, 1.0F, Draw.Blend.GLOW);
               d.glow(eye, 0.3F * alive, 1.0F, 0.9F, 0.4F, 0.9F);
               List<Vector3f> horn = new ArrayList<>();

               for (int k = 0; k <= 5; k++) {
                  float s = k / 5.0F;
                  horn.add(hm.transformPosition(new Vector3f(sg * (0.16F + 0.14F * s), 0.18F + 0.32F * s - 0.12F * s * s, -0.05F - 0.6F * s)));
               }

               d.tube(FxTex.HORN, horn, 0.075F * alive, 0.01F, 6, 0.4F, 1.0F, 0.95F, 0.85F, 1.0F, Draw.Blend.SOLID);
               List<Vector3f> whisker = new ArrayList<>();

               for (int k = 0; k <= 8; k++) {
                  float s = k / 8.0F;
                  whisker.add(
                     hm.transformPosition(new Vector3f(sg * (0.18F + 0.45F * s), -0.05F + 0.2F * Mth.sin(age * 0.3F + 3.0F * s + sg), 0.62F - 1.1F * s))
                  );
               }

               d.ribbon(FxTex.BEAM, whisker, 0.07F * alive, 1.0F, age * 0.1F, r(16765008), g(16765008), b(16765008), 0.9F * alive, 0.1F, Draw.Blend.GLOW);
            }

            for (int k = 0; k < 3; k++) {
               Vector3f p = hm.transformPosition(new Vector3f(0.0F, 0.28F, -0.25F - 0.25F * k));
               d.sprite(Sprite.FLAME, p, (0.7F - 0.12F * k) * alive, age * 0.15F + k, r(16742938), g(16742938), b(16742938), 0.85F, Draw.Blend.GLOW);
            }
         }
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 8) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(0.6F), 0.7F, 0.8F);
         fx.ring(Sprite.DUST, s.actor.axis(0.05F), 12, 0.07F, 0.35F, 18, 9075302, true);
      }

      if (t == 16 || t == 24) {
         Vector3f fist = this.actorHand(s, t == 16);
         fx.sound(Sfx.FIRE_WHOOSH, fist, 1.0F, 1.0F + (t == 24 ? 0.1F : 0.0F));
         fx.sound(Sfx.WHOOSH, fist, 0.6F, 1.2F);

         for (int i = 0; i < 14; i++) {
            fx.p(Sprite.FLAME, fist)
               .vel(fx.dir((float)Vfx.random(0.03).x, (float)Vfx.random(0.03).y, -0.18F * (0.5F + Vfx.RANDOM.nextFloat())))
               .size(0.35F, 0.05F)
               .life(10 + Vfx.RANDOM.nextInt(6))
               .color(16765008)
               .fadeTo(14690832)
               .drag(0.85);
         }
      }

      if (t == 32) {
         fx.sound(Sfx.FIRE_ROAR, chest(), 1.0F, 0.9F);
         fx.burst(Sprite.FLAME, chest(), 24, 0.25F, 0.4F, 16, 16742938, false);
         fx.shake(0.4F, 0.2F);
      }

      if (t == 40 || t == 48 || t == 56 || t == 64 || t == 72 || t == 80) {
         fx.sound(Sfx.FIRE_WHOOSH, head(t), 1.0F, 0.85F + 0.05F * (t / 8));
      }

      if (t == 52) {
         fx.sound(Sfx.WHOOSH_HEAVY, s.victim.axis(1.0556251F), 0.8F, 0.8F);
      }

      if (t == 84) {
         fx.sound(Sfx.FIRE_ROAR, head(t), 1.0F, 0.75F);
      }

      if (t == 90) {
         fx.sound(Sfx.WHOOSH_HEAVY, head(t), 1.0F, 0.7F);
      }

      if (t > 32.0F && t < 116.0F) {
         for (int i = 0; i < 2; i++) {
            Vector3f p = head(t - 20.0F * Vfx.RANDOM.nextFloat());
            if (p.y > 0.1F) {
               fx.p(Sprite.EMBER, p)
                  .vel(fx.dir(new Vector3f((float)Vfx.random(0.03).x, -0.01F, (float)Vfx.random(0.03).z)))
                  .gravity(0.004)
                  .size(0.12F, 0.03F)
                  .life(25)
                  .color(16765008)
                  .fadeTo(14690832);
            }
         }
      }

      if (t == 96.0F) {
         Vector3f ground = at(0.0F, 0.0F, 0.2F);
         fx.sound(Sfx.BLAST, ground, 1.0F, 0.85F);
         fx.sound(Sfx.FIRE_ROAR, ground, 1.0F, 1.1F);
         fx.sound(Sfx.IMPACT_HEAVY, ground, 1.0F, 0.9F);
         fx.flash(ground, 0.9F);
         fx.shake(1.0F, 0.8F);
         fx.burst(Sprite.FLAME, ground, 40, 0.45F, 0.6F, 24, 16742938, false);
         fx.burst(Sprite.EMBER, ground, 40, 0.6F, 0.12F, 40, 16765008, false);
         fx.ring(Sprite.FLAME, ground, 36, 0.35F, 0.7F, 18, 14690832, false);
         fx.ring(Sprite.SMOKE_DARK, ground, 24, 0.22F, 1.0F, 40, 2761760, true);
      }

      if (t == 98) {
         fx.sound(Sfx.BODY_FALL, s.victim.axis(1.0556251F));
         fx.sound(Sfx.BONE_CRACK, s.victim.axis(1.0556251F), 0.6F, 0.9F);
      }

      if (t > 96.0F && t < 118.0F) {
         Vector3f p = s.victim.axis(0.2F + 1.5F * Vfx.RANDOM.nextFloat()).add(0.0F, 0.15F, 0.0F);
         fx.p(Sprite.FLAME, p).vel(fx.dir(0.0F, 0.04F, 0.0F)).size(0.4F, 0.1F).life(14).color(16742938).fadeTo(14690832);
         if (t % 6 == 0) {
            fx.sound(Sfx.CRACKLE, p, 0.5F, 0.8F);
         }
      }
   }
}
