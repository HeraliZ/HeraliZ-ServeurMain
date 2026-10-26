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
        CoffreBlockEntity.TICK_CLIENT = EffetsCoffre::tick;
        CoffreItem.RENDU_CLIENT.brancher(consumer -> consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CoffreObjetRendu.instance();
            }
        }));
    }
}
