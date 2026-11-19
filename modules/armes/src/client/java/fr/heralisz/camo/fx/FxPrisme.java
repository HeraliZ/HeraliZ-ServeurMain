package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.PI;
import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.fract;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/**
 * 212 Prisme (19/11, demandé par le propriétaire) : quinze éclats de cristal en orbite autour de l'arme, sur trois orbites
 * penchées de 60 degrés les unes sur les autres autour de son axe, comme les électrons d'un atome ; chaque éclat tourne sur
 * lui-même, change de couleur en faisant le tour et laisse une traînée colorée ; des étincelles scintillent sur les
 * orbites, une lueur douce flotte au centre. Boucle exacte de 4 s. Tout est additif (shader fx_212 : éclat, traînée,
 * étincelle, lueur) : environ 320 quads en pleine qualité, moins en main et de loin.
 * <p>Centre et demi-axes des orbites dans des tableaux gardés d'une image à l'autre, rubans et sprites calculés à plat :
 * aucun objet créé dans les boucles. Le même atome sert sur le sac (BagPrisme, avec ses propres orbites).
 */
final class FxPrisme implements FxEffect {
   /** Sortes d'éléments lues par le shader fx_212. */
   static final int ECLAT = 1, TRAINEE = 2, ETINCELLE = 3, LUEUR = 4;
   /** Éclats par orbite. */
   static final int PAR_ORBITE = 5;
   /** Demi-axes des orbites autour de l'arme (longueurs d'arme). */
   private static final float[] A = {0.5F, 0.42F, 0.56F}, B = {0.17F, 0.21F, 0.13F};
   /** Vitesse de chaque orbite : TOURS x 2 pi / 5 en 4 s (cinq éclats par orbite) : l'animation boucle exactement. */
   private static final int[] TOURS = {2, 1, 3};
   private static final int SEGMENTS = 18;

   /** Centre de l'atome et demi-axes de chacune des trois orbites (repère de rendu), posés avant {@link #atome}. */
   final float[] centre = new float[3], u = new float[9], v = new float[9];
   private final float[] pts = new float[(SEGMENTS + 1) * 3], w = new float[SEGMENTS + 1], p = new float[3];

   @Override
   public void build(FxContext c) {
      GunShape s = c.shape;
      // vu par le porteur : orbites plus petites (comme les ailes du Séraphin)
      float k = c.firstPerson ? 0.72F : 1F;
      float xc = s.xAt(0.47F);
      c.f.at(xc, s.yMid(xc), s.zMid(xc), this.centre, 0);
      for (int o = 0; o < 3; o++) {
         // grand axe le long de l'arme, petit axe tourné de 20, 80 puis 140 degrés autour de l'arme
         float al = (o * 60 + 20) * PI / 180;
         c.f.vec(A[o] * k, 0, 0, this.u, o * 3);
         c.f.vec(0, B[o] * k * cos(al), B[o] * k * sin(al), this.v, o * 3);
      }
      this.atome(c, c.L(), c.firstPerson ? 0.85F : 1F, k, c.count(30, 12));
   }

