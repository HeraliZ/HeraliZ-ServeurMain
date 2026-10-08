package fr.heraliz.casino.net;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.pont.Crochet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau du casino.
 */
public final class CasinoNet {
    private CasinoNet() {
    }

    private static final String PROTOCOL = "3";

    /**
     * Paquets recus par le client (serveur -> client) : traitement d'affichage branche par la partie client
     * (client/PartieClient). Neutre sans elle (serveur dedie, jar -serveur).
     */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();

    /** Paquet recu par le serveur, avec le joueur qui l'envoie. */
    public record Recu(ServerPlayer joueur, Object paquet) {
    }

    /**
     * Paquets recus par le serveur (client -> serveur) : traitement branche par la partie serveur
     * (serveur/PartieServeur). Neutre sans elle (jar -client).
     */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeralizCasino.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Envoie un paquet a un joueur ; ignore les joueurs sans connexion reseau (faux joueurs d'autres mods, tests). */
    public static void sendTo(ServerPlayer player, Object message) {
        if (player.connection == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToAll(MinecraftServer server, Object message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendTo(p, message);
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ActionPacket::encode).decoder(ActionPacket::decode).consumerMainThread(ActionPacket::handle).add();
        CHANNEL.messageBuilder(ConfigSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncPacket::encode).decoder(ConfigSyncPacket::decode).consumerMainThread(ConfigSyncPacket::handle).add();
        CHANNEL.messageBuilder(WinPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WinPacket::encode).decoder(WinPacket::decode).consumerMainThread(WinPacket::handle).add();
        CHANNEL.messageBuilder(JackpotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JackpotPacket::encode).decoder(JackpotPacket::decode).consumerMainThread(JackpotPacket::handle).add();
        CHANNEL.messageBuilder(DuelPrivatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DuelPrivatePacket::encode).decoder(DuelPrivatePacket::decode).consumerMainThread(DuelPrivatePacket::handle).add();
        CHANNEL.messageBuilder(AdminConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminConfigPacket::encode).decoder(AdminConfigPacket::decode).consumerMainThread(AdminConfigPacket::handle).add();
        CHANNEL.messageBuilder(AdminStatsPacket.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminStatsPacket.Request::encode).decoder(AdminStatsPacket.Request::decode).consumerMainThread(AdminStatsPacket.Request::handle).add();
        CHANNEL.messageBuilder(AdminStatsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AdminStatsPacket::encode).decoder(AdminStatsPacket::decode).consumerMainThread(AdminStatsPacket::handle).add();
        CHANNEL.messageBuilder(ZoneDialogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ZoneDialogPacket::encode).decoder(ZoneDialogPacket::decode).consumerMainThread(ZoneDialogPacket::handle).add();
        CHANNEL.messageBuilder(ZonePayPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ZonePayPacket::encode).decoder(ZonePayPacket::decode).consumerMainThread(ZonePayPacket::handle).add();
        CHANNEL.messageBuilder(ZoneCutscenePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ZoneCutscenePacket::encode).decoder(ZoneCutscenePacket::decode).consumerMainThread(ZoneCutscenePacket::handle).add();
    }
}
