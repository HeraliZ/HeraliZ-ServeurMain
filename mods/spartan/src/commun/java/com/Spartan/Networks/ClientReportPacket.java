package com.Spartan.Networks;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * C->S : le client signale une trace suspecte dans son propre jeu.
 *
 * - "agent"  : un agent Java a été chargé au lancement (nom du fichier seulement,
 *              pas le chemin complet qui contiendrait le nom d'utilisateur Windows) ;
 * - "thread" : des threads sans nom tournent dans le jeu.
 *
 * C'est le serveur qui décide : les agents autorisés (ex. authlib-injector des
 * launchers crack) sont ignorés, le reste part en alerte au staff.
 * Best-effort : un client modifié peut ne rien signaler.
 */
public class ClientReportPacket {
    private static final int MAX_TYPE = 16;
    private static final int MAX_DETAIL = 200;

    private final String type;
    private final String detail;

    public ClientReportPacket(String type, String detail) {
        this.type = type;
        this.detail = detail;
    }

    public static ClientReportPacket decode(FriendlyByteBuf buf) {
        return new ClientReportPacket(buf.readUtf(MAX_TYPE), buf.readUtf(MAX_DETAIL));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cut(this.type, MAX_TYPE), MAX_TYPE);
        buf.writeUtf(cut(this.detail, MAX_DETAIL), MAX_DETAIL);
    }

    public String type() {
        return this.type;
    }

    public String detail() {
        return this.detail;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
