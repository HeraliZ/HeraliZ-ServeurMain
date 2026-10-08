package fr.heralisz.camo.fx;

import java.util.Arrays;

/**
 * Géométrie d'un effet pour une image, rangée par mode de mélange (opaque, transparent, additif).
 * Par sommet : position (repère de l'arme rendue), « couleur » = paramètres lus par le shader de l'effet
 * (r = sorte d'élément, g / b = paramètres, a = fondu), UV, normale, deux entiers (graine, index) passés dans UV2.
 * Les couleurs réelles sont dans le shader : le code Java ne décrit que des formes.
 */
public final class FxMesh {
   public static final int SOLID = 0, ALPHA = 1, ADD = 2, MODES = 3;
   /** x y z | kind p1 p2 fade | u v | nx ny nz | s1 s2 */
   public static final int STRIDE = 14;
   private static final int MAX_VERTS = 80_000;

   public final float[][] buf = new float[MODES][];
   /** Nombre de sommets par mode. */
   public final int[] verts = new int[MODES];
   private int total;

   private int mode = ADD;
   private float kind, p1, p2, fade = 1, s1, s2;

   public FxMesh() {
      for (int m = 0; m < MODES; m++) {
         this.buf[m] = new float[STRIDE * 4096];
      }
   }

   public void clear() {
      Arrays.fill(this.verts, 0);
      this.total = 0;
   }

   /** Choisit le mode et la sorte d'élément ; remet les paramètres à zéro. */
   public FxMesh set(int mode, int kind) {
      this.mode = mode;
      this.kind = kind / 255F;
      this.p1 = 0;
      this.p2 = 0;
      this.fade = 1;
      this.s1 = 0;
      this.s2 = 0;
      return this;
   }

   /** Paramètres libres (0..1, quantifiés sur 8 bits dans le jeu). */
   public FxMesh p(float p1, float p2) {
      this.p1 = clamp01(p1);
      this.p2 = clamp01(p2);
      return this;
   }

   public FxMesh fade(float f) {
      this.fade = clamp01(f);
      return this;
   }

   /** Deux entiers 0..32767 (graine de l'élément, index…), lus en flottants par le shader. */
   public FxMesh seed(int s1, int s2) {
      this.s1 = s1 & 0x7FFF;
      this.s2 = s2 & 0x7FFF;
      return this;
   }

   public boolean full() {
      return this.total >= MAX_VERTS;
   }

   public void v(V p, float u, float v, V n) {
      this.v(p.x, p.y, p.z, u, v, n.x, n.y, n.z);
   }

   /** Même sommet, coordonnées à plat (géométrie dense : anneaux, maillons, sans créer d'objets). */
   public void v(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
      if (this.total >= MAX_VERTS) {
         return;
      }
      int m = this.mode;
      int i = this.verts[m] * STRIDE;
      float[] b = this.buf[m];
      if (i + STRIDE > b.length) {
         b = this.buf[m] = Arrays.copyOf(b, b.length * 2);
      }
      b[i] = x; b[i + 1] = y; b[i + 2] = z;
      b[i + 3] = this.kind; b[i + 4] = this.p1; b[i + 5] = this.p2; b[i + 6] = this.fade;
      b[i + 7] = u; b[i + 8] = v;
      b[i + 9] = nx; b[i + 10] = ny; b[i + 11] = nz;
      b[i + 12] = this.s1; b[i + 13] = this.s2;
      this.verts[m]++;
      this.total++;
   }

   /** Quad a b c d ; UV (u0,v0) (u1,v0) (u1,v1) (u0,v1). */
   public void quad(V a, V b, V c, V d, float u0, float v0, float u1, float v1, V n) {
      if (this.total + 4 > MAX_VERTS) {
         return;
      }
      this.v(a, u0, v0, n);
      this.v(b, u1, v0, n);
      this.v(c, u1, v1, n);
      this.v(d, u0, v1, n);
   }

   private static float clamp01(float v) {
      return v < 0 ? 0 : (v > 1 ? 1 : v);
   }
}
