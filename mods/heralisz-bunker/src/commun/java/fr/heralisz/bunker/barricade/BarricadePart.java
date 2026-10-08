package fr.heralisz.bunker.barricade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/** Segment de collision de la barricade : bloque les déplacements et reçoit les tirs (transmis à la barricade). */
public class BarricadePart extends PartEntity<BarricadeEntity> {
   private final EntityDimensions size;

   public BarricadePart(BarricadeEntity parent, float width, float height) {
      super(parent);
      this.size = EntityDimensions.fixed(width, height);
      this.refreshDimensions();
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      return this.size;
   }

   @Override
   public boolean canBeCollidedWith() {
      return true;
   }

   @Override
   public boolean isPickable() {
      return true;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      return this.getParent().hurt(source, amount);
   }

   @Override
   public boolean is(net.minecraft.world.entity.Entity entity) {
      return this == entity || this.getParent() == entity;
   }

   @Override
   protected void defineSynchedData() {
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
}
