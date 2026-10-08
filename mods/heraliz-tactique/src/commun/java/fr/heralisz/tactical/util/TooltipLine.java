package fr.heralisz.tactical.util;

import net.minecraft.network.chat.Component;

public record TooltipLine(Component text, boolean collapsible) {
   public static TooltipLine normal(Component text) {
      return new TooltipLine(text, false);
   }

   public static TooltipLine collapsible(Component text) {
      return new TooltipLine(text, true);
   }
}
