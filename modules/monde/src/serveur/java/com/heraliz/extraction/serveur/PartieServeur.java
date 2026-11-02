package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Partie serveur de HeraliZ Extraction (docs/METHODE_CLIENT_SERVEUR.md §4) : pose du terminal, chronos des joueurs,
 * téléportation, zones fixes et commandes. Les commandes (ExtractionCommand) s'inscrivent seules.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // Réglages COMMON déclarés ici, côté serveur seulement (audit du 02/11, point 11) : même fichier qu'avant
        // (config/heraliz_extraction-common.toml du serveur, valeurs gardées), plus jamais écrit chez les joueurs.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ExtractionConfig.SPEC, "heraliz_extraction-common.toml");
        ModRegistry.TERMINAL_USINE.serveur(ExtractionTerminalEntityServeur::new);
        ModRegistry.TERMINAL_ITEM_USINE.serveur(ExtractionTerminalItemServeur::new);
    }
}
