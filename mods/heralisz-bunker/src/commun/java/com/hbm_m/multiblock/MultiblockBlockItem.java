package com.hbm_m.multiblock;

import com.hbm_m.interfaces.IMultiblockController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class MultiblockBlockItem extends BlockItem {
   public MultiblockBlockItem(Block pBlock, Properties pProperties) {
      super(pBlock, pProperties);
      if (!(pBlock instanceof IMultiblockController)) {
         throw new IllegalArgumentException("MultiblockBlockItem can only be used with blocks that implement IMultiblockController!");
      }
   }

   @Nullable
   public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
      BlockPlaceContext vanillaContext = super.updatePlacementContext(context);
      if (vanillaContext == null) {
         return null;
      }

      BlockPlaceContext adjusted = shiftContextToCore(vanillaContext);
      return adjusted != null ? adjusted : vanillaContext;
   }

   protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
      IMultiblockController controller = (IMultiblockController)this.getBlock();
      Level level = context.getLevel();
      Player player = context.getPlayer();
      if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
         return false;
      } else {
         Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
         if (facing.getAxis() == Axis.Y) {
            return false;
         } else {
            BlockPos corePos = context.getClickedPos();
            if (!controller.getStructureHelper().checkPlacement(level, corePos, facing, player)) {
               return false;
            } else {
               return !level.getBlockState(corePos).canBeReplaced() ? false : super.placeBlock(context, state);
            }
         }
      }
   }

   @Nullable
   static BlockPlaceContext shiftContextToCore(BlockPlaceContext context) {
      if (context.getItemInHand().getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         if (block instanceof IMultiblockController controller) {
            BlockState preview = block.getStateForPlacement(context);
            if (preview != null && preview.hasProperty(HorizontalDirectionalBlock.FACING)) {
               Direction facing = (Direction)preview.getValue(HorizontalDirectionalBlock.FACING);
               if (facing.getAxis() == Axis.Y) {
                  return null;
               }

               MultiblockStructureHelper helper = controller.getStructureHelper();
               if (helper == null) {
                  return null;
               }

               BlockPos facadePos = context.getClickedPos();
               int offset = controller.getOffset();
               if (offset < 0) {
                  offset = helper.getPlacementOffset();
               }

               BlockPos corePos = MultiblockPlacement.getCorePos(facadePos, facing, offset);
               return corePos.equals(facadePos)
                  ? null
                  : new BlockPlaceContext(
                     context.getPlayer(),
                     context.getHand(),
                     context.getItemInHand(),
                     new BlockHitResult(Vec3.atCenterOf(corePos), context.getClickedFace(), corePos, context.isInside())
                  );
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }
}
