package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.allonge.EtatAllonge;
import fr.heraliz.tactique.allonge.PaquetRefusAllonge;
import fr.heraliz.tactique.allonge.ReseauAllonge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * S'allonger, décisions du serveur (fr.heraliz.tactique.allonge.Allonge) : le joueur applique les mêmes règles chez lui,
 * mais seul le serveur fait foi (un jeu modifié ne peut pas les ignorer).
 * <ul>
 *    <li>se rallonger trop tôt après s'être relevé : refusé (mixin de LivingEntityCrawl), le joueur est prévenu et relevé
 *    chez lui (PaquetRefusAllonge) ;</li>
 *    <li>tirer pendant qu'on se couche : tir refusé ({@code GunShootEvent} de TaCZ, côté serveur : ni balle, ni munition
 *    dépensée, ni son pour les autres).</li>
 * </ul>
 * Les délais du serveur sont un peu plus courts que ceux du joueur (Allonge.MARGE_RALLONGER_MS, MARGE_TIR_MS).
 */
public final class AllongeServeur {
   private AllongeServeur() {
   }

   /** Appelé par la partie serveur (ServeurTactique), pendant le chargement du mod. */
   static void brancher() {
      Allonge.brancherRegle(AllongeServeur::peutSAllonger);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class, AllongeServeur::avantTir);
   }

   /** Délai pour se rallonger : écoulé ou non depuis que ce joueur s'est relevé (vu par le serveur). */
   private static boolean peutSAllonger(LivingEntity tireur, ShooterDataHolder donnees) {
      if (!(tireur instanceof ServerPlayer joueur)) {
         return true;
      }
      EtatAllonge etat = Allonge.etat(donnees);
      if (etat == null) {
         return true;
      }
      long restant = etat.heraliz$getReleveA() + Allonge.delaiRallongerServeurMs() - Allonge.maintenant();
      if (restant <= 0L) {
         return true;
      }
      // TaCZ ne renvoie jamais l'état au joueur : on le relève nous-mêmes chez lui, avec le temps qu'il reste
      ReseauAllonge.envoyer(joueur, new PaquetRefusAllonge(restant));
      return false;
   }

   /** Pas de tir pendant que le joueur se couche. */
   private static void avantTir(GunShootEvent e) {
      if (e.getLogicalSide() != LogicalSide.SERVER || e.getShooter() == null) {
         return;
      }
      ShooterDataHolder donnees = IGunOperator.fromLivingEntity(e.getShooter()).getDataHolder();
      if (donnees == null || !donnees.isCrawling) {
         return;
      }
      EtatAllonge etat = Allonge.etat(donnees);
      if (etat != null && Allonge.maintenant() - etat.heraliz$getCoucheA() < Allonge.sansTirServeurMs()) {
         e.setCanceled(true);
      }
   }
}
