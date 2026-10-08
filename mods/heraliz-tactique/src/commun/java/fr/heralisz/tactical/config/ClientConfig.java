package fr.heralisz.tactical.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

public class ClientConfig {
   public static BooleanValue BLACK_FLASH;
   public static DoubleValue EXPLODE_SCREEN_SHAKE_MULTIPLIER;

   public static ForgeConfigSpec init() {
      Builder builder = new Builder();
      builder.comment("Use black overlay instead of white when blinded by flashbang");
      BLACK_FLASH = builder.define("blackFlash", false);
      EXPLODE_SCREEN_SHAKE_MULTIPLIER = builder.comment("Screen shake multiplier for explosions, default is 1.0")
         .defineInRange("explodeScreenShakeMultiplier", 1.0, 0.0, 128.0);
      return builder.build();
   }
}
