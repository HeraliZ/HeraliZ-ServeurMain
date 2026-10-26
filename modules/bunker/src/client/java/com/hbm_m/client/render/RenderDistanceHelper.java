package com.hbm_m.client.render;

import com.hbm_m.compat.ContraptionRenderCompat;
import com.hbm_m.config.ModClothConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class RenderDistanceHelper {
   private static final double FADE_ZONE_BLOCKS = 16.0;
   private static final double LIGHT_CORNER_DETAIL_MARGIN_BLOCKS = 48.0;

   private RenderDistanceHelper() {
   }

   public static double distanceSqToCamera(BlockPos blockPos) {
      Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      Vec3 cam = camera.getPosition();
      double dx = blockPos.getX() + 0.5 - cam.x;
      double dy = blockPos.getY() + 0.5 - cam.y;
      double dz = blockPos.getZ() + 0.5 - cam.z;
      return dx * dx + dy * dy + dz * dz;
   }

   public static double getAnimatedDistanceBlocks() {
      return ModClothConfig.get().modelUpdateDistance * 16.0;
   }

   public static double getStaticDistanceBlocks() {
      return ModClothConfig.get().modelStaticRenderDistance * 16.0;
   }

   public static boolean shouldSkipAnimation(BlockPos blockPos) {
      double maxDist = getAnimatedDistanceBlocks();
      return distanceSqToCamera(blockPos) > maxDist * maxDist;
   }

   public static boolean shouldSkipAnimation(BlockEntity blockEntity) {
      return ContraptionRenderCompat.isContraptionRender(blockEntity) ? false : shouldSkipAnimation(blockEntity.getBlockPos());
   }

   public static float computeAnimatedFade(BlockPos blockPos) {
      return computeFade(blockPos, getAnimatedDistanceBlocks());
   }

   public static float computeAnimatedFade(BlockEntity blockEntity) {
      return ContraptionRenderCompat.isContraptionRender(blockEntity) ? 1.0F : computeAnimatedFade(blockEntity.getBlockPos());
   }

   public static float computeStaticFade(BlockPos blockPos) {
      return computeFade(blockPos, getStaticDistanceBlocks());
   }

   public static float computeStaticFade(BlockEntity blockEntity) {
      return ContraptionRenderCompat.isContraptionRender(blockEntity) ? 1.0F : computeStaticFade(blockEntity.getBlockPos());
   }

   public static float computeFade(BlockPos blockPos, double maxBlocks) {
      if (maxBlocks <= 0.0) {
         return -1.0F;
      }

      double distSq = distanceSqToCamera(blockPos);
      double maxSq = maxBlocks * maxBlocks;
      if (distSq > maxSq) {
         return -1.0F;
      }

      double fadeStartBlocks = Math.max(0.0, maxBlocks - 16.0);
      double fadeStartSq = fadeStartBlocks * fadeStartBlocks;
      if (distSq <= fadeStartSq) {
         return 1.0F;
      }

      double dist = Math.sqrt(distSq);
      float t = (float)((maxBlocks - dist) / 16.0);
      return Math.max(0.0F, Math.min(1.0F, t));
   }

   public static float computeFade(BlockEntity blockEntity, double maxBlocks) {
      return ContraptionRenderCompat.isContraptionRender(blockEntity) ? 1.0F : computeFade(blockEntity.getBlockPos(), maxBlocks);
   }

   public static int getStaticViewDistanceBlocks() {
      return ModClothConfig.get().modelStaticRenderDistance * 16;
   }

   public static double getLightCornerDetailDistanceSq() {
      double maxBlocks = getStaticDistanceBlocks();
      double detailBlocks = Math.max(0.0, maxBlocks - 48.0);
      return detailBlocks * detailBlocks;
   }
}
