package fr.heraliz.clashwar.client;

import fr.heraliz.clashwar.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ ClashWar (docs/METHODE_CLIENT_SERVEUR.md §4) : HUD et écrans du moteur et du tournoi,
 * réception des paquets serveur -> client.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        fr.heraliz.clashwar.moteur.client.HudMoteur.enregistrer(busMod);
        fr.heraliz.clashwar.tournoi.client.ClashWarClient.initMod(busMod);
        fr.heraliz.clashwar.tournoi.net.CwPaquets.RECEPTION_CLIENT.brancher(fr.heraliz.clashwar.tournoi.net.ReceptionCwPaquets::recevoir);
        fr.heraliz.clashwar.moteur.net.PaquetsMoteur.RECEPTION_CLIENT.brancher(fr.heraliz.clashwar.moteur.net.ReceptionPaquetsMoteur::recevoir);
    }
}
