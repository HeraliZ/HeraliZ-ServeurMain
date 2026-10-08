package fr.heralisz.sylvorn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le sol sous un point, cherché localement. Les boss vivent dans des donjons souterrains : la carte des hauteurs du monde
 * (Heightmap) y donne la surface, des dizaines de blocs AU-DESSUS de la salle (tirs visés dans le plafond, ver qui sortait
 * à l'air libre, effets posés hors de l'arène). On descend donc depuis le point de référence jusqu'au premier bloc solide.
 */
public final class Ground {
   private Ground() {}

   /** Hauteur du sol sous (x, z), en partant de yRef (pieds d'un joueur, du boss, point d'impact...). */
   public static double y(Level level, double x, double yRef, double z) {
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(yRef + 1.5), Mth.floor(z));
      for (int i = 0; i < 6 && solid(level, p); i++) p.move(Direction.UP);
      for (int i = 0; i < 48; i++) {
         p.move(Direction.DOWN);
         if (solid(level, p)) {
            VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
            return p.getY() + (s.isEmpty() ? 1.0 : s.max(Direction.Axis.Y));
         }
      }
      return yRef;
   }

   private static boolean solid(Level level, BlockPos p) {
      BlockState st = level.getBlockState(p);
      return !st.getCollisionShape(level, p).isEmpty() && !st.is(BlockTags.LEAVES);
   }
}
