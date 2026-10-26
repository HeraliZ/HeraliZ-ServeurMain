package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Marche qui garde la forme posée par la structure (droite) : les marches voisines d'orientations différentes (colimaçon,
 * dais) ne se raccordent pas en angles intérieurs / extérieurs, qui tordaient l'escalier.
 */
public class FixedStairBlock extends StairBlock {
   public FixedStairBlock(Supplier<BlockState> base, Properties p) {
      super(base, p);
   }

   @Override
   public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
      return s;
   }
}
