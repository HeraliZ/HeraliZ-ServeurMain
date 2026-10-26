package com.hbm_m.client.render;

import net.minecraft.client.Minecraft;

public final class RenderFrameLight {
   private static int frameSerial;
   private static int lastUpdatedFrame = -1;

   private RenderFrameLight() {
   }

   public static void onFrameStart() {
      frameSerial++;
   }

   public static void ensureLightTextureUpdated() {
      if (lastUpdatedFrame != frameSerial) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gameRenderer != null) {
            mc.gameRenderer.lightTexture().updateLightTexture(mc.getFrameTime());
         }

         lastUpdatedFrame = frameSerial;
      }
   }
}
