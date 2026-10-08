package fr.heralisz.mere.dungeon;

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

/** Décor immobile du Nid : un modèle de bloc cuit dans le maillage des chunks (aucune entité, aucun tick). Incassable, orienté. */
public class StaticPropBlock extends HorizontalDirectionalBlock {
   public final StaticKind kind;
   private final VoxelShape[] shapes = new VoxelShape[4];

   public StaticPropBlock(StaticKind kind) {
      super(props(kind));
      this.kind = kind;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
      for (Direction d : Direction.Plane.HORIZONTAL) {
         VoxelShape s = Shapes.empty();
         for (double[] b : kind.shape) s = Shapes.or(s, rotated(b, d));
         shapes[d.get2DDataValue()] = s.isEmpty() ? Block.box(4, 0, 4, 12, 1, 12) : s;
      }
   }

   private static BlockBehaviour.Properties props(StaticKind kind) {
      BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .sound(SoundType.STONE).lightLevel(s -> kind.light).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p2, e) -> false)
         .isRedstoneConductor((s, l, p2) -> false).isSuffocating((s, l, p2) -> false).isViewBlocking((s, l, p2) -> false);
      return kind.collide ? p : p.noCollission();
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

   /** L'idole de la chapelle des offrandes : on y dépose l'Œuf de la Couvée, le rituel commence. */
   @Override
   @SuppressWarnings("deprecation")
   public net.minecraft.world.InteractionResult use(BlockState s, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
      if (kind != StaticKind.SPIDER_IDOL || hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
      if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
      net.minecraft.world.item.ItemStack st = player.getItemInHand(hand);
      if (!st.is(fr.heralisz.mere.HeraliszMere.BROOD_EGG.get())) {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_mere.need_egg")
            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
         return net.minecraft.world.InteractionResult.CONSUME;
      }
      NestHallBE h = NestHallBE.nearest(level, pos, 130);
      if (h != null && level instanceof net.minecraft.server.level.ServerLevel sl && h.offerEgg(sl, player, net.minecraft.world.phys.Vec3.atBottomCenterOf(pos))) {
         if (!player.getAbilities().instabuild) st.shrink(1);
      } else {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_mere.not_now")
            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
      }
      return net.minecraft.world.InteractionResult.CONSUME;
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
