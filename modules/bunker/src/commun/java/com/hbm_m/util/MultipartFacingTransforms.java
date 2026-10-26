package com.hbm_m.util;

import net.minecraft.core.Direction;

public final class MultipartFacingTransforms {
   private MultipartFacingTransforms() {
   }

   public static float legacyFacingRotationYDegrees(Direction facing) {
      return switch (facing) {
         case SOUTH -> 180.0F;
         case WEST -> 90.0F;
         case EAST -> 270.0F;
         default -> 0.0F;
      };
   }

   public static int vanillaChunkMeshRotationY(Direction facing) {
      return switch (facing) {
         case SOUTH -> 180;
         case WEST -> 270;
         case EAST -> 90;
         default -> 0;
      };
   }

   public static int advancedAssemblerBakedRotationY(Direction facing) {
      return normalizeDeg360(vanillaChunkMeshRotationY(facing) + 270);
   }

   public static int legacyBlockEntityBakedRotationY(Direction facing) {
      return normalizeDeg360(90 + Math.round(legacyFacingRotationYDegrees(facing)));
   }

   public static int chemicalPlantCanonicalRotationY(Direction facing) {
      int y = legacyBlockEntityBakedRotationY(facing);
      return facing != Direction.NORTH && facing != Direction.SOUTH ? y : normalizeDeg360(y + 180);
   }

   public static int chemicalPlantBakedRotationY(Direction facing) {
      return chemicalPlantCanonicalRotationY(facing);
   }

   public static int poseYawFromChunkYaw(int chunkYawDeg) {
      return normalizeDeg360(-chunkYawDeg);
   }

   public static int chemicalPlantPoseRotationY(Direction facing) {
      return poseYawFromChunkYaw(chemicalPlantCanonicalRotationY(facing));
   }

   @Deprecated
   public static int berChunkMeshRotationY(Direction facing) {
      return normalizeDeg360(90 + Math.round(legacyFacingRotationYDegrees(facing)));
   }

   @Deprecated
   public static int berChunkMeshRotationY(Direction facing, int extraOffsetDeg) {
      return normalizeDeg360(berChunkMeshRotationY(facing) + extraOffsetDeg);
   }

   public static int doorChunkMeshRotationY(Direction facing, int declOffsetDeg) {
      int facingDeg = switch (facing) {
         case SOUTH -> 0;
         case WEST -> 90;
         case EAST -> 270;
         default -> 180;
      };
      return normalizeDeg360(90 + facingDeg + declOffsetDeg);
   }

   public static int normalizeDeg360(int degrees) {
      int d = degrees % 360;
      if (d < 0) {
         d += 360;
      }

      return d;
   }
}
