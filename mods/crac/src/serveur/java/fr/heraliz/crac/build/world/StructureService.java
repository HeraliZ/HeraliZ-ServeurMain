package fr.heraliz.crac.build.world;

import fr.heraliz.crac.build.logic.Assises;
import fr.heraliz.crac.placable.PlacableChutes;
import fr.heraliz.crac.heraliz.Txt;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceShapes;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.logic.Stability;
import fr.heraliz.crac.build.logic.StructureView;
import fr.heraliz.crac.build.logic.Supports;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.registry.ModBlocks;
import fr.heraliz.crac.registry.ModItems;
import fr.heraliz.crac.registry.ModParticles;
import fr.heraliz.crac.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side entry point of the building system: placement, damage, upgrades, doors, collapse and the bookkeeping
 * that keeps the world blocks in line with {@link StructureStore}.
 */
public final class StructureService {
    public enum Cause { DEMOLISH, DESTROYED, COLLAPSE, DECAY, ADMIN, EXTERNAL }

    public record Outcome(boolean ok, Component message, @Nullable Piece piece) {
        static Outcome ok(Piece p) {
            return new Outcome(true, Component.empty(), p);
        }

        /** {@code msg}: a translation key or an encoded message ({@link Txt#k}). */
        static Outcome fail(String msg) {
            return new Outcome(false, Txt.c(msg), null);
        }

        static Outcome fail(Component msg) {
            return new Outcome(false, msg, null);
        }
    }

    /** Depth of our own block edits, so that {@link #onBlockGone} ignores them. */
    private static int internalEdits;

    private StructureService() {
    }

    public static StructureStore store(ServerLevel level) {
        return StructureStore.get(level);
    }

    // ------------------------------------------------------------------ lookups

    /**
     * Any piece occupying the block (the first one when several share it). Only for callers that do not care which:
     * pieces of unrelated parts of a building can share a block, use {@link #pieceAt(ServerLevel, BlockPos, Vec3)} or
     * {@link #seatUnder} instead.
     */
    @Nullable
    public static Piece pieceAt(ServerLevel level, BlockPos pos) {
        List<Piece> occ = store(level).index.occupants(pos);
        return occ.isEmpty() ? null : occ.get(0);
    }

    /** The piece whose shape is closest to {@code hit} inside the block (doors vs walls vs floors sharing a block). */
    @Nullable
    public static Piece pieceAt(ServerLevel level, BlockPos pos, Vec3 hit) {
        List<Piece> occ = store(level).index.occupants(pos);
        if (occ.size() <= 1) {
            return occ.isEmpty() ? null : occ.get(0);
        }
        Piece best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Piece p : occ) {
            for (AABB box : PieceShapes.shape(p.key(), p.state, p.variant, pos).toAabbs()) {
                double d = box.inflate(1e-3).contains(local) ? 0 : box.distanceToSqr(local);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        return best != null ? best : occ.get(0);
    }

    /**
     * Sight sample points of a piece in one of its blocks (world coordinates): the centres of its own collision boxes
     * in that block, nearest the block centre first. Blasts and the packet sight checks aim at these rather than at
     * block centres, so a piece sharing a block with another (a floor behind a rotated wall) is only seen through its
     * own geometry.
     */
    public static List<Vec3> sightPoints(Piece p, BlockPos pos) {
        List<Vec3> out = new ArrayList<>();
        for (AABB b : PieceShapes.shape(p.key(), p.state, p.variant, pos).toAabbs()) {
            out.add(b.getCenter().add(pos.getX(), pos.getY(), pos.getZ()));
        }
        Vec3 c = Vec3.atCenterOf(pos);
        out.sort(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(c)));
        return out;
    }

