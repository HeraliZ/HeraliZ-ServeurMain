package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Statistiques du casino pour la Tablette du Directeur : demande (client -> serveur, JSON vide) et reponse
 * (serveur -> client, JSON). Reserve aux operateurs, verifie cote serveur.
 */
public record AdminStatsPacket(String json) {
    private static final int MAX = 1 << 20;

    public static void encode(AdminStatsPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.json, MAX);
    }

    public static AdminStatsPacket decode(FriendlyByteBuf buf) {
        return new AdminStatsPacket(buf.readUtf(MAX));
    }

    /** Demande des statistiques (client -> serveur). */
    public record Request() {
        public static void encode(Request p, FriendlyByteBuf buf) {
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        /** Cote serveur : un operateur demande les statistiques (traitement : serveur/ReceptionServeur). */
        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CasinoNet.RECEPTION_SERVEUR.accept(new CasinoNet.Recu(player, p));
        }
    }

    /** Cote client : statistiques recues. */
    public static void handle(AdminStatsPacket p, Supplier<NetworkEvent.Context> ctx) {
        CasinoNet.RECEPTION_CLIENT.accept(p);
    }
}
