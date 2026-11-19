package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueurs proches : une roquette de l'hélicoptère (19/11) part (point de départ et vitesse en blocs par tick :
 * le jeu du joueur la fait voler en ligne droite, avec sa traînée de fumée) ou n'est plus là (explosion, sortie de la zone
 * chargée : le jeu cesse de la dessiner ; l'explosion elle-même arrive par un {@link ImpactPacket}). Le vol, l'impact et
 * les dégâts sont décidés par le serveur ; ce paquet ne sert qu'à l'affichage.
 */
public class HelicoRoquettePacket {
    public static final byte LANCEE = 0;
    public static final byte FINIE = 1;

    public final byte type;
    public final int id;
    public final float x, y, z, vx, vy, vz;

    public HelicoRoquettePacket(byte type, int id, double x, double y, double z, double vx, double vy, double vz) {
        this.type = type;
        this.id = id;
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        this.vx = (float) vx;
        this.vy = (float) vy;
        this.vz = (float) vz;
    }

    public HelicoRoquettePacket(FriendlyByteBuf buf) {
        this.type = buf.readByte();
        this.id = buf.readVarInt();
        this.x = buf.readFloat();
        this.y = buf.readFloat();
        this.z = buf.readFloat();
        this.vx = buf.readFloat();
        this.vy = buf.readFloat();
        this.vz = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type);
        buf.writeVarInt(id);
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeFloat(vx);
        buf.writeFloat(vy);
        buf.writeFloat(vz);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoClient.roquette (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
