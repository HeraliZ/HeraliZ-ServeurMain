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

/** Totems et braseros de la tribu : animation de repos (flamme qui danse, carillon qui se balance). */
public class LitPropBE extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public LitPropBE(BlockPos pos, BlockState st) {
      super(ModBlocks.LIT_BE.get(), pos, st);
   }

   public String heroId() {
      return getBlockState().getBlock() instanceof TwoStateBlock t ? t.heroId() : "hero_tribe_brazier";
   }

   public boolean lit() {
      BlockState s = getBlockState();
      return s.hasProperty(TwoStateBlock.LIT) && s.getValue(TwoStateBlock.LIT);
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new AnimationController<>(this, "idle", 12, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation." + heroId()
         + (lit() && "hero_soul_maw".equals(heroId()) ? ".awake" : ".idle")))));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }

   @Override
   public AABB getRenderBoundingBox() {
      if ("hero_soul_maw".equals(heroId())) return new AABB(worldPosition).inflate(9, 0, 9).expandTowards(0, -14, 0);
      return new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 2, 0);
   }
}
