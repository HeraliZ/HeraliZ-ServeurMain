package com.hbm_m.client.render;

import com.hbm_m.config.ModClothConfig;
import com.hbm_m.main.MainRegistry;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MdiRenderDiag {
   private static final AtomicBoolean BANNER = new AtomicBoolean();

   private MdiRenderDiag() {
   }

   private static ModClothConfig cfg() {
      try {
         return ModClothConfig.get();
      } catch (Throwable ignored) {
         return null;
      }
   }

   public static boolean isDebugEnabled() {
      ModClothConfig c = cfg();
      return c != null && c.mdiDebugLogDispatch;
   }

   public static boolean isVerboseEnabled() {
      ModClothConfig c = cfg();
      return c != null && c.mdiVerboseSubdraws;
   }

   public static void logBannerOnce() {
      if (isDebugEnabled() || isVerboseEnabled()) {
         if (BANNER.compareAndSet(false, true)) {
            MainRegistry.LOGGER.info("[HBM-M MDI] Диагностика (Cloth → rendering): mdiDebugLogDispatch, mdiVerboseSubdraws.");
         }
      }
   }
}
