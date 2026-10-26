package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Extraction (docs/METHODE_CLIENT_SERVEUR.md §4) : pose du terminal, chronos des joueurs,
 * téléportation, zones fixes et commandes. Les commandes (ExtractionCommand) s'inscrivent seules.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ModRegistry.TERMINAL_USINE.serveur(ExtractionTerminalEntityServeur::new);
        ModRegistry.TERMINAL_ITEM_USINE.serveur(ExtractionTerminalItemServeur::new);
    }
}
