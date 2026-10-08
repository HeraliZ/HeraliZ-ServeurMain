package fr.heralisz.camo.fx;

/** Petit vecteur 3D immuable (code des effets lisible ; les allocations courtes coûtent peu à la JVM). */
public final class V {
   public static final V ZERO = new V(0, 0, 0), X = new V(1, 0, 0), Y = new V(0, 1, 0), Z = new V(0, 0, 1);
   public final float x, y, z;

   public V(float x, float y, float z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public V add(V o) { return new V(this.x + o.x, this.y + o.y, this.z + o.z); }
   public V sub(V o) { return new V(this.x - o.x, this.y - o.y, this.z - o.z); }
   public V mul(float k) { return new V(this.x * k, this.y * k, this.z * k); }
   /** this + o * k */
   public V add(V o, float k) { return new V(this.x + o.x * k, this.y + o.y * k, this.z + o.z * k); }
   public V neg() { return new V(-this.x, -this.y, -this.z); }
   public float dot(V o) { return this.x * o.x + this.y * o.y + this.z * o.z; }
   public V cross(V o) { return new V(this.y * o.z - this.z * o.y, this.z * o.x - this.x * o.z, this.x * o.y - this.y * o.x); }
   public float len() { return (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z); }
   public float dist(V o) { return this.sub(o).len(); }
   public V lerp(V o, float t) { return new V(this.x + (o.x - this.x) * t, this.y + (o.y - this.y) * t, this.z + (o.z - this.z) * t); }

   /** Vecteur unitaire (Y si nul). */
   public V norm() {
      float l = this.len();
      return l < 1e-9F ? Y : new V(this.x / l, this.y / l, this.z / l);
   }

   /** Composante perpendiculaire à l'axe unitaire a, normalisée (repli : n'importe quelle perpendiculaire). */
   public V perp(V a) {
      V p = this.add(a, -this.dot(a));
      return p.len() < 1e-6F ? a.anyPerp() : p.norm();
   }

   public V anyPerp() {
      V ref = Math.abs(this.y) < 0.9F ? Y : X;
      return this.cross(ref).norm();
   }

   /** Rotation de this autour de l'axe unitaire a (Rodrigues). */
   public V rot(V a, float ang) {
      float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
      return this.mul(c).add(a.cross(this), s).add(a, a.dot(this) * (1 - c));
   }

   @Override
   public String toString() {
      return "(" + this.x + ", " + this.y + ", " + this.z + ")";
   }
}
