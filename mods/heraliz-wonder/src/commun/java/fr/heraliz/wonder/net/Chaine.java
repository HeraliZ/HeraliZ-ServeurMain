package fr.heraliz.wonder.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Foudre en chaine d'un tir de Wunderwaffe : le tireur (pour partir du canon), le niveau de Pack-a-Punch, des drapeaux
 * ({@link #BLOC} : le premier eclair a frappe un bloc), le point d'impact du premier eclair, puis chaque cible dans l'ordre
 * (id d'entite + centre au moment du tir). L'arc {@code i} arrive {@code i * 0,5} tick apres le tir, comme les degats.
 */
public record Chaine(int tireur, byte pap, byte drapeaux, float ix, float iy, float iz, int n, int[] ids, float[] pos) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<Chaine> RECEPTION_CLIENT = new Crochet<>();

    public static final byte BLOC = 1;
    public static final int MAX = 64;

    public static void ecrire(Chaine m, FriendlyByteBuf b) {
        b.writeVarInt(m.tireur);
        b.writeByte(m.pap);
        b.writeByte(m.drapeaux);
        b.writeFloat(m.ix);
        b.writeFloat(m.iy);
        b.writeFloat(m.iz);
        b.writeByte(m.n);
        for (int i = 0; i < m.n; i++) {
            b.writeVarInt(m.ids[i]);
            b.writeFloat(m.pos[i * 3]);
            b.writeFloat(m.pos[i * 3 + 1]);
            b.writeFloat(m.pos[i * 3 + 2]);
        }
    }

    public static Chaine lire(FriendlyByteBuf b) {
        int tireur = b.readVarInt();
        byte pap = b.readByte(), dr = b.readByte();
        float x = b.readFloat(), y = b.readFloat(), z = b.readFloat();
        int n = Math.min(MAX, b.readUnsignedByte());
        int[] ids = new int[n];
        float[] pos = new float[n * 3];
        for (int i = 0; i < n; i++) {
            ids[i] = b.readVarInt();
            pos[i * 3] = b.readFloat();
            pos[i * 3 + 1] = b.readFloat();
            pos[i * 3 + 2] = b.readFloat();
        }
        return new Chaine(tireur, pap, dr, x, y, z, n, ids, pos);
    }

    public static void recevoir(Chaine m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
