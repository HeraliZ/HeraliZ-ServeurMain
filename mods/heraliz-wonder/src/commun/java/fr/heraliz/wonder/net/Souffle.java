package fr.heraliz.wonder.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Onde de choc d'un tir de Thundergun (1.0.2) : le tireur, le niveau de Pack-a-Punch (Zeus Cannon), le point de depart
 * (yeux du tireur), la direction, le cone (longueur, demi-angle, rayon a la bouche) et les creatures soufflees (ids).
 * 33 octets + 1 a 3 par creature. Le client dessine l'onde, la poussiere, les debris, et fait culbuter les soufflees.
 */
public record Souffle(int tireur, byte pap, float ox, float oy, float oz, float dx, float dy, float dz, float portee, float demiAngle,
                      float r0, int n, int[] ids) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<Souffle> RECEPTION_CLIENT = new Crochet<>();

    public static final int MAX = 255;

    public static void ecrire(Souffle m, FriendlyByteBuf b) {
        b.writeVarInt(m.tireur);
        b.writeByte(m.pap);
        b.writeFloat(m.ox);
        b.writeFloat(m.oy);
        b.writeFloat(m.oz);
        b.writeFloat(m.dx);
        b.writeFloat(m.dy);
        b.writeFloat(m.dz);
        b.writeFloat(m.portee);
        b.writeByte(Math.round(m.demiAngle * 2));
        b.writeByte(Math.round(m.r0 * 20));
        b.writeByte(m.n);
        for (int i = 0; i < m.n; i++) b.writeVarInt(m.ids[i]);
    }

    public static Souffle lire(FriendlyByteBuf b) {
        int tireur = b.readVarInt();
        byte pap = b.readByte();
        float ox = b.readFloat(), oy = b.readFloat(), oz = b.readFloat();
        float dx = b.readFloat(), dy = b.readFloat(), dz = b.readFloat();
        float portee = b.readFloat();
        float angle = b.readUnsignedByte() / 2f;
        float r0 = b.readUnsignedByte() / 20f;
        int n = Math.min(MAX, b.readUnsignedByte());
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = b.readVarInt();
        return new Souffle(tireur, pap, ox, oy, oz, dx, dy, dz, portee, angle, r0, n, ids);
    }

    public static void recevoir(Souffle m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
