package fr.heralisz.camo.fx;

import java.util.function.IntPredicate;

/**
 * Faces camouflées de l'arme pour cette image, dans le repère où elle est dessinée (déjà animée : recharge, visée…).
 * Chaque face garde aussi, quand on la connaît, sa position au repos (repère de la carte de positions) et une graine
 * stable tirée de ses coordonnées de texture : un effet posé sur une face y reste d'une image à l'autre.
 */
public final class Surfaces {
   public int n;
   public float[] pos, nrm, area, part, rest;
   public boolean[] hasRest;
   public int[] seed;
   /** Plus grande dimension de la boîte englobante (repère de rendu). */
   public float size;
   private float[] cum;
   public float total;

   /**
    * @param view  12 flottants par quad (4 coins, repère de rendu)
    * @param uv    8 flottants par quad (coordonnées de texture des 4 coins)
    * @param part  partie de chaque quad (≤ 0.15 : ignoré)
    * @param rest  12 flottants par quad (coins au repos) ou NaN si inconnu
    * @param recN  3 flottants par quad : normale transmise par le moteur (sert à orienter la normale calculée)
    */
   public static Surfaces build(float[] view, float[] uv, float[] part, float[] rest, float[] recN, int quads) {
      return build(view, uv, part, rest, recN, quads, null);
   }

