package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.ALPHA;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 208 Séraphin : deux ailes de plumes de lumière déployées de part et d'autre de la carcasse battent lentement,
 * une auréole d'or flotte au-dessus, des plumes et des éclats de lumière tombent doucement.
 */
final class FxSeraph implements FxEffect {
   static final int FEATHER = 1, FEATHER_GLOW = 2, HALO = 3, HALO_GLOW = 4, MOTE = 5;

   @Override
   public void build(FxContext c) {
      float t = c.t;
      float L = c.L();
      GunShape s = c.shape;
      float ws = (c.firstPerson ? 0.3F : 0.45F) * c.f.s;
      float xs = s.xAt(0.34F);
      for (int sg = -1; sg <= 1; sg += 2) {
         float flap = 0.28F * sin(t * 1.5F) - 0.05F;
         V out = c.f.z.mul(sg * 0.82F).add(c.f.x, -0.57F).rot(c.f.x, -sg * flap);
         V up = c.f.y.rot(c.f.x, -sg * flap);
         V back = c.f.x.neg();
         V nrm = out.cross(up).mul(sg);
         V sh = c.f.at(xs, s.top(xs) - 0.01F, s.zMid(xs) + sg * s.halfW(xs));
         V elbow = sh.add(out, 0.09F * ws).add(up, 0.075F * ws).add(back, 0.02F * ws);
         V wrist = elbow.add(out, 0.11F * ws).add(up, 0.04F * ws).add(back, 0.05F * ws);
         // rémiges primaires : éventail depuis le poignet
         for (int k = 0; k < 9; k++) {
            float q = k / 8F;
            V root = wrist.lerp(elbow, q * 0.35F);
            V dir = out.mul(0.9F - q * 0.9F).add(up, 0.35F - q * 0.3F).add(back, 0.2F + q * 0.9F).norm();
            float len = (0.2F - q * 0.05F) * ws;
            this.feather(c, root, dir, nrm, len, 0, sg * 40 + k, t);
         }
         // secondaires : le long de l'avant-bras, vers l'arrière
         for (int k = 0; k < 10; k++) {
            float q = k / 9F;
            V root = elbow.lerp(sh, q * 0.85F).lerp(wrist.lerp(elbow, 0.35F), (1 - q) * 0.3F);
            V dir = back.mul(0.85F).add(out, 0.35F * (1 - q)).add(up, -0.25F).norm();
            float len = (0.13F - q * 0.035F) * ws;
            this.feather(c, root, dir, nrm, len, 0.5F, sg * 40 + 10 + k, t);
         }
         // couvertures : petites plumes serrées sur l'arc de l'aile
         for (int k = 0; k < 12; k++) {
            float q = k / 11F;
            V root = q < 0.5F ? sh.lerp(elbow, q * 2) : elbow.lerp(wrist, (q - 0.5F) * 2);
            V dir = back.mul(0.7F).add(out, 0.4F).add(up, -0.1F).norm();
            this.feather(c, root, dir, nrm, 0.055F * ws, 1, sg * 40 + 22 + k, t);
         }
      }
      // auréole d'or
      float xh = s.xAt(0.4F);
      V hc = c.f.at(xh, s.top(xh) + 0.035F + 0.004F * sin(t * 1.2F), s.zMid(xh));
      V axis = c.f.y.rot(c.f.x, 0.25F).rot(c.f.z, 0.15F);
      c.mesh.set(SOLID, HALO).seed(1, 0);
      FxDraw.torus(c, hc, axis, c.f.x, 0.028F * L, 0.028F * L, 0.003F * L, c.count(32, 16), 4);
      c.mesh.set(ADD, HALO_GLOW).seed(2, 0);
      FxDraw.torus(c, hc, axis, c.f.x, 0.028F * L, 0.028F * L, 0.009F * L, c.count(32, 16), 5);
      // plumes et éclats de lumière qui tombent
      for (int j = 0; j < c.count(8, 3); j++) {
         float per = 4 + 2 * hash(j, 1, 1);
         float cyc = t / per + hash(j, 1, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         float gx = s.xAt(0.15F + 0.4F * hash(j, ci, 3));
         V p = c.f.at(gx, s.top(gx) + 0.05F, s.zMid(gx) + (hash(j, ci, 4) - 0.5F) * 0.12F).add(c.up, -life * 0.13F * L)
            .add(c.f.x, sin(life * 5 + j) * 0.015F * L);
         V dir = c.up.neg().rot(c.f.x, sin(life * 6 + j) * 0.9F).rot(c.up, t + j);
         c.mesh.set(ALPHA, FEATHER).p(0.5F, 0).fade(Math.min(1, Math.min(life * 6, (1 - life) * 4))).seed(200 + j, 0);
         V n = dir.anyPerp();
         FxDraw.quad(c, p, n.cross(dir).mul(0.004F * L), dir.mul(0.013F * L));
      }
      for (int j = 0; j < c.count(16, 6); j++) {
         float life = FxContext.fract(t * 0.3F + hash(j, 7, 1));
         float gx = s.xAt(0.1F + 0.5F * hash(j, 7, 2));
         V p = c.f.at(gx, s.top(gx) + 0.045F * (1 - life) + 0.01F, s.zMid(gx) + (hash(j, 7, 3) - 0.5F) * 0.14F);
         c.mesh.set(ADD, MOTE).p(sin(3.1416F * life), 0).seed(j, 0);
         FxDraw.billboard(c, p, 0.007F * L, 0.007F * L, 0);
      }
   }

   private void feather(FxContext c, V root, V dir, V nrm, float len, float layer, int id, float t) {
      float ruffle = 0.05F * sin(t * 3 + id * 0.7F);
      V d = dir.rot(nrm, ruffle);
      V across = nrm.cross(d).norm();
      V center = root.add(d, len * 0.5F);
      c.mesh.set(ALPHA, FEATHER).p(layer, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, center, across.mul(len * 0.19F), d.mul(len * 0.5F));
      c.mesh.set(ADD, FEATHER_GLOW).p(layer, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, center, across.mul(len * 0.19F), d.mul(len * 0.5F));
   }
}
