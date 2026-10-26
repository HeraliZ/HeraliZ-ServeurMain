package fr.heraliz.crac.raid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CRaidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Raid block: explosive damage to a claimed base stops its group from building, upgrading, repairing and moving its
 * Claim Block for a while. The state lives in {@link RaidBlockData} (saved, keyed by group, with the claim zone).
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class RaidBlockManager {
    private static final long SYNC_TICKS = 20L;
    /** Last (overlay, own group) seconds sent to each player, packed, so that only changes are sent. */
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();
    private static long nextSyncTick = -1L;

    private RaidBlockManager() {
    }

    /** Whether the group's raid block is running in this level. */
    public static boolean isRaidBlocked(ServerLevel level, @Nullable UUID groupId) {
        if (groupId == null || !Config.raidBlockEnabled.get()) {
            return false;
        }
        RaidBlockData.Entry e = RaidBlockData.get(level).get(groupId);
        return e != null && e.activeAt(level.getGameTime());
    }

    /** Whether a running raid block covers the block, whoever it belongs to (its claim may be gone since). */
    public static boolean isZoneRaidBlocked(ServerLevel level, BlockPos pos) {
        if (!Config.raidBlockEnabled.get()) {
            return false;
        }
        long now = level.getGameTime();
        for (RaidBlockData.Entry e : RaidBlockData.get(level).all()) {
            if (e.activeAt(now) && e.covers(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Raid block of something at {@code pos} under {@code claim} (null when it has none): the claim's group, or any
     * raid block whose zone covers it, so that destroying the Claim Block or disbanding the group does not lift it.
     */
    public static boolean isRaidBlocked(ServerLevel level, @Nullable Claim claim, BlockPos pos) {
        return (claim != null && isRaidBlocked(level, claim.groupId())) || isZoneRaidBlocked(level, pos);
    }

    /** Explosive damage at {@code pos}: starts or extends the raid block of the claim there and of any zone covering it. */
    public static void trigger(ServerLevel level, BlockPos pos) {
        if (!Config.raidBlockEnabled.get()) {
            return;
        }
        long now = level.getGameTime();
        long end = now + Config.raidBlockSeconds.get() * 20L;
        RaidBlockData data = RaidBlockData.get(level);
        Claim claim = ClaimManager.getAt(level, pos);
        if (claim != null) {
            data.put(claim.groupId(), end, claim.anchor(), claim.radius(), claim.yRadius());
        }
        // a base whose Claim Block was destroyed stays raid blocked while explosions go on in its zone
        for (RaidBlockData.Entry e : data.all()) {
            if (e.activeAt(now) && e.covers(pos)) {
                data.extend(e, end);
            }
        }
        syncLevel(level);
    }

    /** Ends a group's raid block in this level (tests, admin tools). */
    public static void clear(ServerLevel level, UUID groupId) {
        if (RaidBlockData.get(level).remove(groupId)) {
            syncLevel(level);
        }
    }

    /** Seconds left on a group's raid block in this level, 0 when there is none. */
    public static int secondsLeft(ServerLevel level, @Nullable UUID groupId) {
        if (groupId == null || !Config.raidBlockEnabled.get()) {
            return 0;
        }
        RaidBlockData.Entry e = RaidBlockData.get(level).get(groupId);
        return e == null ? 0 : seconds(e, level.getGameTime());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // an integrated server keeps these statics from the previous world
        LAST_SENT.clear();
        nextSyncTick = -1L;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LAST_SENT.remove(sp.getUUID());
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CRaidBlock(0, 0));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long tick = server.overworld().getGameTime();
        // a cursor far ahead comes from another world (integrated server): start again from now
        if (tick < nextSyncTick && nextSyncTick - tick <= SYNC_TICKS) {
            return;
        }
        nextSyncTick = tick + SYNC_TICKS;
        for (ServerLevel level : server.getAllLevels()) {
            syncLevel(level);
        }
    }

    private static void syncLevel(ServerLevel level) {
        RaidBlockData data = RaidBlockData.get(level);
        if (!Config.raidBlockEnabled.get()) {
            data.clear();
            for (ServerPlayer player : level.players()) {
                send(player, 0, 0);
            }
            return;
        }
        long now = level.getGameTime();
        data.purge(now);
        if (level.players().isEmpty()) {
            return;
        }
        GroupData groups = GroupData.get(level.getServer());
        for (ServerPlayer player : level.players()) {
            Group g = groups.getByMember(player.getUUID());
            RaidBlockData.Entry own = g == null ? null : data.get(g.id());
            send(player, overlaySeconds(player, data, now), own == null ? 0 : seconds(own, now));
        }
    }

    /** The longest raid block whose zone (plus the overlay margin) the player stands in. */
    private static int overlaySeconds(ServerPlayer player, RaidBlockData data, long now) {
        if (data.isEmpty()) {
            return 0;
        }
        BlockPos pos = player.blockPosition();
        int best = 0;
        for (RaidBlockData.Entry e : data.all()) {
            if (e.activeAt(now) && insideOverlayArea(e, pos)) {
                best = Math.max(best, seconds(e, now));
            }
        }
        return best;
    }

    private static int seconds(RaidBlockData.Entry e, long now) {
        return (int) Math.max(0L, (e.endTick - now + 19L) / 20L);
    }

    private static boolean insideOverlayArea(RaidBlockData.Entry e, BlockPos pos) {
        double dx = pos.getX() + 0.5 - (e.center.getX() + 0.5);
        double dz = pos.getZ() + 0.5 - (e.center.getZ() + 0.5);
        double radius = e.radius + Config.raidBlockExtraRadius.get();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static void send(ServerPlayer player, int overlaySeconds, int ownSeconds) {
        UUID id = player.getUUID();
        long packed = (long) overlaySeconds << 32 | (ownSeconds & 0xFFFFFFFFL);
        Long previous = LAST_SENT.get(id);
        if (previous == null ? packed == 0L : previous == packed) {
            return;
        }
        if (packed == 0L) {
            LAST_SENT.remove(id);
        } else {
            LAST_SENT.put(id, packed);
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CRaidBlock(overlaySeconds, ownSeconds));
    }
}
