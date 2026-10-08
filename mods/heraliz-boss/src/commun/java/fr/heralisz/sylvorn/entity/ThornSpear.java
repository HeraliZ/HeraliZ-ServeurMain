package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.particle.ModParticles;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Javelot d'épines arraché du dos de Sylvorn : très rapide, trajectoire presque tendue (on l'évite en sortant de la ligne
 * annoncée), se plante dans le sol et y reste un moment, hérissé d'épines.
 */
public class ThornSpear extends ThrowableItemProjectile {
   static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(ThornSpear.class, EntityDataSerializers.BOOLEAN);
   private int stuckTicks;

   public ThornSpear(EntityType<? extends ThornSpear> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STUCK, false);
   }

   public boolean stuck() {
      return this.entityData.get(STUCK);
   }

   @Override
   protected Item getDefaultItem() {
      return Items.STICK;
   }

   @Override
   protected float getGravity() {
      return 0.012F;
   }

   @Override
   protected boolean canHitEntity(Entity e) {
      return !(e instanceof Sylvorn) && !(e instanceof SapNode) && !(e instanceof ThornSpear) && super.canHitEntity(e);
   }

   @Override
   public void tick() {
      if (stuck()) {
         // planté : ne bouge plus, reste 3 s puis tombe en poussière
         this.setDeltaMovement(Vec3.ZERO);
         if (!this.level().isClientSide && ++stuckTicks > 60) {
            ((ServerLevel) this.level()).sendParticles(ModParticles.WOOD_CHIP.get(),
               getX(), getY() + 0.5, getZ(), 8, 0.2, 0.4, 0.2, 0.05);
            this.discard();
         }
         return;
      }
      super.tick();
      if (this.level().isClientSide) {
         this.level().addParticle(ModParticles.LEAF.get(), getX(), getY(), getZ(), 0, -0.02, 0);
         if (this.tickCount % 2 == 0) this.level().addParticle(ModParticles.SPORE.get(), getX(), getY(), getZ(), 0, 0, 0);
      }
      if (!this.level().isClientSide && this.tickCount > 80) this.discard();
   }

   /** Coup ou plantage : dans ThornSpearServeur ; côté client (comme à l'origine) et sans partie serveur, rien. */
   @Override
   protected void onHit(HitResult r) {
   }
}
