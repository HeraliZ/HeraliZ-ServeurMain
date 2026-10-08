package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.core.Downed;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.event.InputEvent.MouseScrollingEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre;
import net.minecraftforge.client.event.RenderPlayerEvent.Post;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = "reanimation", bus = Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
   private static final Set<ResourceLocation> KEPT = Set.of(
      VanillaGuiOverlay.VIGNETTE.id(),
      VanillaGuiOverlay.PORTAL.id(),
      VanillaGuiOverlay.FROSTBITE.id(),
      VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
      VanillaGuiOverlay.SLEEP_FADE.id(),
      VanillaGuiOverlay.DEBUG_TEXT.id(),
      VanillaGuiOverlay.FPS_GRAPH.id(),
      VanillaGuiOverlay.TITLE_TEXT.id(),
      VanillaGuiOverlay.SUBTITLES.id(),
      VanillaGuiOverlay.CHAT_PANEL.id(),
      VanillaGuiOverlay.PLAYER_LIST.id()
   );

   private ClientEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase == Phase.START) {
         ExecutionScenes.restoreAllHands();
         if (mc.player == null) {
            return;
         }

         int f = ClientDowned.localFlags();
         Options o = mc.options;
         if ((f & 1) == 0) {
            if ((f & 144) != 0) {
               drain(o.keyInventory, o.keyDrop, o.keySwapOffhand, o.keyAttack, o.keyUse, o.keyPickItem);
            }
         } else {
            drain(o.keyInventory, o.keyDrop, o.keySwapOffhand, o.keyPickItem, o.keyAttack, o.keyUse);

            for (KeyMapping key : o.keyHotbarSlots) {
               drain(key);
            }

            if (mc.player.isSprinting()) {
               mc.player.setSprinting(false);
            }
         }

         while (ClientSetup.MENU.consumeClick()) {
            if (mc.screen == null && (f & 1) == 0) {
               ExecutionScreen.open();
            }
         }
      } else {
         ClientDowned.tick();
         ExecutionScenes.tick(mc);
         FirstPersonRevive.tick();
         CinematicCamera.tick(mc);
         ExecutionFx.tick(mc);
         LocalEffects.tick(mc);
      }
   }

   private static void drain(KeyMapping... keys) {
      for (KeyMapping key : keys) {
         while (key.consumeClick()) {
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onClickInput(InteractionKeyMappingTriggered event) {
      if ((ClientDowned.localFlags() & 145) != 0) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onScroll(MouseScrollingEvent event) {
      if (ClientDowned.localDowned() && Minecraft.getInstance().screen == null) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onScreenOpening(Opening event) {
      if (ClientDowned.localDowned() && event.getNewScreen() instanceof AbstractContainerScreen) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onMovementInput(MovementInputUpdateEvent event) {
      int f = Downed.flags(event.getEntity());
      Input input = event.getInput();
      if ((f & 1) != 0) {
         input.jumping = false;
         input.shiftKeyDown = false;
         if ((f & 64) != 0) {
            freeze(input);
         }
      } else if ((f & 144) != 0) {
         ClientDowned.Info me = ClientDowned.local();
         boolean committed = (f & 128) != 0 && me != null && me.revive >= 58;
         boolean moving = input.jumping || input.up || input.down || input.left || input.right;
         if (moving && !committed && me != null && me.revive > 8) {
            LocalEffects.requestCancel();
         }

         input.jumping = false;
         input.shiftKeyDown = false;
         freeze(input);
      }
   }

   private static void freeze(Input input) {
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
   }

   @SubscribeEvent
   public static void onCameraAngles(ComputeCameraAngles event) {
      FirstPersonRevive.onCameraAngles(event);
      CinematicCamera.onCameraAngles(event);
      ExecutionFx.onCameraAngles(event);
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      ExecutionFx.onRenderLevel(event);
   }

   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      if (ClientDowned.localDowned()) {
         event.setCanceled(true);
      } else {
         FirstPersonRevive.onRenderHand(event);
      }
   }

   @SubscribeEvent
   public static void onViewFov(ComputeFov event) {
      ExecutionFx.onFov(event);
   }

   @SubscribeEvent
   public static void onFov(ComputeFovModifierEvent event) {
      if ((Downed.flags(event.getPlayer()) & 145) != 0) {
         event.setNewFovModifier(1.0F);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onOverlay(Pre event) {
      if (ClientDowned.localDowned() || ExecutionFx.cinema()) {
         ResourceLocation id = event.getOverlay().id();
         if (!KEPT.contains(id) && !"reanimation".equals(id.getNamespace())) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRenderPlayerHide(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
      if (ExecutionScenes.hidden(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
      Poses.beginPlayer(event.getEntity(), event.getPoseStack(), event.getPartialTick());
      ExecutionScenes.stashHands(event.getEntity());
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRenderPlayerPost(Post event) {
      ExecutionScenes.restoreHands(event.getEntity());
      Poses.endPlayer(event.getPoseStack());
   }

   @SubscribeEvent
   public static void onNameTag(RenderNameTagEvent event) {
      if (ExecutionScenes.involved(event.getEntity())) {
         event.setResult(Result.DENY);
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(LoggingOut event) {
      ClientDowned.clear();
      ExecutionScenes.clear();
      ClientUnlocks.clear();
      LocalEffects.reset();
      ExecutionFx.clear();
      KnifeTrail.clear();
      ScriptFx.clear();
      CinematicCamera.reset();
   }

   @SubscribeEvent
   public static void onRespawn(Clone event) {
      ClientDowned.clear();
   }
}
