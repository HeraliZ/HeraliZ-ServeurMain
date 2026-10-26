package fr.heraliz.ping.client;

import fr.heralisz.utils.pont.Partie;
import fr.heraliz.ping.net.Paquets;
import fr.heraliz.ping.net.PingNet;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de heraliz_ping (jar fusionné HeraliZ Utils) : construction du client et paquets reçus du serveur. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        PingClient.construire();
        PingNet.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof Paquets.PingS2C p) PingsClient.recevoir(p);
            else if (m instanceof Paquets.MajS2C p) PingsClient.maj(p);
            else if (m instanceof Paquets.ReglagesS2C p) PingsClient.reglages(p);
        });
    }
}
