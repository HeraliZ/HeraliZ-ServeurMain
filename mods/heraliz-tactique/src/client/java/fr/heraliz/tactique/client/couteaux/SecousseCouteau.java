package fr.heraliz.tactique.client.couteaux;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Petit mouvement de caméra du coup puissant des couteaux, chez le joueur qui frappe, en 1re personne seulement (comme
 * l'os « camera » des animations des couteaux de LR, que les modèles des gunpacks n'ont pas). Rien n'est envoyé au
 * serveur : seule l'image bouge, pas le regard. La vue se relève un peu pendant qu'on arme, puis plonge et avance avec
 * la poussée (champ de vision resserré), et revient doucement. Le coup rapide n'en a pas (comme avant la 2.16.0).
 * Temps calés sur l'animation de {@code outils/couteaux_coups.py} (armé à 0,16 s, impact à 0,24 s) : à changer ensemble.
 */
final class SecousseCouteau {
   // clés : temps (s), tangage (+ = vers le bas), lacet (+ = vers la droite), roulis (°), champ de vision ajouté (°)
   private static final float[][] ESTOC = {
      {0.00F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.16F, -1.2F, 0.3F, -1.0F, 0.6F},
      {0.24F, 2.4F, -0.4F, 1.4F, -3.5F},
      {0.31F, 2.0F, -0.3F, 0.9F, -3.0F},
      {0.48F, 0.8F, 0.0F, 0.3F, -1.2F},
      {0.80F, 0.0F, 0.0F, 0.0F, 0.0F},
   };

   private static float[][] courbe;
   private static long debutNs;

   private SecousseCouteau() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.ComputeCameraAngles.class, SecousseCouteau::angles);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.ComputeFov.class, SecousseCouteau::champ);
   }

   /** Coup puissant parti. */
   static void lancer() {
      courbe = ESTOC;
      debutNs = System.nanoTime();
   }

   private static float[] valeur() {
      float[][] c = courbe;
      Minecraft mc = Minecraft.getInstance();
      if (c == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
         return null;
      }
      float t = (System.nanoTime() - debutNs) / 1.0E9F;
      if (t >= c[c.length - 1][0]) {
         courbe = null;
         return null;
      }
      for (int i = 1; i < c.length; i++) {
         if (t <= c[i][0]) {
            float f = (t - c[i - 1][0]) / (c[i][0] - c[i - 1][0]);
            f = f * f * (3.0F - 2.0F * f);
            float[] v = new float[4];
            for (int k = 0; k < 4; k++) {
               v[k] = c[i - 1][k + 1] + (c[i][k + 1] - c[i - 1][k + 1]) * f;
            }
            return v;
         }
      }
      return null;
   }

   private static void angles(ViewportEvent.ComputeCameraAngles e) {
      float[] v = valeur();
      if (v != null) {
         e.setPitch(e.getPitch() + v[0]);
         e.setYaw(e.getYaw() + v[1]);
         e.setRoll(e.getRoll() + v[2]);
      }
   }

   private static void champ(ViewportEvent.ComputeFov e) {
      if (!e.usedConfiguredFov()) {
         return; // champ de la main (rendu de l'arme) : on ne touche qu'à celui du monde
      }
      float[] v = valeur();
      if (v != null && v[3] != 0.0F) {
         e.setFOV(e.getFOV() + v[3]);
      }
   }
}
