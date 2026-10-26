package fr.heralisz.clans.territory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SavedData du monde « heralisz_clans_territoires » : un {@link EtatTerritoire} par identifiant de zone. Les zones
 * elles-memes (centre, rayon, drapeau) viennent de zones.json ; un etat dont la zone n'existe plus est garde (si la
 * zone revient, son proprietaire aussi) mais ignore.
 * <p>NBT : {@code {zones: [{id, proprio?, captureLe, prochainRavitaillement, protegeeJusque, progression, capteur?}]}}.
 */
public final class TerritoireData extends SavedData {
    public static final String NOM = "heralisz_clans_territoires";

    private final Map<String, EtatTerritoire> etats = new LinkedHashMap<>();

    public TerritoireData() {
    }

    public static TerritoireData load(CompoundTag tag) {
        TerritoireData d = new TerritoireData();
        ListTag l = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            String id = t.getString("id");
            if (!id.isEmpty()) d.etats.put(id, EtatTerritoire.load(t));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<String, EtatTerritoire> e : etats.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putString("id", e.getKey());
            l.add(t);
        }
        tag.put("zones", l);
        return tag;
    }

    /** Etat d'une zone, cree libre au besoin. */
    public EtatTerritoire etat(String zoneId) {
        return etats.computeIfAbsent(zoneId, k -> {
            setDirty();
            return new EtatTerritoire();
        });
    }

    public Map<String, EtatTerritoire> tous() {
        return etats;
    }

    public void retirer(String zoneId) {
        if (etats.remove(zoneId) != null) setDirty();
    }
}
