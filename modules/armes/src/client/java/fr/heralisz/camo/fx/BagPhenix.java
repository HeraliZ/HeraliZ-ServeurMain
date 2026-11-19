package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.PI;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/**
 * 210 Phénix sur un sac : deux grandes ailes de flammes sortent du haut du dos, derrière le sac, ouvertes sur les côtés et
 * relevées en V (squelette des ailes du Séraphin : vues en entier de dos) ; des flammes montent du bras de l'aile, un
 * éventail de longues flammes en part au bout ; elles battent lentement, au rythme des ailes de l'arme. Des braises montent
 * du sac et des ailes, une lueur de chaleur flotte entre les épaules. Mêmes éléments que sur l'arme (shader fx_210, tout
 * additif) ; calculs à plat dans des tableaux gardés : aucun objet créé dans les boucles.
 */
final class BagPhenix implements BagEffects.BagEffect {
   // repère et squelette d'une aile (repère de rendu), réécrits à chaque image
   private final float[] s = new float[3], u = new float[3], o = new float[3], along = new float[3], upw = new float[3],
      nrm = new float[3];
   private final float[] sh = new float[3], elbow = new float[3], wrist = new float[3], main = new float[3];
   private final float[] root = new float[3], dir = new float[3], d = new float[3], across = new float[3];

   @Override
   public void build(FxContext c, BagFrame b) {
      float t = c.t, k = b.scale;
      // même battement que les ailes de l'arme : l'aile s'ouvre et se relève, puis se replie un peu vers l'arrière
      float bat = 0.5F + 0.5F * sin(t * 1.35F);
      // les ailes partent du haut du dos, derrière le sac
      float back = b.backDepth() * 0.8F;
      for (int sg = -1; sg <= 1; sg += 2) {
         this.wing(c, b, sg, bat, back, k, t);
      }
      this.braises(c, b, t, k);
      // lueur de chaleur entre les épaules
      c.mesh.set(ADD, FxPhenix.LUEUR).p(0.16F, 0).seed(90, 0);
      FxDraw.billboard(c, b.neck.x + b.out.x * back + b.up.x * 0.06F * k, b.neck.y + b.out.y * back + b.up.y * 0.06F * k,
         b.neck.z + b.out.z * back + b.up.z * 0.06F * k, 0.9F * k, 0.5F * k);
   }

   private void wing(FxContext c, BagFrame b, int sg, float bat, float back, float k, float t) {
      set(this.s, b.side.x * sg, b.side.y * sg, b.side.z * sg);
      set(this.u, b.up.x, b.up.y, b.up.z);
      set(this.o, b.out.x, b.out.y, b.out.z);
      // ailes ouvertes sur les côtés, un peu en arrière, relevées en V
      float open = 0.72F + 0.12F * bat, rise = 0.44F + 0.06F * bat;
      for (int i = 0; i < 3; i++) this.along[i] = this.s[i] * open + this.o[i] * (1 - open) + this.u[i] * rise;
      norm(this.along);
      float du = dot(this.u, this.along);
      for (int i = 0; i < 3; i++) this.upw[i] = this.u[i] - this.along[i] * du;
      norm(this.upw);
      cross(this.nrm, this.along, this.upw);
      norm(this.nrm);
      if (dot(this.nrm, this.o) < 0) for (int i = 0; i < 3; i++) this.nrm[i] = -this.nrm[i];
      float w = 1.15F * k;
      // épaules : haut du dos, derrière le sac, un peu écartées
      float ecart = Math.max(0.45F * b.hx, 0.1F * k);
      for (int i = 0; i < 3; i++) {
         float n = i == 0 ? b.neck.x : i == 1 ? b.neck.y : b.neck.z;
         this.sh[i] = n + this.o[i] * back - this.u[i] * 0.08F * k + this.s[i] * ecart;
         this.elbow[i] = this.sh[i] + this.along[i] * 0.30F * w + this.upw[i] * 0.24F * w;
         this.wrist[i] = this.elbow[i] + this.along[i] * 0.31F * w + this.upw[i] * (0.05F + 0.03F * bat) * w;
         this.main[i] = this.wrist[i] + (this.elbow[i] - this.wrist[i]) * 0.35F;
      }
      int id0 = sg < 0 ? 0 : 60;
      // rémiges : grand éventail de longues flammes au bout de l'aile, de vers l'extérieur et le haut à vers le bas
      for (int i = 0; i < 11; i++) {
         float q = i / 10F;
         mix(this.root, this.wrist, this.elbow, q * 0.42F);
         lin(this.dir, this.along, 1 - 0.55F * q, this.upw, 0.55F - 1.15F * q);
         this.plume(c, (0.55F - 0.18F * q) * w, 0, id0 + i, 1 - 0.25F * q, 0.24F, t);
      }
      // flammes qui montent du bras de l'aile
      for (int i = 0; i < 12; i++) {
         float q = i / 11F;
         this.arm(q);
         lin(this.dir, this.upw, 0.95F, this.along, 0.35F - 0.25F * q);
         this.plume(c, (0.42F - 0.12F * q) * w, 0.5F, id0 + 20 + i, 0.9F, 0.26F, t);
      }
      // couvertures : flammes courtes serrées sur le bord d'attaque
      for (int i = 0; i < 12; i++) {
         float q = i / 11F;
         this.arm(q * 0.98F);
         for (int e = 0; e < 3; e++) this.root[e] += this.upw[e] * 0.03F * w;
         lin(this.dir, this.upw, 0.6F, this.along, 0.6F);
         this.plume(c, 0.16F * w, 1, id0 + 40 + i, 0.85F, 0.3F, t);
      }
   }

