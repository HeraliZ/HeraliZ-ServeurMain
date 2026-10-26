package fr.heraliz.reanimation.execution;

public final class Timeline {
   private static final float[] FRAPPE_ORBITALE = new float[]{
      0.0F, 0.0F, 20.0F, 12.0F, 40.0F, 24.0F, 58.0F, 34.0F, 70.0F, 42.0F, 90.0F, 60.0F, 96.0F, 62.0F, 118.0F, 88.0F
   };
   private static final float[] CHUTE_METEORE = new float[]{0.0F, 0.0F, 22.0F, 12.0F, 56.0F, 30.0F, 82.0F, 40.0F, 96.0F, 48.0F, 118.0F, 70.0F};
   private static final float[] COMBO_AERIEN = new float[]{
      0.0F, 0.0F, 32.0F, 16.0F, 50.0F, 26.0F, 53.0F, 28.0F, 62.0F, 34.0F, 82.0F, 44.0F, 88.0F, 46.0F, 91.0F, 48.0F, 96.0F, 53.0F, 118.0F, 75.0F
   };
   private static final float[] ARRACHE_AME = new float[]{
      0.0F, 0.0F, 22.0F, 13.0F, 36.0F, 22.0F, 39.0F, 24.0F, 82.0F, 56.0F, 94.0F, 70.0F, 96.0F, 72.0F, 118.0F, 94.0F
   };

   private Timeline() {
   }

   public static float old(ExecutionType type, float t) {
      float[] map = map(type);
      if (map == null) {
         return t;
      }

      if (t <= map[0]) {
         return map[1] + (t - map[0]);
      }

      for (int i = 2; i < map.length; i += 2) {
         if (t <= map[i]) {
            float k = (t - map[i - 2]) / (map[i] - map[i - 2]);
            return map[i - 1] + k * (map[i + 1] - map[i - 1]);
         }
      }

      return map[map.length - 1] + (t - map[map.length - 2]);
   }

   private static float[] map(ExecutionType type) {
      return switch (type) {
         case FRAPPE_ORBITALE -> FRAPPE_ORBITALE;
         case CHUTE_METEORE -> CHUTE_METEORE;
         case COMBO_AERIEN -> COMBO_AERIEN;
         case ARRACHE_AME -> ARRACHE_AME;
         default -> null;
      };
   }
}
