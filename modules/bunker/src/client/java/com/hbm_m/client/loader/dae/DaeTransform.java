package com.hbm_m.client.loader.dae;

public class DaeTransform {
   public final DaeTransform.Type type;
   public final String sid;
   public final float[] data;

   public DaeTransform(DaeTransform.Type type, String sid, float[] data) {
      this.type = type;
      this.sid = sid != null ? sid : "";
      this.data = data;
   }

   public enum Type {
      TRANSLATE,
      ROTATE,
      SCALE,
      MATRIX;
   }
}
