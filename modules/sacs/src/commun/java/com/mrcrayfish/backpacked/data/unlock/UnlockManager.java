package com.mrcrayfish.backpacked.data.unlock;

import com.mrcrayfish.backpacked.Config;
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
      // HeraliZ (21/11) : tous les sacs debloques (unlockAllCosmetics) : aucun defi n'avance, plus rien a compter
      if (!defisSuivis()) {
         return Collections.emptyList();
      }
      UnlockTracker tracker = (UnlockTracker)ModSyncedDataKeys.UNLOCK_TRACKER.getValue(player);
      return tracker != null ? tracker.getProgressTrackers(trackerClass) : Collections.emptyList();
   }

   /**
    * HeraliZ (21/11, audit) : les defis de deblocage ne sont suivis que s'il reste un sac a debloquer. Avec
    * {@code unlockAllCosmetics = true} (choix du proprietaire du 19/11 : rien a debloquer), ils avancaient quand meme a
    * chaque pas (paquet de deplacement de chaque joueur), a chaque clic sur un bloc (copie de l'objet tenu, filtres des 73
    * defis) et chaque tick (relecture des 118 defis de chaque joueur qui avait bouge) pour rien. Reglage pas encore lu :
    * suivis, comme avant.
    */
   public static boolean defisSuivis() {
      try {
         return !Boolean.TRUE.equals(Config.SERVER.backpack.unlockAllCosmetics.get());
      } catch (IllegalStateException pasEncoreLu) {
         return true;
      }
   }
}
