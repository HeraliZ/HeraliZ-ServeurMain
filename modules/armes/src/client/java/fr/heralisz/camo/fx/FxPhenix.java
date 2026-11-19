package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.PI;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/**
 * 210 Phénix (19/11, rendus validés par le propriétaire) : deux grandes ailes de flammes déployées de part et d'autre de la
 * carcasse, relevées vers l'arrière, battent lentement ; des braises montent de l'arme et des ailes, une lueur de chaleur
 * flotte au-dessus. Tout est additif (shader fx_210 : plume de flamme, braise, lueur) : environ 130 quads par image.
 * <p>Le squelette des ailes est calculé dans le repère de l'arme (unités : longueur de l'arme) dans des tableaux gardés d'une
 * image à l'autre, puis porté dans le repère de rendu : aucun objet créé dans les boucles. Sur un sac : BagPhenix (mêmes
 * plumes de flammes, squelette des ailes du Séraphin pour qu'elles se voient de dos).
 */
final class FxPhenix implements FxEffect {
   /** Sortes d'éléments lues par le shader fx_210. */
   static final int PLUME = 1, BRAISE = 2, LUEUR = 3;

   // squelette d'une aile et plume en cours (repère de l'arme), réécrits à chaque image
   private final float[] out = new float[3], up = new float[3], span = new float[3], nrm = new float[3];
   private final float[] sh = new float[3], elbow = new float[3], wrist = new float[3], tip = new float[3];
   private final float[] root = new float[3], dir = new float[3], d = new float[3], across = new float[3];
   /** Centre et demi-axes d'un quad dans le repère de rendu. */
   private final float[] p = new float[3], ax = new float[3], ay = new float[3];

   @Override
   public void build(FxContext c) {
      float t = c.t;
      GunShape s = c.shape;
      // vu par le porteur : ailes plus petites (comme le Séraphin), sinon elles envahissent tout le ciel
      float ws = c.firstPerson ? 0.55F : 1F;
      float xs = s.xAt(0.47F);
      // battement lent autour de l'axe de l'arme
      float flap = 0.2F * sin(t * 1.35F) + 0.08F;
      for (int sg = -1; sg <= 1; sg += 2) {
         // épaule : sur le flanc de la carcasse, juste sous son dessus
         this.wing(c, c.f, xs, s.top(xs) - 0.012F, s.zMid(xs) + sg * s.halfW(xs) * 0.8F, sg, flap, ws, t, sg < 0 ? 0 : 60);
      }
      this.braises(c, s, t);
      this.lueur(c, s);
   }

