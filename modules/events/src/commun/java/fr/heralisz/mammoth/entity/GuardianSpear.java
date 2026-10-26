package fr.heralisz.mammoth.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** La lance qu'un Gardien projette : vol en cloche, blesse le premier joueur touché, puis se plante (le Gardien vient la reprendre). */
public class GuardianSpear extends Projectile implements GeoEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public GuardianSpear(EntityType<? extends GuardianSpear> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {}

   @Override
   public void tick() {
      super.tick();
      vol();
   }

   /**
    * Un pas de vol en cloche (des deux côtés, pour un mouvement fluide). GuardianSpearServeur le redéfinit avec le code
    * d'origine (coups, atterrissage) : chaque instruction ne s'exécute qu'une fois (jamais super.tick() dans la version serveur).
    */
   protected void vol() {
      Vec3 v = this.getDeltaMovement();
      this.setPos(this.position().add(v));
      this.setDeltaMovement(v.scale(0.99).add(0, -0.035, 0));
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 128 * 128;
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {}

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
