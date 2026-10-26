package com.hbm_m.multiblock;

import com.hbm_m.interfaces.IMultiblockController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MultiblockPlacement {
   private MultiblockPlacement() {
   }

   public static BlockPos getCorePos(BlockPos facadePos, Direction facing, int placementOffset) {
      return placementOffset <= 0 ? facadePos : facadePos.relative(facing.getOpposite(), placementOffset);
   }

   public static BlockPos getFacadePos(BlockPos corePos, Direction facing, int placementOffset) {
      return placementOffset <= 0 ? corePos : corePos.relative(facing, placementOffset);
   }

   public static BlockPos getCorePos(BlockPos placedPos, Direction facing, MultiblockStructureHelper helper) {
      return getCorePos(placedPos, facing, helper.getPlacementOffset());
   }

   public static BlockPos getCorePos(BlockPos placedPos, Direction facing, MultiblockStructureHelper helper, Block block) {
      int offset = helper.getPlacementOffset();
      if (block instanceof IMultiblockController controller) {
         int customOffset = controller.getOffset();
         if (customOffset >= 0) {
            offset = customOffset;
         }
      }

      return getCorePos(placedPos, facing, offset);
   }

   public static boolean canSurvive(BlockState state, LevelReader level, BlockPos placedPos, MultiblockStructureHelper helper) {
      int offset = helper.getPlacementOffset();
      if (state.getBlock() instanceof IMultiblockController controller) {
         int customOffset = controller.getOffset();
         if (customOffset >= 0) {
            offset = customOffset;
         }
      }

      if (offset <= 0) {
         return true;
      }

      Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
      BlockPos corePos = getCorePos(placedPos, facing, offset);
      return corePos.equals(placedPos) ? true : level.getBlockState(corePos).canBeReplaced();
   }
}
