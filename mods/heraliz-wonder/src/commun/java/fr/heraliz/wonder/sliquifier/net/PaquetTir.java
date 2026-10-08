package fr.heraliz.wonder.sliquifier.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Une boule de gelee part : numero, tireur, niveau de Pack-a-Punch, depart, vitesse, gravite (~34 octets). */
public record PaquetTir(int boule, int tireur, byte pap, float ox, float oy, float oz, float vx, float vy, float vz, float gravite) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<PaquetTir> RECEPTION_CLIENT = new Crochet<>();

    public static void ecrire(PaquetTir m, FriendlyByteBuf b) {
        b.writeVarInt(m.boule);
        b.writeVarInt(m.tireur);
        b.writeByte(m.pap);
        b.writeFloat(m.ox);
        b.writeFloat(m.oy);
        b.writeFloat(m.oz);
        b.writeFloat(m.vx);
        b.writeFloat(m.vy);
        b.writeFloat(m.vz);
        b.writeFloat(m.gravite);
    }

    public static PaquetTir lire(FriendlyByteBuf b) {
        return new PaquetTir(b.readVarInt(), b.readVarInt(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat());
    }

    public static void recevoir(PaquetTir m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
