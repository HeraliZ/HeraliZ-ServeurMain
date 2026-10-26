package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueurs proches : un coup du minigun de l'hélicoptère (départ, arrivée, ce qu'il a touché), pour la traçante,
 * l'éclair à la bouche, la rotation des canons et les étincelles. Le coup lui-même (ligne de vue, dégâts) est décidé par le
 * serveur ; ce paquet ne sert qu'à l'affichage.
 */
public class HelicoTirPacket {
    public static final byte AIR = 0;
    public static final byte BLOC = 1;
    public static final byte CIBLE = 2;

    public final int entite;
    public final boolean droite;
    public final float x0, y0, z0, x1, y1, z1;
    public final byte impact;

    public HelicoTirPacket(int entite, boolean droite, double x0, double y0, double z0, double x1, double y1, double z1, byte impact) {
        this.entite = entite;
        this.droite = droite;
        this.x0 = (float) x0;
        this.y0 = (float) y0;
        this.z0 = (float) z0;
        this.x1 = (float) x1;
        this.y1 = (float) y1;
        this.z1 = (float) z1;
        this.impact = impact;
    }

    public HelicoTirPacket(FriendlyByteBuf buf) {
        this.entite = buf.readVarInt();
        this.droite = buf.readBoolean();
        this.x0 = buf.readFloat();
        this.y0 = buf.readFloat();
        this.z0 = buf.readFloat();
        this.x1 = buf.readFloat();
        this.y1 = buf.readFloat();
        this.z1 = buf.readFloat();
        this.impact = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entite);
        buf.writeBoolean(droite);
        buf.writeFloat(x0);
        buf.writeFloat(y0);
        buf.writeFloat(z0);
        buf.writeFloat(x1);
        buf.writeFloat(y1);
        buf.writeFloat(z1);
        buf.writeByte(impact);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoClient.tir (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
