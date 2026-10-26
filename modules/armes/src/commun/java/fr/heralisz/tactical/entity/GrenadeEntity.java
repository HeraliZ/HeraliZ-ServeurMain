package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModEntities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PlayMessages.SpawnEntity;

public class GrenadeEntity extends ThrowableItemEntity {
   public static EntityType<GrenadeEntity> TYPE = Builder.<GrenadeEntity>of(ModEntities.GRENADE_USINE, MobCategory.MISC)
      .setShouldReceiveVelocityUpdates(true)
      .setTrackingRange(64)
      .setUpdateInterval(1)
      .setCustomClientFactory(GrenadeEntity::new)
      .sized(0.3F, 0.3F)
      .noSave()
      .noSummon()
      .fireImmune()
      .build("grenade_entity");
   private double damage = 18.0;
   private float radius = 4.5F;
   private boolean destroyBlocks = false;
   private float destroyMultiplier = 1.0F;
   private double screenShakeTime = 20.0;
   private double screenShakeAmplitude = 50.0;
   private boolean triggerOnExplode = false;
   boolean exploded = false;

   public GrenadeEntity(EntityType<? extends GrenadeEntity> type, LivingEntity entity, Level level, int lifeTime) {
      super(type, entity, level, lifeTime);
   }

   public GrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
      super(TYPE, entity, level, lifeTime);
   }

   public GrenadeEntity(SpawnEntity spawnEntity, Level level) {
      super(TYPE, level);
   }

   public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public void onDeath(HitResult hitResult) {
      this.exploded = true;
      Vec3 pos = hitResult == null ? this.position() : this.position().lerp(hitResult.getLocation(), 0.8);
      if (!this.level().isClientSide()) {
         if (!this.exploser(pos)) {
            return;
         }
      }

      super.onDeath(hitResult);
   }

   /**
    * Fusion (heraliz_tactique) : l'explosion (serveur). Neutre ici (jamais appelée côté client) ; GrenadeEntityServeur et
    * StickyGrenadeEntityServeur la redéfinissent avec le code d'origine. Rend false si l'explosion est annulée par un autre
    * mod (ForgeEventFactory.onExplosionStart) : comme avant, la grenade n'est alors pas retirée.
    */
   protected boolean exploser(Vec3 pos) {
      return true;
   }

   public boolean hurt(DamageSource pSource, float pAmount) {
      if (!this.level().isClientSide() && this.reactionEnChaine(pSource)) {
         return true;
      } else {
         return super.hurt(pSource, pAmount);
      }
   }

   /**
    * Fusion (heraliz_tactique) : réaction en chaîne (serveur) : une explosion proche fait exploser la grenade 3 ticks plus
    * tard (triggerOnExplode). Neutre ici ; redéfinie côté serveur avec le code d'origine.
    */
   protected boolean reactionEnChaine(DamageSource pSource) {
      return false;
   }

   public double getDamage() {
      return this.damage;
   }

   public void setDamage(double damage) {
      this.damage = damage;
   }

   public float getRadius() {
      return this.radius;
   }

   public void setRadius(float radius) {
      this.radius = radius;
   }

   public boolean isDestroyBlocks() {
      return this.destroyBlocks;
   }

   public void setDestroyBlocks(boolean destroyBlocks) {
      this.destroyBlocks = destroyBlocks;
   }

   public double getScreenShakeTime() {
      return this.screenShakeTime;
   }

   public void setScreenShakeTime(double screenShakeTime) {
      this.screenShakeTime = screenShakeTime;
   }

   public double getScreenShakeAmplitude() {
      return this.screenShakeAmplitude;
   }

   public void setScreenShakeAmplitude(double screenShakeAmplitude) {
      this.screenShakeAmplitude = screenShakeAmplitude;
   }

   public float getDestroyMultiplier() {
      return this.destroyMultiplier;
   }

   public void setExplodeDestroyMultiplier(float destroyMultiplier) {
      this.destroyMultiplier = destroyMultiplier;
   }

   public void setTriggerOnExplode(boolean triggerOnExplode) {
      this.triggerOnExplode = triggerOnExplode;
   }

   public boolean isTriggerOnExplode() {
      return this.triggerOnExplode;
   }
}
