package fr.heraliz.tactique.client.allonge;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.config.client.KeyConfig;
import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.allonge.PaquetRefusAllonge;
import fr.heraliz.tactique.allonge.ReseauAllonge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * S'allonger, chez le joueur (fr.heraliz.tactique.allonge.Allonge) : la touche « Ramper » de TaCZ en un seul appui, le
 * délai pour se rallonger (avec un message dans la barre d'action) et pas de tir pendant qu'on se couche. Le serveur
 * applique les mêmes règles de son côté (AllongeServeur) : ici, c'est seulement pour que le joueur honnête ne voie ni tir
 * fantôme ni allongé refusé après coup.
 * <p>
 * L'état du joueur local (un seul à la fois) est gardé ici ; les passages debout <-> allongé sont notés par le mixin de
 * LocalPlayerCrawl de TaCZ (fr.heraliz.tactique.mixin.client.LocalPlayerCrawlMixin). Tout se passe sur le fil principal
 * du jeu (ticks, touches, paquets reçus).
 */
public final class AllongeClient {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String CLE_ATTENTE = "overlay.heraliz_tactique.allonge_attente";
   /** Après un refus du serveur, petite attente en plus du temps qu'il annonce (aller-retour du réseau). */
   private static final long MARGE_REFUS_MS = 100L;

   /** Heures (Allonge.maintenant()) du dernier coucher et du dernier relevé du joueur local ; 0 = jamais. */
   private static long coucheA;
   private static long releveA;
   /** Après un refus du serveur : pas de nouvel allongé avant cette heure. */
   private static long attenteServeurJusqua;
   /** Refus du serveur reçu : le mixin relève le joueur au tick suivant. */
   private static boolean releverDemande;

   private AllongeClient() {
   }

   /** Appelé par la partie client du mod porteur (ClientTactique), pendant le chargement du mod. */
   public static void brancher(IEventBus busMod) {
      busMod.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, e -> e.enqueueWork(AllongeClient::unSeulAppui));
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, e -> {
         reinitialiser();
         unSeulAppui();
      });
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class, AllongeClient::avantTir);
      ReseauAllonge.REFUS_CLIENT.brancher(AllongeClient::refusServeur);
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
   }

   /** Passage debout <-> allongé du joueur local (mixin de LocalPlayerCrawl). */
   public static void changement(boolean allonge) {
      if (allonge) {
         coucheA = Allonge.maintenant();
      } else {
         releveA = Allonge.maintenant();
      }
   }

   /** Millisecondes avant de pouvoir se rallonger (0 : tout de suite). */
   public static long restantMs() {
      long t = Allonge.maintenant();
      long local = releveA + Allonge.delaiRallongerMs() - t;
      return Math.max(0L, Math.max(local, attenteServeurJusqua - t));
   }

   /** « Tu pourras te rallonger dans 3 s », dans la barre d'action. */
   public static void afficherAttente(long restantMs) {
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (joueur == null) {
         return;
      }
      long secondes = Math.max(1L, (restantMs + 999L) / 1000L);
      joueur.displayClientMessage(Component.translatable(CLE_ATTENTE, String.valueOf(secondes)), true);
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

   /**
    * Pas de tir pendant que le joueur local se couche : le tir est annulé avant de partir (TaCZ rend
    * FORGE_EVENT_CANCEL : ni son, ni balle, ni munition ; en semi-automatique, gâchette tenue, le coup part dès la fin
    * de l'attente).
    */
   private static void avantTir(GunShootEvent e) {
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (e.getLogicalSide() != LogicalSide.CLIENT || joueur == null || e.getShooter() != joueur) {
         return;
      }
      if (IClientPlayerGunOperator.fromLocalPlayer(joueur).isCrawl() && Allonge.maintenant() - coucheA < Allonge.sansTirMs()) {
         e.setCanceled(true);
      }
   }
}
