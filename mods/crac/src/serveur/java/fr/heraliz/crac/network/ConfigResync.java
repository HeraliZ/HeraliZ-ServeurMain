package fr.heraliz.crac.network;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import fr.heraliz.crac.Crac;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Sends CRac's SERVER config files to the connected clients again when they change. Forge syncs them only at login,
 * so without this the client side (ghost reach, repair time, upkeep screen...) keeps stale values until a relog.
 * These files must never hold a secret: the admin password is in the COMMON {@code crac-admin.toml}.
 */
public final class ConfigResync {
    /** Last content pushed per file name; server thread only. Skips the duplicate push of a save followed by its reload. */
    private static final Map<String, byte[]> LAST = new HashMap<>();
    /** Claim view settings at the last push; server thread only. */
    @Nullable
    private static int[] lastClaimView;

    private ConfigResync() {
    }

    /**
     * Pushes every CRac SERVER config whose file changed since the last push, and the claim views when their zone sizes
     * changed: clients keep each zone with its size baked in. Server thread only.
     */
    public static void pushChanged(MinecraftServer server) {
        pushFiles(server);
        if (claimViewChanged()) {
            for (ServerLevel level : server.getAllLevels()) {
                C2SRequestNearbyClaimsTraitement.pushAll(level);
            }
        }
    }

    /** Whether the settings claim views are built from changed since the last call (the first call says yes). */
    public static boolean claimViewChanged() {
        int[] now = C2SRequestNearbyClaimsTraitement.viewSettings();
        if (Arrays.equals(now, lastClaimView)) {
            return false;
        }
        lastClaimView = now;
        return true;
    }

    private static void pushFiles(MinecraftServer server) {
        for (ModConfig config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER)) {
            if (!Crac.MODID.equals(config.getModId())) {
                continue;
            }
            byte[] data;
            try {
                data = Files.readAllBytes(config.getFullPath());
            } catch (IOException | RuntimeException e) {
                Crac.LOGGER.warn("Config resync: cannot read {}", config.getFileName(), e);
                continue;
            }
            if (data.length > S2CConfigResync.MAX_BYTES || Arrays.equals(data, LAST.get(config.getFileName()))) {
                continue;
            }
            LAST.put(config.getFileName(), data);
            S2CConfigResync msg = new S2CConfigResync(config.getFileName(), data);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                // the integrated server's host shares the config objects
                if (!p.connection.connection.isMemoryConnection()) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        /** Fired by Forge's file watcher thread when a config file changes on disk (also after our own saves). */
        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            ModConfig config = event.getConfig();
            if (config.getType() != ModConfig.Type.SERVER || !Crac.MODID.equals(config.getModId())) {
                return;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> pushChanged(server));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            LAST.clear();
            lastClaimView = null;
        }
    }
}
