package fr.heraliz.crac.placable;

import java.util.HashSet;
import java.util.Set;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class PlacablePositionData extends SafeSavedData {
    private static final String ID = "crac_placable_positions";
    private final Set<Long> positions = new HashSet<>();
    private final Set<Long> everClaimed = new HashSet<>();

    public PlacablePositionData() {
    }

    public PlacablePositionData(CompoundTag tag) {
        for (long l : tag.getLongArray("Pos")) {
            this.positions.add(l);
        }

        for (long l : tag.getLongArray("Claimed")) {
            this.everClaimed.add(l);
        }
    }

    public static PlacablePositionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacablePositionData::new, () -> SafeSavedData.fresh(level, ID, new PlacablePositionData()), ID);
    }

    public void add(BlockPos pos) {
        if (this.positions.add(pos.asLong())) {
            this.setDirty();
        }
    }

    public void remove(BlockPos pos) {
        boolean changed = this.positions.remove(pos.asLong());
        changed |= this.everClaimed.remove(pos.asLong());
        if (changed) {
            this.setDirty();
        }
    }

    public void markClaimed(BlockPos pos) {
        if (this.everClaimed.add(pos.asLong())) {
            this.setDirty();
        }
    }

    public boolean wasClaimed(BlockPos pos) {
        return this.everClaimed.contains(pos.asLong());
    }

    public long[] snapshot() {
        return this.positions.stream().mapToLong(Long::longValue).toArray();
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.putLongArray("Pos", this.snapshot());
        tag.putLongArray("Claimed", this.everClaimed.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
