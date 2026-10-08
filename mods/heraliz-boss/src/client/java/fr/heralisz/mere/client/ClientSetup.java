package fr.heralisz.mere.client;

import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.dungeon.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeraliszMere.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   private ClientSetup() {}

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(HeraliszMere.RUIN_MOTHER.get(), RuinMotherRenderer::new);
      event.registerEntityRenderer(HeraliszMere.SPIDERLING.get(), SpiderlingRenderer::new);
      event.registerEntityRenderer(HeraliszMere.WEB_GLOB.get(), WebGlobRenderer::new);
      event.registerBlockEntityRenderer(ModBlocks.HERO_BE.get(), ctx -> new HeroPropRenderer());
   }

   @SubscribeEvent
   public static void overlays(RegisterGuiOverlaysEvent e) {
      e.registerAboveAll("mere_bar", Hud::renderBar);
      e.registerBelowAll("mere_screen", ScreenFx::render);
      e.registerAboveAll("mere_cinematic", Hud::renderCinematic);
   }
}
