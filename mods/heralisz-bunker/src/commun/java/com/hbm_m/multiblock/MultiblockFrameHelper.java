package com.hbm_m.multiblock;

import com.hbm_m.interfaces.IFrameSupportable;
import com.hbm_m.interfaces.IMultiblockController;
import com.hbm_m.interfaces.IMultiblockPart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

public final class MultiblockFrameHelper {
   public static final String FRAME_PROPERTY_NAME = "frame";

   private MultiblockFrameHelper() {
   }

   @Nullable
   public static BooleanProperty findFrameProperty(BlockState state) {
      for (Property<?> property : state.getProperties()) {
         if ("frame".equals(property.getName()) && property instanceof BooleanProperty frame) {
            return frame;
         }
      }

      return null;
   }

   public static int getMaxY(MultiblockStructureHelper structure) {
      int maxY = Integer.MIN_VALUE;

      for (BlockPos local : structure.getStructureMap().keySet()) {
         if (local.getY() > maxY) {
            maxY = local.getY();
         }
      }

      return maxY;
   }

   public static boolean isTopRingPart(MultiblockStructureHelper structure, BlockPos localOffset) {
      return localOffset.getY() == getMaxY(structure);
   }

   public static List<BlockPos> getTopRingWorldPositions(MultiblockStructureHelper structure, BlockPos controllerPos, Direction facing) {
      List<BlockPos> topRing = new ArrayList<>();
      int maxY = getMaxY(structure);

      for (BlockPos localOffset : structure.getStructureMap().keySet()) {
         if (localOffset.getY() == maxY) {
            topRing.add(structure.getRotatedPos(controllerPos, localOffset, facing));
         }
      }

      return topRing;
   }

   public static boolean computeFrameVisible(Level level, MultiblockStructureHelper structure, BlockPos controllerPos, Direction facing) {
      for (BlockPos worldPos : getTopRingWorldPositions(structure, controllerPos, facing)) {
         if (!level.isEmptyBlock(worldPos.above())) {
            return true;
         }
      }

      return false;
   }

   public static boolean applyFrameToBlockState(Level level, BlockPos controllerPos, boolean visible) {
      BlockState state = level.getBlockState(controllerPos);
      BooleanProperty frameProp = findFrameProperty(state);
      if (frameProp != null && (Boolean)state.getValue(frameProp) != visible) {
         level.setBlock(controllerPos, (BlockState)state.setValue(frameProp, visible), 3);
         return true;
      } else {
         return false;
      }
   }

   public static void updateFrameForController(Level level, BlockPos controllerPos) {
      if (!level.isClientSide()) {
         BlockState state = level.getBlockState(controllerPos);
         if (state.getBlock() instanceof IMultiblockController controller) {
            BlockEntity be = level.getBlockEntity(controllerPos);
            if (be instanceof IFrameSupportable) {
               if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                  MultiblockStructureHelper helper = controller.getStructureHelper();
                  if (helper != null) {
                     Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
                     boolean visible = computeFrameVisible(level, helper, controllerPos, facing);
                     applyFrameToBlockState(level, controllerPos, visible);
                  }
               }
            }
         }
      }
   }

   public static void onNeighborChangedForPart(Level level, BlockPos partPos, BlockPos changedPos) {
      if (!level.isClientSide() && level.getServer() != null) {
         if (level.getBlockEntity(partPos) instanceof IMultiblockPart part) {
            BlockPos ctrlPos = part.getControllerPos();
            if (ctrlPos != null) {
               BlockState controllerState = level.getBlockState(ctrlPos);
               if (controllerState.getBlock() instanceof IMultiblockController controller) {
                  MultiblockStructureHelper helper = controller.getStructureHelper();
                  if (helper != null) {
                     BlockPos worldOffset = partPos.subtract(ctrlPos);
                     Direction facing = (Direction)controllerState.getValue(HorizontalDirectionalBlock.FACING);
                     BlockPos localOffset = MultiblockStructureHelper.rotateBack(worldOffset, facing);
                     if (isTopRingPart(helper, localOffset) && changedPos.equals(partPos.above())) {
                        level.getServer().execute(() -> {
                           BlockEntity be = level.getBlockEntity(ctrlPos);
                           if (be != null && !be.isRemoved() && level.getBlockState(ctrlPos).is(controllerState.getBlock())) {
                              updateFrameForController(level, ctrlPos);
                           }
                        });
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean isFrameVisible(BlockState state) {
      BooleanProperty frameProp = findFrameProperty(state);
      return frameProp != null && (Boolean)state.getValue(frameProp);
   }
}
