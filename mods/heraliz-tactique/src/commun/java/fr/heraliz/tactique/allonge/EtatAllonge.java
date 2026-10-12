package fr.heraliz.tactique.allonge;

/**
 * État « allongé » noté par HeraliZ sur les données du tireur de TaCZ ({@code ShooterDataHolder}, une par entité vivante),
 * ajouté par le mixin commun {@code fr.heraliz.tactique.mixin.commun.ShooterDataHolderMixin}. Rempli et lu côté serveur
 * seulement ({@link Allonge#noter}). Heures en millisecondes de {@link Allonge#maintenant()} ; 0 = jamais.
 */
public interface EtatAllonge {
   /** Dernier état vu (allongé ou debout), pour repérer les passages de l'un à l'autre. */
   boolean heraliz$getEtaitAllonge();

   void heraliz$setEtaitAllonge(boolean allonge);

   /** Heure du dernier passage debout -> allongé. */
   long heraliz$getCoucheA();

   void heraliz$setCoucheA(long heure);

   /** Heure du dernier passage allongé -> debout. */
   long heraliz$getReleveA();

   void heraliz$setReleveA(long heure);
}
