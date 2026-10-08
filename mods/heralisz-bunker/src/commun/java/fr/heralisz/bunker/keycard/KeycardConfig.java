package fr.heralisz.bunker.keycard;

import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages serveur des cartes d'accès (config/heralisz_bunker-common.toml). */
public final class KeycardConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue USES_GREEN, USES_BLUE, USES_RED, PULSE_TICKS;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.comment("Cartes d'accès (lecteurs de cartes façon Rust)").push("keycards");
      USES_GREEN = b.comment("Passages avant que la carte verte casse (0 = illimité)").defineInRange("usesGreen", 10, 0, 10000);
      USES_BLUE = b.comment("Passages avant que la carte bleue casse (0 = illimité)").defineInRange("usesBlue", 8, 0, 10000);
      USES_RED = b.comment("Passages avant que la carte rouge casse (0 = illimité)").defineInRange("usesRed", 6, 0, 10000);
      PULSE_TICKS = b.comment("Durée du signal redstone après un accès accordé (ticks, 20 = 1 s)").defineInRange("pulseTicks", 100, 10, 12000);
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

   private KeycardConfig() {}
}
