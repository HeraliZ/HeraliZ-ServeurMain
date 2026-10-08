package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class FxDraw {
   private static final ResourceLocation SOUL = FxTex.SOUL;

   private FxDraw() {
   }

   static void soul(PoseStack ps, MultiBufferSource buffers, PlayerModel<?> model, float alpha, float age) {
      if (!(alpha <= 0.004F)) {
         model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(SOUL)), 15728880, OverlayTexture.NO_OVERLAY, 0.85F, 0.95F, 1.0F, Math.min(1.0F, alpha));
         ps.pushPose();
         ps.translate(0.0F, 0.6F, 0.0F);
         ps.scale(1.1F, 1.05F, 1.1F);
         ps.translate(0.0F, -0.6F, 0.0F);
         float glow = Math.min(1.0F, alpha) * (0.75F + 0.25F * Mth.sin(age * 0.15F));
         model.renderToBuffer(ps, buffers.getBuffer(FxTypes.glow(FxTex.SWIRL)), 15728880, OverlayTexture.NO_OVERLAY, 0.18F, 0.5F, 0.85F, 0.8F * glow);
         ps.popPose();
      }
   }

   static void beam(
      PoseStack ps,
      MultiBufferSource buffers,
      Vector3f base,
      float height,
      float radius,
      float glow,
      float r,
      float g,
      float b,
      long gameTime,
      float partialTick
   ) {
      if (!(radius <= 0.001F) && !(height <= 0.0F)) {
         float scroll = -((float)(gameTime % 400L) + partialTick) * 0.06F;
         column(ps, buffers, base, height, radius * 1.6F, scroll, r, g, b, 1.0F);
         column(ps, buffers, base, height, radius * 0.6F, scroll * 1.5F, 1.0F, 1.0F, 1.0F, 0.9F);
         column(ps, buffers, base, height, glow * 1.6F, scroll * 0.5F, r, g, b, 0.35F);
      }
   }

   private static void column(
      PoseStack ps, MultiBufferSource buffers, Vector3f base, float height, float radius, float scroll, float r, float g, float b, float a
   ) {
      VertexConsumer vc = buffers.getBuffer(FxTypes.glow(FxTex.BEAM));
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float v1 = scroll + height * 0.08F;

      for (int i = 0; i < 4; i++) {
         float angle = i * (float) Math.PI / 4.0F;
         float dx = Mth.cos(angle) * radius;
         float dz = Mth.sin(angle) * radius;
         vertex(vc, m, n, base.x - dx, base.y, base.z - dz, 0.0F, v1, r, g, b, a);
         vertex(vc, m, n, base.x + dx, base.y, base.z + dz, 1.0F, v1, r, g, b, a);
         vertex(vc, m, n, base.x + dx, base.y + height, base.z + dz, 1.0F, scroll, r, g, b, a);
         vertex(vc, m, n, base.x - dx, base.y + height, base.z - dz, 0.0F, scroll, r, g, b, a);
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
      vc.vertex(m, x, y, z)
         .color(r, g, b, a)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(15728880)
         .normal(n, 0.0F, 1.0F, 0.0F)
         .endVertex();
   }

   static void ring(PoseStack ps, MultiBufferSource buffers, Vector3f center, float radius, float width, float r, float g, float b, float a) {
      if (!(a <= 0.004F) && !(radius <= 0.0F)) {
         VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
         Matrix4f m = ps.last().pose();
         float inner = Math.max(0.0F, radius - width);
         int n = 48;

         for (int i = 0; i < n; i++) {
            float a0 = i * (float) (Math.PI * 2) / n;
            float a1 = (i + 1) * (float) (Math.PI * 2) / n;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(vc, m, center, inner * c0, inner * s0, radius * c0, radius * s0, radius * c1, radius * s1, inner * c1, inner * s1, r, g, b, a);
            quad(vc, m, center, inner * c1, inner * s1, radius * c1, radius * s1, radius * c0, radius * s0, inner * c0, inner * s0, r, g, b, a);
         }
      }
   }

   private static void quad(
      VertexConsumer vc,
      Matrix4f m,
      Vector3f c,
      float x0,
      float z0,
      float x1,
      float z1,
      float x2,
      float z2,
      float x3,
      float z3,
      float r,
      float g,
      float b,
      float a
   ) {
      vc.vertex(m, c.x + x0, c.y, c.z + z0).color(r, g, b, a).endVertex();
      vc.vertex(m, c.x + x1, c.y, c.z + z1).color(r, g, b, 0.0F).endVertex();
      vc.vertex(m, c.x + x2, c.y, c.z + z2).color(r, g, b, 0.0F).endVertex();
      vc.vertex(m, c.x + x3, c.y, c.z + z3).color(r, g, b, a).endVertex();
   }

   static void streak(PoseStack ps, MultiBufferSource buffers, Vector3f bottom, Vector3f top, float width, float r, float g, float b, float a) {
      if (!(a <= 0.004F)) {
         VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
         Matrix4f m = ps.last().pose();
         Vector3f d = new Vector3f(top).sub(bottom);
         Vector3f side1 = new Vector3f(d).cross(0.0F, 1.0F, 0.0F);
         if (side1.lengthSquared() < 1.0E-6F) {
            side1.set(1.0F, 0.0F, 0.0F);
         }

         side1.normalize(width);
         Vector3f side2 = new Vector3f(d).cross(side1).normalize(width);

         for (Vector3f s : new Vector3f[]{side1, side2}) {
            for (int face = 0; face < 2; face++) {
               float k = face == 0 ? 1.0F : -1.0F;
               vc.vertex(m, bottom.x - s.x * k, bottom.y - s.y * k, bottom.z - s.z * k).color(r, g, b, a).endVertex();
               vc.vertex(m, bottom.x + s.x * k, bottom.y + s.y * k, bottom.z + s.z * k).color(r, g, b, a).endVertex();
               vc.vertex(m, top.x + s.x * k, top.y + s.y * k, top.z + s.z * k).color(r, g, b, 0.0F).endVertex();
               vc.vertex(m, top.x - s.x * k, top.y - s.y * k, top.z - s.z * k).color(r, g, b, 0.0F).endVertex();
            }
         }
      }
   }
}
