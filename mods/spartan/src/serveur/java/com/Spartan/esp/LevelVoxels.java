package com.Spartan.esp;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Lecture des blocs d'un monde serveur pour le Raycaster, utilisable depuis un
 * thread de l'anti-ESP : sections lues via ChunkTable, occultation via la table
 * immuable d'Occluders, petits caches locaux (chunks récents, dernier état).
 * Une instance par thread. Un chunk non chargé est transparent.
 */
final class LevelVoxels implements Raycaster.Voxels {
    private static final int CACHE = 4;

    private Map<Long, LevelChunkSection[]> chunks;
    private Reference2ObjectOpenHashMap<BlockState, double[]> table;
    private int minSection;
    private int sectionCount;
    private final long[] keys = new long[CACHE];
    private final LevelChunkSection[][] values = new LevelChunkSection[CACHE][];
    private int next;
    private long curKey;
    private LevelChunkSection[] cur;
    private BlockState lastState;
    private double[] lastOcc;

    /** Monde des prochains rayons (à appeler avant chaque paire). */
    void use(ServerLevel level, int minSection, int sectionCount) {
        Map<Long, LevelChunkSection[]> c = ChunkTable.of(level);
        Reference2ObjectOpenHashMap<BlockState, double[]> t = Occluders.table();
        if (c != chunks || t != table) {
            chunks = c;
            table = t;
            this.minSection = minSection;
            this.sectionCount = sectionCount;
            reset();
        }
    }

    /** Oublie les caches (début de lot : des chunks ont pu être déchargés). */
    void reset() {
        for (int i = 0; i < CACHE; i++) {
            keys[i] = Long.MIN_VALUE;
            values[i] = null;
        }
        curKey = Long.MIN_VALUE;
        cur = null;
        lastState = null;
        lastOcc = null;
    }

    @Override
    public double[] at(int x, int y, int z) {
        int sy = (y >> 4) - minSection;
        if (sy < 0 || sy >= sectionCount) {
            return null;
        }
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        if (key != curKey) {
            curKey = key;
            cur = chunk(key);
        }
        if (cur == null || sy >= cur.length) {
            return null;
        }
        LevelChunkSection section = cur[sy];
        if (section == null || section.hasOnlyAir()) {
            return null;
        }
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state == lastState) {
            return lastOcc;
        }
        double[] occ = state.isAir() ? null : table.get(state);
        lastState = state;
        lastOcc = occ;
        return occ;
    }

    private LevelChunkSection[] chunk(long key) {
        for (int i = 0; i < CACHE; i++) {
            if (keys[i] == key) {
                return values[i];
            }
        }
        LevelChunkSection[] s = chunks.get(key);
        keys[next] = key;
        values[next] = s;
        next = (next + 1) % CACHE;
        return s;
    }
}