   /**
    * Éclats, traînées, étincelles et lueur sur les trois orbites (centre, u, v déjà posés) ; L : échelle des éléments (longueur
    * de l'arme ; sur un sac, une échelle à taille humaine), eclat et lueur : facteurs de taille, etincelles : nombre.
    */
   void atome(FxContext c, float L, float eclat, float lueur, int etincelles) {
      float t = c.t;
      int nseg = c.quality >= 0.9F ? SEGMENTS : c.quality >= 0.55F ? 12 : 8;
      for (int o = 0; o < 3; o++) {
         int sens = o == 1 ? -1 : 1;
         float vit = sens * TOURS[o] * (TAU / 5) / 4;
         int b = o * 3;
         for (int j = 0; j < PAR_ORBITE; j++) {
            float th = t * vit + j * TAU / PAR_ORBITE + o * 1.3F;
            // teinte et rotation propre suivent la place sur l'orbite (l'éclat change de couleur en faisant le tour)
            float pos = th % TAU;
            if (pos < 0) pos += TAU;
            float teinte = fract(o * 0.33F + pos / TAU + 0.1F);
            // traînée : arc derrière l'éclat (un peu plus d'un quart d'orbite)
            for (int i = 0; i <= nseg; i++) {
               float q = (float) i / nseg;
               this.point(o, th - sens * q * 1.9F, 1, this.pts, i * 3);
               this.w[i] = (0.018F * (1 - 0.8F * q) + 0.0028F) * L;
            }
            c.mesh.set(ADD, TRAINEE).p(teinte, 0).seed(o * 10, 0);
            FxDraw.ribbon(c, this.pts, this.w, nseg + 1, 0, 1);
            // éclat : losange allongé le long de l'orbite, face à la caméra
            this.point(o, th, 1, this.p, 0);
            float cs = cos(th), sn = sin(th);
            float tx = sens * (this.v[b] * cs - this.u[b] * sn), ty = sens * (this.v[b + 1] * cs - this.u[b + 1] * sn),
               tz = sens * (this.v[b + 2] * cs - this.u[b + 2] * sn);
            // direction du regard vers l'éclat (caméra à l'origine ; inventaire : vers -z)
            float dx = 0, dy = 0, dz = -1;
            if (!c.gui) {
               float l = (float) Math.sqrt(this.p[0] * this.p[0] + this.p[1] * this.p[1] + this.p[2] * this.p[2]);
               if (l > 1e-9F) {
                  dx = this.p[0] / l; dy = this.p[1] / l; dz = this.p[2] / l;
               }
            }
            // côté = tangente x regard ; long = regard x côté (dans le plan de l'écran, le long de l'orbite)
            float sx = ty * dz - tz * dy, sy = tz * dx - tx * dz, sz = tx * dy - ty * dx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-9F) continue;
            sx /= sl; sy /= sl; sz /= sl;
            float ax = dy * sz - dz * sy, ay = dz * sx - dx * sz, az = dx * sy - dy * sx;
            float taille = (0.034F + 0.01F * sin(pos * 2 + o)) * L * eclat;
            float lt = taille * 0.45F;
            c.mesh.set(ADD, ECLAT).p(teinte, fract((pos * 3 + o * 1.7F) / TAU)).seed(o * 10, 0);
            FxDraw.quad(c, this.p[0], this.p[1], this.p[2], sx * lt, sy * lt, sz * lt, ax * taille, ay * taille, az * taille);
         }
      }
      // étincelles qui scintillent sur les orbites
      for (int j = 0; j < etincelles; j++) {
         int o = j % 3;
         float th = hash(j, 4, 1) * TAU + 0.06F * sin(t * PI / 2);
         this.point(o, th, 1 + 0.12F * (hash(j, 4, 2) - 0.5F), this.p, 0);
         float br = (float) Math.pow(Math.max(0, sin(t * PI / 2 * (1 + (int) (hash(j, 4, 3) * 2)) + j * 2.3F)), 6) * 0.9F;
         if (br < 0.01F) continue;
         float sz = (0.014F + 0.012F * hash(j, 4, 4)) * L;
         c.mesh.set(ADD, ETINCELLE).p(br, hash(j, 4, 5)).seed(j, 0);
         FxDraw.billboard(c, this.p[0], this.p[1], this.p[2], sz, sz);
      }
      // lueur douce au centre de l'atome
      c.mesh.set(ADD, LUEUR).p(0.18F, 0).seed(99, 0);
      FxDraw.billboard(c, this.centre[0], this.centre[1], this.centre[2], 0.18F * L * lueur, 0.12F * L * lueur);
   }

   /** Point de l'orbite o à l'angle th (rr : facteur de rayon), écrit dans out[i..i+2]. */
   private void point(int o, float th, float rr, float[] out, int i) {
      float co = cos(th) * rr, si = sin(th) * rr;
      int b = o * 3;
      out[i] = this.centre[0] + this.u[b] * co + this.v[b] * si;
      out[i + 1] = this.centre[1] + this.u[b + 1] * co + this.v[b + 1] * si;
      out[i + 2] = this.centre[2] + this.u[b + 2] * co + this.v[b + 2] * si;
   }
}
