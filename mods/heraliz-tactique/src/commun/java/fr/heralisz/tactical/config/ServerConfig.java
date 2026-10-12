package fr.heralisz.tactical.config;

import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.vitres.Vitres;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
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
   public static BooleanValue PLONGEON;
   public static DoubleValue PLONGEON_VITESSE;
   public static DoubleValue PLONGEON_HAUTEUR;
   public static DoubleValue DELAI_PLONGEON;
   /** HeraliZ : vitres cassées par les balles de TaCZ, qui repoussent (fr.heraliz.tactique.vitres.Vitres). */
   public static BooleanValue VITRES_CASSER;
   public static IntValue VITRES_DELAI_MINUTES;
   public static ConfigValue<List<? extends String>> VITRES_DIMENSIONS;
   public static IntValue VITRES_RAYON_SPAWN;
   public static BooleanValue VITRES_CLAIMS_CRAC;
   public static ConfigValue<List<? extends String>> VITRES_BLOCS_PROTEGES;

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
      PLONGEON = builder.comment(
            "Plongeon façon Call of Duty : en avançant, touche « Ramper » pendant un saut (jusqu'à 0,45 s après) ou avec la",
            "touche de saut enfoncée : le joueur est lancé en avant, corps à plat, et retombe allongé. Avec ou sans arme en",
            "main. L'élan est décidé et donné par le serveur (vitesse de Minecraft envoyée au joueur, suivie par Spartan).",
            "Il compte comme « se coucher » : pas de tir pendant duree_sans_tir_couche depuis son début, et il respecte",
            "delai_rallonger. Les dégâts de chute restent ceux de Minecraft. false : pas de plongeon.")
         .define("plongeon", Allonge.DEFAUT_PLONGEON);
      PLONGEON_VITESSE = builder.comment(
            "Vitesse vers l'avant donnée par le plongeon, en blocs par tick (un sprint va à 0,28) : 0,7 lance le joueur",
            "d'environ 2,5 blocs depuis le sol, de 3,5 à 4,5 blocs depuis un saut.")
         .defineInRange("plongeon_vitesse", Allonge.DEFAUT_PLONGEON_VITESSE, 0.1, 1.5);
      PLONGEON_HAUTEUR = builder.comment(
            "Petit élan vers le haut d'un plongeon depuis le sol, en blocs par tick (un saut vaut 0,42) : 0,15 fait monter",
            "le corps d'environ 0,2 bloc. En l'air (juste après un saut), le plongeon ne donne aucun élan vers le haut.",
            "Au-dessus de 0,15, un plongeon lancé avec un saut peut passer par-dessus une clôture ou un muret (1,5 bloc).")
         .defineInRange("plongeon_hauteur", Allonge.DEFAUT_PLONGEON_HAUTEUR, 0.0, 0.3);
      DELAI_PLONGEON = builder.comment(
            "Délai (en secondes) entre deux plongeons, compté depuis le début du précédent (delai_rallonger compte aussi).",
            "Le serveur compte 0,5 s de moins (décalage du réseau).")
         .defineInRange("delai_plongeon", Allonge.DEFAUT_DELAI_PLONGEON, 0.0, 120.0);
      builder.pop();
      // HeraliZ : vitres cassées par les balles, qui repoussent (lu par le serveur seulement)
      builder.push("vitres");
      VITRES_CASSER = builder.comment(
            "Les balles des armes TaCZ cassent le verre (verre, verre coloré ou teinté, vitres et vitres colorées : la même règle",
            "que TaCZ). La balle casse la vitre et s'arrête dessus ; la vitre repousse seule, au même endroit et dans le même",
            "état. Le verre gorgé d'eau ne casse pas (l'eau se répandrait). Le réglage DestroyGlass de tacz-common.toml doit",
            "rester à false : c'est HeraliZ qui casse (TaCZ ne regarde aucune zone protégée et dérange les blocs voisins).",
            "false : comme avant (les blocs de verre laissent passer les balles, les vitres les arrêtent, rien ne casse).")
         .define("casser", true);
      VITRES_DELAI_MINUTES = builder.comment(
            "Délai (en minutes) avant qu'une vitre cassée repousse, compté en temps réel : une vitre dont le délai est passé",
            "pendant un arrêt du serveur repousse au redémarrage. Si quelqu'un se tient à sa place ou si un bloc l'occupe, elle",
            "attend et réessaie. Les vitres à remettre sont gardées dans les données du monde (data/heraliz_vitres.dat, une",
            "liste par dimension).")
         .defineInRange("delai_repousse_minutes", Vitres.DEFAUT_DELAI_MINUTES, 1, 10080);
      VITRES_DIMENSIONS = builder.comment(
            "Dimensions où le verre ne casse jamais (arènes des matchs, zombies, bunkers des clans) : les balles le traversent",
            "ou s'y arrêtent comme avant.")
         .defineList("dimensions_protegees", Vitres.DEFAUT_DIMENSIONS, o -> o instanceof String);
      VITRES_RAYON_SPAWN = builder.comment(
            "Rayon (en blocs) autour du point d'apparition du monde (dimension minecraft:overworld) où le verre ne casse pas,",
            "sur toute la hauteur. 0 : aucun.")
         .defineInRange("rayon_spawn", Vitres.DEFAUT_RAYON_SPAWN, 0, 100000);
      VITRES_CLAIMS_CRAC = builder.comment("Le verre ne casse pas dans les claims de CRac (bases des groupes).")
         .define("proteger_claims_crac", true);
      VITRES_BLOCS_PROTEGES = builder.comment("Blocs de verre qui ne cassent jamais (identifiants, par exemple \"minecraft:tinted_glass\").")
         .defineList("blocs_proteges", List.of(), o -> o instanceof String);
      builder.pop();
      return builder.build();
   }
}
