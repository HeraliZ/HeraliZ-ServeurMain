package fr.heralisz.bunker.keycard;

import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages serveur des cartes d'accès (config/heralisz_bunker-common.toml). */
public final class KeycardConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue USES_GREEN, USES_BLUE, USES_RED, PULSE_TICKS;
   public static final ForgeConfigSpec.IntValue ENTREE, FERMETURE, RECHARGE_GREEN, RECHARGE_BLUE, RECHARGE_RED;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.comment("Cartes d'accès (lecteurs de cartes façon Rust)").push("keycards");
      USES_GREEN = b.comment("Passages avant que la carte verte casse (0 = illimité)").defineInRange("usesGreen", 10, 0, 10000);
      USES_BLUE = b.comment("Passages avant que la carte bleue casse (0 = illimité)").defineInRange("usesBlue", 8, 0, 10000);
      USES_RED = b.comment("Passages avant que la carte rouge casse (0 = illimité)").defineInRange("usesRed", 6, 0, 10000);
      PULSE_TICKS = b.comment("Durée du signal redstone après un accès accordé (ticks, 20 = 1 s)").defineInRange("pulseTicks", 100, 10, 12000);
      b.pop();
      b.comment("Salles à carte (délimitées par le staff avec /salle) : porte tenue ouverte et loot de la salle").push("salles");
      ENTREE = b.comment("Secondes pour entrer dans la salle une fois la porte ouverte ; personne dedans à la fin : elle se referme")
         .defineInRange("entreeSecondes", 10, 1, 600);
      FERMETURE = b.comment("Secondes d'attente avant que la porte se referme quand le dernier joueur est sorti")
         .defineInRange("fermetureSecondes", 2, 0, 60);
      RECHARGE_GREEN = b.comment("Minutes, après la sortie du dernier joueur, avant que le loot d'une salle verte réapparaisse")
         .defineInRange("rechargeVerteMinutes", 30, 1, 10080);
      RECHARGE_BLUE = b.comment("Minutes avant que le loot d'une salle bleue réapparaisse").defineInRange("rechargeBleueMinutes", 120, 1, 10080);
      RECHARGE_RED = b.comment("Minutes avant que le loot d'une salle rouge réapparaisse").defineInRange("rechargeRougeMinutes", 240, 1, 10080);
      b.pop();
      SPEC = b.build();
   }

   public static int uses(AccessLevel level) {
      return switch (level) {
         case GREEN -> USES_GREEN.get();
         case BLUE -> USES_BLUE.get();
         case RED -> USES_RED.get();
      };
   }

   /** Délai de recharge du loot d'une salle de cette couleur (ticks de jeu, 20 = 1 s). */
   public static long rechargeTicks(AccessLevel level) {
      int min = !SPEC.isLoaded() ? switch (level) {
         case GREEN -> 30;
         case BLUE -> 120;
         case RED -> 240;
      } : switch (level) {
         case GREEN -> RECHARGE_GREEN.get();
         case BLUE -> RECHARGE_BLUE.get();
         case RED -> RECHARGE_RED.get();
      };
      return 1200L * min;
   }

   public static int entreeTicks() {
      return 20 * (SPEC.isLoaded() ? ENTREE.get() : 10);
   }

   public static int fermetureTicks() {
      return 20 * (SPEC.isLoaded() ? FERMETURE.get() : 2);
   }

   private KeycardConfig() {}
}
