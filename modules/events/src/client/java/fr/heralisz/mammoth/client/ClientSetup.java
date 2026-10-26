package fr.heralisz.mammoth.client;

import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   private ClientSetup() {}

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(HeraliszMammoth.MAMMOTH.get(), MammothRenderer::new);
      event.registerEntityRenderer(HeraliszMammoth.DRAUGR.get(), DraugrRenderer::new);
      event.registerEntityRenderer(HeraliszMammoth.GUARDIAN_SPEAR.get(), SpearRenderer::new);
      event.registerEntityRenderer(HeraliszMammoth.SPECTRAL_HAND.get(), SpectralHandRenderer::new);
      event.registerBlockEntityRenderer(ModBlocks.ALTAR_BE.get(), ctx -> new AltarRenderer());
      event.registerBlockEntityRenderer(ModBlocks.GATE_BE.get(), ctx -> new IceGateRenderer());
      event.registerBlockEntityRenderer(ModBlocks.TITAN_BE.get(), ctx -> new TitanRenderer());
      event.registerBlockEntityRenderer(ModBlocks.HERO_BE.get(), ctx -> new HeroPropRenderer());
      event.registerBlockEntityRenderer(ModBlocks.LIT_BE.get(), ctx -> new LitPropRenderer());
      event.registerBlockEntityRenderer(ModBlocks.PRISON_BE.get(), ctx -> new IcePrisonRenderer());
      event.registerBlockEntityRenderer(ModBlocks.AURORA_BE.get(), ctx -> new AuroraRenderer());
   }

   @SubscribeEvent
   public static void overlays(RegisterGuiOverlaysEvent e) {
      e.registerAboveAll("ymirak_bar", Hud::renderBar);
      e.registerBelowAll("ymirak_frost", FrostScreen::render);
      e.registerAboveAll("ymirak_cinematic", Hud::renderCinematic);
   }
}
