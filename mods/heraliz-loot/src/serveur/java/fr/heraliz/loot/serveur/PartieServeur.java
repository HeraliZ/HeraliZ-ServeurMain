package fr.heraliz.loot.serveur;

import fr.heraliz.loot.LootServeur;
import fr.heraliz.loot.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Loot (docs/METHODE_CLIENT_SERVEUR.md §4) : tables et tirages, zones et rotation du loot,
 * coffres T7 (loot, identification, réapparitions), commandes.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        LootServeur.brancher(busMod);
    }
}
