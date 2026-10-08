package fr.heralisz.sylvorn.dungeon;

/**
 * Décors « héros » de l'Arbre-Monde (modèles GeckoLib). Fichier écrit par tools/props/sylvorn_export.py (ne pas modifier à la
 * main) : identifiant, lumière, particules (0 aucune, 1 flamme, 3 sève qui goutte, 4 spores), masque de lueur, animé, étagé
 * (animations s0..s3 pilotées par la salle), lumière seulement allumé, volume (boîtes en px, nord), boîte de rendu {bas, haut,
 * rayon} (blocs), distance d'affichage.
 */
public enum HeroKind {
   BONE_PILE("hero_bone_pile", 0, 0, false, false, false, false, new double[][] {{0, 0, 0, 16, 5, 16}}, new double[] {-0.8, 1.5, 2.0}, 72),
   SKULL_SMALL("hero_skull_small", 6, 1, true, true, true, true, new double[][] {{4, 0, 4, 12, 9, 12}}, new double[] {-0.5, 1.6, 1.0}, 72),
   CANDLES("hero_candles", 8, 1, true, true, true, true, new double[][] {{1, 0, 1, 15, 4, 15}}, new double[] {-0.6, 1.5, 1.1}, 72),
   HEART("hero_heart", 15, 3, true, true, true, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-1.1, 25.7, 7.3}, 192),
   ROOT_DOOR("hero_root_door", 0, 0, true, true, true, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-1.3, 11.0, 6.3}, 192),
   ALTAR("hero_altar", 8, 3, true, true, true, false, new double[][] {{1, 0, 1, 15, 16, 15}}, new double[] {-0.7, 4.8, 3.2}, 192),
   BARK("hero_bark", 0, 0, true, true, true, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 12.4, 5.4}, 192);

   public final String id;
   public final int light, fx;
   public final boolean glow, animated, staged, litOnly;
   public final double[][] shape;
   public final double[] bounds;
   public final int viewDistance;

   HeroKind(String id, int light, int fx, boolean glow, boolean animated, boolean staged, boolean litOnly, double[][] shape, double[] bounds,
            int viewDistance) {
      this.id = id;
      this.light = light;
      this.fx = fx;
      this.glow = glow;
      this.animated = animated;
      this.staged = staged;
      this.litOnly = litOnly;
      this.shape = shape;
      this.bounds = bounds;
      this.viewDistance = viewDistance;
   }
}
