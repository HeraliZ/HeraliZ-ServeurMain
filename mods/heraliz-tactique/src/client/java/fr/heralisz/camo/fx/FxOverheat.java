package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.clamp;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.ALPHA;

/**
 * 232 Surchauffe : l'arme chauffe à chaque tir et refroidit toute seule (ShotTracker, CamoHeat). Surface (overheat dans
 * camo_template) : acier noir, bout de canon en titane brûlé ; en chauffant, couleurs de revenu puis rouge, orange, blanc.
 * Ici : ondulations de chaleur au-dessus du canon, fumée qui monte (elle continue pendant le refroidissement), braises qui
 * gouttent sous le canon chauffé à blanc, halo au bout du canon.
 */
final class FxOverheat implements FxEffect {
   static final int HAZE = 1, SMOKE = 2, EMBER = 3, GLOW = 4;

   @Override
   public void build(FxContext c) {
      float heat = c.heat, t = c.t, L = c.L();
      if (heat < 0.15F) return;
      GunShape s = c.shape;
      float x0 = Math.min(s.barrelStart(), s.xAt(0.62F)), x1 = s.muzzle()[0];
      V up = c.up;
      // ondulations de chaleur : voiles clairs qui ondoient au-dessus du canon
      float haze = smooth(0.3F, 0.7F, heat);
      if (haze > 0.01F) {
         for (int i = 0; i < 6; i++) {
            float q = (i + 0.5F) / 6;
            float x = x0 + (x1 - x0) * q;
            V base = c.f.at(x, s.top(x), (s.zLo(x) + s.zHi(x)) * 0.5F);
            float h = L * (0.06F + 0.05F * haze);
            V p = base.add(up, h * 0.55F + 0.004F * L * sin(t * 6 + i));
            c.mesh.set(ALPHA, HAZE).p(haze * 0.5F, q).seed(i, 0);
            FxDraw.billboard(c, p, L * 0.05F, h, 0.12F * sin(t * 3 + i * 1.7F));
         }
      }
      // fumée : volutes qui montent du canon (plus denses quand c'est chaud)
      float smoke = smooth(0.2F, 0.6F, heat);
      int n = Math.round(10 * c.quality);
      for (int i = 0; i < n; i++) {
         float per = 1.6F + 0.8F * hash(i, 1, 41);
         float cyc = t / per + hash(i, 2, 41);
         int ci = (int) Math.floor(cyc);
         float g = cyc - ci;
         if (hash(i, ci, 43) > smoke) continue;
         float x = x0 + (x1 - x0) * (0.2F + 0.8F * hash(i, ci, 42));
         V base = c.f.at(x, s.top(x), (s.zLo(x) + s.zHi(x)) * 0.5F);
         V p = base.add(up, L * 0.12F * g).add(c.f.x, L * 0.03F * g * sin(g * 5 + i)).add(c.f.z, L * 0.02F * sin(g * 4 + i * 2));
         float sz = L * (0.02F + 0.07F * g);
         c.mesh.set(ALPHA, SMOKE).p(smoke * (1 - g) * smooth(0, 0.15F, g), 0).seed(i, ci & 0x7FFF);
         FxDraw.billboard(c, p, sz, sz, g + i);
      }
      // braises qui gouttent sous le canon chauffé à blanc
      float drip = smooth(0.7F, 0.95F, heat);
      if (drip > 0.01F) {
         for (int i = 0; i < 8; i++) {
            float cyc = t * 1.4F + hash(i, 5, 44);
            int ci = (int) Math.floor(cyc);
            float g = cyc - ci;
            float x = x0 + (x1 - x0) * (0.3F + 0.7F * hash(i, ci, 45));
            V base = c.f.at(x, s.bottom(x), (s.zLo(x) + s.zHi(x)) * 0.5F);
            V p = base.add(up, -L * 0.1F * g * g);
            c.mesh.set(ADD, EMBER).p(drip * (1 - g), 0).seed(i, ci & 0x7FFF);
            FxDraw.streak(c, p, up.neg(), L * 0.012F * (1 + 2 * g), L * 0.004F);
         }
         // halo au bout du canon
         V m = c.f.at(x1, s.top(x1) - 0.01F, (s.zLo(x1) + s.zHi(x1)) * 0.5F);
         c.mesh.set(ADD, GLOW).p(drip * (0.7F + 0.3F * sin(t * 9)), 0).seed(9, 0);
         FxDraw.billboard(c, m, L * 0.08F, L * 0.08F, 0);
      }
   }

   @Override
   public float heldFade() {
      return 1.4F;   // la chaleur doit se voir un peu aussi pour le porteur (le centre de l'écran reste dégagé)
   }
}
