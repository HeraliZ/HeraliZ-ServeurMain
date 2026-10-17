package fr.heraliz.quetes.serveur;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Points de quête posés par le staff (baguette ou /quete point ajouter) : un nom de groupe (« caisse_jack »), un monde,
 * un bloc, un nom affiché facultatif (« Belcourt »). Gardés avec le monde ({@code data/heraliz_quetes_points.dat} du monde
 * principal) : ils suivront la copie finale de la carte. Aucun bloc n'est modifié.
 */
final class Points extends SavedData {
    static final String FICHIER = "heraliz_quetes_points";

    record Point(String id, String groupe, String monde, BlockPos pos, String nom) {
        String cleBloc() {
            return Points.cleBloc(monde, pos);
        }

        String nomOuId() {
            return nom == null || nom.isEmpty() ? id : nom;
        }
    }

    private final Map<String, Point> parId = new LinkedHashMap<>();
    private final Map<String, Point> parBloc = new HashMap<>();

    static Points de(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Points::charger, Points::new, FICHIER);
    }

    static String cleBloc(String monde, BlockPos p) {
        return monde + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ();
    }

    Point point(String id) {
        return parId.get(id);
    }

    Point auBloc(ResourceLocation monde, BlockPos p) {
        return parBloc.get(cleBloc(monde.toString(), p));
    }

    Collection<Point> tous() {
        return parId.values();
    }

    List<Point> groupe(String g) {
        List<Point> l = new ArrayList<>();
        for (Point p : parId.values()) if (p.groupe().equals(g)) l.add(p);
        return l;
    }

    /** Ajoute un point au groupe (identifiant groupe_1, groupe_2...) ; si le bloc a déjà un point, rend celui-là. */
    Point ajouter(String groupe, ResourceLocation monde, BlockPos pos, String nom) {
        groupe = groupe.toLowerCase(Locale.ROOT);
        Point deja = auBloc(monde, pos);
        if (deja != null) return deja;
        int n = 1;
        while (parId.containsKey(groupe + "_" + n)) n++;
        Point p = new Point(groupe + "_" + n, groupe, monde.toString(), pos.immutable(), nom == null ? "" : nom);
        parId.put(p.id(), p);
        parBloc.put(p.cleBloc(), p);
        setDirty();
        return p;
    }

    boolean retirer(String id) {
        Point p = parId.remove(id);
        if (p == null) return false;
        parBloc.remove(p.cleBloc());
        setDirty();
        return true;
    }

    boolean nommer(String id, String nom) {
        Point p = parId.get(id);
        if (p == null) return false;
        Point n = new Point(p.id(), p.groupe(), p.monde(), p.pos(), nom);
        parId.put(id, n);
        parBloc.put(n.cleBloc(), n);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Point p : parId.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", p.id());
            c.putString("Groupe", p.groupe());
            c.putString("Monde", p.monde());
            c.putInt("X", p.pos().getX());
            c.putInt("Y", p.pos().getY());
            c.putInt("Z", p.pos().getZ());
            c.putString("Nom", p.nom());
            l.add(c);
        }
        t.put("Points", l);
        return t;
    }

    private static Points charger(CompoundTag t) {
        Points ps = new Points();
        for (Tag x : t.getList("Points", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            Point p = new Point(c.getString("Id"), c.getString("Groupe"), c.getString("Monde"),
                    new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")), c.getString("Nom"));
            if (p.id().isEmpty()) continue;
            ps.parId.put(p.id(), p);
            ps.parBloc.put(p.cleBloc(), p);
        }
        return ps;
    }
}
