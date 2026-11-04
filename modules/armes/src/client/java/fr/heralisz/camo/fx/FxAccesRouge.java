package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/**
 * 211 Accès Rouge, rechargement (rendus validés : maquettes/skins_premium) : l'ancien chargeur part en cubes rouges ; le
 * nouveau s'imprime dans un cadre d'hologramme (coins rouges, ligne de balayage à la hauteur de l'impression) ; enclenché :
 * éclair et anneau verts au puits de chargeur (« accès autorisé »). Le chargeur lui-même (coupure, cubes, grille, partie
 * solide) et l'onde verte dans les joints de l'arme sont dans le shader (accesRecharge). Rien hors du rechargement.
 */
final class FxAccesRouge implements FxEffect {
   static final int CUBE = 1, TRAIT = 2, BALAYAGE = 3, ECLAIR = 4, ANNEAU = 5;

   @Override
   public boolean actif(float recharge) {
      return recharge >= 0;
   }

   @Override
   public boolean fullInHand() {
      return true;
   }

   @Override
   public void build(FxContext c) {
      float u = c.recharge;
      GunShape r = c.repos;
      if (u < 0 || r == null || !r.aChargeur()) return;
      float L = c.L();
      float mx = (r.magX0 + r.magX1) * 0.5F, mw = r.magX1 - r.magX0;
      float cubes = smooth(0.20F, 0.30F, u) * (1 - smooth(0.50F, 0.60F, u));
      float cadre = smooth(0.42F, 0.46F, u) * (1 - smooth(0.72F, 0.76F, u));
      Surfaces.Subset chargeur = cubes > 0.01F || cadre > 0.01F
         ? c.surf.select(i -> c.surf.part[i] > 0.62F && c.surf.part[i] < 0.88F) : null;
      if (cubes > 0.01F && chargeur != null && !chargeur.empty()) {
         float avance = smooth(0.20F, 0.60F, u);
         V bas = c.up.neg();
         int n = c.count(36, 12);
         for (int j = 0; j < n; j++) {
            float k = Math.min(1, avance * 1.3F * (0.6F + 0.4F * hash(j, 5, 1)));
            V src = c.surf.center(chargeur.pick(hash(j, 5, 2)));
            V dir = c.f.dir(hash(j, 5, 3) - 0.5F, -0.2F - 0.5F * hash(j, 5, 4), (hash(j, 5, 5) - 0.5F) * 1.4F);
            V p = src.add(dir, k * 0.12F * L).add(bas, k * k * 0.06F * L);
            float taille = (0.006F + 0.007F * hash(j, 5, 6)) * L;
            c.mesh.set(ADD, CUBE).p(cubes * (1 - 0.8F * k), hash(j, 5, 7)).seed(j, 0);
            FxDraw.billboard(c, p, taille, taille, 0);
         }
      }
      if (cadre > 0.01F && chargeur != null && !chargeur.empty()) {
         // boîte du chargeur tel qu'il est dessiné (dans la main), dans le repère de l'arme
         float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
         for (int k = 0; k < chargeur.size; k++) {
            V d = c.surf.center(chargeur.idx[k]).sub(c.f.o);
            float gx = d.dot(c.f.x) / c.f.s, gy = d.dot(c.f.y) / c.f.s, gz = d.dot(c.f.z) / c.f.s;
            x0 = Math.min(x0, gx); x1 = Math.max(x1, gx);
            y0 = Math.min(y0, gy); y1 = Math.max(y1, gy);
            z1 = Math.max(z1, gz);
         }
         float pad = 0.012F;
         x0 -= pad; x1 += pad; y0 -= pad; y1 += pad;
         float z = z1 + 0.004F;
         float bras = Math.min(x1 - x0, y1 - y0) * 0.3F * L;
         float ep = 0.0025F * L;
         c.mesh.set(ADD, TRAIT).p(cadre, 0).seed(1, 0);
         for (int k = 0; k < 4; k++) {
            float gx = (k & 1) == 0 ? x0 : x1, gy = (k & 2) == 0 ? y0 : y1;
            V coin = c.f.at(gx, gy, z);
            FxDraw.streak(c, coin, c.f.x.mul((k & 1) == 0 ? 1 : -1), bras, ep);
            FxDraw.streak(c, coin, c.f.y.mul((k & 2) == 0 ? 1 : -1), bras, ep);
         }
         // ligne de balayage : même hauteur que le front d'impression du shader (accesRecharge)
         float a = Math.max(0, Math.min(1, smooth(0.1F, 0.95F, (u - 0.44F) / 0.30F * 0.97F)));
         float ligne = -0.15F + 1.3F * a;
         if (ligne > -0.05F && ligne < 1.05F) {
            float gy = y0 + pad + (y1 - y0 - 2 * pad) * ligne;
            c.mesh.set(ADD, BALAYAGE).p(cadre, 0).seed(2, 0);
            FxDraw.streak(c, c.f.at(x0 - pad, gy, z), c.f.x, (x1 - x0 + 2 * pad) * L, 0.006F * L);
         }
      }
      // enclenché : éclair et anneau verts au puits de chargeur
      float eclair = u > 0.8F ? (float) Math.exp(-Math.pow((u - 0.91F) / 0.035F, 2)) : 0;
      if (eclair > 0.01F) {
         V puits = c.f.at(mx, r.magY1, r.magZ);
         c.mesh.set(ADD, ECLAIR).p(eclair, 0).seed(3, 0);
         FxDraw.billboard(c, puits, mw * 2.2F * L, mw * 1.1F * L, 0);
         float ra = mw * (0.5F + 0.9F * (1 - eclair)) * L;
         c.mesh.set(ADD, ANNEAU).p(eclair, 0).seed(4, 0);
         FxDraw.torus(c, puits, c.f.y, c.f.x, ra, ra * 0.5F, 0.003F * L, c.count(24, 12), 4);
      }
   }
}
