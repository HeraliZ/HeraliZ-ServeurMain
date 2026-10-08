package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 230 Texture manquante sur un sac : des cubes-pixels au damier rose et noir se détachent du sac (dos, flancs, dessus),
 * montent en tournant puis se désintègrent ; quand le sac « plante » (mêmes instants que la surface), une gerbe en jaillit.
 */
final class BagMissing implements BagEffects.BagEffect {
   static final int CUBE = 1;

   @Override
   public void build(FxContext c, BagFrame b) {
      float t = c.t, k = b.scale;
      float win = (float) Math.floor(t / 5.0F);
      float crash = hash((int) win, 3, 17) < 0.55F ? smooth(0, 0.02F, t - win * 5 - 3.6F) * (1 - smooth(0.16F, 0.24F, t - win * 5 - 3.6F)) : 0;
      int n = Math.round(22 * c.quality);
      for (int j = 0; j < n; j++) {
         float per = 1.8F + 1.6F * hash(j, 1, 7);
         float cyc = t / per + hash(j, 2, 7);
         int ci = (int) Math.floor(cyc);
         float g = cyc - ci;
         if (hash(j, ci, 9) < 0.3F) continue;
         V[] at = this.surface(b, hash(j, ci, 3), hash(j, ci, 4), hash(j, ci, 5));
         V p0 = at[0], nrm = at[1], tg = at[2];
         float size = k * (0.022F + 0.04F * hash(j, ci, 6) * hash(j, ci, 16));
         float out = smooth(0, 0.14F, g);
         V dir = nrm.add(c.up, 0.9F).norm();
         V p = p0.add(nrm, size * 0.5F * out).add(dir, k * 0.2F * g * (0.4F + 0.6F * hash(j, ci, 8)));
         float ang = g * (2.5F + 3 * hash(j, ci, 10)) + hash(j, ci, 11) * TAU;
         V axis = new V(hash(j, ci, 12) - 0.5F, hash(j, ci, 13) - 0.5F, hash(j, ci, 14) - 0.5F).norm();
         V e1 = tg.rot(axis, ang), e2 = nrm.rot(axis, ang), e3 = e1.cross(e2).norm();
         float half = size * 0.5F * (g < 0.14F ? out : 1);
         c.mesh.set(SOLID, CUBE).p(smooth(0.62F, 1.0F, g), crash).seed(j & 0x7FFF, ci & 0x7FFF);
         FxDraw.box(c, p, e1.mul(half), e2.mul(half), e3.mul(half));
      }
      if (crash > 0.01F) {
         for (int j = 0; j < 16; j++) {
            V[] at = this.surface(b, hash(j, (int) win, 23), hash(j, (int) win, 24), hash(j, (int) win, 25));
            float size = k * (0.02F + 0.02F * hash(j, (int) win, 26));
            V p = at[0].add(at[1], k * 0.04F * crash);
            c.mesh.set(SOLID, CUBE).p(0, 1).seed(200 + j, 0);
            FxDraw.box(c, p, at[2].mul(size * 0.5F), at[1].mul(size * 0.5F), at[1].cross(at[2]).norm().mul(size * 0.5F));
         }
      }
   }

   /** Point sur une face visible du sac (dos, flancs, dessus : jamais contre le joueur) : {point, normale, tangente}. */
   private V[] surface(BagFrame b, float r, float u, float v) {
      float wBack = b.hx * b.hy, wSide = b.hz * b.hy, wTop = b.hx * b.hz;
      float x = r * (wBack + 2 * wSide + wTop);
      float a = u * 1.8F - 0.9F, c = v * 1.8F - 0.9F;
      if (x < wBack) {
         V p = b.back(a, c, 0);   // vraie surface du dos (sac en biais : pas de cube dans le vide)
         if (p != null) return new V[] {p, b.out, b.side};
         x = wBack + 2 * wSide;
      }
      if (x < wBack + wSide) return new V[] {b.at(1, c, a), b.side, b.up};
      if (x < wBack + 2 * wSide) return new V[] {b.at(-1, c, a), b.side.neg(), b.up};
      return new V[] {b.at(a, 1, c), b.up, b.side};
   }
}
