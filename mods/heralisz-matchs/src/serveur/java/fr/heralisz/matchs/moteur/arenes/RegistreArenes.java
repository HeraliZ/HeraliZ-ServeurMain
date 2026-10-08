package fr.heralisz.matchs.moteur.arenes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Registre des instances d'arenes posees (SavedData du monde principal, fichier
 * {@code data/heralisz_matchs_arenes.dat}) : carte, dimension, rang dans la grille, origine, empreinte de la
 * structure, pose terminee ou non. Une entree d'une ancienne version de la carte reste inscrite (marquee obsolete) pour
 * que sa case ne soit jamais reutilisee. Fil serveur.
 */
public final class RegistreArenes extends SavedData {
    public static final String NOM = "heralisz_matchs_arenes";

    /** Entree du registre (modifiable par le moteur seulement). */
    public static final class Entree {
        public String carte;
        public String dim;
        public int index;
        public BlockPos origine;
        public long empreinte;
        public boolean prete;
        public boolean obsolete;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("carte", carte);
            t.putString("dim", dim);
            t.putInt("index", index);
            t.putInt("x", origine.getX());
            t.putInt("y", origine.getY());
            t.putInt("z", origine.getZ());
            t.putLong("empreinte", empreinte);
            t.putBoolean("prete", prete);
            t.putBoolean("obsolete", obsolete);
            return t;
        }

        static Entree load(CompoundTag t) {
            Entree e = new Entree();
            e.carte = t.getString("carte");
            e.dim = t.getString("dim");
            e.index = t.getInt("index");
            e.origine = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            e.empreinte = t.getLong("empreinte");
            e.prete = t.getBoolean("prete");
            e.obsolete = t.getBoolean("obsolete");
            return e;
        }
    }

    public final List<Entree> entrees = new ArrayList<>();

    public static RegistreArenes de(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(RegistreArenes::load, RegistreArenes::new, NOM);
    }

    private static RegistreArenes load(CompoundTag t) {
        RegistreArenes r = new RegistreArenes();
        ListTag l = t.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) r.entrees.add(Entree.load(l.getCompound(i)));
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Entree e : entrees) l.add(e.save());
        t.put("instances", l);
        return t;
    }

    /** Prochain rang libre de la grille dans cette dimension. */
    public int prochainIndex(String dim) {
        int max = -1;
        for (Entree e : entrees) if (e.dim.equals(dim)) max = Math.max(max, e.index);
        return max + 1;
    }

    public Entree entree(String dim, int index) {
        for (Entree e : entrees) if (e.dim.equals(dim) && e.index == index) return e;
        return null;
    }
}
