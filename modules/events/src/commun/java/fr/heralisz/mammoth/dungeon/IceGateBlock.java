package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mur de glace de l'arène : posé au bas, au milieu de l'entrée (FACING = vers l'arène). Quand Ymirak s'éveille, l'autel le
 * ferme : la glace monte du sol et bouche l'entrée (blocs invisibles) tant que le combat dure.
 */
public class IceGateBlock extends BaseEntityBlock {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

   public IceGateBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.GLASS));
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CLOSED, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, CLOSED);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState rotate(BlockState s, Rotation r) {
      return s.setValue(FACING, r.rotate(s.getValue(FACING)));
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState mirror(BlockState s, Mirror m) {
      return s.rotate(m.getRotation(s.getValue(FACING)));
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return state.getValue(CLOSED) ? Shapes.block() : Block.box(0, 0, 0, 16, 1, 16);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return state.getValue(CLOSED) ? Shapes.block() : Shapes.empty();
   }

   @Override
   @SuppressWarnings("deprecation")
   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new IceGateBlockEntity(pos, state);
   }
}
