package fr.heraliz.crac.build.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceSolids;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Supports;
import fr.heraliz.crac.build.model.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the world blocks and the {@link StructureStore} in agreement, from both sides.
 * <ul>
 *   <li>Block side: a structure block that loads is rewritten from the store. A block's record of a piece is only
 *   dropped when the store vouches that the piece is gone ({@link StructureStore#vouchesFor}), so a lost or
 *   unreadable store never erases bases. A block still holding v2 data is only removed once the old buildings were
 *   explicitly discarded ({@link StructureStore#discardedOldData}).</li>
 *   <li>Store side: when a chunk loads, every piece with a block in it is checked. A piece whose block vanished
 *   (region rollback, crash between the chunk and the store saves) is removed and stops holding up the rest of its
 *   building; a foundation with no ground left under any of its columns collapses.</li>
 *   <li>After loading, pieces left with no support at all (stability 0) fall, as they would have when it went.</li>
 * </ul>
 */
public final class StructureIntegrity {
    private static final Map<ResourceKey<Level>, LongSet> BLOCKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, LongSet> CHUNKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, LongSet> GROUND = new HashMap<>();
    /** Levels whose blocks of unknown pieces were already reported this session. */
    private static final Set<ResourceKey<Level>> REPORTED = new HashSet<>();

    private StructureIntegrity() {
    }

    /** A structure block entity loaded: check it against the store on the next tick. */
    public static void queueVerify(Level level, BlockPos pos) {
        queue(BLOCKS, level, pos.asLong());
    }

    /** A chunk loaded: check the store's pieces in it on the next tick. */
    public static void queueChunk(Level level, ChunkPos chunk) {
        queue(CHUNKS, level, chunk.toLong());
    }

    /** The block under {@code pos} changed: if {@code pos} is the bottom of a foundation column, check its ground. */
    public static void queueGroundCheck(Level level, BlockPos pos) {
        queue(GROUND, level, pos.asLong());
    }

    private static void queue(Map<ResourceKey<Level>, LongSet> map, Level level, long value) {
        if (level instanceof ServerLevel sl) {
            map.computeIfAbsent(sl.dimension(), k -> new LongOpenHashSet()).add(value);
        }
    }

    /** Server stopped: positions of this world mean nothing in the next one. */
    public static void clear() {
        BLOCKS.clear();
        CHUNKS.clear();
        GROUND.clear();
        REPORTED.clear();
    }

    /** Called every server tick per level. */
    public static void tick(ServerLevel level) {
        StructureStore store = StructureStore.get(level);
        if (BuildConfig.SPEC.isLoaded() && store.takeCollapsePending()) {
            // again with the server's stability settings, in case the store was first read before they loaded
            store.recomputeAll();
            collapseUnsupported(level, store);
        }
        LongArrayList blocks = take(BLOCKS, level);
        if (blocks != null) {
            verifyBlocks(level, store, blocks);
        }
        LongArrayList chunks = take(CHUNKS, level);
        if (chunks != null) {
            for (long c : chunks) {
                checkChunk(level, store, new ChunkPos(c));
            }
        }
        LongArrayList ground = take(GROUND, level);
        if (ground != null) {
            checkGroundAt(level, store, ground);
        }
    }

    private static LongArrayList take(Map<ResourceKey<Level>, LongSet> map, ServerLevel level) {
        LongSet pending = map.get(level.dimension());
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        LongArrayList batch = new LongArrayList(pending);
        pending.clear();
        return batch;
    }

    // ------------------------------------------------------------------ block side

    private static void verifyBlocks(ServerLevel level, StructureStore store, LongArrayList batch) {
        int unknown = 0;
        for (long l : batch) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof StructureBlock)
                || !(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
                continue;
            }
            if (keepsOldData(store, be)) {
                // v2 data is left exactly as it is unless the old buildings were explicitly discarded (§7.6): the store
                // may be a stand-in for the old file, for an unreadable or a missing one, or never have seen it
                unknown++;
                continue;
            }
            List<Occupant> want = expected(store, pos, be.occupants());
            if (want.size() > store.index.occupants(pos).size()) {
                unknown++;
            }
            if (want.isEmpty()) {
                StructureService.setBlockInternal(level, pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (!sameOccupants(want, be.occupants())) {
                be.setOccupants(want);
                StructureService.syncFaces(level, pos, be);
            }
        }
        if (unknown > 0 && REPORTED.add(level.dimension())) {
            Crac.LOGGER.warn("[CRac buildings] {} structure block(s) in {} hold pieces that {} does not know (lost or unreadable file?):"
                + " they are kept as they are. Restore the file, or remove them with /cracbuild clear.", unknown, level.dimension().location(), StructureStore.ID);
        }
    }

    /** What a block must hold: the store's pieces there, plus the records of pieces the store does not vouch for. */
    static List<Occupant> expected(StructureStore store, BlockPos pos, List<Occupant> have) {
        List<Piece> pieces = store.index.occupants(pos);
        List<Occupant> want = new ArrayList<>(pieces.size());
        for (Piece p : pieces) {
            want.add(Occupant.of(p));
        }
        for (Occupant o : have) {
            if (isUnknown(store, o.pieceId())) {
                want.add(o);
            }
        }
        return want;
    }

    /** A block still holding v2 data that must stay as it is: the old buildings were not explicitly discarded. */
    static boolean keepsOldData(StructureStore store, StructureBlockEntity be) {
        return be.hasLegacyData() && !store.discardedOldData();
    }

    /** A piece the store does not have and cannot vouch is gone: from a lost or unreadable file, or an entry kept unread. */
    private static boolean isUnknown(StructureStore store, long pieceId) {
        return !store.vouchesFor(pieceId) && store.index.piece(pieceId) == null;
    }

    /**
     * Whether the structure block at {@code pos} (loaded) holds a piece the store does not know: the building it belongs
     * to may exist, only missing from the store. Kept v2 data counts.
     */
    public static boolean holdsUnknownPieces(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
            return false;
        }
        StructureStore store = StructureStore.get(level);
        if (keepsOldData(store, be)) {
            return true;
        }
        for (Occupant o : be.occupants()) {
            if (isUnknown(store, o.pieceId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameOccupants(List<Occupant> a, List<Occupant> b) {
        return a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
    }

    /**
     * Operator cleanup: structure blocks inside {@code box} (loaded chunks only) that hold no piece of the store, such
     * as the blocks kept after a lost or unreadable store. Returns how many were removed.
     */
    public static int clearOrphans(ServerLevel level, AABB box) {
        StructureStore store = StructureStore.get(level);
        List<BlockPos> orphans = new ArrayList<>();
        int minX = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(box.maxX));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ));
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (be instanceof StructureBlockEntity && box.contains(Vec3.atCenterOf(pos)) && store.index.occupants(pos).isEmpty()) {
                        orphans.add(pos);
                    }
                }
            }
        }
        for (BlockPos pos : orphans) {
            StructureService.setBlockInternal(level, pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return orphans.size();
    }

    // ------------------------------------------------------------------ store side

    /** Every piece with a block in a chunk that just loaded must still have its blocks there. */
    static void checkChunk(ServerLevel level, StructureStore store, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) {
            return;
        }
        LongSet ids = store.index.piecesInChunk(chunk.toLong());
        if (ids.isEmpty()) {
            return;
        }
        List<Piece> lost = new ArrayList<>();
        List<Piece> foundations = new ArrayList<>();
        for (long id : ids.toLongArray()) {
            Piece p = store.index.piece(id);
            if (p == null) {
                continue;
            }
            boolean missing = false;
            for (BlockPos pos : PieceIndex.blocksOf(p)) {
                if (SectionPos.blockToSectionCoord(pos.getX()) != chunk.x || SectionPos.blockToSectionCoord(pos.getZ()) != chunk.z) {
                    continue;
                }
                if (!(level.getBlockState(pos).getBlock() instanceof StructureBlock)) {
                    missing = true;
                    break;
                }
                if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be) || !holds(be, p.id)) {
                    // the block is there but lost its record: rewrite it from the store
                    queueVerify(level, pos);
                }
            }
            if (missing) {
                lost.add(p);
            } else if (p.kind().isFoundation()) {
                foundations.add(p);
            }
        }
        if (!lost.isEmpty()) {
            Crac.LOGGER.info("[CRac buildings] {} piece(s) in chunk {} of {} lost their blocks while unloaded (rollback or crash): removed",
                lost.size(), chunk, level.dimension().location());
            for (Piece p : lost) {
                StructureService.remove(level, p, StructureService.Cause.EXTERNAL);
            }
        }
        for (Piece f : foundations) {
            collapseIfUngrounded(level, store, f);
        }
    }

    private static boolean holds(StructureBlockEntity be, long pieceId) {
        for (Occupant o : be.occupants()) {
            if (o.pieceId() == pieceId) {
                return true;
            }
        }
        return false;
    }

    /** Foundations whose column ends right above one of {@code positions}, where the block below just changed. */
    private static void checkGroundAt(ServerLevel level, StructureStore store, LongArrayList positions) {
        Set<Piece> candidates = new LinkedHashSet<>();
        for (long l : positions) {
            BlockPos pos = BlockPos.of(l);
            for (Piece p : store.index.occupants(pos)) {
                if (p.kind().isFoundation() && isColumnBottom(p, pos)) {
                    candidates.add(p);
                }
            }
        }
        for (Piece f : candidates) {
            collapseIfUngrounded(level, store, f);
        }
    }

    /**
     * Whether {@code pos} is the ground a foundation column stands on: the block right under the bottom of one of its
     * columns (see {@link #hasGround}).
     */
    public static boolean isFoundationGround(StructureStore store, BlockPos pos) {
        BlockPos bottom = pos.above();
        for (Piece p : store.index.occupants(bottom)) {
            if (p.kind().isFoundation() && isColumnBottom(p, bottom)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code pos} is the bottom block of one of the foundation's columns (slab row minus its skirt). */
    private static boolean isColumnBottom(Piece f, BlockPos pos) {
        List<int[]> cols = Footprints.columns(f.key());
        int slab = PieceSolids.slabRow(f.key());
        for (int i = 0; i < cols.size() && i < f.skirt.length; i++) {
            if (cols.get(i)[0] == pos.getX() && cols.get(i)[1] == pos.getZ()) {
                return pos.getY() == slab - f.skirt[i];
            }
        }
        return false;
    }

    private static void collapseIfUngrounded(ServerLevel level, StructureStore store, Piece f) {
        if (store.index.piece(f.id) != null && !hasGround(level, f)) {
            Crac.LOGGER.info("[CRac buildings] Foundation at {} in {} has no ground left under it: it collapses", f.key().anchorBlock().toShortString(), level.dimension().location());
            StructureService.remove(level, f, StructureService.Cause.COLLAPSE);
        }
    }

    /**
     * Whether a foundation still rests on the ground (see {@link LevelEnv#isGround}) under at least one of its
     * columns. A column in an unloaded chunk counts as resting: it cannot be checked.
     */
    public static boolean hasGround(ServerLevel level, Piece f) {
        LevelEnv env = new LevelEnv(level);
        List<int[]> cols = Footprints.columns(f.key());
        int slab = PieceSolids.slabRow(f.key());
        for (int i = 0; i < cols.size() && i < f.skirt.length; i++) {
            BlockPos ground = new BlockPos(cols.get(i)[0], slab - f.skirt[i] - 1, cols.get(i)[1]);
            if (!level.isLoaded(ground) || env.isGround(ground)) {
                return true;
            }
        }
        return false;
    }

    /** Pieces with no path to a foundation after loading (stability settings changed, data edited) fall. */
    private static void collapseUnsupported(ServerLevel level, StructureStore store) {
        List<Piece> fallen = new ArrayList<>();
        for (Piece p : store.index.allPieces()) {
            if (p.stability <= 0 && !Supports.isGrounded(p.key())) {
                fallen.add(p);
            }
        }
        if (fallen.isEmpty()) {
            return;
        }
        if (!store.isComplete()) {
            Crac.LOGGER.warn("[CRac buildings] {} piece(s) in {} have no support, but some entries of {} could not be read and may be"
                + " what holds them: they are left standing", fallen.size(), level.dimension().location(), StructureStore.ID);
            return;
        }
        Crac.LOGGER.info("[CRac buildings] {} piece(s) in {} had no support left after loading (stability settings changed?): they collapse",
            fallen.size(), level.dimension().location());
        for (Piece p : fallen) {
            StructureService.remove(level, p, StructureService.Cause.COLLAPSE);
        }
    }
}
