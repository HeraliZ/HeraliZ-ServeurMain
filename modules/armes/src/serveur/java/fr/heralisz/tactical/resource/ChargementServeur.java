package fr.heralisz.tactical.resource;

import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SPackSyncMessage;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Partie SERVEUR de {@link CommonAssetsManager} (docs/METHODE_CLIENT_SERVEUR.md §5.6) : chargement des index du moteur depuis
 * les données (data/<ns>/index/melee|consumable|throwable des packs heralisz_melee, heralisz_heal, heralisz_grenade…) et
 * envoi aux joueurs ; code d'origine des écouteurs de CommonAssetsManager, sans changement.
 */
@EventBusSubscriber(modid = "heralisz_tactical")
public final class ChargementServeur {
   private ChargementServeur() {
   }

   @SubscribeEvent
   public static void onReload(AddReloadListenerEvent event) {
      CommonAssetsManager commonAssetsManager = new CommonAssetsManager();
      commonAssetsManager.reloadAndRegister(event::addListener);
      CommonAssetsManager.INSTANCE = commonAssetsManager;
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      CommonAssetsManager.INSTANCE = null;
   }

   @SubscribeEvent
   public static void OnDatapackSync(OnDatapackSyncEvent event) {
      if (CommonAssetsManager.INSTANCE != null) {
         SPackSyncMessage msg = new SPackSyncMessage(CommonAssetsManager.INSTANCE.toNetwork());
         if (event.getPlayer() != null) {
            NetworkHandler.sendToClientPlayer(msg, event.getPlayer());
         } else {
            event.getPlayerList().getPlayers().forEach(player -> NetworkHandler.sendToClientPlayer(msg, player));
         }
      }
   }
}
