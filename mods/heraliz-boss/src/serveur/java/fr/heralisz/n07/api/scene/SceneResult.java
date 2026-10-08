package fr.heralisz.n07.api.scene;

/** Fin d'une scene. */
public enum SceneResult {
   /** Jouee jusqu'au bout. */
   COMPLETED,
   /** Passee par les spectateurs (les actions serveur « essentielles » restantes ont ete appliquees). */
   SKIPPED,
   /** Arretee par le code (SceneHandle.cancel, monde decharge, plus aucun spectateur). */
   CANCELLED
}
