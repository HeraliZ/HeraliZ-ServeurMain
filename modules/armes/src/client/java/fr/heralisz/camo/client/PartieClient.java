package fr.heralisz.camo.client;

import fr.heralisz.camo.network.DessinsPacket;
import fr.heralisz.camo.network.DessinsRefairePacket;
import fr.heraliz.tactique.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §4). L'affichage des camos (shaders, rendus, onglets de
 * l'écran de modification, mixins) s'inscrit seul (CamoClient, @Mod.EventBusSubscriber(value = CLIENT), configs de mixins
 * client). Branché ici (21/11, docs/SKINS_PROTEGES.md, étape 1) : la copie des dessins des skins envoyée par le serveur,
 * gardée en mémoire (DessinsRecus), et la demande du staff de refaire les programmes depuis elle (/camos dessins).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      DessinsPacket.RECEPTION_CLIENT.brancher(DessinsRecus::morceau);
      DessinsRefairePacket.RECEPTION_CLIENT.brancher(p -> CamoShaders.refaireDepuisServeur());
   }
}
