package com.hbm_m.client.render.culling;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MdiRenderFrameGate {
   private static long frameSerial = 0L;

   private MdiRenderFrameGate() {
   }

   public static long currentSerial() {
      return frameSerial;
   }

   public static void advanceAfterPresent() {
      frameSerial++;
   }

   public static void reset() {
      frameSerial = 0L;
   }
}
