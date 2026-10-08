package fr.heraliz.casino.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gains (en jetons) d'un joueur deconnecte avant la fin d'une partie : verses a sa prochaine connexion.
 */
public class PendingPayouts extends SavedData {
    private static final String NAME = "heralizcasino_pending";
    private final Map<UUID, Long> pending = new HashMap<>();

    public static PendingPayouts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PendingPayouts::load, PendingPayouts::new, NAME);
    }

    public void add(UUID player, long amount) {
        if (amount <= 0) return;
        pending.merge(player, amount, Long::sum);
        setDirty();
    }

    public long take(UUID player) {
        Long v = pending.remove(player);
        if (v != null) setDirty();
        return v == null ? 0 : v;
    }

    public static PendingPayouts load(CompoundTag tag) {
        PendingPayouts p = new PendingPayouts();
        CompoundTag players = tag.getCompound("Players");
        for (String k : players.getAllKeys()) {
            try {
                p.pending.put(UUID.fromString(k), players.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Long> e : pending.entrySet()) players.putLong(e.getKey().toString(), e.getValue());
        tag.put("Players", players);
        return tag;
    }
}
