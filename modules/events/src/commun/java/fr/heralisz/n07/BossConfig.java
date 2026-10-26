package fr.heralisz.n07;

import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages serveur (serverconfig/heralisz_n07-server.toml) : équilibrage du boss sans recompiler. */
public final class BossConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue VIRTUAL_HP;
   public static final ForgeConfigSpec.DoubleValue HIT_CAP;
   public static final ForgeConfigSpec.DoubleValue DPS_CAP;
   public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;
   public static final ForgeConfigSpec.IntValue RESPAWN_MINUTES;
   public static final ForgeConfigSpec.IntValue ACTIVATION_RADIUS;
   public static final ForgeConfigSpec.IntValue LEASH_RADIUS;
   public static final ForgeConfigSpec.IntValue GROUP_BASE;
   public static final ForgeConfigSpec.DoubleValue GROUP_STEP;
   public static final ForgeConfigSpec.IntValue REMAINS_MINUTES;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.push("sujet_n07");
      VIRTUAL_HP = b.comment("PV « réels » du Sujet N-07 (les dégâts des armes sont ramenés à cette échelle, comme pour tous les boss).",
            "Réglé pour ~5 min à 3-4 joueurs moyens armés.")
         .defineInRange("virtualHp", 30000, 500, 10000000);
      HIT_CAP = b.comment("Dégâts max retenus par impact (anti lance-roquettes / sniper one-shot).").defineInRange("hitCap", 40.0, 1.0, 10000.0);
      DPS_CAP = b.comment("Au-delà de ces dégâts bruts par seconde, le surplus ne compte qu'à 25 % (anti armes à cadence délirante).")
         .defineInRange("dpsCap", 900.0, 10.0, 100000.0);
      DAMAGE_MULT = b.comment("Multiplicateur des dégâts infligés par le boss.").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
      RESPAWN_MINUTES = b.comment("Délai avant qu'une cuve de confinement relâche un nouveau boss après sa mort.").defineInRange("respawnMinutes", 60, 0, 10080);
      ACTIVATION_RADIUS = b.comment("Distance à laquelle un joueur réveille le boss de sa cuve.").defineInRange("activationRadius", 18, 4, 64);
      LEASH_RADIUS = b.comment("Distance max à sa cuve : au-delà (ou sans joueur autour), il rentre et se soigne.").defineInRange("leashRadius", 34, 8, 128);
      b.pop();
      b.push("groupe");
      GROUP_BASE = b.comment("Jusqu'à ce nombre de joueurs engagés, les PV des boss ne bougent pas (le combat est prévu pour 2 à 6 joueurs).")
         .defineInRange("playersBeforeScaling", 6, 1, 100);
      GROUP_STEP = b.comment("Au-delà, chaque joueur engagé en plus ajoute cette part de PV (0.15 = +15 %) : contre les raids à 15 joueurs.")
         .defineInRange("hpPerExtraPlayer", 0.15, 0.0, 2.0);
      REMAINS_MINUTES = b.comment("Durée pendant laquelle la dépouille d'un boss reste sur place (Sujet N-07 : sa statue de trinitite).")
         .defineInRange("remainsMinutes", 5, 1, 60);
      b.pop();
      SPEC = b.build();
   }

   private BossConfig() {}
}
