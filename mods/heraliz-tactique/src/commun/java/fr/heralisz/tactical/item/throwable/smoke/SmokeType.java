package fr.heralisz.tactical.item.throwable.smoke;

import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.entity.SmokeGrenadeEntity;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heralisz.tactical.item.throwable.ThrowableType;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class SmokeType {
   public static final ThrowableType<ThrowableData, SmokeGrenadeEntity> SMOKE = ThrowableType.Builder.<ThrowableData, SmokeGrenadeEntity>of()
      .setFactory(SmokeType::createEntity)
      .setSerializer(jsonElement -> (ThrowableData)CommonAssetsManager.GSON.fromJson(jsonElement, ThrowableData.class))
      .build();

   public static SmokeGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, ThrowableData data) {
      // Fusion (heraliz_tactique) : créée par son type (usine : version serveur SmokeGrenadeEntityServeur dans un monde serveur), puis
      // placée comme le faisait le constructeur « lanceur » d'origine (position, lanceur, durée de vie).
      SmokeGrenadeEntity entity = SmokeGrenadeEntity.TYPE.create(thrower.level());
      entity.lancePar(thrower, data.getEntityData().getLifeTime());
      float initialSpeed = (float)data.getInitialSpeed();
      if (thrower.isCrouching()) {
         initialSpeed = (float)(initialSpeed * (Double)ServerConfig.CROUCHING_INIT_SPEED_PERCENT.get());
      }

      entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
      entity.setItem(stack);
      entity.setBaseData(data.getEntityData());
      return entity;
   }
}
