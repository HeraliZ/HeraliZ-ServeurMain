package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Les pièces maîtresses de la salle, qui passent toutes en ALERTE (rouge sang) quand Ymirak s'éveille, comme le labo du
 * Sujet N-07 : glace aux veines de lumière, eau de fonte qui coule, aurore sous la coupole, titans pris dans la glace.
 */
public final class SetPieceBlocks {
   public static final BooleanProperty ALERT = BooleanProperty.create("alert");

   private SetPieceBlocks() {}

   /** Bloc plein qui a un état d'alerte (glace aux veines de lumière). */
   public static class AlertBlock extends Block {
      public AlertBlock(Properties p) {
         super(p);
         registerDefaultState(stateDefinition.any().setValue(ALERT, false));
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(ALERT);
      }
   }

   /** Rigole d'eau de fonte : dalle pleine (on marche dessus) creusée d'un filet d'eau lumineux qui se raccorde aux voisines. */
   public static class Meltwater extends Block {
      public static final BooleanProperty NORTH = net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH,
         EAST = net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,
         SOUTH = net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH,
         WEST = net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;

      public Meltwater(Properties p) {
         super(p);
         registerDefaultState(stateDefinition.any().setValue(ALERT, false).setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
            .setValue(WEST, false));
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(ALERT, NORTH, EAST, SOUTH, WEST);
      }

      private static BooleanProperty prop(Direction d) {
         return switch (d) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
         };
      }

      @Override
      public BlockState getStateForPlacement(BlockPlaceContext ctx) {
         BlockState s = defaultBlockState();
         for (Direction d : Direction.Plane.HORIZONTAL) s = s.setValue(prop(d), ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d)).is(this));
         return s;
      }

      @Override
      public BlockState updateShape(BlockState s, Direction d, BlockState n, net.minecraft.world.level.LevelAccessor l, BlockPos p, BlockPos np) {
         return d.getAxis().isHorizontal() ? s.setValue(prop(d), n.is(this)) : s;
      }

      /** Une brume froide rampe au ras de l'eau (côté client, près du joueur). */
      @Override
      public void animateTick(BlockState s, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource r) {
         if (r.nextInt(8) != 0) return;
         level.addParticle(fr.heralisz.mammoth.fx.Particles.MIST.get(), pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5,
            (r.nextDouble() - 0.5) * 0.02, 0.003, (r.nextDouble() - 0.5) * 0.02);
      }
   }

   /** Point d'ancrage invisible de l'aurore (le rendu dessine les voiles sous la coupole). */
   public static class Aurora extends BaseEntityBlock {
      public Aurora(Properties p) {
         super(p);
         registerDefaultState(stateDefinition.any().setValue(ALERT, false));
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(ALERT);
      }

      @Override
      public RenderShape getRenderShape(BlockState s) {
         return RenderShape.INVISIBLE;
      }

      @Override
      public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
         return Shapes.empty();
      }

      @Override
      public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
         return new SetPieceEntities.AuroraBE(pos, st);
      }
   }

   /** Titan pris dans la glace (modèle GeckoLib), orienté. */
   public static class Titan extends BaseEntityBlock {
      public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

      public Titan(Properties p) {
         super(p);
         registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ALERT, false));
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING, ALERT);
      }


      @Override
      @SuppressWarnings("deprecation")
      public BlockState rotate(BlockState s, net.minecraft.world.level.block.Rotation r) {
         return s.setValue(FACING, r.rotate(s.getValue(FACING)));
      }

      @Override
      @SuppressWarnings("deprecation")
      public BlockState mirror(BlockState s, net.minecraft.world.level.block.Mirror m) {
         return s.rotate(m.getRotation(s.getValue(FACING)));
      }

      @Override
      public BlockState getStateForPlacement(BlockPlaceContext ctx) {
         return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
      }

      @Override
      public RenderShape getRenderShape(BlockState s) {
         return RenderShape.ENTITYBLOCK_ANIMATED;
      }

      @Override
      public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
         return new SetPieceEntities.TitanBE(pos, st);
      }
   }
}
