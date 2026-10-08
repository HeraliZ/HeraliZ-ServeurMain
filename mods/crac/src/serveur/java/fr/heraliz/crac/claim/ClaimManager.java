package fr.heraliz.crac.claim;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Claim lifecycle. Every claim created or removed goes through here so the building it is attached to follows. */
public final class ClaimManager {
    private ClaimManager() {
    }

    public static Claim getAt(ServerLevel level, BlockPos pos) {
        return ClaimData.get(level).getAt(level, pos);
    }

    public static Claim getByGroup(ServerLevel level, UUID groupId) {
        return ClaimData.get(level).getByGroup(groupId);
    }

    public static Claim getByAnchor(ServerLevel level, BlockPos anchor) {
        return ClaimData.get(level).getByAnchor(anchor);
    }

    public static void addClaim(ServerLevel level, BlockPos anchor, UUID groupId, int tier) {
        ClaimData.get(level).add(new Claim(groupId, anchor, tier));
        StructureService.onClaimCreated(level, anchor);
        if (level.getBlockEntity(anchor) instanceof ClaimBlockEntity be) {
            // the block remembers its group: a claim lost in a crash can be restored from it
            Group g = GroupData.get(level.getServer()).getById(groupId);
            be.setOwnerGroup(groupId);
            if (g != null) {
                be.setColor(colorOf(g));
            }
        }
    }

    public static boolean removeByAnchor(ServerLevel level, BlockPos anchor) {
        boolean removed = ClaimData.get(level).removeByAnchor(anchor);
        if (removed) {
            StructureService.onClaimRemoved(level, anchor);
        }
        return removed;
    }

    public static boolean removeByGroup(ServerLevel level, UUID groupId) {
        Claim c = ClaimData.get(level).getByGroup(groupId);
        return c != null && removeByAnchor(level, c.anchor());
    }

    /** Removes the group's claim in this level and breaks its Claim Block (upkeep items drop). */
    public static boolean releaseClaim(ServerLevel level, UUID groupId) {
        Claim c = ClaimData.get(level).getByGroup(groupId);
        if (c == null) {
            return false;
        }
        removeByAnchor(level, c.anchor());
        if (level.getBlockState(c.anchor()).getBlock() == ModBlocks.CLAIM_BLOCK.get()) {
            level.destroyBlock(c.anchor(), true);
        }
        return true;
    }

