package com.hbm_m.client.loader.dae;

public class DaeCurve {
   private final float[] times;
   private final float[][] values;
   private final boolean[] step;
   private final int stride;

   public DaeCurve(float[] times, float[][] values, boolean[] step, int stride) {
      this.times = times;
      this.values = values;
      this.step = step;
      this.stride = stride;
   }

   public float getStartTime() {
      return this.times.length > 0 ? this.times[0] : 0.0F;
   }

   public float getEndTime() {
      return this.times.length > 0 ? this.times[this.times.length - 1] : 0.0F;
   }

   public float[] sample(float time) {
      float[] result = new float[this.stride];
      if (this.times.length == 0) {
         return result;
      }

      if (this.times.length == 1) {
         return (float[])this.values[0].clone();
      }

      if (time <= this.times[0]) {
         return (float[])this.values[0].clone();
      }

      if (time >= this.times[this.times.length - 1]) {
         return (float[])this.values[this.times.length - 1].clone();
      }

      for (int i = 0; i < this.times.length - 1; i++) {
         if (time >= this.times[i] && time <= this.times[i + 1]) {
            if (!this.step[i] && !(this.times[i + 1] <= this.times[i])) {
               float t = (time - this.times[i]) / (this.times[i + 1] - this.times[i]);

               for (int j = 0; j < this.stride; j++) {
                  result[j] = this.values[i][j] + (this.values[i + 1][j] - this.values[i][j]) * t;
               }

               return result;
            }

            return (float[])this.values[i].clone();
         }
      }

      return (float[])this.values[this.times.length - 1].clone();
   }
}
