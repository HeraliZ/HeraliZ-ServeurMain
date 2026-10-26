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

   /**
    * Recharge du /tpa (26/10, demande du propriétaire) : 8 min sans grade, Rescapé 7 min 30, Traqueur 7 min, Roi de la Zone
    * 6 min 30 ; indépendante de la téléportation vers la base.
    */
   public static final Perm TPA_RECHARGE = new Perm("tpa_recharge", Sorte.SECONDES, 0, 3600, 15, 480, true, "deplacements");
   public static final Perm TPA_DELAI = new Perm("tpa_delai", Sorte.SECONDES, 0, 60, 1, 15, true, "deplacements");
   public static final Perm HOMES = new Perm("homes", Sorte.NOMBRE, 1, 20, 1, 1, false, "deplacements");
   /**
    * Recharge de la téléportation vers la base (/home et réapparition à la base : un seul délai, CRac
    * {@code bases/TeleportBase}), 26/10 : mêmes valeurs que le /tpa, recharge à part.
    */
   public static final Perm HOME_RECHARGE = new Perm("home_recharge", Sorte.SECONDES, 0, 3600, 15, 480, true, "deplacements");
   public static final Perm RTP_RECHARGE = new Perm("rtp_recharge", Sorte.SECONDES, 0, 3600, 15, 300, true, "deplacements");
   public static final Perm PLANQUE = new Perm("planque_lignes", Sorte.LIGNES, 1, 6, 1, 1, false, "coffres");
   public static final Perm COFFRE_VIP = new Perm("coffre_vip_lignes", Sorte.LIGNES, 0, 6, 1, 0, false, "coffres");
   public static final Perm ZONES_VIP = new Perm("zones_vip", Sorte.OUI_NON, 0, 1, 1, 0, false, "coffres");
   /**
    * Ancienne réduction en pourcentage du délai du /home (22/10) : plus lue depuis le 26/10 (« Recharge du /home » en
    * secondes la remplace). Gardée pour ne pas casser les fichiers des grades qui la contiennent.
    */
   public static final Perm TP_REDUCTION = new Perm("tp_reduction", Sorte.NOMBRE, 0, 90, 5, 0, false, "deplacements");
   /**
    * Cases du coffre VIP (« Safe Sentinel », HeraliZ Utils), 0 à 54 : remplace « coffre_vip_lignes » (22/10, choix du
    * propriétaire : Rescapé 3, Traqueur 6, Roi de la Zone 9 cases). 0 = pas d'accès.
    */
   public static final Perm COFFRE_VIP_CASES = new Perm("coffre_vip_cases", Sorte.NOMBRE, 0, 54, 1, 0, false, "coffres");
   /** Droit au surnom (/nick) : 22/10, Traqueur et Roi de la Zone. */
   public static final Perm NICK = new Perm("nick", Sorte.OUI_NON, 0, 1, 1, 0, false, "coffres");
   /** Droit au chat en couleur (/chatcouleur) : 22/10, Roi de la Zone. */
   public static final Perm CHAT_COULEUR = new Perm("chat_couleur", Sorte.OUI_NON, 0, 1, 1, 0, false, "coffres");

   public static final List<Perm> TOUTES = List.of(TPA_RECHARGE, TPA_DELAI, HOMES, HOME_RECHARGE, RTP_RECHARGE, PLANQUE, COFFRE_VIP, ZONES_VIP,
           TP_REDUCTION, COFFRE_VIP_CASES, NICK, CHAT_COULEUR);

   private PermsGrades() {
   }

   public static Perm parCle(String cle) {
      for (Perm p : TOUTES) if (p.cle().equals(cle)) return p;
      return null;
   }

   /** Réglages d'origine des grades internes (ceux d'avant la 2.0.0) ; le fichier des grades les remplace ensuite. */
   static void defauts() {
      for (Perm p : TOUTES) Grade.SURVIVANT.perms.put(p.cle(), p.defaut());
      // 22/10 : /tpa -10, -20, -30 % (5 min pour Survivant), /home réduit d'autant, coffre VIP en cases, surnom, chat en couleur
      // 23/10 (Grades 2.6.0, avantages officiels de la boutique) : 1 home pour tous les grades
      // 26/10 : /tpa et base 8 min, Rescapé -30 s, Traqueur -1 min, Roi de la Zone -1 min 30 (deux recharges séparées)
      regler(Grade.RESCAPE, 450, 450, 1, 1, 10, 3, 0, 0);
      regler(Grade.TRAQUEUR, 420, 420, 1, 1, 20, 6, 1, 0);
      regler(Grade.ROI_DE_LA_ZONE, 390, 390, 1, 1, 30, 9, 1, 1);
      regler(Grade.STREAMER, 465, -1, 1, 1, 0, 0, 0, 0);
   }

   /**
    * Recharges du 26/10 (/tpa et base) des grades d'origine : migration du fichier des grades, version 3 ; null pour les
    * autres. Streamer garde ses 15 s de moins sur le /tpa seulement (comme avant) ; les grades du staff et ceux créés en
    * jeu suivent Survivant.
    */
   public static java.util.Map<String, Integer> recharges26oct(Grade g) {
      if (g == Grade.SURVIVANT) return java.util.Map.of(TPA_RECHARGE.cle(), 480, HOME_RECHARGE.cle(), 480);
      if (g == Grade.RESCAPE) return java.util.Map.of(TPA_RECHARGE.cle(), 450, HOME_RECHARGE.cle(), 450);
      if (g == Grade.TRAQUEUR) return java.util.Map.of(TPA_RECHARGE.cle(), 420, HOME_RECHARGE.cle(), 420);
      if (g == Grade.ROI_DE_LA_ZONE) return java.util.Map.of(TPA_RECHARGE.cle(), 390, HOME_RECHARGE.cle(), 390);
      if (g == Grade.STREAMER) return java.util.Map.of(TPA_RECHARGE.cle(), 465);
      return null;
   }

   /** home &lt; 0 : pas de réglage propre (comme Survivant). */
   private static void regler(Grade g, int tpa, int home, int homes, int vip, int reduction, int casesVip, int nick, int chatCouleur) {
      g.perms.put(TPA_RECHARGE.cle(), tpa);
      if (home >= 0) g.perms.put(HOME_RECHARGE.cle(), home);
      g.perms.put(HOMES.cle(), homes);
      g.perms.put(COFFRE_VIP.cle(), vip);
      g.perms.put(ZONES_VIP.cle(), 1);
      g.perms.put(TP_REDUCTION.cle(), reduction);
      g.perms.put(COFFRE_VIP_CASES.cle(), casesVip);
      g.perms.put(NICK.cle(), nick);
      g.perms.put(CHAT_COULEUR.cle(), chatCouleur);
   }

   /** Réglages du 22/10 d'un grade boutique d'origine (migration du fichier des grades, version 2) ; null pour les autres. */
   public static java.util.Map<String, Integer> reglagesBoutique(Grade g) {
      if (g == Grade.RESCAPE) return java.util.Map.of(TPA_RECHARGE.cle(), 270, TP_REDUCTION.cle(), 10, COFFRE_VIP_CASES.cle(), 3);
      if (g == Grade.TRAQUEUR) return java.util.Map.of(TPA_RECHARGE.cle(), 240, TP_REDUCTION.cle(), 20, COFFRE_VIP_CASES.cle(), 6, NICK.cle(), 1);
      if (g == Grade.ROI_DE_LA_ZONE) return java.util.Map.of(TPA_RECHARGE.cle(), 210, TP_REDUCTION.cle(), 30, COFFRE_VIP_CASES.cle(), 9, NICK.cle(), 1,
              CHAT_COULEUR.cle(), 1);
      return null;
   }
}
