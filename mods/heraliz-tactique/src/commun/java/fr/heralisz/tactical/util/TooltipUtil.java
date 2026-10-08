package fr.heralisz.tactical.util;

import java.util.Locale;

public final class TooltipUtil {
   private TooltipUtil() {
   }

   public static String format(float value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   public static String formatFactor(float factor) {
      return String.format(Locale.ROOT, "x%.2f", factor);
   }

   public static String formatTicks(int ticks) {
      return ticks % 20 == 0 ? ticks / 20 + "s" : String.format(Locale.ROOT, "%.2fs", ticks / 20.0);
   }
}
