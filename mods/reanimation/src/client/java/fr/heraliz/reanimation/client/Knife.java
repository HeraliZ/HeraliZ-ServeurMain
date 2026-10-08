package fr.heraliz.reanimation.client;

import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Knife {
   final Blade blade;
   Knife.Holder holder = Knife.Holder.HIDDEN;
   float spin;
   final Quaternionf wrist = new Quaternionf();
   Knife.Part part = Knife.Part.BODY;
   final Matrix4f matrix = new Matrix4f();
   float open = 1.0F;
   float scale = 1.0F;
   final Vector3f shift = new Vector3f();
   float blood;
   float trail;
   private static final Matrix4f ITEM_IN_ARM = new Matrix4f();
   private static final Matrix4f ARM_IN_ITEM = new Matrix4f(ITEM_IN_ARM).invert();

   Knife(Blade blade) {
      this.blade = blade;
   }

   Knife hand(float spin, @Nullable Quaternionf wrist) {
      return this.hand(spin, wrist, null);
   }

   Knife hand(float spin, @Nullable Quaternionf wrist, @Nullable Vector3f shift) {
      this.holder = Knife.Holder.HAND;
      this.spin = spin;
      if (wrist != null) {
         this.wrist.set(wrist);
      } else {
         this.wrist.identity();
      }

      if (shift != null) {
         this.shift.set(shift);
      } else {
         this.shift.zero();
      }

      return this;
   }

   Knife stuck(Knife.Part part, Vector3f tipPx, Vector3f dir, Vector3f axis) {
      this.holder = Knife.Holder.VICTIM;
      this.part = part;
      Vector3f d = new Vector3f(dir).normalize();
      Vector3f a = new Vector3f(axis);
      orthonormalize(d, a);
      this.matrix.set(placedMatrix(this.blade, new Vector3f(tipPx).div(16.0F), d, a, this.scale));
      return this;
   }

   Knife stuck(Knife.Part part, Matrix4f itemInPart) {
      this.holder = Knife.Holder.VICTIM;
      this.part = part;
      this.matrix.set(itemInPart);
      return this;
   }

   Knife scene(Matrix4f itemInScene) {
      this.holder = Knife.Holder.SCENE;
      this.matrix.set(itemInScene);
      return this;
   }

   Knife hidden() {
      this.holder = Knife.Holder.HIDDEN;
      return this;
   }

   static void applyItemInHand(Matrix4f m) {
      m.rotateX((float)Math.toRadians(-90.0));
      m.rotateY((float)Math.toRadians(180.0));
      m.translate(0.0625F, 0.125F, -0.625F);
   }

   static Matrix4f armInItem() {
      return new Matrix4f(ARM_IN_ITEM);
   }

   static Quaternionf handRotation(Blade blade, float spin, Quaternionf wrist) {
      return new Quaternionf(wrist).mul(new Quaternionf().rotationAxis(spin, blade.axis));
   }

   static Vector3f inHand(Blade blade, Vector3f point, float spin, Quaternionf wrist) {
      return inHand(blade, point, spin, wrist, 1.0F);
   }

   static Vector3f inHand(Blade blade, Vector3f point, float spin, Quaternionf wrist, float scale) {
      return handRotation(blade, spin, wrist).transform(new Vector3f(point).sub(blade.pivot).mul(scale)).add(blade.pivot);
   }

   static Vector3f inHand(Blade blade, Vector3f point, float spin, Quaternionf wrist, float scale, Vector3f shift) {
      return inHand(blade, point, spin, wrist, scale).add(shift);
   }

   static Vector3f regrip(Blade blade, Vector3f grip, float spin, Quaternionf wrist, float scale) {
      return new Vector3f(grip).sub(inHand(blade, grip, spin, wrist, scale));
   }

   static Matrix4f heldMatrix(Blade blade, float spin, Quaternionf wrist) {
      return heldMatrix(blade, spin, wrist, 1.0F);
   }

   static Matrix4f heldMatrix(Blade blade, float spin, Quaternionf wrist, float scale) {
      return heldMatrix(blade, spin, wrist, scale, new Vector3f());
   }

   static Matrix4f heldMatrix(Blade blade, float spin, Quaternionf wrist, float scale, Vector3f shift) {
      Vector3f p = new Vector3f(blade.pivot).div(16.0F);
      Matrix4f m = new Matrix4f()
         .translate(shift.x / 16.0F, shift.y / 16.0F, shift.z / 16.0F)
         .translate(p)
         .rotate(handRotation(blade, spin, wrist))
         .scale(scale)
         .translate(-p.x, -p.y, -p.z);
      applyItemInHand(m);
      return m;
   }

   static Matrix4f placedMatrix(Blade blade, Vector3f tip, Vector3f dir, Vector3f axis, float scale) {
      Vector3f d0 = new Vector3f(blade.tip).sub(blade.pivot).normalize();
      Vector3f a0 = new Vector3f(blade.axis);
      orthonormalize(d0, a0);
      Vector3f c0 = new Vector3f(d0).cross(a0);
      Vector3f c1 = new Vector3f(dir).cross(axis);
      Matrix4f from = new Matrix4f().set(d0.x, d0.y, d0.z, 0.0F, a0.x, a0.y, a0.z, 0.0F, c0.x, c0.y, c0.z, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
      Matrix4f to = new Matrix4f().set(dir.x, dir.y, dir.z, 0.0F, axis.x, axis.y, axis.z, 0.0F, c1.x, c1.y, c1.z, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
      Matrix4f rot = to.mul(from.transpose());
      Vector3f tip0 = new Vector3f(blade.tip).div(16.0F);
      return new Matrix4f().translate(tip).mul(rot).scale(scale).translate(-tip0.x, -tip0.y, -tip0.z).mul(ITEM_IN_ARM);
   }

   static Vector3f through(Matrix4f itemMatrix, Vector3f armPointPx) {
      Vector3f item = ARM_IN_ITEM.transformPosition(new Vector3f(armPointPx).div(16.0F));
      return itemMatrix.transformPosition(item);
   }

   static Matrix4f partMatrix(ModelPart part) {
      return new Matrix4f()
         .translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F)
         .rotate(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
   }

   static Matrix4f sceneFromModel(CoupDeGrace.Body body) {
      return new Matrix4f().translate(body.pos).rotate(body.rot).scale(-0.9375F, -0.9375F, 0.9375F).translate(0.0F, -1.501F, 0.0F);
   }

   static Vector3f sceneDirToPart(Vector3f sceneDir, CoupDeGrace.Body body, ModelPart part) {
      Vector3f d = body.rot.transformInverse(new Vector3f(sceneDir));
      d.set(-d.x, -d.y, d.z);
      return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transformInverse(d);
   }

   static Matrix4f heldInScene(CoupDeGrace.Body body, ModelPart rightArm, Blade blade, float spin, Quaternionf wrist) {
      return heldInScene(body, rightArm, blade, spin, wrist, 1.0F);
   }

   static Matrix4f heldInScene(CoupDeGrace.Body body, ModelPart rightArm, Blade blade, float spin, Quaternionf wrist, float scale) {
      return heldInScene(body, rightArm, blade, spin, wrist, scale, new Vector3f());
   }

   static Matrix4f heldInScene(CoupDeGrace.Body body, ModelPart rightArm, Blade blade, float spin, Quaternionf wrist, float scale, Vector3f shift) {
      return sceneFromModel(body).mul(partMatrix(rightArm)).mul(heldMatrix(blade, spin, wrist, scale, shift));
   }

   static Quaternionf grip(Blade blade, Vector3f dir, float roll) {
      return grip(blade, 0.0F, dir, roll);
   }

   static Quaternionf grip(Blade blade, float spin, Vector3f dir, float roll) {
      Vector3f d0 = new Quaternionf().rotationAxis(spin, blade.axis).transform(new Vector3f(blade.tip).sub(blade.pivot).normalize());
      Vector3f d1 = new Vector3f(dir).normalize();
      return new Quaternionf().rotationAxis(roll, d1).mul(new Quaternionf().rotationTo(d0, d1));
   }

   static Matrix4f flight(Blade blade, Matrix4f a, Matrix4f b, float u, float arc, float turns) {
      Vector3f pa = through(a, blade.pivot);
      Vector3f pb = through(b, blade.pivot);
      Vector3f p = new Vector3f(pa).lerp(pb, u).add(0.0F, 4.0F * arc * u * (1.0F - u), 0.0F);
      Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf());
      Quaternionf qb = b.getUnnormalizedRotation(new Quaternionf());
      float scale = a.getScale(new Vector3f()).x;
      Vector3f pivotItem = ARM_IN_ITEM.transformPosition(new Vector3f(blade.pivot).div(16.0F));
      Vector3f axisItem = ARM_IN_ITEM.transformDirection(new Vector3f(blade.axis)).normalize();
      Quaternionf q = qa.slerp(qb, u).mul(new Quaternionf().rotationAxis((float) (Math.PI * 2) * turns * u, axisItem));
      return new Matrix4f().translate(p).rotate(q).scale(scale).translate(-pivotItem.x, -pivotItem.y, -pivotItem.z);
   }

   static void orthonormalize(Vector3f dir, Vector3f axis) {
      axis.sub(new Vector3f(dir).mul(axis.dot(dir)));
      if (axis.lengthSquared() < 1.0E-6F) {
         axis.set(Math.abs(dir.x) < 0.9F ? 1.0F : 0.0F, Math.abs(dir.x) < 0.9F ? 0.0F : 1.0F, 0.0F);
         axis.sub(new Vector3f(dir).mul(axis.dot(dir)));
      }

      axis.normalize();
   }

   static {
      applyItemInHand(ITEM_IN_ARM);
   }

   enum Holder {
      HIDDEN,
      HAND,
      VICTIM,
      SCENE;
   }

   enum Part {
      HEAD,
      BODY,
      RIGHT_ARM,
      LEFT_ARM,
      RIGHT_LEG,
      LEFT_LEG;

      ModelPart of(HumanoidModel<?> m) {
         return switch (this) {
            case HEAD -> m.head;
            case BODY -> m.body;
            case RIGHT_ARM -> m.rightArm;
            case LEFT_ARM -> m.leftArm;
            case RIGHT_LEG -> m.rightLeg;
            case LEFT_LEG -> m.leftLeg;
         };
      }
   }
}
