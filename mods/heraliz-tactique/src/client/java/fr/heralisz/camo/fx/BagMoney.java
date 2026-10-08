package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 231 Money Gun sur un sac : un sac bourré de billets. De temps en temps un billet s'échappe du dessus, s'envole un peu,
 * puis retombe en tournoyant et en virevoltant (mêmes billets que ceux éjectés par l'arme).
 */
final class BagMoney implements BagEffects.BagEffect {
   static final int BILL = 1;
   private static final float LIFE = 2.2F;

   @Override
   public void build(FxContext c, BagFrame b) {
      float t = c.t, k = b.scale;
      for (int j = 0; j < 4; j++) {
         float per = 2.6F + 1.2F * hash(j, 1, 41);
         float cyc = t / per + hash(j, 2, 41);
         int ci = (int) Math.floor(cyc);
         float age = (cyc - ci) * per;
         if (age >= LIFE || hash(j, ci, 43) < 0.3F) continue;
         V port = b.top((hash(j, ci, 44) - 0.5F) * 1.4F, (hash(j, ci, 45) - 0.5F) * 1.2F + 0.2F);
         V S = b.side.mul(hash(j, ci, 46) < 0.5F ? -1 : 1);
         this.bill(c, port, b.out, c.up, S, 0.9F * k, age, j * 1000 + (ci & 0x3FF));
      }
   }

   /** Billet sorti il y a a secondes : s'envole, freiné par l'air, puis retombe en tournant sur lui-même. */
   private void bill(FxContext c, V port, V F, V U, V S, float L, float a, int id) {
      float h1 = hash(id, 1, 37), h2 = hash(id, 2, 37), h3 = hash(id, 3, 37), h4 = hash(id, 4, 37);
      float kd = 2.6F;
      float drag = (1 - (float) Math.exp(-kd * a)) / kd;
      V v0 = S.mul(0.35F + 0.4F * h1).add(U, 1.0F + 0.5F * h2).add(F, 0.2F + 0.4F * h3);
      float ph = h4 * TAU;
      V p = port.add(v0, drag * L)
         .add(U, -0.28F * a * a * L)
         .add(S, 0.06F * sin(a * 6 + ph) * L * a)
         .add(F, 0.05F * cos(a * 4 + ph) * L * a);
      float spin = a * (5 + 4 * h1) + ph, tilt = sin(a * 3.5F + ph) * 0.9F;
      V ax = F.rot(U, tilt).norm();
      V ay = S.rot(ax, spin).norm();
      float grow = smooth(0, 0.1F, a) * (1 - smooth(LIFE - 0.4F, LIFE, a));
      float w = 0.085F * L * grow, h = 0.037F * L * grow;
      if (w < 0.002F) return;
      V bend = ax.cross(ay).norm();
      c.mesh.set(SOLID, BILL).p(0, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, p.add(ax, -w * 0.5F).add(bend, 0.12F * h), ax.mul(w * 0.5F), ay.mul(h));
      c.mesh.set(SOLID, BILL).p(1, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, p.add(ax, w * 0.5F).add(bend, 0.12F * h), ax.mul(w * 0.5F), ay.mul(h));
   }
}
