package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Entités de bloc des pièces maîtresses (sans tick : tout est côté rendu). */
public final class SetPieceEntities {
   private SetPieceEntities() {}

   public static class AuroraBE extends BlockEntity {
      public AuroraBE(BlockPos pos, BlockState st) {
         super(ModBlocks.AURORA_BE.get(), pos, st);
      }

      @Override
      public AABB getRenderBoundingBox() {
         return new AABB(worldPosition).inflate(30, 16, 30);
      }
   }

   public static class TitanBE extends BlockEntity implements GeoBlockEntity {
      private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.prop_frozen_titan.idle");
      private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

      public TitanBE(BlockPos pos, BlockState st) {
         super(ModBlocks.TITAN_BE.get(), pos, st);
      }

      @Override
      public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
         reg.add(new AnimationController<>(this, "idle", 0, s -> s.setAndContinue(IDLE)));
      }

      @Override
      public AnimatableInstanceCache getAnimatableInstanceCache() {
         return cache;
      }

      @Override
      public AABB getRenderBoundingBox() {
         return new AABB(worldPosition).inflate(4, 11, 4).move(0, 5, 0);
      }
   }
}
