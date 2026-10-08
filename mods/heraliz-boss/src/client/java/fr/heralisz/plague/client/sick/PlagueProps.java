package fr.heralisz.plague.client.sick;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.client.fvfx.VfxDraw;
import fr.heralisz.plague.client.fvfx.VfxRenderTypes;
import fr.heralisz.plague.entity.PlagueGlob;
import fr.heralisz.plague.entity.PlagueSwarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Rendus des projectiles du Porteur de Peste : grenade à gaz, pustule, essaim de mouches. */
public final class PlagueProps {
   private PlagueProps() {}

   private static void box(PoseStack ps, MultiBufferSource buf, BlockState st, double x, double y, double z, float sx, float sy, float sz, int light) {
      ps.pushPose();
      ps.translate(x, y, z);
      ps.scale(sx, sy, sz);
      ps.translate(-0.5, -0.5, -0.5);
      Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   /** Grenade à gaz (cylindre kaki, bouchon de fer, bande verte) qui tournoie ; pustule qui palpite. */
   public static class Glob extends EntityRenderer<PlagueGlob> {
      public Glob(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public ResourceLocation getTextureLocation(PlagueGlob e) {
         return TextureAtlas.LOCATION_BLOCKS;
      }

      @Override
      public void render(PlagueGlob e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float t = e.tickCount + partial;
         ps.pushPose();
         ps.translate(0, 0.3, 0);
         if (e.kind() == PlagueGlob.CANISTER) {
            ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 53 % 360));
            ps.mulPose(Axis.XP.rotationDegrees(t * 28));
            box(ps, buf, Blocks.GREEN_TERRACOTTA.defaultBlockState(), 0, 0, 0, 0.32F, 0.62F, 0.32F, light);
            box(ps, buf, Blocks.IRON_BLOCK.defaultBlockState(), 0, 0.36, 0, 0.2F, 0.12F, 0.2F, light);
            box(ps, buf, Blocks.LIME_CONCRETE.defaultBlockState(), 0, -0.08, 0, 0.335F, 0.08F, 0.335F, LightTexture.FULL_BRIGHT);
         } else {
            float s = 0.55F * (1 + 0.12F * Mth.sin(t * 0.9F));
            ps.mulPose(Axis.YP.rotationDegrees(t * 13));
            ps.mulPose(Axis.XP.rotationDegrees(t * 9));
            box(ps, buf, Blocks.NETHER_WART_BLOCK.defaultBlockState(), 0, 0, 0, s, s * 0.9F, s, light);
            box(ps, buf, Blocks.SLIME_BLOCK.defaultBlockState(), 0.12, 0.1, -0.1, s * 0.5F, s * 0.5F, s * 0.5F, LightTexture.FULL_BRIGHT);
         }
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }
   }

   /** Essaim : des dizaines de mouches qui tournoient en nuée, de plus en plus serrées autour de leur proie. */
   public static class Swarm extends EntityRenderer<PlagueSwarm> {
      private static final ResourceLocation T_FLY = new ResourceLocation(HeraliszPlague.MODID, "textures/vfx/fly.png");

      public Swarm(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public ResourceLocation getTextureLocation(PlagueSwarm e) {
         return T_FLY;
      }

      @Override
      public boolean shouldRender(PlagueSwarm e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
         return e.distanceToSqr(x, y, z) < 96 * 96;
      }

      @Override
      public void render(PlagueSwarm e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float t = e.tickCount + partial;
         float dens = e.density(partial);
         if (dens <= 0.01F) return;
         Minecraft mc = Minecraft.getInstance();
         Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
         Vec3 me = e.getPosition(partial);
         Matrix4f m = ps.last().pose();
         VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glass(T_FLY));
         RandomSource r = RandomSource.create(e.getId() * 7919L);
         int n = (int) (70 * dens);
         for (int i = 0; i < n; i++) {
            double a0 = r.nextDouble() * Mth.TWO_PI, b0 = r.nextDouble() * Mth.TWO_PI;
            double w1 = 0.12 + r.nextDouble() * 0.18, w2 = 0.09 + r.nextDouble() * 0.2;
            double rad = 0.4 + r.nextDouble() * 1.1;
            double x = Math.cos(a0 + t * w1) * rad + Math.sin(t * 0.37 + i) * 0.15;
            double y = Math.sin(b0 + t * w2) * rad * 0.6 + Math.cos(t * 0.29 + i * 1.3) * 0.12;
            double z = Math.sin(a0 + t * w1) * rad + Math.cos(t * 0.41 + i) * 0.15;
            float s = 0.07F + (i % 4) * 0.012F;
            Vec3 c = new Vec3(x, y, z);
            // quad face caméra, en coordonnées relatives à l'entité
            Vec3 f = cam.subtract(me.add(c)).normalize();
            Vec3 up = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 rr = up.cross(f).normalize().scale(s), uu = f.cross(up.cross(f).normalize()).normalize().scale(s);
            int argb = VfxDraw.argb(0xFFFFFF, Math.min(1F, dens * 1.2F));
            VfxDraw.v(vc, m, x - rr.x - uu.x, y - rr.y - uu.y, z - rr.z - uu.z, 0, 1, argb);
            VfxDraw.v(vc, m, x + rr.x - uu.x, y + rr.y - uu.y, z + rr.z - uu.z, 1, 1, argb);
            VfxDraw.v(vc, m, x + rr.x + uu.x, y + rr.y + uu.y, z + rr.z + uu.z, 1, 0, argb);
            VfxDraw.v(vc, m, x - rr.x + uu.x, y - rr.y + uu.y, z - rr.z + uu.z, 0, 0, argb);
         }
         super.render(e, yaw, partial, ps, buf, light);
      }
   }
}
