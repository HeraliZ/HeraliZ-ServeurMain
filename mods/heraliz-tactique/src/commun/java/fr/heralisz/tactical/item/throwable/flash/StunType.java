package fr.heralisz.tactical.item.throwable.flash;

import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.entity.StunGrenadeEntity;
import fr.heralisz.tactical.item.throwable.ThrowableType;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class StunType {
   public static final ThrowableType<StunThrowableData, StunGrenadeEntity> STUN = ThrowableType.Builder.<StunThrowableData, StunGrenadeEntity>of()
      .setFactory(StunType::createEntity)
      .setSerializer(jsonElement -> (StunThrowableData)CommonAssetsManager.GSON.fromJson(jsonElement, StunThrowableData.class))
      .build();

   public static StunGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, StunThrowableData data) {
      // Fusion (heraliz_tactique) : créée par son type (usine : version serveur StunGrenadeEntityServeur dans un monde serveur), puis
      // placée comme le faisait le constructeur « lanceur » d'origine (position, lanceur, durée de vie).
      StunGrenadeEntity entity = StunGrenadeEntity.TYPE.create(thrower.level());
      entity.lancePar(thrower, data.getEntityData().getLifeTime());
      float initialSpeed = (float)data.getInitialSpeed();
      if (thrower.isCrouching()) {
         initialSpeed = (float)(initialSpeed * (Double)ServerConfig.CROUCHING_INIT_SPEED_PERCENT.get());
      }

      entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
      entity.setItem(stack);
      entity.setBaseData(data.getEntityData());
      entity.setStunData(data.getStunData());
      return entity;
   }
}
