package fr.heralisz.tactical.item.throwable.explode;

import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.entity.StickyGrenadeEntity;
import fr.heralisz.tactical.item.throwable.ThrowableType;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class StickyType {
   public static final ThrowableType<ExplodeThrowableData, StickyGrenadeEntity> STICKY = ThrowableType.Builder.<ExplodeThrowableData, StickyGrenadeEntity>of()
      .setFactory(StickyType::createEntity)
      .setSerializer(jsonElement -> (ExplodeThrowableData)CommonAssetsManager.GSON.fromJson(jsonElement, ExplodeThrowableData.class))
      .build();

   public static StickyGrenadeEntity createEntity(ItemStack stack, LivingEntity thrower, ExplodeThrowableData data) {
      // Fusion (heraliz_tactique) : créée par son type (usine : version serveur StickyGrenadeEntityServeur dans un monde serveur), puis
      // placée comme le faisait le constructeur « lanceur » d'origine (position, lanceur, durée de vie).
      StickyGrenadeEntity entity = StickyGrenadeEntity.TYPE.create(thrower.level());
      entity.lancePar(thrower, data.getEntityData().getLifeTime());
      float initialSpeed = (float)data.getInitialSpeed();
      if (thrower.isCrouching()) {
         initialSpeed = (float)(initialSpeed * (Double)ServerConfig.CROUCHING_INIT_SPEED_PERCENT.get());
      }

      entity.shootFromRotation(entity, thrower.getXRot(), thrower.getYRot(), 0.0F, initialSpeed, 1.0F);
      entity.setItem(stack);
      entity.setBaseData(data.getEntityData());
      entity.setDamage(data.getExplode().getDamage());
      entity.setRadius(data.getExplode().getRadius());
      entity.setDestroyBlocks(data.getExplode().isDestroyBlocks());
      entity.setExplodeDestroyMultiplier(data.getExplode().getDestroyMultiplier());
      entity.setTriggerOnExplode(data.getExplode().isTriggerOnExplode());
      entity.setScreenShakeTime(data.getExplode().getScreenShakeTime());
      entity.setScreenShakeAmplitude(data.getExplode().getScreenShakeAmplitude());
      return entity;
   }
}
