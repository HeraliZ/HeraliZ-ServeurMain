package fr.heraliz.carte.serveur;

import fr.heraliz.carte.Lieu;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recycleurs de la carte connus (19/11, demande du propriétaire : « un petit point sur la carte pour chaque recycleur,
 * posé tout seul : je ne sais plus où je les ai mis »), gardés AVEC LE MONDE ({@code data/heraliz_carte_recycleurs.dat} du
 * monde principal) : position de chaque machine ({@code heraliz_quetes:recycleur_carte}) par monde, et la recherche
 * automatique déjà faite ({@link RechercheRecycleurs}, une seule fois). Tenus à jour par {@link RecycleursCarte} ; fil
 * serveur seulement.
 * <p>Rangés par tronçon : au chargement d'un tronçon, savoir s'il contient un recycleur connu coûte deux lectures de
 * table, sans rien créer. La liste des repères envoyée aux joueurs est gardée telle quelle jusqu'au prochain changement.
 */
public final class RecycleursData extends SavedData {
    public static final String FICHIER = "heraliz_carte_recycleurs";
    /** Version de la recherche automatique : la monter la relance une fois, au démarrage suivant. */
    static final int RECHERCHE = 1;

    /** Une machine connue (liste du staff, fin de la recherche). */
    record Recycleur(ResourceKey<Level> monde, BlockPos pos) {
    }

    /** Par monde : tronçon ({@link ChunkPos#toLong}) vers les positions ({@link BlockPos#asLong}) de ses recycleurs. */
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<long[]>> parMonde = new HashMap<>();
    private int nombre;
    /** Version de la dernière recherche finie (0 : jamais) et sa date (ms). */
    private int recherche;
    private long rechercheLe;
    /** Repères envoyés aux joueurs (refaits au prochain changement). */
    @Nullable
    private List<Lieu> lieux;

    static RecycleursData de(MinecraftServer serveur) {
        return serveur.overworld().getDataStorage().computeIfAbsent(RecycleursData::charger, RecycleursData::new, FICHIER);
    }

    static long troncon(long pos) {
        return ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
    }

    int nombre() {
        return nombre;
    }

    /** Positions des recycleurs connus dans ce tronçon, ou null (le cas courant). */
    @Nullable
    long[] dansTroncon(ResourceKey<Level> monde, long troncon) {
        Long2ObjectOpenHashMap<long[]> m = parMonde.get(monde);
        return m == null ? null : m.get(troncon);
    }

    boolean contient(ResourceKey<Level> monde, long pos) {
        long[] ps = dansTroncon(monde, troncon(pos));
        if (ps == null) return false;
        for (long p : ps) if (p == pos) return true;
        return false;
    }

    /** Vrai si la machine n'était pas connue. */
    boolean ajouter(ResourceKey<Level> monde, long pos) {
        if (!inserer(monde, pos)) return false;
        changer();
        return true;
    }

    private boolean inserer(ResourceKey<Level> monde, long pos) {
        if (contient(monde, pos)) return false;
        Long2ObjectOpenHashMap<long[]> m = parMonde.computeIfAbsent(monde, k -> new Long2ObjectOpenHashMap<>());
        long t = troncon(pos);
        long[] ps = m.get(t);
        long[] n = ps == null ? new long[1] : Arrays.copyOf(ps, ps.length + 1);
        n[n.length - 1] = pos;
        m.put(t, n);
        nombre++;
        return true;
    }

    /** Vrai si la machine était connue. */
    boolean retirer(ResourceKey<Level> monde, long pos) {
        Long2ObjectOpenHashMap<long[]> m = parMonde.get(monde);
        if (m == null) return false;
        long t = troncon(pos);
        long[] ps = m.get(t);
        if (ps == null) return false;
        int i = 0;
        while (i < ps.length && ps[i] != pos) i++;
        if (i == ps.length) return false;
        if (ps.length == 1) {
            m.remove(t);
            if (m.isEmpty()) parMonde.remove(monde);
        } else {
            long[] n = new long[ps.length - 1];
            System.arraycopy(ps, 0, n, 0, i);
            System.arraycopy(ps, i + 1, n, i, ps.length - 1 - i);
            m.put(t, n);
        }
        nombre--;
        changer();
        return true;
    }

    private void changer() {
        lieux = null;
        setDirty();
    }

    /** Un repère par machine (nom vide : l'écran écrit « Recycleur »), dans son monde ; même liste jusqu'au prochain changement. */
    List<Lieu> lieux() {
        List<Lieu> l = lieux;
        if (l == null) {
            List<Lieu> n = new ArrayList<>(nombre);
            for (Recycleur r : tous()) {
                n.add(new Lieu("", Lieu.Type.RECYCLEUR, r.monde().location(), r.pos().getX(), r.pos().getZ()));
            }
            lieux = l = List.copyOf(n);
        }
        return l;
    }

    /** Toutes les machines connues, monde par monde, d'ouest en est puis du nord au sud. */
    List<Recycleur> tous() {
        List<Recycleur> out = new ArrayList<>(nombre);
        for (Map.Entry<ResourceKey<Level>, Long2ObjectOpenHashMap<long[]>> e : parMonde.entrySet()) {
            for (long[] ps : e.getValue().values()) for (long p : ps) out.add(new Recycleur(e.getKey(), BlockPos.of(p)));
        }
        out.sort(Comparator.comparing((Recycleur r) -> r.monde().location().toString())
                .thenComparingInt(r -> r.pos().getX()).thenComparingInt(r -> r.pos().getZ()).thenComparingInt(r -> r.pos().getY()));
        return out;
    }

    /** La recherche automatique de cette version a été faite jusqu'au bout. */
    boolean rechercheFaite() {
        return recherche >= RECHERCHE;
    }

    /** Date (ms) de la dernière recherche finie, 0 si aucune. */
    long rechercheLe() {
        return rechercheLe;
    }

    void rechercheFinie(long quand) {
        recherche = RECHERCHE;
        rechercheLe = quand;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("recherche", recherche);
        tag.putLong("recherche_le", rechercheLe);
        ListTag mondes = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Long2ObjectOpenHashMap<long[]>> e : parMonde.entrySet()) {
            int n = 0;
            for (long[] ps : e.getValue().values()) n += ps.length;
            long[] toutes = new long[n];
            int i = 0;
            for (long[] ps : e.getValue().values()) for (long p : ps) toutes[i++] = p;
            CompoundTag m = new CompoundTag();
            m.putString("monde", e.getKey().location().toString());
            m.putLongArray("positions", toutes);
            mondes.add(m);
        }
        tag.put("mondes", mondes);
        return tag;
    }

    public static RecycleursData charger(CompoundTag tag) {
        RecycleursData d = new RecycleursData();
        d.recherche = tag.getInt("recherche");
        d.rechercheLe = tag.getLong("recherche_le");
        ListTag mondes = tag.getList("mondes", Tag.TAG_COMPOUND);
        for (int i = 0; i < mondes.size(); i++) {
            CompoundTag m = mondes.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(m.getString("monde"));
            if (rl == null) continue;
            ResourceKey<Level> monde = ResourceKey.create(Registries.DIMENSION, rl);
            for (long p : m.getLongArray("positions")) d.inserer(monde, p);
        }
        return d;
    }
}
