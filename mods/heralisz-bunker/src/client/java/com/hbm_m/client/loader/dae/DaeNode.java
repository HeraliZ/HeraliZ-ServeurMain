package com.hbm_m.client.loader.dae;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class DaeNode {
   public final String name;
   public String colladaId;
   public final List<DaeTransform> transforms = new ArrayList<>();
   public final List<DaeNode> children = new ArrayList<>();
   public DaeMesh mesh;
   public ResourceLocation texture;

   public DaeNode(String name) {
      this.name = name;
   }

   public Matrix4f localMatrix() {
      return this.localMatrix(-1.0F, null);
   }

   public Matrix4f localMatrix(float time, DaeAnimation animation) {
      if (animation != null) {
         Map<String, DaeCurve> animChannels = animation.getChannels(this.name);
         if (animChannels == null && this.colladaId != null && !this.colladaId.equals(this.name)) {
            animChannels = animation.getChannels(this.colladaId);
         }

         if (animChannels != null && !animChannels.isEmpty()) {
            Matrix4f result = new Matrix4f();
            DaeCurve matrixCurve = animChannels.get("transform");
            if (matrixCurve != null) {
               return matrixFromCollada(matrixCurve.sample(time));
            }

            for (DaeTransform t : this.transforms) {
               result.mul(this.transformMatrix(t, time, animChannels));
            }

            return result;
         }
      }

      Matrix4f result = new Matrix4f();

      for (DaeTransform t : this.transforms) {
         result.mul(this.transformMatrix(t, -1.0F, null));
      }

      return result;
   }

   private Matrix4f transformMatrix(DaeTransform t, float time, Map<String, DaeCurve> animChannels) {
      float[] data = (float[])t.data.clone();
      if (animChannels != null) {
         switch (t.type) {
            case TRANSLATE: {
               DaeCurve wholex = animChannels.get(t.sid);
               if (wholex != null) {
                  float[] v = wholex.sample(time);
                  data[0] = v[0];
                  data[1] = v[1];
                  data[2] = v[2];
               }

               DaeCurve x = animChannels.get(t.sid + ".X");
               if (x != null) {
                  data[0] = x.sample(time)[0];
               }

               DaeCurve y = animChannels.get(t.sid + ".Y");
               if (y != null) {
                  data[1] = y.sample(time)[0];
               }

               DaeCurve z = animChannels.get(t.sid + ".Z");
               if (z != null) {
                  data[2] = z.sample(time)[0];
               }
               break; }
            case ROTATE: {
               DaeCurve angle = animChannels.get(t.sid + ".ANGLE");
               if (angle != null) {
                  data[3] = angle.sample(time)[0];
               } else {
                  DaeCurve wholex = animChannels.get(t.sid);
                  if (wholex != null) {
                     data[3] = wholex.sample(time)[0];
                  }
               }
               break; }
            case SCALE: {
               DaeCurve wholex = animChannels.get(t.sid);
               if (wholex != null) {
                  float[] v = wholex.sample(time);
                  data[0] = v[0];
                  data[1] = v[1];
                  data[2] = v[2];
               }

               DaeCurve x = animChannels.get(t.sid + ".X");
               if (x != null) {
                  data[0] = x.sample(time)[0];
               }

               DaeCurve y = animChannels.get(t.sid + ".Y");
               if (y != null) {
                  data[1] = y.sample(time)[0];
               }

               DaeCurve z = animChannels.get(t.sid + ".Z");
               if (z != null) {
                  data[2] = z.sample(time)[0];
               }
               break; }
            case MATRIX: {
               DaeCurve whole = animChannels.get(t.sid);
               if (whole != null) {
                  data = whole.sample(time);
               }
            }
         }
      }
      return switch (t.type) {
         case TRANSLATE -> new Matrix4f().translation(data[0], data[1], data[2]);
         case ROTATE -> new Matrix4f().rotation((float)Math.toRadians(data[3]), data[0], data[1], data[2]);
         case SCALE -> new Matrix4f().scale(data[0], data[1], data[2]);
         case MATRIX -> matrixFromCollada(data);
      };
   }

   private static Matrix4f matrixFromCollada(float[] m) {
      return new Matrix4f(m[0], m[4], m[8], m[12], m[1], m[5], m[9], m[13], m[2], m[6], m[10], m[14], m[3], m[7], m[11], m[15]);
   }
}
