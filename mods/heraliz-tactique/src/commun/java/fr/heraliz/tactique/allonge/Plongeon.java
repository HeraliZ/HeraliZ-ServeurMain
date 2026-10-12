package fr.heraliz.tactique.allonge;

/**
 * Plongeon d'un joueur vu par le serveur (fr.heraliz.tactique.allonge.Allonge) : où il en est, quand il a plongé pour la
 * dernière fois, son dernier saut et sa vitesse lissée. Noté sur les données du tireur de TaCZ ({@link EtatAllonge}),
 * rempli et lu côté serveur seulement ({@code fr.heraliz.tactique.serveur.PlongeonServeur}). Heures en millisecondes de
 * {@link Allonge#maintenant()} ; 0 = jamais.
 * <p>
 * Les phases servent aussi au joueur local ({@code fr.heraliz.tactique.client.allonge.AllongeClient}), qui suit son propre
 * plongeon de son côté.
 */
public final class Plongeon {
   /** Pas de plongeon en cours. */
   public static final int AUCUN = 0;
   /** Plongeon accepté par le serveur, le joueur n'a pas encore quitté le sol (l'élan arrive par le réseau). */
   public static final int DECOLLAGE = 1;
   /** En l'air, corps à plat. */
   public static final int VOL = 2;
   /** Retombé au sol : le serveur garde le corps à plat le temps que la demande « s'allonger » du joueur arrive. */
   public static final int ATTERRI = 3;

   /** Phase en cours (AUCUN, DECOLLAGE, VOL, ATTERRI) et heure de son début. */
   public int phase = AUCUN;
   public long phaseA;
   /** Début du plongeon en cours (le coucher, pour la durée sans tir, compte depuis cette heure). */
   public long debutA;
   /** Dernier plongeon accepté (délai entre deux plongeons). */
   public long dernierA;
   /** Dernière demande reçue (les demandes trop rapprochées sont ignorées). */
   public long demandeA;
   /** Dernier saut vu par le serveur. */
   public long sautA;
   /** Position au tick précédent et vitesse horizontale lissée (blocs par tick), vues par le serveur. */
   public boolean suivi;
   public double xPrec;
   public double zPrec;
   public double vx;
   public double vz;

   public boolean enCours() {
      return this.phase != AUCUN;
   }
}
