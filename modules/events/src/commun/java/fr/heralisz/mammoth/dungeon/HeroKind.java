package fr.heralisz.mammoth.dungeon;

/**
 * Décors « héros » (modèles GeckoLib rendus tels quels). Fichier écrit par tools/props/hero_mammoth.py (ne pas modifier à la
 * main) : identifiant, lumière, particules (0 aucune, 1 feu, 3 givre), masque de lueur, volume (boîtes en px, orientation nord).
 */
public enum HeroKind {
   PLANTED_SPEAR("hero_planted_spear", 0, 0, false, false, new double[][] {{6, 0, 6, 10, 16, 10}}),
   BONE_TORCH("hero_bone_torch", 14, 1, true, true, new double[][] {{5, 5, 9, 11, 16, 16}}),
   BONE_CHIMES("hero_bone_chimes", 0, 0, false, true, new double[][] {{6, 4, 6, 10, 16, 10}}),
   PELT_BANNER("hero_pelt_banner", 0, 0, true, true, new double[][] {{6, 0, 6, 10, 16, 10}}),
   KNEELING_HUNTER("hero_kneeling_hunter", 0, 0, true, false, new double[][] {{3, 0, 3, 13, 20, 13}}),
   ANCESTOR_SKULL("hero_ancestor_skull", 0, 0, true, false, new double[][] {{-8, 0, -8, 24, 24, 24}}),
   GLACIER_BOULDER("hero_glacier_boulder", 6, 3, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}),
   HIDE_TENT("hero_hide_tent", 6, 0, true, false, new double[][] {{-8, 0, -8, 24, 32, 24}}),
   ICE_STALAGMITE("hero_ice_stalagmite", 7, 3, true, false, new double[][] {{2, 0, 2, 14, 16, 14}}),
   BONE_HEAP("hero_bone_heap", 0, 0, false, false, new double[][] {{1, 0, 1, 15, 5, 15}}),
   OFFERING_PILE("hero_offering_pile", 4, 0, true, false, new double[][] {{1, 0, 1, 15, 6, 15}}),
   WAR_DRUM("hero_war_drum", 0, 0, false, false, new double[][] {{1, 0, 1, 15, 15, 15}}),
   SPEAR_RACK("hero_spear_rack", 0, 0, false, false, new double[][] {{0, 0, 10, 16, 16, 15}}),
   HUNTER_PYRE("hero_hunter_pyre", 9, 1, true, false, new double[][] {{0, 0, 0, 16, 14, 16}}),
   IMPALED_HUNTER("hero_impaled_hunter", 0, 0, false, false, new double[][] {{6, 0, 6, 10, 16, 10}}),
   MAMMOTH_SKULL("hero_mammoth_skull", 0, 0, false, false, new double[][] {{1, 0, 0, 15, 14, 16}});

   public final String id;
   public final int light, fx;
   public final boolean glow, animated;
   public final double[][] shape;

   HeroKind(String id, int light, int fx, boolean glow, boolean animated, double[][] shape) {
      this.id = id;
      this.light = light;
      this.fx = fx;
      this.glow = glow;
      this.animated = animated;
      this.shape = shape;
   }
}
