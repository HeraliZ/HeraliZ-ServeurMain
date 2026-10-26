package com.hbm_m.block.entity.doors.variant;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class DoorModelSelection {
   private final DoorModelType modelType;
   private final DoorSkin skin;
   public static final DoorModelSelection DEFAULT = new DoorModelSelection(DoorModelType.LEGACY, DoorSkin.DEFAULT);

   public DoorModelSelection(DoorModelType modelType, DoorSkin skin) {
      this.modelType = Objects.requireNonNull(modelType, "modelType cannot be null");
      this.skin = Objects.requireNonNull(skin, "skin cannot be null");
   }

   public static DoorModelSelection legacy() {
      return new DoorModelSelection(DoorModelType.LEGACY, DoorSkin.DEFAULT);
   }

   public static DoorModelSelection modern() {
      return new DoorModelSelection(DoorModelType.MODERN, DoorSkin.DEFAULT);
   }

   public static DoorModelSelection modern(DoorSkin skin) {
      return new DoorModelSelection(DoorModelType.MODERN, skin);
   }

   public static DoorModelSelection modern(String skinId) {
      return new DoorModelSelection(DoorModelType.MODERN, DoorSkin.of(skinId));
   }

   public DoorModelType getModelType() {
      return this.modelType;
   }

   public DoorSkin getSkin() {
      return this.skin;
   }

   public boolean isLegacy() {
      return this.modelType.isLegacy();
   }

   public boolean isModern() {
      return this.modelType.isModern();
   }

   public CompoundTag save(CompoundTag tag) {
      tag.putString("modelType", this.modelType.getId());
      tag.putString("skin", this.skin.getId());
      return tag;
   }

   public CompoundTag save() {
      return this.save(new CompoundTag());
   }

   public static DoorModelSelection load(CompoundTag tag) {
      if (!tag.contains("modelType")) {
         return DEFAULT;
      }

      DoorModelType type = DoorModelType.fromId(tag.getString("modelType"));
      String skinId = tag.getString("skin");
      DoorSkin skin = !skinId.isEmpty() && !"default".equals(skinId) ? DoorSkin.of(skinId) : DoorSkin.DEFAULT;
      return new DoorModelSelection(type, skin);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof DoorModelSelection other) ? false : this.modelType == other.modelType && this.skin.equals(other.skin);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.modelType, this.skin);
   }

   @Override
   public String toString() {
      return "DoorModelSelection{" + this.modelType.getId() + ", skin=" + this.skin.getId() + "}";
   }

   public Component getDisplayName(String doorId) {
      return (Component)(this.modelType.isLegacy() ? Component.translatable("door.model_type.hbm_m.legacy") : this.skin.getDisplayName(doorId));
   }
}
