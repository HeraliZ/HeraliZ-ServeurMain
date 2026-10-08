package fr.heraliz.wonder.sliquifier;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.sliquifier.serveur.SliqCommandes;
import fr.heraliz.wonder.sliquifier.serveur.SliqPap;
import fr.heraliz.wonder.sliquifier.serveur.SliqServeur;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/** Partie serveur du Sliquifier (reprise telle quelle de Sliquifier.init) : flaques, glissades, commandes, TaCZ. */
public final class SliquifierServeur {
    private SliquifierServeur() {
    }

    public static void brancher(IEventBus mod, IEventBus forge) {
        forge.addListener(SliqServeur::tick);
        forge.addListener(SliqServeur::connexion);
        forge.addListener(SliqServeur::changementDimension);
        forge.addListener(SliqServeur::reapparition);
        forge.addListener(SliqServeur::dechargement);
        forge.addListener(SliqServeur::arret);
        forge.addListener(SliqCommandes::enregistrer);
        forge.addListener(SliqCommandes::stressTick);
        forge.addListener(SliqPap::tick);
        if (ModList.get().isLoaded("tacz")) {
            fr.heraliz.wonder.sliquifier.serveur.SliqTacz.brancher(forge);
            HeralizWonder.LOG.info("[sliquifier] TaCZ present : tirs du Sliquifier branches");
        }
    }
}
