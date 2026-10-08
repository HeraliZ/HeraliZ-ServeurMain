package fr.heralisz.bunker.keycard;

import net.minecraft.ChatFormatting;

/** Niveaux d'accès façon Rust : chaque lecteur n'accepte que la carte de sa couleur. */
public enum AccessLevel {
   GREEN("green", ChatFormatting.GREEN, 1),
   BLUE("blue", ChatFormatting.BLUE, 2),
   RED("red", ChatFormatting.RED, 3);

   public final String id;
   public final ChatFormatting color;
   public final int tier;

   AccessLevel(String id, ChatFormatting color, int tier) {
      this.id = id;
      this.color = color;
      this.tier = tier;
   }
}
