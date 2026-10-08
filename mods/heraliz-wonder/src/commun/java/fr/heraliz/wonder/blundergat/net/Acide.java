package fr.heraliz.wonder.blundergat.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Cartouches d'acide de l'Acid Gat. Trois genres :
 * <ul>
 *   <li>{@link #TIR} : le tireur, le niveau de Pack-a-Punch, puis chaque cartouche (id, depart, vitesse par tick) — le
 *   client simule le vol avec la meme physique que le serveur ;</li>
 *   <li>{@link #COLLE} : la cartouche {@code id} a colle (sur la creature {@code entite}, ou sur un bloc si -1) au point donne ;</li>
 *   <li>{@link #EXPLOSE} : la cartouche {@code id} explose au point donne (rayon en dixiemes de bloc), et les creatures
 *   touchees (ids) se mettent a fondre.</li>
 * </ul>
 */
public record Acide(byte genre, int tireur, byte pap, int n, int[] ids, float[] vec, int entite, byte rayon) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<Acide> RECEPTION_CLIENT = new Crochet<>();

    public static final byte TIR = 0, COLLE = 1, EXPLOSE = 2;
    public static final int MAX = 48;

    public static Acide tir(int tireur, byte pap, int n, int[] ids, float[] posVit) {
        return new Acide(TIR, tireur, pap, n, ids, posVit, -1, (byte) 0);
    }

    public static Acide colle(int id, int entite, float x, float y, float z) {
        return new Acide(COLLE, -1, (byte) 0, 1, new int[]{id}, new float[]{x, y, z}, entite, (byte) 0);
    }

    /** {@code ids[0]} = la cartouche, puis les creatures touchees. */
    public static Acide explose(int id, byte pap, float x, float y, float z, byte rayon, int nTouchees, int[] touchees) {
        int[] ids = new int[1 + nTouchees];
        ids[0] = id;
        System.arraycopy(touchees, 0, ids, 1, nTouchees);
        return new Acide(EXPLOSE, -1, pap, ids.length, ids, new float[]{x, y, z}, -1, rayon);
    }

    public static void ecrire(Acide m, FriendlyByteBuf b) {
        b.writeByte(m.genre);
        switch (m.genre) {
            case TIR -> {
                b.writeVarInt(m.tireur);
                b.writeByte(m.pap);
                b.writeByte(m.n);
                for (int i = 0; i < m.n; i++) {
                    b.writeVarInt(m.ids[i]);
                    for (int k = 0; k < 6; k++) b.writeFloat(m.vec[i * 6 + k]);
                }
            }
            case COLLE -> {
                b.writeVarInt(m.ids[0]);
                b.writeVarInt(m.entite + 1);
                b.writeFloat(m.vec[0]);
                b.writeFloat(m.vec[1]);
                b.writeFloat(m.vec[2]);
            }
            default -> {
                b.writeByte(m.pap);
                b.writeByte(m.rayon);
                b.writeFloat(m.vec[0]);
                b.writeFloat(m.vec[1]);
                b.writeFloat(m.vec[2]);
                b.writeByte(m.n);
                for (int i = 0; i < m.n; i++) b.writeVarInt(m.ids[i]);
            }
        }
    }

    public static Acide lire(FriendlyByteBuf b) {
        byte g = b.readByte();
        switch (g) {
            case TIR -> {
                int tireur = b.readVarInt();
                byte pap = b.readByte();
                int n = Math.min(MAX, b.readUnsignedByte());
                int[] ids = new int[n];
                float[] v = new float[n * 6];
                for (int i = 0; i < n; i++) {
                    ids[i] = b.readVarInt();
                    for (int k = 0; k < 6; k++) v[i * 6 + k] = b.readFloat();
                }
                return new Acide(TIR, tireur, pap, n, ids, v, -1, (byte) 0);
            }
            case COLLE -> {
                int id = b.readVarInt();
                int ent = b.readVarInt() - 1;
                return new Acide(COLLE, -1, (byte) 0, 1, new int[]{id}, new float[]{b.readFloat(), b.readFloat(), b.readFloat()}, ent, (byte) 0);
            }
            default -> {
                byte pap = b.readByte();
                byte r = b.readByte();
                float x = b.readFloat(), y = b.readFloat(), z = b.readFloat();
                int n = Math.min(MAX, b.readUnsignedByte());
                int[] ids = new int[n];
                for (int i = 0; i < n; i++) ids[i] = b.readVarInt();
                return new Acide(EXPLOSE, -1, pap, n, ids, new float[]{x, y, z}, -1, r);
            }
        }
    }

    public static void recevoir(Acide m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
