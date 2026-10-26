package fr.heralisz.sylvorn.client.forest;

import net.minecraft.client.renderer.LightTexture;

/**
 * Lisibilité des boss : dans les donjons (sous terre, peu de lumière), un boss rendu avec la lumière réelle du bloc n'est
 * qu'une silhouette noire. On garantit un plancher d'éclairage de bloc au rendu du boss (et de sa dépouille) : il reste
 * dans l'ambiance sombre de la salle, mais on voit sa forme, ses textures et ses animations. La lumière du ciel ne bouge pas.
 */
public final class BossLight {
   /** Lumière de bloc minimale (0-15) sur le modèle d'un boss. */
   public static final int MIN_BLOCK = 12;

   private BossLight() {}

   public static int lift(int packed) {
      return LightTexture.pack(Math.max(LightTexture.block(packed), MIN_BLOCK), LightTexture.sky(packed));
   }
}
