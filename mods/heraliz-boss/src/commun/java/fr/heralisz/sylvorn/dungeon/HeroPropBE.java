package fr.heralisz.sylvorn.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Entité de bloc des décors « héros » (rendu GeckoLib seulement, aucun tick). Décor étagé : joue « s<étape> » et repart de zéro
 * quand l'étape change (la chute du lustre, la herse qui s'abat, les chaînes qui cèdent) ; sinon « idle » en boucle.
 */
public class HeroPropBE extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private int shownStage = -1;

   public HeroPropBE(BlockPos pos, BlockState st) {
      super(ModBlocks.HERO_BE.get(), pos, st);
   }

   public HeroKind kind() {
      return getBlockState().getBlock() instanceof HeroPropBlock b ? b.kind : HeroKind.values()[0];
   }

   public int stage() {
      BlockState s = getBlockState();
      return s.hasProperty(HeroPropBlock.STAGE) ? s.getValue(HeroPropBlock.STAGE) : 0;
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new AnimationController<>(this, "main", 0, s -> {
         HeroKind k = kind();
         if (!k.animated) return PlayState.STOP;
         if (!k.staged) return s.setAndContinue(RawAnimation.begin().thenLoop("animation." + k.id + ".idle"));
         int st = stage();
         if (st != shownStage) {
            shownStage = st;
            s.getController().forceAnimationReset();
         }
         String n = "animation." + k.id + ".s" + st;
         return s.setAndContinue(st == 0 ? RawAnimation.begin().thenLoop(n) : RawAnimation.begin().thenPlayAndHold(n));
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }

   /** Boîte de rendu calculée sur le modèle (et ce que ses animations et ses rayons y ajoutent) : HeroKind.bounds. */
   @Override
   public AABB getRenderBoundingBox() {
      double[] b = kind().bounds;
      double x = worldPosition.getX() + 0.5, y = worldPosition.getY(), z = worldPosition.getZ() + 0.5;
      return new AABB(x - b[2], y + b[0], z - b[2], x + b[2], y + b[1], z + b[2]);
   }
}
