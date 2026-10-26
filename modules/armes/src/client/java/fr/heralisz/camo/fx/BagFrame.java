package fr.heralisz.camo.fx;

/**
 * Repère d'un sac porté, dans le repère où il est dessiné : centre du modèle, haut du sac, côté (x du modèle), dos (z du
 * modèle : face vue derrière le joueur, vers l'extérieur) et demi-tailles en blocs. scale = échelle du rendu (~1 : le
 * joueur mesure 1,8) : les effets des sacs (ailes, chaînes…) sont dessinés à taille humaine, pas à la taille du sac.
 */
public final class BagFrame {
   public final V o, up, side, out;
   /** Point d'accroche du sac sur le joueur (haut du dos, au niveau de la nuque) : sert à placer ce qui dépend du corps. */
   public final V neck;
   public final float hx, hy, hz, scale;
   /**
    * Dos réel du sac (facultatif) : profondeur maximale du modèle (z, vers l'extérieur) sur une grille g x g de sa boîte
    * (x, y du modèle), NaN = pas de sac à cet endroit ; bornes du modèle et transformation modèle -> rendu (3 lignes de 4).
    */
   private float[] depth;
   private int g;
   private float[] mmin, mmax, mat;

   public BagFrame(V o, V neck, V up, V side, V out, float hx, float hy, float hz, float scale) {
      this.o = o;
      this.neck = neck;
      this.up = up;
      this.side = side;
      this.out = out;
      this.hx = hx;
      this.hy = hy;
      this.hz = hz;
      this.scale = scale;
   }

   /** Distance de la nuque à la face arrière du sac (le long de out). */
   public float backDepth() {
      return this.o.sub(this.neck).dot(this.out) + this.hz;
   }

   public BagFrame withBack(float[] depth, int g, float[] mmin, float[] mmax, float[] mat) {
      this.depth = depth;
      this.g = g;
      this.mmin = mmin;
      this.mmax = mmax;
      this.mat = mat;
      return this;
   }

   /**
    * Point de la vraie surface arrière du sac en (fx, fy) (-1..1 sur la largeur et la hauteur de sa boîte), décollé de lift
    * (blocs) vers l'extérieur ; null s'il n'y a pas de sac à cet endroit (sac en biais, trous). Sans carte : face de la boîte.
    */
   public V back(float fx, float fy, float lift) {
      if (this.depth == null) return this.at(fx, fy, 1).add(this.out, lift);
      float u = (fx + 1) * 0.5F * (this.g - 1), v = (fy + 1) * 0.5F * (this.g - 1);
      int i = Math.max(0, Math.min(this.g - 2, (int) u)), j = Math.max(0, Math.min(this.g - 2, (int) v));
      float a = u - i, b = v - j;
      float d00 = this.depth[j * this.g + i], d10 = this.depth[j * this.g + i + 1], d01 = this.depth[(j + 1) * this.g + i],
         d11 = this.depth[(j + 1) * this.g + i + 1];
      float z;
      if (Float.isNaN(d00) || Float.isNaN(d10) || Float.isNaN(d01) || Float.isNaN(d11)) {
         float n = this.depth[Math.round(v) * this.g + Math.round(u)];
         if (Float.isNaN(n)) return null;
         z = n;
      } else {
         z = (d00 * (1 - a) + d10 * a) * (1 - b) + (d01 * (1 - a) + d11 * a) * b;
      }
      float x = this.mmin[0] + (fx + 1) * 0.5F * (this.mmax[0] - this.mmin[0]) - 0.5F;
      float y = this.mmin[1] + (fy + 1) * 0.5F * (this.mmax[1] - this.mmin[1]) - 0.5F;
      z -= 0.5F;
      float[] m = this.mat;
      V p = new V(m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]);
      return p.add(this.out, lift);
   }

   /**
    * Haut réel du sac à la position fx (-1..1 sur sa largeur) et à la profondeur fz (-1 contre le joueur .. 1 dos) : le point
    * le plus haut du dos à cet endroit, ramené à la profondeur voulue. Sans sac à cet endroit : haut de la boîte.
    */
   public V top(float fx, float fz) {
      for (float fy = 0.98F; fy >= -0.98F; fy -= 0.07F) {
         V p = this.back(fx, fy, 0);
         if (p != null) return p.add(this.out, (fz - 1) * this.hz);
      }
      return this.at(fx, 1, fz);
   }

   /** Point du sac en demi-tailles (-1..1 sur chaque axe : côté, haut, dos). */
   public V at(float fx, float fy, float fz) {
      return this.o.add(this.side, fx * this.hx).add(this.up, fy * this.hy).add(this.out, fz * this.hz);
   }
}
