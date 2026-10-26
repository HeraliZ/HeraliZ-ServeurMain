package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueurs à moins de {@code HelicopterServeur.PORTEE_SON} blocs, une fois par seconde : position et vitesse d'un
 * hélicoptère, pour que son rotor s'entende de loin (jusqu'à ~250 blocs), au-delà de la distance où le serveur envoie
 * l'entité elle-même. Le jeu du joueur prolonge la trajectoire entre deux paquets. Affichage seulement.
 */
public class HelicoPositionPacket {
    public final int entite;
    public final float x, y, z, vx, vy, vz;
    public final float rotor;
    public final byte phase;

    public HelicoPositionPacket(int entite, double x, double y, double z, double vx, double vy, double vz, float rotor, byte phase) {
        this.entite = entite;
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        this.vx = (float) vx;
        this.vy = (float) vy;
        this.vz = (float) vz;
        this.rotor = rotor;
        this.phase = phase;
    }

    public HelicoPositionPacket(FriendlyByteBuf buf) {
        this.entite = buf.readVarInt();
        this.x = buf.readFloat();
        this.y = buf.readFloat();
        this.z = buf.readFloat();
        this.vx = buf.readFloat();
        this.vy = buf.readFloat();
        this.vz = buf.readFloat();
        this.rotor = buf.readFloat();
        this.phase = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entite);
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeFloat(vx);
        buf.writeFloat(vy);
        buf.writeFloat(vz);
        buf.writeFloat(rotor);
        buf.writeByte(phase);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoClient.position (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
