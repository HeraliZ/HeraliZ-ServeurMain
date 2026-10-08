package fr.heraliz.wonder.sliquifier.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Ce qui arrive a une creature : {@link #GLISSE} (elle glisse dans la direction (dx, dz) / 127 et tombe a la renverse),
 * {@link #LIQUEFIE} (elle fond), {@link #COLOSSE} (boss touche par la gelee : eclaboussure seulement). 5 a 7 octets.
 */
public record PaquetZombie(byte action, int id, byte dx, byte dz, byte pap) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<PaquetZombie> RECEPTION_CLIENT = new Crochet<>();

    public static final byte GLISSE = 0, LIQUEFIE = 1, COLOSSE = 2;

    public static void ecrire(PaquetZombie m, FriendlyByteBuf b) {
        b.writeByte(m.action);
        b.writeVarInt(m.id);
        b.writeByte(m.dx);
        b.writeByte(m.dz);
        b.writeByte(m.pap);
    }

    public static PaquetZombie lire(FriendlyByteBuf b) {
        return new PaquetZombie(b.readByte(), b.readVarInt(), b.readByte(), b.readByte(), b.readByte());
    }

    public static void recevoir(PaquetZombie m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
