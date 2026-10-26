package com.hbm_m.block.machines;

import com.hbm_m.block.ModBlocks;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.blockentity.machines.TransitionSealBlockEntity;
import com.hbm_m.interfaces.IMultiblockController;
import com.hbm_m.multiblock.MultiblockStructureHelper;
import com.hbm_m.multiblock.PartRole;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TransitionSealBlock extends BaseEntityBlock implements IMultiblockController {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   private final MultiblockStructureHelper structureHelper;

   public TransitionSealBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
      this.structureHelper = defineStructure();
   }

   private static MultiblockStructureHelper defineStructure() {
      String bottom = "OOOOOOOOOOOOCOOOOOOOOOOOOO";
      String fill = "OOOOOOOOOOOOOOOOOOOOOOOOOO";
      String[][] layers = new String[24][];

      for (int y = 0; y < 24; y++) {
         layers[y] = new String[]{y == 0 ? bottom : fill};
      }

      return MultiblockStructureHelper.createFromLayersWithRoles(
         layers,
         Map.of(),
         () -> ((Block)ModBlocks.UNIVERSAL_MACHINE_PART.get()).defaultBlockState(),
         Map.of('O', PartRole.DEFAULT, 'C', PartRole.CONTROLLER),
         null,
         null
      );
   }

   @Override
   public MultiblockStructureHelper getStructureHelper() {
      return this.structureHelper;
   }

   @Override
   public PartRole getPartRole(BlockPos localOffset) {
      return PartRole.DEFAULT;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TransitionSealBlockEntity(pos, state);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return createTickerHelper(type, (BlockEntityType)ModBlockEntities.TRANSITION_SEAL_BE.get(), TransitionSealBlockEntity::tick);
   }

   public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
      super.onPlace(state, level, pos, oldState, isMoving);
      if (!state.is(oldState.getBlock()) && !level.isClientSide()) {
         this.placeMultiblockStructure(level, pos, state);
      }
   }

   public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return super.canSurvive(state, level, pos) && this.canSurviveMultiblockPlacement(state, level, pos);
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
      if (!state.is(newState.getBlock()) && !level.isClientSide()) {
         this.structureHelper.destroyStructure(level, pos, (Direction)state.getValue(FACING));
      }

      super.onRemove(state, level, pos, newState, isMoving);
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.structureHelper.generateShapeFromParts((Direction)state.getValue(FACING));
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return getCellCollisionShape(level, pos, state, pos);
   }

   public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
      return Shapes.empty();
   }

   public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
      if (level.getBlockEntity(pos) instanceof TransitionSealBlockEntity seal) {
         return switch (type) {
            case LAND, AIR -> seal.isOpen();
            default -> false;
         };
      } else {
         return false;
      }
   }

   public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
      return false;
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   public static VoxelShape getCellCollisionShape(BlockGetter level, BlockPos corePos, BlockState state, BlockPos cellPos) {
      Direction facing = (Direction)state.getValue(FACING);
      boolean zAxis = facing == Direction.EAST || facing == Direction.WEST;
      int dw = zAxis ? cellPos.getZ() - corePos.getZ() : cellPos.getX() - corePos.getX();
      int dy = cellPos.getY() - corePos.getY();
      double ex = dw / 10.5;
      double ey = (dy - 11.5) / 9.5;
      return ex * ex + ey * ey <= 1.0 ? Shapes.empty() : Shapes.block();
   }
}
