package fr.heralisz.n07.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BaseRailBlock;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Décor du spawn : un modèle 3D animé posé sur un seul bloc, qui installe autour de lui ses cases de collision invisibles
 * (DecorPartBlock) et ses lumières invisibles (bloc de lumière vanilla, là où brille la lanterne) et les retire quand on le casse.
 * Incassable en survie (le spawn reste propre), cassable en créatif — y compris en visant n'importe laquelle de ses cases.
 */
public class DecorBlock extends BaseEntityBlock {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   /** Longueur de chaîne ajoutée (lanternes suspendues) : 0 à 3 blocs. */
   public static final IntegerProperty DROP = IntegerProperty.create("drop", 0, 3);
   /** Véhicule posé sur un rail : le modèle descend d'un bloc. */
   public static final BooleanProperty ON_RAIL = BooleanProperty.create("on_rail");

   public final DecorKind kind;

   public DecorBlock(DecorKind kind) {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .lightLevel(s -> kind.spec.selfLight(s.getValue(DROP), s.getValue(ON_RAIL))));
      this.kind = kind;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DROP, 0).setValue(ON_RAIL, false));
   }

   public DecorSpec spec() {
      return kind.spec;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, DROP, ON_RAIL);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      Direction face = ctx.getClickedFace();
      Direction f = spec().mount == DecorSpec.Mount.WALL && face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
      boolean rail = spec().rail && ctx.getLevel().getBlockState(ctx.getClickedPos().below()).getBlock() instanceof BaseRailBlock;
      return this.defaultBlockState().setValue(FACING, f).setValue(ON_RAIL, rail);
   }

   @Override
   public BlockState rotate(BlockState s, Rotation r) {
      return s.setValue(FACING, r.rotate(s.getValue(FACING)));
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState mirror(BlockState s, Mirror m) {
      return s.rotate(m.getRotation(s.getValue(FACING)));
   }

   // ------------------------------------------------------------------ forme
   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return spec().shape(state.getValue(FACING));
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return spec().collide ? spec().shape(state.getValue(FACING)) : Shapes.empty();
   }

   @Override
   @SuppressWarnings("deprecation")
   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Override
   public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }

   @Override
   @SuppressWarnings("deprecation")
   public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
      return 1.0F;
   }

   @Override
   @SuppressWarnings("deprecation")
   public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
      return false;
   }

   /** Clic droit à main nue sur une lanterne suspendue : rallonge la chaîne (puis revient au plus court). */
   @Override
   @SuppressWarnings("deprecation")
   public InteractionResult use(BlockState st, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!spec().drop || !player.getItemInHand(hand).isEmpty() || !player.mayBuild()) return InteractionResult.PASS;
      // la chaîne est rallongée côté serveur (DecorBlockServeur, usine DecorRegistry.DECOR_USINE)
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   // ------------------------------------------------------------------ entité de bloc
   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new DecorBlockEntity(pos, state);
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide ? createTickerHelper(type, DecorRegistry.BE.get(), DecorBlockEntity::clientTick) : null;
   }
}
