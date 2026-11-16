package fr.heraliz.recolte.serveur;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les places des ressources de la carte (A_FAIRE §6, « Apparition »), gardées dans les données de l'overworld
 * ({@code <monde>/data/heraliz_recolte.dat}) : pour chaque ressource voulue, sa sorte, sa variante (espèce d'un arbre),
 * sa position, son état (en attente ou posée), l'UUID de son entité et l'heure (temps réel) à partir de laquelle elle peut
 * réapparaître. Aucune entité n'est cherchée ici : c'est la liste que le serveur suit ({@link Apparition}). Fil du
 * serveur seulement.
 */
final class Places extends SavedData {
    static final String NOM = "heraliz_recolte";
    /** États d'une place : en attente (pas encore posée, ou récoltée et à reposer ailleurs), posée (son entité est là). */
    static final byte ATTENTE = 0, POSEE = 1;

    static final class Place {
        /** {@link Reglages#ARBRE}, {@link Reglages#PIERRE}, {@link Reglages#METAL} ou {@link Reglages#RARE}. */
        int sorte;
        /** Espèce d'un arbre (0 bouleau, 1 épicéa) ; 0 pour un gisement. */
        int variante;
        /** Bloc de la place (y : hauteur des pieds de l'entité une fois posée). */
        int x, y, z;
        byte etat = ATTENTE;
        @Nullable
        UUID entite;
        /** Heure (temps réel, ms) à partir de laquelle elle peut être posée. */
        long prete;
        /** Heure (temps réel, ms) où cette position a été tirée. */
        long depuis;
        /** Posée par le staff (/recolte placer) : en plus du compte, retirée de la liste une fois récoltée. */
        boolean bonus;
        /** Heure où son entité a été vue absente de son coin de carte chargé (0 : pas vue absente) ; pas sauvegardée. */
        long absenteDepuis;
    }

    private final List<Place> liste = new ArrayList<>();
    private final Map<UUID, Place> parEntite = new HashMap<>();

    @Nullable
    private static Places cache;
    @Nullable
    private static MinecraftServer cacheServeur;

    /** Les places de ce serveur (lues dans les données de l'overworld à la première demande). */
    static Places de(MinecraftServer s) {
        Places p = cache;
        if (p == null || cacheServeur != s) {
            p = s.overworld().getDataStorage().computeIfAbsent(Places::lire, Places::new, NOM);
            cache = p;
            cacheServeur = s;
        }
        return p;
    }

    /** Arrêt du serveur. */
    static void oublier() {
        cache = null;
        cacheServeur = null;
    }

    /** Toutes les places (ne pas en ajouter ni en retirer directement : {@link #nouvelle}, {@link #retirer}). */
    List<Place> liste() {
        return liste;
    }

    @Nullable
    Place parEntite(UUID entite) {
        return parEntite.get(entite);
    }

    Place nouvelle(int sorte, int variante, boolean bonus) {
        Place p = new Place();
        p.sorte = sorte;
        p.variante = variante;
        p.bonus = bonus;
        liste.add(p);
        setDirty();
        return p;
    }

    /** La place est posée : son entité a cet UUID, ses pieds sont à la hauteur y. */
    void poser(Place p, UUID entite, int y) {
        if (p.entite != null) {
            parEntite.remove(p.entite);
        }
        p.etat = POSEE;
        p.entite = entite;
        p.y = y;
        p.absenteDepuis = 0L;
        parEntite.put(entite, p);
        setDirty();
    }

    /** L'entité de cette place n'est plus là : la place repasse en attente (null si cet UUID n'a pas de place). */
    @Nullable
    Place liberer(UUID entite) {
        Place p = parEntite.remove(entite);
        if (p == null) {
            return null;
        }
        p.etat = ATTENTE;
        p.entite = null;
        p.absenteDepuis = 0L;
        setDirty();
        return p;
    }

    void retirer(Place p) {
        liste.remove(p);
        if (p.entite != null) {
            parEntite.remove(p.entite);
        }
        setDirty();
    }

    void vider() {
        liste.clear();
        parEntite.clear();
        setDirty();
    }

    /** Places de cette sorte, sans celles du staff. */
    int compter(int sorte) {
        int n = 0;
        for (Place p : liste) {
            if (p.sorte == sorte && !p.bonus) {
                n++;
            }
        }
        return n;
    }

    /**
     * Le point (x, z) est assez loin des autres places (posées ou en attente) : l'écart de sa sorte avec celles de la même
     * sorte, {@code ecart_autres} avec toutes.
     */
    boolean loinDesAutres(Place p, int x, int z, Reglages r) {
        long memeSorte = r.ressource(p.sorte).ecart(), autres = r.ecartAutres;
        memeSorte *= memeSorte;
        autres *= autres;
        for (Place q : liste) {
            if (q == p) {
                continue;
            }
            long dx = q.x - x, dz = q.z - z, d2 = dx * dx + dz * dz;
            if (d2 < autres || (q.sorte == p.sorte && d2 < memeSorte)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ sauvegarde
    private static Places lire(CompoundTag tag) {
        Places pl = new Places();
        ListTag l = tag.getList("Places", Tag.TAG_COMPOUND);
        List<String> sortes = Arrays.asList(Reglages.SORTES);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            int sorte = sortes.indexOf(c.getString("Sorte"));
            if (sorte < 0) {
                continue;
            }
            Place p = new Place();
            p.sorte = sorte;
            p.variante = c.getInt("Variante");
            p.x = c.getInt("X");
            p.y = c.getInt("Y");
            p.z = c.getInt("Z");
            p.prete = c.getLong("Prete");
            p.depuis = c.getLong("Depuis");
            p.bonus = c.getBoolean("Bonus");
            if (c.getBoolean("Posee") && c.hasUUID("Entite")) {
                p.etat = POSEE;
                p.entite = c.getUUID("Entite");
                pl.parEntite.put(p.entite, p);
            }
            pl.liste.add(p);
        }
        return pl;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Place p : liste) {
            CompoundTag c = new CompoundTag();
            c.putString("Sorte", Reglages.SORTES[p.sorte]);
            c.putInt("Variante", p.variante);
            c.putInt("X", p.x);
            c.putInt("Y", p.y);
            c.putInt("Z", p.z);
            c.putBoolean("Posee", p.etat == POSEE);
            if (p.entite != null) {
                c.putUUID("Entite", p.entite);
            }
            c.putLong("Prete", p.prete);
            c.putLong("Depuis", p.depuis);
            if (p.bonus) {
                c.putBoolean("Bonus", true);
            }
            l.add(c);
        }
        tag.put("Places", l);
        return tag;
    }
}
