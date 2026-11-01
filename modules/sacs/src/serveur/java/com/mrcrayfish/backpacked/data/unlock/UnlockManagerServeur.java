package com.mrcrayfish.backpacked.data.unlock;

import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageSyncUnlockTracker;
import com.mrcrayfish.framework.api.event.PlayerEvents;
import com.mrcrayfish.framework.api.event.TickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Partie serveur de l'UnlockManager (deblocage des sacs) : envoi du suivi au joueur (connexion, reapparition,
 * changement de dimension) et verification des defis termines a chaque tick serveur. Code d'origine.
 */
public final class UnlockManagerServeur {
    private UnlockManagerServeur() {}
   /** Branche les ecouteurs serveur de l'UnlockManager (code d'origine du constructeur, meme ordre). */
   public static void init() {
      PlayerEvents.LOGGED_IN.register(UnlockManagerServeur::onPlayerLoggedIn);
      PlayerEvents.RESPAWN.register(UnlockManagerServeur::onPlayerRespawn);
      PlayerEvents.CHANGE_DIMENSION.register(UnlockManagerServeur::onPlayerChangedDimension);
      TickEvents.END_SERVER.register(UnlockManagerServeur::onServerTick);
      // HeraliZ (perf, 01/11) : plus d'écouteur du tick de chaque joueur ; celui d'origine lisait le biome du joueur toutes
      // les secondes sans s'en servir (reste d'un défi « biome » de l'auteur, rien ne lisait ce résultat)
   }

   private static void onPlayerLoggedIn(Player player) {
      UnlockManager.getTracker(player).ifPresent(unlockTracker -> {
         Network.getPlay().sendToPlayer(() -> (ServerPlayer)player, new MessageSyncUnlockTracker(unlockTracker.getUnlockedBackpacks()));
         UnlockManager.queuePlayerForCompletionTest((ServerPlayer)player);
      });
   }

   private static void onPlayerRespawn(Player player, boolean finishedGame) {
      UnlockManager.getTracker(player)
         .ifPresent(
            unlockTracker -> Network.getPlay().sendToPlayer(() -> (ServerPlayer)player, new MessageSyncUnlockTracker(unlockTracker.getUnlockedBackpacks()))
         );
   }

   private static void onPlayerChangedDimension(Player player, ResourceKey<Level> oldDimension, ResourceKey<Level> newDimension) {
      UnlockManager.getTracker(player)
         .ifPresent(
            unlockTracker -> Network.getPlay().sendToPlayer(() -> (ServerPlayer)player, new MessageSyncUnlockTracker(unlockTracker.getUnlockedBackpacks()))
         );
   }

   private static void onServerTick(MinecraftServer server) {
      if (!UnlockManager.instance().testForCompletion.isEmpty()) {
         for (ServerPlayer player : UnlockManager.instance().testForCompletion) {
            // HeraliZ (perf, 01/11) : « fini ? » (un nombre comparé) avant « déjà débloqué ? » (une recherche) ; ce test est
            // fait pour chaque défi (99) de chaque joueur qui a avancé un défi pendant le tick (marcher suffit). Même résultat
            UnlockManager.getTracker(player).ifPresent(unlockTracker -> unlockTracker.getProgressTrackerMap().forEach((location, progressTracker) -> {
               if (progressTracker.isComplete() && !unlockTracker.getUnlockedBackpacks().contains(location)) {
                  BackpackManager.instance().unlockBackpack(player, location);
               }
            }));
         }

         UnlockManager.instance().testForCompletion.clear();
      }
   }
}
