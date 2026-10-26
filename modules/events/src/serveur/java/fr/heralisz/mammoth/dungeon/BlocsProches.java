package fr.heralisz.mammoth.dungeon;

import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Recherche d'un bloc précis dans une grande boîte (Gueule des Ancêtres : 81 × 33 × 81 blocs, piédestal du cœur : 97 × 13 ×
 * 97, prison de glace). Même résultat que le parcours complet de {@link BlockPos#betweenClosed} (même ordre, x puis y puis
 * z, égalités départagées pareil), mais les sections de 16 × 16 × 16 qui ne peuvent pas contenir ce bloc (palette de la
 * section) ne sont pas lues : avant, jusqu'à 216 000 blocs lus d'un coup sur le fil du serveur (pic de lag au réveil et à
 * la mort d'Ymirak).
 */
public final class BlocsProches {
   private BlocsProches() {
   }

   /** Premier bloc {@code bloc} trouvé dans la boîte [a, b] (bornes comprises), dans l'ordre de betweenClosed ; null : aucun. */
   @Nullable
   public static BlockPos premier(ServerLevel level, BlockPos a, BlockPos b, Block bloc) {
      return chercher(level, a, b, bloc, null);
   }

   /** Bloc {@code bloc} de la boîte [a, b] le plus proche selon {@code distance} (le premier en cas d'égalité) ; null : aucun. */
   @Nullable
   public static BlockPos plusProche(ServerLevel level, BlockPos a, BlockPos b, Block bloc, ToDoubleFunction<BlockPos> distance) {
      return chercher(level, a, b, bloc, distance);
   }

   @Nullable
   private static BlockPos chercher(ServerLevel level, BlockPos a, BlockPos b, Block bloc, @Nullable ToDoubleFunction<BlockPos> distance) {
      int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
      int x1 = Math.max(a.getX(), b.getX()), y1 = Math.max(a.getY(), b.getY()), z1 = Math.max(a.getZ(), b.getZ());
      int sx0 = x0 >> 4, sy0 = y0 >> 4, sz0 = z0 >> 4;
      int nx = (x1 >> 4) - sx0 + 1, ny = (y1 >> 4) - sy0 + 1, nz = (z1 >> 4) - sz0 + 1;
      // sections qui peuvent contenir le bloc (hors du monde en hauteur : vide, comme getBlockState)
      boolean[] peut = new boolean[nx * ny * nz];
      java.util.function.Predicate<BlockState> test = s -> s.is(bloc);
      boolean aucune = true;
      for (int ix = 0; ix < nx; ix++) {
         for (int iz = 0; iz < nz; iz++) {
            LevelChunk ch = level.getChunk(sx0 + ix, sz0 + iz);
            LevelChunkSection[] secs = ch.getSections();
            for (int iy = 0; iy < ny; iy++) {
               int idx = level.getSectionIndexFromSectionY(sy0 + iy);
               if (idx < 0 || idx >= secs.length) continue;
               LevelChunkSection sec = secs[idx];
               if (sec == null || sec.hasOnlyAir() || !sec.getStates().maybeHas(test)) continue;
               peut[(ix * ny + iy) * nz + iz] = true;
               aucune = false;
            }
         }
      }
      if (aucune) return null;
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      BlockPos best = null;
      double bd = Double.MAX_VALUE;
      for (int z = z0; z <= z1; z++) {
         int iz = (z >> 4) - sz0;
         for (int y = y0; y <= y1; y++) {
            int iy = (y >> 4) - sy0;
            for (int x = x0; x <= x1; x++) {
               if (!peut[(((x >> 4) - sx0) * ny + iy) * nz + iz]) continue;
               p.set(x, y, z);
               if (!level.getBlockState(p).is(bloc)) continue;
               if (distance == null) return p.immutable();
               double d = distance.applyAsDouble(p);
               if (d < bd) {
                  bd = d;
                  best = p.immutable();
               }
            }
         }
      }
      return best;
   }
}
