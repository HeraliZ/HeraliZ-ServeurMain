package fr.heralisz.camo.fx;

/**
 * Repère de l'arme à cette image : origine (centre de la boîte au repos), axe x vers le canon, y vers le haut, z sur le côté,
 * et échelle s = longueur de l'arme dans le repère de rendu. Retrouvé en calant les positions au repos des faces
 * (carte de positions) sur leurs positions dessinées (moindres carrés, faces aberrantes écartées) : il suit l'arme
 * dans toutes les animations, sur n'importe quel modèle, en 1re personne comme dans l'inventaire.
 */
public final class FxFrame {
   public V o, x, y, z;
   public float s;

   /** Point du repère de l'arme (unités : longueur de l'arme). */
   public V at(float gx, float gy, float gz) {
      return this.o.add(this.x, gx * this.s).add(this.y, gy * this.s).add(this.z, gz * this.s);
   }

   /** Vecteur du repère de l'arme, mis à l'échelle. */
   public V vec(float dx, float dy, float dz) {
      return this.x.mul(dx * this.s).add(this.y, dy * this.s).add(this.z, dz * this.s);
   }

   /** Direction unitaire du repère de l'arme. */
   public V dir(float dx, float dy, float dz) {
      return this.x.mul(dx).add(this.y, dy).add(this.z, dz).norm();
   }

   /** Faces au plus pour le calage : au-delà on en garde un échantillon fixe (le repère ne bouge pas, le coût si). */
   private static final int FIT_MAX = 160;
   private static final float[] RC = new float[FIT_MAX * 3], VC = new float[FIT_MAX * 3], W = new float[FIT_MAX], RES = new float[FIT_MAX],
      SORTED = new float[FIT_MAX];

   public static FxFrame fit(Surfaces s) {
      FxFrame f = new FxFrame();
      int m = 0;
      for (int i = 0; i < s.n; i++) if (s.hasRest[i] && s.area[i] > 0) m++;
      boolean ok = m >= 8;
      if (ok) {
         // échantillon stable d'une image à l'autre (même face -> même tirage), réparti sur toute l'arme sans suivre
         // l'ordre des faces des cubes (un pas régulier ne garderait que les dessous, par exemple)
         int keep = Math.min(m, FIT_MAX);
         long thr = (long) keep * 0x1_0000_0000L / m;
         int k = 0;
         for (int i = 0; i < s.n && k < FIT_MAX; i++) {
            if (!s.hasRest[i] || s.area[i] <= 0) continue;
            if (keep < m && (mix(i) & 0xFFFFFFFFL) >= thr) continue;
            int o = i * 12;
            float[] rp = s.rest, vp = s.pos;
            RC[k * 3] = (rp[o] + rp[o + 3] + rp[o + 6] + rp[o + 9]) * 0.25F;
            RC[k * 3 + 1] = (rp[o + 1] + rp[o + 4] + rp[o + 7] + rp[o + 10]) * 0.25F;
            RC[k * 3 + 2] = (rp[o + 2] + rp[o + 5] + rp[o + 8] + rp[o + 11]) * 0.25F;
            VC[k * 3] = (vp[o] + vp[o + 3] + vp[o + 6] + vp[o + 9]) * 0.25F;
            VC[k * 3 + 1] = (vp[o + 1] + vp[o + 4] + vp[o + 7] + vp[o + 10]) * 0.25F;
            VC[k * 3 + 2] = (vp[o + 2] + vp[o + 5] + vp[o + 8] + vp[o + 11]) * 0.25F;
            W[k++] = (float) Math.sqrt(s.area[i]);
         }
         float[] w = W;
         float[][] a = k >= 8 ? solve(k, w) : null;
         if (a != null) {
            // 2e passe : on écarte le quart des faces les plus éloignées du calage (pièces animées, faces en miroir)
            for (int i = 0; i < k; i++) {
               float rx = RC[i * 3], ry = RC[i * 3 + 1], rz = RC[i * 3 + 2];
               float ex = a[0][0] * rx + a[0][1] * ry + a[0][2] * rz + a[0][3] - VC[i * 3];
               float ey = a[1][0] * rx + a[1][1] * ry + a[1][2] * rz + a[1][3] - VC[i * 3 + 1];
               float ez = a[2][0] * rx + a[2][1] * ry + a[2][2] * rz + a[2][3] - VC[i * 3 + 2];
               RES[i] = SORTED[i] = ex * ex + ey * ey + ez * ez;
            }
            java.util.Arrays.sort(SORTED, 0, k);
            float cut = SORTED[Math.min(k - 1, (int) (k * 0.75F))];
            for (int i = 0; i < k; i++) if (RES[i] > cut) w[i] = 0;
            float[][] a2 = solve(k, w);
            if (a2 != null) a = a2;
            V ax = new V(a[0][0], a[1][0], a[2][0]), ay = new V(a[0][1], a[1][1], a[2][1]), az = new V(a[0][2], a[1][2], a[2][2]);
            float sc = ax.len();
            if (sc > 1e-6F && ay.len() > 1e-6F) {
               f.x = ax.norm();
               f.y = ay.perp(f.x);
               V zc = f.x.cross(f.y);
               f.z = zc.dot(az) < 0 ? zc.neg() : zc;
               f.s = sc;
               f.o = new V(a[0][3], a[1][3], a[2][3]);
               return f;
            }
         }
      }
      // repli sans carte : axe principal de la boîte (moins précis mais toujours quelque chose)
      float lx = Float.MAX_VALUE, ly = Float.MAX_VALUE, lz = Float.MAX_VALUE, hx = -Float.MAX_VALUE, hy = -Float.MAX_VALUE, hz = -Float.MAX_VALUE;
      for (int i = 0; i < s.n * 4; i++) {
         float px = s.pos[i * 3], py = s.pos[i * 3 + 1], pz = s.pos[i * 3 + 2];
         lx = Math.min(lx, px); ly = Math.min(ly, py); lz = Math.min(lz, pz);
         hx = Math.max(hx, px); hy = Math.max(hy, py); hz = Math.max(hz, pz);
      }
      f.o = new V((lx + hx) / 2, (ly + hy) / 2, (lz + hz) / 2);
      f.s = Math.max(s.size, 1e-4F);
      f.x = hx - lx >= hz - lz ? V.X : V.Z.neg();
      f.y = V.Y;
      f.z = f.x.cross(f.y);
      return f;
   }

