package fr.heraliz.reanimation.entity;

import java.util.Locale;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.level.Level;

public class BotEntity extends PathfinderMob {
   public static final String[] SKINS = new String[]{"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
   static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);

   public BotEntity(EntityType<? extends BotEntity> type, Level level) {
      super(type, level);
      this.setPersistenceRequired();
      this.setCanPickUpLoot(false);

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         this.setDropChance(slot, 2.0F);
      }
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32.0);
   }

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(DATA_SKIN, 0);
      this.entityData.define(DATA_MODE, 0);
   }

   public int skin() {
      return Math.floorMod((Integer)this.entityData.get(DATA_SKIN), SKINS.length);
   }

   public BotEntity.Mode mode() {
      return BotEntity.Mode.values()[Math.floorMod((Integer)this.entityData.get(DATA_MODE), BotEntity.Mode.values().length)];
   }

   public EntityDimensions getDimensions(Pose pose) {
      if (pose == Pose.SWIMMING) {
         return EntityDimensions.scalable(0.6F, 0.6F);
      } else {
         return pose == Pose.CROUCHING ? EntityDimensions.scalable(0.6F, 1.5F) : super.getDimensions(pose);
      }
   }

   protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
      if (pose == Pose.SWIMMING) {
         return 0.4F;
      } else {
         return pose == Pose.CROUCHING ? 1.27F : 1.62F;
      }
   }

   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   public enum Mode {
      PASSIVE,
      MEDIC,
      LOOTER,
      EXECUTIONER;

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static BotEntity.Mode byId(String id) {
         for (BotEntity.Mode m : values()) {
            if (m.id().equals(id)) {
               return m;
            }
         }

         return PASSIVE;
      }
   }
}
