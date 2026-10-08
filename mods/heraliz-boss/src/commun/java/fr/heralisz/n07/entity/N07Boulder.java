package fr.heralisz.n07.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Bloc de béton arraché par le Sujet N-07 : tenu dans ses crochets (le boss le place à chaque tick), puis lancé en tir tendu.
 * Il s'écrase au premier obstacle (bloc ou joueur). Ne casse rien.
 */
public class N07Boulder extends Entity {
   private static final EntityDataAccessor<Boolean> THROWN = SynchedEntityData.defineId(N07Boulder.class, EntityDataSerializers.BOOLEAN);
   static final double G = 0.04;
   int thrownAt;

   public N07Boulder(EntityType<? extends N07Boulder> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(THROWN, false);
   }

   public boolean isHeld() {
      return !this.entityData.get(THROWN);
   }

   public boolean thrown() {
      return this.entityData.get(THROWN);
   }

   /** Tick du lancer (rendu : il tourne sur lui-même). */
   public int flightTicks() {
      return thrown() ? this.tickCount - thrownAt : 0;
   }

   public void hold(Vec3 p) {
      this.setPos(p.x, p.y - 0.6, p.z);
      this.setDeltaMovement(Vec3.ZERO);
   }

   /** Tir tendu qui arrive sur `aim` en `flight` ticks. */
   public void throwAt(Vec3 aim, int flight) {
      Vec3 from = this.position();
      this.setDeltaMovement((aim.x - from.x) / flight, (aim.y - from.y + 0.5 * G * flight * flight) / flight, (aim.z - from.z) / flight);
      this.entityData.set(THROWN, true);
      this.thrownAt = this.tickCount;
   }

   /** Côté client : poussière du bloc lancé. Le bloc tenu, le vol et l'impact sont dans N07BoulderServeur (position synchronisée). */
   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (thrown() && this.tickCount % 2 == 0) {
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultBlockState()), getX(), getY() + 0.6, getZ(), 0, 0, 0);
         }
      }
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 128 * 128;
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }
}
