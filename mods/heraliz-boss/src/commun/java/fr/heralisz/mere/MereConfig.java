package fr.heralisz.mere;

import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages serveur (serverconfig/heralisz_mere-server.toml) : équilibrage sans recompiler. */
public final class MereConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue VIRTUAL_HP;
   public static final ForgeConfigSpec.DoubleValue HIT_CAP;
   public static final ForgeConfigSpec.DoubleValue DPS_CAP;
   public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;
   public static final ForgeConfigSpec.DoubleValue SHIELD_BLOCK;
   public static final ForgeConfigSpec.IntValue RESPAWN_MINUTES;
   public static final ForgeConfigSpec.IntValue LEASH_RADIUS;
   public static final ForgeConfigSpec.IntValue TRIGGER_DISTANCE;
   public static final ForgeConfigSpec.IntValue CHANDELIER_SECONDS;
   public static final ForgeConfigSpec.IntValue GROUP_BASE;
   public static final ForgeConfigSpec.DoubleValue GROUP_STEP;
   public static final ForgeConfigSpec.BooleanValue CINEMATICS;
   public static final ForgeConfigSpec.DoubleValue AXE_THROW_DAMAGE;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.push("mere");
      VIRTUAL_HP = b.comment("PV « réels » de la Mère : les dégâts des armes sont ramenés à cette échelle. Réglé pour ~7 min à 3-4 joueurs moyens armés.")
         .defineInRange("virtualHp", 42000, 500, 10000000);
      HIT_CAP = b.comment("Dégâts max retenus par impact (anti one-shot).").defineInRange("hitCap", 40.0, 1.0, 10000.0);
      DPS_CAP = b.comment("Au-delà de ces dégâts bruts par seconde, le surplus ne compte qu'à 25 %.").defineInRange("dpsCap", 900.0, 10.0, 100000.0);
      DAMAGE_MULT = b.comment("Multiplicateur des dégâts infligés par la Mère et sa couvée.").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
      SHIELD_BLOCK = b.comment("Part des dégâts encaissés pendant qu'elle est suspendue à son fil (0 = intouchable).")
         .defineInRange("hangingDamage", 0.0, 0.0, 1.0);
      RESPAWN_MINUTES = b.comment("Délai avant qu'une nouvelle Mère n'éclose après sa mort.").defineInRange("respawnMinutes", 60, 0, 10080);
      LEASH_RADIUS = b.comment("Distance max au nid : au-delà (ou sans joueur autour), elle remonte dans son cocon et se soigne.")
         .defineInRange("leashRadius", 96, 12, 200);
      TRIGGER_DISTANCE = b.comment("(inutilisé : le rituel de l'autel réveille la Mère)")
         .defineInRange("triggerDistance", 34, 6, 80);
      CHANDELIER_SECONDS = b.comment("Phase 3 : une grappe d'œufs de la voûte crève sur un joueur toutes les N secondes.").defineInRange("clusterSeconds", 24, 5, 600);
      GROUP_BASE = b.comment("Jusqu'à ce nombre de joueurs engagés, ses PV ne bougent pas.").defineInRange("playersBeforeScaling", 6, 1, 100);
      GROUP_STEP = b.comment("Au-delà, chaque joueur en plus ajoute cette part de PV (0.15 = +15 %).").defineInRange("hpPerExtraPlayer", 0.15, 0.0, 2.0);
      AXE_THROW_DAMAGE = b.comment("Dégâts d'une grappe qui s'écrase (par joueur touché).").defineInRange("clusterDamage", 12.0, 0.0, 1000.0);
      CINEMATICS = b.comment("Cinématiques (caméra) à l'entrée, au réveil, aux changements de phase et à la mort.").define("cinematics", true);
      b.pop();
      SPEC = b.build();
   }

   private MereConfig() {}
}
