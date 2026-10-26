package fr.heralisz.economie.client;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.economie.network.Packets;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de HeraliZ Economie : branche la reception des paquets (solde, reglages) sur {@link ClientSolde}. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Packets.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof Packets.SoldeS2C s) ClientSolde.recevoir(s);
            else if (m instanceof Packets.ReglagesS2C r) ClientSolde.reglages(r);
        });
    }
}
