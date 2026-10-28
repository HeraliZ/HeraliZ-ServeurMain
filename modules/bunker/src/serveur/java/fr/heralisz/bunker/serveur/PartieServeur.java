package fr.heralisz.bunker.serveur;

import fr.heralisz.bunker.pont.Partie;
import fr.heralisz.bunker.serveur.salle.SallesBranchement;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Bunker (docs/METHODE_CLIENT_SERVEUR.md §4) : salles à carte (porte tenue ouverte tant que
 * quelqu'un est dedans, loot de la salle avec HeraliZ Loot, commande /salle, baguette du staff). Les fragments de cartes
 * d'accès ne s'assemblent plus au clic droit (28/10) : seulement à la Table d'armurier, onglet Divers.
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      SallesBranchement.brancher();
   }
}
