package fr.heraliz.airstrike.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueur qui fouille une épave : avancement de 0 à 1 (barre sous le viseur), ARRET (interrompue ou relâchée)
 * ou FINI (pièce récupérée). duree : ticks de la fouille entière (pour lisser la barre entre deux paquets).
 */
public class FouilleEtatPacket {
    public static final float ARRET = -1f, FINI = 2f;

    public final float progres;
    public final int duree;

    public FouilleEtatPacket(float progres, int duree) {
        this.progres = progres;
        this.duree = duree;
    }

    public FouilleEtatPacket(FriendlyByteBuf buf) {
        this.progres = buf.readFloat();
        this.duree = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(progres);
        buf.writeVarInt(duree);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        Network.RECEPTION_CLIENT.accept(this);   // client : HelicoClient (PartieClient)
        ctx.get().setPacketHandled(true);
    }
}
