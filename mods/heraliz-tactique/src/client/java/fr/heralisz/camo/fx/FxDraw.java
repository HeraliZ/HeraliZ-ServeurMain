package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.sin;

/**
 * Formes de base des effets. Chaque forme écrit dans c.mesh avec l'état courant (mode, sorte, paramètres) et des UV
 * que le shader de l'effet utilise pour dessiner (écailles le long d'un corps, flamme dans un sprite, runes sur un disque…).
 */
public final class FxDraw {
   private FxDraw() {
   }

   /** Sprite face à la caméra, centré en p, tourné de rot ; UV 0..1 (v = 1 en haut). */
   public static void billboard(FxContext c, V p, float w, float h, float rot) {
      V n = c.toCam(p);
      V up = c.up.perp(n);
      V right = up.cross(n);
      float cs = cos(rot), sn = sin(rot);
      V r = right.mul(cs).add(up, sn).mul(w * 0.5F);
      V u = up.mul(cs).add(right, -sn).mul(h * 0.5F);
      c.mesh.quad(p.sub(r).sub(u), p.add(r).sub(u), p.add(r).add(u), p.sub(r).add(u), 0, 0, 1, 1, n);
   }

   /** Sprite allongé le long de dir (traînée, goutte, flamme orientée), face à la caméra autant que possible. */
   public static void streak(FxContext c, V p, V dir, float len, float w) {
      V d = dir.norm();
      V n = c.toCam(p);
      V side = d.cross(n);
      if (side.len() < 1e-4F) side = d.anyPerp();
      side = side.norm().mul(w * 0.5F);
      V a = p, b = p.add(d, len);
      c.mesh.quad(a.sub(side), a.add(side), b.add(side), b.sub(side), 0, 0, 1, 1, n);
   }

   /** Quad orienté : centre, demi-axes ax / ay ; UV 0..1. */
   public static void quad(FxContext c, V p, V ax, V ay) {
      V n = ax.cross(ay).norm();
      c.mesh.quad(p.sub(ax).sub(ay), p.add(ax).sub(ay), p.add(ax).add(ay), p.sub(ax).add(ay), 0, 0, 1, 1, n);
   }

   /** Ruban face à la caméra le long d'une polyligne ; u de u0 à u1 le long, v 0..1 en travers. */
   public static void ribbon(FxContext c, V[] pts, float[] w, float u0, float u1) {
      int n = pts.length;
      if (n < 2) return;
      V[] l = new V[n], r = new V[n];
      V[] cam = new V[n];
      for (int i = 0; i < n; i++) {
         V t = pts[Math.min(i + 1, n - 1)].sub(pts[Math.max(i - 1, 0)]);
         cam[i] = c.toCam(pts[i]);
         V side = t.cross(cam[i]);
         side = side.len() < 1e-9F ? t.anyPerp() : side.norm();
         l[i] = pts[i].add(side, -w[i] * 0.5F);
         r[i] = pts[i].add(side, w[i] * 0.5F);
      }
      for (int i = 0; i + 1 < n; i++) {
         float ua = u0 + (u1 - u0) * i / (n - 1), ub = u0 + (u1 - u0) * (i + 1) / (n - 1);
         c.mesh.v(l[i], ua, 0, cam[i]);
         c.mesh.v(l[i + 1], ub, 0, cam[i + 1]);
         c.mesh.v(r[i + 1], ub, 1, cam[i + 1]);
         c.mesh.v(r[i], ua, 1, cam[i]);
      }
   }

   /** Ruban à orientation fixe (normale donnée par point) : ailes, rideaux d'aurore, plans de lumière. */
   public static void sheet(FxContext c, V[] a, V[] b, float u0, float u1) {
      int n = a.length;
      for (int i = 0; i + 1 < n; i++) {
         float ua = u0 + (u1 - u0) * i / (n - 1), ub = u0 + (u1 - u0) * (i + 1) / (n - 1);
         V nr = a[i + 1].sub(a[i]).cross(b[i].sub(a[i])).norm();
         c.mesh.v(a[i], ua, 0, nr);
         c.mesh.v(a[i + 1], ub, 0, nr);
         c.mesh.v(b[i + 1], ub, 1, nr);
         c.mesh.v(b[i], ua, 1, nr);
      }
   }

   /**
    * Tube lisse le long d'une polyligne. hint (optionnel) : direction « dessus » voulue en chaque point (v = 0 de ce côté) ;
    * sinon repère transporté. u de u0 à u1 le long, v 0..1 autour.
    */
   public static void tube(FxContext c, V[] pts, float[] r, V[] hint, int sides, float u0, float u1) {
      int n = pts.length;
      if (tubeU.length < n) tubeU = new float[n];
      float[] us = tubeU;
      for (int i = 0; i < n; i++) us[i] = u0 + (u1 - u0) * i / Math.max(1, n - 1);
      tube(c, pts, r, hint, sides, us);
   }

   /** Tableaux de travail des tubes (réutilisés : un piquot, un corps de serpent… par appel). */
   private static float[] tubeU = new float[16], tubeF = new float[16 * 9], ringA = new float[16 * 6], ringB = new float[16 * 6];

