package fr.heraliz.recolte.client;

import fr.heraliz.recolte.client.modeles.ModelesRecolte;
import fr.heraliz.recolte.entites.EntitesRecolte;
import fr.heraliz.recolte.pont.Partie;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Récolte (docs/METHODE_CLIENT_SERVEUR.md §4), chargée par {@code Parties.client} sur un client
 * physique : modèles cuits (lus avec les ressources, relus avec elles), rendus des entités, outils en main.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        busMod.addListener(PartieClient::rendus);
        busMod.addListener(PartieClient::rechargement);
    }

    private static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EntitesRecolte.GISEMENT.get(), NoopRenderer::new);
        e.registerEntityRenderer(EntitesRecolte.ARBRE.get(), NoopRenderer::new);
    }

    /** Les modèles cuits ({@code assets/heraliz_recolte/modeles/}) : lus au chargement des ressources du jeu. */
    private static void rechargement(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(ModelesRecolte.INSTANCE);
    }
}