   /** Point du bras de l'aile dans root : 0 = près de la main, 1 = épaule (en passant par le coude). */
   private void arm(float q) {
      if (q < 0.6F) mix(this.root, this.main, this.elbow, q / 0.6F);
      else mix(this.root, this.elbow, this.sh, (q - 0.6F) / 0.4F);
   }

   /** Plume de flamme (mêmes règles que FxPhenix) partant de root vers dir, dans le plan de l'aile (repère de rendu). */
   private void plume(FxContext c, float len, float layer, int id, float bright, float large, float t) {
      norm(this.dir);
      float a = 0.07F * sin(t * 3.1F + id * 0.7F), co = cos(a), si = sin(a), kk = dot(this.nrm, this.dir) * (1 - co);
      float[] v = this.dir, n = this.nrm;
      set(this.d, v[0] * co + (n[1] * v[2] - n[2] * v[1]) * si + n[0] * kk, v[1] * co + (n[2] * v[0] - n[0] * v[2]) * si + n[1] * kk,
         v[2] * co + (n[0] * v[1] - n[1] * v[0]) * si + n[2] * kk);
      norm(this.d);
      cross(this.across, this.nrm, this.d);
      norm(this.across);
      float h = len * 0.5F, wd = len * large;
      c.mesh.set(ADD, FxPhenix.PLUME).p(bright, layer).seed(id, 0);
      FxDraw.quad(c, this.root[0] + this.d[0] * h, this.root[1] + this.d[1] * h, this.root[2] + this.d[2] * h,
         this.across[0] * wd, this.across[1] * wd, this.across[2] * wd, this.d[0] * h, this.d[1] * h, this.d[2] * h);
   }

   /** Braises : montent du dessus du sac (sa boîte) et des ailes. */
   private void braises(FxContext c, BagFrame b, float t, float k) {
      V up = b.up, side = b.side, out = b.out;
      int ne = c.count(24, 10);
      for (int j = 0; j < ne; j++) {
         float per = 1.6F + hash(j, 3, 1);
         float cyc = t / per + hash(j, 3, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         float px, py, pz;
         if (hash(j, ci, 9) < 0.6F) {
            float fx = (hash(j, ci, 4) - 0.5F) * 1.8F * b.hx, fz = ((hash(j, ci, 5) - 0.5F) * 1.6F + 0.2F) * b.hz;
            px = b.o.x + side.x * fx + up.x * b.hy + out.x * fz;
            py = b.o.y + side.y * fx + up.y * b.hy + out.y * fz;
            pz = b.o.z + side.z * fx + up.z * b.hy + out.z * fz;
         } else {
            // dans les ailes : entre l'épaule et le bout, au-dessus du bras (squelette de la dernière aile calculée)
            float sg = hash(j, ci, 8) < 0.5F ? -1 : 1, q = hash(j, ci, 6), hh = (0.05F + 0.3F * hash(j, ci, 7)) * k;
            float ax = this.sh[0] + (this.wrist[0] - this.sh[0]) * q, ay = this.sh[1] + (this.wrist[1] - this.sh[1]) * q,
               az = this.sh[2] + (this.wrist[2] - this.sh[2]) * q;
            if (sg < 0) {
               // l'autre aile : symétrique par rapport au plan du dos (côté opposé)
               float m = (ax - b.neck.x) * side.x + (ay - b.neck.y) * side.y + (az - b.neck.z) * side.z;
               ax -= 2 * m * side.x;
               ay -= 2 * m * side.y;
               az -= 2 * m * side.z;
            }
            px = ax + up.x * hh;
            py = ay + up.y * hh;
            pz = az + up.z * hh;
         }
         float rise = life * 0.4F * k, wob = sin(life * 8 + j) * 0.025F * k;
         float sz = (0.016F + 0.012F * hash(j, 2, 2)) * k;
         float br = sin(PI * Math.min(1, life * 1.15F)) * (0.6F + 0.4F * hash(j, ci, 11));
         c.mesh.set(ADD, FxPhenix.BRAISE).p(br, 0).seed(j, 0);
         FxDraw.billboard(c, px + c.up.x * rise + side.x * wob, py + c.up.y * rise + side.y * wob, pz + c.up.z * rise + side.z * wob, sz, sz);
      }
   }

   // ------------------------------------------------------------------ petits calculs sur des tableaux de 3 flottants

   private static void set(float[] r, float x, float y, float z) {
      r[0] = x;
      r[1] = y;
      r[2] = z;
   }

   private static float dot(float[] a, float[] b) {
      return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
   }

   private static void norm(float[] r) {
      float l = (float) Math.sqrt(dot(r, r));
      if (l < 1e-9F) {
         set(r, 0, 1, 0);
      } else {
         r[0] /= l;
         r[1] /= l;
         r[2] /= l;
      }
   }

   /** r = a · ka + b · kb */
   private static void lin(float[] r, float[] a, float ka, float[] b, float kb) {
      set(r, a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb);
   }

   /** r = a + (b - a) · k */
   private static void mix(float[] r, float[] a, float[] b, float k) {
      set(r, a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k);
   }

   /** r = a × b (r différent de a et b). */
   private static void cross(float[] r, float[] a, float[] b) {
      set(r, a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
   }
}
