package fr.heralisz.utils.spawn;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Points et zones d'arrivée de la borne de départ, posés en jeu par le staff (/depart point ..., /depart zone ...).
 * Gardés avec le monde de la carte (data/heraliz_depart.dat) : ils suivent la copie de la carte.
 * Point : une place exacte (pieds du joueur, regard). Zone : un disque (centre, rayon) où un sol sûr est cherché.
 */
public final class PointsDepart extends SavedData {
    private static final String NOM = "heraliz_depart";

    public record Point(double x, double y, double z, float lacet) {}

    public record Zone(int x, int z, int rayon) {}

    public final Map<String, Point> points = new LinkedHashMap<>();
    public final Map<String, Zone> zones = new LinkedHashMap<>();

    private PointsDepart() {}

    public static PointsDepart get(ServerLevel carte) {
        return carte.getDataStorage().computeIfAbsent(PointsDepart::lire, PointsDepart::new, NOM);
    }

    private static PointsDepart lire(CompoundTag t) {
        PointsDepart d = new PointsDepart();
        for (Tag x : t.getList("points", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) x;
            d.points.put(p.getString("nom"), new Point(p.getDouble("x"), p.getDouble("y"), p.getDouble("z"), p.getFloat("lacet")));
        }
        for (Tag x : t.getList("zones", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) x;
            d.zones.put(p.getString("nom"), new Zone(p.getInt("x"), p.getInt("z"), p.getInt("rayon")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag lp = new ListTag();
        points.forEach((nom, p) -> {
            CompoundTag c = new CompoundTag();
            c.putString("nom", nom);
            c.putDouble("x", p.x());
            c.putDouble("y", p.y());
            c.putDouble("z", p.z());
            c.putFloat("lacet", p.lacet());
            lp.add(c);
        });
        ListTag lz = new ListTag();
        zones.forEach((nom, z) -> {
            CompoundTag c = new CompoundTag();
            c.putString("nom", nom);
            c.putInt("x", z.x());
            c.putInt("z", z.z());
            c.putInt("rayon", z.rayon());
            lz.add(c);
        });
        t.put("points", lp);
        t.put("zones", lz);
        return t;
    }
}
