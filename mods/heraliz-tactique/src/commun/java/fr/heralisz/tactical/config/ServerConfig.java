package fr.heralisz.tactical.config;

import fr.heraliz.tactique.allonge.Allonge;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class ServerConfig {
   public static IntValue FLASH_SHIELD_MAX_DURABILITY;
   public static IntValue FLASH_SHIELD_COOLDOWN;
   public static DoubleValue CROUCHING_INIT_SPEED_PERCENT;
   public static IntValue MELEE_MAX_TARGET_PER_PACKET;
   /** HeraliZ : s'allonger avec la touche « Ramper » de TaCZ (fr.heraliz.tactique.allonge.Allonge). */
   public static DoubleValue DELAI_RALLONGER;
   public static DoubleValue SANS_TIR_EN_SE_COUCHANT;

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
      // HeraliZ : config serveur envoyée par Forge aux joueurs à la connexion (le joueur et le serveur lisent les mêmes valeurs)
      builder.push("allonge");
      DELAI_RALLONGER = builder.comment(
            "Délai (en secondes) avant de pouvoir se rallonger, compté à partir du moment où le joueur s'est relevé",
            "(touche, saut, autre objet en main, eau...). Le serveur compte 0,5 s de moins (décalage du réseau).")
         .defineInRange("delai_rallonger", Allonge.DEFAUT_DELAI_RALLONGER, 0.0, 60.0);
      SANS_TIR_EN_SE_COUCHANT = builder.comment(
            "Durée (en secondes) sans pouvoir tirer pendant que le joueur se couche (passage debout -> allongé ;",
            "l'animation de Minecraft dure 0,6 s). Le serveur compte 0,2 s de moins (décalage du réseau).")
         .defineInRange("duree_sans_tir_couche", Allonge.DEFAUT_SANS_TIR, 0.0, 5.0);
      builder.pop();
      return builder.build();
   }
}
