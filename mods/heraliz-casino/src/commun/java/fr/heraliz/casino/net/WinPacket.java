package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Gain d'un joueur (serveur -> client) : bandeau anime a l'ecran.
 */
public record WinPacket(long amount, float multiplier, int machine) {
    public static void encode(WinPacket p, FriendlyByteBuf buf) {
        buf.writeVarLong(p.amount);
        buf.writeFloat(p.multiplier);
        buf.writeVarInt(p.machine);
    }

    public static WinPacket decode(FriendlyByteBuf buf) {
        return new WinPacket(buf.readVarLong(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(WinPacket p, Supplier<NetworkEvent.Context> ctx) {
        CasinoNet.RECEPTION_CLIENT.accept(p);
    }
}
