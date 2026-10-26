package fr.heraliz.reanimation.core;

import fr.heraliz.reanimation.execution.ExecutionsCommun;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Règles de l'état à terre appliquées DES DEUX CÔTÉS (docs/METHODE_CLIENT_SERVEUR.md, piège n°31) : sur le client, elles
 * annoncent le refus tout de suite (pas de coup, de clic ni d'objet utilisé à terre) ; sur le serveur, elles décident. Les
 * écouteurs purement serveur (mort, dégâts, tick, connexions, commandes...) sont dans ServerEventsServeur.
 */
@EventBusSubscriber(modid = "reanimation", bus = Bus.FORGE)
public final class ServerEvents {
   private ServerEvents() {
   }

   @SubscribeEvent
   public static void onHeal(LivingHealEvent event) {
      if (Downed.isDowned(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onInteractEntity(EntityInteract event) {
      Player player = event.getEntity();
      if (blocked(player)) {
         deny(event);
      } else if (event.getTarget() instanceof LivingEntity living && Downed.isDowned(living)) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.SUCCESS);
         if (event.getHand() == InteractionHand.MAIN_HAND && !player.level().isClientSide && !player.isSpectator()) {
            if (player.isShiftKeyDown()) {
               DownedManager.startRevive(player, living);
            } else {
               DownedManager.startSearch(player, living);
            }

            return;
         }

         return;
      }
   }

   static boolean blocked(Player player) {
      return Downed.isDowned(player) || Downed.isBusy(player);
   }

   static void deny(Event event) {
      if (event.isCancelable()) {
         event.setCanceled(true);
      }

      if (event instanceof PlayerInteractEvent interact) {
         interact.setCancellationResult(InteractionResult.FAIL);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onInteractSpecific(EntityInteractSpecific event) {
      if (blocked(event.getEntity())) {
         deny(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRightClickBlock(RightClickBlock event) {
      if (blocked(event.getEntity())) {
         deny(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRightClickItem(RightClickItem event) {
      if (blocked(event.getEntity())) {
         deny(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onLeftClickBlock(LeftClickBlock event) {
      if (blocked(event.getEntity())) {
         deny(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onAttack(AttackEntityEvent event) {
      // coup d'essai d'une execution (Executions.canHurt) : le bourreau est « occupe » par l'execution, ce n'est pas un refus
      if (blocked(event.getEntity()) && !ExecutionsCommun.probing()) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onBreakSpeed(BreakSpeed event) {
      if (blocked(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onUseItem(Start event) {
      if (Downed.isDowned(event.getEntity()) || Downed.isBusy(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onMount(EntityMountEvent event) {
      if (event.isMounting() && event.getEntityMounting() instanceof LivingEntity living && Downed.isDowned(living)) {
         event.setCanceled(true);
      }
   }
}
