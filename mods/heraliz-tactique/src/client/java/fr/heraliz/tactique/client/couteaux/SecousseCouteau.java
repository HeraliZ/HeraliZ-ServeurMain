package fr.heraliz.tactique.client.couteaux;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Petit mouvement de caméra des coups de couteau, chez le joueur qui frappe, en 1re personne seulement (comme l'os
 * « camera » des animations des couteaux de LR, que les modèles des gunpacks n'ont pas). Rien n'est envoyé au serveur :
 * seule l'image bouge, pas le regard.
 * <ul>
 *    <li>tranche (coup rapide) : la vue suit à peine la lame, vers la droite ou vers la gauche (moins de 1°) ;</li>
 *    <li>estoc (coup puissant) : la vue se relève un peu pendant qu'on arme, puis plonge et avance avec la poussée (champ
 *    de vision resserré), et revient doucement.</li>
 * </ul>
 * Temps calés sur les animations de {@code outils/couteaux_coups.py} (lame au milieu à 0,11 s, poussée de l'estoc de
 * 0,22 à 0,27 s) : à changer ensemble.
 */
final class SecousseCouteau {
   // clés : temps (s), tangage (+ = vers le bas), lacet (+ = vers la droite), roulis (°), champ de vision ajouté (°)
   private static final float[][] TRANCHE = {
      {0.00F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.06F, -0.3F, -0.5F, -0.6F, 0.0F},
      {0.14F, 0.4F, 0.8F, 0.9F, 0.0F},
      {0.26F, 0.1F, 0.2F, 0.2F, 0.0F},
      {0.40F, 0.0F, 0.0F, 0.0F, 0.0F},
   };
   private static final float[][] ESTOC = {
      {0.00F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.20F, -1.2F, 0.3F, -1.0F, 0.6F},
      {0.27F, 2.4F, -0.4F, 1.4F, -3.5F},
      {0.34F, 2.0F, -0.3F, 0.9F, -3.0F},
      {0.50F, 0.8F, 0.0F, 0.3F, -1.2F},
      {0.80F, 0.0F, 0.0F, 0.0F, 0.0F},
   };

   private static float[][] courbe;
   /** Sens de la tranche : 1 de gauche à droite, -1 de droite à gauche. */
   private static float sens = 1.0F;
   private static long debutNs;

   private SecousseCouteau() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.ComputeCameraAngles.class, SecousseCouteau::angles);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.ComputeFov.class, SecousseCouteau::champ);
   }

   /** Coup parti : tranche (sens : vrai = de gauche à droite) ou estoc. */
   static void lancer(boolean lourd, boolean gaucheDroite) {
      courbe = lourd ? ESTOC : TRANCHE;
      sens = gaucheDroite ? 1.0F : -1.0F;
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
         float s = courbe == TRANCHE ? sens : 1.0F;
         e.setPitch(e.getPitch() + v[0]);
         e.setYaw(e.getYaw() + v[1] * s);
         e.setRoll(e.getRoll() + v[2] * s);
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
