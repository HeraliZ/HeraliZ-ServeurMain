package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.allonge.EtatAllonge;
import fr.heraliz.tactique.allonge.PaquetRefusAllonge;
import fr.heraliz.tactique.allonge.Plongeon;
import fr.heraliz.tactique.allonge.ReseauAllonge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * S'allonger, décisions du serveur (fr.heraliz.tactique.allonge.Allonge) : le joueur applique les mêmes règles chez lui,
 * mais seul le serveur fait foi (un jeu modifié ne peut pas les ignorer).
 * <ul>
 *    <li>se rallonger trop tôt après s'être relevé : refusé (mixin de LivingEntityCrawl), le joueur est prévenu et relevé
 *    chez lui (PaquetRefusAllonge) ; à terre, ou en train de fouiller ou réanimer (Réanimation) : refusé sans message ;</li>
 *    <li>allongé sans arme TaCZ en main (main vide ou autre objet) : le tick de TaCZ relèverait le joueur ; il est
 *    remplacé, pour ce joueur seulement, par le même tick sans les règles de l'arme (relevé hors du sol, à la nage, en
 *    véhicule, en spectateur), et la même pose à plat. Avec une arme, le tick de TaCZ, inchangé. L'état reste celui de
 *    TaCZ ({@code isCrawling}) : changer d'objet en restant allongé ne relève pas le joueur ;</li>
 *    <li>dans l'eau, à terre, ou en train de fouiller ou réanimer (Réanimation), avec ou sans arme : plus allongé (la
 *    pose d'un joueur à terre, à plat, ou qui fouille, accroupi, est celle de Réanimation) ;</li>
 *    <li>allongé : pas de course (un joueur qui s'allonge en courant ne court plus) ;</li>
 *    <li>plongeon : {@link PlongeonServeur} ;</li>
 *    <li>tirer pendant qu'on se couche, ou pendant le début d'un plongeon : tir refusé ({@code GunShootEvent} de TaCZ,
 *    côté serveur : ni balle, ni munition dépensée, ni son pour les autres).</li>
 * </ul>
 * Les délais du serveur sont un peu plus courts que ceux du joueur (Allonge.MARGE_RALLONGER_MS, MARGE_TIR_MS).
 */
public final class AllongeServeur {
   private AllongeServeur() {
   }

   /** Appelé par la partie serveur (ServeurTactique), pendant le chargement du mod. */
   static void brancher() {
      Allonge.brancherRegle(AllongeServeur::peutSAllonger);
      Allonge.brancherTick(AllongeServeur::avantTick);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class, AllongeServeur::avantTir);
      PlongeonServeur.brancher();
   }

   /** Délai pour se rallonger : écoulé ou non depuis que ce joueur s'est relevé (vu par le serveur). */
   private static boolean peutSAllonger(LivingEntity tireur, ShooterDataHolder donnees) {
      if (!(tireur instanceof ServerPlayer joueur)) {
         return true;
      }
      if (CompatReanimation.empeche(joueur)) {
         return false;
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

   /**
    * Tick d'un joueur, avant le « ramper » de TaCZ. Vrai : traité ici (TaCZ ne passe pas, l'état est noté) ; faux : TaCZ
    * fait son tick habituel (arme TaCZ en main).
    */
   private static boolean avantTick(Player joueur, ShooterDataHolder donnees) {
      EtatAllonge etat = Allonge.etat(donnees);
      if (etat == null) {
         return false;
      }
      boolean empeche = CompatReanimation.empeche(joueur);
      if (donnees.isCrawling && (empeche || joueur.isInWater())) {
         donnees.isCrawling = false;
      }
      if (PlongeonServeur.tick(joueur, donnees, etat, empeche)) {
         Allonge.noter(donnees);
         return true;
      }
      // allongé : pas de course (2.12.0). Le joueur s'arrête lui-même (mixin de LocalPlayerCrawl) ; ceci couvre un jeu
      // modifié ou un ancien jar : sinon TaCZ le compte « en train de courir » à plat (tir refusé, arme baissée)
      if (donnees.isCrawling && joueur.isSprinting()) {
         joueur.setSprinting(false);
      }
      if (!sansArme(donnees)) {
         return false;
      }
      // Sans arme : ce que fait TaCZ avec une arme (LivingEntityCrawl.tickCrawling), sans ses règles d'arme. TaCZ remet
      // aussi la pose forcée à rien à chaque tick quand le joueur n'est pas allongé : gardé tel quel (Réanimation, le
      // casino et les zombies remettent la leur ensuite, comme avant).
      if (donnees.isCrawling && (joueur.isSpectator() || joueur.isPassenger() || joueur.isSwimming() || !joueur.onGround())) {
         donnees.isCrawling = false;
      }
      joueur.setForcedPose(donnees.isCrawling ? Pose.SWIMMING : null);
      Allonge.noter(donnees);
      return true;
   }

   /** Vrai si TaCZ relèverait ce joueur faute d'arme : l'objet qu'il a sorti (paquet de TaCZ) n'est pas une arme TaCZ. */
   private static boolean sansArme(ShooterDataHolder donnees) {
      if (donnees.currentGunItem == null) {
         return true;
      }
      ItemStack objet = donnees.currentGunItem.get();
      return objet == null || !(objet.getItem() instanceof IGun);
   }

   /** Pas de tir pendant que le joueur se couche (ou pendant le début de son plongeon). */
   private static void avantTir(GunShootEvent e) {
      if (e.getLogicalSide() != LogicalSide.SERVER || e.getShooter() == null) {
         return;
      }
      ShooterDataHolder donnees = IGunOperator.fromLivingEntity(e.getShooter()).getDataHolder();
      EtatAllonge etat = Allonge.etat(donnees);
      if (donnees == null || etat == null) {
         return;
      }
      Plongeon plongeon = etat.heraliz$getPlongeon();
      long depuis;
      if (plongeon != null && plongeon.enCours()) {
         depuis = plongeon.debutA;
      } else if (donnees.isCrawling) {
         depuis = etat.heraliz$getCoucheA();
      } else {
         return;
      }
      if (Allonge.maintenant() - depuis < Allonge.sansTirServeurMs()) {
         e.setCanceled(true);
      }
   }
}
