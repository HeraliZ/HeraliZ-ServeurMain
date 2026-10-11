package fr.heraliz.reanimation;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public final class ReanimationConfig {
   public static final ForgeConfigSpec SPEC;
   public static final BooleanValue ENABLED;
   public static final DoubleValue DOWNED_HEALTH;
   public static final IntValue BLEED_OUT_SECONDS;
   public static final DoubleValue CRAWL_SPEED;
   public static final BooleanValue DIE_ON_DISCONNECT;
   public static final BooleanValue MOBS_IGNORE_DOWNED;
   public static final DoubleValue INTERACT_RANGE;
   public static final BooleanValue ALLOW_SEARCH;
   public static final BooleanValue SEARCH_FREEZES;
   public static final IntValue REVIVE_SECONDS;
   public static final DoubleValue REVIVE_HEALTH;
   public static final BooleanValue PAUSE_BLEED_WHILE_REVIVING;
   public static final IntValue GROGGY_SECONDS;
   public static final BooleanValue EXECUTIONS_ENABLED;
   public static final DoubleValue EXECUTION_RANGE;
   public static final ConfigValue<List<? extends String>> FREE_EXECUTIONS;
   public static final BooleanValue FREE_FINISHERS;

   private ReanimationConfig() {
   }

   static {
      Builder b = new Builder();
      b.push("downed");
      ENABLED = b.comment("Les joueurs tombent au sol au lieu de mourir.").define("enabled", true);
      DOWNED_HEALTH = b.comment("Vie au sol (8 = 4 cœurs). Tout dégât reçu au sol peut achever le joueur.").defineInRange("downedHealth", 8.0, 1.0, 40.0);
      BLEED_OUT_SECONDS = b.comment("Secondes au sol avant de se vider de son sang.").defineInRange("bleedOutSeconds", 30, 5, 600);
      CRAWL_SPEED = b.comment("Vitesse de reptation à terre, par rapport au ramper vanilla (lui-même 0,3 x la marche) : 1.0 = vanilla, 0.35 = environ 1/10 de la marche, 0 = immobile.").defineInRange("crawlSpeed", 0.35, 0.0, 2.0);
      DIE_ON_DISCONNECT = b.comment(
            new String[]{
               "Un joueur qui se déconnecte au sol meurt (anti déco-combat).",
               "Si false, il est toujours au sol à son retour. Un redémarrage du serveur ne tue jamais."
            }
         )
         .define("dieOnDisconnect", true);
      MOBS_IGNORE_DOWNED = b.comment(
            new String[]{
               "Les monstres arrêtent d'attaquer les joueurs au sol.",
               "false (défaut) : un zombie peut achever un joueur au sol (vraie mort, un seul corps)."
            }
         )
         .define("mobsIgnoreDowned", false);
      b.pop();
      b.push("interaction");
      INTERACT_RANGE = b.comment("Distance max (en blocs) pour fouiller ou réanimer un joueur au sol.").defineInRange("range", 3.0, 1.5, 6.0);
      ALLOW_SEARCH = b.comment("Clic droit sur un joueur au sol : ouvre son inventaire.").define("allowSearch", true);
      SEARCH_FREEZES = b.comment("Un joueur en train d'être fouillé ne peut plus ramper.").define("searchFreezesTarget", true);
      b.pop();
      b.push("revive");
      REVIVE_SECONDS = b.comment("Durée de la réanimation (Maj + clic droit), en secondes.").defineInRange("reviveSeconds", 6, 2, 60);
      REVIVE_HEALTH = b.comment("Vie après la réanimation.").defineInRange("reviveHealth", 6.0, 1.0, 40.0);
      PAUSE_BLEED_WHILE_REVIVING = b.comment("Le chrono s'arrête pendant la réanimation.").define("pauseBleedWhileReviving", true);
      GROGGY_SECONDS = b.comment("Lenteur et faiblesse après une réanimation, en secondes (0 = aucune).").defineInRange("groggySeconds", 8, 0, 120);
      b.pop();
      b.push("execution");
      EXECUTIONS_ENABLED = b.comment("Autorise les exécutions (touche Exécuter, I par défaut, maintenue en regardant un joueur au sol).",
            "Une exécution n'est possible que là où un coup normal du bourreau blesserait la victime (PvP, zones protégées).").define("enabled", true);
      EXECUTION_RANGE = b.comment("Distance max (en blocs) pour lancer une exécution.").defineInRange("range", 3.0, 1.5, 6.0);
      FREE_EXECUTIONS = b.comment(
            new String[]{
               "Exécutions accessibles à tous les joueurs. Retirez-en une de cette liste pour la vendre :",
               "elle se débloque alors joueur par joueur avec /reanimation execution unlock <joueur> <id>.",
               "Mains nues (menu des exécutions, touche = par défaut) : coup_de_grace, frappe_orbitale, chute_meteore, combo_aerien, arrache_ame",
               "Armes (automatiques quand le bourreau tient l'arme) : karambit, sea_gleam, butterfly, m9, home_run",
               "Finishers : mille_coupes, singularite, courroux_ciel, souffle_dragon, zero_absolu, danse_ombres, main_joker, faille, onde_celeste,",
               "apesanteur, marionnettiste, freestyle_combo, tempete_sakura, tag_final, jugement_runique ; Noël : cadeau_surprise, sucre_d_orge, bonhomme_de_neige"
            }
         )
         .defineListAllowEmpty(
            "freeExecutions",
            List.of("coup_de_grace", "frappe_orbitale", "chute_meteore", "combo_aerien", "arrache_ame", "karambit", "sea_gleam", "butterfly", "m9", "home_run"),
            o -> o instanceof String
         );
      FREE_FINISHERS = b.comment(
            new String[]{
               "Les finishers (et ceux de Noël) sont accessibles à tous. Mettez false pour les vendre un par un",
               "(ils se débloquent alors avec /reanimation execution unlock <joueur> <id>, ou en les ajoutant à freeExecutions)."
            }
         )
         .define("freeFinishers", true);
      b.pop();
      SPEC = b.build();
   }
}
