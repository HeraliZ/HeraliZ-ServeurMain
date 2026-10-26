package fr.heralisz.n07.scene;

import fr.heralisz.n07.api.scene.SkipMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Vote « maintenir pour passer » d'un groupe de spectateurs (scene ou cinematique du boss). */
public final class SkipVote {
   public final SkipMode mode;
   private final Set<UUID> holding = new HashSet<>();

   public SkipVote(SkipMode mode) {
      this.mode = mode == null ? SkipMode.NONE : mode;
   }

   /** Met a jour l'appui d'un joueur ; rend true si le changement compte. */
   public boolean hold(UUID who, boolean on) {
      if (mode == SkipMode.NONE) return false;
      return on ? holding.add(who) : holding.remove(who);
   }

   public boolean holds(UUID who) {
      return holding.contains(who);
   }

   /** Nombre d'appuis parmi les spectateurs encore la. */
   public int count(Collection<ServerPlayer> viewers) {
      int n = 0;
      for (ServerPlayer p : viewers) if (holding.contains(p.getUUID())) n++;
      return n;
   }

   /** Appuis necessaires pour passer pour tout le monde. */
   public int needed(int viewers) {
      return switch (mode) {
         case MAJORITY -> viewers / 2 + 1;
         case ALL -> Math.max(1, viewers);
         case ANY, PER_PLAYER -> 1;
         default -> Integer.MAX_VALUE;
      };
   }

   /** Le groupe a decide de passer (hors PER_PLAYER, qui se regle joueur par joueur). */
   public boolean decided(Collection<ServerPlayer> viewers) {
      if (mode == SkipMode.NONE || mode == SkipMode.PER_PLAYER || viewers.isEmpty()) return false;
      return count(viewers) >= needed(viewers.size());
   }
}
