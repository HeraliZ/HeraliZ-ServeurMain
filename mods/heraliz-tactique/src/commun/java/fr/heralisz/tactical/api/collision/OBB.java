package fr.heralisz.tactical.api.collision;

import net.minecraft.world.phys.AABB;
import org.joml.Intersectionf;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record OBB(Vector3f center, Vector3f extents, Quaternionf rotation) {
   public void setCenter(Vector3f center) {
      this.center.set(center);
   }

   public void setExtents(Vector3f extents) {
      this.extents.set(extents);
   }

   public void setRotation(Quaternionf rotation) {
      this.rotation.set(rotation);
   }

   public Vector3f[] getVertices() {
      Vector3f[] vertices = new Vector3f[8];
      Vector3f[] localVertices = new Vector3f[]{
         new Vector3f(-this.extents.x, -this.extents.y, -this.extents.z),
         new Vector3f(this.extents.x, -this.extents.y, -this.extents.z),
         new Vector3f(this.extents.x, this.extents.y, -this.extents.z),
         new Vector3f(-this.extents.x, this.extents.y, -this.extents.z),
         new Vector3f(-this.extents.x, -this.extents.y, this.extents.z),
         new Vector3f(this.extents.x, -this.extents.y, this.extents.z),
         new Vector3f(this.extents.x, this.extents.y, this.extents.z),
         new Vector3f(-this.extents.x, this.extents.y, this.extents.z)
      };

      for (int i = 0; i < 8; i++) {
         Vector3f vertex = localVertices[i];
         vertex.rotate(this.rotation);
         vertex.add(this.center);
         vertices[i] = vertex;
      }

      return vertices;
   }

   public Vector3f[] getAxes() {
      Vector3f[] axes = new Vector3f[]{new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F)};
      this.rotation.transform(axes[0]);
      this.rotation.transform(axes[1]);
      this.rotation.transform(axes[2]);
      return axes;
   }

   public static boolean isColliding(OBB obb, OBB other) {
      Vector3f[] axes1 = obb.getAxes();
      Vector3f[] axes2 = other.getAxes();
      return Intersectionf.testObOb(obb.center(), axes1[0], axes1[1], axes1[2], obb.extents(), other.center(), axes2[0], axes2[1], axes2[2], other.extents());
   }

   public static boolean isColliding(OBB obb, AABB aabb) {
      Vector3f obbCenter = obb.center();
      Vector3f[] obbAxes = obb.getAxes();
      Vector3f obbHalfExtents = obb.extents();
      Vector3f aabbCenter = aabb.getCenter().toVector3f();
      Vector3f aabbHalfExtents = new Vector3f((float)(aabb.getXsize() / 2.0), (float)(aabb.getYsize() / 2.0), (float)(aabb.getZsize() / 2.0));
      return Intersectionf.testObOb(
         obbCenter.x,
         obbCenter.y,
         obbCenter.z,
         obbAxes[0].x,
         obbAxes[0].y,
         obbAxes[0].z,
         obbAxes[1].x,
         obbAxes[1].y,
         obbAxes[1].z,
         obbAxes[2].x,
         obbAxes[2].y,
         obbAxes[2].z,
         obbHalfExtents.x,
         obbHalfExtents.y,
         obbHalfExtents.z,
         aabbCenter.x,
         aabbCenter.y,
         aabbCenter.z,
         1.0F,
         0.0F,
         0.0F,
         0.0F,
         1.0F,
         0.0F,
         0.0F,
         0.0F,
         1.0F,
         aabbHalfExtents.x,
         aabbHalfExtents.y,
         aabbHalfExtents.z
      );
   }

   public boolean interactsWithAABB(AABB aabb) {
      return isColliding(this, aabb);
   }

   public static Vector3f getClosestPointOBB(Vector3f point, OBB obb) {
      Vector3f nearP = new Vector3f(obb.center());
      Vector3f dist = point.sub(nearP, new Vector3f());
      float[] extents = new float[]{obb.extents().x, obb.extents().y, obb.extents().z};
      Vector3f[] axes = obb.getAxes();

      for (int i = 0; i < 3; i++) {
         float distance = dist.dot(axes[i]);
         distance = Math.clamp(distance, -extents[i], extents[i]);
         nearP.x = nearP.x + distance * axes[i].x;
         nearP.y = nearP.y + distance * axes[i].y;
         nearP.z = nearP.z + distance * axes[i].z;
      }

      return nearP;
   }
}
