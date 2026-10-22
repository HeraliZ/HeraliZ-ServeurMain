package fr.heraliz.grades;

import java.util.List;

/**
 * Permissions réglables par grade (2.0.0) : de simples nombres, avec leurs bornes et leur valeur de base (celle de
 * Survivant). Un joueur qui a plusieurs grades garde le meilleur réglage de chacun : le plus petit pour une recharge ou un
 * délai, le plus grand pour un nombre ou un accès. Aucune ne donne d'avantage en combat (règles de Mojang pour la boutique).
 * <p>
 * Pour en ajouter une : une ligne dans {@link #TOUTES} (clé jamais renommée : elle est dans le fichier des grades et lue
 * par les autres mods via {@code GradesApi.perm}), ses textes {@code heraliz_grades.perm.<clé>} et {@code .<clé>.aide}
 * dans les trois langues, puis la lire là où elle sert.
 */
public final class PermsGrades {
   /** SECONDES : recharge ou délai ; NOMBRE ; LIGNES (de 9 cases) ; OUI_NON (0 ou 1). */
   public enum Sorte { SECONDES, NOMBRE, LIGNES, OUI_NON }

   /** Une permission : plusPetitMeilleur = une recharge (le plus petit gagne). */
   public record Perm(String cle, Sorte sorte, int min, int max, int pas, int defaut, boolean plusPetitMeilleur, String groupe) {
      public int borner(int v) {
         return Math.max(min, Math.min(max, v));
      }

      /** Le meilleur de deux réglages. */
      public int meilleur(int a, int b) {
         return plusPetitMeilleur ? Math.min(a, b) : Math.max(a, b);
      }
   }

   public static final Perm TPA_RECHARGE = new Perm("tpa_recharge", Sorte.SECONDES, 0, 3600, 15, 300, true, "deplacements");
   public static final Perm TPA_DELAI = new Perm("tpa_delai", Sorte.SECONDES, 0, 60, 1, 15, true, "deplacements");
   public static final Perm HOMES = new Perm("homes", Sorte.NOMBRE, 1, 20, 1, 1, false, "deplacements");
   public static final Perm HOME_RECHARGE = new Perm("home_recharge", Sorte.SECONDES, 0, 3600, 15, 180, true, "deplacements");
   public static final Perm RTP_RECHARGE = new Perm("rtp_recharge", Sorte.SECONDES, 0, 3600, 15, 300, true, "deplacements");
   public static final Perm PLANQUE = new Perm("planque_lignes", Sorte.LIGNES, 1, 6, 1, 1, false, "coffres");
   public static final Perm COFFRE_VIP = new Perm("coffre_vip_lignes", Sorte.LIGNES, 0, 6, 1, 0, false, "coffres");
   public static final Perm ZONES_VIP = new Perm("zones_vip", Sorte.OUI_NON, 0, 1, 1, 0, false, "coffres");

   public static final List<Perm> TOUTES = List.of(TPA_RECHARGE, TPA_DELAI, HOMES, HOME_RECHARGE, RTP_RECHARGE, PLANQUE, COFFRE_VIP, ZONES_VIP);

   private PermsGrades() {
   }

   public static Perm parCle(String cle) {
      for (Perm p : TOUTES) if (p.cle().equals(cle)) return p;
      return null;
   }

   /** Réglages d'origine des grades internes (ceux d'avant la 2.0.0) ; le fichier des grades les remplace ensuite. */
   static void defauts() {
      for (Perm p : TOUTES) Grade.SURVIVANT.perms.put(p.cle(), p.defaut());
      regler(Grade.RESCAPE, 285, 2, 1);
      regler(Grade.TRAQUEUR, 270, 3, 1);
      regler(Grade.ROI_DE_LA_ZONE, 255, 4, 1);
      regler(Grade.STREAMER, 285, 2, 1);
   }

   private static void regler(Grade g, int tpa, int homes, int vip) {
      g.perms.put(TPA_RECHARGE.cle(), tpa);
      g.perms.put(HOMES.cle(), homes);
      g.perms.put(COFFRE_VIP.cle(), vip);
      g.perms.put(ZONES_VIP.cle(), 1);
   }
}
