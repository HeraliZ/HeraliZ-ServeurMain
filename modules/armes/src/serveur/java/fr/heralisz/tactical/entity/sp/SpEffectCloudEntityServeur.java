package fr.heralisz.tactical.entity.sp;

import fr.heralisz.tactical.entity.SmokeGrenadeEntity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Partie SERVEUR de {@link SpEffectCloudEntity} (nuage au sol des molotovs et gaz ; docs/METHODE_CLIENT_SERVEUR.md §5) : met le
 * feu aux entités dans le nuage, s'éteint dans la fumée d'un fumigène ; code d'origine. La version commune n'a plus de tick
 * à elle (celui d'AreaEffectCloud) : le tick ci-dessous appelle super.tick() une seule fois, comme avant.
 */
public class SpEffectCloudEntityServeur extends SpEffectCloudEntity {
   public SpEffectCloudEntityServeur(EntityType<? extends AreaEffectCloud> type, Level level) {
      super(type, level);
   }

   public SpEffectCloudEntityServeur(Level level, double x, double y, double z) {
      this(TYPE, level);
      this.setPos(x, y, z);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide() && this.tickCount % 10 == 0) {
         for (Entity entity : this.level().getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(0.0, 2.0, 0.0))) {
            if (this.isExtinguishBySmoke() && entity instanceof SmokeGrenadeEntity smokeGrenade) {
               if (this.shouldBeExtinguished(smokeGrenade)) {
                  this.discard();
               }
            } else if (this.isIgnite() && !entity.fireImmune() && entity instanceof LivingEntity && this.getBoundingBox().intersects(entity.getBoundingBox())) {
               entity.setSecondsOnFire(this.getIgniteTime());
            }
         }
      }
   }

   public boolean shouldBeExtinguished(SmokeGrenadeEntity smokeGrenade) {
      return smokeGrenade.tickCount >= 40 && smokeGrenade.position().distanceToSqr(this.position()) < 25.0;
   }
}
