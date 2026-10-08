package fr.heralisz.clans.territory.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.registry.ClansRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Enregistrements client du module territoires : rendus du drapeau et de la caisse (blocs-entites), de l'avion et
 * de la caisse sous parachute (entites), fournisseurs des particules maison, ecran de la caisse, vidage des caches
 * (maillages, tissus) au rechargement des ressources. Priorite basse sur les rendus : les rendus provisoires de
 * ClansClient, enregistres avant, sont remplaces.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TerritoireClient {
    private TerritoireClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(ClansRegistry.DRAPEAU_BE.get(), DrapeauRenderer::new);
        e.registerBlockEntityRenderer(ClansRegistry.CAISSE_BE.get(), CaisseRenderer::new);
        e.registerEntityRenderer(ClansRegistry.AVION.get(), AvionRenderer::new);
        e.registerEntityRenderer(ClansRegistry.CAISSE_PARACHUTE.get(), CaisseParachuteRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ClansRegistry.FUMEE_ROUGE.get(), FumeeParticle.Provider::new);
        e.registerSpriteSet(ClansRegistry.TRAINEE.get(), TraineeParticle.Provider::new);
        e.registerSpriteSet(ClansRegistry.FLAMME_FUSEE.get(), FlammeParticle.Provider::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(ClansRegistry.CAISSE_MENU.get(), CaisseScreen::new));
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) rm -> {
            Maillage.vider();
            TissuTexture.vider();
        });
    }
}
