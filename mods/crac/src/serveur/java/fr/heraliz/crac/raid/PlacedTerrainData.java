package fr.heraliz.crac.raid;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Positions (per dimension) where a player put a terrain-type block ({@link RaidBlocks#TERRAIN}: dirt, stone, sand...).
 * Unlike natural terrain, raids may break those inside claims, so a dirt or stone shell is no raid shield. Kept up to
 * date by {@link PlacedTerrainTracker}.
 */
public final class PlacedTerrainData extends SafeSavedData {
    private static final String ID = "crac_placed_terrain";
    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static PlacedTerrainData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacedTerrainData::load, () -> SafeSavedData.fresh(level, ID, new PlacedTerrainData()), ID);
    }

    public boolean contains(BlockPos pos) {
        return this.positions.contains(pos.asLong());
    }

    public void add(BlockPos pos) {
        if (this.positions.add(pos.asLong())) {
            this.setDirty();
        }
    }

    public boolean remove(BlockPos pos) {
        boolean removed = this.positions.remove(pos.asLong());
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /** Copie des positions (remise à zéro du serveur). */
    public long[] snapshot() {
        return this.positions.toLongArray();
    }

    public int size() {
        return this.positions.size();
    }

    public static PlacedTerrainData load(CompoundTag tag) {
        PlacedTerrainData data = new PlacedTerrainData();
        for (long l : tag.getLongArray("Pos")) {
            data.positions.add(l);
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.putLongArray("Pos", this.positions.toLongArray());
        return tag;
    }
}
