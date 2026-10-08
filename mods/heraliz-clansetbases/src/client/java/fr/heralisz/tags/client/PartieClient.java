package fr.heralisz.tags.client;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.util.Langue;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de HeraliZ Tags : langue du jeu, reception des paquets du serveur (aiguillage vers ClientHandlers). */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Langue.CLIENT = LangueClient::francais;
        Packets.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof Packets.EtatS2C p) ClientHandlers.etat(p);
            else if (m instanceof Packets.TagS2C p) ClientHandlers.tag(p);
            else if (m instanceof Packets.TagRetireS2C p) ClientHandlers.tagRetire(p);
            else if (m instanceof Packets.OuvrirIndexS2C) ClientHandlers.ouvrirIndex();
        });
    }
}
