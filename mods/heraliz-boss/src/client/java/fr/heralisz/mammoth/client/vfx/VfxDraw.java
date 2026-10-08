package fr.heralisz.mammoth.client.vfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Petites primitives de maillage (disques, anneaux, cylindres, sphères, rubans) en coordonnées relatives à la caméra. */
public final class VfxDraw {
   private VfxDraw() {}

   public static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, float u, float v, int argb) {
      vc.vertex(m, (float) x, (float) y, (float) z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24))
         .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
   }

   public static int argb(int rgb, float alpha) {
      return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
   }

   /** Disque horizontal texturé (u,v = carré de texture centré), rotation rot (radians). */
   public static void disc(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, float rot, int argb) {
      double c = Math.cos(rot) * r, s = Math.sin(rot) * r;
      v(vc, m, cx - c + s, cy, cz - s - c, 0, 0, argb);
      v(vc, m, cx - c - s, cy, cz - s + c, 0, 1, argb);
      v(vc, m, cx + c - s, cy, cz + s + c, 1, 1, argb);
      v(vc, m, cx + c + s, cy, cz + s - c, 1, 0, argb);
   }

   /** Anneau horizontal (r0 -> r1), texture étirée radialement (v 0 = intérieur). */
   public static void annulus(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r0, double r1, int seg, float uScroll, int argb) {
      for (int i = 0; i < seg; i++) {
         double a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
         float u0 = (float) i / seg * 4 + uScroll, u1 = (float) (i + 1) / seg * 4 + uScroll;
         v(vc, m, cx + Math.cos(a0) * r0, cy, cz + Math.sin(a0) * r0, u0, 0, argb);
         v(vc, m, cx + Math.cos(a1) * r0, cy, cz + Math.sin(a1) * r0, u1, 0, argb);
         v(vc, m, cx + Math.cos(a1) * r1, cy, cz + Math.sin(a1) * r1, u1, 1, argb);
         v(vc, m, cx + Math.cos(a0) * r1, cy, cz + Math.sin(a0) * r1, u0, 1, argb);
      }
   }

   /** Cylindre vertical ouvert (mur), v 1 = bas, 0 = haut ; alpha du haut réglable (fondu). */
   public static void wall(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, double h, int seg, float uRep, float vScroll, int argbBottom, int argbTop) {
      for (int i = 0; i < seg; i++) {
         double a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
         float u0 = (float) i / seg * uRep, u1 = (float) (i + 1) / seg * uRep;
         double x0 = cx + Math.cos(a0) * r, z0 = cz + Math.sin(a0) * r, x1 = cx + Math.cos(a1) * r, z1 = cz + Math.sin(a1) * r;
         v(vc, m, x0, cy, z0, u0, 1 + vScroll, argbBottom);
         v(vc, m, x1, cy, z1, u1, 1 + vScroll, argbBottom);
         v(vc, m, x1, cy + h, z1, u1, vScroll, argbTop);
         v(vc, m, x0, cy + h, z0, u0, vScroll, argbTop);
      }
   }

   /** Sphère (latitude/longitude). */
   public static void sphere(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r, int seg, float scroll, int argb) {
      int rings = seg / 2;
      for (int j = 0; j < rings; j++) {
         double t0 = Math.PI * j / rings - Math.PI / 2, t1 = Math.PI * (j + 1) / rings - Math.PI / 2;
         for (int i = 0; i < seg; i++) {
            double p0 = Mth.TWO_PI * i / seg, p1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = (float) i / seg * 3 + scroll, u1 = (float) (i + 1) / seg * 3 + scroll, v0 = (float) j / rings, v1 = (float) (j + 1) / rings;
            v(vc, m, cx + r * Math.cos(t0) * Math.cos(p0), cy + r * Math.sin(t0), cz + r * Math.cos(t0) * Math.sin(p0), u0, v0, argb);
            v(vc, m, cx + r * Math.cos(t0) * Math.cos(p1), cy + r * Math.sin(t0), cz + r * Math.cos(t0) * Math.sin(p1), u1, v0, argb);
            v(vc, m, cx + r * Math.cos(t1) * Math.cos(p1), cy + r * Math.sin(t1), cz + r * Math.cos(t1) * Math.sin(p1), u1, v1, argb);
            v(vc, m, cx + r * Math.cos(t1) * Math.cos(p0), cy + r * Math.sin(t1), cz + r * Math.cos(t1) * Math.sin(p0), u0, v1, argb);
         }
      }
   }

   /** Rectangle horizontal de longueur len le long de (dx,dz), largeur w ; v répété selon la longueur. */
   public static void strip(VertexConsumer vc, Matrix4f m, double x, double y, double z, double dx, double dz, double len, double w,
                            float v0, float v1, int argb) {
      double sx = -dz * w / 2, sz = dx * w / 2;
      double ex = x + dx * len, ez = z + dz * len;
      v(vc, m, x + sx, y, z + sz, 0, v0, argb);
      v(vc, m, x - sx, y, z - sz, 1, v0, argb);
      v(vc, m, ex - sx, y, ez - sz, 1, v1, argb);
      v(vc, m, ex + sx, y, ez + sz, 0, v1, argb);
   }

   /**
    * Prisme de cristal vertical (base (x,y,z), hauteur h corps + ht pointe, rayon r), incliné par la pose courante.
    * u = tour, v = hauteur (0 en haut).
    */
   public static void prism(VertexConsumer vc, Matrix4f m, double x, double y, double z, double h, double ht, double r, int sides, int argb) {
      for (int i = 0; i < sides; i++) {
         double a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
         double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r, x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;
         float u0 = (float) i / sides, u1 = (float) (i + 1) / sides;
         v(vc, m, x0, y, z0, u0, 1, argb);
         v(vc, m, x1, y, z1, u1, 1, argb);
         v(vc, m, x1, y + h, z1, u1, 0.3F, argb);
         v(vc, m, x0, y + h, z0, u0, 0.3F, argb);
         // pointe (quad dégénéré vers le sommet)
         v(vc, m, x0, y + h, z0, u0, 0.3F, argb);
         v(vc, m, x1, y + h, z1, u1, 0.3F, argb);
         v(vc, m, x, y + h + ht, z, (u0 + u1) / 2, 0, argb);
         v(vc, m, x, y + h + ht, z, (u0 + u1) / 2, 0, argb);
      }
   }

   /**
    * Éclat de glace : fût effilé et irrégulier (trois anneaux qui se resserrent et dérivent, pointe décentrée), facettes
    * inégales ; v va de 1 (pied givré) à 0 (pointe claire). seed fixe la forme.
    */
   public static void shard(VertexConsumer vc, Matrix4f m, double h, double r, int sides, float seed, int argb) {
      java.util.Random rnd = new java.util.Random((long) (seed * 7919));
      double[] ringY = {0, h * 0.42, h * 0.78};
      double[] ringR = {r, r * (0.62 + rnd.nextDouble() * 0.1), r * (0.28 + rnd.nextDouble() * 0.08)};
      double[][] off = new double[3][2];
      for (int k = 1; k < 3; k++) {
         off[k][0] = off[k - 1][0] + (rnd.nextDouble() - 0.5) * r * 0.35;
         off[k][1] = off[k - 1][1] + (rnd.nextDouble() - 0.5) * r * 0.35;
      }
      double tipX = off[2][0] * 1.4, tipZ = off[2][1] * 1.4;
      double[] ang = new double[sides + 1], jit = new double[sides];
      for (int i = 0; i < sides; i++) {
         ang[i] = (i + (rnd.nextDouble() - 0.5) * 0.5) * Mth.TWO_PI / sides;
         jit[i] = 0.8 + rnd.nextDouble() * 0.35;
      }
      ang[sides] = ang[0] + Mth.TWO_PI;
      float[] vv = {1F, 0.55F, 0.22F};
      for (int k = 0; k < 2; k++) {
         for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            double a0 = ang[i], a1 = ang[i + 1];
            float u0 = (float) i / sides, u1 = (float) (i + 1) / sides;
            v(vc, m, off[k][0] + Math.cos(a0) * ringR[k] * jit[i], ringY[k], off[k][1] + Math.sin(a0) * ringR[k] * jit[i], u0, vv[k], argb);
            v(vc, m, off[k][0] + Math.cos(a1) * ringR[k] * jit[j], ringY[k], off[k][1] + Math.sin(a1) * ringR[k] * jit[j], u1, vv[k], argb);
            v(vc, m, off[k + 1][0] + Math.cos(a1) * ringR[k + 1] * jit[j], ringY[k + 1], off[k + 1][1] + Math.sin(a1) * ringR[k + 1] * jit[j], u1, vv[k + 1], argb);
            v(vc, m, off[k + 1][0] + Math.cos(a0) * ringR[k + 1] * jit[i], ringY[k + 1], off[k + 1][1] + Math.sin(a0) * ringR[k + 1] * jit[i], u0, vv[k + 1], argb);
         }
      }
      for (int i = 0; i < sides; i++) {                                  // pointe
         int j = (i + 1) % sides;
         double a0 = ang[i], a1 = ang[i + 1];
         float u0 = (float) i / sides, u1 = (float) (i + 1) / sides;
         v(vc, m, off[2][0] + Math.cos(a0) * ringR[2] * jit[i], ringY[2], off[2][1] + Math.sin(a0) * ringR[2] * jit[i], u0, vv[2], argb);
         v(vc, m, off[2][0] + Math.cos(a1) * ringR[2] * jit[j], ringY[2], off[2][1] + Math.sin(a1) * ringR[2] * jit[j], u1, vv[2], argb);
         v(vc, m, tipX, h, tipZ, (u0 + u1) / 2, 0, argb);
         v(vc, m, tipX, h, tipZ, (u0 + u1) / 2, 0, argb);
      }
   }

   /** Secteur horizontal (cône au sol), de l'angle a0 à a1 (radians, 0 = +X), rayons r0 -> r1. */
   public static void sector(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, double r0, double r1, double a0, double a1, int seg, int argb) {
      for (int i = 0; i < seg; i++) {
         double b0 = a0 + (a1 - a0) * i / seg, b1 = a0 + (a1 - a0) * (i + 1) / seg;
         float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
         v(vc, m, cx + Math.cos(b0) * r0, cy, cz + Math.sin(b0) * r0, u0, 1, argb);
         v(vc, m, cx + Math.cos(b1) * r0, cy, cz + Math.sin(b1) * r0, u1, 1, argb);
         v(vc, m, cx + Math.cos(b1) * r1, cy, cz + Math.sin(b1) * r1, u1, 0, argb);
         v(vc, m, cx + Math.cos(b0) * r1, cy, cz + Math.sin(b0) * r1, u0, 0, argb);
      }
   }
}
