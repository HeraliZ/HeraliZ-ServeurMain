package fr.heralisz.n07.api;

import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

/** Combat pilote en cours (fil serveur). Cree par N07Api.prepare / N07Api.start. */
public interface Encounter {
   enum State {
      /** Boss endormi dans sa cuve (intro). */
      PREPARED,
      /** Cinematique d'apparition. */
      SPAWNING,
      /** Combat. */
      FIGHT,
      /** Cinematique de mort. */
      DYING,
      /** Vaincu (statue posee). */
      VICTORY,
      /** Interrompu / nettoye. */
      ABORTED
   }

   State state();

   EncounterSpec spec();

   /** Le boss (null apres sa disparition). */
   @Nullable
   Mob boss();

   /** Phase en cours (1..3). */
   int phase();

   /** PV restants 0..1 (affichage). */
   float healthFraction();

   /** PV virtuels en vigueur. */
   int virtualHp();

   /** Reveille le boss endormi : cinematique d'apparition (12 s) puis combat. Sans effet hors PREPARED. */
   void wake();

   /** Passe la cinematique en cours (apparition ou mort) comme si les joueurs l'avaient votee. */
   void skipCinematic();

   /**
    * Arrete tout : boss, projectiles, flaques, statue retires ; la salle n'est pas remise a zero (voir PlacedHall.reset).
    * Evenement onAborted.
    */
   void abort(String reason);

   /** Vrai tant que le combat n'est ni gagne ni interrompu. */
   boolean active();
}
