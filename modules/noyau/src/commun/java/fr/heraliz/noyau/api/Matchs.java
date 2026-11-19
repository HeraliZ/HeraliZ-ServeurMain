package fr.heraliz.noyau.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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
   /**
    * Marque du {@code PlayerPersisted} : inventaire du joueur mis de côté par le Coffre du mod des matchs (posée au départ,
    * retirée au retour ; elle reste après un plantage tant que le Coffre n'est pas rendu).
    */
   public static final String CLE_DEPOT = "HeralizClashWarDepot";
   /** Espace de noms des mondes des matchs (arènes, zombies). */
   public static final String MONDES = "heraliz_clashwar";

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

   /**
    * Inventaire « tenu par le match » (19/11, audit des duplications) : joueur en match, drapeau {@link #CLE_EN_MATCH},
    * inventaire mis de côté par le Coffre ({@link #CLE_DEPOT}, reconnexion après un plantage comprise) ou joueur dans un
    * monde des matchs. Ce qui serait livré dans cet inventaire servirait pendant le match, puis serait effacé au retour :
    * toute attente (objets à rendre, copie gardée d'un écran, récompense, kit) attend que ce soit faux. La seule règle pour
    * tous les mods : l'utiliser plutôt qu'une copie. Fil serveur.
    */
   public static boolean inventaireRetenu(@Nullable ServerPlayer p) {
      if (p == null) return false;
      if (enMatch(p.getUUID())) return true;
      CompoundTag d = p.getPersistentData();
      if (d.getBoolean(CLE_EN_MATCH) || d.getCompound(Player.PERSISTED_NBT_TAG).contains(CLE_DEPOT)) return true;
      return MONDES.equals(p.level().dimension().location().getNamespace());
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
