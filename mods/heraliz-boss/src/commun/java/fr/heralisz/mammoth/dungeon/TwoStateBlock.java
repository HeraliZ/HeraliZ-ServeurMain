package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Décor orienté à deux états (éteint / allumé) : modèle de bloc vanilla, particules seulement quand il est allumé. */
public abstract class TwoStateBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty LIT = BlockStateProperties.LIT;
   private final VoxelShape shape;

   protected TwoStateBlock(Properties p, VoxelShape shape) {
      super(p);
      this.shape = shape;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, LIT);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
      return shape;
   }

   @Override
   @SuppressWarnings("deprecation")
   public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
      return 1.0F;
   }

   /** Rendu GeckoLib (modèle « héros » animé ; la flamme et la lueur n'apparaissent qu'allumé). */
   @Override
   public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState s) {
      return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Override
   public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
      return new LitPropBE(pos, st);
   }

   /** Modèle GeckoLib de ce décor (geo/block/<id>.geo.json). */
   public abstract String heroId();

   @Override
   public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }
}
