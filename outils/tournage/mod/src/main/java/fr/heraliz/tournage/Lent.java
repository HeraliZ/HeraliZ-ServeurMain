package fr.heraliz.tournage;

/** Ralenti du tournage (propriété -Dtournage.lent=5 : tout le jeu tourne 5 fois moins vite). */
public final class Lent {
   public static final double FACTEUR = Math.max(1.0, Double.parseDouble(System.getProperty("tournage.lent", "1")));

   private Lent() {}
}
