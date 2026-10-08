package fr.heralisz.plague.serveur;

import fr.heraliz.boss.pont.Partie;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.dungeon.ModBlocks;
import fr.heralisz.plague.dungeon.StationHallBEServeur;
import fr.heralisz.plague.entity.PlagueBearerServeur;
import fr.heralisz.plague.entity.PlagueGlobServeur;
import fr.heralisz.plague.entity.PlagueSwarmServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR du Porteur de Peste (logique de jeu) : chargée par le @Mod via Parties.serveur si elle est dans le jar
 * (-serveur ou -complet ; aussi pour le serveur intégré du solo). Branche les versions serveur du boss, de ses projectiles,
 * de ses essaims et de la salle (Station de Quarantaine). La commande /peste (StationCommand) s'inscrit seule
 * (@Mod.EventBusSubscriber).
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      HeraliszPlague.PLAGUE_BEARER_USINE.serveur(PlagueBearerServeur::new);
      HeraliszPlague.PLAGUE_GLOB_USINE.serveur(PlagueGlobServeur::new);
      HeraliszPlague.PLAGUE_SWARM_USINE.serveur(PlagueSwarmServeur::new);
      ModBlocks.STATION_USINE.serveur(StationHallBEServeur::new);
   }
}
