package fr.heralisz.tactical.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class CommonConfig {
   public static BooleanValue GRENADE_EXPLOSION_BLOCK_DAMAGE;
   public static BooleanValue MELEE_ITEM_CONSUME_DURABILITY;
   public static IntValue MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD;

   public static ForgeConfigSpec init() {
      Builder builder = new Builder();
      builder.push("grenade");
      GRENADE_EXPLOSION_BLOCK_DAMAGE = builder.comment("Whether grenade explosion can damage blocks (if it can)").define("grenadeExplosionBlockDamage", true);
      builder.pop();
      builder.push("melee");
      MELEE_ITEM_CONSUME_DURABILITY = builder.comment("Whether melee items consume durability on attack").define("meleeItemConsumeDurability", true);
      MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD = builder.comment("when target's invulnerable tick is less than this value, melee attack will ignore it")
         .defineInRange("meleeIgnoreInvulnerableTickThreshold", 20, 0, 100);
      builder.pop();
      return builder.build();
   }
}
