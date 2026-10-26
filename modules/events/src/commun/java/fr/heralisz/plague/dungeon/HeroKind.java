package fr.heralisz.plague.dungeon;

/**
 * Décors « héros » de la Station de Quarantaine (modèles GeckoLib). Fichier écrit par tools/props/plague_export.py (ne pas modifier
 * à la main) : identifiant, lumière, particules (0 aucune, 2 vapeur radioactive, 3 étincelles), masque de lueur, animé, étagé
 * (animations s0..s3 pilotées par la salle), lumière seulement allumé, volume (boîtes en px, nord), boîte de rendu {bas, haut,
 * rayon} (blocs), distance d'affichage.
 */
public enum HeroKind {
   PURGE_VALVE("hero_purge_valve", 5, 0, true, true, true, false, new double[][] {{0, 0, 4, 16, 16, 16}}, new double[] {-0.6, 4.0, 2.9}, 192),
   SHUTTER_DOOR("hero_shutter_door", 4, 0, true, true, true, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-0.5, 8.7, 6.2}, 192),
   CURTAIN("hero_curtain", 0, 0, false, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 5.5, 4.2}, 192),
   INCINERATOR("hero_incinerator", 12, 4, false, true, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-0.5, 6.3, 4.2}, 192),
   BODY_PILE("hero_body_pile", 0, 5, false, false, false, false, new double[][] {{0, 0, 0, 16, 10, 16}}, new double[] {-0.5, 1.9, 3.8}, 192),
   FIELD_TENT("hero_field_tent", 0, 0, true, false, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 4.0, 4.4}, 192),
   BARBED_FENCE("hero_barbed_fence", 0, 0, false, false, false, false, new double[][] {{0, 0, 7, 16, 16, 9}}, new double[] {-0.5, 3.6, 3.2}, 192),
   GAS_VENT("hero_gas_vent", 5, 2, true, true, true, false, new double[][] {{0, 0, 0, 16, 5, 16}}, new double[] {-0.6, 1.5, 2.7}, 192),
   QUARANTINE_SIGN("hero_quarantine_sign", 8, 0, true, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 3.3, 3.4}, 192),
   FUNGUS("hero_fungus", 9, 6, true, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 1.5, 2.5}, 192),
   HANGING_BAGS("hero_hanging_bags", 0, 5, false, true, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 6.5, 3.5}, 192),
   DECON_ARCH("hero_decon_arch", 6, 0, true, true, true, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 5.8, 4.3}, 192),
   ALARM_BEACON("hero_alarm_beacon", 12, 0, true, true, true, true, new double[][] {{4, 0, 4, 12, 12, 12}}, new double[] {-0.5, 1.5, 2.1}, 192),
   BARRELS("hero_barrels", 7, 2, true, true, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-1.0, 2.8, 3.6}, 192),
   SLUDGE_VAT("hero_sludge_vat", 8, 2, true, true, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-0.5, 9.6, 4.2}, 192),
   SLUDGE_PIPE("hero_sludge_pipe", 6, 2, true, true, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-1.2, 2.2, 2.8}, 192),
   CAGE_LAMP("hero_cage_lamp", 14, 0, true, true, false, false, new double[][] {{5, 0, 5, 11, 16, 11}}, new double[] {-0.5, 1.5, 2.2}, 192),
   VENT_FAN("hero_vent_fan", 0, 0, true, true, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-0.5, 1.5, 3.1}, 192),
   LOCKERS("hero_lockers", 0, 0, false, false, false, false, new double[][] {{0, 0, 0, 16, 16, 16}}, new double[] {-0.5, 2.4, 2.6}, 192),
   GURNEY("hero_gurney", 0, 0, false, false, false, false, new double[][] {{0, 0, 0, 16, 8, 16}}, new double[] {-0.5, 2.0, 2.8}, 192),
   CORPSE_SOLDIER("hero_corpse_soldier", 0, 5, false, false, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.6, 1.5, 3.0}, 192),
   CORPSE_ZOMBIE("hero_corpse_zombie", 0, 5, true, false, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 1.7, 2.3}, 192),
   BODY_BAG("hero_body_bag", 0, 5, false, false, false, false, new double[][] {{0, 0, 0, 0, 0, 0}}, new double[] {-0.5, 1.5, 2.8}, 192),
   RUBBLE("hero_rubble", 0, 0, false, false, false, false, new double[][] {{0, 0, 0, 16, 6, 16}}, new double[] {-0.5, 1.5, 2.4}, 192),
   HAZMAT_BARREL("hero_hazmat_barrel", 4, 0, true, false, false, false, new double[][] {{2, 0, 2, 14, 16, 14}}, new double[] {-0.5, 1.5, 2.2}, 192),
   BARRICADE("hero_barricade", 0, 0, false, false, false, false, new double[][] {{0, 0, 4, 16, 16, 12}}, new double[] {-0.5, 2.0, 2.6}, 192);

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
