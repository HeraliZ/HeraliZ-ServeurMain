package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.IceGateBlock;
import fr.heralisz.mammoth.dungeon.IceGateBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Mur de glace : il monte du sol en 1,5 s quand l'alerte est donnée, et redescend à la fin du combat. */
public class IceGateRenderer implements BlockEntityRenderer<IceGateBlockEntity> {
   private static final int TRAVEL = 30;
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMammoth.MODID, "textures/block/gate_ice.png");

   @Override
   public void render(IceGateBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      BlockState st = be.getBlockState();
      Level l = be.getLevel();
      if (l == null || !(st.getBlock() instanceof IceGateBlock)) return;
      boolean closed = st.getValue(IceGateBlock.CLOSED);
      long now = l.getGameTime();
      if (be.clientChange == Long.MIN_VALUE) {
         be.clientChange = now - TRAVEL;
         be.clientClosed = closed;
      } else if (be.clientClosed != closed) {
         be.clientClosed = closed;
         be.clientChange = now;
      }
      float p = Mth.clamp((now + partial - be.clientChange) / TRAVEL, 0, 1);
      p = p * p * (3 - 2 * p);
      float cover = closed ? p : 1 - p;
      if (cover <= 0.001F) return;
      Direction f = st.getValue(IceGateBlock.FACING);
      Direction side = f.getClockWise();
      int w = be.width, h = be.height;
      int half = w / 2;
      double x0 = -half - 0.5, x1 = w - half - 0.5;
      int lit = LevelRenderer.getLightColor(l, be.getBlockPos().above(Math.min(h - 1, 1)).relative(f.getOpposite()));
      VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));
      Matrix4f m = ps.last().pose();
      Matrix3f nm = ps.last().normal();
      double sx = side.getStepX(), sz = side.getStepZ(), fx = f.getStepX() * 0.3, fz = f.getStepZ() * 0.3;
      double top = h * cover;
      // la texture reste collée au sol : le haut du mur apparaît en premier
      for (int layer = 0; layer < 2; layer++) {
         double off = layer * 0.25;
         quad(vc, m, nm, sx, sz, fx + f.getStepX() * off, fz + f.getStepZ() * off, x0, x1, 0, top, 0, (float) (x1 - x0), (float) (h - top), (float) h, lit,
            layer == 0 ? 225 : 150);
      }
   }

   private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, double sx, double sz, double fx, double fz, double a, double b, double y0, double y1,
                            float u0, float u1, float vTop, float vBottom, int light, int alpha) {
      double cx = 0.5 + fx, cz = 0.5 + fz;
      double ax = cx + sx * a, az = cz + sz * a, bx = cx + sx * b, bz = cz + sz * b;
      for (int side = 0; side < 2; side++) {
         float nx = (float) (side == 0 ? fx : -fx), nz = (float) (side == 0 ? fz : -fz);
         v(vc, m, nm, ax, y0, az, u0 / 2, vBottom / 2, light, nx, nz, alpha);
         v(vc, m, nm, bx, y0, bz, u1 / 2, vBottom / 2, light, nx, nz, alpha);
         v(vc, m, nm, bx, y1, bz, u1 / 2, vTop / 2, light, nx, nz, alpha);
         v(vc, m, nm, ax, y1, az, u0 / 2, vTop / 2, light, nx, nz, alpha);
      }
   }

   private static void v(VertexConsumer vc, Matrix4f m, Matrix3f nm, double x, double y, double z, float u, float v, int light, float nx, float nz, int alpha) {
      vc.vertex(m, (float) x, (float) y, (float) z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(nm, nx, 0, nz).endVertex();
   }

   @Override
   public boolean shouldRenderOffScreen(IceGateBlockEntity be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 96;
   }
}
