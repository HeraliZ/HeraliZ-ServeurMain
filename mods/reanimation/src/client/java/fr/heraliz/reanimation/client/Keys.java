package fr.heraliz.reanimation.client;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class Keys {
   private Keys() {
   }

   static float key(float t, float... keys) {
      return sample(t, keys, 2, 1, Keys.Ease.SMOOTH)[0];
   }

   static float linear(float t, float... keys) {
      return sample(t, keys, 2, 1, Keys.Ease.LINEAR)[0];
   }

   static Vector3f vec(float t, float... keys) {
      float[] v = sample(t, keys, 4, 3, Keys.Ease.SMOOTH);
      return new Vector3f(v[0], v[1], v[2]);
   }

   private static float[] sample(float t, float[] keys, int stride, int width, Keys.Ease ease) {
      float[] out = new float[width];
      int n = keys.length / stride;
      if (t <= keys[0]) {
         System.arraycopy(keys, 1, out, 0, width);
         return out;
      }

      for (int i = 0; i < n - 1; i++) {
         int a = i * stride;
         int b = a + stride;
         if (t < keys[b]) {
            float k = (t - keys[a]) / (keys[b] - keys[a]);
            if (ease == Keys.Ease.SMOOTH) {
               k = k * k * (3.0F - 2.0F * k);
            }

            for (int j = 0; j < width; j++) {
               out[j] = Mth.lerp(k, keys[a + 1 + j], keys[b + 1 + j]);
            }

            return out;
         }
      }

      System.arraycopy(keys, (n - 1) * stride + 1, out, 0, width);
      return out;
   }

   static float window(float t, float from, float to, float ramp) {
      return CoupDeGrace.smooth((t - from) / ramp) * (1.0F - CoupDeGrace.smooth((t - (to - ramp)) / ramp));
   }

   static float strike(float t, float start, float hit) {
      float k = Mth.clamp((t - start) / (hit - start), 0.0F, 1.0F);
      return k * k * k;
   }

   static float jolt(float t, float at, float duration, float frequency) {
      if (!(t < at) && !(t > at + duration)) {
         float k = (t - at) / duration;
         return Mth.sin((t - at) * frequency) * (1.0F - k) * (1.0F - k);
      } else {
         return 0.0F;
      }
   }

   enum Ease {
      LINEAR,
      SMOOTH;
   }
}
