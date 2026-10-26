package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Tablette du Directeur (client -> serveur) : nouveaux reglages d'une machine. Reserve aux operateurs (niveau 2),
 * verifie ici cote serveur.
 */
public record AdminConfigPacket(String machine, String json) {
    static final int MAX_JSON = 32767;

    public static void encode(AdminConfigPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.machine, 64);
        buf.writeUtf(p.json, MAX_JSON);
    }

    public static AdminConfigPacket decode(FriendlyByteBuf buf) {
        return new AdminConfigPacket(buf.readUtf(64), buf.readUtf(MAX_JSON));
    }

    /** Cote serveur : traitement dans serveur/ReceptionServeur (operateurs seulement, verifie la-bas). */
    public static void handle(AdminConfigPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) CasinoNet.RECEPTION_SERVEUR.accept(new CasinoNet.Recu(player, p));
    }
}
