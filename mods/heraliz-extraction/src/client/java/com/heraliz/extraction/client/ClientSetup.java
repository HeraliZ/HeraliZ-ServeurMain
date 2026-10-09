package com.heraliz.extraction.client;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.client.particle.ExtractionRingParticle;
import com.heraliz.extraction.client.particle.ExtractionSmokeParticle;
import com.heraliz.extraction.client.particle.ExtractionSparkParticle;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeralizExtraction.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.EXTRACTION_TERMINAL_ENTITY.get(), TerminalEntityRenderer::new);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new TerminalHeldLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.EXTRACTION_SMOKE.get(), ExtractionSmokeParticle.Provider::new);
        event.registerSpriteSet(ModRegistry.EXTRACTION_SPARK.get(), ExtractionSparkParticle.Provider::new);
        event.registerSpriteSet(ModRegistry.EXTRACTION_RING.get(), ExtractionRingParticle.Provider::new);
    }
}
