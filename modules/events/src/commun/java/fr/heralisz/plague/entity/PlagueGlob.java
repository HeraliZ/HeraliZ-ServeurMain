package fr.heralisz.plague.entity;

import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.particle.ModParticles;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Projectiles du Porteur de Peste lancés en cloche : grenade à gaz (CANISTER) et pustule arrachée (PUSTULE).
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.4) : genre synchronisé, vol en cloche (des deux côtés), traînée côté client.
 * La durée de vie et l'impact (gaz, dégâts, par le Porteur) sont dans PlagueGlobServeur.
 */
public class PlagueGlob extends ThrowableItemProjectile {
   public static final int CANISTER = 0, PUSTULE = 1;
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(PlagueGlob.class, EntityDataSerializers.INT);
   private static final double G = 0.05;

   public PlagueGlob(EntityType<? extends PlagueGlob> type, Level level) {
      super(type, level);
   }

   public PlagueGlob(Level level, LivingEntity owner, int kind) {
      super(HeraliszPlague.PLAGUE_GLOB.get(), owner, level);
      this.entityData.set(KIND, kind);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, CANISTER);
   }

   public int kind() {
      return this.entityData.get(KIND);
   }

   /** Tir en cloche qui retombe sur `aim` en `flight` ticks (gravité 0,05, frottement 0,99). */
   public void launch(Vec3 from, Vec3 aim, int flight) {
      this.setPos(from.x, from.y, from.z);
      double f = (1 - Math.pow(0.99, flight)) / (1 - 0.99);
      this.setDeltaMovement((aim.x - from.x) / f, (aim.y - from.y + 0.5 * G * flight * flight) / f, (aim.z - from.z) / f);
   }

   @Override
   protected Item getDefaultItem() {
      return kind() == CANISTER ? Items.GREEN_DYE : Items.FERMENTED_SPIDER_EYE;
   }

   @Override
   protected float getGravity() {
      return (float) G;
   }

   @Override
   protected boolean canHitEntity(Entity e) {
      return !(e instanceof PlagueBearer) && !(e instanceof PlagueGlob) && !(e instanceof PlagueSwarm) && !(e instanceof Zombie) && super.canHitEntity(e);
   }

   /** Vol en cloche (des deux côtés, pour un mouvement fluide) et traînée côté client ; la durée de vie et l'impact sont dans PlagueGlobServeur. */
   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (kind() == CANISTER) this.level().addParticle(ModParticles.TOXIC_SMOKE.get(), getX(), getY(), getZ(), 0, 0.02, 0);
         else if (this.tickCount % 2 == 0) this.level().addParticle(ModParticles.ACID_DROP.get(), getX(), getY(), getZ(), 0, 0, 0);
      }
   }
}
