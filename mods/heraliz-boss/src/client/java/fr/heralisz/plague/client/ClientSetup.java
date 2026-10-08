package fr.heralisz.plague.client;

import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.client.sick.PlagueBearerRenderer;
import fr.heralisz.plague.client.sick.PlagueProps;
import fr.heralisz.plague.dungeon.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeraliszPlague.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   private ClientSetup() {}

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(HeraliszPlague.PLAGUE_BEARER.get(), PlagueBearerRenderer::new);
      event.registerEntityRenderer(HeraliszPlague.PLAGUE_GLOB.get(), PlagueProps.Glob::new);
      event.registerEntityRenderer(HeraliszPlague.PLAGUE_SWARM.get(), PlagueProps.Swarm::new);
      event.registerBlockEntityRenderer(ModBlocks.HERO_BE.get(), ctx -> new HeroPropRenderer());
   }

   @SubscribeEvent
   public static void particles(RegisterParticleProvidersEvent e) {
      fr.heralisz.plague.client.particle.BossParticles.registerProviders(e);
   }

   @SubscribeEvent
   public static void overlays(RegisterGuiOverlaysEvent e) {
      e.registerAboveAll("plague_bar", Hud::renderBar);
      e.registerBelowAll("plague_screen", ScreenFx::render);
      e.registerAboveAll("plague_cinematic", Hud::renderCinematic);
   }
}
