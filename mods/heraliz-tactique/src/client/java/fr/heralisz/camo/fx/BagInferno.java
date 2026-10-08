package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.PI;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 209 Chaînes de l'Enfer sur un sac : deux chaînes de fer chauffé au rouge ligotent le sac en croix (X sur le dos, posé sur
 * sa vraie surface : BagFrame.back) et glissent lentement ; aux bords du sac elles passent par-dessus et repartent vers
 * l'avant. Des flammes lèchent le dessus du sac, des braises s'envolent.
 */
final class BagInferno implements BagEffects.BagEffect {
   static final int LINK = 1, FLAME = 2, EMBER = 3;
   private static final int N = 64, WRAP = 3;
   private final V[] pts = new V[N + 1 + 2 * WRAP];
   private final float[] arc = new float[N + 1 + 2 * WRAP];

   @Override
   public void build(FxContext c, BagFrame b) {
      float t = c.t, k = b.scale;
      int segs = c.quality >= 0.9F ? 10 : c.quality >= 0.45F ? 8 : 6, sides = c.quality >= 0.45F ? 5 : 4;
      // grosse chaîne décollée du sac (sinon elle se perd dans le motif de lave)
      float lift = 0.024F * k;
      float ra = 0.044F * k, rb = 0.027F * k, rt = 0.0095F * k, step = 0.07F * k;
      for (int ch = 0; ch < 2; ch++) {
         // diagonale du dos (X), posée sur la vraie surface du sac (carte de profondeur : suit sa forme, même en biais) ;
         // là où il n'y a plus de sac, la chaîne passe par-dessus le bord et repart vers l'avant
         int n = 0;
         for (int i = 0; i <= N; i++) {
            float u = -0.94F + 1.88F * i / N;
            V p = b.back(u, ch == 0 ? -u : u, lift);
            if (p == null) {
               this.links(c, b, n, ch, t, k, ra, rb, rt, step, segs, sides);
               n = 0;
               continue;
            }
            if (n == 0) n = this.wrap(b, p, b.back(u + 0.06F, ch == 0 ? -u - 0.06F : u + 0.06F, lift), k, true);
            this.pts[n] = p;
            this.arc[n] = n == 0 ? 0 : this.arc[n - 1] + p.dist(this.pts[n - 1]);
            n++;
         }
         this.links(c, b, n, ch, t, k, ra, rb, rt, step, segs, sides);
      }
      // flammes sur le dessus du sac
      int nf = c.count(16, 8);
      for (int j = 0; j < nf; j++) {
         float per = 0.7F + 0.6F * hash(j, 1, 1);
         float cyc = t / per + hash(j, 1, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         V base = b.top((hash(j, ci, 4) - 0.5F) * 1.7F, (hash(j, ci, 5) - 0.5F) * 1.5F + 0.2F);
         float h = k * (0.06F + 0.07F * hash(j, ci, 6)) * sin(PI * Math.min(1, life * 1.3F));
         V lean = c.up.add(b.side, 0.12F * sin(t * 3 + j)).norm();
         c.mesh.set(ADD, FLAME).p(life, hash(j, ci, 7)).seed(j * 17 + ci, 0);
         FxDraw.streak(c, base.add(lean, -h * 0.08F), lean, h, h * 0.55F);
      }
      // braises
      int ne = c.count(18, 8);
      for (int j = 0; j < ne; j++) {
         float per = 1.6F + hash(j, 3, 1);
         float cyc = t / per + hash(j, 3, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         V p = b.top((hash(j, ci, 4) - 0.5F) * 1.8F, (hash(j, ci, 5) - 0.5F) * 1.6F + 0.2F).add(c.up, life * 0.4F * k)
            .add(b.side, sin(life * 8 + j) * 0.025F * k);
         c.mesh.set(ADD, EMBER).p(1 - life, 0).seed(j, 0);
         FxDraw.billboard(c, p, 0.013F * k, 0.013F * k, 0);
      }
   }

   /**
    * Début d'un tronçon : la chaîne arrive de derrière le bord du sac (quelques points qui plongent vers l'avant avant p).
    * Rend le nombre de points écrits.
    */
   private int wrap(BagFrame b, V p, V next, float k, boolean start) {
      if (next == null) return 0;
      V dir = p.sub(next).norm();   // vers l'extérieur du tronçon
      for (int w = 0; w < WRAP; w++) {
         float f = (WRAP - w) / (float) WRAP;
         this.pts[w] = p.add(dir, 0.05F * k * f).add(b.out, -0.12F * k * f * f);
         this.arc[w] = w == 0 ? 0 : this.arc[w - 1] + this.pts[w].dist(this.pts[w - 1]);
      }
      return WRAP;
   }

   /** Maillons le long des n premiers points (fin du tronçon : la chaîne repasse aussi par-dessus le bord). */
   private void links(FxContext c, BagFrame b, int n, int ch, float t, float k, float ra, float rb, float rt, float step, int segs, int sides) {
      if (n < 2) return;
      V last = this.pts[n - 1], dir = last.sub(this.pts[n - 2]).norm();
      for (int w = 1; w <= WRAP && n < this.pts.length; w++) {
         float f = w / (float) WRAP;
         this.pts[n] = last.add(dir, 0.05F * k * f).add(b.out, -0.12F * k * f * f);
         this.arc[n] = this.arc[n - 1] + this.pts[n].dist(this.pts[n - 1]);
         n++;
      }
      float total = this.arc[n - 1];
      float slide = (t * 0.03F * k) % (2 * step);
      int seg = 0;
      for (float a = slide; a < total; a += step) {
         while (seg < n - 2 && this.arc[seg + 1] < a) seg++;
         float f = (a - this.arc[seg]) / Math.max(this.arc[seg + 1] - this.arc[seg], 1e-6F);
         V p = this.pts[seg].lerp(this.pts[seg + 1], f);
         V tan = this.pts[seg + 1].sub(this.pts[seg]).norm();
         V radial = b.out.perp(tan);
         int idx = Math.round((a - slide) / step) + (int) Math.floor((t * 0.03F * k) / (2 * step)) * 2;
         V normal = (idx & 1) == 0 ? radial : radial.cross(tan);
         c.mesh.set(SOLID, LINK).p(0.5F + 0.5F * sin(a / k * 14 - t * 1.5F), 0).seed(idx & 0x7FFF, ch);
         FxDraw.torus(c, p, normal, tan, ra, rb, rt, segs, sides);
      }
   }
}
