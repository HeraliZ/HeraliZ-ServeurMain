package fr.heraliz.crac.bases;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Carrières du monde des bases et blocs cassés qui doivent repousser, gardés AVEC le monde des bases
 * ({@code dimensions/crac/bases/data/crac_carrieres.dat}) : ils suivent ce monde s'il est copié ou remplacé.
 */
public final class CarrieresDonnees extends SafeSavedData {
    public static final String ID = "crac_carrieres";

    /** Un bloc cassé dans une carrière : il reprend {@code etat} à {@code quand} (heure en ms), si la place est libre. */
    public record Repousse(BlockPos pos, BlockState etat, long quand) {
    }

    private final Map<String, Carrieres.Zone> zones = new LinkedHashMap<>();
    private final Deque<Repousse> repousses = new ArrayDeque<>();
    /** Positions déjà notées dans {@link #repousses}. */
    private final java.util.Set<Long> notees = new java.util.HashSet<>();

    public static CarrieresDonnees get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CarrieresDonnees::load, () -> SafeSavedData.fresh(level, ID, new CarrieresDonnees()), ID);
    }

    public static String cle(String nom) {
        return nom.trim().toLowerCase(Locale.ROOT);
    }

    public Collection<Carrieres.Zone> zones() {
        return this.zones.values();
    }

    @Nullable
    public Carrieres.Zone zone(String nom) {
        return this.zones.get(cle(nom));
    }

    @Nullable
    public Carrieres.Zone zoneA(BlockPos pos) {
        for (Carrieres.Zone z : this.zones.values()) {
            if (z.contient(pos)) {
                return z;
            }
        }
        return null;
    }

    public void ajouter(Carrieres.Zone z) {
        this.zones.put(cle(z.nom()), z);
        this.setDirty();
    }

    @Nullable
    public Carrieres.Zone retirer(String nom) {
        Carrieres.Zone z = this.zones.remove(cle(nom));
        if (z != null) {
            this.setDirty();
        }
        return z;
    }

    public int enAttente() {
        return this.repousses.size();
    }

    /** Un même endroit n'est noté qu'une fois (le premier état relevé est celui de la nature). */
    public void noter(BlockPos pos, BlockState etat, long quand) {
        if (this.notees.add(pos.asLong())) {
            this.repousses.addLast(new Repousse(pos.immutable(), etat, quand));
            this.setDirty();
        }
    }

    /** Le premier bloc en attente (le plus ancien), ou null. */
    @Nullable
    public Repousse premier() {
        return this.repousses.peekFirst();
    }

    /** Retire le premier bloc en attente (repoussé, ou abandonné : la main l'a emporté). */
    public void oublierPremier() {
        Repousse r = this.repousses.pollFirst();
        if (r != null) {
            this.notees.remove(r.pos().asLong());
            this.setDirty();
        }
    }

    /** Le premier bloc en attente passe en dernier, à revoir à {@code quand} (un joueur était dessus). */
    public void repousserPremier(long quand) {
        Repousse r = this.repousses.pollFirst();
        if (r != null) {
            this.repousses.addLast(new Repousse(r.pos(), r.etat(), quand));
            this.setDirty();
        }
    }

    /** Tous les blocs en attente d'une zone (null : toutes) deviennent dus tout de suite. */
    public int avancer(@Nullable Carrieres.Zone zone) {
        int n = 0;
        List<Repousse> l = new ArrayList<>(this.repousses);
        this.repousses.clear();
        for (Repousse r : l) {
            if (zone == null || zone.contient(r.pos())) {
                this.repousses.addFirst(new Repousse(r.pos(), r.etat(), 0L));
                n++;
            } else {
                this.repousses.addLast(r);
            }
        }
        this.setDirty();
        return n;
    }

    public void marquer() {
        this.setDirty();
    }

    public static CarrieresDonnees load(CompoundTag tag) {
        CarrieresDonnees d = new CarrieresDonnees();
        ListTag zs = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zs.size(); i++) {
            CompoundTag t = zs.getCompound(i);
            CarriereType type = CarriereType.parId(t.getString("Type"));
            if (type == null || t.getString("Nom").isBlank()) {
                Crac.LOGGER.warn("[CRac] carrière illisible ignorée : {}", t);
                continue;
            }
            Carrieres.Zone z = new Carrieres.Zone(t.getString("Nom"), BlockPos.of(t.getLong("Centre")), t.getInt("Rayon"), type);
            d.zones.put(cle(z.nom()), z);
        }
        ListTag rs = tag.getList("Repousses", Tag.TAG_COMPOUND);
        for (int i = 0; i < rs.size(); i++) {
            CompoundTag t = rs.getCompound(i);
            try {
                BlockState etat = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), t.getCompound("Etat"));
                BlockPos pos = BlockPos.of(t.getLong("Pos"));
                if (!etat.isAir() && d.notees.add(pos.asLong())) {
                    d.repousses.addLast(new Repousse(pos, etat, t.getLong("Quand")));
                }
            } catch (RuntimeException e) {
                Crac.LOGGER.warn("[CRac] bloc de carrière illisible ignoré ({})", e.toString());
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag zs = new ListTag();
        for (Carrieres.Zone z : this.zones.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("Nom", z.nom());
            t.putLong("Centre", z.centre().asLong());
            t.putInt("Rayon", z.rayon());
            t.putString("Type", z.type().id);
            zs.add(t);
        }
        tag.put("Zones", zs);
        ListTag rs = new ListTag();
        List<Repousse> copie = new ArrayList<>(this.repousses);
        for (Repousse r : copie) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", r.pos().asLong());
            t.put("Etat", NbtUtils.writeBlockState(r.etat()));
            t.putLong("Quand", r.quand());
            rs.add(t);
        }
        tag.put("Repousses", rs);
        return tag;
    }
}
