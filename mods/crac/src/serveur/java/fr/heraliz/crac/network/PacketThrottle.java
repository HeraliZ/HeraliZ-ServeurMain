package fr.heraliz.crac.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Per-player rate limits of the mod's client-to-server packets. Neither vanilla nor Forge throttles custom payloads,
 * so a modified client could otherwise flood the server thread. Building packets have their own limits in
 * {@code build.net.PacketGuard}.
 */
@Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PacketThrottle {
    public enum Kind { NEARBY_CLAIMS, GROUP, GROUP_CREATE, INVITE_RESPONSE, ADMIN_LOGIN, ADMIN, ACCES_ENTRETIEN, ACCES_RECHERCHE }

    /** Server-thread only: every handler runs through enqueueWork. */
    private static final Map<UUID, long[]> LAST = new HashMap<>();

    private PacketThrottle() {
    }

    /** True when at least {@code minMillis} passed since the last accepted packet of that kind; records the new one. */
    public static boolean allow(ServerPlayer player, Kind kind, long minMillis) {
        long now = Util.getMillis();
        long[] last = LAST.computeIfAbsent(player.getUUID(), k -> new long[Kind.values().length]);
        if (last[kind.ordinal()] != 0L && now - last[kind.ordinal()] < minMillis) {
            return false;
        }
        last[kind.ordinal()] = now;
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
    }
}
