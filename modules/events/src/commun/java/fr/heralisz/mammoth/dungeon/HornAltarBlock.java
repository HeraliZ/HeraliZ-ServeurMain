package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** L'autel du Cor : clic droit avec le cor de guerre de la tribu, le rituel commence et Ymirak s'arrache de la glace. */
public class HornAltarBlock extends BaseEntityBlock {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

   public HornAltarBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.STONE)
         .lightLevel(s -> 5));
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING);
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
      return SHAPE;
   }

   @Override
   @SuppressWarnings("deprecation")
   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Override
   @SuppressWarnings("deprecation")
   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
      if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HornAltarBlockEntity be) be.use(sp);
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return ModBlocks.ALTAR_BE.get().create(pos, state);           // par le type : version serveur si elle est là
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide ? null : createTickerHelper(type, ModBlocks.ALTAR_BE.get(), HornAltarBlockEntity::serverTick);
   }
}
