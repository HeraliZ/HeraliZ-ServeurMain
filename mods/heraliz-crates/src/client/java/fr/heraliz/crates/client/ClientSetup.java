package fr.heraliz.crates.client;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.client.particle.SparkParticle;
import fr.heraliz.crates.client.render.CrateRenderer;
import fr.heraliz.crates.client.render.GlowFilter;
import fr.heraliz.crates.client.render.scene.Designs;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Enregistrements cote client (bus du mod).
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // les modeles 3D sont generes par code (~0,1 s chacun) : on les prepare en arriere-plan
        // pour ne pas figer l'image la premiere fois qu'une caisse apparait
        Thread t = new Thread(() -> {
            for (CrateType type : CrateType.values()) {
                try {
                    Designs.get(type.id).ensureBuilt();
                    Designs.getLod(type.id).ensureBuilt();
                } catch (Throwable e) {
                    HeralizCrates.LOGGER.error("[HeralizCrates] Construction du modele {} impossible", type.id, e);
                }
            }
        }, "HeralizCrates-Modeles");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.CRATE_BE.get(), CrateRenderer::new);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            GlowFilter.clear();
            fr.heraliz.crates.client.render.StaticCrate.clear();
        });
        // textures HD des caisses preparees pendant l'ecran de chargement (pas au premier regard en jeu)
        event.registerReloadListener(new fr.heraliz.crates.client.render.CrateTextures());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.SPARK.get(), SparkParticle.Provider::new);
    }
}
