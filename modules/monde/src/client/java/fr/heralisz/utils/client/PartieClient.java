package fr.heralisz.utils.client;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.cine.Cinematique;
import fr.heralisz.utils.cine.CineReseau;
import fr.heralisz.utils.client.onglet.PanneauOnglet;
import fr.heralisz.utils.deco.PontBlockEntity;
import fr.heralisz.utils.deco.PontTick;
import fr.heralisz.utils.pont.Partie;
import fr.heralisz.utils.train.Train;
import fr.heralisz.utils.train.TrainClient;
import fr.heralisz.utils.train.Voyageur;
import fr.heralisz.utils.train.VoyageursClient;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de heralisz_utils, porteur du jar fusionné : panneau de l'onglet commun « HeraliZ · Utils », paquets reçus
 * du serveur (train, cinématique, cycle jour / nuit) et ticks client (voyageurs du train, ponts de corde). Le reste de l'affichage (ciel,
 * brouillard, tempête de sable, rendus, sons, touches) s'inscrit tout seul (@Mod.EventBusSubscriber Dist.CLIENT).
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        PanneauOnglet.installer(HeraliszUtils.TAB, HeraliszUtils.ONGLET, "onglet.heralisz_utils.titre", "onglet.heralisz_utils.tous");
        Train.RECEPTION_CLIENT.brancher(TrainClient::recevoir);
        CineReseau.RECEPTION_CLIENT.brancher(m -> Cinematique.demarrer());
        Voyageur.TICK_CLIENT.brancher(VoyageursClient::tick);
        PontBlockEntity.TICK_CLIENT.brancher(PontTick::tick);
        // cycle jour / nuit : canal heralisz_utils:cycle (enregistré une seule fois, aussi par la partie serveur du -complet)
        // et réglages reçus du serveur pour le prédicteur de l'heure (CycleClient)
        fr.heralisz.utils.cycle.Cycle.enregistrer();
        fr.heralisz.utils.cycle.Cycle.RECEPTION_CLIENT.brancher(fr.heralisz.utils.cycle.CycleClient::recevoir);
    }
}
