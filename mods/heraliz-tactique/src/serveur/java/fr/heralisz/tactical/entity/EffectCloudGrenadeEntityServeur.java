package fr.heralisz.tactical.entity;

import java.util.List;
import fr.heralisz.tactical.entity.sp.SpEffectCloudEntity;
import fr.heralisz.tactical.entity.sp.SpEffectCloudEntityServeur;
import fr.heralisz.tactical.item.throwable.area.EffectCloudThrowableData;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SSplashParticle;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Partie SERVEUR de {@link EffectCloudGrenadeEntity} (molotov, grenades à gaz ; docs/METHODE_CLIENT_SERVEUR.md §5.4) : nuage
 * d'effets au sol ou éclaboussure, effets appliqués, mise à feu, impacts ; code d'origine. Créée par l'usine
 * (EffectCloudGrenadeEntity.USINE) dans les mondes serveur. Le nuage au sol est créé en version serveur
 * (SpEffectCloudEntityServeur, constructeur d'origine : permis côté serveur, docs §5.1).
 */
public class EffectCloudGrenadeEntityServeur extends EffectCloudGrenadeEntity {
   public EffectCloudGrenadeEntityServeur(EntityType<EffectCloudGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void impact(HitResult result) {
      LancersServeur.impact(this, result);
   }

   // EffectCloudGrenadeEntity.onDeath (d'origine), partie serveur (appelée si !isClientSide et cloudData != null, comme avant)
   @Override
   protected void liberer(Vec3 pos, @Nullable HitResult hitResult) {
      EffectCloudThrowableData.CloudData cloudData = this.getCloudData();
      if (cloudData.isAreaCloud()) {
         this.spawnEffectCloud(pos, cloudData);
      } else {
         List<MobEffectInstance> effects = cloudData.getEffectInstances();
         Entity target = hitResult instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
         int color = PotionUtils.getColor(effects);
         this.applySplash(effects, cloudData.isIgnite(), cloudData.getIgniteTime(), target, cloudData.getRadius());
         NetworkHandler.sendToNearbyPlayers(new SSplashParticle(this.blockPosition(), color), this.level(), pos, 64.0);
      }
   }

   private void spawnEffectCloud(Vec3 pos, EffectCloudThrowableData.CloudData cloudData) {
      SpEffectCloudEntity cloud = new SpEffectCloudEntityServeur(this.level(), pos.x(), pos.y(), pos.z());
      cloud.setRadius(cloudData.getRadius());
      cloud.setRadiusPerTick(cloudData.getRadiusPerTick());
      cloud.setDuration(cloudData.getDuration());
      cloud.setWaitTime(cloudData.getWaitTime());
      cloud.setParticle(cloudData.getParticles());
      cloud.setIgnite(cloudData.isIgnite());
      cloud.setExtinguishBySmoke(cloudData.isExtinguishBySmoke());

      for (EffectCloudThrowableData.EffectData effect : cloudData.getEffects()) {
         cloud.addEffect(effect.toInstance());
      }

      if (this.getOwner() instanceof LivingEntity livingEntity) {
         cloud.setOwner(livingEntity);
      }

      this.level().addFreshEntity(cloud);
   }

   public void applySplash(List<MobEffectInstance> effectInstances, boolean ignite, int igniteTime, @Nullable Entity target, double radius) {
      if (!effectInstances.isEmpty() && !(radius <= 0.0)) {
         AABB area = this.getBoundingBox().inflate(radius, 2.0, radius);
         List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, area);
         if (!entities.isEmpty()) {
            Entity source = this.getEffectSource();

            for (LivingEntity entity : entities) {
               if (entity.isAffectedByPotions()) {
                  double distanceSqr = this.distanceToSqr(entity);
                  if (distanceSqr < 16.0) {
                     double d = entity == target ? 1.0 : 1.0 - Math.sqrt(distanceSqr) / radius;
                     this.applyAllEffects(effectInstances, entity, d, source, ignite, igniteTime);
                  }
               }
            }
         }
      }
   }

   public void applyAllEffects(List<MobEffectInstance> effectInstances, LivingEntity entity, double d, Entity source, boolean ignite, int igniteTime) {
      for (MobEffectInstance effect : effectInstances) {
         MobEffect mobEffect = effect.getEffect();
         if (mobEffect.isInstantenous()) {
            mobEffect.applyInstantenousEffect(this, this.getOwner(), entity, effect.getAmplifier(), d);
         } else {
            int adjustedDuration = effect.mapDuration(duration -> (int)(d * duration + 0.5));
            MobEffectInstance adjustedEffect = new MobEffectInstance(mobEffect, adjustedDuration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible());
            if (!adjustedEffect.endsWithin(20)) {
               entity.addEffect(adjustedEffect, source);
            }
         }
      }

      if (ignite && !entity.fireImmune()) {
         entity.setSecondsOnFire(igniteTime);
      }
   }
}
