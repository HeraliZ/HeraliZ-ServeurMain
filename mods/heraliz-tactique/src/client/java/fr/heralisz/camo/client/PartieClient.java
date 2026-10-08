package fr.heralisz.camo.client;

import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §4). Rien à brancher depuis le retrait des pendentifs :
 * l'affichage des camos (shaders, rendus, onglets de l'écran de modification, mixins) s'inscrit seul (CamoClient,
 * @Mod.EventBusSubscriber(value = CLIENT), configs de mixins client).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
   }
}
