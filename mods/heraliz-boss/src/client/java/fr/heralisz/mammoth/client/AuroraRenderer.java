package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.SetPieceBlocks;
import fr.heralisz.mammoth.dungeon.SetPieceEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * L'aurore sous la coupole : cinq voiles de lumière qui ondulent en anneau au-dessus de la salle (additif, sans écrire la
 * profondeur, 5 x 48 quads : quasi gratuit). Bleu glacier et violet au repos ; au réveil d'Ymirak, rouge sang, plus vite.
 */
public class AuroraRenderer implements BlockEntityRenderer<SetPieceEntities.AuroraBE> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/aurora.png");
   private static final int SEG = 48;
   private static final float[][] VEILS = {{14F, 3.5F, 0F, 0.9F}, {12F, 5.0F, 1.7F, 1.0F}, {18F, 7.0F, 3.1F, 0.7F}, {9F, 4.0F, 4.4F, 0.8F},
      {20F, 9.0F, 5.6F, 0.6F}};

   @Override
   public void render(SetPieceEntities.AuroraBE be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      if (Minecraft.getInstance().level == null) return;
      boolean alert = be.getBlockState().hasProperty(SetPieceBlocks.ALERT) && be.getBlockState().getValue(SetPieceBlocks.ALERT);
      float time = (Minecraft.getInstance().level.getGameTime() + partial) / 20F * (alert ? 1.8F : 1F);
      VertexConsumer vc = buf.getBuffer(RenderType.eyes(TEX));
      ps.pushPose();
      ps.translate(0.5, 0, 0.5);
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      for (int v = 0; v < VEILS.length; v++) {
         float rad = VEILS[v][0], drop = VEILS[v][1], ph = VEILS[v][2], amp = VEILS[v][3];
         float hgt = 5.5F;
         for (int i = 0; i < SEG; i++) {
            float a0 = i * Mth.TWO_PI / SEG, a1 = (i + 1) * Mth.TWO_PI / SEG;
            float[] p0 = point(rad, a0, time, ph), p1 = point(rad, a1, time, ph);
            float u0 = (float) i / SEG * 3F + time * 0.03F * (v + 1), u1 = (float) (i + 1) / SEG * 3F + time * 0.03F * (v + 1);
            float w0 = 0.35F + 0.65F * Mth.abs(Mth.sin(a0 * 2 + time * 0.6F + ph)), w1 = 0.35F + 0.65F * Mth.abs(Mth.sin(a1 * 2 + time * 0.6F + ph));
            int[] c0 = color(alert, a0 + ph, time, amp * w0), c1 = color(alert, a1 + ph, time, amp * w1);
            float y0 = -drop, y1 = -drop - hgt;
            vert(vc, m, n, p0[0], y0 + p0[1], p0[2], u0, 0, c0);
            vert(vc, m, n, p1[0], y0 + p1[1], p1[2], u1, 0, c1);
            vert(vc, m, n, p1[0], y1 + p1[1] * 1.6F, p1[2], u1, 1, c1);
            vert(vc, m, n, p0[0], y1 + p0[1] * 1.6F, p0[2], u0, 1, c0);
            vert(vc, m, n, p0[0], y1 + p0[1] * 1.6F, p0[2], u0, 1, c0);           // face arrière (vue du dessous comme du dessus)
            vert(vc, m, n, p1[0], y1 + p1[1] * 1.6F, p1[2], u1, 1, c1);
            vert(vc, m, n, p1[0], y0 + p1[1], p1[2], u1, 0, c1);
            vert(vc, m, n, p0[0], y0 + p0[1], p0[2], u0, 0, c0);
         }
      }
      ps.popPose();
   }

   private static float[] point(float rad, float a, float t, float ph) {
      float r = rad + 1.8F * Mth.sin(a * 3 + t * 0.4F + ph) + 0.8F * Mth.sin(a * 7 - t * 0.7F);
      float y = 0.9F * Mth.sin(a * 4 + t * 0.5F + ph * 2);
      return new float[] {Mth.cos(a) * r, y, Mth.sin(a) * r};
   }

   private static int[] color(boolean alert, float a, float t, float k) {
      float s = 0.5F + 0.5F * Mth.sin(a * 2 + t * 0.3F);
      int r, g, b;
      if (alert) {
         r = (int) (255 * k);
         g = (int) ((20 + 50 * s) * k);
         b = (int) ((30 + 40 * (1 - s)) * k);
      } else {
         r = (int) ((60 + 110 * (1 - s)) * k);
         g = (int) ((150 + 60 * s) * k);
         b = (int) (255 * k);
      }
      return new int[] {r, g, b};
   }

   private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int[] c) {
      vc.vertex(m, x, y, z).color(c[0], c[1], c[2], 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
         .normal(n, 0, 1, 0).endVertex();
   }

   @Override
   public boolean shouldRenderOffScreen(SetPieceEntities.AuroraBE be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 96;
   }
}
