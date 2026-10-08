package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/** Partie serveur du Winter's Howl (reprise telle quelle de WintersHowl.init) : souffle, gel, commandes, TaCZ. */
public final class WintersHowlServeur {
    private WintersHowlServeur() {
    }

    public static void brancher(IEventBus mod, IEventBus forge) {
        forge.addListener(Givre::tick);
        forge.addListener(EventPriority.HIGHEST, Givre::attaque);
        forge.addListener(Givre::sortie);
        forge.addListener(Givre::arret);
        forge.addListener(CommandesGivre::enregistrer);
        forge.addListener(CommandesGivre::stressTick);
        forge.addListener(PerfGivre::tick);
        if (ModList.get().isLoaded("tacz")) {
            TaczGivre.brancher(forge);
            HeralizWonder.LOG.info("[wonder] Winter's Howl branche sur TaCZ");
        }
    }
}