   /**
    * Une aile de flammes dans le repère f (x vers l'avant, y vers le haut, z sur le côté ; unités : f.s), épaule en
    * (shx, shy, shz), du côté sg (-1 ou 1) ; ws : taille ; id0 : première graine des plumes (0..32767).
    */
   private void wing(FxContext c, FxFrame f, float shx, float shy, float shz, int sg, float flap, float ws, float t, int id0) {
      // l'aile bat autour de l'axe x : côté (0, 0, sg) et haut (0, 1, 0) tournés ensemble de sg · flap
      float ca = cos(sg * flap), sa = sin(sg * flap);
      set(this.out, 0, -sg * sa, sg * ca);
      set(this.up, 0, ca, sa);
      // bras de l'aile : part du flanc, monte et s'écarte ; le poignet se replie vers l'arrière (-x)
      set(this.span, -0.12F, this.out[1] * 0.8F + this.up[1] * 0.58F, this.out[2] * 0.8F + this.up[2] * 0.58F);
      norm(this.span);
      // normale du plan de l'aile : span × arrière
      set(this.nrm, 0, -this.span[2], this.span[1]);
      norm(this.nrm);
      set(this.sh, shx, shy, shz);
      lin(this.elbow, this.sh, this.span, 0.14F * ws, this.up, 0.03F * ws);
      lin(this.wrist, this.elbow, this.span, 0.17F * ws, this.up, 0);
      this.wrist[0] -= 0.05F * ws;
      // bout de l'aile : prolonge le poignet, un peu vers l'arrière
      set(this.d, this.span[0] - 0.35F, this.span[1], this.span[2]);
      norm(this.d);
      lin(this.tip, this.wrist, this.d, 0.08F * ws, this.d, 0);
      // rémiges primaires : grand éventail au bout de l'aile, des plus longues (vers l'extérieur) aux plus courtes
      for (int k = 0; k < 13; k++) {
         float q = k / 12F;
         if (q < 0.25F) mix(this.root, this.tip, this.wrist, q * 4);
         else mix(this.root, this.wrist, this.elbow, (q - 0.25F) * 0.5F);
         set(this.dir, this.span[0] * (0.95F - q * 0.9F) - (0.25F + q * 1.05F), this.span[1] * (0.95F - q * 0.9F) + this.up[1] * (0.18F - q * 0.12F),
            this.span[2] * (0.95F - q * 0.9F) + this.up[2] * (0.18F - q * 0.12F));
         this.plume(c, f, (0.34F - q * 0.12F) * ws, 0, id0 + k, 1 - 0.25F * q, 0.24F, t);
      }
      // secondaires : le long du bras, vers l'arrière et un peu vers le bas (bord de fuite de l'aile)
      for (int k = 0; k < 12; k++) {
         float q = k / 11F;
         mix(this.root, this.elbow, this.sh, q * 0.9F);
         float e = 0.3F * (1 - q);
         set(this.dir, this.span[0] * e - 0.95F, this.span[1] * e - this.up[1] * 0.22F, this.span[2] * e - this.up[2] * 0.22F);
         this.plume(c, f, (0.24F - q * 0.08F) * ws, 0.5F, id0 + 20 + k, 0.9F, 0.26F, t);
      }
      // couvertures : flammes courtes serrées sur le bord d'attaque
      for (int k = 0; k < 14; k++) {
         float q = k / 13F;
         if (q < 0.5F) mix(this.root, this.sh, this.elbow, q * 2);
         else mix(this.root, this.elbow, this.tip, (q - 0.5F) * 2);
         set(this.dir, this.span[0] * 0.4F - 0.7F, this.span[1] * 0.4F + this.up[1] * 0.12F, this.span[2] * 0.4F + this.up[2] * 0.12F);
         this.plume(c, f, 0.09F * ws, 1, id0 + 40 + k, 0.85F, 0.3F, t);
      }
   }

   /** Plume de flamme partant de root dans la direction dir (non unitaire) : quad le long de la plume (v = 0 à la racine). */
   private void plume(FxContext c, FxFrame f, float len, float layer, int id, float bright, float large, float t) {
      norm(this.dir);
      // la plume ondule un peu dans le plan de l'aile
      rot(this.d, this.dir, this.nrm, 0.07F * sin(t * 3.1F + id * 0.7F));
      norm(this.d);
      cross(this.across, this.nrm, this.d);
      norm(this.across);
      float h = len * 0.5F, w = len * large;
      f.at(this.root[0] + this.d[0] * h, this.root[1] + this.d[1] * h, this.root[2] + this.d[2] * h, this.p, 0);
      f.vec(this.across[0] * w, this.across[1] * w, this.across[2] * w, this.ax, 0);
      f.vec(this.d[0] * h, this.d[1] * h, this.d[2] * h, this.ay, 0);
      c.mesh.set(ADD, PLUME).p(bright, layer).seed(id, 0);
      FxDraw.quad(c, this.p[0], this.p[1], this.p[2], this.ax[0], this.ax[1], this.ax[2], this.ay[0], this.ay[1], this.ay[2]);
   }

