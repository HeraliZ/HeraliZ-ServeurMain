package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Décor « héros » : modèle GeckoLib dessiné tel quel (rotations libres), orienté, incassable ; volume dans sa case. */
public class HeroPropBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public final HeroKind kind;
   private final VoxelShape[] shapes = new VoxelShape[4];

   public HeroPropBlock(HeroKind kind) {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.STONE)
         .lightLevel(s -> kind.light).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
         .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
      this.kind = kind;
      registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
      for (Direction d : Direction.Plane.HORIZONTAL) {
         VoxelShape s = Shapes.empty();
         for (double[] b : kind.shape) s = Shapes.or(s, rotated(b, d));
         shapes[d.get2DDataValue()] = s.isEmpty() ? Block.box(4, 0, 4, 12, 1, 12) : s;
      }
   }

   private static VoxelShape rotated(double[] b, Direction d) {
      double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
      double[] r = switch (d) {
         case SOUTH -> new double[] {16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0};
         case EAST -> new double[] {16 - z1, y0, x0, 16 - z0, y1, x1};
         case WEST -> new double[] {z0, y0, 16 - x1, z1, y1, 16 - x0};
         default -> new double[] {x0, y0, z0, x1, y1, z1};
      };
      for (int i = 0; i < 6; i++) r[i] = Math.max(0, Math.min(16, r[i]));
      if (r[3] - r[0] < 0.01 || r[4] - r[1] < 0.01 || r[5] - r[2] < 0.01) return Shapes.empty();
      return Block.box(r[0], r[1], r[2], r[3], r[4], r[5]);
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
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   @Override
   public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
      return shapes[s.getValue(FACING).get2DDataValue()];
   }

   @Override
   public RenderShape getRenderShape(BlockState s) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
      return new HeroPropBE(pos, st);
   }

   @Override
   public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
      return true;
   }

   @Override
   public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
      double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
      if (kind.fx == 1) {
         if (r.nextInt(2) == 0) level.addParticle(fr.heralisz.mammoth.fx.Particles.CINDER.get(), x + (r.nextDouble() - 0.5) * 0.8,
            y + 0.2 + r.nextDouble(), z + (r.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
         if (r.nextInt(4) == 0) level.addParticle(fr.heralisz.mammoth.fx.Particles.MIST.get(), x, y + 1.3, z, 0, 0.03, 0);
      } else if (kind.fx == 3 && r.nextInt(2) == 0) {
         level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), x + (r.nextDouble() - 0.5) * 1.5, y + r.nextDouble() * 2,
            z + (r.nextDouble() - 0.5) * 1.5, 0, -0.02, 0);
      }
   }
}
