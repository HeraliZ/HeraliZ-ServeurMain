package fr.heraliz.crac.claim;

import fr.heraliz.crac.heraliz.Txt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacablePositionData;
import fr.heraliz.crac.raid.RaidBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Once a minute: upkeep payment, decay of unpaid / inactive / unclaimed buildings and auto-repair of the others.
 * Only loaded chunks are touched; nothing is ever loaded for this.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class ClaimMaintenanceManager {
    private static final long INTERVAL_TICKS = 1200L;
    /** Game ticks in a real day: inactivity is measured in server uptime. */
    private static final long TICKS_PER_DAY = 24L * 60L * 60L * 20L;
    private static long nextRunTick = -1L;
    /** Groups whose upkeep had run out at the last pass, to warn their members once when it starts and stops. */
    private static final Set<UUID> UNPAID = new HashSet<>();
    /** Levels whose untrusted stores were reported this session (once each). */
    private static final Set<ResourceKey<Level>> DISTRUSTED = new HashSet<>();
    /** Claim Blocks kept on structure blocks the store does not know, reported this session (once each). */
    private static final Set<GlobalPos> KEPT = new HashSet<>();

    private ClaimMaintenanceManager() {
    }

    /** No member of the group was online for {@code decayDays} of server uptime (downtime does not count). */
    public static boolean isInactive(MinecraftServer server, UUID groupId) {
        if (!Config.decayEnabled.get() || !fr.heraliz.crac.heraliz.Heraliz.inactivityDecay()) {
            return false;
        }
        GroupData gd = GroupData.get(server);
        Group g = gd.getById(groupId);
        if (g == null) {
            return false;
        }
        long now = server.overworld().getGameTime();
        if (g.lastActiveTick() < 0L) {
            // saved before activity was tracked in game time: start counting from now
            g.touchActive(now);
            gd.markDirty();
            return false;
        }
        return inactiveFor(g.lastActiveTick(), now, Config.decayDays.get());
    }

    /** More than {@code days} of server uptime passed between the last activity and {@code now} (game ticks). */
    public static boolean inactiveFor(long lastActiveTick, long now, double days) {
        return now - lastActiveTick > (long) (days * TICKS_PER_DAY);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // an integrated server keeps these statics from the previous world
        nextRunTick = -1L;
        UNPAID.clear();
        DISTRUSTED.clear();
        KEPT.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long tick = server.overworld().getGameTime();
        if (nextRunTick < 0L || nextRunTick - tick > INTERVAL_TICKS) {
            // first tick after start, or a world whose clock is behind the cursor: the first pass is one interval away,
            // which also leaves members time to log in after a restart
            nextRunTick = tick + INTERVAL_TICKS;
            return;
        }
        if (tick < nextRunTick) {
            return;
        }
        nextRunTick = tick + INTERVAL_TICKS;
        GroupData gd = GroupData.get(server);
        boolean dirty = false;
        for (Group g : gd.all()) {
            boolean online = false;
            for (UUID m : g.members()) {
                if (server.getPlayerList().getPlayer(m) != null) {
                    online = true;
                    break;
                }
            }
            // HeraliZ Clans: a member of the owner's clan online keeps the base lived in too
            if (online || ClanAccess.clanOnline(g)) {
                g.touchActive(tick);
                dirty = true;
            }
        }
        if (dirty) {
            gd.markDirty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            maintainLevel(server, level);
        }
        // a group whose claim is gone starts afresh: its next claim warns again
        UNPAID.removeIf(id -> ClaimManager.getByGroupAnywhere(server, id) == null);
    }

    /**
     * Why the claims and the buildings of a level cannot be checked against each other, or null when they can: a store
     * stands in for a lost file or kept entries it could not read, so a building or a claim missing from it may still
     * exist. Claim maintenance then pops, removes and unlinks nothing until the file is restored.
     */
    @Nullable
    public static String distrust(ServerLevel level) {
        return distrust(StructureStore.get(level), ClaimData.get(level));
    }

    /** Same as {@link #distrust(ServerLevel)} for the given stores. */
    @Nullable
    public static String distrust(StructureStore store, ClaimData claims) {
        List<String> why = new ArrayList<>();
        if (!store.isTrusted()) {
            why.add(StructureStore.ID + ".dat" + (store.isStandIn() ? " was lost or could not be read" : " has entries that could not be read"));
        }
        if (!claims.isTrusted()) {
            why.add(ClaimData.ID + ".dat" + (claims.isStandIn() ? " was lost or could not be read" : " has entries that could not be read"));
        }
        return why.isEmpty() ? null : String.join(" and ", why);
    }

    /**
     * Checks a claim against the world, its Claim Block's chunk being loaded. A claim whose block is gone (a crash
     * between the chunk and the claim data saves, a world edit) is removed. A Claim Block whose link to its building was
     * lost is linked again to the building it stands on; one left without a building is popped, since such a claim would
     * hold the land for free, unless the block under it holds pieces the store does not know. Nothing is removed while
     * {@link #distrust} finds a store that may only miss the data. Returns the block entity of a claim that holds, else null.
     */
    @Nullable
    public static ClaimBlockEntity reconcile(ServerLevel level, Claim claim, @Nullable Building building) {
        BlockPos anchor = claim.anchor();
        boolean trusted = distrust(level) == null;
        if (!(level.getBlockEntity(anchor) instanceof ClaimBlockEntity be)) {
            if (trusted) {
                Crac.LOGGER.warn("Removing the claim of group {} at {}: its Claim Block is missing", claim.groupId(), anchor.toShortString());
                ClaimManager.removeByAnchor(level, anchor);
                C2SRequestNearbyClaimsTraitement.pushAll(level);
            }
            return null;
        }
        if (building == null) {
            Building under = ClaimManager.buildingUnder(level, anchor);
            if (under != null && StructureService.claimOf(level, under) == null) {
                // claims restored from a file older than the buildings' links: the Claim Block still stands on its building
                StructureService.onClaimCreated(level, anchor);
                Crac.LOGGER.info("Linked the claim of group {} at {} back to the building under its Claim Block", claim.groupId(),
                    anchor.toShortString());
            } else if (trusted && !StructureIntegrity.holdsUnknownPieces(level, anchor.below())) {
                Crac.LOGGER.warn("Removing the claim of group {} at {}: {}", claim.groupId(), anchor.toShortString(),
                    under == null ? "no building under its Claim Block" : "the building under its Claim Block has another claim");
                level.destroyBlock(anchor, false);
                if (ClaimManager.getByAnchor(level, anchor) != null) {
                    ClaimManager.removeByAnchor(level, anchor);
                }
                C2SRequestNearbyClaimsTraitement.pushAll(level);
                return null;
            } else if (trusted && KEPT.add(GlobalPos.of(level.dimension(), anchor))) {
                Crac.LOGGER.warn("Keeping the claim of group {} at {}: its Claim Block stands on structure blocks that {}.dat does not know"
                    + " (restore the file, or remove them with /cracbuild clear)", claim.groupId(), anchor.toShortString(), StructureStore.ID);
            }
        }
        if (!claim.groupId().equals(be.ownerGroup())) {
            // Claim Blocks placed before they remembered their group
            be.setOwnerGroup(claim.groupId());
        }
        return be;
    }

    /** Tells the online members when their base starts decaying for lack of upkeep, and when it is paid again. */
    private static void warnUpkeep(MinecraftServer server, UUID groupId, boolean unpaid) {
        boolean changed = unpaid ? UNPAID.add(groupId) : UNPAID.remove(groupId);
        Group g = changed ? GroupData.get(server).getById(groupId) : null;
        if (g == null) {
            return;
        }
        Component msg = unpaid ? Txt.red("crac.upkeep.decaying") : Txt.green("crac.upkeep.paid");
        for (UUID m : g.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    /**
     * Whether a placable decays with the claim around it. The Claim Block does not: it goes when the piece it stands
     * on is destroyed, and keeps the claim and the upkeep stock until then.
     */
    public static boolean decaysAsPlacable(BlockState state) {
        return !(state.getBlock() instanceof ClaimBlock);
    }

    /**
     * Claimed buildings of the level by Claim Block position. A link to a claim that no longer exists is dropped, unless
     * {@link #distrust} finds a store that may only miss that claim.
     */
    public static Map<Long, Building> claimedBuildings(ServerLevel level) {
        boolean trusted = distrust(level) == null;
        ClaimData cd = ClaimData.get(level);
        StructureStore store = StructureStore.get(level);
        Map<Long, Building> claimed = new HashMap<>();
        for (Building b : store.index.allBuildings()) {
            if (b.claimAnchor == null) continue;
            if (cd.getByAnchor(b.claimAnchor) != null) {
                claimed.put(b.claimAnchor.asLong(), b);
            } else if (trusted) {
                b.claimAnchor = null;
                store.setDirty();
            }
        }
        return claimed;
    }

    private static void maintainLevel(MinecraftServer server, ServerLevel level) {
        int decayHp = Config.decayHpPerMinute.get();
        int repairHp = Config.repairHpPerMinute.get();
        boolean repairOn = Config.autoRepairEnabled.get();
        boolean decayOn = Config.decayEnabled.get();
        long graceTicks = Config.decayGraceMinutes.get() * 1200L;
        long now = level.getGameTime();
        ClaimData cd = ClaimData.get(level);
        StructureStore store = StructureStore.get(level);
        String distrust = distrust(store, cd);
        if (distrust != null && DISTRUSTED.add(level.dimension())) {
            Crac.LOGGER.warn("[CRac claims] {} in {}: Claim Blocks, claims and building links are left as they are{} until the file is"
                + " restored (with the server stopped) or repaired", distrust, level.dimension().location(),
                cd.isTrusted() ? "" : ", and nothing decays for lack of a claim");
        }
        // what lacks a claim may only lack an unreadable one
        boolean unclaimedDecays = Config.decayUnclaimed.get() && decayOn && cd.isTrusted();

        // claimed buildings by Claim Block position, built once per pass
        Map<Long, Building> claimed = claimedBuildings(level);

        Map<UUID, Boolean> decaying = new HashMap<>();
        for (Claim claim : new ArrayList<>(cd.all())) {
            BlockPos anchor = claim.anchor();
            boolean unpaid = false;
            if (level.isLoaded(anchor)) {
                Building b = claimed.get(anchor.asLong());
                ClaimBlockEntity be = reconcile(level, claim, b);
                if (be == null) continue;
                if (b == null) {
                    // linked again by reconcile, or still unknown (costs nothing)
                    b = StructureService.buildingOfClaim(level, anchor);
                }
                unpaid = ClaimUpkeep.consumeAndCheck(level, claim, be, b) && decayOn;
                warnUpkeep(server, claim.groupId(), unpaid);
            }
            decaying.put(claim.groupId(), decayOn && (unpaid || isInactive(server, claim.groupId())));
        }

        for (Building b : new ArrayList<>(store.index.allBuildings())) {
            Claim claim = StructureService.claimOf(level, b);
            boolean decay;
            if (claim == null) {
                if (!unclaimedDecays) continue;
                decay = true;
            } else if (decaying.getOrDefault(claim.groupId(), false)) {
                decay = true;
            } else if (repairOn && !RaidBlockManager.isRaidBlocked(level, claim.groupId())) {
                decay = false;
            } else {
                continue;
            }
            for (Piece p : store.piecesOf(b)) {
                boolean skip = decay ? now - p.modifiedAt < graceTicks : p.hp >= p.maxHp;
                if (skip || !StructureService.isLoaded(level, p)) continue;
                if (decay) {
                    StructureService.damage(level, p, decayHp, false, StructureService.Cause.DECAY);
                } else {
                    StructureService.heal(level, p, repairHp);
                }
            }
        }

        if (Config.decayPlacables.get()) {
            PlacablePositionData ppd = PlacablePositionData.get(level);
            for (long l : ppd.snapshot()) {
                BlockPos pos = BlockPos.of(l);
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!PlacableHpManager.isTracked(state)) {
                    ppd.remove(pos);
                    continue;
                }
                if (!decaysAsPlacable(state)) continue;
                Claim claim = cd.getAt(level, pos);
                boolean decays;
                if (claim != null) {
                    ppd.markClaimed(pos);
                    decays = decaying.getOrDefault(claim.groupId(), false);
                } else {
                    decays = unclaimedDecays && ppd.wasClaimed(pos);
                }
                if (decays) {
                    PlacableHpManager.decayDamage(level, pos, decayHp);
                }
            }
        }
    }
}
