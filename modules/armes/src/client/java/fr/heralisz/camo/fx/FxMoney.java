package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 231 Money Gun : l'arme est emballée de billets, chargeur et canon en or massif (surface : moneyGun dans camo_template).
 * Chaque tir éjecte des billets par la fenêtre d'éjection, à la place des douilles : ils partent sur le côté, freinés par l'air,
 * tournoient et virevoltent en retombant. Des éclats d'or brillent sur l'arme. Vu par tous (les tirs des autres joueurs sont
 * suivis aussi, ShotTracker) ; pour le porteur, les billets filent sur le côté comme des douilles (pleinement visibles).
 */
final class FxMoney implements FxEffect {
   static final int BILL = 1, GLINT = 2;
   private static final float LIFE = 1.5F;

   @Override
   public void build(FxContext c) {
      float L = c.L(), t = c.t;
      GunShape s = c.shape;
      V F = c.f.x, U = c.f.y, S = c.f.z;
      // fenêtre d'éjection : flanc droit du boîtier, un peu en arrière du milieu de l'arme
      float xe = s.xAt(0.5F);
      V port = c.f.at(xe, s.top(xe) - 0.012F, s.zHi(xe));
      float[] ages = c.shotAges;
      if (ages != null) {
         for (int i = 0; i < ages.length; i++) {
            float a = ages[i];
            if (a >= LIFE) continue;
            int id = c.shotSeq - i;
            int n = hash(id, 1, 31) < 0.25F ? 2 : 1;
            for (int b = 0; b < n; b++) this.bill(c, port, F, U, S, L, a - b * 0.04F, id * 2 + b);
         }
      }
      // éclats d'or qui scintillent sur l'arme
      Surfaces sf = c.surf;
      if (sf.n > 0) {
         for (int k = 0; k < 5; k++) {
            float cyc = t * 0.6F + hash(k, 3, 33);
            int ci = (int) Math.floor(cyc);
            float g = cyc - ci;
            int fi = sf.pick(hash(k, ci, 34));
            if (fi < 0) continue;
            float k0 = smooth(0, 0.15F, g) * (1 - smooth(0.25F, 0.5F, g));
            if (k0 < 0.02F) continue;
            V p = sf.point(fi, hash(k, ci, 35), hash(k, ci, 36)).add(sf.normal(fi), 0.004F * L);
            c.mesh.set(ADD, GLINT).p(k0, 0).seed(k, ci & 0x7FFF);
            FxDraw.billboard(c, p, 0.07F * L * k0, 0.07F * L * k0, g * 2);
         }
      }
   }

   /** Un billet éjecté il y a a secondes : part sur le côté, freiné par l'air, tournoie et virevolte en tombant. */
   private void bill(FxContext c, V port, V F, V U, V S, float L, float a, int id) {
      if (a < 0) return;
      float h1 = hash(id, 1, 37), h2 = hash(id, 2, 37), h3 = hash(id, 3, 37), h4 = hash(id, 4, 37);
      float k = 3.2F;
      float drag = (1 - (float) Math.exp(-k * a)) / k;
      V v0 = S.mul(1.3F + 0.7F * h1).add(U, 0.9F + 0.6F * h2).add(F, -0.4F + 0.5F * h3);
      float ph = h4 * TAU;
      V p = port.add(v0, drag * L)
         .add(U, -0.55F * a * a * L)
         .add(S, 0.05F * sin(a * 7 + ph) * L * a)
         .add(F, 0.04F * cos(a * 5 + ph) * L * a);
      // orientation : il tourne autour de sa longueur et bascule
      float spin = a * (7 + 5 * h1) + ph, tilt = sin(a * 4.5F + ph) * 0.9F;
      V ax = F.rot(U, tilt).norm();
      V ay = S.rot(ax, spin).norm();
      float grow = smooth(0, 0.06F, a) * (1 - smooth(LIFE - 0.35F, LIFE, a));
      float w = 0.085F * L * grow, h = 0.037F * L * grow;
      // léger pli au milieu : deux moitiés un peu inclinées
      V bend = ax.cross(ay).norm();
      c.mesh.set(SOLID, BILL).p(0, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, p.add(ax, -w * 0.5F).add(bend, 0.12F * h), ax.mul(w * 0.5F), ay.mul(h));
      c.mesh.set(SOLID, BILL).p(1, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, p.add(ax, w * 0.5F).add(bend, 0.12F * h), ax.mul(w * 0.5F), ay.mul(h));
   }

   @Override
   public boolean fullInHand() {
      return true;
   }
}
