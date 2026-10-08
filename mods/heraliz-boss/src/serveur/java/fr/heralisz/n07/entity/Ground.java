package fr.heralisz.n07.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sol sous un point (remplace la carte des hauteurs, qui donnait le toit dans une salle couverte) : on descend depuis un peu
 * au-dessus du point (marches jusqu'a ~2 blocs) jusqu'a 14 blocs plus bas et on prend la premiere surface sur laquelle on
 * peut se tenir. Au plus ~16 lectures de blocs par appel, aucun balayage du monde.
 */
public final class Ground {
   /** Marge au-dessus du point de depart (marches, dalles), et profondeur max de la recherche. */
   public static final double UP = 2.2, DOWN = 14;

   private Ground() {}

   /** Y du sol sous (x, y, z), ou y si rien n'est trouve (vide, puits). */
   public static double floorY(BlockGetter level, double x, double y, double z) {
      double top = y + UP;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(top), Mth.floor(z));
      int stop = Mth.floor(y - DOWN);
      boolean freeAbove = false;
      for (int cy = m.getY(); cy >= stop; cy--) {
         m.setY(cy);
         BlockState st = level.getBlockState(m);
         VoxelShape sh = st.getCollisionShape(level, m);
         if (sh.isEmpty()) {
            freeAbove = true;
            continue;
         }
         double surf = cy + sh.max(Direction.Axis.Y);
         // une surface sous le depart, avec de la place au-dessus (sinon on est dans un plafond ou une passerelle : on continue)
         if (surf <= top + 1e-3 && (freeAbove || surf < cy + 1 - 1e-3)) return surf;
         freeAbove = false;
      }
      return y;
   }

   public static Vec3 floor(BlockGetter level, Vec3 p) {
      return new Vec3(p.x, floorY(level, p.x, p.y, p.z), p.z);
   }
}
