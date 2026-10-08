package fr.heraliz.crac.claim;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class ClaimData extends SafeSavedData {
    public static final String ID = "crac_claims";
    private final Map<UUID, Claim> byGroup = new HashMap<>();
    private final Map<Long, Claim> byAnchor = new HashMap<>();
    /** Entries that could not be read: written back unchanged instead of being lost at the next save. */
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static ClaimData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ClaimData::load, () -> create(level), ID);
    }

    /** No usable file: new claim data, or a stand-in for a file that could not be read or was lost. */
    private static ClaimData create(ServerLevel level) {
        ClaimData data = SafeSavedData.fresh(level, ID, new ClaimData());
        if (!data.isStandIn() && StructureStore.hasClaimLinks(level)) {
            // buildings linked to claims: a missing file was lost (a partial restore), this is no new world
            data.setStandIn(true);
            Crac.LOGGER.error("[CRac claims] {} is missing from {} although buildings are linked to claims: put it back with the server"
                + " stopped if you have it.", ID, level.dimension().location());
        }
        return data;
    }

    /**
     * Whether the level's claim data, read without being created, holds a claim, readable or not (assumed when its file
     * exists but cannot be read). For the building store's factory: a store missing while claims exist was lost.
     */
    public static boolean hasClaims(ServerLevel level) {
        ClaimData data = level.getDataStorage().get(ClaimData::load, ID);
        if (data == null) {
            return Files.exists(dataFile(level, ID));
        }
        return !data.byGroup.isEmpty() || !data.unreadable.isEmpty();
    }

    /** False when some entries were kept unread: a building may be linked to one of those claims. */
    public boolean isComplete() {
        return this.unreadable.isEmpty();
    }

    /**
     * Whether a claim missing from here is really gone: false for a stand-in of a lost file and while entries are kept
     * unread. Buildings are only unlinked, and only decay for lack of a claim, when this holds.
     */
    public boolean isTrusted() {
        return !this.isStandIn() && this.isComplete();
    }

    public Claim getByGroup(UUID groupId) {
        return this.byGroup.get(groupId);
    }

    public Claim getByAnchor(BlockPos anchor) {
        return this.byAnchor.get(anchor.asLong());
    }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(this.byGroup.values());
    }

    /**
     * The claim whose zone holds the block. Zones only overlap after the radius was raised in the config: the nearest
     * Claim Block then wins (ties by position), never the map's iteration order.
     */
    public Claim getAt(ServerLevel level, BlockPos pos) {
        Claim best = null;
        for (Claim c : this.byGroup.values()) {
            if (c.contains(pos) && (best == null || Claim.nearer(c.anchor(), best.anchor(), pos))) {
                best = c;
            }
        }
        return best;
    }

    public void add(Claim c) {
        this.byGroup.put(c.groupId(), c);
        this.byAnchor.put(c.anchor().asLong(), c);
        this.setDirty();
    }

    public boolean removeByAnchor(BlockPos anchor) {
        Claim c = this.byAnchor.remove(anchor.asLong());
        if (c == null) {
            return false;
        } else {
            this.byGroup.remove(c.groupId());
            this.setDirty();
            return true;
        }
    }

    public boolean removeByGroup(UUID groupId) {
        Claim c = this.byGroup.remove(groupId);
        if (c == null) {
            return false;
        } else {
            this.byAnchor.remove(c.anchor().asLong());
            this.setDirty();
            return true;
        }
    }

    public void markDirty() {
        this.setDirty();
    }

    public static ClaimData load(CompoundTag tag) {
        ClaimData data = new ClaimData();
        ListTag list = tag.getList("Claims", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                Claim c = Claim.load(t);
                data.byGroup.put(c.groupId(), c);
                data.byAnchor.put(c.anchor().asLong(), c);
            } catch (RuntimeException e) {
                Crac.LOGGER.error("[CRac] Unreadable claim entry, kept unchanged: {}", t, e);
                data.unreadable.add(t);
            }
        }

        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();

        for (Claim c : this.byGroup.values()) {
            list.add(c.save());
        }

        list.addAll(this.unreadable);
        tag.put("Claims", list);
        return tag;
    }
}
