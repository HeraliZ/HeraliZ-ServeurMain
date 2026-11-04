package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.ALPHA;

/**
 * 210 Faille, rechargement (rendus validés : maquettes/skins_premium) : une faille s'ouvre sous le chargeur au repos et
 * avale l'ancien chargeur en éclats de lumière, puis en renvoie vers le nouveau ; à l'enclenchement elle se referme d'un
 * coup (éclair à plat) et une onde part du puits de chargeur. La finition et la disparition / réapparition du chargeur
 * lui-même sont dans le shader (failleRecharge). Rien hors du rechargement.
 */
final class FxFaille implements FxEffect {
   static final int FOND = 1, BORD = 2, ECLAT = 3, ECLAIR = 4, ONDE = 5;

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
      float t = c.t;
      float L = c.L();
      float mx = (r.magX0 + r.magX1) * 0.5F, mw = r.magX1 - r.magX0, mh = r.magY1 - r.magY0;
      V centre = c.f.at(mx, r.magY0 - 0.62F * mh, r.magZ);
      float ouv = smooth(0.10F, 0.22F, u) * (1 - smooth(0.86F, 0.94F, u));
      if (ouv > 0.01F) {
         // lentille à plat sous l'arme (plan x / z de l'arme) : fond d'espace, puis bord lumineux un peu plus grand
         float larg = mw * (0.6F + 1.3F * ouv);
         V hx = c.f.x.mul(larg * 0.5F * L), hz = c.f.z.mul(larg * 0.42F * L * (0.4F + 0.6F * ouv));
         c.mesh.set(ALPHA, FOND).p(ouv, 0).seed(1, 0);
         FxDraw.quad(c, centre, hx, hz);
         c.mesh.set(ADD, BORD).p(ouv, 0).seed(2, 0);
         FxDraw.quad(c, centre, hx.mul(1.3F), hz.mul(1.3F));
      }
      // éclats : de l'ancien chargeur vers la faille, puis de la faille vers le nouveau
      float sortie = smooth(0.25F, 0.40F, u) * (1 - smooth(0.50F, 0.56F, u));
      float retour = smooth(0.52F, 0.60F, u) * (1 - smooth(0.72F, 0.80F, u));
      if (sortie > 0.01F || retour > 0.01F) {
         Surfaces.Subset chargeur = c.surf.select(i -> c.surf.part[i] > 0.62F && c.surf.part[i] < 0.88F);
         if (!chargeur.empty()) {
            boolean versFaille = sortie > 0.01F;
            float force = versFaille ? sortie : retour;
            int n = c.count(28, 10);
            for (int j = 0; j < n; j++) {
               float ph = FxContext.fract(hash(j, 3, 1) + t * (versFaille ? 1.6F : -1.6F));
               float k = versFaille ? ph : 1 - ph;
               V src = c.surf.center(chargeur.pick(hash(j, 3, 2)));
               V dst = centre.add(c.f.x, (hash(j, 3, 3) - 0.5F) * mw * 1.2F * L);
               V p = src.lerp(dst, k).add(c.f.x, FxContext.sin(k * 6 + j) * mw * 0.15F * L);
               float taille = (0.006F + 0.008F * hash(j, 3, 4)) * L * (1 - 0.5F * k);
               c.mesh.set(ADD, ECLAT).p(force, hash(j, 3, 5)).seed(j, 0);
               FxDraw.billboard(c, p, taille, taille, hash(j, 3, 6) * 6.28F + k * 4);
            }
         }
      }
      // fermeture : éclair à plat, puis onde autour du puits de chargeur
      float eclair = u > 0.85F ? (float) Math.exp(-Math.pow((u - 0.93F) / 0.025F, 2)) : 0;
      if (eclair > 0.01F) {
         c.mesh.set(ADD, ECLAIR).p(eclair, 0).seed(3, 0);
         FxDraw.quad(c, centre, c.f.x.mul(mw * 1.6F * L), c.f.z.mul(0.03F * L));
      }
      if (u >= 0.88F) {
         float k = smooth(0.88F, 1F, u);
         float ra = mw * (0.4F + 2.0F * k) * L;
         c.mesh.set(ADD, ONDE).p(1 - k, 0).seed(4, 0);
         FxDraw.torus(c, c.f.at(mx, r.magY1, r.magZ), c.f.y, c.f.x, ra, ra * 0.6F, 0.004F * L, c.count(28, 14), 4);
      }
   }
}
