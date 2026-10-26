package com.mrcrayfish.backpacked.client;

import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.network.message.MessageOpenCustomisation;
import com.mrcrayfish.backpacked.network.message.MessageSyncUnlockTracker;
import com.mrcrayfish.backpacked.network.message.MessageSyncVillagerBackpack;
import com.mrcrayfish.backpacked.network.message.MessageUnlockBackpack;
import com.mrcrayfish.backpacked.network.play.ClientPlayHandler;
import com.mrcrayfish.backpacked.pont.Partie;
import com.mrcrayfish.backpacked.util.ClientUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Partie CLIENT de HeraliZ Backpack (separation client / serveur HeraliZ, docs/METHODE_CLIENT_SERVEUR.md §4), chargee par
 * Backpacked sur un client physique si elle est dans le jar (-client, -complet). Code d'origine de Backpacked (bloc
 * DistExecutor et onClientSetup), deplace tel quel, plus les crochets du code commun (infobulle, paquets recus).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus bus) {
      bus.addListener(ClientHandler::onRegisterClientLoaders);
      bus.addListener(ClientHandler::onRegisterRenderers);
      bus.addListener(ClientHandler::onAddLayers);
      ClientBootstrap.earlyInit();
      bus.addListener(PartieClient::onClientSetup);
      BackpackItem.INFOBULLE_CLIENT = ClientUtils::createBackpackTooltip;
      MessageUnlockBackpack.RECEPTION_CLIENT.brancher(ClientPlayHandler::handleUnlockBackpack);
      MessageSyncUnlockTracker.RECEPTION_CLIENT.brancher(ClientPlayHandler::handleSyncUnlockTracker);
      MessageOpenCustomisation.RECEPTION_CLIENT.brancher(ClientPlayHandler::handleOpenCustomisation);
      MessageSyncVillagerBackpack.RECEPTION_CLIENT.brancher(ClientPlayHandler::handleSyncVillagerBackpack);
   }

   private static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         ClientBootstrap.init();
         ClientHandler.init();
      });
   }
}
