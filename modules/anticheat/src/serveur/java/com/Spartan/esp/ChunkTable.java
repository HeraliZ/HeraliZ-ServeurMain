package com.Spartan.esp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.level.ChunkEvent;

/**
 * Sections des chunks chargés de chaque monde, lisibles depuis les threads de
 * l'anti-ESP (la table des chunks de vanilla ne l'est pas). Tenue à jour par les
 * événements de chargement / déchargement, émis sur le thread serveur.
 *
 * Lire un bloc depuis un autre thread pendant que le serveur le modifie donne
 * l'ancien ou le nouvel état (jamais un état incohérent exploité ici : une
 * erreur de lecture rend la paire visible).
 */
final class ChunkTable {
    private static final Map<ServerLevel, Map<Long, LevelChunkSection[]>> LEVELS = new ConcurrentHashMap<>();

    private ChunkTable() {
    }

    static Map<Long, LevelChunkSection[]> of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
    }

    static void onLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            of(level).put(chunk.getPos().toLong(), chunk.getSections());
        }
    }

    static void onUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            of(level).remove(chunk.getPos().toLong());
        }
    }

    static void clear() {
        LEVELS.clear();
    }
}
