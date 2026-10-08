package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Montant du jackpot progressif (serveur -> clients), affiche sur les machines a sous.
 */
public record JackpotPacket(long value) {
    /** Dernier montant recu (cote client). */
    public static volatile long clientValue = 0;

    public static void encode(JackpotPacket p, FriendlyByteBuf buf) {
        buf.writeVarLong(p.value);
    }

    public static JackpotPacket decode(FriendlyByteBuf buf) {
        return new JackpotPacket(buf.readVarLong());
    }

    public static void handle(JackpotPacket p, Supplier<NetworkEvent.Context> ctx) {
        clientValue = p.value;
    }
}
