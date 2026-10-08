package fr.heraliz.ping.net;

import fr.heraliz.ping.HeralizPing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import fr.heralisz.utils.pont.Crochet;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Predicate;

/**
 * Canal {@code heraliz_ping:main} (version 1). Un client ou un serveur sans le mod peut se connecter (le canal est
 * facultatif) : le serveur n'envoie alors rien a ce client ({@link #present}). Les messages du client sont traites sur
 * le fil serveur (verifications dans server.PingService, partie serveur) ; ceux du serveur sur le fil du client.
 */
public final class PingNet {
    private PingNet() {
    }

    public static final ResourceLocation ID = HeralizPing.id("main");
    public static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.ChannelBuilder.named(ID)
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();

    /** Paquets serveur -> client reçus (PingS2C, MajS2C, ReglagesS2C) : traités par la partie client (client.PartieClient). */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /**
     * Paquets client -> serveur reçus (PingC2S, AnnulerC2S), avec le joueur qui les envoie : traités par la partie serveur
     * (serveur.PartieServeur -> server.PingService), neutre sans elle (jar -client).
     */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    /** Paquet reçu par le serveur et son expéditeur. */
    public record Recu(ServerPlayer joueur, Object paquet) {
    }

    /**
     * Banc d'essai : faux joueurs à qui l'on envoie comme s'ils avaient le mod (server.PingService.presenceForcee, branché
     * par la partie serveur) ; personne sans elle.
     */
    public static volatile Predicate<ServerPlayer> PRESENCE_FORCEE = p -> false;

    /** Index des messages (1er octet de chaque paquet du canal). */
    public static final int I_PING_C2S = 0, I_ANNULER_C2S = 1, I_PING_S2C = 2, I_MAJ_S2C = 3, I_REGLAGES_S2C = 4;

    public static void enregistrer() {
        CANAL.messageBuilder(Paquets.PingC2S.class, I_PING_C2S, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Paquets.PingC2S::ecrire).decoder(Paquets.PingC2S::lire)
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, m));
                }).add();
        CANAL.messageBuilder(Paquets.AnnulerC2S.class, I_ANNULER_C2S, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Paquets.AnnulerC2S::ecrire).decoder(Paquets.AnnulerC2S::lire)
                .consumerMainThread((m, ctx) -> {
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) RECEPTION_SERVEUR.accept(new Recu(p, m));
                }).add();
        CANAL.messageBuilder(Paquets.PingS2C.class, I_PING_S2C, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Paquets.PingS2C::ecrire).decoder(Paquets.PingS2C::lire)
                .consumerMainThread((m, ctx) -> RECEPTION_CLIENT.accept(m))
                .add();
        CANAL.messageBuilder(Paquets.MajS2C.class, I_MAJ_S2C, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Paquets.MajS2C::ecrire).decoder(Paquets.MajS2C::lire)
                .consumerMainThread((m, ctx) -> RECEPTION_CLIENT.accept(m))
                .add();
        CANAL.messageBuilder(Paquets.ReglagesS2C.class, I_REGLAGES_S2C, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Paquets.ReglagesS2C::ecrire).decoder(Paquets.ReglagesS2C::lire)
                .consumerMainThread((m, ctx) -> RECEPTION_CLIENT.accept(m))
                .add();
    }

    /** Vrai si le client du joueur a le mod (sinon on ne lui envoie rien). */
    public static boolean present(ServerPlayer p) {
        return PRESENCE_FORCEE.test(p) || CANAL.isRemotePresent(p.connection.connection);
    }

    public static void versJoueur(ServerPlayer p, Object msg) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void versServeur(Object msg) {
        CANAL.sendToServer(msg);
    }
}
