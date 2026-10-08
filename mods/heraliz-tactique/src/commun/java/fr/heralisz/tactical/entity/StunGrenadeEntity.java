package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModEntities;
import fr.heralisz.tactical.item.throwable.flash.StunThrowableData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.PlayMessages.SpawnEntity;
import org.jetbrains.annotations.Nullable;

public class StunGrenadeEntity extends ThrowableItemEntity {
   public static EntityType<StunGrenadeEntity> TYPE = Builder.<StunGrenadeEntity>of(ModEntities.STUN_GRENADE_USINE, MobCategory.MISC)
      .setShouldReceiveVelocityUpdates(true)
      .setTrackingRange(64)
      .setUpdateInterval(1)
      .setCustomClientFactory(StunGrenadeEntity::new)
      .sized(0.3F, 0.3F)
      .noSave()
      .noSummon()
      .fireImmune()
      .build("stun_grenade_entity");
   private StunThrowableData.StunData data = new StunThrowableData.StunData();

   public StunGrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
      super(TYPE, entity, level, lifeTime);
   }

   public StunGrenadeEntity(SpawnEntity spawnEntity, Level level) {
      super(TYPE, level);
   }

   public StunGrenadeEntity(EntityType<StunGrenadeEntity> type, Level level) {
      super(type, level);
   }

   public StunThrowableData.StunData getData() {
      return this.data;
   }

   public void setStunData(StunThrowableData.StunData data) {
      this.data = data;
   }

   @Override
   public void onDeath(@Nullable HitResult hitResult) {
      if (!this.level().isClientSide()) {
         this.etourdir();
      }

      super.onDeath(hitResult);
   }

   /**
    * Fusion (heraliz_tactique) : aveuglement et assourdissement des entités proches (serveur). Neutre ici ; code d'origine
    * dans StunGrenadeEntityServeur.
    */
   protected void etourdir() {
   }

}
