package fr.heralisz.mere.dungeon;

/**
 * Décors « héros » du Nid (modèles GeckoLib rendus tels quels) : identifiant, lumière, particules (0 aucune, 1 flamme, 3 ambre qui
 * goutte, 4 spores luisantes), masque de lueur, animé, étagé (animations s0..s3 pilotées par la salle), lumière seulement allumé
 * (étape >= 1), volume (boîtes en px, orientation nord), boîte de rendu {bas, haut, rayon} (blocs), distance d'affichage.
 */
public enum HeroKind {
   BONE_PILE("hero_bone_pile", 0, 0, false, false, false, false, new double[][] {{0, 0, 0, 16, 5, 16}}, new double[] {-0.8, 1.5, 2.0}, 72),
   SKULL_SMALL("hero_skull_small", 6, 1, true, true, true, true, new double[][] {{4, 0, 4, 12, 9, 12}}, new double[] {-0.5, 1.6, 1.0}, 72),
   CANDLES("hero_candles", 8, 1, true, true, true, true, new double[][] {{1, 0, 1, 15, 4, 15}}, new double[] {-0.6, 1.5, 1.1}, 72),
   /** Grappe d'œufs pendue à la voûte : elle palpite, elle luit (ambre) ; ses rejetons en descendent au bout d'un fil. */
   BROOD_CLUSTER("hero_brood_cluster", 12, 3, true, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 3.2, 1.3}, 112),
   /** Petite grappe-lanterne (lumière d'ambiance du nid). */
   BROOD_LAMP("hero_brood_lamp", 10, 4, true, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 1.4, 0.6}, 80),
   /** Victime emmaillotée qui pend de la voûte et tourne lentement au bout de sa soie. */
   WRAPPED_VICTIM("hero_wrapped_victim", 0, 0, false, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-4.3, 1.3, 0.7}, 80),
   /** L'autel de couvée, et au-dessus de l'arène le cocon géant où dort la Mère : s0 il bat, s1 il s'agite, s2 il se déchire,
    *  s3 déchiré (vide). */
   /** La porte de toile de la chapelle des offrandes : s0 fermée, s1 elle se déchire, s2 ouverte, s3 elle se retisse. */
   WEB_DOOR("hero_web_door", 0, 0, false, true, true, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 10.0, 5.0}, 112),
   SHRINE("hero_shrine", 6, 0, true, true, true, false, new double[][] {{0, 0, 1, 16, 14, 15}}, new double[] {-0.5, 40.0, 24.0}, 192);

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
