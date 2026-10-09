package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.client.hud.DesignatorHud;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.client.particle.BigSmokeParticle;
import fr.heraliz.airstrike.client.particle.FireballParticle;
import fr.heraliz.airstrike.client.particle.ShockwaveParticle;
import fr.heraliz.airstrike.client.particle.SparkParticle;
import fr.heraliz.airstrike.client.render.SupplyCrateRenderer;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModParticles;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod bus client registrations. */
@Mod.EventBusSubscriber(modid = AirstrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EFFECT_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.SUPPLY_CRATE.get(), SupplyCrateRenderer::new);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIREBALL.get(), FireballParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SMOKE.get(), BigSmokeParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(DesignatorClient.CYCLE_KEY);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "binoculars", DesignatorHud::render);
    }

    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ModModels.invalidate());
    }
}
