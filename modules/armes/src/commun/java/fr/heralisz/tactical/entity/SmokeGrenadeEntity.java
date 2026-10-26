package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModEntities;
import fr.heralisz.tactical.init.ModParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PlayMessages.SpawnEntity;

public class SmokeGrenadeEntity extends ThrowableItemEntity {
   public static EntityType<SmokeGrenadeEntity> TYPE = Builder.<SmokeGrenadeEntity>of(ModEntities.SMOKE_GRENADE_USINE, MobCategory.MISC)
      .setShouldReceiveVelocityUpdates(true)
      .setTrackingRange(64)
      .setUpdateInterval(1)
      .setCustomClientFactory(SmokeGrenadeEntity::new)
      .sized(0.3F, 0.3F)
      .noSave()
      .noSummon()
      .fireImmune()
      .build("smoke_grenade_entity");

   public SmokeGrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
      super(TYPE, entity, level, lifeTime);
   }

   public SmokeGrenadeEntity(SpawnEntity spawnEntity, Level level) {
      super(TYPE, level);
   }

   public SmokeGrenadeEntity(EntityType<SmokeGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         if (this.tickCount >= 40) {
            double x = this.getX();
            double y = this.getY();
            double z = this.getZ();

            for (int i = 0; i < 16; i++) {
               double offsetX = this.random.triangle(0.0, 5.5);
               double offsetY = this.random.triangle(0.0, 4.5);
               double offsetZ = this.random.triangle(0.0, 5.5);
               this.level().addParticle((ParticleOptions)ModParticleTypes.SMOKE_CLOUD.get(), true, x + offsetX, y + offsetY, z + offsetZ, 0.0, 0.0, 0.0);
            }
         }
      } else if (this.tickCount == 40) {
         this.playThrowableSound("release", 16.0F, 1.0F);
      }
   }
}
