package fr.heralisz.sylvorn.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Les jambes arrachées de Sylvorn : pendues par les chevilles aux deux branches de la voûte, elles se balancent en perdant leur
 * sang, puis l'Arbre les remonte dans l'ombre de ses frondaisons. (Rendu : le modèle de Sylvorn, jambes seules, tête en bas.)
 */
public class SylvornLegs extends Entity implements GeoEntity {
   public static final int LIFE = 200;
   static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(SylvornLegs.class, EntityDataSerializers.FLOAT);
   static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SylvornLegs.class, EntityDataSerializers.INT);
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public SylvornLegs(EntityType<? extends SylvornLegs> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
   }

   public float legsYaw() {
      return this.entityData.get(YAW);
   }

   /** Les branches (côté client) : elles partent des mêmes ancrages que celles qui tenaient Sylvorn. */
   public Sylvorn owner() {
      return this.level().getEntity(this.entityData.get(OWNER)) instanceof Sylvorn s ? s : null;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(YAW, 0F);
      this.entityData.define(OWNER, -1);
   }

   public float swing(float partial) {
      float t = this.tickCount + partial;
      return Mth.sin(t * 0.16F) * 14F * Math.max(0, 1 - t / 120F);
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 160 * 160;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
