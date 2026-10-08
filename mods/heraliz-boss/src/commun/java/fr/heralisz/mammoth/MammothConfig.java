package fr.heralisz.mammoth;

import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages serveur (serverconfig/heralisz_mammoth-server.toml) : équilibrage sans recompiler. */
public final class MammothConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue VIRTUAL_HP;
   public static final ForgeConfigSpec.DoubleValue HIT_CAP;
   public static final ForgeConfigSpec.DoubleValue DPS_CAP;
   public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;
   public static final ForgeConfigSpec.IntValue RESPAWN_MINUTES;
   public static final ForgeConfigSpec.IntValue LEASH_RADIUS;
   public static final ForgeConfigSpec.IntValue GROUP_BASE;
   public static final ForgeConfigSpec.DoubleValue GROUP_STEP;
   public static final ForgeConfigSpec.BooleanValue CINEMATICS;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.push("ymirak");
      VIRTUAL_HP = b.comment("PV « réels » d'Ymirak : les dégâts des armes sont ramenés à cette échelle. Réglé pour ~6 min à 3-4 joueurs moyens armés.")
         .defineInRange("virtualHp", 36000, 500, 10000000);
      HIT_CAP = b.comment("Dégâts max retenus par impact (anti one-shot).").defineInRange("hitCap", 40.0, 1.0, 10000.0);
      DPS_CAP = b.comment("Au-delà de ces dégâts bruts par seconde, le surplus ne compte qu'à 25 %.").defineInRange("dpsCap", 900.0, 10.0, 100000.0);
      DAMAGE_MULT = b.comment("Multiplicateur des dégâts infligés par Ymirak.").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
      RESPAWN_MINUTES = b.comment("Délai avant que l'autel accepte un nouveau cor après sa mort.").defineInRange("respawnMinutes", 60, 0, 10080);
      LEASH_RADIUS = b.comment("Distance max à l'autel : au-delà (ou sans joueur autour), il rentre dans sa glace et se soigne.")
         .defineInRange("leashRadius", 44, 12, 128);
      GROUP_BASE = b.comment("Jusqu'à ce nombre de joueurs engagés, ses PV ne bougent pas.").defineInRange("playersBeforeScaling", 6, 1, 100);
      GROUP_STEP = b.comment("Au-delà, chaque joueur en plus ajoute cette part de PV (0.15 = +15 %).").defineInRange("hpPerExtraPlayer", 0.15, 0.0, 2.0);
      CINEMATICS = b.comment("Cinématiques (caméra) à l'apparition, aux changements de phase et à la mort.").define("cinematics", true);
      b.pop();
      SPEC = b.build();
   }

   private MammothConfig() {}
}
