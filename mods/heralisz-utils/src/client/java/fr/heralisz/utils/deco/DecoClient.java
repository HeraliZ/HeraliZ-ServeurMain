package fr.heralisz.utils.deco;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.cine.CineReseau;
import fr.heralisz.utils.cine.ZombieCinematiqueRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Côté client : rendus animés (mélangeur et modèle de ses pales, décors animés, ponts de corde), rendu du zombie de la
 * cinématique, particules du vent de la faille ; les maillages animés sont relus au rechargement des ressources.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DecoClient {
    private DecoClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(DecoBlocks.MELANGEUR_BE.get(), MelangeurRenderer::new);
        e.registerBlockEntityRenderer(DecoBlocks.ANIME_BE.get(), AnimeRenderer::new);
        e.registerBlockEntityRenderer(DecoBlocks.PONT_BE.get(), PontRenderer::new);
        e.registerEntityRenderer(CineReseau.ZOMBIE.get(), ZombieCinematiqueRenderer::new);
    }

    @SubscribeEvent
    public static void rechargement(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) rm -> Maillage.vider());
    }

    @SubscribeEvent
    public static void particules(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(VentParticules.RAFALE.get(), RafaleParticle.Nappe::new);
        e.registerSpriteSet(VentParticules.TRAIT.get(), RafaleParticle.Trait::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional e) {
        e.register(MelangeurRenderer.PALES);
    }
}
