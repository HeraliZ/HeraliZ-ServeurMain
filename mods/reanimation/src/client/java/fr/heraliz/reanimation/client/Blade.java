package fr.heraliz.reanimation.client;

import org.joml.Vector3f;

final class Blade {
   static final Blade KARAMBIT = new Blade(
      "karambit", new Vector3f(-2.605F, 10.102F, -0.157F), new Vector3f(0.866F, 0.0F, 0.5F), new Vector3f(0.616F, 13.394F, -5.737F)
   );
   static final Blade BUTTERFLY = new Blade(
      "butterfly", new Vector3f(-0.74F, 9.28F, -2.38F), new Vector3f(0.828F, 0.0F, 0.56F), new Vector3f(2.22F, 9.64F, -6.87F)
   );
   static final Blade M9 = new Blade("m9", new Vector3f(-0.2F, 10.1F, -3.77F), new Vector3f(0.83F, -0.25F, 0.5F), new Vector3f(2.63F, 10.91F, -8.32F));
   static final Blade SEA_GLEAM = new Blade("sea_gleam", new Vector3f(-0.9F, 8.4F, 0.0F), new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(-1.0F, 9.8F, -12.2F));
   static final Blade BAT = new Blade("baseball_bat", new Vector3f(-1.0F, 9.0F, 0.0F), new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(-1.0F, 9.0F, -25.0F));
   final String name;
   final Vector3f pivot;
   final Vector3f axis;
   final Vector3f tip;
   final float length;

   private Blade(String name, Vector3f pivot, Vector3f axis, Vector3f tip) {
      this.name = name;
      this.pivot = pivot;
      this.axis = new Vector3f(axis).normalize();
      this.tip = tip;
      this.length = new Vector3f(tip).sub(pivot).length();
   }
}
