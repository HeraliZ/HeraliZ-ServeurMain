package fr.heraliz.crac.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacablePositionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Outils du staff pour vider une zone d'une base : pièces de construction et objets posés ({@code /base effacer},
 * fin du tutoriel, {@code /wipe}). La console admin de CRac (touche à choisir, mot de passe de {@code crac-admin.toml}), jamais
 * activée sur HeraliZ, est retirée depuis le 02/11 : le staff garde ses commandes ({@code /base effacer},
 * {@code /protection}, {@code /coffres}, {@code /clan admin}…) et ses droits d'opérateur.
 */
public final class AdminManager {
    /** Largest zone (in blocks) whose pieces are looked up block by block; beyond it the level's pieces are walked. */
    private static final long BLOCK_LOOKUP_LIMIT = 1_000_000L;

    private AdminManager() {
    }

    /**
     * The pieces with a block between {@code min} and {@code max} (inclusive). A small zone is looked up block by block;
     * a large one (the config allows claims of over 500 million blocks) walks the level's pieces instead.
     */
    public static Collection<Piece> piecesIn(ServerLevel level, BlockPos min, BlockPos max) {
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume <= BLOCK_LOOKUP_LIMIT) {
            return StructureService.piecesIn(level, new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
        }
        List<Piece> out = new ArrayList<>();
        for (Piece p : StructureStore.get(level).index.allPieces()) {
            for (BlockPos pos : PieceIndex.blocksOf(p)) {
                if (pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Removes every placable block between {@code min} and {@code max} (inclusive) and returns how many. Chunk sections
     * whose palette holds no placable are skipped, and an unloaded chunk is only loaded when a player placed a placable
     * in it, so even the largest claim the config allows is wiped without a long freeze.
     */
    public static int removePlacables(ServerLevel level, BlockPos min, BlockPos max) {
        Set<Long> placedChunks = new HashSet<>();
        for (long packed : PlacablePositionData.get(level).snapshot()) {
            BlockPos p = BlockPos.of(packed);
            if (p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) {
                placedChunks.add(ChunkPos.asLong(SectionPos.blockToSectionCoord(p.getX()), SectionPos.blockToSectionCoord(p.getZ())));
            }
        }
        List<BlockPos> found = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(min.getX()); cx <= SectionPos.blockToSectionCoord(max.getX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(min.getZ()); cz <= SectionPos.blockToSectionCoord(max.getZ()); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    if (!placedChunks.contains(ChunkPos.asLong(cx, cz))) {
                        continue;
                    }
                    chunk = level.getChunk(cx, cz);
                }
                collectPlacables(chunk, min, max, found);
            }
        }
        // collected first: removing one half of a bed or door also removes the other
        for (BlockPos pos : found) {
            if (PlacableHpManager.isTracked(level.getBlockState(pos))) {
                level.removeBlock(pos, false);
            }
            PlacableHpManager.clear(level, pos);
        }
        return found.size();
    }

    private static void collectPlacables(LevelChunk chunk, BlockPos min, BlockPos max, List<BlockPos> out) {
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int x0 = Math.max(min.getX(), baseX);
        int x1 = Math.min(max.getX(), baseX + 15);
        int z0 = Math.max(min.getZ(), baseZ);
        int z1 = Math.min(max.getZ(), baseZ + 15);
        for (int sy = SectionPos.blockToSectionCoord(min.getY()); sy <= SectionPos.blockToSectionCoord(max.getY()); sy++) {
            int index = chunk.getSectionIndexFromSectionY(sy);
            if (index < 0 || index >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(PlacableHpManager::isTracked)) {
                continue;
            }
            int y0 = Math.max(min.getY(), SectionPos.sectionToBlockCoord(sy));
            int y1 = Math.min(max.getY(), SectionPos.sectionToBlockCoord(sy) + 15);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        if (PlacableHpManager.isTracked(section.getBlockState(x & 15, y & 15, z & 15))) {
                            out.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
    }
}