   private static int mix(int i) {
      int h = i * 0x9E3779B1;
      h ^= h >>> 16;
      h *= 0x85EBCA6B;
      return h ^ (h >>> 13);
   }

   /** Moindres carrés pondérés : vue = A · [repos ; 1] (3 lignes de 4 coefficients), null si mal posé. */
   private static float[][] solve(int count, float[] w) {
      double[][] nm = new double[4][4];
      double[][] rhs = new double[3][4];
      double[] xv = new double[4], yv = new double[3];
      for (int i = 0; i < count; i++) {
         if (w[i] <= 0) continue;
         xv[0] = RC[i * 3]; xv[1] = RC[i * 3 + 1]; xv[2] = RC[i * 3 + 2]; xv[3] = 1;
         yv[0] = VC[i * 3]; yv[1] = VC[i * 3 + 1]; yv[2] = VC[i * 3 + 2];
         for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) nm[a][b] += w[i] * xv[a] * xv[b];
            for (int k = 0; k < 3; k++) rhs[k][a] += w[i] * xv[a] * yv[k];
         }
      }
      // petite régularisation : l'arme est presque plate en z (évite une matrice singulière)
      double tr = nm[0][0] + nm[1][1] + nm[2][2];
      for (int a = 0; a < 3; a++) nm[a][a] += tr * 1e-5;
      double[][] inv = invert4(nm);
      if (inv == null) return null;
      float[][] out = new float[3][4];
      for (int k = 0; k < 3; k++) {
         for (int a = 0; a < 4; a++) {
            double v = 0;
            for (int b = 0; b < 4; b++) v += inv[a][b] * rhs[k][b];
            out[k][a] = (float) v;
         }
      }
      return out;
   }

   private static double[][] invert4(double[][] m) {
      int n = 4;
      double[][] a = new double[n][2 * n];
      for (int i = 0; i < n; i++) {
         System.arraycopy(m[i], 0, a[i], 0, n);
         a[i][n + i] = 1;
      }
      for (int c = 0; c < n; c++) {
         int p = c;
         for (int r = c + 1; r < n; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
         if (Math.abs(a[p][c]) < 1e-14) return null;
         double[] tmp = a[c]; a[c] = a[p]; a[p] = tmp;
         double d = a[c][c];
         for (int k = 0; k < 2 * n; k++) a[c][k] /= d;
         for (int r = 0; r < n; r++) {
            if (r == c) continue;
            double fct = a[r][c];
            if (fct == 0) continue;
            for (int k = 0; k < 2 * n; k++) a[r][k] -= fct * a[c][k];
         }
      }
      double[][] inv = new double[n][n];
      for (int i = 0; i < n; i++) System.arraycopy(a[i], n, inv[i], 0, n);
      return inv;
   }
}
