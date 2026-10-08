package fr.heralisz.n07.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Déforme et dessine un {@link MeshModel} avec les matrices d'os capturées pendant le rendu GeckoLib
 * (une capture par os et par image : la pose de la pile de matrices au moment où GeckoLib dessinerait ses cubes).
 */
public final class MeshSkin {
   private final MeshModel mesh;
   private final float[][] pose;      // 12 flottants par os : lignes de la matrice affine 3x4
   private final float[][] norm;      // 9 flottants par os : lignes de la matrice des normales
   private final boolean[] seen;
   private float[] px = new float[0], nx = new float[0];

   public MeshSkin(MeshModel mesh) {
      this.mesh = mesh;
      int n = mesh.bones.length;
      pose = new float[n][12];
      norm = new float[n][9];
      seen = new boolean[n];
   }

   public MeshModel mesh() {
      return mesh;
   }

   /** À appeler avant le rendu GeckoLib de chaque image. */
   public void begin() {
      java.util.Arrays.fill(seen, false);
   }

   /** Capture la matrice courante d'un os (appelée depuis renderCubesOfBone). */
   public void capture(String bone, PoseStack.Pose p, boolean hidden) {
      int i = mesh.bone(bone);
      if (i < 0) return;
      Matrix4f m = p.pose();
      Matrix3f r = p.normal();
      float[] a = pose[i], b = norm[i];
      // JOML : mCL = colonne C, ligne L ; on range par lignes (x' = a0 x + a1 y + a2 z + a3, etc.)
      a[0] = m.m00(); a[1] = m.m10(); a[2] = m.m20(); a[3] = m.m30();
      a[4] = m.m01(); a[5] = m.m11(); a[6] = m.m21(); a[7] = m.m31();
      a[8] = m.m02(); a[9] = m.m12(); a[10] = m.m22(); a[11] = m.m32();
      b[0] = r.m00(); b[1] = r.m10(); b[2] = r.m20();
      b[3] = r.m01(); b[4] = r.m11(); b[5] = r.m21();
      b[6] = r.m02(); b[7] = r.m12(); b[8] = r.m22();
      seen[i] = !hidden;
   }

   public boolean visible(String bone) {
      int i = mesh.bone(bone);
      return i >= 0 && seen[i];
   }

   /** Calcule les sommets déformés (à faire une fois par image, avant les passes de dessin). */
   public void skin() {
      int total = 0;
      for (MeshModel.Part p : mesh.parts) total += p.nv;
      if (px.length < total * 3) {
         px = new float[total * 3];
         nx = new float[total * 3];
      }
      int base = 0;
      for (MeshModel.Part p : mesh.parts) {
         if (drawable(p)) {
            for (int i = 0; i < p.nv; i++) {
               int o = (base + i) * 3;
               px[o] = px[o + 1] = px[o + 2] = 0;
               nx[o] = nx[o + 1] = nx[o + 2] = 0;
               add(p.b0[i], p.w0[i], p.q0, p.n0, i, o);
               if (p.ninf[i] > 1) add(p.b1[i], p.w1[i], p.q1, p.n1, i, o);
               float l = (float) Math.sqrt(nx[o] * nx[o] + nx[o + 1] * nx[o + 1] + nx[o + 2] * nx[o + 2]);
               if (l > 1e-6F) {
                  nx[o] /= l;
                  nx[o + 1] /= l;
                  nx[o + 2] /= l;
               }
            }
         }
         base += p.nv;
      }
   }

   private void add(int b, float w, float[] q, float[] n, int i, int o) {
      float[] a = pose[b], r = norm[b];
      float x = q[i * 3], y = q[i * 3 + 1], z = q[i * 3 + 2];
      px[o] += w * (a[0] * x + a[1] * y + a[2] * z + a[3]);
      px[o + 1] += w * (a[4] * x + a[5] * y + a[6] * z + a[7]);
      px[o + 2] += w * (a[8] * x + a[9] * y + a[10] * z + a[11]);
      float u = n[i * 3], v = n[i * 3 + 1], t = n[i * 3 + 2];
      nx[o] += w * (r[0] * u + r[1] * v + r[2] * t);
      nx[o + 1] += w * (r[3] * u + r[4] * v + r[5] * t);
      nx[o + 2] += w * (r[6] * u + r[7] * v + r[8] * t);
   }

   private boolean drawable(MeshModel.Part p) {
      for (int b : p.bones) if (!seen[b]) return false;
      return true;
   }

   /** Dessine toutes les parties visibles (triangles émis en quads dégénérés). */
   public void draw(VertexConsumer vc, float r, float g, float b, float a, int overlay, int light) {
      int base = 0;
      for (MeshModel.Part p : mesh.parts) {
         if (drawable(p)) {
            int[] t = p.tris;
            for (int k = 0; k < t.length; k += 3) {
               emit(vc, p, base, t[k], r, g, b, a, overlay, light);
               emit(vc, p, base, t[k + 1], r, g, b, a, overlay, light);
               emit(vc, p, base, t[k + 2], r, g, b, a, overlay, light);
               emit(vc, p, base, t[k + 2], r, g, b, a, overlay, light);
            }
         }
         base += p.nv;
      }
   }

   private void emit(VertexConsumer vc, MeshModel.Part p, int base, int i, float r, float g, float b, float a, int overlay, int light) {
      int o = (base + i) * 3;
      vc.vertex(px[o], px[o + 1], px[o + 2], r, g, b, a, p.u[i], p.v[i], overlay, light, nx[o], nx[o + 1], nx[o + 2]);
   }
}
