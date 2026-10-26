package fr.heraliz.crac.raid;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Raid blocks of one dimension, saved with the world so a restart or a crash does not lift them. End times are game
 * times, which only advance while the server runs. Each entry also keeps the claim zone it was started in: the zone
 * stays blocked even when the claim or its group disappears, so breaking or re-placing the Claim Block or recreating
 * the group does not end it.
 */
public final class RaidBlockData extends SafeSavedData {
    public static final String ID = "crac_raidblocks";

    /** The raid block of a group, and the claim zone (box around {@code center}) it covers. */
    public static final class Entry {
        public final UUID groupId;
        public long endTick;
        public BlockPos center;
        public int radius;
        public int yRadius;

        public Entry(UUID groupId, long endTick, BlockPos center, int radius, int yRadius) {
            this.groupId = groupId;
            this.endTick = endTick;
            this.center = center.immutable();
            this.radius = radius;
            this.yRadius = yRadius;
        }

        public boolean activeAt(long now) {
            return this.endTick > now;
        }

        /** Whether the block lies in the zone (the claim box when the raid block started). */
        public boolean covers(BlockPos pos) {
            return Math.abs(pos.getX() - this.center.getX()) <= this.radius
                && Math.abs(pos.getZ() - this.center.getZ()) <= this.radius
                && Math.abs(pos.getY() - this.center.getY()) <= this.yRadius;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static RaidBlockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RaidBlockData::load, () -> SafeSavedData.fresh(level, ID, new RaidBlockData()), ID);
    }

    @Nullable
    public Entry get(UUID groupId) {
        return this.entries.get(groupId);
    }

    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(this.entries.values());
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** Starts or extends the group's raid block; the zone follows the claim's current size. */
    public void put(UUID groupId, long endTick, BlockPos center, int radius, int yRadius) {
        Entry e = this.entries.get(groupId);
        if (e == null) {
            this.entries.put(groupId, new Entry(groupId, endTick, center, radius, yRadius));
        } else {
            e.endTick = Math.max(e.endTick, endTick);
            e.center = center.immutable();
            e.radius = radius;
            e.yRadius = yRadius;
        }
        this.setDirty();
    }

    /** Extends a raid block that is still running (more explosions in its zone). */
    public void extend(Entry e, long endTick) {
        if (endTick > e.endTick) {
            e.endTick = endTick;
            this.setDirty();
        }
    }

    public boolean remove(UUID groupId) {
        if (this.entries.remove(groupId) != null) {
            this.setDirty();
            return true;
        }
        return false;
    }

    /** Drops the entries that ended; returns whether any was dropped. */
    public boolean purge(long now) {
        if (this.entries.values().removeIf(e -> !e.activeAt(now))) {
            this.setDirty();
            return true;
        }
        return false;
    }

    public void clear() {
        if (!this.entries.isEmpty()) {
            this.entries.clear();
            this.setDirty();
        }
    }

    public static RaidBlockData load(CompoundTag tag) {
        RaidBlockData data = new RaidBlockData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Group")) continue;
            Entry e = new Entry(t.getUUID("Group"), t.getLong("End"), BlockPos.of(t.getLong("Center")), t.getInt("Radius"), t.getInt("YRadius"));
            data.entries.put(e.groupId, e);
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : this.entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Group", e.groupId);
            t.putLong("End", e.endTick);
            t.putLong("Center", e.center.asLong());
            t.putInt("Radius", e.radius);
            t.putInt("YRadius", e.yRadius);
            list.add(t);
        }
        tag.put("Entries", list);
        return tag;
    }
}
