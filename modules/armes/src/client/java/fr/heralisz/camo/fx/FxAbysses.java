package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.fract;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/**
 * 211 Abysses (19/11, rendus validés par le propriétaire) : douze filaments bioluminescents sortent du garde-main et du canon
 * et ondulent vers l'arrière comme des tentacules dans un courant (des pulsations de lumière les parcourent) ; du plancton
 * lumineux flotte autour de l'arme. Tout est additif (shader fx_211 : filament, plancton) : environ 390 quads par image en
 * pleine qualité, moins en main et de loin (filaments moins découpés, moins de plancton).
 * <p>Points des filaments dans un tableau gardé d'une image à l'autre et rubans calculés à plat (FxDraw) : aucun objet créé
 * dans les boucles.
 */
final class FxAbysses implements FxEffect {
   /** Sortes d'éléments lues par le shader fx_211. */
   static final int FILAMENT = 1, PLANCTON = 2;
   private static final int FILAMENTS = 12, SEGMENTS = 28;

   private final float[] pts = new float[(SEGMENTS + 1) * 3], w = new float[SEGMENTS + 1], p = new float[3];

   @Override
   public void build(FxContext c) {
      float t = c.t;
      float L = c.L();
      GunShape s = c.shape;
      int nseg = segments(c);
      float xb = s.xAt(0.62F), xm = s.xAt(0.95F);
      for (int k = 0; k < FILAMENTS; k++) {
         float x0 = xb + (xm - xb) * ((k + 0.5F) / FILAMENTS + (hash(k, 3, 1) - 0.5F) * 0.08F);
         // départ réparti autour de l'arme (angle d'or) ; radial = (0, ca, sa), perpendiculaire = (0, sa, -ca)
         float ang = (k * 2.39996F + 0.6F) % TAU;
         float ca = cos(ang), sa = sin(ang);
         float ry = Math.max(s.halfH(x0), 0.012F), rz = Math.max(s.halfW(x0), 0.012F);
         float by = s.yMid(x0) + ry * ca, bz = s.zMid(x0) + rz * sa;
         float len = 0.34F + 0.22F * hash(k, 5, 2);
         float ph = hash(k, 7, 3) * TAU;
         float lift = 0.03F * sin(ph);
         for (int i = 0; i <= nseg; i++) {
            float q = (float) i / nseg;
            // il s'écarte de l'arme, file vers l'arrière et ondule de plus en plus vers son bout
            float ecart = 0.04F * (float) Math.sqrt(q) + 0.035F * q + cos(q * TAU * 0.9F - t * 0.9F + ph * 1.7F) * 0.03F * q;
            float onde = sin(q * TAU * 1.4F - t * 1.25F + ph) * 0.04F * q;
            c.f.at(x0 - len * q, by + ca * ecart + sa * onde + lift * q * q, bz + sa * ecart - ca * onde, this.pts, i * 3);
            this.w[i] = (0.0116F * (1 - 0.7F * q) + 0.002F) * L;
         }
         c.mesh.set(ADD, FILAMENT).p(fract(ph), hash(k, 9, 4)).seed(k, 0);
         FxDraw.ribbon(c, this.pts, this.w, nseg + 1, 0, 1);
      }
      // plancton qui dérive autour de l'arme
      int np = c.count(54, 20);
      for (int j = 0; j < np; j++) {
         float gx = s.xAt(-0.05F + 1.1F * hash(j, 11, 1));
         float a = hash(j, 11, 2) * TAU;
         float rad = 0.05F + 0.16F * hash(j, 11, 3);
         c.f.at(gx + 0.01F * sin(t * 0.37F + j), s.yMid(gx) + cos(a) * rad * 0.8F + 0.012F * sin(t * 0.29F + j * 1.7F),
            s.zMid(gx) + sin(a) * rad + 0.01F * cos(t * 0.31F + j * 0.9F), this.p, 0);
         float sz = (0.01F + 0.012F * hash(j, 12, 1) * hash(j, 12, 2)) * L;
         float b = (0.45F + 0.55F * (0.5F + 0.5F * sin(t * (0.8F + hash(j, 13, 1)) + j * 2.1F))) * 0.9F;
         c.mesh.set(ADD, PLANCTON).p(b, hash(j, 14, 1)).seed(j, 0);
         FxDraw.billboard(c, this.p[0], this.p[1], this.p[2], sz, sz);
      }
   }

   /** Découpe des filaments selon la qualité : 28 segments en gros plan, 20 en main, 14 de loin. */
   static int segments(FxContext c) {
      return c.quality >= 0.9F ? SEGMENTS : c.quality >= 0.55F ? 20 : 14;
   }
}
