package fr.heraliz.crac.placable;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class PlacableHpData extends SafeSavedData {
    private static final String ID = "crac_placable_hp";
    private final Map<Long, Integer> hp = new HashMap<>();

    public PlacableHpData() {
    }

    public PlacableHpData(CompoundTag tag) {
        ListTag list = tag.getList("Entries", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            this.hp.put(t.getLong("Pos"), t.getInt("Hp"));
        }
    }

    public static PlacableHpData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacableHpData::new, () -> SafeSavedData.fresh(level, ID, new PlacableHpData()), ID);
    }

    public int getHp(BlockPos pos, int defaultHp) {
        return this.hp.getOrDefault(pos.asLong(), defaultHp);
    }

    public void setHp(BlockPos pos, int value) {
        this.hp.put(pos.asLong(), value);
        this.setDirty();
    }

    public void remove(BlockPos pos) {
        if (this.hp.remove(pos.asLong()) != null) {
            this.setDirty();
        }
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();

        for (Entry<Long, Integer> e : this.hp.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.putInt("Hp", e.getValue());
            list.add(t);
        }

        tag.put("Entries", list);
        return tag;
    }
}
