package com.mrcrayfish.backpacked.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.blockentity.ShelfBlockEntity;
import com.mrcrayfish.backpacked.platform.Services;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ShelfBlock extends HorizontalDirectionalBlock implements EntityBlock {
   private static final Map<Direction, VoxelShape> EMPTY_SHAPES = Maps.newEnumMap(
      ImmutableMap.of(
         Direction.NORTH,
         Block.box(2.0, 3.0, 7.0, 14.0, 5.0, 16.0),
         Direction.SOUTH,
         Block.box(2.0, 3.0, 0.0, 14.0, 5.0, 9.0),
         Direction.WEST,
         Block.box(7.0, 3.0, 2.0, 16.0, 5.0, 14.0),
         Direction.EAST,
         Block.box(0.0, 3.0, 2.0, 9.0, 5.0, 14.0)
      )
   );
   private static final Map<Direction, VoxelShape> SHELVED_SHAPES = Maps.newEnumMap(
      ImmutableMap.of(
         Direction.NORTH,
         Block.box(2.0, 3.0, 7.0, 14.0, 14.0, 16.0),
         Direction.SOUTH,
         Block.box(2.0, 3.0, 0.0, 14.0, 14.0, 9.0),
         Direction.WEST,
         Block.box(7.0, 3.0, 2.0, 16.0, 14.0, 14.0),
         Direction.EAST,
         Block.box(0.0, 3.0, 2.0, 9.0, 14.0, 14.0)
      )
   );

   public ShelfBlock(Properties properties) {
      super(properties);
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult result) {
      return !level.isClientSide() && level.getBlockEntity(pos) instanceof ShelfBlockEntity shelfBlockEntity
         ? shelfBlockEntity.interact(player)
         : InteractionResult.SUCCESS;
   }

   public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
      return getter.getBlockEntity(pos) instanceof ShelfBlockEntity shelfBlockEntity && !shelfBlockEntity.getBackpack().isEmpty()
         ? SHELVED_SHAPES.get(state.getValue(FACING))
         : EMPTY_SHAPES.get(state.getValue(FACING));
   }

   public boolean canSurvive(BlockState state, LevelReader reader, BlockPos pos) {
      Direction facing = (Direction)state.getValue(FACING);
      BlockPos relativePos = pos.relative(facing.getOpposite());
      BlockState relativeState = reader.getBlockState(relativePos);
      return relativeState.isFaceSturdy(reader, relativePos, facing);
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Direction clickedFace = context.getClickedFace();
      if (clickedFace.getAxis().isHorizontal()) {
         BlockState potentialState = (BlockState)this.defaultBlockState().setValue(FACING, clickedFace);
         if (this.canSurvive(potentialState, context.getLevel(), context.getClickedPos())) {
            return potentialState;
         }
      }

      return null;
   }

   public boolean hasAnalogOutputSignal(BlockState state) {
      return true;
   }

   public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof ShelfBlockEntity shelfBlockEntity ? AbstractContainerMenu.getRedstoneSignalFromContainer(shelfBlockEntity) : 0;
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean unknown) {
      if (!state.is(newState.getBlock())) {
         if (level.getBlockEntity(pos) instanceof ShelfBlockEntity shelfBlockEntity) {
            boolean dropsContents = (Boolean)Config.SERVER.backpack.dropContentsFromShelf.get();
            ItemStack stack = dropsContents ? shelfBlockEntity.getBackpack() : shelfBlockEntity.getBackpackWithContents();
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            if (dropsContents) {
               Containers.dropContents(level, pos, shelfBlockEntity);
            }

            level.updateNeighbourForOutputSignal(pos, this);
         }

         super.onRemove(state, level, pos, newState, unknown);
      }
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return Services.BACKPACK.createShelfBlockEntityType(pos, state);
   }
}
