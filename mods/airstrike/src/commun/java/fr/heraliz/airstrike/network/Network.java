package fr.heraliz.airstrike.network;

import javax.annotation.Nullable;
import fr.heraliz.airstrike.pont.Crochet;
import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AirstrikeMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Packets received by the client (StrikePacket, ImpactPacket): plugged in by PartieClient (docs §6). */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();
    /** Packets received by the server (DesignatePacket, LoadCartridgePacket): plugged in by PartieServeur. */
    public static final Crochet<Recu> RECEPTION_SERVEUR = new Crochet<>();

    /** A packet received by the server and its sender (null if none). */
    public record Recu(@Nullable ServerPlayer joueur, Object paquet) {
    }

    /** Radius around which clients receive strike visuals. */
    public static final double VISUAL_RANGE = 700.0;

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(DesignatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DesignatePacket::encode).decoder(DesignatePacket::new)
                .consumerMainThread(DesignatePacket::handle).add();
        CHANNEL.messageBuilder(StrikePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StrikePacket::encode).decoder(StrikePacket::new)
                .consumerMainThread(StrikePacket::handle).add();
        CHANNEL.messageBuilder(ImpactPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ImpactPacket::encode).decoder(ImpactPacket::new)
                .consumerMainThread(ImpactPacket::handle).add();
        CHANNEL.messageBuilder(LoadCartridgePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LoadCartridgePacket::encode).decoder(LoadCartridgePacket::new)
                .consumerMainThread(LoadCartridgePacket::handle).add();
    }

    public static void sendImpact(ServerLevel level, ImpactPacket packet) {
        double r2 = VISUAL_RANGE * VISUAL_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(packet.x, packet.y, packet.z) < r2) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private Network() {
    }
}
