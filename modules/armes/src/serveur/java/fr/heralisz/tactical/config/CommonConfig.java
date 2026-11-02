package fr.heralisz.tactical.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/**
 * Réglages COMMON de heralisz_tactical (config/heralisz_tactical-common.toml). Audit du 02/11 (point 11) : réglage du
 * serveur seulement, déclaré par la partie serveur (PartieServeur) avec le même nom de fichier ; plus jamais écrit sur le
 * PC des joueurs. Le seul réglage lu par du code commun (seuil d'invulnérabilité des coups) est recopié dans
 * {@link ReglagesMelee} à chaque chargement.
 */
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

   /** Après chaque chargement du fichier : recopie ce que lit le code commun. */
   public static void appliquer() {
      ReglagesMelee.seuilInvulnerable = MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD.get();
   }
}
