package fr.heraliz.decors.client;

import fr.heraliz.decors.EntitesBloc;
import fr.heraliz.decors.HeralizDecors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Branche le dessin des grands effets (cascade, rayons de soleil) sur leurs entités de bloc. */
@Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EffetsClient {
    private EffetsClient() {}

    @SubscribeEvent
    public static void dessins(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(EntitesBloc.CASCADE.get(), c -> new CascadeRendu());
        e.registerBlockEntityRenderer(EntitesBloc.RAYON.get(), c -> new RayonRendu());
    }
}
