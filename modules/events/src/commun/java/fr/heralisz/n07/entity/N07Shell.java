package fr.heralisz.n07.entity;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Projectiles en cloche du Sujet N-07 : obus d'acide tiré par la vanne de sa cuve (SHELL) et gouttes de l'aspersion (DROP). */
public class N07Shell extends ThrowableItemProjectile {
   public static final int SHELL = 0, DROP = 1;
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(N07Shell.class, EntityDataSerializers.INT);
   private static final double G = 0.05;

   public N07Shell(EntityType<? extends N07Shell> type, Level level) {
      super(type, level);
   }

   public N07Shell(Level level, LivingEntity owner, int kind) {
      super(HeraliszN07.N07_SHELL.get(), owner, level);
      this.entityData.set(KIND, kind);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, SHELL);
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
      return Items.SLIME_BALL;
   }

   @Override
   protected float getGravity() {
      return (float) G;
   }

   @Override
   protected boolean canHitEntity(Entity e) {
      return !(e instanceof SujetN07) && !(e instanceof N07Shell) && !(e instanceof N07Isotope) && !(e instanceof N07Boulder) && super.canHitEntity(e);
   }

   @Override
   /** Vol en cloche (vanilla, des deux côtés) et traînée côté client ; l'impact et la durée de vie sont dans N07ShellServeur. */
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (kind() == SHELL) {
            this.level().addParticle(ModParticles.TOXIC_SMOKE.get(), getX(), getY(), getZ(), 0, 0.02, 0);
            this.level().addParticle(ModParticles.ACID_DROP.get(), getX(), getY(), getZ(), 0, 0, 0);
         } else if (this.tickCount % 2 == 0) {
            this.level().addParticle(ModParticles.ACID_DROP.get(), getX(), getY(), getZ(), 0, 0, 0);
         }
      }
   }
}