   /** Même tube avec une coordonnée u donnée par point (ex. longueur parcourue : écailles de taille constante). */
   public static void tube(FxContext c, V[] pts, float[] r, V[] hint, int sides, float[] us) {
      int n = pts.length;
      if (n < 2) return;
      // repère de chaque point, à plat : tangente, normale, binormale (9 flottants) ; mêmes règles que V.norm / perp / anyPerp
      if (tubeF.length < n * 9) tubeF = new float[n * 9];
      float[] F = tubeF;
      for (int i = 0; i < n; i++) {
         V a = pts[Math.min(i + 1, n - 1)], b = pts[Math.max(i - 1, 0)];
         float tx = a.x - b.x, ty = a.y - b.y, tz = a.z - b.z;
         float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
         if (tl < 1e-9F) {
            tx = 0; ty = 1; tz = 0;
         } else {
            tx /= tl; ty /= tl; tz /= tl;
         }
         float rx, ry, rz;
         if (hint != null) {
            rx = hint[i].x; ry = hint[i].y; rz = hint[i].z;
         } else if (i > 0) {
            rx = F[(i - 1) * 9 + 3]; ry = F[(i - 1) * 9 + 4]; rz = F[(i - 1) * 9 + 5];
         } else {
            rx = Float.NaN; ry = 0; rz = 0;
         }
         float nx = 0, ny = 0, nz = 0;
         boolean ok = false;
         if (!Float.isNaN(rx)) {
            float d = rx * tx + ry * ty + rz * tz;
            nx = rx - tx * d; ny = ry - ty * d; nz = rz - tz * d;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl >= 1e-6F) {
               nx /= nl; ny /= nl; nz /= nl;
               ok = true;
            }
         }
         if (!ok) {
            // n'importe quelle perpendiculaire (V.anyPerp) : t × Y, ou t × X si t est presque vertical
            if (Math.abs(ty) < 0.9F) {
               nx = -tz; ny = 0; nz = tx;
            } else {
               nx = 0; ny = tz; nz = -ty;
            }
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-9F) {
               nx = 0; ny = 1; nz = 0;
            } else {
               nx /= nl; ny /= nl; nz /= nl;
            }
         }
         int o = i * 9;
         F[o] = tx; F[o + 1] = ty; F[o + 2] = tz;
         F[o + 3] = nx; F[o + 4] = ny; F[o + 5] = nz;
         F[o + 6] = ty * nz - tz * ny; F[o + 7] = tz * nx - tx * nz; F[o + 8] = tx * ny - ty * nx;
      }
      int rl = (sides + 1) * 6;
      if (ringA.length < rl) {
         ringA = new float[rl];
         ringB = new float[rl];
      }
      float[] prev = ringA, cur = ringB, tr = trig(sides);
      FxMesh m = c.mesh;
      float prevU = 0;
      for (int i = 0; i < n; i++) {
         int o = i * 9;
         V p = pts[i];
         float ri = r[i];
         for (int j = 0; j <= sides; j++) {
            float ca = tr[j * 2], sa = tr[j * 2 + 1];
            float dx = F[o + 3] * ca + F[o + 6] * sa, dy = F[o + 4] * ca + F[o + 7] * sa, dz = F[o + 5] * ca + F[o + 8] * sa;
            int k = j * 6;
            cur[k] = p.x + dx * ri; cur[k + 1] = p.y + dy * ri; cur[k + 2] = p.z + dz * ri;
            cur[k + 3] = dx; cur[k + 4] = dy; cur[k + 5] = dz;
         }
         float u = us[i];
         if (i > 0) {
            for (int j = 0; j < sides; j++) {
               float va = (float) j / sides, vb = (float) (j + 1) / sides;
               int a = j * 6, b = a + 6;
               m.v(prev[a], prev[a + 1], prev[a + 2], prevU, va, prev[a + 3], prev[a + 4], prev[a + 5]);
               m.v(cur[a], cur[a + 1], cur[a + 2], u, va, cur[a + 3], cur[a + 4], cur[a + 5]);
               m.v(cur[b], cur[b + 1], cur[b + 2], u, vb, cur[b + 3], cur[b + 4], cur[b + 5]);
               m.v(prev[b], prev[b + 1], prev[b + 2], prevU, vb, prev[b + 3], prev[b + 4], prev[b + 5]);
            }
         }
         float[] sw = prev;
         prev = cur;
         cur = sw;
         prevU = u;
      }
   }

   /**
    * Volume sculpté par sections ovales le long de l'axe f (têtes, museaux, capuchons) : pour chaque section, avance le long
    * de f, demi-largeur (axe s), demi-hauteur (axe u) et décalage vertical. UV : u le long (0..1), v autour (0 = dessus).
    */
   public static void loft(FxContext c, V origin, V f, V u, float[] at, float[] w, float[] h, float[] dy, int sides) {
      V ff = f.norm(), uu = u.perp(ff), ss = ff.cross(uu);
      int n = at.length;
      V[][] ring = new V[n][sides + 1], nrm = new V[n][sides + 1];
      for (int i = 0; i < n; i++) {
         V ctr = origin.add(ff, at[i]).add(uu, dy[i]);
         for (int j = 0; j <= sides; j++) {
            float a = TAU * j / sides;
            float ca = cos(a), sa = sin(a);
            ring[i][j] = ctr.add(uu, ca * h[i]).add(ss, sa * w[i]);
            // normale d'ellipse (gradient), inclinée selon l'évolution du rayon
            V n0 = uu.mul(ca / Math.max(h[i], 1e-6F)).add(ss, sa / Math.max(w[i], 1e-6F)).norm();
            float dr = i == 0 || i == n - 1 ? 0 : ((w[i + 1] + h[i + 1]) - (w[i - 1] + h[i - 1])) / Math.max(at[i + 1] - at[i - 1], 1e-6F) * 0.5F;
            nrm[i][j] = n0.add(ff, -dr).norm();
         }
      }
      for (int i = 0; i + 1 < n; i++) {
         float ua = (float) i / (n - 1), ub = (float) (i + 1) / (n - 1);
         for (int j = 0; j < sides; j++) {
            float va = (float) j / sides, vb = (float) (j + 1) / sides;
            c.mesh.v(ring[i][j], ua, va, nrm[i][j]);
            c.mesh.v(ring[i + 1][j], ub, va, nrm[i + 1][j]);
            c.mesh.v(ring[i + 1][j + 1], ub, vb, nrm[i + 1][j + 1]);
            c.mesh.v(ring[i][j + 1], ua, vb, nrm[i][j + 1]);
         }
      }
   }

   /**
    * Volume sculpté à sections libres (crânes, museaux, mâchoires) : chaque section est une super-ellipse dont le dessus et le
    * dessous ont leur propre hauteur (dessous plat d'une mâchoire, crâne bombé) ; ex = exposant (2 = ovale, 3-4 = plus carré).
    * La section i est centrée en origin + f·at + u·dy + s·dx. UV : u le long (0..1), v autour (0 = dessus, 0.5 = dessous).
    */
   public static void sculpt(FxContext c, V origin, V f, V u, float[] at, float[] w, float[] top, float[] bot, float[] dy, float[] ex, int sides) {
      sculpt(c, origin, f, u, at, w, top, bot, dy, ex, sides, 0, TAU);
   }

   /**
    * Même volume, limité à l'arc d'angles [a0, a1] (0 = dessus, π = dessous) : coque ouverte (manchon qui laisse libre le
    * dessus d'un garde-main, par exemple). Les UV gardent la même convention (v = angle / 2π).
    */
   public static void sculpt(FxContext c, V origin, V f, V u, float[] at, float[] w, float[] top, float[] bot, float[] dy, float[] ex, int sides,
                             float a0, float a1) {
      boolean closed = a1 - a0 >= TAU - 1e-4F;
      V ff = f.norm(), uu = u.perp(ff), ss = ff.cross(uu);
      int n = at.length;
      V[][] ring = new V[n][sides + 1];
      for (int i = 0; i < n; i++) {
         V ctr = origin.add(ff, at[i]).add(uu, dy[i]);
         float e = 2F / Math.max(ex[i], 1F);
         for (int j = 0; j <= sides; j++) {
            float a = a0 + (a1 - a0) * j / sides;
            float ca = cos(a), sa = sin(a);
            float py = Math.signum(ca) * (float) Math.pow(Math.abs(ca), e) * (ca >= 0 ? top[i] : bot[i]);
            float px = Math.signum(sa) * (float) Math.pow(Math.abs(sa), e) * w[i];
            ring[i][j] = ctr.add(uu, py).add(ss, px);
         }
      }
      // normales par différences (les sections sont irrégulières)
      V[][] nrm = new V[n][sides + 1];
      for (int i = 0; i < n; i++) {
         for (int j = 0; j <= sides; j++) {
            V along = ring[Math.min(i + 1, n - 1)][j].sub(ring[Math.max(i - 1, 0)][j]);
            V around = closed ? ring[i][(j + 1) % sides].sub(ring[i][(j - 1 + sides) % sides])
               : ring[i][Math.min(j + 1, sides)].sub(ring[i][Math.max(j - 1, 0)]);
            V nn = around.cross(along);
            V ctr = origin.add(ff, at[i]).add(uu, dy[i]);
            if (nn.dot(ring[i][j].sub(ctr)) < 0) nn = nn.neg();
            nrm[i][j] = nn.len() < 1e-9F ? ring[i][j].sub(ctr).norm() : nn.norm();
         }
      }
      for (int i = 0; i + 1 < n; i++) {
         float ua = (float) i / (n - 1), ub = (float) (i + 1) / (n - 1);
         for (int j = 0; j < sides; j++) {
            float va = (a0 + (a1 - a0) * j / sides) / TAU, vb = (a0 + (a1 - a0) * (j + 1) / sides) / TAU;
            c.mesh.v(ring[i][j], ua, va, nrm[i][j]);
            c.mesh.v(ring[i + 1][j], ub, va, nrm[i + 1][j]);
            c.mesh.v(ring[i + 1][j + 1], ub, vb, nrm[i + 1][j + 1]);
            c.mesh.v(ring[i][j + 1], ua, vb, nrm[i][j + 1]);
         }
      }
   }

   /**
    * Plaque en relief posée sur un volume sculpté (mêmes sections que sculpt) : on reprend la surface entre les sections du
    * tableau et l'arc [a0, a1], gonflée de lift, avec des flancs qui la raccordent au volume. UV normalisés : u 0..1 le long,
    * v 0..1 en travers (le shader borde la plaque d'un liseré) ; les flancs ont v = -1.
    */
   public static void patch(FxContext c, V origin, V f, V u, float[] at, float[] w, float[] top, float[] bot, float[] dy, float[] ex, float a0, float a1,
                            int nv, float lift) {
      V ff = f.norm(), uu = u.perp(ff), ss = ff.cross(uu);
      int n = at.length;
      V[][] outer = new V[n][nv + 1], inner = new V[n][nv + 1];
      for (int i = 0; i < n; i++) {
         V ctr = origin.add(ff, at[i]).add(uu, dy[i]);
         float e = 2F / Math.max(ex[i], 1F);
         for (int j = 0; j <= nv; j++) {
            float a = a0 + (a1 - a0) * j / nv;
            float ca = cos(a), sa = sin(a);
            float py = Math.signum(ca) * (float) Math.pow(Math.abs(ca), e) * (ca >= 0 ? top[i] : bot[i]);
            float px = Math.signum(sa) * (float) Math.pow(Math.abs(sa), e) * w[i];
            V on = ctr.add(uu, py).add(ss, px);
            V out = on.sub(ctr).norm();
            inner[i][j] = on;
            outer[i][j] = on.add(out, lift);
         }
      }
      for (int i = 0; i + 1 < n; i++) {
         float ua = (float) i / (n - 1), ub = (float) (i + 1) / (n - 1);
         for (int j = 0; j < nv; j++) {
            float va = (float) j / nv, vb = (float) (j + 1) / nv;
            V nn = outer[i + 1][j].sub(outer[i][j]).cross(outer[i][j + 1].sub(outer[i][j])).norm();
            V ctr = origin.add(ff, at[i]).add(uu, dy[i]);
            if (nn.dot(outer[i][j].sub(ctr)) < 0) nn = nn.neg();
            c.mesh.v(outer[i][j], ua, va, nn);
            c.mesh.v(outer[i + 1][j], ub, va, nn);
            c.mesh.v(outer[i + 1][j + 1], ub, vb, nn);
            c.mesh.v(outer[i][j + 1], ua, vb, nn);
         }
      }
      // flancs (bordure épaisse)
      for (int i = 0; i + 1 < n; i++) {
         for (int side = 0; side < 2; side++) {
            int j = side == 0 ? 0 : nv;
            V nn = outer[i + 1][j].sub(outer[i][j]).cross(inner[i][j].sub(outer[i][j])).norm();
            c.mesh.v(inner[i][j], 0, -1, nn);
            c.mesh.v(inner[i + 1][j], 1, -1, nn);
            c.mesh.v(outer[i + 1][j], 1, -1, nn);
            c.mesh.v(outer[i][j], 0, -1, nn);
         }
      }
      for (int side = 0; side < 2; side++) {
         int i = side == 0 ? 0 : n - 1;
         for (int j = 0; j < nv; j++) {
            V nn = ff.mul(side == 0 ? -1 : 1);
            c.mesh.v(inner[i][j], 0, -1, nn);
            c.mesh.v(inner[i][j + 1], 1, -1, nn);
            c.mesh.v(outer[i][j + 1], 1, -1, nn);
            c.mesh.v(outer[i][j], 0, -1, nn);
         }
      }
   }

   /**
    * Plaque courbe épaisse (capuchon de cobra, membrane, collerette) : le long de la polyligne spine, demi-largeur w[i] sur l'axe
    * side[i], bords recourbés de curl·w vers back[i], épaisseur th. UV : u le long, v en travers (0..1) ; face avant puis arrière
    * (la face arrière a v décalé de 1 : v 1..2, pour que le shader peigne dos et ventre différemment).
    */
   public static void plate(FxContext c, V[] spine, V[] side, V[] back, float[] w, float curl, float th, int cols) {
      int n = spine.length;
      V[][] p = new V[n][cols + 1];
      for (int i = 0; i < n; i++) {
         for (int j = 0; j <= cols; j++) {
            float q = (float) j / cols * 2 - 1;
            p[i][j] = spine[i].add(side[i], q * w[i]).add(back[i], curl * q * q * w[i]);
         }
      }
      for (int face = 0; face < 2; face++) {
         float off = face == 0 ? -th * 0.5F : th * 0.5F;
         for (int i = 0; i + 1 < n; i++) {
            float ua = (float) i / (n - 1), ub = (float) (i + 1) / (n - 1);
            for (int j = 0; j < cols; j++) {
               float va = (float) j / cols + face, vb = (float) (j + 1) / cols + face;
               V a = p[i][j].add(back[i], off), b = p[i + 1][j].add(back[i + 1], off), d = p[i + 1][j + 1].add(back[i + 1], off), e = p[i][j + 1].add(back[i], off);
               V nn = b.sub(a).cross(e.sub(a)).norm();
               if (face == 0 && nn.dot(back[i]) > 0 || face == 1 && nn.dot(back[i]) < 0) nn = nn.neg();
               c.mesh.v(a, ua, va, nn);
               c.mesh.v(b, ub, va, nn);
               c.mesh.v(d, ub, vb, nn);
               c.mesh.v(e, ua, vb, nn);
            }
         }
      }
   }

   /** Sphère (UV : u = longitude 0..1, v = latitude 0..1). */
   public static void sphere(FxContext c, V p, float r, int rings, int segs) {
      V ax = V.Y, bx = V.X, cx = V.Z;
      for (int i = 0; i < rings; i++) {
         float la = FxContext.PI * i / rings, lb = FxContext.PI * (i + 1) / rings;
         for (int j = 0; j < segs; j++) {
            float oa = TAU * j / segs, ob = TAU * (j + 1) / segs;
            V n00 = sph(ax, bx, cx, la, oa), n01 = sph(ax, bx, cx, la, ob), n11 = sph(ax, bx, cx, lb, ob), n10 = sph(ax, bx, cx, lb, oa);
            c.mesh.v(p.add(n00, r), (float) j / segs, (float) i / rings, n00);
            c.mesh.v(p.add(n01, r), (float) (j + 1) / segs, (float) i / rings, n01);
            c.mesh.v(p.add(n11, r), (float) (j + 1) / segs, (float) (i + 1) / rings, n11);
            c.mesh.v(p.add(n10, r), (float) j / segs, (float) (i + 1) / rings, n10);
         }
      }
   }

   /**
    * Balayage d'une section elliptique le long d'une polyligne (cornes, lames, nageoires, moustaches pleines, crocs courbes) :
    * au point i, demi-largeur wa[i] dans le plan de la lame, demi-épaisseur wb[i] selon plane[i] (normale du plan de la lame).
    * UV : u le long (0..1), v autour (0 et 0.5 = tranchants, 0.25 / 0.75 = faces).
    */
   public static void sweep(FxContext c, V[] pts, float[] wa, float[] wb, V[] plane, int sides) {
      int n = pts.length;
      if (n < 2) return;
      V[][] ring = new V[n][sides + 1];
      V[][] nrm = new V[n][sides + 1];
      for (int i = 0; i < n; i++) {
         V t = pts[Math.min(i + 1, n - 1)].sub(pts[Math.max(i - 1, 0)]).norm();
         V b = plane[i].perp(t);               // épaisseur
         V a = t.cross(b).norm();              // largeur (dans le plan de la lame)
         for (int j = 0; j <= sides; j++) {
            float ang = TAU * j / sides;
            float ca = cos(ang), sa = sin(ang);
            ring[i][j] = pts[i].add(a, ca * wa[i]).add(b, sa * wb[i]);
            nrm[i][j] = a.mul(ca / Math.max(wa[i], 1e-6F)).add(b, sa / Math.max(wb[i], 1e-6F)).norm();
         }
      }
      for (int i = 0; i + 1 < n; i++) {
         float ua = (float) i / (n - 1), ub = (float) (i + 1) / (n - 1);
         for (int j = 0; j < sides; j++) {
            float va = (float) j / sides, vb = (float) (j + 1) / sides;
            c.mesh.v(ring[i][j], ua, va, nrm[i][j]);
            c.mesh.v(ring[i + 1][j], ub, va, nrm[i + 1][j]);
            c.mesh.v(ring[i + 1][j + 1], ub, vb, nrm[i + 1][j + 1]);
            c.mesh.v(ring[i][j + 1], ua, vb, nrm[i][j + 1]);
         }
      }
   }

   /** Même balayage, lame dans un plan fixe (normale unique). */
   public static void sweep(FxContext c, V[] pts, float[] wa, float[] wb, V plane, int sides) {
      V[] pl = new V[pts.length];
      java.util.Arrays.fill(pl, plane);
      sweep(c, pts, wa, wb, pl, sides);
   }

   /** Courbe lisse (Catmull-Rom) passant par les points de contrôle, m points. */
   public static V[] curve(V[] ctl, int m) {
      V[] out = new V[m];
      int n = ctl.length;
      for (int k = 0; k < m; k++) {
         float g = (float) k / (m - 1) * (n - 1);
         int i = Math.min(n - 2, (int) g);
         float t = g - i;
         V p0 = ctl[Math.max(0, i - 1)], p1 = ctl[i], p2 = ctl[i + 1], p3 = ctl[Math.min(n - 1, i + 2)];
         float t2 = t * t, t3 = t2 * t;
         out[k] = p1.mul(2).add(p2.sub(p0), t).add(p0.mul(2).sub(p1.mul(5)).add(p2.mul(4)).sub(p3), t2)
            .add(p0.neg().add(p1.mul(3)).sub(p2.mul(3)).add(p3), t3).mul(0.5F);
      }
      return out;
   }

   /** Profil lisse : valeurs vals aux abscisses keys, rééchantillonnées aux abscisses at (Hermite cubique). */
   public static float[] profile(float[] keys, float[] vals, float[] at) {
      float[] out = new float[at.length];
      int n = keys.length;
      for (int k = 0; k < at.length; k++) {
         float x = at[k];
         int i = 0;
         while (i < n - 2 && x > keys[i + 1]) i++;
         float h = Math.max(keys[i + 1] - keys[i], 1e-6F);
         float t = FxContext.clamp((x - keys[i]) / h, 0, 1);
         float m0 = i > 0 ? (vals[i + 1] - vals[i - 1]) / Math.max(keys[i + 1] - keys[i - 1], 1e-6F) : (vals[i + 1] - vals[i]) / h;
         float m1 = i + 2 < n ? (vals[i + 2] - vals[i]) / Math.max(keys[i + 2] - keys[i], 1e-6F) : (vals[i + 1] - vals[i]) / h;
         float t2 = t * t, t3 = t2 * t;
         out[k] = (2 * t3 - 3 * t2 + 1) * vals[i] + (t3 - 2 * t2 + t) * h * m0 + (-2 * t3 + 3 * t2) * vals[i + 1] + (t3 - t2) * h * m1;
      }
      return out;
   }

   /** n abscisses régulières de a à b. */
   public static float[] range(float a, float b, int n) {
      float[] r = new float[n];
      for (int i = 0; i < n; i++) r[i] = a + (b - a) * i / (n - 1);
      return r;
   }

   /** Ellipsoïde de demi-axes ax (pôles), ay, az (vecteurs, repère libre) : yeux saillants, arcades, naseaux, jointures. */
   public static void blob(FxContext c, V p, V ax, V ay, V az, int rings, int segs) {
      V na = ax.norm(), nb = ay.norm(), nc = az.norm();
      float la = Math.max(ax.len(), 1e-6F), lb = Math.max(ay.len(), 1e-6F), lc = Math.max(az.len(), 1e-6F);
      for (int i = 0; i < rings; i++) {
         float t0 = FxContext.PI * i / rings, t1 = FxContext.PI * (i + 1) / rings;
         for (int j = 0; j < segs; j++) {
            float o0 = TAU * j / segs, o1 = TAU * (j + 1) / segs;
            float[][] k = {{t0, o0}, {t0, o1}, {t1, o1}, {t1, o0}};
            for (float[] q : k) {
               float ct = cos(q[0]), st = sin(q[0]), co = cos(q[1]), so = sin(q[1]);
               V pt = p.add(ax, ct).add(ay, st * co).add(az, st * so);
               V nn = na.mul(ct / la).add(nb, st * co / lb).add(nc, st * so / lc).norm();
               c.mesh.v(pt, q[1] / TAU, q[0] / FxContext.PI, nn);
            }
         }
      }
   }

   private static V sph(V up, V a, V b, float lat, float lon) {
      float s = sin(lat);
      return up.mul(cos(lat)).add(a, s * cos(lon)).add(b, s * sin(lon));
   }

   /** Rocher irrégulier (sphère bosselée par un bruit propre à la graine, qui tourne avec rot autour de spin). */
   public static void rock(FxContext c, V p, float r, int seed, V spin, float rot, int rings, int segs) {
      V ax = V.Y, bx = V.X, cx = V.Z;
      V[][] pts = new V[rings + 1][segs + 1];
      V[][] nrm = new V[rings + 1][segs + 1];
      for (int i = 0; i <= rings; i++) {
         float la = FxContext.PI * i / rings;
         for (int j = 0; j <= segs; j++) {
            V d = sph(ax, bx, cx, la, TAU * j / segs);
            float bump = 0;
            for (int k = 0; k < 5; k++) {
               V o = new V(FxContext.hash(seed, k, 1) - 0.5F, FxContext.hash(seed, k, 2) - 0.5F, FxContext.hash(seed, k, 3) - 0.5F).norm();
               bump += Math.max(0, d.dot(o) - 0.55F) * (FxContext.hash(seed, k, 4) - 0.35F) * 1.4F;
            }
            V dr = d.rot(spin, rot);
            pts[i][j] = p.add(dr, r * (1 + bump));
            nrm[i][j] = dr;
         }
      }
      for (int i = 0; i < rings; i++) {
         for (int j = 0; j < segs; j++) {
            c.mesh.v(pts[i][j], (float) j / segs, (float) i / rings, nrm[i][j]);
            c.mesh.v(pts[i][j + 1], (float) (j + 1) / segs, (float) i / rings, nrm[i][j + 1]);
            c.mesh.v(pts[i + 1][j + 1], (float) (j + 1) / segs, (float) (i + 1) / rings, nrm[i + 1][j + 1]);
            c.mesh.v(pts[i + 1][j], (float) j / segs, (float) (i + 1) / rings, nrm[i + 1][j]);
         }
      }
   }

   /**
    * Paire d'ailes articulées sur un corps (papillon, chauve-souris, oiseau) : fwd = avant du corps, up = dos,
    * span / chord = envergure d'une aile / profondeur, flap = angle de battement. UV : u du corps (0) au bout (1), v de l'arrière (0) à l'avant (1).
    */
   public static void wings(FxContext c, V body, V fwd, V up, float span, float chord, float flap) {
      V f = fwd.norm(), u = up.perp(f), side = f.cross(u);
      for (int s = -1; s <= 1; s += 2) {
         V out = side.mul(s).rot(f, s * -flap);
         V n = out.cross(f).mul(s);
         V a = body.add(f, -chord * 0.5F), b = body.add(out, span).add(f, -chord * 0.5F), cc = body.add(out, span).add(f, chord * 0.5F), d = body.add(f, chord * 0.5F);
         c.mesh.v(a, 0, 0, n);
         c.mesh.v(b, 1, 0, n);
         c.mesh.v(cc, 1, 1, n);
         c.mesh.v(d, 0, 1, n);
      }
   }

   /** Tore autour de axis (anneau, maillon de chaîne si ea != eb) ; UV : u = grand cercle, v = petit cercle. */
   public static void torus(FxContext c, V p, V axis, V ref, float ra, float rb, float r, int segs, int sides) {
      V ax = axis.norm(), e1 = ref.perp(ax), e2 = ax.cross(e1);
      // anneaux et maillons : des milliers de sommets par image, calculés à plat (tables de sinus, aucun objet créé)
      float[] ta = trig(segs), tb = trig(sides);
      FxMesh m = c.mesh;
      for (int i = 0; i < segs; i++) {
         for (int j = 0; j < sides; j++) {
            torusVert(m, p, ax, e1, e2, ra, rb, r, ta, i, tb, j, segs, sides);
            torusVert(m, p, ax, e1, e2, ra, rb, r, ta, i + 1, tb, j, segs, sides);
            torusVert(m, p, ax, e1, e2, ra, rb, r, ta, i + 1, tb, j + 1, segs, sides);
            torusVert(m, p, ax, e1, e2, ra, rb, r, ta, i, tb, j + 1, segs, sides);
         }
      }
   }

   private static void torusVert(FxMesh m, V p, V ax, V e1, V e2, float ra, float rb, float r, float[] ta, int i, float[] tb, int j, int segs,
                                 int sides) {
      float ca = ta[i * 2], sa = ta[i * 2 + 1], cb = tb[j * 2], sb = tb[j * 2 + 1];
      float nx = (e1.x * ca + e2.x * sa) * cb + ax.x * sb;
      float ny = (e1.y * ca + e2.y * sa) * cb + ax.y * sb;
      float nz = (e1.z * ca + e2.z * sa) * cb + ax.z * sb;
      m.v(p.x + e1.x * ra * ca + e2.x * rb * sa + nx * r, p.y + e1.y * ra * ca + e2.y * rb * sa + ny * r,
         p.z + e1.z * ra * ca + e2.z * rb * sa + nz * r, (float) i / segs, (float) j / sides, nx, ny, nz);
   }

   private static final float[][] TRIG = new float[129][];

   /** cos, sin de TAU·k/n pour k = 0..n (le dernier referme l'anneau). */
   private static float[] trig(int n) {
      float[] t = n < TRIG.length ? TRIG[n] : null;
      if (t == null) {
         t = new float[(n + 1) * 2];
         for (int k = 0; k <= n; k++) {
            t[k * 2] = cos(TAU * k / n);
            t[k * 2 + 1] = sin(TAU * k / n);
         }
         if (n < TRIG.length) TRIG[n] = t;
      }
      return t;
   }

   /** Disque plat (un seul quad, UV 0..1 : le shader dessine cercles, runes, cadrans). */
   public static void disc(FxContext c, V p, V normal, V ref, float r) {
      V n = normal.norm(), a = ref.perp(n), b = n.cross(a);
      c.mesh.quad(p.add(a, -r).add(b, -r), p.add(a, r).add(b, -r), p.add(a, r).add(b, r), p.add(a, -r).add(b, r), 0, 0, 1, 1, n);
   }

   /** Cristal : prisme à facettes (sides pans) terminé en pointe ; UV : u autour, v de la base (0) à la pointe (1). */
   public static void crystal(FxContext c, V base, V dir, V ref, float r, float h, float tip, int sides, float rot) {
      V d = dir.norm(), a = ref.perp(d), b = d.cross(a);
      V top = base.add(d, h), apex = base.add(d, h + tip);
      float vTop = h / (h + tip);
      for (int j = 0; j < sides; j++) {
         float t0 = rot + TAU * j / sides, t1 = rot + TAU * (j + 1) / sides;
         V o0 = a.mul(cos(t0)).add(b, sin(t0)), o1 = a.mul(cos(t1)).add(b, sin(t1));
         V p0 = base.add(o0, r * 0.82F), p1 = base.add(o1, r * 0.82F), q0 = top.add(o0, r), q1 = top.add(o1, r);
         V fn = o0.add(o1).norm();
         float ua = (float) j / sides, ub = (float) (j + 1) / sides;
         c.mesh.v(p0, ua, 0, fn);
         c.mesh.v(p1, ub, 0, fn);
         c.mesh.v(q1, ub, vTop, fn);
         c.mesh.v(q0, ua, vTop, fn);
         V tn = q1.sub(q0).cross(apex.sub(q0)).norm();
         if (tn.dot(fn) < 0) tn = tn.neg();
         c.mesh.v(q0, ua, vTop, tn);
         c.mesh.v(q1, ub, vTop, tn);
         c.mesh.v(apex, ub, 1, tn);
         c.mesh.v(apex, ua, 1, tn);
      }
   }

   /** Pavé : centre, demi-axes ; UV 0..1 par face. */
   public static void box(FxContext c, V p, V ax, V ay, V az) {
      V[] axes = {ax, ay, az};
      for (int k = 0; k < 3; k++) {
         V n = axes[k], u = axes[(k + 1) % 3], w = axes[(k + 2) % 3];
         for (int s = -1; s <= 1; s += 2) {
            V fc = p.add(n, s);
            V nn = n.norm().mul(s);
            c.mesh.quad(fc.sub(u).sub(w), fc.add(u).sub(w), fc.add(u).add(w), fc.sub(u).add(w), 0, 0, 1, 1, nn);
         }
      }
   }

   /** Pyramide (piquot) : base polygonale, pointe ; UV : u autour, v de la base à la pointe. */
   public static void spike(FxContext c, V base, V tip, V ref, float r, int sides, float rot) {
      V d = tip.sub(base).norm(), a = ref.perp(d), b = d.cross(a);
      for (int j = 0; j < sides; j++) {
         float t0 = rot + TAU * j / sides, t1 = rot + TAU * (j + 1) / sides;
         V p0 = base.add(a, r * cos(t0)).add(b, r * sin(t0)), p1 = base.add(a, r * cos(t1)).add(b, r * sin(t1));
         V fn = p1.sub(p0).cross(tip.sub(p0)).norm();
         if (fn.dot(p0.sub(base)) < 0) fn = fn.neg();
         float ua = (float) j / sides, ub = (float) (j + 1) / sides;
         c.mesh.v(p0, ua, 0, fn);
         c.mesh.v(p1, ub, 0, fn);
         c.mesh.v(tip, ub, 1, fn);
         c.mesh.v(tip, ua, 1, fn);
      }
   }

   /** Engrenage plat en relief : dents, épaisseur ; UV des faces = position dans le plan (0..1), flancs : v = 0.5. */
   public static void gear(FxContext c, V p, V axis, V ref, float r, float depth, float th, int teeth, float rot) {
      V n = axis.norm(), a = ref.perp(n), b = n.cross(a);
      int m = teeth * 4;
      V[] outline = new V[m];
      float[] ou = new float[m], ov = new float[m];
      for (int k = 0; k < m; k++) {
         float ang = rot + TAU * k / m;
         float rr = (k % 4 == 1 || k % 4 == 2) ? r + depth : r;
         float ca = cos(ang), sa = sin(ang);
         outline[k] = a.mul(ca * rr).add(b, sa * rr);
         ou[k] = 0.5F + 0.5F * ca * rr / (r + depth);
         ov[k] = 0.5F + 0.5F * sa * rr / (r + depth);
      }
      V front = p.add(n, th * 0.5F), back = p.add(n, -th * 0.5F);
      for (int k = 0; k < m; k++) {
         int k2 = (k + 1) % m;
         c.mesh.v(front, 0.5F, 0.5F, n);
         c.mesh.v(front.add(outline[k]), ou[k], ov[k], n);
         c.mesh.v(front.add(outline[k2]), ou[k2], ov[k2], n);
         c.mesh.v(front, 0.5F, 0.5F, n);
         V nb = n.neg();
         c.mesh.v(back, 0.5F, 0.5F, nb);
         c.mesh.v(back.add(outline[k2]), ou[k2], ov[k2], nb);
         c.mesh.v(back.add(outline[k]), ou[k], ov[k], nb);
         c.mesh.v(back, 0.5F, 0.5F, nb);
         V side = outline[k2].sub(outline[k]).cross(n).norm();
         if (side.dot(outline[k]) < 0) side = side.neg();
         c.mesh.v(back.add(outline[k]), (float) k / m, 0.5F, side);
         c.mesh.v(back.add(outline[k2]), (float) k2 / m, 0.5F, side);
         c.mesh.v(front.add(outline[k2]), (float) k2 / m, 0.5F, side);
         c.mesh.v(front.add(outline[k]), (float) k / m, 0.5F, side);
      }
   }
}
