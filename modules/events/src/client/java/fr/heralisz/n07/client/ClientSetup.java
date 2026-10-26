package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(HeraliszN07.SUJET_N07.get(), SujetN07Renderer::new);
      event.registerEntityRenderer(HeraliszN07.N07_SHELL.get(), N07Props.Shell::new);
      event.registerEntityRenderer(HeraliszN07.N07_BOULDER.get(), N07Props.Boulder::new);
      event.registerEntityRenderer(HeraliszN07.N07_ISOTOPE.get(), N07Props.Isotope::new);
      event.registerEntityRenderer(HeraliszN07.BOSS_REMAINS.get(), BossRemainsRenderer::new);
      event.registerBlockEntityRenderer(fr.heralisz.n07.dungeon.DungeonRegistry.SHRINE_BE.get(), ctx -> new ShrineRenderer());
      event.registerBlockEntityRenderer(fr.heralisz.n07.dungeon.DungeonRegistry.PROP_BE.get(), ctx -> new PropRenderer());
      event.registerBlockEntityRenderer(fr.heralisz.n07.dungeon.DungeonRegistry.SIREN_BE.get(), ctx -> new SirenRenderer());
      event.registerBlockEntityRenderer(fr.heralisz.n07.dungeon.DungeonRegistry.GATE_BE.get(), ctx -> new GateRenderer());
      event.registerBlockEntityRenderer(fr.heralisz.n07.decor.DecorRegistry.BE.get(), ctx -> new DecorRenderer());
   }

   private ClientSetup() {}
}
