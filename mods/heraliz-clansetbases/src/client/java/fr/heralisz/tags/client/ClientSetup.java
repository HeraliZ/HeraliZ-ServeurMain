package fr.heralisz.tags.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.client.gui.kit.Ui;
import fr.heralisz.tags.client.particle.BrouillardParticle;
import fr.heralisz.tags.client.render.AtlasCouleurs;
import fr.heralisz.tags.client.render.DecalRenderer;
import fr.heralisz.tags.registry.Particules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Enregistrements cote client (bus du mod) : touche, particules, rechargement des ressources ; et sur le bus de jeu :
 * deconnexion / dechargement du monde (tags vides, tampons fermes).
 */
@Mod.EventBusSubscriber(modid = HeraliszTags.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        HeraliszTags.LOGGER.info("[HeraliZ Tags] client pret ({} tags au catalogue)", fr.heralisz.tags.catalogue.TagCatalog.taille());
        MinecraftForge.EVENT_BUS.register(Jeu.class);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TagsKeys.TAG);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Particules.BROUILLARD.get(), BrouillardParticle.Provider::new);
    }

    /** Rechargement des ressources : atlas oublie (presence, couleurs, filtre) et decalques recuits. */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
            Ui.atlasOublier();
            AtlasCouleurs.oublier();
            if (RenderSystem.isOnRenderThread()) DecalRenderer.rechargement();
            else RenderSystem.recordRenderCall(DecalRenderer::rechargement);
        });
    }

    /** Evenements de jeu (bus Forge). */
    public static final class Jeu {
        private Jeu() {
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientHandlers.deconnexion();
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload e) {
            if (e.getLevel().isClientSide()) ClientTags.vider();
        }
    }
}
