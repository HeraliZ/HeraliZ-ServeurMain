package fr.heraliz.wonder.jetgun.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Créatures déchiquetées par un Jet Gun pendant un tick : le tireur, puis pour chacune son id et sa position (centre).
 * 3 + 13 octets par créature au plus (16 par paquet). Le client cache le corps, fait voler les lambeaux vers l'entrée
 * d'air, la brume rouge et le bruit de broyage.
 */
public record Broyage(int tireur, int n, int[] ids, float[] x, float[] y, float[] z) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<Broyage> RECEPTION_CLIENT = new Crochet<>();

    public static final int MAX = 16;

    public static void ecrire(Broyage m, FriendlyByteBuf b) {
        b.writeVarInt(m.tireur);
        b.writeByte(m.n);
        for (int i = 0; i < m.n; i++) {
            b.writeVarInt(m.ids[i]);
            b.writeFloat(m.x[i]);
            b.writeFloat(m.y[i]);
            b.writeFloat(m.z[i]);
        }
    }

    public static Broyage lire(FriendlyByteBuf b) {
        int tireur = b.readVarInt();
        int n = Math.min(MAX, b.readUnsignedByte());
        int[] ids = new int[n];
        float[] x = new float[n], y = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) {
            ids[i] = b.readVarInt();
            x[i] = b.readFloat();
            y[i] = b.readFloat();
            z[i] = b.readFloat();
        }
        return new Broyage(tireur, n, ids, x, y, z);
    }

    public static void recevoir(Broyage m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
