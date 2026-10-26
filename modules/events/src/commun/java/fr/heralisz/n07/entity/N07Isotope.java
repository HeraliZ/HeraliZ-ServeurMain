package fr.heralisz.n07.entity;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Isotope instable arraché aux tumeurs du Sujet N-07 : il monte, puis dérive vers sa cible (un peu moins vite qu'un joueur qui
 * sprinte) et explose à son contact. Une balle, une flèche ou un coup le fait éclater sans dégâts : il faut les abattre en vol.
 */
public class N07Isotope extends Entity {
   static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(N07Isotope.class, EntityDataSerializers.INT);
   public static final int LIFE = 240;

   public N07Isotope(EntityType<? extends N07Isotope> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(TARGET, 0);
   }

   /** 0..1 : il gonfle en montant, pulse de plus en plus vite en fin de vie. */
   public float strength(float partial) {
      float t = this.tickCount + partial;
      return Mth.clamp(t / 10F, 0.2F, 1F);
   }

   /** Côté client : braises. Le vol vers la cible et l'explosion sont dans N07IsotopeServeur (position synchronisée). */
   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (this.tickCount % 2 == 0) this.level().addParticle(ModParticles.RAD_EMBER.get(), getX(), getY() + 0.3, getZ(), 0, 0, 0);
      }
   }

   /** Abattu en vol : dans N07IsotopeServeur ; ici (côté client, ou sans partie serveur) rien, comme côté client à l'origine. */
   @Override
   public boolean hurt(DamageSource s, float amount) {
      return false;
   }

   @Override
   public boolean isPickable() {
      return true;
   }

   @Override
   public float getPickRadius() {
      return 0.4F;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 128 * 128;
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
