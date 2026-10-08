package fr.heralisz.n07.api.scene;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Scene en cours (serveur). */
public interface SceneHandle {
   Scene scene();

   /** Ticks ecoules depuis le debut. */
   int time();

   boolean running();

   /** Spectateurs encore connectes. */
   List<ServerPlayer> viewers();

   /** Passe la scene comme si les joueurs l'avaient votee (actions essentielles appliquees, resultat SKIPPED). */
   void skip();

   /** Arrete la scene sans appliquer la suite (resultat CANCELLED). */
   void cancel();
}
