package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.PI;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 209 Chaînes de l'Enfer : deux chaînes de fer chauffé au rouge ligotent l'arme en croix et glissent lentement ;
 * des flammes infernales lèchent le dessus de l'arme, des braises s'envolent.
 */
final class FxInferno implements FxEffect {
   static final int LINK = 1, FLAME = 2, EMBER = 3;

   @Override
   public float heldFade() {
      return 0.25F;   // chaînes : presque invisibles pour le porteur (elles traversent la vue), pleines pour les autres
   }

   @Override
   public void build(FxContext c) {
      float t = c.t;
      float L = c.L();
      GunShape s = c.shape;
      boolean pistol = s.aspect() > 0.42F;
      // découpe des maillons selon la qualité : 8×5 en gros plan, 6×3 en jeu, 4×3 de loin (fil de 3 mm : la section ne se voit pas)
      int segs = c.quality >= 0.9F ? 8 : c.quality >= 0.55F ? 6 : 4, sides = c.quality >= 0.9F ? 5 : 3;
      for (int ch = 0; ch < 2; ch++) {
         Coil coil = Coil.build(c, 160, 0.06F, 0.86F, pistol ? 1.0F : 1.5F, ch * PI + 0.3F, 0.014F, 0, 0, 0);
         float total = coil.arc[coil.arc.length - 1];
         float step = 0.021F * L;
         float slide = (t * 0.025F * L) % (2 * step);
         int seg = 0;
         int count = 0;
         for (float a = slide; a < total && count < 140; a += step, count++) {
            while (seg < coil.arc.length - 2 && coil.arc[seg + 1] < a) seg++;
            float f = (a - coil.arc[seg]) / Math.max(coil.arc[seg + 1] - coil.arc[seg], 1e-6F);
            V p = coil.pts[seg].lerp(coil.pts[seg + 1], f);
            V tan = coil.pts[seg + 1].sub(coil.pts[seg]).norm();
            V out = coil.out[seg].perp(tan);
            int idx = Math.round((a - slide) / step) + (int) Math.floor((t * 0.025F * L) / (2 * step)) * 2;
            V normal = (idx & 1) == 0 ? out : out.cross(tan);
            c.mesh.set(SOLID, LINK).p(0.5F + 0.5F * sin(a / L * 9 - t * 1.5F), 0).seed(idx & 0x7FFF, ch);
            FxDraw.torus(c, p, normal, tan, 0.0125F * L, 0.0078F * L, 0.0031F * L, segs, sides);
         }
      }
      // flammes sur les faces tournées vers le ciel
      float[] nr = c.surf.nrm;
      V up = c.up;
      Surfaces.Subset top = c.surf.select(i -> nr[i * 3] * up.x + nr[i * 3 + 1] * up.y + nr[i * 3 + 2] * up.z > 0.45F);
      if (top.empty()) top = c.surf.select(i -> true);
      int nf = c.count(30, 12);
      for (int j = 0; j < nf; j++) {
         float per = 0.7F + 0.6F * hash(j, 1, 1);
         float cyc = t / per + hash(j, 1, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         int fi = top.pick(hash(j, ci, 3));
         if (fi < 0) continue;
         V base = c.surf.point(fi, hash(j, ci, 4), hash(j, ci, 5));
         float h = L * (0.045F + 0.05F * hash(j, ci, 6)) * sin(PI * Math.min(1, life * 1.3F));
         V lean = c.up.add(c.f.x, -0.25F + 0.1F * sin(t * 3 + j)).norm();
         c.mesh.set(ADD, FLAME).p(life, hash(j, ci, 7)).seed(j * 17 + ci, 0);
         FxDraw.streak(c, base.add(lean, -h * 0.08F), lean, h, h * 0.55F);
      }
      // braises
      int ne = c.count(24, 8);
      for (int j = 0; j < ne; j++) {
         float per = 1.6F + hash(j, 3, 1);
         float cyc = t / per + hash(j, 3, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         int fi = top.pick(hash(j, ci, 3));
         if (fi < 0) continue;
         V p = c.surf.point(fi, hash(j, ci, 4), hash(j, ci, 5)).add(c.up, life * 0.16F * L)
            .add(c.f.z, sin(life * 8 + j) * 0.012F * L).add(c.f.x, -life * 0.04F * L);
         c.mesh.set(ADD, EMBER).p(1 - life, 0).seed(j, 0);
         FxDraw.billboard(c, p, 0.006F * L, 0.006F * L, 0);
      }
   }
}
