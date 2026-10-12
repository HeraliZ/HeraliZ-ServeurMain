package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.allonge.EtatAllonge;
import fr.heraliz.tactique.allonge.PaquetReponsePlongeon;
import fr.heraliz.tactique.allonge.Plongeon;
import fr.heraliz.tactique.allonge.ReseauAllonge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Plongeon façon Call of Duty, décidé et donné par le serveur seul (fr.heraliz.tactique.allonge.Allonge). Le joueur ne
 * fait que le demander (PaquetDemandePlongeon) ; le serveur vérifie tout de son côté, puis donne l'élan.
 * <p>
 * <b>Conditions</b> (sinon refus, PaquetReponsePlongeon) : plongeon permis ({@code plongeon}) et ramper permis par TaCZ ;
 * joueur vivant, ni spectateur, ni en vol (créatif ou élytres), ni en véhicule, ni dans l'eau ou la lave, ni à terre ou
 * en train de fouiller ou réanimer (Réanimation), ni déjà allongé ou en plongeon ; pas une arme TaCZ qui interdit de
 * ramper ; délai pour se rallonger écoulé ; délai entre deux plongeons écoulé ; au sol, ou en l'air au plus {@link Allonge#fenetreSautServeurMs()} après un
 * saut (vu par le serveur : {@code LivingJumpEvent}, que Minecraft lance aussi côté serveur quand le joueur quitte le sol
 * vers le haut) ; et en train d'avancer : vitesse horizontale (positions reçues, lissées tick après tick) d'au moins
 * {@link Allonge#AVANT_MIN_SERVEUR} bloc par tick dans le sens du regard, et surtout vers l'avant (pas en crabe ni à
 * reculons). Les demandes à moins de 0,15 s d'intervalle sont ignorées.
 * <p>
 * <b>Élan</b> : vitesse de Minecraft {@code setDeltaMovement} + {@code hurtMarked} (comme un recul) : Minecraft l'envoie au
 * joueur (ClientboundSetEntityMotionPacket), qui la remplace par la sienne ; l'anticheat Spartan voit ce paquet et
 * l'attend. Vers l'avant ({@code plongeon_vitesse}) dans le sens du regard ; vers le haut {@code plongeon_hauteur} depuis
 * le sol, rien en l'air (le saut a déjà donné l'élan : jamais plus haut qu'un saut + 0,22 bloc). Les dégâts de chute ne
 * sont pas touchés (ceux de Minecraft).
 * <p>
 * <b>Ensuite</b> : corps à plat (pose « nage » forcée, comme l'allongé) tant que le plongeon dure : décollage (l'élan
 * arrive par le réseau), vol, puis retombée. C'est le joueur qui, en retombant, demande à s'allonger (paquet habituel de
 * TaCZ, mêmes règles qu'une touche) ; le serveur garde le corps à plat jusqu'à cette demande (pas de redressement d'un
 * tick pour les autres joueurs), au plus le temps d'un aller-retour du réseau. Plongeon abandonné (le joueur retombe
 * debout) : dans l'eau, en véhicule, en vol, à terre ou occupé (Réanimation), sans décollage ou sans demande à temps,
 * ou après 2,5 s de chute.
 */
public final class PlongeonServeur {
   /** Lissage de la vitesse vue par le serveur : moitié de l'ancienne, moitié du dernier tick. */
   private static final double LISSAGE = 0.5D;
   /** Au-delà (blocs en un tick), c'est une téléportation : la vitesse lissée repart de zéro. */
   private static final double SAUT_DE_POSITION = 2.0D;
   /** Demandes plus rapprochées ignorées, sans réponse (un jeu modifié ne peut pas inonder le serveur). */
   private static final long DEMANDES_MIN_MS = 150L;
   /** Attente du réseau au plus (décollage, demande d'allongé à la retombée). */
   private static final long ATTENTE_MAX_MS = 4000L;

   private PlongeonServeur() {
   }

