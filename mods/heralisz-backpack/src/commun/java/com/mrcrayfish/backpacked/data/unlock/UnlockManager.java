package com.mrcrayfish.backpacked.data.unlock;

import com.mrcrayfish.backpacked.core.ModSyncedDataKeys;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class UnlockManager {
   private static UnlockManager instance;
   final Set<ServerPlayer> testForCompletion = new HashSet<>();

   public static UnlockManager instance() {
      if (instance == null) {
         instance = new UnlockManager();
      }

      return instance;
   }

   private UnlockManager() {
      // ecouteurs (connexion, reapparition, dimension, ticks) : partie serveur, UnlockManagerServeur.init (appele juste apres
      // la creation de l'instance, a la meme place qu'avant)
   }

   public static void queuePlayerForCompletionTest(ServerPlayer player) {
      instance().testForCompletion.add(player);
   }

   public static Optional<UnlockTracker> getTracker(Player player) {
      return Optional.ofNullable((UnlockTracker)ModSyncedDataKeys.UNLOCK_TRACKER.getValue(player));
   }

   public static <T> List<T> getTrackers(Player player, Class<T> trackerClass) {
      UnlockTracker tracker = (UnlockTracker)ModSyncedDataKeys.UNLOCK_TRACKER.getValue(player);
      return tracker != null ? tracker.getProgressTrackers(trackerClass) : Collections.emptyList();
   }
}
