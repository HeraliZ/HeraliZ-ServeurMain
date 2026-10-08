package com.hbm_m.interfaces;

import com.hbm_m.multiblock.MultiblockStructureHelper;
import com.hbm_m.multiblock.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public interface IMultiblockController {
   MultiblockStructureHelper getStructureHelper();

   default int getOffset() {
      return -1;
   }

   PartRole getPartRole(BlockPos var1);

   @Nullable
   default VoxelShape getCustomMasterVoxelShape(BlockState state) {
      return null;
   }

   default boolean shouldDestroyOnPartRemoved() {
      return true;
   }

   @Nullable
   default BlockPos getLegacyControllerOffset() {
      return null;
   }

   default boolean canSurviveMultiblockPlacement(BlockState state, LevelReader level, BlockPos placedPos) {
      return true;
   }

   @Nullable
   default BlockPos placeMultiblockStructure(Level level, BlockPos placedPos, BlockState state) {
      MultiblockStructureHelper helper = this.getStructureHelper();
      if (helper != null && !level.isClientSide()) {
         Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
         helper.placeStructure(level, placedPos, facing, this);
      }

      return placedPos;
   }
}
