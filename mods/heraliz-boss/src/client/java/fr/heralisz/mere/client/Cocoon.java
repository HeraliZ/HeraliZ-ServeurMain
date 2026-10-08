package fr.heralisz.mere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mere.HeraliszMere;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Cocon de soie tissé autour d'un corps : une enveloppe ARRONDIE (fuseau bosselé, pointu aux deux bouts) texturée de fils
 * enroulés, qui se referme d'un bout à l'autre le long de l'axe long (grow 0 → 1), le bord ouvert frangé de fils lâches.
 * Dessiné dans l'espace d'un os (capturé au rendu) ; centre et rayons en pixels du modèle (rz = demi-longueur, axe long).
 */
public final class Cocoon {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMere.MODID, "textures/entity/cocoon_silk.png");
   private static final int RINGS = 26, SEGS = 22;

   private Cocoon() {}

   private static float radius(float u, int i, int j) {
      // fuseau : plein au milieu, pointu aux bouts ; bosses de soie irrégulières
      float r = (float) Math.pow(Math.max(0, 1 - u * u), 0.62);
      float h = Mth.sin(i * 1.7F + j * 2.3F) * 0.5F + Mth.sin(i * 0.9F - j * 1.3F) * 0.5F;
      return r * (1 + 0.045F * h);
   }

   public static void draw(PoseStack ps, MultiBufferSource buf, Matrix4f pose, Matrix3f normal, float cx, float cy, float cz, float rx, float ry, float rz,
                           float grow, int light) {
      if (grow <= 0.001F || pose == null || normal == null) return;
      ps.pushPose();
      ps.last().pose().set(pose);
      ps.last().normal().set(normal);
      Matrix4f m = ps.last().pose();
      Matrix3f nm = ps.last().normal();
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
      float uEnd = -1 + 2 * Mth.clamp(grow, 0, 1);
      for (int i = 0; i < RINGS; i++) {
         float u0 = -1 + 2F * i / RINGS, u1 = -1 + 2F * (i + 1) / RINGS;
         if (u0 >= uEnd) break;
         u1 = Math.min(u1, uEnd);
         for (int j = 0; j < SEGS; j++) {
            float a0 = j * Mth.TWO_PI / SEGS, a1 = (j + 1) * Mth.TWO_PI / SEGS;
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, u0, a0, radius(u0, i, j), j / (float) SEGS * 3, (u0 + 1) * 2.4F, light);
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, u0, a1, radius(u0, i, j + 1), (j + 1) / (float) SEGS * 3, (u0 + 1) * 2.4F, light);
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, u1, a1, radius(u1, i + 1, j + 1), (j + 1) / (float) SEGS * 3, (u1 + 1) * 2.4F, light);
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, u1, a0, radius(u1, i + 1, j), j / (float) SEGS * 3, (u1 + 1) * 2.4F, light);
         }
      }
      // bord ouvert : fils lâches qui pendent et tournent autour du corps pendant le tissage
      if (grow < 0.999F) {
         float re = radius(uEnd, RINGS, 0);
         for (int j = 0; j < 9; j++) {
            float a = j * Mth.TWO_PI / 9 + grow * 9F;
            float len = 0.18F + 0.1F * Mth.sin(j * 2.1F);
            float ub = Math.min(1, uEnd + len);
            float w = 0.05F;
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, uEnd, a - w, re * 1.02F, 0, 0, light);
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, uEnd, a + w, re * 1.02F, 0.1F, 0, light);
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, ub, a + w + 0.3F, radius(ub, 0, j) * 1.06F + 0.05F, 0.1F, 1, light);
            vert(vc, m, nm, cx, cy, cz, rx, ry, rz, ub, a - w + 0.3F, radius(ub, 0, j) * 1.06F + 0.05F, 0, 1, light);
         }
      }
      ps.popPose();
   }

   private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f nm, float cx, float cy, float cz, float rx, float ry, float rz, float u, float a,
                            float r, float tu, float tv, int light) {
      float ca = Mth.cos(a), sa = Mth.sin(a);
      float x = cx + rx * r * ca, y = cy + ry * r * sa, z = cz + rz * u;
      // normale de l'ellipsoïde (approchée)
      float nx = ca / rx, ny = sa / ry, nz = u / Math.max(1e-3F, rz) * 0.8F;
      float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
      vc.vertex(m, x / 16F, y / 16F, z / 16F).color(255, 255, 255, 255).uv(tu, tv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(nm, nx / l, ny / l, nz / l).endVertex();
   }
}
