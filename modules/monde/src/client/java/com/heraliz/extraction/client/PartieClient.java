package com.heraliz.extraction.client;

import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.heraliz.extraction.pont.Partie;
import com.heraliz.extraction.reseau.Reseau;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Extraction (docs/METHODE_CLIENT_SERVEUR.md §4) : PC en main et à genoux, écran, rendu du
 * terminal et de la zone, particules, chrono d'extraction en haut de l'écran ({@link HudExtraction}). Rendus et écouteurs (ClientSetup, ClientEvents) s'inscrivent seuls.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ExtractionTerminalItem.RENDU_CLIENT.brancher(c -> c.accept(TerminalClientExtensions.INSTANCE));
        Reseau.PROGRESSION_CLIENT.brancher(HudExtraction::recevoir);
    }
}
