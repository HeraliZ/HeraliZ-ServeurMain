package fr.heraliz.casino.client;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.client.particle.SparkParticle;
import fr.heraliz.casino.client.render.GlowFilter;
import fr.heraliz.casino.client.render.MachineRenderer;
import fr.heraliz.casino.client.render.StaticModel;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Enregistrements cote client (bus du mod).
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(fr.heraliz.casino.client.render.DuelPoses::init);
        event.enqueueWork(fr.heraliz.casino.client.zone.EscortPoses::init);
        // les modeles 3D sont generes par code : on les prepare en arriere-plan
        Thread t = new Thread(() -> {
            for (String id : Designs.IDS) {
                try {
                    Designs.get(id).ensureBuilt();
                    Designs.getLod(id).ensureBuilt();
                } catch (Throwable e) {
                    HeralizCasino.LOGGER.error("[HeralizCasino] Construction du modele {} impossible", id, e);
                }
            }
            // elements de decor : un seul niveau de detail
            for (String id : fr.heraliz.casino.client.render.scene.decor.DecorDesigns.IDS) {
                try {
                    Designs.get(id).ensureBuilt();
                } catch (Throwable e) {
                    HeralizCasino.LOGGER.error("[HeralizCasino] Construction du decor {} impossible", id, e);
                }
            }
        }, "HeralizCasino-Modeles");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (MachineType type : MachineType.values()) {
            event.registerBlockEntityRenderer(ModRegistry.beType(type), MachineRenderer::new);
        }
        event.registerBlockEntityRenderer(ModRegistry.DECOR_BE.get(), fr.heraliz.casino.client.render.DecorRenderer::new);
        // places des joueurs : entites invisibles
        event.registerEntityRenderer(ModRegistry.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        // zone du casino : videurs, et monture invisible du joueur qu'ils sortent
        event.registerEntityRenderer(ModRegistry.GUARD.get(), fr.heraliz.casino.client.zone.GuardRenderer::new);
        event.registerEntityRenderer(ModRegistry.ESCORT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /** Modele du videur (joueur + details en relief). */
    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(fr.heraliz.casino.client.zone.GuardModel.LAYER, fr.heraliz.casino.client.zone.GuardModel::createLayer);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            GlowFilter.clear();
            StaticModel.clear();
        });
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.SPARK.get(), SparkParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("win", WinOverlay.INSTANCE);
        event.registerAboveAll("zone_cinema", fr.heraliz.casino.client.zone.ZoneCinema.OVERLAY);
    }
}
