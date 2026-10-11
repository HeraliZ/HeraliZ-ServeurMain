package fr.heraliz.usure.client;

import fr.heralisz.utils.pont.Partie;
import fr.heraliz.usure.Reseau;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de heraliz_usure (jar fusionné HeraliZ Utils) : HUD en bas à gauche, infobulles, écrans, règles reçues. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ClientUsure.init(busMod);
        Reseau.RECEPTION_CLIENT.brancher(m -> ClientUsure.recevoirRegles(m.regles(), m.ouvrirEcran()));
    }
}
