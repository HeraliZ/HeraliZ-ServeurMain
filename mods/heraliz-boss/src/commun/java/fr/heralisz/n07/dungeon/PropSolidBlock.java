package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Volume invisible et solide autour des grands décors (cuves) : on s'y cogne, on s'abrite derrière, mais il ne se voit pas,
 * ne fait pas d'ombre et laisse passer la lumière.
 */
public class PropSolidBlock extends Block {
   public PropSolidBlock(Properties p) {
      super(p);
   }

   @Override
   @SuppressWarnings("deprecation")
   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.empty();
   }

   @Override
   @SuppressWarnings("deprecation")
   public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
      return 1.0F;
   }

   @Override
   public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }
}
