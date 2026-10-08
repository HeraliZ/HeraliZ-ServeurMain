package fr.heraliz.wonder.paralyzer;

import fr.heraliz.wonder.pont.Crochet;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Canal propre du Paralyzer ({@code heraliz_wonder:paralyseur}, version 1), deux tout petits paquets serveur -> client :
 * <ul>
 *   <li>{@link Etat} (6 octets) : une creature se fige, degele ou tombe en poussiere (envoye aux joueurs proches) ;</li>
 *   <li>{@link Flotte} (4 octets) : au tireur seulement, a chaque impulsion qui touche le sol sous ses pieds : il flotte
 *   (le serveur decide ; le client ne fait que freiner sa chute vers la hauteur donnee, 0,3 s au plus sans nouvelle).</li>
 * </ul>
 * Le rayon lui-meme n'a pas de paquet : chaque client le dessine d'apres les tirs que TaCZ lui annonce deja.
 * Un client sans le mod reste accepte (effets absents).
 */
public final class ReseauParalyseur {
    private ReseauParalyseur() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizWonder.MODID, "paralyseur"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    public static final double PORTEE_EFFETS = 96;

    public static final byte GEL = 1, DEGEL = 2, POUSSIERE = 3, COLOSSE = 4;

    public static void init() {
        CANAL.registerMessage(0, Etat.class, Etat::ecrire, Etat::lire, Etat::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, Flotte.class, Flotte::ecrire, Flotte::lire, Flotte::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void autour(ServerLevel niveau, double x, double y, double z, Object msg) {
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, PORTEE_EFFETS, niveau.dimension())), msg);
    }

    public static void a(ServerPlayer p, Object msg) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** Une creature change d'etat sous le rayon : {@link #GEL}, {@link #DEGEL}, {@link #POUSSIERE}, {@link #COLOSSE} (boss touche). */
    public record Etat(int id, byte etat, byte pap) {
        /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
        public static final Crochet<Etat> RECEPTION_CLIENT = new Crochet<>();

        public static void ecrire(Etat m, FriendlyByteBuf b) {
            b.writeVarInt(m.id);
            b.writeByte(m.etat);
            b.writeByte(m.pap);
        }

        public static Etat lire(FriendlyByteBuf b) {
            return new Etat(b.readVarInt(), b.readByte(), b.readByte());
        }

        public static void recevoir(Etat m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            c.get().setPacketHandled(true);
        }
    }

    /** Le tireur peut flotter a {@code hauteur} blocs du sol (renouvele a chaque impulsion qui touche le sol). */
    public record Flotte(float hauteur) {
        /** Reception cote client (effets) : branchee par la partie client (PartieClient). */
        public static final Crochet<Flotte> RECEPTION_CLIENT = new Crochet<>();

        public static void ecrire(Flotte m, FriendlyByteBuf b) {
            b.writeFloat(m.hauteur);
        }

        public static Flotte lire(FriendlyByteBuf b) {
            return new Flotte(b.readFloat());
        }

        public static void recevoir(Flotte m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
            c.get().setPacketHandled(true);
        }
    }
}
