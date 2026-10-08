package fr.heralisz.mere.dungeon;

import fr.heralisz.mere.fx.Particles;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Décor « héros » : modèle GeckoLib dessiné tel quel (rotations libres), orienté, incassable. Étape 0..3 (pilotée par la salle) :
 * éteint / allumé, balance / chute / au sol, levée / abattue, chaînes / brisées... La lumière des décors à flamme ne brille qu'allumés.
 */
public class HeroPropBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
   public final HeroKind kind;
   private final VoxelShape[] shapes = new VoxelShape[4];
   private final boolean solid;

   public HeroPropBlock(HeroKind kind) {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.STONE)
         .lightLevel(s -> lightOf(kind, s)).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
         .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
      this.kind = kind;
      registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, 0));
      boolean any = false;
      for (Direction d : Direction.Plane.HORIZONTAL) {
         VoxelShape s = Shapes.empty();
         for (double[] b : kind.shape) s = Shapes.or(s, rotated(b, d));
         any |= !s.isEmpty();
         shapes[d.get2DDataValue()] = s.isEmpty() ? Block.box(6, 0, 6, 10, 2, 10) : s;
      }
      this.solid = any;
   }

   private static int lightOf(HeroKind k, BlockState s) {
      int st = s.hasProperty(STAGE) ? s.getValue(STAGE) : 0;
      if (k.litOnly) return st >= 1 ? k.light : 0;
      if (k == HeroKind.SHRINE) return st == 3 ? 0 : st >= 1 ? 10 : k.light;
      return k.light;
   }

   public static boolean lit(BlockState s) {
      if (!(s.getBlock() instanceof HeroPropBlock)) return false;
      return s.getValue(STAGE) >= 1;
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
      b.add(FACING, STAGE);
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
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
      return solid ? shapes[s.getValue(FACING).get2DDataValue()] : Shapes.empty();
   }

   /** L'autel de couvée : on y dépose l'Œuf de la Couvée, le rituel commence (le cocon s'éveille). */
   @Override
   @SuppressWarnings("deprecation")
   public net.minecraft.world.InteractionResult use(BlockState s, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
      if (kind != HeroKind.SHRINE || hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
      if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
      net.minecraft.world.item.ItemStack st = player.getItemInHand(hand);
      if (!st.is(fr.heralisz.mere.HeraliszMere.BROOD_EGG.get())) {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_mere.need_egg")
            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
         return net.minecraft.world.InteractionResult.CONSUME;
      }
      NestHallBE h = NestHallBE.nearest(level, pos, 130);
      if (h != null && level instanceof net.minecraft.server.level.ServerLevel sl && h.offerEgg(sl, player)) {
         if (!player.getAbilities().instabuild) st.shrink(1);
      } else {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_mere.not_now")
            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
      }
      return net.minecraft.world.InteractionResult.CONSUME;
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
      switch (kind.fx) {
         case 1 -> {
            if (!lit(s) || r.nextInt(6) != 0) return;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.6, y + 0.6 + r.nextDouble() * 0.3,
               z + (r.nextDouble() - 0.5) * 0.6, 0, 0.005, 0);
         }
         case 3 -> {                                                    // l'ambre goutte des œufs, la soie s'effiloche
            if (r.nextInt(3) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_HONEY, x + (r.nextDouble() - 0.5) * 1.4,
               y + 0.2 + r.nextDouble() * 0.6, z + (r.nextDouble() - 0.5) * 1.4, 0, 0, 0);
            if (r.nextInt(8) == 0) level.addParticle(Particles.AURORA.get(), x + (r.nextDouble() - 0.5) * 3, y + r.nextDouble() * 3,
               z + (r.nextDouble() - 0.5) * 3, 0, -0.002, 0);
         }
         case 4 -> {
            if (r.nextInt(4) == 0) level.addParticle(Particles.AURORA.get(), x + (r.nextDouble() - 0.5) * 1.6, y + r.nextDouble() * 1.4,
               z + (r.nextDouble() - 0.5) * 1.6, 0, -0.002, 0);
         }
         default -> {
         }
      }
   }
}
