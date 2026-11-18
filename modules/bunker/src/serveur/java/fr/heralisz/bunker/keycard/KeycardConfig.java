package fr.heralisz.bunker.keycard;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages serveur des cartes d'accès (config/heralisz_bunker-common.toml). Audit du 02/11 (point 11) : réglage du serveur
 * seulement, déclaré par la partie serveur (ReglagesCartesServeur) avec le même nom de fichier ; plus jamais écrit sur le PC
 * des joueurs. Ce que lit le code commun (passages, durée du signal) est recopié dans {@link ReglagesCartes}.
 */
public final class KeycardConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue USES_GREEN, USES_BLUE, USES_RED, USES_BUNKER, PULSE_TICKS;
   public static final ForgeConfigSpec.IntValue ENTREE, FERMETURE, RECHARGE_GREEN, RECHARGE_BLUE, RECHARGE_RED;
   public static final ForgeConfigSpec.IntValue BUNKER_MINUTES, BUNKER_AVERTIR;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.comment("Cartes d'accès (lecteurs de cartes façon Rust)").push("keycards");
      // 18/11 (demande du propriétaire) : verte 4, bleue 3, rouge 2, cartes des bunkers 2 ; au dernier passage, la carte
      // se casse et disparaît (jamais réparable)
      USES_GREEN = b.comment("Passages avant que la carte verte casse (0 = illimité)").defineInRange("usesGreen", 4, 0, 10000);
      USES_BLUE = b.comment("Passages avant que la carte bleue casse (0 = illimité)").defineInRange("usesBlue", 3, 0, 10000);
      USES_RED = b.comment("Passages avant que la carte rouge casse (0 = illimité)").defineInRange("usesRed", 2, 0, 10000);
      USES_BUNKER = b.comment("Passages avant qu'une carte de bunker (Bunker des Bois, Bunker du Pic) casse (0 = illimité)")
         .defineInRange("usesBunker", 2, 0, 10000);
      // 18/11 (demande du propriétaire : « doubler le temps avant que la porte se referme si personne n'entre ») : 200
      PULSE_TICKS = b.comment("Durée du signal redstone après un accès accordé (ticks, 20 = 1 s)").defineInRange("pulseTicks", 200, 10, 12000);
      b.pop();
      b.comment("Salles à carte (délimitées par le staff avec /salle) : porte tenue ouverte et loot de la salle").push("salles");
      ENTREE = b.comment("Secondes pour entrer dans la salle une fois la porte ouverte ; personne dedans à la fin : elle se referme")
         .defineInRange("entreeSecondes", 20, 1, 600);
      // 18/11 (demande du propriétaire : « quand on sort, le délai 2 fois plus long avant que ça se referme ») : 4 (avant 2)
      FERMETURE = b.comment("Secondes d'attente avant que la porte se referme quand le dernier joueur est sorti")
         .defineInRange("fermetureSecondes", 4, 0, 60);
      RECHARGE_GREEN = b.comment("Minutes, après la sortie du dernier joueur, avant que le loot d'une salle verte réapparaisse")
         .defineInRange("rechargeVerteMinutes", 30, 1, 10080);
      RECHARGE_BLUE = b.comment("Minutes avant que le loot d'une salle bleue réapparaisse").defineInRange("rechargeBleueMinutes", 120, 1, 10080);
      RECHARGE_RED = b.comment("Minutes avant que le loot d'une salle rouge réapparaisse").defineInRange("rechargeRougeMinutes", 240, 1, 10080);
      b.pop();
      b.comment("Portes de bunker (17/11) : désignées par le staff (/salle bunker ajouter [nom], en regardant la porte), ouvertes",
         "seulement par un lecteur de carte posé à 2 blocs au plus (n'importe quelle couleur : la carte du lecteur posé),",
         "annoncées à tout le serveur, puis refermées").push("bunkers");
      // 18/11 (demande du propriétaire) : 20 min ouverte, alerte dans le tchat 5 min avant la fermeture
      BUNKER_MINUTES = b.comment("Minutes pendant lesquelles la porte reste ouverte après un passage de carte (temps réel)")
         .defineInRange("ouvertureMinutes", 20, 1, 1440);
      BUNKER_AVERTIR = b.comment("Secondes avant la fermeture où tout le serveur est prévenu dans le tchat (0 = jamais)")
         .defineInRange("avertirSecondes", 300, 0, 3600);
      b.pop();
      SPEC = b.build();
   }

   /** Délai de recharge du loot d'une salle de cette couleur (ticks de jeu, 20 = 1 s). */
   public static long rechargeTicks(AccessLevel level) {
      // cartes des bunkers : jamais de salle à carte (comptées comme rouges)
      int min = !SPEC.isLoaded() ? switch (level) {
         case GREEN -> 30;
         case BLUE -> 120;
         case RED, BUNKER_BOIS, BUNKER_PIC -> 240;
      } : switch (level) {
         case GREEN -> RECHARGE_GREEN.get();
         case BLUE -> RECHARGE_BLUE.get();
         case RED, BUNKER_BOIS, BUNKER_PIC -> RECHARGE_RED.get();
      };
      return 1200L * min;
   }

   public static int entreeTicks() {
      return 20 * (SPEC.isLoaded() ? ENTREE.get() : 20);
   }

   public static int fermetureTicks() {
      return 20 * (SPEC.isLoaded() ? FERMETURE.get() : 4);
   }

   private KeycardConfig() {}
}
