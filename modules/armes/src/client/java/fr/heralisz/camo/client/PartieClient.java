package fr.heralisz.camo.client;

import fr.heralisz.camo.network.DessinsPacket;
import fr.heralisz.camo.network.DessinsRefairePacket;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §4). L'affichage des camos (shaders, rendus, onglets de
 * l'écran de modification, mixins) s'inscrit seul (CamoClient, @Mod.EventBusSubscriber(value = CLIENT), configs de mixins
 * client). Branché ici (21/11, docs/SKINS_PROTEGES.md) : la copie des dessins des skins envoyée par le serveur, gardée en
 * mémoire (DessinsRecus) ; étape 2 : la fabrication des programmes des skins depuis elle, par tranches au début de chaque
 * image (CamoShaders.image), l'attente de la copie à chaque connexion, et la demande du staff de les refaire
 * (/camos dessins).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      DessinsPacket.RECEPTION_CLIENT.brancher(DessinsRecus::morceau);
      DessinsRefairePacket.RECEPTION_CLIENT.brancher(p -> CamoShaders.refaireDepuisServeur());
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.RenderTickEvent.class, CamoShaders::image);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, DessinsRecus::arrivee);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class, DessinsRecus::depart);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, DessinsRecus::tick);
   }
}
