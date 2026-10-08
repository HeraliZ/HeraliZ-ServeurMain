package fr.heralisz.bases.monde;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registre des parcelles EN MONDE (SavedData {@code heralisz_bases_parcelles} de la dimension des bases) : parcelle ->
 * clan dont le bunker y est reellement pose (pose terminee). bases.json (global au serveur) dit quel clan possede
 * quelle parcelle ; ce registre dit ce que le monde contient vraiment : apres un wipe de la dimension il est vide, et
 * le serveur repose alors les bunkers (voir {@code server.BaseService#reconcilier}). Une parcelle « occupee » sans base
 * dans bases.json est effacee puis liberee.
 */
public final class ParcellesData extends SavedData {
    public static final String NOM = "heralisz_bases_parcelles";

    private final Map<Integer, UUID> posees = new HashMap<>();

    public static ParcellesData de(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ParcellesData::charger, ParcellesData::new, NOM);
    }

    private static ParcellesData charger(CompoundTag t) {
        ParcellesData d = new ParcellesData();
        ListTag l = t.getList("parcelles", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (c.hasUUID("clan")) d.posees.put(c.getInt("index"), c.getUUID("clan"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Map.Entry<Integer, UUID> e : posees.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("index", e.getKey());
            c.putUUID("clan", e.getValue());
            l.add(c);
        }
        t.put("parcelles", l);
        return t;
    }

    public Map<Integer, UUID> posees() {
        return Collections.unmodifiableMap(posees);
    }

    public boolean estPosee(int parcelle, UUID clan) {
        return clan.equals(posees.get(parcelle));
    }

    public boolean occupee(int parcelle) {
        return posees.containsKey(parcelle);
    }

    public void poser(int parcelle, UUID clan) {
        posees.put(parcelle, clan);
        setDirty();
    }

    public void liberer(int parcelle) {
        if (posees.remove(parcelle) != null) setDirty();
    }
}
