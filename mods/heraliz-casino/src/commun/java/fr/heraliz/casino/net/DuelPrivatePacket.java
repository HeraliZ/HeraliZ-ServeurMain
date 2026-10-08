package fr.heraliz.casino.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Donnees secretes d'un duel (serveur -> un seul joueur) : sa flotte, son coup... jamais envoyees aux autres.
 */
public record DuelPrivatePacket(BlockPos pos, long[] data) {
    public static void encode(DuelPrivatePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeLongArray(p.data);
    }

    public static DuelPrivatePacket decode(FriendlyByteBuf buf) {
        return new DuelPrivatePacket(buf.readBlockPos(), buf.readLongArray(null, 64));
    }

    public static void handle(DuelPrivatePacket p, Supplier<NetworkEvent.Context> ctx) {
        CasinoNet.RECEPTION_CLIENT.accept(p);
    }
}
