package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Le joueur paie son entree au casino (client -> serveur). Le serveur verifie tout : zone, prix, jetons ou objets.
 */
public record ZonePayPacket() {
    public static void encode(ZonePayPacket p, FriendlyByteBuf buf) {
    }

    public static ZonePayPacket decode(FriendlyByteBuf buf) {
        return new ZonePayPacket();
    }

    public static void handle(ZonePayPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) CasinoNet.RECEPTION_SERVEUR.accept(new CasinoNet.Recu(player, p));
    }
}
