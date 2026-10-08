package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModEntities;
import fr.heralisz.tactical.item.throwable.area.EffectCloudThrowableData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PlayMessages.SpawnEntity;
import org.jetbrains.annotations.Nullable;

public class EffectCloudGrenadeEntity extends ThrowableItemEntity {
   public static EntityType<EffectCloudGrenadeEntity> TYPE = Builder.<EffectCloudGrenadeEntity>of(ModEntities.EFFECT_GRENADE_USINE, MobCategory.MISC)
      .setShouldReceiveVelocityUpdates(true)
      .setTrackingRange(64)
      .setUpdateInterval(1)
      .setCustomClientFactory(EffectCloudGrenadeEntity::new)
      .sized(0.3F, 0.3F)
      .noSave()
      .noSummon()
      .fireImmune()
      .build("grenade_entity");
   EffectCloudThrowableData.CloudData cloudData;

   public EffectCloudGrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
      super(TYPE, entity, level, lifeTime);
   }

   public EffectCloudGrenadeEntity(SpawnEntity spawnEntity, Level level) {
      super(TYPE, level);
   }

   public EffectCloudGrenadeEntity(EntityType<EffectCloudGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public void onDeath(@Nullable HitResult hitResult) {
      Vec3 pos = hitResult == null ? this.position() : hitResult.getLocation();
      if (!this.level().isClientSide() && this.cloudData != null) {
         this.liberer(pos, hitResult);
      }

      super.onDeath(hitResult);
   }

   /**
    * Fusion (heraliz_tactique) : nuage d'effets ou éclaboussure (serveur). Neutre ici ; code d'origine dans
    * EffectCloudGrenadeEntityServeur.
    */
   protected void liberer(Vec3 pos, @Nullable HitResult hitResult) {
   }


   public EffectCloudThrowableData.CloudData getCloudData() {
      return this.cloudData;
   }

   public void setCloudData(EffectCloudThrowableData.CloudData cloudData) {
      this.cloudData = cloudData;
   }
}
