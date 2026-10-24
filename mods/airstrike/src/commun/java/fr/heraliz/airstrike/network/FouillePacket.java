package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Joueur -> serveur : clic droit sur un morceau d'épave ou une caisse de l'hélicoptère (appui), toujours tenu (maintien,
 * toutes les 4 ticks) ou relâché. Le serveur décide de tout (portée, chaleur, déjà fouillé, temps tenu) ; un maintien
 * qui n'arrive plus pendant 12 ticks vaut un relâchement.
 */
public class FouillePacket {
    public static final byte APPUI = 0, MAINTIEN = 1, RELACHE = 2;

    public final int entite;
    public final byte etape;

    public FouillePacket(int entite, byte etape) {
        this.entite = entite;
        this.etape = etape;
    }

    public FouillePacket(FriendlyByteBuf buf) {
        this.entite = buf.readVarInt();
        this.etape = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entite);
        buf.writeByte(etape);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_SERVEUR.accept(new Network.Recu(ctx.get().getSender(), this));   // serveur : PartieServeur
        ctx.get().setPacketHandled(true);
    }
}
