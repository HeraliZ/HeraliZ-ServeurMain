package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.particle.BossParticles;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Branchements client : HUD du boss, masquage de la barre vanilla, joueur figé pendant les cinématiques, particules. */
public final class ClientEvents {
   private ClientEvents() {}

   @Mod.EventBusSubscriber(modid = HeraliszN07.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class ModBus {
      @SubscribeEvent
      public static void overlays(RegisterGuiOverlaysEvent e) {
         e.registerAboveAll("n07_bar", BossHud::renderBar);
         e.registerAboveAll("n07_cinematic", BossHud::renderCinematic);
         e.registerAboveAll("ritual", Ritual::renderOverlay);
         e.registerAboveAll("n07_scene", ScenePlayer::renderOverlay);
      }

      @SubscribeEvent
      public static void particles(RegisterParticleProvidersEvent e) {
         BossParticles.registerProviders(e);
      }
   }

   @Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
   public static final class ForgeBus {
      /** La barre vanilla du boss est remplacée par la nôtre. */
      @SubscribeEvent
      public static void hideVanillaBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
         if (e.getBossEvent().getName().getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("entity.heralisz_n07.")) {
            e.setIncrement(0);
            e.setCanceled(true);
         }
      }

      /**
       * Pendant une cinematique ou une scene : plus aucune interface (vanilla, HUD des autres mods enregistres en calques Forge,
       * ex. TaCZ, HUD Zombies), seulement l'image et nos bandes / titres. Pendant le rituel : seulement l'interface vanilla.
       */
      @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
      public static void hideHud(RenderGuiOverlayEvent.Pre e) {
         var id = e.getOverlay().id();
         if (Cinematic.active() || ScenePlayer.active()) {
            boolean ours = id.getNamespace().equals(HeraliszN07.MODID) && (id.getPath().equals("n07_cinematic") || id.getPath().equals("n07_scene"));
            if (!ours) e.setCanceled(true);
         } else if (Ritual.localActive() && id.getNamespace().equals("minecraft")) {
            e.setCanceled(true);
         }
      }

      private static boolean locked() {
         return Cinematic.active() || Ritual.localActive() || ScenePlayer.active();
      }

      @SubscribeEvent
      public static void noHand(net.minecraftforge.client.event.RenderHandEvent e) {
         if (locked()) e.setCanceled(true);
      }

      @SubscribeEvent
      public static void freeze(MovementInputUpdateEvent e) {
         if (locked()) {
            e.getInput().forwardImpulse = 0;
            e.getInput().leftImpulse = 0;
            e.getInput().up = e.getInput().down = e.getInput().left = e.getInput().right = false;
            e.getInput().jumping = false;
            e.getInput().shiftKeyDown = false;
         }
      }

      /** Ni coups ni clics pendant une cinématique ou un rituel. */
      @SubscribeEvent
      public static void noInteract(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
         if (locked()) {
            e.setSwingHand(false);
            e.setCanceled(true);
         }
      }
   }
}
