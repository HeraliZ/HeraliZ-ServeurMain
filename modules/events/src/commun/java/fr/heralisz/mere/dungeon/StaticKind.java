package fr.heralisz.mere.dungeon;

/**
 * Décors immobiles du Nid (modèles OBJ cuits dans les chunks : aucun coût par image) : identifiant, lumière, collé au mur,
 * bloque le passage, volume (boîtes en px, orientation nord).
 */
public enum StaticKind {
   BONE_CANDLES("nest_bone_candles", 10, false, false, new double[][] {{3, 0, 3, 13, 9, 13}}),
   BONE_PILE("nest_bone_pile", 0, false, false, new double[][] {{1, 0, 1, 15, 4, 15}}),
   CANDLE_CLUSTER("nest_candle_cluster", 10, false, false, new double[][] {{3, 0, 3, 13, 10, 13}}),
   COCOON_BODY("nest_cocoon_body", 0, false, false, new double[][] {{4, 0, 0, 12, 7, 16}}),
   EGG_CLUSTER("nest_egg_cluster", 7, false, false, new double[][] {{1, 0, 1, 15, 6, 15}}),
   EXPLORER_LANTERN("nest_explorer_lantern", 12, false, false, new double[][] {{2, 0, 2, 14, 8, 14}}),
   GIANT_WEB("nest_giant_web", 0, true, false, new double[][] {{0, 0, 15, 16, 16, 16}}),
   HANGING_COCOON("nest_hanging_cocoon", 0, false, false, new double[][] {{4, 0, 4, 12, 16, 12}}),
   HUSK_PILE("nest_husk_pile", 0, false, false, new double[][] {{1, 0, 1, 15, 8, 15}}),
   SPIDER_EGGS_WALL("nest_spider_eggs_wall", 7, true, false, new double[][] {{0, 0, 11, 16, 16, 16}}),
   SPIDER_IDOL("nest_spider_idol", 0, false, true, new double[][] {{0, 0, 0, 16, 16, 16}}),
   WEBBED_SKELETON("nest_webbed_skeleton", 0, true, false, new double[][] {{1, 0, 10, 15, 16, 16}}),
   BLOOD_POOL("nest_blood_pool", 0, false, false, new double[][] {{1, 0, 1, 15, 0.5, 15}}),
   BLOOD_SPLAT("nest_blood_splat", 0, false, false, new double[][] {{1, 0, 1, 15, 0.5, 15}}),
   BLOOD_DRAG("nest_blood_drag", 0, false, false, new double[][] {{1, 0, 1, 15, 0.5, 15}});

   public final String id;
   public final int light;
   public final boolean wall, collide;
   public final double[][] shape;

   StaticKind(String id, int light, boolean wall, boolean collide, double[][] shape) {
      this.id = id;
      this.light = light;
      this.wall = wall;
      this.collide = collide;
      this.shape = shape;
   }
}
