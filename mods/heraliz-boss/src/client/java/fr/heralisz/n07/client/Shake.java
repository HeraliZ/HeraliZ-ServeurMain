package fr.heralisz.n07.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Tremblement de caméra (impacts, rugissements, explosions) : bruit lissé qui décroît. */
public final class Shake {
   private static float power;
   private static long end, startTick;
   private static int duration = 1;

   private Shake() {}

   /** Plafond d'intensité, et part gardée en plein combat (hors cinématique) : ça se sent sans gêner la visée. */
   private static final float MAX = 0.8F, IN_COMBAT = 0.45F;

   public static void add(float intensity, int ticks) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      intensity = Math.min(intensity, MAX) * (Cinematic.active() ? 1F : IN_COMBAT);
      if (!Cinematic.active()) ticks = Math.min(ticks, 16);
      long now = mc.level.getGameTime();
      float left = current(0);
      if (intensity >= left) {
         power = intensity;
         startTick = now;
         duration = Math.max(1, ticks);
         end = now + ticks;
      }
   }

   /** Intensité restante (décroissance quadratique). */
   public static float current(float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return 0;
      double now = mc.level.getGameTime() + partial;
      if (now >= end) return 0;
      float k = (float) ((now - startTick) / duration);
      return power * (1 - k) * (1 - k);
   }

   /** Décalage [yaw, pitch] en degrés. */
   public static float[] offset(float partial) {
      float p = current(partial);
      if (p <= 0) return null;
      Minecraft mc = Minecraft.getInstance();
      float t = (float) ((mc.level.getGameTime() + partial) * 1.7);
      return new float[] {p * (Mth.sin(t * 2.1F) + 0.5F * Mth.sin(t * 5.3F + 1)) * 1.6F, p * (Mth.cos(t * 2.7F) + 0.5F * Mth.sin(t * 4.1F)) * 1.6F};
   }
}
