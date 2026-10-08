package com.mrcrayfish.backpacked.data.unlock;

import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageSyncUnlockTracker;
import com.mrcrayfish.framework.api.event.PlayerEvents;
import com.mrcrayfish.framework.api.event.TickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

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
      TickEvents.END_PLAYER.register(UnlockManagerServeur::onPlayerTick);
   }



   private static void onPlayerTick(Player player) {
      if (!player.level().isClientSide() && player.tickCount % 20 == 0) {
         Level world = player.level();
         BlockPos playerPosition = player.blockPosition();
         Biome biome = (Biome)world.getBiome(playerPosition).value();
      }
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
            UnlockManager.getTracker(player).ifPresent(unlockTracker -> unlockTracker.getProgressTrackerMap().forEach((location, progressTracker) -> {
               if (!unlockTracker.getUnlockedBackpacks().contains(location) && progressTracker.isComplete()) {
                  BackpackManager.instance().unlockBackpack(player, location);
               }
            }));
         }

         UnlockManager.instance().testForCompletion.clear();
      }
   }
}
