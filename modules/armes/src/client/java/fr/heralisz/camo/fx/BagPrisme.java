package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.PI;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.sin;

/**
 * 212 Prisme sur un sac : le même atome que sur l'arme (FxPrisme.atome), à taille humaine, dans le dos du joueur : ses trois
 * orbites sont posées à plat derrière le sac, tournées de 60 degrés les unes par rapport aux autres (le dessin d'un atome,
 * vu en entier de dos) et un peu penchées vers l'arrière pour avoir du relief ; quinze éclats de cristal y tournent avec
 * leurs traînées, des étincelles scintillent, une lueur douce au centre. Tout est additif (shader fx_212), aucun objet créé
 * dans les boucles.
 */
final class BagPrisme implements BagEffects.BagEffect {
   /** Demi-axes des orbites (blocs, à l'échelle du sac) et échelle des éclats. */
   private static final float GRAND = 0.55F, PETIT = 0.2F, ECHELLE = 2.2F;
   /** Pente des orbites vers l'arrière (radians) : de profil, l'atome a un peu d'épaisseur. */
   private static final float PENTE = 18 * PI / 180;

   private final FxPrisme atome = new FxPrisme();

   @Override
   public void build(FxContext c, BagFrame b) {
      float k = b.scale;
      V up = b.up, side = b.side, out = b.out;
      // centre : au milieu du dos du sac, un peu décollé vers l'arrière (les orbites passent derrière le joueur)
      float d = b.hz + 0.12F * k;
      float[] ce = this.atome.centre, u = this.atome.u, v = this.atome.v;
      ce[0] = b.o.x + out.x * d;
      ce[1] = b.o.y + out.y * d;
      ce[2] = b.o.z + out.z * d;
      float cp = cos(PENTE), sp = sin(PENTE);
      for (int o = 0; o < 3; o++) {
         // grand axe tourné de 20, 80 puis 140 degrés dans le plan du dos ; petit axe perpendiculaire, penché vers l'arrière
         float phi = (o * 60 + 20) * PI / 180, ca = cos(phi), sa = sin(phi);
         int i = o * 3;
         u[i] = (side.x * ca + up.x * sa) * GRAND * k;
         u[i + 1] = (side.y * ca + up.y * sa) * GRAND * k;
         u[i + 2] = (side.z * ca + up.z * sa) * GRAND * k;
         v[i] = ((up.x * ca - side.x * sa) * cp + out.x * sp) * PETIT * k;
         v[i + 1] = ((up.y * ca - side.y * sa) * cp + out.y * sp) * PETIT * k;
         v[i + 2] = ((up.z * ca - side.z * sa) * cp + out.z * sp) * PETIT * k;
      }
      this.atome.atome(c, ECHELLE * k, 1, 1, c.count(18, 8));
   }
}
