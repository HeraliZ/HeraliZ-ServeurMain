package fr.heraliz.reanimation;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

public final class ClientConfig {
   public static final ForgeConfigSpec SPEC;
   public static final BooleanValue EXECUTION_CAMERA;
   public static final BooleanValue DESATURATE;
   public static final DoubleValue HEARTBEAT_VOLUME;
   public static final ConfigValue<List<? extends String>> DEFAULT_WEAPONS;
   public static final BooleanValue KNIFE_TRAILS;
   public static final BooleanValue EXECUTION_MUSIC;

   private ClientConfig() {
   }

   static {
      Builder b = new Builder();
      EXECUTION_CAMERA = b.comment(
            new String[]{
               "Caméra cinématique pendant une exécution (pour le bourreau et la victime).", "La réanimation, elle, se fait toujours en première personne."
            }
         )
         .define("executionCamera", true);
      DESATURATE = b.comment("Les couleurs se délavent quand on est au sol.").define("desaturate", true);
      HEARTBEAT_VOLUME = b.comment("Volume des battements de cœur entendus au sol.").defineInRange("heartbeatVolume", 0.8, 0.0, 1.0);
      DEFAULT_WEAPONS = b.comment(
            new String[]{
               "Arme affichée pendant une exécution quand le bourreau ne tient pas la bonne : exécution=arme.",
               "Arme = un id d'arme TaCZ (ex. heralisz_skins:karambit__tiger_tooth), \"melee:\" + un id HeralisZ (ex. melee:heralisz_melee:sea_gleam) ou un id d'item."
            }
         )
         .defineListAllowEmpty(
            "defaultWeapons",
            List.of(
               "karambit=heralisz_skins:karambit__doppler_ruby",
               "sea_gleam=melee:heralisz_melee:sea_gleam",
               "butterfly=heralisz_skins:butterfly__fade",
               "m9=heralisz_skins:m9__lore",
               "home_run=melee:heralisz_melee:baseball_bat"
            ),
            o -> o instanceof String
         );
      KNIFE_TRAILS = b.comment("Traînée lumineuse derrière la lame pendant les exécutions au couteau.").define("knifeTrails", true);
      EXECUTION_MUSIC = b.comment("Musique des finishers (catégorie de son « Juke-box / blocs musicaux »).").define("executionMusic", true);
      SPEC = b.build();
   }
}
