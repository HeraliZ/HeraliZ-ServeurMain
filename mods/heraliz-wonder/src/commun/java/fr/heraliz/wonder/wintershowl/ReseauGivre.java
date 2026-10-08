package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.pont.Crochet;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Canal du Winter's Howl ({@code heraliz_wonder:givre}, separe du canal commun : aucun numero de paquet a partager avec les
 * autres armes) : trois petits paquets serveur -> client, envoyes seulement aux joueurs proches.
 * <ul>
 *   <li>{@link Souffle} (38 octets + 3 par creature) : un tir (cone, creatures touchees, leur gel apres le tir) ;</li>
 *   <li>{@link Eclat} (19 octets) : une creature gelee vole en eclats ;</li>
 *   <li>{@link Degel} (1 a 5 octets) : une creature gelee qui survit (hors zombies) reprend vie.</li>
 * </ul>
 * Un client sans le mod reste accepte (effets absents).
 */
public final class ReseauGivre {
    private ReseauGivre() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizWonder.MODID, "givre"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    public static final double PORTEE_EFFETS = 96;

    static void init() {
        int id = 0;
        CANAL.registerMessage(id++, Souffle.class, Souffle::ecrire, Souffle::lire, Souffle::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id++, Eclat.class, Eclat::ecrire, Eclat::lire, Eclat::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id, Degel.class, Degel::ecrire, Degel::lire, Degel::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    static void autour(ServerLevel niveau, double x, double y, double z, Object msg) {
        ResourceKey<Level> dim = niveau.dimension();
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, PORTEE_EFFETS, dim)), msg);
    }

    /**
     * Un tir : tireur, niveau de Pack-a-Punch, depart (yeux du tireur), direction, cone (longueur, demi-angle, rayon a la
     * bouche), delai d'eclatement (ticks), puis chaque creature touchee : id, gel apres ce tir (0..254 ; 255 = gelee) et
     * tick d'arrivee du front.
     */
    public record Souffle(int tireur, byte pap, float ox, float oy, float oz, float dx, float dy, float dz, float portee, float demiAngle, float r0,
                          int delaiEclat, int n, int[] ids, byte[] gels, byte[] arrivees) {
        /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
        public static final Crochet<Souffle> RECEPTION_CLIENT = new Crochet<>();

        public static final int MAX = 128;

        static void ecrire(Souffle m, FriendlyByteBuf b) {
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
            b.writeShort(m.delaiEclat);
            b.writeByte(m.n);
            for (int i = 0; i < m.n; i++) {
                b.writeVarInt(m.ids[i]);
                b.writeByte(m.gels[i]);
                b.writeByte(m.arrivees[i]);
            }
        }

        static Souffle lire(FriendlyByteBuf b) {
            int tireur = b.readVarInt();
            byte pap = b.readByte();
            float ox = b.readFloat(), oy = b.readFloat(), oz = b.readFloat();
            float dx = b.readFloat(), dy = b.readFloat(), dz = b.readFloat();
            float portee = b.readFloat();
            float angle = b.readUnsignedByte() / 2f;
            float r0 = b.readUnsignedByte() / 20f;
            int delai = b.readShort();
            int n = Math.min(MAX, b.readUnsignedByte());
            int[] ids = new int[n];
            byte[] gels = new byte[n], arr = new byte[n];
            for (int i = 0; i < n; i++) {
                ids[i] = b.readVarInt();
                gels[i] = b.readByte();
                arr[i] = b.readByte();
            }
            return new Souffle(tireur, pap, ox, oy, oz, dx, dy, dz, portee, angle, r0, delai, n, ids, gels, arr);
        }

        static void recevoir(Souffle m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            c.get().setPacketHandled(true);
        }
    }

    /** Une creature gelee vole en eclats : id, centre de sa boite, largeur, hauteur, niveau de Pack-a-Punch. */
    public record Eclat(int id, float x, float y, float z, float larg, float haut, byte pap) {
        /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
        public static final Crochet<Eclat> RECEPTION_CLIENT = new Crochet<>();

        static void ecrire(Eclat m, FriendlyByteBuf b) {
            b.writeVarInt(m.id);
            b.writeFloat(m.x);
            b.writeFloat(m.y);
            b.writeFloat(m.z);
            b.writeByte(Math.min(255, Math.round(m.larg * 20)));
            b.writeByte(Math.min(255, Math.round(m.haut * 20)));
            b.writeByte(m.pap);
        }

        static Eclat lire(FriendlyByteBuf b) {
            return new Eclat(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readUnsignedByte() / 20f, b.readUnsignedByte() / 20f, b.readByte());
        }

        static void recevoir(Eclat m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            c.get().setPacketHandled(true);
        }
    }

    /** Une creature gelee survit et degele (hors partie de zombies). */
    public record Degel(int id) {
        /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
        public static final Crochet<Degel> RECEPTION_CLIENT = new Crochet<>();

        static void ecrire(Degel m, FriendlyByteBuf b) {
            b.writeVarInt(m.id);
        }

        static Degel lire(FriendlyByteBuf b) {
            return new Degel(b.readVarInt());
        }

        static void recevoir(Degel m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            c.get().setPacketHandled(true);
        }
    }
}
