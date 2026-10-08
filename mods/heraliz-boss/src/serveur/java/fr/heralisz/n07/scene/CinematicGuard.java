package fr.heralisz.n07.scene;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection cote serveur des joueurs figes dans une cinematique (le gel des commandes n'existe que cote client) : aucun
 * degat, aucun recul, quelle que soit la source (boss, flaques, irradiation, creatures d'un autre mod), le temps du plan.
 * Le boss ne les vise pas non plus (voir HeraliszBoss.hurtPlayer).
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class CinematicGuard {
   /** Joueur -> instant (temps du monde, ms systeme si pas de monde) jusqu'auquel il est protege. */
   private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

   private CinematicGuard() {}

   private static long now(Player p) {
      return p.level().getGameTime();
   }

   public static void protect(Collection<? extends ServerPlayer> players, int ticks) {
      for (ServerPlayer p : players) protect(p, ticks);
   }

   public static void protect(ServerPlayer p, int ticks) {
      long until = now(p) + Math.max(1, ticks);
      UNTIL.merge(p.getUUID(), until, Math::max);
   }

   public static void release(Collection<? extends ServerPlayer> players) {
      for (ServerPlayer p : players) UNTIL.remove(p.getUUID());
   }

   public static boolean isProtected(Player p) {
      if (p == null || p.level().isClientSide) return false;
      Long u = UNTIL.get(p.getUUID());
      if (u == null) return false;
      if (now(p) > u) {
         UNTIL.remove(p.getUUID());
         return false;
      }
      return true;
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttack(LivingAttackEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && isProtected(p)) e.setCanceled(true);
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onKnockback(LivingKnockBackEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && isProtected(p)) e.setCanceled(true);
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
      UNTIL.remove(e.getEntity().getUUID());
   }
}
