package fr.heralisz.bunker.barricade;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * Barricade en bois façon Rust : posée au point exact cliqué, orientée selon le regard (angle libre, pas seulement
 * N/S/E/O). La collision est faite d'un segment (PartEntity) par rondin : ils arrêtent joueurs et balles.
 * 150 PV, s'abîme visuellement (4 états) et se brise à 0.
 */
public class BarricadeEntity extends Entity {
   public static final float MAX_HP = 150.0F;
   /** Hauteur de chaque rondin (px, sans la pointe) : partagée avec le rendu et la pose pour coller au modèle. */
   public static final float[] LOG_H = {35, 38, 36, 39, 37, 39.5F, 36.5F, 38, 35.5F};
   public static final int LOGS = LOG_H.length;
   /** Diamètre d'un rondin, écart entre deux rondins, hauteur de la pointe taillée (px). */
   public static final float LOG_D = 4.6F, LOG_STEP = 4.8F, TIP_H = 5.6F;
   /** Position des jambes de force le long de la barricade (px depuis le centre). */
   public static final float LEG_X = 16.0F;
   private static final int SEGMENTS = LOGS;
   private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(BarricadeEntity.class, EntityDataSerializers.FLOAT);
   private final BarricadePart[] parts = new BarricadePart[SEGMENTS];

   public BarricadeEntity(EntityType<? extends BarricadeEntity> type, Level level) {
      super(type, level);
      for (int i = 0; i < SEGMENTS; i++) {
         this.parts[i] = new BarricadePart(this, LOG_D / 16F, (LOG_H[i] + TIP_H) / 16F);
      }
      this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
   }

   @Override
   public void setId(int id) {
      super.setId(id);
      for (int i = 0; i < this.parts.length; i++) {
         this.parts[i].setId(id + i + 1);
      }
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(HP, MAX_HP);
   }

   public float hp() {
      return this.entityData.get(HP);
   }

   /** 0 = intacte ... 3 = très abîmée. */
   public int damageStage() {
      float r = this.hp() / MAX_HP;
      return r > 0.75F ? 0 : r > 0.5F ? 1 : r > 0.25F ? 2 : 3;
   }

   /** Décalage (en blocs) du centre du rondin i depuis le centre de la barricade, le long de celle-ci. */
   public static double logOffset(int i) {
      return (i - (LOGS - 1) / 2.0) * LOG_STEP / 16.0;
   }

   /** Place les segments le long de la barricade (perpendiculaire au regard de celui qui l'a posée). */
   private void placeParts() {
      Vec3 along = BarricadePlacement.along(this.getYRot());
      for (int i = 0; i < SEGMENTS; i++) {
         Vec3 p = this.position().add(along.scale(logOffset(i)));
         this.parts[i].setPos(p.x, p.y, p.z);
         this.parts[i].xo = this.parts[i].xOld = p.x;
         this.parts[i].yo = this.parts[i].yOld = p.y;
         this.parts[i].zo = this.parts[i].zOld = p.z;
      }
   }

   @Override
   public void tick() {
      super.tick();
      this.placeParts();
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.isInvulnerableTo(source) || this.level().isClientSide || this.isRemoved()) {
         return false;
      }
      // à la main / au corps à corps : ~10 coups pour la casser
      if (source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player) {
         amount = Math.max(amount, 15.0F);
      }
      float hp = this.hp() - amount;
      ServerLevel level = (ServerLevel) this.level();
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
         this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.6, 0.5, 0.6, 0.05);
      if (hp <= 0) {
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
            this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.9, 0.8, 0.9, 0.15);
         this.playSound(SoundEvents.WOOD_BREAK, 1.2F, 0.7F);
         this.discard();
      } else {
         this.entityData.set(HP, hp);
         this.level().playSound(null, this.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.8F + this.random.nextFloat() * 0.3F);
      }
      return true;
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean isMultipartEntity() {
      return true;
   }

   @Override
   public PartEntity<?>[] getParts() {
      return this.parts;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      if (tag.contains("Hp")) {
         this.entityData.set(HP, tag.getFloat("Hp"));
      }
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putFloat("Hp", this.hp());
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }
}