    public static Claim getByGroupAnywhere(MinecraftServer server, UUID groupId) {
        if (server == null) {
            return null;
        }
        for (ServerLevel l : server.getAllLevels()) {
            Claim c = ClaimData.get(l).getByGroup(groupId);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** The level holding the group's claim, or null. */
    @Nullable
    public static ServerLevel levelOfGroupClaim(MinecraftServer server, UUID groupId) {
        if (server == null) {
            return null;
        }
        for (ServerLevel l : server.getAllLevels()) {
            if (ClaimData.get(l).getByGroup(groupId) != null) {
                return l;
            }
        }
        return null;
    }

    public static boolean releaseClaimAnywhere(MinecraftServer server, UUID groupId) {
        if (server == null) {
            return false;
        }
        boolean removed = false;
        for (ServerLevel l : server.getAllLevels()) {
            if (releaseClaim(l, groupId)) {
                removed = true;
            }
        }
        return removed;
    }

    public static Claim tooCloseTo(ServerLevel level, BlockPos anchor, UUID group) {
        int maxRadius = Math.max(Config.claimRadiusL1.get(), Math.max(Config.claimRadiusL2.get(), Config.claimRadiusL3.get()));
        int minDist = maxRadius * 2 + 1 + Config.claimSpacingMargin.get();
        for (Claim c : ClaimData.get(level).all()) {
            if (!c.groupId().equals(group)) {
                int dx = Math.abs(c.anchor().getX() - anchor.getX());
                int dz = Math.abs(c.anchor().getZ() - anchor.getZ());
                if (Math.max(dx, dz) < minDist) {
                    return c;
                }
            }
        }
        return null;
    }

    /** The building whose walking surface a Claim Block at {@code pos} stands on (the seat rule), or null. */
    @Nullable
    public static Building buildingUnder(ServerLevel level, BlockPos pos) {
        Piece seat = StructureService.seatUnder(level, pos);
        return seat == null ? null : StructureStore.get(level).index.building(seat.buildingId);
    }

    /**
     * A building other than {@code own} with a block inside {@code zone} that the group does not control, or null.
     * A claim may take over the building its Claim Block stands on (Rust: place your own cupboard on it), never the
     * pieces of other groups that happen to lie around it.
     */
    @Nullable
    public static Building foreignBuildingIn(ServerLevel level, Claim zone, @Nullable Building own, UUID groupId) {
        StructureStore store = StructureStore.get(level);
        Group group = GroupData.get(level.getServer()).getById(groupId);
        BlockPos a = zone.anchor();
        // a piece's blocks stay within a few blocks of its centre: skip the far ones before the exact test
        int reach = zone.radius() + 4;
        Long2BooleanOpenHashMap controlled = new Long2BooleanOpenHashMap();
        for (Piece p : store.index.allPieces()) {
            if (own != null && p.buildingId == own.id) continue;
            AABB bounds = p.bounds();
            if (bounds.maxX < a.getX() - reach || bounds.minX > a.getX() + 1 + reach
                || bounds.maxZ < a.getZ() - reach || bounds.minZ > a.getZ() + 1 + reach) continue;
            Building b = store.index.building(p.buildingId);
            if (b == null || !touches(zone, p)) continue;
            if (!controlled.containsKey(b.id)) {
                controlled.put(b.id, controls(store, b, group));
            }
            if (!controlled.get(b.id)) {
                return b;
            }
        }
        return null;
    }

    private static boolean touches(Claim zone, Piece p) {
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            if (zone.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** The group controls a building it claimed last or, for a never claimed one, whose every piece its members placed. */
    private static boolean controls(StructureStore store, Building b, @Nullable Group group) {
        if (group == null) {
            return false;
        }
        if (b.ownerGroup != null) {
            return b.ownerGroup.equals(group.id());
        }
        for (Piece p : store.piecesOf(b)) {
            if (!group.isMember(p.owner)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What a new claim of {@code tier} costs on top of its Claim Block: every upgrade from tier 1 up to that tier, an
     * item used by both upgrades counted once. Empty for tier 1.
     */
    public static List<BuildCost> tierCost(int tier) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (tier >= 2) {
            items.merge(Config.claimUpgradeItemL1L2.get().trim(), Config.claimUpgradeCostL1L2.get(), Integer::sum);
        }
        if (tier >= 3) {
            items.merge(Config.claimUpgradeItemL2L3.get().trim(), Config.claimUpgradeCostL2L3.get(), Integer::sum);
        }
        List<BuildCost> cost = new ArrayList<>();
        items.forEach((item, count) -> {
            if (count > 0) {
                cost.add(BuildCost.parse(item + "=" + count));
            }
        });
        return cost;
    }

    /** Why an existing claim may not grow to {@code newTier}, or null. */
    @Nullable
    public static String upgradeProblem(ServerLevel level, Claim claim, int newTier) {
        if (RaidBlockManager.isRaidBlocked(level, claim, claim.anchor())) {
            return "crac.claim.upgrade_raid";
        }
        Claim grown = new Claim(claim.groupId(), claim.anchor(), newTier);
        if (foreignBuildingIn(level, grown, StructureService.buildingOfClaim(level, claim.anchor()), claim.groupId()) != null) {
            return "crac.claim.upgrade_foreign";
        }
        return null;
    }

    public static int colorOf(Group g) {
        int c = g.color() & 16777215;
        return c == 0 ? 16777215 : c;
    }
}
