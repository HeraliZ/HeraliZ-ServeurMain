package fr.heralisz.tactical.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class ServerConfig {
   public static IntValue FLASH_SHIELD_MAX_DURABILITY;
   public static IntValue FLASH_SHIELD_COOLDOWN;
   public static DoubleValue CROUCHING_INIT_SPEED_PERCENT;
   public static IntValue MELEE_MAX_TARGET_PER_PACKET;

   public static ForgeConfigSpec init() {
      Builder builder = new Builder();
      builder.push("flash shield");
      FLASH_SHIELD_MAX_DURABILITY = builder.comment("max durability of flash shield").defineInRange("flash_shield_max_durability", 450, 1, 32767);
      FLASH_SHIELD_COOLDOWN = builder.comment("cooldown of flash shield in ticks").defineInRange("flash_shield_cooldown", 200, 20, 32767);
      builder.pop();
      builder.push("throwable");
      CROUCHING_INIT_SPEED_PERCENT = builder.comment("modifier of initial speed when crouching, 1.0 means no change")
         .defineInRange("flash_shield_cooldown", 0.5, 0.01, 2.0);
      builder.pop();
      builder.push("melee");
      MELEE_MAX_TARGET_PER_PACKET = builder.comment("maximum number of targets can contains in melee attack request packet")
         .defineInRange("melee_max_target", 32, 1, 512);
      builder.pop();
      return builder.build();
   }
}
