package fr.heraliz.loot.client;

import fr.heraliz.loot.CoffreBlockEntity;
import fr.heraliz.loot.CoffreItem;
import fr.heraliz.loot.pont.Partie;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Loot (docs/METHODE_CLIENT_SERVEUR.md §4) : sons et particules des coffres T7, rendu 3D des
 * coffres dans l'inventaire. Rendus, écran et filtres de l'onglet s'inscrivent seuls (@EventBusSubscriber).
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // conteneurs allégés (31/10) : interrupteur du joueur, heraliz_loot-client.toml
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                ConfigLootClient.SPEC, "heraliz_loot-client.toml");
        CoffreBlockEntity.TICK_CLIENT = EffetsCoffre::tick;
        // étoiles des conteneurs remplis (28/10) : vie courte, montrées même avec « Particules : minimales »
        busMod.addListener((net.minecraftforge.client.event.RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(fr.heraliz.loot.ParticulesLoot.ETOILE.get(), EtoileParticule.Blanche::new);
            e.registerSpriteSet(fr.heraliz.loot.ParticulesLoot.ETOILE_OR.get(), EtoileParticule.Doree::new);
        });
        CoffreItem.RENDU_CLIENT.brancher(consumer -> consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CoffreObjetRendu.instance();
            }
        }));
    }
}
