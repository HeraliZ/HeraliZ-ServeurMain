package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Vfx {
   private static final List<Vfx.P> LIVE = new ArrayList<>();
   private static final int MAX = 6000;
   static final RandomSource RANDOM = RandomSource.create();

   private Vfx() {
   }

   static Vfx.P spawn(Vec3 at, Sprite sprite) {
      Vfx.P p = new Vfx.P();
      p.x = p.xo = at.x;
      p.y = p.yo = at.y;
      p.z = p.zo = at.z;
      p.sprite = sprite;
      p.roll = RANDOM.nextFloat() * (float) (Math.PI * 2);
      if (LIVE.size() < 6000) {
         LIVE.add(p);
      }

      return p;
   }

   static Vfx.P preset(Vfx.Kind kind, double x, double y, double z, double vx, double vy, double vz) {
      Vec3 at = new Vec3(x, y, z);

      return switch (kind) {
         case GLINT -> spawn(at, Sprite.TWINKLE).vel(vx, vy, vz).drag(0.96).size(0.14F, 0.04F).life(20).color(16774880);
         case SMOKE -> spawn(at, Sprite.SMOKE).vel(vx, vy, vz).size(0.5F, 1.0F).life(40).color(4210752).alpha(0.7F).soft();
         case FLAME -> spawn(at, Sprite.FLAME).vel(vx, vy, vz).size(0.22F, 0.05F).life(14).color(16752704).fadeTo(12595216);
         case SOUL_FLAME -> spawn(at, Sprite.FLAME).vel(vx, vy, vz).size(0.22F, 0.05F).life(16).color(6349055).fadeTo(2121983);
         case SOUL -> spawn(at, Sprite.GLOW).vel(vx, vy, vz).size(0.28F, 0.05F).life(30).color(8450303);
         case CLOUD -> spawn(at, Sprite.SMOKE).vel(vx, vy, vz).size(0.4F, 0.8F).life(24).color(15790320).alpha(0.8F).soft();
         case EMBER -> spawn(at, Sprite.EMBER).vel(vx, vy + 0.08, vz).gravity(0.03).size(0.14F, 0.04F).life(30).color(16742944);
         case DARK_SMOKE -> spawn(at, Sprite.SMOKE_DARK).vel(vx, vy, vz).size(0.6F, 1.4F).life(80).color(5263440).alpha(0.6F).soft();
         case SPARK -> spawn(at, Sprite.SPARK).vel(vx, vy, vz).drag(0.85).size(0.1F, 0.03F).life(6).color(12642559).stretch(2.0F);
         case BOOM -> spawn(at, Sprite.RING).vel(vx, vy, vz).size(0.5F, 3.0F).life(10).color(8446207);
      };
   }

   static Vec3 random(double scale) {
      double u = RANDOM.nextDouble() * 2.0 - 1.0;
      double a = RANDOM.nextDouble() * Math.PI * 2.0;
      double r = Math.sqrt(1.0 - u * u);
      return new Vec3(r * Math.cos(a) * scale, u * scale, r * Math.sin(a) * scale);
   }

   static void tick() {
      Iterator<Vfx.P> it = LIVE.iterator();

      while (it.hasNext()) {
         Vfx.P p = it.next();
         p.xo = p.x;
         p.yo = p.y;
         p.zo = p.z;
         if (++p.age >= p.life) {
            it.remove();
         } else {
            p.vy = p.vy - p.gravity;
            p.vx = p.vx * p.drag;
            p.vy = p.vy * p.drag;
            p.vz = p.vz * p.drag;
            p.x = p.x + p.vx;
            p.y = p.y + p.vy;
            p.z = p.z + p.vz;
            p.roll = p.roll + p.spin;
            if (p.y < p.floor) {
               p.y = p.floor;
               if (p.vy < -0.03 && p.bounce > 0.0F) {
                  p.vy = -p.vy * p.bounce;
                  p.vx *= 0.6;
                  p.vz *= 0.6;
               } else {
                  p.vx = p.vy = p.vz = 0.0;
                  p.spin = 0.0F;
               }
            }
         }
      }
   }

   static void clear() {
      LIVE.clear();
   }

   static void render(RenderLevelStageEvent event) {
      if (!LIVE.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         Camera cam = event.getCamera();
         PoseStack ps = event.getPoseStack();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         draw(new Draw(ps, buffers, new Quaternionf(cam.rotation()), 15728880), event.getPartialTick(), cam.getPosition());
         buffers.endBatch();
      }
   }

   static void draw(Draw d, float pt, Vec3 c) {
      for (Vfx.P p : LIVE) {
         float k = Mth.clamp((p.age + pt) / p.life, 0.0F, 1.0F);
         float a = p.alpha * (p.fadeIn > 0.0F && k < p.fadeIn ? k / p.fadeIn : 1.0F) * (1.0F - smoothOut(k));
         float size = Mth.lerp(k, p.size0, p.size1);
         float r = p.r;
         float g = p.g;
         float b = p.b;
         if (p.r1 >= 0.0F) {
            r = Mth.lerp(k, p.r, p.r1);
            g = Mth.lerp(k, p.g, p.g1);
            b = Mth.lerp(k, p.b, p.b1);
         }

         Vector3f pos = new Vector3f(
            (float)(Mth.lerp(pt, p.xo, p.x) - c.x),
            (float)(Mth.lerp(pt, p.yo, p.y) - c.y),
            (float)(Mth.lerp(pt, p.zo, p.z) - c.z)
         );
         if (p.stretch > 0.0F && p.vx * p.vx + p.vy * p.vy + p.vz * p.vz > 1.0E-6) {
            Vector3f tail = new Vector3f(pos).sub((float)p.vx * p.stretch, (float)p.vy * p.stretch, (float)p.vz * p.stretch);
            Sprite s = p.sprite;
            d.band(FxTex.PARTICLES, s.u0(), s.v0(), s.u1(), s.v1(), false, tail, pos, size, r, g, b, a, p.blend);
         } else {
            d.sprite(p.sprite, pos, size, p.roll + p.spin * pt, r, g, b, a, p.blend);
         }
      }
   }

   static int count() {
      return LIVE.size();
   }

   private static float smoothOut(float k) {
      float x = Mth.clamp((k - 0.6F) / 0.4F, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   enum Kind {
      GLINT,
      SMOKE,
      FLAME,
      SOUL_FLAME,
      SOUL,
      CLOUD,
      EMBER,
      DARK_SMOKE,
      SPARK,
      BOOM;
   }

   static final class P {
      double x;
      double y;
      double z;
      double xo;
      double yo;
      double zo;
      double vx;
      double vy;
      double vz;
      double gravity;
      double drag = 0.98;
      double floor = Double.NEGATIVE_INFINITY;
      float bounce;
      int age;
      int life = 20;
      Sprite sprite = Sprite.GLOW;
      float size0 = 0.2F;
      float size1 = 0.2F;
      float roll;
      float spin;
      float r = 1.0F;
      float g = 1.0F;
      float b = 1.0F;
      float r1 = -1.0F;
      float g1;
      float b1;
      float alpha = 1.0F;
      float fadeIn = 0.0F;
      Draw.Blend blend = Draw.Blend.GLOW;
      float stretch;

      Vfx.P vel(double x, double y, double z) {
         this.vx = x;
         this.vy = y;
         this.vz = z;
         return this;
      }

      Vfx.P vel(Vec3 v) {
         return this.vel(v.x, v.y, v.z);
      }

      Vfx.P gravity(double g) {
         this.gravity = g;
         return this;
      }

      Vfx.P drag(double d) {
         this.drag = d;
         return this;
      }

      Vfx.P floor(double y, float bounce) {
         this.floor = y;
         this.bounce = bounce;
         return this;
      }

      Vfx.P life(int ticks) {
         this.life = Math.max(1, ticks);
         return this;
      }

      Vfx.P sprite(Sprite s) {
         this.sprite = s;
         return this;
      }

      Vfx.P size(float from, float to) {
         this.size0 = from;
         this.size1 = to;
         return this;
      }

      Vfx.P size(float s) {
         return this.size(s, s);
      }

      Vfx.P roll(float roll, float spin) {
         this.roll = roll;
         this.spin = spin;
         return this;
      }

      Vfx.P color(float r, float g, float b) {
         this.r = r;
         this.g = g;
         this.b = b;
         return this;
      }

      Vfx.P color(int rgb) {
         return this.color((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
      }

      Vfx.P fadeTo(int rgb) {
         this.r1 = (rgb >> 16 & 0xFF) / 255.0F;
         this.g1 = (rgb >> 8 & 0xFF) / 255.0F;
         this.b1 = (rgb & 0xFF) / 255.0F;
         return this;
      }

      Vfx.P alpha(float a) {
         this.alpha = a;
         return this;
      }

      Vfx.P fadeIn(float fraction) {
         this.fadeIn = fraction;
         return this;
      }

      Vfx.P soft() {
         this.blend = Draw.Blend.SOFT;
         return this;
      }

      Vfx.P stretch(float ticks) {
         this.stretch = ticks;
         return this;
      }
   }
}
