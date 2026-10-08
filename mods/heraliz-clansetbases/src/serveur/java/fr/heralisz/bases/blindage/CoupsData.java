package fr.heralisz.bases.blindage;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

/**
 * Coups de C4 encaisses par les blocs proteges d'une dimension (BASES_SPEC §5) : SavedData
 * {@code heralisz_bases_coups} du niveau (persistante, sauvee avec le monde). Cle = position emballee
 * ({@link BlockPos#asLong()}) du bloc « cle » (moitie basse d'une porte, controleur d'une porte HBM). Chaque entree
 * garde l'id du bloc touche : si le bloc a change (remplace, retire), l'entree est perimee et oubliee. Index par chunk
 * pour renvoyer les fissures aux joueurs qui chargent le chunk.
 */
public final class CoupsData extends SavedData {
    public static final String NOM = "heralisz_bases_coups";

    /** Coups d'un bloc : id du bloc touche, nombre de coups, dernier coup (epoch ms). */
    public static final class Coup {
        public final String bloc;
        public int coups;
        public long le;

        public Coup(String bloc, int coups, long le) {
            this.bloc = bloc;
            this.coups = coups;
            this.le = le;
        }
    }

    private final Long2ObjectOpenHashMap<Coup> coups = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> parChunk = new Long2ObjectOpenHashMap<>();

    public static CoupsData de(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CoupsData::charger, CoupsData::new, NOM);
    }

    private static long chunk(long pos) {
        return ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
    }

    @Nullable
    public Coup get(long pos) {
        return coups.get(pos);
    }

    public void mettre(long pos, Coup c) {
        coups.put(pos, c);
        parChunk.computeIfAbsent(chunk(pos), k -> new LongOpenHashSet()).add(pos);
        setDirty();
    }

    public void retirer(long pos) {
        if (coups.remove(pos) == null) return;
        long ch = chunk(pos);
        LongOpenHashSet s = parChunk.get(ch);
        if (s != null) {
            s.remove(pos);
            if (s.isEmpty()) parChunk.remove(ch);
        }
        setDirty();
    }

    /** Positions endommagees d'un chunk (null si aucune). Ne pas modifier pendant le parcours. */
    @Nullable
    public LongSet duChunk(long chunkPos) {
        return parChunk.get(chunkPos);
    }

    public boolean vide() {
        return coups.isEmpty();
    }

    public int taille() {
        return coups.size();
    }

    public Long2ObjectMap<Coup> tous() {
        return coups;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Long2ObjectMap.Entry<Coup> e : coups.long2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getLongKey());
            c.putString("b", e.getValue().bloc);
            c.putInt("c", e.getValue().coups);
            c.putLong("t", e.getValue().le);
            l.add(c);
        }
        tag.put("coups", l);
        return tag;
    }

    private static CoupsData charger(CompoundTag tag) {
        CoupsData d = new CoupsData();
        ListTag l = tag.getList("coups", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (c.getInt("c") <= 0) continue;
            long p = c.getLong("p");
            d.coups.put(p, new Coup(c.getString("b"), c.getInt("c"), c.getLong("t")));
            d.parChunk.computeIfAbsent(chunk(p), k -> new LongOpenHashSet()).add(p);
        }
        return d;
    }
}
