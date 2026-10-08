package fr.heraliz.wonder.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Impact d'un rayon : {@code arme} ({@link fr.heraliz.wonder.Armes#RAYON} = eclaboussure, {@link fr.heraliz.wonder.Armes#MK2}
 * = eclair, {@link fr.heraliz.wonder.Armes#DG2} = eclair perdu), niveau de Pack-a-Punch, point, face touchee (-1 =
 * creature) et rayon de l'eclaboussure en dixiemes de bloc.
 */
public record Impact(byte arme, byte pap, float x, float y, float z, byte face, byte rayon) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<Impact> RECEPTION_CLIENT = new Crochet<>();

    public static void ecrire(Impact m, FriendlyByteBuf b) {
        b.writeByte(m.arme);
        b.writeByte(m.pap);
        b.writeFloat(m.x);
        b.writeFloat(m.y);
        b.writeFloat(m.z);
        b.writeByte(m.face);
        b.writeByte(m.rayon);
    }

    public static Impact lire(FriendlyByteBuf b) {
        return new Impact(b.readByte(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(), b.readByte());
    }

    public static void recevoir(Impact m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
