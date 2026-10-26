package fr.heralisz.n07.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Evenements d'un combat pilote (fil serveur). Toutes les methodes ont un corps vide par defaut : n'implementer que celles
 * dont on a besoin. Une exception levee ici est journalisee et n'interrompt pas le boss.
 */
public interface N07Events {
   /** Une cinematique du boss commence (kind : N07Api.CINE_SPAWN / CINE_PHASE2 / CINE_PHASE3 / CINE_DEATH) pour ces joueurs,
    * proteges (aucun degat) pendant ticks. */
   default void onCinematicStart(Encounter e, int kind, int ticks, List<ServerPlayer> viewers) {}

   /** Fin (ou passage) d'une cinematique d'apparition ou de phase. La mort a ses propres evenements. */
   default void onCinematicEnd(Encounter e, int kind) {}

   /** Le boss change de phase (2 ou 3) : appele au debut de la cinematique de transition. */
   default void onPhase(Encounter e, int phase) {}

   /**
    * Crochet du scenario pour le jeu (voir N07Api.HOOK_*) : ex. « p2_fin » a la fin de la cinematique de phase 2 (le jeu
    * lache un Max Ammo, des chiens...). Aussi les crochets des scenes d'intro / outro (porte, ascenseur...).
    */
   default void onHook(Encounter e, String hook) {}

   /** Le boss est vaincu : sa cinematique de mort (13 s) commence. */
   default void onDeathStart(Encounter e) {}

   /** Fin de la cinematique de mort (statue de trinitite posee) : c'est la victoire. */
   default void onDeath(Encounter e) {}

   /** Combat interrompu (abort, boss disparu, monde decharge) ; reason : texte court pour les journaux. */
   default void onAborted(Encounter e, String reason) {}

   /**
    * Un joueur doit remonter a la carte : il se tient sur le teleporteur 935 allume (ou clic droit), ou une evacuation est
    * demandee (PlacedHall.evacuate). C'est le JEU qui teleporte (le mod du boss ne connait pas la carte).
    */
   default void onReturnRequested(ServerPlayer player, ReturnReason reason) {}

   /** Pourquoi un joueur doit remonter. */
   enum ReturnReason {
      /** Teleporteur 935 apres la victoire. */
      TELEPORTER,
      /** Evacuation demandee (combat interrompu, salle remise a zero, joueur coince). */
      EVACUATION
   }
}
