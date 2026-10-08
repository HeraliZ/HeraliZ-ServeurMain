package fr.heralisz.tactical.item.throwable.area;

import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.entity.EffectCloudGrenadeEntity;
import fr.heralisz.tactical.item.throwable.ThrowableType;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class CloudType {
   public static final ThrowableType<EffectCloudThrowableData, EffectCloudGrenadeEntity> CLOUD = ThrowableType.Builder.<EffectCloudThrowableData, EffectCloudGrenadeEntity>of()
      .setFactory(CloudType::createEntity)
      .setSerializer(jsonElement -> (EffectCloudThrowableData)CommonAssetsManager.GSON.fromJson(jsonElement, EffectCloudThrowableData.class))
      .build();

   public static EffectCloudGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, EffectCloudThrowableData data) {
      // Fusion (heraliz_tactique) : créée par son type (usine : version serveur EffectCloudGrenadeEntityServeur dans un monde serveur), puis
      // placée comme le faisait le constructeur « lanceur » d'origine (position, lanceur, durée de vie).
      EffectCloudGrenadeEntity entity = EffectCloudGrenadeEntity.TYPE.create(thrower.level());
      entity.lancePar(thrower, data.getEntityData().getLifeTime());
      float initialSpeed = (float)data.getInitialSpeed();
      if (thrower.isCrouching()) {
         initialSpeed = (float)(initialSpeed * (Double)ServerConfig.CROUCHING_INIT_SPEED_PERCENT.get());
      }

      entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
      entity.setItem(stack);
      entity.setBaseData(data.getEntityData());
      entity.setCloudData(data.getCloudData());
      return entity;
   }
}
