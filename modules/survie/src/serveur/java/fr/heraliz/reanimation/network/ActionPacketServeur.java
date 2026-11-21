package fr.heraliz.reanimation.network;

import fr.heraliz.reanimation.core.DownedManagerServeur;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.ExecutionUnlocks;
import fr.heraliz.reanimation.execution.Executions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Traitement serveur de {@link ActionPacket} (corps d'origine de ActionPacket.handle, sur le fil principal). */
public final class ActionPacketServeur {
   private ActionPacketServeur() {
   }

   /**
    * 21/11 (audit a7) : derniere demande d'execution de chaque joueur (ms). Le jeu n'en envoie qu'une par appui maintenu
    * (une demi-seconde au moins) ; un jeu modifie en envoyait des centaines par seconde, et chacune faisait un coup d'essai
    * (Executions.canHurt) et, en zone protegee, une ligne de journal. Fil serveur.
    */
   private static final Map<UUID, Long> DERNIERE_EXECUTION = new HashMap<>();
   private static final long CADENCE_EXECUTION_MS = 400L;
   /**
    * 21/11 (validation v7) : choix d'une execution (action 3, bouton « Equiper » du menu) et liste des executions (action 4,
    * a l'ouverture du menu) : chacun renvoie un paquet au joueur, et un jeu modifie pouvait en demander sans fin. Au plus un
    * choix toutes les 150 ms et une liste toutes les 250 ms : un vrai clic ou une vraie ouverture du menu ne vont jamais
    * aussi vite. Fil serveur.
    */
   private static final Map<UUID, Long> DERNIER_CHOIX = new HashMap<>(), DERNIERE_LISTE = new HashMap<>();
   private static final long CADENCE_CHOIX_MS = 150L, CADENCE_LISTE_MS = 250L;

   /** Vrai si ce joueur peut demander une execution maintenant (au plus une toutes les 400 ms ; une rafale repousse). */
   private static boolean cadence(ServerPlayer player) {
      return cadence(DERNIERE_EXECUTION, player, CADENCE_EXECUTION_MS);
   }

   /** Vrai si ce joueur peut refaire cette demande maintenant (au plus une toutes les {@code ms} ; une rafale repousse). */
   private static boolean cadence(Map<UUID, Long> dernieres, ServerPlayer player, long ms) {
      long maintenant = System.currentTimeMillis();
      Long avant = dernieres.put(player.getUUID(), maintenant);
      return avant == null || maintenant - avant >= ms;
   }

   /** Deconnexion : la cadence du joueur est oubliee. */
   public static void oublier(UUID joueur) {
      DERNIERE_EXECUTION.remove(joueur);
      DERNIER_CHOIX.remove(joueur);
      DERNIERE_LISTE.remove(joueur);
   }

   /** Arret du serveur. */
   public static void vider() {
      DERNIERE_EXECUTION.clear();
      DERNIER_CHOIX.clear();
      DERNIERE_LISTE.clear();
   }

   public static void traiter(ActionPacket.Recu recu) {
      ServerPlayer player = recu.player();
      ActionPacket p = recu.paquet();
      if (player != null && p.action == 3) {
         if (!cadence(DERNIER_CHOIX, player, CADENCE_CHOIX_MS)) {
            return;
         }

         if (p.target >= 0 && p.target < ExecutionType.values().length) {
            ExecutionType type = ExecutionType.byIndex(p.target);
            if (type.selectable() && ExecutionUnlocks.owns(player, type)) {
               ExecutionUnlocks.select(player, type);
               player.displayClientMessage(Component.translatable("message.reanimation.execution_equipped", new Object[]{type.displayName()}), true);
            }
         }

         ExecutionUnlocks.sync(player, false);
      } else if (player != null && p.action == 4) {
         if (cadence(DERNIERE_LISTE, player, CADENCE_LISTE_MS)) {
            ExecutionUnlocks.sync(player, false);
         }
      } else if (player != null && player.isAlive() && !player.isSpectator()) {
         if (p.action == 0) {
            DownedManagerServeur.giveUp(player);
         } else if (p.action == 1) {
            DownedManagerServeur.cancelReviveBy(player);
            Executions.cancelBy(player);
         } else if (p.action == 2 && cadence(player) && player.level().getEntity(p.target) instanceof LivingEntity body) {
            Executions.request(player, body);
         }
      }
   }
}
