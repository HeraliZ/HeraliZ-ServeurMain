package fr.heralisz.camo.fx;

/**
 * Silhouette de l'arme au repos, dans le repère de la carte de positions (x = longueur vers le canon, y = haut, z = côté ;
 * unité = longueur de l'arme) : pour chaque tranche le long de l'arme, hauteur et largeur occupées par les pièces camouflées.
 * Les effets s'y adaptent (un dragon s'enroule plus large autour d'une lunette, plus serré autour du canon…).
 */
public final class GunShape {
   public static final int BINS = 64;
   public final float xMin, xMax, yMin, yMax, zMin, zMax;
   private final float[] yLo = new float[BINS], yHi = new float[BINS], zLo = new float[BINS], zHi = new float[BINS];
   /** Enveloppe lissée (pour s'enrouler sans à-coups). */
   private final float[] syLo = new float[BINS], syHi = new float[BINS], szLo = new float[BINS], szHi = new float[BINS];

   private GunShape(float xMin, float xMax, float yMin, float yMax, float zMin, float zMax) {
      this.xMin = xMin;
      this.xMax = xMax;
      this.yMin = yMin;
      this.yMax = yMax;
      this.zMin = zMin;
      this.zMax = zMax;
   }

   /**
    * @param rest 12 flottants par quad (4 coins x, y, z au repos)
    * @param part partie de chaque quad (≤ 0.15 : jamais camouflé, ignoré)
    */
   public static GunShape build(float[] rest, float[] part, int quads) {
      float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
      for (int q = 0; q < quads; q++) {
         if (part[q] <= 0.15F || Float.isNaN(rest[q * 12])) continue;
         for (int k = 0; k < 4; k++) {
            float x = rest[q * 12 + k * 3], y = rest[q * 12 + k * 3 + 1], z = rest[q * 12 + k * 3 + 2];
            x0 = Math.min(x0, x); x1 = Math.max(x1, x);
            y0 = Math.min(y0, y); y1 = Math.max(y1, y);
            z0 = Math.min(z0, z); z1 = Math.max(z1, z);
         }
      }
      if (x0 > x1) {
         x0 = -0.5F; x1 = 0.5F; y0 = -0.1F; y1 = 0.1F; z0 = -0.03F; z1 = 0.03F;
      }
      GunShape s = new GunShape(x0, x1, y0, y1, z0, z1);
      float[] ylo = s.yLo, yhi = s.yHi, zlo = s.zLo, zhi = s.zHi;
      java.util.Arrays.fill(ylo, Float.MAX_VALUE);
      java.util.Arrays.fill(yhi, -Float.MAX_VALUE);
      java.util.Arrays.fill(zlo, Float.MAX_VALUE);
      java.util.Arrays.fill(zhi, -Float.MAX_VALUE);
      float w = Math.max(x1 - x0, 1e-4F);
      for (int q = 0; q < quads; q++) {
         if (part[q] <= 0.15F || Float.isNaN(rest[q * 12])) continue;
         float qx0 = Float.MAX_VALUE, qx1 = -Float.MAX_VALUE, qy0 = Float.MAX_VALUE, qy1 = -Float.MAX_VALUE, qz0 = Float.MAX_VALUE, qz1 = -Float.MAX_VALUE;
         for (int k = 0; k < 4; k++) {
            float x = rest[q * 12 + k * 3], y = rest[q * 12 + k * 3 + 1], z = rest[q * 12 + k * 3 + 2];
            qx0 = Math.min(qx0, x); qx1 = Math.max(qx1, x);
            qy0 = Math.min(qy0, y); qy1 = Math.max(qy1, y);
            qz0 = Math.min(qz0, z); qz1 = Math.max(qz1, z);
         }
         int b0 = Math.max(0, Math.min(BINS - 1, (int) ((qx0 - x0) / w * BINS)));
         int b1 = Math.max(0, Math.min(BINS - 1, (int) ((qx1 - x0) / w * BINS)));
         for (int b = b0; b <= b1; b++) {
            ylo[b] = Math.min(ylo[b], qy0); yhi[b] = Math.max(yhi[b], qy1);
            zlo[b] = Math.min(zlo[b], qz0); zhi[b] = Math.max(zhi[b], qz1);
         }
      }
      // tranches vides : on reprend la plus proche
      for (int b = 0; b < BINS; b++) {
         if (ylo[b] <= yhi[b]) continue;
         for (int d = 1; d < BINS; d++) {
            int j = b - d >= 0 && ylo[b - d] <= yhi[b - d] ? b - d : b + d < BINS && ylo[b + d] <= yhi[b + d] ? b + d : -1;
            if (j >= 0) {
               ylo[b] = ylo[j]; yhi[b] = yhi[j]; zlo[b] = zlo[j]; zhi[b] = zhi[j];
               break;
            }
         }
         if (ylo[b] > yhi[b]) {
            ylo[b] = y0; yhi[b] = y1; zlo[b] = z0; zhi[b] = z1;
         }
      }
      // enveloppe lissée : maximum sur une fenêtre puis moyenne (pas de creux brusques)
      smooth(ylo, s.syLo, true);
      smooth(yhi, s.syHi, false);
      smooth(zlo, s.szLo, true);
      smooth(zhi, s.szHi, false);
      return s;
   }

