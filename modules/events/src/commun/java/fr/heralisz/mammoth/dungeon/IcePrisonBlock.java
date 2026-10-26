package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * La prison d'Ymirak : glaçon colossal (modèle GeckoLib, 17 x 11 blocs) qui ferme la niche ; STAGE 0 intacte, 1 à 3 les
 * fissures incandescentes gagnent la glace depuis le réceptacle du cœur, 4 elle a volé en éclats (plus rien n'est dessiné).
 * Le passage est bouché par un volume invisible posé par la structure.
 */
public class IcePrisonBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

   public IcePrisonBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.GLASS)
         .lightLevel(s -> s.getValue(STAGE) == 0 || s.getValue(STAGE) == 4 ? 3 : 6 + s.getValue(STAGE) * 3).noCollission());
      registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, 0));
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
   public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
      return Shapes.block();
   }

   /** La caméra des cinématiques traverse le bloc porteur (le modèle, lui, est dessiné à part). */
   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
      return Shapes.empty();
   }

   @Override
   public RenderShape getRenderShape(BlockState s) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
      return new BE(pos, st);
   }

   public static class BE extends BlockEntity implements GeoBlockEntity {
      private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

      public BE(BlockPos pos, BlockState st) {
         super(ModBlocks.PRISON_BE.get(), pos, st);
      }

      public int stage() {
         BlockState s = getBlockState();
         return s.hasProperty(STAGE) ? s.getValue(STAGE) : 0;
      }

      @Override
      public void registerControllers(AnimatableManager.ControllerRegistrar reg) {}

      @Override
      public AnimatableInstanceCache getAnimatableInstanceCache() {
         return cache;
      }

      @Override
      public AABB getRenderBoundingBox() {
         return new AABB(worldPosition).inflate(10, 0, 4).expandTowards(0, 12, 0);
      }
   }
}
