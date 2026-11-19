package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.fract;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/**
 * 211 Abysses sur un sac : le sac devient une méduse. Huit filaments bioluminescents pendent du bas du dos du sac et
 * ondulent vers l'arrière comme des tentacules dans un courant (mêmes pulsations que sur l'arme) ; du plancton lumineux
 * flotte autour. Tout est additif (shader fx_211), aucun objet créé dans les boucles.
 */
final class BagAbysses implements BagEffects.BagEffect {
   private static final int FILAMENTS = 8, SEGMENTS = 20;

   private final float[] pts = new float[(SEGMENTS + 1) * 3], w = new float[SEGMENTS + 1], p = new float[3];

   @Override
   public void build(FxContext c, BagFrame b) {
      float t = c.t, k = b.scale;
      int nseg = c.quality >= 0.6F ? SEGMENTS : 14;
      V up = b.up, out = b.out, side = b.side;
      for (int f = 0; f < FILAMENTS; f++) {
         float fx = -0.75F + 1.5F * (f + 0.5F) / FILAMENTS + (hash(f, 3, 1) - 0.5F) * 0.1F;
         // départ : bas du dos du sac (sa vraie surface), à défaut le bas de sa boîte
         V base = b.back(fx, -0.8F, 0.01F * k);
         if (base == null) base = b.at(fx, -0.95F, 0.9F);
         // vers le bas et vers l'arrière (le courant), un peu vers l'extérieur sur les côtés ; onde sur deux axes
         float dx = -up.x * 0.8F + out.x * 0.55F + side.x * fx * 0.25F;
         float dy = -up.y * 0.8F + out.y * 0.55F + side.y * fx * 0.25F;
         float dz = -up.z * 0.8F + out.z * 0.55F + side.z * fx * 0.25F;
         float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
         dx /= dl; dy /= dl; dz /= dl;
         // seconde perpendiculaire : direction × côté
         float qx = dy * side.z - dz * side.y, qy = dz * side.x - dx * side.z, qz = dx * side.y - dy * side.x;
         float len = (0.5F + 0.3F * hash(f, 5, 2)) * k;
         float ph = hash(f, 7, 3) * TAU;
         for (int i = 0; i <= nseg; i++) {
            float q = (float) i / nseg;
            float onde = sin(q * TAU * 1.2F - t * 1.25F + ph) * 0.08F * k * q;
            float onde2 = cos(q * TAU * 0.8F - t * 0.9F + ph * 1.7F) * 0.06F * k * q;
            int o = i * 3;
            this.pts[o] = base.x + dx * len * q + side.x * onde + qx * onde2;
            this.pts[o + 1] = base.y + dy * len * q + side.y * onde + qy * onde2;
            this.pts[o + 2] = base.z + dz * len * q + side.z * onde + qz * onde2;
            this.w[i] = (0.018F * (1 - 0.7F * q) + 0.0036F) * k;
         }
         c.mesh.set(ADD, FxAbysses.FILAMENT).p(fract(ph), hash(f, 9, 4)).seed(f, 0);
         FxDraw.ribbon(c, this.pts, this.w, nseg + 1, 0, 1);
      }
      // plancton autour du sac et derrière lui
      int np = c.count(24, 10);
      for (int j = 0; j < np; j++) {
         float fx = (hash(j, 11, 1) - 0.5F) * 3.6F, fy = -1.6F + 3.0F * hash(j, 11, 2), fz = 1.2F + 2.8F * hash(j, 11, 3);
         float ox = 0.03F * k * sin(t * 0.37F + j), oy = 0.04F * k * sin(t * 0.29F + j * 1.7F), oz = 0.03F * k * cos(t * 0.31F + j * 0.9F);
         this.p[0] = b.o.x + side.x * (fx * b.hx + ox) + up.x * (fy * b.hy + oy) + out.x * (fz * b.hz + oz);
         this.p[1] = b.o.y + side.y * (fx * b.hx + ox) + up.y * (fy * b.hy + oy) + out.y * (fz * b.hz + oz);
         this.p[2] = b.o.z + side.z * (fx * b.hx + ox) + up.z * (fy * b.hy + oy) + out.z * (fz * b.hz + oz);
         float sz = (0.02F + 0.02F * hash(j, 12, 1) * hash(j, 12, 2)) * k;
         float br = (0.45F + 0.55F * (0.5F + 0.5F * sin(t * (0.8F + hash(j, 13, 1)) + j * 2.1F))) * 0.9F;
         c.mesh.set(ADD, FxAbysses.PLANCTON).p(br, hash(j, 14, 1)).seed(j, 0);
         FxDraw.billboard(c, this.p[0], this.p[1], this.p[2], sz, sz);
      }
   }
}
