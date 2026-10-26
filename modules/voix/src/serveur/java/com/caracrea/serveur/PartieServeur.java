package com.caracrea.serveur;

import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.pont.Partie;
import com.caracrea.server.CharacterService;
import com.caracrea.server.ServerEvents;
import com.caracrea.server.VoiceServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de CaraCrea (docs/METHODE_CLIENT_SERVEUR.md §4) : stockage et diffusion des apparences, relais du chat
 * vocal (UDP chiffré et repli par la connexion du jeu), groupes, sourdines, commandes, API pour les autres mods.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        CaraCreaNetwork.RECEPTION_SERVEUR.brancher(PartieServeur::recevoir);
    }

    /** Corps d'origine des gestionnaires des paquets client -> serveur (VoiceUp : fil réseau, comme avant). */
    private static void recevoir(CaraCreaNetwork.Recu recu) {
        ServerPlayer player = recu.joueur();
        Object paquet = recu.paquet();
        if (paquet instanceof CaraCreaNetwork.VoiceUp v) {
            VoiceServer.relayTcp(player.getUUID(), v.sequence(), v.flags(), v.data(), 0, v.data().length);
        } else if (paquet instanceof CaraCreaNetwork.SaveAppearance a) {
            CharacterService.save(player, a.appearance());
        } else if (paquet instanceof CaraCreaNetwork.RequestCreator) {
            CharacterService.requestOpen(player);
        } else if (paquet instanceof CaraCreaNetwork.VoiceStateUp s) {
            VoiceServer.clientState(player, s);
        } else if (paquet instanceof CaraCreaNetwork.GroupAction g) {
            VoiceServer.groupAction(player, g);
        }
    }
}
