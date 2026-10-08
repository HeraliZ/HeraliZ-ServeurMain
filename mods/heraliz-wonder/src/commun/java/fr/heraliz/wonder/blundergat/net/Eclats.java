package fr.heraliz.wonder.blundergat.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Impacts des obus d'un tir de Blundergat pendant un tick : le tireur, le niveau de Pack-a-Punch (The Sweeper), puis
 * chaque impact : point, face du bloc (-1 = creature), drapeaux ({@link #MORT} : la creature est morte). 6 + 13 octets par
 * impact ; un seul paquet par tireur et par tick (les 8 obus d'une gerbe arrivent ensemble).
 */
public record Eclats(int tireur, byte pap, int n, float[] pos, byte[] faces, byte[] drapeaux) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<Eclats> RECEPTION_CLIENT = new Crochet<>();

    public static final byte MORT = 1;
    public static final int MAX = 32;

    public static void ecrire(Eclats m, FriendlyByteBuf b) {
        b.writeVarInt(m.tireur);
        b.writeByte(m.pap);
        b.writeByte(m.n);
        for (int i = 0; i < m.n; i++) {
            b.writeFloat(m.pos[i * 3]);
            b.writeFloat(m.pos[i * 3 + 1]);
            b.writeFloat(m.pos[i * 3 + 2]);
            b.writeByte(m.faces[i]);
            b.writeByte(m.drapeaux[i]);
        }
    }

    public static Eclats lire(FriendlyByteBuf b) {
        int tireur = b.readVarInt();
        byte pap = b.readByte();
        int n = Math.min(MAX, b.readUnsignedByte());
        float[] pos = new float[n * 3];
        byte[] f = new byte[n], d = new byte[n];
        for (int i = 0; i < n; i++) {
            pos[i * 3] = b.readFloat();
            pos[i * 3 + 1] = b.readFloat();
            pos[i * 3 + 2] = b.readFloat();
            f[i] = b.readByte();
            d[i] = b.readByte();
        }
        return new Eclats(tireur, pap, n, pos, f, d);
    }

    public static void recevoir(Eclats m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
