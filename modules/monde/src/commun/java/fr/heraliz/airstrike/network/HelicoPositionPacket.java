package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueurs à moins de {@code HelicopterServeur.PORTEE_SON} blocs, toutes les {@code HelicopterServeur.DIFFUSION}
 * ticks (19/11 : 4 fois par seconde, avant une) : position, vitesse, cap, assiette, régime du rotor et santé d'un
 * hélicoptère, au-delà de la distance où le serveur envoie l'entité elle-même (64 blocs). Le jeu du joueur en fait le son
 * lointain du rotor et, depuis le 19/11, le modèle de l'hélicoptère jusqu'à 320 blocs (HelicoClient : trajectoire
 * prolongée et lissée entre deux paquets). Affichage seulement.
 */
public class HelicoPositionPacket {
    public final int entite;
    public final float x, y, z, vx, vy, vz;
    public final float rotor;
    public final byte phase;
    /** Cap (degrés de Minecraft), tangage et roulis (degrés), santé de 0 à 1 (19/11). */
    public final float lacet, tangage, roulis, sante;

    public HelicoPositionPacket(int entite, double x, double y, double z, double vx, double vy, double vz, float rotor, byte phase,
                                float lacet, float tangage, float roulis, float sante) {
        this.entite = entite;
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        this.vx = (float) vx;
        this.vy = (float) vy;
        this.vz = (float) vz;
        this.rotor = rotor;
        this.phase = phase;
        this.lacet = lacet;
        this.tangage = tangage;
        this.roulis = roulis;
        this.sante = sante;
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
        this.lacet = buf.readFloat();
        this.tangage = buf.readFloat();
        this.roulis = buf.readFloat();
        this.sante = buf.readFloat();
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
        buf.writeFloat(lacet);
        buf.writeFloat(tangage);
        buf.writeFloat(roulis);
        buf.writeFloat(sante);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoClient.position (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
