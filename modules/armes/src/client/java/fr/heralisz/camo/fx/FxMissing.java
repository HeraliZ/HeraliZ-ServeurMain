package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 230 Texture manquante : le damier rose et noir de Minecraft (la texture introuvable) contamine l'arme par plaques qui
 * rampent (surface : missingTexture dans camo_template). Des cubes-pixels au même damier s'en détachent, montent en tournant
 * puis se désintègrent pixel par pixel ; de temps en temps toute l'arme « plante » une fraction de seconde (surface) et une
 * gerbe de cubes jaillit.
 */
final class FxMissing implements FxEffect {
   static final int CUBE = 1, GLOW = 2;

   @Override
   public void build(FxContext c) {
      float t = c.t, L = c.L();
      Surfaces s = c.surf;
      if (s.n == 0) return;
      // « plantage » : mêmes instants que la surface (fenêtres de 5 s, une sur deux environ)
      float win = (float) Math.floor(t / 5.0F);
      float crash = hash((int) win, 3, 17) < 0.55F ? smooth(0, 0.02F, t - win * 5 - 3.6F) * (1 - smooth(0.16F, 0.24F, t - win * 5 - 3.6F)) : 0;
      int n = Math.round(20 * c.quality);
      V up = c.up;
      for (int k = 0; k < n; k++) {
         float per = 1.8F + 1.6F * hash(k, 1, 7);
         float cyc = t / per + hash(k, 2, 7);
         int ci = (int) Math.floor(cyc);
         float g = cyc - ci;
         if (hash(k, ci, 9) < 0.3F) continue;
         int fi = s.pick(hash(k, ci, 3));
         if (fi < 0) continue;
         V p0 = s.point(fi, hash(k, ci, 4), hash(k, ci, 5));
         V nrm = s.normal(fi), tg = s.tangent(fi);
         float size = L * (0.011F + 0.02F * hash(k, ci, 6) * hash(k, ci, 16));
         // sort de la surface, s'éloigne en montant et en tournant, se désintègre
         float out = smooth(0, 0.14F, g);
         V dir = nrm.add(up, 0.9F).norm();
         V p = p0.add(nrm, size * 0.5F * out).add(dir, L * 0.09F * g * (0.4F + 0.6F * hash(k, ci, 8)));
         float ang = g * (2.5F + 3 * hash(k, ci, 10)) + hash(k, ci, 11) * TAU;
         V axis = new V(hash(k, ci, 12) - 0.5F, hash(k, ci, 13) - 0.5F, hash(k, ci, 14) - 0.5F).norm();
         V e1 = tg.rot(axis, ang), e2 = nrm.rot(axis, ang), e3 = e1.cross(e2).norm();
         float half = size * 0.5F * (g < 0.14F ? out : 1);
         float life = smooth(0.62F, 1.0F, g);
         c.mesh.set(SOLID, CUBE).p(life, crash).seed(k & 0x7FFF, ci & 0x7FFF);
         FxDraw.box(c, p, e1.mul(half), e2.mul(half), e3.mul(half));
      }
      // plantage : une gerbe de cubes jaillit de toute l'arme
      if (crash > 0.01F) {
         for (int k = 0; k < 18; k++) {
            int fi = s.pick(hash(k, (int) win, 23));
            if (fi < 0) continue;
            V p0 = s.point(fi, hash(k, (int) win, 24), hash(k, (int) win, 25));
            V nrm = s.normal(fi);
            float size = L * (0.01F + 0.012F * hash(k, (int) win, 26));
            V p = p0.add(nrm, L * 0.02F * crash);
            c.mesh.set(SOLID, CUBE).p(0, 1).seed(200 + k, 0);
            FxDraw.box(c, p, s.tangent(fi).mul(size * 0.5F), nrm.mul(size * 0.5F), nrm.cross(s.tangent(fi)).norm().mul(size * 0.5F));
         }
      }
   }

   @Override
   public float heldFade() {
      return 0.8F;
   }
}
