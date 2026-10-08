package fr.heralisz.tactical.entity;

import javax.annotation.ParametersAreNonnullByDefault;
import fr.heralisz.tactical.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Partie SERVEUR de {@link StickyGrenadeEntity} (le C4 et les explosifs collants ; docs/METHODE_CLIENT_SERVEUR.md §5.4) :
 * se coller au bloc ou à l'entité touchée, se décrocher (bloc détruit, porteur mort), retrouver le porteur après un
 * rechargement, et l'explosion / la réaction en chaîne de la grenade (GrenadeEntityServeur), code d'origine.
 * Créée par l'usine (StickyGrenadeEntity.USINE) dans les mondes serveur. CRac et HeraliZ Bases la lisent par son type
 * {@code heralisz_tactical:sticky_grenade_entity}, sa sauvegarde (« Item », ThrowableId) et ses méthodes publiques
 * getItem() / getStuckBlockPos() (inchangées, héritées).
 */
public class StickyGrenadeEntityServeur extends StickyGrenadeEntity {
   public StickyGrenadeEntityServeur(EntityType<StickyGrenadeEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected boolean exploser(Vec3 pos) {
      return GrenadeEntityServeur.explosion(this, pos);
   }

   @Override
   protected boolean reactionEnChaine(DamageSource pSource) {
      return GrenadeEntityServeur.reaction(this, pSource);
   }

   // StickyGrenadeEntity.tick (d'origine), branches serveur
   @Override
   protected void verifierSupport() {
      if (!this.level().isClientSide && this.stuckBlockPos != null) {
         BlockState state = this.level().getBlockState(this.stuckBlockPos);
         if (state.isAir()) {
            this.detach();
         }
      }
   }

   @Nullable
   @Override
   protected Entity retrouverPorteur() {
      Entity entity = null;
      if (!this.level().isClientSide && this.stuckEntityUUID != null && this.level() instanceof ServerLevel serverLevel) {
         entity = serverLevel.getEntity(this.stuckEntityUUID);
         if (entity != null) {
            this.entityData.set(STUCK_ENTITY_ID, entity.getId());
         }
      }

      return entity;
   }

   @Override
   protected void porteurPerdu() {
      if (!this.level().isClientSide) {
         this.detach();
      }
   }

   // StickyGrenadeEntity.onHit (d'origine)
   @ParametersAreNonnullByDefault
   @Override
   protected void onHit(HitResult result) {
      if (!(Boolean)this.entityData.get(STICKED)) {
         if (result.getType() != Type.MISS) {
            if (result.getType() == Type.BLOCK) {
               BlockHitResult blockResult = (BlockHitResult)result;
               BlockPos resultPos = blockResult.getBlockPos();
               BlockState state = this.level().getBlockState(resultPos);
               SoundEvent event = state.getBlock().getSoundType(state, this.level(), resultPos, this).getStepSound();
               this.level().playSound(null, result.getLocation().x, result.getLocation().y, result.getLocation().z, event, SoundSource.AMBIENT, 2.0F, 1.0F);
               this.level()
                  .playSound(
                     null,
                     result.getLocation().x,
                     result.getLocation().y,
                     result.getLocation().z,
                     (SoundEvent)ModSounds.GRENADE_BOUNCE.get(),
                     SoundSource.AMBIENT,
                     2.0F,
                     1.0F
                  );
               this.entityData.set(STICKED, true);
               this.entityData.set(STUCK_FACE, (byte)blockResult.getDirection().get3DDataValue());
               this.setNoGravity(true);
               this.setDeltaMovement(Vec3.ZERO);
               this.setPos(
                  result.getLocation()
                     .subtract(
                        -blockResult.getDirection().getStepX() * 0.15,
                        -blockResult.getDirection().getStepY() * 0.15 + 0.15,
                        -blockResult.getDirection().getStepZ() * 0.15
                     )
               );
               this.stuckBlockPos = resultPos;
               Vec3 normal = Vec3.atLowerCornerOf(blockResult.getDirection().getNormal());
               this.alignToVec(normal);
            } else if (result.getType() == Type.ENTITY) {
               EntityHitResult entityResult = (EntityHitResult)result;
               Entity entity = entityResult.getEntity();
               if (entity == this.getOwner() || entity == this.getVehicle()) {
                  return;
               }

               entity.hurt(entity.damageSources().thrown(this, this.getOwner()), this.getHitDamage());
               this.entityData.set(STICKED, true);
               this.entityData.set(STUCK_FACE, (byte)-1);
               this.setNoGravity(true);
               this.setDeltaMovement(result.getLocation().subtract(this.position()));
               this.stuckEntityUUID = entity.getUUID();
               this.entityData.set(STUCK_ENTITY_ID, entity.getId());
               Vec3 globalOffset = result.getLocation().subtract(entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0));
               this.alignToVec(globalOffset);
               float entityYRot = this.getEntityRotation(entity);
               Vec3 localOffset = globalOffset.yRot(entityYRot * (float) (Math.PI / 180.0)).scale(0.75);
               this.entityData.set(STUCK_OFFSET, new Rotations((float)localOffset.x, (float)localOffset.y, (float)localOffset.z));
               float relativeYaw = this.getYRot() - entityYRot;
               float relativePitch = this.getXRot();
               this.entityData.set(STUCK_ROTATION, new Rotations(relativePitch, relativeYaw, 0.0F));
            }
         }
      }
   }
}
