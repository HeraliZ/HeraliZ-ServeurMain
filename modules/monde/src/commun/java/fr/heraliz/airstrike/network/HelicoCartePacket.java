package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> tous les joueurs de la carte (overworld), toutes les 2 s tant qu'un hélicoptère ou son épave existe : sa
 * marque sur la carte M (position en direct, épave à l'endroit du crash), puis « retiré » à la fin. Rien hors événement.
 * Affichage seulement (le jeu du joueur oublie une marque qui n'est plus rafraîchie).
 */
public class HelicoCartePacket {
    public static final byte HELICO = 0;
    public static final byte EPAVE = 1;
    public static final byte RETIRE = 2;

    public final byte type;
    public final int id;
    public final float x, z, lacet;

    public HelicoCartePacket(byte type, int id, double x, double z, float lacet) {
        this.type = type;
        this.id = id;
        this.x = (float) x;
        this.z = (float) z;
        this.lacet = lacet;
    }

    public HelicoCartePacket(FriendlyByteBuf buf) {
        this.type = buf.readByte();
        this.id = buf.readVarInt();
        this.x = buf.readFloat();
        this.z = buf.readFloat();
        this.lacet = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type);
        buf.writeVarInt(id);
        buf.writeFloat(x);
        buf.writeFloat(z);
        buf.writeFloat(lacet);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoCarte.recevoir (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
