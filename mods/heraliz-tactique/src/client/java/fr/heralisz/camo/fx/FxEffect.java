package fr.heralisz.camo.fx;

/** Un effet 3D de skin : construit sa géométrie pour l'image courante dans c.mesh. */
@FunctionalInterface
public interface FxEffect {
   void build(FxContext c);

   /**
    * Pièces fondues dans l'arme (tête au canon, écailles, queue…) : elles font partie de l'arme et restent pleinement
    * visibles en 1re personne (elles sont dessinées pour ne jamais passer dans la ligne de visée), au lieu du simple soupçon
    * réservé aux effets qui flottent autour.
    */
   default boolean fused() {
      return false;
   }

   /**
    * Part de l'effet encore visible pour le porteur, arme en main en 1re personne (multiplie le « soupçon » de CamoFx) :
    * 1 par défaut, moins pour un effet qui encombre la vue (chaînes).
    */
   default float heldFade() {
      return 1F;
   }

   /**
    * Effet montré en entier au porteur même arme en main (il ne gêne pas la visée : objets éjectés sur le côté comme des
    * douilles) ; le shader dégage toujours le centre de l'écran.
    */
   default boolean fullInHand() {
      return false;
   }
}
