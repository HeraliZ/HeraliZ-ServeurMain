package fr.heraliz.crac.build.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.logic.Conflicts;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Stability;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side source of truth: every piece and building of one dimension.
 * <p>
 * Loading never throws data away: an entry this version cannot use (unknown kind or tier, malformed key, duplicate
 * id or piece, missing building) is kept as raw NBT, written back unchanged, and the blocks of such a piece are left
 * alone by the integrity check. A store created without a readable file (new world, lost or unreadable file) only
 * vouches for the ids it issues itself ({@link #vouchesFor}), so blocks of older pieces are kept rather than erased.
 * <p>
 * A file of an older format (v2 grid buildings) is never read nor erased silently: the store becomes a stand-in that
 * is never saved, unless {@code discardOldBuildings} is set, which copies the file aside and starts empty (§7.6). The
 * v2 structure blocks are only removed after such an explicit discard ({@link #discardedOldData}).
 */
public final class StructureStore extends SafeSavedData {
    public static final String ID = "crac_buildings";
    /** Version of the saved layout, written with the data so that a later layout can convert it. */
    public static final int FORMAT = 3;

    public final PieceIndex index = new PieceIndex();
    private long nextPiece = 1L;
    private long nextBuilding = 1L;
    /** Lowest piece id this store vouches for: the first id it issued (1 for files saved before this existed). */
    private long trustFrom = 1L;
    private final List<CompoundTag> keptPieces = new ArrayList<>();
    private final List<CompoundTag> keptBuildings = new ArrayList<>();
    private final LongSet keptPieceIds = new LongOpenHashSet();
    private boolean needsRecompute;
    private boolean collapsePending;
    /** Read from a file of an older format: its content was not read (see {@link #onOldFormat}). */
    private boolean oldFormat;
    /** Whether that older file linked a building to a claim. */
    private boolean oldClaimLinks;
    /** The store stands in for older building data it does not read: nothing may be built (it would be lost). */
    private boolean holdsOldData;
    /**
     * Older building data was explicitly discarded ({@code discardOldBuildings}): only then do the structure blocks that
     * still hold v2 data clean themselves up. Saved, since their chunks may load long after the discard.
     */
    private boolean discardedOldData;

    public StructureStore() {
    }

    public static StructureStore get(ServerLevel level) {
        StructureStore s = level.getDataStorage().computeIfAbsent(StructureStore::load, () -> create(level), ID);
        if (s.oldFormat) {
            s.oldFormat = false;
            s.onOldFormat(dataFile(level, ID), BuildConfig.SPEC.isLoaded() && BuildConfig.discardOldBuildings.get(),
                level.dimension().location().toString());
        }
        if (s.needsRecompute) {
            s.needsRecompute = false;
            s.recomputeAll();
            s.collapsePending = true;
        }
        return s;
    }

    /** No usable file: a new store, or a stand-in for a file Minecraft could not read (it logged why) or that was lost. */
    private static StructureStore create(ServerLevel level) {
        StructureStore s = new StructureStore();
        boolean unreadable = guardUnreadable(level, ID, s);
        // A new store only vouches for the ids it issues itself: if structure blocks are already in the world (the
        // file was lost or could not be read), their pieces have lower ids and their blocks are kept, never erased.
        s.distrustOlderIds(Math.max(System.currentTimeMillis(), 1L << 40));
        if (!unreadable && ClaimData.hasClaims(level)) {
            // every claim needs a building: with claims around, a missing file was lost (a partial restore), not new
            s.setStandIn(true);
            Crac.LOGGER.error("[CRac buildings] {} is missing from {} although claims exist there: put it back with the server stopped"
                + " if you have it.", ID, level.dimension().location());
        }
        if (s.isStandIn()) {
            Crac.LOGGER.error("[CRac buildings] The structure blocks already in {} are kept as they are (they cannot be damaged or"
                + " upgraded) until the file is restored or an operator clears them with /cracbuild clear.", level.dimension().location());
        }
        return s;
    }

    /**
     * Whether the level's store, read without being created, links a building to a claim (assumed when its file exists
     * but cannot be read). For the claim data's factory: claim data missing while buildings are linked to claims was lost.
     */
    public static boolean hasClaimLinks(ServerLevel level) {
        StructureStore s = level.getDataStorage().get(StructureStore::load, ID);
        if (s == null) {
            return Files.exists(dataFile(level, ID));
        }
        if (s.oldClaimLinks) {
            return true;
        }
        for (Building b : s.index.allBuildings()) {
            if (b.claimAnchor != null) {
                return true;
            }
        }
        for (CompoundTag t : s.keptBuildings) {
            if (t.contains("Claim")) {
                return true;
            }
        }
        return false;
    }

    /** New pieces and buildings get ids from {@code base} on, and no id below it is vouched for. */
    void distrustOlderIds(long base) {
        this.nextPiece = Math.max(this.nextPiece, base);
        this.nextBuilding = Math.max(this.nextBuilding, base);
        this.trustFrom = Math.max(this.trustFrom, base);
    }

    public static StructureStore load(CompoundTag tag) {
        StructureStore s = new StructureStore();
        int format = tag.getInt("Format");
        if (format < FORMAT) {
            // v2 grid data: nothing in it maps to the lattice, it is left to onOldFormat (stand-in or discard)
            s.oldFormat = true;
            ListTag old = tag.getList("Buildings", Tag.TAG_COMPOUND);
            for (int i = 0; i < old.size(); i++) {
                s.oldClaimLinks |= old.getCompound(i).contains("Claim");
            }
            return s;
        }
        if (format > FORMAT) {
            Crac.LOGGER.warn("[CRac buildings] {} was written by a newer version of the mod (format {}): what this version cannot read is kept unchanged", ID, format);
        }
        s.nextPiece = Math.max(1L, tag.getLong("NextPiece"));
        s.nextBuilding = Math.max(1L, tag.getLong("NextBuilding"));
        s.trustFrom = Math.max(1L, tag.getLong("TrustFrom"));
        s.discardedOldData = tag.getBoolean("DiscardedV2");
        ListTag buildings = tag.getList("Buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildings.size(); i++) {
            CompoundTag t = buildings.getCompound(i);
            Building b = Building.load(t);
            s.nextBuilding = Math.max(s.nextBuilding, b.id + 1);
            if (b.id <= 0 || s.index.building(b.id) != null) {
                s.keptBuildings.add(t);
                continue;
            }
            s.index.putBuilding(b);
        }
        ListTag pieces = tag.getList("Pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < pieces.size(); i++) {
            CompoundTag t = pieces.getCompound(i);
            long id = t.getLong("Id");
            s.nextPiece = Math.max(s.nextPiece, id + 1);
            s.nextBuilding = Math.max(s.nextBuilding, t.getLong("Building") + 1);
            Piece p = readPiece(t);
            if (p == null || id <= 0 || s.index.piece(id) != null || s.index.building(p.buildingId) == null || duplicate(s.index, p)) {
                s.keptPieces.add(t);
                s.keptPieceIds.add(id);
                continue;
            }
            s.index.addWithBlocks(p);
        }
        if (!s.keptPieces.isEmpty() || !s.keptBuildings.isEmpty()) {
            Crac.LOGGER.warn("[CRac buildings] {} piece(s) and {} building(s) of {} could not be used (unknown kind or tier, malformed or duplicate"
                + " entry, missing building): they are kept unchanged in the file and their blocks are left as they are",
                s.keptPieces.size(), s.keptBuildings.size(), ID);
        }
        s.needsRecompute = true;
        return s;
    }

    private static Piece readPiece(CompoundTag t) {
        try {
            return Piece.load(t);
        } catch (RuntimeException e) {
            Crac.LOGGER.error("[CRac buildings] Unreadable piece entry {}", t, e);
            return null;
        }
    }

    /**
     * An exact duplicate of a piece already loaded (same footprint and h, same edge with an overlapping band, or an exact
     * interior overlap) would corrupt the index. Clearance and joint rules are placement rules only: tuning them never
     * invalidates saved pieces.
     */
    private static boolean duplicate(PieceIndex index, Piece p) {
        return Conflicts.duplicateOf(index, p.key(), p.id) != null;
    }

    /**
     * The file holds buildings of an older format. Unless {@code discard}, the store stays an empty stand-in that is
     * never saved (claims are kept, the old structure blocks keep their data and are left alone). With {@code discard}
     * the file is copied to {@code crac_buildings.dat.v2-<time>} first; if that copy fails, nothing is discarded.
     */
    void onOldFormat(Path file, boolean discard, String where) {
        if (discard) {
            Path copy = Files.exists(file) ? copyAside(file, "v2") : null;
            if (copy != null) {
                Crac.LOGGER.warn("[CRac buildings] v2 building data of {} discarded (discardOldBuildings = true): the old file was copied to {}."
                    + " The old structure blocks clean themselves up.", where, copy.getFileName());
                this.discardedOldData = true;
                this.setDirty();
                return;
            }
            Crac.LOGGER.error("[CRac buildings] v2 building data of {} could not be copied aside: it is NOT discarded.", where);
        }
        this.setStandIn(true);
        this.protectFile();
        this.holdsOldData = true;
        Crac.LOGGER.error("[CRac buildings] v2 building data found in {}: use a fresh world or set discardOldBuildings = true in"
            + " crac-building.toml. Until then the old buildings are left untouched and no building data is saved.", where);
    }

    /** Toutes les pièces de toutes les bases : écrites en fond (25/10, perf, voir {@link SafeSavedData#ecritureEnFond}). */
    @Override
    protected boolean ecritureEnFond() {
        return true;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("Format", FORMAT);
        tag.putLong("NextPiece", this.nextPiece);
        tag.putLong("NextBuilding", this.nextBuilding);
        tag.putLong("TrustFrom", this.trustFrom);
        if (this.discardedOldData) {
            tag.putBoolean("DiscardedV2", true);
        }
        ListTag buildings = new ListTag();
        for (Building b : this.index.allBuildings()) {
            buildings.add(b.save());
        }
        buildings.addAll(this.keptBuildings);
        tag.put("Buildings", buildings);
        ListTag pieces = new ListTag();
        for (Piece p : this.index.allPieces()) {
            pieces.add(p.save());
        }
        pieces.addAll(this.keptPieces);
        tag.put("Pieces", pieces);
        return tag;
    }

    /**
     * Whether the store has the last word on piece {@code id}: the integrity check only drops a block's record of a
     * piece the store vouches for. Not vouched: ids older than an unreadable file, and entries kept unread.
     */
    public boolean vouchesFor(long id) {
        return id >= this.trustFrom && !this.keptPieceIds.contains(id);
    }

    /** Whether this store stands in for building data of an older format (see {@link #onOldFormat}). */
    public boolean holdsOldData() {
        return this.holdsOldData;
    }

    /**
     * Whether older building data was explicitly discarded here ({@code discardOldBuildings}), now or before a restart:
     * the only case where structure blocks still holding v2 data are removed. A stand-in (old, unreadable or missing
     * file) or a store that never saw the old file keeps them.
     */
    public boolean discardedOldData() {
        return this.discardedOldData;
    }

    /** Sets {@link #discardedOldData} (GameTests: the test world never holds a v2 file to discard). */
    public void markOldDataDiscarded(boolean discarded) {
        this.discardedOldData = discarded;
        this.setDirty();
    }

    /** False when some entries were kept unread: pieces may then miss supports that are only unreadable. */
    public boolean isComplete() {
        return this.keptPieces.isEmpty() && this.keptBuildings.isEmpty();
    }

    /**
     * Whether a building missing from the store is really gone: false for a stand-in of a lost file and while entries
     * are kept unread. Claims are only removed or unlinked for a missing building when this holds.
     */
    public boolean isTrusted() {
        return !this.isStandIn() && this.isComplete();
    }

    /** True once after loading: the first tick drops the pieces left without any support. */
    boolean takeCollapsePending() {
        boolean pending = this.collapsePending;
        this.collapsePending = false;
        return pending;
    }

    public long newPieceId() {
        this.setDirty();
        return this.nextPiece++;
    }

    public Building newBuilding(BlockPos origin) {
        Building b = new Building(this.nextBuilding++, origin);
        this.index.putBuilding(b);
        this.setDirty();
        return b;
    }

    public List<Piece> piecesOf(Building b) {
        List<Piece> out = new ArrayList<>(b.pieces.size());
        for (long id : b.pieces) {
            Piece p = this.index.piece(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /** Stability and variants of every piece; done once after loading. */
    public void recomputeAll() {
        Stability.Params params = BuildConfig.SPEC.isLoaded() ? BuildConfig.stability() : Stability.Params.DEFAULT;
        for (Building b : this.index.allBuildings()) {
            Stability.recompute(this.index, this.piecesOf(b), params);
        }
        for (Piece p : this.index.allPieces()) {
            p.variant = Variants.of(this.index, p);
        }
    }
}
