package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;

/**
 * Tronçons qui tournent « au repos » (20/11, audit du tick : {@code ServerChunkCache.tickChunks}, 7,8 ms par tick au banc à
 * 100 joueurs, le plus gros morceau de Minecraft). À chaque tick, pour CHAQUE tronçon qui tourne (plusieurs milliers),
 * Minecraft demande si les créatures peuvent y vivre, si un joueur est à moins de 128 blocs (temps passé dans le tronçon),
 * s'il peut faire des tics de blocs, puis appelle {@code ServerLevel.tickChunk} (foudre, glace, neige, tics aléatoires).
 * <p>Sur ce serveur, dans un monde où rien de tout cela ne peut arriver pendant ce tick — tics aléatoires à 0 ({@code [monde]
 * tics_aleatoires}), règle {@code doMobSpawning} à faux, pas de pluie (ni foudre, ni neige, ni remplissage par la pluie),
 * eau qui ne gèle pas ({@code [monde] gel_eau}) — {@code tickChunk} ne change rien : seul compte le temps passé par les
 * joueurs dans chaque tronçon (difficulté locale : chance d'un zombie « chef » plus résistant). Alors chaque tronçon n'est
 * vu qu'une fois tous les {@code N} ticks ({@code [perf] troncons_tranches}, une tranche de 1/N des tronçons par tick, mêlée
 * sur la carte), avec N ticks de temps passé d'un coup : même total. Les envois des blocs changés aux joueurs restent faits
 * à CHAQUE tick pour tous les tronçons qui en ont ({@link TronconsActifs}). Dès qu'une condition manque (pluie, règle
 * changée...), le tick redevient celui de Minecraft.
 * <p>Fil serveur seulement ; l'état vaut pour le {@code tickChunks} en cours (un monde après l'autre).
 */
public final class TronconsRepos {
   private TronconsRepos() {
   }

   /** Le {@code tickChunks} en cours est « au repos » : tranche seulement. */
   private static boolean repos;
   private static int tranches = 1;
   private static int tranche;

   /** Début de {@code tickChunks} d'un monde : au repos pendant ce tick ? */
   public static void debut(ServerLevel niveau) {
      int n = NoyauConfig.tronconsTranches;
      GameRules regles = niveau.getGameRules();
      repos = n > 1 && !niveau.isDebug() && regles.getInt(GameRules.RULE_RANDOMTICKING) == 0
            && !regles.getBoolean(GameRules.RULE_DOMOBSPAWNING) && !niveau.isRaining() && !NoyauConfig.gelEau;
      if (repos) {
         tranches = n;
         tranche = Math.floorMod(niveau.getServer().getTickCount(), n);
         Allege.reposTicks++;
      } else {
         Allege.reposMinecraft++;
      }
   }

   /** Fin de {@code tickChunks} : plus rien n'est « au repos » en dehors. */
   public static void fin() {
      repos = false;
   }

   public static boolean repos() {
      return repos;
   }

   /** Tranche d'un tronçon (0 à n - 1), mêlée sur la carte : deux voisins ne tombent pas dans la même. */
   static int trancheDe(int x, int z, int n) {
      return Math.floorMod(x * 7 + z * 13, n);
   }

   /** Au repos : ce tronçon est-il de la tranche de ce tick ? (hors repos : toujours) */
   public static boolean dansLaTranche(int x, int z) {
      return !repos || trancheDe(x, z, tranches) == tranche;
   }

   /** Au repos, un tronçon hors de sa tranche ne fait rien ce tick (ni temps passé, ni tics) : Minecraft passe au suivant. */
   public static boolean sauter(ChunkPos pos) {
      if (repos && trancheDe(pos.x, pos.z, tranches) != tranche) {
         Allege.reposSautes++;
         return true;
      }
      return false;
   }

   /**
    * Temps passé à ajouter au tronçon : N ticks d'un coup pour un tronçon de la tranche (vu une fois tous les N ticks). Hors
    * de sa tranche, seul un tronçon à tics forcés par Forge ({@code shouldForceTicks}) arrive ici : rien ce tick, il reçoit
    * ses N ticks avec sa tranche (même total qu'avec Minecraft).
    */
   public static long duree(ChunkPos pos, long minecraft) {
      if (!repos) {
         return minecraft;
      }
      return trancheDe(pos.x, pos.z, tranches) == tranche ? minecraft * tranches : 0L;
   }
}
