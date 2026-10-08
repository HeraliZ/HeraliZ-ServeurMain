package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.sin;

/**
 * Trajet en hélice autour de l'arme qui épouse sa silhouette : l'ellipse suit la hauteur et la largeur de chaque tranche
 * (lunette, chargeur, canon…). Sert aux créatures enroulées (dragon, serpent).
 */
final class Coil {
   final V[] pts, out;
   /** Abscisse curviligne (repère de rendu) cumulée à chaque point. */
   final float[] arc;
   final float[] theta;

   private Coil(int n) {
      this.pts = new V[n];
      this.out = new V[n];
      this.arc = new float[n];
      this.theta = new float[n];
   }

   /**
    * @param f0     début (fraction de la longueur, 0 = crosse)
    * @param f1     fin
    * @param turns  nombre de tours
    * @param phase  angle de départ
    * @param margin écart à la surface (unités : longueur de l'arme)
    * @param wave   ondulation radiale (fraction), wFreq / wSpeed : fréquence le long du corps / vitesse
    */
   static Coil build(FxContext c, int n, float f0, float f1, float turns, float phase, float margin, float wave, float wFreq, float wSpeed) {
      return build(c, n, f0, f1, turns, phase, margin, 0, wave, wFreq, wSpeed);
   }

   /** rise : écart supplémentaire en fin de trajet (le cou se relève pour que la tête domine l'arme). */
   static Coil build(FxContext c, int n, float f0, float f1, float turns, float phase, float margin, float rise, float wave, float wFreq, float wSpeed) {
      return build(c, n, f0, f1, turns, phase, margin, rise, wave, wFreq, wSpeed, 0, 0);
   }

   /**
    * neck : à partir de cette fraction du trajet, le corps quitte l'hélice et longe le dessus de l'arme vers le canon
    * (la tête finit au-dessus de la bouche du canon, tournée vers l'avant) ; raise : hauteur dont le cou se dresse à la fin.
    */
   static Coil build(FxContext c, int n, float f0, float f1, float turns, float phase, float margin, float rise, float wave, float wFreq, float wSpeed,
                     float neck, float raise) {
      return build(c, n, f0, f1, turns, phase, margin, rise, wave, wFreq, wSpeed, neck, raise, 0);
   }

   /**
    * core : si > 0, l'hélice serre le « cœur » de l'arme (boîtier, garde-main, canon : demi-hauteur plafonnée à core sous le
    * dessus de l'arme) au lieu de l'enveloppe entière — le corps ne fait plus de grandes boucles autour du chargeur ou de la crosse.
    */
   static Coil build(FxContext c, int n, float f0, float f1, float turns, float phase, float margin, float rise, float wave, float wFreq, float wSpeed,
                     float neck, float raise, float core) {
      Coil k = new Coil(n);
      GunShape s = c.shape;
      for (int i = 0; i < n; i++) {
         float u = (float) i / (n - 1);
         float gx = s.xAt(f0 + (f1 - f0) * u);
         float th = phase + TAU * turns * u;
         float lift = rise * FxContext.smooth(0.72F, 1, u);
         float hh = core > 0 ? Math.min(s.halfH(gx), core) : s.halfH(gx);
         float hw = core > 0 ? Math.min(s.halfW(gx), core * 0.8F) : s.halfW(gx);
         float ym = core > 0 ? s.top(gx) - hh : s.yMid(gx);
         float ry = hh + margin + lift;
         float rz = Math.max(hw + margin + lift * 0.4F, 0.6F * ry);
         float w = 1 + wave * sin(u * wFreq - c.t * wSpeed);
         float gy = ym + ry * cos(th) * w;
         float gz = s.zMid(gx) + rz * sin(th) * w;
         V out = c.f.dir(0, cos(th) * rz, sin(th) * ry);
         if (neck > 0) {
            float wN = FxContext.smooth(neck, neck + (1 - neck) * 0.55F, u);
            float up = raise * FxContext.smooth(0.9F, 1, u);
            gy = FxContext.mix(gy, s.top(gx) + margin + up, wN);
            gz = FxContext.mix(gz, s.zMid(gx), wN);
            out = out.lerp(c.f.y, wN).norm();
         }
         k.pts[i] = c.f.at(gx, gy, gz);
         k.out[i] = out;
         k.theta[i] = th;
         k.arc[i] = i == 0 ? 0 : k.arc[i - 1] + k.pts[i].dist(k.pts[i - 1]);
      }
      return k;
   }

   V tangent(int i) {
      int n = this.pts.length;
      return this.pts[Math.min(i + 1, n - 1)].sub(this.pts[Math.max(i - 1, 0)]).norm();
   }
}
