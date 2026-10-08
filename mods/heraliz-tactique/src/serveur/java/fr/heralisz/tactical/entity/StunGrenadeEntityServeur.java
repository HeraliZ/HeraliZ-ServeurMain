package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModEffects;
import fr.heralisz.tactical.item.throwable.flash.StunThrowableData;
import fr.heralisz.tactical.util.SightTraceUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link StunGrenadeEntity} (grenade flash ; docs/METHODE_CLIENT_SERVEUR.md §5.4) : aveuglement et
 * assourdissement des entités proches, impacts ; code d'origine. Créée par l'usine (StunGrenadeEntity.USINE) dans les
 * mondes serveur.
 */
public class StunGrenadeEntityServeur extends StunGrenadeEntity {
   public StunGrenadeEntityServeur(EntityType<StunGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void impact(HitResult result) {
      LancersServeur.impact(this, result);
   }

   // StunGrenadeEntity.onDeath (d'origine), partie serveur
   @Override
   protected void etourdir() {
      if (!this.level().isClientSide()) {
         double radius = this.getData().getRadius();
         AABB aabb = this.getBoundingBox().inflate(radius);

         for (Entity entity : this.level().getEntities(this, aabb, EntitySelector.NO_SPECTATORS)) {
            if (entity instanceof LivingEntity living) {
               calculateAndApplyEffect(this, living, this.getData());
            }
         }
      }
   }

   // StunGrenadeEntity.calculateAndApplyEffect (d'origine)
   public static void calculateAndApplyEffect(Entity starter, LivingEntity target, StunThrowableData.StunData data) {
      Vec3 p = starter.position().add(0.0, 1.0, 0.0);
      Vec3 eyes = target.getEyePosition(1.0F);
      Vec3 d1 = p.subtract(eyes);
      double distanceMax = data.getRadius();
      double distance = d1.length();
      if (!(distance > distanceMax)) {
         double a1 = Math.toDegrees(Math.acos(target.getViewVector(1.0F).dot(d1.normalize())));
         double angleMax = data.getBlind().getMaxAngle();
         if (a1 > 0.0 && a1 < angleMax && SightTraceUtil.rayTraceOpaqueBlocks(starter, target.level(), eyes, p, false, false, false) == null) {
            int durationBlinded = data.calcBlindDuration(distance, a1);
            if (durationBlinded > 0) {
               target.addEffect(new MobEffectInstance((MobEffect)ModEffects.BLIND.get(), durationBlinded, 0, false, false));
            }
         }

         int durationDeafened = data.calcDeafenedDuration(distance);
         if (durationDeafened > 0) {
            target.addEffect(new MobEffectInstance((MobEffect)ModEffects.DEAFENED.get(), durationDeafened, 0, false, false));
         }
      }
   }
}
