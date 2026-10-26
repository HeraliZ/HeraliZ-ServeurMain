package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Entité de bloc des décors « héros » : immobile (aucune animation, aucun tick), sert seulement au rendu GeckoLib. */
public class HeroPropBE extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public HeroPropBE(BlockPos pos, BlockState st) {
      super(ModBlocks.HERO_BE.get(), pos, st);
   }

   public HeroKind kind() {
      return getBlockState().getBlock() instanceof HeroPropBlock b ? b.kind : HeroKind.values()[0];
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "idle", 0, s -> {
         HeroKind k = kind();
         if (!k.animated) return software.bernie.geckolib.core.object.PlayState.STOP;
         return s.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation." + k.id + ".idle"));
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(3, 0, 3).expandTowards(0, 4, 0);
   }
}
