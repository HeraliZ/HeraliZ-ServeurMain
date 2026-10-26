package fr.heralisz.sylvorn.dungeon;

/**
 * Décors immobiles du Nid (modèles OBJ cuits dans les chunks : aucun coût par image) : identifiant, lumière, collé au mur,
 * bloque le passage, volume (boîtes en px, orientation nord).
 */
public enum StaticKind {
   ;

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
