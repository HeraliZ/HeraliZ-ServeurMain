package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.sylvorn.client.forest.ProjectileRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import fr.heralisz.sylvorn.entity.SapNode;
import fr.heralisz.sylvorn.entity.SeedPod;
import fr.heralisz.sylvorn.entity.ThornSpear;
import fr.heralisz.sylvorn.entity.ThrownLog;

/** Rendus des projectiles de Sylvorn, faits de vrais volumes texturés : tronc lancé, javelot d'épines, cosse de graines, nœud de sève. */
public final class SylvornProjectiles {
   private SylvornProjectiles() {}

   /** Tronc de 3 blocs qui tournoie en vol. */
   public static class Log extends ProjectileRender.Base<ThrownLog> {
      public Log(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public void render(ThrownLog e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float t = e.tickCount + partial;
         ps.pushPose();
         ps.translate(0, 0.5, 0);
         ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 47 % 360));
         ps.mulPose(Axis.XP.rotationDegrees(t * 24));
         ps.scale(0.9F, 0.9F, 0.9F);
         for (int i = -1; i <= 1; i++) {
            ps.pushPose();
            ps.translate(0, i, 0);
            ProjectileRender.cube(ps, buf, Blocks.SPRUCE_LOG.defaultBlockState(), light);
            ps.popPose();
         }
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }
   }

   /** Javelot d'épines : hampe de bois sombre, pointe pâle effilée, barbes en spirale ; orienté selon sa vitesse, planté il reste incliné. */
   public static class Spear extends ProjectileRender.Base<ThornSpear> {
      public Spear(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      private static void box(PoseStack ps, MultiBufferSource buf, BlockState st, double x, double y, double z, float sx, float sy, float sz, int light) {
         ps.pushPose();
         ps.translate(x, y, z);
         ps.scale(sx, sy, sz);
         Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }

      @Override
      public void render(ThornSpear e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float yr = Mth.lerp(partial, e.yRotO, e.getYRot()), xr = Mth.lerp(partial, e.xRotO, e.getXRot());
         BlockState wood = Blocks.DARK_OAK_WOOD.defaultBlockState(), pale = Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState(),
            barb = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
         ps.pushPose();
         ps.mulPose(Axis.YP.rotationDegrees(yr));
         ps.mulPose(Axis.XP.rotationDegrees(-xr));
         if (!e.stuck()) ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partial) * 30));
         box(ps, buf, wood, -0.08, -0.08, -1.5, 0.16F, 0.16F, 2.2F, light);
         box(ps, buf, wood, -0.06, -0.06, 0.7, 0.12F, 0.12F, 0.3F, light);
         box(ps, buf, pale, -0.04, -0.04, 1.0, 0.08F, 0.08F, 0.28F, light);
         box(ps, buf, pale, -0.02, -0.02, 1.28, 0.04F, 0.04F, 0.2F, light);
         for (int i = 0; i < 7; i++) {
            ps.pushPose();
            ps.translate(0, 0, -1.25 + i * 0.3);
            ps.mulPose(Axis.ZP.rotationDegrees(i * 137));
            ps.mulPose(Axis.XP.rotationDegrees(-40));
            box(ps, buf, barb, -0.02, 0.05, -0.02, 0.04F, 0.24F, 0.04F, light);
            ps.popPose();
         }
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }
   }

   /** Cosse moussue : tourne en vol ; plantée, elle gonfle en pulsant de plus en plus vite avant d'éclater. */
   public static class Pod extends ProjectileRender.Base<SeedPod> {
      public Pod(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public void render(SeedPod e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float t = e.tickCount + partial;
         ps.pushPose();
         ps.translate(0, 0.35, 0);
         float s = 0.55F;
         if (e.landedAt() >= 0) {
            float k = Mth.clamp((t - e.landedAt()) / SeedPod.FUSE, 0, 1);
            s *= 1 + 0.5F * k + 0.12F * Mth.sin(t * (0.6F + k * 1.6F));
         } else {
            ps.mulPose(Axis.XP.rotationDegrees(t * 18));
            ps.mulPose(Axis.ZP.rotationDegrees(t * 11));
         }
         ps.scale(s, s, s);
         ProjectileRender.cube(ps, buf, Blocks.MOSS_BLOCK.defaultBlockState(), light);
         ps.scale(0.55F, 1.35F, 0.55F);
         ProjectileRender.cube(ps, buf, Blocks.SHROOMLIGHT.defaultBlockState(), LightTexture.FULL_BRIGHT);
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }
   }

   /** Nœud de sève : cœur ambré lumineux qui bat, pris dans une cage de racines. */
   public static class Node extends ProjectileRender.Base<SapNode> {
      public Node(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public void render(SapNode e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float t = e.tickCount + partial;
         float grow = Mth.clamp(t / 12F, 0, 1);
         float beat = 1 + 0.08F * Mth.sin(t * 0.5F) + 0.06F * Math.max(0, Mth.sin(t * 1.0F));
         ps.pushPose();
         ps.translate(0, 1.5 * grow, 0);
         ps.pushPose();
         ps.mulPose(Axis.YP.rotationDegrees(t * 2));
         ps.scale(1.0F * beat * grow, 1.2F * beat * grow, 1.0F * beat * grow);
         ProjectileRender.cube(ps, buf, Blocks.SHROOMLIGHT.defaultBlockState(), LightTexture.FULL_BRIGHT);
         ps.popPose();
         for (int i = 0; i < 4; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(i * 90 + 45));
            ps.translate(0.62, -0.2, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(12));
            ps.scale(0.3F, 2.6F * grow, 0.3F);
            ProjectileRender.cube(ps, buf, Blocks.MANGROVE_ROOTS.defaultBlockState(), light);
            ps.popPose();
         }
         ps.translate(0, -1.3, 0);
         ps.scale(1.4F, 0.5F, 1.4F);
         ProjectileRender.cube(ps, buf, Blocks.ROOTED_DIRT.defaultBlockState(), light);
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }
   }}
