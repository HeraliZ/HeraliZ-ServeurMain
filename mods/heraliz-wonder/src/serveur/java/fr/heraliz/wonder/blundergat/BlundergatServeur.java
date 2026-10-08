package fr.heraliz.wonder.blundergat;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.blundergat.serveur.BlunderCommandes;
import fr.heraliz.wonder.blundergat.serveur.BlunderNoms;
import fr.heraliz.wonder.blundergat.serveur.BlunderPerf;
import fr.heraliz.wonder.blundergat.serveur.Cartouches;
import fr.heraliz.wonder.blundergat.serveur.EclatsServeur;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/** Partie serveur du Blundergat et de l'Acid Gat (reprise telle quelle de Blundergat.init) : ecouteurs serveur et TaCZ. */
public final class BlundergatServeur {
    private BlundergatServeur() {
    }

    public static void brancher(IEventBus mod, IEventBus forge) {
        forge.addListener(Cartouches::tick);
        forge.addListener(Cartouches::dechargement);
        forge.addListener(EclatsServeur::tick);
        forge.addListener(BlunderNoms::tick);
        forge.addListener(BlunderPerf::tick);
        forge.addListener(BlunderCommandes::enregistrer);
        forge.addListener(BlunderCommandes::stressTick);
        if (ModList.get().isLoaded("tacz")) {
            fr.heraliz.wonder.blundergat.serveur.BlunderTacz.brancher(forge);
            HeralizWonder.LOG.info("[wonder] Blundergat et Acid Gat branches");
        }
    }
}
