package fr.heraliz.ping.serveur;

import fr.heraliz.ping.net.Paquets;
import fr.heraliz.ping.net.PingNet;
import fr.heraliz.ping.server.PingService;
import fr.heralisz.utils.pont.Partie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heraliz_ping (jar fusionne HeraliZ Utils, docs/METHODE_CLIENT_SERVEUR.md §4) : les evenements
 * serveur de l'ancien @Mod (meme place dans le constructeur), les paquets recus des joueurs (PingC2S, AnnulerC2S ->
 * PingService, sur le fil serveur comme avant) et la presence forcee des faux joueurs du banc d'essai.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.register(EvenementsServeur.class);
        PingNet.RECEPTION_SERVEUR.brancher(r -> {
            if (r.paquet() instanceof Paquets.PingC2S m) PingService.recevoir(r.joueur(), m);
            else if (r.paquet() instanceof Paquets.AnnulerC2S m) PingService.annuler(r.joueur(), m.id());
        });
        PingNet.PRESENCE_FORCEE = PingService::presenceForcee;
    }
}
