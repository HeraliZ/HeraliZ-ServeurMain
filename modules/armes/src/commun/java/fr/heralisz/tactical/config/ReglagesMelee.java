package fr.heralisz.tactical.config;

/**
 * Réglage du serveur lu par le code commun des coups de mêlée (IMeleeWeapon.attackTarget, qui ne tourne que sur le
 * serveur). Audit du 02/11 (point 11) : CommonConfig (heralisz_tactical-common.toml) est maintenant un réglage du serveur
 * seulement, déclaré par sa partie serveur ; elle recopie ici la valeur à chaque chargement du fichier. Sans elle (jeu
 * des joueurs), la valeur d'origine reste.
 */
public final class ReglagesMelee {
   private ReglagesMelee() {}

   /** Valeur d'origine de meleeIgnoreInvulnerableTickThreshold. */
   public static final int SEUIL_INVULNERABLE_DEFAUT = 20;

   /** Si la cible a moins de ticks d'invulnérabilité que ce seuil, un coup de mêlée l'ignore (réglage du serveur). */
   public static volatile int seuilInvulnerable = SEUIL_INVULNERABLE_DEFAUT;
}
