package fr.heraliz.reanimation.core;

import fr.heraliz.reanimation.ReanimationConfig;
import fr.heraliz.reanimation.command.ReanimationCommand;
import fr.heraliz.reanimation.execution.ExecutionUnlocks;
import fr.heraliz.reanimation.execution.Executions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent.Hands;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.SaveToFile;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.event.entity.player.PlayerEvent.StopTracking;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Écouteurs SERVEUR de l'état à terre (docs/METHODE_CLIENT_SERVEUR.md §5.6) : mise à terre à la mort, dégâts, ciblage des
 * monstres, objets jetés, voyages, tick, connexions, sauvegarde, suivi des entités, commandes. Corps d'origine (ancien
 * ServerEvents) ; les règles appliquées aussi sur le client restent dans ServerEvents (commun).
 */
@EventBusSubscriber(modid = "reanimation", bus = Bus.FORGE)
public final class ServerEventsServeur {
   private ServerEventsServeur() {
   }
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity e = event.getEntity();
      if (!e.level().isClientSide
         && (Boolean)ReanimationConfig.ENABLED.get()
         && DownedManagerServeur.canBeDowned(e)
         && !DownedManagerServeur.isDowned(e)
         && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         if (e instanceof Player p && p.isSpectator()) {
            return;
         }

         event.setCanceled(true);
         DownedManagerServeur.down(e, event.getSource());
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeathConfirmed(LivingDeathEvent event) {
      if (!event.getEntity().level().isClientSide) {
         DownedManagerServeur.onDeath(event.getEntity());
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDamage(LivingDamageEvent event) {
      if (!event.getEntity().level().isClientSide && event.getAmount() > 0.0F) {
         DownedManagerServeur.onDamaged(event.getEntity());
      }
   }

   @SubscribeEvent
   public static void onTarget(LivingChangeTargetEvent event) {
      LivingEntity target = event.getNewTarget();
      if (target != null && (Boolean)ReanimationConfig.MOBS_IGNORE_DOWNED.get() && Downed.isDowned(target)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onBreak(BreakEvent event) {
      if (ServerEvents.blocked(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onSwapHands(Hands event) {
      if (Downed.isDowned(event.getEntity())) {
         ServerEvents.deny(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onToss(ItemTossEvent event) {
      Player player = event.getPlayer();
      if (!player.level().isClientSide && Downed.isDowned(player)) {
         event.setCanceled(true);
         ItemStack stack = event.getEntity().getItem();
         if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onTravel(EntityTravelToDimensionEvent event) {
      if (Downed.isDowned(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         DownedManagerServeur.tick();
         Executions.tick();
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         DownedManagerServeur.onLogin(p);
         ExecutionUnlocks.sync(p, false);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         DownedManagerServeur.onLogout(p);
      }
   }

   @SubscribeEvent
   public static void onSave(SaveToFile event) {
      DownedManagerServeur.onSave(event.getEntity());
   }

   @SubscribeEvent
   public static void onStartTracking(StartTracking event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         DownedManagerServeur.onStartTracking(p, event.getTarget());
      }
   }

   @SubscribeEvent
   public static void onStopTracking(StopTracking event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         DownedManagerServeur.onStopTracking(p, event.getTarget());
      }
   }

   @SubscribeEvent
   public static void onCommands(RegisterCommandsEvent event) {
      ReanimationCommand.register(event.getDispatcher());
   }

   @SubscribeEvent
   public static void onStopped(ServerStoppedEvent event) {
      DownedManagerServeur.clearAll();
   }
}
