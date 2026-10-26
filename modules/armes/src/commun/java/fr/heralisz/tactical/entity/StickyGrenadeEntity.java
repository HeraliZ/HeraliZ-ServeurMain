package fr.heralisz.tactical.entity;

import fr.heralisz.tactical.init.ModEntities;
import java.util.UUID;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.network.PlayMessages.SpawnEntity;
import org.jetbrains.annotations.Nullable;

public class StickyGrenadeEntity extends GrenadeEntity {
   public static EntityType<StickyGrenadeEntity> TYPE = Builder.<StickyGrenadeEntity>of(ModEntities.STICKY_GRENADE_USINE, MobCategory.MISC)
      .setShouldReceiveVelocityUpdates(true)
      .setTrackingRange(64)
      .setUpdateInterval(1)
      .setCustomClientFactory(StickyGrenadeEntity::new)
      .sized(0.3F, 0.3F)
      .noSave()
      .noSummon()
      .fireImmune()
      .build("sticky_grenade_entity");
   static final EntityDataAccessor<Boolean> STICKED = SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.BOOLEAN);
   static final EntityDataAccessor<Integer> STUCK_ENTITY_ID = SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Rotations> STUCK_OFFSET = SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.ROTATIONS);
   static final EntityDataAccessor<Rotations> STUCK_ROTATION = SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.ROTATIONS);
   static final EntityDataAccessor<Byte> STUCK_FACE = SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.BYTE);
   @Nullable
   BlockPos stuckBlockPos;
   @Nullable
   UUID stuckEntityUUID;

   public StickyGrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
      super(TYPE, entity, level, lifeTime);
   }

   public StickyGrenadeEntity(SpawnEntity spawnEntity, Level level) {
      super(TYPE, level);
   }

   public StickyGrenadeEntity(EntityType<StickyGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STICKED, false);
      this.entityData.define(STUCK_ENTITY_ID, -1);
      this.entityData.define(STUCK_OFFSET, new Rotations(0.0F, 0.0F, 0.0F));
      this.entityData.define(STUCK_ROTATION, new Rotations(0.0F, 0.0F, 0.0F));
      this.entityData.define(STUCK_FACE, (byte)-1);
   }

   @Override
   public boolean shouldBounce() {
      return false;
   }

   @Override
   public void tick() {
      super.tick();
      if ((Boolean)this.entityData.get(STICKED)) {
         this.setDeltaMovement(Vec3.ZERO);
         this.setNoGravity(true);
         this.verifierSupport();

         int entityId = (Integer)this.entityData.get(STUCK_ENTITY_ID);
         if (entityId != -1) {
            Entity entity = this.level().getEntity(entityId);
            if (entity == null) {
               entity = this.retrouverPorteur();
            }

            if (entity != null && entity.isAlive()) {
               Rotations offsetRot = (Rotations)this.entityData.get(STUCK_OFFSET);
               Vec3 offset = new Vec3(offsetRot.getX(), offsetRot.getY(), offsetRot.getZ());
               float yRot = this.getEntityRotation(entity);
               Vec3 rotatedOffset = offset.yRot(-yRot * (float) (Math.PI / 180.0));
               Vec3 newPos = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).add(rotatedOffset);
               this.setPos(newPos);
               Rotations relativeRot = (Rotations)this.entityData.get(STUCK_ROTATION);
               this.setYRot(yRot + relativeRot.getY());
               this.setXRot(relativeRot.getX());
            } else {
               this.porteurPerdu();
            }
         }
      }
   }

   /** Fusion (heraliz_tactique) : collé à un bloc devenu air -> décroché (serveur ; code d'origine dans StickyGrenadeEntityServeur). */
   protected void verifierSupport() {
   }

   /** Fusion : porteur introuvable par son numéro -> recherché par son UUID (serveur). Neutre ici : null. */
   @Nullable
   protected Entity retrouverPorteur() {
      return null;
   }

   /** Fusion : porteur disparu ou mort -> décroché (serveur). Neutre ici. */
   protected void porteurPerdu() {
   }

   public Rotations getStuckOffset() {
      return (Rotations)this.entityData.get(STUCK_OFFSET);
   }

   @Nullable
   public Direction getStuckFace() {
      byte value = (Byte)this.entityData.get(STUCK_FACE);
      return value >= 0 && value <= 5 ? Direction.from3DDataValue(value) : null;
   }

   @Nullable
   public BlockPos getStuckBlockPos() {
      return this.stuckBlockPos;
   }

   protected void updateRotation() {
      if (!(Boolean)this.entityData.get(STICKED)) {
         super.updateRotation();
      }
   }

   void alignToVec(Vec3 vec) {
      if (!(vec.lengthSqr() < 1.0E-7)) {
         Vec3 normalized = vec.normalize();
         this.setYRot((float)(Mth.atan2(-normalized.x, normalized.z) * 180.0F / (float)Math.PI));
         this.setXRot((float)(-Mth.atan2(normalized.y, Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0F / (float)Math.PI));
         this.yRotO = this.getYRot();
         this.xRotO = this.getXRot();
      }
   }

   @Override
   public ThrowableItemEntity.BounceResult doMultiBounce(Vec3 deltaMovement) {
      if ((Boolean)this.entityData.get(STICKED)) {
         return new ThrowableItemEntity.BounceResult(this.position(), Vec3.ZERO);
      }

      Vec3 start = this.position();
      Vec3 end = start.add(deltaMovement);
      HitResult hitResult = this.getHitResult(start, end, deltaMovement, x$0 -> this.canHitEntity(x$0), this.level());
      if (hitResult.getType() != Type.MISS) {
         if (!this.level().isClientSide()) {
            this.onHit(hitResult);
         }

         return new ThrowableItemEntity.BounceResult(this.position(), Vec3.ZERO);
      } else {
         return new ThrowableItemEntity.BounceResult(end, deltaMovement);
      }
   }

   public void lerpTo(double pX, double pY, double pZ, float pYRot, float pXRot, int pPosRotationIncrements, boolean pTeleport) {
      if (!(Boolean)this.entityData.get(STICKED) || (Integer)this.entityData.get(STUCK_ENTITY_ID) == -1) {
         super.lerpTo(pX, pY, pZ, pYRot, pXRot, pPosRotationIncrements, pTeleport);
      }
   }

   void detach() {
      this.entityData.set(STICKED, false);
      this.entityData.set(STUCK_ENTITY_ID, -1);
      this.entityData.set(STUCK_FACE, (byte)-1);
      this.setNoGravity(false);
      this.stuckBlockPos = null;
      this.stuckEntityUUID = null;
   }

   /**
    * Fusion (heraliz_tactique) : se coller au bloc ou à l'entité touchée. N'est appelée que côté serveur (voir doMultiBounce) :
    * neutre ici ; code d'origine dans StickyGrenadeEntityServeur.onHit.
    */
   @ParametersAreNonnullByDefault
   @Override
   protected void onHit(HitResult result) {
   }

   float getEntityRotation(Entity entity) {
      return entity instanceof LivingEntity livingEntity ? livingEntity.yBodyRot : entity.getYRot();
   }
}
