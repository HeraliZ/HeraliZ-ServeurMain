package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.dungeon.ArenaGateBlock;
import fr.heralisz.n07.dungeon.ArenaGateBlockEntity;
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

/**
 * Grille de l'arène : rideau blindé / rouillé et herse descendent du linteau (la texture suit le mouvement) ; racines et toiles
 * poussent depuis les deux côtés. 1,5 s de mouvement, puis immobile. Ouverte : rien à dessiner.
 */
public class GateRenderer implements BlockEntityRenderer<ArenaGateBlockEntity> {
   private static final int TRAVEL = 30;

   private static ResourceLocation tex(ArenaGateBlock.Style s) {
      return new ResourceLocation(HeraliszN07.MODID, "textures/block/gate_" + s.name().toLowerCase(java.util.Locale.ROOT) + ".png");
   }

   @Override
   public void render(ArenaGateBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      BlockState st = be.getBlockState();
      Level l = be.getLevel();
      if (l == null || !(st.getBlock() instanceof ArenaGateBlock gb)) return;
      boolean closed = st.getValue(ArenaGateBlock.CLOSED);
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
      Direction f = st.getValue(ArenaGateBlock.FACING);
      Direction side = f.getClockWise();
      int w = be.width, h = be.height;
      int half = w / 2;
      double x0 = -half - 0.5, x1 = w - half - 0.5;                  // bords de l'ouverture le long de « side », depuis le centre du bloc
      int lit = LevelRenderer.getLightColor(l, be.getBlockPos().above(Math.min(h - 1, 1)).relative(f.getOpposite()));
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex(gb.style)));
      Matrix4f m = ps.last().pose();
      Matrix3f nm = ps.last().normal();
      double sx = side.getStepX(), sz = side.getStepZ(), fx = f.getStepX() * 0.35, fz = f.getStepZ() * 0.35;
      boolean grow = gb.style == ArenaGateBlock.Style.ROOTS || gb.style == ArenaGateBlock.Style.WEB;
      if (!grow) {
         // descend du linteau : bas du rideau à h * (1 - cover)
         double yb = h * (1 - cover);
         quad(vc, m, nm, sx, sz, fx, fz, x0, x1, yb, h, 0, (float) (x1 - x0), (float) (64 - (h - yb)), 64F, lit);
      } else {
         // pousse depuis les deux côtés jusqu'au milieu
         double mid = (x0 + x1) / 2, reach = (mid - x0) * cover;
         quad(vc, m, nm, sx, sz, fx, fz, x0, x0 + reach, 0, h, 0, (float) reach, 0F, (float) h, lit);
         quad(vc, m, nm, sx, sz, fx, fz, x1 - reach, x1, 0, h, (float) (x1 - x0 - reach), (float) (x1 - x0), 0F, (float) h, lit);
      }
   }

   /** Panneau vertical (deux faces) dans le plan de l'ouverture, de a à b le long de side, de y0 à y1 ; UV en blocs. */
   private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, double sx, double sz, double fx, double fz, double a, double b, double y0, double y1,
                            float u0, float u1, float vTop, float vBottom, int light) {
      double cx = 0.5 + fx, cz = 0.5 + fz;
      double ax = cx + sx * a, az = cz + sz * a, bx = cx + sx * b, bz = cz + sz * b;
      float vt = vTop, vb = vBottom;
      for (int side = 0; side < 2; side++) {
         float nx = (float) (side == 0 ? fx : -fx), nz = (float) (side == 0 ? fz : -fz);
         v(vc, m, nm, ax, y0, az, u0, vb, light, nx, nz);
         v(vc, m, nm, bx, y0, bz, u1, vb, light, nx, nz);
         v(vc, m, nm, bx, y1, bz, u1, vt, light, nx, nz);
         v(vc, m, nm, ax, y1, az, u0, vt, light, nx, nz);
      }
   }

   private static void v(VertexConsumer vc, Matrix4f m, Matrix3f nm, double x, double y, double z, float u, float v, int light, float nx, float nz) {
      vc.vertex(m, (float) x, (float) y, (float) z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(nm, nx, 0, nz).endVertex();
   }

   @Override
   public boolean shouldRenderOffScreen(ArenaGateBlockEntity be) {
      // 1.1.0 : soumis a l occlusion des sections (ne se dessine plus a travers la roche, ex. depuis la carte au-dessus)
      return false;
   }

   @Override
   public int getViewDistance() {
      return 64;
   }
}
