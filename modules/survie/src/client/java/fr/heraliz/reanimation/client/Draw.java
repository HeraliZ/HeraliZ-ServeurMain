package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Random;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class Draw {
   static final int FULL_BRIGHT = 15728880;
   final PoseStack ps;
   final MultiBufferSource buffers;
   final Quaternionf facing;
   final int light;

   Draw(PoseStack ps, MultiBufferSource buffers, Quaternionf facing, int light) {
      this.ps = ps;
      this.buffers = buffers;
      this.facing = facing;
      this.light = light;
   }

   VertexConsumer vc(ResourceLocation tex, Draw.Blend blend) {
      if (this.buffers instanceof Draw.Sink sink) {
         return sink.buffer(tex, blend);
      } else {
         RenderType type = switch (blend) {
            case GLOW -> FxTypes.glow(tex);
            case SOFT -> FxTypes.soft(tex);
            case SOLID -> RenderType.entityCutoutNoCull(tex);
            case CLEAR -> RenderType.entityTranslucent(tex);
         };
         return this.buffers.getBuffer(type);
      }
   }

   private int lightFor(Draw.Blend blend) {
      return blend != Draw.Blend.GLOW && blend != Draw.Blend.SOFT ? this.light : 15728880;
   }

   private static void vertex(
      VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float u, float v, float r, float g, float b, float a, int light, Vector3f normal
   ) {
      vc.vertex(m, p.x, p.y, p.z)
         .color(r, g, b, a)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(n, normal.x, normal.y, normal.z)
         .endVertex();
   }

   void quad(
      ResourceLocation tex,
      Draw.Blend blend,
      Vector3f p0,
      Vector3f p1,
      Vector3f p2,
      Vector3f p3,
      float u0,
      float v0,
      float u1,
      float v1,
      float r,
      float g,
      float b,
      float a
   ) {
      VertexConsumer vc = this.vc(tex, blend);
      Matrix4f m = this.ps.last().pose();
      Matrix3f n = this.ps.last().normal();
      Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0));
      if (normal.lengthSquared() < 1.0E-12F) {
         normal.set(0.0F, 1.0F, 0.0F);
      } else {
         normal.normalize();
      }

      int l = this.lightFor(blend);
      vertex(vc, m, n, p0, u0, v0, r, g, b, a, l, normal);
      vertex(vc, m, n, p1, u0, v1, r, g, b, a, l, normal);
      vertex(vc, m, n, p2, u1, v1, r, g, b, a, l, normal);
      vertex(vc, m, n, p3, u1, v0, r, g, b, a, l, normal);
   }

   void sprite(Sprite s, Vector3f c, float size, float roll, float r, float g, float b, float a, Draw.Blend blend) {
      if (!(a <= 0.003F) && !(size <= 0.0F)) {
         Quaternionf q = new Quaternionf(this.facing).rotateZ(roll);
         float h = size / 2.0F;
         Vector3f p0 = q.transform(new Vector3f(-h, -h, 0.0F)).add(c);
         Vector3f p1 = q.transform(new Vector3f(-h, h, 0.0F)).add(c);
         Vector3f p2 = q.transform(new Vector3f(h, h, 0.0F)).add(c);
         Vector3f p3 = q.transform(new Vector3f(h, -h, 0.0F)).add(c);
         this.quad(FxTex.PARTICLES, blend, p0, p1, p2, p3, s.u0(), s.v1(), s.u1(), s.v0(), r, g, b, a);
      }
   }

   void glow(Vector3f c, float size, float r, float g, float b, float a) {
      this.sprite(Sprite.GLOW, c, size, 0.0F, r, g, b, a, Draw.Blend.GLOW);
   }

   private Vector3f side(Vector3f dir, float halfWidth) {
      Vector3f toCam = this.facing.transform(new Vector3f(0.0F, 0.0F, 1.0F));
      Vector3f s = new Vector3f(dir).cross(toCam);
      if (s.lengthSquared() < 1.0E-8F) {
         s = this.facing.transform(new Vector3f(1.0F, 0.0F, 0.0F));
      }

      return s.normalize(halfWidth);
   }

   void band(
      ResourceLocation tex,
      float u0,
      float v0,
      float u1,
      float v1,
      boolean vAlong,
      Vector3f a,
      Vector3f b,
      float width,
      float r,
      float g,
      float bl,
      float al,
      Draw.Blend blend
   ) {
      if (!(al <= 0.003F)) {
         Vector3f dir = new Vector3f(b).sub(a);
         if (!(dir.lengthSquared() < 1.0E-10F)) {
            Vector3f s = this.side(dir, width / 2.0F);
            Vector3f p0 = new Vector3f(a).sub(s);
            Vector3f p1 = new Vector3f(a).add(s);
            Vector3f p2 = new Vector3f(b).add(s);
            Vector3f p3 = new Vector3f(b).sub(s);
            if (vAlong) {
               this.quadUV(tex, blend, p0, p1, p2, p3, u0, v0, u1, v0, u1, v1, u0, v1, r, g, bl, al);
            } else {
               this.quadUV(tex, blend, p0, p1, p2, p3, u0, v1, u0, v0, u1, v0, u1, v1, r, g, bl, al);
            }
         }
      }
   }

   private void quadUV(
      ResourceLocation tex,
      Draw.Blend blend,
      Vector3f p0,
      Vector3f p1,
      Vector3f p2,
      Vector3f p3,
      float a0,
      float b0,
      float a1,
      float b1,
      float a2,
      float b2,
      float a3,
      float b3,
      float r,
      float g,
      float bl,
      float al
   ) {
      VertexConsumer vc = this.vc(tex, blend);
      Matrix4f m = this.ps.last().pose();
      Matrix3f n = this.ps.last().normal();
      Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0));
      normal = normal.lengthSquared() < 1.0E-12F ? new Vector3f(0.0F, 1.0F, 0.0F) : normal.normalize();
      int l = this.lightFor(blend);
      vertex(vc, m, n, p0, a0, b0, r, g, bl, al, l, normal);
      vertex(vc, m, n, p1, a1, b1, r, g, bl, al, l, normal);
      vertex(vc, m, n, p2, a2, b2, r, g, bl, al, l, normal);
      vertex(vc, m, n, p3, a3, b3, r, g, bl, al, l, normal);
   }

   void streak(Vector3f a, Vector3f b, float width, float r, float g, float bl, float al) {
      Sprite s = Sprite.STREAK;
      this.band(FxTex.PARTICLES, s.u0(), s.v0(), s.u1(), s.v1(), false, a, b, width, r, g, bl, al, Draw.Blend.GLOW);
   }

   void beam(Vector3f a, Vector3f b, float width, float scroll, float r, float g, float bl, float al) {
      float len = a.distance(b);
      this.band(FxTex.BEAM, 0.0F, scroll, 1.0F, scroll + len / Math.max(width, 0.05F) * 0.25F, true, a, b, width, r, g, bl, al, Draw.Blend.GLOW);
   }

   void ribbon(
      ResourceLocation tex,
      List<Vector3f> pts,
      float width,
      float tile,
      float scroll,
      float r,
      float g,
      float b,
      float headAlpha,
      float tailAlpha,
      Draw.Blend blend
   ) {
      if (pts.size() >= 2) {
         VertexConsumer vc = this.vc(tex, blend);
         Matrix4f m = this.ps.last().pose();
         Matrix3f n = this.ps.last().normal();
         int l = this.lightFor(blend);
         Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
         float along = scroll;

         for (int i = 0; i < pts.size() - 1; i++) {
            Vector3f a = pts.get(i);
            Vector3f bp = pts.get(i + 1);
            Vector3f dirA = new Vector3f((Vector3fc)pts.get(Math.min(i + 1, pts.size() - 1))).sub((Vector3fc)pts.get(Math.max(i - 1, 0)));
            Vector3f dirB = new Vector3f((Vector3fc)pts.get(Math.min(i + 2, pts.size() - 1))).sub((Vector3fc)pts.get(i));
            Vector3f sa = this.side(dirA, width / 2.0F);
            Vector3f sb = this.side(dirB, width / 2.0F);
            float seg = a.distance(bp) / Math.max(tile, 0.001F);
            float ka = (float)i / (pts.size() - 1);
            float kb = (float)(i + 1) / (pts.size() - 1);
            float aa = Mth.lerp(ka, tailAlpha, headAlpha);
            float ab = Mth.lerp(kb, tailAlpha, headAlpha);
            vertex(vc, m, n, new Vector3f(a).sub(sa), 0.0F, along, r, g, b, aa, l, up);
            vertex(vc, m, n, new Vector3f(a).add(sa), 1.0F, along, r, g, b, aa, l, up);
            vertex(vc, m, n, new Vector3f(bp).add(sb), 1.0F, along + seg, r, g, b, ab, l, up);
            vertex(vc, m, n, new Vector3f(bp).sub(sb), 0.0F, along + seg, r, g, b, ab, l, up);
            along += seg;
         }
      }
   }

   void bolt(Vector3f a, Vector3f b, long seed, float jag, float width, float r, float g, float bl, float al) {
      Random rnd = new Random(seed);
      int n = 12;
      Vector3f dir = new Vector3f(b).sub(a);
      Vector3f p1 = Math.abs(dir.y) < 0.9F * dir.length() ? new Vector3f(dir).cross(0.0F, 1.0F, 0.0F) : new Vector3f(dir).cross(1.0F, 0.0F, 0.0F);
      p1.normalize();
      Vector3f p2 = new Vector3f(dir).cross(p1).normalize();
      Vector3f prev = new Vector3f(a);

      for (int i = 1; i <= n; i++) {
         float k = (float)i / n;
         float amp = i == n ? 0.0F : jag * Mth.sin((float) Math.PI * k);
         Vector3f p = new Vector3f(a)
            .lerp(b, k)
            .add(new Vector3f(p1).mul((rnd.nextFloat() - 0.5F) * 2.0F * amp))
            .add(new Vector3f(p2).mul((rnd.nextFloat() - 0.5F) * 2.0F * amp));
         this.streak(prev, p, width * 3.5F, r, g, bl, al * 0.5F);
         this.streak(prev, p, width, 1.0F, 1.0F, 1.0F, al);
         if (rnd.nextFloat() < 0.25F && i < n - 1) {
            Vector3f fork = new Vector3f(p)
               .add((rnd.nextFloat() - 0.5F) * jag * 2.0F, -Math.abs(rnd.nextFloat()) * jag * 1.5F, (rnd.nextFloat() - 0.5F) * jag * 2.0F);
            this.streak(p, fork, width * 0.8F, r, g, bl, al * 0.6F);
         }

         prev = p;
      }
   }

   void decal(ResourceLocation tex, Vector3f c, float size, float yaw, float r, float g, float b, float a, Draw.Blend blend) {
      if (!(a <= 0.003F)) {
         float h = size / 2.0F;
         float cs = Mth.cos(yaw);
         float sn = Mth.sin(yaw);
         Vector3f p0 = new Vector3f(c.x + (-h * cs - -h * sn), c.y + 0.012F, c.z + (-h * sn + -h * cs));
         Vector3f p1 = new Vector3f(c.x + (-h * cs - h * sn), c.y + 0.012F, c.z + (-h * sn + h * cs));
         Vector3f p2 = new Vector3f(c.x + (h * cs - h * sn), c.y + 0.012F, c.z + (h * sn + h * cs));
         Vector3f p3 = new Vector3f(c.x + (h * cs - -h * sn), c.y + 0.012F, c.z + (h * sn + -h * cs));
         this.quad(tex, blend, p0, p1, p2, p3, 0.0F, 0.0F, 1.0F, 1.0F, r, g, b, a);
      }
   }

   void plane(ResourceLocation tex, Matrix4f m, float u0, float v0, float u1, float v1, float r, float g, float b, float a, Draw.Blend blend) {
      Vector3f p0 = m.transformPosition(new Vector3f(-0.5F, -0.5F, 0.0F));
      Vector3f p1 = m.transformPosition(new Vector3f(-0.5F, 0.5F, 0.0F));
      Vector3f p2 = m.transformPosition(new Vector3f(0.5F, 0.5F, 0.0F));
      Vector3f p3 = m.transformPosition(new Vector3f(0.5F, -0.5F, 0.0F));
      this.quad(tex, blend, p0, p1, p2, p3, u0, v1, u1, v0, r, g, b, a);
   }

   void box(ResourceLocation tex, Matrix4f m, float r, float g, float b, float a, Draw.Blend blend) {
      float[][] faces = new float[][]{
         {-1.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, 0.0F, -1.0F}, {0.0F, 0.0F, 1.0F}
      };

      for (float[] f : faces) {
         Vector3f nrm = new Vector3f(f[0], f[1], f[2]);
         Vector3f t1 = Math.abs(nrm.y) > 0.5F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
         Vector3f t2 = new Vector3f(nrm).cross(t1);
         Vector3f c = new Vector3f(nrm).mul(0.5F);
         Vector3f p0 = m.transformPosition(new Vector3f(c).sub(new Vector3f(t1).mul(0.5F)).sub(new Vector3f(t2).mul(0.5F)));
         Vector3f p1 = m.transformPosition(new Vector3f(c).sub(new Vector3f(t1).mul(0.5F)).add(new Vector3f(t2).mul(0.5F)));
         Vector3f p2 = m.transformPosition(new Vector3f(c).add(new Vector3f(t1).mul(0.5F)).add(new Vector3f(t2).mul(0.5F)));
         Vector3f p3 = m.transformPosition(new Vector3f(c).add(new Vector3f(t1).mul(0.5F)).sub(new Vector3f(t2).mul(0.5F)));
         this.quad(tex, blend, p0, p1, p2, p3, 0.0F, 0.0F, 1.0F, 1.0F, r, g, b, a);
      }
   }

   void cylinder(ResourceLocation tex, Matrix4f m, int seg, float vRepeat, float r, float g, float b, float a, Draw.Blend blend, boolean caps) {
      for (int i = 0; i < seg; i++) {
         float a0 = (float) (Math.PI * 2) * i / seg;
         float a1 = (float) (Math.PI * 2) * (i + 1) / seg;
         Vector3f p0 = m.transformPosition(new Vector3f(0.5F * Mth.cos(a0), 0.0F, 0.5F * Mth.sin(a0)));
         Vector3f p1 = m.transformPosition(new Vector3f(0.5F * Mth.cos(a0), 1.0F, 0.5F * Mth.sin(a0)));
         Vector3f p2 = m.transformPosition(new Vector3f(0.5F * Mth.cos(a1), 1.0F, 0.5F * Mth.sin(a1)));
         Vector3f p3 = m.transformPosition(new Vector3f(0.5F * Mth.cos(a1), 0.0F, 0.5F * Mth.sin(a1)));
         this.quadUV(
            tex, blend, p0, p1, p2, p3, (float)i / seg, vRepeat, (float)i / seg, 0.0F, (float)(i + 1) / seg, 0.0F, (float)(i + 1) / seg, vRepeat, r, g, b, a
         );
         if (caps) {
            Vector3f c0 = m.transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
            Vector3f c1 = m.transformPosition(new Vector3f(0.0F, 1.0F, 0.0F));
            this.quad(tex, blend, c0, p0, p3, c0, 0.5F, 0.5F, 0.6F, 0.6F, r, g, b, a);
            this.quad(tex, blend, c1, p2, p1, c1, 0.5F, 0.5F, 0.6F, 0.6F, r, g, b, a);
         }
      }
   }

   void sphere(ResourceLocation tex, Matrix4f m, int seg, float r, float g, float b, float a, Draw.Blend blend) {
      int rings = Math.max(3, seg / 2);

      for (int j = 0; j < rings; j++) {
         float t0 = (float) Math.PI * j / rings;
         float t1 = (float) Math.PI * (j + 1) / rings;

         for (int i = 0; i < seg; i++) {
            float a0 = (float) (Math.PI * 2) * i / seg;
            float a1 = (float) (Math.PI * 2) * (i + 1) / seg;
            Vector3f p0 = m.transformPosition(spherePoint(t0, a0));
            Vector3f p1 = m.transformPosition(spherePoint(t1, a0));
            Vector3f p2 = m.transformPosition(spherePoint(t1, a1));
            Vector3f p3 = m.transformPosition(spherePoint(t0, a1));
            float u0 = (float)i / seg;
            float u1 = (float)(i + 1) / seg;
            float v0 = (float)j / rings;
            float v1 = (float)(j + 1) / rings;
            this.quadUV(tex, blend, p0, p1, p2, p3, u0, v0, u0, v1, u1, v1, u1, v0, r, g, b, a);
         }
      }
   }

   private static Vector3f spherePoint(float theta, float phi) {
      return new Vector3f(0.5F * Mth.sin(theta) * Mth.cos(phi), 0.5F * Mth.cos(theta), 0.5F * Mth.sin(theta) * Mth.sin(phi));
   }

   void tube(ResourceLocation tex, List<Vector3f> pts, float radius, int seg, float tile, float r, float g, float b, float a, Draw.Blend blend) {
      this.tube(tex, pts, radius, radius, seg, tile, r, g, b, a, blend);
   }

   void tube(ResourceLocation tex, List<Vector3f> pts, float r0, float r1, int seg, float tile, float r, float g, float b, float a, Draw.Blend blend) {
      float along = 0.0F;
      Vector3f prevN = null;
      Vector3f[] ring0 = null;

      for (int i = 0; i < pts.size(); i++) {
         Vector3f tan = new Vector3f((Vector3fc)pts.get(Math.min(i + 1, pts.size() - 1))).sub((Vector3fc)pts.get(Math.max(i - 1, 0))).normalize();
         Vector3f nrm = prevN == null
            ? (Math.abs(tan.y) < 0.9F ? new Vector3f(tan).cross(0.0F, 1.0F, 0.0F) : new Vector3f(tan).cross(1.0F, 0.0F, 0.0F)).normalize()
            : new Vector3f(prevN).sub(new Vector3f(tan).mul(prevN.dot(tan))).normalize();
         Vector3f bin = new Vector3f(tan).cross(nrm).normalize();
         Vector3f[] ring = new Vector3f[seg + 1];

         for (int k = 0; k <= seg; k++) {
            float ang = (float) (Math.PI * 2) * k / seg;
            float radius = Mth.lerp(pts.size() > 1 ? (float)i / (pts.size() - 1) : 0.0F, r0, r1);
            ring[k] = new Vector3f((Vector3fc)pts.get(i))
               .add(new Vector3f(nrm).mul(radius * Mth.cos(ang)))
               .add(new Vector3f(bin).mul(radius * Mth.sin(ang)));
         }

         if (ring0 != null) {
            float seglen = pts.get(i).distance((Vector3fc)pts.get(i - 1)) / tile;

            for (int k = 0; k < seg; k++) {
               float u0 = (float)k / seg;
               float u1 = (float)(k + 1) / seg;
               this.quadUV(tex, blend, ring0[k], ring[k], ring[k + 1], ring0[k + 1], u0, along, u0, along + seglen, u1, along + seglen, u1, along, r, g, b, a);
            }

            along += seglen;
         }

         ring0 = ring;
         prevN = nrm;
      }
   }

   void model(PlayerModel<?> model, ResourceLocation tex, CoupDeGrace.Body body, float scale, float r, float g, float b, float a, Draw.Blend blend) {
      this.model(model, tex, body, scale, scale, r, g, b, a, blend);
   }

   void model(
      PlayerModel<?> model, ResourceLocation tex, CoupDeGrace.Body body, float width, float length, float r, float g, float b, float a, Draw.Blend blend
   ) {
      if (!(a <= 0.003F)) {
         Spectacle.copyLayers(model);
         this.ps.pushPose();
         this.ps.translate(body.pos.x, body.pos.y, body.pos.z);
         this.ps.mulPose(body.rot);
         if (width != 1.0F || length != 1.0F) {
            this.ps.translate(0.0F, 1.0556251F, 0.0F);
            this.ps.scale(width, length, width);
            this.ps.translate(0.0F, -1.0556251F, 0.0F);
         }

         this.ps.scale(-1.0F, -1.0F, 1.0F);
         this.ps.scale(0.9375F, 0.9375F, 0.9375F);
         this.ps.translate(0.0F, -1.501F, 0.0F);
         model.renderToBuffer(this.ps, this.vc(tex, blend), this.lightFor(blend), OverlayTexture.NO_OVERLAY, r, g, b, a);
         this.ps.popPose();
      }
   }

   enum Blend {
      GLOW,
      SOFT,
      SOLID,
      CLEAR;
   }

   interface Sink {
      VertexConsumer buffer(ResourceLocation var1, Draw.Blend var2);
   }
}