    /** Pieces with at least one block inside the box. */
    public static List<Piece> piecesIn(ServerLevel level, AABB box) {
        StructureStore store = store(level);
        Set<Piece> out = new LinkedHashSet<>();
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            out.addAll(store.index.occupants(p));
        }
        return new ArrayList<>(out);
    }

    /** Whether every block of the piece is in a loaded chunk (background systems skip the others). */
    public static boolean isLoaded(ServerLevel level, Piece p) {
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            if (!level.isLoaded(pos)) {
                return false;
            }
        }
        return true;
    }

    /** World-space bounding box of the piece's collision shape (closed state) in the blocks it fills. */
    public static AABB bounds(Piece p) {
        AABB box = null;
        for (AABB b : PlacementRules.worldBoxes(p.key(), PieceIndex.blocksOf(p))) {
            box = box == null ? b : box.minmax(b);
        }
        return box == null ? new AABB(p.centreBlock()) : box;
    }

    @Nullable
    public static Building buildingOf(ServerLevel level, Piece p) {
        return store(level).index.building(p.buildingId);
    }

    @Nullable
    public static Claim claimOf(ServerLevel level, @Nullable Building b) {
        if (b == null || b.claimAnchor == null) {
            return null;
        }
        return ClaimManager.getByAnchor(level, b.claimAnchor);
    }

    // ------------------------------------------------------------------ permissions

    public static boolean isMember(ServerLevel level, Player player, @Nullable Claim claim, ClanAccess.Action action) {
        return claim != null && isMember(level, player, claim.groupId(), action);
    }

    /**
     * Whether the player may do {@code action} for the group: one of its members or, with HeraliZ Clans, a member of
     * its owner's clan holding the matching BASE_* permission ({@link ClanAccess}).
     */
    public static boolean isMember(ServerLevel level, Player player, @Nullable UUID groupId, ClanAccess.Action action) {
        if (groupId == null) {
            return false;
        }
        Group g = GroupData.get(level.getServer()).getById(groupId);
        return ClanAccess.allows(g, player.getUUID(), action);
    }

    /**
     * Group whose members control a building's doors and may demolish it: the claim's group, else the group that
     * claimed it last (the claim was destroyed), else null (never claimed).
     */
    @Nullable
    public static UUID controllingGroup(ServerLevel level, @Nullable Building b) {
        Claim c = claimOf(level, b);
        if (c != null) {
            return c.groupId();
        }
        return b == null ? null : b.ownerGroup;
    }

    /** Building privilege over an existing piece: claim members, or anyone when the building has no claim. */
    public static boolean hasPrivilege(ServerLevel level, Player player, Piece p) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        Claim c = claimOf(level, buildingOf(level, p));
        if (c == null) {
            c = ClaimManager.getAt(level, p.centreBlock());
            return c == null || isMember(level, player, c, ClanAccess.Action.BUILD);
        }
        return isMember(level, player, c, ClanAccess.Action.BUILD);
    }

    /** Whether one of the piece's collision boxes in {@code blocks} is within reach of the player's eyes. */
    private static boolean inReach(Player player, PieceKey key, List<BlockPos> blocks) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        double reach = BuildConfig.reach.get() + 1.0;
        Vec3 eye = player.getEyePosition();
        for (AABB box : PlacementRules.worldBoxes(key, blocks)) {
            if (box.inflate(0.5).distanceToSqr(eye) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    public static boolean inReach(Player player, Piece p) {
        return inReach(player, p.key(), PieceIndex.blocksOf(p));
    }

    /**
     * Vanilla protections that the placement packet skips (it is not a vanilla block use): build height, spawn
     * protection and the world border, for every block of the piece. Null when allowed.
     */
    @Nullable
    private static String worldProblem(ServerLevel level, ServerPlayer player, PieceKey key, PlacementRules.Plan plan) {
        for (BlockPos pos : plan.blocks()) {
            if (level.isOutsideBuildHeight(pos)) {
                return "crac.build.height";
            }
            if (!level.mayInteract(player, pos)) {
                return "crac.build.cannot_here";
            }
        }
        return null;
    }

    /** Claim rules on top of the grid rules; null when allowed. */
    @Nullable
    private static String privilegeProblem(ServerLevel level, ServerPlayer player, PieceKey key, PlacementRules.Plan plan) {
        StructureStore store = store(level);
        Map<BlockPos, Claim> claims = new HashMap<>();
        List<BlockPos> blocks = plan.blocks();
        for (BlockPos pos : blocks) {
            Claim c = ClaimManager.getAt(level, pos);
            if (c != null) {
                claims.put(c.anchor(), c);
            }
        }
        boolean creative = player.getAbilities().instabuild;
        if (plan.buildingId() == PlacementRules.NEW_BUILDING) {
            return claims.isEmpty() || creative ? null : "crac.build.no_new_in_claim";
        }
        Building b = store.index.building(plan.buildingId());
        Claim own = claimOf(level, b);
        if (own != null) {
            if (!creative && !isMember(level, player, own, ClanAccess.Action.BUILD)) {
                return "crac.build.not_yours";
            }
            for (BlockPos pos : blocks) {
                if (!own.contains(pos)) {
                    return "crac.build.outside_zone";
                }
            }
            if (claims.size() > 1 || (claims.size() == 1 && !claims.containsKey(own.anchor()))) {
                return "crac.build.too_close";
            }
            if (!creative && Config.raidBlockBuilding.get() && RaidBlockManager.isRaidBlocked(level, own.groupId())) {
                return "crac.build.raid_blocked";
            }
            return null;
        }
        if (!claims.isEmpty() && !creative) {
            return "crac.build.foreign_claim";
        }
        // protectOutsideClaims: outside a claim only the first foundation (the Claim Block's seat) may be placed
        boolean limited = Config.protectOutsideClaims.get() || b != null && b.pieces.size() >= BuildConfig.maxPiecesWithoutClaim.get();
        // HeraliZ : la cabane du tutoriel des bases se bâtit avant son Claim Block
        if (b != null && limited && !creative && !fr.heraliz.crac.bases.Tutoriel.sansClaimPermis(player, b)) {
            return "crac.build.claim_first";
        }
        return null;
    }

    // ------------------------------------------------------------------ placement

    /** Refusal while the store stands in for building data of an older version (§7.6). */
    private static final String OLD_DATA = "crac.build.old_data";

    public static Outcome place(ServerLevel level, ServerPlayer player, PieceKey key) {
        StructureStore store = store(level);
        if (store.holdsOldData()) {
            return Outcome.fail(OLD_DATA);
        }
        Component heraliz = Heraliz.buildProblem(player);
        if (heraliz != null) {
            return Outcome.fail(heraliz);
        }
        PlacementRules.Plan plan = PlacementRules.check(store.index, new LevelEnv(level), key, BuildConfig.stability(), BuildConfig.foundationMaxHeight.get());
        if (!plan.ok()) {
            return Outcome.fail(plan.result().message);
        }
        if (!inReach(player, key, plan.blocks())) {
            return Outcome.fail("crac.build.too_far");
        }
        String deny = worldProblem(level, player, key, plan);
        if (deny == null) {
            deny = privilegeProblem(level, player, key, plan);
        }
        if (deny != null) {
            return Outcome.fail(deny);
        }
        // HeraliZ : pendant le tutoriel des bases, seulement les pièces de l'étape, contre la cabane
        Component tuto = fr.heraliz.crac.bases.Tutoriel.refusPiece(player, key, plan.buildingId());
        if (tuto != null) {
            return Outcome.fail(tuto);
        }
        List<BuildCost> cost = Costs.place(key.kind());
        if (!Costs.has(player, cost)) {
            return Outcome.fail(Component.translatable("crac.build.need", Costs.describe(cost)));
        }
        Costs.take(player, cost);
        Piece p = commit(level, store, key, plan, Tier.TWIG, player.getUUID());
        playPlace(level, p);
        fr.heraliz.crac.bases.Tutoriel.piecePosee(player, p);
        return Outcome.ok(p);
    }

    /**
     * Place without claim, reach or cost checks (admin tools, tests). Grid rules still apply, and nothing is built while
     * the store stands in for older building data (it would never be saved).
     */
    public static Outcome placeAdmin(ServerLevel level, PieceKey key, Tier tier, UUID owner) {
        StructureStore store = store(level);
        if (store.holdsOldData()) {
            return Outcome.fail(OLD_DATA);
        }
        PlacementRules.Plan plan = PlacementRules.check(store.index, new LevelEnv(level), key, BuildConfig.stability(), BuildConfig.foundationMaxHeight.get());
        if (!plan.ok()) {
            return Outcome.fail(plan.result().message);
        }
        return Outcome.ok(commit(level, store, key, plan, tier, owner));
    }

    private static Piece commit(ServerLevel level, StructureStore store, PieceKey key, PlacementRules.Plan plan, Tier tier, UUID owner) {
        Building b = plan.buildingId() == PlacementRules.NEW_BUILDING ? store.newBuilding(key.origin()) : store.index.building(plan.buildingId());
        long now = level.getGameTime();
        Piece p = new Piece(store.newPieceId(), key, b.id, tier, BuildConfig.maxHp(tier, key.kind()), owner, now, plan.skirt());
        p.blocks = plan.blocks();
        p.stability = plan.stability();
        store.index.addWithBlocks(p);
        p.variant = Variants.of(store.index, p);
        if (plan.buildingId() != PlacementRules.NEW_BUILDING) {
            // a new support can raise pieces already standing (a wall put under a cantilevered floor)
            Stability.recompute(store.index, store.piecesOf(b), BuildConfig.stability());
        }
        store.setDirty();
        writeBlocks(level, store, p);
        updateVariantsAround(level, store, p);
        return p;
    }

    /** Admin: change a piece's tier without cost. */
    public static void setTier(ServerLevel level, Piece p, Tier tier) {
        p.tier = tier;
        p.maxHp = BuildConfig.maxHp(tier, p.kind());
        p.hp = p.maxHp;
        p.modifiedAt = level.getGameTime();
        tierChanged(level, store(level), p);
    }

    /**
     * Pushes a piece whose tier changed: its own variant first (corner posts go to the highest tier, §6.5), then its
     * neighbours'.
     */
    private static void tierChanged(ServerLevel level, StructureStore store, Piece p) {
        p.variant = Variants.of(store.index, p);
        refresh(level, p);
        updateVariantsAround(level, store, p);
    }

    // ------------------------------------------------------------------ block sync

    // Updates of existing pieces skip positions in unloaded chunks instead of loading them: the block entity of such a
    // position checks itself against the store when its chunk loads again (see StructureIntegrity). A new piece is
    // written everywhere (loading the chunk if needed): a block that is never written would count as vanished.

    private static void writeBlocks(ServerLevel level, StructureStore store, Piece p) {
        StructureBlock block = ModBlocks.STRUCTURE.get();
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            BlockState cur = level.getBlockState(pos);
            if (!(cur.getBlock() instanceof StructureBlock)) {
                internalEdits++;
                try {
                    level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
                } finally {
                    internalEdits--;
                }
            }
            if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
                be.setOccupants(withOccupant(be.occupants(), Occupant.of(p)));
                syncFaces(level, pos, be);
            }
        }
    }

    /** The list with {@code o} replacing the entry of the same piece in place (stable order), or appended. */
    static List<Occupant> withOccupant(List<Occupant> list, Occupant o) {
        List<Occupant> out = new ArrayList<>(list.size() + 1);
        boolean done = false;
        for (Occupant cur : list) {
            if (cur.pieceId() == o.pieceId()) {
                if (!done) {
                    out.add(o);
                    done = true;
                }
            } else {
                out.add(cur);
            }
        }
        if (!done) {
            out.add(o);
        }
        return out;
    }

    private static void eraseBlocks(ServerLevel level, Piece p) {
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            if (!level.isLoaded(pos)) continue;
            // a structure block being replaced (onBlockGone) already holds the new block: leave it alone
            if (!(level.getBlockState(pos).getBlock() instanceof StructureBlock)) continue;
            if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
                List<Occupant> list = new ArrayList<>(be.occupants());
                list.removeIf(o -> o.pieceId() == p.id);
                if (list.isEmpty()) {
                    internalEdits++;
                    try {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    } finally {
                        internalEdits--;
                    }
                } else {
                    be.setOccupants(list);
                    syncFaces(level, pos, be);
                }
            }
        }
    }

    /** Push a piece's new tier / state / variant / damage to the blocks it occupies. */
    public static void refresh(ServerLevel level, Piece p) {
        StructureStore store = store(level);
        Occupant occ = Occupant.of(p);
        for (BlockPos pos : PieceIndex.blocksOf(p)) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
                be.setOccupants(withOccupant(be.occupants(), occ));
                syncFaces(level, pos, be);
            }
        }
        store.setDirty();
    }

    static void syncFaces(ServerLevel level, BlockPos pos, StructureBlockEntity be) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState cur = level.getBlockState(pos);
        if (cur.getBlock() instanceof StructureBlock block) {
            BlockState want = block.stateFor(be.occlusionShape());
            if (want != cur) {
                internalEdits++;
                try {
                    level.setBlock(pos, want, Block.UPDATE_CLIENTS);
                } finally {
                    internalEdits--;
                }
            }
        }
    }

    /**
     * Refreshes the neighbours whose variant depends on {@code changed} (added, removed, upgraded): walls sharing a
     * vertex with a wall (corner posts), roofs of the building at the same h sharing a vertex with a roof (§6.5).
     * {@code changed} itself is skipped: its caller has just computed its variant.
     */
    private static void updateVariantsAround(ServerLevel level, StructureStore store, Piece changed) {
        PieceKey key = changed.key();
        List<Piece> affected;
        if (key.kind().isRoof()) {
            affected = Variants.roofsAround(store.index, key, changed.buildingId);
        } else if (key.kind().isWall()) {
            affected = Variants.wallsAround(store.index, key);
        } else {
            return;
        }
        for (Piece a : affected) {
            if (a.id == changed.id) continue;
            long v = Variants.of(store.index, a);
            if (v != a.variant) {
                a.variant = v;
                refresh(level, a);
            }
        }
    }

    // ------------------------------------------------------------------ removal, collapse, split

    public static void remove(ServerLevel level, Piece piece, Cause cause) {
        StructureStore store = store(level);
        if (store.index.piece(piece.id) == null) {
            return;
        }
        removeBatch(level, store, List.of(piece), cause, 0);
    }

    private static void removeBatch(ServerLevel level, StructureStore store, List<Piece> batch, Cause cause, int depth) {
        LongSet touchedBuildings = new LongOpenHashSet();
        for (Piece p : batch) {
            if (store.index.piece(p.id) == null) continue;
            touchedBuildings.add(p.buildingId);
            playDestroy(level, p, cause);
            store.index.removeWithBlocks(p);
            eraseBlocks(level, p);
            if (p.kind().isSurface()) {
                dropResting(level, p);
            }
        }
        for (Piece p : batch) {
            updateVariantsAround(level, store, p);
        }
        store.setDirty();
        Stability.Params params = BuildConfig.stability();
        for (long bid : touchedBuildings) {
            Building b = store.index.building(bid);
            if (b == null) continue;
            List<Piece> rest = store.piecesOf(b);
            if (rest.isEmpty()) {
                store.index.removeBuilding(bid);
                continue;
            }
            // with entries kept unread, only what this removal cut off falls (see StructureIntegrity.collapseUnsupported)
            List<Piece> fallen = Stability.recomputeFallen(store.index, rest, params, !store.isComplete());
            if (!fallen.isEmpty() && depth < 64) {
                removeBatch(level, store, fallen, Cause.COLLAPSE, depth + 1);
            } else {
                split(level, store, b);
            }
        }
    }

    /**
     * Things standing on a surface that just disappeared fall with it (loot spills, like Rust deployables): every
     * placable or Claim Block resting on the top of one of its blocks that no longer holds blocks by the placable rule
     * (rule 14: a structure block only while foundations or floors flush with its top cover it,
     * {@link PlacableSupport#holdsWhatStandsOn}). Roofs included: a merged roof's plateau can be flush with a block top,
     * and what stood there (placed before that rule, or with {@code placablesNeedSupport} off) falls with it.
     */
    private static void dropResting(ServerLevel level, Piece surface) {
        for (BlockPos below : PieceIndex.blocksOf(surface)) {
            BlockPos pos = below.above();
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (!PlacableSet.canPlace(level, s.getBlock()) || PlacableSupport.holdsWhatStandsOn(level, below)) continue;
            // a damaged placable keeps its HP in the item it drops, the Claim Block drops nothing
            PlacableChutes.fall(level, pos, s);
            PlacableChutes.dropUnsupportedAbove(level, pos);
        }
    }

    /** After a removal, pieces cut off from the part holding the claim become a separate, unclaimed building. */
    private static void split(ServerLevel level, StructureStore store, Building b) {
        List<Piece> pieces = store.piecesOf(b);
        if (pieces.size() <= 1) return;
        Long2ObjectOpenHashMap<List<Piece>> adj = new Long2ObjectOpenHashMap<>();
        for (Piece p : pieces) {
            for (Supports.Support s : Supports.of(store.index, p.key())) {
                if (s.piece().buildingId != b.id) continue;
                adj.computeIfAbsent(p.id, k -> new ArrayList<>()).add(s.piece());
                adj.computeIfAbsent(s.piece().id, k -> new ArrayList<>()).add(p);
            }
            if (p.kind().isFoundation()) {
                for (Piece n : Topology.foundationsSharingEdge(store.index, p.key())) {
                    if (n.buildingId != b.id) continue;
                    adj.computeIfAbsent(p.id, k -> new ArrayList<>()).add(n);
                }
            }
        }
        // the part holding the Claim Block's seat keeps the building (and its claim)
        Piece root = b.claimSeat != Building.NO_SEAT ? store.index.piece(b.claimSeat) : null;
        if (root == null || root.buildingId != b.id) {
            root = pieces.get(0);
        }
        LongSet reached = new LongOpenHashSet();
        ArrayDeque<Piece> queue = new ArrayDeque<>();
        queue.add(root);
        reached.add(root.id);
        while (!queue.isEmpty()) {
            Piece cur = queue.poll();
            for (Piece n : adj.getOrDefault(cur.id, List.of())) {
                if (reached.add(n.id)) {
                    queue.add(n);
                }
            }
        }
        if (reached.size() == pieces.size()) return;
        // every other component becomes its own building on the same grid
        LongSet assigned = new LongOpenHashSet(reached);
        for (Piece start : pieces) {
            if (assigned.contains(start.id)) continue;
            Building nb = store.newBuilding(b.origin);
            nb.ownerGroup = b.ownerGroup;
            ArrayDeque<Piece> q = new ArrayDeque<>();
            q.add(start);
            assigned.add(start.id);
            List<Piece> comp = new ArrayList<>();
            while (!q.isEmpty()) {
                Piece cur = q.poll();
                comp.add(cur);
                for (Piece n : adj.getOrDefault(cur.id, List.of())) {
                    if (assigned.add(n.id)) q.add(n);
                }
            }
            for (Piece p : comp) {
                store.index.removePiece(p);
                p.buildingId = nb.id;
                store.index.addPiece(p);
                refresh(level, p);
            }
        }
    }

    // ------------------------------------------------------------------ health

    /** Damage the piece at a block; returns false when there is none. */
    public static boolean damageAt(ServerLevel level, BlockPos pos, int amount) {
        Piece p = pieceAt(level, pos);
        if (p == null) {
            return false;
        }
        damage(level, p, amount, true);
        return true;
    }

    /**
     * Take health off a piece; at 0 it is destroyed and whatever it carried collapses. {@code raid} is for player
     * attacks (explosives): it starts the raid block of the claim. Mobs and decay pass false.
     */
    public static void damage(ServerLevel level, Piece p, int amount, boolean raid) {
        damage(level, p, amount, raid, Cause.DESTROYED);
    }

    public static void damage(ServerLevel level, Piece p, int amount, boolean raid, Cause cause) {
        if (amount <= 0 || store(level).index.piece(p.id) == null) {
            return;
        }
        // base d'un nouveau joueur encore protégée (monde des bases, 1.5.0) : ni raid ni usure
        if (fr.heraliz.crac.bases.Protections.pieceProtegee(level, p, cause)) {
            return;
        }
        int stage = p.crackStage();
        p.hp -= amount;
        store(level).setDirty();
        if (raid) {
            Building b = buildingOf(level, p);
            RaidBlockManager.trigger(level, b != null && b.claimAnchor != null ? b.claimAnchor : p.centreBlock());
        }
        if (p.hp <= 0) {
            remove(level, p, cause);
        } else if (p.crackStage() != stage) {
            refresh(level, p);
        }
    }

    public static void heal(ServerLevel level, Piece p, int amount) {
        if (amount <= 0 || p.hp >= p.maxHp) {
            return;
        }
        int stage = p.crackStage();
        p.hp = Math.min(p.maxHp, p.hp + amount);
        store(level).setDirty();
        if (p.crackStage() != stage) {
            refresh(level, p);
        }
    }

    // ------------------------------------------------------------------ hammer

    public static Outcome upgrade(ServerLevel level, ServerPlayer player, Piece p, Tier target) {
        Component combat = Heraliz.combatProblem(player);
        if (combat != null) return Outcome.fail(combat);
        if (!inReach(player, p)) return Outcome.fail("crac.build.too_far");
        if (!hasPrivilege(level, player, p)) return Outcome.fail("crac.build.not_yours");
        // also once the claim is gone: destroying the Claim Block or disbanding must not lift the raid block
        Claim c = claimOf(level, buildingOf(level, p));
        if (Config.raidBlockUpgrading.get() && RaidBlockManager.isRaidBlocked(level, c, p.centreBlock()) && !player.getAbilities().instabuild) {
            return Outcome.fail("crac.build.upgrade_raid");
        }
        if (target.ordinal() <= p.tier.ordinal()) {
            return Outcome.fail(Component.translatable("crac.build.already", Component.translatable("tier.crac." + p.tier.id)));
        }
        List<BuildCost> cost = Costs.upgrade(p.kind(), target);
        if (!Costs.has(player, cost)) {
            return Outcome.fail(Component.translatable("crac.build.need", Costs.describe(cost)));
        }
        Costs.take(player, cost);
        int oldMax = Math.max(1, p.maxHp);
        p.tier = target;
        p.maxHp = BuildConfig.maxHp(target, p.kind());
        p.hp = BuildConfig.upgradeHeals.get() ? p.maxHp : Math.max(1, (int) Math.round((double) p.hp / oldMax * p.maxHp));
        p.modifiedAt = level.getGameTime();
        tierChanged(level, store(level), p);
        playUpgrade(level, p);
        return Outcome.ok(p);
    }

    public static Outcome repair(ServerLevel level, ServerPlayer player, Piece p) {
        if (p.hp >= p.maxHp) return Outcome.ok(p);
        Component combat = Heraliz.combatProblem(player);
        if (combat != null) return Outcome.fail(combat);
        if (!inReach(player, p)) return Outcome.fail("crac.build.too_far");
        if (!hasPrivilege(level, player, p)) return Outcome.fail("crac.build.not_yours");
        Claim c = claimOf(level, buildingOf(level, p));
        if (BuildConfig.repairBlockedDuringRaid.get() && RaidBlockManager.isRaidBlocked(level, c, p.centreBlock()) && !player.getAbilities().instabuild) {
            return Outcome.fail("crac.build.repair_raid");
        }
        List<BuildCost> cost = Costs.repair(p.tier);
        if (!Costs.has(player, cost)) {
            return Outcome.fail(Component.translatable("crac.build.need", Costs.describe(cost)));
        }
        Costs.take(player, cost);
        heal(level, p, Math.max(1, (int) Math.round(p.maxHp * BuildConfig.repairPercent.get() / 100.0)));
        level.playSound(null, BlockPos.containing(centre(p)), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        return Outcome.ok(p);
    }

    public static Outcome demolish(ServerLevel level, ServerPlayer player, Piece p) {
        boolean creative = player.getAbilities().instabuild;
        // HeraliZ : pendant le tutoriel des bases, une pièce démolie rend ce qu'elle a coûté (le kit est compté)
        boolean tuto = !creative && fr.heraliz.crac.bases.Tutoriel.enCours(player);
        if (!creative) {
            Component combat = Heraliz.combatProblem(player);
            if (combat != null) return Outcome.fail(combat);
            if (!inReach(player, p)) return Outcome.fail("crac.build.too_far");
            UUID group = controllingGroup(level, buildingOf(level, p));
            boolean allowed = group != null ? isMember(level, player, group, ClanAccess.Action.BUILD) : p.owner.equals(player.getUUID());
            if (!allowed) return Outcome.fail("crac.build.not_yours");
            int window = BuildConfig.demolishWindowSeconds.get();
            if (window >= 0 && !tuto && level.getGameTime() - p.modifiedAt > window * 20L) {
                return Outcome.fail(Txt.k("crac.build.demolish_late", window / 60));
            }
        }
        remove(level, p, Cause.DEMOLISH);
        if (tuto) {
            fr.heraliz.crac.bases.Tutoriel.rembourser(player, p);
        }
        return Outcome.ok(p);
    }

    /** Melee hit on a twig piece (Rust: twigs break by hand). */
    public static void meleeHit(ServerLevel level, ServerPlayer player, BlockPos pos, Vec3 hit, float strength) {
        // HeraliZ: only C4 damage pieces, twigs do not break by hand either
        if (!BuildConfig.twigMeleeDamage.get() || Heraliz.onlyC4()) return;
        Piece p = pieceAt(level, pos, hit);
        if (p == null || p.tier != Tier.TWIG) return;
        int dmg = Math.max(1, Math.round(BuildConfig.twigMeleeHit.get() * Math.max(0.1F, strength)));
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        damage(level, p, dmg, false);
    }

    // ------------------------------------------------------------------ doors and windows

    public static InteractionResult onUse(Level level, BlockPos pos, Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide()) {
            for (Piece p : ClientMirror.view().occupants(pos)) {
                if (p.kind().hasDoor() || p.kind() == PieceKind.WINDOW) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel sl) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        Piece p = pieceAt(sl, pos, hit.getLocation());
        if (p == null) {
            return InteractionResult.PASS;
        }
        if (p.kind().hasDoor()) {
            if (!canUseDoor(sl, player, p)) {
                player.displayClientMessage(Txt.red("crac.door.locked"), true);
                level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            boolean open = !p.isOpen();
            if (!open && closingBlocked(sl, p)) {
                player.displayClientMessage(Txt.yellow("crac.door.someone"), true);
                return InteractionResult.CONSUME;
            }
            p.setOpen(open);
            refresh(sl, p);
            playDoor(sl, p, open);
            majSas(sl, p);
            return InteractionResult.CONSUME;
        }
        if (p.kind() == PieceKind.WINDOW && p.insert() == Piece.INSERT_NONE) {
            ItemStack stack = player.getItemInHand(hand);
            int insert = stack.is(ModItems.METAL_WINDOW.get()) ? Piece.INSERT_BARS : stack.is(ModItems.GLASS_WINDOW.get()) ? Piece.INSERT_GLASS : Piece.INSERT_NONE;
            if (insert == Piece.INSERT_NONE) {
                return InteractionResult.PASS;
            }
            if (!hasPrivilege(sl, player, p)) {
                player.displayClientMessage(Txt.red("crac.build.not_yours"), true);
                return InteractionResult.CONSUME;
            }
            p.setInsert(insert);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            refresh(sl, p);
            level.playSound(null, pos, insert == Piece.INSERT_BARS ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Doors of a claimed (or once claimed) building only open for its group; never-claimed doors are free. */
    public static boolean canUseDoor(ServerLevel level, Player player, Piece p) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        UUID group = controllingGroup(level, buildingOf(level, p));
        return group == null || isMember(level, player, group, ClanAccess.Action.DOORS);
    }

    /**
     * Airlock (HeraliZ, CRac 1.10.1): the doors on the edges of a same triangle foundation or floor. Each door always
     * opens and closes; while two of them are open, each open one collides like a closed leaf (it still looks open,
     * {@link Piece#STATE_SAS}): nobody walks straight through, and nobody is ever locked in (closing one door frees the
     * other). A door with someone standing in its opening is left passable.
     */
    private static void majSas(ServerLevel level, Piece door) {
        Building b = buildingOf(level, door);
        List<Piece> portes = b == null ? List.of(door) : portesDuSas(store(level).piecesOf(b), door);
        int ouvertes = 0;
        for (Piece d : portes) {
            if (d.isOpen()) {
                ouvertes++;
            }
        }
        for (Piece d : portes) {
            boolean bloque = d.isOpen() && ouvertes >= 2 && !closingBlocked(level, d);
            if (bloque != d.isSasBloque()) {
                d.setSasBloque(bloque);
                refresh(level, d);
            }
        }
    }

    /** {@code door} and the doors that share a triangle foundation or floor with it (an airlock). */
    private static List<Piece> portesDuSas(List<Piece> pieces, Piece door) {
        List<Piece> out = new ArrayList<>();
        out.add(door);
        EdgeKey edge = bord(door);
        for (Piece t : pieces) {
            if (t.kind().shape != PieceKind.Shape.TRIANGLE || t.kind().family != PieceKind.Family.SURFACE) {
                continue;
            }
            List<EdgeKey> bords = t.key().edges();
            if (!bords.contains(edge)) {
                continue;
            }
            for (Piece o : pieces) {
                if (o != door && o.kind().hasDoor() && !out.contains(o) && bords.contains(bord(o))) {
                    out.add(o);
                }
            }
        }
        return out;
    }

    /** A wall's edge in the canonical form of footprint edges (either direction). */
    private static EdgeKey bord(Piece wall) {
        return EdgeKey.of(wall.key().originKey(), wall.key().anchor(), wall.key().orient());
    }

    /** A closing door must not shut on a player standing in the opening. */
    private static boolean closingBlocked(ServerLevel level, Piece p) {
        LevelEnv env = new LevelEnv(level);
        for (AABB box : PlacementRules.worldBoxes(p.key(), PieceIndex.blocksOf(p))) {
            if (env.entityIn(box)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ claims

    /** Why a Claim Block may not go there, or null. The Claim Block must stand on a surface of an unclaimed building. */
    @Nullable
    public static String claimProblem(ServerLevel level, BlockPos claimPos, int tier) {
        StructureStore store = store(level);
        Piece under = seatUnder(level, claimPos);
        if (under == null) {
            StructureBlock.StandIssue issue = StructureBlock.standIssue(level, claimPos.below());
            if (issue == StructureBlock.StandIssue.HALF_LEVEL) {
                return "crac.claim.half_level";
            }
            if (issue == StructureBlock.StandIssue.PARTLY_COVERED) {
                return "crac.claim.cut";
            }
            return "crac.claim.on_floor";
        }
        Building b = store.index.building(under.buildingId);
        if (b == null) {
            return "crac.claim.on_floor";
        }
        if (claimOf(level, b) != null) {
            return "crac.claim.already";
        }
        Claim probe = new Claim(new UUID(0, 0), claimPos, tier);
        for (Piece p : store.piecesOf(b)) {
            for (BlockPos pos : PieceIndex.blocksOf(p)) {
                if (!probe.contains(pos)) {
                    return "crac.claim.too_big";
                }
            }
        }
        return null;
    }

    /**
     * The smallest claim tier whose zone around a Claim Block at {@code claimPos} holds every block of the building it
     * stands on: a building grown under a bigger claim needs that tier again. 1 when it stands on no building, 3 when no
     * zone is big enough ({@link #claimProblem} then refuses it).
     */
    public static int claimTierFor(ServerLevel level, BlockPos claimPos) {
        Building b = ClaimManager.buildingUnder(level, claimPos);
        if (b == null) {
            return 1;
        }
        int horizontal = 0;
        int vertical = 0;
        for (Piece p : store(level).piecesOf(b)) {
            for (BlockPos pos : PieceIndex.blocksOf(p)) {
                horizontal = Math.max(horizontal, Math.max(Math.abs(pos.getX() - claimPos.getX()), Math.abs(pos.getZ() - claimPos.getZ())));
                vertical = Math.max(vertical, Math.abs(pos.getY() - claimPos.getY()));
            }
        }
        for (int tier = 1; tier < 3; tier++) {
            if (horizontal <= Config.claimRadius(tier) && vertical <= Config.claimYRadius(tier)) {
                return tier;
            }
        }
        return 3;
    }

    /** Links the building under a new Claim Block to its claim; the claim's group becomes the building's owner. */
    public static void onClaimCreated(ServerLevel level, BlockPos claimPos) {
        StructureStore store = store(level);
        Claim claim = ClaimManager.getByAnchor(level, claimPos);
        Piece seat = seatUnder(level, claimPos);
        if (seat == null) {
            return;
        }
        Building b = store.index.building(seat.buildingId);
        if (b != null) {
            b.claimAnchor = claimPos.immutable();
            b.claimSeat = seat.id;
            if (claim != null) {
                b.ownerGroup = claim.groupId();
            }
            store.setDirty();
        }
    }

    /**
     * The seat of a Claim Block (or any block that needs a full flush surface) at {@code pos}: the top face of the
     * block below must be fully covered by walkable surfaces of one building whose top is flush with that face (24 h
     * on a block boundary, so h even). Returns the surface covering most of the face (lowest id on ties), or null.
     * A floor on a half wall (odd h) or a sliver block at a rotated edge holds no Claim Block (§7.5).
     */
    @Nullable
    public static Piece seatUnder(ServerLevel level, BlockPos pos) {
        return seatUnder(store(level).index, pos);
    }

    /** {@link #seatUnder(ServerLevel, BlockPos)} over any view : build/logic/Assises (commun, lu aussi par l'aperçu client). */
    @Nullable
    public static Piece seatUnder(StructureView view, BlockPos pos) {
        return Assises.seatUnder(view, pos);
    }

    /** The building a Claim Block is attached to, or null. */
    @Nullable
    public static Building buildingOfClaim(ServerLevel level, BlockPos claimPos) {
        for (Building b : store(level).index.allBuildings()) {
            if (claimPos.equals(b.claimAnchor)) {
                return b;
            }
        }
        return null;
    }

    /** Unlinks the claim; the building keeps its {@link Building#ownerGroup}. */
    public static void onClaimRemoved(ServerLevel level, BlockPos claimPos) {
        StructureStore store = store(level);
        for (Building b : store.index.allBuildings()) {
            if (claimPos.equals(b.claimAnchor)) {
                b.claimAnchor = null;
                b.claimSeat = Building.NO_SEAT;
                store.setDirty();
            }
        }
    }

    // ------------------------------------------------------------------ integrity (see StructureIntegrity)

    public static void queueVerify(Level level, BlockPos pos) {
        StructureIntegrity.queueVerify(level, pos);
    }

    /** Called every server tick per level: keeps the blocks and the store in agreement. */
    public static void tick(ServerLevel level) {
        StructureIntegrity.tick(level);
    }

    /** A block write of the building system itself: {@link #onBlockGone} ignores it. */
    static void setBlockInternal(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        internalEdits++;
        try {
            level.setBlock(pos, state, flags);
        } finally {
            internalEdits--;
        }
    }

    /** A structure block was removed by something else (creative player, /setblock, world editor): drop its pieces. */
    public static void onBlockGone(Level level, BlockPos pos) {
        if (internalEdits > 0 || !(level instanceof ServerLevel sl)) {
            return;
        }
        List<Piece> hit = new ArrayList<>(store(sl).index.occupants(pos));
        for (Piece p : hit) {
            remove(sl, p, Cause.EXTERNAL);
        }
    }

    // ------------------------------------------------------------------ effects

    public static BlockState particleState(Tier tier) {
        return switch (tier) {
            case TWIG -> Blocks.OAK_LEAVES.defaultBlockState();
            case WOOD -> Blocks.OAK_PLANKS.defaultBlockState();
            case STONE -> Blocks.STONE_BRICKS.defaultBlockState();
            case METAL -> Blocks.IRON_BLOCK.defaultBlockState();
            case ARMORED -> Blocks.GRAY_CONCRETE.defaultBlockState();
        };
    }

    private static Vec3 centre(Piece p) {
        return bounds(p).getCenter();
    }

    private static void playPlace(ServerLevel level, Piece p) {
        level.playSound(null, BlockPos.containing(centre(p)), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    private static void playDestroy(ServerLevel level, Piece p, Cause cause) {
        Vec3 c = centre(p);
        SoundEvent snd = switch (p.tier) {
            case TWIG, WOOD -> SoundEvents.WOOD_BREAK;
            case STONE -> SoundEvents.STONE_BREAK;
            default -> SoundEvents.NETHERITE_BLOCK_BREAK;
        };
        level.playSound(null, BlockPos.containing(c), snd, SoundSource.BLOCKS, cause == Cause.COLLAPSE ? 1.3F : 1.0F, 0.85F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, particleState(p.tier)), c.x, c.y, c.z, 40, 1.0, 0.8, 1.0, 0.15);
    }

    private static void playUpgrade(ServerLevel level, Piece p) {
        Vec3 c = centre(p);
        BlockPos at = BlockPos.containing(c);
        switch (p.tier) {
            case WOOD -> level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            case STONE -> level.playSound(null, at, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            case METAL, ARMORED -> {
                level.playSound(null, at, ModSounds.SHEET_METAL_UPGRADE.get(), SoundSource.BLOCKS, 1.0F, p.tier == Tier.ARMORED ? 0.85F : 1.0F);
                level.sendParticles(ModParticles.SHEET_METAL_SPARK.get(), c.x, c.y, c.z, 60, 1.0, 0.8, 1.0, 0.0);
            }
            default -> {
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, particleState(p.tier)), c.x, c.y, c.z, 30, 1.0, 0.8, 1.0, 0.1);
    }

    private static void playDoor(ServerLevel level, Piece p, boolean open) {
        boolean wood = p.tier == Tier.TWIG || p.tier == Tier.WOOD;
        SoundEvent snd = wood
            ? (open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE)
            : (open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE);
        level.playSound(null, BlockPos.containing(centre(p)), snd, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.1F);
    }
}
