package fr.heraliz.recolte.client;

import fr.heraliz.recolte.entites.EntitesRecolte;
import fr.heraliz.recolte.pont.Partie;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Récolte (docs/METHODE_CLIENT_SERVEUR.md §4), chargée par {@code Parties.client} sur un client
 * physique : rendus des entités (modèles cuits), outils en main.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        busMod.addListener(PartieClient::rendus);
    }

    private static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EntitesRecolte.GISEMENT.get(), NoopRenderer::new);
        e.registerEntityRenderer(EntitesRecolte.ARBRE.get(), NoopRenderer::new);
    }
}
