package fr.heraliz.tactique.client.allonge;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.CrawlKey;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.util.InputExtraCheck;
import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.allonge.PaquetDemandePlongeon;
import fr.heraliz.tactique.allonge.PaquetRefusAllonge;
import fr.heraliz.tactique.allonge.PaquetReponsePlongeon;
import fr.heraliz.tactique.allonge.Plongeon;
import fr.heraliz.tactique.allonge.ReseauAllonge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * S'allonger, chez le joueur (fr.heraliz.tactique.allonge.Allonge) : la touche « Ramper » de TaCZ en un seul appui, avec
 * ou sans arme en main, le délai pour se rallonger (avec un message dans la barre d'action), pas de tir pendant qu'on se
 * couche, et le plongeon. Le serveur applique les mêmes règles de son côté (AllongeServeur, PlongeonServeur) : ici, c'est
 * seulement pour que le joueur honnête ne voie ni tir fantôme ni allongé refusé après coup.
 * <ul>
 *    <li>arme TaCZ en main : la touche est celle de TaCZ (CrawlKey), inchangée ;</li>
 *    <li>sans arme TaCZ (main vide ou autre objet) : TaCZ ignore la touche ; HeraliZ la lit lui-même et passe par le même
 *    chemin ({@code crawl} du joueur de TaCZ, que le mixin de LocalPlayerCrawl complète pour le cas sans arme) ;</li>
 *    <li>plongeon : la touche (pour s'allonger) en avançant, pendant un saut ({@link Allonge#FENETRE_SAUT_MS} au plus après)
 *    ou avec la touche de saut enfoncée : au lieu de s'allonger, le joueur demande un plongeon au serveur
 *    ({@link #tenterPlongeon}). Rien ne bouge avant la réponse : l'élan vient du serveur (vitesse de Minecraft). Accepté :
 *    corps à plat ; en retombant, le joueur s'allonge (même chemin qu'une touche, mêmes règles), sauf si la touche de
 *    saut est encore enfoncée (il resaute, debout). Refusé pour un délai : message avec le temps restant.</li>
 * </ul>
 * L'état du joueur local (un seul à la fois) est gardé ici ; les passages debout <-> allongé sont notés par le mixin de
 * LocalPlayerCrawl de TaCZ (fr.heraliz.tactique.mixin.client.LocalPlayerCrawlMixin). Tout se passe sur le fil principal
 * du jeu (ticks, touches, paquets reçus).
 */
public final class AllongeClient {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String CLE_ATTENTE = "overlay.heraliz_tactique.allonge_attente";
   private static final String CLE_ATTENTE_PLONGEON = "overlay.heraliz_tactique.plongeon_attente";
   /** Après un refus du serveur, petite attente en plus du temps qu'il annonce (aller-retour du réseau). */
   private static final long MARGE_REFUS_MS = 100L;
   /** Une demande de plongeon à la fois : sans réponse au bout d'une seconde, on n'attend plus. */
   private static final long ATTENTE_REPONSE_MS = 1000L;
   /** Comme TaCZ (LocalPlayerCrawl.COOLDOWN_TICKS) : 10 ticks (0,5 s) entre deux appuis pris en compte. */
   public static final int ATTENTE_TOUCHE_TICKS = 10;

   /** Réponses de {@link #tickPlongeon} au mixin. */
   public static final int PLONGEON_AUCUN = 0;
   public static final int PLONGEON_EN_VOL = 1;
   public static final int PLONGEON_RETOMBE = 2;

   /** Heures (Allonge.maintenant()) du dernier coucher et du dernier relevé du joueur local ; 0 = jamais. */
   private static long coucheA;
   private static long releveA;
   /** Après un refus du serveur : pas de nouvel allongé avant cette heure. */
   private static long attenteServeurJusqua;
   /** Refus du serveur reçu : le mixin relève le joueur au tick suivant. */
   private static boolean releverDemande;

   /** Plongeon du joueur local : phase (Plongeon.AUCUN, DECOLLAGE, VOL), début du plongeon et de la phase. */
   private static int phasePlongeon;
   private static long debutPlongeonA;
   private static long phasePlongeonA;
   /** Dernier plongeon accepté ; après un refus du serveur, pas de nouveau plongeon avant cette heure. */
   private static long dernierPlongeonA;
   private static long attentePlongeonJusqua;
   /** Demande de plongeon envoyée, réponse attendue (0 : aucune). */
   private static long demandePlongeonA;
   /** Dernier saut du joueur local. */
   private static long sautA;
   /** Retombée d'un plongeon en cours : le coucher compte depuis le début du plongeon. */
   private static long retombeeDebutA;

   private AllongeClient() {
   }

   /** Appelé par la partie client du mod porteur (ClientTactique), pendant le chargement du mod. */
   public static void brancher(IEventBus busMod) {
      busMod.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, e -> e.enqueueWork(AllongeClient::unSeulAppui));
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, e -> {
         reinitialiser();
         unSeulAppui();
      });
      // réapparition, changement de dimension : nouveau joueur local, plus de plongeon en cours
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.Clone.class, e -> finPlongeon());
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class, AllongeClient::avantTir);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, InputEvent.Key.class, AllongeClient::toucheSansArme);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEvent.LivingJumpEvent.class, AllongeClient::saut);
      ReseauAllonge.REFUS_CLIENT.brancher(AllongeClient::refusServeur);
      ReseauAllonge.REPONSE_PLONGEON_CLIENT.brancher(AllongeClient::reponsePlongeon);
   }

   /**
    * Un appui pour s'allonger, un autre pour se relever (choix du propriétaire) : réglage « HoldToCrawl » de TaCZ
    * (tacz-client.toml) remis à false au lancement du jeu et à chaque connexion, même si le joueur l'a changé.
    */
   static void unSeulAppui() {
      try {
         if (Boolean.TRUE.equals(KeyConfig.HOLD_TO_CRAWL.get())) {
            KeyConfig.HOLD_TO_CRAWL.set(false);
            KeyConfig.HOLD_TO_CRAWL.save();
            LOG.info("[heraliz_tactique] TaCZ : « HoldToCrawl » mis à false (un appui pour s'allonger, un autre pour se relever)");
         }
      } catch (RuntimeException ex) {
         LOG.warn("[heraliz_tactique] TaCZ : réglage « HoldToCrawl » impossible à changer ({})", ex.toString());
      }
   }

   /** Nouvelle connexion : nouvel état du serveur, on repart de zéro. */
   private static void reinitialiser() {
      coucheA = 0L;
      releveA = 0L;
      attenteServeurJusqua = 0L;
      releverDemande = false;
      dernierPlongeonA = 0L;
      attentePlongeonJusqua = 0L;
      sautA = 0L;
      finPlongeon();
   }

   private static void finPlongeon() {
      phasePlongeon = Plongeon.AUCUN;
      demandePlongeonA = 0L;
      retombeeDebutA = 0L;
   }

   /**
    * Touche « Ramper » sans arme TaCZ en main : TaCZ ne la traite qu'avec une arme (CrawlKey) ; ici, mêmes conditions que
    * TaCZ (en jeu, ramper permis par le serveur, ni spectateur ni en véhicule), un appui pour s'allonger, un autre pour se
    * relever. Le reste (délai, plongeon, pose) passe par le {@code crawl} du joueur de TaCZ et son mixin.
    */
   private static void toucheSansArme(InputEvent.Key e) {
      if (e.getAction() != GLFW.GLFW_PRESS || !InputExtraCheck.isInGame() || !CrawlKey.CRAWL_KEY.matches(e.getKey(), e.getScanCode())) {
         return;
      }
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (joueur == null || joueur.isSpectator() || joueur.isPassenger() || joueur.getMainHandItem().getItem() instanceof IGun) {
         return;
      }
      if (!Boolean.TRUE.equals(SyncConfig.ENABLE_CRAWL.get())) {
         return;
      }
      IClientPlayerGunOperator operateur = IClientPlayerGunOperator.fromLocalPlayer(joueur);
      operateur.crawl(!operateur.isCrawl());
   }

   /** Saut du joueur local (le plongeon est possible juste après). */
   private static void saut(LivingEvent.LivingJumpEvent e) {
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (joueur != null && e.getEntity() == joueur) {
         sautA = Allonge.maintenant();
      }
   }

   /** Passage debout <-> allongé du joueur local (mixin de LocalPlayerCrawl). */
   public static void changement(boolean allonge) {
      if (allonge) {
         // en retombant d'un plongeon : couché depuis le début du plongeon (durée sans tir)
         coucheA = retombeeDebutA != 0L ? retombeeDebutA : Allonge.maintenant();
      } else {
         releveA = Allonge.maintenant();
      }
   }

   /**
    * Train de HeraliZ Utils (appelé par réflexion, {@code fr.heralisz.utils.train.VoyageursClient}) : s'allonger dans le
    * train, même délai qu'au sol. Vrai : permis ; faux : « Tu pourras te rallonger dans N s » est affiché. Ne pas renommer.
    */
   public static boolean allongeTrain() {
      long restant = restantMs();
      if (restant > 0L) {
         afficherAttente(restant);
         return false;
      }
      return true;
   }

   /** Train de HeraliZ Utils (par réflexion) : le passager se relève ; le délai pour se rallonger repart. */
   public static void releveTrain() {
      releveA = Allonge.maintenant();
   }

   /** Millisecondes avant de pouvoir se rallonger (0 : tout de suite). */
   public static long restantMs() {
      long t = Allonge.maintenant();
      long local = releveA + Allonge.delaiRallongerMs() - t;
      return Math.max(0L, Math.max(local, attenteServeurJusqua - t));
   }

   /** « Tu pourras te rallonger dans 3 s », dans la barre d'action. */
   public static void afficherAttente(long restantMs) {
      afficher(CLE_ATTENTE, restantMs);
   }

   private static void afficher(String cle, long restantMs) {
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (joueur == null) {
         return;
      }
      long secondes = Math.max(1L, (restantMs + 999L) / 1000L);
      joueur.displayClientMessage(Component.translatable(cle, String.valueOf(secondes)), true);
   }

   /** Vrai une seule fois après un refus du serveur : le mixin relève alors le joueur. */
   public static boolean prendreRelever() {
      boolean r = releverDemande;
      releverDemande = false;
      return r;
   }

   /** Le serveur a refusé l'allongé (délai pas écoulé de son côté) : relevé au tick suivant, puis attente annoncée. */
   private static void refusServeur(PaquetRefusAllonge paquet) {
      long restant = Math.max(0L, paquet.restantMs());
      attenteServeurJusqua = Math.max(attenteServeurJusqua, Allonge.maintenant() + restant + MARGE_REFUS_MS);
      releverDemande = true;
      afficherAttente(restantMs());
   }

   // ------------------------------------------------------------------ plongeon

   /** Un plongeon est en cours (accepté, pas encore retombé) : la touche ne fait rien. */
   public static boolean plongeonEnCours() {
      return phasePlongeon != Plongeon.AUCUN;
   }

   /** Millisecondes avant de pouvoir plonger de nouveau (0 : tout de suite). */
   private static long plongeonRestantMs(long t) {
      long local = dernierPlongeonA == 0L ? 0L : dernierPlongeonA + Allonge.delaiPlongeonMs() - t;
      return Math.max(0L, Math.max(local, attentePlongeonJusqua - t));
   }

   /**
    * Appui pour s'allonger (debout, délai pour se rallonger écoulé) : est-ce un plongeon ? Vrai si la touche est prise par
    * le plongeon (demande envoyée, ou en attente : message), et le joueur ne s'allonge pas ; faux : appui ordinaire.
    * Mêmes conditions que le serveur (PlongeonServeur), un peu plus strictes : au sol avec la touche de saut enfoncée, ou
    * en l'air juste après un saut ; en se déplaçant dans le sens des touches tenues (devant, derrière, côté) ; ni en vol, ni en véhicule, ni dans l'eau, ni à terre ou occupé
    * (Réanimation).
    */
   public static boolean tenterPlongeon(LocalPlayer joueur) {
      if (!Allonge.plongeonActif()) {
         return false;
      }
      long t = Allonge.maintenant();
      if (demandePlongeonA != 0L && t - demandePlongeonA < ATTENTE_REPONSE_MS) {
         return true; // une demande à la fois
      }
      if (joueur.isSpectator() || joueur.isPassenger() || joueur.getAbilities().flying || joueur.isFallFlying() || joueur.isInWater()
            || joueur.isInLava() || CompatReanimation.empeche(joueur)) {
         return false;
      }
      ItemStack enMain = joueur.getMainHandItem();
      IGun arme = IGun.getIGunOrNull(enMain);
      if (arme != null && !arme.isCanCrawl(enMain)) {
         return false;
      }
      boolean auSol = joueur.onGround();
      boolean sautEnCours = !auSol && sautA != 0L && t - sautA <= Allonge.FENETRE_SAUT_MS;
      boolean sautEnfonce = auSol && sautEnfonce(joueur);
      if (!sautEnCours && !sautEnfonce) {
         return false;
      }
      // dans le sens des touches de déplacement tenues (devant, derrière, côté, diagonale) : déplacement du dernier tick
      // dans ce sens. Touches lues directement (l'état du joueur n'est mis à jour qu'au tick suivant).
      Minecraft mc = Minecraft.getInstance();
      int avant = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
      int gauche = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
      if (avant == 0 && gauche == 0 && joueur.input != null) {
         avant = (int) Math.signum(joueur.input.forwardImpulse);
         gauche = (int) Math.signum(joueur.input.leftImpulse);
      }
      if (avant == 0 && gauche == 0) {
         return false;
      }
      Vec3 sens = PaquetDemandePlongeon.direction(gauche, avant, joueur.getYRot());
      double dx = joueur.getX() - joueur.xo;
      double dz = joueur.getZ() - joueur.zo;
      double vitesse = dx * sens.x + dz * sens.z;
      if (vitesse < Allonge.AVANT_MIN || vitesse < 0.5D * Math.sqrt(dx * dx + dz * dz)) {
         return false;
      }
      long attente = plongeonRestantMs(t);
      if (attente > 0L) {
         afficher(CLE_ATTENTE_PLONGEON, attente);
         return true;
      }
      demandePlongeonA = t;
      ReseauAllonge.envoyerAuServeur(new PaquetDemandePlongeon((byte) gauche, (byte) avant));
      return true;
   }

   /** Touche de saut enfoncée (lue directement : l'état du joueur n'est mis à jour qu'au tick suivant). */
   private static boolean sautEnfonce(LocalPlayer joueur) {
      return Minecraft.getInstance().options.keyJump.isDown() || (joueur.input != null && joueur.input.jumping);
   }

   /** Réponse du serveur à la demande de plongeon. */
   private static void reponsePlongeon(PaquetReponsePlongeon reponse) {
      long t = Allonge.maintenant();
      demandePlongeonA = 0L;
      switch (reponse.motif()) {
         case PaquetReponsePlongeon.ACCEPTE -> {
            // l'élan suit (paquet de vitesse de Minecraft) : corps à plat dès maintenant
            phasePlongeon = Plongeon.DECOLLAGE;
            debutPlongeonA = t;
            phasePlongeonA = t;
            dernierPlongeonA = t;
            LocalPlayer joueur = Minecraft.getInstance().player;
            if (joueur != null) {
               joueur.setForcedPose(Pose.SWIMMING);
            }
         }
         case PaquetReponsePlongeon.ATTENTE_ALLONGE -> {
            attenteServeurJusqua = Math.max(attenteServeurJusqua, t + Math.max(0L, reponse.attenteMs()) + MARGE_REFUS_MS);
            afficherAttente(restantMs());
         }
         case PaquetReponsePlongeon.ATTENTE_PLONGEON -> {
            attentePlongeonJusqua = Math.max(attentePlongeonJusqua, t + Math.max(0L, reponse.attenteMs()) + MARGE_REFUS_MS);
            afficher(CLE_ATTENTE_PLONGEON, plongeonRestantMs(t));
         }
         default -> {
            // refusé sans message (le serveur voit le joueur dans l'eau, immobile, trop longtemps après son saut...)
         }
      }
   }

   /**
    * Chaque tick du joueur local, avant le « ramper » de TaCZ (mixin de LocalPlayerCrawl) : où en est le plongeon ?
    * <ul>
    *    <li>{@link #PLONGEON_AUCUN} : pas de plongeon (ou abandonné : dans l'eau, en véhicule, en vol, à terre ou occupé, pas de
    *    décollage, longue chute, touche de saut enfoncée en retombant) ;</li>
    *    <li>{@link #PLONGEON_EN_VOL} : corps à plat, rien d'autre ;</li>
    *    <li>{@link #PLONGEON_RETOMBE} : retombé au sol, le mixin allonge le joueur maintenant ({@link #finRetombee()} ensuite).</li>
    * </ul>
    */
   public static int tickPlongeon(LocalPlayer joueur) {
      long t = Allonge.maintenant();
      if (demandePlongeonA != 0L && t - demandePlongeonA >= ATTENTE_REPONSE_MS) {
         demandePlongeonA = 0L; // pas de réponse : on n'attend plus
      }
      if (phasePlongeon == Plongeon.AUCUN) {
         return PLONGEON_AUCUN;
      }
      if (!joueur.isAlive() || joueur.isSpectator() || joueur.isPassenger() || joueur.getAbilities().flying || joueur.isFallFlying()
            || joueur.isInWater() || joueur.isInLava() || CompatReanimation.empeche(joueur)) {
         finPlongeon();
         return PLONGEON_AUCUN;
      }
      long dansPhase = t - phasePlongeonA;
      if (phasePlongeon == Plongeon.DECOLLAGE) {
         if (!joueur.onGround()) {
            phasePlongeon = Plongeon.VOL;
            phasePlongeonA = t;
         } else if (dansPhase > Allonge.DECOLLAGE_MAX_MS) {
            finPlongeon(); // l'élan n'est jamais arrivé
            return PLONGEON_AUCUN;
         }
         return PLONGEON_EN_VOL;
      }
      if (joueur.onGround()) {
         long debut = debutPlongeonA;
         finPlongeon();
         if (sautEnfonce(joueur)) {
            return PLONGEON_AUCUN; // il resaute : debout
         }
         retombeeDebutA = debut;
         return PLONGEON_RETOMBE;
      }
      if (dansPhase > Allonge.VOL_MAX_MS) {
         finPlongeon(); // longue chute : il retombera debout
         return PLONGEON_AUCUN;
      }
      return PLONGEON_EN_VOL;
   }

   /** Fin de la retombée (le joueur s'est allongé, ou non). */
   public static void finRetombee() {
      retombeeDebutA = 0L;
   }

   /**
    * Pas de tir pendant que le joueur local se couche (ou pendant le début de son plongeon) : le tir est annulé avant de
    * partir (TaCZ rend FORGE_EVENT_CANCEL : ni son, ni balle, ni munition ; en semi-automatique, gâchette tenue, le coup
    * part dès la fin de l'attente).
    */
   private static void avantTir(GunShootEvent e) {
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (e.getLogicalSide() != LogicalSide.CLIENT || joueur == null || e.getShooter() != joueur) {
         return;
      }
      long depuis;
      if (plongeonEnCours()) {
         depuis = debutPlongeonA;
      } else if (IClientPlayerGunOperator.fromLocalPlayer(joueur).isCrawl()) {
         depuis = coucheA;
      } else {
         return;
      }
      if (Allonge.maintenant() - depuis < Allonge.sansTirMs()) {
         e.setCanceled(true);
      }
   }
}
