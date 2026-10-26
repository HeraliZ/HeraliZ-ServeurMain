package fr.heralisz.n07.api.scene;

/**
 * Comment une scene (ou une cinematique du boss en combat pilote) peut etre passee. Les spectateurs maintiennent la touche
 * de saut (ESPACE par defaut) ~0,6 s ; l'invite s'affiche en bas a droite avec le compte des votes.
 */
public enum SkipMode {
   /** Jamais. */
   NONE,
   /** Des que plus de la moitie des spectateurs maintiennent la touche. */
   MAJORITY,
   /** Quand tous les spectateurs la maintiennent. */
   ALL,
   /** Le premier qui la maintient passe pour tout le monde. */
   ANY,
   /** Chacun pour soi : la vue de ce joueur s'arrete, la scene continue pour les autres (actions serveur incluses). */
   PER_PLAYER
}
