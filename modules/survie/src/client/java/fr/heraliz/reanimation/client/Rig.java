package fr.heraliz.reanimation.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Rig {
   static final Vector3f HAND = new Vector3f(-1.0F, 10.0F, 0.0F);
   static final Vector3f LEFT_HAND = new Vector3f(1.0F, 10.0F, 0.0F);

   private Rig() {
   }

   static void aim(ModelPart part, Vector3f local, Vector3f target, float twist) {
      Vector3f d = new Vector3f(target).sub(part.x, part.y, part.z);
      Vector3f l = new Vector3f(local);
      if (!(d.lengthSquared() < 1.0E-6F) && !(l.lengthSquared() < 1.0E-6F)) {
         d.normalize();
         l.normalize();
         Quaternionf q = new Quaternionf().rotationTo(l, d);
         if (twist != 0.0F) {
            q.premul(new Quaternionf().rotationAxis(twist, d));
         }

         set(part, q);
      }
   }

   static float twistToward(ModelPart part, Vector3f local, Vector3f target, Vector3f partDir, Vector3f want) {
      Vector3f d = new Vector3f(target).sub(part.x, part.y, part.z);
      if (d.lengthSquared() < 1.0E-6F) {
         return 0.0F;
      }

      d.normalize();
      Vector3f a = new Quaternionf().rotationTo(new Vector3f(local).normalize(), d).transform(new Vector3f(partDir));
      a.sub(new Vector3f(d).mul(a.dot(d)));
      Vector3f w = new Vector3f(want).sub(new Vector3f(d).mul(want.dot(d)));
      return !(a.lengthSquared() < 1.0E-6F) && !(w.lengthSquared() < 1.0E-6F) ? (float)Math.atan2(d.dot(new Vector3f(a).cross(w)), a.dot(w)) : 0.0F;
   }

   static void aim(ModelPart part, Vector3f local, Vector3f target, float twist, float amount) {
      if (!(amount <= 0.0F)) {
         float x = part.xRot;
         float y = part.yRot;
         float z = part.zRot;
         aim(part, local, target, twist);
         if (amount < 1.0F) {
            Quaternionf from = new Quaternionf().rotationZYX(z, y, x);
            Quaternionf to = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
            set(part, from.slerp(to, amount));
         }
      }
   }

   static void set(ModelPart part, Quaternionf q) {
      Vector3f e = euler(q);
      part.xRot = e.x;
      part.yRot = e.y;
      part.zRot = e.z;
   }

   static Vector3f euler(Quaternionf q) {
      Matrix3f r = new Matrix3f().set(q);
      float y = (float)Math.asin(Mth.clamp(-r.m02(), -1.0F, 1.0F));
      float x = (float)Math.atan2(r.m12(), r.m22());
      float z = (float)Math.atan2(r.m01(), r.m00());
      return new Vector3f(x, y, z);
   }

   static Quaternionf rotation(ModelPart part) {
      return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
   }

   static Vector3f point(ModelPart part, Vector3f local) {
      return rotation(part).transform(new Vector3f(local)).add(part.x, part.y, part.z);
   }

   static void kneel(ModelPart rightLeg, ModelPart leftLeg, float amount, float spread) {
      rightLeg.xRot = Mth.lerp(amount, rightLeg.xRot, 1.1F);
      leftLeg.xRot = Mth.lerp(amount, leftLeg.xRot, 1.1F);
      rightLeg.yRot = Mth.lerp(amount, rightLeg.yRot, 0.1F);
      leftLeg.yRot = Mth.lerp(amount, leftLeg.yRot, -0.1F);
      rightLeg.zRot = Mth.lerp(amount, rightLeg.zRot, 0.1F + spread);
      leftLeg.zRot = Mth.lerp(amount, leftLeg.zRot, -0.1F - spread);
   }

   static float kneelDrop(float amount) {
      float a = 1.1F * amount;
      return Math.max(0.0F, 0.703F * (1.0F - Mth.cos(a)) - 0.117F * Mth.sin(a));
   }

   static void keepHeadUp(ModelPart head, float floorPx) {
      Quaternionf q = rotation(head);
      float low = -Float.MAX_VALUE;

      for (int i = 0; i < 8; i++) {
         Vector3f c = q.transform(new Vector3f((i & 1) == 0 ? -4.0F : 4.0F, (i & 2) == 0 ? -8.0F : 0.0F, (i & 4) == 0 ? -4.0F : 4.0F));
         low = Math.max(low, c.z);
      }

      head.z = head.z - Math.max(0.0F, low - floorPx);
   }

   static void walk(ModelPart rightLeg, ModelPart leftLeg, ModelPart rightArm, ModelPart leftArm, float phase, float amount, boolean arms) {
      if (!(amount <= 0.0F)) {
         rightLeg.xRot = Mth.lerp(amount, rightLeg.xRot, 0.6F * Mth.sin(phase));
         leftLeg.xRot = Mth.lerp(amount, leftLeg.xRot, -0.6F * Mth.sin(phase));
         if (arms) {
            rightArm.xRot = Mth.lerp(amount, rightArm.xRot, -0.4F * Mth.sin(phase));
            leftArm.xRot = Mth.lerp(amount, leftArm.xRot, 0.4F * Mth.sin(phase));
         }
      }
   }
}
