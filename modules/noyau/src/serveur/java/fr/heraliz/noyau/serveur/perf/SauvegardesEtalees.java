package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.allege.JoueursAcces;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Sauvegarde au fil de l'eau des tronçons (11/11, profil du banc à 100 joueurs : {@code ChunkMap.processUnloads} 8,1 ms par
 * tick sur 37,8, soit 21 % ; déjà 5,8 ms le 02/11). À CHAQUE tick, Minecraft repart du début de la carte des tronçons chargés
 * (18 500 au banc) et les passe tous en revue ({@code saveChunkIfNeeded}) jusqu'à en avoir écrit 20 ou manqué de temps : quand
 * peu de tronçons ont changé (le cas normal), il les regarde donc tous, 20 fois par seconde, et pour chacun cherche 24 fois
 * si un de ses étages de points d'intérêt est à écrire ({@code PoiManager.flush}).
 * <p>Ici, le tour est étalé : chaque tick regarde une tranche de la carte (un vingtième, 64 tronçons au moins), en reprenant
 * là où le tick d'avant s'est arrêté ; chaque tronçon est donc regardé une fois par seconde au lieu de vingt. Le reste est
 * celui de Minecraft : mêmes tronçons écrits ({@code saveChunkIfNeeded} inchangé), 20 écritures par tick au plus, rien quand
 * le serveur n'a plus de temps, délai de 10 s entre deux écritures d'un même tronçon. Un tronçon modifié est écrit une
 * seconde plus tard au pire. La sauvegarde automatique (toutes les 5 min), celle des tronçons déchargés et celle de l'arrêt
 * ne passent pas par ici : elles regardent toujours tout.
 * <p>Un objet par carte de tronçons (par monde), réemployé à chaque tick : aucune allocation. Fil serveur seulement.
 * <p><b>Sauvegarde automatique étalée (19/11, audit des lenteurs, point C2)</b> : toutes les 5 min, Minecraft écrivait
 * dans le MÊME tick tous les joueurs connectés (NBT complet, compression, fichier) et remettait en NBT toutes les entités
 * de tous les tronçons chargés. Ici, pendant la seule sauvegarde automatique ({@code saveEverything} sans {@code flush} ni
 * {@code force} : ni {@code /save-all}, ni l'arrêt, ni Bukkit) :
 * <ul>
 *    <li>joueurs ({@code JoueursEtalesMixin}, {@code [perf] joueurs_etales}) : rangés en file, puis écrits par Minecraft
 *    lui-même ({@code PlayerList.save} : .dat_old gardé, événement de Forge) quelques-uns par tick, tous en 5 s au plus. Un
 *    joueur qui a un menu ouvert (coffre, coffre du clan, corps, échange) est écrit tout de suite, comme avant : l'objet
 *    qu'il déplace est sur le disque au même instant que le coffre. Un joueur déconnecté entre-temps a été écrit par
 *    Minecraft à sa déconnexion ; un joueur réapparu est écrit sous sa nouvelle forme ;</li>
 *    <li>entités ({@code EntitesEtaleesMixin}, {@code [perf] entites_etalees}) : la liste des tronçons à écrire est prise
 *    au même moment, puis chaque tronçon est traité comme Minecraft le fait (tronçon caché : déchargé ; sinon ses entités
 *    écrites), par tranches, tout en 5 s au plus. {@code /save-all flush} et l'arrêt ({@code saveAll}) écrivent tout et
 *    effacent la tranche en cours.</li>
 * </ul>
 */
public final class SauvegardesEtalees implements ObjectIterator<ChunkHolder> {
   /** Durée d'un tour de sauvegarde étalée (ticks) : 5 s. */
   private static final int DUREE = 100;
   /** Entités : au moins autant de tronçons par tick et par monde. */
   private static final int ENTITES_MINIMUM = 16;

   /** Une sauvegarde automatique est en cours ({@code JoueursEtalesMixin}, fil du serveur). */
   public static boolean automatique;
   /** Tick de la dernière sauvegarde automatique (pour les rapports du diagnostic). */
   public static int tickAuto = -1;
   private static final ArrayDeque<UUID> JOUEURS = new ArrayDeque<>();
   private static int joueursParTick;

   /** Gestionnaire des entités d'un monde, avec un tour en cours ({@code EntitesEtaleesMixin}). */
   public interface Entites {
      /** Traite au plus {@code max} tronçons du tour ; rend le nombre de tronçons qui restent. */
      int heraliz$tranche(int max);
   }

   private static final ArrayList<Entites> ENTITES = new ArrayList<>();
   private static final ArrayList<Integer> ENTITES_PAR_TICK = new ArrayList<>();
   /** Rien à faire après l'écriture des entités d'un tronçon (comme {@code autoSave} de Minecraft). */
   public static final java.util.function.Consumer<Object> RIEN = e -> {
   };

   /**
    * À la place de {@code PlayerList.saveAll()} dans {@code saveEverything} : vrai si les joueurs sont mis en file (la
    * sauvegarde automatique seulement), faux si Minecraft doit tous les écrire tout de suite.
    */
   public static boolean joueurs(PlayerList liste) {
      if (!automatique || !NoyauConfig.joueursEtales || !(liste instanceof JoueursAcces acces)) {
         return false;
      }
      JOUEURS.clear();   // tour d'avant pas fini (jamais vu : 5 s pour 5 min) : celui-ci reprend tout le monde
      for (ServerPlayer p : liste.getPlayers()) {
         if (p.containerMenu != p.inventoryMenu) {
            acces.heraliz$sauver(p);   // menu ouvert : échange en cours, écrit au même instant que le coffre
            Allege.joueursToutDeSuite++;
         } else {
            JOUEURS.add(p.getUUID());
         }
      }
      joueursParTick = Math.max(1, (JOUEURS.size() + DUREE - 1) / DUREE);
      Allege.joueursTours++;
      return true;
   }

   /** {@code EntitesEtaleesMixin} : la sauvegarde automatique des entités de ce monde peut-elle être étalée ? */
   public static boolean entitesEtalables() {
      return automatique && NoyauConfig.entitesEtalees;
   }

   /** {@code EntitesEtaleesMixin} : un tour de {@code n} tronçons commence pour ce gestionnaire. */
   public static void entitesAEtaler(Entites e, int n) {
      int par = Math.max(ENTITES_MINIMUM, (n + DUREE - 1) / DUREE);
      int i = ENTITES.indexOf(e);
      if (i >= 0) {
         ENTITES_PAR_TICK.set(i, par);
      } else {
         ENTITES.add(e);
         ENTITES_PAR_TICK.add(par);
      }
      Allege.entitesTours++;
   }

   /** Fin de chaque tick (Allege) : une tranche de joueurs et une tranche des entités de chaque monde en cours. */
   public static void tick(MinecraftServer srv) {
      automatique = false;
      if (!JOUEURS.isEmpty()) {
         PlayerList liste = srv.getPlayerList();
         for (int i = 0; i < joueursParTick && !JOUEURS.isEmpty(); i++) {
            ServerPlayer p = liste.getPlayer(JOUEURS.poll());
            // déconnecté entre-temps : déjà écrit par Minecraft à sa déconnexion
            if (p != null && !p.hasDisconnected() && liste instanceof JoueursAcces acces) {
               acces.heraliz$sauver(p);
               Allege.joueursEtales++;
            }
         }
      }
      for (int i = ENTITES.size() - 1; i >= 0; i--) {
         int par = ENTITES_PAR_TICK.get(i);
         int reste = ENTITES.get(i).heraliz$tranche(par);
         Allege.entitesTranches++;
         if (reste <= 0) {
            ENTITES.remove(i);
            ENTITES_PAR_TICK.remove(i);
         }
      }
   }

   /** Pour un rapport de blocage : la sauvegarde automatique a eu lieu pendant ce tick, ou un tour étalé est en cours ; sinon null. */
   public static String dansCeTick(int tick) {
      if (tick == tickAuto) return "Sauvegarde automatique de Minecraft pendant ce tick (toutes les 5 min)";
      if (!JOUEURS.isEmpty() || !ENTITES.isEmpty()) {
         return "Sauvegarde automatique étalée en cours (" + JOUEURS.size() + " joueurs et " + ENTITES.size() + " mondes d'entités à finir)";
      }
      return null;
   }

   /** Arrêt du serveur : plus rien à étaler (l'arrêt écrit tout). */
   public static void vider() {
      JOUEURS.clear();
      ENTITES.clear();
      ENTITES_PAR_TICK.clear();
      automatique = false;
   }

   /** Part de la carte regardée par tick : un vingtième (un tour complet par seconde). */
   private static final int PART = 20;
   /** Au moins autant de tronçons par tick (petites cartes : tour complet plus vite). */
   private static final int MINIMUM = 64;

   private Long2ObjectLinkedOpenHashMap<ChunkHolder> carte;
   private LongIterator cles;
   private int reste;
   /** Déjà reparti du début de la carte pendant ce tick. */
   private boolean reparti;
   /** Dernier tronçon regardé (clé de la carte) : le tick suivant reprend juste après. */
   private long reprise;
   private boolean aReprise;

   /** Prépare la tranche de ce tick sur la carte visible du moment, et se rend lui-même. */
   public SauvegardesEtalees tranche(Long2ObjectLinkedOpenHashMap<ChunkHolder> visibles) {
      this.carte = visibles;
      int n = visibles.size();
      if (n == 0) {
         // monde sans tronçon chargé (arènes vides, tutoriel sans joueur) : rien à regarder. Surtout ne pas demander à la
         // carte un parcours « à partir de » : sur une carte vide, fastutil lit sa case -1 (plantage du banc du 12/11).
         this.reste = 0;
         this.reparti = true;
         this.aReprise = false;
         this.cles = LongIterators.EMPTY_ITERATOR;
         return this;
      }
      this.reste = Math.min(n, Math.max(MINIMUM, (n + PART - 1) / PART));
      this.reparti = false;
      LongIterator depart = null;
      if (aReprise) {
         try {
            depart = visibles.keySet().iterator(reprise);   // juste après le dernier regardé
         } catch (RuntimeException plusCharge) {
            depart = null;                                  // ce tronçon n'est plus chargé : on repart du début
         }
      }
      if (depart == null) {
         depart = visibles.keySet().iterator();
         this.reparti = true;
      }
      this.cles = depart;
      Allege.sauvegardesTranches++;
      return this;
   }

   @Override
   public boolean hasNext() {
      if (reste <= 0) {
         return false;
      }
      if (cles.hasNext()) {
         return true;
      }
      if (reparti) {
         return false;
      }
      reparti = true;
      cles = carte.keySet().iterator();
      return cles.hasNext();
   }

   @Override
   public ChunkHolder next() {
      if (!hasNext()) {
         throw new NoSuchElementException();
      }
      long cle = cles.nextLong();
      reste--;
      reprise = cle;
      aReprise = true;
      Allege.sauvegardesRegardes++;
      return carte.get(cle);
   }
}