   /** Même construction dans les tableaux de reuse (jeu : une seule instance, rien à allouer à chaque image). */
   public static Surfaces build(float[] view, float[] uv, float[] part, float[] rest, float[] recN, int quads, Surfaces reuse) {
      Surfaces s = reuse != null ? reuse : new Surfaces();
      s.n = 0;
      if (s.area == null || s.area.length < quads) {
         s.pos = new float[quads * 12];
         s.nrm = new float[quads * 3];
         s.area = new float[quads];
         s.part = new float[quads];
         s.rest = new float[quads * 12];
         s.hasRest = new boolean[quads];
         s.seed = new int[quads];
         s.cum = new float[quads];
      }
      float lx = Float.MAX_VALUE, ly = Float.MAX_VALUE, lz = Float.MAX_VALUE, hx = -Float.MAX_VALUE, hy = -Float.MAX_VALUE, hz = -Float.MAX_VALUE;
      for (int q = 0; q < quads; q++) {
         if (part[q] <= 0.15F) continue;
         int o = q * 12;
         float ax = view[o + 6] - view[o], ay = view[o + 7] - view[o + 1], az = view[o + 8] - view[o + 2];
         float bx = view[o + 9] - view[o + 3], by = view[o + 10] - view[o + 4], bz = view[o + 11] - view[o + 5];
         float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
         float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
         if (cl < 1e-12F) continue;
         cx /= cl; cy /= cl; cz /= cl;
         if (cx * recN[q * 3] + cy * recN[q * 3 + 1] + cz * recN[q * 3 + 2] < 0) {
            cx = -cx; cy = -cy; cz = -cz;
         }
         int k = s.n++;
         System.arraycopy(view, o, s.pos, k * 12, 12);
         System.arraycopy(rest, o, s.rest, k * 12, 12);
         s.hasRest[k] = !Float.isNaN(rest[o]);
         s.nrm[k * 3] = cx; s.nrm[k * 3 + 1] = cy; s.nrm[k * 3 + 2] = cz;
         s.area[k] = cl * 0.5F;
         s.part[k] = part[q];
         float u = (uv[q * 8] + uv[q * 8 + 2] + uv[q * 8 + 4] + uv[q * 8 + 6]) * 0.25F;
         float v = (uv[q * 8 + 1] + uv[q * 8 + 3] + uv[q * 8 + 5] + uv[q * 8 + 7]) * 0.25F;
         // côté de la face (distingue les faces en miroir qui partagent la texture) : normale au repos si connue, sinon
         // celle de l'image (qui tourne avec l'arme : les effets posés dessus sauteraient quand le porteur se retourne)
         float gx = cx, gy = cy, gz = cz;
         if (s.hasRest[k]) {
            float rax = rest[o + 6] - rest[o], ray = rest[o + 7] - rest[o + 1], raz = rest[o + 8] - rest[o + 2];
            float rbx = rest[o + 9] - rest[o + 3], rby = rest[o + 10] - rest[o + 4], rbz = rest[o + 11] - rest[o + 5];
            float nx = ray * rbz - raz * rby, ny = raz * rbx - rax * rbz, nz = rax * rby - ray * rbx;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1e-12F) {
               gx = nx / nl; gy = ny / nl; gz = nz / nl;
            }
         }
         int sg = (int) (gx * 1.5F + 1.5F) + 3 * (int) (gy * 1.5F + 1.5F) + 9 * (int) (gz * 1.5F + 1.5F);
         s.seed[k] = ((int) (u * 8192) * 73856093) ^ ((int) (v * 8192) * 19349663) ^ (sg * 83492791);
         for (int c = 0; c < 4; c++) {
            float x = view[o + c * 3], y = view[o + c * 3 + 1], z = view[o + c * 3 + 2];
            lx = Math.min(lx, x); ly = Math.min(ly, y); lz = Math.min(lz, z);
            hx = Math.max(hx, x); hy = Math.max(hy, y); hz = Math.max(hz, z);
         }
      }
      s.size = s.n == 0 ? 1 : Math.max(hx - lx, Math.max(hy - ly, hz - lz));
      // les minuscules faces ne comptent pas dans les tirages
      float min = s.size * s.size * 4e-6F;
      if (s.cum == null || s.cum.length < s.n) s.cum = new float[s.n];
      float acc = 0;
      for (int i = 0; i < s.n; i++) {
         if (s.area[i] < min) s.area[i] = 0;
         acc += s.area[i];
         s.cum[i] = acc;
      }
      s.total = acc;
      return s;
   }

   /** Point de la face i (a, b dans [0,1] : interpolation des coins). */
   public V point(int i, float a, float b) {
      return bilinear(this.pos, i, a, b);
   }

   /** Même point au repos (repère de la carte de positions), si connu. */
   public V restPoint(int i, float a, float b) {
      return bilinear(this.rest, i, a, b);
   }

   private static V bilinear(float[] q, int i, float a, float b) {
      int o = i * 12;
      float tx = q[o] + (q[o + 3] - q[o]) * a, ty = q[o + 1] + (q[o + 4] - q[o + 1]) * a, tz = q[o + 2] + (q[o + 5] - q[o + 2]) * a;
      float bx = q[o + 9] + (q[o + 6] - q[o + 9]) * a, by = q[o + 10] + (q[o + 7] - q[o + 10]) * a, bz = q[o + 11] + (q[o + 8] - q[o + 11]) * a;
      return new V(tx + (bx - tx) * b, ty + (by - ty) * b, tz + (bz - tz) * b);
   }

   public V center(int i) { return this.point(i, 0.5F, 0.5F); }
   public V restCenter(int i) { return this.restPoint(i, 0.5F, 0.5F); }
   public V normal(int i) { return new V(this.nrm[i * 3], this.nrm[i * 3 + 1], this.nrm[i * 3 + 2]); }

   public V tangent(int i) {
      int o = i * 12;
      V t = new V(this.pos[o + 3] - this.pos[o], this.pos[o + 4] - this.pos[o + 1], this.pos[o + 5] - this.pos[o + 2]);
      return t.len() < 1e-9F ? this.normal(i).anyPerp() : t.perp(this.normal(i));
   }

   /** Face tirée au hasard, pondérée par l'aire (r dans [0,1)). */
   public int pick(float r) {
      return pickIn(this.cum, this.n, this.total, null, r);
   }

   /** Sous-ensemble de faces (filtre), avec son propre tirage pondéré. */
   public Subset select(IntPredicate keep) {
      int[] idx = new int[this.n];
      float[] cum = new float[this.n];
      int m = 0;
      float acc = 0;
      for (int i = 0; i < this.n; i++) {
         if (this.area[i] > 0 && keep.test(i)) {
            acc += this.area[i];
            idx[m] = i;
            cum[m++] = acc;
         }
      }
      return new Subset(idx, cum, m, acc, this);
   }

   private static int pickIn(float[] cum, int n, float total, int[] idx, float r) {
      if (n == 0) return -1;
      float target = r * total;
      int lo = 0, hi = n - 1;
      while (lo < hi) {
         int mid = (lo + hi) >>> 1;
         if (cum[mid] < target) lo = mid + 1; else hi = mid;
      }
      return idx == null ? lo : idx[lo];
   }

   public static final class Subset {
      /** Indices des faces retenues (les size premières cases). */
      public final int[] idx;
      private final float[] cum;
      public final int size;
      public final float total;
      private final Surfaces s;

      Subset(int[] idx, float[] cum, int size, float total, Surfaces s) {
         this.idx = idx;
         this.cum = cum;
         this.size = size;
         this.total = total;
         this.s = s;
      }

      public boolean empty() { return this.size == 0; }

      /** Face tirée au hasard dans le sous-ensemble (pondérée par l'aire), -1 si vide. */
      public int pick(float r) {
         return pickIn(this.cum, this.size, this.total, this.idx, r);
      }
   }
}
