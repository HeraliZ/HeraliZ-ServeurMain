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

/** Cosse de graines : retombe en cloche, se plante, gonfle en pulsant puis éclate en racines et spores. */
public class SeedPod extends ThrowableItemProjectile {
   public static final int FUSE = 18;
   static final EntityDataAccessor<Integer> LANDED = SynchedEntityData.defineId(SeedPod.class, EntityDataSerializers.INT);

   public SeedPod(EntityType<? extends SeedPod> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(LANDED, -1);
   }

   /** Tick d'atterrissage (-1 en vol). */
   public int landedAt() {
      return this.entityData.get(LANDED);
   }

   @Override
   protected Item getDefaultItem() {
      return Items.MOSS_BLOCK;
   }

   @Override
   protected float getGravity() {
      return 0.05F;
   }

   @Override
   protected boolean canHitEntity(Entity e) {
      return !(e instanceof Sylvorn) && !(e instanceof SapNode) && super.canHitEntity(e);
   }

   @Override
   public void tick() {
      if (landedAt() >= 0) {
         // planté : ne bouge plus, pulse, puis éclate
         this.baseTick();
         int t = this.tickCount - landedAt();
         if (this.level().isClientSide) {
            if (t % 3 == 0) this.level().addParticle(ModParticles.SPORE.get(), getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
         } else if (t >= FUSE) {
            burst((ServerLevel) this.level());
         }
         return;
      }
      super.tick();
      if (this.level().isClientSide) this.level().addParticle(ModParticles.SPORE.get(), getX(), getY(), getZ(), 0, 0, 0);
      if (!this.level().isClientSide && this.tickCount > 100) this.discard();
   }

   /** Éclosion (dégâts, ralentissement, poison, projection) : dans SeedPodServeur ; neutre ici (la cosse disparaît). */
   void burst(ServerLevel level) {
      this.discard();
   }
}
