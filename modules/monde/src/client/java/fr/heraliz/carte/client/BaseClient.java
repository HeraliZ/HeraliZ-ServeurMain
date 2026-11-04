package fr.heraliz.carte.client;

import fr.heraliz.carte.CarteNet;

/**
 * Base du clan du joueur, reçue du serveur à l'ouverture de la carte (04/11) : l'armoire (Claim Block) de la base, pour y
 * retourner à pied. Vide sans clan ou sans base posée.
 */
final class BaseClient {
    private static volatile CarteNet.BaseS2C base = CarteNet.BaseS2C.AUCUNE;

    private BaseClient() {
    }

    static void recevoir(CarteNet.BaseS2C m) {
        base = m;
    }

    static CarteNet.BaseS2C base() {
        return base;
    }

    /** Demande au serveur (seulement s'il a le canal de la carte : serveur sans le mod, rien n'est envoyé). */
    static void demander() {
        net.minecraft.client.multiplayer.ClientPacketListener c = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (c != null && CarteNet.CANAL.isRemotePresent(c.getConnection())) CarteNet.CANAL.sendToServer(new CarteNet.BaseC2S());
    }

    static void vider() {
        base = CarteNet.BaseS2C.AUCUNE;
    }
}
