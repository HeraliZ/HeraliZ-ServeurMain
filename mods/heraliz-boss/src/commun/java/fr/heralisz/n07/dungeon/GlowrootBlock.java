package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Racines lumineuses qui pendent des voûtes du tertre sylvestre (sans collision, tiennent au bloc du dessus). */
public class GlowrootBlock extends Block {
   private static final VoxelShape SHAPE = box(2, 0, 2, 14, 16, 14);

   public GlowrootBlock(Properties props) {
      super(props);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }

   @Override
   @SuppressWarnings("deprecation")
   public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      BlockState up = level.getBlockState(pos.above());
      return up.is(this) || up.isFaceSturdy(level, pos.above(), Direction.DOWN);
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      return dir == Direction.UP && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, other, level, pos, otherPos);
   }
}
