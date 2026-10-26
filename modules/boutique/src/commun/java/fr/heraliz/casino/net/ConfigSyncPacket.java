package fr.heraliz.casino.net;

import fr.heraliz.casino.config.CasinoConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Reglages du casino (serveur -> client) : tables de gains, limites, cases de la roue...
 */
public record ConfigSyncPacket(String json) {
    public static void encode(ConfigSyncPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.json, 262144);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ConfigSyncPacket(buf.readUtf(262144));
    }

    public static void handle(ConfigSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        CasinoConfig.applyJson(p.json);
    }
}
