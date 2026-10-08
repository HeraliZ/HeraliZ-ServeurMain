package fr.heraliz.crates.loot;

import fr.heraliz.crates.crate.CrateType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recompenses gagnees par un joueur deconnecte avant la fin de l'animation :
 * elles lui sont donnees a sa prochaine connexion.
 */
public class PendingRewards extends SavedData {
    private static final String NAME = "heralizcrates_pending";
    private final Map<UUID, ListTag> pending = new HashMap<>();

    public static PendingRewards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PendingRewards::load, PendingRewards::new, NAME);
    }

    public static PendingRewards load(CompoundTag tag) {
        PendingRewards p = new PendingRewards();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            try {
                p.pending.put(UUID.fromString(key), players.getList(key, Tag.TAG_COMPOUND));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, ListTag> e : pending.entrySet()) {
            players.put(e.getKey().toString(), e.getValue());
        }
        tag.put("Players", players);
        return tag;
    }

    /** Ajoute une recompense en attente et retourne l'etiquette enregistree (pour la retirer ensuite avec remove). */
    public CompoundTag add(UUID player, CompoundTag reward, CrateType type) {
        CompoundTag t = reward.copy();
        t.putString("crate", type.id);
        pending.computeIfAbsent(player, k -> new ListTag()).add(t);
        setDirty();
        return t;
    }

    /** Retire une recompense precise (celle retournee par add) ; false si elle a deja ete donnee. */
    public boolean remove(UUID player, CompoundTag stored) {
        ListTag list = pending.get(player);
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == stored) {
                list.remove(i);
                if (list.isEmpty()) pending.remove(player);
                setDirty();
                return true;
            }
        }
        return false;
    }

    public List<CompoundTag> take(UUID player) {
        ListTag list = pending.remove(player);
        List<CompoundTag> out = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i));
            setDirty();
        }
        return out;
    }
}
