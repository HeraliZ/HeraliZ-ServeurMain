package fr.heraliz.carte;

import fr.heralisz.utils.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * Canal {@code heraliz_carte:main} (version 1, facultatif des deux côtés). Un seul message : la liste des lieux nommés de
 * la carte ({@link LieuxS2C}), envoyée à la connexion et à chaque changement fait par le staff. Les territoires des clans
 * passent par le mod des clans ({@code fr.heralisz.clans.api.CarteApi}), les extractions par le leur
 * ({@code com.heraliz.extraction.api.ExtractionApi}), les pings par heraliz_ping.
 */
public final class CarteNet {
    private CarteNet() {
    }

    public static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.ChannelBuilder.named(HeralizCarte.id("main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();

    /** Paquets reçus par le client : traités par la partie client (client.PartieClient), neutre ailleurs. */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();

    /** Les lieux de tous les mondes et le nom de la carte (sous-titre de l'écran). */
    public record LieuxS2C(String nomCarte, List<Lieu> lieux) {
        public static final int MAX = 512;

        public void ecrire(FriendlyByteBuf b) {
            b.writeUtf(Lieu.nettoyer(nomCarte), Lieu.NOM_MAX);
            int n = Math.min(MAX, lieux.size());
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) lieux.get(i).ecrire(b);
        }

        public static LieuxS2C lire(FriendlyByteBuf b) {
            String nom = b.readUtf(Lieu.NOM_MAX);
            int n = Math.min(MAX, b.readVarInt());
            List<Lieu> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(Lieu.lire(b));
            return new LieuxS2C(nom, l);
        }
    }

    public static void enregistrer() {
        CANAL.messageBuilder(LieuxS2C.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LieuxS2C::ecrire).decoder(LieuxS2C::lire)
                .consumerMainThread((m, ctx) -> RECEPTION_CLIENT.accept(m))
                .add();
    }

    /** Vrai si le client du joueur a le mod (sinon on ne lui envoie rien). */
    public static boolean present(ServerPlayer p) {
        return CANAL.isRemotePresent(p.connection.connection);
    }

    public static void versJoueur(ServerPlayer p, Object msg) {
        if (present(p)) CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