   /** Appelé par AllongeServeur.brancher(), pendant le chargement du mod. */
   static void brancher() {
      ReseauAllonge.PLONGEON_SERVEUR.brancher(PlongeonServeur::demande);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEvent.LivingJumpEvent.class, PlongeonServeur::saut);
   }

   /** Le plongeon de ce joueur, créé au premier besoin. */
   private static Plongeon plongeon(EtatAllonge etat) {
      Plongeon p = etat.heraliz$getPlongeon();
      if (p == null) {
         p = new Plongeon();
         etat.heraliz$setPlongeon(p);
      }
      return p;
   }

   /** Saut d'un joueur vu par le serveur (ServerGamePacketListenerImpl : le joueur quitte le sol vers le haut). */
   private static void saut(LivingEvent.LivingJumpEvent e) {
      if (e.getEntity() instanceof ServerPlayer joueur) {
         EtatAllonge etat = Allonge.etat(IGunOperator.fromLivingEntity(joueur).getDataHolder());
         if (etat != null) {
            plongeon(etat).sautA = Allonge.maintenant();
         }
      }
   }

   /**
    * Chaque tick d'un joueur (AllongeServeur.avantTick, avant le « ramper » de TaCZ) : vitesse lissée, puis le plongeon en
    * cours. Vrai si un plongeon est en cours (corps à plat, TaCZ ne passe pas) ; faux sinon.
    */
   static boolean tick(Player joueur, ShooterDataHolder donnees, EtatAllonge etat, boolean empeche) {
      Plongeon p = plongeon(etat);
      suivre(joueur, p);
      if (!p.enCours()) {
         return false;
      }
      if (donnees.isCrawling) {
         // le joueur s'est allongé en retombant (sa demande vient d'arriver) : plongeon fini, l'allongé prend la suite
         p.phase = Plongeon.AUCUN;
         return false;
      }
      if (empeche || !joueur.isAlive() || joueur.isSpectator() || joueur.isPassenger() || joueur.isInWater() || joueur.isInLava()
            || joueur.getAbilities().flying || joueur.isFallFlying()) {
         p.phase = Plongeon.AUCUN;
         return false;
      }
      long t = Allonge.maintenant();
      long dansPhase = t - p.phaseA;
      long attente = attenteReseau(joueur);
      switch (p.phase) {
         case Plongeon.DECOLLAGE -> {
            if (!joueur.onGround()) {
               phase(p, Plongeon.VOL, t);
            } else if (dansPhase > attente) {
               p.phase = Plongeon.AUCUN; // l'élan n'est jamais arrivé
               return false;
            }
         }
         case Plongeon.VOL -> {
            if (joueur.onGround()) {
               phase(p, Plongeon.ATTERRI, t);
            } else if (dansPhase > Allonge.VOL_MAX_MS) {
               p.phase = Plongeon.AUCUN; // longue chute : il retombera debout
               return false;
            }
         }
         case Plongeon.ATTERRI -> {
            if (!joueur.onGround()) {
               phase(p, Plongeon.VOL, t); // retombée vue trop tôt (positions du joueur en retard sur l'élan) : encore en vol
            } else if (dansPhase > attente) {
               p.phase = Plongeon.AUCUN; // pas de demande d'allongé : debout
               return false;
            }
         }
         default -> {
            p.phase = Plongeon.AUCUN;
            return false;
         }
      }
      joueur.setForcedPose(Pose.SWIMMING);
      return true;
   }

   private static void phase(Plongeon p, int phase, long t) {
      p.phase = phase;
      p.phaseA = t;
   }

   /** Vitesse horizontale lissée (blocs par tick), d'après les positions reçues du joueur. */
   private static void suivre(Player joueur, Plongeon p) {
      double x = joueur.getX();
      double z = joueur.getZ();
      double dx = x - p.xPrec;
      double dz = z - p.zPrec;
      if (!p.suivi || Math.abs(dx) > SAUT_DE_POSITION || Math.abs(dz) > SAUT_DE_POSITION) {
         p.suivi = true;
         p.vx = 0.0D;
         p.vz = 0.0D;
      } else {
         p.vx = p.vx * LISSAGE + dx * (1.0D - LISSAGE);
         p.vz = p.vz * LISSAGE + dz * (1.0D - LISSAGE);
      }
      p.xPrec = x;
      p.zPrec = z;
   }

   /** Attente du réseau : 1,5 s plus deux fois le ping du joueur, au plus 4 s. */
   private static long attenteReseau(Player joueur) {
      long ping = joueur instanceof ServerPlayer sp ? Math.max(0, sp.latency) : 0L;
      return Math.min(ATTENTE_MAX_MS, Allonge.DECOLLAGE_MAX_MS + 2L * ping);
   }

   /** Demande de plongeon d'un joueur (PaquetDemandePlongeon). */
   private static void demande(ServerPlayer joueur) {
      ShooterDataHolder donnees = IGunOperator.fromLivingEntity(joueur).getDataHolder();
      EtatAllonge etat = Allonge.etat(donnees);
      if (donnees == null || etat == null) {
         repondre(joueur, PaquetReponsePlongeon.REFUSE, 0L);
         return;
      }
      Plongeon p = plongeon(etat);
      long t = Allonge.maintenant();
      if (t - p.demandeA < DEMANDES_MIN_MS) {
         return;
      }
      p.demandeA = t;
      if (!Allonge.plongeonActif() || !Boolean.TRUE.equals(SyncConfig.ENABLE_CRAWL.get())) {
         repondre(joueur, PaquetReponsePlongeon.REFUSE, 0L);
         return;
      }
      if (!joueur.isAlive() || joueur.isSpectator() || joueur.isPassenger() || joueur.getAbilities().flying || joueur.isFallFlying()
            || joueur.isInWater() || joueur.isInLava() || joueur.isSleeping() || CompatReanimation.empeche(joueur)
            || donnees.isCrawling || p.enCours()) {
         repondre(joueur, PaquetReponsePlongeon.REFUSE, 0L);
         return;
      }
      ItemStack enMain = joueur.getMainHandItem();
      if (enMain.getItem() instanceof IGun arme && !arme.isCanCrawl(enMain)) {
         // arme qui interdit de ramper (règle de TaCZ) : pas d'allongé, donc pas de plongeon
         repondre(joueur, PaquetReponsePlongeon.REFUSE, 0L);
         return;
      }
      long rallonger = etat.heraliz$getReleveA() + Allonge.delaiRallongerServeurMs() - t;
      if (rallonger > 0L) {
         repondre(joueur, PaquetReponsePlongeon.ATTENTE_ALLONGE, rallonger);
         return;
      }
      long recharge = p.dernierA == 0L ? 0L : p.dernierA + Allonge.delaiPlongeonServeurMs() - t;
      if (recharge > 0L) {
         repondre(joueur, PaquetReponsePlongeon.ATTENTE_PLONGEON, recharge);
         return;
      }
      boolean auSol = joueur.onGround();
      boolean apresSaut = !auSol && p.sautA != 0L && t - p.sautA <= Allonge.fenetreSautServeurMs();
      if (!auSol && !apresSaut) {
         repondre(joueur, PaquetReponsePlongeon.REFUSE, 0L);
         return;
      }
      Vec3 regard = Vec3.directionFromRotation(0.0F, joueur.getYRot());
      if (!avance(joueur, p, regard)) {
         repondre(joueur, PaquetReponsePlongeon.REFUSE, 0L);
         return;
      }
      // Élan donné par le serveur : Minecraft envoie cette vitesse au joueur (comme un recul), Spartan la voit.
      double vitesse = Allonge.plongeonVitesse();
      double haut = auSol ? Allonge.plongeonHauteur() : 0.0D;
      joueur.setDeltaMovement(regard.x * vitesse, haut, regard.z * vitesse);
      joueur.hurtMarked = true;
      p.phase = Plongeon.DECOLLAGE;
      p.phaseA = t;
      p.debutA = t;
      p.dernierA = t;
      joueur.setForcedPose(Pose.SWIMMING);
      repondre(joueur, PaquetReponsePlongeon.ACCEPTE, 0L);
   }

   /**
    * Le joueur avance-t-il ? Vitesse lissée, ou déplacement reçu depuis le dernier tick s'il est plus franc (les positions
    * arrivent par paquets) : assez rapide dans le sens du regard, et surtout vers l'avant (au moins la moitié de la vitesse).
    */
   private static boolean avance(ServerPlayer joueur, Plongeon p, Vec3 regard) {
      double lisseAvant = p.vx * regard.x + p.vz * regard.z;
      double lisseTotal = Math.sqrt(p.vx * p.vx + p.vz * p.vz);
      double dx = joueur.getX() - p.xPrec;
      double dz = joueur.getZ() - p.zPrec;
      double recentAvant = dx * regard.x + dz * regard.z;
      double recentTotal = Math.sqrt(dx * dx + dz * dz);
      boolean recent = p.suivi && recentAvant > lisseAvant && recentTotal <= SAUT_DE_POSITION;
      double avant = recent ? recentAvant : lisseAvant;
      double total = recent ? recentTotal : lisseTotal;
      return avant >= Allonge.AVANT_MIN_SERVEUR && avant >= total * 0.5D;
   }

   private static void repondre(ServerPlayer joueur, int motif, long attenteMs) {
      ReseauAllonge.envoyer(joueur, new PaquetReponsePlongeon(motif, Math.max(0L, attenteMs)));
   }
}
