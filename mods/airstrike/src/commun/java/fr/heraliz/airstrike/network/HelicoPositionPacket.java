package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueurs de la carte : où est l'hélicoptère (toutes les 10 ticks, jusqu'à porteeSuivi blocs). Le jeu du
 * joueur le dessine et l'entend même au-delà de sa distance de vue (l'entité, elle, n'est suivie que dans cette
 * distance). present = false : il n'existe plus (abattu au sol, parti, retiré).
 */
public class HelicoPositionPacket {
    public final int id;
    public final boolean present;
    public final double x, y, z;
    public final float yaw, vie;
    public final byte etat;

    public HelicoPositionPacket(int id, boolean present, double x, double y, double z, float yaw, float vie, byte etat) {
        this.id = id;
        this.present = present;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.vie = vie;
        this.etat = etat;
    }

    public HelicoPositionPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.present = buf.readBoolean();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yaw = buf.readFloat();
        this.vie = buf.readFloat();
        this.etat = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeBoolean(present);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(vie);
        buf.writeByte(etat);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoClient (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
