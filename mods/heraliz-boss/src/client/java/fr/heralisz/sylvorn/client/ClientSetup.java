package fr.heralisz.sylvorn.client;

import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.client.forest.SylvornLegsRenderer;
import fr.heralisz.sylvorn.client.forest.SylvornProjectiles;
import fr.heralisz.sylvorn.client.forest.SylvornRenderer;
import fr.heralisz.sylvorn.dungeon.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeraliszSylvorn.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   private ClientSetup() {}

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(HeraliszSylvorn.SYLVORN.get(), SylvornRenderer::new);
      event.registerEntityRenderer(HeraliszSylvorn.SYLVORN_LEGS.get(), SylvornLegsRenderer::new);
      event.registerEntityRenderer(HeraliszSylvorn.THROWN_LOG.get(), SylvornProjectiles.Log::new);
      event.registerEntityRenderer(HeraliszSylvorn.THORN_SPEAR.get(), SylvornProjectiles.Spear::new);
      event.registerEntityRenderer(HeraliszSylvorn.SEED_POD.get(), SylvornProjectiles.Pod::new);
      event.registerEntityRenderer(HeraliszSylvorn.SAP_NODE.get(), SylvornProjectiles.Node::new);
      event.registerBlockEntityRenderer(ModBlocks.HERO_BE.get(), ctx -> new HeroPropRenderer());
   }

   @SubscribeEvent
   public static void particles(RegisterParticleProvidersEvent e) {
      fr.heralisz.sylvorn.client.particle.BossParticles.registerProviders(e);
   }

   @SubscribeEvent
   public static void overlays(RegisterGuiOverlaysEvent e) {
      e.registerAboveAll("sylvorn_bar", Hud::renderBar);
      e.registerBelowAll("sylvorn_screen", ScreenFx::render);
      e.registerAboveAll("sylvorn_cinematic", Hud::renderCinematic);
   }
}