   private static void smooth(float[] in, float[] out, boolean low) {
      float[] m = new float[BINS];
      for (int b = 0; b < BINS; b++) {
         float v = in[b];
         for (int d = -3; d <= 3; d++) {
            int j = Math.max(0, Math.min(BINS - 1, b + d));
            v = low ? Math.min(v, in[j]) : Math.max(v, in[j]);
         }
         m[b] = v;
      }
      for (int b = 0; b < BINS; b++) {
         float acc = 0;
         int n = 0;
         for (int d = -3; d <= 3; d++) {
            int j = Math.max(0, Math.min(BINS - 1, b + d));
            acc += m[j];
            n++;
         }
         out[b] = acc / n;
      }
   }

   private float sample(float[] a, float x) {
      float f = (x - this.xMin) / Math.max(this.xMax - this.xMin, 1e-4F) * BINS - 0.5F;
      if (f <= 0) return a[0];
      if (f >= BINS - 1) return a[BINS - 1];
      int i = (int) f;
      float t = f - i;
      return a[i] + (a[i + 1] - a[i]) * t;
   }

   public float length() { return this.xMax - this.xMin; }
   /** x au repos pour une fraction f (0 = crosse, 1 = bouche du canon). */
   public float xAt(float f) { return this.xMin + f * (this.xMax - this.xMin); }

   public float yLo(float x) { return this.sample(this.yLo, x); }
   public float yHi(float x) { return this.sample(this.yHi, x); }
   public float zLo(float x) { return this.sample(this.zLo, x); }
   public float zHi(float x) { return this.sample(this.zHi, x); }

   /** Enveloppe lissée : centre et demi-tailles. */
   public float yMid(float x) { return (this.sample(this.syLo, x) + this.sample(this.syHi, x)) * 0.5F; }
   public float zMid(float x) { return (this.sample(this.szLo, x) + this.sample(this.szHi, x)) * 0.5F; }
   public float halfH(float x) { return (this.sample(this.syHi, x) - this.sample(this.syLo, x)) * 0.5F; }
   public float halfW(float x) { return (this.sample(this.szHi, x) - this.sample(this.szLo, x)) * 0.5F; }
   public float top(float x) { return this.sample(this.syHi, x); }
   public float bottom(float x) { return this.sample(this.syLo, x); }

   /**
    * Bout du canon : [x, y du centre, z du centre, rayon] d'après la dernière tranche (brute). Le rayon vient de la largeur
    * (le guidon, fin et haut, ne l'élargit guère) ; le centre est posé sur le dessous du canon.
    */
   public float[] muzzle() {
      int b = BINS - 1;
      float r = Math.max(0.006F, Math.min(0.03F, (this.zHi[b] - this.zLo[b]) * 0.5F));
      return new float[] {this.xMax, this.yLo[b] + r, (this.zLo[b] + this.zHi[b]) * 0.5F, r};
   }

   /**
    * Guidon : [x, hauteur du sommet] de la tranche la plus haute (brute) dans le dernier quart de l'arme ; null s'il ne dépasse
    * pas nettement du canon (lunette, arme sans guidon).
    */
   public float[] frontSight() {
      float[] m = this.muzzle();
      int best = -1;
      float top = -Float.MAX_VALUE;
      for (int b = BINS * 3 / 4; b < BINS; b++) {
         if (this.yHi[b] > top) {
            top = this.yHi[b];
            best = b;
         }
      }
      if (best < 0 || top - m[1] < m[3] * 2.2F) return null;
      return new float[] {this.xMin + (best + 0.5F) / BINS * (this.xMax - this.xMin), top};
   }

   /** Début du garde-main : en partant du canon vers l'arrière, première tranche qui descend nettement (chargeur, poignée). */
   public float foreEnd() {
      float[] m = this.muzzle();
      float limit = m[1] - m[3] - 0.07F;
      for (int b = BINS - 1; b >= 0; b--) {
         if (this.yLo[b] < limit) return this.xMin + (b + 1.5F) / BINS * (this.xMax - this.xMin);
      }
      return this.xAt(0.5F);
   }

   /**
    * Début du canon nu : en partant de la bouche vers l'arrière, dernière tranche encore fine (largeur et hauteur de l'ordre du
    * canon) ; on s'arrête au garde-main, au fût ou à l'avant d'une lunette.
    */
   public float barrelStart() {
      float[] m = this.muzzle();
      float r = m[3];
      int b = BINS - 1;
      while (b > 1) {
         float hw = (this.zHi[b - 1] - this.zLo[b - 1]) * 0.5F, h = this.yHi[b - 1] - this.yLo[b - 1];
         if (hw > r * 2.6F || h > r * 5.0F) break;
         b--;
      }
      return this.xMin + (float) b / BINS * (this.xMax - this.xMin);
   }

   /** Rayon du canon à l'abscisse x (largeur brute, bornée autour du rayon à la bouche). */
   public float barrelRadius(float x) {
      float r = this.muzzle()[3];
      return Math.max(r, Math.min(r * 2.2F, (this.zHi(x) - this.zLo(x)) * 0.5F));
   }

   /** Hauteur de l'arme (enveloppe) rapportée à sa longueur : ~0.25 fusil, ~0.6 pistolet. */
   public float aspect() {
      return (this.yMax - this.yMin) / Math.max(this.length(), 1e-4F);
   }
}
