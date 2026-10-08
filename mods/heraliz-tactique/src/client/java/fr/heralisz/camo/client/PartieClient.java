package fr.heralisz.camo.client;

import fr.heralisz.camo.decor.DecorItems;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §4) : branche le rendu 3D des pendentifs (code d'origine de
 * CharmItem.initializeClient). Le reste de l'affichage des camos (shaders, rendus, onglets de l'écran de modification,
 * mixins) s'inscrit seul (CamoClient, @Mod.EventBusSubscriber(value = CLIENT), configs de mixins client).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      DecorItems.CharmItem.RENDU_CLIENT.brancher(consumer -> consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
         @Override
         public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return fr.heralisz.camo.decor.client.CharmItemRenderer.get();
         }
      }));
   }
}
