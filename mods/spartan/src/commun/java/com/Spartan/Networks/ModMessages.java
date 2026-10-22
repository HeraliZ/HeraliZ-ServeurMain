package com.Spartan.Networks;

import com.Spartan.Spartan;
import com.Spartan.pont.Crochet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    // Historique : "2" = paquets screenshot retirés ; "3" = ClientReportPacket ;
    // "4" = build + défi d'intégrité (+ jeton launcher) ; "5" = contrôle d'intégrité (scan) ;
    // "6" = le défi couvre toutes les classes (plus de 16 indices) ;
    // "7" = HeraliZ 2.0 : jar coupé client / serveur, défi lié au code réellement chargé, contrôle natif ;
    // "8" = captures d'écran (avec l'accord du joueur donné dans le launcher).
    // Client et serveur doivent partager la même version, sinon la connexion est refusée.
    private static final String PROTOCOL_VERSION = "8";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Spartan.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered = false;

    /** Paquets reçus par le client (défi, demande de contrôle) : branché par la partie client. */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /** Paquets reçus par le serveur : branché par la partie serveur (ReceptionServeur). */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    /** Un paquet reçu par le serveur et son expéditeur (null si aucun). */
    public record Recu(@Nullable ServerPlayer joueur, Object paquet) {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        // Tous ces paquets vont du client vers le serveur, et uniquement dans ce sens.
        Optional<NetworkDirection> toServer = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        int id = 0;
        INSTANCE.registerMessage(id++, TestPacket.class, TestPacket::encode, TestPacket::decode, TestPacket::handle, toServer);
        INSTANCE.registerMessage(id++, ResourcePackPacket.class, ResourcePackPacket::encode, ResourcePackPacket::decode, ResourcePackPacket::handle, toServer);
        INSTANCE.registerMessage(id++, ModsPacket.class, ModsPacket::encode, ModsPacket::decode, ModsPacket::handle, toServer);
        INSTANCE.registerMessage(id++, RequestModsPacket.class, RequestModsPacket::encode, RequestModsPacket::decode, RequestModsPacket::handle, toServer);
        INSTANCE.registerMessage(id++, ClientReportPacket.class, ClientReportPacket::encode, ClientReportPacket::decode, ClientReportPacket::handle, toServer);
        INSTANCE.registerMessage(id++, BuildPacket.class, BuildPacket::encode, BuildPacket::decode, BuildPacket::handle, toServer);
        INSTANCE.registerMessage(id++, ChallengeResponsePacket.class, ChallengeResponsePacket::encode, ChallengeResponsePacket::decode, ChallengeResponsePacket::handle, toServer);
        INSTANCE.registerMessage(id++, LaunchTokenPacket.class, LaunchTokenPacket::encode, LaunchTokenPacket::decode, LaunchTokenPacket::handle, toServer);
        // Serveur -> client.
        INSTANCE.registerMessage(id++, ChallengePacket.class, ChallengePacket::encode, ChallengePacket::decode, ChallengePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // Contrôle d'intégrité : demande (serveur -> client) et rapport en morceaux (client -> serveur).
        INSTANCE.registerMessage(id++, ScanRequestPacket.class, ScanRequestPacket::encode, ScanRequestPacket::decode, ScanRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, ScanChunkPacket.class, ScanChunkPacket::encode, ScanChunkPacket::decode, ScanChunkPacket::handle, toServer);
        // Capture d'écran : demande (serveur -> client) et image en morceaux (client -> serveur).
        INSTANCE.registerMessage(id++, CaptureRequestPacket.class, CaptureRequestPacket::encode, CaptureRequestPacket::decode, CaptureRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, CaptureChunkPacket.class, CaptureChunkPacket::encode, CaptureChunkPacket::decode, CaptureChunkPacket::handle, toServer);
    }

    public static void sendToPlayer(CaptureRequestPacket packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(CaptureChunkPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToPlayer(ChallengePacket packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToPlayer(ScanRequestPacket packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(ScanChunkPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(LaunchTokenPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(BuildPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(ChallengeResponsePacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(ClientReportPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(TestPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(ModsPacket packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToServer(ResourcePackPacket packet) {
        INSTANCE.sendToServer(packet);
    }
}
