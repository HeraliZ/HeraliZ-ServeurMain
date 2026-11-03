package fr.heraliz.tactique.tir;

/**
 * Ajouté aux données du tireur de TaCZ (ShooterDataHolder, mixin ShooterDataHolderHorlogeMixin) : dernier recalage de
 * l'horloge des tirs ({@link HorlogeTirs}). Fil du serveur seulement.
 */
public interface HorlogeTir {
   /** Base de l'horloge avant le dernier recalage. */
   long heraliz$ancienneBase();

   /** Heure (System.currentTimeMillis) du dernier recalage, 0 si jamais. */
   long heraliz$recaleA();

   void heraliz$noterRecalage(long ancienneBase, long quand);
}
