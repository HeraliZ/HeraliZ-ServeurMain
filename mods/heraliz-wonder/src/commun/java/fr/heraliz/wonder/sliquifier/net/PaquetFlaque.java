package fr.heraliz.wonder.sliquifier.net;

import fr.heraliz.wonder.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Une flaque : {@link #AJOUT} (apparait, avec l'eclaboussure {@code splat} ; la boule {@code boule} s'arrete la),
 * {@link #MAJ} (fusion : elle s'etale et dure plus longtemps), {@link #RETRAIT} (seche ou chassee par une plus jeune),
 * {@link #SPLAT_SEUL} (la boule s'ecrase sans flaque : rien sous un mur, dans le vide). {@code ix, iy, iz} = point
 * d'impact de la boule (mur, zombie) quand il differe du centre de la flaque ; {@code face} = face du bloc touche (-1 sinon).
 */
public record PaquetFlaque(byte action, int id, float x, float y, float z, float rayon, int duree, int age, byte pap, byte splat, int boule,
                           float ix, float iy, float iz, byte face) {
    /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
    public static final Crochet<PaquetFlaque> RECEPTION_CLIENT = new Crochet<>();

    public static final byte AJOUT = 0, MAJ = 1, RETRAIT = 2, SPLAT_SEUL = 3;
    /** Genre d'eclaboussure : aucune, au sol, sur un mur, sur un zombie (coup direct). */
    public static final byte S_AUCUN = 0, S_SOL = 1, S_MUR = 2, S_ZOMBIE = 3;

    public static PaquetFlaque retrait(int id) {
        return new PaquetFlaque(RETRAIT, id, 0, 0, 0, 0, 0, 0, (byte) 0, S_AUCUN, -1, 0, 0, 0, (byte) -1);
    }

    private static boolean impact(byte action, byte splat) {
        return splat == S_MUR || splat == S_ZOMBIE || action == SPLAT_SEUL;
    }

    public static void ecrire(PaquetFlaque m, FriendlyByteBuf b) {
        b.writeByte(m.action);
        b.writeVarInt(m.id);
        if (m.action == RETRAIT) return;
        b.writeFloat(m.x);
        b.writeFloat(m.y);
        b.writeFloat(m.z);
        b.writeShort(Math.round(m.rayon * 100));
        b.writeVarInt(m.duree);
        b.writeVarInt(m.age);
        b.writeByte(m.pap);
        b.writeByte(m.splat);
        b.writeVarInt(m.boule + 1);
        if (impact(m.action, m.splat)) {
            b.writeFloat(m.ix);
            b.writeFloat(m.iy);
            b.writeFloat(m.iz);
            b.writeByte(m.face);
        }
    }

    public static PaquetFlaque lire(FriendlyByteBuf b) {
        byte a = b.readByte();
        int id = b.readVarInt();
        if (a == RETRAIT) return retrait(id);
        float x = b.readFloat(), y = b.readFloat(), z = b.readFloat();
        float r = b.readShort() / 100f;
        int duree = b.readVarInt(), age = b.readVarInt();
        byte pap = b.readByte(), splat = b.readByte();
        int boule = b.readVarInt() - 1;
        float ix = x, iy = y, iz = z;
        byte face = -1;
        if (impact(a, splat)) {
            ix = b.readFloat();
            iy = b.readFloat();
            iz = b.readFloat();
            face = b.readByte();
        }
        return new PaquetFlaque(a, id, x, y, z, r, duree, age, pap, splat, boule, ix, iy, iz, face);
    }

    public static void recevoir(PaquetFlaque m, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
        c.get().setPacketHandled(true);
    }
}
