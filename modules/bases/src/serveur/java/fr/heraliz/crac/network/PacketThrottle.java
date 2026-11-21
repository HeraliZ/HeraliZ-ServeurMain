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
    public enum Kind { NEARBY_CLAIMS, GROUP, GROUP_CREATE, INVITE_RESPONSE, ACCES_ENTRETIEN, ACCES_RECHERCHE, ACCES, REAPPARITION }

    /** Server-thread only: every handler runs through enqueueWork. */
    private static final Map<UUID, long[]> LAST = new HashMap<>();
    /** {@link #allowBurst} : par joueur et par sorte, l'heure (ms) où la réserve sera de nouveau pleine. Fil du serveur. */
    private static final Map<UUID, long[]> PLEIN_A = new HashMap<>();

    private PacketThrottle() {
    }

    /**
     * HeraliZ (21/11, audit, choix du propriétaire « sécurités en plus ») : comme {@link #allow}, avec une réserve de
     * {@code rafale} paquets d'un coup, puis un toutes les {@code minMillis} ms (seau qui se vide à vitesse fixe). Un joueur
     * normal (quelques clics, une recherche tapée) n'atteint jamais la limite ; un flot de paquets d'un jeu modifié est
     * ramené à cette cadence. Vrai : le paquet est traité (et compté).
     */
    public static boolean allowBurst(ServerPlayer player, Kind kind, long minMillis, int rafale) {
        long now = Util.getMillis();
        long[] plein = PLEIN_A.computeIfAbsent(player.getUUID(), k -> {
            long[] a = new long[Kind.values().length];
            java.util.Arrays.fill(a, Long.MIN_VALUE);   // réserve pleine au départ, quelle que soit l'horloge
            return a;
        });
        long t = Math.max(plein[kind.ordinal()], now);
        if (t - now > (long) Math.max(0, rafale - 1) * minMillis) {
            return false;
        }
        plein[kind.ordinal()] = t + minMillis;
        return true;
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
        PLEIN_A.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
        PLEIN_A.clear();
    }
}
