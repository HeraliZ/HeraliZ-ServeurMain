package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;

/** Effet de camo légendaire du prototype : arcs électriques (Tempête, 70). */
final class FxLegacy {
   private FxLegacy() {
   }

   /** Tempête : arcs électriques qui sautent d'un point à l'autre de l'arme en crépitant. */
   static final class Storm implements FxEffect {
      static final int BOLT = 1;

      @Override
      public void build(FxContext c) {
         float t = c.t;
         float L = c.L();
         Surfaces s = c.surf;
         int crack = (int) Math.floor(t * 24);
         for (int a = 0; a < 3; a++) {
            int ep = (int) Math.floor(t * 5 + a * 0.33F);
            if (hash(a, ep, 11) < 0.2F) continue;
            int si = s.pick(hash(a, ep, 12)), ei = -1;
            if (si < 0) return;
            V ps = s.center(si);
            for (int tr = 0; tr < 12; tr++) {
               int cand = s.pick(hash(a, ep, 13 + tr));
               float d = s.center(cand).dist(ps);
               if (d > 0.12F * L && d < 0.45F * L) {
                  ei = cand;
                  break;
               }
            }
            if (ei < 0) continue;
            V ns = s.normal(si), ne = s.normal(ei);
            V p0 = ps.add(ns, 0.012F * L), p1 = s.center(ei).add(ne, 0.012F * L);
            V up = ns.add(ne).norm();
            float lift = 0.015F * L + 0.025F * L * hash(a, ep, 20);
            float bright = 0.7F + 0.3F * hash(a, crack, 99);
            int n = 20;
            V[] pts = new V[n + 1];
            for (int i = 0; i <= n; i++) {
               float u = (float) i / n, env = sin(FxContext.PI * u);
               V p = p0.lerp(p1, u).add(up, lift * env);
               float fu = u * 4, w = fu - (int) fu;
               int c0 = (int) fu;
               float[] off = new float[3];
               for (int x = 0; x < 3; x++) {
                  float coarse = (hash(a * 7 + c0, ep, 50 + x) * (1 - w) + hash(a * 7 + c0 + 1, ep, 50 + x) * w - 0.5F) * 2;
                  float fine = (hash(a * 31 + i, crack, 1 + x) - 0.5F) * 2;
                  off[x] = (coarse * 0.03F * L + fine * 0.009F * L) * env;
               }
               pts[i] = p.add(new V(off[0], off[1], off[2]));
            }
            bolt(c, pts, L, bright);
            int bi = 3 + (int) (hash(a, ep, 30) * 6);
            V dir = new V(hash(a, crack, 31) - 0.5F, hash(a, crack, 32) - 0.5F, hash(a, crack, 33) - 0.5F).add(up, 0.6F).norm();
            V[] br = new V[6];
            br[0] = pts[bi];
            for (int i = 1; i < br.length; i++) {
               br[i] = br[i - 1].add(dir, 0.018F * L).add(new V(hash(a * 17 + i, crack, 41) - 0.5F, hash(a * 17 + i, crack, 42) - 0.5F, hash(a * 17 + i, crack, 43) - 0.5F), 0.02F * L);
            }
            bolt(c, br, L * 0.6F, bright * 0.8F);
         }
      }

      private static void bolt(FxContext c, V[] pts, float L, float b) {
         float[] layers = {0.03F, 0.011F, 0.0032F};
         for (int l = 0; l < 3; l++) {
            float[] w = new float[pts.length];
            java.util.Arrays.fill(w, layers[l] * L);
            c.mesh.set(ADD, BOLT).p(l / 2F, b).seed(l, 0);
            FxDraw.ribbon(c, pts, w, 0, 1);
         }
      }
   }
}
