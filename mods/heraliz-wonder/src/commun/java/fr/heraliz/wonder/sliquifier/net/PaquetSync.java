package fr.heraliz.wonder.sliquifier.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Etat complet pour un joueur qui arrive (connexion, changement de dimension, reapparition) : reglages de la glisse des
 * joueurs et toutes les flaques de sa dimension (10 octets + ~22 par flaque, 64 au plus).
 */
public record PaquetSync(boolean glisseJoueurs, float boost, float vitesseMax, int n, float[] f, int[] ids) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<PaquetSync> RECEPTION_CLIENT = new Crochet<>();

    public static final int MAX = 64;
    /** Valeurs par flaque dans {@code f} : x, y, z, rayon, duree, age, pap. */
    public static final int CHAMPS = 7;

    public static void ecrire(PaquetSync m, FriendlyByteBuf b) {
        b.writeBoolean(m.glisseJoueurs);
        b.writeFloat(m.boost);
        b.writeFloat(m.vitesseMax);
        b.writeByte(m.n);
        for (int i = 0; i < m.n; i++) {
            int o = i * CHAMPS;
            b.writeVarInt(m.ids[i]);
            b.writeFloat(m.f[o]);
            b.writeFloat(m.f[o + 1]);
            b.writeFloat(m.f[o + 2]);
            b.writeShort(Math.round(m.f[o + 3] * 100));
            b.writeVarInt((int) m.f[o + 4]);
            b.writeVarInt((int) m.f[o + 5]);
            b.writeByte((int) m.f[o + 6]);
        }
    }

    public static PaquetSync lire(FriendlyByteBuf b) {
        boolean g = b.readBoolean();
        float boost = b.readFloat(), vmax = b.readFloat();
        int n = Math.min(MAX, b.readUnsignedByte());
        float[] f = new float[n * CHAMPS];
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            int o = i * CHAMPS;
            ids[i] = b.readVarInt();
            f[o] = b.readFloat();
            f[o + 1] = b.readFloat();
            f[o + 2] = b.readFloat();
            f[o + 3] = b.readShort() / 100f;
            f[o + 4] = b.readVarInt();
            f[o + 5] = b.readVarInt();
            f[o + 6] = b.readByte();
        }
        return new PaquetSync(g, boost, vmax, n, f, ids);
    }

    public static void recevoir(PaquetSync m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
