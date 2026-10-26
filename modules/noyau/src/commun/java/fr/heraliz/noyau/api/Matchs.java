package fr.heraliz.noyau.api;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * « Le joueur est-il en match ? » pour tout le serveur, sans dépendre du mod qui organise les matchs (aujourd'hui ClashWar).
 * Le mod des matchs se branche avec {@link #brancher(Fournisseur)} ; sans lui, personne n'est jamais en match.
 * <p>Contrat de données gardé tel quel pour les mods qui le lisent sans dépendance : tant qu'un joueur est en match, son
 * {@code persistentData} porte le booléen {@link #CLE_EN_MATCH} ; un objet prêté par un match porte l'étiquette NBT
 * {@link #NBT_PRET}. C'est le mod des matchs qui les pose et les retire.
 * <p>API lue par réflexion par d'autres mods (voix, anticheat, pings, clans) : ne jamais renommer cette classe ni ses
 * méthodes. Fil serveur.
 */
public final class Matchs {
   private Matchs() {}

   /** Booléen du {@code persistentData} : joueur en match (posé et retiré par le mod des matchs). */
   public static final String CLE_EN_MATCH = "heralisz:en_match";
   /** Étiquette NBT d'un objet prêté par un match (kit) : jamais gardé, échangé, vendu ni usé. */
   public static final String NBT_PRET = "HeraliszMatch";

   /** Ce que le mod des matchs sait d'un joueur. */
   public interface Fournisseur {
      boolean enMatch(UUID joueur);

      /** Identifiant de la partie du joueur, ou null. */
      @Nullable
      String partie(UUID joueur);

      /** Identifiant de l'équipe du joueur dans sa partie, ou null (spectateur, hors match). */
      @Nullable
      String equipe(UUID joueur);

      /** Couleur RGB de l'équipe du joueur, -1 si aucune. */
      int couleurEquipe(UUID joueur);

      /** Vrai si le joueur regarde une partie sans y jouer. */
      boolean spectateur(UUID joueur);
   }

   private static volatile Fournisseur fournisseur;

   /** Réservé au mod des matchs. */
   public static void brancher(Fournisseur f) {
      fournisseur = f;
   }

   public static boolean enMatch(@Nullable ServerPlayer p) {
      return p != null && enMatch(p.getUUID());
   }

   public static boolean enMatch(UUID joueur) {
      Fournisseur f = fournisseur;
      return f != null && f.enMatch(joueur);
   }

   @Nullable
   public static String partie(UUID joueur) {
      Fournisseur f = fournisseur;
      return f == null ? null : f.partie(joueur);
   }

   @Nullable
   public static String equipe(UUID joueur) {
      Fournisseur f = fournisseur;
      return f == null ? null : f.equipe(joueur);
   }

   public static int couleurEquipe(UUID joueur) {
      Fournisseur f = fournisseur;
      return f == null ? -1 : f.couleurEquipe(joueur);
   }

   public static boolean spectateur(UUID joueur) {
      Fournisseur f = fournisseur;
      return f != null && f.spectateur(joueur);
   }
}
