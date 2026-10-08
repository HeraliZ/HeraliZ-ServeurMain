package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Décor immobile d'un donjon (cadavre, barricade, bureau renversé, sang, graffiti...) : un simple modèle de bloc vanilla, cuit
 * dans le maillage des chunks (aucun rendu par image, aucune entité de bloc, aucun tick). Incassable ; orienté (FACING).
 */
public class StaticPropBlock extends HorizontalDirectionalBlock {
   public final StaticPropKind kind;
   private final VoxelShape[] shapes = new VoxelShape[4];

   public StaticPropBlock(StaticPropKind kind) {
      super(props(kind));
      this.kind = kind;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
      for (Direction d : Direction.Plane.HORIZONTAL) {
         VoxelShape s = Shapes.empty();
         for (double[] b : kind.shape) s = Shapes.or(s, rotated(b, d));
         shapes[d.get2DDataValue()] = s.isEmpty() ? Block.box(4, 0, 4, 12, 1, 12) : s;
      }
   }

   private static BlockBehaviour.Properties props(StaticPropKind kind) {
      BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .sound(SoundType.STONE).lightLevel(s -> kind.light).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p2, e) -> false)
         .isRedstoneConductor((s, l, p2) -> false).isSuffocating((s, l, p2) -> false).isViewBlocking((s, l, p2) -> false);
      return kind.collide ? p : p.noCollission();
   }

   /** Boîte (pixels, orientation nord) tournée vers la direction d, bornée à la case. */
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
      return shapes[state.getValue(FACING).get2DDataValue()];
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return kind.collide ? shapes[state.getValue(FACING).get2DDataValue()] : Shapes.empty();
   }

   /** Particules des décors vivants (côté client, seulement près du joueur) : bannière en feu, coulée de lave, givre. */
   @Override
   public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource r) {
      if (kind.fx == 0) return;
      double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
      switch (kind.fx) {
         case 1 -> {
            if (r.nextInt(2) == 0) {
               level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x + (r.nextDouble() - 0.5) * 0.8, y + 0.1 + r.nextDouble() * 1.2,
                  z + (r.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
            }
            if (r.nextInt(4) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x, y + 1.4, z, 0, 0.03, 0);
            if (r.nextInt(40) == 0) {
               level.playLocalSound(x, y + 0.5, z, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F,
                  0.8F + r.nextFloat() * 0.3F, false);
            }
         }
         case 2 -> {
            if (r.nextInt(3) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x + (r.nextDouble() - 0.5) * 2, y + 0.3 + r.nextDouble(),
               z + (r.nextDouble() - 0.5) * 2, 0, 0, 0);
            if (r.nextInt(3) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x + (r.nextDouble() - 0.5) * 2, y + 1.5 + r.nextDouble(),
               z + (r.nextDouble() - 0.5) * 2, 0, 0.04, 0);
            if (r.nextInt(60) == 0) {
               level.playLocalSound(x, y + 0.5, z, net.minecraft.sounds.SoundEvents.LAVA_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F,
                  0.8F + r.nextFloat() * 0.3F, false);
            }
         }
         case 3 -> {
            if (r.nextInt(2) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, x + (r.nextDouble() - 0.5) * 1.5, y + r.nextDouble() * 2,
               z + (r.nextDouble() - 0.5) * 1.5, 0, -0.02, 0);
         }
         default -> {
         }
      }
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
}
