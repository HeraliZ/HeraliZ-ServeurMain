package fr.heraliz.tactique.tir;

import com.mojang.logging.LogUtils;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import org.slf4j.Logger;

/**
 * Horloge des tirs de TaCZ 1.1.8 (retours du 02/11 : « 15 tirs refusés par TaCZ » au minigun, « balles à blanc »).
 * <p>
 * Comment TaCZ date un tir : à la connexion (et à chaque changement de monde), le serveur et le jeu du joueur notent
 * chacun une « base » (leur heure à l'échange) ; chaque tir part avec son heure comptée depuis la base du joueur. Le
 * serveur refuse un tir trop rapide pour l'arme (COOL_DOWN : écart avec l'heure du tir précédent) et un tir dont l'heure
 * ne colle pas à la sienne à 300 ms près (NETWORK_FAIL : coupure du réseau, horloge du PC qui dérive ou trafiquée) ; dans ce
 * dernier cas il refait l'échange : les deux bases repartent de « maintenant ».
 * <p>
 * Le défaut : après ce nouvel échange, TaCZ garde l'heure du dernier tir réussi comptée depuis l'ANCIENNE base (un grand
 * nombre : le temps écoulé depuis la connexion) alors que les tirs suivants sont comptés depuis la NOUVELLE (de petits
 * nombres). Chaque tir paraît alors « avant » le précédent : refusé pour cadence (COOL_DOWN), tous, jusqu'à ce que le joueur
 * change d'arme, meure ou change de monde ; le serveur annonce aussi une attente énorme au jeu du joueur, qui ne peut plus
 * recharger. Le joueur, lui, voit et entend son arme tirer (le tir est joué chez lui avant la réponse du serveur), mais
 * aucune balle ne part : des balles à blanc. Une seule coupure de plus de 300 ms en plein combat (Wi-Fi, voix, lag) suffit ;
 * au minigun (20 tirs par seconde) l'anticheat compte 15 refus en moins d'une seconde.
 * <p>
 * Correction : au recalage, les heures gardées du dernier tir sont recomptées depuis la nouvelle base (même instant réel :
 * {@code heure + base} ne change pas). La cadence reste vérifiée sur le temps réel : un tir trop rapide reste refusé.
 * Un tir parti avec l'ancienne base juste après l'échange (le jeu du joueur répond au serveur avant d'avoir changé sa base)
 * est aussi remis à l'heure de la nouvelle base pendant 3 s, s'il collait à l'ancienne : sinon il échouait et relançait un
 * échange. Fil du serveur seulement (données d'un tireur).
 */
public final class HorlogeTirs {
   private static final Logger LOG = LogUtils.getLogger();
   /** Avance maximale d'un tir sur l'heure du serveur acceptée par TaCZ (LivingEntityShoot.shoot). */
   static final long AVANCE_MAX = 300L;
   /** Retard laissé à un tir de l'ancienne base (TaCZ en accepte 300 ms + deux ticks ; il revérifie de toute façon). */
   static final long RETARD_MAX = 2000L;
   /** Après un recalage, un tir daté avec l'ancienne base est remis à l'heure pendant ce temps (ms). */
   static final long APRES_RECALAGE = 3000L;
   /** Un bilan dans le journal au plus toutes les 30 s, seulement s'il s'est passé quelque chose. */
   private static final long BILAN_MS = 30_000L;
   private static int recalages;
   private static int remisALHeure;
   private static long prochainBilan;

   private HorlogeTirs() {
   }

   /**
    * Nouvelle base du serveur pour ce tireur (réponse du joueur à l'échange de TaCZ, ClientMessageSyncBaseTimestamp) :
    * heures du dernier tir recomptées depuis la nouvelle base, puis base remplacée.
    */
   public static void recaler(ShooterDataHolder d, long nouvelleBase, long maintenant) {
      long ancienne = d.baseTimestamp;
      long ecart = nouvelleBase - ancienne;
      if (ecart != 0L) {
         if (d.shootTimestamp != -1L) {
            d.shootTimestamp = horsSentinelle(d.shootTimestamp - ecart);
            recalages++;
         }
         if (d.lastShootTimestamp != -1L) {
            d.lastShootTimestamp = horsSentinelle(d.lastShootTimestamp - ecart);
         }
         ((HorlogeTir) d).heraliz$noterRecalage(ancienne, maintenant);
      }
      d.baseTimestamp = nouvelleBase;
      bilan(maintenant);
   }

   /**
    * Heure d'un tir reçu : inchangée, sauf un tir daté avec l'ancienne base juste après un recalage, remis à l'heure de la
    * nouvelle (même instant réel). TaCZ juge ensuite le tir comme d'habitude (cadence, réseau).
    */
   public static long heureDuTir(ShooterDataHolder d, long heure, long maintenant) {
      HorlogeTir h = (HorlogeTir) d;
      long quand = h.heraliz$recaleA();
      if (quand == 0L || maintenant - quand > APRES_RECALAGE) {
         return heure;
      }
      if (maintenant - d.baseTimestamp - heure >= -AVANCE_MAX) {
         return heure;                     // plausible avec la base actuelle : TaCZ jugera
      }
      long ancienne = h.heraliz$ancienneBase();
      long ecartAncienne = maintenant - ancienne - heure;
      if (ecartAncienne < -AVANCE_MAX || ecartAncienne > RETARD_MAX) {
         return heure;                     // ne colle pas non plus à l'ancienne base : TaCZ le refusera
      }
      remisALHeure++;
      bilan(maintenant);
      return heure - (d.baseTimestamp - ancienne);
   }

   /** -1 veut dire « jamais tiré » pour TaCZ : une heure recomptée ne le devient jamais par hasard. */
   static long horsSentinelle(long heure) {
      return heure == -1L ? -2L : heure;
   }

   private static void bilan(long maintenant) {
      if ((recalages == 0 && remisALHeure == 0) || maintenant < prochainBilan) {
         return;
      }
      prochainBilan = maintenant + BILAN_MS;
      LOG.info("[armes] horloge des tirs de TaCZ recalée {} fois après un tir (coupure réseau ou horloge du PC) : cadence "
         + "gardée, plus de balles à blanc ensuite ; {} tir(s) datés avec l'ancienne horloge remis à l'heure", recalages, remisALHeure);
      recalages = 0;
      remisALHeure = 0;
   }
}