   /** Braises : montent depuis le dessus de l'arme et des ailes, dérivent vers l'arrière, scintillent. */
   private void braises(FxContext c, GunShape s, float t) {
      float L = c.L();
      int ne = c.count(46, 16);
      for (int j = 0; j < ne; j++) {
         float per = 1.7F + 1.3F * hash(j, 1, 1);
         float cyc = t / per + hash(j, 1, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         float bx, by, bz;
         if (hash(j, ci, 9) < 0.65F) {
            bx = s.xAt(0.12F + 0.78F * hash(j, ci, 3));
            by = s.top(bx);
            bz = s.zMid(bx) + (hash(j, ci, 4) - 0.5F) * 2 * s.halfW(bx);
         } else {
            float sg = hash(j, ci, 5) < 0.5F ? -1 : 1;
            bx = s.xAt(0.3F + 0.3F * hash(j, ci, 6));
            by = s.top(bx) + 0.08F + 0.15F * hash(j, ci, 7);
            bz = s.zMid(bx) + sg * (0.08F + 0.18F * hash(j, ci, 8));
         }
         c.f.at(bx - 0.07F * life + 0.012F * sin(life * 7 + j), by, bz + 0.018F * sin(life * 5 + j * 1.3F), this.p, 0);
         // elles montent vers le haut du monde
         float rise = 0.24F * life * L;
         float sz = (0.009F + 0.008F * hash(j, 2, 2)) * L;
         float b = sin(PI * Math.min(1, life * 1.15F)) * (0.6F + 0.4F * hash(j, ci, 10));
         c.mesh.set(ADD, BRAISE).p(b, 0).seed(j, 0);
         FxDraw.billboard(c, this.p[0] + c.up.x * rise, this.p[1] + c.up.y * rise, this.p[2] + c.up.z * rise, sz, sz);
      }
   }

   /** Lueur de chaleur au-dessus de la carcasse. */
   private void lueur(FxContext c, GunShape s) {
      float L = c.L();
      c.mesh.set(ADD, LUEUR).p(0.16F, 0).seed(90, 0);
      for (int k = 0; k < 3; k++) {
         float gx = s.xAt(0.3F + 0.2F * k);
         c.f.at(gx, s.top(gx) + 0.02F, s.zMid(gx), this.p, 0);
         FxDraw.billboard(c, this.p[0], this.p[1], this.p[2], 0.28F * L, 0.14F * L);
      }
   }

   // ------------------------------------------------------------------ petits calculs sur des tableaux de 3 flottants

   private static void set(float[] r, float x, float y, float z) {
      r[0] = x;
      r[1] = y;
      r[2] = z;
   }

   private static void norm(float[] r) {
      float l = (float) Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
      if (l < 1e-9F) {
         set(r, 0, 1, 0);
      } else {
         r[0] /= l;
         r[1] /= l;
         r[2] /= l;
      }
   }

   /** r = a + u · ku + v · kv */
   private static void lin(float[] r, float[] a, float[] u, float ku, float[] v, float kv) {
      r[0] = a[0] + u[0] * ku + v[0] * kv;
      r[1] = a[1] + u[1] * ku + v[1] * kv;
      r[2] = a[2] + u[2] * ku + v[2] * kv;
   }

   /** r = a + (b - a) · k */
   private static void mix(float[] r, float[] a, float[] b, float k) {
      r[0] = a[0] + (b[0] - a[0]) * k;
      r[1] = a[1] + (b[1] - a[1]) * k;
      r[2] = a[2] + (b[2] - a[2]) * k;
   }

   /** r = a × b (r différent de a et b). */
   private static void cross(float[] r, float[] a, float[] b) {
      set(r, a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
   }

   /** r = v tourné de ang autour de l'axe unitaire a (Rodrigues, comme V.rot ; r différent de v et a). */
   private static void rot(float[] r, float[] v, float[] a, float ang) {
      float co = cos(ang), si = sin(ang), k = (a[0] * v[0] + a[1] * v[1] + a[2] * v[2]) * (1 - co);
      r[0] = v[0] * co + (a[1] * v[2] - a[2] * v[1]) * si + a[0] * k;
      r[1] = v[1] * co + (a[2] * v[0] - a[0] * v[2]) * si + a[1] * k;
      r[2] = v[2] * co + (a[0] * v[1] - a[1] * v[0]) * si + a[2] * k;
   }
}
