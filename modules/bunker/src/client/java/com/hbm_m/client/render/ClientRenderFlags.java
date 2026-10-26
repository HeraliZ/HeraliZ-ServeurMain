package com.hbm_m.client.render;

import com.hbm_m.config.ModClothConfig;

public final class ClientRenderFlags {
   private static boolean useInstancedBatching;
   private static boolean gpuBoneSkinning;
   private static boolean enableOcclusionCulling;
   private static boolean useSlicedLight;
   private static int maxInstances = 4096;

   private ClientRenderFlags() {
   }

   public static void onFrameStart() {
      ModClothConfig cfg = ModClothConfig.get();
      useInstancedBatching = cfg.useInstancedStaticRendering;
      gpuBoneSkinning = cfg.gpuBoneSkinning;
      enableOcclusionCulling = cfg.enableOcclusionCulling;
      useSlicedLight = cfg.useSlicedLight;
      maxInstances = cfg.maxInstancedInstancesPerPart;
   }

   public static boolean useInstancedBatching() {
      return useInstancedBatching;
   }

   public static boolean gpuBoneSkinning() {
      return gpuBoneSkinning;
   }

   public static boolean enableOcclusionCulling() {
      return enableOcclusionCulling;
   }

   public static boolean useSlicedLight() {
      return useSlicedLight;
   }

   public static boolean useSlicedLightForNewRenderer() {
      try {
         return ModClothConfig.get().useSlicedLight;
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static int maxInstances() {
      if (maxInstances > 0) {
         return maxInstances;
      }

      try {
         int cfg = ModClothConfig.get().maxInstancedInstancesPerPart;
         return cfg > 0 ? cfg : 4096;
      } catch (Throwable ignored) {
         return 4096;
      }
   }
}
