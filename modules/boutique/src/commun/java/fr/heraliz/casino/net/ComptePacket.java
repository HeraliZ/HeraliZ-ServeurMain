package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Solde du compte de capsules du joueur (serveur -> ce joueur seulement, 27/10) : envoye a la connexion et a chaque
 * changement. Il ne sert qu'a l'affichage (boutons de la caisse) : le serveur revérifie tout a chaque paiement.
 */
public record ComptePacket(long solde) {
    /** Dernier solde recu (cote client). */
    public static volatile long clientSolde = 0;

    public static void encode(ComptePacket p, FriendlyByteBuf buf) {
        buf.writeVarLong(Math.max(0, p.solde));
    }

    public static ComptePacket decode(FriendlyByteBuf buf) {
        return new ComptePacket(buf.readVarLong());
    }

    public static void handle(ComptePacket p, Supplier<NetworkEvent.Context> ctx) {
        clientSolde = Math.max(0, p.solde);
    }
}
