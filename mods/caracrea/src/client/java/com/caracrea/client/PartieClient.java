package com.caracrea.client;

import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de CaraCrea (docs/METHODE_CLIENT_SERVEUR.md §4) : créateur de personnage, peinture des skins, micro,
 * codec et débruitage, lecture des voix, écrans de réglages.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        CaraCreaClient.init(busMod);
        CaraCreaNetwork.RECEPTION_CLIENT.brancher(PartieClient::recevoir);
    }

    /** Corps d'origine des gestionnaires des paquets serveur -> client (VoiceDown : fil réseau, comme avant). */
    private static void recevoir(Object paquet) {
        if (paquet instanceof CaraCreaNetwork.VoiceDown v) {
            ClientPacketHandler.voice(v);
        } else if (paquet instanceof CaraCreaNetwork.SyncAppearances s) {
            ClientPacketHandler.sync(s);
        } else if (paquet instanceof CaraCreaNetwork.OpenCreator o) {
            ClientPacketHandler.openCreator(o);
        } else if (paquet instanceof CaraCreaNetwork.CloseCreator c) {
            ClientPacketHandler.closeCreator(c);
        } else if (paquet instanceof CaraCreaNetwork.VoiceConfig c) {
            ClientPacketHandler.voiceConfig(c);
        } else if (paquet instanceof CaraCreaNetwork.OpenVoiceSettings o) {
            ClientPacketHandler.openVoiceSettings(o);
        } else if (paquet instanceof CaraCreaNetwork.VoiceRoster r) {
            ClientPacketHandler.roster(r);
        }
    }
}
