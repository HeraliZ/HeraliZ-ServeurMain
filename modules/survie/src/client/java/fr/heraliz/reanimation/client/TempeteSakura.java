package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class TempeteSakura extends Finisher {
   static final TempeteSakura INSTANCE = new TempeteSakura();
   static final float THROW = 5.0F;
   static final float LAND = 10.0F;
   static final float GROW = 12.0F;
   static final float LEGS = 20.0F;
   static final float WAIST = 28.0F;
   static final float ARMS = 36.0F;
   static final float BLOOM = 56.0F;
   static final float DRINK = 80.0F;
   static final float FIST = 92.0F;
   static final float WITHER = 100.0F;
   static final float HOME = 2.3F;
   static final float HEIGHT = 3.3F;
   static final Vector3f BASE = at(-1.05F, 0.0F, 0.0F);
   static final Vector3f BOUND = at(-0.62F, 0.0F, 1.8056251F);
   static final int PINK = 16757711;
   static final int DEEP = 16740256;
   static final int RED = 12848164;
   static final int SWIRL = 90;
   static final float[][] BRANCH = new float[][]{
      {0.66F, 95.0F, 30.0F, 1.7F},
      {0.68F, -95.0F, 30.0F, 1.7F},
      {0.78F, 35.0F, 40.0F, 1.5F},
      {0.8F, -35.0F, 40.0F, 1.5F},
      {0.86F, 155.0F, 38.0F, 1.4F},
      {0.88F, -155.0F, 38.0F, 1.4F},
      {1.0F, 0.0F, 75.0F, 0.9F}
   };
   static final List<List<Vector3f>> BRANCHES = new ArrayList<>();
   static final List<List<Vector3f>> TWIGS = new ArrayList<>();
   static final List<List<Vector3f>> ROOTS = new ArrayList<>();
   static final List<Vector3f> TRUNK = new ArrayList<>();
   static final List<Vector3f> CLUSTER = new ArrayList<>();
   static final List<Float> CLUSTER_R = new ArrayList<>();
   static final List<Integer> CLUSTER_B = new ArrayList<>();
   static final List<Vector3f> FLOWER_DIR = new ArrayList<>();
   static final List<Integer> FLOWER_C = new ArrayList<>();

   private TempeteSakura() {
      super(ExecutionType.TEMPETE_SAKURA);
   }

   @Override
   float endFwd() {
      return 2.3F;
   }

   @Override
   float endSide() {
      return 0.0F;
   }

   @Override
   public float reach() {
      return 1.4F;
   }

   static Vector3f trunk(float u) {
      return new Vector3f(BASE).add(at(-0.12F * Mth.sin((float) Math.PI * u), 0.15F * Mth.sin(5.340708F * u), 3.3F * u));
   }

   static List<Vector3f> limb(Vector3f from, float yaw, float elevation, float len) {
      float y = yaw * (float) (Math.PI / 180.0);
      float e = elevation * (float) (Math.PI / 180.0);
      Vector3f dir = at(Mth.cos(e) * Mth.cos(y), Mth.cos(e) * Mth.sin(y), Mth.sin(e));
      List<Vector3f> pts = new ArrayList<>();

      for (int i = 0; i <= 9; i++) {
         float s = i / 9.0F;
         pts.add(new Vector3f(from).add(new Vector3f(dir).mul(len * s)).add(0.0F, 0.3F * Mth.sin((float) Math.PI * s) - 0.15F * s * s, 0.0F));
      }

      return pts;
   }

   static void cluster(Vector3f c, float r, int branch) {
      CLUSTER.add(c);
      CLUSTER_R.add(r);
      CLUSTER_B.add(branch);
   }

   static List<Vector3f> partial(List<Vector3f> pts, float k) {
      List<Vector3f> out = new ArrayList<>();
      if (!(k <= 0.0F) && pts.size() >= 2) {
         float total = 0.0F;

         for (int i = 1; i < pts.size(); i++) {
            total += pts.get(i).distance((Vector3fc)pts.get(i - 1));
         }

         float left = total * Math.min(1.0F, k);
         out.add(pts.get(0));

         for (int i = 1; i < pts.size(); i++) {
            float seg = pts.get(i).distance((Vector3fc)pts.get(i - 1));
            if (seg >= left) {
               out.add(new Vector3f((Vector3fc)pts.get(i - 1)).lerp((Vector3fc)pts.get(i), seg > 0.0F ? left / seg : 1.0F));
               break;
            }

            out.add(pts.get(i));
            left -= seg;
         }

         return out;
      } else {
         return out;
      }
   }

   static float retreat(float t) {
      return 1.0F - ease(t, 100.0F, 116.0F);
   }

   static float grown(float t) {
      float g = 0.0F;

      for (int i = 0; i < 6; i++) {
         g += ease(t, 12.0F + 8.0F * i, 12.0F + 8.0F * i + 4.0F) / 6.0F;
      }

      return g * retreat(t);
   }

   static float branch(float t, int b) {
      return ease(t, 36.0F + 2.0F * b, 50.0F + b) * (1.0F - ease(t, 98.0F, 106.0F));
   }

   static float bloom(float t, int c) {
      float k = phase(t, 56.0F + 4.0F * (c % 3), 56.0F + 4.0F * (c % 3) + 4.0F);
      return smooth(k) + 0.25F * Mth.sin((float) Math.PI * k);
   }

   static float blood(float t, float y) {
      float from = 80.0F + 4.0F * (y - 2.0F);
      return ease(t, from, from + 6.0F);
   }

   List<Vector3f> binding(Spectacle.Frame s, int which) {
      List<Vector3f> pts = new ArrayList<>();
      CoupDeGrace.Body v = s.victim;
      if (which < 2) {
         float y0 = which == 0 ? 22.0F : 11.0F;
         float y1 = which == 0 ? 13.0F : 5.0F;
         float turns = which == 0 ? 2.6F : 1.5F;
         float rx = which == 0 ? 5.2F : 5.4F;
         float rz = which == 0 ? 3.6F : 3.3F;
         pts.add(trunk(which == 0 ? 0.16F : 0.4F));

         for (int i = 0; i <= 20; i++) {
            float u = i / 20.0F;
            float a = (float) (Math.PI / 2) + turns * (float) Math.PI * u;
            pts.add(v.scene(rx * Mth.cos(a), Mth.lerp(u, y0, y1), rz * Mth.sin(a)));
         }
      } else {
         boolean right = which == 2;
         Vector3f shoulder = v.scene(right ? -5.0F : 5.0F, 1.0F, 0.0F);
         Vector3f hand = this.victimHand(s, right);
         Vector3f back = new Vector3f(v.scene(0.0F, 0.0F, 3.0F)).sub(v.scene(0.0F, 0.0F, 0.0F));
         pts.add(new Vector3f(trunk(0.62F)).add(at(0.0F, right ? 0.12F : -0.12F, 0.0F)));
         pts.add(new Vector3f(shoulder).add(back));
         Vector3f axis = new Vector3f(hand).sub(shoulder).normalize();
         Vector3f n1 = new Vector3f(axis).cross(0.0F, 1.0F, 0.0F);
         if (n1.lengthSquared() < 0.01F) {
            n1.set(1.0F, 0.0F, 0.0F);
         }

         n1.normalize();
         Vector3f n2 = new Vector3f(axis).cross(n1).normalize();

         for (int i = 0; i <= 14; i++) {
            float u = 0.15F + 0.85F * i / 14.0F;
            float a = (float) (Math.PI * 4) * u * (right ? 1.0F : -1.0F);
            pts.add(new Vector3f(shoulder).lerp(hand, u).add(new Vector3f(n1).mul(0.14F * Mth.cos(a))).add(new Vector3f(n2).mul(0.14F * Mth.sin(a))));
         }
      }

      return pts;
   }

   static float bound(float t, int which) {
      float from = which == 0 ? 20.0F : (which == 1 ? 28.0F : 36.0F);
      return t >= 96.0F ? 0.0F : ease(t, from, from + 7.0F);
   }

   @Override
   protected void frames(Spectacle.Frame s, float t, float victimYaw) {
      float back = ease(t, 10.0F, 20.0F);
      s.actorPoint.set(at(Mth.lerp(back, 1.4F, 2.3F), 0.0F, 0.0F));
      s.actorYaw = 180.0F;
      s.lean = -0.1F * Keys.window(t, 56.0F, 80.0F, 4.0F) + 0.2F * Keys.window(t, 92.0F, 104.0F, 2.0F);
      CoupDeGrace.Body v = s.victim;
      if (t < 26.0F) {
         prone(v, t, victimYaw, s.side);
         if (t > 20.0F) {
            v.pos.add(at(-0.25F * ease(t, 22.0F, 26.0F), 0.0F, 0.0F));
         }
      } else if (t < 96.0F) {
         float k = ease(t, 26.0F, 44.0F);
         float fight = t > 44.0F && t < 80.0F ? 3.0F * Keys.jolt(t, 8.0F * (float)Math.floor(t / 8.0F), 4.0F, 2.0F) : 0.0F;
         rise(v, k, s.side, fight, BOUND);
         v.anchor(1.0556251F, at(-0.25F, 0.0F, 0.3F).lerp(BOUND, k));
      } else {
         float u = phase(t, 96.0F, 103.0F);
         float y = Mth.lerp(u * u, BOUND.y, 0.15F) + 0.08F * Mth.sin((float) Math.PI * phase(t, 103.0F, 107.0F));
         v.turn(0.0F, 90.0F * smooth(u), 0.0F, 0.0F).anchor(1.0556251F, at(Mth.lerp(u, -0.62F, -0.1F), 0.0F, y));
      }
   }

   @Override
   void poseActor(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      bend(m, s.lean);
      float hold = Keys.window(t, 0.0F, 6.0F, 1.0F);
      pose(m.rightArm, hold, -2.4F, 0.0F, 0.1F);
      float flick = Keys.window(t, 4.5F, 9.0F, 1.0F);
      pose(m.rightArm, flick, Mth.lerp(phase(t, 4.5F, 6.5F), -2.4F, -1.3F), 0.0F, 0.0F);
      float conduct = Keys.window(t, 10.0F, 56.0F, 3.0F);
      if (conduct > 0.0F) {
         float beat = 0.0F;

         for (int i = 0; i < 6; i++) {
            beat = Math.max(beat, Keys.window(t, 12.0F + 8.0F * i - 1.0F, 12.0F + 8.0F * i + 5.0F, 2.0F));
         }

         pose(m.rightArm, conduct, -1.0F - 1.2F * beat, 0.0F, 0.35F);
         pose(m.leftArm, conduct, -1.0F - 1.2F * beat, 0.0F, -0.35F);
      }

      float bloom = Keys.window(t, 56.0F, 80.0F, 4.0F);
      pose(m.rightArm, bloom, -0.4F + 0.05F * Mth.sin(age * 0.15F), 0.0F, 1.1F);
      pose(m.leftArm, bloom, -0.4F + 0.05F * Mth.sin(age * 0.15F + 1.0F), 0.0F, -1.1F);
      float reach = Keys.window(t, 80.0F, 106.0F, 3.0F);
      float pull = ease(t, 92.0F, 96.0F);
      pose(m.rightArm, reach, Mth.lerp(pull, -1.55F, -1.1F), 0.0F, 0.05F);
      pose(m.leftArm, reach, -0.2F, 0.0F, -0.25F);
      if (reach > 0.5F) {
         m.rightArm.xRot = m.rightArm.xRot + 0.05F * Mth.sin(age * 4.0F) * ease(t, 86.0F, 92.0F);
      }

      Vector3f look = t < 10.0F
         ? new Vector3f(BASE).add(0.0F, 0.3F, 0.0F)
         : (t < 56.0F ? trunk(0.5F * grown(t)) : (t < 80.0F ? trunk(0.9F) : s.victim.axis(1.4F)));
      look(m.head, s.actor.model(look), 0.9F, 1.2F);
   }

   @Override
   void poseVictim(HumanoidModel<?> m, Spectacle.Frame s, float age) {
      float t = s.t;
      stand(m);
      if (t < 30.0F) {
         lyingPose(m);
      }

      if (t >= 20.0F && t < 96.0F) {
         float legs = ease(t, 20.0F, 26.0F);
         pose(m.rightLeg, legs, 0.0F, 0.0F, 0.02F);
         pose(m.leftLeg, legs, 0.0F, 0.0F, -0.02F);
         float claw = Keys.window(t, 26.0F, 40.0F, 3.0F);
         pose(m.rightArm, claw, -1.6F + 0.4F * Mth.sin(age * 0.9F), 0.0F, 0.3F);
         pose(m.leftArm, claw, -1.6F + 0.4F * Mth.sin(age * 0.9F + 2.0F), 0.0F, -0.3F);
         float spread = ease(t, 37.0F, 43.0F);
         pose(m.rightArm, spread, -0.1F, 0.0F, 1.45F);
         pose(m.leftArm, spread, -0.1F, 0.0F, -1.45F);
         pose(m.head, ease(t, 30.0F, 40.0F), -0.25F + 0.1F * Mth.sin(age * 0.3F), 0.15F * Mth.sin(age * 0.2F), 0.0F);
         pose(m.head, ease(t, 80.0F, 92.0F), 0.55F, 0.0F, 0.15F);
      } else if (t >= 96.0F) {
         deadPose(m, 0.5F * s.side);
         float k = 1.0F - ease(t, 100.0F, 105.0F);
         pose(m.rightArm, k, -0.1F, 0.0F, 1.45F);
         pose(m.leftArm, k, -0.1F, 0.0F, -1.45F);
      }
   }

   @Override
   CoupDeGrace.Shot shot(Spectacle.Frame s) {
      float t = s.t;
      if (t < 12.0F) {
         return new CoupDeGrace.Shot(at(2.3F, -3.4F, 1.5F), at(0.0F, 0.0F, 0.7F), 0, true, 3.0F, 60.0F);
      }

      if (t < 36.0F) {
         float k = ease(t, 12.0F, 36.0F);
         return new CoupDeGrace.Shot(at(0.2F, -3.5F, Mth.lerp(k, 0.5F, 1.0F)), at(-0.6F, 0.0F, Mth.lerp(k, 0.8F, 1.7F)), 1, true, 3.0F, 64.0F);
      }

      if (t < 56.0F) {
         float a = -70.0F - 2.6F * (t - 36.0F);
         return new CoupDeGrace.Shot(new Vector3f(at(-0.8F, 0.0F, 2.2F)).add(orbit(a, 5.0F, 0.0F)), at(-0.8F, 0.0F, 2.2F), 2, false, 4.0F, 64.0F);
      }

      if (t < 80.0F) {
         float k = ease(t, 56.0F, 80.0F);
         return new CoupDeGrace.Shot(at(Mth.lerp(k, 1.7F, 1.3F), 2.6F, 0.4F), at(-0.8F, -0.2F, Mth.lerp(k, 2.5F, 2.1F)), 3, true, 3.0F, 74.0F);
      }

      if (t < 92.0F) {
         float k = ease(t, 80.0F, 92.0F);
         return new CoupDeGrace.Shot(at(2.2F, -2.0F + 0.5F * k, 1.9F), new Vector3f(BOUND).add(0.0F, 0.3F + 0.3F * k, 0.0F), 4, true, 3.0F, 62.0F);
      }

      if (t < 96.0F) {
         return new CoupDeGrace.Shot(new Vector3f(s.actor.pos).add(at(2.2F, -1.3F, 2.1F)), at(-0.7F, 0.0F, 2.0F), 5, true, 2.0F, 62.0F);
      }

      float k = ease(t, 96.0F, 128.0F);
      return new CoupDeGrace.Shot(at(3.6F, -3.0F, 2.2F).lerp(at(3.0F, -3.6F, 2.8F), k), at(-0.5F, 0.0F, Mth.lerp(k, 1.8F, 0.8F)), 6, false, 2.5F, 68.0F);
   }

   static Vector3f seed(float t) {
      Vector3f from = at(1.25F, 0.35F, 2.1F);
      float u = phase(t, 5.0F, 10.0F);
      return new Vector3f(from).lerp(BASE, u).add(0.0F, 1.6F * Mth.sin((float) Math.PI * u), 0.0F);
   }

   static Vector3f storm(int i, float t) {
      float u = i * 0.618F % 1.0F;
      float v = i * 0.381F % 1.0F;
      float a = (i * 47.0F + (t - 56.0F) * (5.0F + 3.0F * v)) * (float) (Math.PI / 180.0);
      float burst = out(phase(t, 96.0F, 110.0F), 2.0F);
      float r = 1.0F + 1.6F * u + 6.0F * burst * (0.5F + v);
      float y = 0.3F + 3.8F * ((v + (t - 56.0F) * 0.004F) % 1.0F) + 1.5F * burst * (u - 0.3F);
      return new Vector3f(BASE).add(r * Mth.cos(a), y, r * Mth.sin(a));
   }

   @Override
   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
      float t = s.t;
      if (t < 10.0F) {
         Vector3f p = t < 5.0F ? this.actorHand(s, true) : seed(t);
         d.glow(p, 0.5F, r(16757711), g(16757711), b(16757711), 1.0F);
         d.sprite(Sprite.BLOSSOM, p, 0.18F, age * 0.3F, 1.0F, 0.85F, 0.9F, 1.0F, Draw.Blend.SOFT);
      }

      float ground = ease(t, 10.0F, 13.0F) * retreat(t);
      if (ground > 0.0F) {
         d.decal(FxTex.SCORCH, new Vector3f(BASE).add(0.0F, 0.01F, 0.0F), 2.0F * ground, 0.7F, 0.45F, 0.3F, 0.3F, 0.8F * ground, Draw.Blend.SOFT);
         d.glow(new Vector3f(BASE).add(0.0F, 0.1F, 0.0F), 1.4F * (1.0F - ease(t, 12.0F, 22.0F)) * ground, r(16757711), g(16757711), b(16757711), 0.6F);
      }

      float dry = ease(t, 96.0F, 104.0F);
      float wood = Mth.lerp(dry, 1.0F, 0.45F);
      float roots = ease(t, 10.0F, 18.0F) * retreat(t);

      for (List<Vector3f> root : ROOTS) {
         List<Vector3f> part = partial(root, roots);
         if (part.size() > 1) {
            d.tube(FxTex.BARK, part, 0.11F, 0.03F, 6, 0.5F, wood, wood, wood, 1.0F, Draw.Blend.SOLID);
         }
      }

      float grown = grown(t);
      List<Vector3f> trunk = partial(TRUNK, grown);
      if (trunk.size() > 1) {
         d.tube(FxTex.BARK, trunk, 0.06F + 0.24F * grown, 0.12F, 10, 0.8F, wood, wood, wood, 1.0F, Draw.Blend.SOLID);
      }

      for (int b = 0; b < BRANCH.length; b++) {
         float k = branch(t, b);
         List<Vector3f> main = partial(BRANCHES.get(b), k);
         if (main.size() > 1) {
            d.tube(FxTex.BARK, main, 0.1F, 0.035F, 6, 0.6F, wood, wood, wood, 1.0F, Draw.Blend.SOLID);
         }

         List<Vector3f> twig = partial(TWIGS.get(b), (k - 0.55F) / 0.45F);
         if (twig.size() > 1) {
            d.tube(FxTex.BARK, twig, 0.05F, 0.02F, 5, 0.5F, wood, wood, wood, 1.0F, Draw.Blend.SOLID);
         }
      }

      for (int w = 0; w < 4; w++) {
         float k = bound(t, w);
         if (!(k <= 0.0F)) {
            List<Vector3f> pts = partial(this.binding(s, w), k);
            if (pts.size() > 1) {
               d.tube(FxTex.BARK, pts, 0.08F, 0.035F, 6, 0.5F, 1.0F, 1.0F, 1.0F, 1.0F, Draw.Blend.SOLID);
               float sap = ease(t, 76.0F, 84.0F);
               if (sap > 0.0F) {
                  d.ribbon(FxTex.BEAM, pts, 0.16F, 0.5F, -age * 0.2F, r(12848164), g(12848164), b(12848164), 0.7F * sap, 0.7F * sap, Draw.Blend.GLOW);
               }
            }
         }
      }

      float sap = ease(t, 80.0F, 94.0F) * (1.0F - ease(t, 96.0F, 102.0F));
      if (sap > 0.0F) {
         d.ribbon(FxTex.BEAM, partial(TRUNK, 0.3F + 0.7F * sap), 0.35F, 0.6F, -age * 0.25F, r(12848164), g(12848164), b(12848164), 0.6F, 0.9F, Draw.Blend.GLOW);
      }

      float burst = phase(t, 96.0F, 99.0F);

      for (int c = 0; c < CLUSTER.size(); c++) {
         float open = bloom(t, c);
         if (!(open <= 0.0F) && !(burst >= 1.0F)) {
            Vector3f p = CLUSTER.get(c);
            float red = blood(t, p.y);
            float size = 2.0F * CLUSTER_R.get(c) * open * (1.0F + 0.5F * burst) * (1.0F + 0.03F * Mth.sin(age * 0.2F + c));

            for (int j = 0; j < 3; j++) {
               float a = c * 2.1F + j * 2.09F;
               float sub = j == 0 ? 1.0F : 0.72F;
               Vector3f q = j == 0 ? p : new Vector3f(p).add(0.42F * size * Mth.cos(a), (j == 1 ? 0.12F : -0.1F) * size, 0.42F * size * Mth.sin(a));
               Matrix4f m = new Matrix4f().translate(q).rotateY(a).scale(size * sub * 1.1F, size * sub * 0.78F, size * sub);
               float shade = j == 2 ? 0.88F : 1.0F;
               d.sphere(
                  FxTex.FOLIAGE, m, 10, shade, shade * Mth.lerp(red, 1.0F, 0.32F), shade * Mth.lerp(red, 1.0F, 0.36F), 1.0F - burst, Draw.Blend.SOLID
               );
            }

            if (red > 0.0F) {
               d.glow(p, size * 0.9F, r(12848164), g(12848164), b(12848164), 0.35F * red * (1.0F - burst));
            }
         }
      }

      for (int f = 0; f < FLOWER_DIR.size(); f++) {
         int c = FLOWER_C.get(f);
         float open = bloom(t, c);
         if (!(open <= 0.0F) && !(burst >= 1.0F)) {
            Vector3f cp = CLUSTER.get(c);
            float red = blood(t, cp.y);
            Vector3f p = new Vector3f((Vector3fc)FLOWER_DIR.get(f)).mul(CLUSTER_R.get(c) * open * (1.0F + 0.25F * (f % 3))).add(cp);
            int col = f % 4 == 0 ? 16740256 : 16770799;
            d.sprite(
               Sprite.BLOSSOM,
               p,
               0.26F * open,
               f * 1.7F,
               Mth.lerp(red, r(col), r(12848164)),
               Mth.lerp(red, g(col), g(12848164)),
               Mth.lerp(red, b(col), b(12848164)),
               1.0F - burst,
               Draw.Blend.SOFT
            );
         }
      }

      float storm = ease(t, 62.0F, 70.0F) * (1.0F - ease(t, 104.0F, 114.0F));
      if (storm > 0.0F) {
         float red = ease(t, 84.0F, 96.0F);

         for (int i = 0; i < 90; i++) {
            Vector3f p = storm(i, t);
            int col = i % 3 == 0 ? 16740256 : 16757711;
            d.sprite(
               Sprite.PETAL,
               p,
               0.17F + 0.05F * (i % 3),
               age * 0.2F + i,
               Mth.lerp(red, r(col), r(12848164)),
               Mth.lerp(red, g(col), g(12848164)),
               Mth.lerp(red, b(col), b(12848164)),
               0.95F * storm,
               Draw.Blend.SOFT
            );
         }
      }

      if (t >= 96.0F && t < 108.0F) {
         float k = (t - 96.0F) / 12.0F;
         Vector3f crown = at(-0.8F, 0.0F, 2.8F);
         d.glow(crown, 6.0F * (1.0F - k), 1.0F, 0.3F, 0.4F, 0.8F * (1.0F - k));
         d.sprite(Sprite.RING, crown, 1.0F + 8.0F * out(k, 2.0F), 0.0F, r(12848164), g(12848164), b(12848164), 1.0F - k, Draw.Blend.GLOW);
         d.decal(FxTex.SHOCK, at(0.0F, 0.0F, 0.0F), 1.0F + 8.0F * out(k, 2.0F), 0.0F, r(16740256), g(16740256), b(16740256), 0.8F * (1.0F - k), Draw.Blend.GLOW);
      }

      float carpet = ease(t, 98.0F, 110.0F);
      if (carpet > 0.0F) {
         d.decal(FxTex.SCORCH, at(-0.4F, 0.0F, 0.0F), 3.6F * carpet, 2.0F, 1.0F, 0.25F, 0.3F, 0.5F * carpet, Draw.Blend.SOFT);
      }
   }

   @Override
   void events(Finisher.Fx fx, int t) {
      Spectacle.Frame s = fx.s;
      if (t == 1) {
         fx.sound(Sfx.CHIME, s.actor.axis(1.8F), 0.6F, 1.5F);
      }

      if (t == 5) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.5F), 0.6F, 1.5F);
      }

      if (t > 5.0F && t < 10.0F) {
         fx.p(Sprite.PETAL, seed(t)).vel(fx.dir(0.0F, -0.01F, 0.0F)).size(0.12F).life(20).color(16757711).soft();
      }

      if (t == 10) {
         fx.sound(Sfx.ROCK_HIT, BASE, 0.5F, 1.4F);
         fx.sound(Sfx.CHIME, BASE, 0.8F, 0.7F);
         fx.ring(Sprite.PETAL, new Vector3f(BASE).add(0.0F, 0.1F, 0.0F), 14, 0.08F, 0.16F, 30, 16757711, true);
      }

      for (int i = 0; i < 6; i++) {
         if (t == (int)(12.0F + 8.0F * i)) {
            Vector3f top = trunk(grown(t + 4.0F));
            fx.sound(Sfx.ROCK_RUMBLE, BASE, 0.7F, 1.2F + 0.08F * i);
            fx.sound(Sfx.BONE_CRACK, top, 0.6F, 1.5F + 0.05F * i);
            fx.sound(Sfx.WHOOSH, top, 0.5F, 0.8F + 0.1F * i);
            fx.burst(Sprite.ROCK, new Vector3f(BASE).add(0.0F, 0.1F, 0.0F), 6, 0.12F, 0.1F, 20, 5914672, true);
            fx.burst(Sprite.PETAL, top, 6, 0.06F, 0.14F, 30, 16757711, true);
         }
      }

      for (int w = 0; w < 3; w++) {
         float from = w == 0 ? 20.0F : (w == 1 ? 28.0F : 36.0F);
         if (t == (int)from + 3) {
            Vector3f p = s.victim.axis(w == 0 ? 0.4F : (w == 1 ? 0.9F : 1.4F));
            fx.sound(Sfx.CHAIN, p, 0.6F, 0.6F);
            fx.sound(Sfx.BONE_CRACK, p, 0.7F, 1.3F);
            fx.burst(Sprite.SHARD_B, p, 6, 0.1F, 0.08F, 16, 6965808, true);
         }
      }

      for (int i = 0; i < 3; i++) {
         if (t == (int)(56.0F + 4.0F * i)) {
            fx.sound(Sfx.PETAL_GUST, at(-0.8F, 0.0F, 3.0F), 0.9F, 1.0F + 0.1F * i);
            fx.sound(Sfx.CHIME, at(-0.8F, 0.0F, 3.0F), 0.7F, 1.0F + 0.25F * i);
            fx.sound(Sfx.SPARKLE, at(-0.8F, 0.0F, 3.0F), 0.6F, 1.2F);
         }
      }

      if (t >= 56.0F && t < 96.0F) {
         int c = Vfx.RANDOM.nextInt(CLUSTER.size());
         if (bloom(t, c) > 0.5F) {
            Vector3f p = new Vector3f((Vector3fc)CLUSTER.get(c)).add(Vfx.random(0.4).toVector3f());
            int col = blood(t, CLUSTER.get(c).y) > 0.5F ? 12848164 : 16757711;
            fx.p(Sprite.PETAL, p).vel(fx.dir(0.02F, -0.02F, 0.01F)).size(0.15F).life(60).color(col).soft().roll(0.0F, 0.12F);
         }
      }

      if (t == 80 || t == 88) {
         fx.sound(Sfx.HEARTBEAT, s.victim.axis(1.3F), 0.9F, 0.9F);
         fx.heartbeat(0.6F);
      }

      if (t == 82) {
         fx.sound(Sfx.REVERSE_SWELL, s.victim.axis(1.3F), 0.8F, 0.8F);
      }

      if (t > 80.0F && t < 96.0F && t % 3 == 0) {
         fx.p(Sprite.DROP, s.victim.axis(0.6F + 0.8F * Vfx.RANDOM.nextFloat()))
            .vel(fx.dir(0.0F, -0.04F, 0.0F))
            .gravity(0.02)
            .size(0.08F)
            .life(20)
            .color(9043984);
      }

      if (t == 92) {
         fx.sound(Sfx.WHOOSH, s.actor.axis(1.4F), 0.5F, 0.7F);
      }

      if (t == 96.0F) {
         Vector3f crown = at(-0.8F, 0.0F, 2.8F);
         Vector3f body = s.victim.axis(1.0556251F);
         fx.sound(Sfx.PETAL_GUST, crown, 1.0F, 0.6F);
         fx.sound(Sfx.BLAST, crown, 0.8F, 1.1F);
         fx.sound(Sfx.BONE_CRACK, body, 1.0F, 1.0F);
         fx.sound(Sfx.FLESH, body, 1.0F, 0.8F);
         fx.sound(Sfx.IMPACT_HEAVY, body, 0.8F, 1.1F);
         fx.flash(crown, 0.5F);
         fx.shake(0.8F, 0.6F);

         for (int c = 0; c < CLUSTER.size(); c++) {
            for (int i = 0; i < 8; i++) {
               fx.p(Sprite.PETAL, CLUSTER.get(c))
                  .vel(Vfx.random(0.2 + 0.25 * Vfx.RANDOM.nextDouble()))
                  .drag(0.92)
                  .gravity(0.004)
                  .size(0.2F)
                  .life(50 + Vfx.RANDOM.nextInt(40))
                  .color(i % 3 == 0 ? 9043984 : 12848164)
                  .soft()
                  .roll(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 0.4F);
            }
         }

         fx.burst(Sprite.SHARD_B, body, 14, 0.25F, 0.1F, 24, 6965808, true);
         fx.burst(Sprite.DROP, body, 20, 0.3F, 0.12F, 30, 9043984, true);
      }

      if (t == 103.0F) {
         fx.sound(Sfx.BODY_FALL, at(-0.2F, 0.0F, 0.2F));
      }

      if (t == 100) {
         fx.sound(Sfx.ROCK_RUMBLE, BASE, 0.8F, 0.7F);
      }

      if (t > 96.0F && t < 118.0F && t % 2 == 0) {
         Vector3f p = new Vector3f(BASE).add(orbit(Vfx.RANDOM.nextFloat() * 360.0F, 2.6F * Vfx.RANDOM.nextFloat(), 3.0F + Vfx.RANDOM.nextFloat()));
         fx.p(Sprite.PETAL, p).vel(fx.dir(0.01F, -0.03F, 0.005F)).size(0.17F).life(80).color(12848164).soft().roll(0.0F, 0.1F);
      }
   }

   static {
      for (int i = 0; i <= 16; i++) {
         TRUNK.add(trunk(i / 16.0F));
      }

      for (int r = 0; r < 5; r++) {
         float yaw = (110.0F + 70.0F * r) * (float) (Math.PI / 180.0);
         List<Vector3f> root = new ArrayList<>();

         for (int i = 0; i <= 8; i++) {
            float s = i / 8.0F;
            float len = 1.0F + 0.25F * (r % 2);
            root.add(new Vector3f(BASE).add(at(len * s * Mth.cos(yaw + 0.3F * s), len * s * Mth.sin(yaw + 0.3F * s), 0.18F * (1.0F - s) + 0.03F)));
         }

         ROOTS.add(root);
      }

      Random rnd = new Random(8L);

      for (int b = 0; b < BRANCH.length; b++) {
         float[] def = BRANCH[b];
         List<Vector3f> main = limb(trunk(def[0]), def[1], def[2], def[3]);
         BRANCHES.add(main);
         Vector3f fork = main.get(5);
         List<Vector3f> twig = limb(fork, def[1] + (b % 2 == 0 ? 40.0F : -40.0F), def[2] + 18.0F, def[3] * 0.5F);
         TWIGS.add(twig);
         cluster(main.get(main.size() - 1), 0.62F + 0.12F * (b % 3), b);
         cluster(twig.get(twig.size() - 1), 0.48F, b);
         cluster(new Vector3f((Vector3fc)main.get(6)).add(0.0F, 0.25F, 0.0F), 0.42F, b);
      }

      for (int c = 0; c < CLUSTER.size(); c++) {
         for (int i = 0; i < 18; i++) {
            FLOWER_DIR.add(new Vector3f(rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.35F, rnd.nextFloat() - 0.5F).normalize());
            FLOWER_C.add(c);
         }
      }
   }
}
