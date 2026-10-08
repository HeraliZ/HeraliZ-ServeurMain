package com.hbm_m.block.entity.doors.variant;

import java.util.Locale;

public enum DoorModelType {
   LEGACY("legacy"),
   MODERN("modern");

   private final String id;

   DoorModelType(String id) {
      this.id = id;
   }

   public String getId() {
      return this.id;
   }

   public boolean isLegacy() {
      return this == LEGACY;
   }

   public boolean isModern() {
      return this == MODERN;
   }

   public static DoorModelType fromId(String id) {
      if (id != null && !id.isEmpty()) {
         try {
            return valueOf(id.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException e) {
            return LEGACY;
         }
      } else {
         return LEGACY;
      }
   }
}
